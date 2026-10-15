# UX — navigation, rules and screens

Every screen follows the **calm-screen rules** in DESIGN_SYSTEM.md. In short:
- one job and one primary action per screen,
- details one tap away,
- grey on white, with colour only for the main action and statuses.

## Navigation map
```
Splash ─ no session ──→ Welcome → Sign in ⇄ Create account → Check your email
       │                           └→ Forgot password → Reset sent
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
- **Create account:** next comes "Check your email to confirm", with Resend and Open email app.
- **Forgot password:** sends a reset email and shows "Reset link sent".

### Onboarding (3 steps)
Each step has a progress bar at the top and one primary button at the bottom.
1. **Profile:** name, mobile, designation and city. Photo, agency code and branch are under More details.
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
- **Empty state:** "Share your website to get enquiries" with **Share link**.

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

### Customers tab — `[ Customers | Dues ]`
There's a segmented control at the top. Dues lives here so the bottom bar stays at 4 tabs.

**Customers list**
- Search and filter chips: All · Overdue · Due this month · No policy.
- **Each row:** name, "2 policies · ₹45,000/yr", and a trailing `UrgencyChip`.
- **FAB `+`:** a sheet offering Add manually or Pick from contacts. The system contact picker needs no permission.

**Dues**
- Chips from `dueBuckets`: Overdue (n) · This week · This month · Next 3 months.
- One summary line: "₹1,24,500 due · 9 policies".
- Rows grouped by date. **Each row:** customer, plan, amount and `UrgencyChip`. Tapping opens Policy detail; a trailing Call icon is the one quick action.
- **Empty state:** "All caught up".

### Customer detail
- **Header:** name, phone and city, with an outlined action row: Call · SMS · Edit.
- **Tabs:** Policies · Details · Activity.
  - **Policies:** one `ListRow` per policy (plan, policy number, premium/mode, `UrgencyChip`), and **Add policy** as the primary button.
  - **Details:** plain label and value pairs.
  - **Activity:** a timeline of the source enquiry and payments.
- **Overflow menu:** Delete, with a confirm dialog that states how many policies go with it.

### Customer form
Name\* and mobile\* are always shown. Email, DOB, address, city, nominee and notes are under More details.

### Policy form
- **Always shown:** plan name\* (autocomplete), policy number\*, premium\*, mode\* (segmented control), start date\*.
- **Under More details:** sum assured, a next due date override (otherwise calculated automatically), status and notes.
- **Live summary** above Save: "₹12,000 every quarter · next due 15 Jan 2027".

### Policy detail
- A summary (plan, number, premium, mode, next due, status) and the payment history list.
- **Primary:** **Record premium paid**, which opens a sheet:
  - amount (prefilled) and date (today),
  - and the line "Next due becomes 15 Apr 2027".
  - **Record** saves it and shows Undo.

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

**Content screen**
- One row per section from the schema: icon, title, summary and a visibility switch. Hero and Contact are pinned on.
- **Reorder** in the top bar enables drag handles.

**Section editor:** a schema-driven `FormRenderer`.
- List fields show as rows with an "Add …" button, up to `maxItems`.
- Images go through the photo picker, a square crop and WebP at 1024px or less, then upload.
- Saving writes only the draft.

**Appearance screen**
- Template (opens the picker), an accent row of 8 swatches, and Site language (English / हिन्दी).
- **Template picker:** a full-height pager of the agent's real content in each template, with **Use this template**.

**Preview:** a full-screen WebView of the draft, with **Publish** in the top bar.

**Stats screen:** Views, Enquiries and Visit → enquiry %, plus one views chart.

### Link & domain
This screen has two sections.
1. **Free link:** `agentpro.site/a/{slug}`, with **Change link** (text button). The dialog validates and checks availability live, and warns that old links and QR codes will stop working.
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
| About | Privacy policy, Terms, Plan terms, Domain terms, Version |
| — | Sign out, Delete account (the user types DELETE) |

- **Notifications:** toggles for New enquiries, Follow-ups, Premium dues, and Plan & domain renewal. Plus a lead time of 3 / 7 / 15 days, and the digest time (default 9:00).
- **Export:** checkboxes and an optional date range, then **Export**. It saves a zip through the system file picker, or can be shared.

## Notifications UX
| Channel | When | Text | Tap |
|---|---|---|---|
| New enquiries (high) | Realtime while open; otherwise polled every 15 min | "New enquiry from Ramesh · Term insurance" | Opens the enquiry (Call action) |
| Follow-ups | At `follow_up_at` | "Follow up with Sunita today" | Opens the enquiry |
| Daily digest | Daily at the digest time; skipped if there's nothing to report | "3 premiums due this week (₹45,000) · 2 follow-ups today" | Opens Dues |
| Plan | 2 days and 1 day before the trial ends; 30, 7 and 1 days before the period ends; days 1, 7 and 14 of grace; on lapse | "Your free trial ends tomorrow · Continue for ₹499/year" / "Renew Agent PRO for ₹299 — 7 days left" / "Your site goes offline in 1 day" | Opens Plan & billing |
| Domain | Status changes (live or failed); renewal at 30, 7 and 1 days before expiry; on expiry | "rameshsharma.in is live" / "Renew rameshsharma.in — expires in 7 days" | Opens the domain screen |

`POST_NOTIFICATIONS` is requested after the first publish, following a short explanation, not on first launch.
