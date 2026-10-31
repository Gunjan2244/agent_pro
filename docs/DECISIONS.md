# Decisions log

This log is append-only. Each entry is numbered and dated. To reverse a decision, add a new entry that supersedes it and mark the old one `Superseded by #N`.

**Format:** `#N — YYYY-MM-DD — Title`, followed by **Decision**, **Why**, **Alternatives considered** and **Status**.

---

### #1 — 2026-10-15 — Backend: Supabase
- **Decision:** Supabase (Postgres, Auth, RLS, Realtime, Storage) in a new project, `agent-pro`, in ap-south-1.
- **Why:** the account was already connected. It gives SQL, row-level security, realtime enquiries and storage in one place, and Mumbai is close to the users.
- **Alternatives considered:** Firebase; writing our own Ktor/Node server.
- **Status:** Accepted. The project isn't created yet.

### #2 — 2026-10-15 — One static renderer for all agent sites
- **Decision:** a single static site renders any agent's site from the database: `/a/{slug}` now, custom hosts later.
- **Why:** template changes reach every agent instantly, edits need no redeploy, and there's one codebase to maintain.
- **Alternatives considered:** generating and uploading HTML per agent; custom domains from day one.
- **Status:** Accepted.

### #3 — 2026-10-15 — Login: email + password
- **Decision:** Supabase email and password login, with password reset.
- **Why:** it's free and works out of the box.
- **Alternatives considered:** phone OTP (needs a paid SMS provider); Google sign-in.
- **Status:** Accepted.

### #4 — 2026-10-15 — Hosting: Vercel
- **Decision:** host the renderer on Vercel.
- **Why:** its API can attach custom domains with automatic SSL, which the planned domain purchase feature needs, and it offers domain registration. Edge Functions allow server-side rendering later.
- **Alternatives considered:** Netlify; Cloudflare Pages, whose registrar has no purchase API.
- **Status:** Superseded by #59 (Cloudflare). Registrar API coverage still needs confirming (see DOMAINS.md).

### #5 — 2026-10-15 — Single source of truth in `shared/` with codegen
- **Decision:** all enums, site schema, tokens, templates registry and labels live in `shared/*.json`. Gradle `generateShared` generates the Kotlin, strings XML, web assets and SQL constraints, and `verifyShared` enforces consistency.
- **Why:** the user wants every feature defined in one place, so edits are easy and uniform. Codegen keeps the app type-safe.
- **Alternatives considered:** Kotlin enums as the source; database lookup tables.
- **Status:** Accepted.

### #6 — 2026-10-15 — Manual DI, single app module
- **Decision:** manual DI through `AppContainer` (no Hilt), and one `:app` module with feature-first packages.
- **Why:** a simpler build and faster iteration. The packages can become Gradle modules later.
- **Alternatives considered:** Hilt; a multi-module setup from the start.
- **Status:** Manual DI is still accepted. The module layout is superseded by #22 (`composeApp` + `androidApp`).

### #7 — 2026-10-15 — Languages: English + Hindi at launch
- **Decision:** both languages in v1, with a per-app locale switch.
- **Why:** user choice, and the agents' customers are often Hindi-speaking.
- **Status:** Accepted.

### #8 — 2026-10-15 — Offline: read-only Room cache
- **Decision:** Room caches everything for reading, while writes require a connection. There's no write queue.
- **Why:** agents work in the field with patchy network. A read cache gives most of the benefit without the complexity of sync conflicts.
- **Alternatives considered:** full offline sync; online only.
- **Status:** Accepted.

### #9 — 2026-10-15 — Theme "Trust & Warmth", dynamic colour off
- **Decision:** Indigo `#1F4AA8` primary, Marigold `#E8A317` secondary used sparingly, Teal `#0F7F74` tertiary, and a warm off-white surface. Poppins for headings, Noto Sans for body text. Material You dynamic colour is off.
- **Why:** it signals trust and stays consistent across the app and sites. Poppins covers Devanagari for Hindi headings.
- **Status:** Superseded by #15.

### #10 — 2026-10-15 — Charts: Vico + dataviz reference palette
- **Decision:** Vico, wrapped in a central `ChartStyle`, with series colours from the validated dataviz palette (blue, orange), separate from the brand colours. No dual axes, and every chart has a table view.
- **Why:** colourblind-safe, validated colours; one place to style charts; accessibility.
- **Status:** Accepted.

### #11 — 2026-10-15 — Features plug in through `FeatureRegistry` + flags
- **Decision:** each feature implements `AppFeature`. The nav, bottom bar, Home, Settings and Website hub are built from the enabled features. Flags default from the catalog and can be overridden remotely through `app_config`.
- **Why:** custom domains, payments and later features can be added without rewiring, and switched on without an app update.
- **Status:** Accepted.

### #12 — 2026-10-15 — Custom domains: seams in v1, feature later
- **Decision:** v1 includes host-based site resolution, the `site_domains` table, host-aware RPCs and the off flag. The purchase and connect flows come later (DOMAINS.md).
- **Why:** the user wants domain purchase to be easy to add.
- **Status:** Superseded by #16. The seams remain, and the feature itself is now in v1.

### #13 — 2026-10-15 — Docs are mandatory and updated with every change
- **Decision:** CLAUDE.md requires every change to update the matching doc, CHANGELOG, DECISIONS when relevant, and STATUS.
- **Why:** the user's request, so work can always be picked up from the documented state.
- **Status:** Accepted.

### #15 — 2026-10-15 — Theme "Clean Professional" and calm-screen rules
- **Decision:**
  - A white background (`#FFFFFF`) with grey neutrals and hairline borders, and no shadows on cards.
  - Indigo `#1F4AA8`, only for the primary action and the selected state; tones only for status. Marigold and the warm surface are dropped.
  - Inter for all text, with the system Devanagari fallback for Hindi.
  - Calm-screen rules: one primary action per screen, at most 3 blocks on Home, progressive disclosure, and "More details" for optional fields.
- **Why:** the user asked for ease of use, professionalism, near-white and uncluttered screens.
- **Status:** Accepted. Supersedes #9.

### #16 — 2026-10-15 — Custom domains + payments are a v1 feature: ₹499 buy, ₹299/yr renew
- **Decision:**
  - The domain purchase, renewal and connect flows ship in v1, and the `customDomains` and `payments` flags are on.
  - Prices are server-authoritative, from `catalog.pricing`, written to `app_config.pricing`.
  - v1 renews manually after reminders; there's no auto-charge.
  - Registration failure after payment triggers an automatic full refund.
- **Why:** the user asked for it as a working feature.
- **Alternatives considered:** keep it for later (#12).
- **Status:** Superseded by #20 and #21. The ₹499/₹299 prices apply to the service, not the domain. Domains remain a v1 feature. This decision superseded #12.

### #17 — 2026-10-15 — Payments through Razorpay, behind `PaymentProvider`
- **Decision:**
  - The Razorpay Android Checkout SDK handles payment.
  - Orders are created and signatures verified **only in Edge Functions**, with an idempotent webhook as backup.
  - The Registrar, HostingAttach and PaymentProvider interfaces keep vendors swappable.
- **Why:** UPI, cards and netbanking for Indian users. It's also secure: the price and payment verification never depend on the client.
- **Alternatives considered:** Google Play Billing, which may be required by Play policy and can replace Razorpay behind `PaymentProvider`; Stripe, which has weaker UPI support in India.
- **Status:** Accepted, pending the Play policy check.

### #18 — 2026-10-15 — 4-tab bottom bar; Dues inside Customers
- **Decision:** Home · Enquiries · Customers · Website, with Dues as a segment of the Customers tab. Home also links to dues.
- **Why:** a less cluttered navigation, per the calm-screen rules, and dues are about customers anyway.
- **Alternatives considered:** 5 tabs with Dues as its own tab.
- **Status:** Accepted.

### #19 — 2026-10-15 — 3 spacing values, 4 type sizes at a 1.67 ratio, defined fonts
- **Decision:**
  - **Spacing** is limited to `s` 8, `m` 16 and `l` 24 dp.
  - **Type sizes:** 14, 23, 39 and 65 sp. That's 14 × 1.67ⁿ, rounded. 65 is web-only, so the app uses 3 sizes.
  - **Hierarchy** within a size comes from weight (400/500/600) and colour only.
  - **Fonts:** Inter for Latin and Noto Sans Devanagari for Hindi, bundled in the app.
  - All Material 3 type slots map onto the 4 sizes.
  - Enforced by `verifyShared`.
- **Why:** the user asked for 3 standard spacings, a defined font, and at most 4 sizes each 1.67× the previous.
  - A base of 14 is the smallest that keeps every size readable (a base of 10 would fail accessibility), with headings that stay proportionate on a phone.
  - Bundling Noto Sans Devanagari gives Hindi the same look on every device.
- **Alternatives considered:**
  - A base of 16 (16/27/45), which makes the list-heavy screens too sparse.
  - A base of 12 (12/20/33/56), which makes body text 20 and too large.
  - Using the system Devanagari font, which looks different across OEMs.
- **Status:** Accepted. It refines #15 (Inter and the system fallback become Inter plus bundled Noto Sans Devanagari).

### #20 — 2026-10-15 — Service plan: 14-day trial, ₹499 first year, ₹299/yr renewal; grace then read-only
- **Decision:**
  - **Trial:** 14 days of full access with no card.
  - **Price:** ₹499 for the first year, then ₹299 per year, incl. GST. Renewal is manual, and early renewal extends from the current end date.
  - **After the period ends:** 15 days of grace with full access, then `lapsed`. When lapsed, the public site is offline and the app is read-only (view, export and renew still work). Data is kept.
  - **Enforcement** is on the server through `entitlement()` and RLS `can_write()`; the app's `FeatureGate` only reflects it.
  - All values are in `catalog.pricing.service`.
- **Why:** this is the user's clarification of the ₹499/₹299 prices and their choices on trial and lapse.
  - A trial lets agents see the value before paying.
  - Read-only, rather than deleting, protects the agent's data and makes renewing easy.
- **Alternatives considered:** a free CRM with a paid website; pay before use; on lapse, taking only the site offline or locking everything.
- **Status:** Accepted. Supersedes the pricing part of #16.

### #21 — 2026-10-15 — Custom domain price = live registrar price + fixed service fee
- **Decision:**
  - Domain prices are fetched live from the registrar and stored as 15-minute `domain_quotes`, with a fixed `serviceFeeInr` (default ₹99, configurable) added to each purchase and renewal.
  - A paid plan is required.
  - Connecting your own domain is free.
  - Orders accept only a server-issued `quote_id`.
- **Why:** the user said a domain is an additional cost priced by the registrar, and chose a fixed fee on top.
- **Alternatives considered:** registrar price with no margin; a percentage margin.
- **Status:** Accepted. The ₹99 amount needs confirming.

### #22 — 2026-10-15 — Kotlin Multiplatform + Compose Multiplatform, Android first
- **Decision:**
  - **Structure:** the app is a KMP project in `mobile/`.
    - `composeApp` holds the shared UI, ViewModels, data, domain and design system in `commonMain`.
    - `androidApp` is the Android application.
    - `iosApp` is added in a later phase.
  - **Platform APIs** (background work, notifications, payments, pickers, file export, PDF, WebView, locale) sit behind `core/platform` interfaces, rather than scattered `expect`/`actual`.
  - **Strings, fonts and icons** use Compose MP resources, and Hindi completeness is enforced by `verifyShared`.
  - **Libraries** must support KMP: supabase-kt, Ktor, Room KMP, DataStore, Vico multiplatform, Coil 3, and the multiplatform Navigation and ViewModel libraries.
- **Why:** the user wants Kotlin Multiplatform. Android-first gets v1 out sooner, and iOS needs no rewrite later.
- **Alternatives considered:** shipping Android and iOS together at launch (needs a Mac, and Apple payment rules up front); staying Android-only.
- **Status:** Accepted. Supersedes the "single `:app` module" part of #6; manual DI stays.

### #14 — 2026-10-15 — No WhatsApp, OTP or FCM in v1
- **Decision:** leave these out of v1. Use realtime plus WorkManager polling for enquiry alerts.
- **Why:** the user didn't choose WhatsApp; OTP and FCM add setup and cost.
- **Status:** Accepted. Can be revisited.

### #23 — 2026-10-15 — `catalog_sync` migration is generated only once schema migrations exist
- **Decision:** `generateShared` writes `supabase/migrations/<timestamp>_catalog_sync.sql` only when `supabase/migrations/` already holds at least one schema migration (any migration that isn't a `catalog_sync`). Until then it logs a skip, and `verifyShared` skips the catalog-hash check.
- **Why:** the CHECK constraints and the `app_config` upsert target tables that are only created in Phase 3. A `catalog_sync` generated in Phase 1 would sort before the table migrations and fail when applied. With this rule, the first `catalog_sync` always comes after the tables. (User's choice.)
- **Alternatives considered:** generate it in Phase 1 as originally written, then delete and regenerate it in Phase 3 before anything is applied.
- **Status:** Accepted.

### #24 — 2026-10-15 — Build with an orchestrator and parallel sub-agents
- **Decision:**
  - The main session acts as the **orchestrator**. It splits each phase into independent work packages and runs them in parallel as sub-agents.
  - **Model choice:** Opus for complex work (codegen, Gradle/KMP wiring, SQL/RLS, payment flows), Sonnet for well-specified work (copying values from the docs into JSON, assets, verification passes).
  - **Doc ownership:** each agent updates its own topic doc. Only the orchestrator edits `CHANGELOG.md`, `DECISIONS.md` and `STATUS.md`, so they don't conflict. Agents report anything ambiguous to the orchestrator, which asks the user.
  - **Resource rule:** only one agent runs Gradle at a time, because the machine has about 7 GB of RAM.
  - A **verifier** agent checks each phase against the docs and TESTING.md before it's ticked.
- **Why:** user request ("deploy multiple agents … sonnet or opus depending upon the complexity … have an orchestrator").
- **Status:** Accepted.

### #25 — 2026-10-15 — Shared definitions: icon source, option format, catalog extras
- **Decision:**
  - **Icons:** Material Symbols Rounded (weight 400, unfilled) SVGs, pinned to `@material-symbols/svg-400@0.48.0`, converted to vector drawables (24dp, viewport 960, `translateY=960`). Only the icons in use are imported. `emoji_events` isn't in that set, so the achievements section uses `trophy`.
  - **Schema options:** select options are objects (`{key, labelKey}` or `{key, icon, labelKey}`), and `optionsFrom` points at a catalog list. Highlights use a fixed set of 8 icons, and `languagesSpoken` offers 12 Indian languages plus English.
  - **Catalog extras:**
    - each shared JSON file has a top-level `version: 1`
    - `planSuggestions` is a string array
    - `minDays: null` means −∞
    - lists with no status meaning use tone `neutral`
    - features carry `labelKey`, `icon` and `default`
  - Values the docs didn't specify are marked "(provisional)" in SHARED_DEFINITIONS.md.
- **Why:** needed to complete Phase 1. The user accepted the provisional values for now and will review them before release.
- **Status:** Accepted (provisional values pending the user's review).

### #26 — 2026-10-15 — Web renderer implementation choices
- **Decision:**
  - **Shared parts:** templates share the section partials and `base.css`. Each template adds only a small `render.js` wrapper and a layout-only `style.css`.
  - **No dependencies:** no framework, SDK or npm dependencies. The renderer uses native ES modules, and the build and test tools are small Node scripts.
  - **Fonts** come from Google Fonts with `display=swap`.
  - **Mock backend** for local development, with three gates: it's only in `WEB_DEV=1` builds, and it only runs on localhost with `?mock=1`.
  - **Images:** the hero photo loads eagerly, because it's the largest element on first view (LCP); all other media is lazy-loaded.
  - **Pinned sections** (hero, contact) ignore `content.hidden`.
  - **The site is light-only;** DESIGN_SYSTEM.md doesn't define a dark website theme.
  - **The RPC contract** (argument names and the rate-limit signal) is kept in `web/api.js`, so it can be aligned with the SQL in one place.
- **Why:** meets WEB_RENDERER.md's performance budget and keeps the templates pure, so they can run in the in-app preview and later on the server.
- **Status:** Accepted.

### #27 — 2026-10-15 — Soft-deleted rows stay visible to their owner under RLS
- **Decision:** select policies are only `agent_id = (select auth.uid())`. Clients filter out rows where `deleted_at` is set (Room queries and the sync code).
- **Why:**
  - Postgres checks the result of an UPDATE against the table's SELECT policies. A select policy that hides soft-deleted rows makes every soft delete and every Undo fail with "new row violates row-level security policy" (verified locally).
  - Sync also needs to receive the deleted rows (tombstones).
- **Alternatives considered:** the original BACKEND.md wording, plus a separate RPC to fetch tombstones.
- **Status:** Accepted. Supersedes the "rows with `deleted_at` set are excluded from select" line in BACKEND.md's conventions.

### #28 — 2026-10-15 — `catalog_sync` emits named SQL functions as well as CHECK constraints
- **Decision:** the generated migration emits:
  - `public.catalog_interest_keys()`, `public.catalog_premium_mode_months(p_mode)` and `public.site_schema()`;
  - CHECK constraints named `<table>_<column>_check`;
  - an upsert of the `flags` and `pricing` rows in `app_config`.
- **Why:** the RPCs need catalog and schema data, and definitions must never be hard-coded in SQL.
- **Status:** Accepted. The contract is in BACKEND.md.

### #29 — 2026-10-15 — Database access is granted explicitly
- **Decision:**
  - Migrations revoke Supabase's default grants to `anon` and `authenticated` in `public`.
  - Internal helpers live in an unexposed `private` schema.
  - Each RPC is granted to specific roles.
- **Why:** this enforces "anon never touches tables directly".
- **Status:** Accepted.

### #30 — 2026-10-15 — Tax records survive account deletion; media is deleted first
- **Decision:**
  - `orders.agent_id` and `payments.agent_id` are nullable with `on delete set null`, and `payments.raw` is cleared when the agent is deleted.
  - `delete_my_account` refuses (`media_not_deleted`) until the app has removed `site-media/<uid>/` through the Storage API.
- **Why:**
  - Payment records must be kept for tax purposes.
  - SQL can't delete Storage files, so deleting only the database rows would leave the files orphaned and publicly reachable.
- **Alternatives considered:** an Edge Function that deletes the media and then the account.
- **Status:** The tax-record part is accepted. The media part is superseded by #32.

### #31 — 2026-10-15 — Premium due dates follow calendar months from `start_date`; Undo is latest-only
- **Decision:**
  - Due dates step forward by calendar months from `start_date`. When the day doesn't exist in a month, it moves to the last day of that month.
  - Only the latest live payment can be undone, and Undo soft-deletes it.
- **Why:** this avoids drift such as 31 Jan → 28 Feb → 28 Mar. Undo for older payments isn't specified.
- **Status:** Accepted. The user confirmed latest-only Undo on 2026-10-15.

### #32 — 2026-10-15 — Account deletion goes through an Edge Function
- **Decision:** a server-side Edge Function, built in Phase 13, deletes the agent's `site-media/<uid>/` files and then calls `delete_my_account` with the service role. Until it exists, `delete_my_account` keeps refusing with `media_not_deleted` while any media remains.
- **Why:** the user's choice. Deleting in one server-side step can't leave orphaned files behind if the app is closed halfway through.
- **Alternatives considered:** the app deletes the media through the Storage API first (#30).
- **Status:** Accepted. Supersedes the media part of #30.

### #33 — 2026-10-15 — Android SDK levels: compileSdk 37, targetSdk 36, minSdk 26
- **Decision:** the app compiles against API 37, targets API 36, and keeps minSdk 26. The SDK has `platforms;android-36` and `android-37.0`, and `build-tools;36.0.0` (AGP's default) is kept.
- **Why:**
  - Current stable Compose Multiplatform 1.12.1 and AndroidX (activity 1.13, core 1.18, navigationevent 1.0) refuse to build with a compileSdk below 36–37.
  - Google Play has required targetSdk 36 for new apps and updates since August 2026.
  - Targeting 36 enforces edge-to-edge and predictive back, which Compose supports. (User's choice.)
- **Alternatives considered:** compileSdk 37 with targetSdk 35 (can't be published on Play); keeping 35 and rolling the libraries back to mid-2025 releases.
- **Status:** Accepted. Supersedes "compile and target SDK 35" in ARCHITECTURE.md.

### #34 — 2026-10-15 — Mobile build setup
- **Decision:**
  - **Versions:** Gradle 9.7.1, AGP 9.3.3, Kotlin 2.4.20, Compose Multiplatform 1.12.1. All pinned in `mobile/gradle/libs.versions.toml`, with the full list in ARCHITECTURE.md.
  - **Plugins:** `composeApp` uses AGP's `com.android.kotlin.multiplatform.library` plugin, because AGP 9 dropped the old library plugin for KMP. `androidApp` uses AGP's built-in Kotlin support.
  - **iOS targets:** `iosArm64` and `iosSimulatorArm64` only. `iosX64` is dropped because Coil, Vico and qrose don't publish it.
  - **Generated resources:** they replace the default `commonMain` Compose resources directory. That directory is a copy of the hand-written resources plus the generated `strings_catalog.xml`. The `Res` accessors are public, in `com.sledje.agentpro.resources`.
  - **Writing into the repo:** only an explicit `./gradlew generateShared` writes source-tree files (the `catalog_sync` migration and the catalog keys lockfile at `mobile/build-logic/catalog-keys.lock.json`). The steps that run automatically before compilation only produce code and resources inside `build/`. `verifyShared` fails if the migration is stale.
  - **Memory:** one Gradle daemon at `-Xmx2g`, the Kotlin compiler in-process, 2 workers, and `android.builder.sdkDownload=false`, so AGP can never install SDK packages by itself.
- **Why:** current stable, mutually compatible versions. Ordinary builds never modify `supabase/` or the lockfile behind the developer's back. The machine has about 7 GB of RAM.
- **Status:** Accepted. Refines SHARED_DEFINITIONS.md, which said all of `generateShared` runs before compilation.

### #35 — 2026-10-15 — Feature flag defaults and overrides live in separate rows
- **Decision:**
  - `catalog_sync` writes the catalog defaults to `app_config` key `flags`, and overwrites them on every catalog change.
  - Manual server-side overrides go in a separate key, `flag_overrides`, which migrations never touch.
  - The effective flags are `flags` merged with `flag_overrides` (an override wins), computed the same way by the app's `FeatureFlags`, the SQL and the Edge Functions.
- **Why:** the docs had both catalog_sync and the remote overrides writing the same `flags` row, so every catalog migration would wipe out an override. (User's choice.)
- **Alternatives considered:** merging so existing values are kept, which means changed defaults never reach the server; resetting overrides on every catalog migration.
- **Status:** Accepted and implemented on 2026-10-15 as `public.effective_flags()`. Only boolean override values count, so a malformed override can't turn on a feature that's off by default.

### #36 — 2026-10-15 — The catalog drives reserved slugs, locales and the site CHECKs; deprecated interests stay publishable
- **Decision:**
  - `reservedSlugs` (a string array) and `locales` (a catalog list) live in `catalog.json`.
  - `catalog_sync` emits `catalog_reserved_slugs()` and `catalog_interest_keys_all()`, plus CHECKs on `sites.template_key`, `sites.accent`, `sites.locale` and `profiles.preferred_language`.
  - The catalog hash covers `templates/index.json`.
  - `submit_enquiry` accepts only active interests, while `publish_site` also accepts deprecated ones.
  - The Kotlin enum is `AppLocale`, to avoid clashing with `java.util.Locale`.
- **Why:**
  - Definitions live only in `shared/` (CLAUDE.md).
  - Site content saved before an interest was deprecated must still publish.
- **Status:** Accepted. The `reservedSlugs` values are provisional.

### #37 — 2026-10-15 — SQL tests run only against the generated `catalog_sync`
- **Decision:** retire the Python fixture generator. `supabase/tests/run.sh` requires the generated migration and exposes it to the tests as `:catalog_sync`, so a test can re-apply it.
- **Why:** there's one implementation of the contract, so it can't drift. The test that overrides survive a migration also runs against the real SQL.
- **Status:** Accepted.

### #38 — 2026-10-15 — Noto Sans Devanagari is bundled as the Google Fonts variable font
- **Decision:** bundle one variable TTF from google/fonts (commit `2bc5d431`), instanced at 400/500/600 with `FontVariation`. Inter 4.1 uses static TTFs at 400/500/600; there's no 700, because the app never uses `webHero`.
- **Why:** the notofonts static TTFs have no Latin glyphs, but DESIGN_SYSTEM.md relies on the Hindi font also covering Latin, digits and ₹. The variable file is also small (642 KB).
- **Alternatives considered:** the notofonts static files, which would leave Latin to the system font; generating static instances with fontTools.
- **Status:** Accepted by the user on 2026-10-15.

### #39 — 2026-10-15 — Android host tests run on Robolectric
- **Decision:** the Compose UI tests and Roborazzi screenshot tests run in `composeApp/src/androidHostTest` on Robolectric (SDK 35, native graphics). The baselines are committed in `src/androidHostTest/screenshots`. The test-only libraries are Robolectric 4.17, JUnit 4.13.2 and androidx ui-test-junit4/manifest 1.12.1.
- **Why:** both need an Android runtime on the JVM, and `commonTest` can't carry `@RunWith`.
- **Status:** Accepted by the user on 2026-10-15.

### #40 — 2026-10-15 — `LocalAppLocale` is the one expect/actual; reduce-motion uses `MotionDurationScale`
- **Decision:** `LocalAppLocale` in `core/designsystem` is the only expect/actual declaration, because the locale is needed inside composition. "Remove animations" is read through the platform's `MotionDurationScale` rather than a new seam.
- **Why:** a minimal exception to ARCHITECTURE.md's rule of "interfaces over expect/actual".
- **Status:** Accepted.

### #41 — 2026-10-15 — Component behaviour the docs left open
- **Decision:**
  - **UrgencyChip:** "Due today" at 0 days, "Due in Nd" up to 7 days, then "Due 15 Jan".
  - **`Money.compact`:** truncates rather than rounds (₹1,25,000 → ₹1.2L), shows the full amount below 1 lakh, and keeps L/Cr in Latin script in Hindi.
  - **FilterChipRow:** single-choice, with "All" first and counts written as "New · 3".
  - **Fields:** labels above the field, a counter only on multiline fields that have a maxLength, and no asterisk on required fields.
  - **Icons:** pill icons are 18dp.
  - **ChartCard titles** use the `strong` style (14/600).
  - **Surfaces:** dialogs, menus and the date picker are white in light mode and `surfaceContainerHigh` in dark mode. The snackbar uses inverse colours.
  - **FormRenderer** shows every required field, then the first optional fields up to 6 in total; screens can override this with `visibleKeys`.
  - The Hindi UI copy is provisional.
- **Why:** needed to build the components. Each choice follows the calm-screen rules.
- **Status:** Accepted by the user on 2026-10-15 for now. It will be reviewed before release, along with the provisional Hindi copy.

### #42 — 2026-10-16 — Production backend is the user's `AGENT_PRO`; the advisor SECURITY DEFINER warnings are accepted
- **Decision:**
  - **Project:** the backend runs on the user's Supabase project `AGENT_PRO` (org letssee, ap-south-1, free plan).
  - **Migrations the connector refuses** as destructive (`rpcs`) are run by the user in the Dashboard SQL Editor.
  - **Advisor warnings 0028/0029** on the 4 public and 7 agent RPCs are accepted by design and documented in BACKEND.md. Any other advisor finding is fixed with a migration, as was done for `rls_auto_enable`.
- **Why:**
  - The user created `AGENT_PRO` in Mumbai, which matches DECISIONS #1's region.
  - The connector can't show its confirmation prompt in this session.
  - CLAUDE.md requires that public access goes only through SECURITY DEFINER RPCs.
- **Alternatives considered:** the Supabase CLI with the user's database password; the Tokyo project (rejected because of latency).
- **Status:** Accepted.

### #43 — 2026-10-16 — App gate, feature contract and sync details (Phase 5)
- **Decision:**
  - **Gate:** auth, onboarding and main each have their own NavHost, chosen by a pure `resolveGate()`. Onboarding stays in place until its step 3 finishes. A cached user opens Main even when offline.
  - **Feature contract:**
    - `AppFeature.flag: FeatureId?`, where null means a core feature (Home, Settings) with no catalog flag.
    - Features contribute `TabSegment`s; Dues is a segment of Customers.
    - `routes(host: FeatureHost)` replaces `routes(nav)`.
  - **Sync:**
    - Keyset pagination on `(updated_at, id)`. The sync position is saved once the whole refresh succeeds.
    - The Room cache is rebuilt from scratch on a schema change, and the sync positions reset.
    - The cache is cleared on sign-out or when the user changes.
  - **Room:** its generated `AppDatabaseConstructor` is a second expect/actual (it refines #40).
  - **Forms:** `FieldType.Password` and `FieldSpec.minLength` are additions for app forms only; the site schema can't use them.
  - **Errors:** an RLS refusal (`42501`) maps to `ReadOnly`.
  - **Onboarding:** the starter site content uses the agent's name, designation and phone, so "Publish now" works. "Later" saves an unpublished draft.
  - **Profile limits:** name 80, designation 60, city 60, agency code 30, branch 60 characters.
- **Why:** these were needed to build Phase 5. The user accepted them as provisional on 2026-10-16.
- **Status:** Accepted, provisional until the review before release.

### #44 — 2026-10-16 — The session is encrypted at rest with the Android Keystore
- **Decision:** add a `SecureSettings` seam in `core/platform`, as ARCHITECTURE.md planned.
  - **Android:** the supabase-kt session is encrypted with AES-GCM, using a key held in the Android Keystore that never leaves it. The ciphertext is stored in app-private storage.
  - **iOS:** the Keychain, in the iOS phase.
  - **If the key or ciphertext is unreadable,** the app signs out cleanly rather than crashing.
- **Why:** the user's choice. It protects the login session on rooted or compromised devices.
- **Alternatives considered:** supabase-kt's default app-private storage (sandboxed and excluded from backup); `androidx.security-crypto`, which is deprecated.
- **Status:** Accepted; being implemented in the Phase 5 follow-up.

### #45 — 2026-10-16 — Language switch uses AppCompat's per-app locales on Android
- **Decision:** add `androidx.appcompat` (Android only). On Android, `LocaleController` calls `AppCompatDelegate.setApplicationLocales`, so the choice appears in Android 13+'s per-app language settings. The common `LocalAppLocale` override stays for previews, tests and iOS.
- **Why:** the user's choice, and it matches ARCHITECTURE.md's platform-seam table.
- **Status:** Accepted; being implemented in the Phase 5 follow-up.

### #46 — 2026-10-16 — Password reset ends on a "Set new password" screen
- **Decision:** a recovery link (`agentpro://auth-callback`) opens the Set new password screen (UX.md). An expired link offers "Send a new link".
- **Why:** without it, a reset link signs the agent in but never lets them choose a new password. (User's choice.)
- **Status:** Accepted; being implemented in the Phase 5 follow-up.

### #47 — 2026-10-16 — Multi-company platform: one company per agent, admin-managed offers, offer slots, company logos
- **Decision:**
  - **Companies:** Agent PRO serves agents of many insurance companies. Each agent belongs to **exactly one** company.
  - **Admin:** companies and their ongoing **offers** are managed by admins in a **separate web admin panel**.
  - **Placement on the agent's site:** a featured-offer banner in the hero, plus an Offers section the agent can place anywhere. On the on-device template preview, the agent taps a slot and picks from their company's live offers; expired offers hide automatically.
  - **Custom offers:** agents can add their own, which show a "Details provided by the agent" note (provisional).
  - **Logos:** company logos are allowed as admin-uploaded content, only with the company's permission. They're never bundled in templates or app assets.
- **Why:** the user's requirement and answers on 2026-10-16.
- **Alternatives considered:** several companies per agent; admin screens inside the app; editing in the Supabase dashboard only; placing offers between any sections; names only with no logos.
- **Status:** Accepted. The detailed design goes in `docs/OFFERS.md`. This supersedes the "no LIC logo" constraint in PRODUCT.md and CLAUDE.md, which now allow permitted, admin-uploaded logos.

### #48 — 2026-10-16 — DPDP compliance is a product-wide requirement
- **Decision:** every part of the product (app, agent websites and the enquiry form, backend, admin panel, processors) follows India's DPDP Act 2023 and the DPDP Rules. The design lives in `docs/PRIVACY.md`, and CLAUDE.md's engineering rules include it.
- **Why:** user request. The product holds personal data about agents, about the public who submit enquiries, and about the agents' customers (policies, nominees).
- **Status:** Accepted. The design is pending (the `product-design` agent), and counsel must review it before launch.

### #49 — 2026-10-16 — Companies and offers are database data; their shape lives in `shared/`
- **Decision:**
  - `companies`, `offers`, `admins` and `audit_log` are tables.
  - Their shape and enums live in `shared/`: `offer-schema.json`, offer statuses and phases, button types, and admin roles.
  - Company names are stored per language in the table.
- **Why:** they're runtime data managed by admins, the same pattern as interests (in the catalog) and enquiries (in a table). See OFFERS.md.
- **Status:** Accepted (design).

### #50 — 2026-10-16 — Admin access is an `admins` table with MFA; admin tables use admin-only RLS
- **Decision:**
  - `is_admin()` checks the `admins` allow-list and requires an MFA-verified session (`aal2`).
  - Admin tables use admin-only RLS, an exception to owner-only.
  - Admins are invite-only and get no agent profile or site.
- **Why:** revoking an admin takes effect immediately and can be audited, with no Auth hook needed.
- **Status:** Accepted (design).

### #51 — 2026-10-16 — Offer storage and resolution
- **Decision:**
  - Custom offers are stored in the site content.
  - Company offers are resolved when the public page loads.
  - An ended offer is a warning at publish time, not an error.
  - Tap-to-place uses `addWebMessageListener` on the preview's existing address, with a strict message parser. The Content list is the accessible way to make the same choices.
- **Status:** Accepted (design).

### #52 — 2026-10-16 — Admin panel: a separate Vite/TypeScript/React app using supabase-js
- **Decision:** the admin panel lives in `admin/` and runs on its own Vercel project. It requires MFA and signs out after 30 idle minutes. #26's no-SDK rule doesn't apply, because that rule is about public page speed.
- **Status:** Accepted (design). Deploying needs the user's approval.

### #53 — 2026-10-16 — User decisions on offers (OFFERS.md)
- **Decision:**
  - **Company membership is verified by an admin.** The agent picks their company (or "My company isn't listed") and uses the app right away; company offers appear only after an admin approves the link. The admin panel gets a membership-requests screen.
  - **Custom offers are on by default.** The agent confirms the details, the offer shows "Details provided by the agent", and the admin can switch custom offers off per company.
  - **Offers need both English and Hindi** before they can go live.
- **Why:** the user's choices on 2026-10-16. The admin-verification option was read as non-blocking, because agents can use the app before approval; the user may tighten this.
- **Status:** Accepted.

### #54 — 2026-10-16 — User decisions on privacy (PRIVACY.md)
- **Decision:**
  - **Lapsed accounts** are deleted 12 months after lapse, with notices at 30 days, 7 days and 48 hours. Tax records are kept for 8 years, anonymised.
  - **Never-converted enquiries are left to the agent.** Nothing is deleted automatically. The agent is the fiduciary for that data, and the app gives them the tools to clear or erase it.
  - **Requests from the public** go to the agent, with a 30-day deadline. The agent verifies the person by phone, and the platform follows up if a request stays unresolved.
  - **Approved for launch:** self-hosted fonts (this supersedes the fonts part of #26) and a transactional email provider.
  - **Backups (Supabase Pro, about $25/month):** not approved yet, but marked **high priority** in the next steps.
  - **Other parts of PRIVACY.md, accepted as the design:**
    - consent records with versioned, hashed notices;
    - erasure blanks the data first, then deletes the rows after 30 days;
    - `payments.raw` is minimised when it's written (refines #30);
    - the device's Room cache stays unencrypted, relying on the app sandbox and Android's own file encryption, as an accepted risk.
- **Status:** Accepted. The grievance officer's details are still needed, and counsel should review before launch.

### #55 — 2026-10-16 — Sonnet by default for sub-agents; Opus only where needed
- **Decision:** refines #24. The default sub-agent model is **Sonnet**. Use **Opus** only for:
  - codegen and build-logic design;
  - SQL, RLS and security-critical migrations;
  - payment, entitlement and webhook logic;
  - auth and crypto;
  - hard cross-cutting debugging.
  
  **Sonnet** handles everything else: UI screens and ViewModels built on existing patterns, web templates, docs, verification passes, wiring, and test writing. Agents that are already running finish on their current model, because restarting them would cost more.
- **Why:** the user said running everything on Opus was raising costs.
- **Status:** Accepted.

### #56 — 2026-10-16 — Phase 8 implementation choices (provisional)
- **Decision:**
  - **Preview:** `SitePreview` serves the bundled renderer from `https://appassets.androidplatform.net` through `shouldInterceptRequest`, because `file://` can't load ES modules or root-relative URLs. It uses no androidx.webkit dependency, and build-logic generates `tokens.css` for it.
  - **Photos:** an automatic centred crop to the schema's aspect ratio, WebP at 1024 px or less, with the quality stepped down until the file fits in 2 MB. Each upload gets a unique object name.
  - **Saving:** show/hide saves immediately; reorder saves when the agent taps Done; pinned sections stay first and last. Appearance changes save immediately and go live, because the public site reads those columns directly.
  - **Libraries and code layout:** qrose is the QR library. `ui-backhandler` makes system Back ask before discarding changes. Website strings live in `strings_website.xml`.
- **Status:** Accepted for now. The user still needs to confirm the items listed in STATUS.md.

### #57 — 2026-10-16 — Phase 5 follow-up implementation details
- **Decision:**
  - **Reset redirect:** it carries `?type=recovery`, so even an expired reset link is recognised. Supabase Auth's redirect allow-list therefore needs **`agentpro://auth-callback**`** (with the wildcard).
  - **Session manager:** `SecureSessionManager` replaces supabase-kt's `SessionManager`. Both "nothing stored" and "unreadable" throw `NoSessionFoundException`, because supabase-kt 3.8 requires a non-null result.
  - **Testing the cipher:** the cipher sits behind a `SecretCipher` interface, so the store logic is tested on Robolectric with software AES-GCM. The real Keystore cipher needs a device test.
  - **Locale timing:** on Android, the language choice is read by `sync()` after `MainActivity.super.onCreate`. `LocalAppLocale` is still applied.
  - **Locales list:** `locales_config.xml` is hand-written. `LocalesConfigTest` fails the build if it drifts from `catalog.locales`, a tested exception to "definitions live only in `shared/`". Generating it is the alternative.
  - **Back navigation:** system back on the root of the reset flow closes the app; the top-bar back arrow closes the flow.
  - **Old data:** an earlier plaintext session or language choice isn't migrated, because the app hasn't shipped.
- **Why:** constraints of the supabase-kt 3.8 and AppCompat 1.8 APIs, and Robolectric's limits.
- **Status:** Accepted, provisional until the live test. Refines #44–#46.

### #58 — 2026-10-17 — CRM implementation choices (Phases 6 and 7)
- **Decision:**
  - **Contacts:** contact import uses the system contact picker (`ACTION_PICK` on Phone), so there's no `READ_CONTACTS` permission and only one contact per tap (data minimisation). Numbers are normalised to +91.
  - **Phone, SMS and email actions** use `ACTION_DIAL`/`ACTION_SENDTO`, so there's no `CALL_PHONE` permission.
  - **Adding an enquiry by hand** offers the manual sources only (not `website`).
  - **Notes** save automatically after 800 ms.
  - **Search** matches phone digits only once at least 3 digits are typed.
  - **Dues:** active policies only, up to 90 days ahead, in the 4 catalog `dueBuckets`.
  - **Tab badges:** the Customers badge counts customers with an overdue premium; the Enquiries badge counts unseen enquiries.
  - **Converting to a customer** reuses the already-created customer on a retry, so there are no duplicates.
  - **Deleting an enquiry** soft-deletes it, with Undo.
  - **Website Stats** counts enquiries from the Enquiries cache by Asia/Kolkata day.
- **Why:** needed to build the phases; each choice follows the calm-screen and DPDP rules.
- **Status:** Accepted, provisional until the user's review.

### #59 — 2026-10-17 — Host the public renderer on Cloudflare (Workers static assets + Cloudflare for SaaS)
- **Decision:**
  - **Hosting:** the renderer (`web/dist`) is served as **Cloudflare Workers static assets**. Every path falls back to `index.html` (single-page-application handling), with security headers. Free links live under the platform domain (to be bought by the user), e.g. `<domain>/a/<slug>`.
  - **Agents' own domains:** use **Cloudflare for SaaS custom hostnames** on the platform's zone. The agent adds a CNAME to our fallback origin, and Cloudflare issues and renews the certificate. The first 100 hostnames are free, then $0.10 each per month (pricing checked 2026-10-17).
  - **Domain registration:** stays with a separate registrar API (the open question in STATUS).
  - **Preview:** a free `*.workers.dev` preview with demo data only can go up before P1. Public launch waits for the P1 privacy items, and every deploy needs the user's approval.
- **Why:**
  - Static requests are free and unlimited, whereas Vercel's Hobby plan forbids commercial use and Pro costs $20 a seat per month.
  - Cloudflare for SaaS is built for multi-tenant custom domains.
  - Cloudflare has edge locations across India.
  - Workers can run the same pure `render()` for server-side rendering later.
- **Alternatives considered:** Vercel Pro (#4); Netlify.
- **Status:** Accepted (the user's choice, 2026-10-17). Supersedes #4. Changes the domain-attach part of #17 and #21 (the `HostingAttach` adapter is now Cloudflare Custom Hostnames).

### #60 — 2026-10-17 — Auth email capacity: at least 100/hour once the platform domain exists
- **Decision:**
  - The requirement is for sign-up and password-reset emails to allow **at least 100 per hour**. Supabase's built-in sender allows about 2 per hour.
  - The user chose to **wait for the platform domain**, then set up a transactional provider (Brevo or Amazon SES) properly, with SPF/DKIM/DMARC on that domain. Once custom SMTP is connected, Supabase Auth → Rate Limits → emails goes to 100 or more per hour.
  - No interim Gmail SMTP.
  - Until then, test sign-ups are limited to about 2 emails per hour.
- **Alternatives considered:** Gmail SMTP with an app password as a stopgap; Resend (its free tier is only 100 per day); Amazon SES straight away.
- **Status:** Accepted. Blocked on buying the platform domain.

### #61 — 2026-10-17 — Privacy notice hashing and the release gate
- **Decision:**
  - **The notice hash** is sha256 over `JSON.stringify({key, locale, text, version})`, where `text` is the NFC-normalised `textKeys` values joined by `\n`, with placeholders left unfilled.
  - **Locked hashes** live in `catalog-keys.lock.json`, and changing a notice's text requires a version bump.
  - **The web** reads the committed generated file `shared/generated/privacy-notices.json`.
  - **The server** validates `{notice_key, version, hash}` against `catalog_privacy_notice*()`, accepts only the current version, and resolves the language from the hash.
  - **Release gate:** upper-case `{TOKEN}` placeholders block release builds through `verifyReleaseReady`, which isn't part of `check`.
- **Why:** this proves exactly which text each person saw, in both languages, without duplicating the hash logic in JS or SQL, and keeps debug builds green while the grievance officer is undecided.
- **Alternatives considered:** hashing at runtime on the web; generating the JSON at web build time; failing `check` on placeholders.
- **Status:** Accepted, provisional. The notice texts need user and counsel review.

### #62 — 2026-10-17 — Web privacy surfaces and the production build gate
- **Decision:**
  - **Production web builds** must use `RELEASE=1`, which runs `scripts/release-check.mjs` and fails while there are release blockers or i18n placeholders. Preview builds only warn.
  - **The per-site privacy page** is a route in the single-page app (`/a/<slug>/privacy`). Its data-request form has no consent checkbox, per PRIVACY.md.
  - **The platform `/privacy` and `/terms`** ship as shells that reuse the `agent_signup` notice texts until P2-4 provides the final legal text.
- **Status:** Accepted.

### #63 — 2026-10-17 — Privacy backend shapes (P1-2)
- **Decision:**
  - **Consent payload:** `consent` is the notice payload itself (`jsonb`), not a boolean.
  - **Sign-up:** an invalid or missing sign-up consent doesn't block Auth; the app calls `record_consent` instead.
  - **`record_consent`** is idempotent per notice version and customer, refuses `enquiry_form`, and may return `field:'customer_id'`.
  - **Lapsed agents:** privacy RPCs don't require `can_write()`, so agents keep their privacy duties after a lapse.
  - **Payments:** `payments.raw` is minimised by a database trigger for every writer.
  - **`audit_log`** has no API access until the admin tables exist.
  - **Codegen:** the privacy CHECKs are emitted only when the catalog has those lists.
  - **Naming:** the RPC is `update_data_request`, not the planned `resolve_data_request`.
- **Why:** this keeps one PostgREST function per RPC, keeps the hash logic in generated SQL only (#61), and enforces data minimisation whichever writer stores payment data.
- **Status:** Accepted, provisional (counsel review pending).

### #64 — 2026-10-17 — App consent implementation (P1-4)
- **Decision:**
  - **Form field:** consent is a form field type (`FieldType.Consent`), not a hand-built checkbox, so FormRenderer stays the only form builder.
  - **Customer-data sheet:** it appears when Add customer or Add enquiry opens. Acceptance is remembered per account and notice version, and recorded again on a new device.
  - **"Terms"** opens the web `/terms` page, because there's no in-app terms text.
  - **Guardian consent** is recorded after the customer exists on the server, and a retry doesn't create a duplicate.
- **Status:** Accepted, provisional until the user's review.

### #65 — 2026-10-17 — Agent sites installable as web apps ("Add to home screen")
- **Decision:**
  - **Manifest per site from a Worker:** every agent's site gets a web app manifest at `/manifest.webmanifest` (`?s=<slug>` on the free link; by host on a custom domain). A small Worker script (`web/worker.js`) builds it from `get_public_site` / `get_public_site_by_host`. It runs **only** for that path (`assets.run_worker_first`); every other request is still served straight from static assets. An unpublished, unknown or lapsed site gets a 404, so it can't be installed.
  - **Scope and identity:** `id` = `start_url` = `scope` = `/a/<slug>/` (or `/` on a custom domain), so each agent's site is a separate app on the shared origin. The page moves `/a/<slug>` to `/a/<slug>/` with `history.replaceState`, so the page itself is inside the scope. The canonical URL stays without the slash.
  - **Icons:** 192 and 512 **PNGs**, generated at build time by `scripts/gen-icons.mjs`, one per `catalog.accentSwatches` colour: a white head-and-shoulders figure inside the maskable safe zone. No insurer artwork. The agent's photo isn't used yet, because photos are stored as WebP, and every source checked names PNG as the safe format for Android's WebAPK. A follow-up can have the app upload a PNG copy of the photo.
  - **No service worker.** Chrome no longer needs one to install a site (checked 2026-10-17, web.dev install criteria), and a cache could keep showing a lapsed or changed site.
  - **The Worker gets no secrets in config:** it reads `SUPABASE_URL` and the anon key from the deployed `config.js` asset, which is public anyway, so `wrangler.jsonc` holds no keys.
  - **On the page:** a ghost "Add to home screen" button in the contact section, shown on phones (and on desktops once the browser offers a prompt). Agents turn it off with the schema toggle `contact.showInstallButton` (default on), which lives in content, so there's no table change. Tapping it uses Chrome's prompt (`beforeinstallprompt`) when there is one, and otherwise shows the steps (the ⋮ menu, or the iOS Share sheet). The page always takes over `beforeinstallprompt`, so Chrome's mini-infobar never appears. Nothing is stored in the browser.
  - **From the app:** Website tab → ⋮ → **Add to home screen** opens `<site>/?install=1` in the default browser (not a Custom Tab, which can't install). That shows a sheet with **Install** (or the steps) whatever the toggle says, and the visit isn't counted as a view. It's shown only once the site has been published.
- **Why:** a native app can't trigger a browser's install for a website; only the page can. A real manifest gives a proper app icon and a full-screen window, rather than a browser bookmark. Customers keep the agent one tap away.
- **Alternatives considered:**
  - Android pinned shortcuts (`ShortcutManagerCompat.requestPinShortcut`): one tap, but it's a bookmark that opens in the browser, it may carry an Agent PRO badge, and there's no iOS equivalent. Kept as a possible fallback.
  - A client-built manifest (blob URL): Android's WebAPK service can't fetch it.
  - Echoing query parameters into a manifest: anyone could mint an installable app with any name on our origin.
  - The agent's WebP photo as the icon: not confirmed to work with WebAPK.
- **Status:** Accepted (the user chose the installable site plus a customer button that agents can turn off, 2026-10-17). Changes #59: the hosting is no longer "static only", but the Worker runs only for the manifest path.

### #66 — 2026-10-17 — App icon: generated vector, primary colour written into the asset
- **Decision:**
  - **The icon:** the app icon is the "A with a rising chart line" concept the user picked. It is redrawn as geometry in `design/app-icon/build_icon.py`, which generates the Android vector layers and the store PNGs. Nobody edits a raster or the path data by hand.
  - **Colour:** the background colour `#1F4AA8` is written directly into `ic_launcher_background.xml` and the script. No Android colour resource is generated from `shared/design-tokens.json`.
- **Why:**
  - The vector stays sharp at every launcher size.
  - One even-odd outline serves the monochrome themed-icon layer too.
  - The geometry stays editable and is checked against the safe zone.
  - Adding an XML colour generator to the codegen for one static asset isn't worth it. The icon is brand artwork rather than UI code, so the "no hard-coded colours in UI code" rule doesn't apply.
- **Trade-off:** if the `primary` token changes, these two places must be updated by hand. Both carry a comment pointing to the token.
- **Alternatives considered:**
  - shipping the AI-generated PNG as is (it's raster, has no monochrome layer and isn't safe-zone checked);
  - generating a `colors.xml` from tokens.
- **Status:** Accepted.

### #67 — 2026-10-17 — Auth email Resend: client countdown, refusals told apart, Resend everywhere an email is expected
- **Decision:**
  - **Resend everywhere:** "Check your email", "Reset link sent", and Sign in for an unconfirmed email ("Resend confirmation email") all offer Resend, through one `ResendEmailViewModel`.
  - **Countdown:** after each send, Resend is disabled for `AuthRules.EMAIL_RESEND_SECONDS` (60, Supabase's per-address minimum) and shows "Resend in N s".
  - **Two refusals:** `over_email_send_rate_limit` becomes `AppError.EmailLimited(retryAfterSeconds)`. A per-address wait restarts the countdown at the server's N. The hourly quota (null) shows "Try again in about an hour". `RateLimited` stays for other 429s.
- **Why:** the user reported that a confirmation email that was refused, or accepted but never delivered, couldn't be sent again. The auth logs on `AGENT_PRO` showed that no `/resend` or `/recover` request had ever reached Supabase, and two `/signup` calls were refused with the hourly quota. In the old flow:
  - Sign in for an unconfirmed address was a dead end;
  - "Reset link sent" had no Resend;
  - every refusal said "Wait a minute", which is wrong for the hourly quota;
  - Resend right after sign-up always hit the 60-second wait.
- **Trade-off:** the 60 s mirrors a Supabase setting by hand. If Auth → Rate Limits changes it, update `AuthRules`; the server's own "after N seconds" corrects the countdown anyway. The countdown resets if the screen is recreated after process death, and the server then still enforces the wait.
- **Not solved here:** the hourly quota itself. That still needs custom SMTP (DECISIONS #60).
- **Alternatives considered:**
  - parsing nothing and showing one message (keeps the misleading "a minute");
  - storing the last send time per address in `KeyValueStore` (more state for little gain, since the server enforces the wait).
- **Status:** Accepted.

### #68 — 2026-10-17 — Gmail SMTP as the interim Auth email sender
- **Decision:** until the transactional provider in DECISIONS #60 exists, Supabase Auth on `AGENT_PRO` sends through Gmail SMTP: `smtp.gmail.com:465`, a dedicated Gmail account and a Google app password. The password is entered only in the Supabase dashboard and is never in the repo. Auth → Rate Limits → emails is 100 per hour, and the per-address interval stays at 60 s to match `AuthRules.EMAIL_RESEND_SECONDS`.
- **Why:** the built-in sender's limit of about 2 per hour blocked test sign-ups. The user chose Gmail because it needs no domain.
- **Trade-off:**
  - emails come from a `@gmail.com` address, with no SPF/DKIM for our domain;
  - Gmail caps a free account at about 500 a day and may lock it for bulk sending;
  - Google becomes a processor of agent email addresses (PRIVACY.md, Processors).
  - So it's for testing only, not for launch.
- **Supersedes:** nothing. It's a stopgap within DECISIONS #60, which still holds for launch.
- **Status:** Accepted (interim).

### #69 — 2026-10-17 — Branded Auth emails generated from the design tokens
- **Decision:**
  - The five Supabase Auth email templates in use (confirmation, recovery, invite, email change, reauthentication) are generated by `scripts/gen-email-templates.mjs` from `shared/design-tokens.json` into `supabase/email-templates/`. They are pasted into the Supabase dashboard by hand.
  - Each email carries English and Hindi, because Supabase takes one template per email type.
  - The logo is an HTML "A" on a primary square for now.
- **Why:** the user asked for emails in the brand colours and identity. Generating from the tokens keeps the emails from drifting from the app (CLAUDE.md, definitions live in `shared/`). One bilingual email avoids picking a language we can't know at send time.
- **Trade-off:**
  - The email copy lives in the generator, not in `shared/i18n`, because it is server-side and has no app string resource. If it grows, move it into `shared/` and the codegen.
  - Applying is manual; nothing fails if the dashboard copy goes stale.
  - The HTML mark isn't the real icon. To use it, host a PNG (for example on the renderer's domain after deploy) and put an `<img>` with alt text in the wordmark.
- **Alternatives considered:**
  - a per-language template chosen by the user's locale (Supabase doesn't support it without an Auth hook);
  - an inline SVG or base64 logo (stripped or blocked by Gmail and Outlook).
- **Status:** Accepted.

### #70 — 2026-10-17 — Sign-up collects name and mobile, and signs in by itself
- **Decision:**
  - Create account asks for the full name, mobile, email, password and Confirm password (plus the consent box). The name and mobile go in the sign-up metadata (`full_name`, `phone`), which the existing signup trigger already copies into `profiles`, so **no migration** was needed.
  - Onboarding **always opens on step 1**, prefilled. When the name and mobile are there, they show as a card with **Edit** (`FormRenderer.hiddenKeys`), and only designation, city and the More details fields are asked. `AppGate.Onboarding` lost its `profileDone` flag.
  - **Automatic sign-in:** if sign-up returns a session (Confirm email off), the gate moves straight on. Otherwise the repository keeps the email and password **in memory only** and retries the sign-in whenever "Check your email" is resumed, i.e. when the agent comes back from the email app or the browser. The confirmation deep link still signs in by itself. A Sign in refused as unconfirmed keeps them the same way.
  - Create account's switch link reads "Sign in instead".
- **Why:** the user found the sign-up screen too bare, didn't want onboarding to ask for the same details again (but wanted them editable), and wanted to be signed in after signing up. A name and mobile were already required in step 1, and the sign-up notice already lists them, so collecting them earlier adds no new data (PRIVACY.md: same items, same purpose; the notice text and hash are unchanged).
- **Trade-off:**
  - Starting at step 1 means a restarted onboarding no longer resumes at step 2. Step 1 is prefilled, so it costs one tap on Continue.
  - The password stays in process memory until the agent is signed in or the process ends. It is never written to disk, logs or navigation state. Each retry is one Auth sign-in request (the Supabase limit is about 30 per 5 minutes per IP), and it only happens on resume.
  - A confirmed email whose sign-up was in an earlier process still needs a manual sign-in.
- **Alternatives considered:**
  - turning off Confirm email in Supabase, so sign-up always signs in at once. The app already handles it with no change, but unverified emails could then own accounts, and changing it is a dashboard action for the user to decide (STATUS, Open questions);
  - polling the sign-in on a timer (it eats into the Auth rate limit for no benefit);
  - putting the password in the navigation route (it would be saved to instance state).
- **Status:** Accepted.

### #72 — 2026-10-17 — Offers CHECKs arrive with the tables, not before
- **Decision:** the 8b-1 `catalog_sync` emits only what needs no new table: `offer_schema()`, `catalog_locales()`, `catalog_default_locale()`, `catalog_offer_cta_kinds()`, `catalog_offer_cta_kinds_agent()`, `catalog_membership_reject_reasons()` and `catalog_offer_limits()`, plus the new `site_schema()`. The five CHECKs on `offers`, `admins` and `company_memberships` are added to the generator's `CHECKS` by 8b-2, together with the migration that creates those tables, and `catalog_sync` is regenerated then so it sorts after them.
- **Why:** a `catalog_sync` that alters a table that doesn't exist fails to apply, and it would also break the local SQL tests, which apply every migration. P1 hit the same ordering (the privacy CHECKs landed with P1-2's migration).
- **Trade-off:** OFFERS.md's plan put the CHECKs in 8b-1; 8b-2 now has one more step. Meanwhile `20261017085151_catalog_sync.sql` is safe to apply on its own: `validate_fields` skips the unknown `offerRef`/`offerList` types and ignores the new rules, and existing content has no `offers` key.
- **Alternatives considered:** CHECKs guarded by `to_regclass(...)` (they'd silently not exist until the next catalog change); writing the 8b-2 tables in 8b-1 (outside its scope and files).
- **Status:** Accepted.

### #73 — 2026-10-17 — 8b-1 scope details: no OfferSchema.kt, reused icons, offers hidden until built
- **Decision:**
  - **No generated `OfferSchema.kt`.** The app only displays company offers (from `my_company_offers`); it never edits them. The admin panel reads `offer-schema.json` directly and SQL gets `offer_schema()`. Generate it if the app ever needs the fields.
  - **Icons reuse ones already imported** (offers `savings`, statuses `edit`/`check_circle`/`block`, …; SHARED_DEFINITIONS.md, Offers). `local_offer` would suit offers better, but importing it means downloading `@material-symbols/svg-400`, which wasn't in the npm cache; swap it in later with the user's approval.
  - **Until their builders land, the new schema stays invisible:** the app leaves `offers` out of the Content list (`SiteContent.pendingSections`) and renders nothing for `offerRef`/`offerList` fields, but keeps their values unchanged when a section is saved; the web renderer ignores the `offers` key, and `test-web.mjs` expects it only once 8b-3 renders it (`PENDING`).
  - The custom offer's generated `id` field has a label (`field.offer_id`), because every schema field needs a `labelKey`; it's never shown.
- **Why:** keep 8b-1 within `shared/` and the codegen, keep the test APK free of half-built screens, and never lose a placed offer when an older editor saves a section.
- **Status:** Accepted.

### #71 — 2026-10-17 — Three launch screens after "Publish now"
- **Decision:**
  - Once a site is published from onboarding step 3, three screens run before Home, in one scaffold with a sliding progress bar: **Add it to your phone** (a home-screen tile preview; Add to home screen opens the install sheet in the browser, then moves on; "Not now" skips), **This is what customers see** (the live render in a small phone frame; Open in the browser), and **Share it with customers** (the QR code, Share through other apps, Done).
  - Back moves one screen back; on the first screen it skips ahead. Share records `SITE_SHARED` for the Home setup checklist, as the Website tab does.
  - "Later" (a draft) skips the three screens, because a draft has nothing to add or share.
- **Why:** the user asked for a smooth three-step hand-off once the site exists, so the agent sees the site, installs it, and shares it straight away.
- **Trade-off:**
  - The gate stays on onboarding until Done, so a failure while the screens run can't be recovered in place. Each screen only navigates and opens other apps, so there's little to fail.
  - The preview is a WebView, like step 2, so it needs the bundled renderer.
- **Alternatives considered:** a bottom sheet over Home (it would hide the site behind a dismissable card and be easy to miss), and a single screen with three sections (too much to read at once).
- **Status:** Accepted.

### #74 — 2026-10-18 — 8b-2 backend refinements to the offers plan
- **Decision:** the backend (`20261017132241_companies_offers.sql`) follows OFFERS.md with these refinements, recorded there under "As built (8b-2)" and in BACKEND.md:
  - **companies:** insert only for unscoped admins, update only for admins of that company (the plan said any admin for both);
  - **client-chosen ids** on insert for companies and offers, so the admin panel can name `company-media` paths first;
  - **storage writes** through `public.can_manage_company_media(name)` (path and scope checked in one SECURITY DEFINER function, granted to `authenticated` for the policies), plus an admin-only select policy for listing;
  - **publish:** offers of a company the agent requested before (a changed, withdrawn or revoked link) are the warning `offer_unavailable`, never an error; unknown offer ids are `offer_wrong_company`; the custom-offer rules (`equals`, `maxDaysAhead`, agent CTA kinds, date format) are in the generic `validate_fields`;
  - **`requested_at` defaults to `clock_timestamp()`**, so the latest request is well defined even within one transaction.
- **Why:** least privilege for insurer staff; simpler uploads; switching company must not block publishing unrelated edits (OFFERS.md "Changing company"); one validator for every schema field; deterministic membership history (found by the tests).
- **Status:** Accepted.

### #75 — 2026-10-18 — Server-side rendering in the existing Worker
- **Decision:** the Worker `agentpro-sites` renders `/`, `/a/*` and `/privacy` (custom domains use the same paths) with the same pure template `render()` the browser uses. The response carries the full page, head tags and JSON-LD; the browser only hydrates (enquiry form, Add to home screen, view count) through a slim `hydrate.js`. `index.html` and the client render stay for the in-app preview (`renderSite`) and as the fallback when the backend fails. `run_worker_first` stays an explicit path list, so static assets never invoke the Worker. Security headers are set by the Worker for its own responses (`_headers` applies only to assets), from one shared `web/headers.js`. No edge cache in v1, so a publish shows on the next load.
- **Why:** the user asked for SSR and for SEO; a client-only page shows "Loading…" to crawlers and link previews, and its first paint waits for JS. The templates were built as pure functions for exactly this (WEB_RENDERER.md).
- **Trade-off:** one Supabase RPC per page view on the server (the client no longer fetches the site); Worker invocations count against the Cloudflare plan only for page navigations.
- **Alternatives considered:** pre-rendering static HTML at publish time (needs storage and purge on every lapse or domain change); `run_worker_first: true` (every font and CSS request would invoke the Worker).
- **Status:** Accepted. Supersedes "server-side rendering" in PRODUCT.md "Designed in, built later".

### #76 — 2026-10-18 — Two template families and a conversational layer
- **Decision:** `shared/templates/index.json` gains `family` (`personal` | `offers`), labelled by `catalog.templateFamilies`. **"Meet me"** (`personal`) holds the refreshed `classic`, `modern` and `minimal`; **"Best offers"** (`offers`) adds `spotlight` (featured offer front and centre, offer grid, "ends on" urgency) and `guide` ("What would you like to protect?" chips leading to matching offer and plan cards). All templates share a conversational layer: an agent "speech bubble" line above each section's normal heading, a greeting and quick-reply chips in the hero, FAQs as visitor and agent bubbles, and a sticky Call/WhatsApp bar on phones. For the `offers` family only, offer cards may use the pale `--tint` surface and the offer's 16:9 image; never saturated accent blocks, and still no image in the hero banner (LCP).
- **Why:** the user asked for two directions: one about the agent, one persuasive to acquire clients, both presenting the agent as the person providing the service, partly as a conversation but still a normal website.
- **Supersedes:** "the three templates differ in layout only" (DESIGN_SYSTEM.md, WEB_RENDERER.md) for the `offers` family.
- **Alternatives considered:** replacing the 3 templates with 2; a Personal/Offers mode switch inside every template (both offered to the user, who chose families).
- **Status:** Accepted.

### #77 — 2026-10-18 — Disguised offer names; no insurer name or logo on sites
- **Decision:**
  - The public payload never carries the company's name, the company's logo, or an offer's real product name. `company` in the payload is `{disclaimer}` only.
  - `offers.content.title` is the offer's **public, generic name**, written by the admin. A new admin-only `offers.internal_name` keeps the real product name for the admin panel; it's never in any payload.
  - A server-side check rejects public offer text (every localized field, company and custom offers) that contains a term from `catalog.publicTextRules.blockedTerms` or any company's name in any locale. The terms are multi-word product names and insurer names (Latin and Devanagari), never single everyday words like "jeevan" or "bima".
  - The logo upload and permission columns stay, for the admin panel and the app only.
- **Why:** the user asked that offers and policies on sites be disguised by name to prevent copyright and trademark issues, and chose to hide the company's name too.
- **Partly supersedes #47:** company logos and names no longer appear on sites.
- **Open (counsel):** IRDAI's advertising rules may require an agent's site to name the insurer they represent. An agent's own free text (e.g. a designation like "LIC Agent") isn't checked. Hiding the insurer's name also means sites can't rank for "<insurer> agent in <city>" searches.
- **Status:** Accepted.

### #78 — 2026-10-18 — New sites are seeded with complete starter content
- **Decision:** `shared/site-seed.json` defines the starter content per section as i18n keys with `{name}`, `{firstName}` and `{city}` placeholders; the codegen resolves it for every locale into `SiteSeed.kt`, and `StarterContent` builds the draft from it at site creation (and for onboarding's template preview). Long texts have 3 variants, picked deterministically from the agent's user id (FNV-1a, per-field salt; SHARED_DEFINITIONS.md), so sites don't duplicate each other (SEO). The id is used rather than the slug because it exists before a slug is chosen (onboarding previews the seeded site) and never changes. Testimonials, achievements, years of experience and clients served are **never invented**: those sections start hidden and empty. The `offers` section starts visible only for the `offers` family. Existing sites aren't backfilled; there are no real users yet (launch gate), and the demo accounts are re-seeded by script.
- **Why:** the user asked that a new site already contain all the general things, so the agent can see and approve it at once rather than face an empty page.
- **Alternatives considered:** fallback copy at render time only (the agent would see text that isn't in the editor).
- **Status:** Accepted.

### #79 — 2026-10-18 — "Best offers" sites show the company's offers until the agent picks
- **Decision:** when a site's `offers` section isn't hidden and has no picked or custom offers, `get_public_site*` returns up to 3 of the agent's company's running offers (by `sort`), resolved at read time like picked ones. The primary-host sitemap is fed by a new anon RPC `list_public_sites()` that returns only `slug` and `published_at` for published, non-lapsed sites.
- **Why:** a new "Best offers" site must look finished without the agent picking anything (#78); the sitemap needs the published slugs, which are public pages anyway.
- **Trade-off:** `list_public_sites()` lets anyone enumerate published agent slugs. Each site is already public and linkable; no other field is returned.
- **Status:** Accepted.

### #80 — 2026-10-18 — App transitions: shared-axis X, fade-through, crossfade; no container transform
- **Decision:** every state change in the app animates with one of the four Material motion patterns that fit it, all built from the existing tokens (150/250ms, standard easing) in `Motion.kt`. Navigation uses shared-axis X (with predictive back), bottom-bar tabs and the app gate use a true fade-through (fade out 100ms, then fade in and scale from 92% over 150ms), screen states and in-place swaps crossfade (keyed on the state's kind, not its data), and lazy list rows animate in, out and into place. Linear flows use one `SharedAxisNavHost` wrapper in `core/ui`.
- **Why:** user request ("transitions that look nice and necessary"). The motion spec in DESIGN_SYSTEM.md was mostly not wired: no NavHost set transitions, so every screen change used Navigation Compose's default 700ms crossfade (nearly 3× the 250ms spec), and screen states, gate changes and list updates snapped. Material's guidance maps each relationship to a pattern: spatial/navigational → shared axis, unrelated (tabs) → fade-through, in place → fade.
- **Alternatives considered:** container transform or shared elements (a row growing into its detail): rejected for now, since they need a shared-transition scope threaded through every feature and the gain over shared-axis is small. KPI count-ups and an animated bottom bar: rejected (numbers should read at once; the bar would resize screens mid-transition). Springs: rejected (no bounce, DESIGN_SYSTEM.md).
- **Status:** Accepted.


### #81 — 2026-10-18 — Bundled site illustrations, one house style, no invented faces
- **Decision:** sites get a small set of bundled, AI-generated illustrations in one flat editorial style (docs/IMAGES.md): 3 hero scenes for the Best offers family, one image per `catalog.interests` key (services cards, plan cards, and the default for an offer without an image), a "Let's talk" contact image, and 2 share (OG) images for agents with no photo. They live in `web/images/` as 400/800 px WebP. The renderer draws only the images listed in `web/images/manifest.json`, so it works before they exist. **No face is ever invented** for the agent (initials monogram fallback) or for customers and testimonials. No text, brands or insurer marks appear inside images. The agent's photo stays the only eager image (LCP).
- **Why:** the user asked where the site needs images and for a prompt per image; pre-filled sites (#78) and the persuasive family (#76) look unfinished without pictures. Fabricated faces would be misleading social proof; text in images can't be translated or indexed.
- **Trade-off:** about 300 KB of WebP in the repo (only the images in view load, lazily); every site shares the same illustrations (the hero scene varies by slug).
- **Alternatives considered:** stock photos (licensing per image, inconsistent look, real faces); agent-uploaded images for every slot (contradicts "finished at once").
- **Status:** Accepted. Generation is pending (the user, or a generator connected later).

### #82 — 2026-10-18 — The desktop hero scene may be the LCP element
- **Decision:** on wide screens (≥ 1024 px), Spotlight and Guide show the hero scene in the right column under the featured offer, 400 px wide, and it becomes the desktop LCP element. On phones the LCP element stays the `<h1>` (or the agent's photo), and the scene stays lazy and below the fold. This refines #81's "the agent's photo stays the only eager image (LCP)".
- **Why:** the scene alone under the hero left an orphaned image and a large gap on desktop (screenshot review). Measured on the SSR output: desktop LCP 0.5 s (spotlight) and 0.7 s (guide), Performance 100, CLS 0, so the rule's purpose (speed) still holds.
- **Alternative considered:** narrowing the scene to about 240 px on desktop so it doesn't win LCP (it looked too small beside the offer card).
- **Status:** Accepted.

### #83 — 2026-10-19 — Onboarding only after the server confirms the agent has no site
- **Decision:** the app never offers to create a site (onboarding steps 2 and 3) without first checking that the agent has none. The gate reads the cache afresh and then asks the server (`SiteRepository.fetchMine()`, a direct owner-only select that also caches the row) before starting onboarding for an agent with a complete profile; onboarding asks the server again after step 1, and closes itself if the agent's site arrives. If the server can't be asked, the gate shows the startup error with Retry, and step 1 stays put with the error. A failed sync with no cached site is also the startup error, not onboarding.
- **Why:** user report: after every app update the app reopened onboarding and asked for a new link. Cause: an update that changes the cache schema empties the cache; on the first sync the gate saw "sync done, no site" for a moment, because the sync marks itself Done before Room's live query re-emits the site, and that moment latched onboarding. Step 3 then inserted a new site, which the database refused (`unique (agent_id)`), shown as "this link is taken" for any link. The user asked for the site's existence to be checked before the creation flow starts. One server read at that point is cheap and authoritative, while the cache can be empty or behind.
- **Alternatives considered:** only fixing the race (still trusts a cache that can be emptied while the sync cursors survive); letting step 3 adopt the existing site on a conflict (the agent would still walk through steps they don't need, and a conflict can also mean the link is taken).
- **Status:** Accepted.


### #84 — 2026-10-20 — In-app previews get their offers from a signed-in RPC
- **Decision:** a new `public.preview_site_offers(content jsonb, template_key text) returns jsonb` (SECURITY DEFINER, `authenticated` only, the caller from `auth.uid()`) returns `{company, offers}` exactly as `get_public_site` would resolve them for that draft and template: picked offers, or the automatic ones (DECISIONS #79), the featured one for the offers family, `company` = `{disclaimer}` only, never `internal_name`. The app adds the result to every `renderSite` payload. With no network, the preview renders without offers (as before).
- **Why:** the preview only had the draft, so "Best offers" previews showed generic plan cards instead of the company's offers, and agents chose without seeing the family's main point. One server function keeps the preview and the live site resolving offers the same way.
- **Alternatives considered:** the app re-implementing the resolution from `my_company_offers` (two copies of the rules to keep in step).
- **Status:** Accepted.

### #85 — 2026-10-20 — "Meet me" templates get distinct layouts
- **Decision:** Classic, Modern and Minimal keep the same content, schema and section partials' data, but each template may arrange sections with its own structure, not only CSS: Classic stays centred and traditional; Modern becomes a magazine layout (photo beside the headline, two-column about with the facts in a side column, services as a numbered list); Minimal becomes a short one-page "visiting card" (compact header, about and services merged into one block, FAQ collapsed). Tokens, the 3 spacings, the 4 type sizes, one CTA per section, the semantic rules (one `<h1>`, an `<h2>` per section) and the SSR = client equality all still hold. The user approves screenshots before deploying.
- **Why:** at phone size the three read as one page restyled (user review, 2026-10-20).
- **Supersedes:** "the templates differ in layout only" is now "in layout and structure, never in content or colour scheme".
- **Status:** Accepted.

### #86 — 2026-10-20 — The Appearance preview shows the page after a family switch
- **Decision:** when the agent previews a template of the other family in Website → Appearance, the preview uses the draft as it would be after switching (to "Best offers": `offers` un-hidden and moved after `hero`, as the switch does), plus the RPC's offers (#84). Nothing is saved until the agent chooses the template.
- **Why:** the preview used the current draft, so Spotlight or Guide looked almost like "Meet me" and hid what switching would change.
- **Status:** Accepted.

### #87 — 2026-10-21 — No conversational (chat-style) layer on sites
- **Decision:** remove the speech-bubble layer from all five templates: no bubble above section headings, no hero greeting bubble, no bubbles opening the guide's groups, the FAQ as a normal accordion, and a normal enquiry success message. Kept: the "What can I help you with?" interest links (ordinary buttons), the sticky Call/WhatsApp bar on phones, offers, images and both families' layouts. The first-person seed copy stays ("Hello, I'm …"), as on any personal site.
- **Why:** the user saw it live and found it looked bad ("keep it normal").
- **Supersedes:** the conversational layer in #76 (the families stay).
- **Status:** Accepted. The unused `site.say_*` i18n keys get removed from `shared/` once the web change lands.

### #88 — 2026-10-22 — Email codes (OTP) replace confirmation and reset links
- **Decision:** sign-up confirmation and password reset use the one-time code Supabase emails (`{{ .Token }}`), entered in the app (`verifyEmailOtp`), instead of links through `agentpro://auth-callback`. Password sign-in stays. The sign-up details are no longer held in memory to sign in after a link: a right code starts the session itself. The deep-link handling is kept only so links in older emails still work.
- **Why:** user request. Links depended on the phone's mail app opening the app, on the redirect allow-list, and on the same device; a code works from any mail client.
- **Cost:** the two email templates must be pasted again in Supabase (generated, see BACKEND.md), and Auth's OTP length/expiry settings now matter. Until pasted, emails still carry links and the code field has nothing to enter.
- **Status:** Accepted.

### #89 — 2026-10-22 — An agent can have several companies, each with its insurance types
- **Decision:** `company_memberships` allows **one pending or approved row per agent and company** (was one per agent), and each row carries `insurance_types text[]` (≥ 1 key of the new `catalog.insuranceTypes`, a list separate from the enquiry `interests`). The agent picks them in onboarding step 1 (Create account has no session until the email code is entered, so company links can't be saved there) and edits them in Settings → Profile. Everything else about #53 stays: each link is requested by the agent with an agency code and decided by an admin.
- **Consequences:** offers from **every approved company** can be placed and appear on the site (a featured or picked offer must belong to one of them); the site's company disclaimer comes from the earliest-approved company, and each offer keeps its own; custom offers are allowed unless **any** approved company has `allow_custom_offers = false` (the strictest wins). Changing the types of an approved link needs no new review; changing the agency code or company does (a new request). Supersedes the "one current membership per agent" part of #53.
- **Why:** agents often hold agencies with several insurers and deal in different lines at each.
- **Provisional:** the `insuranceTypes` values and their Hindi labels need the user's review.

### #90 — 2026-10-22 — Approved company names are shown on sites, as text
- **Decision:** the public payload gains `companies: [{name, insurance_types}]` (the agent's **approved** links to **active** companies, oldest approval first; the name in the site's language with the default-locale fallback; type keys from `catalog.insuranceTypes`). Templates render them as a "Companies I represent" section with the types per company. **Names only, as plain text: no logos, slugs or links** (the logo stays admin/app-only). Pending, rejected, revoked and withdrawn links never appear.
- **Supersedes** the "no company name on sites" part of DECISIONS #77, at the user's request (2026-10-22). The rest of #77 stays: offers keep generic public names, `internal_name` never appears, and the blocked-term check still applies to offer text, custom offers and disclaimers (a company's name inside offer copy is still refused; the companies section comes from the `companies` table, not from agent text).
- **Why:** the user wants customers to see which insurers the agent represents. It may also satisfy IRDAI's rule that an agent's advertising names the insurer (the open counsel question under #77).
- **Risk:** trademark use of insurer names in plain text (nominative use) still needs counsel review before a public launch; it's a launch gate, listed in STATUS.md.

### #91 — 2026-10-22 — One layout system for every site template
- **Decision:** all templates share `web/styles/base.css`: one content width (1080px; minimal 640px), sections separated by whitespace only (48px phone / 96px desktop, no hairline dividers; hairlines only between list rows), one heading scale, one card style, 48px buttons with 12px corners. Templates differ in structure, not in ad-hoc spacing. The companies section uses JSON-LD `affiliation` (not `worksFor`/`memberOf`: an agent represents an insurer, isn't employed by or a member of it).
- **Size caps raised in the tests** because the shared stylesheet grew (11 → 15 KB raw): SSR first-paint CSS 20 → 26 KB raw plus a new 7 KB gzip cap (actual 19.7–23.8 KB raw, 5.2–6.1 KB gzip); the in-app preview / fallback path 90 → 100 KB (106 KB with the install modules; privacy page 94 KB).
- **Why:** the user asked for sites that look "standard and planned"; the audit found five different widths, dividers everywhere, mismatched corners and misaligned cards.

### #92 — 2026-10-22 — Company links are saved as they're added, and kept remote only
- **Decision:** in onboarding step 1 each company is sent to `request_company` when its form is saved (not on Continue), so edits and removals act on real ids and closing the app loses nothing. Memberships live in the repository's memory (refreshed after each write), not in Room: OFFERS.md's planned Room snapshot is deferred until something needs offline memberships. Changing an agency code is Remove + Add (no in-place edit).
- **Why:** the server decides the status (admin-verified links, #53), and a Room table would add a schema migration for no current need.

### #93 — 2026-10-22 — Site services and starter copy follow the agent's insurance types
- **Decision:**
  - **Interests cover every insurance type.** Each `catalog.interests` entry names its `insuranceType` (term, endowment, money_back, child, pension, ulip → life; health → health; `other` → none, always available). New interests `motor`, `home`, `travel`, `business` and `accident` (provisional labels, EN + HI). Until their illustrations exist, they use the generic fallback image; IMAGES.md gets their prompts.
  - **The agent's types** are the union of `insurance_types` over their **pending and approved** links (what they declared; rejected, revoked and withdrawn don't count). Company *names* still appear only once approved (#90).
  - **Seeding at site creation** (onboarding step 3, after step 1's companies): `services.offered` and the service items come from the agent's types (a default interest list per type in `shared/`), and the hero subheadline and About bio use copy written for the mix: the current life-and-family copy when every type is life, otherwise copy for general and mixed agents. Nothing is invented.
  - **The enquiry form's "Interested in"** lists the site's offered interests plus "Other" (and `submit_enquiry` accepts every catalog interest).
  - **When the types change later** (Settings → Profile), the app offers once to update the website: add services for new types, and remove services whose type is no longer dealt in. It never rewrites the agent's own text silently. A site whose services don't match the types shows a hint in Website → Content with the same action, which also covers sites made before this change.
- **Why:** a motor or health agent got a life-insurance site.
- **Not done:** the renderer doesn't filter the agent's content by type; the agent's saved content stays the source of truth.

### #95 — 2026-10-22 — Admin panel hosted as a sibling Worker on the current subdomain
- **Decision:** the admin panel (`admin/`) is its own Worker, `agentpro-admin`, deployed next to `agentpro-sites` on the same account subdomain (`agentpro-admin.sledjesoftwares.workers.dev`) until the platform domain is bought, then `admin.<domain>`.
- **Why:** the user asked for a subdomain of the site's current host. workers.dev doesn't allow `admin.agentpro-sites.…`, so a sibling Worker on the account subdomain is the closest option.
- **Status:** Accepted. Deploying still needs the user's approval each time.

### #94 — 2026-10-22 — Real insurance companies replace the single "Demo Life Insurer" test row
- **Decision:** the live `companies` table holds **11 real, IRDAI-registered insurers** (LIC, HDFC Life, ICICI Prudential Life, SBI Life, Max Life, Bajaj Allianz Life, Tata AIA Life, Kotak Mahindra Life, Aditya Birla Sun Life, PNB MetLife, Star Health), each with an EN/HI name and the standard IRDAI solicitation disclaimer. Only names, per #90; no logos are added (CLAUDE.md branding rule). The fake "Demo Life Insurer" row is **deactivated** (`is_active = false`), not deleted: its 6 disguised test offers are re-created (same content, per #77) under LIC because `offers_guard` forbids moving an offer between companies, and the 4 existing test memberships move from demo-life to LIC through the normal withdraw → request → approve state machine, because `memberships_guard` forbids changing `company_id` on an already-linked row. A real `DELETE` of demo-life was tried first and rejected (`23503`, `company_memberships_company_id_fkey`): withdrawn memberships keep their `company_id` as history, so `ON DELETE RESTRICT` blocks it even once nothing active points there — which is also exactly why `is_active` exists ("never hard-deleted", OFFERS.md). SQL: `supabase/migrations/20261022180000_real_companies.sql` (data-only; run once as postgres in the SQL Editor, not through the connector, which declines it as a production write; the whole thing is one transaction, so the first failed attempt rolled back cleanly with nothing committed).
- **Scope:** this is the **live `AGENT_PRO` project only**. `supabase/demo/demo-offers.json`, `gen_demo_offers.py`/`demo_offers.sql`, `supabase/tests/95_offers.sql` and `web/dev-fixtures/*` keep the fictional `demo-life` company for local Postgres tests and web dev fixtures — that's an internal test fixture, not production data, and doesn't need to match the live catalog.
- **Why:** the user asked to use real insurance companies, not a demo placeholder, now that #90 allows a real company's name to appear on an agent's site.
- **Risk:** this is exactly the trademark/nominative-use question #90 already flagged for counsel, now with real data live rather than hypothetical — still a launch gate (STATUS.md).
- **Not done:** the admin panel (8c) to manage this list doesn't exist yet, so adding, editing or deactivating a company is still a manual SQL statement by the user. `offers.internal_name` was left null for the 6 re-created LIC offers (admin-only field, unused until 8c).

### #101 — 2026-10-22 — Offer images are one per offer
- **Numbering note:** first written as a second "#96" (the customer-portal entries #96–#100 were added in parallel); renumbered 2026-10-23, content unchanged.
- **Decision:** each offer has at most one image (`offers.image_url`, 16:9 WebP, 512 KB), uploaded by an admin in the panel. There is no shared company image library for agents.
- **Why:** the user's answer to the open question. It keeps insurer artwork out of agent uploads and matches the existing schema.
- **Status:** Accepted.

### #96 — 2026-10-23 — Customers log in with an agent-issued link, then mobile + 6-digit PIN
- **Decision:** the agent taps **Send login link** on Customer detail; the app shares `…/my/start?t=<token>` (WhatsApp/SMS through the share sheet). The customer types their mobile, which must equal the number the agent has on file (the second factor if the link is forwarded), sets a 6-digit PIN and ticks the `customer_portal` consent. Afterwards: mobile + PIN. The invite is single-use, valid `catalog.portal.inviteDays` (7), and burns after `inviteMaxPhoneTries` (5) wrong numbers; a new link also resets the PIN ("forgot PIN" = ask the agent). Lockout: `maxFailedAttempts` (5) → `lockMinutes` (15); `hardLockAfter` (20) → only a new invite unlocks. One login per (agent, mobile): it shows every customer the agent has **explicitly invited** on that number (families), never a number match alone.
- **Why:** SMS OTP needs a paid provider (out of scope for v1); many customers have no email and Auth email is capped (Gmail SMTP, 100/hour). The agent already knows and verifies the customer.
- **Status:** Accepted (user: "decide everything"). Values in `catalog.portal` are provisional.

### #97 — 2026-10-23 — Customers are not Supabase Auth users
- **Decision:** customer logins live in four `portal_*` tables (accounts, account_customers, invites, sessions) with RLS on and **no API grants**, reached only through `SECURITY DEFINER` RPCs that take an opaque bearer token. Tokens are 32 random bytes (pgcrypto `gen_random_bytes`), stored only as SHA-256; PINs are bcrypt (`extensions.crypt`). A session is valid only on its own agent's site.
- **Why:** `auth.users` means "agent" everywhere (signup trigger, `entitlement()`, owner-only RLS); adding customers there would mix tenants and need exceptions in every policy. Public access through listed RPCs is the existing rule (CLAUDE.md).
- **Status:** Accepted.

### #98 — 2026-10-23 — The portal is server-rendered by the Worker with an HttpOnly cookie
- **Decision:** `/a/<slug>/my…` (custom host `/my…`) is rendered by the Worker; forms POST to the Worker, which calls the RPCs and redirects (PRG). The session token is only in the cookie `ap_portal` (`HttpOnly; Secure; SameSite=Lax; Path=/a/<slug>/my` or `/my`; 90 days). CSRF: SameSite=Lax plus an `Origin` check on every POST. Responses are `no-store` and `noindex`. Works without JS.
- **Why:** page JS never sees the token, so an XSS can't steal it; same stack and SSR path as the sites (#75); fast on budget phones.
- **Status:** Accepted.

### #99 — 2026-10-23 — Customer login v1 is read-only
- **Decision:** in scope: policies, due status, payment history, "Ask about this policy" / "I've paid this premium" (an enquiry with the new source `portal`, linked to the customer), "Your details" with correction/erasure requests (`data_requests` linked to the customer), sign out (and everywhere). The payload never includes the agent's `notes`. Out of scope: online premium payment, document upload, self-editing.
- **Why:** smallest useful scope; payments go through the insurer, and editing would bypass the agent, who is the fiduciary.
- **Status:** Accepted.

### #100 — 2026-10-23 — The portal is an installable PWA on the existing stack, with Web Push reminders
- **Decision:** no framework: the Worker SSR + vanilla ES modules + token CSS stay. The portal gets its own manifest (`id` = `start_url` = `scope` = `/a/<slug>/my`, name "{Agent} · My policies"), a service worker served at `/a/<slug>/my/sw.js` (scope = the portal only; network-first, the last home page cached for offline with an "Offline — last updated" banner, never a POST or the start page; cleared on sign-out with `Clear-Site-Data: "cache"` and on revoked/unavailable). Opt-in Web Push premium reminders (`portal_push_subscriptions`, Edge Function `portal-reminders` on `pg_cron`, VAPID; minimal payload without amounts or policy numbers), built after the core portal is verified.
- **Why:** the sites' PWA path already works (#65) and keeps first paint around 25 KB; a framework adds weight for mostly static lists. Offline access and reminders are what make installing worth it.
- **Scope:** site pages still have **no** service worker (#65 stands). On iOS the installed app has its own storage, so the customer signs in once more there; push only inside the installed app (iOS 16.4+).
- **Status:** Accepted. Push needs the user's approval for the VAPID secret, the Edge Function deploy and `pg_cron`.

### #102 — 2026-10-23 — Admin invites use a short-lived email marker
- **Decision:** before inviting, the `admin` Edge Function stores the email in `private.admin_invites` for at most 15 minutes, and `private.handle_new_user` skips a new user with a fresh marker. `app_metadata.account_kind = 'admin'` is set straight after the invite.
- **Why:** GoTrue inserts the user before applying app_metadata, and `/invite` accepts none, so `account_kind` alone can't stop the signup trigger from creating an agent profile, trial and site.
- **Alternatives rejected:** deleting the auto-created profile afterwards; user_metadata (users can edit it).
- **Status:** Accepted (built, not applied).

### #103 — 2026-10-23 — Revoking an admin ends their sessions in SQL
- **Decision:** `svc_admin_revoke` sets `revoked_at` and deletes the user's `auth.sessions` and `auth.refresh_tokens` in the same transaction. Revoked accounts aren't banned. If Postgres can't write those tables, the revoke still succeeds and returns `signed_out: false`.
- **Why:** supabase-js 2.58's `auth.admin.signOut` needs the user's own JWT.
- **Status:** Accepted.

### #104 — 2026-10-23 — Admin writes are audited by the helpers, not the trigger
- **Decision:** each Edge Function write logs one explicit `audit_log` row (`admin.invite`, `admin.set_role`, `admin.revoke`, `admin.reset_mfa`, with the actor and IP; role and scope only). `audit_row` skips those writes via a transaction-local setting, and still logs any other write to `admins` as `system`.
- **Why:** no double rows, and the actor and IP are only known in the function.
- **Status:** Accepted.

### #105 — 2026-10-23 — Admin self-protection and error statuses
- **Decision:** an owner can't change, revoke or reset MFA on their own account (`self`), and the last active owner can't be demoted or revoked (`last_owner`, under an advisory lock). Errors are always 4xx and never 404 (`not_found` is 410, `failed` 422), because the panel reads 404 as "not deployed".
- **Status:** Accepted.

### #106 — 2026-10-23 — Admin role keys are repeated in the Edge Function
- **Decision:** `supabase/functions/admin/index.ts` repeats `owner` and `editor`, with a comment pointing at `catalog.adminRoles`. The `admins_role_check` CHECK from `catalog_sync` is the authority.
- **Why:** the function can't read `shared/` at runtime. This is an accepted exception to "definitions live only in `shared/`".
- **Status:** Accepted.

### #101 — 2026-10-23 — Customer login hardening after the security review
- **Decision:** (1) the six anon portal RPCs run only with a secret header from the Worker (`x-portal-key`, a Worker secret; its SHA-256 sits in a private table set by hand), so the public anon key can't reach bcrypt or the lockouts; plus a per-site cap on failed logins checked before bcrypt; (2) a customer is attached to a login only when **their own** link is used (activation), with one consent row for that customer, never at invite time; (3) PIN rules reject patterns, a list of common PINs (`catalog.portal.blockedPins`) and the customer's date of birth; failures count over a window (`failureWindowDays`) and a successful login no longer resets the hard-lock count; the hard lock lasts `hardLockHours` instead of forever; (4) the invite token moves from the URL into a short-lived HttpOnly cookie on first load (`ap_invite`, 15 min), and the start page uses `Referrer-Policy: strict-origin` (with `no-referrer` browsers send `Origin: null`, which the CSRF check rightly refuses — no customer could activate); (5) verified customers' data requests get their own rate counter; (6) `Clear-Site-Data` is dropped (it doesn't clear service-worker caches and, on the shared origin, would clear every agent's cache); the signed-out page tells the service worker to wipe.
- **Why:** the review (2026-10-23) found H1 (activation impossible in Chrome/Firefox), H2 (direct RPC calls bypass the Worker's rate limit: database DoS and mass lockouts), M1 (a new invite exposed a customer to an existing login before consent) and M2 (endless slow guessing; DOB PINs accepted).
- **Recorded for later:** cookie `Path` is not a security boundary on the shared sites origin, and Cache Storage is per origin; the long-term fix is a separate origin for portals (e.g. `my.<platform domain>`) once the platform domain exists. Until then any XSS on the sites origin is treated as critical.
- **Supersedes:** the "permanent hard lock" and "sessions on invite" parts of #96, and the `Clear-Site-Data` part of #100.
- **Status:** Accepted (security review; the user said "decide everything").

### #102 — 2026-10-23 — Sites promote customer accounts; the portal home opens with a dashboard row
- **Decision:** a new field-less site section `portal` ("For my customers", before Contact in every template's default order, placed before Contact when a saved order doesn't name it, hideable and movable like `companies`) and a small "Customer login" link at the top of the hero, both rendered only when the public payload's `portal` is true. The section's copy is fixed i18n (not agent-editable) and promises only what exists: policies, next premium, recorded payments, asking about a policy; reminders are mentioned only once Web Push ships. Each template draws it in its own system (classic soft card, modern hairline row, minimal plain lines, Best offers a compact card above the contact band). The portal home gets summary tiles computed from the existing payload (policies, premium per year, total cover, next due, people).
- **Why:** user request; customers can't use a login they don't know exists, and agents gain a reason to share their site with existing clients.
- **Status:** Accepted (user: "plan it well, then implement it").

### #107 — 2026-10-24 — Sites reveal sections on scroll, injected by script
**Decision:** one subtle reveal (fade + `--space-s` rise, `--motion-medium`) for below-the-fold sections, applied by `web/reveal.js`, with its CSS injected by the script instead of added to `base.css`.
**Why:** the user asked for section animations "as per the convention", which is subtle, token-only, reduced-motion-safe motion (DESIGN_SYSTEM.md, Motion). A hidden-by-default CSS state would hide content from no-JS visitors and crawlers and would push modern's first-paint CSS past its 26 KB limit.
**Not done:** per-template animation styles, stagger of cards, parallax or hero animation (decorative, and they would delay reading).

### #103 — 2026-10-24 — Existing agents are led to new features by a one-time "What's new" sheet and the setup checklist
- **Decision:** (1) a one-time "What's new" bottom sheet on Home, versioned by a constant (`WhatsNew.VERSION = 1`) stored in the key-value store (`whats_new_seen`); shown only when the agent already has a site and hasn't seen this version; finishing onboarding marks it seen, so new sign-ups never get it. Version 1 lists: companies and insurance types (→ Settings → Profile), customer login (→ Customers), and "For my customers" on your website (→ Website → Content); each row opens its screen, plus "Got it". (2) The Home "Finish setup" checklist gains "Add your companies" (done when the agent has a pending or approved company link, or a "not listed" request; checked online from `my_company_offers`, stays undone offline) and "Give a customer their login" (done once an invite has been sent from this device, a local mark like "Share your link"); each item shows only when its feature flag is on. (3) In the checklist only the **next** task (the first visible item not done, in order) and the tasks already done are tappable, each opening its screen; later tasks are shown muted and can't be tapped until their turn (user, 2026-10-24: "let them click only the next task or the previously completed tasks"). The avatar shows the agent's photo (hero photo, else profile photo, as the site does).
- **Why:** user question; website changes reach existing sites automatically, but the app never told existing agents about companies or customer login.
- **Status:** Accepted (user: "ok do it").

### #104 — 2026-10-24 — Offers editor in two steps: a list editor now, tap-to-place next
- **Decision:** step 1 (8b-4a) ships a list-based Offers editor; step 2 (8b-4b) adds OFFERS.md's slot mode (tap a spot on the preview, `preview-bridge.js`, `addWebMessageListener`), with its own security review. Step 1:
  - `offers` leaves `SiteContent.pendingSections`, so Website → Content lists **Offers** (show/hide, move, the section title).
  - `FormRenderer` gets the `offerRef` and `offerList` field types. Each is a row opening an **offer picker**:
    - **Pick offers** (`offers.items`): the agent's live company offers from `my_company_offers`, with tick boxes, an order and the `maxSectionItems` cap. Each row shows the public title, the summary, "Ends {date}" and the company name (the company name only inside the app). Ended or unavailable picks are flagged, as `publish_site` warns.
    - **Featured offer** (`hero.featuredOffer`): one company offer, one custom offer, or none.
  - **Your own offers** (`offers.custom`, the existing list field): title, summary, end date (`maxDaysAhead`), CTA kind (agent-allowed kinds), interest, and the required "I confirm this offer is accurate and my company allows it" toggle (`equals: true`).
  - App validators for `mustBeTrue` and `maxDaysAhead`, matching `publish_site`.
  - With no approved company, the picker explains why and points to Settings → Profile.
  - Empty picks keep the automatic offers (DECISIONS #79), and the editor says so.
- **Why:** user report; the site showed offers the agent couldn't control.
- **Status:** Accepted (user chose this option).
- **Amended the same day (user: "offers are configured by the admin and they just need a click to be added by the agent, which can be edited after they are added"):** the picker is an **"Offers on your site"** manager instead of a checkbox list.
  - **Add:** "From your companies" lists live company offers with a one-tap **Add**. It saves at once as a company reference, so admin updates and endings flow through.
  - **"On your site":** each added offer can be moved, removed or set as featured.
  - **Edit on a company offer** makes the agent's **own copy**: after a one-time confirmation, it becomes an `offers.custom` item prefilled from the company's text, with `confirmed` false so the agent must confirm before publishing. Its reference (and the featured reference, if it was featured) is re-pointed to the copy. The site then shows "Details provided by the agent", and the blocked-term check applies.
  - **Why the admin's text isn't edited in place:** it stays accurate and keeps its disclaimer. No schema, backend or web change.

### #108 — 2026-10-25 — Two ways to add a customer: autosaved forms ("Incomplete forms") and a live customer sign-up link
- **Decision:** Add customer offers **Fill it in myself** (Pick from contacts stays as a shortcut) and **Send the customer a link**. Both kinds of unfinished form live in one agent-owned table, `customer_drafts` (`source` `agent` or `customer`), listed in the app as **Incomplete forms** (resume or delete, with Undo).
  - **Manual form:** the Add form autosaves into a `source = agent` draft as the agent types (debounced), through the normal client writes and RLS. Saving the customer completes the draft.
  - **Sign-up link:** `customer_signup_link()` creates a `source = customer` draft and a single-use token (32 random bytes, stored only as sha256 in a private table, returned once), shared as `<site>/join?t=<token>`. The page on the agent's site is served by the Worker (the token moves into an HttpOnly path cookie, as for the portal invite). It calls four anon RPCs (`signup_open`, `signup_consent`, `signup_save`, `signup_submit`), all behind the portal's Worker gate (`x-portal-key`). The page autosaves each pause in typing plus a heartbeat. The app watches the row over Supabase Realtime (values, `active_field`, `customer_seen_at`), so the agent can guide the customer live.
  - **Submit adds the customer automatically** (the user's choice over "agent reviews first"). The RPC validates fully, creates the `customers` row, records the consents (channel `web`), and completes the draft, clearing its `fields`.
  - **Consent before collection (DPDP):** the page shows the new `customer_signup` notice, and no field can be saved until the customer ticks it; the consent is held on the draft and copied to `consents` at Submit. An under-18 date of birth needs the separate `customer_signup_guardian` notice ticked by the parent or guardian, because the app's `guardian_consent` wording is the agent's attestation, not the guardian's own words. The agent passes the one-time customer-duty gate before sending a link.
  - **Retention:** `retention.customerDraftsDays = 30` (the user's choice). Old drafts stop working and are purged by `private.purge_customer_drafts()`; its daily schedule needs the user's approval (pg_cron). Limits are in `catalog.customerSignup` (provisional): link 7 days, 60 saves a minute, 25 open links, presence 30 s.
- **Why:** user request (PRODUCT.md, 2026-10-25). One table keeps "Incomplete forms" a single list and lets Realtime drive the live view without polling. The token lives off the client-readable table, so RLS `select *` can never expose it. Reusing the portal's gate and token rules means one reviewed pattern instead of two.
- **Alternatives considered:** local-only drafts in Room (rejected: the user asked for the database, and a phone change would lose them); the agent reviewing before the customer is added (offered; the user chose automatic); storing the customer's typing before consent (rejected: DPDP needs notice and consent before collection).
- **Status:** Accepted (user chose the three options on 2026-10-25).

## 105. Finish setup: every step tappable, soft prompt on arrival
- **Decision:** supersedes the next-only lock in #103. All visible checklist rows open their screen; "Next step" stays as a suggestion. Opening the About editor from the list (`WebsiteSectionRoute(fromSetup = true)`) shows a prompt card saying what to do.
- **Why:** user report: could not get past "Make your About section your own" (the editor opens pre-filled with the seeded bio; saving it unchanged leaves the step undone, and the lock hid every later step). A step the agent doesn't understand must not block the rest.
- **Not done (open):** backfill of the local Share / Customer login marks for agents who finished them before this update; prompts for steps other than About; a Skip action. Existing agents' data-derived steps (profile, publish, About, first customer) already count automatically.
- **Status:** Accepted.

### #111 — 2026-10-25 — The first owner is the converted account `sledjesoftwares@gmail.com`
- **Decision:** the first admin (`owner`, every company) is the existing account `sledjesoftwares@gmail.com`, converted from an agent account instead of a fresh invitee: its agent-only rows (profile, trial subscription, sign-up consent; it had no site or customer data) are deleted, `raw_app_meta_data.account_kind` is set to `admin`, an `admins` row is inserted as postgres (the SQL Editor bootstrap, audited as `system`), its password is set from a locally computed bcrypt hash, and its sessions are ended.
- **Why:** user request. Admins are separate from agents (OFFERS.md), so the account must not keep a profile: the invite path refuses an address with a profile, and the app would otherwise still treat it as an agent.
- **Consequence:** this address can no longer be used as an agent in the app. MFA (TOTP) is enrolled at the first admin-panel sign-in, since `is_admin()` needs `aal2`.
- **Status:** Accepted (pending the user's SQL Editor paste).

### #112 — 2026-10-25 — One-tap "Continue with Google" is the main way in; email sign-up stays
- **Decision:** Welcome's main action is **Continue with Google**. Android uses Credential Manager behind a new platform seam, `core/platform/GoogleSignIn` (`AndroidGoogleSignIn`): first the one-tap account sheet (`GetGoogleIdOption`, every account, no auto-select), then, if it has nothing to offer, the full "Sign in with Google" dialog (`GetSignInWithGoogleOption`). The ID token goes to Supabase with `signInWith(IDToken) { provider = Google; nonce }`; each try uses a fresh random nonce, and the token carries its SHA-256. The web client ID is the build key `GOOGLE_WEB_CLIENT_ID` (`mobile/local.properties` → generated `AppConfig`); without it the button is hidden and Welcome is as before. **Sign up with email** and **Sign in** stay as text buttons, so existing accounts are unaffected.
  - **Consent first (DPDP):** the same unticked, required `agent_signup` checkbox as Create account sits above the button, and the button is disabled until it is ticked. A Google sign-in can't carry sign-up metadata to the signup trigger, so the app keeps the consent as pending (`StoreKeys.PENDING_SIGNUP_CONSENT`) before signing in and records it with `record_consent` straight after, outside the screen's lifetime. If that call fails, onboarding step 1 records it before it saves anything, and won't continue until it has. Sign-out clears it.
  - **Sign-up notice v2:** the notice now says the account data can come from Google and lists Google as a processor; the designation is no longer listed (#114). New `catalog_sync` migration `20261025173029`.
  - **Errors:** "no Google account on this phone" and one "couldn't sign you in with Google" message for every refused token or setup problem (never "Wrong email or password"); offline and rate limits keep their own messages.
- **Why:** user request (PRODUCT.md, 2026-10-25): reach the templates sooner. Google gives a confirmed name and email in one tap, with no password and no email code.
- **Libraries:** `androidx.credentials` 1.6.0 (+ `credentials-play-services-auth`) and `com.google.android.libraries.identity.googleid` 1.2.1, Android only, behind the seam (KMP rule). iOS gets its own implementation in the iOS phase.
- **Alternatives considered:** supabase-kt's `compose-auth` plugin (rejected: it hides the flow inside a composable we can't fake in tests, and adds a second auth abstraction); the browser OAuth redirect (rejected: slower, leaves the app, needs a deep link); dropping email sign-up (rejected: existing accounts and agents without a Google account).
- **Needs (user, outward-facing):** a Google Cloud OAuth "Web application" client (its ID goes in `GOOGLE_WEB_CLIENT_ID` and in Supabase → Authentication → Providers → Google, with its secret) and an Android OAuth client for package `com.sledje.agentpro` with the signing key's SHA-1 (debug and release). STATUS.md has the steps.
- **Status:** Accepted (built 2026-10-25; not live until the user sets up the Google clients and the provider).

### #113 — 2026-10-25 — Onboarding shows the website before asking about companies: Mobile → Style → Design → Your work → Link
- **Decision:** onboarding becomes five steps: **1 Your mobile number** (the name from Google or Create account as a card with the email and Edit; only the mobile asked), **2 Choose a style** (one card per family with what the site shows first, who it suits and "See N designs"; the texts are `templateFamilies.showsKey`/`bestForKey` in the catalog), **3 Pick a design** (the pager of that family's templates, previewing the agent's own starter content), **4 Your work** (the companies with their insurance types, then the city; photo, agency code and branch under More details) and **5 Your link** (unchanged). The site is still created only at step 5, so it is seeded from step 4's insurance types (#93). The server site check (#83) stays on step 1.
  - **Preview before companies:** step 3 previews with no company links, so the seed's `whenNone` types (life) apply; the hint line says the companies come next and every word can be changed. Coming back from step 4 re-renders with the real types.
  - The catalog codegen now generates any extra `<name>Key` field of a catalog list as `<name>: StringResource` (it did only `descriptionKey`), and `verifyShared` checks those keys exist in EN and HI.
- **Why:** user request (PRODUCT.md, 2026-10-25): seeing the website early is the hook; the company form was the slowest part and blocked it.
- **Supersedes:** the step order of #89 ("Your companies" on step 1) and the three-step flow in UX.md; #89's rules for the companies themselves are unchanged.
- **Status:** Accepted.

### #114 — 2026-10-25 — No designation input: every agent is an "Insurance agent"
- **Decision:** the designation field is removed from onboarding and from `ProfileInput`, so the app no longer writes `profiles.designation` (the column stays; older rows keep their value and nothing reads it for new content). The seed's `about.designation` is the fixed text `seed.about.designation` ("Insurance agent" / "बीमा एजेंट"), and the bios no longer use a `{designation}` placeholder ("I'm {name}, an insurance agent in {city}."); `designation` is no longer a seed profile field or placeholder. The site's About field stays editable. The web hero line reads the site's `about.designation` first, then the profile's (older accounts).
- **Why:** user request: "remove the box that asks designation and remove its dependencies; the designation is of agent". A bonus: a blank designation used to push every bio to its city-less fallback.
- **Consequence:** a bio seeded before this change no longer matches the seeded texts, so Home's "Make your About section your own" counts it as the agent's own (only the demo and test accounts have such bios).
- **Status:** Accepted.

### #115 — 2026-10-26 — No template families in the app: one intro, then one pager of the 5 templates
- **Decision:** the agent no longer chooses between "Meet me" and "Best offers". Onboarding step 2 is now an intro, "Let's select a template for your website", with **Continue →**. The arrow nudges back and forth, and the screen opens the designs by itself after 5 s, with the countdown shown under the button. Step 3 and Website → Appearance → Template are one pager of all 5 templates (classic, modern, minimal, spotlight, guide). This supersedes the family-first parts of #76, #85 and #113 for the app UI; the step order of #113 is otherwise unchanged.
- **Kept on purpose:** `templateFamilies` in `catalog.json`, `family` in `templates/index.json`, `TemplateFamily` and `catalog_template_family()` stay as an internal grouping. The seed (`visibleOnlyInFamily`: `offers` visible only on spotlight/guide), the Appearance un-hide-offers switch (#86), the web renderer's styling and SQL all still read them. Removing them would mean a schema, codegen and migration change with no visible effect.
- **Why:** user request (2026-10-26): "remove two families, have 5 templates only"; the choose screen should say "Let's select a template for your website" with a Continue button whose arrow moves back and forth, and move forward to the templates automatically in 5 seconds.
- **Status:** Accepted.


### #116 — 2026-10-26 — Onboarding: no More details; a skippable photo step and the finished website before the link
- **Decision:** onboarding becomes seven steps: Your mobile → Template intro → Pick a design → **Your work** (companies, then the city; **no More details**) → **Your photo** (its own screen with the photo tips: **Add photo** / **Skip**, then **Continue**) → **Your website** (the finished site, full width: step 3's design with the starter content, the photo and the companies) → Your link. After **Publish now** the launch screens are **Add to phone → Share**; the old "This is what customers see" screen is dropped, because step 6 just showed the site, and its **Open** moves to the Share screen.
  - **Agency code and branch are no longer asked in onboarding.** The agency code belongs to each company link (the company sheet asks for it; DECISIONS #89), so a second, profile-level code was a duplicate. The branch goes with it. The `profiles.agency_code` and `branch` columns stay; onboarding keeps whatever was saved there before (`ProfileInput` gets the saved values), and nothing in the app edits them now. The site's "show agency code" option still reads `profiles.agency_code`, so for new agents it has nothing to show (see Open questions in STATUS).
  - **Pending companies in the step-6 preview:** the public payload lists only approved companies (#90), but a new agent's links are all pending, so the preview would show none. Step 6 builds the list from the agent's pending and approved links (`StarterPreview.companies`) and says, while any is pending, that they appear on the live site once confirmed. This is honest about the gap, not a promise that the live site shows them now.
  - **The template pager no longer steals vertical scrolls:** the Android `SitePreview` WebView asks its parent not to intercept once a drag has passed the touch slop mostly vertically (`VerticalScrollLock`); Compose's AndroidView passes that on, so the pager doesn't take the rest of the gesture. A drag that starts sideways still swipes templates.
- **Why:** user report after moving through the app (PRODUCT.md, 2026-10-26): "More details asks for agency code and branch, which is a fault because it is already linked with the companies"; "add a new screen to ask for the picture of the agent with an option to skip; after that show him the final site with picture and company details; then let him share"; "the templates shown are hard to scroll down".
- **Alternatives considered:** showing only approved companies in step 6 (rejected: a new agent would see no companies at all, which is what the user complained about); keeping the post-publish preview (rejected: the same site twice within a minute); putting the link field on the step-6 screen (rejected: the keyboard would cover the preview); turning off swiping in the pager and using arrows (rejected: swiping between designs is the main gesture there).
- **Supersedes:** the step 4 contents and the five-step count of #113, and the three launch screens of #71 (now two).
- **Status:** Accepted.

### #117 — 2026-10-27 — Five redesigned, lively templates: a top bar, sliders, tickers, count-up numbers and dark bands
- **Decision:** the five template keys stay (`classic`, `modern`, `minimal`, `spotlight`, `guide`; no migration, saved sites keep their template), but each gets a new structure and the sites get moving parts:
  - **Trust (`classic`) follows the user's Theme A wireframe:** a sticky top bar (face, name, links to the sections that rendered, Call; a menu on phones), a full-width banner (the hero scene picture) with the headline on a dark panel, a ticker of the agent's numbers, languages and highlights, a "Current offers" slider (featured first, picture beside text), an introduction (photo beside a dark panel with counting numbers), a companies ticker, a 2×3 grid of plan cards, a three-up "Why choose me" slider, a "What my policyholders say" slider, a dark footer.
  - **Fresh (`modern`):** sticky top bar; a big left headline beside a tall 4:5 portrait on a tinted block with a floating badge of counting numbers; a companies marquee band; big-number about; a bento grid of services; a wide lead offer then hairline rows; numbered highlight columns; a rotating pull quote; the FAQ beside its heading; a dark contact section.
  - **Simple (`minimal`):** a profile card (photo, Call / WhatsApp / Email, facts) pinned on the left from 1024 px beside a résumé-like column (a compact header card on phones); still no bundled illustrations.
  - **Spotlight:** a dark hero band with a slider of the first offers (featured first, each with its button), a trust bar overlapping its edge, an offer wall (the first card double width), picture tiles, icon cards, a quotes slider, a two-column FAQ, a tinted contact band.
  - **Guide:** "What would you like to protect?" answered with picture tiles; a three-step "How it works" strip (new keys `site.steps_heading`, `site.step_1..3`); groups by need beside an index that follows the group in view (sticky side list, a sticky swipeable strip on phones); a trust ticker; one big rotating quote.
  - **Shared parts** (`web/sections/ui.js`, `web/motion.js`, base.css): sliders are scroll-snap lists that work without JS; `motion.js` adds arrows, dots, autoplay (paused on hover or focus, stopped for good once the visitor uses them, WCAG 2.2.2), count-up of `.cu` numbers, the top bar's shadow and menu, and the guide index. Tickers are CSS animations of a duplicated run (the copy `aria-hidden`), paused on hover. The hero text rises in with **transform only**, so the `<h1>` stays the LCP element. Cards marked `.stg` rise in one after another (reveal.js). Under `prefers-reduced-motion: reduce` nothing autoplays, counts or scrolls, and tickers wrap as static text.
- **Supersedes:** in DESIGN_SYSTEM "Website theme", "the accent never for large colour blocks", "whitespace only between sections" and "one call to action per section" are relaxed: dark bands (`#111827`-ish ink), a deep accent ticker band and pale tint bands are allowed as section backgrounds, and a hero may have a main button plus a WhatsApp ghost button (or a slider of offer buttons on Spotlight). The structures of #85 (classic centred portrait, modern magazine, minimal 640 px visiting card) and the hero layouts of #76 for spotlight and guide are replaced. The "Customer login" link (#102) moves into the top bar on templates that have one (classic, modern, guide) and into the profile card on minimal.
- **Kept:** the 4 type sizes and 3 spacing values (CSS lint), Inter / Noto Sans Devanagari only, tokens and the agent's accent, the escaping rules, the form, the sticky Call / WhatsApp bar (still the only `<nav>`), no logos, no stock faces, no chat-style layer (#87), the companies rules (#90), the portal section (#102), section order and hiding.
- **Budgets raised (tests):** SSR first-paint CSS 26 → 34 KB raw, 7 → 9 KB gzip (measured 29.9–32.7 KB, 7.6–8.1 KB gzip); client render 106 → 124 KB (+ install 113 → 131); privacy/legal pages 102 → 112 KB; portal first paint 26 → 30 KB. The weight is the top bar, slider, ticker and band CSS and the template parts.
- **Why:** user request (PRODUCT.md, 2026-10-27): the templates were "not well designed"; they asked for five proper templates with different structures following their wireframe, with sliders, animation and moving parts, and a lively site.
- **Alternatives considered:** new template keys beside the old ones (rejected: saved sites and the catalog sync would need a migration for no benefit); a carousel library (rejected: CSP `script-src 'self'`, page weight; scroll-snap plus ~4 KB of our own JS does it); autoplay without a stop (rejected: WCAG 2.2.2).
- **Status:** Accepted.

### #118 — 2026-10-28 — CI/CD: the web renderer auto-deploys on push to main
- **Decision:** `.github/workflows/deploy-web.yml` builds and deploys the public-site renderer to the `agentpro-sites` Cloudflare Worker automatically on every push to `main` touching `web/**`, `shared/**` or the build/test scripts (or on manual `workflow_dispatch`). It runs the full `node scripts/test-web.mjs` suite and `node scripts/lint-web-css.mjs` first, builds with `sh scripts/build-web.sh` (no `RELEASE=1`, matching the current preview-only state), then `npx wrangler@4.151.1 deploy --config web/wrangler.jsonc`, authenticated with `CLOUDFLARE_API_TOKEN` / `CLOUDFLARE_ACCOUNT_ID` repo secrets instead of an interactive `wrangler login`. `SUPABASE_URL` and `SUPABASE_ANON_KEY` are repo secrets; `SITE_BASE_URL` is a repo variable (default: the existing preview URL). A failing test or lint step blocks the deploy; the manual deploy path (CLAUDE.md, WEB_RENDERER.md) still works unchanged for ad hoc use.
- **This is a carved-out exception to CLAUDE.md's "outward-facing actions need the user's explicit approval each time" rule** (which otherwise covers deploys) — the user chose automatic deploy over a manual-approval gate (e.g. a protected GitHub Environment) when asked directly. The exception is scoped to this one Worker/workflow; every other deploy, domain purchase or cloud project action still needs approval each time.
- **Why:** user request ("build the pipeline") after noting pushes to git were not auto-deploying the site.
- **Alternatives considered:** a protected GitHub Environment requiring a manual approval click before the deploy step (rejected by the user — they want it fully automatic); `workflow_dispatch`-only with no automatic trigger (rejected, same reason); a third-party `wrangler-action` (skipped in favour of the exact `npx wrangler@4.151.1` command the docs already specify, so CI and manual deploys stay identical).
- **Not done:** the GitHub repo secrets (`CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`) are not yet added — the user is adding them. Until they exist, pushes to `main` will run the workflow and fail at the build/deploy step.
- **Status:** Accepted.

### #119 — 2026-10-28 — The in-app preview loads the live renderer; the bundled copy is the fallback
- **Decision:** `SitePreview` first loads the deployed renderer at `<SITE_BASE_URL>/?preview=1` (the Worker's static shell, never SSR'd, no backend call) and calls `window.renderSite(json)` as before. The renderer bundled in the APK (`agentpro.site-preview`, served from `https://appassets.androidplatform.net`) is used only when `SITE_BASE_URL` isn't an https URL, the live page failed in the last 60 s, the main frame gets a network or HTTP error, the page has no `window.renderSite`, or it isn't ready within 10 s. Only a server redirect on the live host is followed; link taps still never navigate. **Supersedes the "Preview" item of #56** (the bundled renderer as the only source); the rest of #56 stands.
- **Why:** the user found that every template change on the web needed a new APK before agents saw it in the app ("should it not be fetched from the web"). The public sites already update with each deploy (now automatic, #118); the preview lagged behind and could show a design that no longer matches the live site.
- **Trade-offs:**
  - The preview shows whatever is deployed, so `renderSite` must keep accepting the payloads older app versions send (WEB_RENDERER.md, Resolution 0), and a broken deploy shows in the app too (the bundled copy only covers a page that fails to load or lacks `renderSite`).
  - Each preview fetches the shell over the network (the Worker sends `max-age=300`, so the WebView cache absorbs most of it); offline or on a very slow network the agent waits up to 10 s before the bundled copy appears, and the next 60 s go straight to it.
  - The draft (the agent's own unsaved content) is only passed into the page through `evaluateJavascript`; the preview shell sends nothing anywhere, so no personal data leaves the phone that didn't before (PRIVACY.md unchanged).
  - The offers slot-mode bridge (OFFERS.md 8b-4b, not built yet) must accept the `SITE_BASE_URL` origin as well as `appassets`.
- **Not in scope:** the template list itself (`Templates.kt`: keys, names, descriptions, sections, default accent) is still generated at build time, so a brand-new template key still needs an app update. Fetching the list and its labels from `/shared/` would need its own decision, because it changes the "definitions in `shared/` + codegen" pattern for the pickers.
- **Alternatives considered:** keep bundling and rebuild for every template change (the problem); live only with no fallback (a blank preview offline, and the app works offline elsewhere, #8); download and cache the renderer files in app storage (more code — versioning, partial downloads — for little gain over the WebView's HTTP cache plus the bundled copy).
- **Status:** Accepted.


### #120 — 2026-10-28 — Choose a template from a grid of auto-scrolling full-page pictures, then preview it
- **Decision:** onboarding step 2 and Website → Appearance → Template show every template in one grid: 2 per row, 2 rows whole and a tenth of the third peeking. Each tile holds the template's **full-page picture**: strips generated from the web templates by `scripts/gen-template-pages.py` and bundled as `composeResources/drawable/template_page_<key>_<n>.webp`.
  - **Auto-scroll:** 4 s after every picture has loaded, the tiles scroll one at a time, in order. Each one jumps back to the top at its end, then the next starts, round and round. Nothing moves when animations are off.
  - **Tapping a tile** opens that template's live preview (`SitePreview`, the agent's own content) with **Make website from this →** in onboarding, or **Use this template →** in Appearance, and **See other templates**.
  - **Replaces** the step-2 intro with its 5 s countdown (#115) and the swipe pager of all 5 templates in step 3 and in Appearance. The step numbers stay the same.
- **Why:** the user asked for it: the template choice screen should show the 5 templates itself, scroll through them automatically, and send the agent to a preview of the chosen one with a way back.
- **Tiles as pictures, not live renders (the user's choice):** five live WebViews at once would be slow to load and heavy. On low-memory phones the WebView's renderer process can die, and it already did with two previews alive in the pager (`onRenderProcessGone`). So the tiles stay blank.
  - Pictures are light: about 870 KB in the APK and about 57 MB of bitmaps, freed when the grid closes. They also scroll smoothly.
  - They are cut into strips of 2048 px or less, because a single 8600 px bitmap can exceed GPU texture limits.
- **Trade-offs:**
  - The tiles show a sample agent, not the agent's name. The preview after the tap shows the agent's own content.
  - The pictures ship in the app, so a template's new look reaches the grid only with an app build (the preview itself is live, #119). They must be regenerated with `preview.webp` (WEB_RENDERER.md, Template page strips), and `TemplateGridTest` fails if a template has none.
  - The loop is an endless animation, so it runs under Compose's `InfiniteAnimationPolicy` (UI tests stop it), and it is skipped under "remove animations", where every `animateTo` would end at once.
- **Alternatives considered:**
  - Live mini-websites in the tiles (memory and crashes, above).
  - One picture per template from the web at runtime (needs network, more code).
  - Keeping the pager with an auto-advance (not what was asked).
- **Status:** Accepted. Supersedes the step-2 intro and the template pager of #115 (the families stay removed).

### #121 — 2026-10-28 — Important rollouts take existing agents through a full-screen "What's new" walkthrough; items are versioned
- **Decision:**
  - **Versioned items:** each "What's new" item (`WhatsNewItem`) records the release that brought it (`since`), and `WhatsNew.VERSION` is the highest one. Agents see only the items newer than the version they've seen, so old rows never repeat.
  - **Important items:** if any new item is important, all the new items show as a **full-screen walkthrough on the first Home of the app run**, before anything else. It has one page per item with **Show me** (opens it), **Next**, and **Not now** on the last page. Smaller items keep the dismissible Home sheet (#103).
  - **What counts as important:** it changes the agent's public website, needs them to act or decide, adds a required step, or moves something they use.
  - **Seen:** any way out marks everything seen. New sign-ups never see it, because finishing onboarding marks it seen.
  - **The standing rule (CLAUDE.md, Rolling out to existing users):** every change existing agents should know about ships with an item, and the PRODUCT.md requirement says whether it is important and what Show me opens.
  - **First use:** "5 new website designs" (`NewTemplates`, since 2, important). Show me opens the template grid (#120).
- **Why:** the user asked how existing members would learn about the new templates, and asked for a standing rule so important features always reach existing users. The redesign (#117) already changed every existing site's look without telling its agent, and onboarding only reaches new sign-ups.
  - The #103 sheet was too easy to dismiss.
  - Its single `VERSION` couldn't add rows without re-showing the old ones.
- **Options the user chose between:** full-screen walkthrough (chosen), must act before Home (blocks an agent who just wants a due date), the Home sheet only (easy to ignore). For this rollout: Show me opens the grid directly (chosen) rather than first showing their own site in the new look.
- **Trade-offs:**
  - Show me ends the walkthrough, so later pages of the same run aren't shown. Pages are ordered important first so the one that matters is always seen.
  - A fresh existing agent who never saw version 1 gets 4 pages.
  - The flow UI tests that reach Home with a site now mark What's new seen first (`whatsNewSeen()`).
- **Status:** Accepted. Supersedes #103's single-version scheme (the sheet itself stays for small items).

### #122 — 2026-10-28 — Agent sites are multi-page: Home, Insurance (+ a page per plan type), About me, Contact; inner pages are shared
- **Decision:** every site has **Home** (the template's own hero and story, with teasers that link on), **Insurance** (`/insurance`: plan-type cards and the current offers), **one page per plan type** (`/insurance/<interest key>`: the plan picture, the agent's description, "Ask about this plan", that type's offers or a "no offer right now" note, other plans), **About me** (`/about`) and **Contact** (`/contact`: ways to reach, the enquiry form, FAQ, "For my customers"). Customer login stays in the menu.
  - **URLs:** `/a/<slug>/<page>` on the primary host, `/<page>` on a custom domain (added to `run_worker_first`), `&page=<page>` on the local `?s=` fallback / mock, `#p=<page>` in the in-app preview (no network; the stored draft re-renders). A trailing slash 301s to the bare form; unknown paths 404 without an RPC; a plan type the agent doesn't offer 404s; a page left empty is dropped from the menu and 404s.
  - **The page model is a definition in `shared/site-pages.json`** (web-only, so the Kotlin codegen is untouched): keys, paths, label/title/lead keys, which sections go on which page, Home's teasers, the help band. `sectionOrder` and `hidden` still apply within a page.
  - **Shared inner pages:** Insurance, plan, About and Contact are one markup (`web/sections/pages.js`, `web/styles/pages.css`, loaded only on inner pages) that each template skins in its own `style.css`; only Home is template-specific. Overrides only where structure needs them: minimal's profile-card column (the menu lives in the card), spotlight's dark header band with the trust bar, guide's need groups beside the sticky index on Insurance.
  - **SEO:** each page is server-rendered with its own title ("{page} — {name}"), description, canonical and one `<h1>`; the breadcrumb is a `<p class="crumb">` (the call bar stays the only `<nav>`); the primary host's `/sitemap.xml` is an index of per-site sitemaps (`/a/<slug>/sitemap.xml`, the pages that exist); FAQPage JSON-LD moves to Contact.
  - **Enquiries across pages:** chips, offer and plan buttons link to `contact?interest=<key>#enq`; `hydrate.js` / `client.js` preselect it only for an active catalog interest the form offers.
- **Why:** user request (PRODUCT.md, 2026-10-28): "a real site has multiple pages like About me, Insurance", pointing at an agent's Google Site; and "consider sharing pages among the sites where applicable". Page wireframes were drawn on the shared design canvas first.
- **Trade-offs:** more CSS on inner pages (first paint 39.6–42.4 KB raw, 10.3–10.9 KB gzip; caps 44 KB / 11 KB) and on the client-render path (cap 124 → 170 KB, it counts every sections/ file); Home's first paint stays near its size (cap 34 → 36 KB raw). Plan pages ignore `content.hidden` for services (they come from the services content and offers). A rollout What's new page (`MultiPageWebsite`, since 3, important; Show me → the website preview) tells existing agents.
- **Alternatives considered:** five per-template versions of every page (rejected: five times the code, CSS and tests for little visible gain); keeping one page with anchor links (rejected: not what was asked, and one URL per site is weak for search); query-string pages on the live site (rejected: worse URLs).
- **Status:** Accepted.

### #123 — 2026-10-28 — The sticky Call / WhatsApp bar appears on scroll up or past 70% of the page
- **Decision:** on phones the bar (`web/callbar.js`, `nextVisible`) is hidden when a page opens, slides in once the visitor has scrolled 70% of the page (and stays while they are past it) or whenever they scroll up (10 px or more), and slides away when they scroll down again below 70%. A page too short to scroll shows it. Hidden means `translateY(100%)` and `visibility:hidden` (links not focusable, `aria-hidden`); `.tpl.has-bar` still reserves its space (no layout shift); no slide under reduced motion. Without JavaScript the bar is visible as before. The preview re-wires it on every page change without stacking listeners.
- **Why:** user request (PRODUCT.md, 2026-10-28): "make them appear on scrolling 70% of the entire web page or scrolling up and remove if the user scrolled down after scrolling up".
- **Interpretation:** past 70% the bar stays even when scrolling down, so it is there at the end of the page where visitors decide.
- **Supersedes:** the always-visible sticky bar of the earlier templates (WEB_RENDERER.md, Sticky Call / WhatsApp bar).
- **Status:** Accepted.

### #124 — 2026-10-28 — Photos open an "Adjust your photo" editor that shows how the website will cut them
- **Decision:** picking a photo (hero photo, onboarding profile photo) opens a full-screen editor before anything is uploaded (`feature/website/ui/PhotoEditor.kt`, `rememberPhotoEditor`; the surface and previews are `PhotoCropArea` / `PhotoShapePreview` in `core/designsystem`).
  - **Crop model:** `ImageCrop(zoom 1..4, centerX, centerY)` as fractions of the upright photo; `ImageGeometry.crop` turns it into pixels (the centred crop divided by the zoom, kept inside the photo), and `ImageProcessor.prepare(image, aspect, crop)` applies it (null = the old centred crop). The aspect stays the schema's (`1:1`), so the upload, Storage and the renderer are unchanged.
  - **The focus:** the frame is the upload; outside it is dimmed. Since the site cuts more from the square (a circle in most templates, 4:5 on About me, 4:3 on Fresh's cover, all at `object-position: 50% 25%`), the image field lists those shapes in `shared/site-schema.json` as `shownAs` (`aspect`, `round`, `focusY`, `labelKey`), and the editor previews each one live. The onboarding profile photo takes the hero photo's aspect and shapes from the schema (it was a hard-coded `"1:1"`).
  - **Orientation and memory:** the editor works on an upright preview (EXIF applied, longer side ≤ 1600 px) decoded by the same processor, so the crop fractions mean the same thing at upload. A zoomed crop decodes more pixels to stay sharp, capped at 12 MP (`MAX_DECODE_PIXELS`), so a 4× crop of a 50 MP photo can't run out of memory (its output is then smaller than 1024 px).
- **Why:** user request (PRODUCT.md, 2026-10-28). A centred square crop cuts off a face that isn't in the middle, and the agent couldn't see that until the site was live.
- **Alternatives considered:** a platform crop library (rejected: not KMP, and it can't show the website's shapes); a free crop rectangle (rejected: the schema fixes the aspect, and a fixed frame with pan/zoom is simpler on a phone); a draggable focus point only (rejected: no zoom, and the upload would still be a centred square).
- **Rollout:** small (#121): it doesn't change anyone's live site and needs no action. `WhatsNewItem.PhotoEditor`, since 4, Home sheet, Show me → the Hero section editor.
- **Status:** Accepted.

### #125 — 2026-10-29 — The Worker takes its Supabase settings from Cloudflare secrets and serves /config.js itself
- **Decision:** `SUPABASE_URL` and `SUPABASE_ANON_KEY` are Worker secrets (`wrangler secret put`), and `SITE_BASE_URL` is a plain Worker var in `web/wrangler.jsonc`. The Worker merges them over the built `dist/config.js` (`mergeConfig` in `web/manifest.js`: a non-blank env value wins, `BUILD_TIME` is always the build's). It also answers `/config.js` (now in `run_worker_first`, `no-store`) from the same merged values, so the browser and the Worker always agree. The built `config.js` stays as the fallback (local `wrangler dev`, tests).
- **Why:** outage from 2026-10-28 21:19 IST (user report: "something went wrong" on every site). The CI deploy (#118) built `config.js` with an empty `SUPABASE_URL`/`SUPABASE_ANON_KEY` and `SITE_BASE_URL=http://localhost:8080`, because the user had added the Supabase values as Cloudflare Worker secrets, not as GitHub repo secrets. The Worker logged `config.js has no SUPABASE_URL` on every page and served the shell, which then failed in the browser too. The user chose to make the Cloudflare secrets work instead of adding them to GitHub.
- **Trade-offs:** `/config.js` now invokes the Worker on each page view (tiny, uncached as before). The `wrangler.jsonc` `SITE_BASE_URL` wins over the build, so local `wrangler dev` needs `SITE_BASE_URL=http://localhost:8787` in `web/.dev.vars` (git-ignored). The anon key was already public in `config.js`; keeping it as a secret only keeps it out of the repo and CI.
- **Alternatives considered:** adding the two GitHub repo secrets (works, but the user had already set them in Cloudflare, and a missed secret silently ships an empty build again); failing the build on an empty `SUPABASE_URL` (not done: with the env merge an empty build is no longer fatal).
- **Status:** Accepted. Amends #118: the GitHub secrets `SUPABASE_URL` / `SUPABASE_ANON_KEY` are now optional.

### #126 — 2026-10-29 — Skeletons shaped like the coming page replace "Loading…" on the website shell and in the admin panel
- **Decision:** the renderer's static shell (`web/index.html`) shows a template-neutral page skeleton (top bar, hero with photo, headline and button, a section of three cards) while it waits, styled by a new `web/styles/shell.css` that only the shell links. The admin panel shows its own layout as a skeleton while the session and access are checked, and skeleton rows or cards in each list until its first load finishes (`admin/src/Skeleton.tsx`). Both pulse like the app's `SkeletonBlock` (opacity 1 → 0.5, 1200 ms) and stay still under reduced motion; screen readers hear one visually hidden "Loading…".
- **Why:** user request (PRODUCT.md, 2026-10-29). The in-app template previews sit on the shell for up to the 10 s live-renderer timeout plus the template's files, and a bare "Loading…" on white looked broken. The admin lists also said "Nothing here." before their data arrived, which was wrong.
- **Trade-offs:** the shell's skeleton can't match the chosen template (the shell doesn't know it yet), so it draws the shape all five share. A separate stylesheet costs the shell one more same-origin request (2 KB), but server-rendered pages, which never show a skeleton, keep their first-paint CSS unchanged.
- **Alternatives considered:** a skeleton per template (rejected: the template isn't known until the site JSON and the template's files arrive, which is the wait itself); putting the rules in `base.css` (rejected: every SSR page would download them); a Compose overlay over the preview WebView (not done: the shell's skeleton covers the long part of the wait, and the WebView is blank only until the first HTML arrives).
- **Rollout:** none (#121): nothing on an agent's live site changes and no action is needed.
- **Status:** Accepted.

### #127 — 2026-10-29 — The "What's new" seen mark is kept on the agent's account, not only on the phone
- **Decision:** `profiles.whats_new_seen` (integer, default 0) holds the highest What's new version the agent has seen; the RPC `mark_whats_new_seen(seen)` raises it and never lowers it (authenticated, no `can_write()`). The app shows the items newer than the **higher** of the device's `whats_new_seen` and the account's. Dismissing still marks the device at once; `HomeViewModel` then raises the account to the device's mark whenever the device's is higher (a failure is retried on the next app run). Sign-out, and a sign-in as a different user, clear the device's mark, since the account keeps each agent's own.
- **Why:** user report (PRODUCT.md, 2026-10-29): after a new version, an agent was shown the previous version's items again. The mark lived only in DataStore, and app data isn't backed up (`allowBackup="false"`), so a reinstall, "Clear storage" or a new phone read it as 0. Onboarding then closes for an existing site without marking anything (`closeForExistingSite`), and every item since version 1 came back.
- **Interpretation:** marks made before this change exist only on phones, so Home copies a higher device mark up to the account on its first online run. That way today's agents are covered before they ever reinstall.
- **Trade-offs:** one extra RPC per dismissal, and once per device for the copy-up. A mark made offline that never reaches the account (offline, then uninstalled) is lost, as before. An agent whose site predates an item but who reinstalls before ever dismissing it still sees it, which is correct.
- **Alternatives considered:** turning Android backup on for the DataStore file (rejected: no help on a new phone or iOS, and the backup exclusion keeps session tokens and the cache off Google's servers on purpose); inferring "seen" from when the site was created (rejected: it can't tell a dismissed item from an unseen one); a plain `profiles` update from the app (rejected: it needs `can_write()`, so a lapsed agent couldn't dismiss, and an older build could lower the value).
- **Rollout:** none (#121): nothing changes for the agent except that old items stop repeating.
- **Status:** Accepted. Amends #103 and #121 ("signing out doesn't reset it": the account now keeps it instead).

### #128 — 2026-10-31 — The profile photo is edited from Settings → Profile and is the website's hero photo too
- **Decision:** Settings → Profile gets an Add/Change photo button that runs the existing picker → Adjust your photo editor (#124) → `ImageUploader` pipeline on the hero photo's field spec (`SiteSchema` hero.photo: aspect and shapes). `ProfilePhotoViewModel` uploads it as `profile-photo`, saves `profiles.photo_url` (a normal `ProfileInput` update carrying the other fields as they were), then, when the agent has a site, writes the same URL to the draft's `hero.photo` (only that key; the rest of hero is untouched). The live site changes at the next Publish, like any hero edit.
- **Why:** user request. Onboarding already saves one photo that the site shows as the hero photo (#116), but afterwards the only way to change it was the Hero section, and the profile photo (Home avatar fallback, preview payload) could drift from the site's.
- **Trade-offs:** an upload that succeeds but fails to save leaves an orphan file in `site-media` (as before, for any abandoned pick). If the profile saves but the draft write fails, the error shows and the agent can retry; the avatar already follows the hero photo, so nothing shows two photos for long. No Remove button: removing stays in the Hero section.
- **Alternatives considered:** profile photo only, without touching the site (rejected: two different photos for one agent); a new Storage path or RPC (rejected: the existing owner-only upload and `profiles` update already cover it, so no migration).
- **Rollout:** small What's new item `ProfilePhoto` (version 5, Home sheet), Show me → Settings → Profile (#121).
- **Status:** Accepted.
