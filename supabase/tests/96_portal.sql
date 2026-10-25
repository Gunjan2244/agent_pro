-- LOCAL TESTS ONLY. Customer portal backend (supabase/migrations/20261022184000_customer_portal.sql):
-- portal_* tables, agent RPCs (invite / revoke / status), anon RPCs (activate / login / home / ask / request /
-- logout), lockouts, sessions, availability, the customers triggers and the public payload's `portal` key.
-- Black-box tests against the contract; fixtures are created as postgres, the RPCs are called as the real roles.
-- Needs the regenerated catalog_sync (feature customerPortal, app_config 'portal' with the DECISIONS #101 keys,
-- notice 'customer_portal', enquiry source 'portal'). Every block rolls back.
-- The Worker gate (DECISIONS #101): this file stores the hash of a test key in private.portal_gate and sends the
-- key as the session's request.headers (what PostgREST sets from the Worker's x-portal-key header); the gate
-- checks in section 0 override it per transaction. Both are removed at the end of the file.
\set A '''aaaaaaaa-0000-4000-8000-00000000000a'''
\set B '''bbbbbbbb-0000-4000-8000-00000000000b'''
\set C '''cccccccc-0000-4000-8000-00000000000c'''
-- agent A's customers: C1/C2/C3 share one phone (family), C4 own phone, C5 no phone, C6 invalid phone
\set C1 '''20000000-0000-4000-8000-000000000001'''
\set C2 '''20000000-0000-4000-8000-000000000002'''
\set C3 '''20000000-0000-4000-8000-000000000003'''
\set C4 '''20000000-0000-4000-8000-000000000004'''
\set C5 '''20000000-0000-4000-8000-000000000005'''
\set C6 '''20000000-0000-4000-8000-000000000006'''
\set CC1 '''20000000-0000-4000-8000-0000000000c1'''
-- policies: PA/PB/PC/PD of C1 (PD deleted), PE of C2, PF of C3 (never invited), PG of C4
\set PA '''30000000-0000-4000-8000-00000000000a'''
\set PB '''30000000-0000-4000-8000-00000000000b'''
\set PC '''30000000-0000-4000-8000-00000000000c'''
\set PD '''30000000-0000-4000-8000-00000000000d'''
\set PE '''30000000-0000-4000-8000-00000000000e'''
\set PF '''30000000-0000-4000-8000-00000000000f'''
\set PG '''30000000-0000-4000-8000-000000000010'''

-- ---------------------------------------------------------------------------
-- Helpers (schema tests, defined in 10_helpers_seed.sql). Plain-text tokens live in a TEMP table `tk` per block.
-- ---------------------------------------------------------------------------
create or replace function tests.pt_t(p_k text) returns text language plpgsql stable as $$
declare r text;
begin
  execute 'select v from pg_temp.tk where k = $1' into r using p_k;
  return r;
end;
$$;

create or replace function tests.pt_resp(p_k text) returns jsonb language plpgsql stable as $$
begin
  return tests.pt_t(p_k || ':r')::jsonb;
end;
$$;

create or replace function tests.pt_cfg(p_key text, p_default int) returns int language sql stable security definer as $$
  select coalesce((select (value ->> p_key)::int from public.app_config where key = 'portal'), p_default)
$$;

-- Fixtures for agent A (as postgres). Notes carry SECRET-NOTE markers that must never reach the portal.
create or replace function tests.pt_fixture() returns void language plpgsql security definer as $$
declare
  i int;
begin
  insert into public.customers (id, agent_id, name, phone, email, dob, address, city, nominee, notes) values
    ('20000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-00000000000a', 'Asha Verma',   '+919811100001', 'asha@example.com', '1985-04-12', '12 Park Road', 'Pune', 'Ravi Verma', 'SECRET-NOTE-CUSTOMER-1'),
    ('20000000-0000-4000-8000-000000000002', 'aaaaaaaa-0000-4000-8000-00000000000a', 'Bimal Verma',  '+919811100001', null, null, null, 'Pune', null, 'SECRET-NOTE-CUSTOMER-2'),
    ('20000000-0000-4000-8000-000000000003', 'aaaaaaaa-0000-4000-8000-00000000000a', 'Chitra Verma', '+919811100001', null, null, null, 'Pune', null, 'SECRET-NOTE-CUSTOMER-3'),
    ('20000000-0000-4000-8000-000000000004', 'aaaaaaaa-0000-4000-8000-00000000000a', 'Dev Kumar',    '+919811100004', null, null, null, 'Pune', null, null),
    ('20000000-0000-4000-8000-000000000005', 'aaaaaaaa-0000-4000-8000-00000000000a', 'No Phone',     null, null, null, null, null, null, null),
    ('20000000-0000-4000-8000-000000000006', 'aaaaaaaa-0000-4000-8000-00000000000a', 'Bad Phone',    '123', null, null, null, null, null, null),
    ('20000000-0000-4000-8000-0000000000c1', 'cccccccc-0000-4000-8000-00000000000c', 'C Portal Cust', '+919822200001', null, null, null, null, null, null);
  insert into public.policies (id, agent_id, customer_id, policy_number, plan_name, sum_assured, premium_amount, premium_mode, start_date, next_due_date, status, notes, deleted_at) values
    ('30000000-0000-4000-8000-00000000000a', 'aaaaaaaa-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000001', 'POL-A', 'Plan A', 500000, 12000.50, 'yearly', '2024-01-15', '2026-12-01', 'active', 'SECRET-NOTE-POLICY-A', null),
    ('30000000-0000-4000-8000-00000000000b', 'aaaaaaaa-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000001', 'POL-B', 'Plan B', 100000, 3000, 'monthly', '2024-02-01', null, 'active', null, null),
    ('30000000-0000-4000-8000-00000000000c', 'aaaaaaaa-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000001', 'POL-C', 'Plan C', 200000, 6000, 'quarterly', '2024-03-01', '2026-11-01', 'active', null, null),
    ('30000000-0000-4000-8000-00000000000d', 'aaaaaaaa-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000001', 'POL-DELETED', 'DELETED-PLAN', 1, 1, 'yearly', '2024-04-01', '2026-10-30', 'active', null, now()),
    ('30000000-0000-4000-8000-00000000000e', 'aaaaaaaa-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000002', 'POL-E', 'Plan E', 300000, 9000, 'half_yearly', '2024-05-01', '2026-11-15', 'active', null, null),
    ('30000000-0000-4000-8000-00000000000f', 'aaaaaaaa-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000003', 'POL-F', 'Plan F UNINVITED', 400000, 8000, 'yearly', '2024-06-01', '2026-11-20', 'active', null, null),
    ('30000000-0000-4000-8000-000000000010', 'aaaaaaaa-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000004', 'POL-G', 'Plan G', 250000, 5000, 'yearly', '2024-07-01', '2026-12-10', 'active', null, null);
  for i in 1..15 loop
    insert into public.premium_payments (agent_id, policy_id, amount, paid_on, period_due_date)
    values ('aaaaaaaa-0000-4000-8000-00000000000a', '30000000-0000-4000-8000-00000000000a', 1000 + i, date '2025-01-01' + i, date '2025-01-01' + i);
  end loop;
  insert into public.premium_payments (agent_id, policy_id, amount, paid_on, period_due_date, deleted_at)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', '30000000-0000-4000-8000-00000000000a', 777.77, date '2026-06-30', date '2026-06-30', now());
end;
$$;

-- Agent RPC wrapper: runs in the caller's role; stores the token and the whole response.
create or replace function tests.pt_inv(p_k text, p_customer uuid) returns jsonb language plpgsql as $$
declare r jsonb;
begin
  r := public.portal_invite(p_customer);
  execute 'insert into pg_temp.tk values ($1, $2) on conflict (k) do update set v = excluded.v' using p_k, r ->> 'token';
  execute 'insert into pg_temp.tk values ($1, $2) on conflict (k) do update set v = excluded.v' using p_k || ':r', r::text;
  return r;
end;
$$;

-- Anon RPC wrappers (slug of agent A by default)
create or replace function tests.pt_act(p_token text, p_phone text, p_pin text,
  p_consent jsonb default null, p_slug text default 'ramesh-sharma')
returns jsonb language plpgsql as $$
begin
  return public.portal_activate(p_slug, null, p_token, p_phone, p_pin, coalesce(p_consent, tests.consent('customer_portal')));
end;
$$;

create or replace function tests.pt_login(p_phone text, p_pin text, p_slug text default 'ramesh-sharma')
returns jsonb language plpgsql as $$
begin
  return public.portal_login(p_slug, null, p_phone, p_pin);
end;
$$;

create or replace function tests.pt_home(p_k text, p_slug text default 'ramesh-sharma')
returns jsonb language plpgsql as $$
begin
  return public.portal_home(tests.pt_t(p_k), p_slug, null);
end;
$$;

-- SETUP shortcut (definer, as agent p_agent): invite each customer and activate its own link (a customer joins
-- only through its own link), store the last session token under p_k. Returns the last activation response.
create or replace function tests.pt_active(p_k text, p_agent uuid, p_custs uuid[], p_phone text,
  p_pin text default '482913', p_slug text default 'ramesh-sharma')
returns jsonb language plpgsql security definer as $$
declare
  c uuid; inv jsonb; act jsonb;
begin
  foreach c in array p_custs loop
    perform set_config('request.jwt.claims', json_build_object('sub', p_agent, 'role', 'authenticated')::text, true);
    inv := public.portal_invite(c);
    act := public.portal_activate(p_slug, null, inv ->> 'token', p_phone, p_pin, tests.consent('customer_portal'));
    if act ->> 'ok' is distinct from 'true' then
      return act;
    end if;
  end loop;
  if act ->> 'ok' = 'true' then
    execute 'insert into pg_temp.tk values ($1, $2) on conflict (k) do update set v = excluded.v' using p_k, act ->> 'session_token';
  end if;
  return act;
end;
$$;

-- SETUP: clear short locks between login attempts (does not touch hard locks or the window count)
create or replace function tests.pt_unlock() returns void language sql security definer as $$
  update public.portal_accounts set locked_until = null;
$$;

-- Calls of pgcrypto's crypt/gen_salt in this transaction (track_functions = all is set below): proves whether a
-- path ran bcrypt.
create or replace function tests.pt_bcrypt_calls() returns bigint language sql stable as $$
  select coalesce(sum(calls), 0)::bigint from pg_stat_xact_user_functions
   where schemaname = 'extensions' and funcname in ('crypt', 'gen_salt');
$$;

-- The Worker's header for this transaction (null = no header at all).
create or replace function tests.pt_header(p_key text) returns void language sql as $$
  select set_config('request.headers', case when p_key is null then '{}' else json_build_object('x-portal-key', p_key)::text end, true);
$$;

-- Everything an unavailable site must refuse, run as anon. Needs tk 's' (live session of C4) and 't' (live invite).
create or replace function tests.pt_unavail(p_label text) returns void language plpgsql as $$
declare r jsonb;
begin
  execute 'reset role';
  perform set_config('request.jwt.claims', '{"role":"anon"}', true);
  execute 'set local role anon';
  r := tests.pt_login('9811100004', '482913');
  perform tests.ok(r = '{"ok": false, "error": "unavailable"}'::jsonb, '#PT ' || p_label || ': portal_login -> unavailable', r::text);
  r := tests.pt_act(tests.pt_t('t'), '9811100001', '739204');
  perform tests.ok(r = '{"ok": false, "error": "unavailable"}'::jsonb, '#PT ' || p_label || ': portal_activate -> unavailable', r::text);
  r := tests.pt_home('s');
  perform tests.ok(coalesce((r ->> 'ok')::boolean, false) = false and r ->> 'error' in ('unavailable', 'session') and not (r ? 'customers'),
    '#PT ' || p_label || ': portal_home refused, no data', r::text);
  r := public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, '30000000-0000-4000-8000-000000000010', 'question', 'hello');
  perform tests.ok(coalesce((r ->> 'ok')::boolean, false) = false and r ->> 'error' in ('unavailable', 'session'),
    '#PT ' || p_label || ': portal_ask refused', r::text);
  r := public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, '20000000-0000-4000-8000-000000000004', 'access', 'hello');
  perform tests.ok(coalesce((r ->> 'ok')::boolean, false) = false and r ->> 'error' in ('unavailable', 'session'),
    '#PT ' || p_label || ': portal_request refused', r::text);
  execute 'reset role';
  perform tests.ok(not exists (select 1 from public.enquiries where source = 'portal')
    and not exists (select 1 from public.data_requests where customer_id = '20000000-0000-4000-8000-000000000004'),
    '#PT ' || p_label || ': nothing written');
end;
$$;

grant execute on all functions in schema tests to anon, authenticated;

-- The gate's test key (see the header comment) and bcrypt call counting.
insert into private.portal_gate values (1, sha256(convert_to('test-portal-key', 'UTF8')))
on conflict (id) do update set key_hash = excluded.key_hash;
select set_config('request.headers', '{"x-portal-key":"test-portal-key"}', false);
set track_functions = 'all';

-- ===========================================================================
-- 1. Structure: RLS, no table privileges, function attributes and grants
-- ===========================================================================
select tests.ok((select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
                  where n.nspname = 'public' and c.relname in ('portal_accounts', 'portal_account_customers', 'portal_invites', 'portal_sessions')
                    and c.relrowsecurity) = 4,
  '#PT four portal_* tables exist with RLS enabled');
select tests.ok(not exists (
  select 1 from (values ('portal_accounts'), ('portal_account_customers'), ('portal_invites'), ('portal_sessions')) t(n)
  cross join (values ('anon'), ('authenticated')) r(role)
  cross join (values ('select'), ('insert'), ('update'), ('delete'), ('truncate'), ('references'), ('trigger')) p(priv)
  where has_table_privilege(r.role, 'public.' || t.n, p.priv)),
  '#PT anon and authenticated hold no privilege on any portal_* table');
select tests.ok(not exists (
  select 1 from pg_policy p join pg_class c on c.oid = p.polrelid
   where c.relname in ('portal_accounts', 'portal_account_customers', 'portal_invites', 'portal_sessions')),
  '#PT portal_* tables have no RLS policies (access only through RPCs)');

select tests.ok((select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'public' and p.oid = any (array[
     'public.portal_invite(uuid)', 'public.portal_revoke(uuid)', 'public.portal_status(uuid)',
     'public.portal_activate(text,text,text,text,text,jsonb)', 'public.portal_login(text,text,text,text)',
     'public.portal_home(text,text,text)', 'public.portal_ask(text,text,text,uuid,text,text)',
     'public.portal_request(text,text,text,uuid,text,text)', 'public.portal_logout(text,boolean)']::regprocedure[])
     and p.prosecdef and exists (select 1 from unnest(p.proconfig) cfg where replace(cfg, ' ', '') in ('search_path=""', 'search_path=')))
  = 9, '#PT all 9 portal RPCs are SECURITY DEFINER with search_path = ''''');
select tests.ok(not exists (
  select 1 from pg_proc p, unnest(array[
     'public.portal_invite(uuid)', 'public.portal_revoke(uuid)', 'public.portal_status(uuid)',
     'public.portal_activate(text,text,text,text,text,jsonb)', 'public.portal_login(text,text,text,text)',
     'public.portal_home(text,text,text)', 'public.portal_ask(text,text,text,uuid,text,text)',
     'public.portal_request(text,text,text,uuid,text,text)', 'public.portal_logout(text,boolean)']::regprocedure[]) f(oid)
   where p.oid = f.oid and exists (select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a where a.grantee = 0)),
  '#PT no portal RPC is executable by PUBLIC');
select tests.ok(
  has_function_privilege('authenticated', 'public.portal_invite(uuid)', 'execute')
  and has_function_privilege('authenticated', 'public.portal_revoke(uuid)', 'execute')
  and has_function_privilege('authenticated', 'public.portal_status(uuid)', 'execute')
  and not has_function_privilege('anon', 'public.portal_invite(uuid)', 'execute')
  and not has_function_privilege('anon', 'public.portal_revoke(uuid)', 'execute')
  and not has_function_privilege('anon', 'public.portal_status(uuid)', 'execute'),
  '#PT agent RPCs (invite, revoke, status): authenticated only');
select tests.ok(
  has_function_privilege('anon', 'public.portal_activate(text,text,text,text,text,jsonb)', 'execute')
  and has_function_privilege('anon', 'public.portal_login(text,text,text,text)', 'execute')
  and has_function_privilege('anon', 'public.portal_home(text,text,text)', 'execute')
  and has_function_privilege('anon', 'public.portal_ask(text,text,text,uuid,text,text)', 'execute')
  and has_function_privilege('anon', 'public.portal_request(text,text,text,uuid,text,text)', 'execute')
  and has_function_privilege('anon', 'public.portal_logout(text,boolean)', 'execute')
  and not has_function_privilege('authenticated', 'public.portal_activate(text,text,text,text,text,jsonb)', 'execute')
  and not has_function_privilege('authenticated', 'public.portal_login(text,text,text,text)', 'execute')
  and not has_function_privilege('authenticated', 'public.portal_home(text,text,text)', 'execute'),
  '#PT anon RPCs (activate, login, home, ask, request, logout): anon only');
select tests.ok((select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
                  where n.nspname = 'private' and c.relname in ('portal_gate', 'portal_login_failures') and c.relrowsecurity) = 2
  and not exists (
    select 1 from (values ('private.portal_gate'), ('private.portal_login_failures')) t(n)
    cross join (values ('anon'), ('authenticated'), ('service_role')) r(role)
    cross join (values ('select'), ('insert'), ('update'), ('delete')) p(priv)
    where has_table_privilege(r.role, t.n, p.priv)),
  '#PT private.portal_gate and private.portal_login_failures: RLS on, no privilege for anon, authenticated or service_role');
select tests.ok(not exists (
  select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'private' and p.proname in ('portal_gate_ok', 'portal_site_failures', 'portal_login_failed', 'pin_ok')
     and (has_function_privilege('anon', p.oid, 'execute') or has_function_privilege('authenticated', p.oid, 'execute'))),
  '#PT the gate, the failure counter and pin_ok are not executable by the API roles');
select tests.throws($$insert into private.portal_gate values (2, sha256('x'::bytea))$$, '#PT portal_gate holds one row only (id = 1)', '%portal_gate_single_row%');
select tests.throws($$insert into private.portal_gate values (1, '\x00'::bytea) on conflict (id) do update set key_hash = excluded.key_hash$$,
  '#PT portal_gate key_hash must be 32 bytes', '%portal_gate_key_hash_length%');
begin;
select tests.as_anon();
select tests.throws($$select * from public.portal_accounts$$, '#PT anon cannot read portal_accounts', '%permission denied%');
select tests.throws($$select * from public.portal_sessions$$, '#PT anon cannot read portal_sessions', '%permission denied%');
select tests.throws($$select public.portal_invite(gen_random_uuid())$$, '#PT anon cannot call portal_invite', '%permission denied%');
reset role;
select tests.as_agent(:A);
select tests.throws($$select * from public.portal_invites$$, '#PT authenticated cannot read portal_invites', '%permission denied%');
select tests.throws($$select * from public.portal_account_customers$$, '#PT authenticated cannot read portal_account_customers', '%permission denied%');
select tests.throws($$select public.portal_login('ramesh-sharma', null, '9811100004', '482913')$$, '#PT authenticated cannot call portal_login', '%permission denied%');
rollback;

-- catalog dependencies (fail only if the regenerated catalog_sync is not in place)
select tests.ok(exists (select 1 from public.app_config where key = 'portal'), '#PT [catalog] app_config has a portal row');
select tests.ok(public.catalog_privacy_notice('customer_portal') is not null, '#PT [catalog] notice customer_portal exists');
select tests.ok(private.flag_enabled('customerPortal'), '#PT [catalog] flag customerPortal is on by default');
select tests.ok((select value ?& array['failureWindowDays', 'hardLockHours', 'siteFailedLoginsPerMinute', 'blockedPins']
                   and jsonb_array_length(value -> 'blockedPins') > 0
                   and not exists (select 1 from jsonb_array_elements_text(value -> 'blockedPins') b where b !~ '^[0-9]{6}$')
                   from public.app_config where key = 'portal')
  and private.portal_config() ? 'blockedPins',
  '#PT [catalog] app_config.portal has the #101 keys (blockedPins: a non-empty list of 6-digit strings) and portal_config accepts them');
begin;
insert into public.enquiries (agent_id, name, phone, source, status) values (:A, 'Src', '+919000000099', 'portal', 'new');
select tests.ok(true, '#PT [catalog] enquiries accept source portal');
rollback;

-- ===========================================================================
-- 2. portal_invite / portal_status
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.as_agent(:A);
select tests.pt_inv('i1', :C1);
select tests.ok(tests.pt_resp('i1') ->> 'ok' = 'true' and tests.pt_t('i1') ~ '^[A-Za-z0-9_-]{43}$'
  and (tests.pt_resp('i1') ->> 'already_active')::boolean = false
  and (tests.pt_resp('i1') ->> 'expires_at')::timestamptz between now() + interval '6 days 23 hours' and now() + interval '7 days 1 hour',
  '#PT invite: ok, 43-char base64url token, not already active, expires in 7 days', tests.pt_resp('i1')::text);
reset role;
select tests.ok((select count(*) from public.portal_invites where token_hash = sha256(convert_to(tests.pt_t('i1'), 'UTF8'))) = 1,
  '#PT invite: exactly one invite row carries the token hash');
select tests.ok(not exists (
  select 1 from (select to_jsonb(t)::text s from public.portal_accounts t
                 union all select to_jsonb(t)::text from public.portal_account_customers t
                 union all select to_jsonb(t)::text from public.portal_invites t
                 union all select to_jsonb(t)::text from public.portal_sessions t
                 union all select to_jsonb(t)::text from public.audit_log t) x
   where position(tests.pt_t('i1') in x.s) > 0),
  '#PT invite: the plaintext token appears in no portal_* row and no audit row');
select tests.ok((tests.pt_resp('i1') ->> 'expires_at')::timestamptz
  = (select i.expires_at from public.portal_invites i where i.token_hash = sha256(convert_to(tests.pt_t('i1'), 'UTF8'))),
  '#PT invite: stored hash found by sha256 of the token; expires_at matches');
select tests.ok((select a.phone = '+919811100001' and a.agent_id = :A and a.activated_at is null and a.pin_hash is null
                   from public.portal_accounts a), '#PT invite: account created for the normalised phone, not activated, no PIN');
select tests.ok(not exists (select 1 from public.portal_account_customers),
  '#PT invite attaches no one (the customer joins at activation, DECISIONS #101)');
select tests.as_agent(:A);
select tests.ok(public.portal_status(:C1) ->> 'state' = 'invited' and (public.portal_status(:C1) ->> 'invite_expires_at') is not null
  and jsonb_typeof(public.portal_status(:C1) -> 'shared_with') = 'array', '#PT status: invited with expiry and shared_with array',
  public.portal_status(:C1)::text);
select tests.ok(public.portal_status(:C4) ->> 'state' = 'none' and public.portal_status(:C4) -> 'portal_available' = 'true'::jsonb,
  '#PT status: none for a customer never invited; portal_available true', public.portal_status(:C4)::text);
-- error cases
select tests.ok(public.portal_invite(:C5) = '{"ok": false, "error": "invalid", "field": "phone"}'::jsonb, '#PT invite: no phone -> invalid/phone');
select tests.ok(public.portal_invite(:C6) = '{"ok": false, "error": "invalid", "field": "phone"}'::jsonb, '#PT invite: malformed phone -> invalid/phone');
select tests.throws(format('select public.portal_invite(%L)', :CC1), '#PT invite: another agent''s customer -> not_found', '%not_found%');
select tests.throws($$select public.portal_invite(gen_random_uuid())$$, '#PT invite: unknown customer -> not_found', '%not_found%');
select tests.throws(format('select public.portal_status(%L)', :CC1), '#PT status: another agent''s customer -> not_found', '%not_found%');
select tests.throws(format('select public.portal_revoke(%L)', :CC1), '#PT revoke: another agent''s customer -> not_found', '%not_found%');
reset role;
update public.customers set deleted_at = now() where id = :C4;
select tests.as_agent(:A);
select tests.throws(format('select public.portal_invite(%L)', :C4), '#PT invite: soft-deleted customer -> not_found', '%not_found%');
reset role;
-- agent B has an unpublished site
select tests.as_agent(:B);
select tests.ok(public.portal_invite((select id from public.customers where agent_id = :B limit 1)) = '{"ok": false, "error": "no_site"}'::jsonb,
  '#PT invite: no published site -> no_site');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
-- site unpublished after having been published
update public.sites set is_published = false where agent_id = :A;
select tests.as_agent(:A);
select tests.ok(public.portal_invite(:C1) = '{"ok": false, "error": "no_site"}'::jsonb, '#PT invite: site unpublished -> no_site');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
update public.app_config set value = jsonb_set(value, '{customerPortal}', 'false') where key = 'flag_overrides';
select tests.as_agent(:A);
select tests.ok(public.portal_invite(:C1) = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#PT invite: flag customerPortal off via flag_overrides -> unavailable', public.portal_invite(:C1)::text);
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
update public.subscriptions set trial_ends_at = now() - interval '16 days' where agent_id = :A;
select tests.as_agent(:A);
select tests.throws(format('select public.portal_invite(%L)', :C1), '#PT invite: lapsed plan -> plan_read_only', '%plan_read_only%');
rollback;

-- ===========================================================================
-- 3. portal_activate
-- ===========================================================================
-- happy path (family: C1 and C2 on one phone, both invited -> one account, two consents)
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.as_agent(:A);
select tests.pt_inv('i1', :C1);
select tests.pt_inv('i2', :C2);
select tests.ok(tests.pt_resp('i2') ->> 'ok' = 'true', '#PT second invite for the same phone ok');
reset role;
select tests.ok((select count(*) from public.portal_accounts where agent_id = :A and phone = '+919811100001' and deleted_at is null) = 1,
  '#PT two customers on one phone share one account');
select tests.ok(public.portal_status(:C1) ->> 'state' = 'invited' and public.portal_status(:C2) ->> 'state' = 'invited',
  '#PT status: each family member has its own live link (a second customer''s link does not void the first)');
reset role;
select tests.as_anon();
select tests.pt_act(tests.pt_t('i2'), '98111 00001', '482913') as act \gset
select tests.ok(:'act'::jsonb ->> 'ok' = 'true' and :'act'::jsonb ->> 'session_token' ~ '^[A-Za-z0-9_-]{43}$'
  and (:'act'::jsonb ->> 'expires_at')::timestamptz between now() + interval '89 days' and now() + interval '91 days',
  '#PT activate: ok, session token (43 chars), expires in 90 days (phone typed with a space)', :'act');
reset role;
select tests.ok((select array_agg(customer_id) from public.portal_account_customers) = array[:C2]::uuid[]
  and (select count(*) from public.consents where notice_key = 'customer_portal') = 1
  and (select customer_id from public.consents where notice_key = 'customer_portal') = :C2,
  '#PT activate attaches only the link''s customer and records one consent, for that customer');
select tests.as_agent(:A);
select tests.ok(public.portal_status(:C1) ->> 'state' = 'invited' and public.portal_status(:C1) -> 'shared_with' = '["Bimal Verma"]'::jsonb,
  '#PT status: the other member is still invited; shared_with names who its link joins', public.portal_status(:C1)::text);
reset role;
select tests.as_anon();
select tests.ok(tests.pt_act(tests.pt_t('i1'), '9811100001', '482913') ->> 'ok' = 'true', '#PT activate: the first member''s own link then works too');
reset role;
select tests.ok((select a.pin_hash is not null and a.pin_hash <> '482913' and a.activated_at is not null and a.failed_attempts = 0
                   from public.portal_accounts a where a.agent_id = :A), '#PT activate: PIN stored hashed, account activated');
select tests.ok(not exists (select 1 from public.portal_accounts a where position('482913' in to_jsonb(a)::text) > 0),
  '#PT activate: the PIN appears nowhere in plaintext');
select tests.ok((select count(*) from public.consents k where k.notice_key = 'customer_portal') = 2
  and (select count(distinct customer_id) from public.consents k where k.notice_key = 'customer_portal' and customer_id in (:C1, :C2)) = 2,
  '#PT activate: one consents row per customer, each written by its own activation (2 for the family)');
select tests.ok(not exists (select 1 from public.consents k where k.notice_key = 'customer_portal'
  and not (k.principal_kind = 'customer' and k.channel = 'web' and k.agent_id = :A and k.withdrawn_at is null
           and k.notice_version = (public.catalog_privacy_notice('customer_portal') ->> 'version')::int
           and k.notice_hash = public.catalog_privacy_notice('customer_portal') ->> 'hash_en')),
  '#PT activate: consent rows are principal customer, channel web, current version, server hash');
select tests.as_agent(:A);
select tests.ok(public.portal_status(:C1) ->> 'state' = 'active' and public.portal_status(:C2) ->> 'state' = 'active',
  '#PT status: active after activation (both family members)', public.portal_status(:C1)::text);
select tests.ok(public.portal_status(:C3) ->> 'state' = 'none', '#PT status: a third customer on the same phone, not invited -> none');
rollback;

-- invite problems: phone mismatch burns, expiry, reuse, voiding, unknown token, wrong site
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.as_agent(:A);
select tests.pt_inv('m', :C4);
select tests.as_anon();
select tests.ok(tests.pt_act(tests.pt_t('m'), '9811100009', '482913') = '{"ok": false, "error": "invalid", "field": "phone"}'::jsonb,
  '#PT activate: wrong phone -> invalid/phone');
reset role;
select tests.ok((select phone_tries from public.portal_invites where token_hash = sha256(convert_to(tests.pt_t('m'), 'UTF8'))) = 1,
  '#PT activate: a phone mismatch increments phone_tries');
select tests.as_anon();
do $$
begin
  for i in 2..5 loop
    perform tests.pt_act(tests.pt_t('m'), '9811100009', '482913');
  end loop;
end $$;
select tests.ok(tests.pt_act(tests.pt_t('m'), '9811100004', '482913') = '{"ok": false, "error": "invalid_invite"}'::jsonb,
  '#PT activate: after 5 phone mismatches the invite is burned (correct phone -> invalid_invite)');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.as_agent(:A);
select tests.pt_inv('e', :C4);
reset role;
update public.portal_invites set expires_at = now() - interval '1 minute';
select tests.as_anon();
select tests.ok(tests.pt_act(tests.pt_t('e'), '9811100004', '482913') = '{"ok": false, "error": "invalid_invite"}'::jsonb,
  '#PT activate: expired invite -> invalid_invite');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.as_agent(:A);
select tests.pt_inv('u', :C4);
select tests.as_anon();
select tests.ok(tests.pt_act(tests.pt_t('u'), '9811100004', '482913') ->> 'ok' = 'true', '#PT activate: first use ok');
select tests.ok(tests.pt_act(tests.pt_t('u'), '9811100004', '482913') = '{"ok": false, "error": "invalid_invite"}'::jsonb,
  '#PT activate: reused invite -> invalid_invite');
select tests.ok(tests.pt_act('not-a-real-token', '9811100004', '482913') = '{"ok": false, "error": "invalid_invite"}'::jsonb,
  '#PT activate: unknown token -> invalid_invite');
select tests.ok(public.portal_activate('ramesh-sharma', null, null, null, null, null) ->> 'ok' = 'false',
  '#PT activate: all-null arguments never raise');
select tests.ok(public.portal_activate('no-such-site', null, tests.pt_t('u'), '9811100004', '482913', tests.consent('customer_portal')) ->> 'error' = 'unavailable',
  '#PT activate: unknown slug -> unavailable');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.as_agent(:A);
select tests.pt_inv('old', :C4);
select tests.pt_inv('new', :C4);
select tests.as_anon();
select tests.ok(tests.pt_act(tests.pt_t('old'), '9811100004', '482913') = '{"ok": false, "error": "invalid_invite"}'::jsonb,
  '#PT activate: a newer invite voids the previous one');
select tests.ok(tests.pt_act(tests.pt_t('new'), '9811100004', '482913') ->> 'ok' = 'true', '#PT activate: the newest invite works');
rollback;

-- an invite is bound to its agent's site
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.as_agent(:A);
select tests.pt_inv('x', :C4);
select tests.as_anon();
select tests.ok(tests.pt_act(tests.pt_t('x'), '9811100004', '482913', null, 'chetan-lal') ->> 'ok' = 'false'
  and tests.pt_act(tests.pt_t('x'), '9811100004', '482913', null, 'chetan-lal') ->> 'error' in ('invalid_invite', 'unavailable'),
  '#PT activate: agent A''s invite used on agent C''s site is refused');
select tests.ok(tests.pt_act(tests.pt_t('x'), '9811100004', '482913') ->> 'ok' = 'true', '#PT activate: ... and still works on its own site');
rollback;

-- PIN rules and consent rules (the invite survives every refusal)
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.as_agent(:A);
select tests.pt_inv('p', :C1);
select tests.as_anon();
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '48291') = '{"ok": false, "error": "invalid", "field": "pin"}'::jsonb, '#PT PIN: 5 digits refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '4829133') = '{"ok": false, "error": "invalid", "field": "pin"}'::jsonb, '#PT PIN: 7 digits refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', 'abcdef') = '{"ok": false, "error": "invalid", "field": "pin"}'::jsonb, '#PT PIN: non-digits refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', null) ->> 'field' = 'pin', '#PT PIN: null refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '111111') = '{"ok": false, "error": "invalid", "field": "pin"}'::jsonb, '#PT PIN: 111111 refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '123456') = '{"ok": false, "error": "invalid", "field": "pin"}'::jsonb, '#PT PIN: 123456 refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '654321') = '{"ok": false, "error": "invalid", "field": "pin"}'::jsonb, '#PT PIN: 654321 refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '234567') = '{"ok": false, "error": "invalid", "field": "pin"}'::jsonb, '#PT PIN: ascending run 234567 refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '100001') = '{"ok": false, "error": "invalid", "field": "pin"}'::jsonb, '#PT PIN: last 6 digits of the phone refused');
-- DECISIONS #101 (review M2): patterns, the blocked list and the joining customer's date of birth (C1: 1985-04-12)
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '121212') ->> 'field' = 'pin', '#PT PIN: 121212 (ababab) refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '123123') ->> 'field' = 'pin', '#PT PIN: 123123 (abcabc) refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '112233') ->> 'field' = 'pin', '#PT PIN: 112233 (aabbcc) refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '147258') ->> 'field' = 'pin', '#PT PIN: 147258 (blockedPins) refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '159753') ->> 'field' = 'pin', '#PT PIN: 159753 (blockedPins) refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '696969') ->> 'field' = 'pin', '#PT PIN: 696969 refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '120485') ->> 'field' = 'pin', '#PT PIN: the customer''s DOB as DDMMYY (120485) refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '850412') ->> 'field' = 'pin', '#PT PIN: the customer''s DOB as YYMMDD (850412) refused');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '041285') ->> 'field' = 'pin', '#PT PIN: the customer''s DOB as MMDDYY (041285) refused');
select tests.ok(public.portal_activate('ramesh-sharma', null, tests.pt_t('p'), '9811100001', '482913', null) = '{"ok": false, "error": "invalid", "field": "consent"}'::jsonb,
  '#PT consent: missing -> invalid/consent');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '482913', tests.consent('enquiry_form')) ->> 'field' = 'consent',
  '#PT consent: another notice''s payload -> invalid/consent');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '482913', jsonb_set(tests.consent('customer_portal'), '{hash}', to_jsonb(repeat('0', 64)))) ->> 'field' = 'consent',
  '#PT consent: bad hash -> invalid/consent');
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '482913', '[1]'::jsonb) ->> 'field' = 'consent', '#PT consent: non-object -> invalid/consent');
reset role;
select tests.ok((select count(*) from public.consents where notice_key = 'customer_portal') = 0 and (select count(*) from public.portal_sessions) = 0,
  '#PT refused activations created no consent and no session');
select tests.as_anon();
select tests.ok(tests.pt_act(tests.pt_t('p'), '9811100001', '482913', tests.consent('customer_portal', 'hi')) ->> 'ok' = 'true',
  '#PT activate: the same invite still works after every refusal (hi consent hash accepted)');
reset role;
select tests.ok((select locale from public.consents where notice_key = 'customer_portal' and customer_id = :C1) = 'hi', '#PT consent: locale resolved from the hash (hi)');
rollback;

-- re-activation: new invite on an active account resets the PIN and revokes old sessions
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s1', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_agent(:A);
select tests.pt_inv('re', :C4);
select tests.ok((tests.pt_resp('re') ->> 'already_active')::boolean, '#PT invite: already_active true for an active account', tests.pt_resp('re')::text);
reset role;
select tests.as_anon();
select tests.ok(tests.pt_home('s1') ->> 'ok' = 'true', '#PT session s1 works before re-activation');
select tests.pt_act(tests.pt_t('re'), '9811100004', '739204') as act2 \gset
select tests.ok(:'act2'::jsonb ->> 'ok' = 'true', '#PT re-activation with a new PIN ok', :'act2');
select tests.ok(tests.pt_home('s1') ->> 'error' = 'session', '#PT activation revokes every older session');
select tests.ok(tests.pt_login('9811100004', '482913') ->> 'error' = 'invalid', '#PT re-activation: old PIN no longer works');
select tests.ok(tests.pt_login('9811100004', '739204') ->> 'ok' = 'true', '#PT re-activation: new PIN works');
rollback;

-- ===========================================================================
-- 4. portal_login and lockouts
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s0', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '482913') ->> 'ok' = 'true'
  and tests.pt_login('9811100004', '482913') ->> 'session_token' ~ '^[A-Za-z0-9_-]{43}$'
  and (tests.pt_login('9811100004', '482913') ->> 'expires_at')::timestamptz > now() + interval '89 days',
  '#PT login: ok returns a session token valid 90 days');
select tests.ok(tests.pt_login('+91 98111 00004', '482913') ->> 'ok' = 'true', '#PT login: phone typed with +91 and spaces ok');
select tests.ok(tests.pt_login('9811100004', '482913', 'chetan-lal') = '{"ok": false, "error": "invalid"}'::jsonb,
  '#PT login: the account of agent A does not log in on agent C''s site');
select tests.pt_login('9811100004', '482913') ->> 'session_token' as lt \gset
select tests.ok(public.portal_home(:'lt', 'ramesh-sharma', null) ->> 'ok' = 'true', '#PT login: the session token opens portal_home');
select tests.ok(tests.pt_login('9811100004', '000000') = '{"ok": false, "error": "invalid"}'::jsonb, '#PT login: wrong PIN -> invalid');
select tests.ok(tests.pt_login('9000099999', '482913') = '{"ok": false, "error": "invalid"}'::jsonb, '#PT login: unknown phone -> the same invalid shape');
select tests.ok(tests.pt_login('abc', null) = '{"ok": false, "error": "invalid"}'::jsonb, '#PT login: garbage input -> invalid, never raises');
do $$
declare r jsonb;
begin
  for i in 1..8 loop
    r := tests.pt_login('9000099999', '000000');
    if r <> '{"ok": false, "error": "invalid"}'::jsonb then raise exception 'unknown phone changed shape: %', r; end if;
  end loop;
end $$;
select tests.ok(true, '#PT login: unknown phone never locks (8 tries, constant shape)');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s0', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_anon();
do $$
begin
  for i in 1..5 loop
    perform tests.pt_login('9811100004', '000000');
  end loop;
end $$;
select tests.pt_login('9811100004', '482913') as locked \gset
select tests.ok(:'locked'::jsonb ->> 'error' = 'locked' and (:'locked'::jsonb ->> 'until')::timestamptz between now() + interval '14 minutes' and now() + interval '16 minutes',
  '#PT login: 5 failures -> locked with until about 15 minutes; the correct PIN is refused while locked', :'locked');
select tests.ok(tests.pt_login('9811100004', '000000') ->> 'error' = 'locked', '#PT login: a further wrong PIN while locked stays locked');
reset role;
select tests.as_agent(:A);
select tests.ok(public.portal_status(:C4) ->> 'state' = 'locked' and (public.portal_status(:C4) ->> 'locked_until') is not null,
  '#PT status: locked with locked_until', public.portal_status(:C4)::text);
reset role;
update public.portal_accounts set locked_until = now() - interval '1 minute';
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '482913') ->> 'ok' = 'true', '#PT login: after the lock expires the correct PIN works');
reset role;
select tests.ok((select failed_attempts from public.portal_accounts) = 5 and (select failure_streak from public.portal_accounts) = 0
  and (select locked_until from public.portal_accounts) is null,
  '#PT login: a success ends the streak and the lock but keeps the window count (DECISIONS #101)');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s0', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_anon();
do $$
begin
  for i in 1..20 loop
    perform tests.pt_unlock();
    perform tests.pt_login('9811100004', '000000');
  end loop;
end $$;
select tests.pt_unlock();
select tests.pt_login('9811100004', '482913') as hard \gset
select tests.ok(:'hard'::jsonb ->> 'error' = 'locked'
  and (:'hard'::jsonb ->> 'until')::timestamptz = now() + make_interval(hours => tests.pt_cfg('hardLockHours', 24)),
  '#PT login: 20 failures -> hard lock with until = now + hardLockHours, even once the short lock has passed', :'hard');
reset role;
select tests.ok((select hard_locked_at from public.portal_accounts) is not null, '#PT login: hard_locked_at set');
select tests.ok(exists (select 1 from public.audit_log where action = 'portal.hard_lock'), '#PT hard lock is written to the audit log');
select tests.as_agent(:A);
select tests.ok(public.portal_status(:C4) ->> 'state' = 'locked'
  and (public.portal_status(:C4) ->> 'locked_until')::timestamptz = now() + make_interval(hours => tests.pt_cfg('hardLockHours', 24)),
  '#PT status: hard-locked account shows locked with locked_until = the hard lock''s end', public.portal_status(:C4)::text);
reset role;
-- only a new invite and an activation clears it
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '482913') ->> 'error' = 'locked', '#PT hard lock persists across attempts');
reset role;
select tests.pt_active('s2', :A, array[:C4]::uuid[], '9811100004', '739204') as reset_act \gset
select tests.ok(:'reset_act'::jsonb ->> 'ok' = 'true', '#PT hard lock: a new invite + activation succeeds');
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '739204') ->> 'ok' = 'true', '#PT hard lock: login works again with the new PIN');
reset role;
select tests.ok((select hard_locked_at from public.portal_accounts) is null, '#PT hard lock cleared by activation');
rollback;

-- ===========================================================================
-- 5. Sessions
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s1', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_anon();
select tests.pt_login('9811100004', '482913') ->> 'session_token' as t2 \gset
select tests.ok(tests.pt_home('s1') ->> 'ok' = 'true' and jsonb_typeof(tests.pt_home('s1') -> 'customers') = 'array', '#PT home: ok with a customers array');
select tests.ok(not (tests.pt_home('s1', 'chetan-lal') ? 'customers') and tests.pt_home('s1', 'chetan-lal') ->> 'error' in ('session', 'unavailable'),
  '#PT session of agent A used with agent C''s slug -> error, no data', tests.pt_home('s1', 'chetan-lal')::text);
select tests.ok(tests.pt_home('s1', 'sunita-rao') ->> 'ok' = 'false', '#PT session used with an unpublished site''s slug -> error');
select tests.ok(public.portal_home(null, 'ramesh-sharma', null) ->> 'error' = 'session' and public.portal_home('garbage', 'ramesh-sharma', null) ->> 'error' = 'session',
  '#PT home: null and garbage tokens -> session');
select tests.ok(public.portal_logout(tests.pt_t('s1'), false) = '{"ok": true}'::jsonb, '#PT logout -> {ok:true}');
select tests.ok(tests.pt_home('s1') ->> 'error' = 'session', '#PT logout: that session is dead');
select tests.ok(public.portal_home(:'t2', 'ramesh-sharma', null) ->> 'ok' = 'true', '#PT logout (this device) leaves the other session alone');
select tests.ok(public.portal_logout('garbage', false) = '{"ok": true}'::jsonb, '#PT logout with an unknown token never raises');
select tests.pt_login('9811100004', '482913') ->> 'session_token' as t3 \gset
select tests.ok(public.portal_logout(:'t2', true) = '{"ok": true}'::jsonb, '#PT logout everywhere -> ok');
select tests.ok(public.portal_home(:'t2', 'ramesh-sharma', null) ->> 'error' = 'session' and public.portal_home(:'t3', 'ramesh-sharma', null) ->> 'error' = 'session',
  '#PT logout everywhere revokes every session of the account');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s1', :A, array[:C4]::uuid[], '9811100004', '482913');
update public.portal_sessions set expires_at = now() - interval '1 minute';
select tests.as_anon();
select tests.ok(tests.pt_home('s1') ->> 'error' = 'session', '#PT expired session -> session');
rollback;

-- ===========================================================================
-- 6. Availability: unpublished, lapsed owner, toggle off, flag off
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_agent(:A);
select tests.pt_inv('t', :C1);
reset role;
update public.sites set is_published = false where agent_id = :A;
select tests.pt_unavail('unpublished site');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_agent(:A);
select tests.pt_inv('t', :C1);
reset role;
update public.subscriptions set trial_ends_at = now() - interval '16 days' where agent_id = :A;
select tests.ok(public.entitlement(:A) = 'lapsed', '#PT [setup] owner is lapsed');
select tests.pt_unavail('lapsed owner');
select tests.as_agent(:A);
select tests.ok(public.portal_status(:C4) -> 'portal_available' = 'false'::jsonb, '#PT status: portal_available false for a lapsed owner');
reset role;
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_agent(:A);
select tests.pt_inv('t', :C1);
reset role;
update public.sites set published_content = jsonb_set(published_content, '{contact,showCustomerLogin}', 'false', true) where agent_id = :A;
select tests.pt_unavail('contact.showCustomerLogin = false');
select tests.as_agent(:A);
select tests.ok(public.portal_status(:C4) -> 'portal_available' = 'false'::jsonb and public.portal_status(:C4) ->> 'state' = 'active',
  '#PT status: portal_available false while the contact toggle is off (the login itself stays active)', public.portal_status(:C4)::text);
reset role;
select tests.as_anon();
select tests.ok((public.get_public_site('ramesh-sharma') -> 'portal') = 'false'::jsonb, '#PT public payload: portal is false when the toggle is off',
  (public.get_public_site('ramesh-sharma') -> 'portal')::text);
reset role;
update public.sites set published_content = jsonb_set(published_content, '{contact,showCustomerLogin}', 'true', true) where agent_id = :A;
select tests.as_anon();
select tests.ok(tests.pt_home('s') ->> 'ok' = 'true', '#PT toggle back on: the existing session works again');
select tests.ok((public.get_public_site('ramesh-sharma') -> 'portal') = 'true'::jsonb, '#PT public payload: portal is true when the toggle is on');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_agent(:A);
select tests.pt_inv('t', :C1);
reset role;
update public.app_config set value = jsonb_set(value, '{customerPortal}', 'false') where key = 'flag_overrides';
select tests.pt_unavail('flag customerPortal off');
select tests.as_anon();
select tests.ok((public.get_public_site('ramesh-sharma') -> 'portal') = 'false'::jsonb, '#PT public payload: portal is false when the flag is off');
rollback;

begin;
select tests.as_anon();
select tests.ok(jsonb_typeof(public.get_public_site('ramesh-sharma') -> 'portal') = 'boolean', '#PT public payload: portal is a boolean');
select tests.ok((public.get_public_site('ramesh-sharma') -> 'portal') = 'true'::jsonb, '#PT public payload: portal true by default (key absent in contact counts as on)',
  (public.get_public_site('ramesh-sharma') -> 'portal')::text);
rollback;

-- ===========================================================================
-- 7. portal_home payload
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
-- family: C1 and C2 invited; C3 (same phone) and C4 (other phone) are not part of this account
select tests.pt_active('s', :A, array[:C1, :C2]::uuid[], '9811100001', '482913');
select tests.as_anon();
select tests.pt_home('s')::text as home_txt \gset
select tests.pt_home('s') as home \gset
select tests.ok(:'home'::jsonb ->> 'ok' = 'true', '#PT home: ok', :'home');
select tests.ok((select array_agg(c ->> 'id' order by c ->> 'name') from jsonb_array_elements(:'home'::jsonb -> 'customers') c)
  = array[:C1, :C2]::text[], '#PT home: customers are exactly the attached ones, in name order (family of two shown)');
select tests.ok(:'home' not like '%Chitra%' and :'home' not like '%Plan F UNINVITED%' and :'home' not like '%Dev Kumar%' and :'home' not like '%Plan G%',
  '#PT home: a third customer on the same phone who was not invited, and other phones, are not shown');
select tests.ok(:'home' !~ '"notes"' and :'home' not like '%SECRET-NOTE%', '#PT home: no notes key and no note text anywhere in the payload');
select tests.ok(not jsonb_path_exists(:'home'::jsonb, 'strict $.**.notes'), '#PT home: jsonb path search finds no notes member at any depth');
select tests.ok(:'home' !~ 'pin_hash|token_hash|agent_id', '#PT home: no pin_hash, token_hash or agent_id in the payload');
select tests.ok((select c ->> 'phone' = '+919811100001' and c ->> 'email' = 'asha@example.com' and c ->> 'dob' = '1985-04-12' and c ->> 'city' = 'Pune'
                        and c ->> 'address' = '12 Park Road' and c ->> 'nominee' = 'Ravi Verma'
                   from jsonb_array_elements(:'home'::jsonb -> 'customers') c where c ->> 'id' = :C1),
  '#PT home: customer fields name/phone/email/dob/city/address/nominee, dob as YYYY-MM-DD');
select tests.ok((select array_agg(p ->> 'id' order by ord) from jsonb_array_elements(
      (select c -> 'policies' from jsonb_array_elements(:'home'::jsonb -> 'customers') c where c ->> 'id' = :C1)) with ordinality t(p, ord))
  = array[:PC, :PA, :PB]::text[], '#PT home: policies by next_due_date, nulls last; the deleted policy is excluded');
select tests.ok(:'home' not like '%DELETED-PLAN%', '#PT home: soft-deleted policy absent');
select tests.ok((select jsonb_typeof(p -> 'premium_amount') = 'number' and jsonb_typeof(p -> 'sum_assured') = 'number'
                        and (p ->> 'premium_amount')::numeric = 12000.50 and p ->> 'next_due_date' = '2026-12-01' and p ->> 'start_date' = '2024-01-15'
                        and p ->> 'plan_name' = 'Plan A' and p ->> 'policy_number' = 'POL-A' and p ->> 'premium_mode' = 'yearly' and p ->> 'status' = 'active'
                   from jsonb_array_elements((select c -> 'policies' from jsonb_array_elements(:'home'::jsonb -> 'customers') c where c ->> 'id' = :C1)) p
                  where p ->> 'id' = :PA),
  '#PT home: policy fields; amounts are JSON numbers; dates YYYY-MM-DD');
select tests.ok((select jsonb_typeof(p -> 'next_due_date') = 'null' or not (p ? 'next_due_date')
                   from jsonb_array_elements((select c -> 'policies' from jsonb_array_elements(:'home'::jsonb -> 'customers') c where c ->> 'id' = :C1)) p
                  where p ->> 'id' = :PB), '#PT home: a policy without a due date keeps a null date');
select tests.ok((select count(*) from jsonb_array_elements((select p -> 'payments' from jsonb_array_elements(
      (select c -> 'policies' from jsonb_array_elements(:'home'::jsonb -> 'customers') c where c ->> 'id' = :C1)) p where p ->> 'id' = :PA)) x)
  = tests.pt_cfg('paymentsShown', 12), '#PT home: payments capped at paymentsShown');
select tests.ok((select array_agg((x ->> 'paid_on')::date order by ord) from jsonb_array_elements((select p -> 'payments' from jsonb_array_elements(
      (select c -> 'policies' from jsonb_array_elements(:'home'::jsonb -> 'customers') c where c ->> 'id' = :C1)) p where p ->> 'id' = :PA))
      with ordinality t(x, ord)) = (select array_agg(d order by d desc) from (select date '2025-01-01' + i d from generate_series(1, 15) i
      order by 1 desc limit tests.pt_cfg('paymentsShown', 12)) q),
  '#PT home: payments newest first (the last paymentsShown by date)');
select tests.ok(:'home' not like '%777.77%' and (select jsonb_typeof(x -> 'amount') = 'number' from jsonb_array_elements((select p -> 'payments'
      from jsonb_array_elements((select c -> 'policies' from jsonb_array_elements(:'home'::jsonb -> 'customers') c where c ->> 'id' = :C1)) p
      where p ->> 'id' = :PA)) x limit 1), '#PT home: a soft-deleted (newest) payment is excluded; amounts are numbers');
select tests.ok((select jsonb_array_length(p -> 'payments') = 0 from jsonb_array_elements((select c -> 'policies'
      from jsonb_array_elements(:'home'::jsonb -> 'customers') c where c ->> 'id' = :C1)) p where p ->> 'id' = :PB), '#PT home: a policy with no payments has an empty list');
rollback;

-- ===========================================================================
-- 8. portal_ask
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C1, :C2]::uuid[], '9811100001', '482913');
insert into tk select 'bpol', id::text from public.policies where agent_id = :B limit 1;
select tests.as_anon();
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PA, 'question', '  When is my premium due?  ') = '{"ok": true}'::jsonb,
  '#PT ask: ok (message trimmed)');
reset role;
select tests.ok((select e.source = 'portal' and e.customer_id = :C1 and e.agent_id = :A and e.name = 'Asha Verma' and e.phone = '+919811100001'
                        and e.status = 'new' and e.site_id = (select id from public.sites where agent_id = :A)
                        and e.message like '%When is my premium due?%' and e.message like '%Plan A%' and e.message like '%POL-A%'
                   from public.enquiries e where e.source = 'portal'),
  '#PT ask: enquiry source portal with customer_id, site_id, name, phone, message prefixed with plan and policy number');
select tests.as_anon();
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PE, 'paid', 'I paid it') = '{"ok": true}'::jsonb, '#PT ask: kind paid on the other family member''s policy ok');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PA, 'bogus', 'hi') = '{"ok": false, "error": "invalid", "field": "kind"}'::jsonb, '#PT ask: bad kind -> invalid/kind');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PF, 'question', 'hi') = '{"ok": false, "error": "invalid", "field": "policy_id"}'::jsonb,
  '#PT ask: a policy of a customer not attached to the account -> invalid/policy_id');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PG, 'question', 'hi') ->> 'field' = 'policy_id', '#PT ask: another phone''s policy of the same agent -> invalid/policy_id');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PD, 'question', 'hi') ->> 'field' = 'policy_id', '#PT ask: a deleted policy -> invalid/policy_id');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, tests.pt_t('bpol')::uuid, 'question', 'hi') ->> 'field' = 'policy_id',
  '#PT ask: agent B''s policy id -> invalid/policy_id');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, gen_random_uuid(), 'question', 'hi') ->> 'field' = 'policy_id', '#PT ask: unknown policy -> invalid/policy_id');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PA, 'question', '   ') = '{"ok": false, "error": "invalid", "field": "message"}'::jsonb, '#PT ask: blank message -> invalid/message');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PA, 'question', repeat('x', 501)) = '{"ok": false, "error": "invalid", "field": "message"}'::jsonb, '#PT ask: 501 chars -> invalid/message');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PA, 'question', null) ->> 'field' = 'message', '#PT ask: null message -> invalid/message');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PA, 'question', repeat('x', 500)) ->> 'ok' = 'true', '#PT ask: exactly 500 chars ok');
select tests.ok(public.portal_ask('garbage', 'ramesh-sharma', null, :PA, 'question', 'hi') = '{"ok": false, "error": "session"}'::jsonb, '#PT ask: bad session -> session');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'chetan-lal', null, :PA, 'question', 'hi') ->> 'ok' = 'false' and public.portal_ask(tests.pt_t('s'), 'chetan-lal', null, :PA, 'question', 'hi') ->> 'error' in ('session', 'unavailable'),
  '#PT ask: session used on another agent''s slug refused');
reset role;
select tests.ok((select count(*) from public.enquiries where source = 'portal') = 3, '#PT ask: only the 3 valid asks created enquiries');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C1]::uuid[], '9811100001', '482913');
select tests.as_anon();
do $$
declare n int := tests.pt_cfg('enquiriesPerHour', 5);
begin
  for i in 1..n loop
    if public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, '30000000-0000-4000-8000-00000000000a', 'question', 'msg ' || i) ->> 'ok' <> 'true' then
      raise exception 'ask % of % was refused', i, n;
    end if;
  end loop;
end $$;
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PA, 'question', 'one too many') = '{"ok": false, "error": "rate_limited"}'::jsonb,
  '#PT ask: rate limited after enquiriesPerHour');
reset role;
select tests.ok((select count(*) from public.enquiries where source = 'portal') = tests.pt_cfg('enquiriesPerHour', 5), '#PT ask: the refused ask created no enquiry');
update public.enquiries set created_at = now() - interval '2 hours' where source = 'portal';
select tests.as_anon();
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PA, 'question', 'later') = '{"ok": true}'::jsonb, '#PT ask: allowed again an hour later');
rollback;

-- ===========================================================================
-- 9. portal_request
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C1, :C2]::uuid[], '9811100001', '482913');
select tests.as_anon();
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C1, 'correction', 'My city is wrong') = '{"ok": true}'::jsonb, '#PT request: ok');
reset role;
select tests.ok((select d.agent_id = :A and d.customer_id = :C1 and d.kind = 'correction' and d.addressed_to = 'agent' and d.name = 'Asha Verma'
                        and d.message = 'My city is wrong' and d.due_at > now() and d.due_at <= now() + interval '31 days'
                   from public.data_requests d where d.customer_id = :C1),
  '#PT request: data_requests row for the agent with customer_id, kind, name, message and a due_at');
select tests.ok((select count(*) from public.data_requests where customer_id = :C1) = 1, '#PT request: exactly one row');
-- kinds: each valid kind accepted (rate-limit window cleared between calls)
select tests.as_anon();
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C2, 'access', null) = '{"ok": true}'::jsonb, '#PT request: kind access, message optional');
reset role;
update public.data_requests set created_at = now() - interval '2 days';
select tests.as_anon();
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C1, 'erasure', null) ->> 'ok' = 'true', '#PT request: kind erasure ok');
reset role;
update public.data_requests set created_at = now() - interval '2 days';
select tests.as_anon();
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C1, 'withdraw_consent', null) ->> 'ok' = 'true', '#PT request: kind withdraw_consent ok');
reset role;
update public.data_requests set created_at = now() - interval '2 days';
select tests.as_anon();
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C1, 'grievance', null) ->> 'ok' = 'true', '#PT request: kind grievance ok');
reset role;
update public.data_requests set created_at = now() - interval '2 days';
select tests.as_anon();
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C1, 'bogus', 'x') = '{"ok": false, "error": "invalid", "field": "kind"}'::jsonb, '#PT request: unknown kind -> invalid/kind');
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C1, null, 'x') ->> 'field' = 'kind', '#PT request: null kind -> invalid/kind');
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C1, 'access', repeat('x', 501)) = '{"ok": false, "error": "invalid", "field": "message"}'::jsonb, '#PT request: 501 chars -> invalid/message');
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C3, 'access', 'x') ->> 'error' = 'invalid', '#PT request: a customer not attached to the account -> invalid');
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C4, 'access', 'x') ->> 'error' = 'invalid', '#PT request: another phone''s customer -> invalid');
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :CC1, 'access', 'x') ->> 'error' = 'invalid', '#PT request: another agent''s customer -> invalid');
select tests.ok(public.portal_request('garbage', 'ramesh-sharma', null, :C1, 'access', 'x') = '{"ok": false, "error": "session"}'::jsonb, '#PT request: bad session -> session');
reset role;
select tests.ok((select count(*) from public.data_requests where customer_id in (:C3, :C4, :CC1)) = 0, '#PT request: refused requests wrote nothing');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C1]::uuid[], '9811100001', '482913');
select tests.as_anon();
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C1, 'access', 'one') ->> 'ok' = 'true'
  and public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C1, 'access', 'two') ->> 'ok' = 'true'
  and public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C1, 'access', 'three') ->> 'ok' = 'true', '#PT request: three requests in 24 hours allowed');
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C1, 'access', 'four') = '{"ok": false, "error": "rate_limited"}'::jsonb,
  '#PT request: the 4th within 24 hours -> rate_limited');
reset role;
select tests.ok((select count(*) from public.data_requests where customer_id = :C1) = 3, '#PT request: the refused request wrote nothing');
update public.data_requests set created_at = now() - interval '25 hours';
select tests.as_anon();
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C1, 'access', 'five') ->> 'ok' = 'true', '#PT request: allowed again after 24 hours');
rollback;

-- ===========================================================================
-- 10. portal_revoke and the customers triggers
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_agent(:A);
select tests.pt_inv('again', :C4);
select tests.as_agent(:A);
select tests.ok(public.portal_revoke(:C4) = '{"ok": true}'::jsonb, '#PT revoke: ok');
select tests.ok(public.portal_status(:C4) ->> 'state' = 'none', '#PT revoke: status none');
reset role;
select tests.ok((select deleted_at is not null from public.portal_accounts where phone = '+919811100004'), '#PT revoke: an account left with no customer is soft-deleted');
select tests.ok(exists (select 1 from public.audit_log where action = 'portal.revoke') and exists (select 1 from public.audit_log where action = 'portal.invite')
  and exists (select 1 from public.audit_log where action = 'portal.activate'), '#PT audit: portal.invite, portal.activate and portal.revoke rows exist');
select tests.ok(not exists (select 1 from public.audit_log where action like 'portal.%' and (changes::text like '%9811100004%' or entity_id is null and entity is null)),
  '#PT audit: portal rows carry no phone number');
select tests.as_anon();
select tests.ok(tests.pt_home('s') ->> 'error' = 'session', '#PT revoke: the session is dead');
select tests.ok(tests.pt_login('9811100004', '482913') = '{"ok": false, "error": "invalid"}'::jsonb, '#PT revoke: login -> invalid');
select tests.ok(tests.pt_act(tests.pt_t('again'), '9811100004', '739204') = '{"ok": false, "error": "invalid_invite"}'::jsonb, '#PT revoke: an unused invite is dead too');
select tests.as_agent(:B);
select tests.throws(format('select public.portal_revoke(%L)', :C4), '#PT revoke: another agent -> not_found', '%not_found%');
rollback;

-- lapsed agent can still revoke (no can_write)
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
update public.subscriptions set trial_ends_at = now() - interval '16 days' where agent_id = :A;
select tests.as_agent(:A);
select tests.ok(public.portal_revoke(:C4) = '{"ok": true}'::jsonb, '#PT revoke: allowed on a lapsed plan');
rollback;

-- family: revoking one member detaches only that member; the last one kills the account
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C1, :C2]::uuid[], '9811100001', '482913');
select tests.as_agent(:A);
select tests.ok(public.portal_revoke(:C1) = '{"ok": true}'::jsonb, '#PT revoke family member C1 ok');
select tests.ok(public.portal_status(:C1) ->> 'state' = 'none' and public.portal_status(:C2) ->> 'state' <> 'none', '#PT revoke: C1 detached, C2 still attached');
select tests.as_anon();
select tests.ok(not exists (select 1 from jsonb_array_elements(coalesce(tests.pt_home('s') -> 'customers', '[]'::jsonb)) c where c ->> 'id' = :C1),
  '#PT revoke: C1 no longer shown');
reset role;
select tests.as_agent(:A);
select public.portal_revoke(:C2);
select tests.as_anon();
select tests.ok(tests.pt_home('s') ->> 'error' = 'session', '#PT revoke: revoking the last customer kills the session');
reset role;
select tests.ok((select deleted_at is not null from public.portal_accounts where phone = '+919811100001'), '#PT revoke: account soft-deleted after the last customer');
rollback;

-- trigger: phone change
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
update public.customers set phone = '98111-00004', name = 'Dev K Renamed' where id = :C4;
select tests.as_anon();
select tests.ok(tests.pt_home('s') ->> 'ok' = 'true' and tests.pt_login('9811100004', '482913') ->> 'ok' = 'true',
  '#PT trigger: re-typing the same phone (and renaming) does not detach');
reset role;
update public.customers set phone = '+919811100099' where id = :C4;
select tests.as_anon();
select tests.ok(tests.pt_home('s') ->> 'error' = 'session', '#PT trigger: a changed phone detaches the customer and kills the session');
select tests.ok(tests.pt_login('9811100004', '482913') ->> 'error' = 'invalid' and tests.pt_login('9811100099', '482913') ->> 'error' = 'invalid',
  '#PT trigger: no login on the old or the new phone after a phone change');
reset role;
select tests.as_agent(:A);
select tests.ok(public.portal_status(:C4) ->> 'state' = 'none', '#PT trigger: status none after a phone change');
reset role;
select tests.ok((select deleted_at is not null from public.portal_accounts where phone = '+919811100004'), '#PT trigger: the empty account is soft-deleted');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C1, :C2]::uuid[], '9811100001', '482913');
update public.customers set phone = '+919811100077' where id = :C2;
select tests.as_anon();
select tests.ok(not exists (select 1 from jsonb_array_elements(coalesce(tests.pt_home('s') -> 'customers', '[]'::jsonb)) c where c ->> 'id' = :C2)
  and exists (select 1 from jsonb_array_elements(coalesce(tests.pt_home('s') -> 'customers', '[]'::jsonb)) c where c ->> 'id' = :C1),
  '#PT trigger: a phone change detaches only that family member (C1 stays, C2 gone)');
rollback;

-- trigger: soft delete
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
update public.customers set deleted_at = now() where id = :C4;
select tests.as_anon();
select tests.ok(tests.pt_home('s') ->> 'error' = 'session', '#PT trigger: soft-deleting the customer kills the session');
select tests.ok(tests.pt_login('9811100004', '482913') ->> 'error' = 'invalid', '#PT trigger: ... and login');
reset role;
select tests.ok((select deleted_at is not null from public.portal_accounts where phone = '+919811100004'), '#PT trigger: the empty account is soft-deleted');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C1, :C2]::uuid[], '9811100001', '482913');
update public.customers set deleted_at = now() where id = :C2;
select tests.as_anon();
select tests.ok(exists (select 1 from jsonb_array_elements(coalesce(tests.pt_home('s') -> 'customers', '[]'::jsonb)) c where c ->> 'id' = :C1)
  and not exists (select 1 from jsonb_array_elements(coalesce(tests.pt_home('s') -> 'customers', '[]'::jsonb)) c where c ->> 'id' = :C2),
  '#PT trigger: soft-deleting one family member removes only that member from home');
rollback;

-- re-invite after a revoke starts a clean account
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_agent(:A);
select public.portal_revoke(:C4);
select tests.pt_inv('fresh', :C4);
select tests.ok(tests.pt_resp('fresh') ->> 'ok' = 'true' and (tests.pt_resp('fresh') ->> 'already_active')::boolean = false,
  '#PT revoke then invite again: a fresh invite, not already active', tests.pt_resp('fresh')::text);
select tests.as_anon();
select tests.ok(tests.pt_home('s') ->> 'error' = 'session', '#PT revoke then invite again: the old session stays dead');
select tests.ok(tests.pt_act(tests.pt_t('fresh'), '9811100004', '739204') ->> 'ok' = 'true', '#PT revoke then invite again: the new invite activates');
rollback;

-- ===========================================================================
-- 11. The Worker gate (DECISIONS #101, review H2): without the secret header every anon RPC is 'unavailable'
--     and does nothing else, bcrypt included
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_agent(:A);
select tests.pt_inv('t', :C1);
reset role;
select tests.pt_bcrypt_calls() as b0 \gset
select tests.pt_header(null);
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '000000') = '{"ok": false, "error": "unavailable"}'::jsonb
  and tests.pt_login('9811100004', '000000') = '{"ok": false, "error": "unavailable"}'::jsonb
  and tests.pt_login('9811100004', '482913') = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#PT gate: no header -> portal_login unavailable (wrong and right PIN)');
select tests.ok(tests.pt_act(tests.pt_t('t'), '9811100009', '739204') = '{"ok": false, "error": "unavailable"}'::jsonb
  and tests.pt_act(tests.pt_t('t'), '9811100001', '739204') = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#PT gate: no header -> portal_activate unavailable (wrong and right phone)');
select tests.ok(tests.pt_home('s') = '{"ok": false, "error": "unavailable"}'::jsonb, '#PT gate: no header -> portal_home unavailable, no data');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PG, 'question', 'hi') = '{"ok": false, "error": "unavailable"}'::jsonb
  and public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C4, 'access', 'hi') = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#PT gate: no header -> portal_ask and portal_request unavailable');
select tests.ok(public.portal_logout(tests.pt_t('s'), true) = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#PT gate: no header -> portal_logout unavailable');
reset role;
select tests.ok(tests.pt_bcrypt_calls() = :b0, '#PT gate: the refused calls ran no bcrypt (crypt/gen_salt call count unchanged)',
  tests.pt_bcrypt_calls()::text || ' vs ' || :b0);
select tests.ok((select failed_attempts = 0 and failure_streak = 0 and locked_until is null from public.portal_accounts where phone = '+919811100004')
  and not exists (select 1 from private.portal_login_failures)
  and (select phone_tries = 0 and used_at is null from public.portal_invites where token_hash = sha256(convert_to(tests.pt_t('t'), 'UTF8')))
  and not exists (select 1 from public.portal_sessions where revoked_at is not null)
  and not exists (select 1 from public.enquiries where source = 'portal')
  and not exists (select 1 from public.data_requests where customer_id = :C4),
  '#PT gate: the refused calls wrote nothing (no failure counted, no phone try, no session revoked, no enquiry or request)');
-- a wrong key, an empty key, a header that is not JSON
select tests.pt_header('wrong-key');
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '000000') ->> 'error' = 'unavailable' and tests.pt_home('s') ->> 'error' = 'unavailable',
  '#PT gate: a wrong key -> unavailable');
reset role;
select tests.pt_header('');
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '000000') ->> 'error' = 'unavailable', '#PT gate: an empty key -> unavailable');
reset role;
select set_config('request.headers', 'not json', true);
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '000000') ->> 'error' = 'unavailable', '#PT gate: request.headers that is not JSON -> unavailable, never raises');
reset role;
select tests.ok(tests.pt_bcrypt_calls() = :b0 and (select failed_attempts from public.portal_accounts where phone = '+919811100004') = 0,
  '#PT gate: still no bcrypt and no failure counted');
-- the right key, but no gate row
select tests.pt_header('test-portal-key');
delete from private.portal_gate;
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '482913') ->> 'error' = 'unavailable' and tests.pt_home('s') ->> 'error' = 'unavailable',
  '#PT gate: an empty portal_gate table -> unavailable even with the key');
reset role;
select tests.ok(tests.pt_bcrypt_calls() = :b0, '#PT gate: empty table, no bcrypt');
-- go-live statement, then the positive control
insert into private.portal_gate values (1, sha256(convert_to('test-portal-key', 'UTF8')))
on conflict (id) do update set key_hash = excluded.key_hash;
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '000000') = '{"ok": false, "error": "invalid"}'::jsonb and tests.pt_home('s') ->> 'ok' = 'true',
  '#PT gate: with the gate row and the key the RPCs work again');
reset role;
select tests.ok(tests.pt_bcrypt_calls() > :b0, '#PT gate: [control] a gated-in wrong PIN does run bcrypt (the counter works)');
rollback;

-- ===========================================================================
-- 12. Per-site failed-login cap (DECISIONS #101, review H2)
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.pt_active('sc', :C, array[:CC1]::uuid[], '9822200001', '482913', 'chetan-lal');
update public.app_config set value = jsonb_set(value, '{siteFailedLoginsPerMinute}', '3') where key = 'portal';
select tests.as_anon();
select tests.ok(tests.pt_login('9000000001', '000000') ->> 'error' = 'invalid' and tests.pt_login('9000000002', '000000') ->> 'error' = 'invalid'
  and tests.pt_login('9811100004', '000000') ->> 'error' = 'invalid', '#PT cap: three failed logins on the site (two unknown numbers, one wrong PIN)');
reset role;
select tests.pt_bcrypt_calls() as b1 \gset
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '482913') = '{"ok": false, "error": "rate_limited"}'::jsonb,
  '#PT cap: at siteFailedLoginsPerMinute failures the next login on the site is rate_limited, even the right PIN');
reset role;
select tests.ok(tests.pt_bcrypt_calls() = :b1, '#PT cap: a rate_limited login runs no bcrypt');
select tests.ok((select count(*) from private.portal_login_failures) = 3
  and (select failed_attempts from public.portal_accounts where phone = '+919811100004') = 1,
  '#PT cap: rate_limited attempts are not recorded and touch no account');
select tests.as_anon();
select tests.ok(tests.pt_login('9822200001', '482913', 'chetan-lal') ->> 'ok' = 'true', '#PT cap: the cap is per site (agent C''s site still logs in)');
reset role;
update private.portal_login_failures set at = now() - interval '2 minutes';
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '482913') ->> 'ok' = 'true', '#PT cap: a minute later logins work again');
select tests.ok(tests.pt_login('9811100004', '000000') ->> 'error' = 'invalid', '#PT cap: ... and a new failure is counted');
reset role;
select tests.ok((select count(*) from private.portal_login_failures) = 1 and (select at from private.portal_login_failures) = now(),
  '#PT cap: a failure prunes the site''s rows older than a minute');
rollback;

-- ===========================================================================
-- 13. Lockouts over a window (DECISIONS #101, review M2)
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s0', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_anon();
do $$
begin
  for i in 1..19 loop
    perform tests.pt_unlock();
    perform tests.pt_login('9811100004', '000000');
  end loop;
end $$;
select tests.pt_unlock();
select tests.ok(tests.pt_login('9811100004', '482913') ->> 'ok' = 'true', '#PT window: 19 failures, then the right PIN logs in');
reset role;
select tests.ok((select failed_attempts = 19 and failure_streak = 0 from public.portal_accounts), '#PT window: the success kept the 19 failures');
select tests.as_anon();
select tests.pt_login('9811100004', '000000') as h20 \gset
select tests.ok(:'h20'::jsonb ->> 'error' = 'locked'
  and (:'h20'::jsonb ->> 'until')::timestamptz = now() + make_interval(hours => tests.pt_cfg('hardLockHours', 24)),
  '#PT window: one more failure after the success -> hard lock at hardLockAfter (a success does not reset the count)', :'h20');
select tests.ok((tests.pt_login('9811100004', '482913') ->> 'until')::timestamptz = now() + make_interval(hours => tests.pt_cfg('hardLockHours', 24)),
  '#PT window: while hard-locked, locked always carries until');
reset role;
-- the hard lock runs out by itself
update public.portal_accounts set hard_locked_at = now() - make_interval(hours => tests.pt_cfg('hardLockHours', 24)) - interval '1 minute';
select tests.as_agent(:A);
select tests.ok(public.portal_status(:C4) ->> 'state' = 'active' and (public.portal_status(:C4) -> 'locked_until') = 'null'::jsonb,
  '#PT hard lock expired: status active again', public.portal_status(:C4)::text);
reset role;
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '482913') ->> 'ok' = 'true', '#PT hard lock expired: the right PIN logs in, no new invite needed');
reset role;
select tests.ok((select hard_locked_at is null and failed_attempts = 0 and failure_window_started_at is null from public.portal_accounts),
  '#PT hard lock expired: it cleared itself and the counters');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s0', :A, array[:C4]::uuid[], '9811100004', '482913');
update public.portal_accounts set failed_attempts = 19, failure_window_started_at = now() - make_interval(days => tests.pt_cfg('failureWindowDays', 30)) - interval '1 hour';
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '000000') = '{"ok": false, "error": "invalid"}'::jsonb,
  '#PT window: failures older than failureWindowDays no longer count (20th failure overall, no lock)');
reset role;
select tests.ok((select failed_attempts = 1 and failure_window_started_at = now() from public.portal_accounts), '#PT window: a new window starts at the failure');
update public.portal_accounts set failed_attempts = 19, failure_window_started_at = now() - make_interval(days => tests.pt_cfg('failureWindowDays', 30)) + interval '1 hour';
select tests.as_anon();
select tests.ok(tests.pt_login('9811100004', '000000') ->> 'error' = 'locked', '#PT window: inside the window the 20th failure hard-locks');
reset role;
rollback;

-- ===========================================================================
-- 14. A customer joins a login only through its own link (DECISIONS #101, review M1)
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C1]::uuid[], '9811100001', '482913');
select tests.as_agent(:A);
select tests.pt_inv('c3', :C3);
select tests.ok((tests.pt_resp('c3') ->> 'already_active')::boolean, '#PT M1: a link for C3 on C1''s active number: already_active');
select tests.ok(public.portal_status(:C3) ->> 'state' = 'invited' and public.portal_status(:C3) -> 'shared_with' = '["Asha Verma"]'::jsonb
  and public.portal_status(:C1) ->> 'state' = 'active' and public.portal_status(:C1) -> 'shared_with' = '[]'::jsonb,
  '#PT M1: C3 is invited (its link joins Asha''s login); C1 shares with no one yet', public.portal_status(:C3)::text);
reset role;
select tests.as_anon();
select tests.pt_home('s')::text as m1home \gset
select tests.ok((select array_agg(c ->> 'id') from jsonb_array_elements(:'m1home'::jsonb -> 'customers') c) = array[:C1]::text[]
  and :'m1home' not like '%Chitra%' and :'m1home' not like '%Plan F UNINVITED%',
  '#PT M1: C1''s session does not see C3 before C3''s link is activated', :'m1home');
select tests.ok(public.portal_ask(tests.pt_t('s'), 'ramesh-sharma', null, :PF, 'question', 'hi') ->> 'field' = 'policy_id'
  and public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C3, 'access', null) ->> 'field' = 'customer_id',
  '#PT M1: ... nor ask about or request for C3');
reset role;
select tests.ok(not exists (select 1 from public.consents where customer_id = :C3), '#PT M1: no consent recorded for C3 before activation');
select tests.as_anon();
select tests.ok(tests.pt_act(tests.pt_t('c3'), '9811100001', '120485') = '{"ok": false, "error": "invalid", "field": "pin"}'::jsonb,
  '#PT M1: the DOB of a customer already on the login (C1, 120485) is refused as the new PIN');
select tests.pt_act(tests.pt_t('c3'), '9811100001', '739204') as act3 \gset
select tests.ok(:'act3'::jsonb ->> 'ok' = 'true', '#PT M1: C3''s own link activates', :'act3');
select tests.ok(tests.pt_home('s') ->> 'error' = 'session', '#PT M1: activating C3 revokes the login''s older sessions');
select tests.ok(tests.pt_login('9811100001', '482913') ->> 'error' = 'invalid' and tests.pt_login('9811100001', '739204') ->> 'ok' = 'true',
  '#PT M1: activating C3 resets the PIN of the shared login');
select tests.ok((select array_agg(c ->> 'id' order by c ->> 'name') from jsonb_array_elements(
    public.portal_home(:'act3'::jsonb ->> 'session_token', 'ramesh-sharma', null) -> 'customers') c) = array[:C1, :C3]::text[],
  '#PT M1: after activation the login shows C1 and C3');
reset role;
select tests.ok((select count(*) from public.consents where customer_id = :C3 and notice_key = 'customer_portal') = 1
  and (select count(*) from public.consents where customer_id = :C1 and notice_key = 'customer_portal') = 1,
  '#PT M1: C3''s activation recorded one consent, for C3 only');
rollback;

-- ===========================================================================
-- 15. L2: a verified customer's requests have their own limit; L6: a closed account keeps no PIN or counters
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_anon();
select tests.ok(public.submit_data_request('ramesh-sharma', null, 'Someone', '9811100004', 'access', null, null) ->> 'ok' = 'true'
  and public.submit_data_request('ramesh-sharma', null, 'Someone', '9811100004', 'access', null, null) ->> 'ok' = 'true'
  and public.submit_data_request('ramesh-sharma', null, 'Someone', '9811100004', 'access', null, null) ->> 'ok' = 'true',
  '#PT L2: [setup] a stranger files three public requests for the customer''s number');
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C4, 'access', null) ->> 'ok' = 'true',
  '#PT L2: the public form''s requests do not block the verified customer''s request');
select tests.ok(public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C4, 'correction', 'x') ->> 'ok' = 'true'
  and public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C4, 'erasure', null) ->> 'ok' = 'true'
  and public.portal_request(tests.pt_t('s'), 'ramesh-sharma', null, :C4, 'access', null) = '{"ok": false, "error": "rate_limited"}'::jsonb,
  '#PT L2: the portal''s own limit is 3 per login per 24 hours');
reset role;
select tests.ok((select message is null and phone = '+919811100004' and status = 'open' from public.data_requests where customer_id = :C4 and kind = 'access'),
  '#PT request row fields (no message, the login''s number, open)');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
update public.portal_accounts set failed_attempts = 7, failure_streak = 2, failure_window_started_at = now(),
  locked_until = now() + interval '5 minutes', hard_locked_at = now();
select tests.as_agent(:A);
select public.portal_revoke(:C4);
reset role;
select tests.ok((select deleted_at is not null and pin_hash is null and activated_at is null and failed_attempts = 0 and failure_streak = 0
                        and failure_window_started_at is null and locked_until is null and hard_locked_at is null
                   from public.portal_accounts where phone = '+919811100004'),
  '#PT L6: a soft-deleted account keeps no PIN hash, counters or locks');
rollback;

-- an account whose last customer goes while another customer's link is still out: wiped, kept for that link
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C1]::uuid[], '9811100001', '482913');
select tests.as_agent(:A);
select tests.pt_inv('c2', :C2);
select public.portal_revoke(:C1);
select tests.ok(public.portal_status(:C1) ->> 'state' = 'none' and public.portal_status(:C2) ->> 'state' = 'invited',
  '#PT revoke: C1 off; C2''s pending link survives');
reset role;
select tests.ok((select deleted_at is null and pin_hash is null and failed_attempts = 0 from public.portal_accounts where phone = '+919811100001')
  and not exists (select 1 from public.portal_sessions where revoked_at is null),
  '#PT revoke: the emptied account is wiped (no PIN, sessions revoked) but kept open for C2''s link');
select tests.as_anon();
select tests.ok(tests.pt_login('9811100001', '482913') = '{"ok": false, "error": "invalid"}'::jsonb, '#PT revoke: the old PIN no longer logs in');
select tests.ok(tests.pt_act(tests.pt_t('c2'), '9811100001', '739204') ->> 'ok' = 'true', '#PT revoke: C2''s link activates the login afresh');
select tests.ok((select array_agg(c ->> 'id') from jsonb_array_elements(public.portal_home(tests.pt_login('9811100001', '739204') ->> 'session_token', 'ramesh-sharma', null) -> 'customers') c)
      = array[:C2]::text[],
  '#PT revoke: the new login shows C2 only');
rollback;

-- a pending (never activated) link dies with a phone change or a soft delete of its customer
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.as_agent(:A);
select tests.pt_inv('p4', :C4);
select tests.pt_inv('p1', :C1);
reset role;
update public.customers set phone = '+919811100088' where id = :C4;
update public.customers set deleted_at = now() where id = :C1;
select tests.as_anon();
select tests.ok(tests.pt_act(tests.pt_t('p4'), '9811100004', '739204') = '{"ok": false, "error": "invalid_invite"}'::jsonb
  and tests.pt_act(tests.pt_t('p4'), '9811100088', '739204') = '{"ok": false, "error": "invalid_invite"}'::jsonb,
  '#PT trigger: a phone change voids the customer''s pending link (old and new number)');
select tests.ok(tests.pt_act(tests.pt_t('p1'), '9811100001', '739204') = '{"ok": false, "error": "invalid_invite"}'::jsonb,
  '#PT trigger: a soft delete voids the customer''s pending link');
reset role;
select tests.ok(not exists (select 1 from public.portal_invites where revoked_at is null and used_at is null)
  and not exists (select 1 from public.portal_accounts where deleted_at is null),
  '#PT trigger: the pending links are revoked and the empty accounts closed');
rollback;

-- L7: in portal_invite and portal_activate, every advisory lock comes before any row lock
select tests.ok((select bool_and(
    position('pg_advisory_xact_lock' in p.prosrc) > 0
    and position('for share' in p.prosrc) > position('pg_advisory_xact_lock' in p.prosrc)
    and position('portal_lock_account' in p.prosrc) < position('for share' in p.prosrc)
    and (position('for update' in p.prosrc) = 0 or position('for update' in p.prosrc) > position('portal_lock_account' in p.prosrc)))
   from pg_proc p where p.oid = 'public.portal_invite(uuid)'::regprocedure)
  and (select position('portal_lock_account' in p.prosrc) < position('for update' in p.prosrc)
          and position('for update' in p.prosrc) < position('for share' in p.prosrc)
         from pg_proc p where p.oid = 'public.portal_activate(text,text,text,text,text,jsonb)'::regprocedure),
  '#PT L7: portal_invite and portal_activate take the advisory locks before any row lock');

-- ===========================================================================
-- 16. More paths: custom host, cross-agent, status details, deletes, the app path, stored formats
-- ===========================================================================
begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
insert into public.site_domains (agent_id, site_id, hostname, kind, status)
select :A, id, 'ramesh-portal.example.in', 'custom', 'active' from public.sites where agent_id = :A;
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913', 'ramesh-sharma');
select tests.as_anon();
select tests.ok((public.get_public_site_by_host('Ramesh.Example.in:443') -> 'portal') = 'true'::jsonb, '#PT host: the payload by host has portal true');
select tests.ok(public.portal_home(tests.pt_t('s'), null, 'ramesh-portal.example.in') ->> 'ok' = 'true', '#PT host: the session works through the custom host');
select tests.ok(public.portal_login(null, 'ramesh-portal.example.in', '9811100004', '482913') ->> 'ok' = 'true', '#PT host: login through the custom host');
select tests.ok(public.portal_home(tests.pt_t('s'), null, 'nobody-portal.example.in') ->> 'error' = 'unavailable', '#PT host: unknown host -> unavailable');
select tests.ok(public.portal_home(tests.pt_t('s'), null, null) ->> 'error' = 'unavailable', '#PT host: no slug and no host -> unavailable');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.ok(public.entitlement(:C) <> 'lapsed', '#PT [setup] agent C is not lapsed', public.entitlement(:C));
select tests.pt_active('sc', :C, array[:CC1]::uuid[], '9822200001', '482913', 'chetan-lal') ->> 'ok' as cok \gset
select tests.ok(:'cok' = 'true', '#PT cross-agent: agent C''s customer activates on C''s site');
select tests.pt_active('sa', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_anon();
select tests.ok(public.portal_home(tests.pt_t('sc'), 'chetan-lal', null) ->> 'ok' = 'true', '#PT cross-agent: C''s session on C''s site ok');
select tests.ok(public.portal_home(tests.pt_t('sc'), 'ramesh-sharma', null) ->> 'error' = 'session', '#PT cross-agent: C''s session on A''s site -> session');
select tests.ok(public.portal_ask(tests.pt_t('sa'), 'ramesh-sharma', null, :PG, 'question', 'x') ->> 'ok' = 'true', '#PT cross-agent: A''s ask on A''s site ok');
select tests.ok(public.portal_request(tests.pt_t('sc'), 'ramesh-sharma', null, :C4, 'access', null) ->> 'error' = 'session', '#PT cross-agent: C''s token, request on A''s site -> session');
select tests.ok(public.portal_login('chetan-lal', null, '9811100004', '482913') ->> 'error' = 'invalid', '#PT cross-agent: A''s number and PIN on C''s site -> invalid');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C1, :C2]::uuid[], '9811100001', '482913');
select tests.as_agent(:A);
select tests.ok(public.portal_status(:C1) -> 'shared_with' = '["Bimal Verma"]'::jsonb and (public.portal_status(:C1) ->> 'last_login_at') is null,
  '#PT status: shared_with lists the other family member; no login yet', public.portal_status(:C1)::text);
reset role;
update public.portal_sessions set last_seen_at = now() - interval '5 minutes' where revoked_at is null;
select tests.as_anon();
select tests.pt_home('s') ->> 'ok';
reset role;
select tests.ok((select last_seen_at < now() - interval '4 minutes' from public.portal_sessions where revoked_at is null), '#PT session: last_seen_at not touched within 10 minutes');
update public.portal_sessions set last_seen_at = now() - interval '11 minutes' where revoked_at is null;
select tests.as_anon();
select tests.pt_home('s') ->> 'ok';
reset role;
select tests.ok((select last_seen_at > now() - interval '1 minute' from public.portal_sessions where revoked_at is null), '#PT session: last_seen_at touched after 10 minutes');
select tests.as_anon();
select public.portal_login('ramesh-sharma', null, '9811100001', '482913') ->> 'session_token' as t2 \gset
select public.portal_logout(tests.pt_t('s'), false);
select tests.ok(public.portal_logout(tests.pt_t('s'), true) = '{"ok":true}'::jsonb and public.portal_home(:'t2', 'ramesh-sharma', null) ->> 'ok' = 'true',
  '#PT logout everywhere with an already revoked token does nothing');
reset role;
select tests.as_agent(:A);
select tests.ok((public.portal_status(:C1) ->> 'last_login_at') is not null, '#PT status: last_login_at set after a login');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C1, :C2]::uuid[], '9811100001', '482913');
delete from public.customers where id = :C2;
select tests.as_anon();
select tests.ok(public.portal_home(tests.pt_t('s'), 'ramesh-sharma', null) ->> 'ok' = 'true', '#PT hard delete of one family member keeps the session');
reset role;
delete from public.customers where id = :C1;
select tests.ok((select deleted_at is not null and pin_hash is null from public.portal_accounts where phone = '+919811100001')
  and not exists (select 1 from public.portal_sessions where revoked_at is null), '#PT hard delete of the last member closes and wipes the account');
select tests.pt_active('s4', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.lives($$delete from auth.users where id = 'aaaaaaaa-0000-4000-8000-00000000000a'$$, '#PT deleting the agent cascades through portal data');
select tests.ok(not exists (select 1 from public.portal_accounts) and not exists (select 1 from public.portal_sessions)
  and not exists (select 1 from public.portal_invites) and not exists (select 1 from public.portal_account_customers), '#PT nothing portal is left after the cascade');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.as_agent(:A);
select tests.lives($$update public.customers set phone = '9811100055' where id = '20000000-0000-4000-8000-000000000004'$$, '#PT app path: the agent edits a phone through RLS');
select tests.lives($$delete from public.customers where id = '20000000-0000-4000-8000-000000000005'$$, '#PT app path: the agent hard-deletes an unrelated customer through RLS');
reset role;
select tests.ok(not exists (select 1 from public.portal_account_customers), '#PT app path: the SECURITY DEFINER trigger detached the customer');
rollback;

begin;
create temp table tk (k text primary key, v text);
grant all on tk to anon, authenticated;
select tests.pt_fixture();
select tests.pt_active('s', :A, array[:C4]::uuid[], '9811100004', '482913');
select tests.ok((select pin_hash like '$2a$10$%' from public.portal_accounts), '#PT PIN hash is bcrypt cost 10');
select tests.ok(octet_length((select token_hash from public.portal_sessions limit 1)) = 32, '#PT session hash is 32 bytes');
select tests.ok(public.get_public_site('ramesh-sharma')::text !~ 'pin_hash|token_hash|portal_accounts|9811100004', '#PT the public payload has no portal internals');
rollback;

-- Leave the session as other files expect it.
select set_config('request.headers', '', false);
delete from private.portal_gate;
reset track_functions;
