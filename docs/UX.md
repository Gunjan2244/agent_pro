# UX — navigation, rules and screens

Every screen follows the **calm-screen rules** in DESIGN_SYSTEM.md. In short:
- one job and one primary action per screen,
- details one tap away,
- grey on white, with colour only for the main action and statuses.

## Navigation map
```
Splash ─ no session ──→ Welcome → Sign in ⇄ Create account → Check your email
       │                           └→ Forgot password → Reset sent
       └ reset link (agentpro://auth-callback) ──→ Set new password → Home
       ├ session, no profile → Onboarding: 1 Profile → 2 Template → 3 Your link → Done
       └ ready ─────────────→ Main
Main bottom bar (4):  Home · Enquiries (badge: new) · Customers (badge: overdue) · Website
  Home ─→ Today lists · Insights · Setup checklist (sheet) · Settings (avatar)
  Enquiries ─→ Enquiry detail ─→ Convert → Customer form ; + Add enquiry
  Customers  [ Customers | Dues ] ─→ Customer detail ─→ Policy form / Policy detail ─→ Record premium (sheet)
                                ─→ Dues ─→ Policy detail
  Website ─→ Content ─→ Section editor
          ─→ Appearance (template, accent, language) ─→ Template picker
          ─→ Link & domain ─→ Change link · Domain search → Checkout → Setup progress → Domain details (renew)
          ─→ Stats · Preview · Share / QR (sheet)
  Settings ─→ Profile · Plan & billing (→ Checkout → Payment result · Receipts) · Notifications
              · Language · Appearance · Export · Change password · Privacy / Terms · Sign out · Delete account
Any screen (when lapsed) ─→ Plan ended screen ─→ Checkout · Continue read-only
```
- **Bottom bar labels** in English are Home · Enquiries · Customers · Website. In Hindi they're होम · पूछताछ · ग्राहक · वेबसाइट.
- The bar is built from `FeatureRegistry`.

**Deep links**
- `agentpro://enquiry/{id}`
- `agentpro://customer/{id}`
- `agentpro://policy/{id}`
- `agentpro://dues?bucket={key}`
- `agentpro://website`
- `agentpro://domain`

## Global UX rules
1. **Every data screen has four states** (`ScreenState`):
   - **Loading:** skeleton.
   - **Empty:** one sentence and one button.
   - **Error:** a message and Retry.
   - **Content:** with pull-to-refresh.
2. **Undo instead of "are you sure?".** Reversible actions apply straight away with a 5-second Undo snackbar.
   - Confirm dialogs only appear for deleting a customer, deleting the account, and paying.
   - Paying needs no extra dialog of its own: the checkout screen and Razorpay's own sheet already serve as the confirmation.
3. **Forms** (`FormRenderer`):
   - 6 or fewer fields are visible, with optional ones under **More details**.
   - Validation shows when a field loses focus, and each field gets the right keyboard.
   - The IME Next and Done buttons step through fields, and the save bar is sticky.
   - Leaving with unsaved changes asks "Discard changes?".
   - Phone numbers are normalised to +91.
4. **Thumb reach.** The primary action is an FAB or `BottomActionBar`, and the top bar has at most 2 icons.
5. **Search and filters.** Search is debounced by 300ms, and filter chips come from the catalog and show counts. The filter and sort are remembered per screen.
6. **Formatting.**
   - Money: ₹1,25,000, shortened to ₹1.2L or ₹3.4Cr on tiles and charts.
   - Dates: `15 Oct 2026`.
   - Times are relative in lists and absolute in detail screens.
7. **Offline.**
   - A thin `OfflineBanner` appears.
   - Save, publish and pay buttons are disabled, with the helper text "Needs internet".
   - A "Back online" snackbar shows when the connection returns.
8. **Accessibility.**
   - Contrast meets AA.
   - Tones come with an icon and a label.
   - Every icon button has a `contentDescription`.
   - Layouts hold at 200% font scale and in Hindi.
   - Charts have a table view, and TalkBack reads each row as one item.
9. **Language.** An EN/हिं switch sits in Settings and on Welcome. Agent-entered data is never translated.
10. **Read-only mode** (plan `lapsed`, BILLING.md):
    - A thin banner under the top bar reads "Your plan has ended · Renew", with Renew as a text button.
    - Edit actions (FAB, Save, Publish, status changes) stay visible but disabled. Tapping one opens a small sheet: "Renew to make changes · ₹299/year", with **Renew** as the primary button.
    - Viewing, calling customers and export keep working.
    - The banner is the only nag; there are no repeated pop-ups.

## Screens

### Splash → Welcome
- **Splash:** the logo on white while it routes. With a cached session, it opens Main within 2s even when offline.
- **Welcome:**
  - The logo and one line: "Your website, enquiries and customers — in one place".
  - **Create account** (primary) and **Sign in** (text button).
  - EN/हिं at the top right.
  - Nothing else.

### Sign in / Create account / Forgot password
- **Fields:** email and password (show/hide toggle). That's all.
- **Button:** disabled until the form is valid.
- **Errors:** inline and human ("Wrong email or password").
- **Create account:** a third element under the password, an **unticked, required checkbox** (never pre-ticked; `FieldType.Consent`, built by `FormRenderer`): "I'm 18 or older, and I agree to the **Terms** and the **Privacy notice**" (generated `agent_signup` text, EN/HI). "Privacy notice" opens the in-app **Notice screen**; "Terms" opens `<SITE_BASE_URL>/terms` in the browser (the Notice screen when no host is configured). The button stays disabled until the box is ticked; the request carries `data.consent = {notice_key, version, hash}` for the language shown. Next comes "Check your email to confirm", with Resend and Open email app.
- **Notice screen** (Create account link and Settings → Privacy notice): the notice's title, intro, one itemised block per data item (Data in bold, then Purpose and Kept) and the plain paragraphs, in the app language, rendered from the generated ordered texts.
- **Forgot password:** sends a reset email and shows "Reset link sent".

### Set new password
- **Opened by** the reset link in the email (`agentpro://auth-callback`, a recovery session). It's never reachable any other way.
- **One field:** new password, with a show/hide toggle and the same rules as sign-up (at least 8 characters).
- **One primary button:** "Save password". On success, go to Home and show a snackbar, "Password updated".
- **If the link has expired or is invalid:** show "This link has expired", with one button, **Send a new link**, which opens Forgot password.
- **If the agent leaves without saving,** they stay signed in. The password can still be changed later in Settings → Change password.
- **Details:**
  - While the link's session is being read, the Splash shows. The flow covers whatever screen was open.
  - Back (the top-bar arrow) leaves the flow. Leaving Reset sent ("Back to sign in") also leaves it.
  - The button is disabled while saving and offline ("Needs internet"). Errors are inline: "Choose a stronger password", and "Choose a password different from your old one".
  - If the reset session has run out by the time the agent saves, "This link has expired" shows.
  - The expired screen's text: "Reset links work once and only for a short time. Ask for a new one."

### Onboarding (3 steps)
Each step has a progress bar at the top and one primary button at the bottom.
1. **Profile:** name, mobile, designation and city. Photo, agency code and branch are under More details.
   - The photo uses the same picker, centred square crop and WebP upload as the hero photo (Phase 8); Continue waits while it uploads.
2. **Template:** a full-height pager rendering the agent's own content in each template. The template name sits below it, with **Use this template**.
3. **Your link:** a slug suggested from the name, with live validation and an availability check, and a preview of the full URL. Then **Publish now** (primary) or **Later** (text button).

Finishing shows a one-line snackbar: "Your 14-day free trial has started". No payment is asked for during onboarding.

### Home
Home has at most 3 blocks, top to bottom:
1. **Top bar:** "Namaste, {first name}", and an avatar that opens Settings.
2. **Today:** a plain list of up to 3 rows. Rows with a count of zero are hidden.
   - "{n} new enquiries"
   - "{n} follow-ups today"
   - "{n} premiums due this week · ₹x"

   With nothing due, a single line reads "Nothing due today".
3. **This month:** two `KpiTile`s side by side, "Enquiries" (with the change) and "Premium due (30d)". Below them, one chart card, "Enquiries per week", and a **See insights** link.

**While setup is incomplete,** a single compact row appears above Today: "Finish setup · 2 of 4". It opens a checklist sheet covering profile, publishing the site, sharing the link and adding a first customer.

**Plan row:** at most one, shown above Today only when it matters, and it opens Plan & billing.
- In the last 5 days of the trial: "Trial ends in 3 days · Continue for ₹499/year".
- During grace: "Plan ended · renew within 12 days to keep your site live" (warning tone).
- No plan row is shown otherwise.

### Insights
- A period filter row (30d · 90d · 12m), then chart cards in a single column.
- Each card has a title, a takeaway, the chart, and View as table.

| # | Chart | Form |
|---|---|---|
| 1 | Enquiries over time | Columns |
| 2 | Website views | Columns, plus a "Visit → enquiry" stat tile (no dual axis) |
| 3 | Pipeline by status | Horizontal bars, ordinal ramp, count and % |
| 4 | Enquiries by interest | Horizontal bars, sorted largest first |
| 5 | Premium collected vs due | Grouped columns, 2 series, legend |
| 6 | Customers by plan | Horizontal bars, top 6 plus Other |

### Enquiries — list
- **Top bar:** "Enquiries", search and sort icons.
- **Filter chips:** All · New (n) · Contacted · Follow-up · Converted · Closed.
- **Each row:**
  - **Title:** name, in bold with a small blue dot if unseen.
  - **Supporting line:** interest and relative time.
  - **Trailing:** a `StatusPill`.
- **FAB:** an icon-only `+`, labelled "Add enquiry" for TalkBack. It opens a short form with name, phone and interest, and source and message under More details.
- **Empty state:** "Share your website to get enquiries" with **Share link** (the system share sheet with the site link; hidden until a site exists).
- **As built (Phase 6):**
  - Every chip shows its count ("New · 3", "All · 9"). The search icon opens a search field under the top bar; it matches name, email and message, and phone digits once 3 or more are typed. A search or filter that matches nothing says "No enquiries match".
  - The sort icon opens a sheet: Newest first (default) · Oldest first · Name. The filter and sort are remembered.
  - The Enquiries tab badge counts unseen enquiries.

### Enquiry detail
Top to bottom:
1. **Name**, with the status pill and "12 Oct · via Website" underneath.
2. **Action row:** three outlined icon buttons labelled Call · SMS · Email.
3. **`NavRow`s:**
   - **Status:** opens a bottom sheet of status options. Choosing Follow-up asks for a date and time.
   - **Follow-up:** the date, when one is set.
   - **Notes:** a one-line preview that opens a notes editor with autosave.
4. **Details:** phone, email, interest and the message, as plain text with no card.
5. **Bottom bar:** **Convert to customer** (primary), which becomes **View customer** once converted.

Opening the screen sets `seen_at`. The overflow menu has Copy details and Delete (with Undo).

**As built (Phase 6):**
- The header line is the date the enquiry arrived (absolute, "16 Oct 2026") and its source. Call and SMS are disabled without a phone, Email without an email.
- A status change applies at once and shows "Status: Contacted" with **Undo** (5 s). The Follow-up sheet has a date field, a 24-hour time (default 10:00, Asia/Kolkata) and **Set follow-up**; opened from the Follow-up row it also has **Remove follow-up**. Setting a date on an open enquiry makes it a Follow-up. Only `follow_up_at` is stored; reminders are Phase 12.
- **Delete** soft-deletes at once and shows "Enquiry deleted" with **Undo**; when the snackbar ends the screen closes.
- The notes editor saves 800 ms after typing stops and again when it closes ("Saving…" / "Saved" under the field).
- **Convert to customer** opens the customer form prefilled with the name, mobile and email. **Save** creates the customer (linked by `source_enquiry_id`), sets the enquiry's `customer_id` and status Converted, and opens the new customer. A retry after a failed second step reuses that customer. Back on the enquiry, the bar reads **View customer**.
- Offline, Convert and the status, follow-up and delete actions are disabled ("Needs internet").

### Customers tab — `[ Customers | Dues ]`
There's a segmented control at the top. Dues lives here so the bottom bar stays at 4 tabs.

**Customers list**
- Search and filter chips: All · Overdue · Due this month · No policy.
- **Each row:** name, "2 policies · ₹45,000/yr", and a trailing `UrgencyChip`.
- **FAB `+`:** a sheet offering Add manually or Pick from contacts. The system contact picker needs no permission.
- **As built (Phase 7):** the search field is always shown above the chips (name, city, phone digits). Every chip shows its count. "Overdue" uses the catalog bucket; "Due this month" means a premium due in the next 30 days (the `this_week` and `this_month` buckets); a row's chip is the earliest due date of the customer's **active** policies, and the yearly amount counts active policies only. A picked contact opens the customer form prefilled (the number normalised to +91 when it's an Indian mobile). The Customers tab badge counts customers with an overdue premium. Empty state: "Add your first customer" with **Add customer**.

**Dues**
- Chips from `dueBuckets`: Overdue (n) · This week · This month · Next 3 months.
- One summary line: "₹1,24,500 due · 9 policies".
- Rows grouped by date. **Each row:** customer, plan, amount and `UrgencyChip`. Tapping opens Policy detail; a trailing Call icon is the one quick action.
- **Empty state:** "All caught up".
- **As built (Phase 7):** "All" is every bucket (up to 90 days); only **active** policies count. Each date group's header shows the date and its `UrgencyChip`, so a row keeps a single trailing element (Call): the row reads the customer, then "plan · ₹amount". `agentpro://dues?bucket=` opens this segment with that chip selected.

### Customer detail
- **Header:** name, phone and city, with an outlined action row: Call · SMS · Edit.
- **Tabs:** Policies · Details · Activity.
  - **Policies:** one `ListRow` per policy (plan, policy number, premium/mode, `UrgencyChip`), and **Add policy** as the primary button.
  - **Details:** plain label and value pairs.
  - **Activity:** a timeline of the source enquiry and payments.
- **Overflow menu:** Delete, with a confirm dialog that states how many policies go with it.
- **As built (Phase 7):** the tabs are a `SegmentedControl`. **Add policy** (the bottom bar) shows on the Policies tab only. A policy row reads the plan, then "No. 123 · ₹12,000 · Quarterly", with the `UrgencyChip` (or the status pill when the policy isn't active). Activity lists payments, "Customer added" and the source enquiry, newest first. Delete soft-deletes the customer and its policies, then closes the screen. The Policies tab is hidden when the `policies` flag is off.

### Customer form
Name\* and mobile\* are always shown. Email, DOB, address, city, nominee and notes are under More details.
- **First time only** (adding a customer manually, from contacts or by converting an enquiry; also **Add enquiry**): a bottom sheet with the `agent_customer_duty` text, **I understand** (primary; records `record_consent`, disabled offline with "Needs internet") and **Share privacy note** (shares a short message with `<SITE_BASE_URL>/a/<slug>/privacy`). Swiping it away leaves the form. It is remembered per account and version.
- **Under 18:** when the date of birth gives an age under 18, a line "Customer is under 18 — record a parent's or guardian's consent" and the required toggle "A parent or guardian has agreed" sit above Save; Save without it shows "Required". The consent is recorded (`guardian_consent`, with the customer's id) after the customer is saved; if that fails the form stays and Save retries it without adding a second customer. Editing a customer who was already under 18 doesn't ask again.

### Policy form
- **Always shown:** plan name\* (autocomplete), policy number\*, premium\*, mode\* (segmented control), start date\*.
- **Under More details:** sum assured, a next due date override (otherwise calculated automatically), status and notes.
- **Live summary** above Save: "₹12,000 every quarter · next due 15 Jan 2027".
- **As built (Phase 7):** the summary reads "₹12,000 · Quarterly · next due 15 Jan 2027" (the mode's catalog label, pending the user's confirmation). Plan name suggests up to 5 matching `planSuggestions` as you type. Mode defaults to the first catalog mode, status to Active. Without an override, the next due date is the first date on the schedule (start + whole periods) after the start date that is today or later: a policy started on 15 Oct 2026, quarterly, is next due on 15 Jan 2027.

### Policy detail
- A summary (plan, number, premium, mode, next due, status) and the payment history list.
- **Primary:** **Record premium paid**, which opens a sheet:
  - amount (prefilled) and date (today),
  - and the line "Next due becomes 15 Apr 2027".
  - **Record** saves it and shows Undo.
- **As built (Phase 7):** "Next due becomes …" uses the same rule as `mark_premium_paid` (`DueDateCalculator`). After **Record** the sheet closes and "Premium recorded" shows with **Undo** (5 s), which calls `undo_premium_payment`; if a newer payment exists the app says "Only the latest payment can be undone". **Record premium paid** is disabled offline and when the policy has no due date ("No due date set"). Edit is in the overflow menu; the customer is a row that opens Customer detail.

### Website tab (hub)
- **No site yet:** one sentence and **Create your website**.
- **Status card**, the only card on the screen:
  - the site URL (or custom domain) with a `StatusPill` (Live / Draft / Unpublished changes),
  - **Share** as the primary button,
  - and an overflow menu with Copy link, Open, Show QR code.
- **`NavRow` list:**
  - **Content:** "7 sections".
  - **Appearance:** template name and accent.
  - **Link & domain:** the current address, or "Get your own domain".
  - **Stats:** "120 views this week".
- **Sticky bar** whenever the draft differs from the published version: "Unpublished changes" with **Preview** (text button) and **Publish** (primary).
- **As built (Phase 8):**
  - The status is **Draft** until the first publish, **Unpublished changes** while `draft_content` differs from `published_content`, otherwise **Live** (with an icon each: hourglass / sync / check).
  - "7 sections" counts the visible sections; "120 views this week" is the last 7 days including today (Asia/Kolkata).
  - **Share** opens the system share sheet with the full link and ticks "Share your link" on Home's setup checklist. **Copy link** shows "Link copied"; **Open** opens the live link in the browser; **Show QR code** opens the Share / QR sheet (the QR code, the address, "Customers can scan this to open your website", Share and Copy link).
  - **Publish** shows "Your website is live". If `publish_site` refuses the draft, the Content screen opens with "Fix the marked sections, then publish again", each affected row says "Needs a fix before publishing" (error icon), and a snackbar says "Fix N things before publishing".
  - Save and Publish are disabled offline ("Needs internet").

**Content screen**
- One row per section from the schema: icon, title, summary and a visibility switch. Hero and Contact are pinned on.
- **Reorder** in the top bar enables drag handles.
- **As built:** the switch saves the draft at once. In Reorder mode, Hero stays first and Contact last (no handle); the other rows get a drag handle (TalkBack: Move up / Move down actions), and **Done** saves the new order. Summaries: the section's first text ("Ramesh Kumar"), else "3 added" for list/multi-select content, else "Not filled in yet" ("Always shown" for an empty pinned section), or "Hidden".

**Section editor:** a schema-driven `FormRenderer`.
- List fields show as rows with an "Add …" button, up to `maxItems`.
- Images go through the photo picker, a square crop and WebP at 1024px or less, then upload.
- Saving writes only the draft.
- **As built:** Save (sticky, "Needs internet" offline) validates every field first (required, maxLength, min/max, phone/email/url, maxItems); the editor then closes. Leaving with unsaved changes (back arrow or system Back) asks "Discard changes?". Opened after a refused publish, it lists the server's errors on top ("Fix before publishing · Headline: Required") and shows every field error at once. The photo is a **centred** square crop (no manual crop step); "Uploading photo…" shows while it uploads, and an unusable file says "This photo couldn't be used. Try another one." Content over the 64 KB limit says "Your website has too much text. Shorten a section and try again."

**Appearance screen**
- Template (opens the picker), an accent row of 8 swatches, and Site language (English / हिन्दी).
- **Template picker:** a full-height pager of the agent's real content in each template, with **Use this template**.
- **As built:** each choice is saved at once (no Save button). Template, accent and site language are columns the live site reads directly, so the screen says "Changes here show on your live website straight away." The picker renders the draft through `SitePreview` in each template (the `preview.webp` images aren't used in the app). Onboarding step 2 uses the same live render of the starter content.

**Preview:** a full-screen WebView of the draft, with **Publish** in the top bar.

**Stats screen:** Views, Enquiries and Visit → enquiry %, plus one views chart.
- **As built:** a `7 days | 30 days` segmented control (the last N days including today), Views and Enquiries tiles, a Visit → enquiry tile ("—" without views), and a "Views per day" column chart with its table view. It works offline from the cache. Enquiries count website enquiries only.

### Link & domain
This screen has two sections.
1. **Free link:** `agentpro.site/a/{slug}`, with **Change link** (text button). The dialog validates and checks availability live, and warns that old links and QR codes will stop working.
   - **As built (Phase 8):** below the link, the Share / QR content (QR code, Share, Copy link). The dialog checks the format and reserved words as you type, then `is_slug_available` after a 300 ms pause; **Change link** is enabled only for a new, available slug and shows "Link changed". A slug taken meanwhile shows "This link is taken". The custom-domain section (2) comes with Phase 10.
2. **Custom domain.** What it shows depends on the state:
   - **Trial or lapsed plan:** "Your own domain is available on the paid plan", with **See plan**.
   - **None (paid plan):**
     - One line: "Get your own domain, like yourname.in. The price is set by the registrar."
     - **Search domains** (primary).
     - "Already own a domain? Connect it" (text button).
   - **Setting up:** a status pill and a **View progress** row.
   - **Live:** the domain, a Live pill, "Renews 15 Oct 2027", and a **Domain details** row.
   - **Renew soon / Expired:** a warning or danger pill and **Renew domain** (primary). It opens Domain details with a live quote.

### Domain search
- A search field prefilled with name-based suggestions.
- **Results** show only allowed TLDs, with **live prices from the registrar**. **Each row:** the domain, the total per year ("₹749 / year"), and a **Select** button. Unavailable domains are shown greyed out as "Taken".
- Sorted cheapest first, with 5 shown and **Show more** after that.
- While loading, the rows show skeletons. If the registrar can't be reached: "Couldn't fetch prices" with Retry.

### Checkout (one screen for every payment)
- **`PriceSummary`** varies by what's being bought:
  - **Service, first year:** "Agent PRO — 1 year · ₹499 (incl. GST)", with "Starts after your trial ends on 29 Oct" during the trial, or "Renews at ₹299/year".
  - **Service renewal:** "Agent PRO renewal — 1 year · ₹299", with "Your plan will run until 15 Oct 2028".
  - **Domain:** two lines, "rameshsharma.in — 1 year (registrar) ₹650" and "Service fee ₹99", then **Total ₹749 (incl. GST)**. Underneath: "Renews at about ₹650 + ₹99 / year · price set by the registrar".
- **Domain only:** registrant details, which the registrar requires and which are prefilled from the profile. Name, email and mobile are visible; address, city, state and PIN are under More details and required.
- A checkbox: "I agree to the terms" (linking the plan or domain terms).
- **Primary button:** **Pay ₹{total}**, which opens Razorpay checkout (UPI, cards, netbanking, wallets).
- **Quote expired (domain, after 15 minutes):** "Price updated: ₹759", with **Continue**.
- **Results:**
  - **Success:** a Payment result screen ("Payment received · Receipt APRO-2026-000123"), then Setup progress for a domain or back to where the agent came from for the service.
  - **Cancelled:** "Payment cancelled — you weren't charged".
  - **Failed:** "Payment failed" with **Try again**.

### Domain setup progress
- **`ProgressStepper`:** Payment received → Registering domain → Connecting to your site → Securing (HTTPS) → Live. It updates in real time.
- One line at the bottom: "This can take up to 30 minutes. You can leave this screen — we'll notify you."
- **When live:** "rameshsharma.in is live" with **Share** (primary).
- **On failure:** a plain explanation and "Your ₹749 has been refunded", with **Choose another domain**.

### Domain details
- **Shows:** the domain, status, registered date and expiry date.
- **Primary button:** **Renew for ₹{live total}**. It fetches a live quote (registrar renewal price plus fee) when the screen opens, and is enabled from 60 days before expiry until the end of the registrar's grace period.
- **Payment history rows** open receipts.
- **Connect my own domain** (separate flow): step-by-step DNS records with copy buttons, and **Check now** (primary). It costs nothing in v1 (`pricing.domain.connectInr = 0`).

### Plan & billing
- **Status block:** a `StatusPill` from `planStatuses`, with one line of explanation:
  - **Trial:** "Free trial · ends 29 Oct 2026".
  - **Active:** "Active until 29 Oct 2027".
  - **Grace:** "Ended 29 Oct · site stays live until 13 Nov".
  - **Lapsed:** "Ended · your site is offline".
- **Primary button**, one at most:
  - **Continue for ₹499/year** during the trial, or when lapsed without ever having paid.
  - **Renew for ₹299** during the renew window, grace or lapse after a paid year.
  - **None while active and outside the window;** the screen then shows "Renewal opens on 30 Aug 2027".
- **What's included:** a short list of 4 lines (website, enquiries & customers, reminders & insights, eligibility for a custom domain).
- **Rows:** **Receipts** (a list of date, item, amount and status; each opens a receipt with Share PDF) and **Plan terms**.

### Plan ended screen (lapsed)
- Shown once per app session on launch while lapsed. It's full-screen and calm:
  - the headline "Your plan has ended" (display size),
  - one line: "Your data is safe. Renew to put your website back online and make changes."
- **Renew for ₹299** (primary), or **Continue for ₹499** if they never paid.
- "Continue read-only" (text button) enters read-only mode (Global rule 10).

### Settings
| Group | Entries |
|---|---|
| Account | Profile, Change password |
| Plan | Plan & billing (shows the plan's status pill) |
| Preferences | Language, Appearance (System / Light / Dark), Notifications |
| Data | Export CSV |
| About | Privacy notice (built: opens the Notice screen), Privacy policy, Terms, Plan terms, Domain terms, Version |
| — | Sign out, Delete account (the user types DELETE) |

- **Notifications:** toggles for New enquiries, Follow-ups, Premium dues, and Plan & domain renewal. Plus a lead time of 3 / 7 / 15 days, and the digest time (default 9:00).
- **Export:** checkboxes and an optional date range, then **Export**. It saves a zip through the system file picker, or can be shared.

### Planned screens (designed, not built)
The full specs are in OFFERS.md ("Screens") and PRIVACY.md; they move into the sections above as they're built.
- **Companies and offers (OFFERS.md):**
  - Onboarding stays 3 steps: step 1 Profile gains a required **Company** picker (with "My company isn't listed"; choosing a company makes Agency code required), also in Settings → Profile. **An admin verifies the link** (DECISIONS #53): the agent uses the app at once with custom offers only, and Settings → Profile shows the status (Waiting for confirmation · Confirmed · Not confirmed with the reason and **Try again** · No longer linked). One notification when the link is approved or rejected.
  - Website hub: a new **Offers** `NavRow` ("2 on your site") opens **Place offers**, a full-height preview in slot mode ("Tap a highlighted spot to add an offer") with a **List** action for the accessible path.
  - Tapping a slot opens the **Offers picker** sheet (the company's live and upcoming offers, the agent's own offers, **Add your own offer**, Remove, and the section's Position row). Choosing saves the draft with Undo.
  - **Custom offer form** (6 fields, including the confirmation toggle), shown on the site with "Details provided by the agent".
  - Ended offers: greyed in the preview, an "Ended" pill in the editor, one Today row on Home ("1 offer on your site has ended"), a digest line, and a "Published · 1 ended offer isn't shown · Fix" snackbar.
- **Privacy (PRIVACY.md):**
  - Built (P1-4): the Create account checkbox, the Notice screen, the one-time customer-data sheet and the guardian-consent toggle (see Sign in / Create account and Customer form above).
  - Customer and Enquiry detail overflow: **Share privacy note**, **Export this person's data**, **Erase permanently** (a confirm dialog, like deleting a customer).
  - A **Privacy requests** list (from the website's request form), with due dates and the actions above.
  - Enquiries tab overflow: **Clear old enquiries** (closed or not converted, older than a chosen age; count, confirm, bulk erase). Nothing is deleted automatically: the agent decides (DECISIONS #54).
  - Settings gains **Your data** (Download my data, Nominate someone, Contact the grievance officer, Delete account) under Data, and About gains **Privacy & grievance**. The CSV export warns that the file contains customers' personal data.

## Notifications UX
| Channel | When | Text | Tap |
|---|---|---|---|
| New enquiries (high) | Realtime while open; otherwise polled every 15 min | "New enquiry from Ramesh · Term insurance" | Opens the enquiry (Call action) |
| Follow-ups | At `follow_up_at` | "Follow up with Sunita today" | Opens the enquiry |
| Daily digest | Daily at the digest time; skipped if there's nothing to report | "3 premiums due this week (₹45,000) · 2 follow-ups today" | Opens Dues |
| Plan | 2 days and 1 day before the trial ends; 30, 7 and 1 days before the period ends; days 1, 7 and 14 of grace; on lapse | "Your free trial ends tomorrow · Continue for ₹499/year" / "Renew Agent PRO for ₹299 — 7 days left" / "Your site goes offline in 1 day" | Opens Plan & billing |
| Domain | Status changes (live or failed); renewal at 30, 7 and 1 days before expiry; on expiry | "rameshsharma.in is live" / "Renew rameshsharma.in — expires in 7 days" | Opens the domain screen |

`POST_NOTIFICATIONS` is requested after the first publish, following a short explanation, not on first launch.
