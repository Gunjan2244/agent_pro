-- LOCAL TESTS ONLY. Customer sign-up link and Incomplete forms (supabase/migrations/20261025060000_customer_signup.sql,
-- DECISIONS #108): the customer_drafts table (RLS, grants, the guard and validate triggers, realtime), the private link
-- table, the agent RPCs (customer_signup_link / customer_signup_renew), the anon RPCs (signup_open / signup_consent /
-- signup_save / signup_submit) and private.purge_customer_drafts(). Black-box against the contract; fixtures are made
-- as postgres, the RPCs are called as the real roles. Needs the generated 20261025070819_catalog_sync.sql (flag
-- customerSignup, app_config.customer_signup, retention.customerDraftsDays, notices customer_signup and
-- customer_signup_guardian). Every block rolls back, except the concurrency check in section 10, which cleans up.
-- The Worker gate: like 96_portal.sql, this file stores the hash of a test key in private.portal_gate and sends the key
-- as the session's request.headers; section 4 overrides it per transaction. Both are removed at the end.
\set A '''aaaaaaaa-0000-4000-8000-00000000000a'''
\set B '''bbbbbbbb-0000-4000-8000-00000000000b'''
\set C '''cccccccc-0000-4000-8000-00000000000c'''

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------
-- A new sign-up link of agent p_agent (definer: sets the agent's claims for this transaction). Returns the response.
create or replace function tests.cs_link(p_agent uuid) returns jsonb language plpgsql security definer as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', p_agent, 'role', 'authenticated')::text, true);
  return public.customer_signup_link();
end;
$$;

-- Anon wrappers (agent A's slug by default). They run in the caller's role.
create or replace function tests.cs_open(p_token text, p_slug text default 'ramesh-sharma', p_host text default null)
returns jsonb language sql as $$ select public.signup_open(p_slug, p_host, p_token) $$;
create or replace function tests.cs_consent(p_token text, p_slug text default 'ramesh-sharma')
returns jsonb language sql as $$ select public.signup_consent(p_slug, null, p_token, tests.consent('customer_signup')) $$;
create or replace function tests.cs_save(p_token text, p_fields jsonb, p_active text default null, p_slug text default 'ramesh-sharma')
returns jsonb language sql as $$ select public.signup_save(p_slug, null, p_token, p_fields, p_active) $$;
create or replace function tests.cs_submit(p_token text, p_fields jsonb, p_guardian jsonb default null, p_slug text default 'ramesh-sharma')
returns jsonb language sql as $$ select public.signup_submit(p_slug, null, p_token, p_fields, p_guardian) $$;

-- The draft behind a token (as postgres).
create or replace function tests.cs_draft(p_token text) returns public.customer_drafts language sql stable security definer as $$
  select d.* from public.customer_drafts d join private.customer_draft_links l on l.draft_id = d.id
   where l.token_hash = sha256(convert_to(p_token, 'UTF8'));
$$;

-- The Worker's header for this transaction (null = no header at all).
create or replace function tests.cs_header(p_key text) returns void language sql as $$
  select set_config('request.headers', case when p_key is null then '{}' else json_build_object('x-portal-key', p_key)::text end, true);
$$;

-- A draft's timestamps moved back (the set_updated_at trigger would overwrite updated_at; replica mode skips triggers).
create or replace function tests.cs_age(p_id uuid, p_age interval) returns void language plpgsql security definer as $$
begin
  perform set_config('session_replication_role', 'replica', true);
  update public.customer_drafts set updated_at = now() - p_age, created_at = now() - p_age where id = p_id;
  perform set_config('session_replication_role', 'origin', true);
end;
$$;

grant execute on all functions in schema tests to anon, authenticated;
-- Some checks run as the service role (trusted writer).
grant usage on schema tests to service_role;
grant execute on all functions in schema tests to service_role;

insert into private.portal_gate values (1, sha256(convert_to('test-portal-key', 'UTF8')))
on conflict (id) do update set key_hash = excluded.key_hash;
select set_config('request.headers', '{"x-portal-key":"test-portal-key"}', false);

-- ===========================================================================
-- 1. Structure: tables, RLS, grants, functions, realtime, catalog
-- ===========================================================================
select tests.ok((select c.relrowsecurity from pg_class c where c.oid = 'public.customer_drafts'::regclass)
  and (select c.relrowsecurity from pg_class c where c.oid = 'private.customer_draft_links'::regclass),
  '#CS customer_drafts and private.customer_draft_links exist with RLS enabled');
select tests.ok(has_table_privilege('authenticated', 'public.customer_drafts', 'select')
  and has_table_privilege('authenticated', 'public.customer_drafts', 'insert')
  and has_table_privilege('authenticated', 'public.customer_drafts', 'update')
  and not has_table_privilege('authenticated', 'public.customer_drafts', 'delete')
  and not has_table_privilege('authenticated', 'public.customer_drafts', 'truncate')
  and has_table_privilege('service_role', 'public.customer_drafts', 'delete'),
  '#CS customer_drafts: authenticated may select, insert, update (no delete, no truncate); service_role all');
select tests.ok(not exists (
  select 1 from unnest(array['select', 'insert', 'update', 'delete', 'truncate', 'references', 'trigger']) p
   where has_table_privilege('anon', 'public.customer_drafts', p)),
  '#CS anon holds no privilege on customer_drafts');
select tests.ok(not exists (
  select 1 from (values ('anon'), ('authenticated'), ('service_role')) r(role)
  cross join (values ('select'), ('insert'), ('update'), ('delete')) p(priv)
  where has_table_privilege(r.role, 'private.customer_draft_links', p.priv)),
  '#CS private.customer_draft_links: no privilege for anon, authenticated or service_role');
select tests.ok((select array_agg(p.polname::text || ':' || p.polcmd::text order by p.polname) from pg_policy p
                  where p.polrelid = 'public.customer_drafts'::regclass)
  = array['customer_drafts_insert_own:a', 'customer_drafts_select_own:r', 'customer_drafts_update_own:w'],
  '#CS customer_drafts policies: select, insert, update (no delete policy)');
select tests.ok((select pg_get_expr(p.polwithcheck, p.polrelid) from pg_policy p
                  where p.polrelid = 'public.customer_drafts'::regclass and p.polname = 'customer_drafts_insert_own') like '%source = ''agent''%',
  '#CS the insert policy allows source = agent only');
select tests.ok(not exists (select 1 from pg_policy p where p.polrelid = 'private.customer_draft_links'::regclass),
  '#CS private.customer_draft_links has no policies (INFO 0008 by design)');
select tests.ok(exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'customer_drafts'),
  '#CS customer_drafts is in the supabase_realtime publication');
select tests.ok((select count(*) from information_schema.columns
                  where table_schema = 'public' and table_name = 'customer_drafts' and column_name like '%token%') = 0,
  '#CS customer_drafts has no token column (the hash lives in the private table)');

select tests.ok((select count(*) from pg_proc p where p.oid = any (array[
     'public.customer_signup_link()', 'public.customer_signup_renew(uuid)', 'public.signup_open(text,text,text)',
     'public.signup_consent(text,text,text,jsonb)', 'public.signup_save(text,text,text,jsonb,text)',
     'public.signup_submit(text,text,text,jsonb,jsonb)']::regprocedure[])
     and p.prosecdef and exists (select 1 from unnest(p.proconfig) cfg where replace(cfg, ' ', '') in ('search_path=""', 'search_path='))) = 6,
  '#CS all 6 sign-up RPCs are SECURITY DEFINER with search_path = ''''');
select tests.ok(not exists (
  select 1 from pg_proc p, unnest(array[
     'public.customer_signup_link()', 'public.customer_signup_renew(uuid)', 'public.signup_open(text,text,text)',
     'public.signup_consent(text,text,text,jsonb)', 'public.signup_save(text,text,text,jsonb,text)',
     'public.signup_submit(text,text,text,jsonb,jsonb)']::regprocedure[]) f(oid)
   where p.oid = f.oid and exists (select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a where a.grantee = 0)),
  '#CS no sign-up RPC is executable by PUBLIC');
select tests.ok(has_function_privilege('authenticated', 'public.customer_signup_link()', 'execute')
  and has_function_privilege('authenticated', 'public.customer_signup_renew(uuid)', 'execute')
  and not has_function_privilege('anon', 'public.customer_signup_link()', 'execute')
  and not has_function_privilege('anon', 'public.customer_signup_renew(uuid)', 'execute'),
  '#CS agent RPCs (link, renew): authenticated only');
select tests.ok(has_function_privilege('anon', 'public.signup_open(text,text,text)', 'execute')
  and has_function_privilege('anon', 'public.signup_consent(text,text,text,jsonb)', 'execute')
  and has_function_privilege('anon', 'public.signup_save(text,text,text,jsonb,text)', 'execute')
  and has_function_privilege('anon', 'public.signup_submit(text,text,text,jsonb,jsonb)', 'execute')
  and not has_function_privilege('authenticated', 'public.signup_open(text,text,text)', 'execute')
  and not has_function_privilege('authenticated', 'public.signup_consent(text,text,text,jsonb)', 'execute')
  and not has_function_privilege('authenticated', 'public.signup_save(text,text,text,jsonb,text)', 'execute')
  and not has_function_privilege('authenticated', 'public.signup_submit(text,text,text,jsonb,jsonb)', 'execute'),
  '#CS anon RPCs (open, consent, save, submit): anon only');
select tests.ok(not exists (
  select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'private' and p.proname in ('customer_signup_config', 'customer_drafts_days', 'customer_draft_caps',
           'customer_draft_fields_ok', 'customer_draft_merge', 'customer_drafts_guard', 'customer_drafts_validate',
           'customer_draft_lock', 'signup_has_site', 'signup_live_links', 'signup_resolve', 'signup_rate_ok',
           'purge_customer_drafts')
     and (has_function_privilege('anon', p.oid, 'execute') or has_function_privilege('authenticated', p.oid, 'execute')
          or has_function_privilege('service_role', p.oid, 'execute'))),
  '#CS the private sign-up helpers (purge included) are not executable by any API role');
select tests.ok((select not p.prosecdef from pg_proc p where p.oid = 'private.customer_drafts_guard()'::regprocedure)
  and (select p.prosecdef from pg_proc p where p.oid = 'private.customer_drafts_validate()'::regprocedure),
  '#CS the guard trigger is SECURITY INVOKER (it reads current_user), the validate trigger SECURITY DEFINER');
select tests.ok((select array_agg(t.tgname::text order by t.tgname) from pg_trigger t
                  where t.tgrelid = 'public.customer_drafts'::regclass and not t.tgisinternal)
  = array['customer_drafts_guard', 'customer_drafts_validate', 'set_updated_at'],
  '#CS customer_drafts triggers: guard, validate, set_updated_at (fired in that order)');

-- catalog dependencies
select tests.ok(private.flag_enabled('customerSignup'), '#CS [catalog] flag customerSignup is on by default');
select tests.ok(private.customer_signup_config() = '{"linkDays":7,"savesPerMinute":60,"openLinksPerAgent":25,"presenceSeconds":30}'::jsonb,
  '#CS [catalog] app_config.customer_signup has the 4 keys and customer_signup_config accepts it', private.customer_signup_config()::text);
select tests.ok(private.customer_drafts_days() = 30, '#CS [catalog] retention.customerDraftsDays = 30');
select tests.ok(public.catalog_privacy_notice('customer_signup') is not null and public.catalog_privacy_notice('customer_signup_guardian') is not null,
  '#CS [catalog] notices customer_signup and customer_signup_guardian exist');
begin;
update public.app_config set value = value - 'presenceSeconds' where key = 'customer_signup';
select tests.throws($$select private.customer_signup_config()$$, '#CS config: a missing key raises', '%customer_signup.presenceSeconds%');
update public.app_config set value = jsonb_set(value, '{linkDays}', '"7"') where key = 'customer_signup';
select tests.throws($$select private.customer_signup_config()$$, '#CS config: a non-number raises', '%linkDays%');
delete from public.app_config where key = 'customer_signup';
select tests.throws($$select private.customer_signup_config()$$, '#CS config: a missing row raises P0001', '%app_config.customer_signup is missing%');
select tests.as_agent(:A);
select tests.throws($$select public.customer_signup_link()$$, '#CS config missing: customer_signup_link raises (no SQL defaults)', '%customer_signup is missing%');
reset role;
update public.app_config set value = value - 'customerDraftsDays' where key = 'retention';
select tests.throws($$select private.customer_drafts_days()$$, '#CS config: missing retention.customerDraftsDays raises', '%customerDraftsDays%');
rollback;

-- ===========================================================================
-- 2. Client writes: RLS, the guard, field validation
-- ===========================================================================
begin;
select tests.as_agent(:A);
select tests.lives($$insert into public.customer_drafts (id, agent_id, source, fields)
  values ('40000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-00000000000a', 'agent',
          '{"name":"Asha","phone":"98111","notes":"met at the fair"}')$$,
  '#CS client: the agent inserts its own agent-source draft (notes allowed)');
select tests.throws($$insert into public.customer_drafts (agent_id, source, link_expires_at)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'customer', now() + interval '7 days')$$,
  '#CS client: inserting a customer-source draft is refused', '%customer_drafts_source%');
select tests.throws($$insert into public.customer_drafts (agent_id, source, fields)
  values ('bbbbbbbb-0000-4000-8000-00000000000b', 'agent', '{}')$$,
  '#CS client: inserting a draft for another agent is refused by RLS', '%row-level security%');
select tests.throws($$insert into public.customer_drafts (agent_id, source, consent)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', '{"notice_key":"customer_signup"}')$$,
  '#CS client: an agent draft can''t carry consent', '%customer_drafts_agent_columns%');
select tests.throws($$insert into public.customer_drafts (agent_id, source, active_field, customer_seen_at)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', 'name', now())$$,
  '#CS client: an agent draft can''t carry active_field / customer_seen_at', '%customer_drafts_agent_columns%');
select tests.throws($$insert into public.customer_drafts (agent_id, source, link_expires_at)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', now())$$,
  '#CS client: an agent draft can''t carry link_expires_at', '%customer_drafts_agent_columns%');
-- field validation (keys, strings, caps, object, size)
select tests.throws($$insert into public.customer_drafts (agent_id, source, fields)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', '{"name":"x","pan":"ABCDE1234F"}')$$,
  '#CS validate: an unknown key is refused', '%invalid_fields%');
select tests.throws($$insert into public.customer_drafts (agent_id, source, fields)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', '{"phone":9811100001}')$$,
  '#CS validate: a non-string value is refused', '%invalid_fields%');
select tests.throws($$insert into public.customer_drafts (agent_id, source, fields)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', '{"name":null}')$$,
  '#CS validate: a JSON null value is refused', '%invalid_fields%');
select tests.throws(format($$insert into public.customer_drafts (agent_id, source, fields)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', %L)$$, jsonb_build_object('name', repeat('n', 81))),
  '#CS validate: name over 80 characters is refused', '%invalid_fields%');
select tests.lives(format($$insert into public.customer_drafts (agent_id, source, fields)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', %L)$$,
  jsonb_build_object('name', repeat('न', 80), 'phone', repeat('9', 20), 'email', repeat('e', 120), 'dob', '1990-01-01',
                     'address', repeat('a', 200), 'city', repeat('c', 60), 'nominee', repeat('m', 80), 'notes', repeat('z', 500))),
  '#CS validate: every field exactly at its cap is accepted (80 Devanagari characters count as 80)');
select tests.throws(format($$insert into public.customer_drafts (agent_id, source, fields)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', %L)$$, jsonb_build_object('notes', repeat('z', 501))),
  '#CS validate: notes over 500 is refused', '%invalid_fields%');
select tests.throws(format($$insert into public.customer_drafts (agent_id, source, fields)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', %L)$$, jsonb_build_object('dob', '1990-01-011')),
  '#CS validate: dob over 10 is refused', '%invalid_fields%');
select tests.throws(format($$insert into public.customer_drafts (agent_id, source, fields)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', %L)$$, jsonb_build_object('phone', repeat('9', 21))),
  '#CS validate: phone over 20 is refused', '%invalid_fields%');
select tests.throws($$insert into public.customer_drafts (agent_id, source, fields)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', '["name"]')$$,
  '#CS validate: fields must be an object', '%invalid_fields%');
select tests.throws(format($$insert into public.customer_drafts (agent_id, source, fields)
  values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent', %L)$$,
  jsonb_build_object('name', repeat(U&'\+01F600', 80), 'email', repeat(U&'\+01F600', 120), 'address', repeat(U&'\+01F600', 200),
                     'city', repeat(U&'\+01F600', 60), 'nominee', repeat(U&'\+01F600', 80), 'notes', repeat(U&'\+01F600', 500))),
  '#CS validate: fields over 4096 bytes are refused (CHECK customer_drafts_fields_size)', '%customer_drafts_fields_size%');
-- updates of the agent's own draft
select tests.lives($$update public.customer_drafts set fields = '{"name":"Asha Verma","phone":"9811100001"}'
  where id = '40000000-0000-4000-8000-000000000001'$$, '#CS client: the agent updates its draft''s fields');
select tests.throws($$update public.customer_drafts set fields = '{"name":"x","foo":"y"}' where id = '40000000-0000-4000-8000-000000000001'$$,
  '#CS validate: an update with an unknown key is refused', '%invalid_fields%');
select tests.throws($$update public.customer_drafts set active_field = 'name' where id = '40000000-0000-4000-8000-000000000001'$$,
  '#CS guard: the client can''t set active_field', '%customer_drafts_read_only%');
select tests.throws($$update public.customer_drafts set source = 'customer', link_expires_at = now() + interval '7 days'
  where id = '40000000-0000-4000-8000-000000000001'$$, '#CS guard: the client can''t change source', '%customer_drafts_read_only%');
select tests.throws($$update public.customer_drafts set opened_at = now() where id = '40000000-0000-4000-8000-000000000001'$$,
  '#CS guard: the client can''t set opened_at', '%customer_drafts_read_only%');
select tests.throws($$update public.customer_drafts set created_at = now() - interval '1 year' where id = '40000000-0000-4000-8000-000000000001'$$,
  '#CS guard: the client can''t change created_at', '%customer_drafts_read_only%');
select tests.throws($$update public.customer_drafts set customer_id = (select id from public.customers where name = 'A customer')
  where id = '40000000-0000-4000-8000-000000000001'$$,
  '#CS a customer_id without completed_at is refused', '%customer_drafts_customer_completed%');
select tests.lives($$update public.customer_drafts set completed_at = now(), customer_id = (select id from public.customers where name = 'A customer')
  where id = '40000000-0000-4000-8000-000000000001'$$, '#CS client: the agent completes its draft with the customer id');
select tests.ok((select fields = '{}'::jsonb and completed_at is not null and customer_id is not null from public.customer_drafts
                  where id = '40000000-0000-4000-8000-000000000001'),
  '#CS completing a draft clears its fields (even when the client didn''t send fields = {})');
select tests.lives($$update public.customer_drafts set fields = '{"name":"late autosave"}' where id = '40000000-0000-4000-8000-000000000001'$$,
  '#CS a late autosave on a completed draft is accepted ...');
select tests.ok((select fields from public.customer_drafts where id = '40000000-0000-4000-8000-000000000001') = '{}'::jsonb,
  '#CS ... but stores nothing (a completed draft keeps no values)');
select tests.lives($$update public.customer_drafts set deleted_at = now() where id = '40000000-0000-4000-8000-000000000001'$$,
  '#CS client: soft delete of the agent''s draft');
select tests.throws($$delete from public.customer_drafts$$, '#CS client: hard delete is denied (soft delete only)', '%permission denied%');
reset role;
-- another agent sees and changes nothing
select tests.as_agent(:B);
select tests.ok(tests.count('select 1 from public.customer_drafts') = 0, '#CS RLS: another agent sees no draft');
select tests.ok(tests.affected($$update public.customer_drafts set deleted_at = null$$) = 0, '#CS RLS: another agent updates nothing');
reset role;
select tests.ok((select deleted_at is not null from public.customer_drafts where id = '40000000-0000-4000-8000-000000000001'),
  '#CS RLS: the soft delete is untouched by the other agent');
select tests.as_anon();
select tests.throws($$select * from public.customer_drafts$$, '#CS anon cannot read customer_drafts', '%permission denied%');
select tests.throws($$insert into public.customer_drafts (agent_id, source) values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent')$$,
  '#CS anon cannot insert customer_drafts', '%permission denied%');
select tests.throws($$select * from private.customer_draft_links$$, '#CS anon cannot read the link table', '%permission denied%');
reset role;
select tests.as_agent(:A);
select tests.throws($$select * from private.customer_draft_links$$, '#CS authenticated cannot read the link table', '%permission denied%');
rollback;

-- the guard on a customer-source row: only deleted_at
begin;
select tests.cs_link(:A) ->> 'token' as t \gset
select tests.as_anon();
select tests.cs_consent(:'t');
select tests.cs_save(:'t', '{"name":"Asha"}', 'name');
reset role;
select (tests.cs_draft(:'t')).id as d \gset
select tests.as_agent(:A);
select tests.ok(tests.count(format('select 1 from public.customer_drafts where id = %L and source = ''customer'' and fields = ''{"name":"Asha"}''', :'d')) = 1,
  '#CS the agent sees the customer-source draft and its values (owner select)');
select tests.throws(format($$update public.customer_drafts set fields = '{"name":"Agent edit"}' where id = %L$$, :'d'),
  '#CS guard: the client can''t change a customer row''s fields', '%customer_drafts_read_only%');
select tests.throws(format($$update public.customer_drafts set consent = null where id = %L$$, :'d'),
  '#CS guard: the client can''t change consent', '%customer_drafts_read_only%');
select tests.throws(format($$update public.customer_drafts set link_expires_at = now() + interval '1 year' where id = %L$$, :'d'),
  '#CS guard: the client can''t extend link_expires_at', '%customer_drafts_read_only%');
select tests.throws(format($$update public.customer_drafts set customer_seen_at = now() - interval '1 hour' where id = %L$$, :'d'),
  '#CS guard: the client can''t set customer_seen_at', '%customer_drafts_read_only%');
select tests.throws(format($$update public.customer_drafts set active_field = 'phone' where id = %L$$, :'d'),
  '#CS guard: the client can''t set active_field on a customer row', '%customer_drafts_read_only%');
select tests.throws(format($$update public.customer_drafts set completed_at = now() where id = %L$$, :'d'),
  '#CS guard: the client can''t complete a customer row', '%customer_drafts_read_only%');
select tests.throws(format($$update public.customer_drafts set source = 'agent' where id = %L$$, :'d'),
  '#CS guard: the client can''t turn a customer row into an agent row', '%customer_drafts_read_only%');
select tests.lives(format($$update public.customer_drafts set deleted_at = now() where id = %L$$, :'d'),
  '#CS client: soft delete of a customer-source draft');
select tests.lives(format($$update public.customer_drafts set deleted_at = null where id = %L$$, :'d'),
  '#CS client: Undo of that soft delete');
-- forgery attempts: claims that say service_role, plausible GUC names and the gate header change nothing
select set_config('request.jwt.claims', json_build_object('sub', 'aaaaaaaa-0000-4000-8000-00000000000a', 'role', 'service_role')::text, true);
select set_config('agentpro.signup_rpc', 'on', true), set_config('agentpro.trusted', 'on', true),
       set_config('app.customer_drafts_trusted', 'true', true);
select tests.throws(format($$update public.customer_drafts set fields = '{"name":"forged"}' where id = %L$$, :'d'),
  '#CS guard bypass: service_role claims and custom GUCs set by the client are ignored (current_user decides)', '%customer_drafts_read_only%');
reset role;
select tests.ok((tests.cs_draft(:'t')).fields = '{"name":"Asha"}'::jsonb, '#CS guard bypass: the values are unchanged');
-- The PostgREST path: a non-superuser session (like `authenticator`) that switched to authenticated can't become
-- the owner, and the trusted path needs current_user = owner, a superuser or service_role.
create role cs_authenticator nologin noinherit;
grant anon, authenticated to cs_authenticator;
set local session authorization cs_authenticator;
set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub', 'aaaaaaaa-0000-4000-8000-00000000000a', 'role', 'authenticated')::text, true);
select tests.throws($$set local role postgres$$, '#CS guard bypass: the API session can''t switch to the owner role', '%permission denied%');
select tests.throws($$select set_config('role', 'postgres', true)$$, '#CS guard bypass: nor with set_config(''role'')', '%permission denied%');
select tests.throws($$set local role service_role$$, '#CS guard bypass: nor to service_role (not granted to the API session)', '%permission denied%');
select tests.throws(format($$update public.customer_drafts set consent = '{"notice_key":"forged"}' where id = %L$$, :'d'),
  '#CS guard bypass: from the API session the customer row stays read-only', '%customer_drafts_read_only%');
select tests.ok(current_user = 'authenticated' and session_user = 'cs_authenticator', '#CS guard bypass: ran as authenticated in a non-superuser session');
rollback;

-- trusted writes (the service role) and the validate trigger for everyone
begin;
select tests.cs_link(:A) ->> 'token' as t \gset
select (tests.cs_draft(:'t')).id as d \gset
set local role service_role;
select tests.lives(format($$update public.customer_drafts set active_field = 'city' where id = %L$$, :'d'),
  '#CS the service role may write the customer-only columns');
select tests.throws(format($$update public.customer_drafts set fields = '{"notes":"x"}' where id = %L$$, :'d'),
  '#CS validate applies to trusted writers too: no notes on a customer-source row', '%invalid_fields%');
reset role;
select tests.throws(format($$update public.customer_drafts set fields = '{"notes":"x"}' where id = %L$$, :'d'),
  '#CS validate applies to the owner too (no notes on a customer row)', '%invalid_fields%');
select tests.throws(format($$update public.customer_drafts set active_field = 'notes' where id = %L$$, :'d'),
  '#CS active_field can never be notes', '%customer_drafts_active_field_values%');
rollback;

-- a lapsed agent can't write drafts (can_write)
begin;
insert into public.customer_drafts (id, agent_id, source) values ('40000000-0000-4000-8000-000000000002', :A, 'agent');
update public.subscriptions set trial_ends_at = now() - interval '100 days', current_period_end = null where agent_id = :A;
select tests.as_agent(:A);
select tests.throws($$insert into public.customer_drafts (agent_id, source) values ('aaaaaaaa-0000-4000-8000-00000000000a', 'agent')$$,
  '#CS lapsed: insert refused (can_write)', '%row-level security%');
select tests.ok(tests.affected($$update public.customer_drafts set deleted_at = now()$$) = 0, '#CS lapsed: updates change nothing (can_write)');
select tests.ok(tests.count('select 1 from public.customer_drafts') = 1, '#CS lapsed: the agent can still read its drafts');
select tests.throws($$select public.customer_signup_link()$$, '#CS lapsed: customer_signup_link -> plan_read_only', '%plan_read_only%');
select tests.throws($$select public.customer_signup_renew(gen_random_uuid())$$, '#CS lapsed: customer_signup_renew -> plan_read_only', '%plan_read_only%');
rollback;

-- ===========================================================================
-- 3. customer_signup_link
-- ===========================================================================
begin;
select tests.as_agent(:A);
select public.customer_signup_link() as l1 \gset
select tests.ok(:'l1'::jsonb ->> 'ok' = 'true' and :'l1'::jsonb ->> 'token' ~ '^[A-Za-z0-9_-]{43}$'
  and (:'l1'::jsonb ->> 'draft_id')::uuid is not null
  and (:'l1'::jsonb ->> 'expires_at')::timestamptz between now() + interval '6 days 23 hours' and now() + interval '7 days 1 hour'
  and (select array_agg(k order by k) from jsonb_object_keys(:'l1'::jsonb) k) = array['draft_id', 'expires_at', 'ok', 'token'],
  '#CS link: {ok, draft_id, token (43-char base64url), expires_at in 7 days}', :'l1');
select tests.ok(tests.count(format($$select 1 from public.customer_drafts where id = %L and source = 'customer' and fields = '{}'
  and consent is null and opened_at is null and completed_at is null and link_expires_at = %L::timestamptz$$,
  :'l1'::jsonb ->> 'draft_id', :'l1'::jsonb ->> 'expires_at')) = 1,
  '#CS link: the agent sees the new customer-source draft (empty, link_expires_at = expires_at)');
select tests.ok(not exists (select 1 from public.customer_drafts d where position(:'l1'::jsonb ->> 'token' in to_jsonb(d)::text) > 0),
  '#CS link: the token is not in any row the agent can select');
reset role;
select tests.ok((select l.draft_id::text from private.customer_draft_links l
                  where l.token_hash = sha256(convert_to(:'l1'::jsonb ->> 'token', 'UTF8'))) = :'l1'::jsonb ->> 'draft_id',
  '#CS link: the private table stores sha256(token) for the draft');
select tests.ok(not exists (
  select 1 from (select to_jsonb(t)::text s from public.customer_drafts t
                 union all select to_jsonb(t)::text from private.customer_draft_links t
                 union all select to_jsonb(t)::text from public.audit_log t) x
   where position(:'l1'::jsonb ->> 'token' in x.s) > 0),
  '#CS link: the plaintext token is stored nowhere (drafts, links, audit_log)');
select tests.as_agent(:A);
select public.customer_signup_link() as l2 \gset
select tests.ok(:'l2'::jsonb ->> 'token' <> :'l1'::jsonb ->> 'token' and :'l2'::jsonb ->> 'draft_id' <> :'l1'::jsonb ->> 'draft_id',
  '#CS link: each call makes a new draft and a new token');
reset role;
-- B: unpublished site
select tests.as_agent(:B);
select tests.ok(public.customer_signup_link() = '{"ok": false, "error": "no_site"}'::jsonb, '#CS link: no published site -> no_site');
reset role;
select tests.ok(not exists (select 1 from public.customer_drafts where agent_id = :B), '#CS link: no_site creates nothing');
-- not signed in
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
set local role authenticated;
select tests.throws($$select public.customer_signup_link()$$, '#CS link: no user id -> not_authenticated', '%not_authenticated%');
reset role;
select tests.as_anon();
select tests.throws($$select public.customer_signup_link()$$, '#CS link: anon cannot call it', '%permission denied%');
select tests.throws($$select public.customer_signup_renew(gen_random_uuid())$$, '#CS renew: anon cannot call it', '%permission denied%');
reset role;
select tests.as_agent(:A);
select tests.throws($$select public.signup_open('ramesh-sharma', null, 'x')$$, '#CS authenticated cannot call signup_open', '%permission denied%');
select tests.throws($$select public.signup_submit('ramesh-sharma', null, 'x', '{}', null)$$, '#CS authenticated cannot call signup_submit', '%permission denied%');
rollback;

begin;
update public.sites set is_published = false where agent_id = :A;
select tests.as_agent(:A);
select tests.ok(public.customer_signup_link() = '{"ok": false, "error": "no_site"}'::jsonb, '#CS link: site unpublished -> no_site');
reset role;
update public.sites set is_published = true, deleted_at = now() where agent_id = :A;
select tests.as_agent(:A);
select tests.ok(public.customer_signup_link() = '{"ok": false, "error": "no_site"}'::jsonb, '#CS link: site deleted -> no_site');
rollback;

begin;
update public.app_config set value = jsonb_set(value, '{customerSignup}', 'false') where key = 'flag_overrides';
select tests.as_agent(:A);
select tests.ok(public.customer_signup_link() = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#CS link: flag customerSignup off (flag_overrides) -> unavailable');
reset role;
select tests.ok(not exists (select 1 from public.customer_drafts where agent_id = :A), '#CS link: unavailable creates nothing');
rollback;

-- too_many: openLinksPerAgent (25) live links; expired, deleted and completed ones don't count
begin;
select tests.as_agent(:A);
select count(*) filter (where public.customer_signup_link() ->> 'ok' = 'true') as made from generate_series(1, 25) \gset
select tests.ok(:made = 25, '#CS too_many: 25 links are made', :'made');
select public.customer_signup_link() as l26 \gset
select tests.ok(:'l26'::jsonb = '{"ok": false, "error": "too_many"}'::jsonb, '#CS too_many: the 26th live link is refused', :'l26');
reset role;
select tests.ok((select count(*) from public.customer_drafts where agent_id = :A and source = 'customer') = 25, '#CS too_many: nothing created on refusal');
update public.customer_drafts set link_expires_at = now() - interval '1 second'
 where id = (select id from public.customer_drafts where agent_id = :A order by created_at, id limit 1);
select tests.as_agent(:A);
select tests.ok(public.customer_signup_link() ->> 'ok' = 'true', '#CS too_many: an expired link frees a slot');
select tests.ok(public.customer_signup_link() = '{"ok": false, "error": "too_many"}'::jsonb, '#CS too_many: full again');
reset role;
update public.customer_drafts set deleted_at = now()
 where id = (select id from public.customer_drafts where agent_id = :A and deleted_at is null and link_expires_at > now() order by id limit 1);
select tests.as_agent(:A);
select tests.ok(public.customer_signup_link() ->> 'ok' = 'true', '#CS too_many: a deleted draft frees a slot');
reset role;
update public.customer_drafts set completed_at = now()
 where id = (select id from public.customer_drafts where agent_id = :A and deleted_at is null and completed_at is null and link_expires_at > now() order by id limit 1);
select tests.as_agent(:A);
select tests.ok(public.customer_signup_link() ->> 'ok' = 'true', '#CS too_many: a completed draft frees a slot');
select tests.as_agent(:A);
insert into public.customer_drafts (agent_id, source) values (:A, 'agent');
select tests.ok(public.customer_signup_link() = '{"ok": false, "error": "too_many"}'::jsonb, '#CS too_many: agent-source drafts don''t count, the cap still holds');
rollback;

-- ===========================================================================
-- 4. The Worker gate, availability, invalid links
-- ===========================================================================
begin;
select tests.cs_link(:A) ->> 'token' as t \gset
select tests.as_anon();
select tests.cs_header(null);
select tests.ok(tests.cs_open(:'t') = '{"ok": false, "error": "unavailable"}'::jsonb
  and tests.cs_consent(:'t') = '{"ok": false, "error": "unavailable"}'::jsonb
  and tests.cs_save(:'t', '{"name":"x"}') = '{"ok": false, "error": "unavailable"}'::jsonb
  and tests.cs_submit(:'t', '{"name":"x","phone":"9811100001"}') = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#CS gate: no x-portal-key header -> every anon RPC answers unavailable');
select tests.cs_header('wrong-key');
select tests.ok(tests.cs_open(:'t') = '{"ok": false, "error": "unavailable"}'::jsonb
  and tests.cs_submit(:'t', '{"name":"x","phone":"9811100001"}') = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#CS gate: a wrong key -> unavailable');
select tests.cs_header('');
select tests.ok(tests.cs_open(:'t') = '{"ok": false, "error": "unavailable"}'::jsonb, '#CS gate: an empty key -> unavailable');
select set_config('request.headers', 'not json', true);
select tests.ok(tests.cs_open(:'t') = '{"ok": false, "error": "unavailable"}'::jsonb, '#CS gate: headers that aren''t JSON -> unavailable');
reset role;
select tests.ok((select opened_at is null and customer_seen_at is null and consent is null from tests.cs_draft(:'t'))
  and not exists (select 1 from public.customers where agent_id = :A and name = 'x'),
  '#CS gate: refused calls write nothing');
delete from private.portal_gate;
select tests.cs_header('test-portal-key');
select tests.as_anon();
select tests.ok(tests.cs_open(:'t') = '{"ok": false, "error": "unavailable"}'::jsonb, '#CS gate: no gate row -> unavailable even with the header');
rollback;

begin;
select tests.cs_link(:A) ->> 'token' as t \gset
update public.app_config set value = jsonb_set(value, '{customerSignup}', 'false') where key = 'flag_overrides';
select tests.as_anon();
select tests.ok(tests.cs_open(:'t') = '{"ok": false, "error": "unavailable"}'::jsonb
  and tests.cs_consent(:'t') = '{"ok": false, "error": "unavailable"}'::jsonb
  and tests.cs_save(:'t', '{}') = '{"ok": false, "error": "unavailable"}'::jsonb
  and tests.cs_submit(:'t', '{"name":"x","phone":"9811100001"}') = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#CS flag customerSignup off -> unavailable for every anon RPC');
rollback;

begin;
select tests.cs_link(:A) ->> 'token' as t \gset
update public.sites set is_published = false where agent_id = :A;
select tests.as_anon();
select tests.ok(tests.cs_open(:'t') = '{"ok": false, "error": "unavailable"}'::jsonb
  and tests.cs_submit(:'t', '{"name":"x","phone":"9811100001"}') = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#CS site unpublished -> unavailable');
rollback;

begin;
select tests.cs_link(:A) ->> 'token' as t \gset
update public.subscriptions set trial_ends_at = now() - interval '100 days', current_period_end = null where agent_id = :A;
select tests.as_anon();
select tests.ok(tests.cs_open(:'t') = '{"ok": false, "error": "unavailable"}'::jsonb
  and tests.cs_save(:'t', '{}') = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#CS owner lapsed -> unavailable');
reset role;
update public.subscriptions set trial_ends_at = now() - interval '5 days', current_period_end = null where agent_id = :A;
select tests.as_anon();
select tests.ok(tests.cs_open(:'t') ->> 'ok' = 'true', '#CS owner in grace -> the link still works');
rollback;

begin;
select tests.cs_link(:A) ->> 'token' as t \gset
insert into public.site_domains (agent_id, site_id, hostname, kind, status, expires_at)
  select :A, id, 'join.ramesh.example.in', 'custom', 'active', now() + interval '300 days' from public.sites where agent_id = :A;
select tests.as_anon();
select tests.ok(tests.cs_open(:'t', null, 'Join.Ramesh.Example.in') ->> 'ok' = 'true', '#CS the site resolves by custom domain (like submit_enquiry)');
select tests.ok(tests.cs_open(:'t', 'chetan-lal') = '{"ok": false, "error": "invalid_link"}'::jsonb,
  '#CS a token used on another agent''s site -> invalid_link');
select tests.ok(tests.cs_open(:'t', 'no-such-site') = '{"ok": false, "error": "unavailable"}'::jsonb, '#CS unknown slug -> unavailable');
select tests.ok(tests.cs_open(:'t', 'sunita-rao') = '{"ok": false, "error": "unavailable"}'::jsonb, '#CS another agent''s unpublished site -> unavailable');
select tests.ok(tests.cs_open('not-a-token') = '{"ok": false, "error": "invalid_link"}'::jsonb
  and tests.cs_open(null) = '{"ok": false, "error": "invalid_link"}'::jsonb
  and tests.cs_open(repeat('A', 43)) = '{"ok": false, "error": "invalid_link"}'::jsonb
  and tests.cs_open(:'t' || 'x') = '{"ok": false, "error": "invalid_link"}'::jsonb,
  '#CS a malformed, null, unknown or altered token -> invalid_link');
select tests.ok(public.signup_consent('ramesh-sharma', null, 'junk', null) = '{"ok": false, "error": "invalid_link"}'::jsonb
  and public.signup_save('ramesh-sharma', null, 'junk', null, null) = '{"ok": false, "error": "invalid_link"}'::jsonb
  and public.signup_submit('ramesh-sharma', null, 'junk', null, null) = '{"ok": false, "error": "invalid_link"}'::jsonb
  and public.signup_open(null, null, null) = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#CS null / junk arguments never raise');
rollback;

-- expired, deleted, completed, old -> invalid_link
begin;
select tests.cs_link(:A) ->> 'token' as t1 \gset
select tests.cs_link(:A) ->> 'token' as t2 \gset
select tests.cs_link(:A) ->> 'token' as t3 \gset
select tests.cs_link(:A) ->> 'token' as t4 \gset
select tests.cs_link(:A) ->> 'token' as t5 \gset
update public.customer_drafts set link_expires_at = now() where id = (tests.cs_draft(:'t1')).id;
update public.customer_drafts set deleted_at = now() where id = (tests.cs_draft(:'t2')).id;
update public.customer_drafts set completed_at = now() where id = (tests.cs_draft(:'t3')).id;
select tests.cs_age((tests.cs_draft(:'t4')).id, interval '30 days 1 second');
select tests.cs_age((tests.cs_draft(:'t5')).id, interval '29 days 23 hours');
select tests.as_anon();
select tests.ok(tests.cs_open(:'t1') = '{"ok": false, "error": "invalid_link"}'::jsonb
  and tests.cs_save(:'t1', '{}') = '{"ok": false, "error": "invalid_link"}'::jsonb, '#CS link expired (link_expires_at = now) -> invalid_link');
select tests.ok(tests.cs_open(:'t2') = '{"ok": false, "error": "invalid_link"}'::jsonb
  and tests.cs_consent(:'t2') = '{"ok": false, "error": "invalid_link"}'::jsonb, '#CS draft soft-deleted by the agent -> invalid_link');
select tests.ok(tests.cs_open(:'t3') = '{"ok": false, "error": "invalid_link"}'::jsonb
  and tests.cs_submit(:'t3', '{"name":"x","phone":"9811100001"}') = '{"ok": false, "error": "invalid_link"}'::jsonb, '#CS draft completed -> invalid_link');
select tests.ok(tests.cs_open(:'t4') = '{"ok": false, "error": "invalid_link"}'::jsonb, '#CS draft idle longer than customerDraftsDays -> invalid_link');
select tests.ok(tests.cs_open(:'t5') ->> 'ok' = 'true', '#CS draft idle just under customerDraftsDays still works');
reset role;
-- Undo of the soft delete brings the link back
update public.customer_drafts set deleted_at = null where id = (tests.cs_draft(:'t2')).id;
select tests.as_anon();
select tests.ok(tests.cs_open(:'t2') ->> 'ok' = 'true', '#CS Undo of the agent''s delete makes the link work again');
rollback;

-- ===========================================================================
-- 5. signup_open and signup_consent
-- ===========================================================================
begin;
select tests.cs_link(:A) ->> 'token' as t \gset
select tests.as_anon();
select tests.cs_open(:'t') as o1 \gset
select tests.ok(:'o1'::jsonb ->> 'ok' = 'true' and :'o1'::jsonb -> 'fields' = '{}'::jsonb and :'o1'::jsonb -> 'consented' = 'false'::jsonb
  and (:'o1'::jsonb ->> 'expires_at')::timestamptz = (tests.cs_draft(:'t')).link_expires_at
  and (select array_agg(k order by k) from jsonb_object_keys(:'o1'::jsonb) k) = array['consented', 'expires_at', 'fields', 'ok'],
  '#CS open: {ok, fields:{}, consented:false, expires_at}', :'o1');
reset role;
select tests.ok((select opened_at = now() and customer_seen_at = now() from tests.cs_draft(:'t')), '#CS open: sets opened_at and customer_seen_at');
update public.customer_drafts set opened_at = now() - interval '1 hour', customer_seen_at = now() - interval '1 hour' where id = (tests.cs_draft(:'t')).id;
select tests.as_anon();
select tests.cs_open(:'t');
reset role;
select tests.ok((select opened_at = now() - interval '1 hour' and customer_seen_at = now() from tests.cs_draft(:'t')),
  '#CS open again: opened_at kept (first open), customer_seen_at moved');
-- before consent nothing can be stored
select tests.as_anon();
select tests.ok(tests.cs_save(:'t', '{"name":"Asha","phone":"9811100001"}', 'name') = '{"ok": false, "error": "consent_required"}'::jsonb,
  '#CS save before consent -> consent_required');
select tests.ok(tests.cs_submit(:'t', '{"name":"Asha","phone":"9811100001"}') = '{"ok": false, "error": "consent_required"}'::jsonb,
  '#CS submit before consent -> consent_required');
reset role;
select tests.ok((select fields = '{}'::jsonb and active_field is null from tests.cs_draft(:'t'))
  and not exists (select 1 from public.customers where name = 'Asha'),
  '#CS before consent: no field stored, no customer created');
-- consent payloads
select tests.as_anon();
select tests.ok(public.signup_consent('ramesh-sharma', null, :'t', null) = '{"ok": false, "error": "invalid", "field": "consent"}'::jsonb
  and public.signup_consent('ramesh-sharma', null, :'t', tests.consent('customer_portal')) = '{"ok": false, "error": "invalid", "field": "consent"}'::jsonb
  and public.signup_consent('ramesh-sharma', null, :'t', tests.consent('customer_signup_guardian')) = '{"ok": false, "error": "invalid", "field": "consent"}'::jsonb
  and public.signup_consent('ramesh-sharma', null, :'t', tests.consent('customer_signup') || '{"hash":"0000000000000000000000000000000000000000000000000000000000000000"}') = '{"ok": false, "error": "invalid", "field": "consent"}'::jsonb
  and public.signup_consent('ramesh-sharma', null, :'t', tests.consent('customer_signup') || '{"version":"1"}') = '{"ok": false, "error": "invalid", "field": "consent"}'::jsonb
  and public.signup_consent('ramesh-sharma', null, :'t', tests.consent('customer_signup') || '{"version":2}') = '{"ok": false, "error": "invalid", "field": "consent"}'::jsonb
  and public.signup_consent('ramesh-sharma', null, :'t', '"yes"') = '{"ok": false, "error": "invalid", "field": "consent"}'::jsonb,
  '#CS consent: null, another notice (portal, guardian), a wrong hash, a string or unknown version, a non-object -> invalid/consent');
reset role;
select tests.ok((tests.cs_draft(:'t')).consent is null, '#CS consent: refused payloads store nothing');
select tests.as_anon();
select tests.ok(tests.cs_consent(:'t') = '{"ok": true}'::jsonb, '#CS consent: valid -> ok');
reset role;
select tests.ok((select consent ->> 'notice_key' = 'customer_signup'
                   and (consent ->> 'version')::int = (public.catalog_privacy_notice('customer_signup') ->> 'version')::int
                   and consent ->> 'hash' = public.catalog_privacy_notice('customer_signup') ->> 'hash_en'
                   and consent ->> 'locale' = 'en' and (consent ->> 'given_at')::timestamptz = now()
                   and (select array_agg(k order by k) from jsonb_object_keys(consent) k) = array['given_at', 'hash', 'locale', 'notice_key', 'version']
                  from tests.cs_draft(:'t')),
  '#CS consent: stored {notice_key, version, hash, locale (from the hash), given_at}', (tests.cs_draft(:'t')).consent::text);
update public.customer_drafts set consent = jsonb_set(consent, '{given_at}', to_jsonb(now() - interval '1 hour')) where id = (tests.cs_draft(:'t')).id;
select tests.as_anon();
select tests.ok(tests.cs_consent(:'t') = '{"ok": true}'::jsonb
  and public.signup_consent('ramesh-sharma', null, :'t', tests.consent('customer_signup', 'hi')) = '{"ok": true}'::jsonb,
  '#CS consent: idempotent (again in EN or HI -> ok)');
reset role;
select tests.ok((select (consent ->> 'given_at')::timestamptz = now() - interval '1 hour' and consent ->> 'locale' = 'en' from tests.cs_draft(:'t')),
  '#CS consent: the first consent is kept (given_at and locale unchanged)');
select tests.as_anon();
select tests.cs_save(:'t', '{"name":"Asha Verma","city":"Pune"}', 'city');
select tests.cs_open(:'t') as o2 \gset
select tests.ok(:'o2'::jsonb -> 'fields' = '{"name":"Asha Verma","city":"Pune"}'::jsonb and :'o2'::jsonb -> 'consented' = 'true'::jsonb,
  '#CS open after consent: returns the saved values (a reopened link resumes)', :'o2');
rollback;

begin;
select tests.cs_link(:A) ->> 'token' as t \gset
select tests.as_anon();
select tests.ok(public.signup_consent('ramesh-sharma', null, :'t', tests.consent('customer_signup', 'hi')) = '{"ok": true}'::jsonb,
  '#CS consent: the Hindi text''s hash is accepted');
reset role;
select tests.ok((tests.cs_draft(:'t')).consent ->> 'locale' = 'hi', '#CS consent: locale hi resolved from the hash');
rollback;

-- ===========================================================================
-- 6. signup_save
-- ===========================================================================
begin;
select tests.cs_link(:A) ->> 'token' as t \gset
select tests.as_anon();
select tests.cs_consent(:'t');
select tests.ok(tests.cs_save(:'t', '{"name":"Asha"}', 'name') = '{"ok": true}'::jsonb, '#CS save: ok');
reset role;
select tests.ok((select fields = '{"name":"Asha"}'::jsonb and active_field = 'name' and customer_seen_at = now() from tests.cs_draft(:'t')),
  '#CS save: the value, active_field and customer_seen_at stored');
select tests.as_anon();
select tests.cs_save(:'t', '{"phone":"98111 00001"}', 'phone');
reset role;
select tests.ok((tests.cs_draft(:'t')).fields = '{"name":"Asha","phone":"98111 00001"}'::jsonb,
  '#CS save: partial: only the keys sent are replaced (name kept, phone added, typed as is)');
select tests.as_anon();
select tests.cs_save(:'t', '{"name":"Asha Verma","phone":""}', 'name');
reset role;
select tests.ok((tests.cs_draft(:'t')).fields = '{"name":"Asha Verma"}'::jsonb, '#CS save: "" clears a key');
select tests.as_anon();
select tests.cs_save(:'t', '{"notes":"x","pan":"ABCDE1234F","agent_id":"x","email":5,"city":null,"address":{"a":1},"dob":["1990"],"nominee":true}', 'name');
reset role;
select tests.ok((tests.cs_draft(:'t')).fields = '{"name":"Asha Verma"}'::jsonb,
  '#CS save: unknown keys (notes, pan, agent_id) and non-string values (number, null, object, array, boolean) are dropped');
select tests.as_anon();
select tests.ok(tests.cs_save(:'t', jsonb_build_object('name', repeat('n', 100), 'address', repeat('a', 300), 'email', repeat('e', 150),
                  'phone', repeat('9', 30), 'dob', '1990-01-01T00:00', 'city', repeat('c', 70), 'nominee', repeat('m', 90))) = '{"ok": true}'::jsonb,
  '#CS save: over-long values -> still ok (live autosave)');
reset role;
select tests.ok((tests.cs_draft(:'t')).fields = jsonb_build_object('name', repeat('n', 80), 'address', repeat('a', 200), 'email', repeat('e', 120),
                  'phone', repeat('9', 20), 'dob', '1990-01-01', 'city', repeat('c', 60), 'nominee', repeat('m', 80)),
  '#CS save: values truncated to the caps (name 80, phone 20, email 120, dob 10, address 200, city 60, nominee 80)');
select tests.ok((tests.cs_draft(:'t')).active_field is null, '#CS save: active_field null when not sent');
select tests.as_anon();
select tests.cs_save(:'t', '{}', 'notes');
reset role;
select tests.ok((tests.cs_draft(:'t')).active_field is null, '#CS save: active_field notes -> stored null');
select tests.as_anon();
select tests.cs_save(:'t', '{}', 'bogus');
reset role;
select tests.ok((tests.cs_draft(:'t')).active_field is null, '#CS save: an unknown active_field -> stored null');
select tests.as_anon();
select tests.cs_save(:'t', '{}', 'nominee');
reset role;
select tests.ok((tests.cs_draft(:'t')).active_field = 'nominee', '#CS save: each field key is a valid active_field (nominee)');
update public.customer_drafts set customer_seen_at = now() - interval '1 minute' where id = (tests.cs_draft(:'t')).id;
select (tests.cs_draft(:'t')).fields::text as before \gset
select tests.as_anon();
select tests.ok(tests.cs_save(:'t', '{}', 'nominee') = '{"ok": true}'::jsonb
  and public.signup_save('ramesh-sharma', null, :'t', null, 'nominee') = '{"ok": true}'::jsonb
  and public.signup_save('ramesh-sharma', null, :'t', '"name"', 'nominee') = '{"ok": true}'::jsonb
  and public.signup_save('ramesh-sharma', null, :'t', '[1,2]', 'nominee') = '{"ok": true}'::jsonb,
  '#CS save: heartbeat ({}, null or a non-object fields) -> ok');
reset role;
select tests.ok((select fields::text = :'before' and customer_seen_at = now() from tests.cs_draft(:'t')),
  '#CS heartbeat: values unchanged, customer_seen_at moved (the agent sees "On the form now")');
rollback;

-- rate limit: savesPerMinute (60) per draft per minute, submits included
begin;
select tests.cs_link(:A) ->> 'token' as t \gset
select tests.cs_link(:A) ->> 'token' as u \gset
select tests.as_anon();
select tests.cs_consent(:'t');
select tests.cs_consent(:'u');
select count(*) filter (where tests.cs_save(:'t', jsonb_build_object('city', 'c' || i)) = '{"ok": true}'::jsonb) as oks
  from generate_series(1, 60) i \gset
select tests.ok(:oks = 60, '#CS rate limit: 60 saves in a minute are ok', :'oks');
select tests.ok(tests.cs_save(:'t', '{"city":"over"}') = '{"ok": false, "error": "rate_limited"}'::jsonb, '#CS rate limit: the 61st -> rate_limited');
select tests.ok(tests.cs_submit(:'t', '{"name":"Asha","phone":"9811100001"}') = '{"ok": false, "error": "rate_limited"}'::jsonb,
  '#CS rate limit: submit counts with the saves -> rate_limited');
select tests.ok(tests.cs_save(:'u', '{"city":"other"}') = '{"ok": true}'::jsonb, '#CS rate limit: per draft (another link is unaffected)');
select tests.ok(tests.cs_open(:'t') ->> 'ok' = 'true', '#CS rate limit: open isn''t limited');
reset role;
select tests.ok((tests.cs_draft(:'t')).fields = '{"city":"c60"}'::jsonb and not exists (select 1 from public.customers where name = 'Asha'),
  '#CS rate limit: refused calls store nothing');
update private.customer_draft_links set save_window_started_at = now() - interval '61 seconds' where draft_id = (tests.cs_draft(:'t')).id;
select tests.as_anon();
select tests.ok(tests.cs_save(:'t', '{"city":"after"}') = '{"ok": true}'::jsonb, '#CS rate limit: a new minute -> ok again');
reset role;
select tests.ok((select save_count = 1 and save_window_started_at = now() from private.customer_draft_links where draft_id = (tests.cs_draft(:'t')).id),
  '#CS rate limit: the new window starts at the first call');
rollback;

-- ===========================================================================
-- 7. signup_submit
-- ===========================================================================
begin;
select tests.cs_link(:A) ->> 'token' as t \gset
select to_char(private.today_ist() + 1, 'YYYY-MM-DD') as future,
       to_char((private.today_ist() - interval '18 years')::date, 'YYYY-MM-DD') as dob18,
       to_char((private.today_ist() - interval '18 years')::date + 1, 'YYYY-MM-DD') as dob17 \gset
create temp table cs_cases (n int, f jsonb, field text);
grant all on cs_cases to anon;
insert into cs_cases values
  (1,  '{"phone":"9811100001"}', 'name'),
  (2,  '{"name":"   ","phone":"9811100001"}', 'name'),
  (3,  jsonb_build_object('name', repeat('n', 81), 'phone', '9811100001'), 'name'),
  (4,  '{"name":"Asha"}', 'phone'),
  (5,  '{"name":"Asha","phone":"12345"}', 'phone'),
  (6,  '{"name":"Asha","phone":"5811100001"}', 'phone'),
  (7,  '{"name":"Asha","phone":"9811100001","email":"not-an-email"}', 'email'),
  (8,  jsonb_build_object('name', 'Asha', 'phone', '9811100001', 'email', repeat('e', 110) || '@example.com'), 'email'),
  (9,  '{"name":"Asha","phone":"9811100001","dob":"1990-13-01"}', 'dob'),
  (10, '{"name":"Asha","phone":"9811100001","dob":"12/04/1990"}', 'dob'),
  (11, '{"name":"Asha","phone":"9811100001","dob":"1990-02-30"}', 'dob'),
  (12, jsonb_build_object('name', 'Asha', 'phone', '9811100001', 'dob', :'future'), 'dob'),
  (13, '{"name":"Asha","phone":"9811100001","dob":"1899-12-31"}', 'dob'),
  (14, jsonb_build_object('name', 'Asha', 'phone', '9811100001', 'address', repeat('a', 201)), 'address'),
  (15, jsonb_build_object('name', 'Asha', 'phone', '9811100001', 'city', repeat('c', 61)), 'city'),
  (16, jsonb_build_object('name', 'Asha', 'phone', '9811100001', 'nominee', repeat('m', 81)), 'nominee'),
  (17, '{"phone":"12345","email":"bad"}', 'name'),
  (18, '{"name":"Asha","phone":"12345","email":"bad"}', 'phone'),
  (19, jsonb_build_object('name', 'Asha', 'phone', '9811100001', 'dob', :'dob17'), 'guardian');
select tests.as_anon();
select tests.cs_consent(:'t');
select tests.ok(not exists (
    select 1 from cs_cases c
     where tests.cs_submit(:'t', '{"name":"","phone":"","email":"","dob":"","address":"","city":"","nominee":""}'::jsonb || c.f)
           is distinct from jsonb_build_object('ok', false, 'error', 'invalid', 'field', c.field)),
  '#CS submit validation: name (missing, blank, > 80), phone (missing, short, not a mobile), email (format, > 120), dob (month 13, '
  || 'not ISO, 30 Feb, future, before 1900), address > 200, city > 60, nominee > 80, first failure wins, under 18 without guardian',
  (select string_agg(c.n || '=' || tests.cs_submit(:'t', '{"name":"","phone":"","email":"","dob":"","address":"","city":"","nominee":""}'::jsonb || c.f)::text, '; ')
     from cs_cases c));
select tests.ok(tests.cs_submit(:'t', jsonb_build_object('name', 'Asha', 'phone', '9811100001', 'dob', :'dob17'), tests.consent('customer_signup')) = '{"ok": false, "error": "invalid", "field": "guardian"}'::jsonb
  and tests.cs_submit(:'t', jsonb_build_object('name', 'Asha', 'phone', '9811100001', 'dob', :'dob17'), tests.consent('customer_signup_guardian') || '{"hash":"0000000000000000000000000000000000000000000000000000000000000000"}') = '{"ok": false, "error": "invalid", "field": "guardian"}'::jsonb
  and tests.cs_submit(:'t', jsonb_build_object('name', 'Asha', 'phone', '9811100001', 'dob', :'dob17'), '"yes"') = '{"ok": false, "error": "invalid", "field": "guardian"}'::jsonb,
  '#CS submit: a minor with the customer notice, a wrong hash or junk as guardian -> invalid/guardian');
reset role;
select tests.ok(not exists (select 1 from public.customers where agent_id = :A and name like 'Asha%')
  and not exists (select 1 from public.consents where notice_key in ('customer_signup', 'customer_signup_guardian'))
  and (tests.cs_draft(:'t')).completed_at is null,
  '#CS submit: refused submits create no customer, no consent and don''t complete the draft');
select tests.ok((tests.cs_draft(:'t')).fields = jsonb_build_object('name', 'Asha', 'phone', '9811100001', 'dob', :'dob17'),
  '#CS submit: a refused submit saves the values sent (like signup_save: "" cleared keys, so nothing typed is lost)',
  (tests.cs_draft(:'t')).fields::text);
rollback;

-- success (adult), merged with the saved values
begin;
select tests.cs_link(:A) ->> 'token' as t \gset
select (tests.cs_draft(:'t')).id as d \gset
select to_char((private.today_ist() - interval '18 years')::date, 'YYYY-MM-DD') as dob18 \gset
select tests.as_anon();
select tests.cs_consent(:'t');
select tests.cs_save(:'t', jsonb_build_object('name', 'Old Name', 'city', '  Pune ', 'nominee', 'Ravi Verma', 'address', '12 Park Road'), 'city');
reset role;
update public.customer_drafts set consent = jsonb_set(consent, '{given_at}', to_jsonb(now() - interval '10 minutes')) where id = :'d';
select tests.as_anon();
select tests.cs_submit(:'t', jsonb_build_object('name', '  Asha Verma ', 'phone', '+91 98111-00001', 'email', ' Asha@Example.COM ',
                         'dob', :'dob18', 'address', '', 'foo', 'bar'), null) as r \gset
select tests.ok(:'r'::jsonb = '{"ok": true, "agent_name": "Ramesh Sharma"}'::jsonb, '#CS submit: {ok:true, agent_name} (exactly 18 today needs no guardian)', :'r');
reset role;
select tests.ok((select count(*) from public.customers where agent_id = :A and name = 'Asha Verma') = 1
  and (select phone = '+919811100001' and email = 'asha@example.com' and dob = :'dob18'::date and address is null
             and city = 'Pune' and nominee = 'Ravi Verma' and notes is null and source_enquiry_id is null and deleted_at is null
         from public.customers where agent_id = :A and name = 'Asha Verma'),
  '#CS submit: one customer of the draft''s agent; sent values over saved ones (name), saved kept (city, nominee), "" cleared (address); '
  || 'trimmed, phone +91, email lowercased, notes null');
select tests.ok((select count(*) from public.consents where notice_key = 'customer_signup') = 1
  and (select principal_kind = 'customer' and channel = 'web' and agent_id = :A and enquiry_id is null
             and customer_id = (select id from public.customers where agent_id = :A and name = 'Asha Verma')
             and notice_version = (public.catalog_privacy_notice('customer_signup') ->> 'version')::int
             and notice_hash = public.catalog_privacy_notice('customer_signup') ->> 'hash_en' and locale = 'en'
             and given_at = now() - interval '10 minutes' and withdrawn_at is null
         from public.consents where notice_key = 'customer_signup')
  and not exists (select 1 from public.consents where notice_key = 'customer_signup_guardian'),
  '#CS submit: one consents row (customer, web, the draft''s stored notice and given_at); no guardian row for an adult');
select tests.ok((select completed_at = now() and customer_id = (select id from public.customers where agent_id = :A and name = 'Asha Verma')
                   and fields = '{}'::jsonb and active_field is null and deleted_at is null
                 from public.customer_drafts where id = :'d')
  and not exists (select 1 from private.customer_draft_links where draft_id = :'d'),
  '#CS submit: the draft is completed (customer_id, fields {}, active_field null) and its link row deleted');
select tests.ok((select count(*) from public.audit_log where action = 'customer_signup.submit') = 1
  and (select actor_kind = 'system' and actor_id is null and entity = 'customer_drafts' and entity_id = :'d'::uuid and changes = '{}'::jsonb
         from public.audit_log where action = 'customer_signup.submit'),
  '#CS submit: audit customer_signup.submit (system, customer_drafts, the draft id, changes {})');
select tests.ok(not exists (select 1 from public.audit_log a where a.entity = 'customer_drafts' and to_jsonb(a)::text ~* 'asha|9811100001|pune'),
  '#CS submit: no personal data in audit_log');
select tests.as_agent(:A);
select tests.ok(tests.count(format($$select 1 from public.customer_drafts where id = %L and completed_at is not null and customer_id is not null$$, :'d')) = 1
  and tests.count($$select 1 from public.customers where name = 'Asha Verma'$$) = 1,
  '#CS submit: the agent sees the completed draft and the new customer (RLS)');
reset role;
select tests.as_anon();
select tests.ok(tests.cs_submit(:'t', '{"name":"Asha Verma","phone":"9811100001"}') = '{"ok": false, "error": "invalid_link"}'::jsonb
  and tests.cs_open(:'t') = '{"ok": false, "error": "invalid_link"}'::jsonb
  and tests.cs_save(:'t', '{"name":"again"}') = '{"ok": false, "error": "invalid_link"}'::jsonb,
  '#CS submit: a second submit (and open, save) on the same link -> invalid_link');
reset role;
select tests.ok((select count(*) from public.customers where agent_id = :A and name = 'Asha Verma') = 1
  and (select count(*) from public.consents where notice_key = 'customer_signup') = 1,
  '#CS submit: still exactly one customer and one consent');
select tests.as_agent(:A);
select tests.throws(format('select public.customer_signup_renew(%L)', :'d'), '#CS renew: a completed draft -> not_found', '%not_found%');
-- hard delete of the customer (the agent, through RLS): the FK sets the draft's customer_id null (runs as the owner)
select tests.lives($$delete from public.customers where name = 'Asha Verma'$$, '#CS the agent hard-deletes the customer made by a sign-up');
reset role;
select tests.ok((select customer_id is null and completed_at is not null from public.customer_drafts where id = :'d')
  and (select count(*) from public.consents where notice_key = 'customer_signup' and customer_id is null) = 1,
  '#CS the draft''s customer_id (and the consent''s) become null; the draft stays completed');
rollback;

-- success (minor) with the guardian's consent
begin;
select tests.cs_link(:A) ->> 'token' as t \gset
select to_char((private.today_ist() - interval '18 years')::date + 1, 'YYYY-MM-DD') as dob17 \gset
select tests.as_anon();
select tests.cs_consent(:'t');
select tests.cs_submit(:'t', jsonb_build_object('name', 'Minor Kumar', 'phone', '9811100002', 'dob', :'dob17'),
                       tests.consent('customer_signup_guardian', 'hi')) as r \gset
select tests.ok(:'r'::jsonb ->> 'ok' = 'true', '#CS submit: a minor with the guardian''s consent -> ok', :'r');
reset role;
select tests.ok((select count(*) from public.consents k join public.customers c on c.id = k.customer_id
                  where c.name = 'Minor Kumar' and k.principal_kind = 'customer' and k.channel = 'web') = 2
  and (select notice_hash = public.catalog_privacy_notice('customer_signup_guardian') ->> 'hash_hi' and locale = 'hi' and given_at = now()
         from public.consents where notice_key = 'customer_signup_guardian'),
  '#CS submit: a minor gets two consents rows (customer_signup + customer_signup_guardian, locale from its hash)');
rollback;

-- an adult may send a guardian payload: it is ignored (only a minor needs it)
begin;
select tests.cs_link(:A) ->> 'token' as t \gset
select tests.as_anon();
select tests.cs_consent(:'t');
select tests.ok(tests.cs_submit(:'t', '{"name":"Adult Kumar","phone":"9811100003","dob":"1980-01-01"}', tests.consent('customer_signup_guardian')) ->> 'ok' = 'true',
  '#CS submit: an adult with a guardian payload -> ok');
reset role;
select tests.ok(not exists (select 1 from public.consents where notice_key = 'customer_signup_guardian'),
  '#CS submit: no guardian consent recorded for an adult');
select tests.ok(public.get_public_site('ramesh-sharma')::text !~ 'customer_drafts|token_hash|Adult Kumar', '#CS the public payload has no sign-up data');
rollback;

-- the advisory lock: a save holds the draft's lock until the transaction ends
begin;
select tests.cs_link(:A) ->> 'token' as t \gset
select tests.as_anon();
select tests.cs_open(:'t');
reset role;
select tests.ok(exists (select 1 from pg_locks where locktype = 'advisory' and pid = pg_backend_pid() and granted),
  '#CS an anon call takes a transaction advisory lock (per draft)');
rollback;

-- ===========================================================================
-- 8. customer_signup_renew
-- ===========================================================================
begin;
select tests.cs_link(:A) as l \gset
select :'l'::jsonb ->> 'token' as t, :'l'::jsonb ->> 'draft_id' as d \gset
select tests.as_anon();
select tests.cs_consent(:'t');
select tests.cs_save(:'t', '{"name":"Asha"}');
reset role;
update public.customer_drafts set link_expires_at = now() + interval '1 day' where id = :'d';
select tests.as_agent(:A);
select public.customer_signup_renew(:'d') as r \gset
select tests.ok(:'r'::jsonb ->> 'ok' = 'true' and :'r'::jsonb ->> 'draft_id' = :'d' and :'r'::jsonb ->> 'token' ~ '^[A-Za-z0-9_-]{43}$'
  and :'r'::jsonb ->> 'token' <> :'t'
  and (:'r'::jsonb ->> 'expires_at')::timestamptz between now() + interval '6 days 23 hours' and now() + interval '7 days 1 hour'
  and (select array_agg(k order by k) from jsonb_object_keys(:'r'::jsonb) k) = array['draft_id', 'expires_at', 'ok', 'token'],
  '#CS renew: same shape as link, same draft, a new token, expiry extended to 7 days', :'r');
reset role;
select tests.ok((select link_expires_at = (:'r'::jsonb ->> 'expires_at')::timestamptz and fields = '{"name":"Asha"}'::jsonb
                   and consent is not null from public.customer_drafts where id = :'d')
  and (select count(*) from private.customer_draft_links where draft_id = :'d') = 1
  and (select save_count = 0 and save_window_started_at is null from private.customer_draft_links where draft_id = :'d'),
  '#CS renew: one link row (replaced), save counter reset, the values and consent kept');
select tests.as_anon();
select tests.ok(tests.cs_open(:'t') = '{"ok": false, "error": "invalid_link"}'::jsonb, '#CS renew: the old token -> invalid_link');
select tests.ok(tests.cs_open(:'r'::jsonb ->> 'token') -> 'fields' = '{"name":"Asha"}'::jsonb, '#CS renew: the new token resumes the same form');
reset role;
-- errors
insert into public.customer_drafts (id, agent_id, source) values ('40000000-0000-4000-8000-000000000003', :A, 'agent');
select tests.cs_link(:C) ->> 'draft_id' as dc \gset
select tests.as_agent(:A);
select tests.throws('select public.customer_signup_renew(''40000000-0000-4000-8000-000000000003'')', '#CS renew: an agent-source draft -> not_found', '%not_found%');
select tests.throws(format('select public.customer_signup_renew(%L)', :'dc'), '#CS renew: another agent''s draft -> not_found', '%not_found%');
select tests.throws('select public.customer_signup_renew(gen_random_uuid())', '#CS renew: unknown id -> not_found', '%not_found%');
select tests.throws('select public.customer_signup_renew(null)', '#CS renew: null id -> not_found', '%not_found%');
update public.customer_drafts set deleted_at = now() where id = :'d';
select tests.throws(format('select public.customer_signup_renew(%L)', :'d'), '#CS renew: a deleted draft -> not_found', '%not_found%');
update public.customer_drafts set deleted_at = null where id = :'d';
reset role;
update public.app_config set value = jsonb_set(value, '{customerSignup}', 'false') where key = 'flag_overrides';
select tests.as_agent(:A);
select tests.ok(public.customer_signup_renew(:'d') = '{"ok": false, "error": "unavailable"}'::jsonb, '#CS renew: flag off -> unavailable');
reset role;
update public.app_config set value = value - 'customerSignup' where key = 'flag_overrides';
update public.sites set is_published = false where agent_id = :A;
select tests.as_agent(:A);
select tests.ok(public.customer_signup_renew(:'d') = '{"ok": false, "error": "no_site"}'::jsonb, '#CS renew: no published site -> no_site');
reset role;
select tests.as_anon();
select tests.ok(tests.cs_open(:'r'::jsonb ->> 'token') = '{"ok": false, "error": "unavailable"}'::jsonb, '#CS renew refused: the current link is untouched (site off -> unavailable)');
rollback;

-- renew of an expired link counts against openLinksPerAgent
begin;
select tests.cs_link(:A) ->> 'draft_id' as d \gset
update public.customer_drafts set link_expires_at = now() - interval '1 minute' where id = :'d';
select tests.as_agent(:A);
select count(*) filter (where public.customer_signup_link() ->> 'ok' = 'true') as made from generate_series(1, 25) \gset
select tests.ok(public.customer_signup_renew(:'d') = '{"ok": false, "error": "too_many"}'::jsonb,
  '#CS renew: reviving an expired link with 25 live ones -> too_many');
reset role;
update public.customer_drafts set deleted_at = now()
 where id = (select id from public.customer_drafts where agent_id = :A and link_expires_at > now() order by id limit 1);
select tests.as_agent(:A);
select tests.ok(public.customer_signup_renew(:'d') ->> 'ok' = 'true', '#CS renew: with a free slot the expired link is renewed');
select tests.ok(public.customer_signup_renew(:'d') ->> 'ok' = 'true', '#CS renew: a live link can always be renewed (it doesn''t add a link)');
rollback;

-- ===========================================================================
-- 9. Account deletion and purge
-- ===========================================================================
begin;
select tests.cs_link(:A) ->> 'draft_id' as d \gset
insert into public.customer_drafts (agent_id, source, fields) values (:A, 'agent', '{"name":"x"}');
select tests.lives($$delete from auth.users where id = 'aaaaaaaa-0000-4000-8000-00000000000a'$$, '#CS deleting the agent cascades through its drafts');
select tests.ok(not exists (select 1 from public.customer_drafts) and not exists (select 1 from private.customer_draft_links),
  '#CS nothing of the sign-up is left after the cascade');
rollback;

begin;
-- p1 open customer link idle 31 days; p2 open agent draft idle 31 days; p3 completed 31 days; p4 deleted 31 days;
-- p5 open fresh; p6 completed fresh; p7 deleted fresh; p8 another agent's open draft idle 31 days
select tests.cs_link(:A) ->> 'token' as t1 \gset
select (tests.cs_draft(:'t1')).id as p1 \gset
select tests.as_anon();
select tests.cs_consent(:'t1');
select tests.cs_save(:'t1', '{"name":"Idle Customer"}', 'name');
reset role;
insert into public.customer_drafts (id, agent_id, source, fields, completed_at, deleted_at) values
  ('40000000-0000-4000-8000-000000000012', :A, 'agent', '{"name":"Idle Agent"}', null, null),
  ('40000000-0000-4000-8000-000000000013', :A, 'agent', '{}', now(), null),
  ('40000000-0000-4000-8000-000000000014', :A, 'agent', '{"name":"Deleted"}', null, now()),
  ('40000000-0000-4000-8000-000000000015', :A, 'agent', '{"name":"Fresh"}', null, null),
  ('40000000-0000-4000-8000-000000000016', :A, 'agent', '{}', now(), null),
  ('40000000-0000-4000-8000-000000000017', :A, 'agent', '{"name":"Fresh deleted"}', null, now()),
  ('40000000-0000-4000-8000-000000000018', :C, 'agent', '{"name":"C idle"}', null, null);
select tests.cs_age(id, interval '31 days') from public.customer_drafts
 where id in (:'p1', '40000000-0000-4000-8000-000000000012', '40000000-0000-4000-8000-000000000013',
              '40000000-0000-4000-8000-000000000014', '40000000-0000-4000-8000-000000000018');
select private.purge_customer_drafts() as pr \gset
select tests.ok(:'pr'::jsonb = '{"erased": 3, "deleted": 2}'::jsonb, '#CS purge: {erased:3, deleted:2}', :'pr');
select tests.ok(not exists (select 1 from public.customer_drafts where id in ('40000000-0000-4000-8000-000000000013', '40000000-0000-4000-8000-000000000014')),
  '#CS purge: completed and soft-deleted drafts idle 31 days are hard-deleted');
select tests.ok((select count(*) from public.customer_drafts
                  where id in (:'p1', '40000000-0000-4000-8000-000000000012', '40000000-0000-4000-8000-000000000018')
                    and deleted_at = now() and fields = '{}'::jsonb and active_field is null and consent is null) = 3
  and not exists (select 1 from private.customer_draft_links where draft_id = :'p1'),
  '#CS purge: open drafts idle 31 days are erased (fields, active_field, consent cleared), soft-deleted (a tombstone for sync) and lose their link');
select tests.ok((select count(*) from public.customer_drafts where id in ('40000000-0000-4000-8000-000000000015',
                   '40000000-0000-4000-8000-000000000016', '40000000-0000-4000-8000-000000000017')) = 3
  and (select fields from public.customer_drafts where id = '40000000-0000-4000-8000-000000000015') = '{"name":"Fresh"}'::jsonb,
  '#CS purge: fresh drafts (open, completed, deleted) are untouched');
select tests.ok((select count(*) from public.audit_log where action = 'retention.customer_drafts') = 1
  and (select actor_kind = 'system' and entity = 'customer_drafts' and changes = '{"erased": 3, "deleted": 2}'::jsonb
         from public.audit_log where action = 'retention.customer_drafts'),
  '#CS purge: one audit row with counts only');
select tests.as_anon();
select tests.ok(tests.cs_open(:'t1') = '{"ok": false, "error": "invalid_link"}'::jsonb, '#CS purge: the erased link -> invalid_link');
reset role;
select private.purge_customer_drafts() as pr1 \gset
select tests.ok(:'pr1'::jsonb = '{"erased": 0, "deleted": 0}'::jsonb
  and (select count(*) from public.audit_log where action = 'retention.customer_drafts') = 1,
  '#CS purge: a second run right away changes nothing and writes no audit row');
select tests.cs_age(id, interval '31 days') from public.customer_drafts where id in (:'p1', '40000000-0000-4000-8000-000000000012');
select private.purge_customer_drafts() as pr2 \gset
select tests.ok(:'pr2'::jsonb = '{"erased": 0, "deleted": 2}'::jsonb
  and not exists (select 1 from public.customer_drafts where id in (:'p1', '40000000-0000-4000-8000-000000000012')),
  '#CS purge: the tombstones are hard-deleted after another customerDraftsDays');
select tests.as_agent(:A);
select tests.throws($$select private.purge_customer_drafts()$$, '#CS purge: not callable by authenticated', '%permission denied%');
reset role;
set local role service_role;
select tests.throws($$select private.purge_customer_drafts()$$, '#CS purge: not callable by the service role', '%permission denied%');
rollback;

-- ===========================================================================
-- 10. Concurrent double submit (two real sessions, dblink): the second waits on the draft lock, then gets invalid_link
-- ===========================================================================
create extension if not exists dblink schema tests;
select tests.cs_link(:A) ->> 'token' as ct \gset
select public.signup_consent('ramesh-sharma', null, :'ct', tests.consent('customer_signup'));
select tests.dblink_connect('cs2', 'dbname=postgres user=postgres');
select * from tests.dblink('cs2', $$select set_config('request.headers', '{"x-portal-key":"test-portal-key"}', false)$$) as x(v text);
begin;
select public.signup_submit('ramesh-sharma', null, :'ct', '{"name":"Race Kumar","phone":"9811100077"}', null) as r1 \gset
select tests.dblink_send_query('cs2', format('select public.signup_submit(%L, null, %L, %L::jsonb, null)::text',
  'ramesh-sharma', :'ct', '{"name":"Race Kumar Two","phone":"9811100078"}'));
select pg_sleep(0.5);
select tests.ok(tests.dblink_is_busy('cs2') = 1, '#CS concurrency: the second submit waits while the first holds the draft lock');
commit;
select r as r2 from tests.dblink_get_result('cs2') as x(r text) \gset
select tests.ok(:'r1'::jsonb ->> 'ok' = 'true' and :'r2'::jsonb = '{"ok": false, "error": "invalid_link"}'::jsonb,
  '#CS concurrency: first submit ok, the concurrent second -> invalid_link', :'r1' || ' / ' || :'r2');
select tests.ok((select count(*) from public.customers where agent_id = :A and name like 'Race Kumar%') = 1,
  '#CS concurrency: exactly one customer created');
select tests.dblink_disconnect('cs2');
-- clean up the committed rows
delete from public.audit_log where action = 'customer_signup.submit'
   and entity_id in (select id from public.customer_drafts where customer_id in (select id from public.customers where name like 'Race Kumar%'));
delete from public.consents where customer_id in (select id from public.customers where name like 'Race Kumar%');
delete from public.customer_drafts where customer_id in (select id from public.customers where name like 'Race Kumar%');
delete from public.customers where name like 'Race Kumar%';
drop extension dblink;
select tests.ok(not exists (select 1 from public.customer_drafts) and not exists (select 1 from private.customer_draft_links)
  and not exists (select 1 from public.audit_log where action = 'customer_signup.submit'),
  '#CS clean: no sign-up rows left for later files');

-- Leave the session as other files expect it.
select set_config('request.headers', '', false);
delete from private.portal_gate;
