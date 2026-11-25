-- طلبات الانضمام: الطالبة التي تُدخل كود دعوة تصبح pending حتى تقبلها المشرفة.
-- المعلمة والمشرفة (دعوات يصدرها الطاقم) تبقى active مباشرة.
begin;

-- العضو يرى صفّه الخاص ولو كان معلّقا (ليعرف التطبيق حالته).
drop policy if exists members_select_self on public.halqa_members;
create policy members_select_self on public.halqa_members
  for select to authenticated
  using (user_id = auth.uid());

create or replace function public.redeem_invitation(p_code text)
returns public.halqa_members
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_inv public.invitations;
  v_halqa uuid;
  v_member public.halqa_members;
  v_status public.member_status;
begin
  if auth.uid() is null then
    raise exception 'يجب تسجيل الدخول' using errcode = '28000';
  end if;

  v_halqa := public.main_halqa_id();

  if exists (select 1 from public.halqa_members
              where halqa_id = v_halqa and user_id = auth.uid() and status = 'banned') then
    raise exception 'الحساب موقوف' using errcode = '42501';
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

  v_status := case when v_inv.role = 'student' then 'pending' else 'active' end;

  update public.invitations set uses = uses + 1 where id = v_inv.id;
  update public.profiles set role = v_inv.role where id = auth.uid();

  insert into public.halqa_members (halqa_id, user_id, role, level, status)
  values (v_halqa, auth.uid(), v_inv.role, 'foundation', v_status)
  on conflict (halqa_id, user_id) do update
    set role = excluded.role,
        -- لا نخفّض عضوة فعّالة إلى معلّقة عند إعادة استخدام كود
        status = case when public.halqa_members.status = 'active' then 'active' else excluded.status end
  returning * into v_member;

  return v_member;
end;
$$;

grant execute on function public.redeem_invitation(text) to authenticated;
commit;
