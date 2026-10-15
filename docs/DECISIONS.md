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
- **Status:** Accepted. Registrar API coverage still needs confirming (see DOMAINS.md).

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
