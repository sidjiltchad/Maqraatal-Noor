-- =============================================================================
--  اختبارات المسارات الكاملة: دعوة مستخدمة جديدة، التهيئة مرة واحدة،
--  إيصالات القراءة، إنهاء الجلسة، وإعادة إسناد الدور.
-- =============================================================================

-- مستخدمة جديدة سجّلت للتو: ملف شخصي بلا عضوية
insert into auth.users (id, email, raw_user_meta_data)
values ('77777777-7777-7777-7777-777777777777', 'hind@test.local', '{"full_name":"هند"}')
on conflict (id) do nothing;

-- Phase 5 fixture: teacher 444 operates on أمينة, so the assignment must exist.
insert into public.groups (halqa_id, name, created_by)
select public.main_halqa_id(), 'مجموعة RLS 20261127', null
where not exists (
  select 1 from public.groups
   where halqa_id = public.main_halqa_id() and name = 'مجموعة RLS 20261127'
);

insert into public.member_groups (user_id, group_id, level)
select '11111111-1111-1111-1111-111111111111', g.id, 'tajweed'
from public.groups g
where g.halqa_id = public.main_halqa_id() and g.name = 'مجموعة RLS 20261127'
on conflict (user_id, group_id, level) do nothing;

insert into public.teacher_assignments (teacher_id, group_id, level, assigned_by)
select '44444444-4444-4444-4444-444444444444', g.id, 'tajweed', null
from public.groups g
where g.halqa_id = public.main_halqa_id() and g.name = 'مجموعة RLS 20261127'
on conflict (teacher_id, group_id, level) do nothing;

do $$
declare v_count integer;
begin
  select count(*) into v_count from public.profiles where id = '77777777-7777-7777-7777-777777777777';
  if v_count <> 1 then raise exception 'FAIL: لم يُنشأ ملف المستخدمة الجديدة تلقائيا'; end if;

  select count(*) into v_count from public.halqa_members where user_id = '77777777-7777-7777-7777-777777777777';
  if v_count <> 0 then raise exception 'FAIL: المستخدمة الجديدة صارت عضوة بلا دعوة'; end if;
end $$;

-- -----------------------------------------------------------------------------
-- 1) دعوة جديدة تُنشأ من المعلمة وتُستهلك من هند
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '33333333-3333-3333-3333-333333333333', false);
set role authenticated;

do $$
declare v_inv public.invitations;
begin
  v_inv := public.create_invitation('student', 1, 5);
  if v_inv.code is null or length(v_inv.code) < 6 then
    raise exception 'FAIL: كود الدعوة غير صالح';
  end if;
  perform set_config('test.invite_code', v_inv.code, false);
end $$;

reset role;

select set_config('request.jwt.claim.sub', '77777777-7777-7777-7777-777777777777', false);
set role authenticated;

do $$
declare
  v_code text := current_setting('test.invite_code');
  v_member public.halqa_members;
begin
  if public.is_member() then raise exception 'FAIL: هند عضوة قبل الدعوة'; end if;

  v_member := public.redeem_invitation(v_code);
  if v_member.user_id <> auth.uid() then raise exception 'FAIL: الدعوة لم تنضم هند'; end if;
  if v_member.status <> 'pending' then raise exception 'FAIL: دعوة الطالبة يجب أن تبدأ بعضوية معلّقة'; end if;
  if public.is_member() then raise exception 'FAIL: العضوية المعلّقة عُدّت فعّالة قبل موافقة المشرفة'; end if;

  -- اعتماد العضوية من المشرفة بعد استهلاك الدعوة.
  perform set_config('request.jwt.claim.sub', '55555555-5555-5555-5555-555555555555', false);
  perform public.accept_member('77777777-7777-7777-7777-777777777777');
  perform set_config('request.jwt.claim.sub', '77777777-7777-7777-7777-777777777777', false);
  if not public.is_member() then raise exception 'FAIL: لم تصبح هند عضوة بعد موافقة المشرفة'; end if;

  -- الكود استُهلك: محاولة ثانية من مستخدمة أخرى تفشل
  perform set_config('test.invite_code', v_code, false);
end $$;

reset role;

-- مستخدمة أخرى تحاول نفس الكود المستهلك
insert into auth.users (id, email, raw_user_meta_data)
values ('88888888-8888-8888-8888-888888888888', 'salma@test.local', '{"full_name":"سلمى"}')
on conflict (id) do nothing;

select set_config('request.jwt.claim.sub', '88888888-8888-8888-8888-888888888888', false);
set role authenticated;

do $$
declare v_code text := current_setting('test.invite_code');
begin
  begin
    perform public.redeem_invitation(v_code);
    raise exception 'FAIL: كود مستهلك قُبل مرة ثانية';
  exception when invalid_parameter_value then null;  -- متوقع
  end;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 2) التهيئة مرة واحدة فقط
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '88888888-8888-8888-8888-888888888888', false);
set role authenticated;

do $$
begin
  begin
    perform public.bootstrap_supervisor();
    raise exception 'FAIL: التهيئة نُفّذت رغم وجود الطاقم';
  exception when insufficient_privilege then null;  -- متوقع
  end;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 3) إيصالات القراءة
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
set role authenticated;

do $$
declare
  v_ids uuid[];
  v_count integer;
begin
  select array_agg(id) into v_ids from public.messages;
  if v_ids is null then raise exception 'FAIL: لا رسائل للاختبار'; end if;

  v_count := public.mark_thread_read(v_ids);
  if v_count < 1 then raise exception 'FAIL: لم تُسجَّل إيصالات القراءة'; end if;

  -- إعادة التعليم لا تُكرر الصفوف
  v_count := public.mark_thread_read(v_ids);
  if v_count <> 0 then raise exception 'FAIL: الإيصالات تكررت (%)', v_count; end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 4) إنهاء الجلسة يُغلق الطابور، وإعادة إسناد الدور تُحدّث ولا تُكرر
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '44444444-4444-4444-4444-444444444444', false);
set role authenticated;

do $$
declare
  v_session uuid := (select id from public.sessions limit 1);
  v_count integer;
begin
  perform public.give_turn(v_session, '11111111-1111-1111-1111-111111111111');
  perform public.give_turn(v_session, '11111111-1111-1111-1111-111111111111');

  select count(*) into v_count
  from public.recitation_turns
  where session_id = v_session and student_id = '11111111-1111-1111-1111-111111111111';
  if v_count <> 1 then raise exception 'FAIL: تكرر صف الدور للطالبة نفسها (%)', v_count; end if;

  select count(*) into v_count
  from public.recitation_turns
  where session_id = v_session and state = 'reading';
  if v_count <> 1 then raise exception 'FAIL: أكثر من قارئة واحدة في الوقت نفسه (%)', v_count; end if;

  perform public.end_session(v_session);

  select count(*) into v_count
  from public.recitation_turns
  where session_id = v_session and state in ('waiting', 'reading');
  if v_count <> 0 then raise exception 'FAIL: الطابور لم يُغلق بعد الإنهاء'; end if;

  if (select status from public.sessions where id = v_session) <> 'ended' then
    raise exception 'FAIL: حالة الجلسة لم تنتهِ';
  end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- 5) قيود البيانات تمنع المدخلات الفاسدة
-- -----------------------------------------------------------------------------
do $$
begin
  begin
    insert into public.evaluations (session_id, student_id, teacher_id, tajweed, hifz)
    values ((select id from public.sessions limit 1), '11111111-1111-1111-1111-111111111111',
            '33333333-3333-3333-3333-333333333333', 9, 3);
    raise exception 'FAIL: تقييم بقيمة 9 قُبل';
  exception when check_violation then null;
  end;

  begin
    insert into public.halqa (name) values ('حلقة ثانية');
    raise exception 'FAIL: قُبلت حلقة ثانية';
  exception when unique_violation then null;
  end;

  begin
    insert into public.messages (halqa_id, thread, student_id, sender_id, kind, body)
    values (public.main_halqa_id(), 'private', null,
            '33333333-3333-3333-3333-333333333333', 'text', 'بلا طالبة');
    raise exception 'FAIL: رسالة خاصة بلا طالبة قُبلت';
  exception when check_violation then null;
  end;
end $$;

select 'FLOW TESTS PASSED' as result;
