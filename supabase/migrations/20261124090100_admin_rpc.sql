-- =============================================================================
--  دوال القرار الإداري: القبول والرفض والحظر والطرد والمجموعات والاقتراح
-- =============================================================================

-- is_supervisor() تُعرَّف في 20261124090000_groups_and_membership.sql قبل
-- السياسات التي تعتمد عليها، فلا تُكرَّر هنا.

-- هل المستخدمة عضوة مقبولة؟
create or replace function public.is_member()
returns boolean language sql stable security definer set search_path = public as $$
    select exists (
        select 1 from public.halqa_members
        where user_id = auth.uid() and status = 'active'
    );
$$;

-- قبول طالبة منتظرة
create or replace function public.accept_member(p_user uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not public.is_supervisor() then raise exception 'غير مصرح'; end if;
    update public.halqa_members set status = 'active' where user_id = p_user;
end; $$;

-- رفض طلب الانضمام (إزالة العضوية)
create or replace function public.reject_member(p_user uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not public.is_supervisor() then raise exception 'غير مصرح'; end if;
    delete from public.halqa_members where user_id = p_user and status = 'pending';
end; $$;

-- حظر أو رفع حظر أو طرد
create or replace function public.set_member_status(p_user uuid, p_status text)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not public.is_supervisor() then raise exception 'غير مصرح'; end if;
    if p_status not in ('active', 'banned', 'left') then raise exception 'حالة غير صالحة'; end if;
    update public.halqa_members set status = p_status where user_id = p_user;
end; $$;

-- إنشاء مجموعة جديدة (بلقب)
create or replace function public.create_group(p_level text, p_name text)
returns uuid language plpgsql security definer set search_path = public as $$
declare
    v_id uuid;
    v_halqa uuid := public.main_halqa_id();
begin
    if not public.is_supervisor() then raise exception 'غير مصرح'; end if;
    if p_level not in ('foundation', 'tajweed', 'hifz') then raise exception 'مستوى غير صالح'; end if;
    insert into public.groups (halqa_id, level, name, created_by)
    values (v_halqa, p_level, p_name, auth.uid())
    on conflict (halqa_id, level, name) do update set name = excluded.name
    returning id into v_id;
    return v_id;
end; $$;

-- نقل طالبة إلى مجموعة (تستبدل مجموعاتها في ذلك المستوى)
create or replace function public.assign_member_group(p_user uuid, p_group uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
    v_level text;
begin
    if not public.is_supervisor() then raise exception 'غير مصرح'; end if;
    select level into v_level from public.groups where id = p_group;
    if v_level is null then raise exception 'مجموعة غير موجودة'; end if;

    delete from public.member_groups mg
     using public.groups g
     where mg.group_id = g.id and mg.user_id = p_user and g.level = v_level;

    insert into public.member_groups (user_id, group_id)
    values (p_user, p_group) on conflict do nothing;
end; $$;

-- اقتراح المعلمة على المشرفة
create or replace function public.propose_group(p_user uuid, p_group uuid, p_note text default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_id uuid;
begin
    if not public.is_staff() then raise exception 'غير مصرح'; end if;
    insert into public.group_proposals (student_id, group_id, note, proposed_by)
    values (p_user, p_group, p_note, auth.uid())
    returning id into v_id;
    return v_id;
end; $$;

-- قرار المشرفة على الاقتراح
create or replace function public.decide_proposal(p_id uuid, p_accept boolean)
returns void language plpgsql security definer set search_path = public as $$
declare v_prop public.group_proposals;
begin
    if not public.is_supervisor() then raise exception 'غير مصرح'; end if;
    select * into v_prop from public.group_proposals where id = p_id;
    if v_prop.id is null then raise exception 'اقتراح غير موجود'; end if;
    if p_accept and v_prop.group_id is not null then
        perform public.assign_member_group(v_prop.student_id, v_prop.group_id);
    end if;
    update public.group_proposals
       set status = case when p_accept then 'accepted' else 'rejected' end
     where id = p_id;
end; $$;

grant execute on function public.accept_member(uuid) to authenticated;
grant execute on function public.reject_member(uuid) to authenticated;
grant execute on function public.set_member_status(uuid, text) to authenticated;
grant execute on function public.create_group(text, text) to authenticated;
grant execute on function public.assign_member_group(uuid, uuid) to authenticated;
grant execute on function public.propose_group(uuid, uuid, text) to authenticated;
grant execute on function public.decide_proposal(uuid, boolean) to authenticated;
