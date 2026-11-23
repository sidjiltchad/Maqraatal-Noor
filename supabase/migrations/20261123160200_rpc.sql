-- =============================================================================
--  مقرأة النور — الدوال التنفيذية (RPC)
--  كل عملية حسّاسة تمر من هنا: تحقق من الدور داخل الدالة، لا تُترك للعميل.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- التهيئة: أول مستخدم يصبح مشرفة عامة (مرة واحدة فقط)
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

  if exists (select 1 from public.profiles where role in ('teacher', 'supervisor') and id <> auth.uid()) then
    raise exception 'المقرأة مهيأة مسبقا' using errcode = '42501';
  end if;

  update public.profiles set role = 'supervisor' where id = auth.uid();
  v_halqa := public.main_halqa_id();

  insert into public.halqa_members (halqa_id, user_id, role, level)
  values (v_halqa, auth.uid(), 'supervisor', 'ijaza')
  on conflict (halqa_id, user_id) do update set role = 'supervisor', status = 'active';

  return 'supervisor';
end;
$$;

grant execute on function public.bootstrap_supervisor() to authenticated;

-- -----------------------------------------------------------------------------
-- الدعوات: إنشاء (للطاقم) واستهلاك (للمدعوة)
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

create or replace function public.redeem_invitation(p_code text)
returns public.halqa_members
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_inv public.invitations;
  v_halqa uuid;
  v_member public.halqa_members;
begin
  if auth.uid() is null then
    raise exception 'يجب تسجيل الدخول' using errcode = '28000';
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

  update public.invitations set uses = uses + 1 where id = v_inv.id;
  update public.profiles set role = v_inv.role where id = auth.uid();
  v_halqa := public.main_halqa_id();

  insert into public.halqa_members (halqa_id, user_id, role, level)
  values (v_halqa, auth.uid(), v_inv.role, 'foundation')
  on conflict (halqa_id, user_id) do update
    set role = excluded.role, status = 'active'
  returning * into v_member;

  return v_member;
end;
$$;

grant execute on function public.redeem_invitation(text) to authenticated;

-- -----------------------------------------------------------------------------
-- الجلسات
-- -----------------------------------------------------------------------------
create or replace function public.start_session(p_session uuid)
returns public.sessions
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_row public.sessions;
begin
  if not public.is_staff() then raise exception 'غير مصرح' using errcode = '42501'; end if;

  update public.sessions
     set status = 'live', started_at = coalesce(started_at, now())
   where id = p_session and status in ('scheduled', 'live')
  returning * into v_row;

  if v_row.id is null then raise exception 'الجلسة غير موجودة أو منتهية' using errcode = '22023'; end if;
  return v_row;
end;
$$;
grant execute on function public.start_session(uuid) to authenticated;

create or replace function public.end_session(p_session uuid)
returns public.sessions
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_row public.sessions;
begin
  if not public.is_staff() then raise exception 'غير مصرح' using errcode = '42501'; end if;

  update public.sessions
     set status = 'ended', ended_at = now(), current_reader = null
   where id = p_session
  returning * into v_row;

  if v_row.id is null then raise exception 'الجلسة غير موجودة' using errcode = '22023'; end if;

  update public.recitation_turns
     set state = 'skipped', ended_at = now()
   where session_id = p_session and state in ('waiting', 'reading');

  return v_row;
end;
$$;
grant execute on function public.end_session(uuid) to authenticated;

create or replace function public.set_session_position(
  p_session uuid,
  p_page integer,
  p_surah integer default null,
  p_ayah integer default null
)
returns public.sessions
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_row public.sessions;
begin
  if not public.is_staff() then raise exception 'غير مصرح' using errcode = '42501'; end if;

  update public.sessions
     set current_page = p_page,
         current_surah = coalesce(p_surah, current_surah),
         current_ayah = coalesce(p_ayah, current_ayah)
   where id = p_session
  returning * into v_row;

  if v_row.id is null then raise exception 'الجلسة غير موجودة' using errcode = '22023'; end if;
  return v_row;
end;
$$;
grant execute on function public.set_session_position(uuid, integer, integer, integer) to authenticated;

-- -----------------------------------------------------------------------------
-- دور التلاوة
-- -----------------------------------------------------------------------------
create or replace function public.give_turn(p_session uuid, p_student uuid)
returns public.recitation_turns
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_turn public.recitation_turns;
  v_position integer;
begin
  if not public.is_staff() then raise exception 'غير مصرح' using errcode = '42501'; end if;

  if not exists (
    select 1 from public.halqa_members
    where user_id = p_student and status = 'active'
  ) then
    raise exception 'الطالبة ليست من الحلقة' using errcode = '22023';
  end if;

  update public.recitation_turns
     set state = 'done', ended_at = now()
   where session_id = p_session and state = 'reading';

  update public.recitation_turns
     set state = 'reading', started_at = now(), hand_raised = false
   where session_id = p_session and student_id = p_student
  returning * into v_turn;

  if v_turn.id is null then
    select coalesce(max(position), 0) + 1 into v_position
    from public.recitation_turns where session_id = p_session;

    insert into public.recitation_turns (session_id, student_id, position, state, started_at)
    values (p_session, p_student, v_position, 'reading', now())
    returning * into v_turn;
  end if;

  update public.sessions set current_reader = p_student where id = p_session;
  return v_turn;
end;
$$;
grant execute on function public.give_turn(uuid, uuid) to authenticated;

create or replace function public.raise_hand(p_session uuid)
returns public.recitation_turns
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_turn public.recitation_turns;
  v_position integer;
begin
  if auth.uid() is null then raise exception 'يجب تسجيل الدخول' using errcode = '28000'; end if;

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
-- الحضور والتقييم
-- -----------------------------------------------------------------------------
create or replace function public.mark_attendance(
  p_session uuid,
  p_user uuid,
  p_status public.attendance_status
)
returns public.session_attendance
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_row public.session_attendance;
begin
  if not public.is_staff() then raise exception 'غير مصرح' using errcode = '42501'; end if;

  insert into public.session_attendance (session_id, user_id, status, marked_by)
  values (p_session, p_user, p_status, auth.uid())
  on conflict (session_id, user_id) do update
    set status = excluded.status, marked_by = auth.uid(), marked_at = now()
  returning * into v_row;

  return v_row;
end;
$$;
grant execute on function public.mark_attendance(uuid, uuid, public.attendance_status) to authenticated;

create or replace function public.save_evaluation(
  p_session uuid,
  p_student uuid,
  p_tajweed smallint,
  p_hifz smallint,
  p_note text default null
)
returns public.evaluations
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_row public.evaluations;
begin
  if not public.is_staff() then raise exception 'غير مصرح' using errcode = '42501'; end if;

  insert into public.evaluations (session_id, student_id, teacher_id, tajweed, hifz, note)
  values (p_session, p_student, auth.uid(), p_tajweed, p_hifz, nullif(btrim(coalesce(p_note, '')), ''))
  on conflict (session_id, student_id, teacher_id) do update
    set tajweed = excluded.tajweed, hifz = excluded.hifz, note = excluded.note, updated_at = now()
  returning * into v_row;

  return v_row;
end;
$$;
grant execute on function public.save_evaluation(uuid, uuid, smallint, smallint, text) to authenticated;

-- -----------------------------------------------------------------------------
-- الرسائل: إرسال (مع تحقق الملكية) وتعليم كمقروء
-- -----------------------------------------------------------------------------
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
  if auth.uid() is null then raise exception 'يجب تسجيل الدخول' using errcode = '28000';
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
declare v_count integer;
begin
  if auth.uid() is null then raise exception 'يجب تسجيل الدخول' using errcode = '28000'; end if;

  insert into public.message_receipts (message_id, user_id)
  select unnest(p_message_ids), auth.uid()
  on conflict (message_id, user_id) do nothing;

  get diagnostics v_count = row_count;
  return v_count;
end;
$$;
grant execute on function public.mark_thread_read(uuid[]) to authenticated;
