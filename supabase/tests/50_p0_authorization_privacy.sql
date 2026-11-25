-- =============================================================================
--  P0 authorization/privacy regression tests.
--  Run only on a disposable local PostgreSQL after every migration.
--  Success prints: P0 AUTHORIZATION/PRIVACY TESTS PASSED
--
--  Covers: teacher->supervisor escalation, inactive staff, phone privacy,
--  recitation-turn tampering, non-member messaging, unauthorized receipts, and
--  the voice-notes owner/staff boundary.
-- =============================================================================

-- Supabase enables RLS on storage.objects; the local harness creates the table
-- but does not enable it. Do it for the voice-notes assertions.
alter table storage.objects enable row level security;

-- Fixtures run as the migration owner, not as a leftover JWT from an earlier
-- test file.  Clear the synthetic sub before seeding so ownership/role guards
-- see a genuine server context.
select set_config('request.jwt.claim.sub', '', false);

-- -----------------------------------------------------------------------------
-- 1) Fixtures (as the migration/owner role, before switching to authenticated)
-- -----------------------------------------------------------------------------
select set_config('app.authorized_role_change', 'on', false);

insert into auth.users (id, email, raw_user_meta_data) values
  ('b0000000-0000-0000-0000-000000000001', 'p0-teacher1@test.local', '{"full_name":"معلمة نشطة"}'),
  ('b0000000-0000-0000-0000-000000000002', 'p0-teacher2@test.local', '{"full_name":"معلمة منتهية"}'),
  ('b0000000-0000-0000-0000-000000000003', 'p0-supervisor@test.local', '{"full_name":"مشرفة نشطة"}'),
  ('b0000000-0000-0000-0000-000000000004', 'p0-student-a@test.local', '{"full_name":"طالبة أ"}'),
  ('b0000000-0000-0000-0000-000000000005', 'p0-student-b@test.local', '{"full_name":"طالبة ب"}'),
  ('b0000000-0000-0000-0000-000000000006', 'p0-outsider@test.local', '{"full_name":"غريبة"}'),
  ('b0000000-0000-0000-0000-000000000007', 'p0-pending@test.local', '{"full_name":"معلقة"}')
on conflict (id) do nothing;

update public.profiles set role = 'teacher'
 where id in ('b0000000-0000-0000-0000-000000000001',
              'b0000000-0000-0000-0000-000000000002');
update public.profiles set role = 'supervisor'
 where id = 'b0000000-0000-0000-0000-000000000003';

update public.profiles set phone = '+96500000001'
 where id = 'b0000000-0000-0000-0000-000000000004';
update public.profiles set phone = '+96500000002'
 where id = 'b0000000-0000-0000-0000-000000000005';
update public.profiles set phone = '+96500000003'
 where id = 'b0000000-0000-0000-0000-000000000003';

select set_config('app.authorized_role_change', 'off', false);

delete from public.halqa_members
 where user_id in ('b0000000-0000-0000-0000-000000000001',
                   'b0000000-0000-0000-0000-000000000002',
                   'b0000000-0000-0000-0000-000000000003',
                   'b0000000-0000-0000-0000-000000000004',
                   'b0000000-0000-0000-0000-000000000005',
                   'b0000000-0000-0000-0000-000000000006',
                   'b0000000-0000-0000-0000-000000000007');

insert into public.halqa_members (halqa_id, user_id, role, level, status) values
  (public.main_halqa_id(), 'b0000000-0000-0000-0000-000000000001', 'teacher',    'ijaza',     'active'),
  (public.main_halqa_id(), 'b0000000-0000-0000-0000-000000000002', 'teacher',    'ijaza',     'left'),
  (public.main_halqa_id(), 'b0000000-0000-0000-0000-000000000003', 'supervisor', 'ijaza',     'active'),
  (public.main_halqa_id(), 'b0000000-0000-0000-0000-000000000004', 'student',    'tajweed',   'active'),
  (public.main_halqa_id(), 'b0000000-0000-0000-0000-000000000005', 'student',    'tajweed',   'active'),
  (public.main_halqa_id(), 'b0000000-0000-0000-0000-000000000007', 'student',    'foundation','pending');

-- Phase 5: teacher 1 must be explicitly assigned before she can read/operate on
-- student A and student B.  This keeps the P0 assertions about cross-student
-- private data meaningful under the new group/level scope.
insert into public.groups (halqa_id, name, created_by)
select public.main_halqa_id(), 'P0 مجموعة الإسناد', null
where not exists (
  select 1 from public.groups
   where halqa_id = public.main_halqa_id() and name = 'P0 مجموعة الإسناد'
);

insert into public.member_groups (user_id, group_id, level)
select v.user_id, g.id, 'tajweed'
from (values ('b0000000-0000-0000-0000-000000000004'::uuid),
             ('b0000000-0000-0000-0000-000000000005'::uuid)) as v(user_id)
join public.groups g
  on g.halqa_id = public.main_halqa_id() and g.name = 'P0 مجموعة الإسناد'
on conflict (user_id, group_id, level) do nothing;

insert into public.teacher_assignments (teacher_id, group_id, level, assigned_by)
select 'b0000000-0000-0000-0000-000000000001', g.id, 'tajweed', null
from public.groups g
where g.halqa_id = public.main_halqa_id() and g.name = 'P0 مجموعة الإسناد'
on conflict (teacher_id, group_id, level) do nothing;

-- Deterministic message/session fixtures for this test file.
delete from public.message_receipts
 where message_id in (select id from public.messages where body like 'P0:%');
delete from public.messages where body like 'P0:%';

insert into public.messages (halqa_id, thread, student_id, sender_id, kind, body) values
  (public.main_halqa_id(), 'halqa', null,
   'b0000000-0000-0000-0000-000000000001', 'text', 'P0: public'),
  (public.main_halqa_id(), 'private', 'b0000000-0000-0000-0000-000000000004',
   'b0000000-0000-0000-0000-000000000001', 'text', 'P0: private-a'),
  (public.main_halqa_id(), 'private', 'b0000000-0000-0000-0000-000000000005',
   'b0000000-0000-0000-0000-000000000001', 'text', 'P0: private-b');

select set_config('test.p0_public_message', id::text, false)
  from public.messages where body = 'P0: public';
select set_config('test.p0_private_a', id::text, false)
  from public.messages where body = 'P0: private-a';
select set_config('test.p0_private_b', id::text, false)
  from public.messages where body = 'P0: private-b';

insert into public.sessions (halqa_id, status, current_page, created_by)
select public.main_halqa_id(), 'live', 5, 'b0000000-0000-0000-0000-000000000001'
 where not exists (
   select 1 from public.sessions
    where created_by = 'b0000000-0000-0000-0000-000000000001' and status = 'live'
 );

insert into public.sessions (halqa_id, status, current_page, created_by)
select public.main_halqa_id(), 'scheduled', 10, 'b0000000-0000-0000-0000-000000000001'
 where not exists (
   select 1 from public.sessions
    where created_by = 'b0000000-0000-0000-0000-000000000001' and status = 'scheduled'
 );

select set_config('test.p0_live_session', id::text, false)
  from public.sessions
 where created_by = 'b0000000-0000-0000-0000-000000000001' and status = 'live'
 order by created_at desc limit 1;
select set_config('test.p0_scheduled_session', id::text, false)
  from public.sessions
 where created_by = 'b0000000-0000-0000-0000-000000000001' and status = 'scheduled'
 order by created_at desc limit 1;

delete from storage.objects where bucket_id = 'voice-notes' and name like 'p0/%';
insert into storage.objects (bucket_id, name, owner)
values ('voice-notes', 'p0/owner-a.webm', 'b0000000-0000-0000-0000-000000000004');

-- -----------------------------------------------------------------------------
-- 2) Teacher cannot grant supervisor/teacher, cannot change roles directly.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000001', false);
set role authenticated;

do $$
declare
  v_inv   public.invitations;
  v_rows  integer;
  v_count integer;
begin
  if not public.is_staff() then
    raise exception 'FAIL: active teacher is not staff';
  end if;
  if public.is_supervisor() then
    raise exception 'FAIL: teacher resolved as supervisor';
  end if;

  begin
    perform public.create_invitation('teacher', 1, 1);
    raise exception 'FAIL: teacher created a teacher invitation';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.create_invitation('supervisor', 1, 1);
    raise exception 'FAIL: teacher created a supervisor invitation';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.bootstrap_supervisor();
    raise exception 'FAIL: teacher re-ran the supervisor bootstrap';
  exception when insufficient_privilege then null;
  end;

  v_rows := 0;
  begin
    update public.profiles set role = 'supervisor' where id = auth.uid();
    get diagnostics v_rows = row_count;
  exception when insufficient_privilege then v_rows := 0;
  end;
  if v_rows <> 0 then
    raise exception 'FAIL: teacher changed her own profile role';
  end if;

  v_rows := 0;
  begin
    update public.profiles set role = 'supervisor'
     where id = 'b0000000-0000-0000-0000-000000000004';
    get diagnostics v_rows = row_count;
  exception when insufficient_privilege then v_rows := 0;
  end;
  if v_rows <> 0 then
    raise exception 'FAIL: teacher changed another profile role';
  end if;

  v_rows := 0;
  begin
    update public.halqa_members set role = 'supervisor'
     where user_id = 'b0000000-0000-0000-0000-000000000004';
    get diagnostics v_rows = row_count;
  exception when insufficient_privilege then v_rows := 0;
  end;
  if v_rows <> 0 then
    raise exception 'FAIL: teacher changed another member role';
  end if;

  begin
    insert into public.invitations (code, role, max_uses)
    values ('P0SUP001', 'supervisor', 1);
    raise exception 'FAIL: teacher inserted a supervisor invitation';
  exception when insufficient_privilege then null;
  end;

  -- The gated RPC still lets a teacher invite a student, but the bearer code is
  -- not readable through a direct invitations SELECT.
  v_inv := public.create_invitation('student', 1, 1);
  if v_inv.id is null or v_inv.role <> 'student' then
    raise exception 'FAIL: teacher could not create a student invitation';
  end if;

  select count(*) into v_count from public.invitations;
  if v_count <> 0 then
    raise exception 'FAIL: teacher can read invitation codes (%)', v_count;
  end if;
end $$;

reset role;

do $$
begin
  if (select role from public.profiles
       where id = 'b0000000-0000-0000-0000-000000000001') <> 'teacher' then
    raise exception 'FAIL: teacher own profile role was persisted';
  end if;
  if (select role from public.profiles
       where id = 'b0000000-0000-0000-0000-000000000004') <> 'student' then
    raise exception 'FAIL: teacher persisted another profile role';
  end if;
  if (select role from public.halqa_members
       where user_id = 'b0000000-0000-0000-0000-000000000004') <> 'student' then
    raise exception 'FAIL: teacher persisted a member role';
  end if;
end $$;

-- -----------------------------------------------------------------------------
-- 3) Inactive staff loses every staff/member path; active supervisor still works.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000002', false);
set role authenticated;

do $$
declare v_count integer;
begin
  if public.is_member() then
    raise exception 'FAIL: a left teacher still counts as a member';
  end if;
  if public.is_staff() then
    raise exception 'FAIL: a left teacher still counts as staff';
  end if;
  if public.is_supervisor() then
    raise exception 'FAIL: a left teacher resolved as supervisor';
  end if;

  begin
    perform public.create_invitation('student', 1, 1);
    raise exception 'FAIL: inactive staff created an invitation';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.send_message('halqa', 'P0: inactive', null, 'text', null);
    raise exception 'FAIL: inactive staff sent a halqa message';
  exception when insufficient_privilege then null;
  end;

  select count(*) into v_count from public.halqa;
  if v_count <> 0 then
    raise exception 'FAIL: inactive staff can still read halqa (%)', v_count;
  end if;

  select count(*) into v_count from public.profiles where id <> auth.uid();
  if v_count <> 0 then
    raise exception 'FAIL: inactive staff can still read other profiles (%)', v_count;
  end if;

  select count(*) into v_count from public.session_attendance;
  if v_count <> 0 then
    raise exception 'FAIL: inactive staff can still read attendance (%)', v_count;
  end if;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000003', false);
set role authenticated;

do $$
declare
  v_inv public.invitations;
  v_phone text;
begin
  if not public.is_staff() then
    raise exception 'FAIL: active supervisor is not staff';
  end if;
  if not public.is_supervisor() then
    raise exception 'FAIL: active supervisor did not resolve';
  end if;

  begin
    perform public.create_invitation('supervisor', 1, 1);
    raise exception 'FAIL: active supervisor appointed another supervisor';
  exception when insufficient_privilege then null;
  end;

  -- The supervisor may still invite teachers and students; only supervisor
  -- appointment moved to the owner in the owner-foundation migration.
  v_inv := public.create_invitation('teacher', 1, 1);
  if v_inv.id is null or v_inv.role <> 'teacher' then
    raise exception 'FAIL: active supervisor could not create a teacher invitation';
  end if;

  select phone into v_phone from public.profiles
   where id = 'b0000000-0000-0000-0000-000000000005';
  if v_phone is null then
    raise exception 'FAIL: active supervisor cannot read a member phone';
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 4) Phone privacy: names are available through the safe directory; phone is
--    owner + active supervisor only.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000004', false);
set role authenticated;

do $$
declare
  v_count integer;
  v_name  text;
begin
  select count(*) into v_count from public.profiles
   where id = 'b0000000-0000-0000-0000-000000000005';
  if v_count <> 0 then
    raise exception 'FAIL: student read another profile row (phone risk)';
  end if;

  select count(*) into v_count from public.profiles
   where id = auth.uid() and phone is not null;
  if v_count <> 1 then
    raise exception 'FAIL: student cannot read her own phone';
  end if;

  select full_name into v_name from public.profile_directory
   where id = 'b0000000-0000-0000-0000-000000000005';
  if v_name is null or v_name = '' then
    raise exception 'FAIL: safe profile directory hides member names';
  end if;

  select count(*) into v_count from public.halqa_member_directory
   where user_id = 'b0000000-0000-0000-0000-000000000005';
  if v_count <> 0 then
    raise exception 'FAIL: student read another member directory row (%)', v_count;
  end if;

  select count(*) into v_count from public.halqa_member_directory
   where user_id = auth.uid();
  if v_count <> 1 then
    raise exception 'FAIL: student cannot read her own member directory row (%)', v_count;
  end if;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000001', false);
set role authenticated;

do $$
declare
  v_count integer;
  v_name  text;
begin
  select count(*) into v_count from public.profiles
   where id = 'b0000000-0000-0000-0000-000000000005';
  if v_count <> 0 then
    raise exception 'FAIL: teacher read another profile row (phone risk)';
  end if;

  select full_name into v_name from public.profile_directory
   where id = 'b0000000-0000-0000-0000-000000000005';
  if v_name is null or v_name = '' then
    raise exception 'FAIL: teacher cannot read names through the safe directory';
  end if;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000003', false);
set role authenticated;

do $$
begin
  if (select phone from public.profiles
       where id = 'b0000000-0000-0000-0000-000000000005') is null then
    raise exception 'FAIL: supervisor cannot read a member phone';
  end if;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000006', false);
set role authenticated;

do $$
declare v_count integer;
begin
  select count(*) into v_count from public.profile_directory
   where id = 'b0000000-0000-0000-0000-000000000005';
  if v_count <> 0 then
    raise exception 'FAIL: outsider read the member directory';
  end if;

  select count(*) into v_count from public.profiles where id = auth.uid();
  if v_count <> 1 then
    raise exception 'FAIL: outsider cannot read her own profile';
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 5) Student may only raise/lower her own hand. Position/state/owner/session are
--    rejected even for a row she owns.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000004', false);
set role authenticated;

do $$
declare
  v_session       uuid := current_setting('test.p0_live_session')::uuid;
  v_other_session uuid := current_setting('test.p0_scheduled_session')::uuid;
  v_turn          public.recitation_turns;
begin
  v_turn := public.raise_hand(v_session);
  if v_turn.id is null or not v_turn.hand_raised then
    raise exception 'FAIL: raise_hand did not create a raised turn';
  end if;

  -- Lowering the hand is still allowed.
  update public.recitation_turns set hand_raised = false where id = v_turn.id;
  if (select hand_raised from public.recitation_turns where id = v_turn.id) then
    raise exception 'FAIL: student could not lower her own hand';
  end if;

  begin
    update public.recitation_turns set position = 999 where id = v_turn.id;
    raise exception 'FAIL: student changed turn position';
  exception when insufficient_privilege then null;
  end;

  begin
    update public.recitation_turns set state = 'done' where id = v_turn.id;
    raise exception 'FAIL: student changed turn state';
  exception when insufficient_privilege then null;
  end;

  begin
    update public.recitation_turns
       set student_id = 'b0000000-0000-0000-0000-000000000005'
     where id = v_turn.id;
    raise exception 'FAIL: student changed turn owner';
  exception when insufficient_privilege then null;
  end;

  begin
    update public.recitation_turns set session_id = v_other_session where id = v_turn.id;
    raise exception 'FAIL: student changed turn session';
  exception when insufficient_privilege then null;
  end;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000001', false);
set role authenticated;

do $$
declare
  v_session uuid := current_setting('test.p0_live_session')::uuid;
  v_turn    public.recitation_turns;
begin
  select * into v_turn from public.recitation_turns
   where session_id = v_session
     and student_id = 'b0000000-0000-0000-0000-000000000004'
   limit 1;
  if v_turn.id is null then
    raise exception 'FAIL: turn fixture missing for staff check';
  end if;

  update public.recitation_turns set state = 'reading' where id = v_turn.id;
  if (select state from public.recitation_turns where id = v_turn.id) <> 'reading' then
    raise exception 'FAIL: staff could not update a turn';
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 6) Messaging and receipts: active membership, private ownership, visibility.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000004', false);
set role authenticated;

do $$
declare
  v_public    uuid := current_setting('test.p0_public_message')::uuid;
  v_private_a uuid := current_setting('test.p0_private_a')::uuid;
  v_private_b uuid := current_setting('test.p0_private_b')::uuid;
  v_count     integer;
begin
  perform public.send_message('halqa', 'P0: student public', null, 'text', null);
  perform public.send_message('private', 'P0: student self', auth.uid(), 'text', null);

  begin
    perform public.send_message(
      'private', 'P0: student other',
      'b0000000-0000-0000-0000-000000000005', 'text', null);
    raise exception 'FAIL: student sent a private message for another student';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.mark_thread_read(array[v_private_b]);
    raise exception 'FAIL: student marked another student private message read';
  exception when insufficient_privilege then null;
  end;

  begin
    insert into public.message_receipts (message_id, user_id)
    values (v_private_b, auth.uid());
    raise exception 'FAIL: direct unauthorized receipt was accepted';
  exception when insufficient_privilege then null;
  end;

  select count(*) into v_count from public.message_receipts
   where message_id = v_private_b and user_id = auth.uid();
  if v_count <> 0 then
    raise exception 'FAIL: unauthorized receipt was persisted';
  end if;

  v_count := public.mark_thread_read(array[v_public, v_private_a]);
  if v_count <> 2 then
    raise exception 'FAIL: expected 2 visible receipts, got %', v_count;
  end if;

  v_count := public.mark_thread_read(array[v_public, v_private_a]);
  if v_count <> 0 then
    raise exception 'FAIL: duplicate receipts were inserted (%)', v_count;
  end if;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000006', false);
set role authenticated;

do $$
declare v_public uuid := current_setting('test.p0_public_message')::uuid;
begin
  begin
    perform public.send_message('halqa', 'P0: outsider', null, 'text', null);
    raise exception 'FAIL: outsider sent a halqa message';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.mark_thread_read(array[v_public]);
    raise exception 'FAIL: outsider wrote a read receipt';
  exception when insufficient_privilege then null;
  end;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000007', false);
set role authenticated;

do $$
declare v_public uuid := current_setting('test.p0_public_message')::uuid;
begin
  if public.is_member() then
    raise exception 'FAIL: pending student counts as an active member';
  end if;

  begin
    perform public.send_message('halqa', 'P0: pending', null, 'text', null);
    raise exception 'FAIL: pending student sent a halqa message';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.mark_thread_read(array[v_public]);
    raise exception 'FAIL: pending student wrote a read receipt';
  exception when insufficient_privilege then null;
  end;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000002', false);
set role authenticated;

do $$
declare v_public uuid := current_setting('test.p0_public_message')::uuid;
begin
  begin
    perform public.send_message('halqa', 'P0: left teacher', null, 'text', null);
    raise exception 'FAIL: left teacher sent a halqa message';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.mark_thread_read(array[v_public]);
    raise exception 'FAIL: left teacher wrote a read receipt';
  exception when insufficient_privilege then null;
  end;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000001', false);
set role authenticated;

do $$
declare
  v_private_b uuid := current_setting('test.p0_private_b')::uuid;
  v_count     integer;
begin
  perform public.send_message('halqa', 'P0: teacher public', null, 'text', null);
  perform public.send_message(
    'private', 'P0: teacher private',
    'b0000000-0000-0000-0000-000000000005', 'text', null);

  v_count := public.mark_thread_read(array[v_private_b]);
  if v_count <> 1 then
    raise exception 'FAIL: staff receipt for a private message was not recorded (%)', v_count;
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 7) Voice notes: owner and active staff only; no broad member-wide read.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000004', false);
set role authenticated;

do $$
declare v_count integer;
begin
  select count(*) into v_count from storage.objects
   where bucket_id = 'voice-notes' and name = 'p0/owner-a.webm';
  if v_count <> 1 then
    raise exception 'FAIL: owner cannot read her own voice note (%)', v_count;
  end if;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000005', false);
set role authenticated;

do $$
declare v_count integer;
begin
  select count(*) into v_count from storage.objects
   where bucket_id = 'voice-notes' and name = 'p0/owner-a.webm';
  if v_count <> 0 then
    raise exception 'FAIL: member read another member voice note (%)', v_count;
  end if;

  insert into storage.objects (bucket_id, name, owner)
  values ('voice-notes', 'p0/owner-b.webm', auth.uid());

  select count(*) into v_count from storage.objects
   where bucket_id = 'voice-notes' and name = 'p0/owner-b.webm';
  if v_count <> 1 then
    raise exception 'FAIL: member could not insert/read her own voice note (%)', v_count;
  end if;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000001', false);
set role authenticated;

do $$
declare v_count integer;
begin
  select count(*) into v_count from storage.objects
   where bucket_id = 'voice-notes' and name = 'p0/owner-a.webm';
  if v_count <> 1 then
    raise exception 'FAIL: active staff cannot read a voice note (%)', v_count;
  end if;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000006', false);
set role authenticated;

do $$
declare v_count integer;
begin
  select count(*) into v_count from storage.objects
   where bucket_id = 'voice-notes' and name = 'p0/owner-a.webm';
  if v_count <> 0 then
    raise exception 'FAIL: outsider read a voice note (%)', v_count;
  end if;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-000000000002', false);
set role authenticated;

do $$
declare v_count integer;
begin
  select count(*) into v_count from storage.objects
   where bucket_id = 'voice-notes' and name = 'p0/owner-a.webm';
  if v_count <> 0 then
    raise exception 'FAIL: inactive staff read a voice note (%)', v_count;
  end if;
end $$;

reset role;

select 'P0 AUTHORIZATION/PRIVACY TESTS PASSED' as result;
