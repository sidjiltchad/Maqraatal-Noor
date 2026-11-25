-- =============================================================================
--  اختبارات العزل (RLS) والدوال — تُنفَّذ بعد الهجرات على قاعدة اختبار.
--  كل مجموعة تفشل بصوت عالٍ إن انكسرت القاعدة.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) مستخدمات تجريبية (auth.users + المحفز ينشئ profiles)
-- -----------------------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
  ('11111111-1111-1111-1111-111111111111', 'amina@test.local',  '{"full_name":"أمينة"}'),
  ('22222222-2222-2222-2222-222222222222', 'mariam@test.local', '{"full_name":"مريم"}'),
  ('33333333-3333-3333-3333-333333333333', 'sara@test.local',   '{"full_name":"أم سارة"}'),
  ('44444444-4444-4444-4444-444444444444', 'noura@test.local',  '{"full_name":"أم نورة"}'),
  ('55555555-5555-5555-5555-555555555555', 'admin@test.local',  '{"full_name":"المشرفة"}'),
  ('66666666-6666-6666-6666-666666666666', 'outsider@test.local','{"full_name":"غريبة"}')
on conflict (id) do nothing;

update public.profiles set role = 'teacher'    where id in (
  '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444');
update public.profiles set role = 'supervisor' where id = '55555555-5555-5555-5555-555555555555';

insert into public.halqa_members (halqa_id, user_id, role, level)
select public.main_halqa_id(), p.id, p.role,
       case when p.role = 'student' then 'tajweed'::public.member_level else 'ijaza'::public.member_level end
from public.profiles p
where p.id <> '66666666-6666-6666-6666-666666666666'
on conflict (halqa_id, user_id) do nothing;

-- جلسة حية لاختبار الأدوار
insert into public.sessions (halqa_id, status, current_page, created_by)
select public.main_halqa_id(), 'live', 5, '33333333-3333-3333-3333-333333333333'
where not exists (select 1 from public.sessions);

-- رسائل: عامة + خاصة لأمينة
insert into public.messages (halqa_id, thread, student_id, sender_id, kind, body)
select public.main_halqa_id(), 'halqa', null,
       '33333333-3333-3333-3333-333333333333', 'text', 'السلام عليكم، الحلقة بعد قليل'
where not exists (select 1 from public.messages where thread = 'halqa');

insert into public.messages (halqa_id, thread, student_id, sender_id, kind, body)
select public.main_halqa_id(), 'private', '11111111-1111-1111-1111-111111111111',
       '33333333-3333-3333-3333-333333333333', 'text', 'تسميعك أحسنتِ فيه'
where not exists (select 1 from public.messages where thread = 'private');

-- حضور لأمينة ولغيرها
insert into public.session_attendance (session_id, user_id, status, marked_by)
select s.id, u.id, 'present', '33333333-3333-3333-3333-333333333333'
from public.sessions s
cross join (values ('11111111-1111-1111-1111-111111111111'::uuid),
                   ('22222222-2222-2222-2222-222222222222'::uuid)) as u(id)
where not exists (select 1 from public.session_attendance);

-- Phase 5 fixtures: teachers are scoped to explicitly assigned group/levels.
-- Teacher 333 needs both students; teacher 444 needs أمينة for the flow tests.
insert into public.groups (halqa_id, name, created_by)
select public.main_halqa_id(), 'مجموعة RLS 20261127', null
where not exists (
  select 1 from public.groups
   where halqa_id = public.main_halqa_id() and name = 'مجموعة RLS 20261127'
);

insert into public.member_groups (user_id, group_id, level)
select v.user_id, g.id, 'tajweed'
from (values ('11111111-1111-1111-1111-111111111111'::uuid),
             ('22222222-2222-2222-2222-222222222222'::uuid)) as v(user_id)
join public.groups g
  on g.halqa_id = public.main_halqa_id() and g.name = 'مجموعة RLS 20261127'
on conflict (user_id, group_id, level) do nothing;

insert into public.teacher_assignments (teacher_id, group_id, level, assigned_by)
select v.teacher_id, g.id, 'tajweed', null
from (values ('33333333-3333-3333-3333-333333333333'::uuid),
             ('44444444-4444-4444-4444-444444444444'::uuid)) as v(teacher_id)
join public.groups g
  on g.halqa_id = public.main_halqa_id() and g.name = 'مجموعة RLS 20261127'
on conflict (teacher_id, group_id, level) do nothing;

-- -----------------------------------------------------------------------------
-- 2) الطالبة أمينة: ترى نفسها، لا ترى حضور غيرها، ولا الرسائل الخاصة لغيرها
-- -----------------------------------------------------------------------------
reset role;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
set role authenticated;

do $$
declare v_count integer;
begin
  if not public.is_member() then raise exception 'FAIL: أمينة يجب أن تكون عضوة'; end if;
  if public.is_staff() then raise exception 'FAIL: أمينة ليست من الطاقم'; end if;

  select count(*) into v_count from public.session_attendance;
  if v_count <> 1 then raise exception 'FAIL: أمينة ترى % صفوف حضور، المتوقع 1', v_count; end if;

  select count(*) into v_count from public.messages where thread = 'private';
  if v_count <> 1 then raise exception 'FAIL: أمينة ترى % رسائل خاصة، المتوقع 1', v_count; end if;

  select count(*) into v_count from public.messages where thread = 'halqa';
  if v_count <> 1 then raise exception 'FAIL: أمينة لا ترى رسالة الحلقة العامة'; end if;

  insert into public.messages (halqa_id, thread, sender_id, kind, body)
  values (public.main_halqa_id(), 'halqa', auth.uid(), 'text', 'رسالتي في الحلقة');

  begin
    insert into public.messages (halqa_id, thread, student_id, sender_id, kind, body)
    values (public.main_halqa_id(), 'private',
            '22222222-2222-2222-2222-222222222222', auth.uid(), 'text', 'محاولة');
    raise exception 'FAIL: أمينة أرسلت رسالة خاصة لغيرها';
  exception when insufficient_privilege then null;  -- متوقع
  end;

  begin
    update public.profiles set role = 'teacher' where id = auth.uid();
    if (select role from public.profiles where id = auth.uid()) <> 'student' then
      raise exception 'FAIL: أمينة رفعت دورها';
    end if;
  exception when insufficient_privilege then null;
  end;

  perform public.raise_hand((select id from public.sessions limit 1));
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 3) الطالبة مريم: لا ترى الرسالة الخاصة لأمينة، وتُمنع من دوال الطاقم
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', false);
set role authenticated;

do $$
declare v_count integer;
begin
  select count(*) into v_count from public.messages where thread = 'private';
  if v_count <> 0 then raise exception 'FAIL: مريم ترى رسائل أمينة الخاصة (%)', v_count; end if;

  begin
    perform public.give_turn((select id from public.sessions limit 1), auth.uid());
    raise exception 'FAIL: طالبة أعطت الدور لنفسها';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.save_evaluation((select id from public.sessions limit 1), auth.uid(), 5::smallint, 5::smallint, 'x');
    raise exception 'FAIL: طالبة حفظت تقييما';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.create_invitation('student', 1, 7);
    raise exception 'FAIL: طالبة أنشأت دعوة';
  exception when insufficient_privilege then null;
  end;

  begin
    perform public.mark_attendance((select id from public.sessions limit 1),
                                   '11111111-1111-1111-1111-111111111111', 'absent');
    raise exception 'FAIL: طالبة عدّلت حضور غيرها';
  exception when insufficient_privilege then null;
  end;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 4) المعلمة أم سارة: ترى الكل وتدير الجلسة
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '33333333-3333-3333-3333-333333333333', false);
set role authenticated;

do $$
declare
  v_session uuid := (select id from public.sessions limit 1);
  v_count integer;
  v_turn public.recitation_turns;
begin
  if not public.is_staff() then raise exception 'FAIL: أم سارة ليست من الطاقم'; end if;

  select count(*) into v_count from public.session_attendance;
  if v_count < 2 then raise exception 'FAIL: المعلمة ترى حضور % فقط', v_count; end if;

  select count(*) into v_count from public.messages where thread = 'private';
  if v_count < 1 then raise exception 'FAIL: المعلمة لا ترى الرسائل الخاصة'; end if;

  v_turn := public.give_turn(v_session, '22222222-2222-2222-2222-222222222222');
  if v_turn.state <> 'reading' then raise exception 'FAIL: الدور لم يسند'; end if;

  perform public.mark_attendance(v_session, '22222222-2222-2222-2222-222222222222', 'present');
  perform public.save_evaluation(v_session, '22222222-2222-2222-2222-222222222222', 4::smallint, 5::smallint, 'جيد');
  perform public.set_session_position(v_session, 7, 2, 1);

  if (select current_page from public.sessions where id = v_session) <> 7 then
    raise exception 'FAIL: لم يتحدث موضع المصحف';
  end if;

  perform public.send_message('halqa', 'بداية الحلقة', null, 'text', null);
  perform public.create_invitation('student', 2, 3);
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 5) المشرفة: تُهيّئ نفسها مرة واحدة، والدعوة تعمل
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '55555555-5555-5555-5555-555555555555', false);
set role authenticated;

do $$
declare v_inv public.invitations;
begin
  if not public.is_staff() then raise exception 'FAIL: المشرفة ليست من الطاقم'; end if;

  v_inv := public.create_invitation('student', 1, 5);
  if v_inv.id is null or v_inv.role <> 'student' then
    raise exception 'FAIL: المشرفة لم تستطع إنشاء دعوة طالبة';
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 6) الغريبة (بلا عضوية): لا ترى شيئا
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '66666666-6666-6666-6666-666666666666', false);
set role authenticated;

do $$
declare v_count integer;
begin
  if public.is_member() then raise exception 'FAIL: الغريبة عضوة'; end if;

  select count(*) into v_count from public.messages;
  if v_count <> 0 then raise exception 'FAIL: الغريبة ترى رسائل (%)', v_count; end if;

  select count(*) into v_count from public.halqa;
  if v_count <> 0 then raise exception 'FAIL: الغريبة ترى الحلقة'; end if;

  select count(*) into v_count from public.halqa_members;
  if v_count <> 0 then raise exception 'FAIL: الغريبة ترى الأعضاء'; end if;
end $$;

reset role;

select 'RLS TESTS PASSED' as result;
