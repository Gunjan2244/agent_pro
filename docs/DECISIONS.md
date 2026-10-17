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
