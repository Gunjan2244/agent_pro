# Status — read this first

_Last updated: 2026-10-19 (onboarding no longer reopens after app updates; agent website v2 and app transitions on 2026-10-18). This file was rebuilt on 2026-10-17 after an orchestrator script accidentally emptied it; the content reflects the full session record. See CHANGELOG 2026-10-16._

## Current state
- **How the work is run:** an orchestrator plus parallel sub-agents (DECISIONS #24, refined by #55).
  - Sonnet handles well-specified work, verification, docs and UI screens; Opus only handles complex or security-critical work.
  - Only the orchestrator edits CHANGELOG, DECISIONS and STATUS.
  - Only one Gradle build runs at a time, **across all sessions**. Everyone queues on `flock /tmp/agentpro-gradle.lock` (not a per-session scratchpad lock), because the machine has about 7 GB of RAM.
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
- **New scope designed (2026-10-16; the docs match the user's decisions):** a multi-company platform with admin-managed offers and a web admin panel (`docs/OFFERS.md`, DECISIONS #47, #49–#53), and DPDP compliance (`docs/PRIVACY.md`, DECISIONS #48, #54). P1 privacy is released (above).
- **Phase 8b-1 built and verified (2026-10-17, user said "proceed"; DECISIONS #72, #73):** the offers definitions are in `shared/` (six catalog lists, the `offers` flag and limits, `offer-schema.json`, `hero.featuredOffer` and the `offers` section, 46 EN/HI strings) and in the codegen (`catalog_sync` offer functions, new field types and rules, `verifyShared` checks). Nothing user-visible: the app hides the Offers section until 8b-4 and the web ignores it until 8b-3.
- **Phase 8b-2 built and verified locally (2026-10-18, user said "continue"; DECISIONS #74):** `20261017132241_companies_offers.sql` (tables, admin-only RLS with MFA, guards, audit, `company-media` bucket, agent and admin RPCs, offer checks in `publish_site`, `company`/`offers` in the public payload) and the regenerated `20261018060943_catalog_sync.sql` (with the five offers CHECKs). SQL 476/476 on Postgres 17 and 15 (new `95_offers.sql`, 144 checks). **Not applied to `AGENT_PRO`.** To apply (each step needs the user's approval): `20261017050728_catalog_sync.sql` can be skipped (the new catalog_sync includes it); apply `20261017132241_companies_offers.sql`, then `20261018060943_catalog_sync.sql`, then run the advisors (expect the 0029 list in BACKEND.md). Nothing in the app or web uses it yet, so applying can wait for 8b-3/8b-4. **Next: 8b-3 (web) and 8b-4 (app), then 8b-5, then 8c (admin panel).**
  - The user hasn't said yet whether "predefined images" means one image per offer (designed) or a **company image library** agents can use anywhere on their site (not designed). Ask before 8c.
- **App icon (2026-10-17):** the adaptive launcher icon is in (DESIGN_SYSTEM.md, App icon; DECISIONS #66). It's an "A" with a rising chart line, on primary blue. The Play Store 512 and iOS 1024 PNGs are in `design/app-icon/`. The `-install` test APK (10:40) includes it.
- **Auth email Resend fixed (2026-10-17, DECISIONS #67; user report):** a refused or lost confirmation or reset email can now always be sent again, from "Check your email", "Reset link sent" and Sign in ("Resend confirmation email"). There's a 60 s countdown, and the hourly quota message is honest. 259 app tests pass, with 0 screenshot diffs. **It isn't in a test APK yet** (the 10:40 APK predates it); lint wasn't run. The hourly quota itself (about 2 per hour) still needs custom SMTP (Open questions, Auth emails).
- **Three launch screens after Publish now (2026-10-17, DECISIONS #71; user request):** Add it to your phone → This is what customers see → Share it, then Home. Onboarding and NavSmoke tests pass. Full suite (266 tests), lint (0 errors) and screenshots pass on the combined tree. **Test APK (14:43):** `~/AgentPRO-test/AgentPRO-test-2026-10-17-launch.apk`, **waiting for the user to try it on a phone**.
- **Fuller Create account and automatic sign-in (2026-10-17, DECISIONS #70; user request):**
  - **Create account** asks for the full name, mobile, email, password and Confirm password; its link reads "Sign in instead".
  - The name and mobile reach `profiles` through the existing signup trigger, so there's no migration.
  - **Onboarding step 1** shows them as a card with Edit and asks only for the rest.
  - **Sign-in is automatic:** at once if Confirm email is off; otherwise when the agent comes back to "Check your email" after confirming (in-memory credentials), or through the confirmation deep link.
  - 264 app tests pass, with 0 screenshot diffs. **Test APK (13:18):** `~/AgentPRO-test/AgentPRO-test-2026-10-17-signup.apk` (debug, `AGENT_PRO`; supersedes the 10:40 build). Lint: 0 errors. **Waiting for the user to try it on a phone** (the earlier "it works" ran the 10:40 build, which only covered the confirmation deep link). Any test account created for checks is test data, to be deleted before real users.
- **Gmail SMTP for Auth emails (2026-10-17, DECISIONS #68):** the user set up a Gmail app password and Supabase custom SMTP on `AGENT_PRO`, with Auth → Rate Limits → emails at 100 per hour (confirmed in the auth log). **Test:** a sign-up for `akash2244rocks+smtp1@gmail.com` (user `fc30253a-…`, test only) returned 200, so Gmail accepted the email. **Still to confirm:** the user sees it arrive. **Delete that test user** after the check.
- **Branded Auth emails (2026-10-17, DECISIONS #69):** five templates are generated in `supabase/email-templates/` and look right in headless Chrome. **To make them live,** the user pastes each into Supabase → Authentication → Emails → Templates, with its subject from `subjects.json`. Then send a real sign-up and check the email. **Follow-up:** the real icon as a hosted PNG in place of the HTML "A" (needs the preview redeployed).
- **Latest test APK (2026-10-18 21:26):** `~/AgentPRO-test/AgentPRO-test-2026-10-18-latest.apk` (debug, built from the current uncommitted tree; no tests or lint run for this build). **Not yet tried on a phone.**
- **Onboarding after app updates fixed (2026-10-19, DECISIONS #83; user report):** after an update that empties the cache, a race in the gate (the sync says Done before the cached site re-appears) latched onboarding, and step 3 then asked for a new link that the database refused. Now the app asks the **server** whether the agent has a site before onboarding starts, and again after step 1. An agent with a site always gets Main with the same link; if the server can't be asked, Retry. 288 app tests pass, 0 screenshot diffs, lint 0 errors. **Installed on the user's phone as an update** (SM-F127G, data kept), but the phone was locked, so Home wasn't seen. **Waiting for the user to open it and to try the next update** (TESTING.md End to end #11). APK: `~/AgentPRO-test/AgentPRO-test-2026-10-19-gate.apk`.
- **App transitions (2026-10-18, DECISIONS #80; user request):** the motion spec is now wired everywhere: shared-axis X navigation with predictive back, fade-through for tabs and the app gate, crossfaded screen states (keyed on the state's kind), animated list rows and the chart ⇄ table swap (DESIGN_SYSTEM.md, Motion). Before this, every screen change used Navigation Compose's default 700ms crossfade. 262 of 266 app tests pass; the 4 failures are the `OnboardingTemplate` screenshot, whose "1 of 3" → "1 of 5" comes from the in-progress "Best offers" templates, not from motion. Lint: 0 errors. **Test APK (12:39):** `~/AgentPRO-test/AgentPRO-test-2026-10-18-transitions.apk` (debug; it also contains the other uncommitted work in the tree). **Not yet tried on a phone** (TESTING.md End to end #10).
- **Agent website v2 (2026-10-18, user request; DECISIONS #75–#79, #81, #82; PRODUCT.md requirement log; plan in CHANGELOG):** **LIVE on 2026-10-18** (the user said "go live"): applied to `AGENT_PRO`, and the preview is redeployed (Worker version `8a8ef744`).
  - **SSR:** the Worker renders every site page. It 301s `/a/<slug>` to `/a/<slug>/`, serves a 404 with `noindex` for unavailable sites and falls back to the shell on backend errors. It sends `Last-Modified` = max(published_at, build time) and answers 304, and serves `robots.txt` and `sitemap.xml`. The client only hydrates.
  - **Seeded sites:** `shared/site-seed.json` → `SiteSeed.kt` → `StarterContent`. Every general section is filled at creation in the site's language, with 3 variants per long text picked by the agent id. Nothing is invented (no testimonials or achievements).
  - **Two families:** "Meet me" (classic, modern, minimal, refreshed) and "Best offers" (new spotlight, guide). The app picks the family first.
  - **Conversational layer:** speech bubbles, a greeting, quick-reply chips, the FAQ as a chat, and a sticky Call/WhatsApp bar.
  - **Offers rendered and disguised:** generic public names, the admin-only `internal_name`, and a blocked-term check (149 insurer product and company names plus every company's name). No company name or logo on sites. Auto offers appear on new "Best offers" sites.
  - **SEO:** semantic headings, a JSON-LD graph, a sitemap with the new anon RPC `list_public_sites()`. Lighthouse on the SSR output: spotlight 99–100 / 100 / 96 / 100, classic 96 / 100 / 96 / 100.
  - **Images:** all 20 user-generated illustrations are shipped as WebP (masters in `design/site-images/source/`).
  - **Demo offers** modelled on the insurer's current public plans, disguised: `supabase/demo/demo-offers.json`, with `demo_offers.sql` as test data.
  - **Verified (2026-10-18, combined tree):** 208 web checks, the CSS lint, `site-seed --check`; `generateShared` (no drift), `verifyShared` and the build-logic tests; SQL **565/565** on Postgres 17 (and 15 by the agent); app tests **280/280** (screenshots included; the `OnboardingTemplate` baseline matches again, because the pager now shows one family's 3 templates).
  - **Go-live done (2026-10-18):** the user pasted `20261017132241_companies_offers.sql`, `20261018073025_catalog_sync.sql`, `demo_offers.sql` and `demo_sites.sql` in the SQL Editor; verified (tables, functions, the demo company and 6 offers, 2 approved demo links). Advisors: no errors; 6 × 0028 and 21 × 0029 WARNs as expected, plus Auth's "Leaked password protection disabled" (a dashboard setting, for the user). Worker deployed; live checks passed: the 301, SSR 200, 404 for unknown slugs, headers, `Last-Modified`, robots.txt, sitemap.xml, and Priya's spotlight page with offers and no company name. Sites created by the old app (e.g. `gunjan`) keep their sparse content until refilled or edited. The original plan was:
    1. Apply `20261017132241_companies_offers.sql`, then `20261018073025_catalog_sync.sql`, to `AGENT_PRO`. Check `list_migrations` first, because the connector may auto-decline and the user may need to paste them in the SQL Editor.
    2. Run the advisors. Expect the accepted 0028/0029 WARNs listed in BACKEND.md.
    3. Optionally, seed the demo company and offers (`supabase/demo/demo_offers.sql`, as postgres) and approve the demo agents' memberships.
    4. Re-seed the two demo sites with the new starter content (a script; test data).
    5. Redeploy the Worker: tests, then the real build, then a check of `config.js` (including `BUILD_TIME`), then `wrangler deploy`.
    6. Run Lighthouse and the host-resolution checks on the preview.
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
00a. **Real insurance companies replace "Demo Life Insurer" (2026-10-22, user: "instead of demo use real insurance companies as per the plan"; DECISIONS #94; CHANGELOG 2026-10-22).** `supabase/migrations/20261022180000_real_companies.sql` is written and verified against the live schema/triggers (inspected `companies_guard`, `offers_guard`, `memberships_guard` directly). **Not applied — needs the user to paste it into the `AGENT_PRO` SQL Editor as `postgres`:**
    - the connector declined to run it first (classified as a production write, same as every other migration here);
    - the user then pasted it themselves and hit `23503 company_memberships_company_id_fkey` — the original version tried a real `delete from companies where slug = 'demo-life'`, but withdrawn `company_memberships` rows keep their `company_id` as history, so `ON DELETE RESTRICT` blocks it (this is also exactly why `is_active` exists: companies are never hard-deleted, OFFERS.md). The whole thing is one transaction, so that attempt rolled back cleanly — confirmed nothing was committed. **Fixed:** the file now deactivates demo-life (`is_active = false, sort = 999`) instead of deleting it.
    - It seeds 11 real insurers (LIC, HDFC Life, ICICI Prudential Life, SBI Life, Max Life, Bajaj Allianz Life, Tata AIA Life, Kotak Mahindra Life, Aditya Birla Sun Life, PNB MetLife, Star Health; names + standard IRDAI disclaimer, no logos), re-creates the 6 existing disguised test offers under LIC (offers can't change company via UPDATE), moves the 4 existing test memberships from demo-life to LIC via withdraw→request→approve, then deletes the now-unreferenced demo-life offers and deactivates demo-life itself. Confirmed no live site's content pins any of the 6 old offer ids, so re-creating them is safe.
    - After it's pasted: re-run the advisors (one extra company + offer rows, no new WARN expected beyond the existing 0028/0029 set) before the Worker redeploy in 0b below. `supabase/demo/` and `supabase/tests/95_offers.sql` are untouched on purpose (local test fixtures, not production data).
0b. **Companies, insurance types and site redesign (2026-10-22; PRODUCT.md requirement log; DECISIONS #89–#92; CHANGELOG 2026-10-22): built and verified locally, nothing applied or deployed.** Shared + backend (SQL 648/648 on PG 15 and 17), web (companies section + redesign of all five templates; 241 web checks), app (company + insurance-type pickers in onboarding step 1 and Settings → Profile; 327 app tests, 36 new screenshots, lint 0 errors). **Waiting for the user:** (1) review the before/after site screenshots (session scratchpad `site-redesign/`, ephemeral); (2) **done 2026-10-22:** the user applied `20261022074432_catalog_sync.sql` and `20261022090000_multi_company.sql` to `AGENT_PRO` in the SQL Editor (`20261020090000_preview_offers.sql` was already live). Verified: the 4-argument `request_company`, `set_membership_types`, `withdraw_company_request(uuid)` and `catalog_insurance_types()` exist, and the 3 earlier links were backfilled with `{life}`. A phone save then worked (pending request for Demo Life Insurer, health + motor; **since approved** as postgres in the SQL Editor, and **since moved to LIC** by 00a above). **Advisors run 2026-10-22:** 6 anon + 23 authenticated `SECURITY DEFINER` WARNs (0028/0029, two more than the earlier 21 — the new `request_company`/`set_membership_types`/`withdraw_company_request`/`catalog_insurance_types` RPCs — accepted by design) plus the known "Leaked password protection disabled"; no errors. **Still to do:** the Worker redeploy (re-run Lighthouse first); (3) review the provisional insurance-type labels/icons, the `{life}` backfill, and the Hindi copy (`site.companies_heading`, `strings_companies.xml`). Done since: agents can hide/reorder the Companies section, and the cards show the status once (the live `site_schema()` lacks `companies` until the next catalog_sync is applied). **DECISIONS #93 done locally (CHANGELOG 2026-10-22):** the site's services, quick links, enquiry options and starter copy follow the agent's insurance types. **To make it live:** the user pastes `20261022162929_catalog_sync.sql` in the SQL Editor (also adds `companies` to the live `site_schema()`), then the advisors. **For the user:** the new interest labels; whether life-only agents should also get Health; review of the new general copy (EN + HI) and the `website_match_*` Hindi strings; generate the 5 new interest illustrations (IMAGES.md prompts), then run `scripts/gen-site-images.py`. The demo seeds weren't regenerated. **Asked the user:** remove designation from onboarding step 1 and show a generated "Insurance advisor · <types> · <city>" line, keeping an optional checked Title in Content? Follow-ups: Settings → Profile holds only companies; a phone check (TESTING.md End to end). **Launch gate:** counsel review of insurer names on sites (#90).
00. **Email codes replace links (2026-10-22, DECISIONS #88):** code written; build/tests: see CHANGELOG. **Before it works live, the user pastes the regenerated `confirmation.html` and `recovery.html` (with `subjects.json`) into Supabase → Authentication → Emails.** Then phone check (TESTING.md End to end #1). Screenshot baselines for the auth code screens may need recording.
0a. **Sign-up with an existing email (2026-10-20, CHANGELOG):** now shows "An account with this email already exists" instead of "Check your email". Compiles only; **needs a test and a phone check** (sign up twice with the same email, Confirm email on).
0. **Trust-step crash fixed (2026-10-18):** an unescaped `}` in a regex crashed on Android's ICU engine (CHANGELOG). Installed on the phone as `~/AgentPRO-test/AgentPRO-test-2026-10-18-regexfix.apk`; **waiting for the user to confirm the onboarding flow now works.** Tests and lint were not re-run.
1. **P1 privacy foundations** (PRIVACY.md), required before any public deploy. Work packages, Sonnet by default (DECISIONS #55):
   - P1-1 notices and hashing in `shared/` and codegen (Opus, codegen);
   - P1-2 the backend tables `consents`, `data_requests` and `audit_log`, and `payments.raw` minimisation (Opus, security);
   - P1-3 web: consent checkbox, privacy page, self-hosted fonts, policy pages (Sonnet);
   - P1-4 app: sign-up consent and the customer attestation (Sonnet).
2. **Website v2 follow-ups:** a new test APK with the seeding and the family picker, so new sites start full; Lighthouse on the live preview; decide whether to refill sites made with the old app (e.g. `gunjan`).
3. **Phase 8b companies and offers, and 8c admin panel** (OFFERS.md); split per the roadmap, mostly Sonnet. **8b-1 and 8b-2 are done (8b-2 local only), and 8b-3's offer cards and banner are done (website v2).** Next: the rest of 8b-3 (slot mode and `preview-bridge.js`) and 8b-4 (app `feature/offers`; remove `offers` from `SiteContent.pendingSections`), in parallel; then 8b-5 (verifier), then 8c (admin panel: `admin` Edge Function, Vite/React app with MFA, CRUD screens; deploying needs approval).
4. **Ask the user before:**
   - creating a test user on `AGENT_PRO` for the live test (TESTING.md End to end #1); the redirect URL `agentpro://auth-callback**` is on the allow list (2026-10-17);
   - the Cloudflare `*.workers.dev` preview deploy with demo data (approved in principle; needs the user's `npx wrangler@4.151.1 login`, then a go-ahead);
   - committing.
5. **Get from the user:** the grievance officer's details, and approval of Supabase Pro (HIGH PRIORITY).
6. **On the phone:** confirm the update opens Home, not onboarding (TESTING.md End to end #11, DECISIONS #83), and try the transitions (#10, DECISIONS #80).
7. **Small UI fix:** the "Half-yearly" label is cut off in the premium-mode segmented control (Sonnet).
8. **Phases 9 and 10** (payments, domains) are blocked by the ❗ questions below.

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
- [ ] **4b. Agent website v2** (2026-10-18; DECISIONS #75–#79, #81, #82)
  - [x] WP1 `shared/` and codegen: families, seed, blocked terms, `internalName`
  - [x] WP3a SSR and SEO plumbing
  - [x] WP2 backend: disguise, `internal_name`, auto offers, `interest`, `list_public_sites`
  - [x] WP3b templates (spotlight, guide, refreshed three), conversational layer, offers, images
  - [x] WP4 app: seeded StarterContent, the family picker, the checklist item, photo tips
  - [x] Go live (2026-10-18): applied to `AGENT_PRO`, the advisors, the demo seed and the refill, the Worker redeployed (`8a8ef744`)
  - [ ] Lighthouse on the live preview (headless Chrome segfaults on this machine; run it from the user's Chrome)
  - [x] All 20 site images (2026-10-18)
- [x] **5. App foundation:** auth, onboarding, Room/`SyncEngine`, `NetworkMonitor`, `FeatureRegistry`, nav (4 tabs). Verified on 2026-10-16 (156 tests, 84 screenshots)
  - [x] Follow-up: Set new password screen, AppCompat locales, Keystore-encrypted session (DECISIONS #44–#46, #57). Verified on 2026-10-17
  - [ ] Live test against `AGENT_PRO` (TESTING.md End to end #1). It needs a test user, so ask first. `agentpro://auth-callback**` is now on Supabase Auth's allowed redirect URLs (2026-10-17)
- [x] **6. Enquiries** (list, detail, add, convert). Verified on 2026-10-17
- [x] **7. Customers, policies and dues** (Customers tab with the Customers | Dues segment, record-payment sheet). Verified on 2026-10-17
- [x] **8. Website hub** (Content, Appearance, Link, Stats, Preview, Publish, QR). Verified on 2026-10-17
- [ ] **8b. Companies and offers** (OFFERS.md), after Phase 8
  - [x] 8b-1 `shared/` and codegen (2026-10-17)
  - [x] 8b-2 backend (2026-10-18; local; extended by website v2 with the disguise, `internal_name`, auto offers and `list_public_sites`: 565/565 SQL checks; not applied to `AGENT_PRO`)
  - [ ] 8b-3 web: **offer cards and the hero banner done (2026-10-18, website v2)**; slot mode and `preview-bridge.js` still to do, together with 8b-4
  - [ ] 8b-4 app `feature/offers` (including the admin-verified membership states); remove `offers` from `SiteContent.pendingSections`
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
- [ ] **Later:** auto-renewal for the plan and domains, FCM push on Android (server-side rendering is built: website v2, DECISIONS #75)

## Open questions / blockers
- **Website v2 (2026-10-18):**
  - **Counsel:** IRDAI's advertising rules may require an agent's site to name the insurer they represent, which conflicts with hiding the company name (DECISIONS #77). Also review the 149-term blocklist (`catalog.publicTextRules`, provisional) and the demo offers' wording.
  - **SEO trade-off for the user to confirm:** with the insurer's name hidden, sites can't rank for "<insurer> agent in <city>". They rank for "insurance advisor in <city>", the agent's name and plan types.
  - **The agent's own text** (e.g. a designation like "LIC Agent") isn't checked. Should publish warn?
  - **Blocked terms** aren't re-checked on existing rows when the list or a company changes (only on the next edit or go-live).
- **HIGH PRIORITY:** approve **Supabase Pro (about $25/month) for daily backups** before launch. The free plan has no backups and pauses after about a week without activity (DECISIONS #54). **Launch gate (PRIVACY.md, Backup risk):** test data only, with no real agents onboarded and no real sites published, until it's approved.
- **Grievance officer:** name, email and phone, to publish on the privacy pages (PRIVACY.md). This is the only one of the 8 offers and privacy decisions still open.
- **Confirm email on or off (DECISIONS #70; the user decides):** with it **on** (now), the agent confirms by email, then is signed in on returning to the app. With it **off** (Supabase → Authentication → Sign In / Providers → Email → Confirm email), sign-up signs in at once with no email, and the app needs no change. The cost is that addresses aren't verified, so a mistyped email owns the account and password reset can't reach it.
- **Auth emails:** **interim fix live since 2026-10-17:** Gmail SMTP as Supabase custom SMTP, with the limit at 100 per hour (DECISIONS #68). **Before launch** (DECISIONS #60, still blocked on the platform domain): a transactional provider (Brevo or Amazon SES) with SPF/DKIM/DMARC replaces Gmail.
- **Auth "verify" rate limit:** the auth log shows it went from 30 to 10 when the SMTP settings were saved (2026-10-17 07:15 UTC). Ask the user whether that was intended; 10 may be tight while testing.
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
  - import the `local_offer` icon for offers (needs the user's OK to download `@material-symbols/svg-400@0.48.0`; DECISIONS #73);
  - the site footer's "Independent insurance advisor" may be wrong for agents tied to one insurer, so review the copy;
  - `profiles.branch` is unused, so remove it or justify it (PRIVACY.md);
  - `web/` still has form limits that mirror `submit_enquiry` and have no source in `shared/`.

## Environment
| Item | State |
|---|---|
| Java | OpenJDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`) is the build JDK; OpenJDK 25 is also installed. `.vscode/settings.json` sets `java.import.gradle.java.home` to JDK 21, so the VS Code Java extension doesn't import with its bundled JRE 25. A second JVM would start a second 2 GB Gradle daemon, which this 7 GB machine can't hold: syncs fail with "Gradle build daemon has been stopped" |
| Gradle | System Gradle 4.4.1 is unusable. The project uses the wrapper (Gradle 9.7.1) in `mobile/`. `gradle.properties`: `-Xmx2g -XX:MaxMetaspaceSize=768m`, 2 workers |
| Android SDK | `~/Android/Sdk` (installs approved by the user on 2026-10-15/16): cmdline-tools (latest), platform-tools, `platforms;android-35`, `android-36` and `android-37.0`, and `build-tools;35.0.0` and `36.0.0`. AGP downloaded `36.0.0` by itself; the user kept it, and `android.builder.sdkDownload=false` now blocks automatic downloads. No emulator system image yet |
| Mac / Xcode | Not needed until the iOS phase. This machine is Linux, so iOS targets can't be built here |
| Docker | Available. The local SQL tests (`bash supabase/tests/run.sh`) run in a throwaway container capped at 384 MB: `postgres:15` by default, `postgres:17` via `PG_IMAGE=postgres:17` (pulled 2026-10-16 with approval). Both pass 226/226 |
| Supabase | **Project `AGENT_PRO`**, ref `rpqatijolcajehshoikc`, URL `https://rpqatijolcajehshoikc.supabase.co`, org **letssee** (free plan), **ap-south-1 (Mumbai)**, Postgres 17.11, created by the user on 2026-10-16. **10 migrations applied** (including `20261016220600_privacy.sql` and `20261016220836_catalog_sync.sql`, pasted in the SQL Editor by the user on 2026-10-17). Earlier batch: 7 through the connector, and `rpcs` by the user in the SQL Editor. **Verified:** functions and grants, 14 tables with RLS, 30+4 policies, 10 catalog CHECKs, realtime publication, `app_config` rows. **Advisors:** no errors; 11 WARNs accepted. The project also has Supabase's `rls_auto_enable` event trigger, whose API execute permission is revoked. The remote migration history has 7 connector-versioned entries (no `rpcs`), so run `supabase migration repair` before any `db push`. `mobile/local.properties` (git-ignored) holds the URL and the legacy anon JWT. The user's other org, Sledje-SCM, isn't used. The empty Tokyo project `agent_Pro` and a temporary `agent-pro` were removed by the user |
| Razorpay | No account or keys configured yet |
| Registrar | Not chosen |
| Hosting | **Cloudflare** (DECISIONS #59), account "Sledjesoftwares@gmail.com's Account", logged in through `npx wrangler@4.151.1 login`. **Redeployed on 2026-10-18** (website v2, version `8a8ef744`; first deployed 2026-10-17): Worker `agentpro-sites` → `https://agentpro-sites.sledjesoftwares.workers.dev` (preview, demo data only; built with `SITE_BASE_URL` set to that URL). Redeploy: build with `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SITE_BASE_URL` set, then `npx wrangler@4.151.1 deploy --config web/wrangler.jsonc`; each deploy needs the user's approval. No platform domain yet |
| Git | Remote `origin` = `git@github.com:Gunjan2244/agent_pro.git`. On `main`, the user committed and pushed `0e77dc7` ("everything documented") and `06ab83f` ("almost", 2026-10-17). No secrets are committed (scanned). **To clean up:** `.wrangler/` and `mobile/**/bin/` (IDE output) are committed but should be git-ignored. The agents never commit; the user decides commits and pushes |
| Connectors | The claude.ai Supabase connector reaches org letssee. It disconnects at times; if so, re-enable it in claude.ai → Settings → Connectors |
