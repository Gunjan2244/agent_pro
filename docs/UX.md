# UX — navigation, rules and screens

Every screen follows the **calm-screen rules** in DESIGN_SYSTEM.md. In short:
- one job and one primary action per screen,
- details one tap away,
- grey on white, with colour only for the main action and statuses.

## Navigation map
```
Splash ─ no session ──→ Welcome → Continue with Google (account chooser) ─→ session
       │                         → Sign in ⇄ Create account → Check your email (code)
       │                           └→ Forgot password → Enter the code → Set new password
       └ reset link (agentpro://auth-callback) ──→ Set new password → Home
       ├ session, no profile or no site → Onboarding: 1 Your mobile → 2 Style → 3 Design → 4 Your work → 5 Your link → Done
       │   ("no site" is confirmed with the server first; an agent who has one goes to Main)
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
   - The focused field is highlighted and kept above the keyboard with the next field visible under it, so the agent can move down the form (to the submit button) without closing the keyboard.
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
  - **With Google sign-in** (DECISIONS #112; the build has `GOOGLE_WEB_CLIENT_ID`): the sign-up consent checkbox (unticked, required; the same `agent_signup` line as Create account, with its Terms and Privacy notice links), then **Continue with Google** (an outlined button with Google's "G"; disabled until the box is ticked and while offline), then **Sign up with email** and **Sign in** (text buttons).
    - Tapping it opens the phone's Google account sheet (one tap); if the phone offers none there, the full "Sign in with Google" dialog, which can add an account. Picking an account signs in, or creates the account with Google's name and email, and the gate moves on (onboarding for a new agent). Closing the sheet shows nothing.
    - Errors (inline, above the button): "There's no Google account on this phone. Add one in your phone's settings, or sign up with email." and "We couldn't sign you in with Google. Try again, or sign up with email."; offline and too-many-tries keep their usual messages.
  - **Without it:** **Create account** (primary) and **Sign in** (text button).
  - EN/हिं at the top right.
  - Nothing else.

### Sign in / Create account / Forgot password
- **Fields:**
  - **Sign in:** email and password (show/hide toggle). That's all.
  - **Create account:** full name, mobile (normalised to +91), email, password (at least 8 characters) and **Confirm password**, which must match ("Passwords don't match"). Both password fields have the show/hide toggle. The name and mobile go into the sign-up metadata, and the signup trigger saves them in the profile, so onboarding doesn't ask for them again (DECISIONS #70).
  - Create account's text button is **"Sign in instead"**, so it doesn't read as a second submit button. Sign in's is "Create account".
- **Button:** disabled until the form is valid.
- **Errors:** inline and human ("Wrong email or password").
- **Create account:** a third element under the password, an **unticked, required checkbox** (never pre-ticked; `FieldType.Consent`, built by `FormRenderer`): "I'm 18 or older, and I agree to the **Terms** and the **Privacy notice**" (generated `agent_signup` text, EN/HI). "Privacy notice" opens the in-app **Notice screen**; "Terms" opens `<SITE_BASE_URL>/terms` in the browser (the Notice screen when no host is configured). The button stays disabled until the box is ticked; the request carries `data.consent = {notice_key, version, hash}` for the language shown. Next comes "Check your email to confirm", with Resend and Open email app (see **Resend** below).
- **Signed in automatically after sign-up** (DECISIONS #70):
  - If Supabase's Confirm email setting is off, sign-up starts the session, and the app goes straight to onboarding with no "Check your email".
  - Otherwise "Check your email" says "We sent a code to … Enter it below to confirm your email." It has a **Code** field (6 to 10 characters; kept as text so a leading zero survives), **Verify** in the bottom bar, Resend beside it and an **Open email app** text button. A right code confirms the email and starts the session, so the gate moves on to onboarding. A wrong or expired code shows "That code is wrong or has expired. Check it, or ask for a new one." and the field stays. Nothing is held in memory: no links, no password kept (DECISIONS #88).
  - The same applies after Sign in → "Resend confirmation email".
- **Sign in with an unconfirmed email:** under "Confirm your email first", a text button **Resend confirmation email** opens "Check your email" for that address and sends a new code at once.
- **Notice screen** (Create account link and Settings → Privacy notice): the notice's title, intro, one itemised block per data item (Data in bold, then Purpose and Kept) and the plain paragraphs, in the app language, rendered from the generated ordered texts.
- **Forgot password:** sends a reset email with a code and shows "Enter the code" (same layout as Check your email: Code field, **Verify**, Resend, Open email app). A right code starts a recovery session and opens Set new password.
- **Resend** ("Check your email" and "Enter the code"; DECISIONS #67):
  - Supabase sends at most one email per address per minute, so after each send the text button reads **"Resend in 42 s"** (disabled) and counts down to **Resend**. "Code sent again" is a snackbar.
  - If Supabase still asks to wait ("after N seconds"), the countdown restarts at N with no error message.
  - If the project's hourly email quota is used up, the snackbar says "We can't send more emails right now. Try again in about an hour", and Resend opens again after a minute.
  - Other failures (offline, unknown) show their message, and Resend can be tapped again at once.
  - Under the line there's a muted hint: "Not there? Check your spam folder".
  - The same quota message replaces "Too many tries. Wait a minute" on Create account and Forgot password.

### Set new password
- **Opened by** the reset code (Enter the code → Verify, a recovery session). A reset *link* from an older email (`agentpro://auth-callback`) still opens it too.
- **One field:** new password, with a show/hide toggle and the same rules as sign-up (at least 8 characters).
- **One primary button:** "Save password". On success, go to Home and show a snackbar, "Password updated".
- **If the link has expired or is invalid:** show "This link has expired", with one button, **Send a new link**, which opens Forgot password.
- **If the agent leaves without saving,** they stay signed in. The password can still be changed later in Settings → Change password.
- **Details:**
  - While the link's session is being read, the Splash shows. The flow covers whatever screen was open.
  - Back (the top-bar arrow) leaves the flow. Back on Enter the code returns to Forgot password.
  - The button is disabled while saving and offline ("Needs internet"). Errors are inline: "Choose a stronger password", and "Choose a password different from your old one".
  - If the reset session has run out by the time the agent saves, "This link has expired" shows.
  - The expired screen's text: "Reset links work once and only for a short time. Ask for a new one."

### Onboarding (7 steps)
Each step has a progress bar at the top and one primary button at the bottom. The order shows the agent their website before asking about companies (DECISIONS #113), and the finished website, with their photo and companies, before the link (DECISIONS #116).

**Only for an agent with no site** (DECISIONS #83). Step 7 creates the site, and an agent has exactly one, with a link customers may already have. So the app never offers to create a site without first checking that none exists:
- **Before onboarding opens:** with a complete profile, an empty-looking cache isn't trusted. The gate reads the cache afresh (its live queries trail a sync by a moment) and then asks the server. A site there opens Main; no answer from the server shows the startup error with Retry, never onboarding.
- **After step 1:** with an incomplete profile, onboarding opens on step 1 as usual. Continue saves the name and mobile and then asks the server; if a site exists, Main opens with it, skipping the rest. If the server can't be asked, step 1 stays with the error.
- **While onboarding is open:** if the agent's site arrives (a late sync) once the profile is complete, onboarding closes and Main opens.
- In each case the existing site, its link and its template are untouched, and there's no "trial started" message.
1. **Your mobile number:** the name (from Google, or from Create account) shows as a **card** with the account's email under it and an **Edit** text button; Edit brings back the name field. Without a name, the name field shows instead. Then one line, "Customers will call and WhatsApp you on this number from your website.", and the **Mobile** field (prefilled when Create account gave it). Nothing else is asked here.
   - **Always the first step**, even when both are saved, so the agent sees them. A sign-up consent left pending by Continue with Google is recorded first (DECISIONS #112); if that fails, the step shows the error and saves nothing.
2. **Choose your template** (the template grid; DECISIONS #120, replacing the #115 intro and countdown): a muted line, "Tap a design to see it with your name.", then **all 5 templates** in the grid described under Template grid below. There is no bottom bar: tapping a tile opens step 3 on that template. Back returns to step 1.
3. **That template's preview** (top bar: the template's name): a hint line, "This is your website with starter text. You'll add your companies next, and you can change every word later.", then the template rendered live, full height, with **the agent's own seeded starter content** (`StarterContent.from(profile, template, locale, userId, insuranceTypes)`, DECISIONS #78, #93), filling the space (the name is only in the top bar). There are no companies yet, so the seed's default types (life) are used; after step 4 the preview follows the agent's types (Back from step 4 shows them).
   - **Make website from this →** (primary; the arrow nudges forward and back, still when animations are off) takes this template and opens step 4. **See other templates** (text button) and Back return to the grid.
   - **Scrolling the page** (DECISIONS #116): the page scrolls freely; a drag that starts vertical stays with the page until the finger lifts.
4. **Your work:** one line, "Add the companies you work with and the insurance you sell with each. Your website's services will match them.", then **Your companies** (below), then the **City**. There is **no More details** (DECISIONS #116): the agency code is asked per company (below), the branch isn't asked, and the photo has its own step. Continue waits for at least one company and saves the city.
   - **Your companies** (required, DECISIONS #89): at least one company. Continue stays disabled until there is one pending or approved company link, and "Add at least one company to continue" shows while there is none. It is a block of **cards**, one per company (name, status pill, a status line, the agency code, the insurance types, and the actions its status allows; the same cards as Settings → Profile, below), then **Add company**.
   - **Add company** opens a sheet: **Choose your company**. A search field shows when there are more than 8 companies; one row per company from `list_companies()` (initials avatar, the name in the app language; one already added shows "Already added" and can't be picked); and **My company isn't listed** at the end ("Type its name. We will check it and add it."), which works even when the list can't load. Picking one opens the form (a FormRenderer form): a listed company asks for the **agency code** (required, at most 30 characters; the admin verifies it) and the **insurance types** (chips from the catalog, at least one); a company that isn't listed asks for its **name** (required, at most 80) and the types. **Save** calls `request_company`, closes the sheet and shows the new card as "Waiting for confirmation". Back returns to the list.
   - **Saved as each is added, not on Continue** (each add, change and removal is one RPC), so the list is the server's truth, survives the app closing between steps, and Edit and Remove have real ids to act on. A failed call keeps the sheet open with the error ("No internet connection", "Renew to make changes", "You have already added this company", "Choose at least one type of insurance"); Save and Add company are disabled offline with "Needs internet". If the first load fails, the block shows the error with Retry.
   - **No designation** is asked anywhere (DECISIONS #114): the site says "Insurance agent", editable in the website's About section.
5. **Your photo** (DECISIONS #116): one line, "Customers trust a face they can see. Your photo goes at the top of your website.", then the photo slot with the **photo tips** as three short helper lines (docs/IMAGES.md, "Asking agents for their photo": face and shoulders looking at the camera in daylight by a window; a plain background and smart everyday clothes; no sunglasses, group photos, insurer uniform or logos). With no photo the button is **Add photo** (the picker) and a **Skip** text button goes on without one; once a photo is uploaded the button reads **Continue** and saves it. The photo uses the same picker, **Adjust your photo** editor (below, Website tab, Photo editor) and WebP upload as the hero photo; the buttons wait while it uploads. Skip after removing a photo saved earlier removes it from the profile.
6. **Your website** (DECISIONS #116): "This is your website with your photo and companies. Scroll to look around; you can change anything later.", then the site rendered full width (SitePreview) so it scrolls like the real page: step 3's design with the agent's starter content, the step-5 photo, and **every pending or approved company** in "Companies I represent" with its insurance types. The public site lists only approved companies (DECISIONS #90), so while one is still waiting a muted line says "Your companies show on your live website once we confirm you're their agent." **Continue** opens step 7; Back returns to step 5. Nothing is created yet.
7. **Your link:** a slug suggested from the name, with live validation and an availability check, and a preview of the full URL. Then **Publish now** (primary) or **Later** (text button). Both create the site with step 3's design, seeded from step 4's companies, in the app's current language.

**Site is live** (after **Publish now**, DECISIONS #71, #116): two screens run before Home, in one scaffold whose progress bar slides between them. Back goes one screen back, and on the first it skips ahead. (Step 6 already showed the site, so there is no preview screen here.)
1. **Add it to your phone:** a home-screen tile preview with the agent's initials and the link. **Add to home screen** opens the install sheet in the browser and moves on; **Not now** skips.
2. **Share it with customers:** the QR code, the short link and an **Open** text button (the site in the browser), then **Share** (other apps) and **Done**, which goes to Home.

"Later" (a draft) skips these, since there's nothing live to add or share.

Finishing shows a one-line snackbar: "Your 14-day free trial has started", after the last screen. No payment is asked for during onboarding.

### Home
Home has at most 3 blocks, top to bottom:
1. **Top bar:** "Namaste, {first name}", and an avatar that opens Settings. The avatar shows the agent's photo (the website's hero photo, else the profile photo), with the initials underneath while it loads or when there is no photo.
2. **Today:** a plain list of up to 3 rows. Rows with a count of zero are hidden.
   - "{n} new enquiries"
   - "{n} follow-ups today"
   - "{n} premiums due this week · ₹x"

   With nothing due, a single line reads "Nothing due today".
3. **This month:** two `KpiTile`s side by side, "Enquiries" (with the change) and "Premium due (30d)". Below them, one chart card, "Enquiries per week", and a **See insights** link.

**While setup is incomplete,** a single compact row appears above Today: "Finish setup · 2 of 5". It opens a checklist sheet covering profile, publishing the site, **making your About section your own** (done when `about.bio` in the draft is not blank and is none of the seeded bio variants in any language, so unique text can rank), sharing the link and adding a first customer, plus (each only when its feature is on) **Add your companies** and **Give a customer their login** (DECISIONS #103).
- **Every step is tappable (DECISIONS #105, supersedes the next-only rule of #103).** The first undone item, in order, shows a primary icon and title with "Next step" under it as the suggestion, but no step blocks another. Order: Profile, Companies, Publish, About, Share, First customer, Customer login. Items whose feature is off are hidden and not counted ("1 of 7").
- **Each item opens its screen:** Profile and Companies → Settings → Profile; Publish → the Website tab; About → the About section editor, with a soft prompt card at the top ("Replace this text with a few lines in your own words…"; shown only when opened from this list); Share → Link & domain; First customer → the Customers tab with the new-customer form; Customer login → the Customers tab (pick a customer; no hint).
- **When an item ticks:** Companies when the agent has a pending or approved company link, or a "not listed" request (checked online once; it stays undone offline); First customer once any customer exists; Customer login once an invite has been sent from this device (a local mark, like Share).
- **What's new: walkthrough and sheet (DECISIONS #103, #121; CLAUDE.md, Rolling out to existing users):** how agents who already have a website learn about new things. New sign-ups never see either one: finishing onboarding marks everything seen. Signing out doesn't reset it.
  - **Versioned items:** each item (`WhatsNewItem`) records the release that brought it (`since`). An agent sees only the items newer than the version they last saw, and only those whose feature is on. **Any way out marks them all seen:** Show me, Not now, Got it, closing the sheet, tapping a row, or Back. So nothing repeats, and later items still show.
  - **Important item → full-screen walkthrough.** If any new item is important, all the new items show as **full-screen pages on the first Home of the app run**, important ones first, before the agent does anything else. Important means it changes their public website, needs them to act or decide, adds a required step, or moves something they use.
    - **Page layout:** top bar "What's new" (no back arrow), "n of N" when there are several pages, a round tinted icon, the title and a short line.
    - **Buttons:** **Show me →** (the arrow nudges) opens the item's screen and ends the walkthrough. **Next** turns the page, and on the last page it reads **Not now** and returns to Home. Back counts as Not now.
    - It opens once per app run and never comes back once seen.
  - **Only small items → the Home sheet:** a bottom sheet "What's new" with one row per item (each opens its screen) and **Got it**. It never shows over the checklist sheet.
  - **Items:**
    - **Version 1** (sheet rows): **Add your companies and insurance types** (→ Settings → Profile), **Give customers a login** (→ Customers), **"For my customers" on your website** (→ Website → Content).
    - **Version 2, important:** **5 new website designs**, "Your website has a fresh new look. See all five designs and pick the one you like." Show me → Website → Appearance → Template (the template grid, #120).
    - **Version 3, important:** **Your website now has pages**, "Home, Insurance (with a page for each plan), About me and Contact. Your menu links them, and each page can be found on Google." (DECISIONS #122.) Show me → Website → the agent's website preview. An agent who saw version 1 sees the version 2 and 3 pages; one who saw version 2 sees only this one. One who saw nothing gets 5 pages, the important ones first.
    - **Version 4, small (sheet row):** **Adjust your photo: zoom, move it and see how customers will see it** (DECISIONS #124). Show me → Website → Content → Hero (the section editor with the photo). An agent who saw version 3 gets just this row in the Home sheet; one who saw less gets it as a page after the important ones.

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
- **FAB `+`:** the **Add customer** sheet with up to three options (DECISIONS #108): **Fill it in myself** (the customer form), **Pick from contacts** (the customer form, prefilled; the system picker needs no permission) and **Send the customer a link** (only while the `customerSignup` flag is on; disabled with "Needs internet" offline). The link option first passes the one-time customer-data sheet (`agent_customer_duty`), then opens "Making the link…", calls `customer_signup_link()` and replaces itself with the live screen. If it fails the screen says why with **Retry**: no published website ("Publish your website first, then send a link"), too many open links ("Delete some from Incomplete forms"), the feature unavailable, or the network error.
- **Incomplete forms · N:** a row above the search field, only while there is at least one unfinished form (also when there are no customers yet). It opens the Incomplete forms screen.
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
  - **Details:** plain label and value pairs, then (while the `customerPortal` flag is on) the **Website login** card below.
  - **Activity:** a timeline of the source enquiry and payments.
- **Website login card (Details tab, under the fields; DECISIONS #96–#100):** lets the agent give this customer a login to the agent's website. Online only; it reads `portal_status` when the screen opens. States:
  | State | Card shows | Actions |
  |---|---|---|
  | Not set up | "Let {name} see their policies on your website" | **Send login link** |
  | Invited | "Link valid until {date}" | **Send again**, **Turn off** |
  | Active | "Signed in {relative time}" or "Not signed in yet"; "Also shows: {names}" (the other customers who have activated their own link on this login) | **Send new link (resets PIN)**, **Turn off** |
  | Locked | "Locked after too many wrong PINs" | **Send new link** |
  Sending calls `portal_invite` and opens the **share sheet** with a prefilled Namaste message and the link (`…/my/start?t=<token>`, WhatsApp or SMS; the app never sends it). **Turn off** always asks first ("{name} will no longer be able to sign in. You can send a new link any time.").
  - **Website login is off:** when the server reports the site's login unavailable (`portal_available` false: the Contact switch is off, or the site is not live), the card keeps working but adds the warning "Customer login is off on your website. Turn on “Show ‘My policies’ login link” in Website → Content." with a tertiary **Open Website content** button (opens the Content screen). Sending a link stays allowed; the customer then sees "This page isn't available" until the switch is on.
  - **Blocked:** no valid mobile number → "Add a mobile number first" with **Edit customer**; no published site → "Publish your website first"; offline → the buttons are disabled with "Needs internet"; the flag off on the server → "Website login isn't available right now". While loading the card shows a skeleton; a failed load shows the error and **Retry**; a failed send shows its message inline.
  - **Known gap:** the link always uses the free `/a/<slug>/` address, even when the site has a custom domain (the app's `Site` model has no domain field yet).
  - Copy: sentence case, no jargon, "PIN", "link", "website login".
- **Overflow menu:** Delete, with a confirm dialog that states how many policies go with it.
- **As built (Phase 7):** the tabs are a `SegmentedControl`. **Add policy** (the bottom bar) shows on the Policies tab only. A policy row reads the plan, then "No. 123 · ₹12,000 · Quarterly", with the `UrgencyChip` (or the status pill when the policy isn't active). Activity lists payments, "Customer added" and the source enquiry, newest first. Delete soft-deletes the customer and its policies, then closes the screen. The Policies tab is hidden when the `policies` flag is off.

**Incomplete forms** (top bar "Incomplete forms"; empty state "No unfinished forms")
- Newest first (by `updated_at`). Each row: the name typed so far or "No name yet", a supporting line, and a trailing delete icon.
  - The agent's own form: "Filled in by you · 12 minutes ago". Tap resumes it in the customer form.
  - A customer link: "Customer link · On the form now" (the customer's page was seen within `CustomerSignup.presenceSeconds`), "· Opened 20 minutes ago", "· Not opened yet" or "· Link expired". Tap opens the live screen.
- **Delete** is a soft delete with the **Undo** snackbar "Form deleted" (Global rule 2: no confirm dialog); disabled offline. A form that has become a customer leaves the list. Forms not finished within `Retention.customerDraftsDays` (30) are deleted by the server.
- The relative times and "On the form now" refresh every 10 seconds while the screen is open.

**Customer link (live screen)** (title: the name typed so far, or "Customer link")
- Right after the link is made: a card with the link, "The link works until 23 Oct 2026", **Share link** (the share sheet with "Hello! Please fill in your details here so I can add you as a customer: <link>") and **Copy link** ("Link copied"). The token is only known at that moment (it is never stored): a screen opened later from Incomplete forms shows **Send a new link** instead (`customer_signup_renew`, then the share sheet; disabled with "Needs internet" offline). Refusals show one red line under the buttons.
- The presence line: "On the form now" / "Last seen 5 minutes ago" / "Not opened yet" / "Link expired 23 Oct 2026".
- Until the customer ticks the privacy notice: "Waiting for the customer to agree to the privacy notice" (no field is stored before that).
- Then the seven fields (name, mobile, email, date of birth, address, city, nominee) read-only and filling in live (realtime on `customer_drafts` while the app is open); the field the customer is in is highlighted with "Typing…" while they are on the form.
- When the customer submits the server adds the customer: the screen turns into "Added as a customer" with **Open customer** (the customer's detail). The Customers list and the Incomplete forms row update at once.

### Customer form
Name\* and mobile\* are always shown. Email, DOB, address, city, nominee and notes are under More details.
- **Autosave (adding a customer, also from contacts or when resuming):** after the first non-empty change and a 1.5 s pause, what is typed is saved into a draft (`source = agent`): inserted once, then updated. A small note at the top of the form says "Saved", "Not saved (offline)" or "Not saved"; a failed save is retried on the next change. A picked contact is only saved once something is changed. Leaving the form (Back) keeps the draft, so there is no "Discard changes?" in Add mode; it is listed under Incomplete forms and resumes with its values. Save creates the customer as before, then marks the draft completed (tried twice). If that still fails, the new customer opens anyway and the draft stays under Incomplete forms, where it can be deleted: a saved customer never holds the agent on the form. Edit and Convert don't autosave.
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

### Customer login pages (the agent's website, `/a/<slug>/my`; DECISIONS #96–#100)
Not part of the app: the customer sees these in a browser or the installed web app (WEB_RENDERER.md, "Customer login (portal)"). They use the website theme (white background, the agent's accent, 3 spacing values, 4 type sizes), work without JS, are mobile-first (one column, 48 px targets) and exist in English and Hindi.
- **Sign in:** "Sign in to see your policies"; mobile and 6-digit PIN; **Sign in**. Below: "New here or forgot your PIN? Ask {name} to send you a login link" with a WhatsApp button (a new link also resets the PIN). Errors are inline and generic ("That mobile number and PIN don't match. Try again."); a lock says "Too many tries. Wait {minutes} minutes, or ask {name} for a new link", a hard lock "This login is locked. Ask {name} to send you a new link."; too many requests says "Too many requests. Please try again later."
- **First login (from the agent's link), "Set up your login":** the link's token is moved out of the address bar on first load, so the address shows the bare `…/my/start` page. Mobile, new PIN, confirm PIN (six digits; obvious PINs, repeats, runs and the customer's date of birth are refused), the unticked consent checkbox with "How my data is used" (the privacy page's portal section), **Save and continue**. A used, expired or burned link shows "This link has expired or was already used. Ask {name} for a new one."
- **Home:** "Hello {first name}"; a **dashboard row of tiles** (replaces the old "Next premium" card): **Policies**, **Premium per year**, **Total cover** (hidden when 0), **Next due** (the date with its due-status pill; "nothing due" when none) and **People** (only when a family shares the login); two columns on phones, four from 720 px, with the Next due tile's border tinted when overdue or due soon; a button **Add "My policies" to your home screen** (only where the browser can install); **Your policies** (a card each: plan, number, premium and mode, sum assured, next due with its pill; **Payments**, **Ask about this policy** and **I've paid this premium** unfold in place); **Your advisor** (Call, WhatsApp); **Your details** with **Ask to correct** and **Ask to delete**; **Sign out** and **Sign out on all devices**. A family shares one login: each customer gets their own "Policies of {name}" list and details card. A confirmation line shows after a question ("Sent. {name} will get back to you.") or a request ("Request sent. {name} will contact you."); the agent gets the question as an enquiry with the source "Site login".
- **On the agent's site (DECISIONS #102):** where the login is on, the site itself tells customers about it: a small **Customer login** link at the top of the hero (it never moves the page), a section just above Contact headed "Already my customer?" ("See your policies online, any time, on your phone.", four short points, a **Sign in to My policies** button and a "First time? Ask me for your login link." WhatsApp line; the agent sees it in Content as **For my customers**) and **My policies** in the footer. The text promises only what the login does today: policies, the next premium, recorded payments and asking about a policy (reminders only once they exist). The agent can move or hide the section in Content; each template draws it in its own style (WEB_RENDERER.md, "For my customers").
- **Offline (installed app):** the last home page with "Offline — last updated {time}"; with none cached, "You're offline. Connect to the internet to continue."
- **Not available:** a site that's unpublished, lapsed or has the login switched off shows the generic "This page isn't available".

### Customer sign-up page (the agent's website, `/a/<slug>/join`; DECISIONS #108)
Not part of the app: the customer opens the link the agent shared (WEB_RENDERER.md, "Customer sign-up page"). Website theme, mobile first, EN + HI, no script needed to read or submit.
- **First visit, "Before you start":** "{name} has asked you to fill in your details here. Please read how they are used." The notice (what, why, who, how long, rights), a "How my data is used" link to the site's privacy page, an unticked checkbox ("I agree that {name} saves the details I type in this form, as I type them, to add me as a customer, as described in the notice.") and **Start**. No form is shown until the customer agrees.
- **The form, "Your details":** "Fill in your details. What you type is saved as you go, and {name} adds you as a customer when you press Submit." Fields: Name*, Mobile number* (hint "A 10-digit Indian mobile number."), Email, Date of birth, Address, City, Nominee (optional ones say "(optional)"). A status line under the fields: "Saving…", "Saved" or "Not saved — check your connection" (it retries by itself); it never stops the customer submitting. Opening the same link again resumes with the saved values.
- **Under 18:** as soon as the date of birth makes the person a minor, a "Parent or guardian" block appears with the notice line and a checkbox ("I am this person's parent or guardian, and I agree to this on their behalf."). Submit without it shows "A parent or guardian must tick the box for someone under 18."
- **Submit:** field errors show under the field with the typed values kept ("Please enter your name.", "Enter a valid 10-digit mobile number.", "Enter a valid email address.", "Enter a real date of birth."). Success goes to **Thank you.** "{name} has your details and will be in touch." (with Call / WhatsApp).
- **Link expired or already used:** "This link has expired or was already used. Ask {name} for a new one." with Call and WhatsApp to the agent. A site that is unpublished, lapsed or has the feature off shows "This page isn't available". Too many requests: "Too many requests. Please try again in a minute."

### Website tab (hub)
- **No site yet:** one sentence and **Create your website**.
- **Status card**, the only card on the screen:
  - the site URL (or custom domain) with a `StatusPill` (Live / Draft / Unpublished changes),
  - **Share** as the primary button,
  - and an overflow menu with Copy link, Open, Add to home screen (only once the site has been published), Show QR code.
- **`NavRow` list:**
  - **Content:** "7 sections".
  - **Appearance:** template name and accent.
  - **Link & domain:** the current address, or "Get your own domain".
  - **Stats:** "120 views this week".
- **Sticky bar**, always shown once the site exists: **Publish** (primary; enabled only while the draft differs from the published version, and online) and **My website →** (text button; opens the preview).
- **As built (Phase 8):**
  - The status is **Draft** until the first publish, **Unpublished changes** while `draft_content` differs from `published_content`, otherwise **Live** (with an icon each: hourglass / sync / check).
  - "7 sections" counts the visible sections; "120 views this week" is the last 7 days including today (Asia/Kolkata).
  - **Share** opens the system share sheet with the full link and ticks "Share your link" on Home's setup checklist. **Copy link** shows "Link copied"; **Open** opens the live link in the browser; **Add to home screen** (DECISIONS #65) opens `<link>/?install=1` in the default browser (not a Custom Tab, which can't install), where a sheet offers **Install** or, when the browser has no prompt yet, the steps (⋮ → Install app; on iPhone, Share → Add to Home Screen); **Show QR code** opens the Share / QR sheet (the QR code, the address, "Customers can scan this to open your website", Share and Copy link).
  - **Publish** shows "Your website is live". If `publish_site` refuses the draft, the Content screen opens with "Fix the marked sections, then publish again", each affected row says "Needs a fix before publishing" (error icon), and a snackbar says "Fix N things before publishing".
  - Save and Publish are disabled offline ("Needs internet").
- **On the public site (Add to home screen, DECISIONS #65):** the Contact section has a ghost button, "Add to home screen", shown on phones (and on desktops once the browser offers to install). The agent turns it off in Content → Contact → **Show "Add to home screen" button** (default on). The installed app is named after the agent and opens full-screen at their site. The icon is a white head-and-shoulders figure on the site's accent colour. WEB_RENDERER.md has the details.

**Content screen**
- One row per section from the schema: icon, title, summary and a visibility switch. Hero and Contact are pinned on.
- **Field-less section (`companies`, "Companies"):** listed, switchable and reorderable like the others, but it has no editor: the row isn't tappable and its line reads "Shows the companies an admin has approved. Manage them in Settings → Profile." ("Hidden" when switched off). A saved order that doesn't name it yet gets the row right after the schema section before it (`about`).
- **Field-less section (`portal`, "For my customers"; DECISIONS #102):** the same kind of row: movable and hideable, no editor, **before Contact by default** (a saved order that doesn't name it gets it right before `contact`). It controls the website's "For my customers" section only; the hero's "Customer login" link and the footer link stay while the login is on, and the **Show "My policies" login link** switch in Contact turns all three off together. The row shows a note like Companies'.
  - Field-less sections show a one-line note instead of an editor: `companies` says approved companies are managed in Settings → Profile; `portal` ("For my customers") says customers can sign in to see their policies, only when customer login is on, with access given from the customer's page; any other field-less section shows no note.
- **Services hint (DECISIONS #93):** while the draft's services don't match the agent's insurance types (same rule as Settings → Profile; the screen loads the company links when it opens), a card on top says "Your website's services don't match the insurance you deal in." with **Update services**, which opens the same "Update your website to match?" sheet. It also covers sites made before the services followed the types. Hidden while reordering; the button is disabled offline.
- **Offers row (DECISIONS #104, step 1):** after Services in the default order, listed, switchable and reorderable like the others. With nothing picked, the site shows the companies' live offers automatically.
- **Reorder** in the top bar enables drag handles.
- **As built:** the switch saves the draft at once. In Reorder mode, Hero stays first and Contact last (no handle); the other rows get a drag handle (TalkBack: Move up / Move down actions), and **Done** saves the new order. Summaries: the section's first text ("Ramesh Kumar"), else "3 added" for list/multi-select content, else "Not filled in yet" ("Always shown" for an empty pinned section), or "Hidden".

**Section editor:** a schema-driven `FormRenderer`.
- List fields show as rows with an "Add …" button, up to `maxItems`.
- Images go through the photo picker, the **Photo editor** (below) on the field's aspect, then WebP at 1024px or less, then upload.
- Saving writes only the draft.
- **As built:** Save (sticky, "Needs internet" offline) validates every field first (required, maxLength, min/max, phone/email/url, maxItems); the editor then closes. Leaving with unsaved changes (back arrow or system Back) asks "Discard changes?". Opened after a refused publish, it lists the server's errors on top ("Fix before publishing · Headline: Required") and shows every field error at once. The photo is cropped in the **Photo editor** (DECISIONS #124); "Uploading photo…" shows while it uploads, and an unusable file says "This photo couldn't be used. Try another one." Content over the 64 KB limit says "Your website has too much text. Shorten a section and try again."

**Photo editor ("Adjust your photo"; DECISIONS #124):** opens full screen after the agent picks a photo for an image field with an `aspect` (the hero photo in the section editor, the profile photo in onboarding step 5), before anything is uploaded.
- **Top bar:** "Adjust your photo", back arrow (cancel: nothing is uploaded, the old photo stays).
- **Hint:** "Pinch or use the slider to zoom, and drag to move it. Only the bright part goes on your website." When the site shows the photo round, a second line: "Keep your face inside the circle: most designs show your photo round."
- **Crop surface:** a 4:3 area with the photo (wider than tall, so the previews fit on the first screen); the upload frame (the field's aspect) is outlined in white, everything outside it dimmed, on a dark surround in both themes; a thin white circle inside the frame when a `shownAs` shape is round. Pinch zooms around the fingers (1× to 4×), drag moves; the frame always stays filled. The photo opens at the old default (centred, zoom 1), so doing nothing gives the same result as before.
- **Zoom row:** "Zoom" + a slider (the accessible way to zoom; TalkBack reads "Zoom") + **Reset** (back to centred, disabled when already there).
- **How customers see it:** one live preview per schema `shownAs` shape, 72 dp tall, each with its label: **Round, at the top** (circle), **About me page** (4:5), **Wide cover** (4:3). They follow every move.
- **Bottom bar:** **Use this photo** (uploads the crop; "Uploading photo…" as before), **Choose another** (reopens the picker).
- **While the photo opens:** a skeleton in place of the surface; Use waits. A file that can't be opened skips the editor, and the upload shows "This photo couldn't be used. Try another one."

**Offers editor (Website → Content → Offers; DECISIONS #104 step 1):** the section editor has the section title, **Offers on your site** and **Your own offers**. The first is a row, not a list: "Automatic: your companies' live offers" when nothing is picked, else "{n} on your site"; it opens the picker below. (The hero editor's **Featured offer** row says "None" or "Chosen" and opens the picker in featured mode.) The rows are muted when the form is disabled. When the picker returns, the editor refreshes these two fields from the saved draft.
- **Your own offers** is the list field: title, summary, end date (not more than a year ahead, as `publish_site` checks; the date is stored as YYYY-MM-DD), call to action, interest, and the toggle **I confirm this offer is accurate and my company allows it**, which must be on to publish. A custom offer whose details aren't confirmed shows "Confirm the details before publishing". The site marks these "Details provided by the agent".

**Offers picker ("Offers on your site"; route `OfferPickerRoute(section, field)`)**: every change saves the draft at once (no Save button; the picker closes with Back).
- **On your site:** one row per added offer (title, summary, "Ends {date}", chips **Featured**, **Ended** or **No longer available**). The ⋮ menu has **Edit**, **Set as featured** / **Not featured**, **Move up**, **Move down** and **Remove**. Removing the featured offer clears the hero's featured offer. With none, the text says nothing is added and the site shows the companies' live offers automatically.
- **From your companies:** the live offers from `my_company_offers` (title, summary, "Ends {date}", company name; the name never appears on the site), each with one-tap **Add**; it saves a reference, so admin edits and endings flow through. At the cap (`maxSectionItems`, 6) Add is disabled with "Remove one to add another (max 6)". **Add your own offer** opens the Offers section editor.
- **Edit on a company offer** asks "Make your own copy?" ("Editing makes this your own copy. It won't update when the company changes it, and your site will show 'Details provided by the agent'.", **Make a copy**). It then becomes an item in Your own offers prefilled with the company's text (ends on capped at today + 366 days, "enquire", the company offer's interest, **not confirmed**), the reference and any featured reference point to the copy, and the Offers section editor opens on it. Refused with "You already have the most own offers allowed. Remove one first." when 6 own offers exist. **Edit** on an own offer opens the same editor.
- **Featured mode** (from the hero): radio rows **None**, the offers on your site, and the company offers; choosing saves and closes.
- **States:** skeleton while loading; offline shows "Needs internet" with **Retry** (own offers still list); an error shows **Retry**; with no approved company, "Offers come from the companies you're approved with" and **Open Settings → Profile**; with a company but nothing live, "No offers from your companies right now". A company offer that's gone from the live list shows "No longer available" (an ended one isn't told apart from a removed one yet).

**Appearance screen**
- Template (opens the picker), an accent row of 8 swatches, and Site language (English / हिन्दी).
- **Template picker** ("Choose a template"; DECISIONS #120): a muted line, "Tap a design to see your website in it.", then the template grid (below) with the site's current template marked by a primary-colour border and "Current" after its name. A tap opens **Template preview**: the agent's real draft as it would look in that template (live render, top bar the template's name; the preview card fills the whole space, with no name or description repeated under it), with **Use this template →** (the arrow nudges; needs internet) and **See other templates** (back to the grid, like Back). Using it saves and returns to Appearance; nothing changes until then. Switching **to** an offers template (spotlight or guide) from one of the other three also un-hides `offers` and moves it right after `hero` in the draft, once (the draft is saved first, then the template); other sections stay as they are, and switching back (or between the two offers templates) changes nothing in the draft. The Content list shows the `offers` section (Offers editor below, DECISIONS #104); its `sectionOrder` and `hidden` entries are kept by every save and by reordering.
- **Template grid** (onboarding step 2 and the Template picker; DECISIONS #120): every template as a tile, **2 per row**, each row sized so **2 rows show whole and about a tenth of the third peeks out** (row height = the grid's height ÷ 2.1), so the agent can tell there is more. A tile is a card holding the template's **full-page picture** (a sample agent's page, `template_page_<key>_<n>.webp`; until it loads, a wash of the template's accent), with the template's name below. The whole tile is one tap target; drags scroll the grid.
  - **Auto-scroll:** 4 s after every template's picture has loaded, the tiles scroll **one at a time, in order**: a tile's picture slides up at a steady pace (`Motion.TEMPLATE_SCROLL_DP_PER_SECOND`, about 10–15 s per template) until the page's end shows, jumps straight back to the top, and the next tile starts; after the last, the first again, for as long as the grid is open. Coming back to the grid starts over with the 4 s wait. Nothing moves when animations are off.
- **As built:** each choice is saved at once (no Save button). Template, accent and site language are columns the live site reads directly, so the screen says "Changes here show on your live website straight away." The picker renders the draft through `SitePreview` in each template (the `preview.webp` images aren't used in the app). Onboarding steps 3 and 6 use the same live render of the starter content. **Real offers:** every preview (onboarding steps 3 and 6, the picker, Website → Preview) shows at once, then re-renders with the company's real offers and disclaimer when `preview_site_offers` answers; offline or on any error it stays as is, with no error shown (DECISIONS #84). **After a switch:** previewing a template of the other family shows the draft as it would be after switching (to "Best offers": `offers` un-hidden after `hero`; to "Meet me": unchanged), and nothing is saved until **Use this template** (DECISIONS #86).

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
| Account | Profile (built: your companies), Change password |
| Plan | Plan & billing (shows the plan's status pill) |
| Preferences | Language, Appearance (System / Light / Dark), Notifications |
| Data | Export CSV |
| About | Privacy notice (built: opens the Notice screen), Privacy policy, Terms, Plan terms, Domain terms, Version |
| — | Sign out, Delete account (the user types DELETE) |

- **Profile (built, DECISIONS #89):** for now the screen holds **Your companies**: the same cards as onboarding step 4 (Your work), one per company link, with a status pill from the catalog (`membershipStatuses`: Waiting for approval, Approved, Not approved, Removed; the pill is the only place the status is worded) and, where there is something extra to say, one detail line:

| Status (pill) | Detail line | Actions |
|---|---|---|
| Waiting for approval | "Requested 16 Oct" | **Edit types**, **Cancel request** (asks first, then `withdraw_company_request`) |
| Approved | none | **Edit types** (`set_membership_types`, no new review), **Remove** (asks first: "Its offers will leave your website.") |
| Not approved | "Reason: {reason}" (from `membershipRejectReasons`) and the admin's note | **Try again** (the form opens prefilled and sends a new `request_company`) |
| Removed | the admin's note, if any | **Request again** (same) |

  Each card also shows the agency code and the types ("Life, Health"). **Add company** works as in onboarding. Changing the types is a sheet with just the types field. To change an agency code, remove the company and add it again. The profile's own fields (name, photo and so on) aren't editable here yet.
- **"Update your website to match?" (DECISIONS #93):** after each successful Add company, Edit types, Cancel request, Remove or Try again, if the agent has a site whose draft services no longer match their insurance types (the union over pending and approved links), a sheet opens **once** for that change:
  - "Add these services: Motor insurance, Travel insurance" (the seeded interests of each type that has no service on the site yet, with their seeded descriptions in the site's language; the items stop at the list's maximum of 8, the rest are still offered);
  - "Remove services you no longer deal in": one chip per service whose type the agent no longer has, **none ticked** (removal is opt-in; `other` never counts);
  - "Your own text stays as it is. Publish to show the changes on your live website." The hero, About, FAQ and the agent's own service descriptions are never changed;
  - **Update website** (saves the draft only; disabled offline with "Needs internet", or when there is nothing to add and nothing ticked) and **Not now**. A failed save keeps the sheet open with the error; success says "Services updated. Publish to make them live."
  - "Not now" closes it until the next change; Website → Content keeps the hint below. An agent with no type at all, or no site, never sees it.
- **Notifications:** toggles for New enquiries, Follow-ups, Premium dues, and Plan & domain renewal. Plus a lead time of 3 / 7 / 15 days, and the digest time (default 9:00).
- **Export:** checkboxes and an optional date range, then **Export**. It saves a zip through the system file picker, or can be shared.

### Planned screens (designed, not built)
The full specs are in OFFERS.md ("Screens") and PRIVACY.md; they move into the sections above as they're built.
- **Companies and offers (OFFERS.md):**
  - Onboarding has a required **Company** picker on step 4, Your work (DECISIONS #113; it was step 1 Profile) (with "My company isn't listed"; choosing a company makes Agency code required), also in Settings → Profile. **An admin verifies the link** (DECISIONS #53): the agent uses the app at once with custom offers only, and Settings → Profile shows the status (Waiting for confirmation · Confirmed · Not confirmed with the reason and **Try again** · No longer linked). One notification when the link is approved or rejected.
  - Built (DECISIONS #104 step 1): the list editor in Website → Content → Offers and the hero's Featured offer row (see Website tab). Still planned: the hub **Offers** `NavRow` ("2 on your site") opens **Place offers**, a full-height preview in slot mode ("Tap a highlighted spot to add an offer") with a **List** action for the accessible path.
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
