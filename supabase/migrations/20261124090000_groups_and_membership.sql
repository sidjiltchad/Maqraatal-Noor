-- 1) قيم جديدة لحالة العضوية (ENUM): منتظرة ومحظورة
alter type public.member_status add value if not exists 'pending';
alter type public.member_status add value if not exists 'banned';

-- 1.1) تعريف is_supervisor() قبل أي سياسة تستخدمه. كانت السياسات أدناه
-- تُنشأ قبل تعريف الدالة في 20261124090100_admin_rpc.sql، فيفشل التثبيت
-- الجديد عند إنشاء السياسة. تبقى الدالة قابلة لإعادة التعريف بأمان.
create or replace function public.is_supervisor()
returns boolean language sql stable security definer set search_path = public as $$
    select exists (
        select 1 from public.profiles p
        join public.halqa_members m on m.user_id = p.id
        where p.id = auth.uid() and p.role = 'supervisor' and m.status = 'active'
    );
$$;

-- 2) المجموعات: لكل مستوى مجموعات بأسماء
create table if not exists public.groups (
    id          uuid primary key default gen_random_uuid(),
    halqa_id    uuid not null references public.halqa (id) on delete cascade,
    level       text not null check (level in ('foundation', 'tajweed', 'hifz')),
    name        text not null,
    created_by  uuid references public.profiles (id),
    created_at  timestamptz not null default now(),
    unique (halqa_id, level, name)
);

-- 3) عضوية الطالبة في مجموعة
create table if not exists public.member_groups (
    user_id   uuid not null references public.profiles (id) on delete cascade,
    group_id  uuid not null references public.groups (id) on delete cascade,
    joined_at timestamptz not null default now(),
    primary key (user_id, group_id)
);

-- 4) اقتراحات المعلمة للمشرفة
create table if not exists public.group_proposals (
    id          uuid primary key default gen_random_uuid(),
    student_id  uuid not null references public.profiles (id) on delete cascade,
    group_id    uuid references public.groups (id) on delete cascade,
    note        text,
    proposed_by uuid not null references public.profiles (id),
    status      text not null default 'pending' check (status in ('pending', 'accepted', 'rejected')),
    created_at  timestamptz not null default now()
);

create index if not exists groups_halqa_idx on public.groups (halqa_id, level);
create index if not exists member_groups_group_idx on public.member_groups (group_id);
create index if not exists proposals_status_idx on public.group_proposals (status, created_at desc);

alter table public.groups enable row level security;
alter table public.member_groups enable row level security;
alter table public.group_proposals enable row level security;

-- قراءة: كل عضوات الحلقة. كتابة: المشرفة العامة وحدها.
drop policy if exists groups_read on public.groups;
create policy groups_read on public.groups for select to authenticated
    using (public.is_member());

drop policy if exists groups_write on public.groups;
create policy groups_write on public.groups for all to authenticated
    using (public.is_supervisor()) with check (public.is_supervisor());

drop policy if exists member_groups_read on public.member_groups;
create policy member_groups_read on public.member_groups for select to authenticated
    using (public.is_member());

drop policy if exists member_groups_write on public.member_groups;
create policy member_groups_write on public.member_groups for all to authenticated
    using (public.is_supervisor()) with check (public.is_supervisor());

drop policy if exists proposals_read on public.group_proposals;
create policy proposals_read on public.group_proposals for select to authenticated
    using (public.is_supervisor() or proposed_by = auth.uid());

drop policy if exists proposals_insert on public.group_proposals;
create policy proposals_insert on public.group_proposals for insert to authenticated
    with check (public.is_staff() and proposed_by = auth.uid());

drop policy if exists proposals_decide on public.group_proposals;
create policy proposals_decide on public.group_proposals for update to authenticated
    using (public.is_supervisor()) with check (public.is_supervisor());
