-- =============================================================================
--  General Supervisor provisioning regression tests.
--  Run only on a disposable local PostgreSQL after every migration.
--  Fixtures are synthetic local UUIDs; no real account or credential is used.
--  Success prints: GENERAL SUPERVISOR PROVISIONING TESTS PASSED
--
--  The whole file runs in one transaction and rolls back, so it neither
--  depends on earlier test fixtures nor changes them.
-- =============================================================================
\set ON_ERROR_STOP on
set client_min_messages = warning;

begin;

select set_config('request.jwt.claim.sub', '', false);
select set_config('request.jwt.claim.role', '', false);
select set_config('app.authorized_role_change', 'on', false);
select set_config('app.authorized_supervisor_change', 'off', false);

-- -----------------------------------------------------------------------------
-- 1) Synthetic fixtures.  Profiles are created by the auth.users trigger.
-- -----------------------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
  ('d0000000-0000-0000-0000-000000000001', 'provision-candidate@test.local',
   '{"full_name":"مرشحة المشرفة"}'),
  ('d0000000-0000-0000-0000-000000000002', 'provision-second@test.local',
   '{"full_name":"مرشحة ثانية"}')
on conflict (id) do nothing;

-- Make this test independent of earlier fixtures: inside this transaction the
-- system models production (owner only, no non-owner active supervisor).
update public.halqa_members m
   set status = 'left'
  from public.profiles p
 where p.id = m.user_id
   and p.role = 'supervisor'
   and m.status = 'active'
   and not exists (
     select 1 from public.app_owner o where o.user_id = p.id
   );

-- Test 60 normally provisions an owner.  On an isolated run, create a synthetic
-- one so owner-as-target and owner-unchanged checks are meaningful.
do $$
begin
  if not exists (select 1 from public.app_owner) then
    insert into auth.users (id, email, raw_user_meta_data)
    values ('d0000000-0000-0000-0000-0000000000ff', 'provision-owner@test.local',
            '{"full_name":"مالكة الاختبار"}')
    on conflict (id) do nothing;
    perform public.provision_app_owner('d0000000-0000-0000-0000-0000000000ff');
  end if;
end $$;

select set_config(
  'test.provision_owner_id',
  coalesce((select user_id::text from public.app_owner where singleton), ''),
  false
);

create temp table provision_owner_baseline on commit drop as
select p.id, p.role::text as role, m.status::text as status
  from public.profiles p
  left join public.halqa_members m
    on m.user_id = p.id
   and m.halqa_id = public.main_halqa_id()
 where p.id = (select user_id from public.app_owner where singleton);

-- -----------------------------------------------------------------------------
-- 2) The RPC is service-role only; anon and authenticated cannot execute it.
-- -----------------------------------------------------------------------------
do $$
begin
  if to_regprocedure('public.provision_general_supervisor(uuid)') is null then
    raise exception 'FAIL: provisioning RPC is missing';
  end if;
  if has_function_privilege('anon', 'public.provision_general_supervisor(uuid)', 'EXECUTE') then
    raise exception 'FAIL: anon can execute provision_general_supervisor()';
  end if;
  if has_function_privilege('authenticated', 'public.provision_general_supervisor(uuid)', 'EXECUTE') then
    raise exception 'FAIL: authenticated can execute provision_general_supervisor()';
  end if;
  if not has_function_privilege('service_role', 'public.provision_general_supervisor(uuid)', 'EXECUTE') then
    raise exception 'FAIL: service_role cannot execute provision_general_supervisor()';
  end if;
end $$;

select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-000000000002', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
do $$
begin
  begin
    perform public.provision_general_supervisor('d0000000-0000-0000-0000-000000000001');
    raise exception 'FAIL: authenticated user executed provision_general_supervisor()';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;

select set_config('request.jwt.claim.sub', '', false);
select set_config('request.jwt.claim.role', 'anon', false);
set role anon;
do $$
begin
  begin
    perform public.provision_general_supervisor('d0000000-0000-0000-0000-000000000001');
    raise exception 'FAIL: anon executed provision_general_supervisor()';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;

-- -----------------------------------------------------------------------------
-- 3) Validation as service_role: null, missing profile and owner-as-target are
--    rejected before any write; the valid target is then provisioned.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '', false);
select set_config('request.jwt.claim.role', 'service_role', false);
set role service_role;

do $$
declare
  v_state text;
  v_owner text := current_setting('test.provision_owner_id', true);
begin
  v_state := '';
  begin
    perform public.provision_general_supervisor(null);
  exception when others then
    v_state := sqlstate;
  end;
  if v_state <> '22023' then
    raise exception 'FAIL: null target rejected with state %, expected 22023', v_state;
  end if;

  v_state := '';
  begin
    perform public.provision_general_supervisor('d0000000-0000-0000-0000-0000000000aa');
  exception when others then
    v_state := sqlstate;
  end;
  if v_state <> '22023' then
    raise exception 'FAIL: missing profile rejected with state %, expected 22023', v_state;
  end if;

  if v_owner is not null and v_owner <> '' then
    v_state := '';
    begin
      perform public.provision_general_supervisor(v_owner::uuid);
    exception when others then
      v_state := sqlstate;
    end;
    if v_state <> 'MQR01' then
      raise exception 'FAIL: owner target rejected with state %, expected MQR01', v_state;
    end if;
  else
    raise exception 'FAIL: no owner exists for the owner-as-target check';
  end if;

  perform public.provision_general_supervisor('d0000000-0000-0000-0000-000000000001');
end $$;

-- -----------------------------------------------------------------------------
-- 4) Provisioning result: supervisor role, active main-halqa membership, audit.
-- -----------------------------------------------------------------------------
do $$
declare
  v_profile_role text;
  v_member_role  public.user_role;
  v_status       public.member_status;
  v_level        public.member_level;
  v_halqa        uuid;
begin
  select p.role::text, m.role, m.status, m.level, m.halqa_id
    into v_profile_role, v_member_role, v_status, v_level, v_halqa
    from public.profiles p
    join public.halqa_members m on m.user_id = p.id
   where p.id = 'd0000000-0000-0000-0000-000000000001'
     and m.halqa_id = public.main_halqa_id();

  if v_profile_role is distinct from 'supervisor' then
    raise exception 'FAIL: provisioned profile role is %', v_profile_role;
  end if;
  if v_member_role is distinct from 'supervisor' then
    raise exception 'FAIL: provisioned membership role is %', v_member_role;
  end if;
  if v_status is distinct from 'active' then
    raise exception 'FAIL: provisioned membership status is %', v_status;
  end if;
  if v_level is distinct from 'ijaza' then
    raise exception 'FAIL: provisioned membership level is %', v_level;
  end if;
  if v_halqa is distinct from public.main_halqa_id() then
    raise exception 'FAIL: provisioned membership is not in the main halqa';
  end if;
  if not exists (
    select 1 from public.authorization_audit
     where event = 'supervisor_provisioned'
       and subject_id = 'd0000000-0000-0000-0000-000000000001'
  ) then
    raise exception 'FAIL: provisioning did not append a supervisor audit event';
  end if;
end $$;

-- -----------------------------------------------------------------------------
-- 5) A second active non-owner General Supervisor is rejected atomically.
-- -----------------------------------------------------------------------------
do $$
declare
  v_state text;
begin
  v_state := '';
  begin
    perform public.provision_general_supervisor('d0000000-0000-0000-0000-000000000002');
  exception when others then
    v_state := sqlstate;
  end;
  if v_state <> 'MQR02' then
    raise exception 'FAIL: duplicate supervisor rejected with state %, expected MQR02', v_state;
  end if;
end $$;

do $$
begin
  if (select role from public.profiles
       where id = 'd0000000-0000-0000-0000-000000000002') <> 'student' then
    raise exception 'FAIL: rejected second candidate was promoted';
  end if;
  if exists (select 1 from public.halqa_members
              where user_id = 'd0000000-0000-0000-0000-000000000002') then
    raise exception 'FAIL: rejected second candidate received a membership';
  end if;
  if exists (select 1 from public.authorization_audit
              where subject_id = 'd0000000-0000-0000-0000-000000000002') then
    raise exception 'FAIL: rejected second candidate has an audit event';
  end if;
end $$;

-- Re-provisioning the same supervisor is idempotent for role and membership.
do $$
begin
  perform public.provision_general_supervisor('d0000000-0000-0000-0000-000000000001');
end $$;

do $$
declare
  v_count integer;
begin
  select count(*) into v_count
    from public.halqa_members
   where user_id = 'd0000000-0000-0000-0000-000000000001'
     and halqa_id = public.main_halqa_id();
  if v_count <> 1 then
    raise exception 'FAIL: idempotent provisioning created % main-halqa memberships', v_count;
  end if;
  if (select status from public.halqa_members
       where user_id = 'd0000000-0000-0000-0000-000000000001'
         and halqa_id = public.main_halqa_id()) <> 'active' then
    raise exception 'FAIL: idempotent provisioning changed the membership status';
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 6) The owner remains hidden from this path and unchanged by it.
-- -----------------------------------------------------------------------------
do $$
declare
  v_owner  uuid;
  v_role   text;
  v_status text;
  b        record;
begin
  select user_id into v_owner from public.app_owner where singleton;
  if v_owner is null then
    raise exception 'FAIL: owner row disappeared';
  end if;
  if (select count(*) from public.app_owner) <> 1 then
    raise exception 'FAIL: more than one owner row exists';
  end if;

  select p.role::text, m.status::text
    into v_role, v_status
    from public.profiles p
    left join public.halqa_members m
      on m.user_id = p.id
     and m.halqa_id = public.main_halqa_id()
   where p.id = v_owner;

  select * into b from provision_owner_baseline;
  if v_role is distinct from b.role or v_status is distinct from b.status then
    raise exception 'FAIL: owner profile/membership changed by supervisor provisioning';
  end if;
end $$;

select set_config('request.jwt.claim.role', '', false);
select set_config('request.jwt.claim.sub', '', false);
select set_config('app.authorized_role_change', 'off', false);
select set_config('app.authorized_supervisor_change', 'off', false);

select 'GENERAL SUPERVISOR PROVISIONING TESTS PASSED' as result;

rollback;
