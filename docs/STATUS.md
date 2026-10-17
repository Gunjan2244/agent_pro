# Status — read this first

_Last updated: 2026-10-17 (Add to home screen). This file was rebuilt on that date after an orchestrator script accidentally emptied it; the content reflects the full session record. See CHANGELOG 2026-10-16._

## Current state
- **How the work is run:** an orchestrator plus parallel sub-agents (DECISIONS #24, refined by #55).
  - Sonnet handles well-specified work, verification, docs and UI screens; Opus only handles complex or security-critical work.
  - Only the orchestrator edits CHANGELOG, DECISIONS and STATUS.
  - Only one Gradle build runs at a time. Agents queue on `flock <scratchpad>/gradle.lock`, because the machine has about 7 GB of RAM.
- **Phase 1 (shared definitions and codegen):** done and independently verified on 2026-10-15.
  - `shared/` holds 6 JSON files and 54+ icons.
  - The `mobile/` KMP scaffold uses Gradle 9.7.1, AGP 9.3.3, Kotlin 2.4.20 and Compose Multiplatform 1.12.1, with compileSdk 37 and targetSdk 36.
  - `build-logic` provides `generateShared` and `verifyShared`.
- **Phase 2 (design system):** done and verified on 2026-10-15. The user accepted the provisional choices (DECISIONS #38, #39, #41).
- **Phase 3 (backend):** **live on the Supabase project `AGENT_PRO`** (Mumbai) since 2026-10-16.
  - All 8 migrations are applied. `rpcs` was run by the user in the SQL Editor, because the connector auto-declines it as destructive.
  - The functions and grants are verified.
  - Advisors: no errors; 11 WARNs accepted by design (BACKEND.md, Advisors).
  - Local tests: 226/226 pass on Postgres 15 and on Postgres 17.
- **Phase 4 (web renderer):** built and tested locally (29 checks pass), with preview images, and Lighthouse on a local mock: Performance 90, Accessibility 100, SEO 100. **It isn't deployed.**
- **Phase 5 (app foundation):** done and verified on 2026-10-16 (156 tests, 84 screenshots, re-run by the orchestrator).
- **Phases 6, 7 and 8 and the Phase 5 follow-up:** built and **verified on 2026-10-17** in one combined run: 62 build-logic and 231 app tests, 0 failures, 0 screenshot diffs across 160 baselines, lint 0 errors. Live and device checks are still pending (TESTING.md End to end).
- **P1 privacy foundations started (2026-10-17, user request):**
  - **Done:** part of P1-3, self-hosted fonts, a CSP without Google, and the "Interested in" blank "Select…" (2026-10-17; 43 web checks pass). It isn't on the preview yet; that needs a redeploy, with the user's approval.
  - **P1-1 done and verified** (2026-10-17, DECISIONS #61): 83 build-logic tests; SQL 226/226 with the new `catalog_sync`.
  - **P1-3 web done and verified** (2026-10-17, DECISIONS #62): 73 web checks; a `RELEASE=1` gate. Not deployed; it must go out together with P1-2.
  - **P1-2 backend done and verified locally** (2026-10-17, DECISIONS #63): SQL 323/323 on Postgres 17. **Not applied.**
  - **P1-4 app done and verified** (2026-10-17, DECISIONS #64): 253 app tests, 0 screenshot diffs.
  - **P1 is released (2026-10-17):** the privacy migrations are applied to `AGENT_PRO` (pasted in the SQL Editor by the user), and the preview is redeployed (version `c588c5c8`). A live browser check passed, and the consent row is stored correctly.
  - **Still pending:**
    - the Supabase advisors (the connector is disconnected; reconnect it in claude.ai);
    - before a public launch: the grievance officer's details and the email provider (release blockers), plus counsel review.
  - **Small follow-up:** make `scripts/test-web.mjs` build into a temp directory, so it can't overwrite `web/dist` (Sonnet).
  - **Release plan (needs the user's approval):**
    1. Apply `20261016220600_privacy.sql` to `AGENT_PRO`. The connector will probably auto-decline it because of the `drop function` overload, so the user may need to paste it in the SQL Editor.
    2. Immediately after, apply `20261016220836_catalog_sync.sql`.
    3. Redeploy the web preview with P1-3, because the old web form sends no consent.
    4. Run the advisors (expect 5 anon and 9 authenticated SECURITY DEFINER WARNs, accepted by design).
  - **The fixed interface between them:**
    - `submit_enquiry(…, consent jsonb)`, where consent is `{notice_key, version, hash}`;
    - `submit_data_request(slug, host, name, phone, kind, message, honeypot)`;
    - `record_consent(notice_key, version, hash, customer_id)`;
    - sign-up consent passed in `raw_user_meta_data.consent`.
  - **Afterwards:** applying to `AGENT_PRO` and redeploying the preview must happen **together**, because the new `submit_enquiry` needs the new web form. Both need the user's approval.
  - Next, after P1-1 lands: P1-2 backend (Opus), the rest of P1-3 web (Sonnet) and P1-4 app (Sonnet), in parallel.
  - If this session ended before they reported, check `git status` and the CHANGELOG and resume from what's on disk. The scratchpad (with the Gradle lock file) may have been wiped; recreate it.
- **Live demo sign-ups on `AGENT_PRO` (2026-10-17, at the user's request: test data only, without the app):**
  - Using the user's email: `akash2244rocks@gmail.com` (persona Ramesh Kumar, classic, EN, slug `ramesh-kumar-pune`) and `akash2244rocks+priya@gmail.com` (Priya Sharma, modern, EN, `priya-sharma-jaipur`). Both are **confirmed and published, and render from the live database** (verified 2026-10-17).
  - `akash2244rocks+sunita@gmail.com` (सुनीता वर्मा, minimal, HI, `sunita-verma-lucknow`) was refused by Supabase's built-in email rate limit (`over_email_send_rate_limit`); retry later.
  - **To finish the third:** retry its sign-up after the email rate limit resets, then run `python3 -I <scratchpad>/demo/demo_signups.py <repo> finish`. It signs in, fills the profile and site, publishes, and checks `get_public_site`.
  - The site is viewed locally from `web/dist`, built against `AGENT_PRO` and served at `http://localhost:8080/?s=<slug>`.
  - **The demo passwords were lost** when the session scratchpad was wiped (2026-10-17 02:37). With the user's approval, the two demo accounts were given new passwords on 2026-10-17 (bcrypt hashes computed locally; only the hashes went to the database), and both sign in. **Test credentials live in `~/.config/agentpro-demo/accounts.json`** (mode 600, outside the repo); the scratchpad is ephemeral. A third sign-up for `akash2244everyone@gmail.com` was refused by the email rate limit; retry after it resets.
  - **Before real users:** delete these demo accounts.
  - **The built-in email limit (about 2 per hour) shows a transactional email provider is needed** before launch (approved, DECISIONS #54).
- **New scope designed (2026-10-16; the docs match the user's decisions):** a multi-company platform with admin-managed offers and a web admin panel (`docs/OFFERS.md`, DECISIONS #47, #49–#53), and DPDP compliance (`docs/PRIVACY.md`, DECISIONS #48, #54). **Nothing of either is built yet.**
- **App icon (2026-10-17):** the adaptive launcher icon is in (DESIGN_SYSTEM.md, App icon; DECISIONS #66). It's an "A" with a rising chart line, on primary blue. The Play Store 512 and iOS 1024 PNGs are in `design/app-icon/`. The `-install` test APK (10:40) includes it.
- **Nothing is committed yet.** On 2026-10-15 the user said not to commit yet; ask again before committing.

- **Add to home screen (2026-10-17, DECISIONS #65):** built and tested locally, **not deployed**.
  - Agent sites are installable web apps: a manifest from a manifest-only Worker, accent PNG icons, a customer button with an agent toggle, and an app menu item, Website → ⋮ → Add to home screen.
  - **To make it live (each step needs the user's approval):**
    1. Redeploy the Cloudflare preview: run the tests first, then the real build, check `config.js`, then deploy. The deploy now uploads `worker.js` too.
    2. Optionally apply `20261017050728_catalog_sync.sql`. It only adds the toggle to `site_schema()`, and nothing breaks without it.
  - Then run the phone checks in TESTING.md (Add to home screen on a real phone).
  - **Until the preview is redeployed,** the app's new menu item opens the current preview, which has no install sheet yet.
  - **Follow-up:** use the agent's photo as the icon. It needs a PNG copy uploaded by the app, because WebP isn't confirmed for Android's WebAPK.
- **Test APK with Add to home screen (2026-10-17 10:40):** `~/AgentPRO-test/AgentPRO-test-2026-10-17-install.apk` (debug, `AGENT_PRO` + preview; includes the new launcher icon). It supersedes the 10:04 build.
- **Test APK (2026-10-17 10:04):** a debug build pointed at `AGENT_PRO` and the preview site (`SITE_BASE_URL` set in the git-ignored `local.properties`). A copy is at `~/AgentPRO-test/AgentPRO-test-2026-10-17.apk`. It's waiting for the user to install it on a phone (USB with adb, or sideload). It's for testing only, not a release.

## Next steps
1. **P1 privacy foundations** (PRIVACY.md), required before any public deploy. Work packages, Sonnet by default (DECISIONS #55):
   - P1-1 notices and hashing in `shared/` and codegen (Opus, codegen);
   - P1-2 the backend tables `consents`, `data_requests` and `audit_log`, and `payments.raw` minimisation (Opus, security);
   - P1-3 web: consent checkbox, privacy page, self-hosted fonts, policy pages (Sonnet);
   - P1-4 app: sign-up consent and the customer attestation (Sonnet).
2. **Phase 8b companies and offers, and 8c admin panel** (OFFERS.md); split per the roadmap, mostly Sonnet.
3. **Ask the user before:**
   - creating a test user on `AGENT_PRO` for the live test (TESTING.md End to end #1); the redirect URL `agentpro://auth-callback**` must be added first;
   - the Cloudflare `*.workers.dev` preview deploy with demo data (approved in principle; needs the user's `npx wrangler@4.151.1 login`, then a go-ahead);
   - committing.
4. **Get from the user:** the grievance officer's details, and approval of Supabase Pro (HIGH PRIORITY).
5. **Small UI fix:** the "Half-yearly" label is cut off in the premium-mode segmented control (Sonnet).
6. **Phases 9 and 10** (payments, domains) are blocked by the ❗ questions below.

## Roadmap
Tick items as they're done and add the date.

- [x] **1. Shared definitions and codegen.** Done and independently verified on 2026-10-15
  - [x] `shared/catalog.json` (including `pricing` and `domainStatuses`), `site-schema.json`, `design-tokens.json`, `templates/index.json`, `i18n/en.json`, `i18n/hi.json`
  - [x] `mobile/` KMP scaffold (`composeApp` with `commonMain` and an `iosMain` stub; the Android target is configured in AGP's `android {}` block), `androidApp`, version catalog
  - [x] `mobile/build-logic`: `generateShared` and `verifyShared`
- [x] **2. Design system:** theme (light/dark), components (DESIGN_SYSTEM.md), `FormRenderer`, debug gallery. Done and verified on 2026-10-15 (95 tests, 60 screenshot baselines); the user accepted DECISIONS #38, #39 and #41 for now
- [x] **3. Backend core.** Live on `AGENT_PRO` (2026-10-16)
  - [x] Supabase project: the user's `AGENT_PRO` (Mumbai, free plan, ₹0) (2026-10-16)
  - [x] Migrations: tables, RLS, RPCs, storage, realtime. Passing 226 local checks on Postgres 15 and 17 (2026-10-15/16)
  - [x] Applied to `AGENT_PRO` and verified on 2026-10-16. Advisors: no errors; the 11 WARNs are accepted by design (BACKEND.md)
- [ ] **4. Web renderer:** core, 3 templates, enquiry form, host resolution, `build-web.sh`, Cloudflare `wrangler.jsonc` (DECISIONS #59). Test locally; deploying needs approval
  - [x] Cloudflare config: `web/wrangler.jsonc` (Worker `agentpro-sites`, static assets from `web/dist`, SPA fallback), and `web/_headers` (CSP and security/cache headers). `wrangler@4.151.1 deploy --dry-run` passes; 31 web checks pass (2026-10-17)
  - [x] **Preview deployed on 2026-10-17** (the user said "deploy"): **https://agentpro-sites.sledjesoftwares.workers.dev**, demo data only. `/a/<slug>` routing, the CSP and security headers, and the cache headers are verified, and both demo sites render live. **Not for public sharing until P1**
  - [x] Built and tested locally (2026-10-15)
  - [x] Preview images, and Lighthouse on a local mock (Performance 90, Accessibility 100, SEO 100) (2026-10-15)
  - [x] Real-backend render: two demo sign-ups render from `AGENT_PRO` locally (2026-10-17)
  - [x] **Full live check of website creation passed on 2026-10-17** (11 areas, A–K; CHANGELOG). Minor UX question: the enquiry form's "Interested in" defaults to "Other"
  - [ ] Host resolution and Lighthouse on the Cloudflare preview
  - [ ] **P1 privacy items are required before the first public deploy** (consent checkbox, privacy page, self-hosted fonts)
- [x] **5. App foundation:** auth, onboarding, Room/`SyncEngine`, `NetworkMonitor`, `FeatureRegistry`, nav (4 tabs). Verified on 2026-10-16 (156 tests, 84 screenshots)
  - [x] Follow-up: Set new password screen, AppCompat locales, Keystore-encrypted session (DECISIONS #44–#46, #57). Verified on 2026-10-17
  - [ ] Live test against `AGENT_PRO` (TESTING.md End to end #1). It needs a test user, so ask first, and add **`agentpro://auth-callback**`** (with the wildcard) to Supabase Auth's allowed redirect URLs
- [x] **6. Enquiries** (list, detail, add, convert). Verified on 2026-10-17
- [x] **7. Customers, policies and dues** (Customers tab with the Customers | Dues segment, record-payment sheet). Verified on 2026-10-17
- [x] **8. Website hub** (Content, Appearance, Link, Stats, Preview, Publish, QR). Verified on 2026-10-17
- [ ] **8b. Companies and offers** (OFFERS.md), after Phase 8
  - [ ] 8b-1 `shared/` and codegen
  - [ ] Then in parallel: 8b-2 backend, 8b-3 web slots and bridge, 8b-4 app `feature/offers` (including the admin-verified membership states)
  - [ ] 8b-5 verifier
- [ ] **8c. Admin panel** (OFFERS.md): 8c-1 `admin` Edge Function, 8c-2 app shell, auth and MFA, 8c-3 CRUD screens including membership requests, 8c-4 deploy (needs the user's approval)
- [x] **P1. Privacy foundations** (PRIVACY.md). **Released on 2026-10-17** to `AGENT_PRO` and the preview. Advisors pending; the release blockers (grievance officer, email provider) gate a public launch, **before the renderer's first public deploy**: notices, consent and hashing in `shared/`; the `consents`, `data_requests` and `audit_log` tables and the `payments.raw` minimisation; the web consent checkbox, privacy page, self-hosted fonts and policy pages; app sign-up consent and the customer attestation
- [ ] **9. Service plan and payments** (BILLING.md)
  - [x] `subscriptions`, `orders`, `payments`, `entitlement()`/`can_write()`, RLS write gating, lapse handling in the public RPCs. Done early, in the Phase 3 migrations
  - [ ] Edge Functions `billing` and `razorpay-webhook`, plus `cron` (plan reminders, stale orders)
  - [ ] Secrets (the user enters them), Razorpay test-mode webhook
  - [ ] App `feature/billing`: `EntitlementRepository`, `FeatureGate`, read-only mode, Plan & billing, Checkout, Payment result, Receipts, Plan ended screen
  - [ ] Test-mode end-to-end run (TESTING.md)
- [ ] **10. Custom domains** (DOMAINS.md)
  - [ ] Choose the registrar ❗ and confirm the fee and TLDs ❗
  - [ ] Edge Function `domains` (live quotes into `domain_quotes`), registrar and Cloudflare for SaaS (custom hostname) adapters, domain fulfilment, `cron` DNS/SSL/renewal
  - [ ] App `feature/domains`: search, progress, details (renew), connect
  - [ ] Test-mode end-to-end run: purchase, refund on failure, renewal, expiry
- [ ] **11. Home and Insights** (including the plan row)
- [ ] **12. Notifications and workers** (enquiries, follow-ups, digest, plan and domain reminders)
- [ ] **13. Settings and export** (including Plan & billing and Delete account; the account-deletion Edge Function, DECISIONS #32)
- [ ] **P2. Privacy rights and retention** (PRIVACY.md), with Phase 13: erase and summary RPCs, nominations, retention jobs (lapsed accounts deleted after 12 months; enquiry retention is left to the agent), incidents; app `feature/privacy`; the admin Privacy area; draft policy, terms and processing addendum for counsel (EN and HI)
- [ ] **14. Hindi, accessibility and screenshot passes, end-to-end run**
- [ ] **iOS phase**
  - [ ] `iosApp` Xcode project; real `iosMain` implementations for every `core/platform` seam
  - [ ] APNs/FCM push, because background polling on iOS is unreliable
  - [ ] StoreKit or Apple's rules for selling the plan
  - [ ] App Store review. This needs a Mac with Xcode
- [ ] **Later:** auto-renewal for the plan and domains, FCM push on Android, server-side rendering

## Open questions / blockers
- **HIGH PRIORITY:** approve **Supabase Pro (about $25/month) for daily backups** before launch. The free plan has no backups and pauses after about a week without activity (DECISIONS #54). **Launch gate (PRIVACY.md, Backup risk):** test data only, with no real agents onboarded and no real sites published, until it's approved.
- **Grievance officer:** name, email and phone, to publish on the privacy pages (PRIVACY.md). This is the only one of the 8 offers and privacy decisions still open.
- **Auth emails at 100+ per hour (DECISIONS #60):** blocked on the platform domain. Then: a transactional provider (Brevo or Amazon SES) with SPF/DKIM/DMARC, connected as custom SMTP in Supabase, and Auth → Rate Limits → emails set to 100 or more per hour. Until then, about 2 emails per hour.
- **To set up before launch:**
  - a transactional email provider (approved; pick one, e.g. Resend or Postmark free tier);
  - self-hosted fonts (approved, part of P1).
- **Counsel review** of PRIVACY.md, of the IRDAI advertising rules for agents' offers, and of the record-keeping and tax retention periods.
- **DPDP dates:** Consent Manager registration from 13 Nov 2026; the main duties from 13 May 2027.
- **Phase 8 confirmations** from the `website-hub` report:
  - the automatic centred photo crop;
  - pinned sections stay first and last when reordering;
  - Stats periods of 7 and 30 days;
  - Appearance changes go live immediately;
  - the template picker shows a live render;
  - Share sends just the URL;
  - an old photo stays in Storage until cleanup is built.
- **Supabase Auth:** add **`agentpro://auth-callback**`** (with the wildcard) to the allowed redirect URLs (Dashboard → Authentication → URL Configuration) before the live test.
- ❗ **Registrar:** ResellerClub or another (Vercel Domains is no longer an option after DECISIONS #59, and Cloudflare Registrar can't be resold). It must support live pricing, register, renew and DNS through an API for `.in`, `.co.in` and `.com`.
- ❗ **Domain service fee:** confirm the ₹99 (incl. GST) default, charged on both purchase and renewal.
- ❗ **Google Play payments policy:** selling the ₹499/₹299 plan in the app most likely needs Play Billing, or India's user-choice/alternative billing. Domain sales need checking too. Resolve before release (BILLING.md, Compliance).
- **GST:** the GSTIN, and whether receipts must be tax invoices.
- **Accounts:** Razorpay account and KYC; a Cloudflare account (the free plan is enough for now).
- **Platform domain:** not bought yet. It's needed for free links (`<domain>/a/<slug>`) and for Cloudflare for SaaS. Buying it needs the user's explicit approval.
- **Content:** domain terms, privacy policy and terms pages (drafts are part of P2).
- **Provisional values in `shared/` and in the UI copy:** the user should review them before release. They're marked "(provisional)" in SHARED_DEFINITIONS.md and listed in DECISIONS #41, #43, #53.
- **Small follow-ups:**
  - the site footer's "Independent insurance advisor" may be wrong for agents tied to one insurer, so review the copy;
  - `profiles.branch` is unused, so remove it or justify it (PRIVACY.md);
  - `web/` still has form limits that mirror `submit_enquiry` and have no source in `shared/`.

## Environment
| Item | State |
|---|---|
| Java | OpenJDK 21 installed |
| Gradle | System Gradle 4.4.1 is unusable. The project uses the wrapper (Gradle 9.7.1) in `mobile/`. `gradle.properties`: `-Xmx2g -XX:MaxMetaspaceSize=768m`, 2 workers |
| Android SDK | `~/Android/Sdk` (installs approved by the user on 2026-10-15/16): cmdline-tools (latest), platform-tools, `platforms;android-35`, `android-36` and `android-37.0`, and `build-tools;35.0.0` and `36.0.0`. AGP downloaded `36.0.0` by itself; the user kept it, and `android.builder.sdkDownload=false` now blocks automatic downloads. No emulator system image yet |
| Mac / Xcode | Not needed until the iOS phase. This machine is Linux, so iOS targets can't be built here |
| Docker | Available. The local SQL tests (`bash supabase/tests/run.sh`) run in a throwaway container capped at 384 MB: `postgres:15` by default, `postgres:17` via `PG_IMAGE=postgres:17` (pulled 2026-10-16 with approval). Both pass 226/226 |
| Supabase | **Project `AGENT_PRO`**, ref `rpqatijolcajehshoikc`, URL `https://rpqatijolcajehshoikc.supabase.co`, org **letssee** (free plan), **ap-south-1 (Mumbai)**, Postgres 17.11, created by the user on 2026-10-16. **10 migrations applied** (including `20261016220600_privacy.sql` and `20261016220836_catalog_sync.sql`, pasted in the SQL Editor by the user on 2026-10-17). Earlier batch: 7 through the connector, and `rpcs` by the user in the SQL Editor. **Verified:** functions and grants, 14 tables with RLS, 30+4 policies, 10 catalog CHECKs, realtime publication, `app_config` rows. **Advisors:** no errors; 11 WARNs accepted. The project also has Supabase's `rls_auto_enable` event trigger, whose API execute permission is revoked. The remote migration history has 7 connector-versioned entries (no `rpcs`), so run `supabase migration repair` before any `db push`. `mobile/local.properties` (git-ignored) holds the URL and the legacy anon JWT. The user's other org, Sledje-SCM, isn't used. The empty Tokyo project `agent_Pro` and a temporary `agent-pro` were removed by the user |
| Razorpay | No account or keys configured yet |
| Registrar | Not chosen |
| Hosting | **Cloudflare** (DECISIONS #59), account "Sledjesoftwares@gmail.com's Account", logged in through `npx wrangler@4.151.1 login`. **Deployed on 2026-10-17:** Worker `agentpro-sites` → `https://agentpro-sites.sledjesoftwares.workers.dev` (preview, demo data only; built with `SITE_BASE_URL` set to that URL). Redeploy: build with `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SITE_BASE_URL` set, then `npx wrangler@4.151.1 deploy --config web/wrangler.jsonc`; each deploy needs the user's approval. No platform domain yet |
| Git | Remote `origin` = `git@github.com:Gunjan2244/agent_pro.git`. On `main`, the user committed and pushed `0e77dc7` ("everything documented") and `06ab83f` ("almost", 2026-10-17). No secrets are committed (scanned). **To clean up:** `.wrangler/` and `mobile/**/bin/` (IDE output) are committed but should be git-ignored. The agents never commit; the user decides commits and pushes |
| Connectors | The claude.ai Supabase connector reaches org letssee. It disconnects at times; if so, re-enable it in claude.ai → Settings → Connectors |
