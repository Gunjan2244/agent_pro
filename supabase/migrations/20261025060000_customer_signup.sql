-- Agent PRO — customer sign-up link and Incomplete forms (DECISIONS #108; contract sections 1 and 2).
-- One agent-owned table, public.customer_drafts, holds every unfinished customer form:
--   * source 'agent'    : the agent's own Add customer form, autosaved through the normal client writes (RLS);
--   * source 'customer' : a sign-up link. customer_signup_link() creates the row and a single-use token (32 random
--     bytes, base64url, 43 characters) that is returned once and stored only as sha256 in the private table
--     private.customer_draft_links, so no client `select *` can ever read it. The customer fills in the form on the
--     agent's site (the Worker) through four anon RPCs: signup_open, signup_consent, signup_save, signup_submit.
--   * The anon RPCs first check private.portal_gate_ok() (the portal's Worker secret, header x-portal-key), then the
--     customerSignup flag, then resolve (slug, host) exactly like submit_enquiry (private.find_public_site: published,
--     not deleted, owner not lapsed); the site's agent must own the draft. They never raise for user input.
--   * No field is stored before the customer has consented to the customer_signup notice (signup_consent); Submit
--     needs the separate customer_signup_guardian notice for an under-18 date of birth.
--   * signup_submit validates everything, creates the customers row and its consents rows (channel 'web',
--     principal 'customer'), completes the draft (fields cleared) and deletes the link, in one transaction, under a
--     per-draft advisory lock (a double submit can't create two customers).
--   * Client writes are guarded by private.customer_drafts_guard (SECURITY INVOKER on purpose): it compares
--     current_user, which a client cannot forge (no GUC is trusted), with the table owner / service_role. Inside the
--     SECURITY DEFINER RPCs below current_user is the owner, so only they (and the service role) can write the
--     customer-only columns. private.customer_drafts_validate checks the fields on every write.
--   * Retention: retention.customerDraftsDays (30). The anon RPCs treat a draft idle longer as invalid_link;
--     private.purge_customer_drafts() erases idle open drafts and deletes old completed or deleted ones. Its daily
--     schedule (pg_cron) is NOT set up here: it needs the user's approval.
--   * audit_log: customer_signup.submit (actor system, entity customer_drafts, changes {}: never personal data) and
--     retention.customer_drafts (counts only) from the purge.
--
-- Apply order: after 20261023163214_catalog_sync.sql and BEFORE the generated 20261025070819_catalog_sync.sql (it adds
-- the customerSignup flag, app_config.customer_signup, retention.customerDraftsDays and the two notices with the
-- consents CHECK). Nothing here reads those at apply time (plpgsql resolves names lazily; app_config is read at run
-- time by private.customer_signup_config() and private.customer_drafts_days(), which raise P0001 when it's missing).
-- The anon RPCs answer 'unavailable' until private.portal_gate is seeded (the customer portal's go-live step).
-- Expected advisors: 0028 +4 anon (signup_open, signup_consent, signup_save, signup_submit); 0029 +2 authenticated
-- (customer_signup_link, customer_signup_renew); INFO 0008 (RLS on, no policy) on private.customer_draft_links.
-- Errors from the agent RPCs: not_authenticated / plan_read_only (42501), not_found (P0002). Client writes that break
-- the guard raise customer_drafts_read_only / customer_drafts_source (42501) or invalid_fields (22023).

-- ===========================================================================
-- Tables
-- ===========================================================================

-- source, active_field: internal value lists (BACKEND.md), hand-written CHECKs like orders.kind.
-- fields: partial values, all strings; keys and caps are checked by the validate trigger
-- (private.customer_draft_caps). consent: {notice_key, version, hash, locale, given_at} once the customer agreed.
create table public.customer_drafts (
  id               uuid primary key default gen_random_uuid(),
  agent_id         uuid not null references auth.users (id) on delete cascade,
  source           text not null,
  fields           jsonb not null default '{}'::jsonb,
  active_field     text,
  customer_seen_at timestamptz,
  opened_at        timestamptz,
  consent          jsonb,
  link_expires_at  timestamptz,
  completed_at     timestamptz,
  customer_id      uuid,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  deleted_at       timestamptz,
  constraint customer_drafts_source_values check (source in ('agent', 'customer')),
  constraint customer_drafts_fields_object check (jsonb_typeof(fields) = 'object'),
  constraint customer_drafts_fields_size check (octet_length(fields::text) <= 4096),
  constraint customer_drafts_active_field_values check (
    active_field is null or active_field in ('name', 'phone', 'email', 'dob', 'address', 'city', 'nominee')),
  constraint customer_drafts_consent_object check (consent is null or jsonb_typeof(consent) = 'object'),
  -- The customer-only columns stay null on the agent's own drafts.
  constraint customer_drafts_agent_columns check (
    source = 'customer'
    or (active_field is null and customer_seen_at is null and opened_at is null and consent is null
        and link_expires_at is null)),
  constraint customer_drafts_customer_link check (source = 'agent' or link_expires_at is not null),
  -- A completed draft keeps no values (they live in customers now).
  constraint customer_drafts_completed_empty check (completed_at is null or (fields = '{}'::jsonb and active_field is null)),
  constraint customer_drafts_customer_completed check (customer_id is null or completed_at is not null),
  constraint customer_drafts_customer_fk foreign key (customer_id, agent_id)
    references public.customers (id, agent_id) on delete set null (customer_id)
);
create index customer_drafts_agent_id_updated_at_idx on public.customer_drafts (agent_id, updated_at);
create index customer_drafts_customer_id_idx on public.customer_drafts (customer_id, agent_id);

create trigger set_updated_at before insert or update on public.customer_drafts
  for each row execute function private.set_updated_at();

-- The sign-up link of a customer-source draft (one per draft; a renewed link replaces it). token_hash = sha256 of
-- the 43-character token; save_window_started_at / save_count = the per-draft save limit (savesPerMinute).
-- RLS on, no grants to any API role (the service role included): only the SECURITY DEFINER RPCs touch it.
create table private.customer_draft_links (
  draft_id               uuid primary key references public.customer_drafts (id) on delete cascade,
  token_hash             bytea not null,
  created_at             timestamptz not null default now(),
  save_window_started_at timestamptz,
  save_count             integer not null default 0,
  constraint customer_draft_links_token_hash_key unique (token_hash),
  constraint customer_draft_links_token_hash_length check (octet_length(token_hash) = 32),
  constraint customer_draft_links_save_count_nonneg check (save_count >= 0)
);
alter table private.customer_draft_links enable row level security;
revoke all on private.customer_draft_links from public, anon, authenticated, service_role;

-- ===========================================================================
-- Config and field helpers
-- ===========================================================================

-- app_config('customer_signup') = catalog.customerSignup (from catalog_sync). No defaults in SQL: fails loudly.
create or replace function private.customer_signup_config()
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
  select c.value into v from public.app_config c where c.key = 'customer_signup';
  if v is null or jsonb_typeof(v) <> 'object' then
    raise exception 'app_config.customer_signup is missing: apply the generated catalog_sync migration' using errcode = 'P0001';
  end if;
  foreach k in array array['linkDays', 'savesPerMinute', 'openLinksPerAgent', 'presenceSeconds'] loop
    if jsonb_typeof(v -> k) is distinct from 'number' or (v ->> k) !~ '^[1-9][0-9]{0,5}$' then
      raise exception 'app_config.customer_signup.% is missing or not a positive integer', k using errcode = 'P0001';
    end if;
  end loop;
  return v;
end;
$$;

-- retention.customerDraftsDays (from catalog_sync). Fails loudly when it's missing.
create or replace function private.customer_drafts_days()
returns integer
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v jsonb := private.retention_config() -> 'customerDraftsDays';
begin
  if jsonb_typeof(v) is distinct from 'number' or (v #>> '{}') !~ '^[1-9][0-9]{0,5}$' then
    raise exception 'app_config.retention.customerDraftsDays is missing or not a positive integer' using errcode = 'P0001';
  end if;
  return (v #>> '{}')::int;
end;
$$;

-- The allowed field keys of a draft and their caps (characters). notes is the agent's own field only.
create or replace function private.customer_draft_caps(p_source text)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select case p_source
    when 'customer' then '{"name":80,"phone":20,"email":120,"dob":10,"address":200,"city":60,"nominee":80}'::jsonb
    when 'agent' then '{"name":80,"phone":20,"email":120,"dob":10,"address":200,"city":60,"nominee":80,"notes":500}'::jsonb
  end;
$$;

-- true when p_fields is an object whose keys are all allowed for p_source, with string values within the caps.
create or replace function private.customer_draft_fields_ok(p_fields jsonb, p_source text)
returns boolean
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_caps jsonb := private.customer_draft_caps(p_source);
  e      record;
begin
  if v_caps is null or p_fields is null or jsonb_typeof(p_fields) <> 'object' then
    return false;
  end if;
  for e in select j.key, j.value from jsonb_each(p_fields) j loop
    if not (v_caps ? e.key) or jsonb_typeof(e.value) <> 'string'
       or char_length(e.value #>> '{}') > (v_caps ->> e.key)::int then
      return false;
    end if;
  end loop;
  return true;
end;
$$;

-- The customer's values sent from the page laid over the saved ones: only the keys sent are replaced, "" clears a
-- key, unknown keys and non-string values are dropped, and (p_truncate) values are cut to the caps. Never notes.
create or replace function private.customer_draft_merge(p_saved jsonb, p_sent jsonb, p_truncate boolean)
returns jsonb
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_caps jsonb := private.customer_draft_caps('customer');
  v      jsonb := '{}'::jsonb;
  e      record;
  s      text;
begin
  if jsonb_typeof(p_saved) = 'object' then
    select coalesce(jsonb_object_agg(j.key, j.value), '{}'::jsonb) into v
      from jsonb_each(p_saved) j
     where v_caps ? j.key and jsonb_typeof(j.value) = 'string';
  end if;
  if jsonb_typeof(p_sent) is distinct from 'object' then
    return v;
  end if;
  for e in select j.key, j.value from jsonb_each(p_sent) j loop
    continue when not (v_caps ? e.key) or jsonb_typeof(e.value) <> 'string';
    s := e.value #>> '{}';
    if s = '' then
      v := v - e.key;
    else
      v := v || jsonb_build_object(e.key, case when p_truncate then left(s, (v_caps ->> e.key)::int) else s end);
    end if;
  end loop;
  return v;
end;
$$;

-- ===========================================================================
-- Triggers
-- ===========================================================================

-- The client write rules (contract section 1). SECURITY INVOKER on purpose: current_user is then the role that runs
-- the statement, i.e. `authenticated` for an app write through PostgREST, and the function owner inside the SECURITY
-- DEFINER RPCs of this file. A client can't change current_user (PostgREST's authenticator is not a member of the
-- owner role) and no GUC is consulted, so the rule can't be bypassed with set_config or request headers.
-- Trusted (no column rules): the table owner, a superuser, the service role.
-- Client: inserts only source 'agent' (also in the RLS policy; the customer-only columns must be null, CHECK
-- customer_drafts_agent_columns); on a source 'agent' row only fields, deleted_at, completed_at and customer_id may
-- change; on a source 'customer' row only deleted_at (delete / Undo). For everyone: completing a draft clears its
-- fields and active_field (data minimisation, and a late autosave can't re-store values on a completed draft).
create or replace function private.customer_drafts_guard()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  v_trusted boolean;
  v_allowed text[];
begin
  if new.completed_at is not null then
    new.fields := '{}'::jsonb;
    new.active_field := null;
  end if;

  select current_user = 'service_role' or r.rolsuper or r.oid = c.relowner into v_trusted
    from pg_catalog.pg_class c, pg_catalog.pg_roles r
   where c.oid = tg_relid and r.rolname = current_user;
  if coalesce(v_trusted, false) then
    return new;
  end if;

  if tg_op = 'INSERT' then
    if new.source is distinct from 'agent' then
      raise exception 'customer_drafts_source' using errcode = '42501';
    end if;
    return new;
  end if;

  v_allowed := case when old.source = 'agent'
                    then array['fields', 'deleted_at', 'completed_at', 'customer_id', 'updated_at']
                    else array['deleted_at', 'updated_at'] end;
  if (to_jsonb(new) - v_allowed) is distinct from (to_jsonb(old) - v_allowed) then
    raise exception 'customer_drafts_read_only' using errcode = '42501';
  end if;
  return new;
end;
$$;

-- fields valid for the row's source on every write (keys, string values, caps). SECURITY DEFINER because client
-- writes fire it as `authenticated`, which has no usage on `private` (it calls the private helpers).
create or replace function private.customer_drafts_validate()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not private.customer_draft_fields_ok(new.fields, new.source) then
    raise exception 'invalid_fields' using errcode = '22023';
  end if;
  return new;
end;
$$;

-- Fires in name order: customer_drafts_guard, customer_drafts_validate, set_updated_at.
create trigger customer_drafts_guard
  before insert or update on public.customer_drafts
  for each row execute function private.customer_drafts_guard();

create trigger customer_drafts_validate
  before insert or update on public.customer_drafts
  for each row execute function private.customer_drafts_validate();

-- ===========================================================================
-- RLS, grants, realtime
-- ===========================================================================
alter table public.customer_drafts enable row level security;
revoke all on public.customer_drafts from public, anon, authenticated;
-- No delete grant: soft delete only (deleted_at). The guard decides which columns a client may change.
grant select, insert, update on public.customer_drafts to authenticated;
grant all on public.customer_drafts to service_role;

-- Owner-only; no deleted_at filter (DECISIONS #27: tombstones must reach the app, and soft deletes must pass).
create policy customer_drafts_select_own on public.customer_drafts
  for select to authenticated
  using (agent_id = (select auth.uid()));

-- Customer-source rows are created only by customer_signup_link().
create policy customer_drafts_insert_own on public.customer_drafts
  for insert to authenticated
  with check (agent_id = (select auth.uid()) and (select public.can_write()) and source = 'agent');

create policy customer_drafts_update_own on public.customer_drafts
  for update to authenticated
  using (agent_id = (select auth.uid()) and (select public.can_write()))
  with check (agent_id = (select auth.uid()) and (select public.can_write()));

-- The app's live view of a customer filling in the form (values, active_field, customer_seen_at). RLS applies.
alter publication supabase_realtime add table public.customer_drafts;

-- ===========================================================================
-- Private helpers for the RPCs
-- ===========================================================================

-- Serialises everything that changes one draft (open, consent, save, submit, renew).
create or replace function private.customer_draft_lock(p_draft_id uuid)
returns void
language sql
volatile
set search_path = ''
as $$
  select pg_advisory_xact_lock(hashtextextended('customer_draft|' || p_draft_id::text, 0));
$$;

-- The agent's published site (as portal_invite checks it).
create or replace function private.signup_has_site(p_agent_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.sites s
                  where s.agent_id = p_agent_id and s.deleted_at is null
                    and s.is_published and s.published_content is not null);
$$;

-- Live sign-up links of an agent (customer-source, not deleted, not completed, not expired), except p_except.
create or replace function private.signup_live_links(p_agent_id uuid, p_except uuid)
returns integer
language sql
stable
security definer
set search_path = ''
as $$
  select count(*)::int from public.customer_drafts d
   where d.agent_id = p_agent_id and d.source = 'customer' and d.deleted_at is null and d.completed_at is null
     and d.link_expires_at > now() and d.id is distinct from p_except;
$$;

-- The draft for an anon call: the Worker gate, the flag, the site (find_public_site's rule), the token, the site's
-- agent = the draft's agent; then the per-draft advisory lock and the row lock, and the re-checks: still this
-- token (a renew may have replaced it), customer-source, not deleted, not completed, link not expired, not idle
-- longer than retention.customerDraftsDays. {ok:true, draft_id} | {ok:false, error:'unavailable'|'invalid_link'}.
create or replace function private.signup_resolve(p_slug text, p_host text, p_token text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_site public.sites;
  v_hash bytea;
  v_id   uuid;
  v_d    public.customer_drafts;
begin
  if not private.portal_gate_ok() or not private.flag_enabled('customerSignup') then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_site := private.find_public_site(p_slug, p_host);
  if v_site.id is null then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  v_hash := private.token_hash(p_token);
  if v_hash is null then
    return jsonb_build_object('ok', false, 'error', 'invalid_link');
  end if;
  select l.draft_id into v_id
    from private.customer_draft_links l
    join public.customer_drafts d on d.id = l.draft_id
   where l.token_hash = v_hash and d.agent_id = v_site.agent_id;
  if v_id is null then
    return jsonb_build_object('ok', false, 'error', 'invalid_link');
  end if;

  perform private.customer_draft_lock(v_id);
  select d.* into v_d from public.customer_drafts d where d.id = v_id for update;
  if v_d.id is null
     or not exists (select 1 from private.customer_draft_links l where l.draft_id = v_id and l.token_hash = v_hash)
     or v_d.source <> 'customer' or v_d.agent_id <> v_site.agent_id
     or v_d.deleted_at is not null or v_d.completed_at is not null
     or v_d.link_expires_at is null or v_d.link_expires_at <= now()
     or v_d.updated_at <= now() - make_interval(days => private.customer_drafts_days()) then
    return jsonb_build_object('ok', false, 'error', 'invalid_link');
  end if;
  return jsonb_build_object('ok', true, 'draft_id', v_id);
end;
$$;

-- One save or submit against the per-draft limit: savesPerMinute per window of one minute from the window's first
-- call (refused calls count too). Caller holds the draft lock. true = allowed.
create or replace function private.signup_rate_ok(p_draft_id uuid)
returns boolean
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_n int;
begin
  update private.customer_draft_links l
     set save_count = case when l.save_window_started_at is null or l.save_window_started_at <= now() - interval '1 minute'
                           then 1 else l.save_count + 1 end,
         save_window_started_at = case when l.save_window_started_at is null or l.save_window_started_at <= now() - interval '1 minute'
                                       then now() else l.save_window_started_at end
   where l.draft_id = p_draft_id
  returning l.save_count into v_n;
  return coalesce(v_n, 0) between 1 and (private.customer_signup_config() ->> 'savesPerMinute')::int;
end;
$$;

-- ===========================================================================
-- Agent RPCs (authenticated)
-- ===========================================================================

-- Send the customer a link: {ok:true, draft_id, token, expires_at} | {ok:false, error:'unavailable'} (flag off)
-- | 'no_site' (no published site) | 'too_many' (openLinksPerAgent live links already). The token is returned only
-- here and stored only as sha256. Raises not_authenticated / plan_read_only.
create or replace function public.customer_signup_link()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := private.require_uid();
  v_cfg   jsonb;
  v_token text;
  v_exp   timestamptz;
  v_id    uuid;
begin
  perform private.require_can_write();
  if not private.flag_enabled('customerSignup') then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  if not private.signup_has_site(v_uid) then
    return jsonb_build_object('ok', false, 'error', 'no_site');
  end if;
  v_cfg := private.customer_signup_config();

  -- Serialise the agent's link creation so concurrent calls can't pass the cap together.
  perform pg_advisory_xact_lock(hashtextextended('customer_signup|' || v_uid::text, 0));
  if private.signup_live_links(v_uid, null) >= (v_cfg ->> 'openLinksPerAgent')::int then
    return jsonb_build_object('ok', false, 'error', 'too_many');
  end if;

  v_token := private.new_token();
  v_exp := now() + make_interval(days => (v_cfg ->> 'linkDays')::int);
  insert into public.customer_drafts (agent_id, source, link_expires_at)
  values (v_uid, 'customer', v_exp)
  returning id into v_id;
  insert into private.customer_draft_links (draft_id, token_hash) values (v_id, private.token_hash(v_token));
  return jsonb_build_object('ok', true, 'draft_id', v_id, 'token', v_token, 'expires_at', v_exp);
end;
$$;

-- Send a new link for an open sign-up: replaces the token (the old link stops working at once), extends
-- link_expires_at by linkDays and resets the save counter. Same results as customer_signup_link (too_many only when
-- reviving an expired link would pass the cap). Raises not_found unless the draft is the caller's, customer-source,
-- not deleted and not completed; not_authenticated / plan_read_only.
create or replace function public.customer_signup_renew(draft_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_uid   uuid := private.require_uid();
  v_id    uuid;
  v_d     public.customer_drafts;
  v_cfg   jsonb;
  v_token text;
  v_exp   timestamptz;
begin
  perform private.require_can_write();
  select d.id into v_id from public.customer_drafts d
   where d.id = customer_signup_renew.draft_id and d.agent_id = v_uid and d.source = 'customer'
     and d.deleted_at is null and d.completed_at is null;
  if v_id is null then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  if not private.flag_enabled('customerSignup') then
    return jsonb_build_object('ok', false, 'error', 'unavailable');
  end if;
  if not private.signup_has_site(v_uid) then
    return jsonb_build_object('ok', false, 'error', 'no_site');
  end if;
  v_cfg := private.customer_signup_config();

  -- Lock order: the agent's link lock, then the draft's lock, then the row.
  perform pg_advisory_xact_lock(hashtextextended('customer_signup|' || v_uid::text, 0));
  perform private.customer_draft_lock(v_id);
  select d.* into v_d from public.customer_drafts d where d.id = v_id for update;
  if v_d.id is null or v_d.deleted_at is not null or v_d.completed_at is not null then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  if v_d.link_expires_at <= now()
     and private.signup_live_links(v_uid, v_id) >= (v_cfg ->> 'openLinksPerAgent')::int then
    return jsonb_build_object('ok', false, 'error', 'too_many');
  end if;

  v_token := private.new_token();
  v_exp := now() + make_interval(days => (v_cfg ->> 'linkDays')::int);
  update public.customer_drafts set link_expires_at = v_exp where id = v_id;
  insert into private.customer_draft_links (draft_id, token_hash) values (v_id, private.token_hash(v_token))
  on conflict on constraint customer_draft_links_pkey do update
    set token_hash = excluded.token_hash, created_at = now(), save_window_started_at = null, save_count = 0;
  return jsonb_build_object('ok', true, 'draft_id', v_id, 'token', v_token, 'expires_at', v_exp);
end;
$$;

-- ===========================================================================
-- Anon RPCs (the Worker). Never raise for user input.
-- ===========================================================================

-- The link page: {ok:true, fields, consented, expires_at} | {ok:false, error:'unavailable'|'invalid_link'}.
-- Sets opened_at once and customer_seen_at. fields = the saved values ({} before consent), never notes.
create or replace function public.signup_open(slug text, host text, token text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_r jsonb;
  v_d public.customer_drafts;
begin
  v_r := private.signup_resolve(signup_open.slug, signup_open.host, signup_open.token);
  if not (v_r ->> 'ok')::boolean then
    return v_r;
  end if;
  update public.customer_drafts
     set opened_at = coalesce(opened_at, now()), customer_seen_at = now()
   where id = (v_r ->> 'draft_id')::uuid
  returning * into v_d;
  return jsonb_build_object(
    'ok', true,
    'fields', case when v_d.consent is not null then v_d.fields - 'notes' else '{}'::jsonb end,
    'consented', v_d.consent is not null,
    'expires_at', v_d.link_expires_at);
end;
$$;

-- The customer ticked the customer_signup notice: consent = {notice_key:'customer_signup', version, hash} checked
-- with private.resolve_consent; stores {notice_key, version, hash, locale, given_at} on the draft. Idempotent (the
-- first consent is kept). {ok:true} | {ok:false, error:'invalid', field:'consent'} | 'unavailable' | 'invalid_link'.
create or replace function public.signup_consent(slug text, host text, token text, consent jsonb)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_r jsonb;
  v_c jsonb;
begin
  v_r := private.signup_resolve(signup_consent.slug, signup_consent.host, signup_consent.token);
  if not (v_r ->> 'ok')::boolean then
    return v_r;
  end if;
  v_c := private.resolve_consent(signup_consent.consent, 'customer_signup');
  if v_c is null then
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', 'consent');
  end if;
  update public.customer_drafts
     set consent = coalesce(consent, v_c || jsonb_build_object('given_at', now())), customer_seen_at = now()
   where id = (v_r ->> 'draft_id')::uuid;
  return jsonb_build_object('ok', true);
end;
$$;

-- Autosave and heartbeat: fields is a partial object (only the keys sent are replaced; "" clears a key; unknown
-- keys and non-strings dropped; values cut to the caps; {} = heartbeat). active_field must be a field key (never
-- notes) or it is stored null. Updates customer_seen_at. At most savesPerMinute saves and submits per draft per
-- minute. {ok:true} | {ok:false, error:'consent_required'|'rate_limited'|'unavailable'|'invalid_link'}.
create or replace function public.signup_save(slug text, host text, token text, fields jsonb, active_field text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_r      jsonb;
  v_d      public.customer_drafts;
  v_active text;
begin
  v_r := private.signup_resolve(signup_save.slug, signup_save.host, signup_save.token);
  if not (v_r ->> 'ok')::boolean then
    return v_r;
  end if;
  select d.* into v_d from public.customer_drafts d where d.id = (v_r ->> 'draft_id')::uuid;
  if v_d.consent is null then
    return jsonb_build_object('ok', false, 'error', 'consent_required');
  end if;
  if not private.signup_rate_ok(v_d.id) then
    return jsonb_build_object('ok', false, 'error', 'rate_limited');
  end if;
  v_active := case when private.customer_draft_caps('customer') ? signup_save.active_field
                   then signup_save.active_field end;
  update public.customer_drafts
     set fields = private.customer_draft_merge(fields, signup_save.fields, true),
         active_field = v_active,
         customer_seen_at = now()
   where id = v_d.id;
  return jsonb_build_object('ok', true);
end;
$$;

-- Submit: the saved values overlaid by fields, then full validation, first failure
-- {ok:false, error:'invalid', field:'name|phone|email|dob|address|city|nominee|guardian'} (the sent values are then
-- saved like signup_save does, so nothing typed is lost). Under 18 by dob (Asia/Kolkata today) needs guardian =
-- {notice_key:'customer_signup_guardian', version, hash}. Success, in one transaction under the draft lock: the
-- customers row (the draft's agent; notes null), its consents rows (customer_signup from the draft's stored consent,
-- given_at kept; customer_signup_guardian for a minor), the draft completed (customer_id set, fields {}, active_field
-- null), the link deleted, audit customer_signup.submit. {ok:true, agent_name} (the profile's full_name). Also
-- 'consent_required', 'rate_limited' (counts with the saves), 'unavailable', 'invalid_link' (incl. a second submit).
create or replace function public.signup_submit(slug text, host text, token text, fields jsonb, guardian jsonb default null)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_r        jsonb;
  v_d        public.customer_drafts;
  v_m        jsonb;
  v_name     text;
  v_phone    text;
  v_email    text;
  v_dob_text text;
  v_dob      date;
  v_address  text;
  v_city     text;
  v_nominee  text;
  v_today    date := private.today_ist();
  v_minor    boolean := false;
  v_guardian jsonb;
  v_field    text;
  v_cust     uuid;
  v_agent    text;
begin
  v_r := private.signup_resolve(signup_submit.slug, signup_submit.host, signup_submit.token);
  if not (v_r ->> 'ok')::boolean then
    return v_r;
  end if;
  select d.* into v_d from public.customer_drafts d where d.id = (v_r ->> 'draft_id')::uuid;
  if v_d.consent is null then
    return jsonb_build_object('ok', false, 'error', 'consent_required');
  end if;
  if not private.signup_rate_ok(v_d.id) then
    return jsonb_build_object('ok', false, 'error', 'rate_limited');
  end if;

  v_m        := private.customer_draft_merge(v_d.fields, signup_submit.fields, false);
  v_name     := btrim(coalesce(v_m ->> 'name', ''));
  v_phone    := private.normalize_in_phone(v_m ->> 'phone');
  v_email    := nullif(lower(btrim(coalesce(v_m ->> 'email', ''))), '');
  v_dob_text := nullif(btrim(coalesce(v_m ->> 'dob', '')), '');
  v_dob      := private.try_date(v_dob_text);
  v_address  := nullif(btrim(coalesce(v_m ->> 'address', '')), '');
  v_city     := nullif(btrim(coalesce(v_m ->> 'city', '')), '');
  v_nominee  := nullif(btrim(coalesce(v_m ->> 'nominee', '')), '');

  if char_length(v_name) < 1 or char_length(v_name) > 80 then
    v_field := 'name';
  elsif v_phone is null then
    v_field := 'phone';
  elsif v_email is not null and (char_length(v_email) > 120
        or v_email !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$') then
    v_field := 'email';
  elsif v_dob_text is not null and (v_dob is null or v_dob > v_today or v_dob < date '1900-01-01') then
    v_field := 'dob';
  elsif v_address is not null and char_length(v_address) > 200 then
    v_field := 'address';
  elsif v_city is not null and char_length(v_city) > 60 then
    v_field := 'city';
  elsif v_nominee is not null and char_length(v_nominee) > 80 then
    v_field := 'nominee';
  else
    v_minor := v_dob is not null and (v_dob + interval '18 years')::date > v_today;
    if v_minor then
      v_guardian := private.resolve_consent(signup_submit.guardian, 'customer_signup_guardian');
      if v_guardian is null then
        v_field := 'guardian';
      end if;
    end if;
  end if;

  if v_field is not null then
    update public.customer_drafts
       set fields = private.customer_draft_merge(fields, signup_submit.fields, true), customer_seen_at = now()
     where id = v_d.id;
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', v_field);
  end if;

  insert into public.customers (agent_id, name, phone, email, dob, address, city, nominee, notes)
  values (v_d.agent_id, v_name, v_phone, v_email, v_dob, v_address, v_city, v_nominee, null)
  returning id into v_cust;

  insert into public.consents (agent_id, principal_kind, customer_id, notice_key, notice_version, notice_hash, locale,
                               channel, given_at)
  values (v_d.agent_id, 'customer', v_cust, v_d.consent ->> 'notice_key', (v_d.consent ->> 'version')::int,
          v_d.consent ->> 'hash', v_d.consent ->> 'locale', 'web',
          coalesce((v_d.consent ->> 'given_at')::timestamptz, now()));
  if v_guardian is not null then
    insert into public.consents (agent_id, principal_kind, customer_id, notice_key, notice_version, notice_hash, locale, channel)
    values (v_d.agent_id, 'customer', v_cust, v_guardian ->> 'notice_key', (v_guardian ->> 'version')::int,
            v_guardian ->> 'hash', v_guardian ->> 'locale', 'web');
  end if;

  update public.customer_drafts
     set completed_at = now(), customer_id = v_cust, fields = '{}'::jsonb, active_field = null, customer_seen_at = now()
   where id = v_d.id;
  delete from private.customer_draft_links l where l.draft_id = v_d.id;

  insert into public.audit_log (actor_id, actor_kind, action, entity, entity_id, changes)
  values (null, 'system', 'customer_signup.submit', 'customer_drafts', v_d.id, '{}'::jsonb);

  select p.full_name into v_agent from public.profiles p where p.id = v_d.agent_id;
  return jsonb_build_object('ok', true, 'agent_name', v_agent);
end;
$$;

-- ===========================================================================
-- Retention (part of the planned private.retention_run(); its daily pg_cron schedule needs the user's approval)
-- ===========================================================================

-- retention.customerDraftsDays (30): (1) completed or soft-deleted drafts not touched for that long are hard-deleted
-- (their link rows cascade); (2) open drafts idle for that long are erased (fields, active_field and consent cleared,
-- the link deleted) and soft-deleted, so the app's sync receives the tombstone and drops the row; step 1 removes the
-- tombstone after another customerDraftsDays. Writes one audit row (counts only) when anything changed.
-- Returns {erased, deleted}.
create or replace function private.purge_customer_drafts()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_cut     timestamptz := now() - make_interval(days => private.customer_drafts_days());
  v_ids     uuid[];
  v_deleted int;
  v_erased  int;
begin
  delete from public.customer_drafts d
   where d.updated_at < v_cut and (d.deleted_at is not null or d.completed_at is not null);
  get diagnostics v_deleted = row_count;

  with x as (
    update public.customer_drafts d
       set fields = '{}'::jsonb, active_field = null, consent = null, deleted_at = now()
     where d.updated_at < v_cut and d.deleted_at is null and d.completed_at is null
    returning d.id)
  select coalesce(array_agg(x.id), '{}'::uuid[]) into v_ids from x;
  v_erased := cardinality(v_ids);
  delete from private.customer_draft_links l where l.draft_id = any (v_ids);

  if v_deleted + v_erased > 0 then
    insert into public.audit_log (actor_id, actor_kind, action, entity, changes)
    values (null, 'system', 'retention.customer_drafts', 'customer_drafts',
            jsonb_build_object('erased', v_erased, 'deleted', v_deleted));
  end if;
  return jsonb_build_object('erased', v_erased, 'deleted', v_deleted);
end;
$$;

-- ===========================================================================
-- Grants
-- ===========================================================================
revoke all on function private.customer_signup_config() from public, anon, authenticated;
revoke all on function private.customer_drafts_days() from public, anon, authenticated;
revoke all on function private.customer_draft_caps(text) from public, anon, authenticated;
revoke all on function private.customer_draft_fields_ok(jsonb, text) from public, anon, authenticated;
revoke all on function private.customer_draft_merge(jsonb, jsonb, boolean) from public, anon, authenticated;
revoke all on function private.customer_drafts_guard() from public, anon, authenticated;
revoke all on function private.customer_drafts_validate() from public, anon, authenticated;
revoke all on function private.customer_draft_lock(uuid) from public, anon, authenticated;
revoke all on function private.signup_has_site(uuid) from public, anon, authenticated;
revoke all on function private.signup_live_links(uuid, uuid) from public, anon, authenticated;
revoke all on function private.signup_resolve(text, text, text) from public, anon, authenticated;
revoke all on function private.signup_rate_ok(uuid) from public, anon, authenticated;
revoke all on function private.purge_customer_drafts() from public, anon, authenticated;

revoke all on function public.customer_signup_link() from public, anon, authenticated;
revoke all on function public.customer_signup_renew(uuid) from public, anon, authenticated;
revoke all on function public.signup_open(text, text, text) from public, anon, authenticated;
revoke all on function public.signup_consent(text, text, text, jsonb) from public, anon, authenticated;
revoke all on function public.signup_save(text, text, text, jsonb, text) from public, anon, authenticated;
revoke all on function public.signup_submit(text, text, text, jsonb, jsonb) from public, anon, authenticated;

grant execute on function public.customer_signup_link() to authenticated;
grant execute on function public.customer_signup_renew(uuid) to authenticated;

grant execute on function public.signup_open(text, text, text) to anon;
grant execute on function public.signup_consent(text, text, text, jsonb) to anon;
grant execute on function public.signup_save(text, text, text, jsonb, text) to anon;
grant execute on function public.signup_submit(text, text, text, jsonb, jsonb) to anon;
