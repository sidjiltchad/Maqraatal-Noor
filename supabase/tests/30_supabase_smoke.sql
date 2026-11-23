-- =============================================================================
--  دخان على قاعدة Supabase الحقيقية: ينشئ بيانات تجريبية ثم يتراجع (ROLLBACK)
--  فلا يبقى أثر. النجاح = SMOKE PASSED.
-- =============================================================================
begin;

-- -----------------------------------------------------------------------------
-- مستخدمات مؤقتة (تُلغى مع التراجع)
-- -----------------------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data, aud, role) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'smoke-teacher@example.test', '{"full_name":"معلمة اختبار"}', 'authenticated', 'authenticated'),
  ('aaaaaaaa-0000-0000-0000-000000000002', 'smoke-student1@example.test', '{"full_name":"طالبة أولى"}', 'authenticated', 'authenticated'),
  ('aaaaaaaa-0000-0000-0000-000000000003', 'smoke-student2@example.test', '{"full_name":"طالبة ثانية"}', 'authenticated', 'authenticated'),
  ('aaaaaaaa-0000-0000-0000-000000000004', 'smoke-outsider@example.test', '{"full_name":"غريبة"}', 'authenticated', 'authenticated')
on conflict (id) do nothing;

update public.profiles set role = 'teacher'
 where id = 'aaaaaaaa-0000-0000-0000-000000000001';

insert into public.halqa_members (halqa_id, user_id, role, level)
select public.main_halqa_id(), id, role, 'tajweed'
from public.profiles
where id in ('aaaaaaaa-0000-0000-0000-000000000001',
             'aaaaaaaa-0000-0000-0000-000000000002',
             'aaaaaaaa-0000-0000-0000-000000000003')
on conflict (halqa_id, user_id) do nothing;

  -- (no-op if absent; يبقي السكربت صالحا في أي بيئة)

insert into public.sessions (halqa_id, status, current_page, created_by)
values (public.main_halqa_id(), 'live', 5, 'aaaaaaaa-0000-0000-0000-000000000001');

insert into public.session_attendance (session_id, user_id, status, marked_by)
select s.id, u.id, 'present', 'aaaaaaaa-0000-0000-0000-000000000001'
from public.sessions s,
     (values ('aaaaaaaa-0000-0000-0000-000000000002'::uuid),
             ('aaaaaaaa-0000-0000-0000-000000000003'::uuid)) as u(id);

insert into public.messages (halqa_id, thread, student_id, sender_id, kind, body)
values (public.main_halqa_id(), 'halqa', null, 'aaaaaaaa-0000-0000-0000-000000000001', 'text', 'رسالة عامة');

insert into public.messages (halqa_id, thread, student_id, sender_id, kind, body)
values (public.main_halqa_id(), 'private', 'aaaaaaaa-0000-0000-0000-000000000002',
        'aaaaaaaa-0000-0000-0000-000000000001', 'text', 'خاصة بالطالبة الأولى');

-- -----------------------------------------------------------------------------
-- المعلمة
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-0000-0000-000000000001', true);
set local role authenticated;

do $$
declare
  v_session uuid := (select id from public.sessions order by created_at desc limit 1);
  v_count integer;
begin
  if not public.is_staff() then raise exception 'SMOKE FAIL: المعلمة ليست طاقما'; end if;

  select count(*) into v_count from public.session_attendance where session_id = v_session;
  if v_count <> 2 then raise exception 'SMOKE FAIL: المعلمة ترى % حضور', v_count; end if;

  perform public.give_turn(v_session, 'aaaaaaaa-0000-0000-0000-000000000002');
  perform public.save_evaluation(v_session, 'aaaaaaaa-0000-0000-0000-000000000002', 4::smallint, 5::smallint, 'اختبار');

  select count(*) into v_count from public.messages where thread = 'private';
  if v_count <> 1 then raise exception 'SMOKE FAIL: المعلمة لا ترى الخاصة'; end if;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- الطالبة الأولى: حضورها فقط ورسالتها فقط
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-0000-0000-000000000002', true);
set local role authenticated;

do $$
declare v_count integer;
begin
  if public.is_staff() then raise exception 'SMOKE FAIL: الطالبة طاقم'; end if;

  select count(*) into v_count from public.session_attendance;
  if v_count <> 1 then raise exception 'SMOKE FAIL: الطالبة ترى % حضور', v_count; end if;

  select count(*) into v_count from public.messages where thread = 'private';
  if v_count <> 1 then raise exception 'SMOKE FAIL: الطالبة ترى % خاصة', v_count; end if;

  begin
    perform public.give_turn((select id from public.sessions limit 1),
                             'aaaaaaaa-0000-0000-0000-000000000003');
    raise exception 'SMOKE FAIL: طالبة أعطت الدور';
  exception when insufficient_privilege then null;
  end;
end $$;

reset role;

-- -----------------------------------------------------------------------------
-- الطالبة الثانية والغريبة
-- -----------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-0000-0000-000000000003', true);
set local role authenticated;

do $$
declare v_count integer;
begin
  select count(*) into v_count from public.messages where thread = 'private';
  if v_count <> 0 then raise exception 'SMOKE FAIL: طالبة ترى رسالة غيرها'; end if;
end $$;

reset role;

select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-0000-0000-000000000004', true);
set local role authenticated;

do $$
declare v_count integer;
begin
  select count(*) into v_count from public.messages;
  if v_count <> 0 then raise exception 'SMOKE FAIL: غريبة ترى رسائل'; end if;
  select count(*) into v_count from public.halqa;
  if v_count <> 0 then raise exception 'SMOKE FAIL: غريبة ترى الحلقة'; end if;
end $$;

reset role;

rollback;

select 'SMOKE PASSED' as result;
