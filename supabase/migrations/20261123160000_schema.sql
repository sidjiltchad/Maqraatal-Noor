-- =============================================================================
--  مقرأة النور — المخطط الأساسي (Supabase / PostgreSQL)
--  نموذج المجال: حلقة واحدة، عدة معلمات، طالبات كثيرات، دعوة فقط.
-- =============================================================================

create extension if not exists pgcrypto;

-- -----------------------------------------------------------------------------
-- الأنواع
-- -----------------------------------------------------------------------------
do $$ begin
  create type public.user_role as enum ('student', 'teacher', 'supervisor');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.member_level as enum ('foundation', 'tajweed', 'hifz', 'ijaza');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.member_status as enum ('active', 'paused', 'left');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.session_status as enum ('scheduled', 'live', 'ended', 'cancelled');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.attendance_status as enum ('present', 'absent', 'late', 'excused');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.turn_state as enum ('waiting', 'reading', 'done', 'skipped');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.message_kind as enum ('text', 'voice', 'system');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.thread_kind as enum ('halqa', 'private');
exception when duplicate_object then null; end $$;

-- -----------------------------------------------------------------------------
-- الحسابات: ملف شخصي لكل مستخدم في auth.users
-- -----------------------------------------------------------------------------
create table if not exists public.profiles (
  id          uuid primary key references auth.users (id) on delete cascade,
  full_name   text not null check (length(btrim(full_name)) between 2 and 80),
  role        public.user_role not null default 'student',
  phone       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.profiles is 'ملف كل مستخدم: الاسم والدور (طالبة/معلمة/مشرفة).';

-- -----------------------------------------------------------------------------
-- الحلقة: صف واحد فقط في التطبيق كله
-- -----------------------------------------------------------------------------
create table if not exists public.halqa (
  id           uuid primary key default gen_random_uuid(),
  singleton    boolean not null default true unique check (singleton),
  name         text not null default 'مقرأة النور — الحلقة العامة',
  description  text,
  days         text[] not null default array['الأحد', 'الثلاثاء', 'الخميس']::text[],
  start_time   time not null default '17:00',
  capacity     integer not null default 30 check (capacity between 1 and 500),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

comment on table public.halqa is 'الحلقة الوحيدة؛ عمود singleton يمنع إنشاء أكثر من صف.';

-- -----------------------------------------------------------------------------
-- عضوية الحلقة: الدور والمستوى والتقدم
-- -----------------------------------------------------------------------------
create table if not exists public.halqa_members (
  halqa_id        uuid not null references public.halqa (id) on delete cascade,
  user_id         uuid not null references public.profiles (id) on delete cascade,
  role            public.user_role not null default 'student',
  level           public.member_level not null default 'foundation',
  level_progress  numeric(4, 3) not null default 0 check (level_progress between 0 and 1),
  status          public.member_status not null default 'active',
  joined_at       timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  primary key (halqa_id, user_id)
);

comment on table public.halqa_members is 'من في الحلقة: معلمات وطالبات، ولكل واحدة مستواها وتقدمها.';

-- -----------------------------------------------------------------------------
-- الجلسات (الحلقة الحية)
-- -----------------------------------------------------------------------------
create table if not exists public.sessions (
  id              uuid primary key default gen_random_uuid(),
  halqa_id        uuid not null references public.halqa (id) on delete cascade,
  scheduled_at    timestamptz not null default now(),
  started_at      timestamptz,
  ended_at        timestamptz,
  status          public.session_status not null default 'scheduled',
  current_page    integer check (current_page between 1 and 700),
  current_surah   integer check (current_surah between 1 and 114),
  current_ayah    integer check (current_ayah between 1 and 300),
  current_reader  uuid references public.profiles (id) on delete set null,
  created_by      uuid references public.profiles (id) on delete set null,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

comment on table public.sessions is 'جلسة واحدة من جلسات الحلقة: الحالة وموضع المصحف والقارئة الآن.';

-- -----------------------------------------------------------------------------
-- الحضور
-- -----------------------------------------------------------------------------
create table if not exists public.session_attendance (
  session_id  uuid not null references public.sessions (id) on delete cascade,
  user_id     uuid not null references public.profiles (id) on delete cascade,
  status      public.attendance_status not null default 'present',
  marked_by   uuid references public.profiles (id) on delete set null,
  marked_at   timestamptz not null default now(),
  primary key (session_id, user_id)
);

-- -----------------------------------------------------------------------------
-- دور التلاوة (الطابور)
-- -----------------------------------------------------------------------------
create table if not exists public.recitation_turns (
  id          uuid primary key default gen_random_uuid(),
  session_id  uuid not null references public.sessions (id) on delete cascade,
  student_id  uuid not null references public.profiles (id) on delete cascade,
  position    integer not null check (position > 0),
  state       public.turn_state not null default 'waiting',
  hand_raised boolean not null default false,
  started_at  timestamptz,
  ended_at    timestamptz,
  created_at  timestamptz not null default now(),
  unique (session_id, position),
  unique (session_id, student_id)
);

comment on table public.recitation_turns is 'طابور التلاوة في الجلسة: من تقرأ الآن ومن في الانتظار.';

-- -----------------------------------------------------------------------------
-- التقييمات
-- -----------------------------------------------------------------------------
create table if not exists public.evaluations (
  id          uuid primary key default gen_random_uuid(),
  session_id  uuid not null references public.sessions (id) on delete cascade,
  student_id  uuid not null references public.profiles (id) on delete cascade,
  teacher_id  uuid not null references public.profiles (id) on delete cascade,
  tajweed     smallint not null check (tajweed between 1 and 5),
  hifz        smallint not null check (hifz between 1 and 5),
  note        text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  unique (session_id, student_id, teacher_id)
);

-- -----------------------------------------------------------------------------
-- الرسائل: عامة للحلقة أو خاصة بين معلمة وطالبة
-- -----------------------------------------------------------------------------
create table if not exists public.messages (
  id                uuid primary key default gen_random_uuid(),
  halqa_id          uuid not null references public.halqa (id) on delete cascade,
  thread            public.thread_kind not null default 'halqa',
  student_id        uuid references public.profiles (id) on delete cascade,
  sender_id         uuid not null references public.profiles (id) on delete cascade,
  kind              public.message_kind not null default 'text',
  body              text,
  duration_seconds  integer check (duration_seconds is null or duration_seconds between 0 and 3600),
  created_at        timestamptz not null default now(),
  constraint private_thread_needs_student check (thread <> 'private' or student_id is not null),
  constraint message_has_content check (
    (kind = 'text' and body is not null and length(btrim(body)) > 0)
    or (kind = 'voice' and duration_seconds is not null)
    or kind = 'system'
  )
);

create table if not exists public.message_receipts (
  message_id  uuid not null references public.messages (id) on delete cascade,
  user_id     uuid not null references public.profiles (id) on delete cascade,
  read_at     timestamptz not null default now(),
  primary key (message_id, user_id)
);

-- -----------------------------------------------------------------------------
-- الدعوات (الدخول بدعوة فقط)
-- -----------------------------------------------------------------------------
create table if not exists public.invitations (
  id          uuid primary key default gen_random_uuid(),
  code        text not null unique check (length(code) between 6 and 40),
  role        public.user_role not null default 'student',
  max_uses    integer not null default 1 check (max_uses between 1 and 100),
  uses        integer not null default 0 check (uses >= 0),
  expires_at  timestamptz,
  created_by  uuid references public.profiles (id) on delete set null,
  created_at  timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- علامة المصحف (تُزامن لاحقا بين الأجهزة)
-- -----------------------------------------------------------------------------
create table if not exists public.mushaf_bookmarks (
  user_id     uuid primary key references public.profiles (id) on delete cascade,
  mushaf_id   text not null default 'reading' check (mushaf_id in ('reading', 'tajweed')),
  page        integer not null check (page between 1 and 700),
  updated_at  timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- رموز الإشعارات (FCM)
-- -----------------------------------------------------------------------------
create table if not exists public.device_tokens (
  user_id     uuid not null references public.profiles (id) on delete cascade,
  token       text not null,
  platform    text not null default 'android' check (platform in ('android', 'ios')),
  updated_at  timestamptz not null default now(),
  primary key (user_id, token)
);

-- -----------------------------------------------------------------------------
-- الفهارس
-- -----------------------------------------------------------------------------
create index if not exists messages_halqa_time_idx on public.messages (halqa_id, created_at desc);
create index if not exists messages_private_idx on public.messages (student_id, created_at desc)
  where thread = 'private';
create index if not exists turns_session_idx on public.recitation_turns (session_id, position);
create index if not exists attendance_session_idx on public.session_attendance (session_id);
create index if not exists evaluations_student_idx on public.evaluations (student_id, created_at desc);
create index if not exists sessions_halqa_time_idx on public.sessions (halqa_id, scheduled_at desc);
create index if not exists members_user_idx on public.halqa_members (user_id);

-- -----------------------------------------------------------------------------
-- الدوال المساعدة (تتجاوز RLS لأنها security definer ومالكها postgres)
-- -----------------------------------------------------------------------------
create or replace function public.app_role()
returns public.user_role
language sql stable security definer set search_path = public, pg_temp as $$
  select role from public.profiles where id = auth.uid();
$$;

create or replace function public.is_staff()
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select coalesce(public.app_role() in ('teacher', 'supervisor'), false);
$$;

create or replace function public.is_member()
returns boolean
language sql stable security definer set search_path = public, pg_temp as $$
  select exists (
    select 1 from public.halqa_members m
    where m.user_id = auth.uid() and m.status <> 'left'
  );
$$;

create or replace function public.main_halqa_id()
returns uuid
language sql stable security definer set search_path = public, pg_temp as $$
  select id from public.halqa order by created_at limit 1;
$$;

grant execute on function public.app_role(), public.is_staff(), public.is_member(), public.main_halqa_id()
  to authenticated;

-- -----------------------------------------------------------------------------
-- المحفزات: updated_at، وإنشاء الملف الشخصي مع المستخدم الجديد
-- -----------------------------------------------------------------------------
create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

do $$
declare t text;
begin
  foreach t in array array['profiles', 'halqa', 'halqa_members', 'sessions', 'evaluations', 'mushaf_bookmarks']
  loop
    execute format('drop trigger if exists touch_%1$s on public.%1$s', t);
    execute format(
      'create trigger touch_%1$s before update on public.%1$s
       for each row execute function public.touch_updated_at()', t);
  end loop;
end $$;

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_name text;
begin
  v_name := coalesce(nullif(btrim(new.raw_user_meta_data ->> 'full_name'), ''),
                     split_part(new.email, '@', 1),
                     'عضوة جديدة');
  insert into public.profiles (id, full_name)
  values (new.id, v_name)
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- بذرة الحلقة الوحيدة
insert into public.halqa (name, description)
select 'مقرأة النور — الحلقة العامة', 'حلقة قرآنية واحدة للنساء، عدة معلمات، دخول بدعوة فقط'
where not exists (select 1 from public.halqa);
