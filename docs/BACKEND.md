# Backend — Supabase `AGENT_PRO`

- **Project:** `AGENT_PRO` (ref `rpqatijolcajehshoikc`) in org letssee, ap-south-1 (Mumbai), Postgres 17. It was created by the user on 2026-10-16. **All 8 migrations below are applied** (2026-10-16): 7 through the Supabase connector, and `rpcs` by the user in the Dashboard SQL Editor, because the connector auto-declines it as destructive (it contains `delete from auth.users`). So the remote migration history lists 7 entries under connector-assigned versions; run `supabase migration repair` before any future `supabase db push`. `20261016220600_privacy.sql` and then `20261016220836_catalog_sync.sql` were applied by the user in the SQL Editor on 2026-10-17 (P1 release). **Not applied yet:** `20261017050728_catalog_sync.sql` (Add to home screen; needs the user's approval).
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

Internal helpers live in the **`private` schema**, which the Data API doesn't expose and the API roles can't use. These include `set_updated_at`, `handle_new_user`, `service_pricing`, `flag_enabled`, `find_public_site`, `public_site_payload`, `validate_site_content`, `validate_fields`, `normalize_in_phone`, `normalize_host`, `is_reserved_slug`, `sites_slug_guard`, `require_uid`, `require_can_write`, and (P1-2) `resolve_consent`, `retention_config`, `minimise_payment_raw` and `payments_minimise_raw`.

## `catalog_sync` contract (what the generated migration must emit)
The migrations call these objects by name, so `generateShared` must produce exactly the following:

The generated file is built from `shared/catalog.json`, `shared/site-schema.json` and `shared/templates/index.json`, plus the privacy notice texts in `shared/i18n` and the notice hashes in `mobile/build-logic/catalog-keys.lock.json`; its line-3 hash covers the three files and the current notice hashes (SHARED_DEFINITIONS.md, Codegen).

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

   The three privacy CHECKs are emitted only when the catalog has the list (they're optional lists in the generator; the real catalog has all three).
2. **Functions.** Each is `language sql immutable set search_path = ''`, followed by `revoke all on function … from public, anon, authenticated;`. Only SECURITY DEFINER code calls them, so no grants are needed.
   - `public.catalog_interest_keys() returns text[]`: the **active** interest keys (deprecated ones left out). Used by `submit_enquiry`, so a deprecated interest can't be submitted.
   - `public.catalog_interest_keys_all() returns text[]`: every interest key, deprecated ones included. Used by `publish_site`, so content that already uses a deprecated interest still publishes.
   - `public.catalog_premium_mode_months(p_mode text) returns integer`: `months` per `premiumModes` key, or null if the key is unknown. Used by `mark_premium_paid`.
   - `public.catalog_reserved_slugs() returns text[]`: `catalog.reservedSlugs`. Used by `private.is_reserved_slug` (the slug trigger and `is_slug_available`).
   - `public.site_schema() returns jsonb`: the contents of `shared/site-schema.json`, verbatim. Used by `publish_site`.
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
| `get_public_site(slug text) → jsonb` | anon | For a published site whose owner isn't `lapsed`, returns `{template_key, accent, locale, published_content, canonical_host, profile}`; otherwise null. See [Public payload](#public-payload). The slug is trimmed and lowercased |
| `get_public_site_by_host(host text) → jsonb` | anon | Same payload. The site is resolved through `site_domains`, where `hostname` = host (lowercased, port and trailing dot stripped), `status in ('active','renew_soon')`, `expires_at` is null or in the future, and `deleted_at` is null |
| `submit_enquiry(slug text, host text, name text, phone text, email text, interest text, message text, honeypot text, consent jsonb default null) → jsonb` | anon | See [submit_enquiry](#submit_enquiry). The only overload (the 8-argument one was dropped in P1-2) |
| `submit_data_request(slug text, host text, name text, phone text, kind text, message text, honeypot text) → jsonb` | anon | See [Privacy](#privacy-p1-2) |
| `record_site_view(slug text, host text) → void` | anon | Resolves the site the same way as `submit_enquiry` and upserts `site_daily_views` for today (Asia/Kolkata), +1. Does nothing for an unknown, unpublished or lapsed site |
| `is_slug_available(slug text) → boolean` | authenticated | Checks the format, the reserved-words list, and that no other agent's site (including soft-deleted ones) uses the slug. The caller's own slug counts as available |
| `publish_site() → jsonb` | authenticated, `can_write()` | Validates the caller's `draft_content` against `site_schema()`. On success it copies draft to published, sets `published_at` and `is_published = true`, and returns `{ok:true, published_at}`. Otherwise it returns `{ok:false, errors:[{section, field, item, rule}]}`. See [Publish validation](#publish-validation) |
| `mark_premium_paid(policy_id uuid, amount numeric, paid_on date) → jsonb` | authenticated, `can_write()` | In one transaction: inserts a `premium_payments` row (`period_due_date` = the current `next_due_date`) and moves `next_due_date` forward by `catalog_premium_mode_months(premium_mode)`. Returns `{payment_id, period_due_date, next_due_date}`. See [Due date rule](#due-date-rule) |
| `undo_premium_payment(payment_id uuid) → jsonb` | authenticated, `can_write()` | Works only on the policy's **latest** live payment (otherwise `not_latest_payment`). Soft-deletes it (sets `deleted_at`, so sync sees it), restores `next_due_date` from `period_due_date`, and returns `{policy_id, next_due_date}` |
| `delete_my_account() → void` | authenticated | See [Account deletion](#account-deletion) |
| `record_consent(notice_key text, version integer, hash text, customer_id uuid default null) → jsonb` | authenticated (no `can_write()`) | See [Privacy](#privacy-p1-2) |
| `update_data_request(id uuid, status text, note text) → jsonb` | authenticated (no `can_write()`) | See [Privacy](#privacy-p1-2) |

All of these except `effective_flags()` (security invoker, because it only reads `app_config`) are `SECURITY DEFINER`. All have `set search_path = ''` and fully qualified names. `execute` is revoked from `public`, `anon` and `authenticated`, and granted only to the roles listed.

**Errors from authenticated RPCs** are raised with a snake_case message:
- `not_authenticated` and `plan_read_only` use SQLSTATE 42501, which PostgREST returns as 401/403.
- `not_found` uses P0002 (404).
- `invalid_amount`, `invalid_paid_on`, `no_due_date`, `unknown_premium_mode`, `invalid_status` and `invalid_note` use 22023.
- `not_latest_payment`, `orders_in_progress` and `media_not_deleted` use P0001.

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
   | interest | Optional. If present, it must be in `catalog_interest_keys()` (active interests only; a deprecated one is rejected) |
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
  - `option`: the value must be in `options[].key`, or in `catalog_interest_keys_all()` for `optionsFrom: "catalog.interests"`. Deprecated interests are accepted here, so existing content keeps publishing.
- **List items** are validated against `itemFields`, and the error's `item` is the 0-based index.

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

## Realtime
Publication `supabase_realtime` includes `enquiries`, `orders`, `subscriptions` and `site_domains`, and RLS applies to realtime too.

## Auth
- Email and password, with **email confirmation on**.
- The redirect URL for password reset and confirmation is `agentpro://auth-callback`, the app's deep link.
- Password minimum is 8 characters. The site URL is set to the renderer domain.
- **Auth emails** go through **custom SMTP via Gmail** (`smtp.gmail.com:465`, a Gmail app password; set on 2026-10-17, DECISIONS #68), an interim sender until the transactional provider (DECISIONS #60). Auth → Rate Limits → emails is **100 per hour**; the minimum interval per address is 60 s. Gmail itself caps a free account at about 500 emails a day. With custom SMTP, a failed handover to Gmail makes `/signup` return an error, not 200. Two refusals share the code `over_email_send_rate_limit` (HTTP 429), and the app tells them apart by the message (`ErrorMapper` → `AppError.EmailLimited`):
  - the **per-address wait**: "For security purposes, you can only request this after N seconds" (one email per address per 60 s; `AuthRules.EMAIL_RESEND_SECONDS`);
  - the **project's hourly quota**: "email rate limit exceeded" (now 100 per hour; it was about 2 per hour on the built-in sender).
  - **Email templates:** `supabase/email-templates/{confirmation,recovery,invite,email_change,reauthentication}.html`, with the subjects in `subjects.json`. They're generated by `node scripts/gen-email-templates.mjs` from `shared/design-tokens.json` (DESIGN_SYSTEM.md, Auth emails). **They are applied by hand:** paste each file into Authentication → Emails → Templates, and its subject from `subjects.json`. `magic_link` isn't used, so it's left alone. They use only Supabase's `{{ .ConfirmationURL }}`, `{{ .Email }}`, `{{ .NewEmail }}` and `{{ .Token }}`. After changing the tokens or the copy, regenerate and paste again.
  - A refused `/signup` creates no user (checked on `AGENT_PRO`, 2026-10-17), so signing up again later is safe. For an existing unconfirmed address, `/signup` sends the confirmation again.
- **Signup trigger** `on_auth_user_created` (after insert on `auth.users`, function `private.handle_new_user`):
  - creates `profiles` (with `email`, plus `full_name` and `phone` from `raw_user_meta_data`; the app's Create account sends both, the phone already as `+91XXXXXXXXXX`; DECISIONS #70),
  - creates `subscriptions` with `trial_ends_at = now() + pricing.service.trialDays`,
  - records the sign-up consent: the app sends `raw_user_meta_data.consent = {"notice_key":"agent_signup","version":…,"hash":…}` in the Auth sign-up call; if it's valid (consent validation contract) a `consents` row is inserted (`principal_kind = 'agent'`, `channel = 'app'`). **If it's missing or invalid the user is still created** (Auth can't be blocked cleanly from a trigger) and nothing is recorded; the app must then call `record_consent('agent_signup', …)` before continuing. The app can check with `select … from consents where notice_key = 'agent_signup'` (owner-only RLS).

## Planned (designed, not built): companies, offers, admins and privacy
Nothing in this section exists in the migrations yet. The full designs are in OFFERS.md and PRIVACY.md; this summary moves into the sections above as each part is built.
- **Companies and offers (OFFERS.md, Phase 8b):** tables `companies`, `offers`, `admins` and `audit_log`, plus `company_memberships` (the agent's company link, **approved by an admin**; the agent can only request it, and company offers and the logo appear only while it's `approved`; DECISIONS #53); the `company-media` bucket (admin-only writes, public read, PNG/WebP only). The first four are **not agent-owned**, so they use **admin-only RLS** through `public.is_admin()` / `is_admin_for(company_id)`, which also require an MFA session (`aal2`). New RPCs `list_companies`, `request_company`, `withdraw_company_request`, `my_company_offers`, `admin_review_membership`, `admin_link_membership`, `admin_membership_queue` and `admin_offer_usage`; `publish_site` validates the new `offerRef`/`offerList` fields (ended offers are warnings); `get_public_site*` gain `company` and resolved `offers`. The signup trigger will skip invited admins (`app_metadata.account_kind = 'admin'`). Edge Function `admin` (invite, set-role, revoke, reset-mfa).
- **Privacy, still planned (PRIVACY.md, phase P2):** tables `nominations`, `incidents`; `erased_at` columns; RPCs `withdraw_consent`, `erase_enquiry`, `erase_enquiries`, `erase_customer`, `my_data_summary`, `create_platform_request`; a `pg_cron` retention job (`private.retention_run()`) with the schedule in PRIVACY.md (soft-deleted rows purged after 30 days; lapsed accounts deleted 12 months after lapse with notices at 30 days, 7 days and 48 hours; **never-converted enquiries are not deleted automatically**: the agent, as their fiduciary, clears them with the app's tools; DECISIONS #54); the `audit_log` owner-admin select policy (with the admin tables). P1-2's part is built: see [Privacy](#privacy-p1-2). **Backups:** the project is on the free plan with no backups; Supabase Pro is high priority and not yet approved (PRIVACY.md, Backup risk).

## Privacy (P1-2)
Built 2026-10-17 in `20261016220600_privacy.sql` (PRIVACY.md; DECISIONS #48, #54, #61). **Not applied to `AGENT_PRO` yet.**

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
- **Re-applying `catalog_sync`:** `run.sh` copies the latest `catalog_sync` into the container and passes its path as the psql variable `catalog_sync`, so a test can run `\i :catalog_sync` inside a transaction (used to prove overrides survive a catalog migration).
- How to run it is in TESTING.md.

## Advisors
**Last run: 2026-10-16 on `AGENT_PRO`, after all migrations.** There are no ERROR findings.
- **Fixed:** `rls_auto_enable` could be executed by `anon` and `authenticated`. The fix is the migration `20261016035136_revoke_rls_auto_enable.sql`; the event trigger stays enabled.
- **Accepted by design (WARN, lint 0028):** `anon` can execute 4 SECURITY DEFINER functions: `get_public_site`, `get_public_site_by_host`, `submit_enquiry` and `record_site_view` (**5 once P1-2 is applied:** plus `submit_data_request`). CLAUDE.md requires public access to go only through these RPCs, and `anon` has no table privileges.
- **Accepted by design (WARN, lint 0029):** `authenticated` can execute 7 SECURITY DEFINER functions: `can_write`, `my_entitlement`, `is_slug_available`, `publish_site`, `mark_premium_paid`, `undo_premium_payment` and `delete_my_account` (**9 once P1-2 is applied:** plus `record_consent` and `update_data_request`). Each one checks `auth.uid()` itself. `can_write` must stay callable because RLS policies call it as `authenticated`.
- **Performance (INFO only):** 27 "unused index" notices, which are expected on an empty database. Re-check once there's real traffic.

Locally, `supabase/tests/80_static_review.sql` checks the usual advisor findings:
- RLS is on for every table;
- `anon` holds no table grants;
- every function pins its `search_path`;
- `anon` and `authenticated` can execute exactly the documented functions;
- policies wrap `auth.uid()` and `can_write()` in `(select …)`;
- every FK has a covering index;
- every agent table has an `(agent_id, updated_at)` index (`audit_log` is excluded: not agent-owned);
- `audit_log` has no API grants and is append-only for the service role; `submit_enquiry` has exactly one overload; the 13 catalog CHECKs exist.

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
