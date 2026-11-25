-- =============================================================================
--  طلبات الالتحاق: الطالبة ترسل من شاشة الدخول، والمشرفة تفتح/تغلق وتقرر.
-- =============================================================================

-- 1) مفتاح فتح الطلبات على مستوى المقرأة (افتراضيا مغلق)
alter table public.halqa
    add column if not exists applications_open boolean not null default false;

-- 2) جدول الطلبات
create table if not exists public.join_requests (
    id             uuid primary key default gen_random_uuid(),
    halqa_id       uuid not null references public.halqa (id) on delete cascade,
    full_name      text not null check (length(trim(full_name)) >= 3),
    phone          text,
    note           text,
    status         text not null default 'pending'
                   check (status in ('pending', 'accepted', 'rejected')),
    invitation_code text,
    created_at     timestamptz not null default now(),
    decided_by     uuid references public.profiles (id),
    decided_at     timestamptz
);

create index if not exists join_requests_status_idx
    on public.join_requests (status, created_at desc);

alter table public.join_requests enable row level security;

-- المشرفة وحدها تقرأ الطلبات. الإدخال يتم حصرا عبر الدالة (security definer).
drop policy if exists join_requests_read on public.join_requests;
create policy join_requests_read on public.join_requests for select to authenticated
    using (public.is_supervisor());

-- 3) إرسال طلب من غير المسجّلات (anon)
create or replace function public.submit_join_request(
    p_name  text,
    p_phone text default null,
    p_note  text default null
) returns text language plpgsql security definer set search_path = public as $$
declare
    v_open boolean;
    v_halqa uuid;
begin
    select applications_open, id into v_open, v_halqa from public.halqa order by created_at limit 1;

    if not coalesce(v_open, false) then
        return 'closed';
    end if;

    if length(trim(coalesce(p_name, ''))) < 3 then
        raise exception 'الاسم قصير جدا';
    end if;

    -- منع التكرار خلال 24 ساعة بنفس الاسم والهاتف
    if exists (
        select 1 from public.join_requests
        where halqa_id = v_halqa
          and lower(trim(full_name)) = lower(trim(p_name))
          and coalesce(phone, '') = coalesce(p_phone, '')
          and created_at > now() - interval '24 hours'
    ) then
        return 'duplicate';
    end if;

    insert into public.join_requests (halqa_id, full_name, phone, note)
    values (v_halqa, trim(p_name), nullif(trim(coalesce(p_phone, '')), ''), nullif(trim(coalesce(p_note, '')), ''));

    return 'submitted';
end; $$;

-- 4) فتح/إغلاق استقبال الطلبات (المشرفة)
create or replace function public.set_applications_open(p_open boolean)
returns boolean language plpgsql security definer set search_path = public as $$
begin
    if not public.is_supervisor() then raise exception 'غير مصرح'; end if;
    update public.halqa set applications_open = p_open;
    return p_open;
end; $$;

-- 5) حالة الاستقبال (يقرأها أي زائر ليعرف هل النموذج متاح)
create or replace function public.applications_open()
returns boolean language sql stable security definer set search_path = public as $$
    select coalesce((select applications_open from public.halqa order by created_at limit 1), false);
$$;

-- 6) قرار المشرفة: قبول (يولّد كود دعوة) أو رفض
create or replace function public.decide_join_request(p_id uuid, p_accept boolean)
returns text language plpgsql security definer set search_path = public as $$
declare
    v_req    public.join_requests;
    v_code   text;
    v_halqa  uuid;
begin
    if not public.is_supervisor() then raise exception 'غير مصرح'; end if;

    select * into v_req from public.join_requests where id = p_id;
    if v_req.id is null then raise exception 'الطلب غير موجود'; end if;

    if p_accept then
        select id into v_halqa from public.halqa order by created_at limit 1;
        v_code := upper(substr(md5(gen_random_uuid()::text), 1, 8));

        insert into public.invitations (halqa_id, code, role, max_uses, uses, expires_at, created_by)
        values (v_halqa, v_code, 'student', 1, 0, now() + interval '30 days', auth.uid());

        update public.join_requests
           set status = 'accepted', invitation_code = v_code,
               decided_by = auth.uid(), decided_at = now()
         where id = p_id;

        return v_code;
    else
        update public.join_requests
           set status = 'rejected', decided_by = auth.uid(), decided_at = now()
         where id = p_id;
        return null;
    end if;
end; $$;

grant execute on function public.submit_join_request(text, text, text) to anon, authenticated;
grant execute on function public.applications_open() to anon, authenticated;
grant execute on function public.set_applications_open(boolean) to authenticated;
grant execute on function public.decide_join_request(uuid, boolean) to authenticated;
