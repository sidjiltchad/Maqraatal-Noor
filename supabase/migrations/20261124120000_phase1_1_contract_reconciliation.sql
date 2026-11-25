-- =============================================================================
--  Phase 1.1: reconcile the local migration chain with the Android contracts.
--  Adds the columns already read/written by the app, and replaces
--  submit_join_request with the exact signature used by SessionViewModel.
--  Safe to re-run on a database that already applied part of the WIP chain.
-- =============================================================================
begin;

-- 1) Membership graduation flag: HalqaRemote.members() selects it and
--    Groups.kt patches it.
alter table public.halqa_members
    add column if not exists graduated boolean not null default false;

-- 2) Join-request contact/preference fields: AdminViewModel selects them and
--    SessionViewModel submits them.
alter table public.join_requests
    add column if not exists email text,
    add column if not exists city text,
    add column if not exists level_pref text;

do $$ begin
    if not exists (
        select 1 from pg_constraint
        where conrelid = 'public.join_requests'::regclass
          and conname = 'join_requests_level_pref_check'
    ) then
        alter table public.join_requests
            add constraint join_requests_level_pref_check
            check (level_pref is null or level_pref in ('foundation', 'tajweed', 'hifz'));
    end if;
end $$;

-- 3) Invitations belong to a halqa: decide_join_request() inserts halqa_id.
alter table public.invitations
    add column if not exists halqa_id uuid references public.halqa (id) on delete cascade;

-- New invitations (including create_invitation) default to the singleton halqa.
alter table public.invitations
    alter column halqa_id set default public.main_halqa_id();

update public.invitations
   set halqa_id = (select id from public.halqa order by created_at limit 1)
 where halqa_id is null;

-- 4) Submit RPC matches SessionViewModel: p_name, p_email, p_phone, p_city,
--    p_note, p_level. The previous three-argument overload is removed so a
--    PostgREST call cannot resolve to the wrong signature.
drop function if exists public.submit_join_request(text, text, text);

create or replace function public.submit_join_request(
    p_name  text,
    p_email text default null,
    p_phone text default null,
    p_city  text default null,
    p_note  text default null,
    p_level text default null
) returns text language plpgsql security definer set search_path = public as $$
declare
    v_open boolean;
    v_halqa uuid;
begin
    select applications_open, id into v_open, v_halqa
      from public.halqa order by created_at limit 1;

    if not coalesce(v_open, false) then
        return 'closed';
    end if;

    if length(trim(coalesce(p_name, ''))) < 3 then
        raise exception 'الاسم قصير جدا';
    end if;

    if p_level is not null and p_level not in ('foundation', 'tajweed', 'hifz') then
        raise exception 'مستوى غير صالح';
    end if;

    -- منع التكرار خلال 24 ساعة بنفس الاسم والهاتف (بعد التطبيع).
    if exists (
        select 1 from public.join_requests
        where halqa_id = v_halqa
          and lower(trim(full_name)) = lower(trim(p_name))
          and coalesce(phone, '') =
              coalesce(nullif(trim(coalesce(p_phone, '')), ''), '')
          and created_at > now() - interval '24 hours'
    ) then
        return 'duplicate';
    end if;

    insert into public.join_requests
        (halqa_id, full_name, email, phone, city, level_pref, note)
    values
        (v_halqa, trim(p_name),
         nullif(trim(coalesce(p_email, '')), ''),
         nullif(trim(coalesce(p_phone, '')), ''),
         nullif(trim(coalesce(p_city, '')), ''),
         nullif(trim(coalesce(p_level, '')), ''),
         nullif(trim(coalesce(p_note, '')), ''));

    return 'submitted';
end; $$;

grant execute on function public.submit_join_request(text, text, text, text, text, text)
    to anon, authenticated;

commit;
