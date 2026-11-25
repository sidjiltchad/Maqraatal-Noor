-- =============================================================================
--  Fix: public.set_member_status(uuid, text) must cast the validated text to
--  public.member_status before assigning it to halqa_members.status.
--
--  The owner-foundation migration (20261125150000) validated p_status against
--  the allowed states, then assigned the text value directly.  PostgreSQL does
--  not implicitly cast text to an enum in UPDATE ... SET, so the owner-status
--  assertion in supabase/tests/60_auth_owner_foundation.sql failed with:
--    column "status" is of type member_status but expression is of type text
--
--  This is a forward-only correction.  It only redefines the function: the
--  signature, SECURITY DEFINER, search_path, existing grants and every
--  authorization branch are unchanged.  Input is validated first and then cast
--  explicitly; invalid text is still rejected with the original P0001 message
--  and never reaches the enum cast.
-- =============================================================================
begin;

create or replace function public.set_member_status(p_user uuid, p_status text)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not public.is_supervisor() then raise exception 'غير مصرح' using errcode = '42501'; end if;
    if p_status is null or p_status not in ('active', 'banned', 'left') then
        raise exception 'حالة غير صالحة';
    end if;
    if auth.uid() is not null and not public.is_owner() then
        if private.is_owner(p_user) then
            raise exception 'لا يمكن تعديل حالة المالكة من دور أدنى' using errcode = '42501';
        end if;
        if p_user <> auth.uid()
           and exists (select 1 from public.profiles where id = p_user and role = 'supervisor') then
            raise exception 'تعديل مشرفة عامة مسموح للمالكة فقط' using errcode = '42501';
        end if;
    end if;
    update public.halqa_members
       set status = p_status::public.member_status
     where user_id = p_user;
end; $$;

-- CREATE OR REPLACE keeps the function OID and ACL, so authenticated retains
-- EXECUTE; restating the grant keeps the privilege surface explicit.
grant execute on function public.set_member_status(uuid, text) to authenticated;

notify pgrst, 'reload schema';

commit;
