-- Agent PRO — customer login on agent sites, "My policies" (DECISIONS #96–#99, hardened by #101; plan "Customer
-- login", contract C and E).
-- An agent's customer signs in on that agent's site (Worker, server-rendered, HttpOnly cookie) with an agent-issued
-- link, then mobile + 6-digit PIN, and sees their policies, due dates and recorded payments; they can ask about a
-- policy (an enquiry with source 'portal') and make a data-principal request (a data_requests row with customer_id).
--   * Customers are NOT auth.users (#97): four portal_* tables, RLS on with no policies and no API grants, reached
--     only through the SECURITY DEFINER RPCs below. Tokens (invite and session) are 32 random bytes, base64url
--     (43 chars), returned once and stored only as sha256 (bytea, 32 bytes). PINs are bcrypt (pgcrypto, cost 10).
--   * Agent RPCs (authenticated): portal_invite (can_write), portal_revoke (no can_write: a lapsed agent can still
--     turn a login off), portal_status.
--   * Anon RPCs (called by the Worker; never raise on user input): portal_activate, portal_login, portal_home,
--     portal_ask, portal_request, portal_logout. Each one first checks private.portal_gate_ok() (#101): the request
--     must carry the Worker's secret header x-portal-key, whose sha256 is the one row of private.portal_gate (set
--     by hand at go-live). Without it they return {ok:false, error:'unavailable'} and do nothing else (no bcrypt).
--   * Portal "available" for a site = find_public_site's rule (published, not deleted, owner not lapsed) AND the
--     customerPortal flag AND published contact.showCustomerLogin is not false. A session is valid only on its own
--     agent's site (one site per agent), so a cookie replayed on another agent's slug or host gets 'session'.
--   * A customer joins a login only when THEIR OWN link is activated (#101, M1): portal_invite creates or finds the
--     account and issues the link; portal_activate attaches that customer and records that customer's consent.
--   * Lockouts: wrong PINs count within failureWindowDays; a streak of maxFailedAttempts locks for lockMinutes (a
--     success ends the streak, never the window count); hardLockAfter failures in the window hard-lock the account
--     for hardLockHours (then it clears itself; a new invite + activation clears it at once). An unknown phone runs
--     a dummy bcrypt and gets the same 'invalid'. Per site, at most siteFailedLoginsPerMinute failed logins a minute
--     (private.portal_login_failures), checked before any bcrypt ('rate_limited'). Per-account advisory locks
--     serialise login / activate / invite / detach; every path takes them before any row lock.
--   * Invites: single use, inviteDays, burned after inviteMaxPhoneTries wrong mobile numbers; a new invite voids the
--     same customer's older unused ones; activation sets the PIN, clears the locks and revokes every session.
--   * Triggers on customers: a phone change (compared after normalisation), a soft delete or a hard delete detaches
--     the customer and voids its unused invites; an account left with no customer has its sessions revoked and its
--     PIN and counters wiped, and is soft-deleted unless another customer's link to it is still live.
--   * private.public_site_payload is re-created from its current definition (20261017132241_companies_offers.sql;
--     20261022090000_multi_company.sql changed only site_offers_payload) with one new key, "portal": boolean.
--   * audit_log gets portal.invite / portal.revoke (actor agent), portal.activate / portal.hard_lock (system);
--     entity 'portal_accounts', entity_id = the account id; changes never hold personal data.
--
-- Apply order: after 20261022183528_admin_function.sql and BEFORE the catalog_sync that adds the 'portal' enquiry
-- source, the customer_portal notice (consents_notice_key_check) and app_config.portal (with the #101 keys
-- failureWindowDays, hardLockHours, siteFailedLoginsPerMinute, blockedPins). Nothing here reads those at apply time
-- (plpgsql resolves names lazily, and app_config.portal is read at run time by private.portal_config()).
-- Go-live, as postgres, after `wrangler secret put PORTAL_RPC_KEY` (the anon RPCs answer 'unavailable' until then):
--   insert into private.portal_gate values (1, sha256(convert_to('<secret>', 'UTF8')))
--   on conflict (id) do update set key_hash = excluded.key_hash;
-- Needs pgcrypto in schema `extensions` (Supabase has it; the local test shim creates the schema).
-- Expected advisors: new accepted WARNs 0028 (anon: the 6 anon RPCs) and 0029 (authenticated: the 3 agent RPCs).
-- Errors from agent RPCs: not_authenticated / plan_read_only (42501), not_found (P0002).

create extension if not exists pgcrypto with schema extensions;

-- ===========================================================================
-- Tables
-- ===========================================================================

-- One login per (agent, normalised mobile number). pin_hash is null until the first activation (and again once the
-- account has no customer). failed_attempts = wrong PINs since failure_window_started_at (the hard-lock count, kept
-- across successes); failure_streak = wrong PINs since the last success or soft lock.
create table public.portal_accounts (
  id                        uuid primary key default gen_random_uuid(),
  agent_id                  uuid not null references auth.users (id) on delete cascade,
  phone                     text not null,
  pin_hash                  text,
  activated_at              timestamptz,
  failed_attempts           integer not null default 0,
  failure_window_started_at timestamptz,
  failure_streak            integer not null default 0,
  locked_until              timestamptz,
  hard_locked_at            timestamptz,
  last_login_at             timestamptz,
  created_at                timestamptz not null default now(),
  updated_at                timestamptz not null default now(),
  deleted_at                timestamptz,
  constraint portal_accounts_id_agent_id_key unique (id, agent_id),
  constraint portal_accounts_phone_format check (phone ~ '^\+91[6-9][0-9]{9}$'),
  constraint portal_accounts_failed_attempts_nonneg check (failed_attempts >= 0),
  constraint portal_accounts_failure_streak_nonneg check (failure_streak >= 0)
);
create unique index portal_accounts_agent_phone_live_idx on public.portal_accounts (agent_id, phone) where deleted_at is null;
create index portal_accounts_agent_id_updated_at_idx on public.portal_accounts (agent_id, updated_at);

-- Which customers an account shows (explicitly invited only; a matching phone alone adds no one).
-- A customer is on at most one account. Rows are hard-deleted on detach.
create table public.portal_account_customers (
  id          uuid primary key default gen_random_uuid(),
  agent_id    uuid not null references auth.users (id) on delete cascade,
  account_id  uuid not null,
  customer_id uuid not null,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  constraint portal_account_customers_customer_key unique (customer_id, agent_id),
  constraint portal_account_customers_account_customer_key unique (account_id, customer_id),
  constraint portal_account_customers_account_fk foreign key (account_id, agent_id)
    references public.portal_accounts (id, agent_id) on delete cascade,
  constraint portal_account_customers_customer_fk foreign key (customer_id, agent_id)
    references public.customers (id, agent_id) on delete cascade
);
create index portal_account_customers_account_id_idx on public.portal_account_customers (account_id, agent_id);
create index portal_account_customers_agent_id_updated_at_idx on public.portal_account_customers (agent_id, updated_at);

-- Agent-issued links. used_at = activated with it; revoked_at = voided (newer invite, burned, detached).
create table public.portal_invites (
  id          uuid primary key default gen_random_uuid(),
  agent_id    uuid not null references auth.users (id) on delete cascade,
  account_id  uuid not null,
  customer_id uuid not null,
  token_hash  bytea not null,
  expires_at  timestamptz not null,
  used_at     timestamptz,
  revoked_at  timestamptz,
  phone_tries integer not null default 0,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  constraint portal_invites_token_hash_key unique (token_hash),
  constraint portal_invites_token_hash_length check (octet_length(token_hash) = 32),
  constraint portal_invites_phone_tries_nonneg check (phone_tries >= 0),
  constraint portal_invites_account_fk foreign key (account_id, agent_id)
    references public.portal_accounts (id, agent_id) on delete cascade,
  constraint portal_invites_customer_fk foreign key (customer_id, agent_id)
    references public.customers (id, agent_id) on delete cascade
);
create index portal_invites_account_id_idx on public.portal_invites (account_id, agent_id);
create index portal_invites_customer_id_idx on public.portal_invites (customer_id, agent_id);
create index portal_invites_agent_id_updated_at_idx on public.portal_invites (agent_id, updated_at);

-- Signed-in devices (the Worker's HttpOnly cookie holds the token).
create table public.portal_sessions (
  id           uuid primary key default gen_random_uuid(),
  agent_id     uuid not null references auth.users (id) on delete cascade,
  account_id   uuid not null,
  token_hash   bytea not null,
  expires_at   timestamptz not null,
  last_seen_at timestamptz,
  revoked_at   timestamptz,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  constraint portal_sessions_token_hash_key unique (token_hash),
  constraint portal_sessions_token_hash_length check (octet_length(token_hash) = 32),
  constraint portal_sessions_account_fk foreign key (account_id, agent_id)
    references public.portal_accounts (id, agent_id) on delete cascade
);
create index portal_sessions_account_id_idx on public.portal_sessions (account_id, agent_id);
create index portal_sessions_agent_id_updated_at_idx on public.portal_sessions (agent_id, updated_at);

do $$
declare
  t text;
begin
  foreach t in array array['portal_accounts', 'portal_account_customers', 'portal_invites', 'portal_sessions'] loop
    execute format(
      'create trigger set_updated_at before insert or update on public.%I
         for each row execute function private.set_updated_at()', t);
  end loop;
end;
$$;

-- RLS on, no policies, no API grants: only SECURITY DEFINER code and the service role touch these.
alter table public.portal_accounts enable row level security;
alter table public.portal_account_customers enable row level security;
alter table public.portal_invites enable row level security;
alter table public.portal_sessions enable row level security;
revoke all on public.portal_accounts, public.portal_account_customers, public.portal_invites, public.portal_sessions
  from public, anon, authenticated;
grant all on public.portal_accounts, public.portal_account_customers, public.portal_invites, public.portal_sessions
  to service_role;

-- The Worker's secret (DECISIONS #101, H2): sha256 of the PORTAL_RPC_KEY Worker secret, one row, set by hand at
-- go-live (see the header). No grants to any API role, the service role included.
create table private.portal_gate (
  id       integer primary key,
  key_hash bytea not null,
  constraint portal_gate_single_row check (id = 1),
  constraint portal_gate_key_hash_length check (octet_length(key_hash) = 32)
);
alter table private.portal_gate enable row level security;
revoke all on private.portal_gate from public, anon, authenticated, service_role;

-- Failed portal_login attempts per site, for the per-minute cap (no personal data: a site and a time). Rows older
-- than a minute are pruned by the next failure on the same site, so a site keeps about a minute's worth.
create table private.portal_login_failures (
  id      bigint generated always as identity primary key,
  site_id uuid not null,
  at      timestamptz not null default now()
);
create index portal_login_failures_site_id_at_idx on private.portal_login_failures (site_id, at);
alter table private.portal_login_failures enable row level security;
revoke all on private.portal_login_failures from public, anon, authenticated, service_role;

-- ===========================================================================
-- Private helpers
-- ===========================================================================

-- app_config('portal') = catalog.portal (from catalog_sync). Fails loudly if it's missing or incomplete (the #101
-- keys included: a catalog_sync generated before them makes every portal RPC raise until it is regenerated).
create or replace function private.portal_config()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v jsonb;
  k text;
begin
  select c.value into v from public.app_config c where c.key = 'portal';
  if v is null or jsonb_typeof(v) <> 'object' then
    raise exception 'app_config.portal is missing: apply the generated catalog_sync migration' using errcode = 'P0001';
  end if;
  foreach k in array array['pinLength', 'inviteDays', 'inviteMaxPhoneTries', 'sessionDays', 'maxFailedAttempts',
                           'lockMinutes', 'hardLockAfter', 'enquiriesPerHour', 'paymentsShown',
                           'failureWindowDays', 'hardLockHours', 'siteFailedLoginsPerMinute'] loop
    if jsonb_typeof(v -> k) is distinct from 'number' or (v ->> k) !~ '^[1-9][0-9]{0,5}$' then
      raise exception 'app_config.portal.% is missing or not a positive integer', k using errcode = 'P0001';
    end if;
  end loop;
  if jsonb_typeof(v -> 'blockedPins') is distinct from 'array' then
    raise exception 'app_config.portal.blockedPins is missing or not an array' using errcode = 'P0001';
  end if;
  return v;
end;
$$;

-- The Worker gate (DECISIONS #101): true only when the request carries the header x-portal-key whose sha256 is the
-- stored hash. PostgREST puts the request's headers (names lowercased) in request.headers as JSON. False when the
-- table is empty, the header is missing or empty, or the setting isn't JSON. The key is always hashed.
create or replace function private.portal_gate_ok()
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_key  text;
  v_hash bytea;
begin
  begin
    v_key := (nullif(current_setting('request.headers', true), '')::jsonb) ->> 'x-portal-key';
  exception when others then
    v_key := null;
  end;
  select g.key_hash into v_hash from private.portal_gate g where g.id = 1;
  return coalesce(sha256(convert_to(coalesce(v_key, ''), 'UTF8')) = v_hash and v_key <> '', false);
end;
$$;

-- The per-site failed-login cap: failures on this site in the last minute (portal_login checks it before bcrypt).
create or replace function private.portal_site_failures(p_site_id uuid)
returns integer
language sql
stable
security definer
set search_path = ''
as $$
  select count(*)::int from private.portal_login_failures f
   where f.site_id = p_site_id and f.at > now() - interval '1 minute';
$$;

-- Record one failed login for a site, pruning that site's rows older than a minute.
create or replace function private.portal_login_failed(p_site_id uuid)
returns void
language sql
volatile
security definer
set search_path = ''
as $$
  delete from private.portal_login_failures f where f.site_id = p_site_id and f.at <= now() - interval '1 minute';
  insert into private.portal_login_failures (site_id) values (p_site_id);
$$;

-- 32 random bytes, base64url without padding (43 characters).
create or replace function private.new_token()
returns text
language sql
volatile
set search_path = ''
as $$
  select translate(rtrim(encode(extensions.gen_random_bytes(32), 'base64'), '='), '+/', '-_');
$$;

-- sha256 of a well-formed token; null for anything else (so junk never reaches a lookup).
create or replace function private.token_hash(p_token text)
returns bytea
language sql
immutable
set search_path = ''
as $$
  select case when p_token ~ '^[A-Za-z0-9_-]{43}$' then sha256(convert_to(p_token, 'UTF8')) end;
$$;

-- PIN rule (DECISIONS #96, #101): exactly pinLength digits; not one digit repeated; not an ascending or descending
-- run; not a repeated pair or triple (ababab, abcabc) or doubled digits (aabbcc); not in catalog.portal.blockedPins;
-- not the end of the account's mobile number; not any of p_dobs (the customers on the login, the one joining
-- included) as DDMMYY, YYMMDD or MMDDYY.
create or replace function private.pin_ok(p_pin text, p_phone text, p_dobs date[])
returns boolean
language plpgsql
stable
set search_path = ''
as $$
declare
  v_cfg   jsonb := private.portal_config();
  v_len   int := (v_cfg ->> 'pinLength')::int;
  v_pairs boolean;
  v_dob   date;
begin
  if p_pin is null or p_pin !~ ('^[0-9]{' || v_len || '}$') then
    return false;
  end if;
  if p_pin ~ '^(.)\1*$'
     or position(p_pin in '0123456789') > 0
     or position(p_pin in '9876543210') > 0 then
    return false;
  end if;
  if (v_len % 2 = 0 and p_pin = repeat(left(p_pin, 2), v_len / 2))
     or (v_len % 3 = 0 and p_pin = repeat(left(p_pin, 3), v_len / 3)) then
    return false;
  end if;
  if v_len % 2 = 0 then
    v_pairs := true;
    for i in 0 .. v_len / 2 - 1 loop
      if substr(p_pin, 2 * i + 1, 1) <> substr(p_pin, 2 * i + 2, 1) then
        v_pairs := false;
      end if;
    end loop;
    if v_pairs then
      return false;
    end if;
  end if;
  if exists (select 1 from jsonb_array_elements_text(v_cfg -> 'blockedPins') b where b = p_pin) then
    return false;
  end if;
  if p_phone is not null and p_pin = right(regexp_replace(p_phone, '[^0-9]', '', 'g'), v_len) then
    return false;
  end if;
  foreach v_dob in array coalesce(p_dobs, '{}'::date[]) loop
    if v_dob is not null
       and p_pin in (to_char(v_dob, 'DDMMYY'), to_char(v_dob, 'YYMMDD'), to_char(v_dob, 'MMDDYY')) then
      return false;
    end if;
  end loop;
  return true;
end;
$$;

-- The portal is on for a site that find_public_site already serves: flag on and the contact toggle not false.
create or replace function private.portal_available(p_site public.sites)
returns boolean
language sql
stable
set search_path = ''
as $$
  select p_site.id is not null
     and private.flag_enabled('customerPortal')
     and (p_site.published_content #>> '{contact,showCustomerLogin}') is distinct from 'false';
$$;

-- The site for the anon RPCs: resolved exactly like submit_enquiry (slug wins, else host), then portal available.
create or replace function private.portal_site(p_slug text, p_host text)
returns public.sites
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_site public.sites := private.find_public_site(p_slug, p_host);
begin
  if v_site.id is null or not private.portal_available(v_site) then
    return null;
  end if;
  return v_site;
end;
$$;

create or replace function private.portal_lock_account(p_account_id uuid)
returns void
language sql
volatile
set search_path = ''
as $$
  select pg_advisory_xact_lock(hashtextextended('portal_account|' || p_account_id::text, 0));
$$;

-- A live session of this site's agent whose account is live and still shows at least one live customer; null
-- otherwise. Touches last_seen_at at most every 10 minutes.
create or replace function private.portal_session_for_site(p_token text, p_site public.sites)
returns public.portal_sessions
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_hash bytea := private.token_hash(p_token);
  v      public.portal_sessions;
begin
  if v_hash is null or p_site.id is null then
    return null;
  end if;
  select s.* into v
    from public.portal_sessions s
    join public.portal_accounts a on a.id = s.account_id
   where s.token_hash = v_hash
     and s.agent_id = p_site.agent_id
     and s.revoked_at is null
     and s.expires_at > now()
     and a.deleted_at is null
     and exists (select 1 from public.portal_account_customers l
                   join public.customers c on c.id = l.customer_id
                  where l.account_id = a.id and c.deleted_at is null);
  if v.id is null then
    return null;
  end if;
  if v.last_seen_at is null or v.last_seen_at < now() - interval '10 minutes' then
    update public.portal_sessions set last_seen_at = now() where id = v.id;
  end if;
  return v;
end;
$$;

-- portal_session(token, slug, host): the session, checked against the site it's used on; null otherwise.
create or replace function private.portal_session(p_token text, p_slug text, p_host text)
returns public.portal_sessions
language sql
volatile
security definer
set search_path = ''
as $$
  select private.portal_session_for_site(p_token, private.portal_site(p_slug, p_host));
$$;

-- A new session for an account: {ok:true, session_token, expires_at}. The token is returned only here.
create or replace function private.portal_new_session(p_account_id uuid, p_agent_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_token text := private.new_token();
  v_exp   timestamptz := now() + make_interval(days => (private.portal_config() ->> 'sessionDays')::int);
begin
  insert into public.portal_sessions (agent_id, account_id, token_hash, expires_at, last_seen_at)
  values (p_agent_id, p_account_id, private.token_hash(v_token), v_exp, now());
  return jsonb_build_object('ok', true, 'session_token', v_token, 'expires_at', v_exp);
end;
$$;

-- audit_log row for an account. p_actor null = the customer / the system. Never personal data in p_changes.
create or replace function private.portal_audit(p_action text, p_account_id uuid, p_actor uuid, p_changes jsonb)
returns void
language sql
volatile
security definer
set search_path = ''
as $$
  insert into public.audit_log (actor_id, actor_kind, action, entity, entity_id, changes)
  values (p_actor, case when p_actor is null then 'system' else 'agent' end, p_action, 'portal_accounts',
          p_account_id, p_changes);
$$;

-- Detach a customer from every portal account (except p_keep_account): remove its links and void its unused
-- invites. An account (among those) left with no customer has its sessions revoked and its PIN, counters and locks
-- wiped (L6), and is soft-deleted unless another customer's link to it is still live (that customer can still
-- activate it). The accounts' advisory locks are taken first, in id order. Shared by portal_revoke,
-- portal_activate and the customers triggers. Returns the number of links removed.
create or replace function private.portal_detach(p_customer_id uuid, p_keep_account uuid default null)
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_accounts uuid[];
  v_acc      uuid;
  n          int;
begin
  select coalesce(array_agg(x.account_id order by x.account_id), '{}'::uuid[]) into v_accounts
    from (select l.account_id from public.portal_account_customers l where l.customer_id = p_customer_id
          union
          select i.account_id from public.portal_invites i
           where i.customer_id = p_customer_id and i.used_at is null and i.revoked_at is null) x
   where x.account_id is distinct from p_keep_account;
  foreach v_acc in array v_accounts loop
    perform private.portal_lock_account(v_acc);
  end loop;

  delete from public.portal_account_customers l
   where l.customer_id = p_customer_id and l.account_id is distinct from p_keep_account;
  get diagnostics n = row_count;
  update public.portal_invites i
     set revoked_at = now()
   where i.customer_id = p_customer_id and i.account_id is distinct from p_keep_account
     and i.used_at is null and i.revoked_at is null;

  foreach v_acc in array v_accounts loop
    if not exists (select 1 from public.portal_account_customers l where l.account_id = v_acc) then
      update public.portal_sessions set revoked_at = now() where account_id = v_acc and revoked_at is null;
      update public.portal_accounts a
         set pin_hash = null, activated_at = null, failed_attempts = 0, failure_window_started_at = null,
             failure_streak = 0, locked_until = null, hard_locked_at = null,
             deleted_at = case
               when exists (select 1 from public.portal_invites i
                             where i.account_id = a.id and i.used_at is null and i.revoked_at is null
                               and i.expires_at > now()) then a.deleted_at
               else coalesce(a.deleted_at, now()) end
       where a.id = v_acc;
    end if;
  end loop;
  return n;
end;
$$;

-- customers triggers (SECURITY DEFINER: the app's writes fire them as `authenticated`).
create or replace function private.portal_customers_changed()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if (new.deleted_at is not null and old.deleted_at is null)
     or private.normalize_in_phone(new.phone) is distinct from private.normalize_in_phone(old.phone) then
    perform private.portal_detach(new.id);
  end if;
  return null;
end;
$$;

create or replace function private.portal_customers_deleting()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  -- Account deletion (auth.users cascade): the owner row is already gone and every portal row cascades too;
  -- updating them here would re-check their FKs against the deleted owner and fail the deletion.
  if exists (select 1 from auth.users u where u.id = old.agent_id) then
    perform private.portal_detach(old.id);
  end if;
  return old;
end;
$$;

create trigger portal_customers_changed
  after update of phone, deleted_at on public.customers
  for each row
  when (old.phone is distinct from new.phone or old.deleted_at is distinct from new.deleted_at)
  execute function private.portal_customers_changed();

create trigger portal_customers_deleting
  before delete on public.customers
  for each row execute function private.portal_customers_deleting();

-- ===========================================================================
-- The public payload: the companies_offers definition, plus "portal" (boolean). Nothing else changes.
-- ===========================================================================
create or replace function private.public_site_payload(p_site public.sites)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'template_key',      p_site.template_key,
    'accent',            p_site.accent,
    'locale',            p_site.locale,
    'published_at',      p_site.published_at,
    'published_content', p_site.published_content,
    'portal',            private.portal_available(p_site),
    'canonical_host', (
      select d.hostname from public.site_domains d
       where d.site_id = p_site.id and d.kind = 'custom' and d.deleted_at is null
         and d.status in ('active', 'renew_soon')
         and (d.expires_at is null or d.expires_at > now())
       order by (d.id is not distinct from p_site.primary_domain_id) desc, d.created_at
       limit 1),
    'profile', jsonb_build_object(
        'full_name',   p.full_name,
        'designation', p.designation,
        'city',        p.city,
        'photo_url',   p.photo_url,
        'phone',       p.phone)
      || case when p_site.published_content #> '{about,showAgencyCode}' = 'true'::jsonb
              then jsonb_build_object('agency_code', p.agency_code)
              else '{}'::jsonb end
  ) || private.site_offers_payload(p_site)
  from public.profiles p
  where p.id = p_site.agent_id;
$$;

-- ===========================================================================
-- Agent RPCs (authenticated)
-- ===========================================================================

-- Send login link: {ok:true, token, expires_at, already_active} | {ok:false, error:'invalid', field:'phone'}
-- | {ok:false, error:'no_site'} | {ok:false, error:'unavailable'} (flag off).
-- Raises not_authenticated / plan_read_only / not_found (another agent's, unknown or deleted customer, or one
-- deleted or given another number while the link was being made).
-- Creates or finds the (agent, phone) account and issues the link; it attaches no one (DECISIONS #101, M1): the
-- customer joins the login when this link is activated. Lock order (L7): the customer is read without a lock, then
-- the advisory locks (the (agent, phone) lock, then the account lock: always that order), then the customer row
-- (FOR SHARE, so a concurrent edit of the number or a delete waits or is seen) and a re-check.
create or replace function public.portal_invite(customer_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid    uuid := private.require_uid();
  v_cust   public.customers;
  v_phone  text;
  v_cfg    jsonb;
  v_acc    public.portal_accounts;
  v_active boolean;
  v_token  text;
  v_exp    timestamptz;
begin
  perform private.require_can_write();
  select c.* into v_cust from public.customers c
   where c.id = portal_invite.customer_id and c.agent_id = v_uid and c.deleted_at is null;
  if v_cust.id is null then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  if not private.flag_enabled('customerPortal') then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_phone := private.normalize_in_phone(v_cust.phone);
  if v_phone is null then
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', 'phone');
  end if;
  if not exists (select 1 from public.sites s
                  where s.agent_id = v_uid and s.deleted_at is null
                    and s.is_published and s.published_content is not null) then
    return jsonb_build_object('ok', false, 'error', 'no_site');
  end if;
  v_cfg := private.portal_config();

  -- One live account per (agent, phone): serialise its creation, then lock the account itself.
  perform pg_advisory_xact_lock(hashtextextended('portal_phone|' || v_uid::text || '|' || v_phone, 0));
  select a.* into v_acc from public.portal_accounts a
   where a.agent_id = v_uid and a.phone = v_phone and a.deleted_at is null;
  if v_acc.id is null then
    insert into public.portal_accounts (agent_id, phone) values (v_uid, v_phone) returning * into v_acc;
  end if;
  perform private.portal_lock_account(v_acc.id);

  -- Row locks only after the advisory locks; re-check the customer under the lock.
  select c.* into v_cust from public.customers c
   where c.id = v_cust.id and c.agent_id = v_uid
   for share;
  if v_cust.id is null or v_cust.deleted_at is not null
     or private.normalize_in_phone(v_cust.phone) is distinct from v_phone then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  select a.* into v_acc from public.portal_accounts a where a.id = v_acc.id for update;
  v_active := v_acc.pin_hash is not null;

  -- Only the customer's newest link works (other customers' links on the same number stay live).
  update public.portal_invites i
     set revoked_at = now()
   where i.customer_id = v_cust.id and i.agent_id = v_uid and i.used_at is null and i.revoked_at is null;

  v_token := private.new_token();
  v_exp := now() + make_interval(days => (v_cfg ->> 'inviteDays')::int);
  insert into public.portal_invites (agent_id, account_id, customer_id, token_hash, expires_at)
  values (v_uid, v_acc.id, v_cust.id, private.token_hash(v_token), v_exp);

  perform private.portal_audit('portal.invite', v_acc.id, v_uid, jsonb_build_object('already_active', v_active));
  return jsonb_build_object('ok', true, 'token', v_token, 'expires_at', v_exp, 'already_active', v_active);
end;
$$;

-- Turn off: detach the customer and void its unused links (an account left empty is wiped and closed, see
-- portal_detach). No can_write. {ok:true}. Raises not_authenticated / not_found.
create or replace function public.portal_revoke(customer_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid      uuid := private.require_uid();
  v_customer uuid;
  v_accounts uuid[];
  v_acc      uuid;
begin
  select c.id into v_customer from public.customers c
   where c.id = portal_revoke.customer_id and c.agent_id = v_uid;
  if v_customer is null then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  select array_agg(x.account_id) into v_accounts
    from (select l.account_id from public.portal_account_customers l where l.customer_id = v_customer
          union
          select i.account_id from public.portal_invites i
           where i.customer_id = v_customer and i.used_at is null and i.revoked_at is null) x;
  perform private.portal_detach(v_customer);
  foreach v_acc in array coalesce(v_accounts, '{}'::uuid[]) loop
    perform private.portal_audit('portal.revoke', v_acc, v_uid, jsonb_build_object(
      'account_closed', (select a.deleted_at is not null from public.portal_accounts a where a.id = v_acc)));
  end loop;
  return jsonb_build_object('ok', true);
end;
$$;

-- {state: none|invited|active|locked, invite_expires_at, last_login_at, locked_until, shared_with:[name,…],
--  portal_available}. active = the customer is attached to a live login (locked when that login is soft- or
-- hard-locked now; locked_until = the later end); invited = not attached, with a live link of its own;
-- invite_expires_at = the customer's live link, if any (also when active: a PIN reset is pending); last_login_at
-- only when attached; shared_with = the other customers attached to the login it is on, or that its link joins.
-- portal_available = the agent's site serves the portal now (published, owner not lapsed, flag on, contact toggle
-- on), so the app can warn before sharing a link. Raises not_authenticated / not_found.
create or replace function public.portal_status(customer_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid       uuid := private.require_uid();
  v_customer  uuid;
  v_cfg       jsonb;
  v_site      public.sites;
  v_available boolean;
  v_acc       public.portal_accounts;
  v_inv       public.portal_invites;
  v_until     timestamptz;
  v_shared    uuid;
  v_state     text;
begin
  select c.id into v_customer from public.customers c
   where c.id = portal_status.customer_id and c.agent_id = v_uid;
  if v_customer is null then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  v_cfg := private.portal_config();
  select s.* into v_site from public.sites s where s.agent_id = v_uid and s.deleted_at is null
   order by s.created_at limit 1;
  v_available := v_site.id is not null and (private.portal_site(v_site.slug, null)).id is not null;

  select a.* into v_acc
    from public.portal_account_customers l
    join public.portal_accounts a on a.id = l.account_id
   where l.customer_id = v_customer and a.deleted_at is null
   limit 1;
  select i.* into v_inv
    from public.portal_invites i
    join public.portal_accounts a on a.id = i.account_id
   where i.customer_id = v_customer and i.used_at is null and i.revoked_at is null and i.expires_at > now()
     and i.phone_tries < (v_cfg ->> 'inviteMaxPhoneTries')::int and a.deleted_at is null
   order by i.expires_at desc
   limit 1;

  if v_acc.id is not null then
    v_until := greatest(
      case when v_acc.locked_until > now() then v_acc.locked_until end,
      case when v_acc.hard_locked_at + make_interval(hours => (v_cfg ->> 'hardLockHours')::int) > now()
           then v_acc.hard_locked_at + make_interval(hours => (v_cfg ->> 'hardLockHours')::int) end);
    v_state := case when v_until is not null then 'locked' else 'active' end;
    v_shared := v_acc.id;
  elsif v_inv.id is not null then
    v_state := 'invited';
    v_shared := v_inv.account_id;
  else
    v_state := 'none';
  end if;

  return jsonb_build_object(
    'state', v_state,
    'invite_expires_at', v_inv.expires_at,
    'last_login_at', v_acc.last_login_at,
    'locked_until', v_until,
    'shared_with', coalesce((
      select jsonb_agg(c.name order by c.name, c.id)
        from public.portal_account_customers l
        join public.customers c on c.id = l.customer_id
       where l.account_id = v_shared and c.id <> v_customer and c.deleted_at is null), '[]'::jsonb),
    'portal_available', v_available);
end;
$$;

-- ===========================================================================
-- Anon RPCs (the Worker). Never raise for user input.
-- ===========================================================================

-- Set up the login from an invite: the Worker gate, then, in order: site, invite (live, unused, this site's agent,
-- its customer still live and still on the account's number), mobile (a mismatch counts; the last allowed one burns
-- the invite), PIN rule (pin_ok, with the dates of birth of the joining customer and of those already on the login),
-- consent (customer_portal). Then it sets the PIN, clears every counter and lock, revokes every session, marks the
-- invite used, attaches the invite's customer (DECISIONS #101, M1: only now, and only that customer) and records ONE
-- consent for that customer, and opens a session. Lock order (L7): the advisory locks of the invite's account and of
-- any account the customer is on or has a live link to (sorted), then the rows (invite, account, customer FOR SHARE).
-- {ok:true, session_token, expires_at} | {ok:false, error:'unavailable'|'invalid_invite'}
-- | {ok:false, error:'invalid', field:'phone'|'pin'|'consent'}.
create or replace function public.portal_activate(slug text, host text, token text, phone text, pin text, consent jsonb)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_site    public.sites;
  v_hash    bytea;
  v_cfg     jsonb;
  v_inv     public.portal_invites;
  v_acc     public.portal_accounts;
  v_cust    public.customers;
  v_phone   text;
  v_max     int;
  v_consent jsonb;
  v_locks   uuid[];
  v_lock    uuid;
  v_dobs    date[];
  v_n       int;
begin
  if not private.portal_gate_ok() then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_site := private.portal_site(portal_activate.slug, portal_activate.host);
  if v_site.id is null then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_hash := private.token_hash(portal_activate.token);
  if v_hash is null then
    return jsonb_build_object('ok', false, 'error', 'invalid_invite');
  end if;
  v_cfg := private.portal_config();
  v_max := (v_cfg ->> 'inviteMaxPhoneTries')::int;

  select i.* into v_inv from public.portal_invites i
   where i.token_hash = v_hash and i.agent_id = v_site.agent_id;
  if v_inv.id is null then
    return jsonb_build_object('ok', false, 'error', 'invalid_invite');
  end if;

  select array_agg(x.account_id order by x.account_id) into v_locks
    from (select v_inv.account_id
          union
          select l.account_id from public.portal_account_customers l where l.customer_id = v_inv.customer_id
          union
          select i.account_id from public.portal_invites i
           where i.customer_id = v_inv.customer_id and i.used_at is null and i.revoked_at is null) x(account_id);
  foreach v_lock in array v_locks loop
    perform private.portal_lock_account(v_lock);
  end loop;
  select i.* into v_inv from public.portal_invites i where i.id = v_inv.id for update;
  select a.* into v_acc from public.portal_accounts a where a.id = v_inv.account_id for update;
  select c.* into v_cust from public.customers c
   where c.id = v_inv.customer_id and c.agent_id = v_inv.agent_id
   for share;
  if v_inv.used_at is not null or v_inv.revoked_at is not null or v_inv.expires_at <= now()
     or v_inv.phone_tries >= v_max or v_acc.id is null or v_acc.deleted_at is not null
     or v_cust.id is null or v_cust.deleted_at is not null
     or private.normalize_in_phone(v_cust.phone) is distinct from v_acc.phone then
    return jsonb_build_object('ok', false, 'error', 'invalid_invite');
  end if;

  v_phone := private.normalize_in_phone(portal_activate.phone);
  if v_phone is distinct from v_acc.phone then
    update public.portal_invites
       set phone_tries = phone_tries + 1,
           revoked_at = case when phone_tries + 1 >= v_max then now() else revoked_at end
     where id = v_inv.id;
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', 'phone');
  end if;

  select array_agg(c.dob) into v_dobs
    from public.customers c
   where c.dob is not null and c.deleted_at is null
     and (c.id = v_cust.id
          or c.id in (select l.customer_id from public.portal_account_customers l where l.account_id = v_acc.id));
  if not private.pin_ok(portal_activate.pin, v_acc.phone, v_dobs) then
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', 'pin');
  end if;

  v_consent := private.resolve_consent(portal_activate.consent, 'customer_portal');
  if v_consent is null then
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', 'consent');
  end if;

  -- A customer is on one account: a stale link elsewhere goes first (its lock is already held).
  perform private.portal_detach(v_cust.id, v_acc.id);

  update public.portal_accounts
     set pin_hash = extensions.crypt(portal_activate.pin, extensions.gen_salt('bf', 10)),
         activated_at = now(),
         failed_attempts = 0,
         failure_window_started_at = null,
         failure_streak = 0,
         locked_until = null,
         hard_locked_at = null
   where id = v_acc.id;
  update public.portal_sessions set revoked_at = now() where account_id = v_acc.id and revoked_at is null;
  update public.portal_invites set used_at = now() where id = v_inv.id;

  insert into public.portal_account_customers (agent_id, account_id, customer_id)
  values (v_acc.agent_id, v_acc.id, v_cust.id)
  on conflict on constraint portal_account_customers_customer_key do nothing;

  insert into public.consents (agent_id, principal_kind, customer_id, notice_key, notice_version, notice_hash, locale, channel)
  values (v_acc.agent_id, 'customer', v_cust.id, v_consent ->> 'notice_key', (v_consent ->> 'version')::int,
          v_consent ->> 'hash', v_consent ->> 'locale', 'web');

  select count(*) into v_n from public.portal_account_customers l where l.account_id = v_acc.id;
  perform private.portal_audit('portal.activate', v_acc.id, null, jsonb_build_object('customers', v_n));
  return private.portal_new_session(v_acc.id, v_acc.agent_id);
end;
$$;

-- Mobile + PIN. {ok:true, session_token, expires_at} | {ok:false, error:'invalid'}
-- | {ok:false, error:'locked', until:<timestamptz>} | {ok:false, error:'rate_limited'} | {ok:false, error:'unavailable'}.
-- The Worker gate first; then the site; then the per-site cap (siteFailedLoginsPerMinute failures in the last
-- minute -> rate_limited, before any bcrypt). Every refused attempt after that counts towards the cap. An unknown,
-- unactivated or locked account still runs one bcrypt, so the timing is the same.
-- Counters (DECISIONS #101, M2): failed_attempts counts wrong PINs since failure_window_started_at, a window of
-- failureWindowDays (a failure after it starts a new one); a success does NOT reset it. failure_streak counts wrong
-- PINs since the last success or soft lock: at maxFailedAttempts the account is locked for lockMinutes. At
-- hardLockAfter failures in the window it is hard-locked for hardLockHours; an expired hard lock clears itself (and
-- the counters) on the next attempt. 'locked' always carries until (the end of the soft or hard lock).
create or replace function public.portal_login(slug text, host text, phone text, pin text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_site   public.sites;
  v_pin    text;
  v_phone  text;
  v_cfg    jsonb;
  v_acc_id uuid;
  v_acc    public.portal_accounts;
  v_hard   interval;
  v_n      int;
  v_streak int;
  v_window timestamptz;
  v_until  timestamptz;
begin
  if not private.portal_gate_ok() then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_site := private.portal_site(portal_login.slug, portal_login.host);
  if v_site.id is null then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_cfg := private.portal_config();
  if private.portal_site_failures(v_site.id) >= (v_cfg ->> 'siteFailedLoginsPerMinute')::int then
    return jsonb_build_object('ok', false, 'error', 'rate_limited');
  end if;
  v_hard := make_interval(hours => (v_cfg ->> 'hardLockHours')::int);
  v_pin := left(coalesce(portal_login.pin, ''), 72);
  v_phone := private.normalize_in_phone(portal_login.phone);

  if v_phone is not null then
    select a.id into v_acc_id from public.portal_accounts a
     where a.agent_id = v_site.agent_id and a.phone = v_phone and a.deleted_at is null;
  end if;
  if v_acc_id is not null then
    perform private.portal_lock_account(v_acc_id);
    select a.* into v_acc from public.portal_accounts a where a.id = v_acc_id for update;
  end if;

  if v_acc.id is null or v_acc.deleted_at is not null or v_acc.pin_hash is null then
    perform extensions.crypt(v_pin, extensions.gen_salt('bf', 10));
    perform private.portal_login_failed(v_site.id);
    return jsonb_build_object('ok', false, 'error', 'invalid');
  end if;
  if v_acc.hard_locked_at is not null then
    if v_acc.hard_locked_at + v_hard > now() then
      perform extensions.crypt(v_pin, extensions.gen_salt('bf', 10));
      perform private.portal_login_failed(v_site.id);
      return jsonb_build_object('ok', false, 'error', 'locked', 'until', v_acc.hard_locked_at + v_hard);
    end if;
    -- The hard lock has run out: it clears itself with the counters.
    update public.portal_accounts
       set hard_locked_at = null, failed_attempts = 0, failure_window_started_at = null, failure_streak = 0,
           locked_until = null
     where id = v_acc.id
    returning * into v_acc;
  end if;
  if v_acc.locked_until > now() then
    perform extensions.crypt(v_pin, extensions.gen_salt('bf', 10));
    perform private.portal_login_failed(v_site.id);
    return jsonb_build_object('ok', false, 'error', 'locked', 'until', v_acc.locked_until);
  end if;

  if extensions.crypt(v_pin, v_acc.pin_hash) = v_acc.pin_hash then
    update public.portal_accounts
       set failure_streak = 0, locked_until = null, last_login_at = now()
     where id = v_acc.id;
    return private.portal_new_session(v_acc.id, v_acc.agent_id);
  end if;

  perform private.portal_login_failed(v_site.id);
  if v_acc.failure_window_started_at is null
     or v_acc.failure_window_started_at <= now() - make_interval(days => (v_cfg ->> 'failureWindowDays')::int) then
    v_n := 1;
    v_window := now();
  else
    v_n := v_acc.failed_attempts + 1;
    v_window := v_acc.failure_window_started_at;
  end if;
  v_streak := v_acc.failure_streak + 1;

  if v_n >= (v_cfg ->> 'hardLockAfter')::int then
    update public.portal_accounts
       set failed_attempts = v_n, failure_window_started_at = v_window, failure_streak = 0, locked_until = null,
           hard_locked_at = now()
     where id = v_acc.id;
    perform private.portal_audit('portal.hard_lock', v_acc.id, null, jsonb_build_object('failed_attempts', v_n));
    return jsonb_build_object('ok', false, 'error', 'locked', 'until', now() + v_hard);
  end if;
  if v_streak >= (v_cfg ->> 'maxFailedAttempts')::int then
    v_until := now() + make_interval(mins => (v_cfg ->> 'lockMinutes')::int);
    update public.portal_accounts
       set failed_attempts = v_n, failure_window_started_at = v_window, failure_streak = 0, locked_until = v_until
     where id = v_acc.id;
    return jsonb_build_object('ok', false, 'error', 'locked', 'until', v_until);
  end if;
  update public.portal_accounts
     set failed_attempts = v_n, failure_window_started_at = v_window, failure_streak = v_streak
   where id = v_acc.id;
  return jsonb_build_object('ok', false, 'error', 'invalid');
end;
$$;

-- The "My policies" page: the account's attached, live customers (name order) with their live policies
-- (next_due_date, nulls last) and live payments (newest first, at most paymentsShown). Never notes.
-- {ok:true, customers:[…]} | {ok:false, error:'session'|'unavailable'} ('unavailable' also without the Worker gate).
create or replace function public.portal_home(token text, slug text, host text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_site  public.sites;
  v_sess  public.portal_sessions;
  v_limit int;
begin
  if not private.portal_gate_ok() then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_site := private.portal_site(portal_home.slug, portal_home.host);
  if v_site.id is null then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_sess := private.portal_session_for_site(portal_home.token, v_site);
  if v_sess.id is null then
    return jsonb_build_object('ok', false, 'error', 'session');
  end if;
  v_limit := (private.portal_config() ->> 'paymentsShown')::int;

  return jsonb_build_object('ok', true, 'customers', coalesce((
    select jsonb_agg(jsonb_build_object(
             'id', c.id, 'name', c.name, 'phone', c.phone, 'email', c.email,
             'dob', to_char(c.dob, 'YYYY-MM-DD'), 'city', c.city, 'address', c.address, 'nominee', c.nominee,
             'policies', coalesce((
               select jsonb_agg(jsonb_build_object(
                        'id', p.id, 'plan_name', p.plan_name, 'policy_number', p.policy_number,
                        'sum_assured', p.sum_assured, 'premium_amount', p.premium_amount,
                        'premium_mode', p.premium_mode, 'start_date', to_char(p.start_date, 'YYYY-MM-DD'),
                        'next_due_date', to_char(p.next_due_date, 'YYYY-MM-DD'), 'status', p.status,
                        'payments', coalesce((
                          select jsonb_agg(jsonb_build_object('amount', x.amount, 'paid_on', to_char(x.paid_on, 'YYYY-MM-DD'))
                                           order by x.paid_on desc, x.created_at desc, x.id desc)
                            from (select pp.amount, pp.paid_on, pp.created_at, pp.id
                                    from public.premium_payments pp
                                   where pp.policy_id = p.id and pp.agent_id = p.agent_id and pp.deleted_at is null
                                   order by pp.paid_on desc, pp.created_at desc, pp.id desc
                                   limit v_limit) x), '[]'::jsonb))
                      order by p.next_due_date asc nulls last, p.created_at, p.id)
                 from public.policies p
                where p.customer_id = c.id and p.agent_id = c.agent_id and p.deleted_at is null), '[]'::jsonb))
           order by c.name, c.id)
      from public.portal_account_customers l
      join public.customers c on c.id = l.customer_id and c.agent_id = l.agent_id
     where l.account_id = v_sess.account_id and c.deleted_at is null), '[]'::jsonb));
end;
$$;

-- "Ask about this policy" / "I've paid this premium": an enquiry (source 'portal', status 'new') for the policy's
-- customer, message prefixed with the plan and policy number. kind in {question, paid}; message 1–500 after trim;
-- at most enquiriesPerHour portal enquiries per account (same site and phone) per rolling hour, soft-deleted count.
-- {ok:true} | {ok:false, error:'session'|'unavailable'|'rate_limited'} | {ok:false, error:'invalid', field}.
create or replace function public.portal_ask(token text, slug text, host text, policy_id uuid, kind text, message text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_site    public.sites;
  v_sess    public.portal_sessions;
  v_kind    text := btrim(coalesce(portal_ask.kind, ''));
  v_message text := btrim(coalesce(portal_ask.message, ''));
  v_phone   text;
  v_pol     record;
  v_prefix  text;
  v_recent  int;
begin
  if not private.portal_gate_ok() then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_site := private.portal_site(portal_ask.slug, portal_ask.host);
  if v_site.id is null then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_sess := private.portal_session_for_site(portal_ask.token, v_site);
  if v_sess.id is null then
    return jsonb_build_object('ok', false, 'error', 'session');
  end if;

  select p.id, p.plan_name, p.policy_number, c.id as customer_id, c.name as customer_name into v_pol
    from public.policies p
    join public.customers c on c.id = p.customer_id and c.agent_id = p.agent_id
    join public.portal_account_customers l on l.customer_id = c.id and l.account_id = v_sess.account_id
   where p.id = portal_ask.policy_id and p.agent_id = v_sess.agent_id
     and p.deleted_at is null and c.deleted_at is null;
  if v_pol.id is null then
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', 'policy_id');
  end if;
  if v_kind not in ('question', 'paid') then
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', 'kind');
  end if;
  if char_length(v_message) < 1 or char_length(v_message) > 500 then
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', 'message');
  end if;

  perform private.portal_lock_account(v_sess.account_id);
  select a.phone into v_phone from public.portal_accounts a where a.id = v_sess.account_id;
  select count(*) into v_recent
    from public.enquiries e
   where e.site_id = v_site.id and e.phone = v_phone and e.source = 'portal'
     and e.created_at > now() - interval '1 hour';
  if v_recent >= (private.portal_config() ->> 'enquiriesPerHour')::int then
    return jsonb_build_object('ok', false, 'error', 'rate_limited');
  end if;

  v_prefix := concat_ws(' · ', nullif(btrim(coalesce(v_pol.plan_name, '')), ''),
                               nullif(btrim(coalesce(v_pol.policy_number, '')), ''));
  insert into public.enquiries (agent_id, site_id, name, phone, source, message, status, customer_id)
  values (v_sess.agent_id, v_site.id, v_pol.customer_name, v_phone, 'portal',
          case when v_prefix = '' then v_message else '[' || v_prefix || '] ' || v_message end,
          'new', v_pol.customer_id);
  return jsonb_build_object('ok', true);
end;
$$;

-- A data-principal request from the verified customer: submit_data_request's rules (kind in
-- catalog_data_request_kinds(), message optional <= 500, due after retention.dataRequests.responseDays) with
-- customer_id set. Its own limit (DECISIONS #101, L2): 3 portal requests per login (the account's agent and mobile,
-- rows with a customer_id, which only this RPC sets) per rolling 24 hours, under the account lock, so requests from
-- the public form never block a verified customer.
-- {ok:true} | {ok:false, error:'session'|'unavailable'|'rate_limited'}
-- | {ok:false, error:'invalid', field:'customer_id'|'kind'|'message'}.
create or replace function public.portal_request(token text, slug text, host text, customer_id uuid, kind text, message text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_site    public.sites;
  v_sess    public.portal_sessions;
  v_kind    text := nullif(btrim(coalesce(portal_request.kind, '')), '');
  v_message text := nullif(btrim(coalesce(portal_request.message, '')), '');
  v_cust    public.customers;
  v_phone   text;
  v_recent  int;
  v_days    int;
begin
  if not private.portal_gate_ok() then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_site := private.portal_site(portal_request.slug, portal_request.host);
  if v_site.id is null then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_sess := private.portal_session_for_site(portal_request.token, v_site);
  if v_sess.id is null then
    return jsonb_build_object('ok', false, 'error', 'session');
  end if;

  select c.* into v_cust
    from public.customers c
    join public.portal_account_customers l on l.customer_id = c.id and l.account_id = v_sess.account_id
   where c.id = portal_request.customer_id and c.agent_id = v_sess.agent_id and c.deleted_at is null;
  if v_cust.id is null then
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', 'customer_id');
  end if;
  if v_kind is null or not (v_kind = any (coalesce(public.catalog_data_request_kinds(), '{}'::text[]))) then
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', 'kind');
  end if;
  if v_message is not null and char_length(v_message) > 500 then
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', 'message');
  end if;

  perform private.portal_lock_account(v_sess.account_id);
  select a.phone into v_phone from public.portal_accounts a where a.id = v_sess.account_id;
  select count(*) into v_recent
    from public.data_requests r
   where r.agent_id = v_sess.agent_id and r.phone = v_phone and r.customer_id is not null
     and r.created_at > now() - interval '24 hours';
  if v_recent >= 3 then
    return jsonb_build_object('ok', false, 'error', 'rate_limited');
  end if;

  v_days := (private.retention_config() #>> '{dataRequests,responseDays}')::int;
  insert into public.data_requests (agent_id, addressed_to, kind, status, name, phone, message, customer_id, due_at)
  values (v_sess.agent_id, 'agent', v_kind, 'open',
          coalesce(nullif(left(btrim(v_cust.name), 80), ''), v_phone), v_phone, v_message, v_cust.id,
          now() + make_interval(days => v_days));
  return jsonb_build_object('ok', true);
end;
$$;

-- Sign out this device, or (everywhere, with a live session) every device of the account. {ok:true}, or
-- {ok:false, error:'unavailable'} without the Worker gate (then nothing is revoked).
create or replace function public.portal_logout(token text, everywhere boolean)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_hash bytea;
  v_sess public.portal_sessions;
begin
  if not private.portal_gate_ok() then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_hash := private.token_hash(portal_logout.token);
  if v_hash is null then
    return jsonb_build_object('ok', true);
  end if;
  select s.* into v_sess from public.portal_sessions s
   where s.token_hash = v_hash and s.revoked_at is null;
  if v_sess.id is null then
    return jsonb_build_object('ok', true);
  end if;
  if coalesce(portal_logout.everywhere, false) and v_sess.expires_at > now() then
    update public.portal_sessions set revoked_at = now()
     where account_id = v_sess.account_id and revoked_at is null;
  else
    update public.portal_sessions set revoked_at = now() where id = v_sess.id;
  end if;
  return jsonb_build_object('ok', true);
end;
$$;

-- ===========================================================================
-- Grants
-- ===========================================================================
revoke all on function private.portal_config() from public, anon, authenticated;
revoke all on function private.new_token() from public, anon, authenticated;
revoke all on function private.token_hash(text) from public, anon, authenticated;
revoke all on function private.pin_ok(text, text, date[]) from public, anon, authenticated;
revoke all on function private.portal_gate_ok() from public, anon, authenticated;
revoke all on function private.portal_site_failures(uuid) from public, anon, authenticated;
revoke all on function private.portal_login_failed(uuid) from public, anon, authenticated;
revoke all on function private.portal_available(public.sites) from public, anon, authenticated;
revoke all on function private.portal_site(text, text) from public, anon, authenticated;
revoke all on function private.portal_lock_account(uuid) from public, anon, authenticated;
revoke all on function private.portal_session_for_site(text, public.sites) from public, anon, authenticated;
revoke all on function private.portal_session(text, text, text) from public, anon, authenticated;
revoke all on function private.portal_new_session(uuid, uuid) from public, anon, authenticated;
revoke all on function private.portal_audit(text, uuid, uuid, jsonb) from public, anon, authenticated;
revoke all on function private.portal_detach(uuid, uuid) from public, anon, authenticated;
revoke all on function private.portal_customers_changed() from public, anon, authenticated;
revoke all on function private.portal_customers_deleting() from public, anon, authenticated;
revoke all on function private.public_site_payload(public.sites) from public, anon, authenticated;

revoke all on function public.portal_invite(uuid) from public, anon, authenticated;
revoke all on function public.portal_revoke(uuid) from public, anon, authenticated;
revoke all on function public.portal_status(uuid) from public, anon, authenticated;
revoke all on function public.portal_activate(text, text, text, text, text, jsonb) from public, anon, authenticated;
revoke all on function public.portal_login(text, text, text, text) from public, anon, authenticated;
revoke all on function public.portal_home(text, text, text) from public, anon, authenticated;
revoke all on function public.portal_ask(text, text, text, uuid, text, text) from public, anon, authenticated;
revoke all on function public.portal_request(text, text, text, uuid, text, text) from public, anon, authenticated;
revoke all on function public.portal_logout(text, boolean) from public, anon, authenticated;

grant execute on function public.portal_invite(uuid) to authenticated;
grant execute on function public.portal_revoke(uuid) to authenticated;
grant execute on function public.portal_status(uuid) to authenticated;

grant execute on function public.portal_activate(text, text, text, text, text, jsonb) to anon;
grant execute on function public.portal_login(text, text, text, text) to anon;
grant execute on function public.portal_home(text, text, text) to anon;
grant execute on function public.portal_ask(text, text, text, uuid, text, text) to anon;
grant execute on function public.portal_request(text, text, text, uuid, text, text) to anon;
grant execute on function public.portal_logout(text, boolean) to anon;
