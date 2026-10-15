# Testing and verification

## Mobile app (Kotlin Multiplatform)
- Run `cd mobile && ./gradlew verifyShared :androidApp:assembleDebug :androidApp:lint :composeApp:testDebugUnitTest`.
  - `verifyShared` is part of `check`.
  - In the iOS phase, on a Mac, add `:composeApp:iosSimulatorArm64Test`.
- **Unit tests** live in `commonTest` so they run on every target (`domain/`, `core/`):
  - `DueDateCalculator`: every premium mode, month-end dates (31 Jan + 1 month), leap years, and policies started in the past.
  - `StatsAggregator`: weekly and monthly buckets, conversion rate, empty data, timezone (Asia/Kolkata).
  - `CsvWriter`: RFC 4180 quoting (commas, quotes, newlines), UTF-8 with BOM so Excel shows Hindi correctly.
  - `Money`: Indian grouping (1,00,000 and 12,34,567), compact ₹ (1.2L, 3.4Cr).
  - Validators: Indian phone normalisation, email, maxLength, slug.
  - Catalog codegen: generated enums match the JSON, and missing i18n keys fail.
- **ViewModel tests** use Turbine with fake repositories.
- **Screenshot tests (Roborazzi):** the component gallery in light, dark, EN, HI and 200% font scale.
- **Compose UI tests** (`runComposeUiTest` in `commonTest`): `FormRenderer` validation, the Convert-to-customer flow, and the Undo snackbar.
- **Platform seams:** fakes of every `core/platform` interface are used in tests. Android implementations get instrumented smoke tests (notification posted, worker enqueued, file exported).

## Database (run with `execute_sql` after each migration)
1. `set role anon;` then `select` from each table. It must fail or return nothing.
2. `submit_enquiry` on a published site returns `{ok:true}` and inserts a row. With the honeypot filled it returns fake success and inserts nothing. The 6th submission within an hour is rejected, and an invalid interest is rejected.
3. Isolation: inside a transaction, `set local request.jwt.claims` to agent A, then confirm agent B's rows aren't visible or updatable.
4. `mark_premium_paid` advances the due date correctly for each mode. `undo_premium_payment` restores it.
5. `get_public_site` returns nothing for an unpublished site and leaks no private fields.
6. Run the advisors (security and performance) and get a clean report.

## Service plan (Razorpay **test mode**; move the time by editing `subscriptions` dates)
1. **Sign-up** creates a `subscriptions` row with a 14-day trial, and `my_entitlement()` returns `trial`.
2. **Pay ₹499 during the trial:** `current_period_end` = trial end + 1 year. Pay after the trial: now + 1 year. The receipt appears in Plan & billing → Receipts.
3. **Renew ₹299** inside the window: the end date extends by exactly 1 year from the current end. Renew is disabled outside the window.
4. **Lapse.**
   - **Period end + 1 day:** `grace`. The site is live and the Home row and notification show.
   - **Period end + 16 days:** `lapsed`.
     - `get_public_site` returns null, and the web page shows unavailable.
     - `submit_enquiry` is rejected.
     - RLS blocks inserts and updates, while select and export still work.
     - The app shows the Plan ended screen, then read-only mode.
   - **Renewing** restores everything immediately.
5. **Tampering:** a client-sent amount is ignored, and a modified price can't be paid.

## Payments pipeline and domains (Razorpay **test mode**, registrar sandbox)
1. **Domain search** returns live registrar prices plus the fee, and creates `domain_quotes`. Paying with an expired or foreign `quote_id` is rejected. A changed registrar price produces a new quote.
2. **Purchase, happy path:** `orders.step` moves through `registering → configuring → ssl_pending → live`. The progress screen updates live, the site resolves on the new host with HTTPS, and a receipt appears.
3. **Kill the app straight after paying:** the webhook alone must complete fulfilment, with no duplicates.
4. **Replay** the webhook and send verify twice: exactly one capture and one registration (idempotency).
5. **A bad signature** on verify or the webhook is rejected, and nothing changes.
6. **Force a registrar failure:** `failed`, then `refunded`. Razorpay shows the full refund of price plus fee, and the app shows "refunded".
7. **Cancel in checkout:** nobody is charged, and the order becomes `cancelled` after 30 minutes.
8. **Domain renewal:** with expiry 7 days away, check the reminder and the `renew_soon` pill. A live renewal quote and payment extend `expires_at` by 1 year. Domain renewal is allowed even when the plan is lapsed.
9. **Domain expiry:** the host stops resolving, and the free link still works (if the plan is active).
10. **Gating:** domain search and connect are refused on `trial` and `lapsed` plans.
11. **RLS:** an agent can read only their own `orders`, `payments`, `subscriptions`, `domain_quotes` and `site_domains`, and can't write them.
12. **Connect own domain:** DNS instructions are shown, **Check now** updates the status, and the domain goes live.

## Web
- Run `sh scripts/build-web.sh && python3 -m http.server -d web/dist 8080`.
- Open `/?s=<test-slug>` in all three templates and in both locales.
- Submit the form and confirm the row appears in `enquiries`.
- Check host resolution, either by sending a custom `Host` header to a local proxy or by testing on a Vercel preview with a test domain.
- Run Lighthouse on mobile and reach at least 90 for Performance, Accessibility and SEO.

## End to end (emulator)
1. Sign up, confirm the email, and complete onboarding.
2. Publish the site and open it in a browser.
3. Submit an enquiry. It should appear in real time with a notification. With the app killed, the poll worker should notify within 15 min.
4. Set a follow-up and confirm the reminder fires.
5. Convert the enquiry to a customer, add a policy, and record a payment. Check that Dues and Insights update and that Undo works.
6. Switch to Hindi and dark mode, and check the layouts at 200% font size.
7. Turn on airplane mode: the data is still visible, the offline banner shows, and the save buttons are disabled.
8. Export the zip and open the CSVs in a spreadsheet app; Hindi text must display correctly.
9. Delete the test account; its data and media should be gone.
