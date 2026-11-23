-- =============================================================================
--  بيئة اختبار محلية: تحاكي ما يوفره Supabase قبل الهجرات.
--  تُنفَّذ فقط على قاعدة اختبار محلية، لا على Supabase.
-- =============================================================================

create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;

create schema if not exists auth;
create schema if not exists storage;

create table if not exists auth.users (
  id uuid primary key default gen_random_uuid(),
  email text unique,
  raw_user_meta_data jsonb default '{}'::jsonb
);

-- auth.uid() في Supabase يقرأ الـJWT؛ هنا نقرأه من GUC للاختبار.
create or replace function auth.uid()
returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
$$;

create table if not exists storage.buckets (
  id text primary key,
  name text not null,
  public boolean not null default false
);

create table if not exists storage.objects (
  id uuid primary key default gen_random_uuid(),
  bucket_id text references storage.buckets (id),
  name text,
  owner uuid
);

create publication supabase_realtime;

grant usage on schema auth, storage to authenticated;
grant select, insert on auth.users to authenticated;
grant select on storage.buckets to authenticated;
grant select, insert on storage.objects to authenticated;
