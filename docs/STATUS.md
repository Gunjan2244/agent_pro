# Status — read this first

_Last updated: 2026-10-28 (existing agents are walked through important news full screen, first "5 new website designs", plus the CLAUDE.md rollout rule, DECISIONS #121); also 2026-10-28 (template choice is now an auto-scrolling grid of the 5 templates, then a preview of the tapped one, in onboarding and Appearance, DECISIONS #120); also 2026-10-28 (the in-app template preview loads the live renderer with the bundled copy as fallback, DECISIONS #119); also 2026-10-28 (CI/CD: the web renderer auto-deploys to Cloudflare on push to main, GitHub Actions, DECISIONS #118; secrets not yet added); earlier 2026-10-27 (five redesigned, lively templates built locally, not deployed, DECISIONS #117); earlier 2026-10-26 (onboarding: no More details, a skippable photo step and the finished website before the link; template designs scroll easily; DECISIONS #116; and no template families, #115); earlier 2026-10-25, late (Continue with Google and the 5-step onboarding that shows the website before the company details; no designation; DECISIONS #112–#114); earlier 2026-10-25 (customer sign-up link and Incomplete forms built, DECISIONS #108; the missing "Your website is live" confirmation fixed); earlier 2026-10-25 (Home guidance and the Offers list editor built); before that 2026-10-23 (admin panel 8c built locally); before that 2026-10-19 (onboarding no longer reopens after app updates; agent website v2 and app transitions on 2026-10-18). This file was rebuilt on 2026-10-17 after an orchestrator script accidentally emptied it; the content reflects the full session record. See CHANGELOG 2026-10-16._

## Current state
- **"What's new" walkthrough for existing agents (2026-10-28, user request; DECISIONS #121): built and verified locally, not on a phone, not committed.**
  - **Versioned items:** "What's new" items now carry the release that brought them. Agents see only what's newer than they've seen.
  - **Important items** (they change the public site, need a decision or a step, or move something) open **full screen before Home**, once, with Show me / Next / Not now. Small ones keep the Home sheet.
  - **First item:** "5 new website designs" (Show me → the template grid of #120).
  - **The rule:** CLAUDE.md "Rolling out to existing users" makes this part of every change existing agents should know about.
  - **Verified:** 468 app tests with 0 failures and 0 screenshot diffs, lint 0 errors.
  - **Test APK:** `~/AgentPRO-test/AgentPRO-test-2026-10-28-whats-new.apk` (also has #119 and #120; supersedes the template-grid APK).
- **Template grid → preview (2026-10-28, user request; DECISIONS #120): built and verified locally, not on a phone, not committed.**
  - **Onboarding step 2 "Choose your template"** (it replaces the intro and its countdown) and **Website → Appearance → Template** show all 5 templates. There are 2 per row, with 2 rows whole and a sliver of the third.
  - **Tiles:** each tile is the template's full-page picture. 4 s after all have loaded, they scroll one at a time; each jumps back to the top at its end, then the next starts.
  - **Tap a tile** to open that template live with the agent's content. Onboarding has **Make website from this →** (the arrow nudges); Appearance has **Use this template →**. Both have **See other templates**. The swipe pager is gone.
  - **Pictures:** 20 WebP strips (about 870 KB) made by the new `scripts/gen-template-pages.py`. Regenerate them, and rebuild the app, whenever a template's look changes (WEB_RENDERER.md, Template page strips).
  - **Verified:** verifyShared, assembleDebug, lint 0 errors, 464 app tests with 0 failures and 0 screenshot diffs (baselines re-recorded).
  - **Test APK:** `~/AgentPRO-test/AgentPRO-test-2026-10-28-template-grid.apk` (debug; built from the working tree, which also holds #119's uncommitted live preview).
- **Live template preview in the app (2026-10-28, user request; DECISIONS #119): built and verified locally, not on a phone, not committed.** `SitePreview` loads `<SITE_BASE_URL>/?preview=1` from the Worker, so template changes reach the app's previews with each web deploy, no APK rebuild; the bundled renderer is the fallback (offline, error, no `renderSite`, 10 s timeout). 460 app tests, 0 failures, lint clean; 8 stale template-picker baselines re-recorded (they were failing since #117). The test APK below predates this change. Still needs an app update: adding a brand-new template key (the list is compiled in).
- **Test APK (2026-10-28 14:38):** `~/AgentPRO-test/AgentPRO-test-2026-10-28.apk` (debug, `AGENT_PRO`, built from commit `be813d5`, clean tree; `GOOGLE_WEB_CLIENT_ID` is now set, so Welcome shows Continue with Google). Supersedes the 2026-10-26 onboarding APK. Only `generateShared verifyShared :androidApp:assembleDebug` were run; tests and lint weren't re-run. The first attempt was killed by low memory (exit 137) while VS Code's Gradle daemons were resident; `--max-workers=2` got it through.
- **CI/CD for the web renderer (2026-10-28, user request; DECISIONS #118): pipeline built, not yet live.** `.github/workflows/deploy-web.yml` auto-deploys the public-site renderer to the `agentpro-sites` Cloudflare Worker on every push to `main` touching `web/`, `shared/` or the build/test scripts: tests (`test-web.mjs`, CSS lint) → build (`build-web.sh`, preview/no-`RELEASE`) → `wrangler deploy`, using repo secrets instead of interactive login. This is a user-approved standing exception to CLAUDE.md's per-deploy-approval rule (CLAUDE.md, WEB_RENDERER.md, DECISIONS #118). **Not done:** the four repo secrets (`CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`) aren't added yet (the user is adding them); the workflow hasn't run (nothing pushed to trigger it); no `SITE_BASE_URL` repo variable set (falls back to the preview URL). The 2026-10-27 template work below is still sitting uncommitted locally — once committed and pushed, this pipeline will pick it up automatically.
- **Five redesigned, lively templates (2026-10-27, user request; DECISIONS #117; PRODUCT.md 2026-10-27): built and verified locally, not deployed, not committed or pushed.** Same five keys; Trust (`classic`) follows the user's Theme A wireframe (top bar, banner, tickers, offers / why-me / policyholders sliders, dark intro, 2×3 plans); Fresh, Simple, Spotlight and Guide each have a new structure. New `web/motion.js` and `web/sections/ui.js`; new i18n keys (EN + HI) `site.menu`, `site.slider_prev/next/dot`, `site.see_plans`, `site.steps_heading`, `site.step_1..3`, `site.policyholders_heading`, and new template descriptions. Web suite 537 checks pass, CSS lint OK, moving parts checked in Chromium; previews regenerated. Wireframes are on the user's Design canvas artifact. Budgets raised (#117). Not done: Lighthouse re-run, a phone check in the app's WebView preview, the Hindi of the new keys is provisional.
- **Onboarding fixes after the user's walk-through (2026-10-26; DECISIONS #116; PRODUCT.md 2026-10-26): built and verified locally, not on a phone.** Seven steps: Your mobile → Template intro → Pick a design → Your work (companies with their agency codes, city; **no More details**, no branch) → **Your photo** (Add photo / Skip) → **Your website** (the finished site with the photo and the companies, pending ones included, with a "once we confirm" line) → Your link → Publish → Add to phone → Share. The post-publish preview screen is gone (Open moved to Share). The template pager no longer takes over vertical page scrolls (`VerticalScrollLock` in `AndroidSitePreview`). Verified: verifyShared, lint 0 errors, 459 app tests, 0 failures, baselines re-recorded (CHANGELOG 2026-10-26). **Test APK (2026-10-26 01:29):** `~/AgentPRO-test/AgentPRO-test-2026-10-26-onboarding.apk` (debug, `AGENT_PRO`, built from the current uncommitted tree, which also has #115; no `GOOGLE_WEB_CLIENT_ID`, so Welcome shows email sign-up only). A phone check is still needed (Next steps).
- **No template families (2026-10-26, user request; DECISIONS #115): built and verified locally, not on a phone.** Onboarding step 2 is now "Let's select a template for your website", with Continue → (the arrow nudges). It opens the designs by itself after 5 s. Step 3 and Appearance → Template show one pager of all 5 templates. Families remain only as an internal grouping in `shared/`. 450 app tests, 0 failures, 0 screenshot diffs, lint clean (run before the parallel Photo/Preview onboarding change, which re-records its own baselines).
- **Continue with Google and the new onboarding (2026-10-25, user request; DECISIONS #112–#114; PRODUCT.md 2026-10-25): built and verified locally, not live.**
  - **Welcome:** consent checkbox → **Continue with Google** (Credential Manager one tap) → Sign up with email / Sign in. Hidden when the build has no `GOOGLE_WEB_CLIENT_ID` (the current `local.properties` has none, so a build today still shows the old Welcome).
  - **Onboarding, now 7 steps (DECISIONS #116):** Your mobile number (Google's name shown) → Template intro (DECISIONS #115; was Choose a style) → Pick a design (before any company) → Your work (companies, insurance types, city) → Your photo → Your website → Your link.
  - **Designation removed:** sites say "Insurance agent" (editable in About); the app no longer writes `profiles.designation`.
  - **Sign-up notice v2** → migration `20261025173029_catalog_sync.sql`, **not applied**.
  - **Verified:** app suite 450 tests, 0 failures, 0 screenshot diffs (baselines re-recorded); lint 0 errors; build-logic tests; `verifyShared`; web 522 checks. Not on a phone yet.
- **Website hub bar (2026-10-25, user request):** Publish and "My website →" (preview) are always shown on the Website tab. Tests run and the `website/WebsiteHub` baselines re-recorded later the same day (CHANGELOG, Google sign-up entry).
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
- **Live accounts wiped (2026-10-25, user request; CHANGELOG):** all accounts and websites except `sledjesoftwares@gmail.com` (intended admin; not yet in `admins`) and the demo agents Ramesh and Priya are being deleted (10 accounts, 6 sites); companies and offers are kept. **Done and verified 2026-10-26:** the wipe and the admin conversion (DECISIONS #111). **Pending:** (1) a second wipe (2026-10-26) of 3 accounts signed up since (`gunjan23ths@`, `shatrughan.sinha.rm@`, `akash2244everyone@`; sites `gunjan`, `shatrughan-sinha`): the user pastes the script in the SQL Editor (the connector declines deletes on `auth.users`), then verify 3 accounts and 2 sites; (2) the user deletes the 3 orphan profile photos in Dashboard → Storage → `site-media`, folders `4b71438f-…`, `59d4c557-…`, `b661c605-…`. Earlier mentions of the `gunjan` site and the `+smtp1` test user below are then historical.
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
  - Offer images are one per offer (user answer 2026-10-22, DECISIONS #101).
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
- **"What's new" walkthrough (DECISIONS #121):** install `~/AgentPRO-test/AgentPRO-test-2026-10-28-whats-new.apk` over an older build on an account with a published site (with the user's go-ahead) and run TESTING.md End to end #15. Ship it together with the web deploy of the redesigned templates (#117/#118), so the "fresh new look" line is true when agents read it.
- **Template grid (DECISIONS #120):**
  - Install `~/AgentPRO-test/AgentPRO-test-2026-10-28-template-grid.apk` (with the user's go-ahead) and run TESTING.md onboarding step 3 and End to end #14. Check:
    - the 2.1-row fit on a small and a tall phone;
    - the scroll pace (`Motion.TEMPLATE_SCROLL_DP_PER_SECOND` = 300 dp/s);
    - no crash or blank tiles on a low-memory phone.
  - After any template redesign, run `python3 scripts/gen-template-pages.py` and rebuild.
- **Live template preview (DECISIONS #119):** build a test APK and run TESTING.md End to end #13 (live design shown, airplane-mode fallback, slow-network timeout). It only shows the new templates once the Worker is redeployed (the CI secrets, below). Optional later: fetch the template list and labels from the web, so a new template key needs no app update (needs its own decision).
- **CI/CD (DECISIONS #118):** the user adds the four GitHub Actions secrets (`CLOUDFLARE_API_TOKEN` — a Cloudflare API token scoped to Edit Workers on this account; `CLOUDFLARE_ACCOUNT_ID`; `SUPABASE_URL`; `SUPABASE_ANON_KEY`) in the `agent_pro` repo's Settings → Secrets and variables → Actions, and optionally a `SITE_BASE_URL` repo variable. Once added, the next push to `main` that touches `web/`, `shared/` or the build/test scripts deploys automatically; check the Actions tab for the run. The uncommitted 2026-10-27 template work (DECISIONS #117) will deploy as part of whichever push includes it.
- **Templates (DECISIONS #117):** the user reviews the five designs (wireframe canvas + the screenshots); then re-run Lighthouse (mobile; the classic banner picture may now be the desktop LCP element), check the in-app preview (Android WebView: sliders swipe inside the template pager without switching designs), and redeploy the Worker with the user's approval.
- **Phone check of the 7-step onboarding (DECISIONS #116):** build a test APK (with the user's go-ahead to install it) and sign up with email (no Google keys needed). Check: a slanted vertical swipe scrolls a design to its footer without jumping to the next; Your work has no More details; Your photo with Add photo and with Skip; Your website shows the photo and the pending company with the "once we confirm" line and scrolls smoothly; Publish → Add to phone → Share (Open works) → Home. TESTING.md, "End to end: Google sign-up and the new onboarding", step 3, lists the details.
00000000. **Make Continue with Google live (DECISIONS #112; each step needs the user, all outward-facing):**
    1. **Google Cloud** (console.cloud.google.com, a project for Agent PRO): OAuth consent screen (External; app name, support email, the privacy policy URL); an OAuth client **Web application** (its client ID and secret); an OAuth client **Android** for package `com.sledje.agentpro` with the SHA-1 of the debug key (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`) and later the release/Play signing key.
    2. **Supabase** → Authentication → Providers → Google: enable, paste the web client ID and secret.
    3. Put the web client ID in `mobile/local.properties` as `GOOGLE_WEB_CLIENT_ID=…` (never commit it).
    4. Apply `supabase/migrations/20261025173029_catalog_sync.sql` to `AGENT_PRO` (notice v2) **before** handing out an APK with this code, or the Google sign-up consent (and email sign-ups) is refused as an unknown hash; then run the advisors.
    5. Redeploy the Worker (web tests, real build, `config.js` check, deploy): the hero's "Insurance agent" line and the v2 notice text on the web privacy page.
    6. Build a test APK and run the phone check (TESTING.md, "End to end: Google sign-up and the new onboarding").
0000000. **Forms follow the keyboard (DECISIONS #110, 2026-10-25, user report on Create account):** built; compiles, test run in CHANGELOG. Needs a **phone check** (open Create account, tap each field: it is highlighted, sits above the keyboard with the next field visible, and Create account stays reachable). Not in a test APK yet.
000000. **Customer sign-up link and Incomplete forms (DECISIONS #108; PRODUCT.md 2026-10-25): built and verified locally; **LIVE on 2026-10-25: database, Worker (version `a10aa49a-c52d-4d07-a800-5166b6e706d3`) and the daily clean-up; waiting for the phone check.** Add customer → Fill it in myself / Pick from contacts / Send the customer a link. Manual forms autosave into `customer_drafts` and unfinished ones are listed under Incomplete forms (resume, delete with Undo); the link opens `/a/<slug>/join`, where the customer agrees to the notice first, then every pause in typing is saved and the agent watches live (values, current field, presence); Submit adds the customer. Contract: `~/.claude/plans/customer-signup-contract.md`.
    - **Verified:** SQL 1273/1273 (PG 15 and 17), web 522 + CSS lint, build-logic + `verifyShared`, app suite: see CHANGELOG 2026-10-25.
    - **Go-live, each step with the user's approval:**
      1. ~~Paste the two SQL files~~ **done 2026-10-25** (the user pasted `20261025060000_customer_signup.sql` and `20261025070819_catalog_sync.sql`; verified by the connector).
      2. ~~Advisors~~ **done 2026-10-25:** no errors; 0028 = 16 anon, 0029 = 28 authenticated, INFO 0008 now includes `private.customer_draft_links`, all as expected (BACKEND.md, Advisors).
      3. ~~Worker redeploy~~ **done 2026-10-25** (the user said "deploy the site"): web 522 checks + CSS lint, preview build against `AGENT_PRO` (the known release-blocker placeholders remain), `config.js` checked, `--dry-run` accepted `JOIN_LIMIT` (120/60 s), deployed as version `a10aa49a-c52d-4d07-a800-5166b6e706d3`. Live checks passed: the site 200; `join?t=` → 303 with the path-scoped HttpOnly `ap_join` cookie; `/join` with an unknown token → the "link has expired" page from the live RPC through the gate, `no-store` + `noindex`; a save POST without Origin → 403; robots.txt disallows `/a/*/join` and `/join`; the portal login still 200; `join-client.js` served.
      4. ~~A test APK~~ **built 2026-10-25:** `~/AgentPRO-test/AgentPRO-test-2026-10-25-signup.apk` (debug, `AGENT_PRO` + the preview; includes the sign-up link, Incomplete forms and the "Your website is live" fix; the suite and lint ran green on this tree). **Next: the phone check** (TESTING.md, End to end): send a link, fill it on another phone while watching the live screen, Submit and see the customer appear; leave a manual form half-done and find it under Incomplete forms; publish from the Website tab and see "Your website is live".
      5. ~~Daily clean-up~~ **scheduled 2026-10-25** (the user chose 11 pm IST): pg_cron enabled on `AGENT_PRO`, job `purge-customer-drafts` at `30 17 * * *` UTC (23:00 IST), running `select private.purge_customer_drafts()` as postgres, active. Script: `supabase/live-data/20261025_purge_customer_drafts_schedule.sql`.
    - **For the user to review:** the Hindi of `join.*`, `strings_signup.xml` and the two new notices; the provisional limits (link 7 days, 60 saves/min, 25 open links, presence 30 s); counsel: live saving after consent, and the web guardian checkbox for under-18s.
    - **Known gaps:** no Compose UI flow test for the Add sheet / Incomplete forms taps or for `MainShell` starting the drafts realtime; the live screen's Copy uses the deprecated `LocalClipboardManager` (as the enquiry screen does); the sign-up page is listed on every site's privacy page even with the flag off.
000001. **"Your website is live" after Publish fixed (user report, 2026-10-25):** the snackbar was cancelled the frame it appeared (CHANGELOG). In the test APK `AgentPRO-test-2026-10-25-signup.apk`; needs a phone check (publish from the Website tab and from Preview).
- **Finish setup fix (DECISIONS #105, 2026-10-25):** all steps tappable + About prompt card, unit tests green. Still open: backfill of Share/Customer-login marks for existing agents, prompts for the other steps, optional Skip, and a screenshot test for the prompt card; confirm the About step now completes on a phone.
00000. **Home guidance (DECISIONS #103) and the Offers list editor (DECISIONS #104 step 1, 8b-4a) are built and verified locally (2026-10-25):** `testAndroidHostTest` (screenshot verify) and `:androidApp:lint` are green on the combined tree; `generateShared` is up to date (no new migration).
    - **Built:**
      - Home: the one-time "What's new" sheet, the checklist with "Add your companies" and "Give a customer their login", next-task-only taps, `FeatureHost.openTab`, and the avatar photo.
      - Offers: Website → Content → Offers, the picker "Offers on your site" (add, remove, move, feature, edit as an own copy) and the hero's Featured offer, with the app validators `mustBeTrue` and `maxDaysAhead`.
    - **Now:** test APK built 2026-10-25: `~/AgentPRO-test/AgentPRO-test-2026-10-25-offers.apk` (debug, `AGENT_PRO` + the preview; app suite with screenshot verify, lint and web 465 checks green on this tree). Waiting for the user's phone check (TESTING.md, End to end, item 12: What's new on an existing account, the checklist next-step rule, add / remove / feature / edit-as-copy an offer, then publish).
    - **Next:** 8b-4b, tap-to-place (slot mode, `preview-bridge.js`, Place offers, the hub Offers row; OFFERS.md) with its own security review.
    - **Known gaps:** the editor doesn't focus the copied offer after "Make a copy"; an ended company offer shows "No longer available" because `my_company_offers` drops ended offers.
0001. **Site section reveal animation (2026-10-24, DECISIONS #107): built, 455 web checks pass; not deployed.** Needs a look in a real browser (scroll a page on the preview after the next Worker redeploy) and the next Worker redeploy to go live.
0000. **Customer login on agent sites ("My policies", DECISIONS #96–#102): built, security-reviewed, fixed and verified locally (2026-10-23). DATABASE LIVE on `AGENT_PRO` (verified by the connector 2026-10-23, after the user pasted the files): the 11 real insurers (demo-life inactive), the four `portal_*` tables, the 9 portal functions, `app_config.portal` with the new limits and blocked PINs, `site_schema()` with the `portal` section, the `portal` enquiry source and `customer_portal` notice CHECKs, the `customerPortal` flag, and one `private.portal_gate` row. Advisors run: no errors; 12 anon and 26 authenticated SECURITY DEFINER WARNs (the 6 + 3 new portal functions, accepted by design), INFO 0008 on the 7 policy-less private/portal tables (intended), plus the known "Leaked password protection disabled". WORKER DEPLOYED 2026-10-23 (version `461c4b3e-221c-4437-b1b7-1222a0303bdd`, `https://agentpro-sites.sledjesoftwares.workers.dev`; the secret `PORTAL_RPC_KEY` is set and matches the `portal_gate` hash; the `ratelimits` binding passed `--dry-run` and deployed). Live checks passed: site shows "Already my customer?" and the hero "Customer login"; `/my` → 303 to login; portal pages `no-store` + `noindex`; POST without Origin → 403; a wrong PIN → the normal "don't match" reply (so the gate works); portal manifest 200; service worker 200 with `Service-Worker-Allowed`. The build was a preview build (release blockers remain: grievance officer and email provider). NOT YET DONE: a test APK and the phone check (TESTING.md "End to end: customer login").**
    - **Built:**
      - Customers sign in on the agent's site (`/a/<slug>/my`) through an agent-issued link, then mobile + 6-digit PIN. They see a dashboard (policies, premium per year, cover, next due), their policies and payments, can ask or say they've paid, request a correction or deletion, and sign out.
      - The portal is installable as "{Agent} · My policies", with an offline copy of the last page.
      - Every template has a "For my customers" section and a "Customer login" hero link.
      - The app has Customer detail → Website login.
    - **Verified:** SQL 1048/0/0 on PG 15 and 17; web 453 checks + CSS lint; app `testAndroidHostTest` + `:androidApp:lint` green; build-logic green.
    - **Security review (Opus) done, all findings fixed (DECISIONS #101).** It is recorded for later that cookie `Path` is not a boundary on the shared sites origin; the fix is a separate portal origin once the platform domain exists.
    - **Go-live, each step with the user's approval:**
      1. Paste in the SQL Editor:
         1. `supabase/live-data/20261022180000_real_companies.sql` (pending);
         2. the other session's `20261022183528_admin_function.sql`, if it's applied;
         3. `20261022184000_customer_portal.sql`;
         4. `20261023163214_catalog_sync.sql` (it supersedes the never-applied `20261022162929` and `20261023162917`, which can be deleted).
      2. Create a random secret and set it with `npx wrangler@4.151.1 secret put PORTAL_RPC_KEY --config web/wrangler.jsonc`. Then, as postgres: `insert into private.portal_gate values (1, sha256(convert_to('<secret>','UTF8'))) on conflict (id) do update set key_hash = excluded.key_hash;`. Until then every portal call answers "unavailable".
      3. Advisors (expect 0028 → 12 anon, 0029 +3).
      4. Worker redeploy: tests, then the real build, then `config.js` check, then `deploy --dry-run`, since the `ratelimits` block is unverified, then deploy.
      5. A test APK, then the phone check (TESTING.md "End to end: customer login").
    - **Next build (optional):** Web Push premium reminders (wave 4: `portal_push_subscriptions`, the `portal-reminders` Edge Function, VAPID; approval needed for the secret, the function deploy and `pg_cron`). The `customer_portal` notice already has a reminders line. If push is dropped, remove that line and bump the notice to v2.
    - **For the user to review:**
      - the Hindi wording (पिन, कूटबद्ध रूप, नॉमिनी, the consent sentence, बीमा राशि, ग्राहक लॉगिन);
      - the provisional `catalog.portal` values and the 111 blocked PINs;
      - counsel: is mobile + 6-digit PIN enough for policy data;
      - two small UI notes from the screenshots (the indented "Edit customer" link, the gap above "Needs internet").
    - **Known gaps:**
      - the share link ignores custom domains (the app's `Site` has no domain yet);
      - the retention job for portal sessions and invites isn't built;
      - Modern's first-paint CSS is 25.9 of 26 KB;
      - the preview images are mock renders, so they don't show the login links.
    - **Working files (durable, outside the repo):** `~/.claude/plans/` (plan, `portal-contract.md`, `portal-docs-notes.md`, `portal-review.md`).
000. **Admin panel (8c; DECISIONS #95, #101–#106; CHANGELOG 2026-10-23): built locally; nothing deployed, applied or tried live.** `admin/` has sign-in, MFA, Membership requests, Companies (with permission-gated logos), Offers (one image per offer), and owner-only Admins and Audit log; the `admin` Edge Function backs Admins. **To go live (each step needs the user's approval):**
    1. ~~Apply `20261022183528_admin_function.sql`~~ **done 2026-10-23** (pasted by the user; verified: 6 `svc_admin_*` helpers, service_role-only execute, the signup-trigger skip, postgres may delete `auth.sessions`/`refresh_tokens`). Advisors: unchanged 6 × 0028 and 23 × 0029, plus one INFO 0008 on `private.admin_invites` (RLS on, no policies: intended, service-role only).
    2. Bootstrap the first owner as postgres: **scripted 2026-10-25 (DECISIONS #111), pending the user's SQL Editor paste.** `sledjesoftwares@gmail.com` is converted from an agent account (profile, subscription and consent removed; `account_kind = admin`; `admins` owner, every company; the password the user chose; sessions ended). Verify afterwards: one `admins` row, no profile. TOTP is enrolled at the first panel sign-in.
    3. `npx supabase@2.120.0 secrets set --project-ref rpqatijolcajehshoikc ADMIN_ORIGINS=https://agentpro-admin.sledjesoftwares.workers.dev,http://localhost:5173`, then `npx supabase@2.120.0 functions deploy admin --project-ref rpqatijolcajehshoikc`.
    4. Build `admin/` with `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY`, then `npx wrangler@4.151.1 deploy --config admin/wrangler.jsonc` → `agentpro-admin.sledjesoftwares.workers.dev`.
    - **To try locally first:** `cd admin && cp .env.example .env.local`, then `npm run dev` (needs step 2; the Admins screen also needs step 3).
    - **Still to build:** accepting an invite in the panel (setting the password from the invite link; `ADMIN_INVITE_REDIRECT_TO` plus a redirect URL in Auth settings; the panel has `detectSessionInUrl: false`), the Privacy area (P2), and the live preview of offer cards.
    - **Local tests:** `real_companies.sql` was moved to `supabase/live-data/` after it was applied (it holds real agent ids and broke `run.sh`). `supabase/tests/96_portal.sql` still fails until its portal migration is written (customer-portal work).
    - The planned "unconfirmed sign-ups, delete after 7 days" retention job must skip admin invitees.
00a. **Real insurance companies replace "Demo Life Insurer" (DECISIONS #94): applied 2026-10-23** (pasted by the user). Verified live: 11 active insurers, `demo-life` inactive (sort 999), 6 live offers under LIC, 4 approved LIC links (the 4 demo-life links kept as withdrawn history), advisors unchanged. The script now lives in `supabase/live-data/` (one-off live data, not a migration).
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
  - [ ] 8b-4 app `feature/offers`: **step 1 (8b-4a) done 2026-10-25** (list editor, `offers` out of `pendingSections`, validators; DECISIONS #104); membership states done earlier (#89); **8b-4b tap-to-place (slot mode, `preview-bridge.js`) still to do**
  - [ ] 8b-5 verifier
- [ ] **8e. Customer sign-up link and Incomplete forms** (DECISIONS #108): built, verified and live 2026-10-25 (database, Worker `a10aa49a`, daily clean-up at 23:00 IST); test APK built; phone check still to do
- [ ] **8d. Customer login on agent sites** (DECISIONS #96–#102): built, security-reviewed, fixed and verified locally 2026-10-23; reminders (Web Push) and go-live still to do
- [ ] **8c. Admin panel** (OFFERS.md)
  - [x] 8c-1 `admin` Edge Function (2026-10-23; local, not deployed)
  - [x] 8c-2 app shell, auth and MFA (2026-10-22; invite acceptance still to do)
  - [x] 8c-3 CRUD screens: memberships, companies + logos, offers, admins, audit log (2026-10-23; Privacy area is P2)
  - [ ] 8c-4 deploy (needs the user's approval)
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
- **"What's new" walkthrough (DECISIONS #121):**
  - Its Hindi strings are provisional.
  - **Timing:** the page says the site "has a fresh new look". If this app version reaches agents before the redesigned templates are deployed, that is briefly untrue. Release the app after the web deploy, or soften the wording.
- **Template grid (DECISIONS #120):**
  - The Hindi of the new strings is provisional: `template_grid_title`, `template_grid_hint`, `template_grid_hint_site`, `template_current`, `template_make_website`, `template_see_others`.
  - Interpretations to confirm with the user:
    - the 4 s wait happens once after loading, not between tiles;
    - the loop runs round and round;
    - the fifth tile (mostly below the fold) still takes its turn.
  - The tiles show a sample agent, not the agent's own name; the preview after the tap does.
- **Live preview (DECISIONS #119):** the preview now shows whatever is deployed, so a broken deploy shows in the app too, and `renderSite` must stay compatible with older app payloads. When the offers slot-mode bridge (8b-4b) is built, its `addWebMessageListener` must allow the `SITE_BASE_URL` origin as well as `appassets`.
- **CI/CD secrets (DECISIONS #118):** not yet added to GitHub, so the new workflow will fail at the build/deploy step on its first trigger until the user adds them (Next steps). Once the P1 privacy items (grievance officer, email provider) are filled in, decide whether to switch the workflow's build step to `RELEASE=1` (production release build) or keep it on the preview path deliberately.
- **Templates (DECISIONS #117):** the Hindi of the new site keys (`site.menu`, slider labels, `site.see_plans`, the three steps, `site.policyholders_heading`) is provisional. Does a slider inside a template preview fight the app's sideways template pager (`VerticalScrollLock` only hands over vertical drags)? Check on a phone.
- **Agency code on the site (DECISIONS #116):** onboarding no longer asks a profile-level agency code, and nothing else in the app edits `profiles.agency_code`, so About's "Show agency code" toggle has nothing to show for new agents. Should it show the code of the agent's (approved) company link instead, or be removed? Needs the user's call; until then it silently shows nothing.
- **Onboarding step 6 shows pending companies (DECISIONS #116):** the live site shows them only after an admin approves. The screen says so; confirm with the user that this is the wanted wording.
- **Google sign-in (DECISIONS #112–#114):** (1) the Google Cloud clients and the Supabase provider need the user (Next steps); (2) counsel: the v2 sign-up notice wording (Google as a source and processor); (3) the Hindi of the new onboarding and Welcome strings and the style explanations is provisional; (4) the iOS `GoogleSignIn` is still to build; (5) the user may want the designation shown differently ("Insurance agent" was chosen from "the designation is of agent").
- **Customer sign-up link (DECISIONS #108):** the Hindi review; counsel on autosaving after consent and on a guardian ticking a box on the web.
- **Another session's "Finish setup no longer blocks" change (CHANGELOG 2026-10-25)** cites "#105", but #105 is already "Admin self-protection and error statuses" (2026-10-23), and DECISIONS has no entry for it. That session should add one with the next free number (#109).
- **Git index:** `supabase/live-data/20261022180000_real_companies.sql` is staged as added while the deletion of `supabase/migrations/20261022180000_real_companies.sql` is unstaged (a sub-agent's accidental `git stash` / `stash apply` split the staged move; file contents are unchanged). Stage both together before committing.
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
| Supabase | **Project `AGENT_PRO`**, ref `rpqatijolcajehshoikc`, URL `https://rpqatijolcajehshoikc.supabase.co`, org **letssee** (free plan), **ap-south-1 (Mumbai)**, Postgres 17.11, created by the user on 2026-10-16. **10 migrations applied** (including `20261016220600_privacy.sql` and `20261016220836_catalog_sync.sql`, pasted in the SQL Editor by the user on 2026-10-17). Earlier batch: 7 through the connector, and `rpcs` by the user in the SQL Editor. **Verified:** functions and grants, 14 tables with RLS, 30+4 policies, 10 catalog CHECKs, realtime publication, `app_config` rows. **Advisors:** no errors; 11 WARNs accepted. **pg_cron** enabled 2026-10-25 for the daily `purge-customer-drafts` job (23:00 IST). The project also has Supabase's `rls_auto_enable` event trigger, whose API execute permission is revoked. The remote migration history has 7 connector-versioned entries (no `rpcs`), so run `supabase migration repair` before any `db push`. `mobile/local.properties` (git-ignored) holds the URL and the legacy anon JWT. The user's other org, Sledje-SCM, isn't used. The empty Tokyo project `agent_Pro` and a temporary `agent-pro` were removed by the user |
| Razorpay | No account or keys configured yet |
| Registrar | Not chosen |
| Hosting | **Cloudflare** (DECISIONS #59), account "Sledjesoftwares@gmail.com's Account", logged in locally through `npx wrangler@4.151.1 login`. **Last deployed 2026-10-25** (customer sign-up page, version `a10aa49a-c52d-4d07-a800-5166b6e706d3`, with the `JOIN_LIMIT` ratelimit binding; before that 2026-10-23 `461c4b3e`, 2026-10-18 `8a8ef744`; first deployed 2026-10-17): Worker `agentpro-sites` → `https://agentpro-sites.sledjesoftwares.workers.dev` (preview, demo data only; built with `SITE_BASE_URL` set to that URL). **As of 2026-10-28 (DECISIONS #118), deploys also happen automatically** via `.github/workflows/deploy-web.yml` on push to `main` (build + test + `wrangler deploy`, no per-deploy approval — the one exception to the rule below), once its GitHub Actions secrets are added (Open questions). Manual redeploy still works unchanged: build with `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SITE_BASE_URL` set, then `npx wrangler@4.151.1 deploy --config web/wrangler.jsonc`; every deploy outside the pipeline still needs the user's approval. No platform domain yet |
| Git | Remote `origin` = `git@github.com:Gunjan2244/agent_pro.git`. On `main`, the user committed and pushed `0e77dc7` ("everything documented") and `06ab83f` ("almost", 2026-10-17). No secrets are committed (scanned). **To clean up:** `.wrangler/` and `mobile/**/bin/` (IDE output) are committed but should be git-ignored. The agents never commit; the user decides commits and pushes. **New, uncommitted (2026-10-28):** `.github/workflows/deploy-web.yml` (DECISIONS #118) |
| Connectors | The claude.ai Supabase connector reaches org letssee. It disconnects at times; if so, re-enable it in claude.ai → Settings → Connectors |
