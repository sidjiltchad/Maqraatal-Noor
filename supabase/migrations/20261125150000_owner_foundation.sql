-- =============================================================================
--  Owner foundation: protected singleton, server-only provisioning, and the
--  owner invariant across profiles, membership, directories, invitations and
--  supervisor administration.
--
--  Version 20261125150000 replaces the pre-acceptance 20261126120000 draft.
--
--  This is a forward-only migration.  It does not add an "owner" value to the
--  public user_role enum: the owner is a protected singleton user referenced by
--  app_owner.  public.is_owner() is the no-argument self-check; the target-ID
--  predicate is private.is_owner(uuid) and is for internal RLS use only.
--
--  No account is created or provisioned here.  provision_app_owner(uuid) is
--  granted only to service_role and is never invoked by this migration.
-- =============================================================================
begin;

-- -----------------------------------------------------------------------------
-- 1) Protected singleton.  Clients have no table privilege at all; the helper
--    below is SECURITY DEFINER and is the only read path for RLS policies.
-- -----------------------------------------------------------------------------
create table if not exists public.app_owner (
  singleton  boolean primary key default true check (singleton),
  user_id    uuid not null unique references public.profiles (id) on delete restrict,
  created_at timestamptz not null default now()
);

comment on table public.app_owner is
  'المالكة الوحيدة المحمية. لا يصل إليها العملاء؛ التهيئة عبر إجراء خادمي فقط.';

alter table public.app_owner enable row level security;
revoke all on table public.app_owner from public, anon, authenticated, service_role;
grant select on table public.app_owner to service_role;

-- service_role is the server process that calls provision_app_owner(); it needs
-- schema USAGE, but no table write privilege.  Supabase already grants this;
-- the local harness gets the same guarantee here.
grant usage on schema public to service_role;

-- -----------------------------------------------------------------------------
-- 2) Minimal append-only authorization audit.  Only an authorization event and
--    a subject UUID are stored: no credentials, names, phones or free text.
-- -----------------------------------------------------------------------------
create table if not exists public.authorization_audit (
  id         bigint generated always as identity primary key,
  event      text not null check (event in ('owner_provisioned')),
  subject_id uuid not null,
  created_at timestamptz not null default now()
);

comment on table public.authorization_audit is
  'سجل تدقيق أحداث التفويض فقط؛ إلحاقي ولا يحتوي اعتمادات أو بيانات حساسة.';

alter table public.authorization_audit enable row level security;
revoke all on table public.authorization_audit from public, anon, authenticated, service_role;
revoke truncate on table public.authorization_audit from public, anon, authenticated, service_role;
grant select on table public.authorization_audit to service_role;

create or replace function public.deny_authorization_audit_mutation()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  raise exception 'سجل التدقيق إلحاقي فقط' using errcode = '42501';
end;
$$;

drop trigger if exists deny_authorization_audit_mutation on public.authorization_audit;
create trigger deny_authorization_audit_mutation
  before update or delete on public.authorization_audit
  for each row execute function public.deny_authorization_audit_mutation();

-- -----------------------------------------------------------------------------
-- 3) Owner markers.
--
--    public.is_owner() takes no argument, so the only question a client can ask
--    is "is the current auth.uid() the owner?".  The target-ID predicate that
--    RLS needs to evaluate individual rows lives in private.is_owner(uuid).
--
--    SECURITY: the private schema is internal.  It must NOT be added to
--    PostgREST's exposed schemas (Supabase's `db-schemas` / PGRST_DB_SCHEMAS
--    setting or the dashboard "Exposed schemas" list).  Exposing it would turn
--    private.is_owner(uuid) back into the arbitrary-UUID RPC oracle.
-- -----------------------------------------------------------------------------
create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated, service_role;

create or replace function private.is_owner(p_user uuid)
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select p_user is not null
     and exists (select 1 from public.app_owner o where o.user_id = p_user);
$$;

comment on function private.is_owner(uuid) is
  'صحيحة فقط عندما يكون المعرف المعطى هو المالكة المحمية؛ للاستخدام الداخلي في RLS فقط.';

revoke all on function private.is_owner(uuid) from public, anon;
grant execute on function private.is_owner(uuid) to authenticated, service_role;

-- Remove the old exposed target-ID overload before recreating the safe name.
-- Keeping it, even without a grant to authenticated, leaves a misleading
-- public surface.
drop function if exists public.is_owner(uuid);

-- The only PostgREST-reachable helper: no target argument, current caller only.
create or replace function public.is_owner()
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select private.is_owner(auth.uid());
$$;

comment on function public.is_owner() is
  'صحيحة فقط لحساب المالكة الحالي (auth.uid())؛ لا تقبل معرفا خارجيا ولا تكشف بيانات الملف.';

revoke all on function public.is_owner() from public, anon;
grant execute on function public.is_owner() to authenticated, service_role;

-- Owner inherits member/staff/supervisor capability, without weakening the
-- existing active-membership and role checks for everyone else.
create or replace function public.is_member()
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select public.is_owner()
      or exists (
           select 1 from public.halqa_members
            where user_id = auth.uid() and status = 'active'
         );
$$;

create or replace function public.is_staff()
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select public.is_owner()
      or exists (
           select 1
             from public.profiles p
             join public.halqa_members m on m.user_id = p.id
            where p.id = auth.uid()
              and p.role in ('teacher', 'supervisor')
              and m.status = 'active'
         );
$$;

create or replace function public.is_supervisor()
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select public.is_owner()
      or exists (
           select 1
             from public.profiles p
             join public.halqa_members m on m.user_id = p.id
            where p.id = auth.uid()
              and p.role = 'supervisor'
              and m.status = 'active'
         );
$$;

-- -----------------------------------------------------------------------------
-- 4) Retire the self-service bootstrap.  The function is made inert and its
--    EXECUTE privilege is removed from PUBLIC/anon/authenticated, so the old
--    client-facing promotion path cannot be used even if it is still named.
-- -----------------------------------------------------------------------------
create or replace function public.bootstrap_supervisor()
returns public.user_role
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
begin
  raise exception 'التهيئة الذاتية أُلغيت؛ تُهيأ المالكة عبر إجراء خادمي فقط'
    using errcode = '42501';
end;
$$;

revoke all on function public.bootstrap_supervisor() from public, anon, authenticated;

comment on function public.bootstrap_supervisor() is
  'متوقفة: لا تنفيذ لأي دور عميل ولا ترقية ذاتية؛ المالكة تُهيأ عبر provision_app_owner.';

-- -----------------------------------------------------------------------------
-- 5) The only provisioning path.  It accepts a profile UUID, never an email or
--    password, and only a service_role/postgres caller can reach it.
-- -----------------------------------------------------------------------------
create or replace function public.provision_app_owner(p_user uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_caller   text;
  v_existing uuid;
  v_halqa    uuid;
begin
  -- Inside a SECURITY DEFINER function current_user is the function owner, so
  -- use the session role/JWT role to identify the caller.
  v_caller := coalesce(
    nullif(current_setting('request.jwt.claim.role', true), ''),
    nullif(current_setting('role', true), '')
  );
  if v_caller is null or v_caller = 'none' then
    v_caller := session_user;
  end if;

  if v_caller not in ('service_role', 'postgres', 'supabase_admin')
     and not exists (
       select 1 from pg_roles where rolname::text = v_caller and rolsuper
     )
  then
    raise exception 'تهيئة المالكة مسموحة للخادم فقط' using errcode = '42501';
  end if;

  if p_user is null then
    raise exception 'معرف المستخدمة مطلوب' using errcode = '22023';
  end if;

  if not exists (select 1 from public.profiles where id = p_user) then
    raise exception 'الملف الشخصي غير موجود' using errcode = '22023';
  end if;

  -- Serialize concurrent provisioning attempts on the singleton.
  perform pg_advisory_xact_lock(hashtext('public.app_owner')::bigint);

  select user_id into v_existing from public.app_owner where singleton;
  if v_existing is not null and v_existing <> p_user then
    raise exception 'المالكة مهيأة مسبقا ولا يمكن استبدالها' using errcode = '42501';
  end if;

  v_halqa := public.main_halqa_id();
  if v_halqa is null then
    raise exception 'الحلقة غير مهيأة' using errcode = '22023';
  end if;

  perform set_config('app.owner_provisioning', 'on', true);
  perform set_config('app.authorized_role_change', 'on', true);

  update public.profiles set role = 'supervisor' where id = p_user;

  insert into public.app_owner (singleton, user_id)
  values (true, p_user)
  on conflict (singleton) do nothing;

  insert into public.halqa_members (halqa_id, user_id, role, level, status)
  values (v_halqa, p_user, 'supervisor', 'ijaza', 'active')
  on conflict (halqa_id, user_id) do update
    set role = 'supervisor', level = 'ijaza', status = 'active';

  insert into public.authorization_audit (event, subject_id)
  values ('owner_provisioned', p_user);

  perform set_config('app.owner_provisioning', 'off', true);
  perform set_config('app.authorized_role_change', 'off', true);
end;
$$;

comment on function public.provision_app_owner(uuid) is
  'تهيئة المالكة من الخادم فقط: تضمن العضوية النشطة وتسجل حدث تدقيق بلا اعتمادات.';

revoke all on function public.provision_app_owner(uuid) from public, anon, authenticated;
grant execute on function public.provision_app_owner(uuid) to service_role;

-- -----------------------------------------------------------------------------
-- 6) Direct role/invitation writes.  A supervisor may still create teacher and
--    student invitations, but only the owner may mint a supervisor invitation.
-- -----------------------------------------------------------------------------
create or replace function public.enforce_invitation_role_guard()
returns trigger
language plpgsql set search_path = public, pg_temp as $$
begin
  if new.role = 'supervisor'
     and coalesce(current_setting('app.authorized_role_change', true), 'off') <> 'on'
     and current_user not in ('postgres', 'supabase_admin', 'service_role')
     and not exists (
       select 1 from pg_roles where rolname = current_user and rolsuper
     )
     and not public.is_owner()
  then
    raise exception 'تعيين مشرفة عامة مسموح للمالكة فقط'
      using errcode = '42501';
  end if;

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

-- Owner-issued invitations are bearer codes: do not expose them to non-owner
-- supervisors.  Student/teacher invitations created by supervisors keep the
-- existing staff visibility.
drop policy if exists invitations_all_supervisor on public.invitations;
create policy invitations_all_supervisor on public.invitations
  for all to authenticated
  using (
    public.is_supervisor()
    and (
      public.is_owner()
      or (role <> 'supervisor' and not private.is_owner(created_by))
    )
  )
  with check (
    public.is_supervisor()
    and (
      public.is_owner()
      or (role <> 'supervisor' and not private.is_owner(created_by))
    )
  );

-- -----------------------------------------------------------------------------
-- 7) Owner profile/membership protection.  RLS already hides the owner from
--    non-owner staff; these triggers also protect SECURITY DEFINER paths and
--    direct table writes.  They fire for any authenticated caller, including
--    one inside a definer RPC, and allow only the owner or a server process.
-- -----------------------------------------------------------------------------
create or replace function public.guard_owner_profile()
returns trigger
language plpgsql set search_path = public, pg_temp as $$
begin
  if tg_op = 'DELETE' then
    if private.is_owner(old.id)
       and coalesce(current_setting('app.owner_provisioning', true), 'off') <> 'on'
       and auth.uid() is not null
       and not public.is_owner()
    then
      raise exception 'لا يمكن حذف حساب المالكة أو تعديله من دور أدنى'
        using errcode = '42501';
    end if;
    return old;
  end if;

  if (private.is_owner(old.id) or private.is_owner(new.id))
     and coalesce(current_setting('app.owner_provisioning', true), 'off') <> 'on'
     and auth.uid() is not null
     and not public.is_owner()
  then
    raise exception 'لا يمكن تعديل حساب المالكة من دور أدنى'
      using errcode = '42501';
  end if;

  -- Only the owner may demote another supervisor's profile role.  The
  -- membership trigger covers the membership row; this covers profiles.role.
  if old.role = 'supervisor'
     and new.role is distinct from old.role
     and old.id <> auth.uid()
     and auth.uid() is not null
     and not public.is_owner()
     and coalesce(current_setting('app.owner_provisioning', true), 'off') <> 'on'
  then
    raise exception 'تعديل دور مشرفة عامة مسموح للمالكة فقط'
      using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists guard_owner_profile on public.profiles;
create trigger guard_owner_profile
  before update or delete on public.profiles
  for each row execute function public.guard_owner_profile();

create or replace function public.guard_owner_membership()
returns trigger
language plpgsql set search_path = public, pg_temp as $$
declare
  v_caller uuid := auth.uid();
  v_owner  boolean := public.is_owner();
begin
  if coalesce(current_setting('app.owner_provisioning', true), 'off') = 'on'
     or coalesce(current_setting('app.authorized_supervisor_change', true), 'off') = 'on'
  then
    if tg_op = 'DELETE' then return old; else return new; end if;
  end if;

  if v_caller is null then
    if tg_op = 'DELETE' then return old; else return new; end if;
  end if;

  if tg_op = 'DELETE' then
    if private.is_owner(old.user_id) and not v_owner then
      raise exception 'لا يمكن حذف عضوية المالكة من دور أدنى' using errcode = '42501';
    end if;
    if old.role = 'supervisor' and old.user_id <> v_caller and not v_owner then
      raise exception 'إزالة مشرفة عامة مسموحة للمالكة فقط' using errcode = '42501';
    end if;
    return old;
  end if;

  if tg_op = 'INSERT' then
    if private.is_owner(new.user_id) and not v_owner then
      raise exception 'لا يمكن إنشاء عضوية للمالكة من دور أدنى' using errcode = '42501';
    end if;
    if new.role = 'supervisor' and not v_owner then
      raise exception 'تعيين مشرفة عامة مسموح للمالكة فقط' using errcode = '42501';
    end if;
    return new;
  end if;

  -- UPDATE
  if (private.is_owner(old.user_id) or private.is_owner(new.user_id)) and not v_owner then
    raise exception 'لا يمكن تعديل عضوية المالكة من دور أدنى' using errcode = '42501';
  end if;

  if new.role = 'supervisor' and old.role is distinct from 'supervisor' and not v_owner then
    raise exception 'تعيين مشرفة عامة مسموح للمالكة فقط' using errcode = '42501';
  end if;

  if (old.role = 'supervisor' or new.role = 'supervisor')
     and old.user_id <> v_caller
     and not v_owner
     and (
       new.role is distinct from old.role
       or new.status is distinct from old.status
     )
  then
    raise exception 'تعديل أو إزالة مشرفة عامة مسموح للمالكة فقط' using errcode = '42501';
  end if;

  return new;
end;
$$;

drop trigger if exists guard_owner_membership on public.halqa_members;
create trigger guard_owner_membership
  before insert or update or delete on public.halqa_members
  for each row execute function public.guard_owner_membership();

-- -----------------------------------------------------------------------------
-- 8) Invitations: keep the existing student/teacher flows, gate supervisor
--    appointment to the owner, and refuse legacy supervisor-issued supervisor
--    invitations at redemption time.
-- -----------------------------------------------------------------------------
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

  if p_role = 'supervisor' and not public.is_owner() then
    raise exception 'تعيين مشرفة عامة مسموح للمالكة فقط' using errcode = '42501';
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

create or replace function public.redeem_invitation(p_code text)
returns public.halqa_members
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_inv    public.invitations;
  v_halqa  uuid;
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

  -- A legacy supervisor-minted teacher/supervisor invitation must not promote
  -- anyone.  A supervisor invitation is valid only when the current owner
  -- issued it.
  if v_inv.role = 'supervisor' and not private.is_owner(v_inv.created_by) then
    raise exception 'دعوة المشرفة العامة يجب أن تصدر من المالكة' using errcode = '42501';
  end if;

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
  if v_inv.role = 'supervisor' then
    perform set_config('app.authorized_supervisor_change', 'on', true);
  end if;

  update public.profiles set role = v_inv.role where id = auth.uid();

  insert into public.halqa_members (halqa_id, user_id, role, level, status)
  values (v_halqa, auth.uid(), v_inv.role, 'foundation', v_status)
  on conflict (halqa_id, user_id) do update
    set role = excluded.role,
        status = case when public.halqa_members.status = 'active' then 'active' else excluded.status end
  returning * into v_member;

  if v_inv.role = 'supervisor' then
    perform set_config('app.authorized_supervisor_change', 'off', true);
  end if;
  perform set_config('app.authorized_role_change', 'off', true);
  return v_member;
end;
$$;

-- -----------------------------------------------------------------------------
-- 9) Supervisor administration RPCs: the owner can still manage everyone, but
--    a non-owner supervisor cannot change another supervisor's status or the
--    owner's record.  The membership trigger is the final backstop.
-- -----------------------------------------------------------------------------
create or replace function public.accept_member(p_user uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not public.is_supervisor() then raise exception 'غير مصرح' using errcode = '42501'; end if;
    if auth.uid() is not null and not public.is_owner() then
        if private.is_owner(p_user) then
            raise exception 'لا يمكن تعديل حساب المالكة من دور أدنى' using errcode = '42501';
        end if;
        if p_user <> auth.uid()
           and exists (select 1 from public.profiles where id = p_user and role = 'supervisor') then
            raise exception 'تعديل مشرفة عامة مسموح للمالكة فقط' using errcode = '42501';
        end if;
    end if;
    update public.halqa_members set status = 'active' where user_id = p_user;
end; $$;

create or replace function public.reject_member(p_user uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not public.is_supervisor() then raise exception 'غير مصرح' using errcode = '42501'; end if;
    if auth.uid() is not null and not public.is_owner() then
        if private.is_owner(p_user) then
            raise exception 'لا يمكن حذف حساب المالكة من دور أدنى' using errcode = '42501';
        end if;
        if p_user <> auth.uid()
           and exists (select 1 from public.profiles where id = p_user and role = 'supervisor') then
            raise exception 'إزالة مشرفة عامة مسموحة للمالكة فقط' using errcode = '42501';
        end if;
    end if;
    delete from public.halqa_members where user_id = p_user and status = 'pending';
end; $$;

create or replace function public.set_member_status(p_user uuid, p_status text)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not public.is_supervisor() then raise exception 'غير مصرح' using errcode = '42501'; end if;
    if p_status not in ('active', 'banned', 'left') then raise exception 'حالة غير صالحة'; end if;
    if auth.uid() is not null and not public.is_owner() then
        if private.is_owner(p_user) then
            raise exception 'لا يمكن تعديل حالة المالكة من دور أدنى' using errcode = '42501';
        end if;
        if p_user <> auth.uid()
           and exists (select 1 from public.profiles where id = p_user and role = 'supervisor') then
            raise exception 'تعديل مشرفة عامة مسموح للمالكة فقط' using errcode = '42501';
        end if;
    end if;
    update public.halqa_members set status = p_status where user_id = p_user;
end; $$;

create or replace function public.assign_member_group(p_user uuid, p_group uuid, p_level text)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not public.is_supervisor() then raise exception 'غير مصرح' using errcode = '42501'; end if;
    if auth.uid() is not null and private.is_owner(p_user) and not public.is_owner() then
        raise exception 'لا يمكن تعديل مجموعة المالكة من دور أدنى' using errcode = '42501';
    end if;
    if p_level not in ('foundation', 'tajweed', 'hifz') then raise exception 'مستوى غير صالح'; end if;
    if not exists (select 1 from public.groups where id = p_group) then raise exception 'مجموعة غير موجودة'; end if;
    delete from public.member_groups where user_id = p_user and group_id = p_group;
    insert into public.member_groups (user_id, group_id, level)
    values (p_user, p_group, p_level)
    on conflict (user_id, group_id, level) do nothing;
end; $$;

create or replace function public.propose_group(p_user uuid, p_group uuid, p_level text, p_note text default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_id uuid;
begin
    if not public.is_staff() then raise exception 'غير مصرح' using errcode = '42501'; end if;
    if auth.uid() is not null and private.is_owner(p_user) and not public.is_owner() then
        raise exception 'لا يمكن اقتراح مجموعة للمالكة من دور أدنى' using errcode = '42501';
    end if;
    if p_level not in ('foundation', 'tajweed', 'hifz') then raise exception 'مستوى غير صالح'; end if;
    insert into public.group_proposals (student_id, group_id, level, note, proposed_by)
    values (p_user, p_group, p_level, p_note, auth.uid())
    returning id into v_id;
    return v_id;
end; $$;

-- -----------------------------------------------------------------------------
-- 10) RLS: hide the owner from every non-owner profile/membership path, but
--     let the owner see her own account and exercise inherited staff access.
-- -----------------------------------------------------------------------------
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles
  for select to authenticated
  using (
    id = auth.uid()
    or (public.is_supervisor() and not private.is_owner(id))
  );

drop policy if exists profiles_update_staff on public.profiles;
create policy profiles_update_staff on public.profiles
  for update to authenticated
  using (public.is_supervisor() and not private.is_owner(id))
  with check (public.is_supervisor() and not private.is_owner(id));

drop policy if exists members_select on public.halqa_members;
create policy members_select on public.halqa_members
  for select to authenticated
  using (
    user_id = auth.uid()
    or (
      public.is_member()
      and (public.is_owner() or not private.is_owner(user_id))
    )
  );

drop policy if exists members_write_supervisor on public.halqa_members;
create policy members_write_supervisor on public.halqa_members
  for all to authenticated
  using (
    public.is_supervisor()
    and (public.is_owner() or not private.is_owner(user_id))
  )
  with check (
    public.is_supervisor()
    and (public.is_owner() or not private.is_owner(user_id))
  );

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
      and (public.is_owner() or not private.is_owner(p.id))
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
      and (public.is_owner() or not private.is_owner(m.user_id))
    );

comment on view public.halqa_member_directory is
  'قائمة الأعضاء للواجهات والدردشة: الاسم والحالة فقط، وتخفي المالكة عن غيرها.';

revoke all on public.profile_directory from anon;
revoke all on public.halqa_member_directory from anon;
grant select on public.profile_directory, public.halqa_member_directory to authenticated;

notify pgrst, 'reload schema';

commit;
