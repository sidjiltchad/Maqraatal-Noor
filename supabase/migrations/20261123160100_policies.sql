-- =============================================================================
--  مقرأة النور — سياسات الوصول (RLS)
--  القاعدة: الطالبة ترى نفسها ورسائلها الخاصة؛ المعلمات والمشرفة يرين الحلقة كلها.
-- =============================================================================

alter table public.profiles            enable row level security;
alter table public.halqa               enable row level security;
alter table public.halqa_members       enable row level security;
alter table public.sessions            enable row level security;
alter table public.session_attendance  enable row level security;
alter table public.recitation_turns    enable row level security;
alter table public.evaluations         enable row level security;
alter table public.messages            enable row level security;
alter table public.message_receipts    enable row level security;
alter table public.invitations         enable row level security;
alter table public.mushaf_bookmarks    enable row level security;
alter table public.device_tokens       enable row level security;

-- -----------------------------------------------------------------------------
-- profiles
-- -----------------------------------------------------------------------------
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles
  for select to authenticated
  using (id = auth.uid() or public.is_member());

drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles
  for update to authenticated
  using (id = auth.uid())
  with check (id = auth.uid() and role = public.app_role());  -- لا تغيير للدور من نفسها

drop policy if exists profiles_update_staff on public.profiles;
create policy profiles_update_staff on public.profiles
  for update to authenticated
  using (public.is_staff())
  with check (public.is_staff());

-- -----------------------------------------------------------------------------
-- halqa
-- -----------------------------------------------------------------------------
drop policy if exists halqa_select on public.halqa;
create policy halqa_select on public.halqa
  for select to authenticated
  using (public.is_member());

drop policy if exists halqa_update_staff on public.halqa;
create policy halqa_update_staff on public.halqa
  for update to authenticated
  using (public.is_staff())
  with check (public.is_staff());

-- -----------------------------------------------------------------------------
-- halqa_members
-- -----------------------------------------------------------------------------
drop policy if exists members_select on public.halqa_members;
create policy members_select on public.halqa_members
  for select to authenticated
  using (public.is_member());

drop policy if exists members_write_staff on public.halqa_members;
create policy members_write_staff on public.halqa_members
  for all to authenticated
  using (public.is_staff())
  with check (public.is_staff());

-- -----------------------------------------------------------------------------
-- sessions
-- -----------------------------------------------------------------------------
drop policy if exists sessions_select on public.sessions;
create policy sessions_select on public.sessions
  for select to authenticated
  using (public.is_member());

drop policy if exists sessions_write_staff on public.sessions;
create policy sessions_write_staff on public.sessions
  for all to authenticated
  using (public.is_staff())
  with check (public.is_staff());

-- -----------------------------------------------------------------------------
-- session_attendance: الطالبة ترى حضورها فقط
-- -----------------------------------------------------------------------------
drop policy if exists attendance_select on public.session_attendance;
create policy attendance_select on public.session_attendance
  for select to authenticated
  using (public.is_staff() or user_id = auth.uid());

drop policy if exists attendance_write_staff on public.session_attendance;
create policy attendance_write_staff on public.session_attendance
  for all to authenticated
  using (public.is_staff())
  with check (public.is_staff());

-- -----------------------------------------------------------------------------
-- recitation_turns: الجميع يرى الطابور (مطلوب في الحلقة الحية)
-- -----------------------------------------------------------------------------
drop policy if exists turns_select on public.recitation_turns;
create policy turns_select on public.recitation_turns
  for select to authenticated
  using (public.is_member());

drop policy if exists turns_write_staff on public.recitation_turns;
create policy turns_write_staff on public.recitation_turns
  for all to authenticated
  using (public.is_staff())
  with check (public.is_staff());

-- رفع اليد: الطالبة تعدّل دورها فقط، وبعمود اليد فقط via RPC
drop policy if exists turns_raise_own on public.recitation_turns;
create policy turns_raise_own on public.recitation_turns
  for update to authenticated
  using (student_id = auth.uid())
  with check (student_id = auth.uid());

-- -----------------------------------------------------------------------------
-- evaluations: صاحبة التقييم أو الطاقم
-- -----------------------------------------------------------------------------
drop policy if exists evaluations_select on public.evaluations;
create policy evaluations_select on public.evaluations
  for select to authenticated
  using (public.is_staff() or student_id = auth.uid());

drop policy if exists evaluations_write_staff on public.evaluations;
create policy evaluations_write_staff on public.evaluations
  for all to authenticated
  using (public.is_staff())
  with check (public.is_staff());

-- -----------------------------------------------------------------------------
-- messages
-- -----------------------------------------------------------------------------
drop policy if exists messages_select on public.messages;
create policy messages_select on public.messages
  for select to authenticated
  using (
    public.is_member()
    and (
      thread = 'halqa'
      or public.is_staff()
      or student_id = auth.uid()
    )
  );

drop policy if exists messages_insert on public.messages;
create policy messages_insert on public.messages
  for insert to authenticated
  with check (
    sender_id = auth.uid()
    and public.is_member()
    and (
      thread = 'halqa'
      or public.is_staff()
      or student_id = auth.uid()
    )
  );

drop policy if exists messages_delete_own on public.messages;
create policy messages_delete_own on public.messages
  for delete to authenticated
  using (sender_id = auth.uid() or public.is_staff());

-- إيصالات القراءة
drop policy if exists receipts_select on public.message_receipts;
create policy receipts_select on public.message_receipts
  for select to authenticated
  using (public.is_staff() or user_id = auth.uid());

drop policy if exists receipts_write_own on public.message_receipts;
create policy receipts_write_own on public.message_receipts
  for insert to authenticated
  with check (user_id = auth.uid());

-- -----------------------------------------------------------------------------
-- invitations: الطاقم فقط، والاستهلاك عبر دالة redeem_invitation
-- -----------------------------------------------------------------------------
drop policy if exists invitations_staff on public.invitations;
create policy invitations_staff on public.invitations
  for all to authenticated
  using (public.is_staff())
  with check (public.is_staff());

-- -----------------------------------------------------------------------------
-- mushaf_bookmarks / device_tokens: صاحبتها فقط
-- -----------------------------------------------------------------------------
drop policy if exists bookmarks_own on public.mushaf_bookmarks;
create policy bookmarks_own on public.mushaf_bookmarks
  for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

drop policy if exists tokens_own on public.device_tokens;
create policy tokens_own on public.device_tokens
  for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

-- -----------------------------------------------------------------------------
-- المزامنة اللحظية
-- -----------------------------------------------------------------------------
do $$
declare t text;
begin
  foreach t in array array['sessions', 'recitation_turns', 'session_attendance', 'messages', 'evaluations']
  loop
    begin
      execute format('alter publication supabase_realtime add table public.%I', t);
    exception when duplicate_object then null;
    end;
  end loop;
end $$;

-- -----------------------------------------------------------------------------
-- تخزين الرسائل الصوتية
-- -----------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('voice-notes', 'voice-notes', false)
on conflict (id) do nothing;

drop policy if exists voice_notes_read on storage.objects;
create policy voice_notes_read on storage.objects
  for select to authenticated
  using (bucket_id = 'voice-notes' and public.is_member());

drop policy if exists voice_notes_write on storage.objects;
create policy voice_notes_write on storage.objects
  for insert to authenticated
  with check (bucket_id = 'voice-notes' and public.is_member());

-- -----------------------------------------------------------------------------
-- الصلاحيات الأساسية (RLS تحكم الصفوف، وهذه تسمح بالوصول للجداول أصلا)
-- -----------------------------------------------------------------------------
grant usage on schema public to authenticated;
grant select, insert, update, delete on all tables in schema public to authenticated;
grant usage, select on all sequences in schema public to authenticated;
alter default privileges in schema public
  grant select, insert, update, delete on tables to authenticated;