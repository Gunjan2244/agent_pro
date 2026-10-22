# Backend — Supabase `AGENT_PRO`

- **Project:** `AGENT_PRO` (ref `rpqatijolcajehshoikc`) in org letssee, ap-south-1 (Mumbai), Postgres 17. It was created by the user on 2026-10-16. **All 8 migrations below are applied** (2026-10-16): 7 through the Supabase connector, and `rpcs` by the user in the Dashboard SQL Editor, because the connector auto-declines it as destructive (it contains `delete from auth.users`). So the remote migration history lists 7 entries under connector-assigned versions; run `supabase migration repair` before any future `supabase db push`. `20261016220600_privacy.sql` and then `20261016220836_catalog_sync.sql` were applied by the user in the SQL Editor on 2026-10-17 (P1 release). `20261017132241_companies_offers.sql` and `20261018073025_catalog_sync.sql` (companies and offers, website v2; the catalog_sync includes the Add to home screen toggle, so `20261017050728_catalog_sync.sql` was skipped) were pasted by the user in the SQL Editor on 2026-10-18 (go-live; advisors: no errors). `20261020090000_preview_offers.sql` is live, and the user pasted `20261022074432_catalog_sync.sql` and `20261022090000_multi_company.sql` in the SQL Editor on 2026-10-22. **Not applied yet:** `20261022162929_catalog_sync.sql` (DECISIONS #93; the user pastes it).
- **Migrations** live in `supabase/migrations/`. Files named `*_catalog_sync.sql` are generated from `shared/` by `./gradlew generateShared`; never edit them by hand.
- **After every migration:** run `get_advisors` (security and performance), fix what they report, and update this doc.
- **Local tests:** `supabase/tests/run.sh` applies everything to a throwaway Postgres container and runs the checks in TESTING.md, "Database". See [Local test harness](#local-test-harness).

## Migrations (apply in this order)
| File | Contents |
|---|---|
| `20261015100000_schema.sql` | Opts out of Supabase's default `anon`/`authenticated` grants in `public`; creates the `private` schema; every table, its constraints, FKs, indexes and `set_updated_at` triggers; seeds `app_config.flag_overrides` as `{}` once |
| `20261015100100_entitlement.sql` | `entitlement()`, `can_write()`, `my_entitlement()`, `effective_flags()`, the config helpers and the signup trigger |
| `20261015100200_rls.sql` | Enables RLS on every table; grants and policies |
| `20261015100300_rpcs.sql` | The reserved-slug guard (the list comes from the generated `catalog_reserved_slugs()`), private helpers and every RPC below |
| `20261015100400_storage.sql` | The `site-media` bucket and its `storage.objects` policies |
| `20261015100500_realtime.sql` | Adds tables to the `supabase_realtime` publication |
| `<timestamp>_catalog_sync.sql` | **Generated.** Must sort after the files above (DECISIONS #23). See the contract below |
| `20261016035136_revoke_rls_auto_enable.sql` | Advisor fix: revokes API execute on Supabase's `public.rls_auto_enable()` event-trigger helper. Guarded, so it's a no-op where the function doesn't exist (the local tests) |
| `20261016220600_privacy.sql` | P1-2 (PRIVACY.md): tables `consents`, `data_requests`, `audit_log` with RLS and grants; `submit_enquiry` with `consent` (drops the 8-argument overload); `submit_data_request`, `record_consent`, `update_data_request`; the signup trigger records the sign-up consent; the `payments.raw` minimisation trigger. See [Privacy](#privacy-p1-2) |
| `20261016220836_catalog_sync.sql` | **Generated**, sorts after `privacy` because it adds the CHECKs on `consents` and `data_requests`. Replaces the never-applied `20261016214959_catalog_sync.sql` (deleted) |
| `20261017050728_catalog_sync.sql` | **Generated.** Its only change is the new `contact.showInstallButton` toggle inside `site_schema()` (DECISIONS #65). Until it's applied, `publish_site` simply doesn't check that key (validation walks the schema's fields and ignores unknown keys), so the app and web work either way. Applying it makes `publish_site` reject a non-boolean value |
| `20261017132241_companies_offers.sql` | Phase 8b-2 (OFFERS.md): tables `companies`, `offers`, `admins`, `company_memberships` with RLS and grants; `is_admin()`, `is_admin_for()`, `can_manage_company_media()`; guard, actor-stamp and audit triggers; the `company-media` bucket and its policies; `validate_fields` gains the offers rules; `validate_site_offers`, the payload's `published_at`, `company` (`{disclaimer}` only) and `offers` (picked or automatic); `publish_site` with offer errors and warnings; `offers.internal_name` (admin-only); the public text check `private.public_text_violation` in the guards and at publish (DECISIONS #77); the agent and admin RPCs; the anon sitemap RPC `list_public_sites()` (DECISIONS #79); the signup trigger skips admin accounts. See [Companies and offers](#companies-and-offers-8b-2) |
| `20261018073025_catalog_sync.sql` | **Generated**, sorts after `companies_offers` because it adds the five offers CHECKs (DECISIONS #72). It also carries the 8b-1 offer functions, `site_schema()` with `hero.featuredOffer` and the `offers` section, the `sites.template_key` CHECK with the 5 templates, and `catalog_template_families()`, `catalog_template_family(text)` and `catalog_blocked_terms()` (the public text check's terms). Replaces the never-applied `20261017085151_catalog_sync.sql` and `20261018060943_catalog_sync.sql` (deleted) |
| `20261020090000_preview_offers.sql` | WP5a (DECISIONS #84): `preview_site_offers(content, template_key)`, the signed-in preview of a draft's `company` and `offers`. It reuses `private.site_offers_payload` unchanged (no existing function is replaced) and grants execute to `authenticated` only. Apply after `20261018073025_catalog_sync.sql`. Not applied to `AGENT_PRO` yet (the user pastes it) |
| `20261022074432_catalog_sync.sql` | **Generated.** Adds `catalog_insurance_types()` (DECISIONS #89). Applied to `AGENT_PRO` on 2026-10-22. **It was regenerated in place afterwards** to add the field-less `companies` section to `site_schema()`, so the copy in the repo differs from the live one: the live `site_schema()` has no `companies` entry. Don't re-apply it; `20261022162929_catalog_sync.sql` supersedes it |
| `20261022090000_multi_company.sql` | DECISIONS #89, #90: `company_memberships.insurance_types` (existing rows backfilled `{life}`), one pending or approved row per agent **and company** plus at most one pending "not listed" row per agent, the new membership RPCs (`request_company` with `insurance_types`, `withdraw_company_request(membership_id)`, `set_membership_types`, `my_company_offers` → `{memberships, offers}`), offers from every approved company, the strictest custom-offer switch, the payload's `companies` list, the admin queue's types and per-company `previous`. Drops `request_company(uuid, text, text)`, `withdraw_company_request()`, `private.current_membership` and the old `private.resolve_offer_ref`. See [Companies and offers](#companies-and-offers-8b-2). Applied to `AGENT_PRO` on 2026-10-22 |
| `20261022162929_catalog_sync.sql` | **Generated** (DECISIONS #93), sorts after `multi_company`. It re-creates everything a catalog_sync emits with `create or replace` / drop-and-add, so it **supersedes `20261022074432` entirely**. What changes on `AGENT_PRO`: (1) `enquiries_interest_check` and `catalog_interest_keys()` / `catalog_interest_keys_all()` gain `motor`, `home`, `travel`, `business` and `accident` (before `other`), so `submit_enquiry`, `publish_site` and the offers guard accept them; (2) `site_schema()` gains the `companies` section that the live copy lacks (the in-place edit above). Nothing else differs from the repo's `20261022074432`. Not applied yet (the user pastes it in the SQL Editor; then the advisors, which should show no new WARN since no function is added) |

Internal helpers live in the **`private` schema**, which the Data API doesn't expose and the API roles can't use. These include `set_updated_at`, `handle_new_user`, (8b-2) `try_date`, `today_ist`, `company_logo_url`, `offer_phase`, `valid_localized`, `public_text_terms`, `public_text_match`, `public_text_violation`, `membership_json`, (#89) `ordered_insurance_types`, `normalize_insurance_types`, `require_admin`, `stamp_actor`, `companies_guard`, `offers_guard`, `memberships_guard`, `audit_row`, `validate_site_offers`, `resolve_offer_ref`, `site_offers_payload`, `service_pricing`, `flag_enabled`, `find_public_site`, `public_site_payload`, `validate_site_content`, `validate_fields`, `normalize_in_phone`, `normalize_host`, `is_reserved_slug`, `sites_slug_guard`, `require_uid`, `require_can_write`, and (P1-2) `resolve_consent`, `retention_config`, `minimise_payment_raw` and `payments_minimise_raw`.

## `catalog_sync` contract (what the generated migration must emit)
The migrations call these objects by name, so `generateShared` must produce exactly the following:

The generated file is built from `shared/catalog.json`, `shared/site-schema.json`, `shared/templates/index.json` and (when it exists) `shared/offer-schema.json`, plus the privacy notice texts in `shared/i18n` and the notice hashes in `mobile/build-logic/catalog-keys.lock.json`; its line-3 hash covers those files and the current notice hashes (SHARED_DEFINITIONS.md, Codegen).

1. **CHECK constraints** named `<table>_<column>_check`, written as `alter table public.<t> drop constraint if exists <t>_<c>_check;` followed by `alter table public.<t> add constraint <t>_<c>_check check (<c> in (...));`. Deprecated keys stay in the list. A null passes the check, so the nullable columns stay nullable.
   | Constraint | Values from | Column |
   |---|---|---|
   | `enquiries_status_check` | `enquiryStatuses` | `text not null` |
   | `enquiries_interest_check` | `interests` | `text`, nullable |
   | `enquiries_source_check` | `enquirySources` | `text not null` |
   | `policies_premium_mode_check` | `premiumModes` | `text not null` |
   | `policies_status_check` | `policyStatuses` | `text not null` |
   | `site_domains_status_check` | `domainStatuses` | `text not null` |
   | `sites_template_key_check` | the keys in `templates/index.json` | `text not null` |
   | `sites_accent_check` | `accentSwatches` (the swatch **key**, e.g. `indigo`, never the hex) | `text`, nullable |
   | `sites_locale_check` | `locales` | `text`, nullable |
   | `profiles_preferred_language_check` | `locales` | `text`, nullable |
   | `consents_notice_key_check` | `privacyNotices` | `text not null` |
   | `data_requests_kind_check` | `dataRequestKinds` | `text not null` |
   | `data_requests_status_check` | `dataRequestStatuses` | `text not null` |
   | `offers_status_check`, `offers_cta_kind_check` | `offerStatuses`, `offerCtaKinds` | `text not null` |
   | `admins_role_check` | `adminRoles` | `text not null` |
   | `company_memberships_status_check`, `company_memberships_reject_reason_check` | `membershipStatuses`, `membershipRejectReasons` | `text not null`; `reject_reason` nullable |

   The privacy and offers CHECKs are emitted only when the catalog has the list (they're optional lists in the generator; the real catalog has them all).
2. **Functions.** Each is `language sql immutable set search_path = ''`, followed by `revoke all on function … from public, anon, authenticated;`. Only SECURITY DEFINER code calls them, so no grants are needed.
   - `public.catalog_interest_keys() returns text[]`: the **active** interest keys (deprecated ones left out). Used by `submit_enquiry`, so a deprecated interest can't be submitted.
   - `public.catalog_interest_keys_all() returns text[]`: every interest key, deprecated ones included. Used by `publish_site`, so content that already uses a deprecated interest still publishes.
   - `public.catalog_premium_mode_months(p_mode text) returns integer`: `months` per `premiumModes` key, or null if the key is unknown. Used by `mark_premium_paid`.
   - `public.catalog_reserved_slugs() returns text[]`: `catalog.reservedSlugs`. Used by `private.is_reserved_slug` (the slug trigger and `is_slug_available`).
   - `public.site_schema() returns jsonb`: the contents of `shared/site-schema.json`, verbatim. Used by `publish_site`.
   - `public.catalog_locales() returns text[]`: the active `locales` keys in display order (`{en,hi}`), for content that must exist in every language (company offers going live, company names).
   - `public.catalog_default_locale() returns text`: the first of them (`en`).
   - **Offers (OFFERS.md; generated since 8b-1, used from 8b-2):**
     - `public.offer_schema() returns jsonb`: `shared/offer-schema.json`, verbatim, for the offers guard trigger;
     - `public.catalog_offer_cta_kinds() returns text[]`: the active `offerCtaKinds` (admins, company offers);
     - `public.catalog_offer_cta_kinds_agent() returns text[]`: the active ones with `agentAllowed` (`{enquire,call}`), for custom offers in `publish_site`;
     - `public.catalog_membership_reject_reasons() returns text[]`: the active `membershipRejectReasons`, for `admin_review_membership`;
     - `public.catalog_insurance_types() returns text[]`: the active `insuranceTypes` keys **in display order** (`order`), for the membership RPCs and guard, and the order types are listed in (DECISIONS #89);
     - `public.catalog_offer_limits() returns jsonb`: `catalog.offers` verbatim (`maxSectionItems`, `maxValidityDays`, `logoMaxBytes`, `imageMaxBytes`, `imageAspect`, `logoMaxPx`).
   - **Privacy (PRIVACY.md; generated since P1-1, used from P1-2):**
     - `public.catalog_privacy_notice(p_key text) returns jsonb`: the **current** version of an active notice, `{"version": <int>, "hash_en": "<64 hex>", "hash_hi": "<64 hex>"}` (one `hash_<locale>` per catalog locale); null for an unknown or deprecated key.
     - `public.catalog_privacy_notice_hash(p_key text, p_version integer, p_locale text) returns text`: the text hash of **any released** version (every version in the lockfile, plus the current one) in that locale; null if unknown.
     - `public.catalog_privacy_notice_locale(p_key text, p_version integer, p_hash text) returns text`: the locale whose text of that released version has this hash; null if the hash matches none. This is how the server verifies a client's `{notice_key, version, hash}` and learns the language the person read.
     - `public.catalog_data_request_kinds() returns text[]` and `public.catalog_data_request_statuses() returns text[]`: the **active** keys of `dataRequestKinds` / `dataRequestStatuses`, for `submit_data_request` and `update_data_request` (and P2's `create_platform_request`).
3. **`app_config` upsert:** `insert into public.app_config (key, value) values ('flags', …), ('pricing', …), ('retention', …) on conflict (key) do update set value = excluded.value;`
   - `flags` is `{"<featureKey>": <default bool>, …}`, built from `catalog.features`. These are only the **defaults**: every `catalog_sync` rewrites them.
   - `pricing` is `catalog.pricing`, verbatim.
   - `retention` is `catalog.retention`, verbatim: the retention periods the P2 retention job reads (there is no enquiry period, DECISIONS #54).
   - It never writes `flag_overrides` (see [Feature flags](#feature-flags)).

**Generated data the backend can't run without:**
- `app_config.pricing` is required by the signup trigger, by `entitlement()` once a trial or period has ended, and by `my_entitlement()`. Without it they raise `app_config.pricing.service is missing`.
- `site_schema()`, `catalog_interest_keys()` and `catalog_interest_keys_all()` are required by `publish_site` and `submit_enquiry`, and `catalog_reserved_slugs()` by every write to `sites.slug` and by `is_slug_available`.
- Apply `catalog_sync` in the same deploy as the schema migrations.

**Consent validation contract (`record_consent`, `submit_enquiry` and the signup trigger; implemented in `private.resolve_consent`).** A client sends `{notice_key, version, hash}` for the text it showed (web: from `shared/generated/privacy-notices.json`; app: `PrivacyNotice.consent(locale)`). The RPC accepts it only if `catalog_privacy_notice(notice_key)` is not null, `version = (catalog_privacy_notice(notice_key)->>'version')::int` (new consents only for the current version), and `catalog_privacy_notice_locale(notice_key, version, hash)` is not null; it stores `notice_key`, `notice_version`, `notice_hash` and that locale in `consents`. Anything else is `{ok:false, error:'invalid', field:'consent'}`. The payload must be a JSON object with `notice_key` a string equal to the notice the RPC expects, `version` a JSON number (an integer; `"1"` is rejected) and `hash` 64 **lowercase** hex characters. Never compute or hard-code a hash in SQL.

**Offers CHECKs (built in 8b-2, DECISIONS #72).** `CHECKS` has `offers_status_check` ← `offerStatuses`, `offers_cta_kind_check` ← `offerCtaKinds`, `admins_role_check` ← `adminRoles`, `company_memberships_status_check` ← `membershipStatuses` and `company_memberships_reject_reason_check` ← `membershipRejectReasons` (each emitted only when the catalog has the list). The tables come from `20261017132241_companies_offers.sql`, which sorts before the catalog_sync that adds them; a catalog_sync never alters a table that doesn't exist yet.

**Privacy CHECKs (built in P1-2).** `CHECKS` in `CatalogSyncGenerator.kt` has `consents.notice_key` ← `privacyNotices`, `data_requests.kind` ← `dataRequestKinds` and `data_requests.status` ← `dataRequestStatuses` (all keys, deprecated included). The catalog hash covers `shared/`, not the generator's code, so `generateShared` reports "up to date" after a generator-only change: a never-applied latest `*_catalog_sync.sql` can be deleted and regenerated (done for `20261016214959`), otherwise generator changes land with the next catalog change.

### Feature flags
- **`app_config.flags`** holds the catalog defaults. It's written only by `catalog_sync`, which overwrites it on every catalog change.
- **`app_config.flag_overrides`** holds manual server-side overrides, for example `{"customDomains": false}`. The schema migration seeds it once as `{}` (`on conflict (key) do nothing`), and no migration writes it after that. Set it with the service role.
- **`public.effective_flags() → jsonb`** returns `flags || flag_overrides`: an override wins. Only boolean override values count, and a missing or non-object row counts as `{}`. It's `stable`, `security invoker` (`app_config` is readable by `authenticated`), with `search_path = ''`, and is granted to `authenticated` and `service_role`.
- **Everything reads the effective flags:** `private.flag_enabled` (used by `entitlement()` and `my_entitlement()` for `servicePlan`), the app's `FeatureFlags` and the Edge Functions (DECISIONS #35).

## Conventions
- **Every agent-owned table has these columns:**
  - `id uuid pk default gen_random_uuid()`
  - `agent_id uuid not null references auth.users on delete cascade`. **Exception:** in `orders` and `payments`, `agent_id` is nullable with `on delete set null`, so the tax records survive account deletion, anonymised.
  - `created_at timestamptz not null default now()`
  - `updated_at timestamptz not null default now()`, which the `set_updated_at` trigger also sets on insert and on update
  - `deleted_at timestamptz` (soft delete, so incremental sync sees deletions)
- **`profiles`** uses `id = auth.users.id` instead of `agent_id`. It has no `deleted_at`.
- **RLS is on for every table.** The default policy is owner-only: `agent_id = (select auth.uid())`.
- **Soft delete and RLS.** Select policies do **not** hide rows where `deleted_at` is set. Clients filter `deleted_at is null` themselves. There are two reasons:
  1. Postgres checks the result of an `UPDATE` against the SELECT policies, so a `deleted_at is null` select policy makes every soft delete (and its Undo) fail with "new row violates row-level security policy". This was verified on Postgres 15.
  2. `SyncEngine` has to receive the tombstones to remove rows from Room.
- **Insert, update and delete policies** on client-writable tables also require `(select public.can_write())`. That's true when the entitlement is `trial`, `active` or `grace` (BILLING.md). Select policies have no such requirement, so lapsed agents can still read and export.
- **Function calls in policies.** `auth.uid()` and `can_write()` are always wrapped in `(select …)`, so they're evaluated once per statement.
- **`anon` has no table privileges.** Public access goes only through the RPCs below.
- **Default privileges.** The schema migration revokes Supabase's default grants to `anon` and `authenticated` on new tables, sequences and functions in `public`, and the default `PUBLIC` execute on functions. Every exposure is therefore an explicit `grant`. `service_role` keeps `all` on every table.
- **Cross-agent references are impossible.** Client-writable relations use composite FKs `(x_id, agent_id) → parent (id, agent_id)`:
  - `enquiries → sites` and `enquiries → customers`
  - `customers.source_enquiry_id → enquiries`
  - `policies → customers`
  - `premium_payments → policies`
  - `site_domains`, `site_daily_views → sites`
- **Enum-like text columns** get their CHECK constraints from the `catalog_sync` migration.
  - The internal value lists from BILLING.md and DOMAINS.md are hand-written CHECKs in the schema migration: `orders.kind`, `orders.status`, `orders.step`, `payments.status`, `currency` and `provider`, `domain_quotes.kind`, `site_domains.kind`, `subscriptions.plan`, and `registrant_contacts.country`.
  - These lists aren't in the catalog.
- **Indexes:**
  - `(agent_id, updated_at)` on every agent table, for sync. `profiles` has `(updated_at)`.
  - Every FK has a covering index.
  - `enquiries (site_id, phone, created_at)` supports the rate limit.

## Tables
| Table | Key columns (beyond the conventions) |
|---|---|
| `profiles` | `id = auth.users.id` (pk); full_name, phone, email, designation, agency_code, branch, city, photo_url, preferred_language (nullable, catalog `locales`). Created by a trigger on signup |
| `sites` | slug (unique, `^[a-z0-9-]{3,30}$`, not in `catalog.reservedSlugs`), template_key (a `templates/index.json` key), accent (nullable, an `accentSwatches` key), locale (nullable, catalog `locales`), draft_content jsonb (default `{}`), published_content jsonb, is_published, published_at, primary_domain_id (fk `site_domains`, nullable). One row per agent (`unique (agent_id)`). Each content column is capped at 64KB (`octet_length(content::text) <= 65536`) |
| `site_domains` | site_id, hostname (unique, must be lowercase), kind (`path|subdomain|custom`), status (catalog `domainStatuses`), verification jsonb, provider, provider_ref, purchased_in_app, registered_at, expires_at, renewal_price_inr, auto_renew (false in v1) |
| `enquiries` | site_id (nullable), name, phone, email, interest (nullable), source, message, status, follow_up_at, notes, seen_at, customer_id |
| `customers` | name, phone, email, dob, address, city, nominee, notes, source_enquiry_id |
| `policies` | customer_id, policy_number, plan_name, sum_assured numeric(14,2), premium_amount numeric(12,2), premium_mode, start_date, next_due_date, status, notes |
| `premium_payments` | policy_id, amount numeric(12,2) > 0, paid_on, period_due_date (the due date this payment covered, used by Undo) |
| `site_daily_views` | site_id, day (Asia/Kolkata date), views; unique `(site_id, day)` |
| `subscriptions` | One per agent, created by the signup trigger: plan (`pro`), trial_started_at, trial_ends_at, current_period_start, current_period_end, last_order_id. **No stored status**; `entitlement()` derives it |
| `orders` | kind (`service_purchase|service_renewal|domain_purchase|domain_renewal`), amount_inr > 0, status (`payment_pending|paid|fulfilling|fulfilled|failed|refunded|cancelled`, default `payment_pending`), plus domain fields: quote_id, domain, site_domain_id, step (`registering|configuring|ssl_pending|live`), registrar_ref. Also error and attempts |
| `payments` | order_id (fk `orders`, restrict), amount_paise > 0, currency (`INR`), provider (`razorpay`), provider_order_id (unique), provider_payment_id (unique), status (`created|captured|failed|refunded`), refund_id, receipt_no (unique, `^APRO-[0-9]{4}-[0-9]{6}$`), raw jsonb |
| `domain_quotes` | domain (lowercase), kind (`purchase|renewal`), registrar_price_inr, renew_estimate_inr, service_fee_inr, total_inr, currency_source, expires_at, used_by_order_id |
| `registrant_contacts` | name, email, phone, address, city, state, pin (`^[1-9][0-9]{5}$`), country (`IN`); one per agent |
| `app_config` | key (pk), value jsonb, updated_at. Keys: `flags`, `pricing`, `retention` (from `catalog_sync`), `flag_overrides` (seeded `{}` once, then hand-set) and `min_app_version`. Readable by `authenticated` and writable only by the service role |
| `consents` | P1-2. principal_kind (`agent|enquirer|customer`), enquiry_id / customer_id (nullable, composite FKs, `on delete set null`), notice_key (catalog `privacyNotices`), notice_version (≥ 1), notice_hash (`^[0-9a-f]{64}$`), locale (resolved from the hash), channel (`app|web`), given_at, withdrawn_at + withdrawn_via (`web_form|agent|app`, both set or both null). **No IP or device data.** Written only by `submit_enquiry`, `record_consent` and the signup trigger |
| `data_requests` | P1-2. agent_id (**nullable** for P2 platform requests; required when addressed_to = `agent`), addressed_to (`agent|platform`, default `agent`), kind (`dataRequestKinds`), status (`dataRequestStatuses`), name (1–80), phone, message (≤ 500), enquiry_id / customer_id (composite FKs, set null; not filled yet), due_at, resolved_at, resolution_note (≤ 500), escalated_at. Index `(agent_id, phone, created_at)` for the rate limit. Written only by `submit_data_request` and `update_data_request` |
| `audit_log` | P1-2, shared with OFFERS.md. **Not agent-owned:** `id bigint identity`, at, actor_id (no FK, survives account deletion), actor_kind (`admin|agent|system`), action (e.g. `data_request.update`), entity, entity_id uuid, changes jsonb (never personal data of enquirers or customers), request_ip inet. No `updated_at`/`deleted_at`. Append-only: no API role can read or write it; the service role has select and insert only. The owner-admin select policy comes with the admin tables (OFFERS.md) |

**Client write rules**
- **Client-writable** (by the owner, through RLS and `can_write()`):
  - `profiles` (select and update only, because the trigger creates the row),
  - `sites`,
  - `enquiries`, `customers`, `policies` and `registrant_contacts`.
- **Read-only for the client:**
  - `subscriptions`, `orders`, `payments`, `domain_quotes`, `site_domains` and `site_daily_views`,
  - `premium_payments`, which only `mark_premium_paid` and `undo_premium_payment` write,
  - `consents` and `data_requests` (owner-only select; written only by the privacy RPCs and the signup trigger).
- **No API access:** `audit_log`.

  These are written only by the service role or by `SECURITY DEFINER` functions.
- **Column-level grants** protect `sites`:
  - insert is allowed on `id, agent_id, slug, template_key, accent, locale, draft_content, is_published`;
  - update is allowed on `slug, template_key, accent, locale, draft_content, is_published, deleted_at`.
  - `published_content`, `published_at` and `primary_domain_id` are set only by RPCs or the service role.
- **Reserved slugs** (`catalog.reservedSlugs`, through `catalog_reserved_slugs()`) are blocked by a trigger on every write to `sites.slug`, as well as in `is_slug_available`. The check ignores case.

## RPCs
Argument names are exactly as listed. PostgREST passes them by name (`POST /rest/v1/rpc/<fn>` with a JSON body).

| Function | Caller | Behaviour |
|---|---|---|
| `entitlement(p_agent_id uuid) → text` | internal (no API role) | `trial|active|grace|lapsed`; see [Entitlement rules](#entitlement-rules) |
| `can_write() → boolean` | `authenticated` (needed by RLS) | `entitlement(auth.uid()) in ('trial','active','grace')` |
| `effective_flags() → jsonb` | authenticated, service role | The feature flags: catalog defaults merged with manual overrides. See [Feature flags](#feature-flags) |
| `my_entitlement() → jsonb` | authenticated | `{state, trial_ends_at, current_period_end, grace_ends_at, renew_available, prices:{purchase_inr, renewal_inr, tax_inclusive, next_order_kind}}`. `next_order_kind` is `service_purchase` until the first payment, then `service_renewal` |
| `get_public_site(slug text) → jsonb` | anon | For a published site whose owner isn't `lapsed`, returns `{template_key, accent, locale, published_at, published_content, canonical_host, profile, companies, company, offers}`; otherwise null. `published_at` is the last publish (the SSR Worker's `Last-Modified`). See [Public payload](#public-payload). The slug is trimmed and lowercased |
| `get_public_site_by_host(host text) → jsonb` | anon | Same payload. The site is resolved through `site_domains`, where `hostname` = host (lowercased, port and trailing dot stripped), `status in ('active','renew_soon')`, `expires_at` is null or in the future, and `deleted_at` is null |
| `submit_enquiry(slug text, host text, name text, phone text, email text, interest text, message text, honeypot text, consent jsonb default null) → jsonb` | anon | See [submit_enquiry](#submit_enquiry). The only overload (the 8-argument one was dropped in P1-2) |
| `submit_data_request(slug text, host text, name text, phone text, kind text, message text, honeypot text) → jsonb` | anon | See [Privacy](#privacy-p1-2) |
| `list_public_sites() → setof (slug text, published_at timestamptz)` | anon, authenticated | The primary-host sitemap (DECISIONS #79): every site `get_public_site` would serve (published, not deleted, published content present, owner not `lapsed`) **except sites with an active custom domain** (the `canonical_host` rule: kind `custom`, status `active` or `renew_soon`, not expired, not deleted; their canonical URL is the domain, which serves its own `/sitemap.xml`; once the domain expires or is deleted the free link is listed again), with **only** `slug` and `published_at`, newest first, at most 50,000 rows. PostgREST returns `[{slug, published_at}]` (`POST /rest/v1/rpc/list_public_sites` with `{}`). Built in `20261017132241_companies_offers.sql` |
| `record_site_view(slug text, host text) → void` | anon | Resolves the site the same way as `submit_enquiry` and upserts `site_daily_views` for today (Asia/Kolkata), +1. Does nothing for an unknown, unpublished or lapsed site |
| `is_slug_available(slug text) → boolean` | authenticated | Checks the format, the reserved-words list, and that no other agent's site (including soft-deleted ones) uses the slug. The caller's own slug counts as available |
| `publish_site() → jsonb` | authenticated, `can_write()` | Validates the caller's `draft_content` against `site_schema()` and checks its offer references. On success it copies draft to published, sets `published_at` and `is_published = true`, and returns `{ok:true, published_at}`, plus `warnings:[{section, field, item, rule}]` when an offer has ended or is unavailable. Otherwise it returns `{ok:false, errors:[{section, field, item, rule}]}`. See [Publish validation](#publish-validation) |
| `preview_site_offers(content jsonb, template_key text) → jsonb` | authenticated (no `can_write()`) | The in-app preview's offers (DECISIONS #84): `{companies, company, offers:{featured, items}}` exactly as `get_public_site` would return them (`companies` since `20261022090000_multi_company.sql`, DECISIONS #90) for a site of the caller (`auth.uid()`) with `content` as its published content and this `template_key`: picked offers, or the automatic ones, the featured one for the `offers` family, `company` = `{disclaimer}` only, never a company slug, logo or `internal_name`; company names only in `companies` (see [Companies and offers](#companies-and-offers-8b-2), Public payload). It builds an in-memory `sites` row (never written) and calls the same `private.site_offers_payload`, so the two can't drift. The language is the caller's site's `locale` (a site that isn't deleted), else `catalog_default_locale()`. Content that isn't a JSON object (or null) is treated as `{}`; content over 64KB (the `sites` cap) raises `invalid_content` (22023). An unknown, empty or null `template_key` has no family, so it behaves like the personal family. It doesn't need the site row (onboarding previews before it exists), publish state or entitlement (a lapsed agent can preview). No user id: `not_authenticated`. Example: `{"company":{"disclaimer":"…"},"offers":{"featured":{"source":"company","id":"<uuid>","title":"…","summary":"…","details":null,"badge":null,"disclaimer":"…","image_url":null,"cta":{"kind":"enquire","interest":"health"},"ends_on":"2026-11-19","interest":"health","from_agent":false},"items":[…]}}`; with no approved company `{"companies":[],"company":null,"offers":{"featured":null,"items":[]}}`. Built in `20261020090000_preview_offers.sql` |
| `mark_premium_paid(policy_id uuid, amount numeric, paid_on date) → jsonb` | authenticated, `can_write()` | In one transaction: inserts a `premium_payments` row (`period_due_date` = the current `next_due_date`) and moves `next_due_date` forward by `catalog_premium_mode_months(premium_mode)`. Returns `{payment_id, period_due_date, next_due_date}`. See [Due date rule](#due-date-rule) |
| `undo_premium_payment(payment_id uuid) → jsonb` | authenticated, `can_write()` | Works only on the policy's **latest** live payment (otherwise `not_latest_payment`). Soft-deletes it (sets `deleted_at`, so sync sees it), restores `next_due_date` from `period_due_date`, and returns `{policy_id, next_due_date}` |
| `delete_my_account() → void` | authenticated | See [Account deletion](#account-deletion) |
| `record_consent(notice_key text, version integer, hash text, customer_id uuid default null) → jsonb` | authenticated (no `can_write()`) | See [Privacy](#privacy-p1-2) |
| `update_data_request(id uuid, status text, note text) → jsonb` | authenticated (no `can_write()`) | See [Privacy](#privacy-p1-2) |
| `is_admin(p_role text default null)`, `is_admin_for(p_company_id uuid)`, `can_manage_company_media(p_name text)` → boolean | authenticated (needed by RLS) | See [Companies and offers](#companies-and-offers-8b-2) |
| `list_companies`, `request_company`, `withdraw_company_request`, `set_membership_types`, `my_company_offers` | authenticated (agents) | See [Companies and offers](#companies-and-offers-8b-2) |
| `admin_review_membership`, `admin_link_membership`, `admin_membership_queue`, `admin_offer_usage` | authenticated (admins, aal2) | See [Companies and offers](#companies-and-offers-8b-2) |

All of these except `effective_flags()` (security invoker, because it only reads `app_config`) are `SECURITY DEFINER`. All have `set search_path = ''` and fully qualified names. `execute` is revoked from `public`, `anon` and `authenticated`, and granted only to the roles listed.

**Errors from authenticated RPCs** are raised with a snake_case message:
- `not_authenticated` and `plan_read_only` use SQLSTATE 42501, which PostgREST returns as 401/403.
- `not_found` uses P0002 (404).
- `invalid_amount`, `invalid_paid_on`, `no_due_date`, `unknown_premium_mode`, `invalid_status` and `invalid_note` use 22023.
- `not_latest_payment`, `orders_in_progress` and `media_not_deleted` use P0001.
- Companies and offers: `forbidden` (42501); `invalid`, `invalid_reason`, `invalid_decision`, `invalid_name`, `invalid_disclaimer`, `invalid_content`, `incomplete_content`, `invalid_cta`, `invalid_interest`, `invalid_dates`, `invalid_company`, `offer_ended` and (DECISIONS #89) `invalid_types` (22023); `invalid_transition` and `duplicate_membership` (P0001); `preview_site_offers` raises `invalid_content` (22023) for content over 64KB. The public text check raises `blocked_term:<field>:<locale>:<term>` (22023; detail `<locale>.<field>`), e.g. `blocked_term:title:hi:एलआईसी`, `blocked_term:disclaimer:en:acme life`, `blocked_term:cta_url::licindia` (the URL fields have an empty locale). The admin panel maps the `blocked_term:` prefix and shows the field, language and term.

The anon RPCs never raise for user input.

### Entitlement rules
- **`servicePlan` flag off** (in `effective_flags()`, so a `flag_overrides` entry can switch it): `active` for everyone.
- **No `subscriptions` row:** `lapsed`.
- **`active`:** `current_period_end` is set and `now() < current_period_end`. This includes paying during the trial, when the period starts at the trial end.
- **`trial`:** `current_period_end` is null and `now() < trial_ends_at`.
- **`grace`:** `now() < coalesce(current_period_end, trial_ends_at) + pricing.service.graceDays`.
- **`lapsed`:** otherwise.
- **`renew_available`:**
  - false when the flag is off;
  - in `active`, true only from `renewWindowDays` before `current_period_end`;
  - otherwise true (`trial` means the first purchase, then `grace` and `lapsed`).
- **`grace_ends_at`** is `coalesce(current_period_end, trial_ends_at) + graceDays`.

### Public payload
- **`profile`** contains `full_name`, `designation`, `city`, `photo_url` and `phone`.
  - It also contains `agency_code`, but **only** when `published_content.about.showAgencyCode` is `true`. Otherwise the key is absent.
  - Never included: email, branch, ids, `draft_content`.
- **`canonical_host`** is the hostname of the site's `custom` domain whose status is `active` or `renew_soon` and that hasn't expired. `primary_domain_id` is preferred if there are several. It's null when there is no such domain.
- **`published_at`** (8b-2) is the site's last publish time; the SSR Worker sends it as `Last-Modified` and the sitemap uses it as `lastmod` (with `list_public_sites()`).
- **`companies`** (DECISIONS #90): `[{name, insurance_types}]`, the agent's approved links to active companies, names as plain text only. See [Companies and offers](#companies-and-offers-8b-2), Public payload.
- **`company`** (`{disclaimer}` only) and **`offers`** (picked, or automatic for an empty shown section) (8b-2): see [Companies and offers](#companies-and-offers-8b-2), Public payload. The app's preview gets the same three keys (`companies`, `company`, `offers`) for a draft from `preview_site_offers` (DECISIONS #84).

### submit_enquiry
The steps run in this order:
1. **Resolve the site** by `slug` if one is given, otherwise by `host`. If it's unknown, unpublished, deleted or the owner is `lapsed`, return `{ok:false, error:'unavailable'}`.
2. **Honeypot.** If it's non-blank, return `{ok:true}` and insert nothing.
3. **Validate.** The first failure returns `{ok:false, error:'invalid', field:'<name|phone|email|interest|message>'}`. These are the same limits the web form enforces:
   | Field | Rule |
   |---|---|
   | name | Required after trimming, 1–80 characters |
   | phone | Required. After removing spaces, `()`, `.`, `-` and a `+91`, `91` or `0` prefix, it must match `^[6-9][0-9]{9}$`. It's stored as `+91XXXXXXXXXX` |
   | email | Optional. At most 120 characters, shaped like `x@y.z`, stored lowercased |
   | interest | Optional. If present, it must be in `catalog_interest_keys()` (active interests only; a deprecated one is rejected). Every catalog interest is accepted, of every insurance type (DECISIONS #93), not only the ones the site offers: the form lists the site's own, but the server doesn't narrow by site |
   | message | Optional, at most 500 characters |
4. **Consent.** `consent` must be the enquiry form's notice, `{"notice_key":"enquiry_form","version":<current>,"hash":"<64 hex of the text shown, any locale>"}`, valid under the [consent validation contract](#catalog_sync-contract-what-the-generated-migration-must-emit). Missing or invalid: `{ok:false, error:'invalid', field:'consent'}`.
5. **Rate limit.** If there are already 5 or more `website` enquiries for this site and phone in the last 60 minutes (soft-deleted ones count), return `{ok:false, error:'rate_limited'}`. A per-site, per-phone advisory lock serialises concurrent submissions.
6. **Insert** the enquiry with `source = 'website'`, `status = 'new'`, `site_id` and the owner's `agent_id`, and in the same transaction a `consents` row (`principal_kind = 'enquirer'`, `channel = 'web'`, `enquiry_id` = the new enquiry, the verified version and hash, the locale the hash resolves to; no IP or user agent). Return `{ok:true}`. A honeypot or rejected submission writes neither row.

### Publish validation
The validator walks `site_schema().sections`.
- **Sections it skips:** those listed in `draft_content.hidden`, unless the section is `pinned`.
- **Values it checks** for each field:
  - `required`: the value must not be missing, null, a blank string or an empty array.
  - `maxLength`, `min`/`max` (`range`) and `maxItems`.
  - Type: a string for text-like fields, a number, a boolean for toggles, an array for lists and multi-selects.
  - `format`: phone (same rule as above), email, and `url` (`http(s)://…`).
  - `option`: the value must be in `options[].key`, or in `catalog_interest_keys_all()` for `optionsFrom: "catalog.interests"`, or in `catalog_offer_cta_kinds_agent()` for `optionsFrom: "catalog.offerCtaKinds"`. Deprecated interests are accepted here, so existing content keeps publishing.
  - Dates must be `YYYY-MM-DD` (`format`) and, with `maxDaysAhead`, no later than today (Asia/Kolkata) + N (`max_days_ahead`). An `equals: true` toggle must be true, also when missing (`equals`). `offerRef` must be an object (`offer_shape`); `offerList` an array within `maxItems`.
- **List items** are validated against `itemFields`, and the error's `item` is the 0-based index.
- **Offer references** are then checked by `private.validate_site_offers` (errors and warnings): see [Companies and offers](#companies-and-offers-8b-2).
- **Services and insurance types (DECISIONS #93):** the server neither seeds services nor checks them against the agent's insurance types. A new site's services are seeded by the app (`StarterContent`, SHARED_DEFINITIONS.md, site-seed.json) and stay the agent's content; publishing only checks that each key is a catalog interest.

### Due date rule
1. If `start_date` is set and the current `next_due_date` falls on the policy's schedule (`start_date` + a whole number of calendar months), the new date is `start_date + (elapsed + months)`. This avoids month-end drift: 31 Jan → 28 Feb → 31 Mar.
2. Otherwise the new date is `next_due_date + months`. Postgres clamps the day to the month end, so 31 Jan 2024 + 1 month = 29 Feb 2024.
3. The call is refused if `next_due_date` is null.

### Account deletion
`delete_my_account()` works in this order:
1. It refuses (`orders_in_progress`) if any `orders` row is `paid` or `fulfilling`.
2. It refuses (`media_not_deleted`) while objects remain under `site-media/<uid>/`. Supabase documents that deleting `storage.objects` rows with SQL orphans the files, and they'd stay publicly reachable. **The app must therefore remove the agent's media through the Storage API first,** which the delete policy allows even when the plan has lapsed.
3. It clears `payments.raw` (already minimised to non-personal keys since P1-2) and deletes the `auth.users` row. `consents` and `data_requests` cascade with the account.
4. Everything else cascades. `orders` and `payments` keep the amounts, receipt numbers and dates, with `agent_id` set to null.

## Edge Functions (see BILLING.md and DOMAINS.md) — Phases 9 and 10, not built yet
| Function | Auth | Purpose |
|---|---|---|
| `billing` | JWT | `create-order` (the service amount comes from `app_config.pricing`; domains need a valid `quote_id`), `verify` (signature check, then fulfilment) |
| `domains` | JWT, paid plan | `search` (live registrar quotes plus fee, stored in `domain_quotes`), `quote` (renewal), `connect`, `status` |
| `razorpay-webhook` | none (verifies the Razorpay signature) | Handles `payment.captured`, `payment.failed` and `refund.processed` idempotently |
| `cron` | service (pg_cron through `net.http_post`) | Every 10 min: domain DNS and SSL status, and cancelling stale unpaid orders. Daily: plan and domain reminders, and domain status changes (`renew_soon`/`expired`) |

The shared modules live in `supabase/functions/_shared/`: `payments/razorpay.ts`, `registrar/*.ts`, `hosting/cloudflare.ts`, `fulfil.ts`, `pricing.ts` and `entitlement.ts`. Edge Functions use the service role, which bypasses RLS and holds `all` on every table, and they can call `public.entitlement(uuid)`.

**Secrets** are listed in BILLING.md and DOMAINS.md. They're set through the Supabase secrets tool, and the user enters the values.

## Storage
- **Bucket `site-media`:** public, so files are read through their public URL and need no select policy. `file_size_limit` is 2097152 (2MB) and `allowed_mime_types` is `['image/*']`.
- **Policies on `storage.objects`**, all `to authenticated`, all requiring `bucket_id = 'site-media'` and `(storage.foldername(name))[1] = (select auth.uid())::text`:
  - select (listing your own folder, which the Storage API also needs for upserts),
  - insert and update, which also require `(select public.can_write())`,
  - delete, which doesn't require `can_write()`, so a lapsed agent can still clean up and delete their account.
- **Client side:** the app compresses images to WebP at 1024px or less before uploading.
- **Bucket `company-media`** (8b-2): public read; `file_size_limit` 524288; `allowed_mime_types` `['image/webp', 'image/png']` (**no SVG**: an SVG opened directly runs scripts). Paths `logos/<company_id>/<n>.webp` and `offers/<offer_id>/<n>.webp`, a new `<n>` per upload. Policies, all `to authenticated` with `bucket_id = 'company-media'`: select (listing) for `(select public.is_admin())`; insert, update and delete for `public.can_manage_company_media(name)`, which allows a logo path for admins of that company and an offer path for admins of that offer's company, and nothing else. Agents and visitors use the public URLs only.

## Realtime
Publication `supabase_realtime` includes `enquiries`, `orders`, `subscriptions` and `site_domains`, and RLS applies to realtime too.

## Auth
- Email and password, with **email confirmation on**. **Confirmation and reset use a one-time code from the email, not a link** (DECISIONS #88): the app calls `verifyEmailOtp(SIGNUP | RECOVERY, email, code)`. The **Confirm signup** and **Reset password** email templates must show `{{ .Token }}` (generated in `supabase/email-templates/`; the user pastes them in Authentication → Emails). The code is 6 digits by default and the app accepts 6 to 10; its expiry is Auth → Emails → OTP expiry (default 1 hour). A wrong or expired code is HTTP 403 `otp_expired`, which `ErrorMapper` maps to `Validation("code")`.
- The `agentpro://auth-callback` deep link (and its redirect allow-list entry) stays only for links in emails sent before the switch; the app no longer sends a `redirect_to`. **`agentpro://auth-callback**` is on Auth → URL Configuration → Redirect URLs** (added 2026-10-17); without it Auth silently replaces `redirect_to` with the site URL.
- Password minimum is 8 characters. The site URL is set to the renderer domain (`https://agentpro-sites.sledjesoftwares.workers.dev/`); it's only the fallback for a redirect that isn't on the allow list.
- A new confirmation email (resend, or `/signup` again for an unconfirmed address) invalidates the earlier link: clicking an old one gives "Email link is invalid or has expired".
- **Auth emails** go through **custom SMTP via Gmail** (`smtp.gmail.com:465`, a Gmail app password; set on 2026-10-17, DECISIONS #68), an interim sender until the transactional provider (DECISIONS #60). Auth → Rate Limits → emails is **100 per hour**; the minimum interval per address is 60 s. Gmail itself caps a free account at about 500 emails a day. With custom SMTP, a failed handover to Gmail makes `/signup` return an error, not 200. Two refusals share the code `over_email_send_rate_limit` (HTTP 429), and the app tells them apart by the message (`ErrorMapper` → `AppError.EmailLimited`):
  - the **per-address wait**: "For security purposes, you can only request this after N seconds" (one email per address per 60 s; `AuthRules.EMAIL_RESEND_SECONDS`);
  - the **project's hourly quota**: "email rate limit exceeded" (now 100 per hour; it was about 2 per hour on the built-in sender).
  - **Email templates:** `supabase/email-templates/{confirmation,recovery,invite,email_change,reauthentication}.html`, with the subjects in `subjects.json`. They're generated by `node scripts/gen-email-templates.mjs` from `shared/design-tokens.json` (DESIGN_SYSTEM.md, Auth emails). **They are applied by hand:** paste each file into Authentication → Emails → Templates, and its subject from `subjects.json`. `magic_link` isn't used, so it's left alone. They use only Supabase's `{{ .ConfirmationURL }}`, `{{ .Email }}`, `{{ .NewEmail }}` and `{{ .Token }}`. After changing the tokens or the copy, regenerate and paste again.
  - A refused `/signup` creates no user (checked on `AGENT_PRO`, 2026-10-17), so signing up again later is safe. For an existing unconfirmed address, `/signup` sends the confirmation again.
- **Signup trigger** `on_auth_user_created` (after insert on `auth.users`, function `private.handle_new_user`):
  - creates `profiles` (with `email`, plus `full_name` and `phone` from `raw_user_meta_data`; the app's Create account sends both, the phone already as `+91XXXXXXXXXX`; DECISIONS #70),
  - creates `subscriptions` with `trial_ends_at = now() + pricing.service.trialDays`,
  - records the sign-up consent: the app sends `raw_user_meta_data.consent = {"notice_key":"agent_signup","version":…,"hash":…}` in the Auth sign-up call; if it's valid (consent validation contract) a `consents` row is inserted (`principal_kind = 'agent'`, `channel = 'app'`). **If it's missing or invalid the user is still created** (Auth can't be blocked cleanly from a trigger) and nothing is recorded; the app must then call `record_consent('agent_signup', …)` before continuing. The app can check with `select … from consents where notice_key = 'agent_signup'` (owner-only RLS).

## Companies and offers (8b-2)
Built 2026-10-17/18 in `20261017132241_companies_offers.sql` (OFFERS.md; DECISIONS #49–#53, #72, #74, #77, #79). **Applied to `AGENT_PRO` on 2026-10-18** (pasted by the user). Several companies per agent with insurance types (DECISIONS #89) and company names on sites (DECISIONS #90): `20261022090000_multi_company.sql`, **not applied yet**; this section describes the state after it. The admin panel and the `admin` Edge Function (invite, set-role, revoke, reset-mfa) are Phase 8c.

**Tables.** `companies`, `offers` and `admins` are **not agent-owned**, so they use **admin-only RLS** (DECISIONS #50); `company_memberships` is agent-owned (owner select) but decided by admins.
| Table | Columns and rules |
|---|---|
| `companies` | slug (unique, `^[a-z0-9-]{2,40}$`), name jsonb (every catalog locale, ≤ 80; `companies_guard`), website_url (https), disclaimer jsonb (per locale, ≤ 300), logo_url (https; **needs `logo_permission_at`**), logo_permission_at/ref (≤ 200)/until, allow_custom_offers (default true), is_active, sort, created_by/updated_by (trigger). Never deleted |
| `offers` | company_id (fk, restrict), status (`offerStatuses`, default draft), content jsonb (offer-schema.json `localized` per locale: the offer's **public, generic** name and text, DECISIONS #77), internal_name (the real product name, ≤ 120, **admin-only**: readable and writable through the admin RLS, never in any agent or public payload, not checked against the blocked terms), image_url (https), cta_kind (`offerCtaKinds`), cta_url (only with `link`, https), interest (catalog interest), starts_at < ends_at, sort, published_at/by and archived_at (trigger only), created_by/updated_by. Index `(company_id, status, ends_at)`. Never deleted |
| `admins` | user_id (pk, fk `auth.users` cascade), role (`adminRoles`), company_id (null = every company), invited_by, revoked_at. Written only by the service role |
| `company_memberships` | agent_id (fk cascade), company_id (null = unlisted, then suggested_name ≤ 80), agency_code (≤ 30), status (`membershipStatuses`, default pending), reject_reason (required exactly when rejected), review_note (≤ 200), **insurance_types** `text[] not null default '{}'` (keys of `catalog.insuranceTypes`: one-dimensional, no nulls, unique, in `catalog_insurance_types()`, **at least one on pending and approved rows** (CHECK `company_memberships_insurance_types_required`); rows made before #89 were backfilled `{life}`), requested_at (`clock_timestamp()`), reviewed_at/by. **One pending or approved row per agent and company** (`company_memberships_one_current_per_company_idx`) and **at most one pending "not listed" row per agent** (`company_memberships_one_pending_unlisted_idx`) |

**Admin helpers** (SECURITY DEFINER, granted to `authenticated` because RLS calls them): `is_admin(p_role default null)` is an active (not revoked) `admins` row for `auth.uid()`, with that role if given, **and** `auth.jwt()->>'aal' = 'aal2'` (MFA in this session). `is_admin_for(p_company_id)` adds: unscoped (company_id null), or scoped to that company; `is_admin_for(null)` is unscoped admins only.

**RLS and grants.**
- `companies`: select `(select is_admin())`; insert `(select is_admin_for(null))` (unscoped admins create companies); update `is_admin_for(id)`. Column grants exclude the stamps; `id` may be client-chosen.
- `offers`: select/insert/update `is_admin_for(company_id)`; `company_id` isn't updatable; stamps are trigger-only; no delete.
- `admins`: select your own row (so the panel can say "No access") or every row for `is_admin('owner')`.
- `company_memberships`: select own (`agent_id = (select auth.uid())`) or `is_admin_for(company_id)`; **no write grants** (RPCs only).
- `audit_log`: select for `is_admin('owner')`; inserts only from SECURITY DEFINER code. `anon` has nothing on any of them.

**Triggers.** `stamp_actor` (created_by/updated_by from the session; created_* never change); `companies_guard` (names and disclaimers per locale; the disclaimer, in every locale, passes the public text check against the blocked terms and every company's name, **its own included**, whenever the disclaimer or the name changes); `offers_guard` (new offers start as drafts; transitions draft → live → archived → draft; content keys and lengths against `offer_schema()`; active CTA kinds; catalog interest; validity ≤ `maxValidityDays`; **going live** needs every required localized field in every catalog locale, a URL for `link`, and an end in the future; the [public text check](#public-text-check) on every localized field in every locale plus `cta_url` and `image_url`, on insert, when one of them changes and again on going live, never on other updates, so archiving always works; stamps published_at/by and archived_at); `memberships_guard` (new rows pending for an active company; pending → approved | rejected | withdrawn, approved → revoked | withdrawn; the company may be set once on a pending unlisted row; reviews stamped; `insurance_types` on insert and whenever they change must be unique active catalog keys (`invalid_types`), and change only on a pending or approved row whose status stays the same, keeping `reviewed_at/by`); `audit_row` (after insert/update on all four: `company.create/update`, `offer.create/update/go_live/archive/restore`, `admin.create/update/revoke`, `membership.request/approve/reject/revoke/withdraw/link/update` (a change of insurance types is `membership.update` with the new `insurance_types`), with the changed columns; memberships leave out agency code, suggested name and note; actor_kind admin/agent/system; request_ip from `x-forwarded-for` for admins).

**Agent RPCs.** The membership JSON (`private.membership_json`, app only) is `{id, status, company:{id, name:{en,hi}, logo_url, allow_custom_offers} | null, suggested_name, agency_code, insurance_types:[key…] (catalog order), reject_reason, review_note, requested_at, reviewed_at}`.
| Function | Behaviour |
|---|---|
| `list_companies() → jsonb` | Active companies `[{id, name:{en,hi}, logo_url}]` (logo only while permitted: `logo_permission_until` null or today or later, Asia/Kolkata), by sort then name |
| `request_company(company_id uuid, suggested_name text, agency_code text, insurance_types text[]) → jsonb` | `can_write()`. Exactly one of company_id (active; agency_code required) and suggested_name. `insurance_types`: at least one active `catalog.insuranceTypes` key (trimmed, de-duplicated, stored in catalog order; else `invalid_types`). **The agent's other companies are never touched.** For a company that already has a pending or approved row: the same agency code is a no-op that only updates the types (no new review); a different code withdraws that row and inserts a new pending one (a new code needs a new review, DECISIONS #89). For "not listed": the same name (case-insensitive) and code only updates the types; anything else withdraws the pending unlisted request and makes a new one (at most one pending). Returns the membership. Errors `invalid`, `invalid_types`, `not_found`, `plan_read_only` |
| `withdraw_company_request(membership_id uuid) → jsonb` | The caller's pending or approved row becomes withdrawn (an approved company's offers and name leave the site); already withdrawn is a no-op; rejected or revoked → `invalid_transition`; not the caller's → `not_found`. No `can_write()`. Returns the membership |
| `set_membership_types(membership_id uuid, insurance_types text[]) → jsonb` | `can_write()`. Replaces the types of the caller's pending or approved row, **with no new review** (an approved link stays approved). Errors `not_found` (not the caller's), `invalid_transition` (other statuses), `invalid_types`, `plan_read_only`. Returns the membership |
| `my_company_offers() → jsonb` | `{memberships:[membership…], offers:[…]}`. `memberships`: per company (and one entry for "not listed"), the pending or approved row, else the latest row if it's rejected or revoked; withdrawn ones are left out; oldest request first. `offers`: the live offers that haven't ended (upcoming included) of **every approved, active company**, `[{id, company_id, content, image_url, cta_kind, cta_url, interest, starts_at, ends_at, phase}]`, by company approval order, then sort, starts_at, id. Never `internal_name`. Works on a lapsed plan. (Before #89 it returned `{membership, offers}`; no client used it yet, so there's no compatibility key) |

**Admin RPCs** (an admin in an aal2 session, else `forbidden`; another company's row is `not_found`).
| Function | Behaviour |
|---|---|
| `admin_review_membership(membership_id, decision, reject_reason, note) → jsonb` | `approve` or `reject` (reason from `catalog_membership_reject_reasons()`) a pending request; `revoke` an approved link (note required). Note ≤ 200, shown to the agent |
| `admin_link_membership(membership_id, company_id) → jsonb` | Unscoped admins: sets the company of a pending unlisted request; it stays pending for review. `duplicate_membership` (P0001) when the agent already has a pending or approved row for that company (reject the unlisted one instead) |
| `admin_membership_queue(company_id, status) → jsonb` | Oldest first, at most 200; company_id null = every company the admin may see (unlisted ones for unscoped admins); status default pending. Each row has the request fields (with `insurance_types`, catalog order), `agent:{full_name, designation, city, email}` only, and `previous`: the agent's last decided request **for the same company** (for a "not listed" request, the last decided "not listed" one), so an admin scoped to one company never learns about the agent's other companies |
| `admin_offer_usage(offer_id) → jsonb` | `{published_sites, draft_sites}`: counts only, never which agents |

**Publish (`private.validate_site_offers`).** `hero.featuredOffer` is always checked; `offers.items` only while the section is shown; custom offer ids always. A company offer must belong to **one of the agent's approved companies** (DECISIONS #89).
- Errors: `offer_shape` (not `{source: company|agent, id: <uuid>}`, or a custom id that isn't a uuid), `duplicate` (placed twice, or two custom offers with one id), `offer_wrong_company` (an unknown offer, or a company the agent never asked for), `offer_custom_missing`, `custom_offers_off` (**any** approved, active company turned them off: the strictest wins; an inactive company's switch doesn't count), `blocked_term` (a custom offer's `title` or `summary` fails the [public text check](#public-text-check): `{section:'offers', field:'title'|'summary', item:i, rule:'blocked_term'}`; checked while the offers section is shown, or for the custom offer `hero.featuredOffer` points at). The app's `PublishIssues.ruleError` maps an unknown rule to the generic `ValidationError.Option` on the right field, so `blocked_term` shows there until a specific label is wired.
- Warnings (publishing continues; skipped at render): `offer_ended` (a company offer or a custom offer past its end), `offer_unavailable` (an approved company's offer that isn't live, or whose company is inactive; or a company the agent asked for before but isn't approved for now).
- Positions: `{section:'hero', field:'featuredOffer', item:null}`, `{section:'offers', field:'items', item:i}`, and custom offer fields `{section:'offers', field:'<field>', item:i}`.

**Public payload** (DECISIONS #77, #79, #89, #90), built by `private.site_offers_payload` for `get_public_site*` and `preview_site_offers`. The agent's **approved links to active companies** are taken in approval order (`reviewed_at`, then `requested_at`, id).
- `companies` (DECISIONS #90): `[{name, insurance_types}]` in that order; `name` in the site's language (fallback `catalog_default_locale()`), plain text; `insurance_types` the membership's keys in catalog order. **Nothing else about a company**: no logo, slug, id or link. Pending, rejected, revoked and withdrawn links and inactive companies never appear; `[]` when there are none. Example: `"companies":[{"name":"Bharat Life","insurance_types":["motor"]},{"name":"Acme Life","insurance_types":["life","health"]}]`.
- `company`: **`{disclaimer}` only**, the **earliest-approved** active company's, in the site's language (fallback `catalog_default_locale()`); null without an approved active company. Each offer keeps its own `disclaimer`.
- `offers`: `{featured, items}`, each offer `{source, id, title, summary, details, badge, disclaimer, image_url, cta, ends_on, interest, from_agent}` in one language; `interest` is the offer's catalog interest (company offers: `offers.interest`; custom offers: their stored `interest`), null when unset, on **every** offer (featured, items, auto) whatever the CTA kind, for fallback images and the guide template's grouping; `title` is the offer's public, generic name, never `internal_name`, and offers never name their company. Company offers appear only while live and running (not upcoming or ended), from **any** of the approved active companies; the featured one never has an image. Custom offers need a title, the confirmation, an agent CTA kind and an end date today or later, and appear unless **any** approved active company turned them off. `cta` is `{kind:'enquire', interest}`, `{kind:'call'}` or `{kind:'link', url}` (company offers only); `cta.interest` is kept for compatibility.
- **Auto offers** (DECISIONS #79): when the published `offers` section isn't in `hidden` and has **no `items` and no `custom` entries**, `items` is up to 3 live, running offers of the approved companies, **taken in turn**: each company's first offer (by `sort`, then `starts_at`, then id) in approval order, then each one's second, and so on (with one company: by `sort`, `starts_at`, id, as before), resolved exactly like picked ones (locale fallback; offers with no title in either language are skipped). A company offer picked as `hero.featuredOffer` is left out of them. When `hero.featuredOffer` is unset **and** `catalog_template_family(template_key) = 'offers'` (spotlight, guide), the first auto offer becomes `featured` (with `image_url` null) and the rest stay in `items`, so at most 3 offers show in all. Nothing is stored: they're resolved on every read, so a new or archived company offer shows or leaves at once. Without an approved active company there are none.

### Public text check
`private.public_text_violation(txt text) → text` returns the first offending term (normalized) or null. It's `private.public_text_match(txt, private.public_text_terms())`, all three security invoker with `search_path = ''`, executable by no API role (only SECURITY DEFINER code calls them). It mirrors `PublicText` in `mobile/build-logic/.../codegen/SiteSeedKotlinGenerator.kt`:
- **Terms** (`public_text_terms()`): `catalog_blocked_terms()` (generated from `catalog.publicTextRules.blockedTerms`) in list order, then every `companies.name` value, in every locale, of every company (active or not); NFC-normalized, lowercased, trimmed, unique.
- **Match:** the text is NFC-normalized and lowercased; a term's words match across any run of whitespace; the characters before and after the match must not be word characters (letters, combining marks, digits). Postgres regex has no `\p{L}`, so the boundary class is `[:alnum:]` plus, by code point, Latin-1/Latin Extended letters (U+00C0–U+024F without × and ÷), combining diacritics (U+0300–U+036F) and the Devanagari block (U+0900–U+0963, U+0966–U+096F, U+0971–U+097F: letters, vowel signs, virama, nukta, digits), leaving out the dandas and U+0970. So "lic" doesn't match in "public", "एलआईसी" doesn't match in a longer word or before a vowel sign or virama, and punctuation and the danda are boundaries. The check is lookaround-free (`(^|[^W])term([^W]|$)`), and the boundary works the same under a `C` ctype (verified); `lower()` needs a UTF-8 ctype (Supabase's default) to fold non-ASCII capitals.
- **Where:** `offers_guard` (every localized field in every locale, `cta_url`, `image_url`), `companies_guard` (the disclaimer), `validate_site_offers` (custom offers' title and summary).
- **Not re-checked:** existing rows when a term or a company is added later (a live offer is re-checked when its text changes or it goes live again; the agent's custom offers at their next publish). The agent's own free text (designation, bio, headline) isn't checked (DECISIONS #77, open question).
- **Cost:** a `strpos` on each term's first word skips most terms before a regex runs; a guard or publish call checks a handful of texts against about 200 terms.

### Demo seed (test data, not a migration)
`supabase/demo/demo_offers.sql` is **generated** from `supabase/demo/demo-offers.json` by `python3 -I supabase/demo/gen_demo_offers.py` (stdlib only; never edit the SQL by hand). It creates or updates the demo company `demo-life` (name and disclaimer per locale, sort 100) and its 6 offers, with their public text, the admin-only `internal_name`, CTA, interest, dates (start 00:00, end 23:59:59 Asia/Kolkata) and sort, then puts them live (archived → draft → live; one already past its end stays a draft). Offer ids are uuid5 values from the company slug and offer key, so re-running it updates the same rows (`on conflict`). `image_url` stays null: the JSON's `image` names a bundled web image for the web fixtures only.
- **How it bypasses the admin-only RLS:** it runs as `postgres`, the tables' owner, which RLS doesn't apply to. `auth.uid()` is null there, so `created_by`, `updated_by` and `published_by` stay null and the audit rows have `actor_kind = 'system'`; the guards allow that, and the public text check still runs.
- **How to run it** (only after every migration is applied, and only with the user's approval for that project): paste it into the Supabase SQL Editor (one request runs as one transaction), or `psql --single-transaction -f supabase/demo/demo_offers.sql` as `postgres`. It has no `begin`/`commit` of its own, so the SQL tests run it inside a transaction (`\i :demo_offers`) and roll it back. It has **not** been run on `AGENT_PRO`.
- **Demo sites refill:** `supabase/demo/demo_sites.sql` is generated by `node supabase/demo/gen_demo_sites.mjs` from the Node seed mirror (`scripts/site-seed.mjs`). It links the two demo agents (`ramesh-kumar-pune`, `priya-sharma-jaipur`) to `demo-life` (pending → approved, insurance types `{life}`; regenerated for DECISIONS #89, so run it only after `20261022090000_multi_company.sql`) and re-seeds their sites with the website-v2 starter content. Ramesh stays on classic; Priya moves to spotlight. It keeps each site's stored `contact`, hero photo and designation server-side, so no personal data is read out, and it uses the slug as the variant seed. Run it after `demo_offers.sql`, as postgres in the SQL Editor (it has its own `begin`/`commit` and ends with a public-data check). The same generator refills one other test site from the name and city it already shows publicly: `node supabase/demo/gen_demo_sites.mjs --slug <slug> --name <name> --city <city> --template <key> [--no-link]` writes `refill_<slug>.sql`. `--no-link` skips the demo company link, for a "Meet me" site. Generated on 2026-10-18: `refill_gunjan.sql` (spotlight, linked) and `refill_shatrughan-sinha.sql` (classic, not linked), for the user to paste. It was rehearsed on 2026-10-18 on a throwaway Postgres 17 in the live state: the migrations, `demo_offers.sql` and this file all applied; both sites pass `validate_site_content` and `validate_site_offers` with no errors or warnings; spotlight shows 2 items plus 1 featured offer, with `company` = `{disclaimer}` only.
- A demo agent's site shows these offers only once an admin approves the agent's link to `demo-life` (`admin_review_membership`, or as `postgres`: insert the membership, then set its status to `approved`).

### Applying 8b-2 to `AGENT_PRO` (needs the user's approval)
1. Check `list_migrations` on `AGENT_PRO`: the last applied files must be `20261017050728_catalog_sync.sql` and earlier (`20261016220600_privacy.sql` and `20261016220836_catalog_sync.sql` included; apply those first if they're missing).
2. Apply `20261017132241_companies_offers.sql`, then `20261018073025_catalog_sync.sql`, each with `apply_migration` under its file name, in that order (plpgsql resolves the catalog functions lazily; the CHECKs need the tables).
3. Run the security and performance advisors and compare with [Advisors](#advisors) (expected: only the accepted WARNs).
4. Run the on-project checks in TESTING.md ("On the Supabase project", 1–7).
5. Optionally, with separate approval, run the demo seed as above.
6. Record the result in STATUS.md (environment) and the advisor run here.


**Privacy, still planned (PRIVACY.md, phase P2):** tables `nominations`, `incidents`; `erased_at` columns; RPCs `withdraw_consent`, `erase_enquiry`, `erase_enquiries`, `erase_customer`, `my_data_summary`, `create_platform_request`; a `pg_cron` retention job (`private.retention_run()`) with the schedule in PRIVACY.md (soft-deleted rows purged after 30 days; lapsed accounts deleted 12 months after lapse with notices at 30 days, 7 days and 48 hours; **never-converted enquiries are not deleted automatically**: the agent, as their fiduciary, clears them with the app's tools; DECISIONS #54). P1-2's part is built: see [Privacy](#privacy-p1-2). **Backups:** the project is on the free plan with no backups; Supabase Pro is high priority and not yet approved (PRIVACY.md, Backup risk).

## Privacy (P1-2)
Built 2026-10-17 in `20261016220600_privacy.sql` (PRIVACY.md; DECISIONS #48, #54, #61). **Applied to `AGENT_PRO` on 2026-10-17** (P1 release, pasted by the user).

**`submit_data_request(slug, host, name, phone, kind, message, honeypot) → jsonb`** (anon; the site privacy page's request form). Steps, in order:
1. Resolve the site exactly like `submit_enquiry`; otherwise `{ok:false, error:'unavailable'}`.
2. Honeypot non-blank: `{ok:true}`, nothing stored.
3. Validate, first failure `{ok:false, error:'invalid', field:'<name|phone|kind|message>'}`: name 1–80 after trimming; phone the same Indian-mobile rule (stored `+91…`); kind in `catalog_data_request_kinds()` (active keys); message optional, ≤ 500.
4. Rate limit: 3 requests per phone per agent's site per rolling 24 hours (advisory lock), else `{ok:false, error:'rate_limited'}`.
5. Insert `data_requests` for the site's agent: `addressed_to = 'agent'`, `status = 'open'` (the initial `dataRequestStatuses` key), `due_at = now() + app_config.retention.dataRequests.responseDays` days. Return `{ok:true}`.

No realtime publication is added for `data_requests` yet (PRIVACY.md doesn't require it); the P2 app work decides how the "Privacy request from …" notification is delivered.

**`record_consent(notice_key, version, hash, customer_id default null) → jsonb`** (authenticated; **no `can_write()`**, so a lapsed agent can still meet privacy duties). Records the caller's own consent with `channel = 'app'`:
- `agent_signup`, `agent_customer_duty`: `principal_kind = 'agent'`; `customer_id` must be null, else `{ok:false, error:'invalid', field:'customer_id'}`.
- `guardian_consent`: `principal_kind = 'customer'`; `customer_id` is required (`field:'customer_id'` if missing) and must be one of the caller's live customers **on the server** (otherwise it raises `not_found`, P0002; the app must sync the customer first).
- `enquiry_form` (only `submit_enquiry` records it), an unknown notice or an invalid version/hash: `{ok:false, error:'invalid', field:'consent'}`.
- **Idempotent:** if a live, unwithdrawn consent to the same notice version (and customer) exists, it returns `{ok:true}` without a new row.
- Returns `{ok:true}`.

**`update_data_request(id, status, note) → jsonb`** (authenticated; no `can_write()`). For the caller's own `addressed_to = 'agent'` request: status must be in `catalog_data_request_statuses()` (else `invalid_status`, 22023), note optional ≤ 500 (else `invalid_note`, 22023), unknown or another agent's id raises `not_found` (P0002). Sets `status` and `resolution_note`; `resolved_at` is set when the status isn't `open` (kept if the status doesn't change) and cleared on reopening. Writes `audit_log` (`actor_kind = 'agent'`, `action = 'data_request.update'`, `changes = {status, previous_status}`; never the note). Returns `{id, status, resolved_at}`.

**`payments.raw` minimisation.** Trigger `payments_minimise_raw` (before insert or update of `raw`, `SECURITY DEFINER`) passes every write, the service role's included, through `private.minimise_payment_raw`: only `id, order_id, status, method, amount, currency, fee, tax, captured, error_code, error_reason, created_at` and `acquirer_data.rrn` of the Razorpay **payment entity** are kept; a non-object or an empty result is stored as null. Edge Functions should pass the payment entity (not the webhook envelope, which would be reduced to null). Existing rows were minimised by the migration.

**Grants.** anon: `submit_enquiry`, `submit_data_request` (no table access). authenticated: select on `consents` and `data_requests` (owner-only policies `consents_select_own`, `data_requests_select_own`), execute on `record_consent` and `update_data_request`. `audit_log`: nothing for API roles; service role select and insert only.

## Local test harness
**For local tests only. None of `supabase/tests/` is ever applied to a Supabase project.**
- **`supabase/tests/00_supabase_shim.sql`** stands in for the parts of Supabase the migrations rely on, so they apply to a plain `postgres` container:
  - the `anon`, `authenticated` and `service_role` roles, with Supabase's default privileges;
  - `auth.users`, plus `auth.uid()`, `auth.role()` and `auth.jwt()` reading `request.jwt.claims`;
  - `storage.buckets`, `storage.objects` (with RLS) and `storage.foldername()`;
  - the `supabase_realtime` publication.
- **`run.sh`** applies the files in this order: shim → migrations, which include the generated `*_catalog_sync.sql` → tests. It **requires** the generated `catalog_sync` (it exits and says to run `./gradlew generateShared` if there's none), so the tests always run against the real generated SQL. The old Python fixture generator is retired.
- **Re-applying `catalog_sync`:** `run.sh` copies the latest `catalog_sync` into the container and passes its path as the psql variable `catalog_sync`, so a test can run `\i :catalog_sync` inside a transaction (used to prove overrides survive a catalog migration). It copies the demo seed the same way (`\i :demo_offers`).
- How to run it is in TESTING.md.

## Advisors
**Last run: 2026-10-16 on `AGENT_PRO`, after all migrations.** There are no ERROR findings.
- **Fixed:** `rls_auto_enable` could be executed by `anon` and `authenticated`. The fix is the migration `20261016035136_revoke_rls_auto_enable.sql`; the event trigger stays enabled.
- **Accepted by design (WARN, lint 0028):** `anon` can execute 4 SECURITY DEFINER functions: `get_public_site`, `get_public_site_by_host`, `submit_enquiry` and `record_site_view` (**5 once P1-2 is applied:** plus `submit_data_request`; **6 once 8b-2 is applied:** plus `list_public_sites`, which returns only published slugs and their publish times, DECISIONS #79). CLAUDE.md requires public access to go only through these RPCs, and `anon` has no table privileges.
- **Accepted by design (WARN, lint 0029):** `authenticated` can execute 7 SECURITY DEFINER functions: `can_write`, `my_entitlement`, `is_slug_available`, `publish_site`, `mark_premium_paid`, `undo_premium_payment` and `delete_my_account` (**9 once P1-2 is applied:** plus `record_consent` and `update_data_request`; **20 once 8b-2 is applied:** plus `is_admin`, `is_admin_for`, `can_manage_company_media`, `list_companies`, `request_company`, `withdraw_company_request`, `my_company_offers`, `admin_review_membership`, `admin_link_membership`, `admin_membership_queue` and `admin_offer_usage`; **21 with `list_public_sites`**, which is granted to `authenticated` as well as `anon`; **22 once `20261020090000_preview_offers.sql` is applied:** plus `preview_site_offers`, which reads only the caller's own companies' live offers, the same data `my_company_offers` already returns, in public-payload form; **23 once `20261022090000_multi_company.sql` is applied:** plus `set_membership_types` (`request_company` and `withdraw_company_request` change signature, not count); `effective_flags` is security invoker). Each one checks `auth.uid()` itself (the admin ones also `is_admin()` with aal2). `can_write`, `is_admin`, `is_admin_for` and `can_manage_company_media` must stay callable because RLS policies call them as `authenticated`.
- **Performance (INFO only):** 27 "unused index" notices, which are expected on an empty database (more once 8b-2 adds its indexes). Re-check once there's real traffic.
- **Expected after applying 8b-2:** no ERROR; the security WARNs are exactly the 0028 list (6 anon functions) and the 0029 list (21 authenticated functions) above. **Expected after `20261020090000_preview_offers.sql`:** the same, plus one 0029 WARN for `preview_site_offers` (22 authenticated functions), accepted by design; nothing else changes (no table, policy or index). **Expected after `20261022090000_multi_company.sql`:** one more 0029 WARN for `set_membership_types` (23 authenticated functions); the two new private helpers aren't exposed; no RLS or grant change on tables. The new `private.public_text_*` helpers aren't exposed (private schema, no API grants). Anything else (an RLS-disabled table, a mutable search_path, an anon table grant) is a regression; `80_static_review.sql` checks the same things locally.

Locally, `supabase/tests/80_static_review.sql` checks the usual advisor findings:
- RLS is on for every table;
- `anon` holds no table grants;
- every function pins its `search_path`;
- `anon` and `authenticated` can execute exactly the documented functions;
- policies wrap `auth.uid()` and `can_write()` in `(select …)`;
- every FK has a covering index;
- every agent table has an `(agent_id, updated_at)` index (`audit_log`, `companies`, `offers` and `admins` are excluded: not agent-owned);
- `audit_log` has no `anon` access, only select for `authenticated` (with the owner policy) and is append-only for the service role; `submit_enquiry` has exactly one overload; the 18 catalog CHECKs exist.

## Resolved questions (2026-10-15)
1. **Soft-deleted rows stay visible to their owner** through RLS, and clients filter them out (DECISIONS #27).
2. **Undo works only on the latest payment** of a policy and soft-deletes it. Confirmed by the user (DECISIONS #31).
3. **Phones:** `submit_enquiry` and the publish `phone` format accept Indian mobile numbers only (`[6-9]` followed by 9 digits); landlines and foreign numbers are rejected. Confirmed by the user.
4. **Account deletion:** the user chose a server-side **Edge Function** (Phase 13) that deletes `site-media/<uid>/` and then the account (DECISIONS #32). Until then, `delete_my_account` refuses with `media_not_deleted` while any media remains, so files are never orphaned. The Edge Function calls it with the service role after deleting the media.
5. **Lapsed agents can't update their profile,** in line with read-only mode in BILLING.md. They can still delete media.

## Follow-ups (required by CLAUDE.md: definitions live only in `shared/`)
Done on 2026-10-15:
- The reserved-slug list moved from `private.is_reserved_slug` into `shared/catalog.json` (`reservedSlugs`, provisional), and `catalog_sync` emits it as `catalog_reserved_slugs()`.
- `catalog_sync` adds CHECK constraints for `sites.template_key` (from `templates/index.json`), `sites.accent` (from `accentSwatches`), and `sites.locale` and `profiles.preferred_language` (from the catalog's `locales`).
- Feature flag overrides live in `flag_overrides`, read through `effective_flags()` (DECISIONS #35).
- `publish_site` accepts deprecated interests through `catalog_interest_keys_all()`.
