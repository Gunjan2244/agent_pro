# Status — read this first

_Last updated: 2026-10-15_

## Current state
- **Design is complete and documented.** No application code, Supabase project or deployment exists yet.
- The repo holds the README, CLAUDE.md and `docs/`.
- **Latest design changes (2026-10-15):**
  - The theme is now **"Clean Professional"**: near-white, uncluttered, following the calm-screen rules.
  - The bottom bar has 4 tabs, with Dues moved inside Customers.
  - **Platform:** Kotlin Multiplatform with Compose Multiplatform. Android ships first, and iOS is a later phase (DECISIONS #22).
  - Strict tokens: **3 spacing values (8/16/24)** and **4 type sizes (14/23/39/65 sp, ratio 1.67)**. The fonts are Inter and Noto Sans Devanagari (DECISIONS #19).
  - **Service plan:** a 14-day trial, then ₹499 for the first year and ₹299/yr to renew. After that, 15 days of grace, then the site goes offline and the app becomes read-only (BILLING.md).
  - **Custom domains** are an extra cost: the live registrar price plus a fixed fee (₹99, to be confirmed). Payment is through Razorpay (DOMAINS.md).

## Next steps
1. Get the user's answers on the open questions marked ❗ below (registrar, the domain fee amount, Play Billing). Phases 1–8 can start without them.
2. **Phase 1:** create the `shared/` JSON files exactly as specified in [SHARED_DEFINITIONS.md](SHARED_DEFINITIONS.md). Then scaffold the Kotlin Multiplatform project in `mobile/` (`composeApp` + `androidApp` + `build-logic`) with the `generateShared` and `verifyShared` tasks.
3. This needs the Android SDK, which isn't installed. **Ask the user before installing it.**

## Roadmap
Tick items as they're done and add the date.

- [ ] **1. Shared definitions and codegen**
  - [ ] `shared/catalog.json` (including `pricing` and `domainStatuses`), `site-schema.json`, `design-tokens.json`, `templates/index.json`, `i18n/en.json`, `i18n/hi.json`
  - [ ] `mobile/` KMP scaffold (`composeApp` with commonMain/androidMain/iosMain stubs, `androidApp`), version catalog
  - [ ] `mobile/build-logic`: `generateShared` and `verifyShared`
- [ ] **2. Design system:** theme (light/dark), components (DESIGN_SYSTEM.md), `FormRenderer`, debug gallery
- [ ] **3. Backend core**
  - [ ] Create the Supabase project `agent-pro`. **Confirm the cost first.**
  - [ ] Migrations: tables, RLS, RPCs, storage, realtime
  - [ ] SQL verification and a clean advisors report
- [ ] **4. Web renderer:** core, 3 templates, enquiry form, host resolution, `build-web.sh`, `vercel.json`. Test locally; deploying needs approval.
- [ ] **5. App foundation:** auth, onboarding, Room/`SyncEngine`, `NetworkMonitor`, `FeatureRegistry`, nav (4 tabs)
- [ ] **6. Enquiries** (list, detail, add, convert)
- [ ] **7. Customers, policies and dues** (Customers tab with the Customers | Dues segment, record-payment sheet)
- [ ] **8. Website hub** (Content, Appearance, Link, Stats, Preview, Publish, QR)
- [ ] **9. Service plan and payments** (BILLING.md)
  - [ ] `subscriptions`, `orders`, `payments`, `entitlement()`/`can_write()`, RLS write gating, lapse handling in the public RPCs
  - [ ] Edge Functions `billing` and `razorpay-webhook`, plus `cron` (plan reminders, stale orders)
  - [ ] Secrets (the user enters them), Razorpay test-mode webhook
  - [ ] App `feature/billing`: `EntitlementRepository`, `FeatureGate`, read-only mode, Plan & billing, Checkout, Payment result, Receipts, Plan ended screen
  - [ ] Test-mode end-to-end run (TESTING.md)
- [ ] **10. Custom domains** (DOMAINS.md)
  - [ ] Choose the registrar ❗ and confirm the fee and TLDs ❗
  - [ ] Edge Function `domains` (live quotes into `domain_quotes`), registrar and Vercel adapters, domain fulfilment, `cron` DNS/SSL/renewal
  - [ ] App `feature/domains`: search, progress, details (renew), connect
  - [ ] Test-mode end-to-end run: purchase, refund on failure, renewal, expiry
- [ ] **11. Home and Insights** (including the plan row)
- [ ] **12. Notifications and workers** (enquiries, follow-ups, digest, plan and domain reminders)
- [ ] **13. Settings and export** (including Plan & billing and Delete account)
- [ ] **14. Hindi, accessibility and screenshot passes, end-to-end run**
- [ ] **iOS phase**
  - [ ] `iosApp` Xcode project; real `iosMain` implementations for every `core/platform` seam
  - [ ] APNs/FCM push, because background polling on iOS is unreliable
  - [ ] StoreKit or Apple's rules for selling the plan
  - [ ] App Store review. This needs a Mac with Xcode
- [ ] **Later:** auto-renewal for the plan and domains, FCM push on Android, server-side rendering

## Open questions / blockers
- ❗ **Registrar:** ResellerClub vs Vercel Domains vs another. It must support live pricing, register, renew and DNS through an API for `.in`, `.co.in` and `.com`.
- ❗ **Domain service fee:** confirm the ₹99 (incl. GST) default, charged on both purchase and renewal.
- ❗ **Google Play payments policy:** selling the ₹499/₹299 plan in the app most likely needs Play Billing, or India's user-choice/alternative billing. Domain sales need checking too. Resolve before release (BILLING.md, Compliance).
- **Data retention** for accounts that stay lapsed for a long time: keep forever, or delete after N months with notice?
- **GST:** the GSTIN, and whether receipts must be tax invoices.
- **Accounts:** Razorpay account and KYC; a paid Vercel plan.
- **Content:** domain terms, privacy policy and terms pages.
- Android SDK isn't installed.
- Supabase project creation is pending the user's cost confirmation.
- The `planSuggestions` list needs the user to review it for accuracy.

## Environment
| Item | State |
|---|---|
| Java | OpenJDK 21 installed |
| Gradle | System Gradle present (the project will use the Gradle wrapper) |
| Android SDK | **Not installed**. Only `~/.android/avd` exists |
| Mac / Xcode | Not needed until the iOS phase. This machine is Linux, so iOS targets can't be built here |
| Supabase | Org "Sledje-SCM" has `sledje-scm` (active, another product) and `sledje_plus` (paused). `agent-pro` **not created yet** |
| Razorpay | No account or keys configured yet |
| Registrar | Not chosen |
| Hosting | Vercel chosen, nothing deployed |
| Git | branch `main`; docs not yet committed |
