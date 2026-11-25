-- =============================================================================
--  General Supervisor provisioning (owner workflow, service role only).
--
--  Additive migration: the 14 existing migrations are left untouched, no
--  invitation is created, no RLS policy or table grant is changed, and no
--  account or test data is created here.
--
--  The Android app never receives the service-role key.  The authenticated Edge
--  Function create-general-supervisor verifies the caller with auth.getUser()
--  and public.is_owner(), creates an email-confirmed Auth user, then calls this
--  function with the service-role client.  This function is the only SQL path
--  that gives the new account the General Supervisor role and an active
--  membership in the main halqa.
-- =============================================================================
begin;

-- -----------------------------------------------------------------------------
-- 1) The append-only authorization audit gains one new event kind.  The table
--    itself, its trigger, its RLS and its grants are unchanged.
-- -----------------------------------------------------------------------------
alter table public.authorization_audit
  drop constraint if exists authorization_audit_event_check;

alter table public.authorization_audit
  add constraint authorization_audit_event_check
  check (event in ('owner_provisioned', 'supervisor_provisioned'));

-- -----------------------------------------------------------------------------
-- 2) Service-only provisioning RPC.
--
--    Rules, all enforced inside the transaction:
--      * caller must be service_role / a superuser (Edge Function server path);
--      * p_user must be a non-null, existing profile;
--      * the protected owner can never be the target;
--      * at most one active non-owner General Supervisor may exist;
--      * profile role, main-halqa membership and audit are written atomically;
--      * the role-change guard flag is transaction-local and reset explicitly.
--
--    The concurrency check takes a transaction-scoped advisory lock, so two
--    simultaneous calls cannot both observe "no supervisor" and both succeed.
-- -----------------------------------------------------------------------------
create or replace function public.provision_general_supervisor(p_user uuid)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_caller text;
  v_owner  uuid;
  v_halqa  uuid;
begin
  -- Inside SECURITY DEFINER current_user is the function owner, so identify the
  -- caller from the request JWT role first, then the session role.
  v_caller := coalesce(
    nullif(current_setting('request.jwt.claim.role', true), ''),
    nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role',
    nullif(current_setting('role', true), '')
  );
  if v_caller is null or v_caller = 'none' then
    v_caller := session_user;
  end if;

  if v_caller not in ('service_role', 'postgres', 'supabase_admin')
     and not exists (
       select 1 from pg_roles where rolname::text = v_caller and rolsuper
     )
  then
    raise exception 'تهيئة المشرفة العامة مسموحة للخادم فقط'
      using errcode = '42501';
  end if;

  if p_user is null then
    raise exception 'معرف المستخدمة مطلوب' using errcode = '22023';
  end if;

  if not exists (select 1 from public.profiles where id = p_user) then
    raise exception 'الملف الشخصي غير موجود' using errcode = '22023';
  end if;

  select user_id into v_owner from public.app_owner where singleton;
  if v_owner is not null and v_owner = p_user then
    raise exception 'لا يمكن استخدام حساب المالكة كمشرفة عامة'
      using errcode = 'MQR01';
  end if;

  -- Serialize the "only one active general supervisor" decision.
  perform pg_advisory_xact_lock(hashtext('public.general_supervisor')::bigint);

  if exists (
    select 1
      from public.profiles p
      join public.halqa_members m on m.user_id = p.id
     where p.role = 'supervisor'
       and m.status = 'active'
       and p.id <> p_user
       and (v_owner is null or p.id <> v_owner)
  ) then
    raise exception 'يوجد مشرفة عامة نشطة بالفعل؛ لا يمكن إنشاء ثانية'
      using errcode = 'MQR02';
  end if;

  v_halqa := public.main_halqa_id();
  if v_halqa is null then
    raise exception 'الحلقة غير مهيأة' using errcode = 'MQR03';
  end if;

  -- Transaction-local guards: they are never left on for the session.
  perform set_config('app.authorized_role_change', 'on', true);
  perform set_config('app.authorized_supervisor_change', 'on', true);

  update public.profiles
     set role = 'supervisor'
   where id = p_user;

  insert into public.halqa_members (halqa_id, user_id, role, level, status)
  values (v_halqa, p_user, 'supervisor', 'ijaza', 'active')
  on conflict (halqa_id, user_id) do update
    set role = 'supervisor',
        level = 'ijaza',
        status = 'active';

  insert into public.authorization_audit (event, subject_id)
  values ('supervisor_provisioned', p_user);

  perform set_config('app.authorized_supervisor_change', 'off', true);
  perform set_config('app.authorized_role_change', 'off', true);
end;
$$;

comment on function public.provision_general_supervisor(uuid) is
  'تهيئة المشرفة العامة من الخادم فقط: دور مشرفة وعضوية نشطة في الحلقة العامة وسجل تدقيق، بلا اعتمادات.';

revoke all on function public.provision_general_supervisor(uuid)
  from public, anon, authenticated;
grant execute on function public.provision_general_supervisor(uuid)
  to service_role;

notify pgrst, 'reload schema';

commit;
