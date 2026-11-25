-- =============================================================================
--  Phase 1.1 contract regression tests.
--  Proves the reconciled columns and the full join-request submit -> approve ->
--  redeem -> accept path used by SessionViewModel / AdminViewModel.
--  Run only on a disposable local PostgreSQL after all migrations.
--  Success prints: PHASE 1.1 CONTRACT TESTS PASSED
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) Column contracts read/written by the Android client
-- -----------------------------------------------------------------------------
do $$
declare v_count integer;
begin
  select count(*) into v_count
    from information_schema.columns
   where table_schema = 'public'
     and table_name = 'halqa_members'
     and column_name = 'graduated'
     and data_type = 'boolean';
  if v_count <> 1 then
    raise exception 'FAIL: halqa_members.graduated is missing or not boolean';
  end if;

  select count(*) into v_count
    from information_schema.columns
   where table_schema = 'public'
     and table_name = 'join_requests'
     and column_name in ('email', 'city', 'level_pref');
  if v_count <> 3 then
    raise exception 'FAIL: join_requests email/city/level_pref missing (%)', v_count;
  end if;

  select count(*) into v_count
    from information_schema.columns
   where table_schema = 'public'
     and table_name = 'invitations'
     and column_name = 'halqa_id'
     and data_type = 'uuid';
  if v_count <> 1 then
    raise exception 'FAIL: invitations.halqa_id is missing or not uuid';
  end if;

  -- One submit_join_request only: the stale three-argument overload must be gone.
  select count(*) into v_count
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'public' and p.proname = 'submit_join_request';
  if v_count <> 1 then
    raise exception 'FAIL: expected one submit_join_request overload, found %', v_count;
  end if;

  select count(*) into v_count
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'public'
     and p.proname = 'submit_join_request'
     and p.pronargs = 6;
  if v_count <> 1 then
    raise exception 'FAIL: submit_join_request does not take the six client arguments';
  end if;
end $$;

-- -----------------------------------------------------------------------------
-- 2) Dedicated supervisor for the flow
-- -----------------------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
  ('99999999-0000-0000-0000-000000000001',
   'phase11-supervisor@test.local',
   '{"full_name":"مشرفة اختبار المرحلة"}')
on conflict (id) do nothing;

update public.profiles
   set role = 'supervisor'
 where id = '99999999-0000-0000-0000-000000000001';

insert into public.halqa_members (halqa_id, user_id, role, level, status)
select public.main_halqa_id(), '99999999-0000-0000-0000-000000000001',
       'supervisor', 'ijaza', 'active'
on conflict (halqa_id, user_id) do update
   set role = 'supervisor', status = 'active';

select set_config('request.jwt.claim.sub', '99999999-0000-0000-0000-000000000001', false);
set role authenticated;

do $$
begin
  if not public.is_supervisor() then
    raise exception 'FAIL: is_supervisor() did not resolve for an active supervisor';
  end if;
  if (select graduated from public.halqa_members
       where user_id = '99999999-0000-0000-0000-000000000001') is not false then
    raise exception 'FAIL: graduated must default to false';
  end if;
  perform public.set_applications_open(true);
  if not public.applications_open() then
    raise exception 'FAIL: applications_open() did not persist the open state';
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 3) Anonymous submit with the exact SessionViewModel arguments
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '', false);
set role anon;

do $$
begin
  if public.submit_join_request(
       'طالبة المرحلة', 'phase11-applicant@example.test', '+96500000001',
       'الكويت', 'حفظ جزء عم', 'hifz') <> 'submitted' then
    raise exception 'FAIL: submit_join_request did not return submitted';
  end if;

  -- Same normalized name + phone inside 24 hours.
  if public.submit_join_request(
       'طالبة المرحلة', 'other@example.test', ' +96500000001 ',
       'الكويت', 'ملاحظة أخرى', 'hifz') <> 'duplicate' then
    raise exception 'FAIL: duplicate request was not detected';
  end if;
end $$;

reset role;

-- Closed applications are reported without inserting a request.
select set_config('request.jwt.claim.sub', '99999999-0000-0000-0000-000000000001', false);
set role authenticated;
do $$ begin
  perform public.set_applications_open(false);
end $$;
reset role;

select set_config('request.jwt.claim.sub', '', false);
set role anon;
do $$ begin
  if public.submit_join_request(
       'طالبة ثانية', 'phase11-second@example.test', '+96500000002',
       'الجهراء', 'ملاحظة', 'tajweed') <> 'closed' then
    raise exception 'FAIL: closed applications accepted a request';
  end if;
end $$;
reset role;

-- -----------------------------------------------------------------------------
-- 4) Supervisor reads the reconciled fields and approves the request
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '99999999-0000-0000-0000-000000000001', false);
set role authenticated;

do $$
declare
  v_req  public.join_requests;
  v_inv  public.invitations;
  v_code text;
begin
  perform public.set_applications_open(true);

  select * into v_req
    from public.join_requests
   where email = 'phase11-applicant@example.test'
     and status = 'pending';
  if v_req.id is null then
    raise exception 'FAIL: supervisor cannot read the pending join request';
  end if;
  if v_req.full_name <> 'طالبة المرحلة'
     or v_req.phone <> '+96500000001'
     or v_req.city <> 'الكويت'
     or v_req.level_pref <> 'hifz'
     or v_req.note <> 'حفظ جزء عم' then
    raise exception 'FAIL: join request fields were not persisted as submitted';
  end if;

  v_code := public.decide_join_request(v_req.id, true);
  if v_code is null or length(v_code) <> 8 then
    raise exception 'FAIL: decide_join_request did not return an 8-character code';
  end if;

  select * into v_inv from public.invitations where code = v_code;
  if v_inv.id is null then
    raise exception 'FAIL: approved request did not create an invitation';
  end if;
  if v_inv.halqa_id is distinct from public.main_halqa_id() then
    raise exception 'FAIL: invitation.halqa_id was not set';
  end if;
  if v_inv.role <> 'student' or v_inv.max_uses <> 1 or v_inv.uses <> 0 then
    raise exception 'FAIL: invitation role/uses are not the student defaults';
  end if;

  if (select status from public.join_requests where id = v_req.id) <> 'accepted' then
    raise exception 'FAIL: join request was not marked accepted';
  end if;
  if (select invitation_code from public.join_requests where id = v_req.id) <> v_code then
    raise exception 'FAIL: invitation code was not stored on the request';
  end if;

  -- Teacher-created invitations also receive the singleton halqa by default.
  v_inv := public.create_invitation('student', 1, 1);
  if v_inv.halqa_id is distinct from public.main_halqa_id() then
    raise exception 'FAIL: create_invitation did not populate halqa_id';
  end if;

  perform set_config('test.phase11_code', v_code, false);
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 5) Applicant redeems the code and becomes a pending student, then supervisor
--    accepts her membership.
-- -----------------------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
  ('99999999-0000-0000-0000-000000000002',
   'phase11-applicant@test.local',
   '{"full_name":"طالبة المرحلة"}')
on conflict (id) do nothing;

select set_config('request.jwt.claim.sub', '99999999-0000-0000-0000-000000000002', false);
set role authenticated;

do $$
declare
  v_member public.halqa_members;
  v_code   text := current_setting('test.phase11_code', true);
begin
  if v_code is null or v_code = '' then
    raise exception 'FAIL: no invitation code was captured';
  end if;

  v_member := public.redeem_invitation(v_code);
  if v_member.user_id <> auth.uid() then
    raise exception 'FAIL: redeem_invitation did not return the applicant membership';
  end if;
  if v_member.status <> 'pending' or v_member.role <> 'student' then
    raise exception 'FAIL: student invitation did not create a pending student';
  end if;
  if (select role from public.profiles where id = auth.uid()) <> 'student' then
    raise exception 'FAIL: profile role was not set to student';
  end if;
end $$;

reset role;

select set_config('request.jwt.claim.sub', '99999999-0000-0000-0000-000000000001', false);
set role authenticated;

do $$
begin
  perform public.accept_member('99999999-0000-0000-0000-000000000002');
  if (select status from public.halqa_members
       where user_id = '99999999-0000-0000-0000-000000000002') <> 'active' then
    raise exception 'FAIL: accept_member did not activate the membership';
  end if;

  -- The graduated flag is writable through the staff membership policy.
  update public.halqa_members
     set graduated = true
   where user_id = '99999999-0000-0000-0000-000000000002';
  if (select graduated from public.halqa_members
       where user_id = '99999999-0000-0000-0000-000000000002') is not true then
    raise exception 'FAIL: graduated flag was not writable';
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 6) Rejection path
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '', false);
set role anon;
do $$ begin
  if public.submit_join_request(
       'طالبة مرفوضة', 'phase11-reject@example.test', '+96500000003',
       'الفروانية', null, 'foundation') <> 'submitted' then
    raise exception 'FAIL: rejection fixture request was not submitted';
  end if;
end $$;
reset role;

select set_config('request.jwt.claim.sub', '99999999-0000-0000-0000-000000000001', false);
set role authenticated;
do $$
declare
  v_id   uuid;
  v_code text;
begin
  select id into v_id
    from public.join_requests
   where email = 'phase11-reject@example.test' and status = 'pending';
  if v_id is null then
    raise exception 'FAIL: rejection fixture request is not visible';
  end if;

  v_code := public.decide_join_request(v_id, false);
  if v_code is not null then
    raise exception 'FAIL: rejected request returned an invitation code';
  end if;
  if (select status from public.join_requests where id = v_id) <> 'rejected' then
    raise exception 'FAIL: join request was not marked rejected';
  end if;
end $$;

reset role;

select 'PHASE 1.1 CONTRACT TESTS PASSED' as result;
