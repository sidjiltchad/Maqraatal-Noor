-- =============================================================================
--  P0 authorization & privacy hardening.
--  Evidence: docs/AUDIT_REPORT.md section 5 (items 1-8) and Phase B.
--  Forward-only: this file does not edit any earlier migration.
--
--  What this closes:
--    * teacher -> supervisor escalation through invitations or direct role writes
--    * staff access for a profile whose membership is not active
--    * broad profile SELECT that exposed every member's phone
--    * arbitrary student UPDATEs on recitation_turns
--    * send_message from non-members and private messages for another student
--    * mark_thread_read receipts for messages the caller cannot see
--    * member-wide voice-notes bucket reads while no path contract exists
-- =============================================================================
begin;

-- -----------------------------------------------------------------------------
-- 1) One definition of "member"/"staff"/"supervisor", all based on an ACTIVE
--    halqa_members row. is_supervisor() already required this; is_staff() used
--    to trust profiles.role alone, which kept banned/left teachers staff.
-- -----------------------------------------------------------------------------
create or replace function public.is_member()
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select exists (
    select 1 from public.halqa_members
    where user_id = auth.uid() and status = 'active'
  );
$$;

create or replace function public.is_staff()
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select exists (
    select 1
      from public.profiles p
      join public.halqa_members m on m.user_id = p.id
     where p.id = auth.uid()
       and p.role in ('teacher', 'supervisor')
       and m.status = 'active'
  );
$$;

-- Kept identical in spirit to 20261124090000_groups_and_membership.sql so an
-- active supervisor continues to pass every admin path.
create or replace function public.is_supervisor()
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select exists (
    select 1
      from public.profiles p
      join public.halqa_members m on m.user_id = p.id
     where p.id = auth.uid()
       and p.role = 'supervisor'
       and m.status = 'active'
  );
$$;

-- -----------------------------------------------------------------------------
-- 2) Direct role writes are not a supported API. Roles change only through the
--    authorized SECURITY DEFINER paths (bootstrap_supervisor / redeem_invitation)
--    which opt in with a transaction-local flag. Superusers and service_role are
--    allowed so migrations and test fixtures can still seed data.
-- -----------------------------------------------------------------------------
create or replace function public.enforce_role_change_guard()
returns trigger
language plpgsql set search_path = public, pg_temp as $$
begin
  if new.role is distinct from old.role
     and coalesce(current_setting('app.authorized_role_change', true), 'off') <> 'on'
     and current_user not in ('postgres', 'supabase_admin', 'service_role')
     and not exists (
       select 1 from pg_roles where rolname = current_user and rolsuper
     )
  then
    raise exception 'تغيير الدور مسموح فقط عبر الدعوات أو الدوال الإدارية'
      using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists guard_profile_role on public.profiles;
create trigger guard_profile_role
  before update of role on public.profiles
  for each row execute function public.enforce_role_change_guard();

drop trigger if exists guard_member_role on public.halqa_members;
create trigger guard_member_role
  before update of role on public.halqa_members
  for each row execute function public.enforce_role_change_guard();

-- An invitation is the other grant path. A teacher may only mint student
-- invitations; only an active supervisor may mint teacher/supervisor ones.
-- This also blocks a direct PostgREST INSERT/UPDATE that bypasses create_invitation.
create or replace function public.enforce_invitation_role_guard()
returns trigger
language plpgsql set search_path = public, pg_temp as $$
begin
  if new.role <> 'student'
     and coalesce(current_setting('app.authorized_role_change', true), 'off') <> 'on'
     and current_user not in ('postgres', 'supabase_admin', 'service_role')
     and not exists (
       select 1 from pg_roles where rolname = current_user and rolsuper
     )
     and not public.is_supervisor()
  then
    raise exception 'إنشاء دعوة بهذا الدور مسموح للمشرفة فقط'
      using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists guard_invitation_role on public.invitations;
drop trigger if exists guard_invitation_role_insert on public.invitations;
create trigger guard_invitation_role_insert
  before insert on public.invitations
  for each row execute function public.enforce_invitation_role_guard();

drop trigger if exists guard_invitation_role_update on public.invitations;
create trigger guard_invitation_role_update
  before update of role on public.invitations
  for each row execute function public.enforce_invitation_role_guard();

-- -----------------------------------------------------------------------------
-- 3) Invitations: keep the RPC contract but only supervisors may grant
--    teacher/supervisor. Teachers may still invite students through the gated
--    RPC, which returns the created row to its caller.
--
--    Invitation codes are bearer tokens. The old invitations_staff policy let a
--    teacher SELECT a supervisor code created by a supervisor and redeem it, so
--    direct invitation reads/writes are limited to active supervisors. The
--    SECURITY DEFINER RPCs below still bypass RLS for the authorized flows.
-- -----------------------------------------------------------------------------
drop policy if exists invitations_staff on public.invitations;
drop policy if exists invitations_all_supervisor on public.invitations;
create policy invitations_all_supervisor on public.invitations
  for all to authenticated
  using (public.is_supervisor())
  with check (public.is_supervisor());

create or replace function public.create_invitation(
  p_role public.user_role default 'student',
  p_max_uses integer default 1,
  p_days integer default 7
)
returns public.invitations
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_row public.invitations;
begin
  if not public.is_staff() then
    raise exception 'غير مصرح' using errcode = '42501';
  end if;

  if p_role is null then
    raise exception 'الدور مطلوب' using errcode = '22023';
  end if;

  if p_role <> 'student' and not public.is_supervisor() then
    raise exception 'لا يمكن للمعلمة منح دور معلمة أو مشرفة' using errcode = '42501';
  end if;

  insert into public.invitations (code, role, max_uses, expires_at, created_by)
  values (
    upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8)),
    p_role,
    greatest(1, least(coalesce(p_max_uses, 1), 100)),
    now() + make_interval(days => greatest(1, least(coalesce(p_days, 7), 365))),
    auth.uid()
  )
  returning * into v_row;

  return v_row;
end;
$$;

grant execute on function public.create_invitation(public.user_role, integer, integer) to authenticated;

-- -----------------------------------------------------------------------------
-- 4) Authorized role-change paths opt in explicitly. Everything else is blocked
--    by the triggers above, including a teacher writing profiles.role directly.
-- -----------------------------------------------------------------------------
create or replace function public.bootstrap_supervisor()
returns public.user_role
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_halqa uuid;
begin
  if auth.uid() is null then
    raise exception 'يجب تسجيل الدخول' using errcode = '28000';
  end if;

  -- First unprivileged user only. Do not let an existing teacher bootstrap
  -- herself just because she is temporarily the only staff account.
  if exists (select 1 from public.profiles where role in ('teacher', 'supervisor')) then
    raise exception 'المقرأة مهيأة مسبقا' using errcode = '42501';
  end if;

  perform set_config('app.authorized_role_change', 'on', true);
  update public.profiles set role = 'supervisor' where id = auth.uid();
  v_halqa := public.main_halqa_id();

  insert into public.halqa_members (halqa_id, user_id, role, level)
  values (v_halqa, auth.uid(), 'supervisor', 'ijaza')
  on conflict (halqa_id, user_id) do update set role = 'supervisor', status = 'active';

  perform set_config('app.authorized_role_change', 'off', true);
  return 'supervisor';
end;
$$;

grant execute on function public.bootstrap_supervisor() to authenticated;

create or replace function public.redeem_invitation(p_code text)
returns public.halqa_members
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_inv public.invitations;
  v_halqa uuid;
  v_member public.halqa_members;
  v_status public.member_status;
begin
  if auth.uid() is null then
    raise exception 'يجب تسجيل الدخول' using errcode = '28000';
  end if;

  v_halqa := public.main_halqa_id();

  if exists (select 1 from public.halqa_members
              where halqa_id = v_halqa and user_id = auth.uid() and status = 'banned') then
    raise exception 'الحساب موقوف' using errcode = '42501';
  end if;

  select * into v_inv
  from public.invitations
  where code = upper(btrim(p_code))
    and (expires_at is null or expires_at > now())
    and uses < max_uses
  for update;

  if v_inv.id is null then
    raise exception 'الدعوة غير صالحة أو منتهية' using errcode = '22023';
  end if;

  -- A legacy teacher-minted teacher/supervisor invitation must not promote
  -- anyone. Only invitations issued by an active supervisor can grant a
  -- non-student role.
  if v_inv.role <> 'student'
     and v_inv.created_by is not null
     and not exists (
       select 1
         from public.profiles p
         join public.halqa_members m on m.user_id = p.id
        where p.id = v_inv.created_by
          and p.role = 'supervisor'
          and m.status = 'active'
     ) then
    raise exception 'هذه الدعوة لم تصدر من مشرفة' using errcode = '42501';
  end if;

  v_status := case when v_inv.role = 'student' then 'pending' else 'active' end;

  update public.invitations set uses = uses + 1 where id = v_inv.id;

  perform set_config('app.authorized_role_change', 'on', true);
  update public.profiles set role = v_inv.role where id = auth.uid();

  insert into public.halqa_members (halqa_id, user_id, role, level, status)
  values (v_halqa, auth.uid(), v_inv.role, 'foundation', v_status)
  on conflict (halqa_id, user_id) do update
    set role = excluded.role,
        -- لا نخفّض عضوة فعّالة إلى معلّقة عند إعادة استخدام كود
        status = case when public.halqa_members.status = 'active' then 'active' else excluded.status end
  returning * into v_member;

  perform set_config('app.authorized_role_change', 'off', true);
  return v_member;
end;
$$;

grant execute on function public.redeem_invitation(text) to authenticated;

-- -----------------------------------------------------------------------------
-- 5) Profile privacy. phone is readable only by the owner and an active
--    supervisor; active members read names through the safe directory views.
--    The views are barriers owned by the migration role, so RLS on profiles
--    cannot be used to reach phone through them.
-- -----------------------------------------------------------------------------
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles
  for select to authenticated
  using (id = auth.uid() or public.is_supervisor());

drop policy if exists profiles_update_staff on public.profiles;
create policy profiles_update_staff on public.profiles
  for update to authenticated
  using (public.is_supervisor())
  with check (public.is_supervisor());

create or replace view public.profile_directory
with (security_barrier = true) as
select p.id, p.full_name, p.role
  from public.profiles p
 where p.id = auth.uid()
    or (
      public.is_member()
      and exists (
        select 1 from public.halqa_members m
         where m.user_id = p.id and m.status = 'active'
      )
    );

comment on view public.profile_directory is
  'آمنة للقراءة العامة داخل الحلقة: الاسم والدور فقط، بلا هاتف.';

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
 where public.is_member() or m.user_id = auth.uid();

comment on view public.halqa_member_directory is
  'قائمة الأعضاء للواجهات: الاسم والحالة والمستوى، بلا هاتف.';

revoke all on public.profile_directory from anon;
revoke all on public.halqa_member_directory from anon;
grant select on public.profile_directory, public.halqa_member_directory to authenticated;

-- Membership rows are managed by the supervisor RPCs; teachers keep reading
-- them but can no longer rewrite another member's role directly.
drop policy if exists members_write_staff on public.halqa_members;
drop policy if exists members_write_supervisor on public.halqa_members;
create policy members_write_supervisor on public.halqa_members
  for all to authenticated
  using (public.is_supervisor())
  with check (public.is_supervisor());

-- -----------------------------------------------------------------------------
-- 6) recitation_turns: a student may change hand_raised on her own row only.
--    Everything else (position/state/owner/session/timestamps) is immutable for
--    students and remains available to staff RPCs.
-- -----------------------------------------------------------------------------
drop policy if exists turns_raise_own on public.recitation_turns;
create policy turns_raise_own on public.recitation_turns
  for update to authenticated
  using (student_id = auth.uid() and public.is_member())
  with check (student_id = auth.uid() and public.is_member());

create or replace function public.guard_turn_self_update()
returns trigger
language plpgsql set search_path = public, pg_temp as $$
begin
  if public.is_staff() then
    return new;
  end if;

  if auth.uid() is null then
    return new;
  end if;

  if old.student_id is distinct from auth.uid() then
    raise exception 'غير مصرح' using errcode = '42501';
  end if;

  if new.id is distinct from old.id
     or new.session_id is distinct from old.session_id
     or new.student_id is distinct from old.student_id
     or new.position is distinct from old.position
     or new.state is distinct from old.state
     or new.started_at is distinct from old.started_at
     or new.ended_at is distinct from old.ended_at
     or new.created_at is distinct from old.created_at then
    raise exception 'يمكن للطالبة رفع يدها أو إنزالها فقط' using errcode = '42501';
  end if;

  return new;
end;
$$;

drop trigger if exists guard_turn_self_update on public.recitation_turns;
create trigger guard_turn_self_update
  before update on public.recitation_turns
  for each row execute function public.guard_turn_self_update();

-- raise_hand is a SECURITY DEFINER function, so it must enforce membership
-- itself rather than relying on the RLS SELECT policy.
create or replace function public.raise_hand(p_session uuid)
returns public.recitation_turns
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_turn public.recitation_turns;
  v_position integer;
begin
  if auth.uid() is null then raise exception 'يجب تسجيل الدخول' using errcode = '28000'; end if;
  if not public.is_member() then raise exception 'غير مصرح' using errcode = '42501'; end if;

  update public.recitation_turns
     set hand_raised = true
   where session_id = p_session and student_id = auth.uid() and state = 'waiting'
  returning * into v_turn;

  if v_turn.id is null then
    select coalesce(max(position), 0) + 1 into v_position
    from public.recitation_turns where session_id = p_session;

    insert into public.recitation_turns (session_id, student_id, position, state, hand_raised)
    values (p_session, auth.uid(), v_position, 'waiting', true)
    on conflict (session_id, student_id) do update set hand_raised = true
    returning * into v_turn;
  end if;

  return v_turn;
end;
$$;

grant execute on function public.raise_hand(uuid) to authenticated;

-- -----------------------------------------------------------------------------
-- 7) Messaging: halqa messages require active membership; private messages keep
--    the existing owner rule (staff or the student herself). Receipts are only
--    written for messages the caller can actually see, through the RPC or a
--    direct INSERT.
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
         or public.is_staff()
         or m.student_id = auth.uid()
       )
  );
$$;

grant execute on function public.can_see_message(uuid) to authenticated;

drop policy if exists receipts_write_own on public.message_receipts;
create policy receipts_write_own on public.message_receipts
  for insert to authenticated
  with check (
    user_id = auth.uid()
    and public.can_see_message(message_id)
  );

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

  if p_thread = 'private' and not (public.is_staff() or p_student = auth.uid()) then
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

grant execute on function public.send_message(public.thread_kind, text, uuid, public.message_kind, integer) to authenticated;

create or replace function public.mark_thread_read(p_message_ids uuid[])
returns integer
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_count integer;
begin
  if auth.uid() is null then
    raise exception 'يجب تسجيل الدخول' using errcode = '28000';
  end if;

  if not public.is_member() then
    raise exception 'غير مصرح' using errcode = '42501';
  end if;

  if p_message_ids is null or cardinality(p_message_ids) = 0 then
    return 0;
  end if;

  -- Reject the whole batch if any requested id is invisible or does not exist.
  if exists (
    select 1
      from unnest(p_message_ids) as requested(id)
     where requested.id is null
        or not public.can_see_message(requested.id)
  ) then
    raise exception 'رسالة غير مرئية' using errcode = '42501';
  end if;

  insert into public.message_receipts (message_id, user_id)
  select distinct requested.id, auth.uid()
    from unnest(p_message_ids) as requested(id)
   where public.can_see_message(requested.id)
  on conflict (message_id, user_id) do nothing;

  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

grant execute on function public.mark_thread_read(uuid[]) to authenticated;

-- -----------------------------------------------------------------------------
-- 8) Voice notes. There is still no upload/path contract (message id, thread,
--    halqa, owner directory) in the client, so recipient-scoped reads cannot be
--    expressed safely. Until that contract exists, only the uploader and active
--    staff can read; only the uploader can write. This does not add upload code.
-- -----------------------------------------------------------------------------
drop policy if exists voice_notes_read on storage.objects;
create policy voice_notes_read on storage.objects
  for select to authenticated
  using (
    bucket_id = 'voice-notes'
    and public.is_member()
    and (owner = auth.uid() or public.is_staff())
  );

drop policy if exists voice_notes_write on storage.objects;
create policy voice_notes_write on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'voice-notes'
    and public.is_member()
    and owner = auth.uid()
  );

-- Make the new safe directory views visible to PostgREST after the migration
-- commits, avoiding a stale schema-cache error on the Android client.
notify pgrst, 'reload schema';

commit;
