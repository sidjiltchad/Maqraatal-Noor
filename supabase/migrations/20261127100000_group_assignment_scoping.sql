-- =============================================================================
--  Phase 5: authoritative teacher/student assignment scoping by group + level.
--
--  This is a forward-only migration.  It adds:
--    * teacher_assignments(teacher_id, group_id, level)
--    * server-side helpers for teacher/student scope
--    * RLS that limits teachers to their assigned groups/levels and students
--    * RPC checks so a teacher cannot act outside an assigned student
--    * supervisor-only assign/remove RPCs for teachers and students
--    * a pending-proposal path where only supervisor approval mutates assignment
--
--  It does not touch LiveKit, accounts, OAuth, or any remote service.
-- =============================================================================
begin;

-- -----------------------------------------------------------------------------
-- 1) Teacher -> group/level assignment.  A group is the parent unit; the level
--    is the learning level inside that group, exactly as member_groups stores
--    it for students.  The general supervisor does not need a row here: she has
--    global teacher capability through is_supervisor()/is_staff().
-- -----------------------------------------------------------------------------
create table if not exists public.teacher_assignments (
    teacher_id  uuid not null references public.profiles (id) on delete cascade,
    group_id    uuid not null references public.groups (id) on delete cascade,
    level       text not null check (level in ('foundation', 'tajweed', 'hifz')),
    assigned_by uuid references public.profiles (id) on delete set null,
    assigned_at timestamptz not null default now(),
    primary key (teacher_id, group_id, level)
);

create index if not exists teacher_assignments_group_idx
    on public.teacher_assignments (group_id, level);
create index if not exists teacher_assignments_teacher_idx
    on public.teacher_assignments (teacher_id);

comment on table public.teacher_assignments is
    'إسناد المعلمة الصريح إلى مجموعة+مستوى؛ المشرفة العامة لا تحتاج صفًا هنا.';

alter table public.teacher_assignments enable row level security;
grant select, insert, update, delete on public.teacher_assignments to authenticated;

-- -----------------------------------------------------------------------------
-- 2) Scope helpers.  All are SECURITY DEFINER so RLS policies can evaluate
--    them without recursive RLS or accidental row visibility changes.
-- -----------------------------------------------------------------------------

-- True only when the caller is an active teacher assigned to a group/level that
-- currently contains the target active student.  It deliberately does NOT
-- include supervisors; supervisor-wide access is expressed separately.
create or replace function public.teaches_student(p_student uuid)
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select p_student is not null
     and public.is_staff()
     and exists (
       select 1
         from public.halqa_members s
         join public.member_groups mg on mg.user_id = s.user_id
         join public.teacher_assignments ta
           on ta.group_id = mg.group_id and ta.level = mg.level
        where s.user_id = p_student
          and s.status = 'active'
          and s.role = 'student'
          and ta.teacher_id = auth.uid()
     );
$$;

-- Supervisor/owner-wide access plus the explicit teacher assignment above.
create or replace function public.can_access_student(p_student uuid)
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select coalesce(public.is_supervisor(), false)
      or coalesce(public.teaches_student(p_student), false);
$$;

create or replace function public.is_assigned_to_group_level(p_group uuid, p_level text)
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select p_group is not null
     and p_level is not null
     and public.is_staff()
     and exists (
       select 1 from public.teacher_assignments ta
        where ta.teacher_id = auth.uid()
          and ta.group_id = p_group
          and ta.level = p_level
     );
$$;

create or replace function public.is_assigned_to_group(p_group uuid)
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select p_group is not null
     and public.is_staff()
     and exists (
       select 1 from public.teacher_assignments ta
        where ta.teacher_id = auth.uid()
          and ta.group_id = p_group
     );
$$;

create or replace function public.belongs_to_group(p_group uuid)
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select p_group is not null
     and exists (
       select 1 from public.member_groups mg
        where mg.user_id = auth.uid()
          and mg.group_id = p_group
     );
$$;

revoke all on function public.teaches_student(uuid) from public, anon;
revoke all on function public.can_access_student(uuid) from public, anon;
revoke all on function public.is_assigned_to_group_level(uuid, text) from public, anon;
revoke all on function public.is_assigned_to_group(uuid) from public, anon;
revoke all on function public.belongs_to_group(uuid) from public, anon;
grant execute on function public.teaches_student(uuid),
                         public.can_access_student(uuid),
                         public.is_assigned_to_group_level(uuid, text),
                         public.is_assigned_to_group(uuid),
                         public.belongs_to_group(uuid)
  to authenticated, service_role;

-- -----------------------------------------------------------------------------
-- 3) Direct-write guards.  RLS is the primary boundary; these triggers are a
--    second line for direct table writes.  SECURITY DEFINER RPCs perform their
--    own authorization checks before reaching these tables.
-- -----------------------------------------------------------------------------
create or replace function public.is_server_context()
returns boolean
language sql stable set search_path = public, pg_temp as $$
  select current_user::text in ('postgres', 'supabase_admin', 'service_role')
      or exists (
        select 1 from pg_roles
         where rolname::text = current_user::text and rolsuper
      );
$$;

create or replace function public.guard_assignment_write()
returns trigger
language plpgsql set search_path = public, pg_temp as $$
begin
  if coalesce(current_setting('app.authorized_assignment_change', true), 'off') = 'on'
     or public.is_server_context()
  then
    if tg_op = 'DELETE' then return old; else return new; end if;
  end if;

  if not public.is_supervisor() then
    raise exception 'تعديل الإسناد مسموح للمشرفة العامة أو المالكة فقط'
      using errcode = '42501';
  end if;

  if tg_op = 'DELETE' then return old; else return new; end if;
end;
$$;

drop trigger if exists guard_groups_write on public.groups;
create trigger guard_groups_write
  before insert or update or delete on public.groups
  for each row execute function public.guard_assignment_write();

drop trigger if exists guard_member_groups_write on public.member_groups;
create trigger guard_member_groups_write
  before insert or update or delete on public.member_groups
  for each row execute function public.guard_assignment_write();

drop trigger if exists guard_teacher_assignments_write on public.teacher_assignments;
create trigger guard_teacher_assignments_write
  before insert or update or delete on public.teacher_assignments
  for each row execute function public.guard_assignment_write();

-- The membership level is assignment data.  Role changes already have their
-- own guard from the P0 migration; this trigger covers the remaining field.
create or replace function public.guard_member_level_write()
returns trigger
language plpgsql set search_path = public, pg_temp as $$
begin
  if new.level is not distinct from old.level then
    return new;
  end if;

  if coalesce(current_setting('app.authorized_assignment_change', true), 'off') = 'on'
     or coalesce(current_setting('app.authorized_role_change', true), 'off') = 'on'
     or coalesce(current_setting('app.owner_provisioning', true), 'off') = 'on'
     or public.is_server_context()
     or public.is_supervisor()
  then
    return new;
  end if;

  raise exception 'تعديل المستوى مسموح للمشرفة العامة أو المالكة فقط'
    using errcode = '42501';
end;
$$;

drop trigger if exists guard_member_level_write on public.halqa_members;
create trigger guard_member_level_write
  before update of level on public.halqa_members
  for each row execute function public.guard_member_level_write();

-- Proposals are proposals only.  A teacher may insert a pending proposal for an
-- assigned student; only a supervisor may decide it.  The RPCs enforce the same
-- rules, so a direct PostgREST write cannot bypass them.
create or replace function public.guard_group_proposal_write()
returns trigger
language plpgsql set search_path = public, pg_temp as $$
begin
  if public.is_server_context() then
    if tg_op = 'DELETE' then return old; else return new; end if;
  end if;

  if tg_op = 'INSERT' then
    if new.proposed_by is distinct from auth.uid() then
      raise exception 'الاقتراح يجب أن يكون باسم مقدّمته' using errcode = '42501';
    end if;
    if new.status <> 'pending' then
      raise exception 'الاقتراح الجديد يجب أن يكون معلّقا' using errcode = '42501';
    end if;
    if not (public.is_supervisor()
            or (public.is_staff() and public.teaches_student(new.student_id))) then
      raise exception 'اقتراح النقل/الترقية مسموح للمعلمة المسندة أو المشرفة فقط'
        using errcode = '42501';
    end if;
    return new;
  end if;

  if not public.is_supervisor() then
    raise exception 'قرار الاقتراح مسموح للمشرفة العامة أو المالكة فقط'
      using errcode = '42501';
  end if;

  if tg_op = 'DELETE' then return old; else return new; end if;
end;
$$;

drop trigger if exists guard_group_proposal_write on public.group_proposals;
create trigger guard_group_proposal_write
  before insert or update or delete on public.group_proposals
  for each row execute function public.guard_group_proposal_write();

-- -----------------------------------------------------------------------------
-- 4) RLS: read scope.  Keep self-read of the membership row for pending users
--    so the app can show their status, but no educational/assignment rows until
--    the membership is active.
-- -----------------------------------------------------------------------------
drop policy if exists groups_read on public.groups;
create policy groups_read on public.groups
  for select to authenticated
  using (
    public.is_supervisor()
    or public.is_assigned_to_group(id)
    or (public.is_member() and public.belongs_to_group(id))
  );

drop policy if exists member_groups_read on public.member_groups;
create policy member_groups_read on public.member_groups
  for select to authenticated
  using (
    public.is_supervisor()
    or (public.is_staff() and public.is_assigned_to_group_level(group_id, level))
    or (public.is_member() and user_id = auth.uid())
  );

drop policy if exists teacher_assignments_read on public.teacher_assignments;
create policy teacher_assignments_read on public.teacher_assignments
  for select to authenticated
  using (
    (public.is_supervisor() and (public.is_owner() or not private.is_owner(teacher_id)))
    or (public.is_staff() and teacher_id = auth.uid())
  );

drop policy if exists teacher_assignments_write on public.teacher_assignments;
create policy teacher_assignments_write on public.teacher_assignments
  for all to authenticated
  using (
    public.is_supervisor()
    and (public.is_owner() or not private.is_owner(teacher_id))
  )
  with check (
    public.is_supervisor()
    and (public.is_owner() or not private.is_owner(teacher_id))
  );

drop policy if exists members_select on public.halqa_members;
create policy members_select on public.halqa_members
  for select to authenticated
  using (
    user_id = auth.uid()
    or (
      public.is_member()
      and (
        (public.is_supervisor() and (public.is_owner() or not private.is_owner(user_id)))
        or public.teaches_student(user_id)
      )
    )
  );

drop policy if exists attendance_select on public.session_attendance;
create policy attendance_select on public.session_attendance
  for select to authenticated
  using (
    (public.is_member() and user_id = auth.uid())
    or public.is_supervisor()
    or (public.is_staff() and public.teaches_student(user_id))
  );

drop policy if exists turns_select on public.recitation_turns;
create policy turns_select on public.recitation_turns
  for select to authenticated
  using (
    (public.is_member() and student_id = auth.uid())
    or public.is_supervisor()
    or (public.is_staff() and public.teaches_student(student_id))
  );

drop policy if exists evaluations_select on public.evaluations;
create policy evaluations_select on public.evaluations
  for select to authenticated
  using (
    (public.is_member() and student_id = auth.uid())
    or public.is_supervisor()
    or (public.is_staff() and public.teaches_student(student_id))
  );

drop policy if exists messages_select on public.messages;
create policy messages_select on public.messages
  for select to authenticated
  using (
    public.is_member()
    and (
      thread = 'halqa'
      or public.is_supervisor()
      or student_id = auth.uid()
      or (public.is_staff() and public.teaches_student(student_id))
    )
  );

drop policy if exists receipts_select on public.message_receipts;
create policy receipts_select on public.message_receipts
  for select to authenticated
  using (
    user_id = auth.uid()
    or (public.is_supervisor() and public.can_see_message(message_id))
  );

-- -----------------------------------------------------------------------------
-- 5) RLS: write scope.  Supervisors/owners retain administrative assignment
--    authority; teachers may only operate on assigned students.
-- -----------------------------------------------------------------------------
drop policy if exists attendance_write_staff on public.session_attendance;
create policy attendance_write_staff on public.session_attendance
  for all to authenticated
  using (
    public.is_supervisor()
    or (public.is_staff() and public.teaches_student(user_id))
  )
  with check (
    public.is_supervisor()
    or (public.is_staff() and public.teaches_student(user_id))
  );

drop policy if exists turns_write_staff on public.recitation_turns;
create policy turns_write_staff on public.recitation_turns
  for all to authenticated
  using (
    public.is_supervisor()
    or (public.is_staff() and public.teaches_student(student_id))
  )
  with check (
    public.is_supervisor()
    or (public.is_staff() and public.teaches_student(student_id))
  );

drop policy if exists evaluations_write_staff on public.evaluations;
create policy evaluations_write_staff on public.evaluations
  for all to authenticated
  using (
    public.is_supervisor()
    or (public.is_staff() and public.teaches_student(student_id))
  )
  with check (
    public.is_supervisor()
    or (public.is_staff() and public.teaches_student(student_id))
  );

drop policy if exists messages_insert on public.messages;
create policy messages_insert on public.messages
  for insert to authenticated
  with check (
    sender_id = auth.uid()
    and public.is_member()
    and (
      thread = 'halqa'
      or public.is_supervisor()
      or student_id = auth.uid()
      or (public.is_staff() and public.teaches_student(student_id))
    )
  );

drop policy if exists messages_delete_own on public.messages;
create policy messages_delete_own on public.messages
  for delete to authenticated
  using (sender_id = auth.uid() or public.is_supervisor());

drop policy if exists proposals_read on public.group_proposals;
create policy proposals_read on public.group_proposals
  for select to authenticated
  using (public.is_supervisor() or proposed_by = auth.uid());

drop policy if exists proposals_insert on public.group_proposals;
create policy proposals_insert on public.group_proposals
  for insert to authenticated
  with check (
    proposed_by = auth.uid()
    and status = 'pending'
    and (
      public.is_supervisor()
      or (public.is_staff() and public.teaches_student(student_id))
    )
  );

drop policy if exists proposals_decide on public.group_proposals;
create policy proposals_decide on public.group_proposals
  for update to authenticated
  using (public.is_supervisor())
  with check (public.is_supervisor());

-- -----------------------------------------------------------------------------
-- 6) Safe directory views: names stay available to active members, but a
--    teacher's directory is limited to assigned students and the member
--    directory (which includes level/progress/status) is never halqa-wide for a
--    student.  The owner remains hidden from non-owners.
-- -----------------------------------------------------------------------------
create or replace view public.profile_directory
with (security_barrier = true) as
select p.id, p.full_name, p.role
  from public.profiles p
 where p.id = auth.uid()
    or (
      public.is_member()
      and (public.is_owner() or not private.is_owner(p.id))
      and (
        public.is_supervisor()
        or public.teaches_student(p.id)
        or (
          not public.is_staff()
          and exists (
            select 1 from public.halqa_members m
             where m.user_id = p.id and m.status = 'active'
          )
        )
      )
    );

comment on view public.profile_directory is
  'آمنة للقراءة داخل الحلقة: الاسم والدور فقط، بلا هاتف، وتخفي المالكة عن غيرها.';

create or replace view public.halqa_member_directory
with (security_barrier = true) as
select m.halqa_id,
       m.user_id,
       p.full_name,
       m.role,
       m.level,
       m.level_progress,
       m.graduated,
       m.status,
       m.joined_at
  from public.halqa_members m
  join public.profiles p on p.id = m.user_id
 where m.user_id = auth.uid()
    or (
      public.is_member()
      and (public.is_supervisor() or public.teaches_student(m.user_id))
      and (public.is_owner() or not private.is_owner(m.user_id))
    );

comment on view public.halqa_member_directory is
  'قائمة الأعضاء للواجهات: الاسم والحالة والمستوى، بلا هاتف، ومقصورة على المسندات للمعلمة.';

revoke all on public.profile_directory from anon;
revoke all on public.halqa_member_directory from anon;
grant select on public.profile_directory, public.halqa_member_directory to authenticated;

-- -----------------------------------------------------------------------------
-- 7) RPCs: assignment administration.  Existing create_group() stays intact.
-- -----------------------------------------------------------------------------
create or replace function public.assign_member_group(p_user uuid, p_group uuid, p_level text)
returns void
language plpgsql security definer set search_path = public, pg_temp as $$
begin
    if not public.is_supervisor() then
        raise exception 'غير مصرح' using errcode = '42501';
    end if;
    if auth.uid() is not null and private.is_owner(p_user) and not public.is_owner() then
        raise exception 'لا يمكن تعديل مجموعة المالكة من دور أدنى' using errcode = '42501';
    end if;
    if p_level is null or p_level not in ('foundation', 'tajweed', 'hifz') then
        raise exception 'مستوى غير صالح' using errcode = '22023';
    end if;
    if not exists (
        select 1 from public.groups
         where id = p_group and halqa_id = public.main_halqa_id()
    ) then
        raise exception 'مجموعة غير موجودة' using errcode = '22023';
    end if;
    if not exists (
        select 1
          from public.halqa_members m
         where m.user_id = p_user
           and m.status = 'active'
           and m.role = 'student'
    ) then
        raise exception 'الطالبة غير موجودة أو غير مفعّلة' using errcode = '22023';
    end if;

    delete from public.member_groups
     where user_id = p_user and group_id = p_group;

    insert into public.member_groups (user_id, group_id, level)
    values (p_user, p_group, p_level)
    on conflict (user_id, group_id, level) do nothing;
end; $$;

create or replace function public.remove_member_group(p_user uuid, p_group uuid, p_level text)
returns void
language plpgsql security definer set search_path = public, pg_temp as $$
begin
    if not public.is_supervisor() then
        raise exception 'غير مصرح' using errcode = '42501';
    end if;
    if auth.uid() is not null and private.is_owner(p_user) and not public.is_owner() then
        raise exception 'لا يمكن تعديل مجموعة المالكة من دور أدنى' using errcode = '42501';
    end if;
    if p_level is null or p_level not in ('foundation', 'tajweed', 'hifz') then
        raise exception 'مستوى غير صالح' using errcode = '22023';
    end if;

    delete from public.member_groups
     where user_id = p_user and group_id = p_group and level = p_level;
end; $$;

create or replace function public.assign_teacher(
    p_teacher uuid,
    p_group uuid,
    p_level text
)
returns void
language plpgsql security definer set search_path = public, pg_temp as $$
begin
    if not public.is_supervisor() then
        raise exception 'غير مصرح' using errcode = '42501';
    end if;
    if auth.uid() is not null and private.is_owner(p_teacher) and not public.is_owner() then
        raise exception 'لا يمكن إسناد المالكة من دور أدنى' using errcode = '42501';
    end if;
    if p_level is null or p_level not in ('foundation', 'tajweed', 'hifz') then
        raise exception 'مستوى غير صالح' using errcode = '22023';
    end if;
    if not exists (
        select 1 from public.groups
         where id = p_group and halqa_id = public.main_halqa_id()
    ) then
        raise exception 'مجموعة غير موجودة' using errcode = '22023';
    end if;
    if not exists (
        select 1
          from public.profiles p
          join public.halqa_members m on m.user_id = p.id
         where p.id = p_teacher
           and p.role in ('teacher', 'supervisor')
           and m.role in ('teacher', 'supervisor')
           and m.status = 'active'
    ) then
        raise exception 'المعلمة غير موجودة أو غير مفعّلة' using errcode = '22023';
    end if;

    insert into public.teacher_assignments (teacher_id, group_id, level, assigned_by)
    values (p_teacher, p_group, p_level, auth.uid())
    on conflict (teacher_id, group_id, level) do update
      set assigned_by = excluded.assigned_by,
          assigned_at = now();
end; $$;

create or replace function public.remove_teacher_assignment(
    p_teacher uuid,
    p_group uuid,
    p_level text
)
returns void
language plpgsql security definer set search_path = public, pg_temp as $$
begin
    if not public.is_supervisor() then
        raise exception 'غير مصرح' using errcode = '42501';
    end if;
    if auth.uid() is not null and private.is_owner(p_teacher) and not public.is_owner() then
        raise exception 'لا يمكن تعديل إسناد المالكة من دور أدنى' using errcode = '42501';
    end if;
    if p_level is null or p_level not in ('foundation', 'tajweed', 'hifz') then
        raise exception 'مستوى غير صالح' using errcode = '22023';
    end if;

    delete from public.teacher_assignments
     where teacher_id = p_teacher and group_id = p_group and level = p_level;
end; $$;

revoke all on function public.assign_member_group(uuid, uuid, text) from public, anon;
revoke all on function public.remove_member_group(uuid, uuid, text) from public, anon;
revoke all on function public.assign_teacher(uuid, uuid, text) from public, anon;
revoke all on function public.remove_teacher_assignment(uuid, uuid, text) from public, anon;
grant execute on function public.assign_member_group(uuid, uuid, text),
                         public.remove_member_group(uuid, uuid, text),
                         public.assign_teacher(uuid, uuid, text),
                         public.remove_teacher_assignment(uuid, uuid, text)
  to authenticated;

-- -----------------------------------------------------------------------------
-- 8) Proposals: a teacher may only propose; the supervisor decision performs
--    the assignment.  Re-deciding a settled proposal is rejected.
-- -----------------------------------------------------------------------------
create or replace function public.propose_group(p_user uuid, p_group uuid, p_level text, p_note text default null)
returns uuid
language plpgsql security definer set search_path = public, pg_temp as $$
declare
    v_id uuid;
begin
    if not public.is_staff() then
        raise exception 'غير مصرح' using errcode = '42501';
    end if;
    if auth.uid() is not null and private.is_owner(p_user) and not public.is_owner() then
        raise exception 'لا يمكن اقتراح مجموعة للمالكة من دور أدنى' using errcode = '42501';
    end if;
    if p_level is null or p_level not in ('foundation', 'tajweed', 'hifz') then
        raise exception 'مستوى غير صالح' using errcode = '22023';
    end if;
    if p_group is null or not exists (
        select 1 from public.groups
         where id = p_group and halqa_id = public.main_halqa_id()
    ) then
        raise exception 'مجموعة غير موجودة' using errcode = '22023';
    end if;
    if not exists (
        select 1
          from public.halqa_members m
         where m.user_id = p_user
           and m.status = 'active'
           and m.role = 'student'
    ) then
        raise exception 'الطالبة غير موجودة أو غير مفعّلة' using errcode = '22023';
    end if;
    if not (public.is_supervisor() or public.teaches_student(p_user)) then
        raise exception 'اقتراح النقل/الترقية مسموح للمعلمة المسندة أو المشرفة فقط'
          using errcode = '42501';
    end if;

    insert into public.group_proposals (student_id, group_id, level, note, proposed_by)
    values (p_user, p_group, p_level, nullif(btrim(coalesce(p_note, '')), ''), auth.uid())
    returning id into v_id;
    return v_id;
end; $$;

create or replace function public.decide_proposal(p_id uuid, p_accept boolean)
returns void
language plpgsql security definer set search_path = public, pg_temp as $$
declare
    v_prop public.group_proposals;
begin
    if not public.is_supervisor() then
        raise exception 'غير مصرح' using errcode = '42501';
    end if;

    select * into v_prop
      from public.group_proposals
     where id = p_id
     for update;

    if v_prop.id is null then
        raise exception 'اقتراح غير موجود' using errcode = '22023';
    end if;
    if v_prop.status <> 'pending' then
        raise exception 'الاقتراح مُقرَّر مسبقا' using errcode = '22023';
    end if;

    if p_accept then
        if v_prop.group_id is null or v_prop.level is null then
            raise exception 'الاقتراح بلا مجموعة أو مستوى صالح' using errcode = '22023';
        end if;
        perform public.assign_member_group(v_prop.student_id, v_prop.group_id, v_prop.level);
    end if;

    update public.group_proposals
       set status = case when p_accept then 'accepted' else 'rejected' end
     where id = p_id;
end; $$;

revoke all on function public.propose_group(uuid, uuid, text, text) from public, anon;
revoke all on function public.decide_proposal(uuid, boolean) from public, anon;
grant execute on function public.propose_group(uuid, uuid, text, text) to authenticated;
grant execute on function public.decide_proposal(uuid, boolean) to authenticated;

-- -----------------------------------------------------------------------------
-- 9) Teacher classroom actions: enforce assigned-student scope inside the
--    SECURITY DEFINER functions, not only through RLS.
-- -----------------------------------------------------------------------------
create or replace function public.end_session(p_session uuid)
returns public.sessions
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_row public.sessions;
begin
  if not public.is_staff() then raise exception 'غير مصرح' using errcode = '42501'; end if;

  update public.sessions
     set status = 'ended', ended_at = now(), current_reader = null
   where id = p_session
  returning * into v_row;

  if v_row.id is null then raise exception 'الجلسة غير موجودة' using errcode = '22023'; end if;

  -- A teacher closes only the turns of students she is assigned to; a
  -- supervisor/owner closes the whole queue.  The session row itself is a
  -- halqa-wide object (sessions has no group_id in this phase).
  update public.recitation_turns
     set state = 'skipped', ended_at = now()
   where session_id = p_session and state in ('waiting', 'reading')
     and (public.is_supervisor() or public.teaches_student(student_id));

  return v_row;
end;
$$;

create or replace function public.give_turn(p_session uuid, p_student uuid)
returns public.recitation_turns
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_turn public.recitation_turns;
  v_position integer;
begin
  if not public.is_staff() then raise exception 'غير مصرح' using errcode = '42501'; end if;

  if not exists (
    select 1 from public.halqa_members
    where user_id = p_student and status = 'active' and role = 'student'
  ) then
    raise exception 'الطالبة ليست من الحلقة' using errcode = '22023';
  end if;

  if not (public.is_supervisor() or public.teaches_student(p_student)) then
    raise exception 'الطالبة غير مسندة لهذه المعلمة' using errcode = '42501';
  end if;

  update public.recitation_turns
     set state = 'done', ended_at = now()
   where session_id = p_session and state = 'reading'
     and (public.is_supervisor() or public.teaches_student(student_id));

  update public.recitation_turns
     set state = 'reading', started_at = now(), hand_raised = false
   where session_id = p_session and student_id = p_student
  returning * into v_turn;

  if v_turn.id is null then
    select coalesce(max(position), 0) + 1 into v_position
    from public.recitation_turns where session_id = p_session;

    insert into public.recitation_turns (session_id, student_id, position, state, started_at)
    values (p_session, p_student, v_position, 'reading', now())
    returning * into v_turn;
  end if;

  update public.sessions
     set current_reader = p_student
   where id = p_session
     and (
       public.is_supervisor()
       or current_reader is null
       or current_reader = p_student
       or public.teaches_student(current_reader)
     );
  return v_turn;
end;
$$;

create or replace function public.mark_attendance(
  p_session uuid,
  p_user uuid,
  p_status public.attendance_status
)
returns public.session_attendance
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_row public.session_attendance;
begin
  if not public.is_staff() then raise exception 'غير مصرح' using errcode = '42501'; end if;

  if not exists (
    select 1 from public.halqa_members
    where user_id = p_user and status = 'active'
  ) then
    raise exception 'العضوة ليست من الحلقة' using errcode = '22023';
  end if;

  if not (public.is_supervisor() or public.teaches_student(p_user)) then
    raise exception 'العضوة غير مسندة لهذه المعلمة' using errcode = '42501';
  end if;

  insert into public.session_attendance (session_id, user_id, status, marked_by)
  values (p_session, p_user, p_status, auth.uid())
  on conflict (session_id, user_id) do update
    set status = excluded.status, marked_by = auth.uid(), marked_at = now()
  returning * into v_row;

  return v_row;
end;
$$;

create or replace function public.save_evaluation(
  p_session uuid,
  p_student uuid,
  p_tajweed smallint,
  p_hifz smallint,
  p_note text default null
)
returns public.evaluations
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_row public.evaluations;
begin
  if not public.is_staff() then raise exception 'غير مصرح' using errcode = '42501'; end if;

  if not exists (
    select 1 from public.halqa_members
    where user_id = p_student and status = 'active' and role = 'student'
  ) then
    raise exception 'الطالبة ليست من الحلقة' using errcode = '22023';
  end if;

  if not (public.is_supervisor() or public.teaches_student(p_student)) then
    raise exception 'الطالبة غير مسندة لهذه المعلمة' using errcode = '42501';
  end if;

  insert into public.evaluations (session_id, student_id, teacher_id, tajweed, hifz, note)
  values (p_session, p_student, auth.uid(), p_tajweed, p_hifz, nullif(btrim(coalesce(p_note, '')), ''))
  on conflict (session_id, student_id, teacher_id) do update
    set tajweed = excluded.tajweed, hifz = excluded.hifz, note = excluded.note, updated_at = now()
  returning * into v_row;

  return v_row;
end;
$$;

revoke all on function public.end_session(uuid) from public, anon;
revoke all on function public.give_turn(uuid, uuid) from public, anon;
revoke all on function public.mark_attendance(uuid, uuid, public.attendance_status) from public, anon;
revoke all on function public.save_evaluation(uuid, uuid, smallint, smallint, text) from public, anon;
grant execute on function public.end_session(uuid),
                         public.give_turn(uuid, uuid),
                         public.mark_attendance(uuid, uuid, public.attendance_status),
                         public.save_evaluation(uuid, uuid, smallint, smallint, text)
  to authenticated;

-- -----------------------------------------------------------------------------
-- 10) Messaging: private threads are limited to the student herself, assigned
--     teachers, and supervisors.  Visibility is shared by the RPC and receipts.
-- -----------------------------------------------------------------------------
create or replace function public.can_see_message(p_message uuid)
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select exists (
    select 1
      from public.messages m
     where m.id = p_message
       and public.is_member()
       and (
         m.thread = 'halqa'
         or public.is_supervisor()
         or m.student_id = auth.uid()
         or (public.is_staff() and public.teaches_student(m.student_id))
       )
  );
$$;

create or replace function public.send_message(
  p_thread public.thread_kind,
  p_body text,
  p_student uuid default null,
  p_kind public.message_kind default 'text',
  p_duration integer default null
)
returns public.messages
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_row public.messages;
  v_halqa uuid;
begin
  if auth.uid() is null then
    raise exception 'يجب تسجيل الدخول' using errcode = '28000';
  end if;

  if not public.is_member() then
    raise exception 'غير مصرح' using errcode = '42501';
  end if;

  if p_thread = 'private' and p_student is null then
    raise exception 'الرسالة الخاصة تحتاج طالبة' using errcode = '22023';
  end if;

  if p_thread = 'private'
     and not (
       public.is_supervisor()
       or p_student = auth.uid()
       or (public.is_staff() and public.teaches_student(p_student))
     ) then
    raise exception 'غير مصرح' using errcode = '42501';
  end if;

  v_halqa := public.main_halqa_id();

  insert into public.messages (halqa_id, thread, student_id, sender_id, kind, body, duration_seconds)
  values (v_halqa, p_thread, case when p_thread = 'private' then p_student else null end,
          auth.uid(), p_kind, nullif(btrim(coalesce(p_body, '')), ''), p_duration)
  returning * into v_row;

  return v_row;
end;
$$;

revoke all on function public.can_see_message(uuid) from public, anon;
revoke all on function public.send_message(public.thread_kind, text, uuid, public.message_kind, integer) from public, anon;
grant execute on function public.can_see_message(uuid) to authenticated;
grant execute on function public.send_message(public.thread_kind, text, uuid, public.message_kind, integer) to authenticated;

notify pgrst, 'reload schema';

commit;
