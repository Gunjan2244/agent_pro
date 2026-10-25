-- LOCAL TESTS ONLY. Phase 8c-1: the SQL helpers of the `admin` Edge Function (20261022183528_admin_function.sql;
-- docs/BACKEND.md "Edge Functions → admin"). The Edge Function itself (JWT, aal2, is_admin('owner'), the Auth admin
-- API calls) is checked by hand on the project (TESTING.md, "Admin Edge Function").
-- Reuses the committed setup of 95_offers.sql: owner O, editor S (scoped to C2), revoked editor X, agents E and F,
-- companies C1, C2, C3. Every block rolls back.
\set E '''eeeeeeee-0000-4000-8000-00000000000e'''
\set O '''a1a1a1a1-0000-4000-8000-0000000000a1'''
\set S '''a2a2a2a2-0000-4000-8000-0000000000a2'''
\set X '''a3a3a3a3-0000-4000-8000-0000000000a3'''
\set C1 '''c1c1c1c1-0000-4000-8000-0000000000c1'''
\set C2 '''c2c2c2c2-0000-4000-8000-0000000000c2'''
\set N '''a4a4a4a4-0000-4000-8000-0000000000a4'''
\set MISSING '''99999999-0000-4000-8000-000000000099'''

-- The Edge Function's service-role session (PostgREST with the service key: no sub).
create function tests.as_service()
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', '{"role":"service_role"}', true);
  perform set_config('role', 'service_role', true);
end;
$$;
grant execute on function tests.as_service() to anon, authenticated;
grant usage on schema tests to service_role;
grant execute on all functions in schema tests to service_role;

-- ---------------------------------------------------------------------------
-- Grants: service_role only
-- ---------------------------------------------------------------------------
select tests.ok(not exists (
  select 1 from pg_proc f join pg_namespace n on n.oid = f.pronamespace
   where n.nspname = 'public' and f.proname like 'svc\_admin\_%'
     and (has_function_privilege('anon', f.oid, 'EXECUTE') or has_function_privilege('authenticated', f.oid, 'EXECUTE'))),
  '#AF anon and authenticated cannot execute any svc_admin_* helper');
select tests.ok((select count(*) from pg_proc f join pg_namespace n on n.oid = f.pronamespace
                  where n.nspname = 'public' and f.proname like 'svc\_admin\_%'
                    and has_function_privilege('service_role', f.oid, 'EXECUTE')
                    and f.prosecdef) = 6,
  '#AF the 6 svc_admin_* helpers are SECURITY DEFINER and executable by service_role');
select tests.ok(not has_table_privilege('service_role', 'private.admin_invites', 'SELECT')
                and not has_table_privilege('authenticated', 'private.admin_invites', 'SELECT'),
  '#AF private.admin_invites is not readable by the API roles');

begin;
select tests.as_admin(:O);
select tests.throws($$select public.svc_admin_invite_prepare(null, 'x@example.com', null)$$,
  '#AF an owner session (authenticated) cannot call svc_admin_invite_prepare', '%permission denied%');
reset role;
rollback;

-- ---------------------------------------------------------------------------
-- invite: prepare -> (GoTrue creates the user) -> finish
-- ---------------------------------------------------------------------------
begin;
update auth.users set email_confirmed_at = now() where id = :O;
select tests.as_service();

select tests.throws(format('select public.svc_admin_invite_prepare(%L, %L, null)', :S, 'new.admin@agentpro.test'),
  '#AF prepare: an editor actor is forbidden', 'forbidden');
select tests.throws(format('select public.svc_admin_invite_prepare(%L, %L, null)', :X, 'new.admin@agentpro.test'),
  '#AF prepare: a revoked actor is forbidden', 'forbidden');
select tests.throws(format('select public.svc_admin_invite_prepare(%L, %L, null)', :O, 'not an email'),
  '#AF prepare: a bad email is invalid', 'invalid');
select tests.throws(format('select public.svc_admin_invite_prepare(%L, %L, null)', :O, 'Esha.Private@Example.com'),
  '#AF prepare: an agent account (any case) is refused', 'invalid');
select tests.throws(format('select public.svc_admin_invite_prepare(%L, %L, null)', :O, 'owner@agentpro.test'),
  '#AF prepare: an active admin who has signed in is refused (use set-role)', 'invalid');
select tests.throws(format('select public.svc_admin_invite_prepare(%L, %L, %L)', :O, 'new.admin@agentpro.test', :MISSING),
  '#AF prepare: an unknown company is not_found', 'not_found');

select tests.ok(public.svc_admin_invite_prepare(:O, '  New.Admin@AgentPRO.test ', :C1) = '{"state":"new"}'::jsonb,
  '#AF prepare: a new email returns state new');
reset role;
select tests.ok(exists (select 1 from private.admin_invites where email = 'new.admin@agentpro.test'),
  '#AF prepare stores a lower-cased invite marker');

-- What inviteUserByEmail does: insert the user with GoTrue's default app_metadata (account_kind comes later).
insert into auth.users (id, email, raw_app_meta_data) values (:N, 'new.admin@agentpro.test', '{"provider":"email","providers":["email"]}');
select tests.ok(not exists (select 1 from public.profiles where id = :N)
                and not exists (select 1 from public.subscriptions where agent_id = :N),
  '#AF the signup trigger skips an invitee with a fresh marker (no profile, no subscription)');
update auth.users set raw_app_meta_data = raw_app_meta_data || '{"account_kind":"admin"}' where id = :N;

select tests.as_service();
select tests.throws(format('select public.svc_admin_invite_finish(%L, %L, %L, %L, %L, null)', :O, :N, 'other@agentpro.test', 'editor', :C1),
  '#AF finish: an email that is not the user''s is invalid', 'invalid');
select tests.throws(format('select public.svc_admin_invite_finish(%L, %L, %L, %L, %L, null)', :O, :N, 'new.admin@agentpro.test', 'boss', :C1),
  '#AF finish: an unknown role is invalid (admins_role_check)', 'invalid');
select tests.throws(format('select public.svc_admin_invite_finish(%L, %L, %L, %L, %L, null)', :O, :E, 'esha.private@example.com', 'editor', null),
  '#AF finish: an agent account is refused', 'invalid');
select tests.ok(public.svc_admin_invite_finish(:O, :N, 'New.Admin@agentpro.test', 'editor', :C1, '203.0.113.7') ->> 'user_id' = :N,
  '#AF finish returns the user id');
reset role;

select tests.ok(exists (select 1 from public.admins where user_id = :N and role = 'editor' and company_id = :C1
                          and invited_by = :O and revoked_at is null),
  '#AF finish creates the admins row (role, scope, invited_by)');
select tests.ok(not exists (select 1 from private.admin_invites where email = 'new.admin@agentpro.test'),
  '#AF finish deletes the marker');
select tests.ok((select array_agg(action) from public.audit_log where entity = 'admins' and entity_id = :N) = array['admin.invite'],
  '#AF exactly one audit row for the invite (the trigger row is skipped)',
  (select string_agg(action, ',') from public.audit_log where entity = 'admins' and entity_id = :N));
select tests.ok(exists (select 1 from public.audit_log where entity_id = :N and action = 'admin.invite' and actor_kind = 'admin'
                          and actor_id = :O and request_ip = '203.0.113.7'::inet
                          and changes = jsonb_build_object('role', 'editor', 'company_id', :C1::uuid, 'reinvite', false)),
  '#AF the invite audit row has the actor, the IP and only the role and scope');

-- After the helper, writes to admins are audited by the trigger again (the setting doesn't leak).
update public.admins set role = 'owner' where user_id = :N;
select tests.ok(exists (select 1 from public.audit_log where entity_id = :N and action = 'admin.update' and actor_kind = 'system'),
  '#AF a direct admins write (SQL Editor) is still audited by the trigger');
rollback;

-- Re-inviting a revoked admin reactivates them; an orphaned invite (no row, no profile) can be finished later.
begin;
select tests.as_service();
select tests.ok(public.svc_admin_invite_prepare(:O, 'former.admin@agentpro.test', null)
                @> jsonb_build_object('state', 'existing', 'user_id', :X::uuid, 'active', false),
  '#AF prepare: a revoked admin is an existing user');
select public.svc_admin_invite_finish(:O, :X, 'former.admin@agentpro.test', 'editor', null, null);
reset role;
select tests.ok(exists (select 1 from public.admins where user_id = :X and revoked_at is null and invited_by = :O),
  '#AF re-inviting a revoked admin clears revoked_at');
select tests.ok(exists (select 1 from public.audit_log where entity_id = :X and action = 'admin.invite' and changes ->> 'reinvite' = 'true'),
  '#AF the re-invite is audited as a reinvite');
rollback;

-- Markers: an agent signup without one still gets a profile; a stale one is ignored.
begin;
insert into private.admin_invites (email, created_at) values ('late.admin@agentpro.test', now() - interval '20 minutes');
insert into auth.users (id, email) values ('a5a5a5a5-0000-4000-8000-0000000000a5', 'late.admin@agentpro.test'),
                                          ('a6a6a6a6-0000-4000-8000-0000000000a6', 'plain.agent@example.com');
select tests.ok((select count(*) from public.profiles where id in ('a5a5a5a5-0000-4000-8000-0000000000a5',
                                                                   'a6a6a6a6-0000-4000-8000-0000000000a6')) = 2,
  '#AF a stale marker is ignored and an ordinary signup still gets a profile');
select tests.as_service();
select public.svc_admin_invite_prepare(:O, 'cancel.me@agentpro.test', null);
select public.svc_admin_invite_cancel('Cancel.Me@agentpro.test');
reset role;
select tests.ok(not exists (select 1 from private.admin_invites where email = 'cancel.me@agentpro.test'),
  '#AF cancel deletes the marker');
rollback;

-- ---------------------------------------------------------------------------
-- set-role
-- ---------------------------------------------------------------------------
begin;
select tests.as_service();
select tests.throws(format('select public.svc_admin_set_role(%L, %L, %L, null, true, null)', :O, :O, 'editor'),
  '#AF set-role: an owner cannot change their own role', 'self');
select tests.throws(format('select public.svc_admin_set_role(%L, %L, %L, null, true, null)', :S, :O, 'editor'),
  '#AF set-role: an editor actor is forbidden', 'forbidden');
select tests.throws(format('select public.svc_admin_set_role(%L, %L, %L, null, true, null)', :O, :MISSING, 'editor'),
  '#AF set-role: an unknown admin is not_found', 'not_found');
select tests.throws(format('select public.svc_admin_set_role(%L, %L, %L, null, true, null)', :O, :E, 'editor'),
  '#AF set-role: an agent (no admins row) is not_found', 'not_found');
select tests.throws(format('select public.svc_admin_set_role(%L, %L, %L, null, true, null)', :O, :X, 'editor'),
  '#AF set-role: a revoked admin is invalid', 'invalid');
select tests.throws(format('select public.svc_admin_set_role(%L, %L, %L, null, true, null)', :O, :S, 'boss'),
  '#AF set-role: an unknown role is invalid', 'invalid');
select tests.throws(format('select public.svc_admin_set_role(%L, %L, %L, %L, true, null)', :O, :S, 'editor', :MISSING),
  '#AF set-role: an unknown company is not_found', 'not_found');
select tests.ok(public.svc_admin_set_role(:O, :S, 'editor', :C2, true, null) = '{"changed":false}'::jsonb,
  '#AF set-role: no change is a no-op');
select tests.ok(public.svc_admin_set_role(:O, :S, 'owner', null, false, '198.51.100.1') = '{"changed":true}'::jsonb,
  '#AF set-role: promote to owner');
reset role;
select tests.ok(exists (select 1 from public.admins where user_id = :S and role = 'owner' and company_id = :C2),
  '#AF set-role without p_set_company keeps the scope');
select tests.ok((select array_agg(action) from public.audit_log where entity = 'admins' and entity_id = :S and at >= now()) = array['admin.set_role'],
  '#AF set-role writes one audit row (the no-op none)');
select tests.ok(exists (select 1 from public.audit_log where entity_id = :S and action = 'admin.set_role' and actor_id = :O
                          and request_ip = '198.51.100.1'::inet
                          and changes = jsonb_build_object('role', 'owner', 'company_id', :C2::uuid,
                                                           'previous_role', 'editor', 'previous_company_id', :C2::uuid)),
  '#AF the set-role audit row has the new and previous role and scope');
select tests.as_service();
select tests.ok(public.svc_admin_set_role(:O, :S, 'owner', null, true, null) = '{"changed":true}'::jsonb,
  '#AF set-role with p_set_company and null widens the scope to all companies');
reset role;
select tests.ok(exists (select 1 from public.admins where user_id = :S and company_id is null),
  '#AF the scope is now all companies');
rollback;

-- last_owner: removing the only active owner is refused (the helper every write uses).
begin;
select tests.throws(format('select private.check_not_last_owner(%L)', :O),
  '#AF check_not_last_owner refuses removing the only owner', 'last_owner');
update public.admins set role = 'owner' where user_id = :S;
select tests.lives(format('select private.check_not_last_owner(%L)', :O),
  '#AF check_not_last_owner allows it with another active owner');
rollback;

-- ---------------------------------------------------------------------------
-- revoke
-- ---------------------------------------------------------------------------
begin;
insert into auth.sessions (id, user_id, aal) values
  ('5e550001-0000-4000-8000-000000000001', :S, 'aal2'), ('5e550002-0000-4000-8000-000000000002', :S, 'aal1'),
  ('5e550003-0000-4000-8000-000000000003', :O, 'aal2');
insert into auth.refresh_tokens (token, user_id, session_id) values
  ('t1', :S, '5e550001-0000-4000-8000-000000000001'), ('t2', :O, '5e550003-0000-4000-8000-000000000003');
select tests.as_service();
select tests.throws(format('select public.svc_admin_revoke(%L, %L, null)', :O, :O),
  '#AF revoke: an owner cannot revoke themselves', 'self');
select tests.throws(format('select public.svc_admin_revoke(%L, %L, null)', :O, :MISSING),
  '#AF revoke: an unknown admin is not_found', 'not_found');
select tests.ok(public.svc_admin_revoke(:O, :S, '192.0.2.4') = '{"changed":true,"signed_out":true}'::jsonb,
  '#AF revoke returns changed and signed_out');
reset role;
select tests.ok(exists (select 1 from public.admins where user_id = :S and revoked_at is not null),
  '#AF revoke sets revoked_at');
select tests.ok(not exists (select 1 from auth.sessions where user_id = :S)
                and not exists (select 1 from auth.refresh_tokens where user_id = :S::text),
  '#AF revoke ends every session and refresh token of the admin');
select tests.ok(exists (select 1 from auth.sessions where user_id = :O) and exists (select 1 from auth.refresh_tokens where user_id = :O::text),
  '#AF revoke leaves other users'' sessions alone');
select tests.ok((select array_agg(action) from public.audit_log where entity = 'admins' and entity_id = :S and at >= now()) = array['admin.revoke'],
  '#AF revoke writes exactly one audit row');
select tests.ok(exists (select 1 from public.audit_log where entity_id = :S and action = 'admin.revoke' and actor_kind = 'admin'
                          and request_ip = '192.0.2.4'::inet
                          and changes = jsonb_build_object('role', 'editor', 'company_id', :C2::uuid, 'sessions_ended', 2)),
  '#AF the revoke audit row has the role, the scope and the sessions ended');
select tests.as_service();
select tests.ok(public.svc_admin_revoke(:O, :S, null) = '{"changed":false,"signed_out":true}'::jsonb,
  '#AF revoking again is a no-op');
reset role;
select tests.ok((select count(*) from public.audit_log where entity_id = :S and at >= now()) = 1,
  '#AF a repeated revoke with no session left writes no audit row');
-- A revoked admin's token no longer passes is_admin().
select tests.as_admin(:S);
select tests.ok(not public.is_admin(), '#AF a revoked admin is no admin, even at aal2');
reset role;
rollback;

-- ---------------------------------------------------------------------------
-- reset-mfa (the factors are deleted through the Auth admin API; SQL only records it)
-- ---------------------------------------------------------------------------
begin;
select tests.as_service();
select tests.throws(format('select public.svc_admin_log_reset_mfa(%L, %L, 1, null)', :O, :O),
  '#AF reset-mfa: not on yourself', 'self');
select tests.throws(format('select public.svc_admin_log_reset_mfa(%L, %L, 1, null)', :O, :E),
  '#AF reset-mfa: an agent (no admins row) is not_found', 'not_found');
select tests.throws(format('select public.svc_admin_log_reset_mfa(%L, %L, 1, null)', :S, :X),
  '#AF reset-mfa: an editor actor is forbidden', 'forbidden');
select public.svc_admin_log_reset_mfa(:O, :S, 2, 'garbage-ip');
reset role;
select tests.ok(exists (select 1 from public.audit_log where entity_id = :S and action = 'admin.reset_mfa' and actor_id = :O
                          and actor_kind = 'admin' and request_ip is null and changes = '{"factors_removed":2}'::jsonb),
  '#AF reset-mfa writes admin.reset_mfa (an unparseable IP is stored as null)');
rollback;
