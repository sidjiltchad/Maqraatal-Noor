-- =============================================================================
--  Phase 5 acceptance tests: teacher/student assignment scoping.
--  Run only on a disposable local PostgreSQL after every migration.
--  Success prints: GROUP ASSIGNMENT SCOPING TESTS PASSED
--
--  Covers supervisor assignment/removal, direct-write denials, cross-teacher
--  isolation, assigned-student access, student self-scope, pending-member
--  denial, and teacher proposal vs supervisor approval.
-- =============================================================================
\set ON_ERROR_STOP on

-- -----------------------------------------------------------------------------
-- 0) Fixtures.  Synthetic local UUIDs only; no account or credential is used.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '', false);
select set_config('request.jwt.claim.role', '', false);
select set_config('app.authorized_role_change', 'on', false);

insert into auth.users (id, email, raw_user_meta_data) values
  ('d0000000-0000-0000-0000-000000000001', 'g70-supervisor@test.local', '{"full_name":"مشرفة G70"}'),
  ('d0000000-0000-0000-0000-000000000002', 'g70-teacher1@test.local',   '{"full_name":"معلمة G70 أ"}'),
  ('d0000000-0000-0000-0000-000000000003', 'g70-teacher2@test.local',   '{"full_name":"معلمة G70 ب"}'),
  ('d0000000-0000-0000-0000-000000000004', 'g70-student1@test.local',   '{"full_name":"طالبة G70 أ"}'),
  ('d0000000-0000-0000-0000-000000000005', 'g70-student2@test.local',   '{"full_name":"طالبة G70 ب"}'),
  ('d0000000-0000-0000-0000-000000000006', 'g70-student3@test.local',   '{"full_name":"طالبة G70 ج"}'),
  ('d0000000-0000-0000-0000-000000000007', 'g70-pending@test.local',    '{"full_name":"معلقة G70"}'),
  ('d0000000-0000-0000-0000-000000000008', 'g70-unassigned@test.local', '{"full_name":"طالبة غير مسندة"}')
on conflict (id) do nothing;

update public.profiles set role = 'supervisor'
 where id = 'd0000000-0000-0000-0000-000000000001';
update public.profiles set role = 'teacher'
 where id in ('d0000000-0000-0000-0000-000000000002',
              'd0000000-0000-0000-0000-000000000003');
update public.profiles set role = 'student'
 where id in ('d0000000-0000-0000-0000-000000000004',
              'd0000000-0000-0000-0000-000000000005',
              'd0000000-0000-0000-0000-000000000006',
              'd0000000-0000-0000-0000-000000000007',
              'd0000000-0000-0000-0000-000000000008');

select set_config('app.authorized_role_change', 'off', false);

insert into public.halqa_members (halqa_id, user_id, role, level, status) values
  (public.main_halqa_id(), 'd0000000-0000-0000-0000-000000000001', 'supervisor', 'ijaza',      'active'),
  (public.main_halqa_id(), 'd0000000-0000-0000-0000-000000000002', 'teacher',    'ijaza',      'active'),
  (public.main_halqa_id(), 'd0000000-0000-0000-0000-000000000003', 'teacher',    'ijaza',      'active'),
  (public.main_halqa_id(), 'd0000000-0000-0000-0000-000000000004', 'student',    'tajweed',    'active'),
  (public.main_halqa_id(), 'd0000000-0000-0000-0000-000000000005', 'student',    'tajweed',    'active'),
  (public.main_halqa_id(), 'd0000000-0000-0000-0000-000000000006', 'student',    'hifz',       'active'),
  (public.main_halqa_id(), 'd0000000-0000-0000-0000-000000000007', 'student',    'foundation', 'pending'),
  (public.main_halqa_id(), 'd0000000-0000-0000-0000-000000000008', 'student',    'foundation', 'active')
on conflict (halqa_id, user_id) do update
  set role = excluded.role, level = excluded.level, status = excluded.status;

insert into public.groups (halqa_id, name, created_by) values
  (public.main_halqa_id(), 'G70 مجموعة أ', 'd0000000-0000-0000-0000-000000000001'),
  (public.main_halqa_id(), 'G70 مجموعة ب', 'd0000000-0000-0000-0000-000000000001')
on conflict (halqa_id, name) do nothing;

-- Capture the group ids as the owner.  Teachers cannot read the other group
-- through RLS, so tests must pass an id they were given, not re-query groups.
select set_config('test.g70_group_a', id::text, false)
  from public.groups where name = 'G70 مجموعة أ';
select set_config('test.g70_group_b', id::text, false)
  from public.groups where name = 'G70 مجموعة ب';

insert into public.member_groups (user_id, group_id, level)
select v.user_id, g.id, v.level
  from (values
    ('d0000000-0000-0000-0000-000000000004'::uuid, 'G70 مجموعة أ', 'tajweed'),
    ('d0000000-0000-0000-0000-000000000005'::uuid, 'G70 مجموعة أ', 'tajweed'),
    ('d0000000-0000-0000-0000-000000000006'::uuid, 'G70 مجموعة ب', 'hifz')
  ) as v(user_id, group_name, level)
  join public.groups g on g.halqa_id = public.main_halqa_id() and g.name = v.group_name
on conflict (user_id, group_id, level) do nothing;

insert into public.teacher_assignments (teacher_id, group_id, level, assigned_by)
select v.teacher_id, g.id, v.level, 'd0000000-0000-0000-0000-000000000001'
  from (values
    ('d0000000-0000-0000-0000-000000000002'::uuid, 'G70 مجموعة أ', 'tajweed'),
    ('d0000000-0000-0000-0000-000000000003'::uuid, 'G70 مجموعة ب', 'hifz')
  ) as v(teacher_id, group_name, level)
  join public.groups g on g.halqa_id = public.main_halqa_id() and g.name = v.group_name
on conflict (teacher_id, group_id, level) do nothing;

insert into public.sessions (id, halqa_id, status, current_page, created_by)
values ('d2000000-0000-0000-0000-000000000001', public.main_halqa_id(), 'live', 5,
        'd0000000-0000-0000-0000-000000000001')
on conflict (id) do nothing;

insert into public.session_attendance (session_id, user_id, status, marked_by) values
  ('d2000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000004', 'present', 'd0000000-0000-0000-0000-000000000001'),
  ('d2000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000005', 'present', 'd0000000-0000-0000-0000-000000000001'),
  ('d2000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000006', 'present', 'd0000000-0000-0000-0000-000000000001')
on conflict (session_id, user_id) do nothing;

insert into public.evaluations (session_id, student_id, teacher_id, tajweed, hifz, note) values
  ('d2000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000002', 4, 4, 'G70 s1'),
  ('d2000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000002', 4, 4, 'G70 s2'),
  ('d2000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000006', 'd0000000-0000-0000-0000-000000000003', 5, 5, 'G70 s3')
on conflict (session_id, student_id, teacher_id) do nothing;

insert into public.messages (id, halqa_id, thread, student_id, sender_id, kind, body) values
  ('d3000000-0000-0000-0000-000000000001', public.main_halqa_id(), 'private', 'd0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000002', 'text', 'G70 private s1'),
  ('d3000000-0000-0000-0000-000000000002', public.main_halqa_id(), 'private', 'd0000000-0000-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000002', 'text', 'G70 private s2'),
  ('d3000000-0000-0000-0000-000000000003', public.main_halqa_id(), 'private', 'd0000000-0000-0000-0000-000000000006', 'd0000000-0000-0000-0000-000000000003', 'text', 'G70 private s3'),
  ('d3000000-0000-0000-0000-000000000004', public.main_halqa_id(), 'halqa', null, 'd0000000-0000-0000-0000-000000000001', 'text', 'G70 public')
on conflict (id) do nothing;

insert into public.recitation_turns (id, session_id, student_id, position, state) values
  ('d4000000-0000-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000004', 1, 'waiting'),
  ('d4000000-0000-0000-0000-000000000002', 'd2000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000005', 2, 'waiting'),
  ('d4000000-0000-0000-0000-000000000003', 'd2000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000006', 3, 'waiting')
on conflict (session_id, student_id) do nothing;

-- -----------------------------------------------------------------------------
-- 1) Supervisor positive assignment/removal for a teacher and a student.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-000000000001', false);
set role authenticated;

do $$
declare
  v_group uuid;
begin
  if not public.is_supervisor() then
    raise exception 'FAIL: G70 supervisor does not resolve';
  end if;

  v_group := public.create_group('G70 مجموعة مؤقتة');

  perform public.assign_teacher(
    'd0000000-0000-0000-0000-000000000002', v_group, 'foundation');
  if not exists (
    select 1 from public.teacher_assignments
     where teacher_id = 'd0000000-0000-0000-0000-000000000002'
       and group_id = v_group
       and level = 'foundation'
  ) then
    raise exception 'FAIL: supervisor could not assign a teacher';
  end if;

  perform public.remove_teacher_assignment(
    'd0000000-0000-0000-0000-000000000002', v_group, 'foundation');
  if exists (
    select 1 from public.teacher_assignments
     where teacher_id = 'd0000000-0000-0000-0000-000000000002'
       and group_id = v_group
       and level = 'foundation'
  ) then
    raise exception 'FAIL: supervisor could not remove a teacher assignment';
  end if;

  perform public.assign_member_group(
    'd0000000-0000-0000-0000-000000000004', v_group, 'foundation');
  if not exists (
    select 1 from public.member_groups
     where user_id = 'd0000000-0000-0000-0000-000000000004'
       and group_id = v_group
       and level = 'foundation'
  ) then
    raise exception 'FAIL: supervisor could not assign a student';
  end if;

  -- Reassignment inside the same group replaces the previous level.
  perform public.assign_member_group(
    'd0000000-0000-0000-0000-000000000004', v_group, 'tajweed');
  if exists (
    select 1 from public.member_groups
     where user_id = 'd0000000-0000-0000-0000-000000000004'
       and group_id = v_group
       and level = 'foundation'
  ) then
    raise exception 'FAIL: supervisor reassignment left the old level';
  end if;
  if not exists (
    select 1 from public.member_groups
     where user_id = 'd0000000-0000-0000-0000-000000000004'
       and group_id = v_group
       and level = 'tajweed'
  ) then
    raise exception 'FAIL: supervisor could not reassign a student level';
  end if;

  perform public.remove_member_group(
    'd0000000-0000-0000-0000-000000000004', v_group, 'tajweed');
  if exists (
    select 1 from public.member_groups
     where user_id = 'd0000000-0000-0000-0000-000000000004'
       and group_id = v_group
       and level = 'tajweed'
  ) then
    raise exception 'FAIL: supervisor could not remove a student assignment';
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 2) A teacher cannot write assignment or role fields directly.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-000000000002', false);
set role authenticated;

do $$
declare
  v_rows integer;
begin
  v_rows := 0;
  update public.halqa_members
     set level = 'hifz'
   where user_id = 'd0000000-0000-0000-0000-000000000004';
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then
    raise exception 'FAIL: teacher changed a member level directly';
  end if;
  if (select level from public.halqa_members
       where user_id = 'd0000000-0000-0000-0000-000000000004') <> 'tajweed' then
    raise exception 'FAIL: teacher level write persisted';
  end if;

  begin
    insert into public.member_groups (user_id, group_id, level)
    values ('d0000000-0000-0000-0000-000000000004',
            current_setting('test.g70_group_a')::uuid,
            'hifz');
    raise exception 'FAIL: teacher inserted a student assignment';
  exception when insufficient_privilege then null;
  end;

  v_rows := 0;
  update public.member_groups set level = 'hifz'
   where user_id = 'd0000000-0000-0000-0000-000000000004';
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then
    raise exception 'FAIL: teacher updated a student assignment';
  end if;

  v_rows := 0;
  delete from public.member_groups
   where user_id = 'd0000000-0000-0000-0000-000000000004';
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then
    raise exception 'FAIL: teacher deleted a student assignment';
  end if;

  begin
    insert into public.teacher_assignments (teacher_id, group_id, level)
    values ('d0000000-0000-0000-0000-000000000002',
            current_setting('test.g70_group_a')::uuid,
            'hifz');
    raise exception 'FAIL: teacher inserted her own assignment';
  exception when insufficient_privilege then null;
  end;

  v_rows := 0;
  update public.teacher_assignments set level = 'foundation'
   where teacher_id = 'd0000000-0000-0000-0000-000000000002';
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then
    raise exception 'FAIL: teacher updated a teacher assignment';
  end if;

  v_rows := 0;
  delete from public.teacher_assignments
   where teacher_id = 'd0000000-0000-0000-0000-000000000002';
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then
    raise exception 'FAIL: teacher deleted a teacher assignment';
  end if;

  v_rows := 0;
  update public.groups set name = 'G70 hacked'
   where name = 'G70 مجموعة أ';
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then
    raise exception 'FAIL: teacher renamed a group';
  end if;

  v_rows := 0;
  update public.halqa_members set role = 'supervisor'
   where user_id = 'd0000000-0000-0000-0000-000000000004';
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then
    raise exception 'FAIL: teacher changed a member role directly';
  end if;

  begin
    update public.profiles set role = 'supervisor'
     where id = auth.uid();
    raise exception 'FAIL: teacher changed her own profile role';
  exception when insufficient_privilege then null;
  end;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 3) A student cannot write assignment or role fields directly.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-000000000004', false);
set role authenticated;

do $$
declare
  v_rows integer;
begin
  v_rows := 0;
  update public.halqa_members set level = 'hifz'
   where user_id = auth.uid();
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then
    raise exception 'FAIL: student changed her member level directly';
  end if;

  begin
    insert into public.member_groups (user_id, group_id, level)
    values (auth.uid(), current_setting('test.g70_group_a')::uuid, 'hifz');
    raise exception 'FAIL: student inserted her own assignment';
  exception when insufficient_privilege then null;
  end;

  begin
    insert into public.teacher_assignments (teacher_id, group_id, level)
    values (auth.uid(), current_setting('test.g70_group_a')::uuid, 'hifz');
    raise exception 'FAIL: student made herself a teacher assignment';
  exception when insufficient_privilege then null;
  end;

  v_rows := 0;
  update public.member_groups set level = 'hifz'
   where user_id = auth.uid();
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then
    raise exception 'FAIL: student updated her own assignment';
  end if;

  v_rows := 0;
  update public.halqa_members set role = 'teacher'
   where user_id = auth.uid();
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then
    raise exception 'FAIL: student changed her member role directly';
  end if;

  begin
    update public.profiles set role = 'teacher'
     where id = auth.uid();
    raise exception 'FAIL: student changed her profile role';
  exception when insufficient_privilege then null;
  end;

  v_rows := 0;
  update public.groups set name = 'G70 student hack'
   where name = 'G70 مجموعة أ';
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then
    raise exception 'FAIL: student renamed a group';
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 4) Teacher 1: assigned-student access and cross-teacher isolation.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-000000000002', false);
set role authenticated;

do $$
declare
  v_session uuid := 'd2000000-0000-0000-0000-000000000001';
  v_private_s1 uuid := 'd3000000-0000-0000-0000-000000000001';
  v_private_s3 uuid := 'd3000000-0000-0000-0000-000000000003';
  v_count integer;
  v_rows integer;
  v_prop uuid;
begin
  if not public.is_staff() or public.is_supervisor() then
    raise exception 'FAIL: G70 teacher1 has the wrong staff state';
  end if;

  select count(*) into v_count from public.halqa_members
   where user_id = 'd0000000-0000-0000-0000-000000000004';
  if v_count <> 1 then raise exception 'FAIL: teacher1 cannot read assigned student1'; end if;

  select count(*) into v_count from public.halqa_members
   where user_id = 'd0000000-0000-0000-0000-000000000005';
  if v_count <> 1 then raise exception 'FAIL: teacher1 cannot read assigned student2'; end if;

  select count(*) into v_count from public.halqa_members
   where user_id = 'd0000000-0000-0000-0000-000000000006';
  if v_count <> 0 then raise exception 'FAIL: teacher1 read cross-group student3'; end if;

  select count(*) into v_count from public.halqa_members
   where user_id = 'd0000000-0000-0000-0000-000000000008';
  if v_count <> 0 then raise exception 'FAIL: teacher1 read an unassigned student'; end if;

  select count(*) into v_count from public.halqa_members
   where user_id = 'd0000000-0000-0000-0000-000000000003';
  if v_count <> 0 then raise exception 'FAIL: teacher1 read another teacher'; end if;

  select count(*) into v_count from public.halqa_members where user_id = auth.uid();
  if v_count <> 1 then raise exception 'FAIL: teacher1 cannot read her own membership'; end if;

  select count(*) into v_count from public.member_groups
   where user_id = 'd0000000-0000-0000-0000-000000000004';
  if v_count <> 1 then raise exception 'FAIL: teacher1 cannot read assigned student assignment'; end if;

  select count(*) into v_count from public.member_groups
   where user_id = 'd0000000-0000-0000-0000-000000000006';
  if v_count <> 0 then raise exception 'FAIL: teacher1 read cross-group assignment'; end if;

  select count(*) into v_count from public.groups where name = 'G70 مجموعة ب';
  if v_count <> 0 then raise exception 'FAIL: teacher1 read the other group'; end if;

  select count(*) into v_count from public.session_attendance
   where user_id = 'd0000000-0000-0000-0000-000000000006';
  if v_count <> 0 then raise exception 'FAIL: teacher1 read cross-group attendance'; end if;

  select count(*) into v_count from public.evaluations
   where student_id = 'd0000000-0000-0000-0000-000000000006';
  if v_count <> 0 then raise exception 'FAIL: teacher1 read cross-group evaluation'; end if;

  select count(*) into v_count from public.recitation_turns
   where student_id = 'd0000000-0000-0000-0000-000000000006';
  if v_count <> 0 then raise exception 'FAIL: teacher1 read cross-group turn'; end if;

  select count(*) into v_count from public.messages where id = v_private_s3;
  if v_count <> 0 then raise exception 'FAIL: teacher1 read cross-group private message'; end if;

  if public.can_see_message(v_private_s3) then
    raise exception 'FAIL: can_see_message allowed a cross-group private message';
  end if;

  if not public.teaches_student('d0000000-0000-0000-0000-000000000004') then
    raise exception 'FAIL: teacher1 does not resolve her assigned student';
  end if;
  if public.teaches_student('d0000000-0000-0000-0000-000000000006') then
    raise exception 'FAIL: teacher1 resolves a cross-group student';
  end if;

  -- Positive own-student operations.
  perform public.give_turn(v_session, 'd0000000-0000-0000-0000-000000000004');
  perform public.mark_attendance(v_session, 'd0000000-0000-0000-0000-000000000004', 'present');
  perform public.save_evaluation(v_session, 'd0000000-0000-0000-0000-000000000004', 4::smallint, 4::smallint, 'G70 own');
  perform public.send_message('private', 'G70 own message',
    'd0000000-0000-0000-0000-000000000004', 'text', null);

  if public.mark_thread_read(array[v_private_s1]) <> 1 then
    raise exception 'FAIL: teacher1 could not mark an assigned private message read';
  end if;

  -- Negative cross-teacher operations.
  begin
    perform public.give_turn(v_session, 'd0000000-0000-0000-0000-000000000006');
    raise exception 'FAIL: teacher1 gave a turn to a cross-group student';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.mark_attendance(v_session, 'd0000000-0000-0000-0000-000000000006', 'present');
    raise exception 'FAIL: teacher1 marked a cross-group student present';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.save_evaluation(v_session, 'd0000000-0000-0000-0000-000000000006', 5::smallint, 5::smallint, 'x');
    raise exception 'FAIL: teacher1 evaluated a cross-group student';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.send_message('private', 'G70 cross',
      'd0000000-0000-0000-0000-000000000006', 'text', null);
    raise exception 'FAIL: teacher1 messaged a cross-group student';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.mark_thread_read(array[v_private_s3]);
    raise exception 'FAIL: teacher1 marked a cross-group private message read';
  exception when insufficient_privilege then null;
  end;

  -- Proposals: assigned student is allowed; cross-group student is not.
  v_prop := public.propose_group(
    'd0000000-0000-0000-0000-000000000004',
    current_setting('test.g70_group_b')::uuid,
    'hifz', 'G70 transfer proposal');
  if v_prop is null then
    raise exception 'FAIL: teacher1 could not propose for an assigned student';
  end if;
  perform set_config('test.g70_proposal', v_prop::text, false);

  v_rows := 0;
  begin
    update public.group_proposals set status = 'accepted' where id = v_prop;
    get diagnostics v_rows = row_count;
  exception when insufficient_privilege then v_rows := 0;
  end;
  if v_rows <> 0 then
    raise exception 'FAIL: teacher decided a proposal by direct update';
  end if;
  if (select status from public.group_proposals where id = v_prop) <> 'pending' then
    raise exception 'FAIL: teacher proposal status changed before approval';
  end if;

  begin
    perform public.propose_group(
      'd0000000-0000-0000-0000-000000000006',
      current_setting('test.g70_group_a')::uuid,
      'tajweed', 'cross');
    raise exception 'FAIL: teacher1 proposed for a cross-group student';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.decide_proposal(v_prop, true);
    raise exception 'FAIL: teacher1 decided her own proposal';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.assign_member_group(
      'd0000000-0000-0000-0000-000000000004',
      current_setting('test.g70_group_b')::uuid, 'hifz');
    raise exception 'FAIL: teacher1 assigned a student directly';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.remove_member_group(
      'd0000000-0000-0000-0000-000000000004',
      current_setting('test.g70_group_a')::uuid, 'tajweed');
    raise exception 'FAIL: teacher1 removed a student assignment directly';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.assign_teacher(auth.uid(),
      current_setting('test.g70_group_b')::uuid, 'hifz');
    raise exception 'FAIL: teacher1 assigned herself';
  exception when insufficient_privilege then null;
  end;

end $$;

reset role;

do $$
begin
  -- Owner-side check: the teacher's proposal must not mutate the assignment
  -- before supervisor approval.  Teacher RLS cannot see the other group, so this
  -- assertion must run as the migration owner.
  if exists (
    select 1 from public.member_groups
     where user_id = 'd0000000-0000-0000-0000-000000000004'
       and group_id = current_setting('test.g70_group_b')::uuid
  ) then
    raise exception 'FAIL: teacher proposal mutated the assignment before approval';
  end if;
end $$;

-- -----------------------------------------------------------------------------
-- 5) Teacher 2 sees the other group only.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-000000000003', false);
set role authenticated;

do $$
declare
  v_session uuid := 'd2000000-0000-0000-0000-000000000001';
  v_private_s1 uuid := 'd3000000-0000-0000-0000-000000000001';
  v_count integer;
begin
  select count(*) into v_count from public.halqa_members
   where user_id = 'd0000000-0000-0000-0000-000000000006';
  if v_count <> 1 then raise exception 'FAIL: teacher2 cannot read her assigned student'; end if;

  select count(*) into v_count from public.halqa_members
   where user_id = 'd0000000-0000-0000-0000-000000000004';
  if v_count <> 0 then raise exception 'FAIL: teacher2 read teacher1 student'; end if;

  select count(*) into v_count from public.session_attendance
   where user_id = 'd0000000-0000-0000-0000-000000000004';
  if v_count <> 0 then raise exception 'FAIL: teacher2 read teacher1 attendance'; end if;

  select count(*) into v_count from public.evaluations
   where student_id = 'd0000000-0000-0000-0000-000000000004';
  if v_count <> 0 then raise exception 'FAIL: teacher2 read teacher1 evaluation'; end if;

  select count(*) into v_count from public.recitation_turns
   where student_id = 'd0000000-0000-0000-0000-000000000004';
  if v_count <> 0 then raise exception 'FAIL: teacher2 read teacher1 turn'; end if;

  select count(*) into v_count from public.member_groups
   where user_id = 'd0000000-0000-0000-0000-000000000004';
  if v_count <> 0 then raise exception 'FAIL: teacher2 read teacher1 assignment'; end if;

  select count(*) into v_count from public.teacher_assignments
   where teacher_id = 'd0000000-0000-0000-0000-000000000002';
  if v_count <> 0 then raise exception 'FAIL: teacher2 read teacher1 teacher assignment'; end if;

  select count(*) into v_count from public.teacher_assignments
   where teacher_id = auth.uid();
  if v_count <> 1 then raise exception 'FAIL: teacher2 cannot read her own teacher assignment'; end if;

  select count(*) into v_count from public.messages where id = v_private_s1;
  if v_count <> 0 then raise exception 'FAIL: teacher2 read teacher1 private message'; end if;

  begin
    perform public.give_turn(v_session, 'd0000000-0000-0000-0000-000000000004');
    raise exception 'FAIL: teacher2 gave a turn to teacher1 student';
  exception when insufficient_privilege then null;
  end;

  perform public.give_turn(v_session, 'd0000000-0000-0000-0000-000000000006');
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 6) Student self-scope and pending-member denial.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-000000000004', false);
set role authenticated;

do $$
declare
  v_count integer;
begin
  if not public.is_member() then raise exception 'FAIL: student1 is not an active member'; end if;

  select count(*) into v_count from public.halqa_members
   where user_id = 'd0000000-0000-0000-0000-000000000004';
  if v_count <> 1 then raise exception 'FAIL: student1 cannot read her own membership'; end if;

  select count(*) into v_count from public.halqa_members
   where user_id = 'd0000000-0000-0000-0000-000000000005';
  if v_count <> 0 then raise exception 'FAIL: student1 read another member row'; end if;

  select count(*) into v_count from public.member_groups
   where user_id = 'd0000000-0000-0000-0000-000000000004';
  if v_count <> 1 then raise exception 'FAIL: student1 cannot read her own assignment'; end if;

  select count(*) into v_count from public.member_groups
   where user_id = 'd0000000-0000-0000-0000-000000000005';
  if v_count <> 0 then raise exception 'FAIL: student1 read another member assignment'; end if;

  select count(*) into v_count from public.groups where name = 'G70 مجموعة ب';
  if v_count <> 0 then raise exception 'FAIL: student1 read an unassigned group'; end if;

  select count(*) into v_count from public.session_attendance
   where user_id = 'd0000000-0000-0000-0000-000000000005';
  if v_count <> 0 then raise exception 'FAIL: student1 read another attendance row'; end if;

  select count(*) into v_count from public.evaluations
   where student_id = 'd0000000-0000-0000-0000-000000000005';
  if v_count <> 0 then raise exception 'FAIL: student1 read another evaluation'; end if;

  select count(*) into v_count from public.recitation_turns
   where student_id = 'd0000000-0000-0000-0000-000000000005';
  if v_count <> 0 then raise exception 'FAIL: student1 read another turn'; end if;

  select count(*) into v_count from public.messages
   where id = 'd3000000-0000-0000-0000-000000000002';
  if v_count <> 0 then raise exception 'FAIL: student1 read another private message'; end if;

  select count(*) into v_count from public.halqa_member_directory
   where user_id = 'd0000000-0000-0000-0000-000000000005';
  if v_count <> 0 then raise exception 'FAIL: student1 read another member directory row'; end if;

  select count(*) into v_count from public.profile_directory
   where id = 'd0000000-0000-0000-0000-000000000005';
  if v_count <> 1 then raise exception 'FAIL: student1 lost the safe name directory'; end if;

  perform public.send_message('halqa', 'G70 student direct', null, 'text', null);
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-000000000007', false);
set role authenticated;

do $$
declare
  v_count integer;
begin
  if public.is_member() then
    raise exception 'FAIL: pending student counts as an active member';
  end if;

  select count(*) into v_count from public.halqa;
  if v_count <> 0 then raise exception 'FAIL: pending student read the halqa'; end if;

  select count(*) into v_count from public.groups;
  if v_count <> 0 then raise exception 'FAIL: pending student read groups'; end if;

  select count(*) into v_count from public.member_groups;
  if v_count <> 0 then raise exception 'FAIL: pending student read assignments'; end if;

  select count(*) into v_count from public.sessions;
  if v_count <> 0 then raise exception 'FAIL: pending student read the schedule'; end if;

  select count(*) into v_count from public.messages;
  if v_count <> 0 then raise exception 'FAIL: pending student read messages'; end if;

  select count(*) into v_count from public.session_attendance;
  if v_count <> 0 then raise exception 'FAIL: pending student read attendance'; end if;

  select count(*) into v_count from public.evaluations;
  if v_count <> 0 then raise exception 'FAIL: pending student read evaluations'; end if;

  select count(*) into v_count from public.recitation_turns;
  if v_count <> 0 then raise exception 'FAIL: pending student read turns'; end if;

  select count(*) into v_count from public.halqa_member_directory where user_id = auth.uid();
  if v_count <> 1 then raise exception 'FAIL: pending student cannot see her own status row'; end if;

  begin
    perform public.send_message('halqa', 'G70 pending', null, 'text', null);
    raise exception 'FAIL: pending student sent a message';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.propose_group(
      'd0000000-0000-0000-0000-000000000004',
      current_setting('test.g70_group_b')::uuid, 'hifz', null);
    raise exception 'FAIL: pending student proposed a transfer';
  exception when insufficient_privilege then null;
  end;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 7) Supervisor inherits teacher actions and approves the proposal.
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-000000000001', false);
set role authenticated;

do $$
declare
  v_session uuid := 'd2000000-0000-0000-0000-000000000001';
  v_proposal uuid := current_setting('test.g70_proposal', true)::uuid;
  v_group_b uuid := current_setting('test.g70_group_b')::uuid;
  v_count integer;
begin
  if not public.is_supervisor() or not public.is_staff() then
    raise exception 'FAIL: supervisor does not inherit staff/supervisor capability';
  end if;

  -- Supervisor can act as a teacher without a teacher_assignments row.
  perform public.give_turn(v_session, 'd0000000-0000-0000-0000-000000000006');
  perform public.mark_attendance(v_session, 'd0000000-0000-0000-0000-000000000006', 'present');
  perform public.save_evaluation(v_session, 'd0000000-0000-0000-0000-000000000006', 5::smallint, 5::smallint, 'G70 sup');
  perform public.send_message('private', 'G70 sup message',
    'd0000000-0000-0000-0000-000000000006', 'text', null);

  if not public.can_see_message('d3000000-0000-0000-0000-000000000003') then
    raise exception 'FAIL: supervisor cannot see a member private message';
  end if;

  -- Approval is the mutation point.
  perform public.decide_proposal(v_proposal, true);

  select count(*) into v_count from public.member_groups
   where user_id = 'd0000000-0000-0000-0000-000000000004'
     and group_id = v_group_b
     and level = 'hifz';
  if v_count <> 1 then
    raise exception 'FAIL: supervisor approval did not perform the assignment';
  end if;

  if (select status from public.group_proposals where id = v_proposal) <> 'accepted' then
    raise exception 'FAIL: proposal was not marked accepted';
  end if;

  begin
    perform public.decide_proposal(v_proposal, false);
    raise exception 'FAIL: a settled proposal was decided again';
  exception when invalid_parameter_value then null;
  end;

  perform public.remove_member_group(
    'd0000000-0000-0000-0000-000000000004', v_group_b, 'hifz');
  select count(*) into v_count from public.member_groups
   where user_id = 'd0000000-0000-0000-0000-000000000004'
     and group_id = v_group_b
     and level = 'hifz';
  if v_count <> 0 then
    raise exception 'FAIL: supervisor could not remove the approved assignment';
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 8) Final invariant checks as the migration owner.
-- -----------------------------------------------------------------------------
do $$
begin
  if (select level from public.halqa_members
       where user_id = 'd0000000-0000-0000-0000-000000000004') <> 'tajweed' then
    raise exception 'FAIL: a direct write changed student1 level';
  end if;
  if (select role from public.halqa_members
       where user_id = 'd0000000-0000-0000-0000-000000000004') <> 'student' then
    raise exception 'FAIL: a direct write changed student1 role';
  end if;
  if (select role from public.profiles
       where id = 'd0000000-0000-0000-0000-000000000002') <> 'teacher' then
    raise exception 'FAIL: a direct write changed teacher1 profile role';
  end if;
  if exists (
    select 1 from public.member_groups
     where user_id = 'd0000000-0000-0000-0000-000000000004'
       and group_id = current_setting('test.g70_group_b')::uuid
  ) then
    raise exception 'FAIL: the approved test assignment was not removed';
  end if;
end $$;

select 'GROUP ASSIGNMENT SCOPING TESTS PASSED' as result;
