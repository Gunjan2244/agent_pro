-- LOCAL TESTS ONLY. Catalog follow-ups (DECISIONS #35, BACKEND.md "catalog_sync contract"):
-- effective_flags() / flag_overrides, catalog_reserved_slugs(), the sites/profiles CHECKs from the
-- catalog, and deprecated interests (submit_enquiry active-only, publish_site accepts them).
-- Every block rolls back, so nothing here leaks into other files.
\set A '''aaaaaaaa-0000-4000-8000-00000000000a'''
\set C '''cccccccc-0000-4000-8000-00000000000c'''

-- ---------------------------------------------------------------------------
-- Feature flags: defaults (`flags`, from catalog_sync) + overrides (`flag_overrides`, never migrated)
-- ---------------------------------------------------------------------------
select tests.ok((select value from public.app_config where key = 'flag_overrides') = '{}'::jsonb,
  '#F flag_overrides row seeded as {} by the schema migration');
select tests.ok(public.effective_flags() = (select value from public.app_config where key = 'flags'),
  '#F effective_flags() = flags while there are no overrides');

begin;
update public.app_config set value = '{"customDomains": false, "servicePlan": false, "notAFlag": "off", "newFlag": true}'
 where key = 'flag_overrides';
select tests.ok((public.effective_flags() -> 'customDomains') = 'false'::jsonb
  and (select value -> 'customDomains' from public.app_config where key = 'flags') = 'true'::jsonb,
  '#F an override wins over the catalog default (flags row unchanged)');
select tests.ok((public.effective_flags() -> 'siteBuilder') = 'true'::jsonb,
  '#F keys without an override keep the default');
select tests.ok(not (public.effective_flags() ? 'notAFlag') and (public.effective_flags() -> 'newFlag') = 'true'::jsonb,
  '#F non-boolean override values are ignored; boolean ones are merged in');
update public.subscriptions set trial_ends_at = now() - interval '100 days' where agent_id = :C;
select tests.ok(public.entitlement(:C) = 'active',
  '#F servicePlan override false -> entitlement active (flag_enabled reads effective_flags)');

-- A catalog change re-runs catalog_sync: defaults are rewritten, overrides survive.
update public.app_config set value = jsonb_set(value, '{siteBuilder}', 'false') where key = 'flags';
\i :catalog_sync
select tests.ok((select value -> 'siteBuilder' from public.app_config where key = 'flags') = 'true'::jsonb,
  '#F re-applied catalog_sync rewrote the flags defaults');
select tests.ok((select value from public.app_config where key = 'flag_overrides')
    = '{"customDomains": false, "servicePlan": false, "notAFlag": "off", "newFlag": true}'::jsonb
  and (public.effective_flags() -> 'customDomains') = 'false'::jsonb,
  '#F re-applied catalog_sync left flag_overrides intact');
-- The schema migration's seed is `on conflict do nothing`, so re-running it can't reset overrides either.
insert into public.app_config (key, value) values ('flag_overrides', '{}'::jsonb) on conflict (key) do nothing;
select tests.ok((public.effective_flags() -> 'customDomains') = 'false'::jsonb,
  '#F re-running the flag_overrides seed keeps existing overrides');

select tests.as_agent(:A);
select tests.ok((public.effective_flags() -> 'customDomains') = 'false'::jsonb,
  '#F authenticated can call effective_flags() and sees overrides');
reset role;
select tests.as_anon();
select tests.throws($$select public.effective_flags()$$, '#F anon cannot call effective_flags()', '%permission denied%');
reset role;
rollback;

-- ---------------------------------------------------------------------------
-- Reserved slugs come from the catalog (catalog_reserved_slugs())
-- ---------------------------------------------------------------------------
select tests.ok('admin' = any (public.catalog_reserved_slugs()) and 'agent-pro' = any (public.catalog_reserved_slugs()),
  '#R catalog_reserved_slugs() holds the catalog list');
select tests.ok(private.is_reserved_slug('ADMIN') and not private.is_reserved_slug('ramesh-sharma'),
  '#R is_reserved_slug: case-insensitive, normal slugs pass');

begin;
-- Simulate a catalog change: a new reserved slug must be enforced without touching rpcs.sql.
create or replace function public.catalog_reserved_slugs()
returns text[] language sql immutable set search_path = '' as $$ select array['admin', 'zzz-catalog-test']::text[] $$;
select tests.as_agent(:A);
select tests.ok(not public.is_slug_available('zzz-catalog-test'), '#R is_slug_available rejects a slug added to the catalog list');
select tests.throws($$update public.sites set slug = 'zzz-catalog-test'$$,
  '#R slug guard rejects a slug added to the catalog list', '%slug_reserved%');
select tests.ok(public.is_slug_available('staff'), '#R a slug removed from the catalog list becomes available');
rollback;

-- ---------------------------------------------------------------------------
-- CHECK constraints from the catalog on sites and profiles (client path: RLS as agent A)
-- ---------------------------------------------------------------------------
begin;
select tests.as_agent(:A);
select tests.throws($$update public.sites set template_key = 'fancy'$$,
  '#C sites.template_key must be a templates/index.json key', '%sites_template_key_check%');
select tests.throws($$update public.sites set accent = '#1F4AA8'$$,
  '#C sites.accent must be an accentSwatches key (not a hex)', '%sites_accent_check%');
select tests.throws($$update public.sites set locale = 'fr'$$,
  '#C sites.locale must be a catalog locale', '%sites_locale_check%');
select tests.throws($$update public.profiles set preferred_language = 'fr'$$,
  '#C profiles.preferred_language must be a catalog locale', '%profiles_preferred_language_check%');
select tests.lives($$update public.sites set template_key = 'minimal', accent = 'rust', locale = 'hi'$$,
  '#C valid template, accent and locale accepted');
select tests.lives($$update public.sites set accent = null, locale = null$$,
  '#C accent and locale stay nullable');
select tests.lives($$update public.profiles set preferred_language = 'hi'$$,
  '#C valid preferred_language accepted');
select tests.lives($$update public.profiles set preferred_language = null$$,
  '#C preferred_language stays nullable');
rollback;

-- ---------------------------------------------------------------------------
-- Deprecated interests: rejected by submit_enquiry, still valid for publish_site and the CHECK.
-- Simulates marking `ulip` "deprecated": true (catalog_interest_keys() drops it, _all keeps it).
-- ---------------------------------------------------------------------------
begin;
create or replace function public.catalog_interest_keys()
returns text[] language sql immutable set search_path = '' as $$
  select array(select k from unnest(public.catalog_interest_keys_all()) k where k <> 'ulip')
$$;
select tests.ok('ulip' = any (public.catalog_interest_keys_all()) and not ('ulip' = any (public.catalog_interest_keys())),
  '#D setup: ulip deprecated (in _all, not in active keys)');

select tests.as_anon();
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Visitor', '9833300001', null, 'ulip', null, null, tests.consent('enquiry_form'))
  = '{"ok": false, "error": "invalid", "field": "interest"}'::jsonb,
  '#D submit_enquiry rejects a deprecated interest');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Visitor', '9833300001', null, 'term', null, null, tests.consent('enquiry_form')) = '{"ok": true}'::jsonb,
  '#D submit_enquiry still accepts an active interest');
reset role;

select tests.as_agent(:A);
update public.sites
   set draft_content = jsonb_set(draft_content, '{services}', '{"offered": ["ulip", "term"], "items": [{"interest": "ulip"}]}')
 where agent_id = :A;
select tests.ok((public.publish_site() ->> 'ok')::boolean,
  '#D publish_site accepts content with a deprecated interest (catalog_interest_keys_all)');
update public.sites
   set draft_content = jsonb_set(draft_content, '{services}', '{"offered": ["crypto"]}')
 where agent_id = :A;
select tests.ok(public.publish_site() -> 'errors' @> '[{"section": "services", "field": "offered", "rule": "option"}]'::jsonb,
  '#D publish_site still rejects an interest that was never in the catalog');
select tests.lives($$insert into public.enquiries (agent_id, name, source, status, interest)
                     values ('aaaaaaaa-0000-4000-8000-00000000000a', 'Old lead', 'walk_in', 'new', 'ulip')$$,
  '#D enquiries_interest_check keeps deprecated keys valid');
reset role;
rollback;
