# Companies, offers and the admin panel

**Status: 8b-1 (shared definitions and codegen) and 8b-2 (backend, local; not applied to `AGENT_PRO`) built; 8b-3 to 8b-5 and 8c designed, not built** (see [Roadmap](#roadmap)). The definitions in `shared/` are real (SHARED_DEFINITIONS.md, Offers) and the backend is described as built in BACKEND.md, "Companies and offers", which wins where it differs from the plan below (DECISIONS #74). The web, the app and the admin panel are still a plan. Values marked (provisional) aren't set by the user yet.

Agent PRO serves agents of **many insurance companies**. **An agent can be linked to several companies, each with the insurance types they deal in there** (`catalog.insuranceTypes`; DECISIONS #89, replacing the one-company rule of #47/#53), and **an admin verifies each link** before that company's offers appear on the agent's site (DECISIONS #53). The approved companies' **names** (never logos) and types are listed on the site (DECISIONS #90). Admins manage the companies and their **ongoing offers** in a **separate web admin panel**. An agent shows offers on their website in two **slots**:
1. a **featured offer** banner in the hero, and
2. an **Offers section** that the agent can place anywhere in the section order.

The agent works on the **on-device template preview**. They **tap a slot**, then pick one of their approved companies' live offers, or add a **custom offer** of their own. Expired offers disappear by themselves.

Related docs: BACKEND.md (tables, RLS, RPCs), SHARED_DEFINITIONS.md (catalog, schemas), WEB_RENDERER.md (rendering), UX.md (screens), PRIVACY.md (DPDP duties of the admin panel and the audit log), TESTING.md.

## Principles
- **Definitions in `shared/`, content in the database.** The *shape* of an offer, its statuses, its CTA kinds, the admin roles and the limits are definitions, so they live in `shared/`. **Companies and offers themselves are runtime data** that admins create and change without an app release, so they live in tables. That's the same split as `interests` (catalog) vs `enquiries` (table). Company names are stored per locale in the database; they're never i18n keys.
- **Resolve at read time.** A site stores only *references* to company offers. `get_public_site*` resolves them to the offer's current live content on every request. Admin edits reach every site at once, and an ended or archived offer simply isn't returned.
- **Calm and safe by default.** One featured offer at most, at most 6 in the section, no images in the hero banner (it must not compete with the agent's photo, the LCP element), and the accent only for links and small highlights.
- **Branding.** No insurer logo is ever bundled in `web/`, the app or `shared/`. Logos are admin-uploaded content in the `company-media` bucket, served only while the company's permission is recorded and current.

## What lives where
| Thing | Where | Why |
|---|---|---|
| Offer statuses `draft`, `live`, `archived` | `catalog.offerStatuses` (CHECK on `offers.status`) | Stored enum |
| Offer phases `upcoming`, `running`, `ended` | `catalog.offerPhases` (labels and tones only) | Derived from the dates; never stored |
| CTA kinds `enquire`, `call`, `link` | `catalog.offerCtaKinds` (`agentAllowed` extra field: `link` is admin-only) | Stored enum |
| Admin roles `owner`, `editor` | `catalog.adminRoles` | Stored enum |
| Membership statuses `pending` (info), `approved` (success), `rejected` (danger), `revoked` (danger), `withdrawn` (neutral) | `catalog.membershipStatuses` (CHECK on `company_memberships.status`) | Stored enum; labels and tones for the app's pills and the admin panel |
| Rejection reasons `not_found`, `details_mismatch`, `not_agent`, `other` | `catalog.membershipRejectReasons` (provisional) | Shown to the agent in their language |
| Insurance types `life`, `health`, `motor`, `home`, `travel`, `business`, `accident` | `catalog.insuranceTypes` (provisional; DECISIONS #89) | Stored per membership (`company_memberships.insurance_types`); `catalog_insurance_types()` |
| Limits | `catalog.offers` object: `maxSectionItems` 6, `maxValidityDays` 366, `logoMaxBytes` 262144, `imageMaxBytes` 524288, `imageAspect` `"16:9"`, `logoMaxPx` 512 (all provisional) | Server-authoritative numbers |
| Company offer fields | New `shared/offer-schema.json` | Drives the admin panel form, SQL validation (`offer_schema()`), and the renderer's field limits |
| Site slots and custom offers | `site-schema.json`: `hero.featuredOffer`, new `offers` section | Agent content |
| Feature flag `offers` (default on) | `catalog.features` | `OffersFeature` in the app; `flag_overrides` can switch it off |
| Fixed copy ("Details provided by the agent", "Ends 31 Dec", "From {company}") | `shared/i18n` `site.offer_*` keys | Site copy, EN + HI |
| Companies, offers, admins, audit log | Tables (below) | Runtime data |

### `shared/offer-schema.json` (new, planned)
```json
{ "version": 1,
  "localized": [
    { "key": "title",      "type": "text",      "required": true,  "maxLength": 60,  "labelKey": "offer_field.title" },
    { "key": "summary",    "type": "multiline", "required": true,  "maxLength": 160, "labelKey": "offer_field.summary" },
    { "key": "details",    "type": "multiline",                    "maxLength": 600, "labelKey": "offer_field.details" },
    { "key": "badge",      "type": "text",                         "maxLength": 20,  "labelKey": "offer_field.badge" },
    { "key": "disclaimer", "type": "multiline",                    "maxLength": 300, "labelKey": "offer_field.disclaimer" } ],
  "fields": [
    { "key": "image",    "type": "image",  "aspect": "16:9", "labelKey": "offer_field.image" },
    { "key": "ctaKind",  "type": "select", "required": true, "optionsFrom": "catalog.offerCtaKinds", "labelKey": "offer_field.cta_kind" },
    { "key": "ctaUrl",   "type": "url",    "requiredIf": { "ctaKind": "link" }, "labelKey": "offer_field.cta_url" },
    { "key": "interest", "type": "select", "optionsFrom": "catalog.interests", "labelKey": "offer_field.interest" },
    { "key": "startsAt", "type": "date",   "required": true, "labelKey": "offer_field.starts_at" },
    { "key": "endsAt",   "type": "date",   "required": true, "labelKey": "offer_field.ends_at" } ] }
```
- `localized` fields are stored per locale in `offers.content` (`{"en": {...}, "hi": {...}}`); the locales come from `catalog.locales`.
- **Going live needs every `required` localized field in every catalog locale** (EN and HI; DECISIONS #53). Drafts can be incomplete.
- `requiredIf` is a new rule (admin forms only). `verifyShared` checks the file like `site-schema.json` (types, options, i18n keys).

## Data model (planned migration `…_companies_offers.sql`)
All new tables follow BACKEND.md conventions (`id`, `created_at`, `updated_at` with the `set_updated_at` trigger, explicit grants, RLS on). `companies`, `offers`, `admins` and `audit_log` are **not agent-owned**, so they have **admin-only RLS** instead of owner-only RLS: a documented exception to the CLAUDE.md rule (DECISIONS #50). `company_memberships` is agent-owned (owner-only select) but decided by admins (below). `anon` has no privileges on any of them.

### `companies`
| Column | Notes |
|---|---|
| `slug` | unique, `^[a-z0-9-]{2,40}$`; used in admin URLs only |
| `name` jsonb | `{"en": "…", "hi": "…"}`, each ≤ 80; both locales required |
| `website_url` | optional, `https://` only |
| `disclaimer` jsonb | optional per locale, ≤ 300 (for example the insurer's standard "Insurance is the subject matter of solicitation" line) |
| `logo_url` | public URL in `company-media/logos/<company_id>/<n>.webp`; nullable |
| `logo_permission_at` date, `logo_permission_ref` text (≤ 200), `logo_permission_until` date (nullable) | **CHECK:** `logo_url is null or logo_permission_at is not null`. The logo is served only while `logo_permission_until` is null or in the future |
| `allow_custom_offers` boolean, default true | Per-company switch for agents' custom offers (on by default, DECISIONS #53) |
| `is_active` boolean, default true | Inactive companies disappear from pickers and from sites; never hard-deleted |
| `sort` integer | Order in the agent's company picker |
| `created_by`, `updated_by` | uuid of the admin (set by trigger from `auth.uid()`) |

### `offers`
| Column | Notes |
|---|---|
| `company_id` | fk `companies`, `on delete restrict`; indexed |
| `status` | `catalog.offerStatuses` (CHECK from `catalog_sync`): `draft` → `live` → `archived`; `archived` → `draft` is allowed (Restore) |
| `content` jsonb | per-locale localized fields, validated against `offer_schema()` |
| `image_url` | optional, `company-media/offers/<offer_id>/<n>.webp`, 16:9 |
| `cta_kind`, `cta_url` | `cta_url` required and `https://` when `cta_kind = 'link'`, otherwise null |
| `interest` | optional `catalog.interests` key (prefills the enquiry form's interest) |
| `starts_at`, `ends_at` | `timestamptz not null`, `ends_at > starts_at`, `ends_at - starts_at <= maxValidityDays`. The admin panel enters dates; the start is 00:00 and the end 23:59:59 Asia/Kolkata |
| `sort` | Order in the agent's picker |
| `published_at`, `published_by`, `archived_at` | Set by trigger on status change |
| `created_by`, `updated_by` | As above |

- **Phase** (derived, `private.offer_phase(o)`): `upcoming` when `now() < starts_at`, `ended` when `now() >= ends_at`, else `running`. Only `live` + `running` offers render on sites.
- **Guard trigger** `private.offers_guard`: enforces the transitions; on `→ live` validates `content` for every locale, the CTA rule and the dates, and refuses `ends_at <= now()`.
- An index on `(company_id, status, ends_at)` serves the picker and the payload.

### `admins` (allow-list) — chosen over a JWT custom claim
| Column | Notes |
|---|---|
| `user_id` | pk, fk `auth.users on delete cascade` |
| `role` | `catalog.adminRoles`: `owner` (everything, including admins and the audit log) or `editor` (companies and offers) |
| `company_id` | nullable. Null = all companies; set = this admin can edit only that company's offers (for insurer staff later) |
| `invited_by`, `revoked_at` | A revoked admin keeps the row (audit), but has no access |

**Why a table rather than a custom JWT claim:** revoking access is immediate (a claim lives until the token expires, about an hour), every change is auditable with plain SQL, no Auth hook is needed, and RLS can join the company scope. The cost is one indexed lookup per statement, which `(select …)` wrapping makes once per statement.

**Helpers** (`security definer`, `search_path = ''`, `stable`):
- `public.is_admin(p_role text default null) → boolean`: an active `admins` row for `auth.uid()` (with the role if given) **and** `auth.jwt()->>'aal' = 'aal2'` (MFA verified in this session). It's `public` and granted to `authenticated` because RLS calls it, like `can_write()` (an accepted advisor 0029 warning).
- `public.is_admin_for(p_company_id uuid) → boolean`: `is_admin()` and (`company_id` is null or equals the argument).

**Admin accounts are separate people from agents.** They're created only by invitation (Edge Function `admin`, below) with `raw_app_meta_data.account_kind = 'admin'`, which only the service role can set. The signup trigger `private.handle_new_user` **skips** such users, so admins get no profile, subscription or site.

### `audit_log` (shared with PRIVACY.md)
`id bigint identity`, `at timestamptz default now()`, `actor_id uuid` (nullable for system jobs), `actor_kind` (`admin|agent|system`), `action` (`company.update`, `offer.go_live`, `admin.invite`, `person.erase`, …), `entity`, `entity_id`, `changes jsonb` (changed column names and new values for companies and offers; **never** personal data of enquirers or customers), `request_ip inet` (admin actions only, from the PostgREST `request.headers` `x-forwarded-for`). Append-only: no update or delete grants; only `SECURITY DEFINER` triggers and functions insert. Retention is in PRIVACY.md (13 months, provisional).

### `company_memberships` (admin-verified link; DECISIONS #53)
The agent's company is a **membership request that an admin approves**. It's a table rather than status columns on `profiles`:
- **The agent must never be able to approve themselves.** `profiles` is agent-writable under owner-only RLS; status columns there would need column-grant exclusions plus a trigger to stop self-approval. A separate table has its own policies: the agent can only *request*, and only an admin can decide.
- **History and audit:** every request, approval, rejection (with its reason), revocation and change of company is its own row, which the admin queue, the agent's status line and the audit need.
- **"My company isn't listed"** fits as a row with no company and a suggested name, which the admin can turn into a new company.

| Column | Notes |
|---|---|
| `agent_id` | fk `auth.users on delete cascade`; the agent-table conventions apply (`updated_at`, sync index) |
| `company_id` | fk `companies`, `on delete restrict`; **null** for a "not listed" request |
| `suggested_name` | ≤ 80, required when `company_id` is null, otherwise null |
| `agency_code` | ≤ 30, required when `company_id` is set: a copy of the code the agent entered, so the admin can verify it (the profile may change later) |
| `status` | `catalog.membershipStatuses`, default `pending` |
| `reject_reason` | `catalog.membershipRejectReasons` key, required when `rejected` |
| `review_note` | ≤ 200, optional, shown to the agent |
| `insurance_types` | `text[]`, keys of `catalog.insuranceTypes`, unique; **at least one** on pending and approved rows (DECISIONS #89). Changing them needs no new review. Rows from before #89 were backfilled `{life}` |
| `requested_at`, `reviewed_at`, `reviewed_by` | `reviewed_by` = the admin |

- **One current row per company** (DECISIONS #89): a partial unique index on `(agent_id, company_id)` where `status in ('pending', 'approved')`, plus at most one pending "not listed" row per agent. Rejected, revoked and withdrawn rows stay as history.
- **The agent's companies** (for the picker, the payload and validation) are their `approved` rows. **Company offers are available only from approved companies**; `pending`, `rejected` and no row all mean: no offers from that company. Custom offers are available unless any approved, active company switched them off. There's no `profiles.company_id`.
- **Adding a company** never touches the others. **Changing the agency code** for a company withdraws that row and makes a new pending request (a new review); **changing the types** doesn't (`set_membership_types`, or `request_company` with the same code). **Removing a company** is `withdraw_company_request(membership_id)`: its offers stop resolving at once and the next publish reports them as warnings.
- **Revoking** (the agent has left the company, say): the admin sets `revoked` with a note; the company's offers and name leave the agent's site on the next page load.
- `private.memberships_guard`: the company must be `is_active`; transitions are `pending → approved | rejected | withdrawn` and `approved → revoked | withdrawn`; `reviewed_at`/`reviewed_by` are set from `auth.uid()`.
- **Privacy:** to verify a request, admins see only the agent's name, designation, city, agency code, the request's insurance types and email (PRIVACY.md), and the agent's previous decision **for the same company only** (a scoped admin never learns about the agent's other companies). Every review is in `audit_log`.

### Custom offers live in site content, not in a table
Custom offers are stored in `sites.draft_content.offers.custom[]` (and published with the rest).
- **Why:** they are the agent's own site content, like testimonials. Draft vs published, preview from the unsaved draft, validation by `publish_site`, the 64KB cap, deletion with the site and the account, and offline preview all work with no new table, RLS or sync code.
- **Trade-off:** expiry can't be a query. Instead `public_site_payload` drops ended custom offers when it builds the payload, and the app flags them in the editor.
- Each item has a stable `id` (a uuid the app generates) so the hero slot can reference it.

## Site schema changes (planned)
### New field types
| Type | Value | FormRenderer | Publish validation |
|---|---|---|---|
| `offerRef` | `{"source": "company"|"agent", "id": "<uuid>"}` or absent | A `PickerField` row ("Featured offer · None") that calls `onPickOffer(slot)`, which opens the Offers picker sheet | Shape; `company` → the offer exists, belongs to one of the agent's `approved` companies, is `live` and not `ended`; `agent` → the id exists in `offers.custom` |
| `offerList` | array of `offerRef`, `maxItems` | A list of rows (title, phase, Remove, drag to reorder) plus **Add offer**, which opens the picker | As above, per item; no duplicates |

### New field rules
- `generated: "uuid"`: the app fills the value; FormRenderer never shows the field.
- `equals: true` (toggles): the value must be `true` (used for the custom-offer attestation).
- `maxDaysAhead: N` (dates): the date must be no later than today (Asia/Kolkata) + N.

### Changes to `site-schema.json`
- **`hero`** gets `{ "key": "featuredOffer", "type": "offerRef", "labelKey": "field.featured_offer" }`.
- **New section `offers`** (icon `savings` for now; `local_offer` once it's imported, DECISIONS #73), `defaultVisible: false` (the first placed offer turns it on), not pinned, default position after `services`:
  ```json
  { "key": "offers", "labelKey": "section.offers", "icon": "local_offer", "defaultVisible": false,
    "fields": [
      { "key": "title",  "type": "text", "maxLength": 40, "labelKey": "field.offers_title", "defaultKey": "site.offers_title_default" },
      { "key": "items",  "type": "offerList", "maxItems": 6, "labelKey": "field.offers_items" },
      { "key": "custom", "type": "list", "maxItems": 6, "labelKey": "field.custom_offers",
        "itemFields": [
          { "key": "id",        "type": "text", "generated": "uuid" },
          { "key": "title",     "type": "text", "required": true, "maxLength": 60, "labelKey": "offer_field.title" },
          { "key": "summary",   "type": "multiline", "maxLength": 160, "labelKey": "offer_field.summary" },
          { "key": "endsOn",    "type": "date", "required": true, "maxDaysAhead": 366, "labelKey": "offer_field.ends_at" },
          { "key": "ctaKind",   "type": "select", "required": true, "optionsFrom": "catalog.offerCtaKinds", "labelKey": "offer_field.cta_kind" },
          { "key": "interest",  "type": "select", "optionsFrom": "catalog.interests", "labelKey": "offer_field.interest" },
          { "key": "confirmed", "type": "toggle", "equals": true, "labelKey": "field.custom_offer_confirm" } ] } ] }
  ```
  - `optionsFrom: "catalog.offerCtaKinds"` resolves to entries with `agentAllowed: true` for agent forms (`enquire`, `call`), so agents can't add outside links.
  - Custom offers have **no image** (minimisation, and it keeps insurer artwork out of agent uploads).
  - `confirmed` reads "I confirm this offer is accurate and my company allows it" (provisional).
- The templates registry adds `offers` to every template's `sections`.

### `publish_site` changes
- Validates the new types and rules above.
- **Ended or unavailable offers are warnings, not errors**, so one ended offer never blocks publishing unrelated edits. A company offer whose membership is no longer `approved` (withdrawn, revoked) is `offer_unavailable` too: `{ok:true, published_at, warnings:[{section, field, item, rule:'offer_ended'|'offer_unavailable'}]}`. The reference is kept (it's skipped at render) and the app offers "Replace" or "Remove".
- Structural problems are errors: `rule: 'offer_shape'`, `'offer_wrong_company'`, `'offer_custom_missing'`, `'duplicate'`, `'equals'`, `'max_days_ahead'`.
- When **any** of the agent's approved, active companies has `allow_custom_offers = false`, custom-offer references are an error (`custom_offers_off`; the strictest wins, DECISIONS #89).

## Backend (planned)
### Storage bucket `company-media`
- Public read (logos and offer images appear on public sites), `file_size_limit` 524288, `allowed_mime_types` `['image/webp', 'image/png']`. **No SVG** (an SVG opened directly runs scripts).
- Paths `logos/<company_id>/<n>.webp` and `offers/<offer_id>/<n>.webp`; a new `<n>` per upload so caches never serve the old file. The admin panel deletes the previous file after saving.
- Policies on `storage.objects`, all `to authenticated` and `bucket_id = 'company-media'`: insert, update and delete require `(select public.is_admin())` (and for `offers/…` paths, `is_admin_for` that offer's company). There's no select policy beyond public URLs, so agents can't list the bucket.

### RLS and grants
| Table | authenticated grants | Policies |
|---|---|---|
| `companies` | select, insert, update (not `created_by`/`updated_by`, which the trigger sets) | select/insert/update: `(select public.is_admin())`. No delete |
| `offers` | select, insert, update (not the `published_*`/`archived_at`/audit columns) | `(select public.is_admin_for(company_id))` for all three. No delete |
| `admins` | select | select own row for anyone (`user_id = (select auth.uid())`, so the panel can tell "no access"); select all for `is_admin('owner')`. Writes only through the `admin` Edge Function (service role) |
| `audit_log` | select | `is_admin('owner')` only. Inserts only from `SECURITY DEFINER` triggers and functions |
| `company_memberships` | select | select: the owner (`agent_id = (select auth.uid())`), or `(select public.is_admin_for(company_id))` (owners and unscoped editors also see `company_id is null` requests). **No insert/update grants:** the agent writes only through `request_company`, admins only through `admin_review_membership` |

Agents never read `companies`, `offers`, `admins` or `audit_log` directly; they use the RPCs below.

### Triggers
- `private.audit_row()` on `companies`, `offers`, `admins` and `company_memberships` (insert/update): writes `audit_log` with the changed columns (`membership.request`, `membership.approve`, `membership.reject`, `membership.revoke`, `membership.withdraw`).
- `private.stamp_actor()` sets `created_by`/`updated_by`; the offer guard sets `published_at/by` and `archived_at`.

### RPCs
| Function | Caller | Behaviour |
|---|---|---|
| `list_companies() → jsonb` | authenticated | Active companies `[{id, name:{en,hi}, logo_url}]` (logo only while permitted), sorted by `sort`, then name. For the onboarding and Settings pickers |
| `request_company(company_id uuid, suggested_name text, agency_code text, insurance_types text[]) → jsonb` | authenticated, `can_write()` | Inserts a `pending` request **without touching the agent's other companies** (DECISIONS #89). Exactly one of `company_id` (an active company; `agency_code` required) and `suggested_name` ("My company isn't listed"); `insurance_types` ≥ 1 catalog key. Re-requesting a company that's pending or approved with the same agency code only updates the types; a different code withdraws that row and makes a new request. Returns the membership (with `insurance_types`). Errors: `not_found`, `invalid`, `invalid_types` (22023). Full rules in BACKEND.md |
| `withdraw_company_request(membership_id uuid) → jsonb` | authenticated | Sets that pending or approved row of the caller to `withdrawn` |
| `set_membership_types(membership_id uuid, insurance_types text[]) → jsonb` | authenticated, `can_write()` | Replaces the types of a pending or approved row, with no new review |
| `my_company_offers() → jsonb` | authenticated | `{memberships:[{id, status, company:{id, name, logo_url, allow_custom_offers} | null, suggested_name, agency_code, insurance_types, reject_reason, review_note, requested_at, reviewed_at}], offers:[{id, company_id, content, image_url, cta_kind, cta_url, interest, starts_at, ends_at, phase}]}`. `memberships`: per company, the current row, else the latest rejection or revocation (withdrawn ones left out). `offers`: the `live` offers that haven't ended (`upcoming` included, so the agent can place an offer before it starts) of **every approved, active company**, each with its `company_id`. Works on a lapsed plan (read-only) |
| `admin_review_membership(membership_id uuid, decision text, reject_reason text, note text) → jsonb` | authenticated, `is_admin_for(company_id)` | `decision` is `approve`, `reject` (a reason is required) or `revoke`. For a "not listed" row, the admin first creates the company, then calls `admin_link_membership(membership_id, company_id)` (owner or unscoped editor), which sets the company and leaves it `pending` for review |
| `admin_membership_queue(company_id uuid, status text) → jsonb` | authenticated, `is_admin_for` | The requests with only the agent fields needed to verify: name, designation, city, agency code and insurance types (from the request), email, requested date; plus the agent's last decided request for the same company, if any |
| `get_public_site*` (changed) | anon | The payload gains `company` and `offers` (below) |
| `publish_site()` (changed) | authenticated, `can_write()` | See above |
| `admin_offer_usage(offer_id uuid) → jsonb` | authenticated, `is_admin_for` | `{published_sites, draft_sites}` counts only, never which agents (minimisation) |

### Public payload additions (`private.public_site_payload`)
As built (DECISIONS #77, #90): `company` is `{disclaimer}` only (the plan's `name` and `logo_url` were removed), and since DECISIONS #90 a separate `companies` list carries the approved companies' **names** as plain text with their insurance types. No logo, slug, id or link ever reaches a site.
```json
"published_at": "2026-10-18T09:30:00+00:00",
"companies": [ { "name": "Bharat Life", "insurance_types": ["motor"] }, { "name": "Acme Life", "insurance_types": ["life", "health"] } ],
"company": { "disclaimer": "Insurance is the subject matter of solicitation." },
"offers": {
  "featured": { "source": "company", "id": "…", "title": "…", "summary": "…", "details": null, "badge": "Till 31 Dec",
                "image_url": null, "cta": { "kind": "enquire", "interest": "term" }, "ends_on": "2026-12-31",
                "disclaimer": "…", "interest": "term", "from_agent": false },
  "items": [ { "...": "same shape" } ] }
```
- `companies` lists the agent's approved links to active companies, earliest approval first, the name in the site's language (fallback the default locale), the types in catalog order; `[]` when none (DECISIONS #90).
- `company` is the **earliest-approved** active company's `{disclaimer}`, or null without one. It never carries the company's name, slug or logo (DECISIONS #77); `title` is the offer's public, generic name and `internal_name` never appears.
- **Auto offers (DECISIONS #79):** a shown `offers` section with no `items` and no `custom` entries gets up to 3 live, running offers of the approved companies, taken in turn from each company (each by `sort`, then start; companies in approval order), resolved the same way; on an `offers`-family template with no `hero.featuredOffer`, the first of them is featured (no image) and the rest are the items. Details in BACKEND.md, Public payload.
- Text is picked for `sites.locale`, falling back to the catalog's first locale (a new generated `catalog_default_locale()`), so the payload carries one language only.
- **Skipped silently:** company offers of companies the agent isn't `approved` for; company offers that aren't `live`, are `upcoming` or `ended`, or whose company is inactive; custom offers whose `endsOn` has passed (Asia/Kolkata), or when any approved, active company has `allow_custom_offers = false` (an agent without an approved company can always use custom offers). The featured image is always null (the hero keeps one image).
- `cta`: `enquire` (with the offer's interest), `call` (the renderer uses `profile.phone`) or `link` (`url`, https only, company offers only).
- The raw references in `published_content` are still returned verbatim, but the renderer reads only `offers`.

### As built (8b-2) — differences from the plan above (DECISIONS #74)
- **Companies:** insert is for unscoped admins only, update for admins of that company (`is_admin_for(id)`), so insurer staff can edit their own company but not create or edit others. `id` may be chosen by the client on insert (companies and offers), so the panel can name storage paths before saving.
- **Storage:** writes go through `public.can_manage_company_media(name)` (a logo path for admins of that company, an offer path for admins of that offer's company); listing is for admins only.
- **Publish:** an unknown company offer id is `offer_wrong_company`; a company the agent asked for before but isn't approved for now (changed, withdrawn or revoked link) gives the warning `offer_unavailable`, so switching company never blocks a publish. The new rules (`equals`, `maxDaysAhead`, agent CTA kinds, date format) live in `validate_fields`, so custom offers are checked like any list; ids, references and company rules in `validate_site_offers`.
- **Payload:** `disclaimer` on each offer is the offer's own (the company's is in `company.disclaimer`); custom offers also need the confirmation and an agent CTA kind to render.
- **Memberships:** `requested_at` uses `clock_timestamp()` so "the latest request" is well defined; `my_company_offers` lists, per company, the current row or else the latest rejection or revocation; `admin_membership_queue(null, …)` means every company the admin may see. Since DECISIONS #89 (`20261022090000_multi_company.sql`): several companies, `insurance_types`, `set_membership_types`, `withdraw_company_request(membership_id)`, `admin_link_membership` refuses a company the agent already has (`duplicate_membership`).
- **Disguised names (DECISIONS #77):** `offers.internal_name` (≤ 120, admin RLS) holds the real product name for the panel; `content.title` is the public, generic name. `private.public_text_violation` (BACKEND.md, Public text check) rejects any blocked term (`catalog.publicTextRules.blockedTerms`) or any company's name, in any locale, word-bounded with Devanagari vowel signs counted as letters: in every localized offer field and the link and image URLs (`offers_guard`, on save, on text change and on going live; error `blocked_term:<field>:<locale>:<term>`), in company disclaimers (`companies_guard`, its own name included), and in custom offers' title and summary at publish (error rule `blocked_term`). The payload's `company` is `{disclaimer}` only.
- **Auto offers and the sitemap (DECISIONS #79):** see Public payload additions above; `get_public_site*` also return `published_at`, and the anon RPC `list_public_sites()` returns `{slug, published_at}` for every site `get_public_site` serves, except sites with an active custom domain (their domain serves its own sitemap; BACKEND.md, RPCs).
- **`interest` on every offer:** each resolved offer (featured, items, auto) carries its catalog `interest` (null when unset) whatever the CTA kind, so the web can pick a fallback image and group offers in the guide template; `cta.interest` stays on `enquire` CTAs for compatibility.
- **Demo data (local tests and web fixtures only):** `supabase/demo/demo-offers.json` → `supabase/demo/demo_offers.sql` (generated by `gen_demo_offers.py`): a fictional `demo-life` company and 6 disguised offers with internal names, run as `postgres` in the SQL Editor only with approval (BACKEND.md, Demo seed). The JSON's `image` is for the web fixtures; the seed leaves `image_url` null. **This stays for local Postgres tests (`supabase/tests/95_offers.sql`) and `web/dev-fixtures/*`; it no longer reflects the live `AGENT_PRO` project.**
- **Live companies (DECISIONS #94):** the `AGENT_PRO` `companies` table holds 11 real insurers (LIC and 10 private life/health insurers) alongside the demo row, seeded by `supabase/migrations/20261022180000_real_companies.sql`. The 6 disguised demo offers now belong to LIC; their content is unchanged. The old `demo-life` row is deactivated (`is_active = false`), not deleted — companies are **never hard-deleted** (history in `company_memberships` references them permanently via `ON DELETE RESTRICT`). Adding, editing or deactivating a company is still a manual SQL statement until the admin panel (8c) exists.

### In-app preview offers (DECISIONS #84)
`public.preview_site_offers(content, template_key)` (signed-in agents only; migration `20261020090000_preview_offers.sql`) returns `{company, offers}` for the caller's **draft**, resolved by the same `private.site_offers_payload` as `get_public_site`: picked offers, or the automatic ones (#79), with the featured one for the offers family. The app adds it to every `renderSite` payload, so previews show the company's offers as the live site will. Offline, the preview has no offers. BACKEND.md has the details.

### Edge Function `admin` (planned, JWT, `is_admin('owner')` with `aal2`)
- `invite {email, role, company_id?}`: `auth.admin.inviteUserByEmail` with `app_metadata.account_kind = 'admin'`, then inserts `admins`.
- `set-role {user_id, role, company_id?}`, `revoke {user_id}` (sets `revoked_at` and signs the user out of every session), `reset-mfa {user_id}` (deletes their factors; they enrol again at next sign-in).
- Every action writes `audit_log`. An owner can't revoke or demote themselves if they're the last owner.

### Advisors
The new `SECURITY DEFINER` functions exposed to `authenticated` (`is_admin`, `is_admin_for`, `list_companies`, `request_company`, `withdraw_company_request`, `set_membership_types`, `my_company_offers`, `admin_review_membership`, `admin_link_membership`, `admin_membership_queue`, `admin_offer_usage`, `list_public_sites`) are added to BACKEND.md's accepted 0029 list. The anon list gains `list_public_sites` (0028; slugs and publish times only, DECISIONS #79).

## App (planned `feature/offers/`, flag `offers`)
- **Built (DECISIONS #89):** `feature/offers/data/CompanyRepository` (`Company`, `Membership`, `CompanyJson`; **remote only for now**: it holds the last `my_company_offers().memberships` in a `StateFlow`, refreshed after every write and cleared on sign-out, with no Room snapshot yet) over `list_companies`, `request_company`, `withdraw_company_request` and `set_membership_types`; `feature/offers/ui/` `CompaniesViewModel` (the picker, add, edit types, try again and remove flows, shared by onboarding step 1 and Settings → Profile), `CompaniesBlock`/`CompanyCard`, `CompanySheets` (`CompanyPickerContent`, `CompanyFormContent`). The offers parts below are still planned.
- **Package (planned in full):** `feature/offers/` with `data/` (`CompanyRepository`, `OfferRepository` over `list_companies` and `my_company_offers`; `CompanyOfferEntity`/`Dao` and `MembershipEntity`: a Room snapshot replaced whole on every refresh, with no sync cursor) and `ui/` (`PlaceOffersScreen`, `OfferPickerSheet`, `CustomOfferForm`, `CompanyPickerSheet`), plus `OffersFeature.kt` (a `websiteHubEntries` row and the routes).
- **Refresh:** with `DataSync.refreshAll()`, and when Place offers opens. A "new offers" dot compares offer ids with the last seen set (DataStore). When a refresh finds the membership has changed from `pending` to `approved` or `rejected`, the app posts one notification ("LIC has approved your link · Add their offers" / "Your company link wasn't approved") that opens Website → Offers.
- **Forms:** `FieldType.OfferRef` and `FieldType.OfferList` in `core/forms`; FormRenderer renders them as picker rows and calls `onPickOffer(SlotRef)`, as it does `onPickImage`.
- **Writes** are draft saves through `SiteRepository`, like the section editor.

### Tap-to-place: the preview bridge
- **Slot mode.** `SitePreview` gains `slotsMode: Boolean` and `onSlotTap: (SlotRef) -> Unit`. In slot mode the app calls `renderSite(json, {slots: true, focus: "offers.items:2"})`.
- **What the renderer draws in slot mode only** (never on a public page; `?preview=1` and the `slots` option are both required):
  - in the hero, below the CTA, the featured offer or a hairline-dashed placeholder "Tap to add an offer";
  - the Offers section at its current position, with each item tappable and a "+ Add offer" tile; if the section is hidden or has no items, a ghost section at its position: "Tap to show offers here";
  - a placed offer that has ended, greyed, with "Ended · Tap to replace".
- **Messages (JS → app),** from a new `web/preview-bridge.js` that loads only in preview: `{v:1, type:"slot", section:"hero", field:"featuredOffer"}` or `{v:1, type:"slot", section:"offers", field:"items", index:2}` (`index` absent = add). Links and the form are inert in preview.
- **App side:** the message is parsed by a pure `SlotMessage.parse()` that accepts only `(section, field)` pairs whose schema type is `offerRef` or `offerList`, and integer indexes in range. Anything else is ignored.
- **Android transport:** `AndroidSitePreview` already serves the bundled site from the reserved origin `https://appassets.androidplatform.net` (its `AssetSiteClient`, Phase 8; ARCHITECTURE.md). The bridge adds `WebViewCompat.addWebMessageListener(webView, "AgentProBridge", setOf("https://appassets.androidplatform.net"))` (the `androidx.webkit` library, Android-only, inside the `SitePreview` seam), so only pages from that origin can post. No `addJavascriptInterface`; file and content access stay off. Messages are only acted on while slot mode is on.
- **iOS (later):** `WKScriptMessageHandler` named `agentpro`, the same JSON.
- **Accessible path:** WebView placeholders aren't reliable with TalkBack, so every slot is also reachable as a list (the Offers section editor and the hero's Featured offer row), opening the same picker. Place offers has a **List** action in the top bar.

## Screens (UX.md style; planned)
### Profile → Companies (onboarding step 1 and Settings → Profile)
> Built (2026-10-22, UX.md "Your companies"): onboarding step 1 and Settings → Profile, with every add, change and removal saved at once through its RPC (not on Continue). The backend contract it builds on: `request_company(company_id, suggested_name, agency_code, insurance_types)` per company, `set_membership_types`, `withdraw_company_request(membership_id)`, `my_company_offers().memberships` (BACKEND.md, Agent RPCs).
- Step 1 Profile lists **one or more companies**, each with its **agency code** (required, the admin verifies it) and **the insurance types** the agent deals in there (one or more chips from `catalog.insuranceTypes`). **My company isn't listed** asks for the company's name (one text field) and its types.
- The picker (**CompanyPickerSheet**): a search field (when there are more than 8 companies), then one `ListRow` per company (logo 40dp if permitted, otherwise the name's initials in a neutral avatar; the name in the app language); companies already added are marked.
- Saving calls `request_company` once per new company, when its sheet is saved. **The agent is never blocked** (but onboarding step 1 needs at least one pending or approved company): onboarding continues, and the whole app works at once, with custom offers only until approval.

**One row per company in Settings → Profile, by membership status** (a `StatusPill` from `catalog.membershipStatuses` plus an optional detail line; the catalog wording is the only status text), plus **Add company**:
| Status (pill) | Detail line | Action |
|---|---|---|
| `pending` (Waiting for approval) | "Requested 16 Oct" plus the types | **Cancel request** (`withdraw_company_request`), **Edit types** |
| `approved` (Approved) | the types | **Edit types** (no new review), **Remove** (`withdraw_company_request`) |
| `rejected` (Not approved) | "Reason: {reason}" plus the admin's note, if any | **Try again** (reopens the request, prefilled) |
| `revoked` (Removed) | the admin's note, if any | **Request again** |

- **Changing the agency code** of an approved company is a new request: warn first ("Offers from {company} will leave your website until {company} confirms you again").
### Website hub → Offers
- A new `NavRow` between Content and Appearance: **Offers** · "2 on your site", or "Add offers" when none are placed; a small dot when the company has new offers since the last visit. While the membership is `pending`, the supporting line reads "Your own offers · {company} not confirmed yet".

### Place offers (full-height preview in slot mode)
- **Top bar:** "Offers", with **List** (opens the Content → Offers section editor).
- **One hint line** above the preview: "Tap a highlighted spot to add an offer".
- **Bottom:** the hub's sticky bar when the draft differs from the published site: **Preview** is implicit here, so it shows only **Publish** (primary), with "Needs internet" when offline.

### Offers picker sheet (`AppBottomSheet`)
- **Title:** "Choose an offer" (hero) or "Add to Offers" (section).
- **Rows:** one `ListRow` per company offer: leading 16:9 thumbnail or the company logo; title; supporting "Ends 31 Dec · Term insurance" or "Starts 1 Nov"; trailing a check if it's already in this slot. Offers already used elsewhere on the site say "On your site".
- Then the agent's custom offers (supporting "Your offer · ends 15 Nov"), then **Add your own offer** (opens the custom offer form). Hidden when the company has `allow_custom_offers = false`.
- When the slot has an offer: **Remove from this spot** (text button).
- **For the section slot only:** a row "Position · After Services" that opens a small sheet listing the positions ("At the top" is never offered, because the hero is pinned first).
- Choosing an offer closes the sheet, saves the draft, re-renders the preview with that slot focused, and shows "Offer added · Undo".
- **Company offers by membership status** (above the custom offers; the custom offers and **Add your own offer** are always there):
  - none, `withdrawn` or `revoked` → one line, "Choose your company to see its offers", + **Choose company**;
  - `pending` → "{company}'s offers appear once we confirm you're their agent" (no button);
  - `rejected` → "We couldn't confirm you as a {company} agent" + **See why** (opens Settings → Profile);
  - `approved` but nothing live → "No offers from {company} right now".
- **Offline:** the cached offers show, but choosing is disabled with "Needs internet". **Lapsed:** read-only rules (UX.md global rule 10).

### Custom offer form (FormRenderer)
- Fields: Title*, Short description, Ends on* (date picker capped at a year ahead), Button (Enquire / Call), Plan type (optional), and the confirmation toggle. That's 6 visible, so no More details.
- Helper line above Save: "Shown with the note 'Details provided by the agent'".
- Saving adds it to `offers.custom` and places it in the slot that opened the form.

### Offer expiry handling
- An ended offer vanishes from the public site by itself (no app action needed).
- In the app: on Place offers it's greyed "Ended · Tap to replace"; in the section editor its row has a danger-tone `StatusPill` "Ended".
- **Home:** the Today list gets one row only while there's something to fix: "1 offer on your site has ended" → Place offers. The daily digest adds "1 offer on your website ended".
- **Publish warnings** (`offer_ended`, `offer_unavailable`) show as a one-line snackbar after a successful publish: "Published · 1 ended offer isn't shown" with **Fix**.

## Web renderer (as built, WP3b, 2026-10-18)
WEB_RENDERER.md ("Offers", "Templates") is the full spec; this is the offers summary. The plan's company logo and "From {company}" line are gone (DECISIONS #77).
- **Partials:** `web/sections/offer.js` (`offerCard(offer, ctx, {tag, image, featured, button})`, `planCard`, `offerCta`, `pickOffers`, `formatDate`, `todayIST`) and `web/sections/offers.js` (the section, `planList`, `needGroups` for the guide). `hero.js` renders the hero banner.
- **Data:** only the payload's `offers` (`{featured, items}`) and `company`; `normalizeSite` reduces `company` to `{disclaimer}`, so a company name or logo can never be rendered, even if a payload carried one (tested).
- **Card:** white, hairline border, `--shape-md`, `--space-m` padding; the badge as a neutral pill; the source line "My pick for you" (`site.offer_pick`), or "Details provided by the agent" (`site.offer_agent_note`) for custom offers; the public name (`<h3>`); summary; `details` behind a "Details" `<details>` (`offer_field.details`); "Ends 31 Mar 2027" (`site.offer_ends`, localised with `Intl` en-IN/hi-IN); the CTA as an accent text link; the offer's disclaimer as muted fine print. The company disclaimer shows once, under the section (or under the hero banner when the section is hidden).
- **Hero banner:** the featured offer (the title is not a heading, never an image). Personal templates: a white card with a text-link CTA, below the hero's own button. Offers family: the card on the pale `--tint`, and its CTA **is** the hero's one button; spotlight leads with the first offer when there's no featured one, the guide never does.
- **CTAs:** `enquire` → `#contact` with `data-interest` (a catalog key only; `hydrate.js` preselects the form); `call` → `tel:` from the contact/profile phone (none without one); `link` → `https` only, `target="_blank" rel="noopener nofollow"`.
- **Images (offers family only, DECISIONS #76):** the admin's `image_url` if `https`; else, for demo fixtures only, a bundled image named by the payload offer's optional `image` (`^offer-[a-z0-9-]+$`, and listed in `web/images/manifest.json`); else the plan-type image of the offer's `interest`; else none. Real payloads have no `image` field.
- **No offers:** the offers family shows plan-type cards from the agent's services under "Plans I can help you with" (`site.plans_heading`); the personal family shows nothing.
- **Layouts:** classic one column; modern two columns from 720 px; minimal a hairline list; spotlight an image grid (1/2/3 columns); guide one anchored group per interest (`#need-<interest>`), reached from the hero's "What would you like to protect?" chips.
- **Expiry:** SQL already filters; the renderer also hides any offer whose `ends_on` is before today in Asia/Kolkata.
- **SEO:** no `Offer` JSON-LD; the `InsuranceAgency` JSON-LD never names the company. Offer text is indexable page content.
- **Fixtures:** `sample-en`/`sample-hi` carry the demo offers (from `supabase/demo/demo-offers.json`; a featured one, a custom call offer, an ended one that must not render) and `company: {disclaimer}`; `sample-offers` (spotlight) all six plus a link CTA; `sample-xss` hostile offers and a company with a name and logo that must not render.
- **Interest:** the renderer reads the offer's top-level `interest` (added by WP2 to every resolved offer), falling back to `cta.interest`, for the image fallback and the guide's groups; a `call` or `link` offer therefore lands in its plan type's group.
- **Still to do (8b-4):** slot mode and `preview-bridge.js` for the app's picker.

## Admin panel (planned, Phase 8c)
### Technology
- **A separate app in `admin/`:** Vite + TypeScript + React + `@supabase/supabase-js` v2 (auth with MFA, PostgREST, Storage uploads). No UI kit; it imports `tokens.css` (generated from `shared/design-tokens.json`) and the renderer's `web/sections/offer.js` for previews.
- **Why it's allowed an SDK when the renderer isn't (DECISIONS #26):** the renderer's "no SDK, < 50 KB" rule exists for public page speed and SEO. The admin panel has a handful of signed-in users; correct auth, MFA and token refresh matter far more than bytes, and supabase-js is the supported way to do them.
- **Forms are generated from `shared/offer-schema.json`** (and the company fields), so limits always match the SQL.
- **The admin UI copy is English only** (internal staff, provisional); offer *content* is entered in every catalog locale.

### Hosting
- A separate Cloudflare Worker with static assets (`agentpro-admin`), served at `admin.<primary domain>` (the `admin` slug is already reserved). Deploying needs the user's approval each time.
- `_headers` file (Workers static assets): `X-Robots-Tag: noindex, nofollow`, a strict CSP (`default-src 'self'`; `connect-src` the Supabase URL; `img-src 'self'` + the Storage URL; `frame-ancestors 'none'`), `Referrer-Policy: no-referrer`, HSTS.
- Build env: `SUPABASE_URL`, `SUPABASE_ANON_KEY` only. **The service role key never reaches the panel**; privileged actions go through the `admin` Edge Function.

### Auth
- Supabase email + password on the same project, **invite-only** (no sign-up screen). A signed-in user without an active `admins` row sees "No access" and is signed out.
- **MFA is mandatory:** TOTP enrolment on first sign-in, and every session must reach `aal2` before any screen loads. RLS enforces it too (`is_admin()` requires `aal2`), so skipping the UI doesn't help.
- An idle timeout of 30 minutes in the client (Supabase's server-side time-boxed sessions need a paid plan). Sign out everywhere on revoke.
- Recommended: every owner keeps two admins with MFA, so a lost phone isn't a lock-out (`reset-mfa`).

### Screens
| Screen | Contents |
|---|---|
| Sign in → MFA (enrol or verify) | Email, password; then a 6-digit code |
| Companies | Table: logo, name (EN), status (Active/Inactive), live offers, updated. **New company** |
| Company edit | Name EN* and HI*, slug*, website, disclaimer EN/HI, sort, Active, Allow custom offers. **Logo:** upload (PNG/WebP, ≤ 256 KB, square, ≤ 512px; previewed at 24px and 40dp) — disabled until **"The company has given written permission to show this logo"** is ticked with the permission date* and a reference (for example an email or letter number); optional "Permission ends on". Removing the permission removes the logo from every site at once |
| Offers | Filters: company, status, phase. Rows: title, company, status pill + phase, starts/ends, "Used on 12 sites". **New offer** |
| Offer edit | EN and HI side by side for the localized fields, with counters from the schema; image upload with a 16:9 crop; CTA; plan type; start and end dates. A **live preview** of the card and the hero banner in all 3 templates and both languages. Actions: **Save draft**, **Go live** (shows "Scheduled — starts 1 Nov" when the start is in the future), **Archive** (confirm: "Used on 12 websites. It will disappear from them now."), **Restore** to draft |
| Membership requests | A queue, oldest first, filtered by company (scoped editors see only theirs) and status (Pending by default). Rows: agent name, designation, city, agency code, requested date, and "Previously rejected" when there's an earlier decision. Detail: the same fields plus the agent's email. Actions: **Approve**, **Reject** (a reason from `membershipRejectReasons` is required, plus an optional note the agent sees), and on approved links **Revoke** (a note is required). "My company isn't listed" requests show the suggested name and **Create company** (prefilled), then **Link to company**, then the usual review. Every action is audited. The count of pending requests shows in the navigation |
| Admins (owner) | List with role, scope, MFA status, last sign-in; **Invite**, change role, revoke, reset MFA |
| Audit log (owner) | Newest first; filter by actor, entity, action, date; shows the changed fields |
| Privacy (owner) | The privacy-request inbox and breach register (PRIVACY.md) |

## Roadmap
These phases go after Phase 8 (they need the Website hub and `SitePreview`). They're independent of Phases 9–10.

### Phase 8b — Companies & offers (shared + backend + web + app)
| WP | Scope | Owns (files) | Depends on | Model |
|---|---|---|---|---|
| 8b-1 Shared + codegen ✅ **built 2026-10-17** | `catalog.offerStatuses/offerPhases/offerCtaKinds/adminRoles/membershipStatuses/membershipRejectReasons/offers`, flag `offers`, `shared/offer-schema.json`, site-schema `featuredOffer` + `offers` section, field types `offerRef`/`offerList`, rules `generated`/`equals`/`maxDaysAhead`/`requiredIf`, i18n keys (EN + HI); codegen: new `FieldType`s and `FieldSpec` rules, `catalog_sync` emits `offer_schema()`, `catalog_locales()`, `catalog_default_locale()`, `catalog_offer_cta_kinds[_agent]()`, `catalog_membership_reject_reasons()`, `catalog_offer_limits()`; `verifyShared` rules. **Changed from the plan:** no `OfferSchema.kt` (the app never edits company offers, DECISIONS #73), and the table CHECKs moved to 8b-2 (DECISIONS #72) | `shared/`, `mobile/build-logic/`, SHARED_DEFINITIONS.md | — | Opus |
| 8b-2 Backend ✅ **built 2026-10-18 (local; applying needs approval)** | **First:** add the five CHECKs (`offers.status`, `offers.cta_kind`, `admins.role`, `company_memberships.status`, `company_memberships.reject_reason`) to `CHECKS` in `CatalogSyncGenerator.kt`, then write the migration, delete the unapplied `20261017085151_catalog_sync.sql` if it's still unapplied, and rerun `generateShared` so the new `catalog_sync` sorts after the tables. Migration (tables including `company_memberships`, guard and audit triggers, the membership RPCs, signup-trigger skip for admins), `company-media` bucket, RLS, RPCs, `publish_site` and payload changes; `supabase/tests/95_offers.sql`; advisors | `supabase/migrations/`, `supabase/tests/`, BACKEND.md | 8b-1 (catalog_sync names) | Opus |
| 8b-3 Web renderer ✅ **built 2026-10-18 (WP3b), except slot mode + `preview-bridge.js` (moved to 8b-4)** | `offer.js`, `offers.js`, hero banner, the template CSS (5 templates), fixtures, `test-web-offers.mjs` cases; `offers` removed from `PENDING` | `web/`, `scripts/test-web.mjs`, WEB_RENDERER.md | 8b-1 (schema), payload contract above | Sonnet |
| 8b-4 App | `feature/offers/`, FormRenderer offer fields (replace the empty `OfferRef`/`OfferList` branch), remove `offers` from `SiteContent.pendingSections`, app validators for `mustBeTrue` and `maxDaysAhead`, `SitePreview` slot mode with `addWebMessageListener` on the existing asset origin, company field in onboarding and Settings with the membership states and the approval notification, Home/digest rows, strings EN + HI, tests and screenshots | `mobile/composeApp/.../feature/offers/`, `core/forms/` (offer types only), `core/platform/SitePreview` + its Android implementation (coordinate with the Phase 8 owner), UX.md, ARCHITECTURE.md | Phase 8, 8b-1; 8b-2 for live tests | Opus |
| 8b-5 Verifier | TESTING.md checks for 8b end to end | read-only | all | Sonnet |

8b-2 and 8b-3 can run in parallel once 8b-1 lands; 8b-4 can start on fakes at the same time.

### Phase 8c — Admin panel
| WP | Scope | Owns | Depends on | Model |
|---|---|---|---|---|
| 8c-1 Edge Function `admin` | invite, set-role, revoke, reset-mfa; audit | `supabase/functions/admin/` | 8b-2 | Opus |
| 8c-2 Admin app: auth + shell | Vite/TS/React scaffold, MFA enrol/verify, no-access, idle timeout, headers, `wrangler.jsonc` + `_headers` | `admin/` (shell, auth) | 8b-2 | Opus |
| 8c-3 Admin app: CRUD screens | Companies, logo permission, offers with schema-driven forms and live preview, archive/restore, usage counts, membership requests, admins, audit log | `admin/` (screens) | 8c-2 | Sonnet |
| 8c-4 Deploy | Cloudflare Worker and subdomain — **needs the user's approval** | — | 8c-2, 8c-3 | orchestrator |

## Tests (to add to TESTING.md when built)
- **SQL (`95_offers.sql`):** anon has no access to the 5 new tables or the bucket; an agent can't insert or update `company_memberships` directly, can't approve themselves, and has at most one pending or approved row per company (several companies at once; a new company never withdraws another; insurance types required and validated; `set_membership_types` only by the owner); a scoped editor can't review another company's requests; `my_company_offers` and the payload return no company offers or logo while `pending`, `rejected` or `revoked`, and do after `approved`; revoking removes them on the next load; an agent can't read `companies`/`offers` directly; an admin without `aal2` is refused; an editor scoped to one company can't edit another's offers; `→ live` refuses missing Hindi, a past end, a `link` without https; archived offers and ended or upcoming offers never appear in the payload; another company's offer id is rejected by `publish_site` (`offer_wrong_company`); an ended offer is a warning; ended custom offers are dropped from the payload; the payload's company is `{disclaimer}` only (no name, slug or logo); `internal_name` never appears in `my_company_offers` or the payload; blocked terms and company names are rejected in EN and HI (offer fields, URLs, disclaimers, custom offers at publish) with the word-boundary cases; auto offers (≤ 3, running only, none when hidden, featured only for the `offers` family); `list_public_sites`; the demo seed; `admin_offer_usage` returns counts only; audit rows for every admin write; admin signups get no profile. (Built: see TESTING.md, `95_offers.sql`.)
- **Web:** cards in 3 templates × 2 locales; the agent note on custom offers; no logo without `logo_url`; expired cards hidden client-side; slot placeholders appear only with `?preview=1` + `slots`; `preview-bridge.js` isn't loaded on public pages; the size budget.
- **App:** `SlotMessage.parse` accepts only schema offer fields and in-range indexes; picker states (no company, pending, rejected, approved with none live, offline, lapsed); the Settings company row in each membership status; the one-time approval notification; custom form validation (`equals`, `maxDaysAhead`); publish warnings → snackbar; company change warning; screenshots of Place offers and the sheet in 4 variants.
- **Admin:** a non-admin sees "No access"; MFA is required before any data loads; the logo upload is disabled until permission is recorded; reject needs a reason and revoke a note; a "not listed" request can be turned into a company and linked; archive removes the offer from a live site on the next load; audit entries appear.

## Decisions (2026-10-16)
The user's answers are recorded in DECISIONS #49–#53 (point 1 refined by #89: several companies per agent, each link verified separately; #90: the approved companies' names on sites):
1. **Company membership is verified by an admin** (`company_memberships`, above). The agent picks their companies (each with its agency code and insurance types; DECISIONS #89) or "My company isn't listed" and uses the app at once with custom offers; a company's offers appear only once an admin approves that link.
2. **Custom offers are on by default,** with the confirmation toggle, the "Details provided by the agent" note and a per-company off switch. **(counsel)** whether IRDAI's advertising rules require the insurer's prior approval of an agent's offer text.
3. **Offers need English and Hindi** before they can go live.
