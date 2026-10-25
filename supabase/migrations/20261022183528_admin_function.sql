-- Agent PRO — Phase 8c-1 — SQL helpers for the `admin` Edge Function (docs/OFFERS.md "Edge Function `admin`",
-- docs/BACKEND.md "Edge Functions → admin").
--
-- The function authorises the caller itself (getUser + aal2 + is_admin('owner') on the caller's JWT) and then calls
-- these with the service role. Every one is SECURITY DEFINER, search_path = '', executable by service_role ONLY
-- (never anon or authenticated), and re-checks that p_actor is an active owner (defence in depth). Each write is one
-- transaction: the admins change, its explicit audit_log row and (revoke) the session sign-out commit together.
--
--   * private.admin_invites: a short-lived invite marker (email, 15 minutes). The signup trigger skips a new
--     auth.users row whose email has a fresh marker. Needed because GoTrue inserts the user BEFORE it applies
--     app_metadata (admin createUser: tx.Create(user) then UpdateAppMetaData; invite has no app_metadata at all), so
--     the AFTER INSERT trigger never sees account_kind = 'admin' on a new invitee;
--   * private.handle_new_user: unchanged from *_companies_offers.sql except the marker check;
--   * private.audit_row: unchanged except that admins writes made by these helpers (transaction-local setting
--     agentpro.audit_explicit = 'on') are skipped, because the helpers write their own row with the actor, the action
--     name (admin.invite / admin.set_role / admin.revoke) and the request IP. Writes to admins made any other way
--     (the SQL Editor bootstrap of the first owner) are still audited by the trigger, as `system`;
--   * public.svc_admin_invite_prepare / _cancel / _finish, svc_admin_set_role, svc_admin_revoke,
--     svc_admin_log_reset_mfa.
--
-- Errors (the message is the Edge Function's error code): forbidden (42501), self / last_owner (P0001),
-- not_found (P0002), invalid (22023).

-- ===========================================================================
-- Invite marker
-- ===========================================================================
-- Personal data (an email), kept at most 15 minutes: _finish and _cancel delete it, _prepare purges stale ones.
create table private.admin_invites (
  email      text primary key,
  created_at timestamptz not null default now(),
  constraint admin_invites_email_lower check (email = lower(btrim(email)))
);
alter table private.admin_invites enable row level security;
revoke all on private.admin_invites from public, anon, authenticated, service_role;

-- ===========================================================================
-- Signup trigger: admin accounts get no profile, subscription or consent row
-- ===========================================================================
create or replace function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_trial_days int;
  v_consent    jsonb;
begin
  -- account_kind = 'admin' (only the service role can set it), or an invite the admin function has just prepared
  -- (GoTrue applies app_metadata only after this AFTER INSERT trigger has run).
  if new.raw_app_meta_data ->> 'account_kind' = 'admin'
     or exists (select 1 from private.admin_invites i
                 where i.email = lower(btrim(new.email)) and i.created_at > now() - interval '15 minutes') then
    return new;
  end if;
  v_trial_days := (private.service_pricing() ->> 'trialDays')::int;
  v_consent := private.resolve_consent(new.raw_user_meta_data -> 'consent', 'agent_signup');

  insert into public.profiles (id, email, full_name, phone)
  values (
    new.id,
    new.email,
    nullif(btrim(new.raw_user_meta_data ->> 'full_name'), ''),
    nullif(btrim(new.raw_user_meta_data ->> 'phone'), '')
  )
  on conflict (id) do nothing;

  insert into public.subscriptions (agent_id, trial_started_at, trial_ends_at)
  values (new.id, now(), now() + make_interval(days => v_trial_days))
  on conflict (agent_id) do nothing;

  if v_consent is not null then
    insert into public.consents (agent_id, principal_kind, notice_key, notice_version, notice_hash, locale, channel)
    values (new.id, 'agent', v_consent ->> 'notice_key', (v_consent ->> 'version')::int,
            v_consent ->> 'hash', v_consent ->> 'locale', 'app');
  end if;

  return new;
end;
$$;

-- ===========================================================================
-- Audit trigger: skip admins writes that the helpers below audit explicitly
-- ===========================================================================
create or replace function private.audit_row()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_new     jsonb := to_jsonb(new);
  v_old     jsonb := case when tg_op = 'UPDATE' then to_jsonb(old) else '{}'::jsonb end;
  v_skip    text[] := array['created_at', 'updated_at', 'created_by', 'updated_by'];
  v_changes jsonb;
  v_uid     uuid := auth.uid();
  v_kind    text;
  v_action  text;
  v_ip      inet;
  v_status  text := v_new ->> 'status';
  v_was     text := v_old ->> 'status';
begin
  if tg_table_name = 'admins' and current_setting('agentpro.audit_explicit', true) = 'on' then
    return null;
  end if;
  if tg_table_name = 'company_memberships' then
    v_skip := v_skip || array['agency_code', 'suggested_name', 'review_note'];
  end if;
  select jsonb_object_agg(e.key, e.value) into v_changes
    from jsonb_each(v_new) e
   where not e.key = any (v_skip)
     and (tg_op = 'INSERT' or e.value is distinct from v_old -> e.key);
  if tg_op = 'UPDATE' and v_changes is null then
    return null;
  end if;

  v_action := case tg_table_name
    when 'companies' then case when tg_op = 'INSERT' then 'company.create' else 'company.update' end
    when 'offers' then case
      when tg_op = 'INSERT' then 'offer.create'
      when v_status = 'live' and v_was = 'draft' then 'offer.go_live'
      when v_status = 'archived' and v_was = 'live' then 'offer.archive'
      when v_status = 'draft' and v_was = 'archived' then 'offer.restore'
      else 'offer.update' end
    when 'admins' then case
      when tg_op = 'INSERT' then 'admin.create'
      when v_new ->> 'revoked_at' is not null and v_old ->> 'revoked_at' is null then 'admin.revoke'
      else 'admin.update' end
    when 'company_memberships' then case
      when tg_op = 'INSERT' then 'membership.request'
      when v_status = 'approved' and v_was <> 'approved' then 'membership.approve'
      when v_status = 'rejected' and v_was <> 'rejected' then 'membership.reject'
      when v_status = 'revoked' and v_was <> 'revoked' then 'membership.revoke'
      when v_status = 'withdrawn' and v_was <> 'withdrawn' then 'membership.withdraw'
      when v_new -> 'company_id' is distinct from v_old -> 'company_id' then 'membership.link'
      else 'membership.update' end
  end;

  v_kind := case
    when v_uid is null then 'system'
    when exists (select 1 from public.admins a where a.user_id = v_uid and a.revoked_at is null) then 'admin'
    else 'agent'
  end;
  if v_kind = 'admin' then
    begin
      v_ip := nullif(btrim(split_part(current_setting('request.headers', true)::jsonb ->> 'x-forwarded-for', ',', 1)), '')::inet;
    exception when others then
      v_ip := null;
    end;
  end if;

  insert into public.audit_log (actor_id, actor_kind, action, entity, entity_id, changes, request_ip)
  values (v_uid, v_kind, v_action, tg_table_name,
          coalesce(v_new ->> 'id', v_new ->> 'user_id')::uuid, v_changes, v_ip);
  return null;
end;
$$;

-- ===========================================================================
-- Private helpers
-- ===========================================================================

-- A text as an inet (the first x-forwarded-for entry, already split by the function), or null. Never raises.
create or replace function private.try_inet(p_text text)
returns inet
language plpgsql
immutable
set search_path = ''
as $$
begin
  return nullif(btrim(coalesce(p_text, '')), '')::inet;
exception when others then
  return null;
end;
$$;

-- Raises forbidden unless p_actor is an active owner. The Edge Function has already checked the caller's JWT
-- (aal2 and is_admin('owner')); this is defence in depth for the service-role-only helpers.
create or replace function private.require_owner_actor(p_actor uuid)
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if p_actor is null or not exists (
       select 1 from public.admins a where a.user_id = p_actor and a.role = 'owner' and a.revoked_at is null) then
    raise exception 'forbidden' using errcode = '42501';
  end if;
end;
$$;

-- Serialises every admins change made by the helpers, so two owners can't demote or revoke each other at once and
-- leave no owner.
create or replace function private.lock_admins()
returns void
language sql
volatile
set search_path = ''
as $$
  select pg_advisory_xact_lock(hashtext('agentpro.admins'));
$$;

-- Raises last_owner when removing p_user's owner role would leave no active owner.
create or replace function private.check_not_last_owner(p_user uuid)
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if not exists (select 1 from public.admins a
                  where a.role = 'owner' and a.revoked_at is null and a.user_id <> p_user) then
    raise exception 'last_owner' using errcode = 'P0001';
  end if;
end;
$$;

-- Raises not_found when p_company_id is given and isn't a company.
create or replace function private.check_admin_company(p_company_id uuid)
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if p_company_id is not null and not exists (select 1 from public.companies c where c.id = p_company_id) then
    raise exception 'not_found' using errcode = 'P0002', detail = 'company';
  end if;
end;
$$;

-- One audit_log row for an admin action. changes holds the role and scope only (no email, no name).
create or replace function private.audit_admin_action(
  p_actor uuid, p_action text, p_user uuid, p_changes jsonb, p_ip text)
returns void
language sql
volatile
security definer
set search_path = ''
as $$
  insert into public.audit_log (actor_id, actor_kind, action, entity, entity_id, changes, request_ip)
  values (p_actor, 'admin', p_action, 'admins', p_user, p_changes, private.try_inet(p_ip));
$$;

-- ===========================================================================
-- Service-role helpers (called only by the `admin` Edge Function)
-- ===========================================================================

-- Step 1 of invite. Checks the email, the company and that the address isn't an agent's account.
--   {state: 'new'}                                   no account yet: a 15-minute marker is stored, so the signup
--                                                    trigger skips the account that inviteUserByEmail creates;
--   {state: 'existing', user_id, confirmed, active}  an earlier admin (active or revoked) or an orphaned invite.
-- Errors: forbidden; invalid (bad email, an agent account, or an active admin who has already signed in: use
-- set-role); not_found (company).
create or replace function public.svc_admin_invite_prepare(p_actor uuid, p_email text, p_company_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_email text := lower(btrim(coalesce(p_email, '')));
  v_user  auth.users;
  v_admin public.admins;
begin
  perform private.require_owner_actor(p_actor);
  if char_length(v_email) > 254 or v_email !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' then
    raise exception 'invalid' using errcode = '22023', detail = 'email';
  end if;
  perform private.check_admin_company(p_company_id);

  select u.* into v_user from auth.users u where lower(u.email) = v_email limit 1;
  if v_user.id is null then
    delete from private.admin_invites i where i.created_at <= now() - interval '15 minutes';
    insert into private.admin_invites (email) values (v_email)
    on conflict (email) do update set created_at = now();
    return jsonb_build_object('state', 'new');
  end if;

  select a.* into v_admin from public.admins a where a.user_id = v_user.id;
  -- Admins are separate people (OFFERS.md): an agent's account (it has a profile) is never made an admin.
  if v_admin.user_id is null and exists (select 1 from public.profiles p where p.id = v_user.id) then
    raise exception 'invalid' using errcode = '22023', detail = 'agent_account';
  end if;
  if v_admin.user_id is not null and v_admin.revoked_at is null and v_user.email_confirmed_at is not null then
    raise exception 'invalid' using errcode = '22023', detail = 'already_admin';
  end if;
  return jsonb_build_object(
    'state', 'existing',
    'user_id', v_user.id,
    'confirmed', v_user.email_confirmed_at is not null,
    'active', v_admin.user_id is not null and v_admin.revoked_at is null);
end;
$$;

-- Drops the marker after a failed invite (the trigger ignores it after 15 minutes anyway).
create or replace function public.svc_admin_invite_cancel(p_email text)
returns void
language sql
volatile
security definer
set search_path = ''
as $$
  delete from private.admin_invites i where i.email = lower(btrim(coalesce(p_email, '')));
$$;

-- Step 3 of invite (after inviteUserByEmail and app_metadata.account_kind = 'admin'). Upserts the admins row
-- (role, scope, invited_by = the actor; a revoked admin is reactivated), drops the marker and writes admin.invite.
-- Errors: forbidden; invalid (unknown role, an agent account, the email doesn't match the user); not_found (the user
-- or the company); last_owner (re-inviting the only owner as an editor).
create or replace function public.svc_admin_invite_finish(
  p_actor uuid, p_user_id uuid, p_email text, p_role text, p_company_id uuid, p_ip text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_email text := lower(btrim(coalesce(p_email, '')));
  v_user  auth.users;
  v_old   public.admins;
begin
  perform private.require_owner_actor(p_actor);
  perform private.lock_admins();
  perform private.check_admin_company(p_company_id);
  select u.* into v_user from auth.users u where u.id = p_user_id;
  if v_user.id is null then
    raise exception 'not_found' using errcode = 'P0002', detail = 'user';
  end if;
  if lower(v_user.email) is distinct from v_email then
    raise exception 'invalid' using errcode = '22023', detail = 'email';
  end if;
  select a.* into v_old from public.admins a where a.user_id = p_user_id for update;
  if v_old.user_id is null and exists (select 1 from public.profiles p where p.id = p_user_id) then
    raise exception 'invalid' using errcode = '22023', detail = 'agent_account';
  end if;
  if p_user_id = p_actor then
    raise exception 'self' using errcode = 'P0001';
  end if;
  if v_old.role = 'owner' and v_old.revoked_at is null and p_role is distinct from 'owner' then
    perform private.check_not_last_owner(p_user_id);
  end if;

  perform set_config('agentpro.audit_explicit', 'on', true);
  begin
    insert into public.admins (user_id, role, company_id, invited_by, revoked_at)
    values (p_user_id, p_role, p_company_id, p_actor, null)
    on conflict (user_id) do update
      set role = excluded.role, company_id = excluded.company_id, invited_by = excluded.invited_by, revoked_at = null;
  exception when check_violation or not_null_violation then
    perform set_config('agentpro.audit_explicit', '', true);
    raise exception 'invalid' using errcode = '22023', detail = 'role';
  end;
  perform set_config('agentpro.audit_explicit', '', true);

  delete from private.admin_invites i where i.email = v_email;
  perform private.audit_admin_action(p_actor, 'admin.invite', p_user_id,
    jsonb_build_object('role', p_role, 'company_id', p_company_id, 'reinvite', v_old.user_id is not null), p_ip);
  return jsonb_build_object('user_id', p_user_id);
end;
$$;

-- Changes an admin's role and (p_set_company) scope. A no-op change writes nothing.
-- Errors: forbidden; self (your own row); not_found (no such admin, the company); invalid (revoked, unknown role);
-- last_owner.
create or replace function public.svc_admin_set_role(
  p_actor uuid, p_user_id uuid, p_role text, p_company_id uuid, p_set_company boolean, p_ip text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_old     public.admins;
  v_company uuid;
begin
  perform private.require_owner_actor(p_actor);
  if p_user_id = p_actor then
    raise exception 'self' using errcode = 'P0001';
  end if;
  perform private.lock_admins();
  select a.* into v_old from public.admins a where a.user_id = p_user_id for update;
  if v_old.user_id is null then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  if v_old.revoked_at is not null then
    raise exception 'invalid' using errcode = '22023', detail = 'revoked';
  end if;
  v_company := case when coalesce(p_set_company, false) then p_company_id else v_old.company_id end;
  perform private.check_admin_company(v_company);
  if v_old.role = 'owner' and p_role is distinct from 'owner' then
    perform private.check_not_last_owner(p_user_id);
  end if;
  if v_old.role = p_role and v_old.company_id is not distinct from v_company then
    return jsonb_build_object('changed', false);
  end if;

  perform set_config('agentpro.audit_explicit', 'on', true);
  begin
    update public.admins set role = p_role, company_id = v_company where user_id = p_user_id;
  exception when check_violation or not_null_violation then
    perform set_config('agentpro.audit_explicit', '', true);
    raise exception 'invalid' using errcode = '22023', detail = 'role';
  end;
  perform set_config('agentpro.audit_explicit', '', true);

  perform private.audit_admin_action(p_actor, 'admin.set_role', p_user_id,
    jsonb_build_object('role', p_role, 'company_id', v_company,
                       'previous_role', v_old.role, 'previous_company_id', v_old.company_id), p_ip);
  return jsonb_build_object('changed', true);
end;
$$;

-- Revokes an admin: revoked_at = now() and every session ended (auth.sessions and auth.refresh_tokens rows deleted;
-- supabase-js's auth.admin.signOut needs the user's own JWT, which the function doesn't have). Their access token
-- stops working at once for the API too: is_admin() reads revoked_at on every statement, and Auth refuses a token
-- whose session is gone. Revoking an already revoked admin only ends any new sessions.
-- Returns {changed, signed_out}; signed_out is false only if the session tables couldn't be written (the revoke
-- itself still commits). Errors: forbidden; self; not_found; last_owner.
create or replace function public.svc_admin_revoke(p_actor uuid, p_user_id uuid, p_ip text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_old      public.admins;
  v_sessions int := 0;
  v_signed   boolean := true;
begin
  perform private.require_owner_actor(p_actor);
  if p_user_id = p_actor then
    raise exception 'self' using errcode = 'P0001';
  end if;
  perform private.lock_admins();
  select a.* into v_old from public.admins a where a.user_id = p_user_id for update;
  if v_old.user_id is null then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  if v_old.revoked_at is null and v_old.role = 'owner' then
    perform private.check_not_last_owner(p_user_id);
  end if;

  if v_old.revoked_at is null then
    perform set_config('agentpro.audit_explicit', 'on', true);
    update public.admins set revoked_at = now() where user_id = p_user_id;
    perform set_config('agentpro.audit_explicit', '', true);
  end if;

  begin
    delete from auth.refresh_tokens t where t.user_id = p_user_id::text;
    delete from auth.sessions s where s.user_id = p_user_id;
    get diagnostics v_sessions = row_count;
  exception when insufficient_privilege or undefined_table then
    v_signed := false;
  end;

  if v_old.revoked_at is null or v_sessions > 0 or not v_signed then
    perform private.audit_admin_action(p_actor, 'admin.revoke', p_user_id,
      jsonb_build_object('role', v_old.role, 'company_id', v_old.company_id,
                         'sessions_ended', case when v_signed then v_sessions end), p_ip);
  end if;
  return jsonb_build_object('changed', v_old.revoked_at is null, 'signed_out', v_signed);
end;
$$;

-- Records a reset-mfa after the Edge Function deleted the admin's factors through the Auth admin API.
-- Errors: forbidden; self; not_found.
create or replace function public.svc_admin_log_reset_mfa(
  p_actor uuid, p_user_id uuid, p_factors_removed int, p_ip text)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  perform private.require_owner_actor(p_actor);
  if p_user_id = p_actor then
    raise exception 'self' using errcode = 'P0001';
  end if;
  if not exists (select 1 from public.admins a where a.user_id = p_user_id) then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  perform private.audit_admin_action(p_actor, 'admin.reset_mfa', p_user_id,
    jsonb_build_object('factors_removed', greatest(coalesce(p_factors_removed, 0), 0)), p_ip);
end;
$$;

-- ===========================================================================
-- Grants: service_role only (no 0028/0029 advisor findings; the Data API refuses anon and authenticated)
-- ===========================================================================
revoke all on function public.svc_admin_invite_prepare(uuid, text, uuid) from public, anon, authenticated;
revoke all on function public.svc_admin_invite_cancel(text) from public, anon, authenticated;
revoke all on function public.svc_admin_invite_finish(uuid, uuid, text, text, uuid, text) from public, anon, authenticated;
revoke all on function public.svc_admin_set_role(uuid, uuid, text, uuid, boolean, text) from public, anon, authenticated;
revoke all on function public.svc_admin_revoke(uuid, uuid, text) from public, anon, authenticated;
revoke all on function public.svc_admin_log_reset_mfa(uuid, uuid, int, text) from public, anon, authenticated;
grant execute on function public.svc_admin_invite_prepare(uuid, text, uuid) to service_role;
grant execute on function public.svc_admin_invite_cancel(text) to service_role;
grant execute on function public.svc_admin_invite_finish(uuid, uuid, text, text, uuid, text) to service_role;
grant execute on function public.svc_admin_set_role(uuid, uuid, text, uuid, boolean, text) to service_role;
grant execute on function public.svc_admin_revoke(uuid, uuid, text) to service_role;
grant execute on function public.svc_admin_log_reset_mfa(uuid, uuid, int, text) to service_role;

revoke all on function private.try_inet(text) from public, anon, authenticated;
revoke all on function private.require_owner_actor(uuid) from public, anon, authenticated;
revoke all on function private.lock_admins() from public, anon, authenticated;
revoke all on function private.check_not_last_owner(uuid) from public, anon, authenticated;
revoke all on function private.check_admin_company(uuid) from public, anon, authenticated;
revoke all on function private.audit_admin_action(uuid, text, uuid, jsonb, text) from public, anon, authenticated;
