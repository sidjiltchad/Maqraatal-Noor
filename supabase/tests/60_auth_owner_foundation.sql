-- =============================================================================
--  Owner foundation acceptance tests.
--  Run only on a disposable local PostgreSQL after every migration.
--  Fixtures are synthetic local UUIDs; no real account or credential is used.
--  Success prints: OWNER FOUNDATION TESTS PASSED
--
--  The first block is also the regression check for the old bootstrap path:
--  on the pre-owner migration chain it must fail because an ordinary
--  authenticated user can promote herself with bootstrap_supervisor().
--
--  Section 1 also pins the helper privilege surface: the old public
--  is_owner(uuid) oracle must not resolve, while public.is_owner() remains the
--  no-argument self-check and the private target helper stays RLS-internal.
-- =============================================================================
\set ON_ERROR_STOP on
set client_min_messages = warning;

-- -----------------------------------------------------------------------------
-- 0) Minimal fixtures for the RED/GREEN bootstrap regression.  No staff account
--    exists yet on a fresh database, so the old function would promote the
--    caller here.
-- -----------------------------------------------------------------------------
select set_config('app.authorized_role_change', 'off', false);
select set_config('request.jwt.claim.sub', '', false);

insert into auth.users (id, email, raw_user_meta_data) values
  ('c0000000-0000-0000-0000-000000000001', 'owner-candidate@test.local',
   '{"full_name":"مرشحة المالكة","role":"supervisor","is_owner":true}'),
  ('c0000000-0000-0000-0000-000000000005', 'student-foundation@test.local',
   '{"full_name":"طالبة الاختبار"}')
on conflict (id) do nothing;

select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000005', false);
set role authenticated;

do $$
begin
  if (select role from public.profiles
       where id = 'c0000000-0000-0000-0000-000000000001') <> 'student' then
    raise exception 'FAIL: signup metadata created a non-student profile';
  end if;

  -- Pre-owner RED: this call succeeds and promotes the caller.  After the
  -- forward migration it is denied before the function body runs.
  begin
    perform public.bootstrap_supervisor();
    raise exception 'FAIL: authenticated non-staff user executed bootstrap_supervisor';
  exception when insufficient_privilege then null;
  end;

  if (select role from public.profiles where id = auth.uid()) <> 'student' then
    raise exception 'FAIL: bootstrap_supervisor promoted the caller';
  end if;

  if public.is_owner() then
    raise exception 'FAIL: an ordinary student resolved as owner';
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 1) Function privileges: only service_role may provision; the old bootstrap
--    and the provisioning RPC are closed to anon/authenticated.
-- -----------------------------------------------------------------------------
do $$
begin
  if has_function_privilege('authenticated', 'public.bootstrap_supervisor()', 'EXECUTE') then
    raise exception 'FAIL: authenticated can still execute bootstrap_supervisor()';
  end if;
  if has_function_privilege('anon', 'public.bootstrap_supervisor()', 'EXECUTE') then
    raise exception 'FAIL: anon can still execute bootstrap_supervisor()';
  end if;
  if has_function_privilege('authenticated', 'public.provision_app_owner(uuid)', 'EXECUTE') then
    raise exception 'FAIL: authenticated can execute provision_app_owner()';
  end if;
  if has_function_privilege('anon', 'public.provision_app_owner(uuid)', 'EXECUTE') then
    raise exception 'FAIL: anon can execute provision_app_owner()';
  end if;
  if not has_function_privilege('service_role', 'public.provision_app_owner(uuid)', 'EXECUTE') then
    raise exception 'FAIL: service_role cannot execute provision_app_owner()';
  end if;

  -- SECURITY REGRESSION: the old public target-ID overload was an RPC oracle
  -- for probing arbitrary UUIDs.  It must not resolve in the exposed schema.
  -- authenticated may only ask the no-argument question about auth.uid().
  if to_regprocedure('public.is_owner(uuid)') is not null then
    raise exception 'FAIL: public.is_owner(uuid) still resolves';
  end if;
  if to_regprocedure('public.is_owner()') is null then
    raise exception 'FAIL: public.is_owner() self-check is missing';
  end if;
  if not has_function_privilege('authenticated', 'public.is_owner()', 'EXECUTE') then
    raise exception 'FAIL: authenticated cannot execute the self-check helper';
  end if;
  if to_regprocedure('private.is_owner(uuid)') is null then
    raise exception 'FAIL: private target-ID helper is missing';
  end if;
  if not has_function_privilege('authenticated', 'private.is_owner(uuid)', 'EXECUTE') then
    raise exception 'FAIL: authenticated lacks the RLS target-ID privilege';
  end if;
  if has_function_privilege('anon', 'private.is_owner(uuid)', 'EXECUTE') then
    raise exception 'FAIL: anon can execute the private target-ID helper';
  end if;
  if not has_schema_privilege('authenticated', 'private', 'USAGE') then
    raise exception 'FAIL: authenticated lacks USAGE on the private schema';
  end if;
  if has_schema_privilege('anon', 'private', 'USAGE') then
    raise exception 'FAIL: anon has USAGE on the private schema';
  end if;
end $$;

select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000005', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
do $$
begin
  begin
    perform public.provision_app_owner('c0000000-0000-0000-0000-000000000001');
    raise exception 'FAIL: authenticated user called provision_app_owner()';
  exception when insufficient_privilege then null;
  end;

  -- Behavior check, not a source-text check: after the fix this statement
  -- cannot resolve a one-argument public helper at all.
  begin
    execute 'select public.is_owner(''c0000000-0000-0000-0000-000000000001''::uuid)';
    raise exception 'FAIL: authenticated resolved public.is_owner(uuid)';
  exception when undefined_function then null;
  end;
end $$;
reset role;
select set_config('request.jwt.claim.role', '', false);

select set_config('request.jwt.claim.sub', '', false);
select set_config('request.jwt.claim.role', 'anon', false);
set role anon;
do $$
begin
  begin
    perform public.provision_app_owner('c0000000-0000-0000-0000-000000000001');
    raise exception 'FAIL: anon called provision_app_owner()';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;
select set_config('request.jwt.claim.role', '', false);

-- -----------------------------------------------------------------------------
-- 2) Remaining fixtures.  Roles are seeded as the migration owner, not by an
--    authenticated client.
-- -----------------------------------------------------------------------------
select set_config('app.authorized_role_change', 'on', false);
select set_config('request.jwt.claim.sub', '', false);

insert into auth.users (id, email, raw_user_meta_data) values
  ('c0000000-0000-0000-0000-000000000002', 'supervisor-foundation@test.local',
   '{"full_name":"المشرفة العامة"}'),
  ('c0000000-0000-0000-0000-000000000003', 'supervisor-two@test.local',
   '{"full_name":"مشرفة ثانية"}'),
  ('c0000000-0000-0000-0000-000000000004', 'teacher-foundation@test.local',
   '{"full_name":"معلمة الاختبار"}'),
  ('c0000000-0000-0000-0000-000000000006', 'second-candidate@test.local',
   '{"full_name":"مرشحة ثانية"}'),
  ('c0000000-0000-0000-0000-000000000007', 'promoted-foundation@test.local',
   '{"full_name":"مرشحة الترقية"}'),
  ('c0000000-0000-0000-0000-000000000008', 'metadata-attempt@test.local',
   '{"full_name":"محاولة ميتاداتا","role":"supervisor","is_owner":true,"app_owner":true}')
on conflict (id) do nothing;

update public.profiles set role = 'supervisor'
 where id in ('c0000000-0000-0000-0000-000000000002',
              'c0000000-0000-0000-0000-000000000003');
update public.profiles set role = 'teacher'
 where id = 'c0000000-0000-0000-0000-000000000004';

insert into public.halqa_members (halqa_id, user_id, role, level, status) values
  (public.main_halqa_id(), 'c0000000-0000-0000-0000-000000000002', 'supervisor', 'ijaza', 'active'),
  (public.main_halqa_id(), 'c0000000-0000-0000-0000-000000000003', 'supervisor', 'ijaza', 'active'),
  (public.main_halqa_id(), 'c0000000-0000-0000-0000-000000000004', 'teacher',    'ijaza', 'active'),
  (public.main_halqa_id(), 'c0000000-0000-0000-0000-000000000005', 'student',    'tajweed', 'active'),
  (public.main_halqa_id(), 'c0000000-0000-0000-0000-000000000007', 'student',    'foundation', 'active')
on conflict (halqa_id, user_id) do update
  set role = excluded.role, level = excluded.level, status = excluded.status;

select set_config('app.authorized_role_change', 'off', false);

do $$
begin
  if (select role from public.profiles
       where id = 'c0000000-0000-0000-0000-000000000008') <> 'student' then
    raise exception 'FAIL: owner metadata was persisted as a role';
  end if;
  if exists (select 1 from public.halqa_members
              where user_id = 'c0000000-0000-0000-0000-000000000008') then
    raise exception 'FAIL: signup metadata created a membership';
  end if;
end $$;

-- -----------------------------------------------------------------------------
-- 3) Provision the owner once as service_role, then refuse a second owner.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '', false);
select set_config('request.jwt.claim.role', 'service_role', false);
set role service_role;
select public.provision_app_owner('c0000000-0000-0000-0000-000000000001');
reset role;
select set_config('request.jwt.claim.role', '', false);

do $$
declare
  v_role    public.user_role;
  v_status  public.member_status;
  v_level   public.member_level;
  v_count   integer;
begin
  select count(*) into v_count from public.app_owner;
  if v_count <> 1 then
    raise exception 'FAIL: expected exactly one owner row, got %', v_count;
  end if;
  if not exists (select 1 from public.app_owner
                  where user_id = 'c0000000-0000-0000-0000-000000000001') then
    raise exception 'FAIL: owner row does not point at the provisioned profile';
  end if;

  select p.role, m.status, m.level
    into v_role, v_status, v_level
    from public.profiles p
    join public.halqa_members m on m.user_id = p.id
   where p.id = 'c0000000-0000-0000-0000-000000000001';

  if v_role <> 'supervisor' then
    raise exception 'FAIL: provisioned owner profile role is %', v_role;
  end if;
  if v_status <> 'active' then
    raise exception 'FAIL: provisioned owner membership is %', v_status;
  end if;
  if v_level <> 'ijaza' then
    raise exception 'FAIL: provisioned owner level is %', v_level;
  end if;
  if not exists (select 1 from public.authorization_audit
                  where event = 'owner_provisioned'
                    and subject_id = 'c0000000-0000-0000-0000-000000000001') then
    raise exception 'FAIL: owner provisioning did not append an audit event';
  end if;
end $$;

select set_config('request.jwt.claim.sub', '', false);
select set_config('request.jwt.claim.role', 'service_role', false);
set role service_role;
do $$
begin
  begin
    perform public.provision_app_owner('c0000000-0000-0000-0000-000000000006');
    raise exception 'FAIL: service_role provisioned a second owner';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;
select set_config('request.jwt.claim.role', '', false);

do $$
begin
  if (select count(*) from public.app_owner) <> 1 then
    raise exception 'FAIL: a second owner row exists';
  end if;
  if (select role from public.profiles
       where id = 'c0000000-0000-0000-0000-000000000006') <> 'student' then
    raise exception 'FAIL: the refused candidate was promoted';
  end if;
  if exists (select 1 from public.halqa_members
              where user_id = 'c0000000-0000-0000-0000-000000000006') then
    raise exception 'FAIL: the refused candidate received a membership';
  end if;
  if exists (select 1 from public.authorization_audit
              where subject_id = 'c0000000-0000-0000-0000-000000000006') then
    raise exception 'FAIL: the refused candidate has an audit event';
  end if;
end $$;

-- A direct table write is never the provisioning path.
select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000005', false);
set role authenticated;
do $$
begin
  begin
    insert into public.app_owner (singleton, user_id)
    values (true, auth.uid());
    raise exception 'FAIL: authenticated user inserted an owner row';
  exception when insufficient_privilege then null;
  end;

  begin
    update public.profiles set role = 'supervisor' where id = auth.uid();
    raise exception 'FAIL: authenticated user updated her own role';
  exception when insufficient_privilege then null;
  end;

  if (select role from public.profiles where id = auth.uid()) <> 'student' then
    raise exception 'FAIL: direct role update persisted';
  end if;
end $$;
reset role;

-- -----------------------------------------------------------------------------
-- 4) Owner is hidden from non-owner profile/membership/chat-candidate views,
--    but can see her own account.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000002', false);
set role authenticated;
do $$
declare v_count integer;
begin
  select count(*) into v_count from public.profiles
   where id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 0 then raise exception 'FAIL: supervisor saw owner profile'; end if;

  select count(*) into v_count from public.profile_directory
   where id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 0 then raise exception 'FAIL: supervisor saw owner in profile directory'; end if;

  select count(*) into v_count from public.halqa_member_directory
   where user_id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 0 then raise exception 'FAIL: supervisor saw owner in chat candidates'; end if;

  select count(*) into v_count from public.halqa_members
   where user_id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 0 then raise exception 'FAIL: supervisor saw owner membership'; end if;
end $$;
reset role;

select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000004', false);
set role authenticated;
do $$
declare v_count integer;
begin
  select count(*) into v_count from public.profiles
   where id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 0 then raise exception 'FAIL: teacher saw owner profile'; end if;

  select count(*) into v_count from public.profile_directory
   where id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 0 then raise exception 'FAIL: teacher saw owner in profile directory'; end if;

  select count(*) into v_count from public.halqa_member_directory
   where user_id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 0 then raise exception 'FAIL: teacher saw owner in chat candidates'; end if;

  select count(*) into v_count from public.halqa_members
   where user_id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 0 then raise exception 'FAIL: teacher saw owner membership'; end if;
end $$;
reset role;

select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000005', false);
set role authenticated;
do $$
declare v_count integer;
begin
  select count(*) into v_count from public.profiles
   where id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 0 then raise exception 'FAIL: student saw owner profile'; end if;

  select count(*) into v_count from public.profile_directory
   where id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 0 then raise exception 'FAIL: student saw owner in profile directory'; end if;

  select count(*) into v_count from public.halqa_member_directory
   where user_id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 0 then raise exception 'FAIL: student saw owner in chat candidates'; end if;
end $$;
reset role;

select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000001', false);
set role authenticated;
do $$
declare
  v_count integer;
  v_inv   public.invitations;
begin
  if not public.is_owner() then raise exception 'FAIL: owner helper did not resolve'; end if;
  if not public.is_member() then raise exception 'FAIL: owner is not a member'; end if;
  if not public.is_staff() then raise exception 'FAIL: owner is not staff'; end if;
  if not public.is_supervisor() then raise exception 'FAIL: owner is not supervisor'; end if;

  select count(*) into v_count from public.profiles
   where id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 1 then raise exception 'FAIL: owner cannot see her own profile'; end if;

  select count(*) into v_count from public.profile_directory
   where id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 1 then raise exception 'FAIL: owner cannot see herself in the directory'; end if;

  select count(*) into v_count from public.halqa_member_directory
   where user_id = 'c0000000-0000-0000-0000-000000000001';
  if v_count <> 1 then raise exception 'FAIL: owner cannot see her own membership'; end if;

  select count(*) into v_count from public.profiles
   where id = 'c0000000-0000-0000-0000-000000000005';
  if v_count <> 1 then raise exception 'FAIL: owner lost supervisor profile access'; end if;

  v_inv := public.create_invitation('teacher', 1, 1);
  if v_inv.id is null or v_inv.role <> 'teacher' then
    raise exception 'FAIL: owner cannot use staff invitation capability';
  end if;

  v_inv := public.create_invitation('supervisor', 1, 1);
  if v_inv.id is null or v_inv.role <> 'supervisor' then
    raise exception 'FAIL: owner cannot appoint another supervisor';
  end if;
  perform set_config('test.owner_supervisor_invite', v_inv.code, false);
end $$;
reset role;

-- -----------------------------------------------------------------------------
-- 5) Supervisors/teachers cannot edit, ban, remove or demote the owner.
--    These are the behavior checks for private.is_owner(uuid): the RLS and
--    trigger target predicate still hides and protects the owner.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000002', false);
set role authenticated;
do $$
declare
  v_rows integer;
  v_name text;
begin
  select full_name into v_name from public.profiles
   where id = 'c0000000-0000-0000-0000-000000000001';
  if v_name is not null then
    raise exception 'FAIL: supervisor read the owner profile';
  end if;

  v_rows := 0;
  begin
    update public.profiles set full_name = 'تغيير غير مصرح'
     where id = 'c0000000-0000-0000-0000-000000000001';
    get diagnostics v_rows = row_count;
  exception when insufficient_privilege then v_rows := 0;
  end;
  if v_rows <> 0 then raise exception 'FAIL: supervisor edited the owner profile'; end if;

  v_rows := 0;
  begin
    update public.profiles set role = 'student'
     where id = 'c0000000-0000-0000-0000-000000000001';
    get diagnostics v_rows = row_count;
  exception when insufficient_privilege then v_rows := 0;
  end;
  if v_rows <> 0 then raise exception 'FAIL: supervisor demoted the owner profile'; end if;

  v_rows := 0;
  begin
    update public.halqa_members set status = 'banned'
     where user_id = 'c0000000-0000-0000-0000-000000000001';
    get diagnostics v_rows = row_count;
  exception when insufficient_privilege then v_rows := 0;
  end;
  if v_rows <> 0 then raise exception 'FAIL: supervisor banned the owner membership'; end if;

  v_rows := 0;
  begin
    delete from public.halqa_members
     where user_id = 'c0000000-0000-0000-0000-000000000001';
    get diagnostics v_rows = row_count;
  exception when insufficient_privilege then v_rows := 0;
  end;
  if v_rows <> 0 then raise exception 'FAIL: supervisor deleted the owner membership'; end if;

  v_rows := 0;
  begin
    delete from public.profiles
     where id = 'c0000000-0000-0000-0000-000000000001';
    get diagnostics v_rows = row_count;
  exception when insufficient_privilege then v_rows := 0;
  end;
  if v_rows <> 0 then raise exception 'FAIL: supervisor deleted the owner profile'; end if;

  begin
    perform public.set_member_status('c0000000-0000-0000-0000-000000000001', 'banned');
    raise exception 'FAIL: supervisor banned the owner through the RPC';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.reject_member('c0000000-0000-0000-0000-000000000001');
    raise exception 'FAIL: supervisor removed the owner through the RPC';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.accept_member('c0000000-0000-0000-0000-000000000001');
    raise exception 'FAIL: supervisor changed the owner through accept_member';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;

do $$
begin
  if (select role from public.profiles
       where id = 'c0000000-0000-0000-0000-000000000001') <> 'supervisor' then
    raise exception 'FAIL: owner profile role changed';
  end if;
  if (select full_name from public.profiles
       where id = 'c0000000-0000-0000-0000-000000000001') <> 'مرشحة المالكة' then
    raise exception 'FAIL: owner full name changed';
  end if;
  if (select status from public.halqa_members
       where user_id = 'c0000000-0000-0000-0000-000000000001') <> 'active' then
    raise exception 'FAIL: owner membership status changed';
  end if;
  if not exists (select 1 from public.halqa_members
                  where user_id = 'c0000000-0000-0000-0000-000000000001') then
    raise exception 'FAIL: owner membership disappeared';
  end if;
end $$;

select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000004', false);
set role authenticated;
do $$
begin
  begin
    perform public.set_member_status('c0000000-0000-0000-0000-000000000001', 'banned');
    raise exception 'FAIL: teacher banned the owner through the RPC';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.accept_member('c0000000-0000-0000-0000-000000000001');
    raise exception 'FAIL: teacher changed the owner through accept_member';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;

-- -----------------------------------------------------------------------------
-- 6) Only the owner appoints/removes another supervisor.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000002', false);
set role authenticated;
do $$
declare v_rows integer;
begin
  begin
    perform public.create_invitation('supervisor', 1, 1);
    raise exception 'FAIL: supervisor created a supervisor invitation';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.set_member_status('c0000000-0000-0000-0000-000000000003', 'left');
    raise exception 'FAIL: supervisor removed another supervisor through the RPC';
  exception when insufficient_privilege then null;
  end;

  v_rows := 0;
  begin
    update public.halqa_members set status = 'left'
     where user_id = 'c0000000-0000-0000-0000-000000000003';
    get diagnostics v_rows = row_count;
  exception when insufficient_privilege then v_rows := 0;
  end;
  if v_rows <> 0 then raise exception 'FAIL: supervisor demoted another supervisor directly'; end if;
end $$;
reset role;

-- A legacy supervisor-minted supervisor invitation must not appoint anyone.
insert into public.invitations (code, role, max_uses, expires_at, created_by)
values ('OLDSUP001', 'supervisor', 1, now() + interval '1 day',
        'c0000000-0000-0000-0000-000000000002')
on conflict (code) do nothing;

select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000006', false);
set role authenticated;
do $$
begin
  begin
    perform public.redeem_invitation('OLDSup001');
    raise exception 'FAIL: redeemed a supervisor invitation not issued by the owner';
  exception when insufficient_privilege then null;
  end;

  if (select role from public.profiles where id = auth.uid()) <> 'student' then
    raise exception 'FAIL: legacy supervisor invitation promoted the redeemer';
  end if;
  if exists (select 1 from public.halqa_members where user_id = auth.uid()) then
    raise exception 'FAIL: legacy supervisor invitation created a membership';
  end if;
end $$;
reset role;

-- The owner-appointed invitation is redeemable and the owner can remove the
-- resulting supervisor.
select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000007', false);
set role authenticated;
do $$
declare
  v_code   text := current_setting('test.owner_supervisor_invite', true);
  v_member public.halqa_members;
begin
  if v_code is null or v_code = '' then
    raise exception 'FAIL: owner supervisor invitation was not created';
  end if;

  v_member := public.redeem_invitation(v_code);
  if v_member.role <> 'supervisor' or v_member.status <> 'active' then
    raise exception 'FAIL: owner appointment did not create an active supervisor';
  end if;
  if not public.is_supervisor() then
    raise exception 'FAIL: owner-appointed supervisor does not resolve';
  end if;
end $$;
reset role;

select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000001', false);
set role authenticated;
do $$
begin
  perform public.set_member_status('c0000000-0000-0000-0000-000000000007', 'left');
end $$;
reset role;

do $$
begin
  if (select status from public.halqa_members
       where user_id = 'c0000000-0000-0000-0000-000000000007') <> 'left' then
    raise exception 'FAIL: owner could not remove another supervisor';
  end if;
end $$;

-- Invalid status text must be rejected by the validation branch (P0001, with
-- the original message), not by a raw enum cast, and the rejected call must
-- leave the membership untouched.
select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000001', false);
set role authenticated;
do $$
declare
  v_message  text;
  v_rejected boolean := false;
begin
  begin
    perform public.set_member_status('c0000000-0000-0000-0000-000000000007', 'archived');
  exception when raise_exception then
    v_rejected := true;
    get stacked diagnostics v_message = message_text;
  end;

  if not v_rejected then
    raise exception 'FAIL: owner set an invalid membership status';
  end if;
  if v_message <> 'حالة غير صالحة' then
    raise exception 'FAIL: invalid status was not rejected by validation: %', v_message;
  end if;
end $$;
reset role;

do $$
begin
  if (select status from public.halqa_members
       where user_id = 'c0000000-0000-0000-0000-000000000007') <> 'left' then
    raise exception 'FAIL: rejected status change mutated the membership';
  end if;
end $$;

-- -----------------------------------------------------------------------------
-- 7) Audit is append-only, server-readable, and stores only authorization data.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-000000000005', false);
set role authenticated;
do $$
declare v_count integer;
begin
  begin
    select count(*) into v_count from public.authorization_audit;
    raise exception 'FAIL: authenticated user read the authorization audit';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;

select set_config('request.jwt.claim.sub', '', false);
set role service_role;
do $$
declare v_count integer;
begin
  select count(*) into v_count from public.authorization_audit
   where event = 'owner_provisioned'
     and subject_id = 'c0000000-0000-0000-0000-000000000001';
  if v_count < 1 then
    raise exception 'FAIL: service_role cannot read the owner audit event';
  end if;
end $$;
reset role;

do $$
declare v_columns text[];
begin
  select array_agg(column_name::text order by ordinal_position)
    into v_columns
    from information_schema.columns
   where table_schema = 'public' and table_name = 'authorization_audit';

  if v_columns <> array['id', 'event', 'subject_id', 'created_at'] then
    raise exception 'FAIL: authorization audit has unexpected columns: %', v_columns;
  end if;

  begin
    update public.authorization_audit set event = 'tampered'
     where subject_id = 'c0000000-0000-0000-0000-000000000001';
    raise exception 'FAIL: authorization audit accepted UPDATE';
  exception when insufficient_privilege then null;
  end;

  begin
    delete from public.authorization_audit
     where subject_id = 'c0000000-0000-0000-0000-000000000001';
    raise exception 'FAIL: authorization audit accepted DELETE';
  exception when insufficient_privilege then null;
  end;
end $$;

select 'OWNER FOUNDATION TESTS PASSED' as result;
