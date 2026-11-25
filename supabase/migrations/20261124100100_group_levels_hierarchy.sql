-- Groups are the parent units. Each student joins a named group at one of its
-- three learning levels; ijaza remains a graduation state on halqa_members.
begin;

alter table public.halqa_members
    add column if not exists groups text[] not null default '{}'::text[];

alter table public.member_groups
    add column if not exists level text not null default 'foundation'
    check (level in ('foundation', 'tajweed', 'hifz'));

-- Preserve the former group-level classification on each existing membership.
do $$ begin
    if exists (select 1 from information_schema.columns where table_schema = 'public' and table_name = 'groups' and column_name = 'level') then
        execute 'update public.member_groups mg set level = g.level from public.groups g where mg.group_id = g.id and g.level in (''foundation'', ''tajweed'', ''hifz'')';
    end if;
end $$;

alter table public.member_groups drop constraint if exists member_groups_pkey;
alter table public.member_groups add constraint member_groups_pkey primary key (user_id, group_id, level);

-- Fold legacy same-name groups into one parent group while preserving links.
with canonical as (
    select id, first_value(id) over (partition by halqa_id, name order by created_at, id) as keep_id
      from public.groups
)
delete from public.member_groups mg using canonical c
 where mg.group_id = c.id and c.id <> c.keep_id
   and exists (
       select 1 from public.member_groups keep
        where keep.user_id = mg.user_id and keep.group_id = c.keep_id and keep.level = mg.level
   );

with canonical as (
    select id, first_value(id) over (partition by halqa_id, name order by created_at, id) as keep_id
      from public.groups
)
update public.member_groups mg set group_id = c.keep_id
  from canonical c where mg.group_id = c.id and c.id <> c.keep_id;

with canonical as (
    select id, first_value(id) over (partition by halqa_id, name order by created_at, id) as keep_id
      from public.groups
)
update public.group_proposals p set group_id = c.keep_id
  from canonical c where p.group_id = c.id and c.id <> c.keep_id;

with canonical as (
    select id, first_value(id) over (partition by halqa_id, name order by created_at, id) as keep_id
      from public.groups
)
delete from public.groups g using canonical c where g.id = c.id and c.id <> c.keep_id;

drop index if exists public.groups_halqa_idx;
alter table public.groups drop constraint if exists groups_level_check;
alter table public.groups drop constraint if exists groups_halqa_id_level_name_key;
alter table public.groups drop column if exists level;
do $$ begin
    if not exists (select 1 from pg_constraint where conrelid = 'public.groups'::regclass and conname = 'groups_halqa_id_name_key') then
        alter table public.groups add constraint groups_halqa_id_name_key unique (halqa_id, name);
    end if;
end $$;
create index if not exists groups_halqa_idx on public.groups (halqa_id, name);
create index if not exists member_groups_level_idx on public.member_groups (group_id, level);

-- Carry the old level arrays into a default parent group for any legacy members.
insert into public.groups (halqa_id, name, created_by)
select distinct m.halqa_id, 'المجموعة العامة', null::uuid
  from public.halqa_members m
 where coalesce(cardinality(m.groups), 0) > 0
   and not exists (select 1 from public.member_groups mg where mg.user_id = m.user_id)
on conflict (halqa_id, name) do nothing;

insert into public.member_groups (user_id, group_id, level)
select m.user_id, g.id, level_name
  from public.halqa_members m
  cross join lateral unnest(coalesce(m.groups, '{}'::text[])) as level_name
  join public.groups g on g.halqa_id = m.halqa_id and g.name = 'المجموعة العامة'
 where level_name in ('foundation', 'tajweed', 'hifz')
   and not exists (select 1 from public.member_groups existing where existing.user_id = m.user_id)
on conflict (user_id, group_id, level) do nothing;

alter table public.halqa_members drop column if exists groups;

alter table public.group_proposals add column if not exists level text
    check (level in ('foundation', 'tajweed', 'hifz'));

drop function if exists public.create_group(text, text);
create or replace function public.create_group(p_name text)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_id uuid;
begin
    if not public.is_supervisor() then raise exception 'غير مصرح'; end if;
    if nullif(btrim(p_name), '') is null then raise exception 'اسم المجموعة مطلوب'; end if;
    insert into public.groups (halqa_id, name, created_by)
    values (public.main_halqa_id(), btrim(p_name), auth.uid())
    on conflict (halqa_id, name) do update set name = excluded.name
    returning id into v_id;
    return v_id;
end; $$;

drop function if exists public.assign_member_group(uuid, uuid);
drop function if exists public.assign_member_group(uuid, uuid, text);
create or replace function public.assign_member_group(p_user uuid, p_group uuid, p_level text)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not public.is_supervisor() then raise exception 'غير مصرح'; end if;
    if p_level not in ('foundation', 'tajweed', 'hifz') then raise exception 'مستوى غير صالح'; end if;
    if not exists (select 1 from public.groups where id = p_group) then raise exception 'مجموعة غير موجودة'; end if;
    delete from public.member_groups where user_id = p_user and group_id = p_group;
    insert into public.member_groups (user_id, group_id, level)
    values (p_user, p_group, p_level)
    on conflict (user_id, group_id, level) do nothing;
end; $$;

drop function if exists public.propose_group(uuid, uuid, text);
create or replace function public.propose_group(p_user uuid, p_group uuid, p_level text, p_note text default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_id uuid;
begin
    if not public.is_staff() then raise exception 'غير مصرح'; end if;
    if p_level not in ('foundation', 'tajweed', 'hifz') then raise exception 'مستوى غير صالح'; end if;
    insert into public.group_proposals (student_id, group_id, level, note, proposed_by)
    values (p_user, p_group, p_level, p_note, auth.uid())
    returning id into v_id;
    return v_id;
end; $$;

create or replace function public.decide_proposal(p_id uuid, p_accept boolean)
returns void language plpgsql security definer set search_path = public as $$
declare v_prop public.group_proposals;
begin
    if not public.is_supervisor() then raise exception 'غير مصرح'; end if;
    select * into v_prop from public.group_proposals where id = p_id;
    if v_prop.id is null then raise exception 'اقتراح غير موجود'; end if;
    if p_accept and v_prop.group_id is not null then
        perform public.assign_member_group(v_prop.student_id, v_prop.group_id, coalesce(v_prop.level, 'foundation'));
    end if;
    update public.group_proposals set status = case when p_accept then 'accepted' else 'rejected' end where id = p_id;
end; $$;

grant execute on function public.create_group(text) to authenticated;
grant execute on function public.assign_member_group(uuid, uuid, text) to authenticated;
grant execute on function public.propose_group(uuid, uuid, text, text) to authenticated;
grant execute on function public.decide_proposal(uuid, boolean) to authenticated;
commit;
