# Privacy — DPDP Act 2023 and DPDP Rules 2025

**Status: designed; P1-1 (shared definitions and codegen), P1-2 (backend; not applied to `AGENT_PRO` yet), P1-3 (web) and P1-4 (app) built 2026-10-17, P2 planned (see [Roadmap](#roadmap)).** The whole product (app, agent websites and their enquiry form, backend, admin panel, processors) must comply with India's **Digital Personal Data Protection Act, 2023** and the **Digital Personal Data Protection Rules, 2025** (DECISIONS #48).

> **This is a product design, not legal advice.** Qualified Indian counsel must review this doc, the notices, the privacy policy, the terms and the retention schedule before launch. Items counsel must confirm are marked **(counsel)**; values the user hasn't confirmed are marked (provisional).

## Where the law stands (checked 2026-10-16)
| Item | Position | Source |
|---|---|---|
| Rules notified | 13 November 2025, G.S.R. 846(E) (a corrigendum, G.S.R. 892(E), followed on 10 December 2025). Some summaries say 14 November | SCC Online (14 Nov 2025); KPMG (Nov 2025); law-firm trackers |
| In force now | Rules 1, 2 and 17–21 (the Data Protection Board) since publication | SCC Online; KPMG |
| **13 November 2026** (4 weeks from today) | Rule 4: registration and duties of **Consent Managers** | SCC Online; KPMG |
| **13 May 2027** | Rules 3, 5–16, 22 and 23: notice, consent, security safeguards, breach intimation, retention and erasure, contact details, children, significant data fiduciaries, rights, cross-border transfer. The Act's matching sections (5, 6, 8–16 …) start then too | SCC Online; KPMG; MirvoLegal (Sept 2026) |
| Proposal to shorten the 18 months to 12 | Floated by MeitY in January 2026; **no amending notification found** as of September 2026 | MirvoLegal; consentos.in |
| Data Protection Board | Constituted in law; **no Chairperson or Members reported appointed** as of September 2026 (applications invited 6 May 2026) | MirvoLegal |

**Our position:** Agent PRO launches before May 2027, but it complies **from launch** (DECISIONS #48), so there's no retrofit and agents can rely on it. Re-check the dates before release: a compression notification would move the deadline earlier.

**Not confirmed:** the official MeitY PDF of the Rules couldn't be opened (HTTP 403), so rule numbers and periods here come from KPMG's guidance (Nov 2025), SCC Online, MirvoLegal, DSCI and other summaries **(counsel: check against the Gazette text)**. In particular, confirm how **Rule 8(3)** (a minimum one-year retention of personal data, traffic data and logs for the Seventh Schedule purposes) interacts with erasure on request.

**Related Indian rules to check (counsel):** the **CERT-In Directions of 28 April 2022** (cyber incidents, including data breaches, reported to CERT-In within **6 hours**; ICT logs kept for **180 days** within India) and **IRDAI** rules on agents' records and insurance advertisements (see OFFERS.md, custom offers).

## Roles
| Data | Data Principal | Data Fiduciary | Agent PRO's role |
|---|---|---|---|
| Agent account: auth email and password hash, `profiles`, `sites` (incl. published content about themselves), `subscriptions`, settings, consents, `nominations` | The agent | **Agent PRO** (the operator, Sledje Softwares; legal entity to confirm) | Fiduciary |
| Billing: `orders`, `payments`, receipts | The agent | **Agent PRO** (Razorpay is a separate regulated entity for the card/UPI data it collects) | Fiduciary |
| Domain registrant data (`registrant_contacts`) | The agent | **Agent PRO**, shared with the registrar and registry | Fiduciary |
| Admin accounts, `admins`, `audit_log` | Admins | **Agent PRO** | Fiduciary |
| Website enquiries (`enquiries`, their consents, `data_requests` from the public) | The person who enquires | **The agent** (they decide why and how to use it: to sell insurance) | **Processor** for the agent |
| Customers, policies, premium payments, nominees | The agent's customers (and nominees) | **The agent** | **Processor** for the agent |
| Site visitors | Visitors | — (no personal data stored: `site_daily_views` is a per-day count; no cookies, no analytics, no IP stored by us) | — (Cloudflare sees IPs; fonts are self-hosted, so no font CDN sees visitors) |
| Insurance companies | — | — | **No agent, enquiry or customer data is ever shared with a company.** Company admins manage only offers (OFFERS.md); usage is reported as counts |

- **Agents are fiduciaries.** The Act's exemption for personal or domestic use doesn't cover an agent's business. Most agents won't know their duties, so the product must make compliance the default: the enquiry form collects consent for them, retention runs automatically, and the app has one-tap tools for requests.
- **Agent PRO as processor** acts only on the agent's instructions, under a **Data Processing Addendum in the Terms** (planned): purpose limited to the service; Rule 6 safeguards; sub-processor list (below) with notice of changes; breach notice to the agent **without delay (target within 24 hours of awareness)**; help with rights requests; deletion at the end of the service (Retention). Under the Act the agent stays responsible for processing done on their behalf **(counsel: DPA text)**.

### Processors and sub-processors
| Processor | What it gets | Location | Notes |
|---|---|---|---|
| Supabase (database, Auth, Storage, Edge Functions, Realtime) | Everything stored | Project in **ap-south-1 (Mumbai)**; Supabase Inc. (US) staff may access for support | Sign Supabase's DPA. **The project is on the free plan, which has no project backups** (confirm on the pricing page): see [Backup risk](#backup-risk-open-high-priority) |
| Cloudflare (renderer and admin hosting, custom-hostname certificates) | Edge request logs (IP, user agent, URL) for every page view; no stored personal data, because pages are static and the browser fetches site data from Supabase directly | Global edge network incl. India; Cloudflare, Inc. (US) | Accept Cloudflare's DPA. Keep Worker logging off or minimal; no Worker code handles personal data today |
| Google Fonts (**removed: done, self-hosted in P1**) | ~~Every visitor's IP and user agent on every agent site page view~~ | Google (global) | **Self-host Inter and Noto Sans Devanagari in `web/fonts/`** (OFL) with `font-display: swap` and a preload. **Approved (DECISIONS #54), in P1**; **Done (web/fonts/, fonts.css, CSP without Google origins; a test fails if any built file references fonts.googleapis/gstatic, so Google is no longer a processor once deployed).** This also fixes the render-blocking fonts item in STATUS.md (supersedes the fonts part of DECISIONS #26) |
| Razorpay (payments) | Order ids and amounts from us; the payer's payment details go to Razorpay directly | India | Razorpay is itself regulated (RBI); we keep only an allow-listed subset of its payload (below). The Android Checkout SDK collects device data: name it in the agent notice |
| Domain registrar (not chosen) and registry (e.g. NIXI for `.in`) | Registrant name, email, phone, address | Depends on the registrar | Enable WHOIS privacy where the TLD allows it |
| Gmail SMTP (Google), **interim Auth sender since 2026-10-17** (DECISIONS #68) | Agent email addresses and the Auth email contents (confirmation and reset links) | Google (global) | Testing only; replaced by the transactional provider below before launch. Remove this row then |
| Transactional email (**approved, provider not chosen**; DECISIONS #54) | Agent email, notice text | Depends on the provider (prefer India or a signed DPA) | Needed for the lapsed-account deletion notices, admin invites and Auth emails (as custom SMTP); Supabase's built-in SMTP is for testing only |
| Google Play | Install and crash data under Google's own terms | Global | We add no analytics or crash SDK |

### Cross-border transfers
- Section 16 allows transfers outside India except to countries the Government restricts by notification; **none are known to be notified (counsel)**. Data at rest stays in Mumbai. Support access by Supabase or Cloudflare staff from abroad, and Cloudflare edge logs, are transfers we disclose in the privacy policy and processor list.
- If Agent PRO is ever notified as a significant data fiduciary, localisation of specified data may apply (below).

## Notice and consent
**Rules:** a notice must stand on its own, be clear and plain, list the personal data item by item with the specific purpose, and give a link to withdraw consent, exercise rights and complain to the Board (Rule 3). Withdrawal must be as easy as giving consent (Section 6). Consent is an unticked checkbox or an equally clear action, never pre-ticked, never bundled with anything optional. Every notice exists in **English and Hindi** (catalog `locales`) and is shown in the language the person is using.

**Definitions in `shared/` (built, P1-1):** each notice is versioned in `catalog.privacyNotices` (`{key, labelKey, order, version, textKeys}`) and its text lives in `shared/i18n` (`privacy.*`). `generateShared` computes a `sha256` per notice, version and locale over the canonical text (SHARED_DEFINITIONS.md, "privacyNotices": NFC, `\n` joins, `JSON.stringify({key, locale, text, version})`) and emits `catalog_privacy_notice(p_key) → jsonb {version, hash_en, hash_hi}`, `catalog_privacy_notice_hash(key, version, locale)` and `catalog_privacy_notice_locale(key, version, hash)` into `catalog_sync`, so the database records exactly which text was shown. Changing the text means a new version (`verifyShared` enforces it); old versions' hashes stay in the lockfile and in the SQL. The notices are `agent_signup`, `enquiry_form`, `agent_customer_duty` and `guardian_consent`, all at version 1. Clients send **`{notice_key, version, hash}`** for the text they showed; see [P1-1 contract](#p1-1-contract-for-p1-2-p1-3-and-p1-4).

### 1. Agent sign-up (app, Create account or Continue with Google) — Agent PRO as fiduciary
- One unticked checkbox under the password field, required to enable **Create account**; the same checkbox on Welcome, above **Continue with Google**, which stays disabled until it is ticked (DECISIONS #112):
  - EN: "I'm 18 or older, and I agree to the Terms and the Privacy notice."
  - HI: "मेरी आयु 18 वर्ष या उससे अधिक है, और मैं शर्तों और गोपनीयता सूचना से सहमत हूँ।"
- "Privacy notice" opens the full notice (an in-app screen, the same text as the web privacy policy). **Itemised content** (provisional; counsel):
  | Data | Purpose | Kept |
  |---|---|---|
  | Email, and either a password (stored hashed by Supabase Auth) or, with Continue with Google, the Google account's name and email | Sign-in, account emails | While the account exists |
  | Name, mobile, city, company, optional photo, agency code | Your profile and your public website (only what you publish is public) | While the account exists |
  | Website content | Showing your website | While the account exists |
  | Orders, payments and receipts | Billing and tax records | 8 years after the financial year, anonymised after account deletion (law) |
  | Registrant details (domain buyers only) | Registering your domain (shared with the registrar and registry) | Until 90 days after your last domain ends |
  | Login session on your phone | Keeping you signed in (encrypted with the Android Keystore) | Until you sign out |
  | Enquiries and customers you add | You decide the purpose; we store them for you as your processor | See "Your customers' data" |
  - Also: no ads, no trackers, no selling of data; processors and their locations; how to download, correct and delete your data and nominate someone (Settings → Your data); the grievance officer's contact (Rule 9); the right to complain to the Data Protection Board.
- **Notice version 2 (2026-10-25, DECISIONS #112, #114):** the account row above, Google added to the processors ("only if you continue with Google: it confirms who you are and shares your name and email with us"), and the designation dropped (no longer collected). Data minimisation: only the name and email are taken from Google; Supabase Auth also keeps Google's profile details (such as the picture link) in the auth user's metadata, which the app never reads. Counsel should confirm the wording.
- **Recording it for Continue with Google (DECISIONS #112):** an id-token sign-in carries no sign-up metadata, so the trigger records nothing. Before signing in, the app keeps the consent the agent ticked as pending (local store); right after the session starts it calls `record_consent('agent_signup', version, hash)` (not tied to the Welcome screen, which closes at once). If that fails, onboarding step 1 records it **before** it saves the mobile or anything else, and doesn't continue until it has. Sign-out drops a pending consent, so it can never be recorded for another account.
- **Recording it (built, P1-2):** the app sends `PrivacyNotice.AgentSignup.consent(locale)` = `{notice_key: 'agent_signup', version, hash}` as `raw_user_meta_data.consent` in the Auth sign-up call, and the signup trigger (`private.handle_new_user`) validates it and inserts the `consents` row (version, hash, the locale the hash resolves to, `principal_kind = 'agent'`, `channel = 'app'`). If the metadata is missing or invalid the account is still created (a trigger can't cleanly block Auth) but nothing is recorded; the app must then call `record_consent('agent_signup', version, hash)` before continuing.

### 2. Website enquiry form — the agent as fiduciary
- Above the submit button, an **unticked, required** checkbox (the RPC rejects a submission without it):
  - EN: "I agree that {name} may use these details to contact me about insurance. I'm 18 or older. How my data is used"
  - HI: "मैं सहमत हूँ कि {name} बीमा के बारे में मुझसे संपर्क करने के लिए इन विवरणों का उपयोग कर सकते हैं। मेरी आयु 18 वर्ष या उससे अधिक है। मेरे डेटा का उपयोग कैसे होगा"
- "How my data is used" opens the site's **privacy page** (`/a/{slug}/privacy`, or `/privacy` on a custom domain). The **itemised notice**:
  - **What:** your name and mobile number; optionally your email, the kind of plan you're interested in, and your message.
  - **Why:** so {name}, an insurance advisor, can reply and advise you about insurance. Nothing else; not sold; not shared with an insurance company without your agreement.
  - **Who:** {name} is responsible for your data. Agent PRO stores it securely in India on their behalf.
  - **How long:** until {name} deletes it, which they should do once it's no longer needed. You can ask them to delete it at any time.
  - **Your rights:** withdraw your consent, see, correct or delete your data, or nominate someone, by contacting {name} ({phone}, {email}) or with the form on this page. If you're not satisfied, contact Agent PRO's grievance officer ({GRIEVANCE_OFFICER_NAME}, {GRIEVANCE_EMAIL}); you can also complain to the Data Protection Board of India.
  - The message field's helper text: "Please don't include health or bank details." (`site.form_message_hint`).
- **Built (P1-2):** `submit_enquiry(slug, host, name, phone, email, interest, message, honeypot, consent jsonb default null)`. There's no separate boolean: the ticked box is shown by sending the notice payload `consent = {notice_key: 'enquiry_form', version, hash}` for the text the form showed. It's checked after the honeypot and the field checks and before the rate limit, and the `consents` row is created in the same transaction as the enquiry (`principal_kind = 'enquirer'`, `channel = 'web'`, `enquiry_id`, the locale the hash resolves to, and the verified hash; no IP or user agent). A missing payload, another notice's key, an unknown version, a version that isn't the current one, or a hash that matches no text of that version returns `{ok:false, error:'invalid', field:'consent'}`. The old 8-argument function is dropped.
- **Withdrawal is as easy as consenting:** the privacy page's request form has **Withdraw my consent** as the first choice (phone number + name, one button), using the same RPC pattern as the enquiry form. See [Rights](#data-principal-rights).
- **Manual enquiries** (walk-in, phone, referral) are entered by the agent: covered by the agent attestation below.

### 3. Customer and manual enquiry data entered by agents — the agent's responsibility
- **One-time attestation** the first time an agent adds a customer, imports a contact or adds a manual enquiry (a sheet, `AppBottomSheet`):
  - EN: "You're responsible for your customers' data. Tell each customer you keep their details in Agent PRO to manage their policies, and keep only what you need, deleting enquiries you no longer need. You can share a privacy note with them from their page." Button: **I understand**.
  - HI: "अपने ग्राहकों के डेटा की ज़िम्मेदारी आपकी है। हर ग्राहक को बताएँ कि आप उनकी पॉलिसी संभालने के लिए उनका विवरण Agent PRO में रखते हैं, और केवल ज़रूरी जानकारी ही रखें; जिन पूछताछ की अब ज़रूरत नहीं, उन्हें हटा दें। आप उनके पेज से उन्हें गोपनीयता सूचना भेज सकते हैं।" Button: **मैं समझता/समझती हूँ**.
  - Stored with `record_consent` and `PrivacyNotice.AgentCustomerDuty.consent(locale)`; shown again when the version changes.
- **Share privacy note** (Customer detail and Enquiry detail overflow): opens the share sheet with a short prefilled message and the agent's privacy-page link, for SMS or WhatsApp. The agent sends it; Agent PRO doesn't message customers.
- **Autosaved forms (DECISIONS #108):** the agent's Add customer form is saved to the server as the agent types (`customer_drafts`, `source = 'agent'`, owner-only RLS), so nothing is lost; saving the customer clears the draft's values, and an unfinished one is listed under **Incomplete forms** until the agent resumes or deletes it, or it is erased after `retention.customerDraftsDays` (30). It holds only the customer form's fields.
- **Customers under 18:** if the date of birth gives an age under 18, the form shows one line, "Customer is under 18 — record a parent's or guardian's consent", and requires a toggle, "A parent or guardian has agreed" (`consents`, `principal_kind = 'customer'`, `notice_key = 'guardian_consent'`) (provisional; counsel on Section 9 and verifiable consent for a minor policyholder or life assured).

### 4. Customer login on the agent's website — the agent as fiduciary (DECISIONS #96–#100)
The agent can let a customer see their own policies, dues and recorded payments at `/a/<slug>/my`. This is **new processing of the customer's data, shown back to them on the web**, so it has its own notice, `customer_portal` (version 1; `shared/i18n` `privacy.customer_portal.*`; texts EN + HI in `catalog.privacyNotices`).
- **Flow:** the agent taps **Send login link** on Customer detail (the app shares the link; Agent PRO doesn't message customers). The customer opens it, types their mobile (it must match the number the agent has on file), sets a 6-digit PIN and ticks an **unticked, required** checkbox: "I agree that {name} shows my policy details to me on this website, as described in the notice." with the link "How my data is used" (the site's privacy page, `#portal` section). Afterwards: mobile + PIN. **The customer is attached to the login only at activation (DECISIONS #101):** `portal_invite` attaches no one, so a new link never exposes a customer through an existing login before they consent, and each customer must open **their own** link. The consent is stored by `portal_activate` as **one `consents` row for the customer whose link was used** (`principal_kind = 'customer'`, `notice_key = 'customer_portal'`, `channel = 'web'`, with the notice version and the hash of the language shown); consent is per activated customer, never per invited one. A family member on the same mobile joins the shared login by activating their own link, which also resets the shared PIN. The link's token is moved out of the address bar into a 15-minute HttpOnly cookie on first load, and the start page sends `Referrer-Policy: strict-origin`.
- **The itemised notice:** *What:* the details {name} has recorded for the customer (name, mobile, email, date of birth, address, city, nominee), their policies, due dates and the premiums recorded as paid; the PIN is stored only in scrambled form. *Why:* so the customer can check their policies and dues and contact {name}; not sold, not shared. *Who:* {name} is responsible; Agent PRO stores it in India on their behalf. *How long:* signed in on a device for up to 90 days; the login is removed when {name} turns it off or the customer asks. *Reminders:* only if turned on, a short encrypted push reminder without amount or policy number (**reminders are not built yet**; the sentence is in the notice already, so remove it or build them before release, and a text change needs notice version 2). *Rights:* see, correct or delete data, withdraw consent, by contacting {name} or from the "My policies" page (**Ask to correct**, **Ask to delete** create a `data_requests` row linked to the customer, shown in the agent's inbox).
- **Withdrawing consent:** the customer can sign out, and can ask the agent to turn the login off ("Ask to delete" or a call); the agent's **Turn off** detaches the customer immediately. There is no self-service "withdraw" button on the portal yet.
- **The agent's site only promises what exists (DECISIONS #102):** a site whose login is on shows a "Customer login" link in the hero, a "For my customers" section and "My policies" in the footer. Their fixed text names only policies, the next premium, recorded payments and asking about a policy; it mentions no reminders until Web Push is built, and no data beyond the notice's list. The agent controls it all with the one **Show "My policies" login link** toggle. The section is public marketing copy and holds no customer data.
- **Purpose limitation:** the portal is read-only (DECISIONS #99). The only things a customer can send are a question about a policy or "I've paid this premium" (an enquiry with the source `portal`, which the agent sees in the Enquiries inbox) and a data request. It never records a payment.

### 5. Customer sign-up link on the agent's website — the agent as fiduciary (DECISIONS #108)
The agent can send a customer a link (`<site>/join?t=<token>`) to fill in their own details, which become the customer record. This is **collection of the customer's data from the customer, on the web, saved as they type and shown live to the agent**, so it has its own notice, `customer_signup` (version 1; `shared/i18n` `privacy.customer_signup.*`; EN + HI in `catalog.privacyNotices`).
- **Consent before collection:** the page first shows the notice and an **unticked, required** checkbox: "I agree that {name} saves the details I type in this form, as I type them, to add me as a customer, as described in the notice." with the link "How my data is used" (the site's privacy page, `#signup` section). **No field can be stored before it is ticked:** `signup_save` and `signup_submit` answer `consent_required` and store nothing, and `signup_open` returns no values. The consent (`notice_key`, version, the hash of the language shown, the locale, `given_at`) is held on the draft and copied to `consents` at Submit (`principal_kind = 'customer'`, `channel = 'web'`, the customer's id, `given_at` = when it was ticked). No IP address or device data is stored.
- **The itemised notice:** *What:* the details typed in the form (name, mobile, email, date of birth, address, city, nominee), saved as they are typed so {name} can see them and help. *Why:* so {name} can add them as a customer and look after their policies; not sold, not shared. *Who:* {name} is responsible; Agent PRO stores it in India on their behalf. *How long:* an unfinished form is deleted after 30 days (`retention.customerDraftsDays`); once submitted, the details are {name}'s customer record until the customer or {name} asks for deletion. *Rights:* see, correct or delete, or withdraw consent, by contacting {name}.
- **What the agent sees, live:** after the consent, every value as it is saved (each pause in typing), which field the customer is in (`active_field`), and when the page was last seen (`customer_seen_at`; "On the form now" within `presenceSeconds`). Before the consent the agent sees only that the link was opened and when the page was last seen. The notice says this ("saved as you type, so {name} can see them").
- **A minor (under 18 by the date of birth, Asia/Kolkata):** Submit also needs the separate `customer_signup_guardian` notice ("This person is under 18, so a parent or guardian must fill in this form and agree for them." / "I am this person's parent or guardian, and I agree to this on their behalf."), recorded as a second `consents` row for the customer. It is separate from the app's `guardian_consent`, which is the agent's own attestation. (Counsel: verifiable guardian consent, Section 9; the same open question as the app flow.)
- **Submit adds the customer automatically** (the user's choice): the server validates, creates the customer with `notes` empty, records the consents and clears the draft's values (`fields = {}`) in one transaction. The link then stops working.
- **Purpose limitation and minimisation:** the form has only the customer record's fields (never notes, PAN, Aadhaar or policy data); unknown keys are dropped server-side; each value is capped. The agent passes the one-time customer-duty gate before sending a link.
- **Withdrawal and erasure:** before Submit the agent can delete the draft (a soft delete with Undo); the link stops working at once and the retention job hard-deletes the row, values included, 30 days after that change, like every soft-deleted row. After Submit it is an ordinary customer record (Customers, Data principal rights below).

### Consent records (`consents`, built in P1-2)
`id`, `agent_id` (the fiduciary context; `on delete cascade`), `principal_kind` (`agent|enquirer|customer`), `enquiry_id`/`customer_id` (nullable, `on delete set null`), `notice_key`, `notice_version`, `notice_hash`, `locale`, `channel` (`app|web`), `given_at`, `withdrawn_at`, `withdrawn_via` (`web_form|agent|app`). **No IP address or device data** (minimisation). RLS owner-only select; inserts only through `submit_enquiry`, `record_consent`, the signup trigger, `portal_activate` and `signup_submit`; the agent will set `withdrawn_at` through `withdraw_consent(consent_id)` (P2). Also: `created_at`/`updated_at`/`deleted_at` and the `(agent_id, updated_at)` index per BACKEND.md conventions, composite FKs for `enquiry_id`/`customer_id`, and CHECKs (`notice_key` from the catalog, `notice_hash` 64 lowercase hex, `withdrawn_at`/`withdrawn_via` set together).
- **`record_consent(notice_key, version, hash, customer_id default null)`** (authenticated, no `can_write()`): `agent_signup` and `agent_customer_duty` are recorded with `principal_kind = 'agent'` (no customer); `guardian_consent` with `principal_kind = 'customer'` and a required `customer_id` the agent owns on the server (`not_found` otherwise, so the app syncs the customer first); `enquiry_form` is refused. Idempotent per notice version (and customer). Returns `{ok:true}` or `{ok:false, error:'invalid', field:'consent'|'customer_id'}`.

### Consent Managers
Rule 4 (from 13 November 2026) lets data principals manage consent through a registered Consent Manager (an Indian company with ₹2 crore net worth, registered with the Board). Using one is **optional for fiduciaries**. v1 doesn't integrate one; revisit when registered Consent Managers and their interfaces exist.

## Purpose limitation and minimisation (field review)
**Product rule (added to this doc as a standing rule):** never add Aadhaar, PAN, bank account, card, health, medical or income fields anywhere. Free-text fields carry a hint not to enter them.

| Table / field | Keep? | Purpose and action |
|---|---|---|
| `profiles.full_name, phone, email, designation, city, photo_url, preferred_language` | Keep | Account and public site |
| `profiles.agency_code` | Keep, optional | Shown only when `about.showAgencyCode` is on |
| `profiles.branch` | **Remove, or give it a use** | Nothing in the product reads it. Drop the column in the privacy migration unless a use is defined (provisional) |
| `company_memberships` (planned) | Keep | The admin-verified company link (OFFERS.md). The agency code copied into the request lets an admin verify it. **Admins** see only name, designation, city, agency code and email for this, in `admin_membership_queue`; every review is audited. Rows are deleted with the account |
| `enquiries.name, phone` | Keep, required | Replying |
| `enquiries.email, interest, message` | Keep, optional | Replying; message hint against health and bank details |
| `enquiries.notes, follow_up_at, seen_at, status` | Keep | The agent's follow-up |
| `customers.name, phone, email, city` | Keep | Servicing policies |
| `customers.dob` | Keep, optional | Plan eligibility and age-based reminders; under-18 flow above |
| `customers.address` | Keep, optional | Correspondence |
| `customers.nominee` | Keep, optional, **name only** | Policy servicing. Helper: "Only if your customer agrees." No nominee phone, DOB or relationship fields |
| `customers.notes`, `policies.notes` | Keep | Hint against health and bank details |
| `policies.*`, `premium_payments.*` | Keep | Dues and history |
| Customer portal payload (`portal_home`) | **Minimise** | Shows the customer **their own** name, mobile, email, date of birth, address, city, nominee, and their policies (plan, number, sum assured, premium, mode, dates, status) with the latest `paymentsShown` (12) recorded payments. **Never `notes`**, never the source enquiry, another customer's data, or an id the page doesn't use. A login shows only the customers who have **activated their own link** on that mobile (an invite alone attaches no one). The home also computes summary tiles (policy count, premium per year, total cover, next due, people) from this same payload in the Worker; nothing new is stored or sent |
| `portal_accounts` (mobile, bcrypt PIN hash, attempt counters, last login), `portal_sessions`, `portal_invites` (SHA-256 token hashes only) | Keep while the login exists | Authentication. **No IP address, user agent or device data is stored**; the only login record is `last_login_at` and `last_seen_at` (updated at most every 10 minutes). Raw tokens and PINs are never stored or logged |
| `payments.raw` | **Minimise** | Store only an allow-list of the provider payload: `id, order_id, status, method, amount, currency, fee, tax, captured, error_code, error_reason, created_at, acquirer_data.rrn`. Drop `email, contact, vpa, card, bank, wallet, notes`. Replaces "cleared on deletion" (#30) with "never stored". **Built (P1-2):** a `before insert or update of raw` trigger on `payments` enforces it for every writer, the service role included (non-object or empty → null) |
| `orders.*`, `domain_quotes.*` | Keep | Billing; quotes purged after 30 days |
| `registrant_contacts.*` | Keep while a domain exists | Registrar requirement |
| `site_daily_views` | Keep | Aggregate counts, not personal data |
| `sites.published_content` | Keep | The agent chooses what's public; testimonials name third parties, so the editor's helper says "Only with the person's permission" |
| `auth.audit_log_entries` (Supabase; sign-in events with IP) | Keep 13 months | Security logging (Rule 6); purged by the retention job |
| Edge Function and app logs | No personal data | Log ids and error codes only, never names, phones, emails or tokens |
| Device | — | Room cache in app-private storage (Android file-based encryption; not encrypted by the app — accepted risk, to record as a decision); backup and device transfer off (exists); session Keystore-encrypted (#44). The CSV export shows "This file contains your customers' personal data. Keep it safe." before saving |
| Web | — | No analytics, no third-party scripts, and **no cookies on the public site** (the customer login sets one strictly necessary, HttpOnly session cookie `ap_portal` and has its own service worker; see §4 and Customer portal safeguards); one `sessionStorage` key to de-duplicate views. No cookie banner is needed (counsel). "Add to home screen" (DECISIONS #65) stores nothing in the browser and has no service worker or cache. Its manifest is built only from the published site's public fields (name, headline, locale, accent), and installing sends nothing to us |

## Data principal rights
**Rules:** access to a summary of the data and the processors it's shared with (Section 11), correction, completion, updating and erasure (12), grievance redressal (13) answered within **90 days** (Rule 14(3)), nomination of someone to act on death or incapacity (14), and the fiduciary's published process and contact details (Rule 9). Agent PRO's own response target is **30 days** (provisional).

### Agents (Agent PRO is the fiduciary): Settings → **Your data** (planned)
| Right | Where |
|---|---|
| Summary / access | **Download my data**: a zip with the account (profile, site content, consents, nominations, data requests) as JSON plus the existing CSVs (customers, enquiries, policies, payments) and the list of processors. Built locally from Room plus `my_data_summary()` (planned RPC for server-only rows: consents, orders, receipts) |
| Correction | Profile, website editor (exist) |
| Erasure | **Delete account** (exists, through the Edge Function in #32). Tax records stay anonymised |
| Withdraw consent | The sign-up consent can only be withdrawn by deleting the account (the service can't run without it); the screen says so |
| Nomination | **Nominate someone**: name, email or mobile (`nominations` table, owner-only RLS). The nominee contacts the grievance officer with proof |
| Grievance | **Contact the grievance officer**: a short form (`data_requests`, addressed to the platform) plus the officer's email; the reply SLA is shown |

### The agent's enquirers and customers (the agent is the fiduciary; Agent PRO provides the tools)
- **How they reach the agent:** the site's privacy page shows the agent's phone and email (from the Contact section) and a **request form**: request type (withdraw consent, see my data, correct, delete, complaint), name, mobile, message (≤ 500). It calls `submit_data_request(slug, host, name, phone, kind, message, honeypot)` (**built, P1-2**; anon, `SECURITY DEFINER`, the same site resolution, honeypot and phone rule as `submit_enquiry`, kind from `catalog_data_request_kinds()`, a rate limit of 3 per site and phone per rolling 24 hours; it creates an `open` request for the site's agent due after `retention.dataRequests.responseDays`; returns `{ok:true}` or `{ok:false, error:'invalid'|'rate_limited'|'unavailable', field?}`). The agent sets the status with `update_data_request(id, status, note)` (built, P1-2; audited). The agent gets a notification, "Privacy request from Ramesh".
- **In the app (planned):** a **Privacy requests** list (Enquiries tab top-bar overflow, and Settings → Your data), one row per request with a due date. Opening one shows the matching enquiries and customers by phone, and the actions:
  - **Export this person's data:** a one-person CSV/PDF from Room, shared through `FileExporter` (also on Customer and Enquiry detail overflow).
  - **Correct:** opens the edit form.
  - **Withdraw consent:** marks the consent withdrawn and stops follow-ups for that enquiry.
  - **Erase permanently:** a `ConfirmDialog` ("This permanently deletes Ramesh, 2 policies and 6 payments. It can't be undone."), then `erase_enquiry(id)` / `erase_customer(id)`.
  - **Mark done / Not genuine** with a short note. The requester isn't notified by the system (no SMS provider); the agent replies by phone. **Verification is by the agent calling the number back** (DECISIONS #54).
- **Reminders and escalation:** reminders in the daily digest at 7 and 21 days; at 30 days an open request is flagged on Home and copied to Agent PRO's privacy inbox (admin panel) so the platform can follow up with the agent (provisional).
- **Erase = redact, then purge.** `erase_*` (`SECURITY DEFINER`, owner and `can_write()` not required, so a lapsed agent can still erase): in one transaction it nulls every personal field of the person's rows (enquiries, customer, their policies and premium payments, linked consents and requests: name becomes "Erased", phones, emails, notes, nominee, dob, address, message and policy numbers null), sets `erased_at` and `deleted_at`, and writes an `audit_log` row with no personal data. The redacted tombstones let `SyncEngine` remove the rows from every device; the retention job hard-deletes them after 30 days.

## Retention and erasure
**Rules:** erase personal data when consent is withdrawn or the purpose is no longer served, unless the law requires keeping it (Section 8); the Third Schedule's 3-year inactivity erasure with 48 hours' notice applies only to large e-commerce, gaming and social media platforms, **not to Agent PRO**, but we adopt the notice practice; logs and some data must be kept for at least one year (Rules 6 and 8(3); counsel on the interaction). All periods live in a new `catalog.retention` object (definitions in `shared/`, server-authoritative through `app_config.retention`).

| Data | Retention (provisional) | Mechanism |
|---|---|---|
| Enquiries never converted | **The agent decides** (DECISIONS #54). Nothing is deleted automatically, and there's no retention setting | **The agent is the fiduciary for enquiry data, so storage limitation (Section 8) is their responsibility; Agent PRO provides the tools:** erase one enquiry (Enquiry detail), and **Clear old enquiries** (Enquiries tab overflow: pick "Closed" or "Not converted" and an age, such as older than 6, 12 or 24 months; the sheet shows the count, then a confirm dialog, then a bulk erase). The terms and the in-app notice tell the agent this is their duty. A withdrawn consent or an erasure request is handled through Privacy requests |
| Converted enquiries | Kept with the customer | — |
| Customers, policies, premium payments | While the agent's account exists, unless the agent erases them | Agent tools. Policies that ended (`matured`, `surrendered`, `lapsed`) with no activity for 3 years are suggested for deletion, not deleted automatically, because the agent may have record-keeping duties **(counsel: IRDAI record-keeping for agents)** |
| Soft-deleted rows (any table) | 30 days | Job hard-deletes rows with `deleted_at` older than 30 days. Today they're kept forever (supersedes the open-ended part of #27) |
| **Long-lapsed accounts** (resolves the STATUS open question) | **12 months after the plan lapses** (DECISIONS #54) | Notices by email and in the app at 30 days, 7 days and 48 hours before; then the `delete-account` Edge Function (#32) runs as if the agent had deleted the account. Renewing at any time before then cancels it. The data stays exportable read-only until deletion |
| Unconfirmed sign-ups | 7 days | Job deletes `auth.users` rows never confirmed |
| Orders, payments, receipts | **8 years after the end of the financial year**, anonymised on account deletion (#30) | Tax and GST law (counsel: the exact period; GST records are 72 months from the annual return due date) |
| `registrant_contacts` | 90 days after the agent's last domain expires or is released | Job |
| `domain_quotes` | 30 days | Job |
| `site_daily_views` | 25 months | Job (charts use 12) |
| `consents` | For the life of the data they cover, plus 1 year | Redacted with the person's data; deleted by the job |
| `data_requests` | 3 years after closing (evidence of handling) | Personal fields redacted when the person is erased; deleted by the job |
| `audit_log`, `auth.audit_log_entries` | 13 months (≥ 1 year for Rule 6; ≥ 180 days for CERT-In) | Job |
| `portal_sessions` | 30 days after expiry or revocation (`retention.portalSessionsDays`) | Job (**planned**, part of `private.retention_run()`; not built). Until then expired rows are inert: the RPCs ignore them |
| `portal_invites` | 30 days after use, expiry or revocation (`retention.portalInvitesDays`) | Job (**planned**, not built) |
| `portal_accounts`, `portal_account_customers` | While the agent keeps the login on. **Turn off** hard-deletes the link; an account left with no customer is soft-deleted (then the 30-day soft-delete job); a customer's phone change or deletion detaches it; the agent's account deletion cascades | Triggers on `customers`; the soft-delete job |
| `customer_drafts` (unfinished customer forms and sign-up links, DECISIONS #108) | **30 days after the last change** (`retention.customerDraftsDays`, the user's choice); completed drafts keep no values (cleared at Submit or Save) | `private.purge_customer_drafts()` (built, part of the planned `private.retention_run()`; its daily pg_cron schedule **needs the user's approval and isn't set up**): an open draft idle 30 days has its values and consent erased and becomes a tombstone (so the app drops it), and completed, deleted and tombstoned drafts are hard-deleted 30 days after their last change. Until it is scheduled, a sign-up link idle 30 days already stops working (`invalid_link`), but the row stays. The link's token hash goes with the draft (cascade) and is deleted at Submit |
| `private.admin_invites` (an invited admin's email, 8c-1) | 15 minutes at most | Deleted when the invite finishes or fails; older markers are purged on the next invite and ignored by the signup trigger (BACKEND.md, Edge Function `admin`). Admin emails themselves live only in `auth.users`. The planned unconfirmed sign-ups job must skip `account_kind = 'admin'` invitees (or give them a longer window) |
| Storage `site-media` | Deleted with the account (#32); replaced images deleted by the app when replaced | A weekly sweep removes files under `<uid>/` that no draft or published content references, older than 30 days |
| Backups | Erased data can survive in backups until they roll off (7 days on Supabase Pro) | Disclosed in the privacy policy; never restored selectively |

**Jobs (planned):** `pg_cron` runs `private.retention_run()` daily at 02:30 IST for the SQL-only steps (each step a separate function, idempotent, logged to `audit_log` as `system`); the Edge Function `cron` sends the notice emails and deletes Storage files and accounts. Every step has a SQL test with moved clocks.

## Security safeguards (Rule 6) and breach response
| Rule 6 safeguard | How |
|---|---|
| Encryption, masking, tokenisation | TLS everywhere (Supabase, Cloudflare with HSTS); Supabase encrypts storage at rest; the session is Keystore-encrypted (#44); passwords are hashed by Supabase Auth; `payments.raw` minimised |
| Access control | RLS owner-only on agent tables, admin-only with MFA (`aal2`) on admin tables (OFFERS.md); `anon` only through listed RPCs; the service role key only in Edge Function secrets; Supabase and Cloudflare dashboard members with MFA, least privilege, reviewed quarterly |
| Logging, monitoring and review | `audit_log` for admin actions and privacy-relevant agent actions (erase person, export, delete account, consent withdrawals, data requests); Supabase auth audit entries; logs kept 13 months; advisors after every migration; a monthly review of auth failures and rate-limit hits |
| Continuity and backups | **Gap today:** the free plan has no backups. Supabase Pro (daily backups, 7 days) is high priority and **not approved yet** — see [Backup risk](#backup-risk-open-high-priority). Once on, write the restore steps and test them once |
| Processor contracts | DPAs with Supabase and Cloudflare; Razorpay's terms; the registrar's terms; the DPA with agents in the Terms |
| Customer portal (DECISIONS #96–#100) | See [Customer portal safeguards](#customer-portal-safeguards-rule-6) |
| Customer sign-up link (DECISIONS #108) | See [Customer sign-up link safeguards](#customer-sign-up-link-safeguards-rule-6) |
| Detection and prevention | Rate limits on public RPCs; honeypots; dependency updates; secret scanning before commits |

### Customer portal safeguards (Rule 6)
- **No Supabase Auth, no tables reachable by API roles:** the four `portal_*` tables have RLS on, no policies and no grants; only `SECURITY DEFINER` RPCs touch them (DECISIONS #97).
- **Hashed tokens:** invite and session tokens are 32 random bytes, shown once and stored only as SHA-256. A database read doesn't give anyone a working session or link.
- **PINs are bcrypt** (cost 10) and never logged; weak PINs (repeated digit, runs, patterns, the 111 PINs in `catalog.portal.blockedPins`, the last digits of the mobile and the customer's date of birth in `DDMMYY`, `YYMMDD` or `MMDDYY`) are refused. A link is single use, valid 7 days, and burns after 5 wrong mobile numbers; a new link voids **that customer's** older unused links (family members' links stay live) and, once activated, resets the PIN.
- **Worker gate:** the six anon portal RPCs run only when the Worker sends the secret `x-portal-key` (its SHA-256 sits in `private.portal_gate`, set by hand). The public anon key alone can't reach bcrypt, the counters or the lockouts; direct calls get `unavailable`. A per-site cap on failed logins (`siteFailedLoginsPerMinute`, 30 a minute) is checked before any bcrypt work.
- **Lockouts:** 5 wrong PINs in a row lock the login for 15 minutes; 20 wrong PINs within 30 days (`failureWindowDays`) hard-lock it for **24 hours** (`hardLockHours`), which clears by itself, or at once when the agent sends a new link and the customer activates it. A correct PIN ends only the 5-in-a-row streak, so slow guessing across days still adds up. Unknown and locked numbers cost the same bcrypt time and show the same message, except the `locked` reply. The Worker also rate-limits all portal POSTs per IP (10 per 60 s).
- **Session handling:** the token lives only in an `HttpOnly; Secure; SameSite=Lax` cookie scoped to one site's portal path; page JS and the service worker never see it; every POST checks `Origin`; every response is `no-store` and `noindex`; the CSP allows only `script-src 'self'`. A session works only on its own agent's site. Sign-out revokes it on the server and clears the cookie (even if the RPC fails); the signed-out page then tells the service worker to wipe its cache. There is no `Clear-Site-Data` header (DECISIONS #101). "Sign out on all devices" revokes every session of the login.
- **No IP stored:** the portal records no IP, user agent or device data (Cloudflare's own edge logs aside, as for every page). Audit rows (`portal.invite`, `portal.revoke`, `portal.activate`, `portal.hard_lock`) carry no personal data.
- **Offline copy:** the installed app keeps the last home page in the browser cache on the customer's own device so it opens offline. It is dropped on sign-out (the service worker wipes it), when the login is revoked, or when the portal is unavailable. A shared device is the customer's risk; the page says "Offline — last updated {time}".
- **Security review of 2026-10-23 (DECISIONS #101).** Findings that were **confirmed and fixed**: activation was impossible in Chrome and Firefox (H1: `Referrer-Policy: no-referrer` made the first-login POST send `Origin: null`); direct RPC calls with the public anon key bypassed the Worker's rate limit, allowing database DoS and mass lockouts (H2: the Worker gate and the per-site cap); a new invite exposed a customer to an existing login before consent (M1: attach at activation, consent per activated customer); endless slow guessing and date-of-birth PINs (M2: the failure window, the blocked list, the DOB rule, a hard lock that expires after 24 hours instead of lasting forever); a verified customer's data requests sharing the public form's counter (L2: their own counter); `Clear-Site-Data` that cleared every agent's cache on the shared origin without touching service-worker caches (dropped); the invite token lingering in the address bar (now moved into a short cookie).
- **What the security review rejected (2026-10-23) and how it was resolved:** (1) a **permanent** hard lock after 20 wrong PINs: anyone who knew a number could lock a customer out until the agent re-invited → now 24 hours (`hardLockHours`), counted over `failureWindowDays`; (2) relying on the Worker's per-IP limit to protect bcrypt: the RPCs were reachable directly with the anon key → the Worker secret gate plus a per-site failed-login cap in the database; (3) a consent row for every attached customer on each activation: a newly invited family member was shown before their own consent → attach and consent only when that customer's own link is used; (4) verified customers' data requests sharing the public form's limit: a stranger could block them → a separate counter. Confirmed as acceptable: a "locked" reply reveals a login exists (only after 5 failures, now behind the gate); sessions stay valid during a lock; turning the flag or toggle off suspends sessions (still bounded by the 90-day expiry); trigger detaches write no audit row.

- **Accepted risks (reviewed and kept):**
  - The `locked` reply shows that a login exists for a number, now only through the Worker and after the gate.
  - Someone who knows a customer's mobile can lock them out for 24 hours (20 wrong PINs within 30 days) until a new link is activated; the lock clears by itself.
  - bcrypt costs CPU per login. The gate, the per-site cap (a slight overshoot under concurrency is accepted) and the edge rate limit bound it (the binding is unverified until a dry-run deploy). An attack on one site can make its logins `rate_limited` for a minute at a time.
  - Sessions stay valid while the account is locked; turning the flag or the agent's toggle off suspends sessions rather than revokes them.
  - A forwarded link plus the customer's mobile number would let someone else set the PIN before the customer does (the agent can see "Not signed in yet" and resend).
- **Shared-origin limitation (recorded, not fixed).** Public sites and portals share one origin (`*.workers.dev` now, the platform domain later). A cookie `Path` is not a security boundary there, and Cache Storage is per origin, so a script running on any agent's site could in principle reach another agent's portal cookie scope or cache. The long-term fix is a separate origin for portals (for example `my.<platform domain>`) once the platform domain exists. Until then **any XSS on the sites origin is treated as critical**: the CSP is `script-src 'self'`, every text is escaped, and there is no agent-supplied HTML or script.
- **Open question for counsel:** is mobile + a 6-digit PIN, set from an agent-issued link, enough verification to show a customer's policy data, date of birth and address on the web? The alternatives are an OTP to the customer's mobile (needs a paid SMS provider, DECISIONS #96) or showing less on screen (for example hiding the date of birth and address until a second step). Also confirm that the `customer_portal` notice and the per-customer `consents` row satisfy Rule 3 when the agent, not the customer, starts the flow.

> TODO(doc): counsel to answer the verification question above before the portal is enabled for real customers.

### Customer sign-up link safeguards (Rule 6)
- **The token never reaches a client:** 32 random bytes, shown once to the agent, stored only as SHA-256 in `private.customer_draft_links` (no grants to any role); the agent's table rows and realtime events don't carry it. The Worker moves it from the address bar into an HttpOnly cookie on first load.
- **Worker gate and limits:** the four anon RPCs run only with the portal's Worker secret (`x-portal-key`); direct calls with the public anon key get `unavailable`. Saves and submits are capped per link (`savesPerMinute`, 60 a minute) in the database; the Worker adds an IP limit. Each value is capped and the whole form is ≤ 4 KB.
- **Client writes can't fake the customer's part:** a guard trigger lets the app change only its own drafts' values and the delete flag, and on a sign-up draft only the delete flag; the customer-only columns (consent, the link expiry, what the customer is typing, when they were seen) are written only by the server functions. The check uses the database role, which a client can't change, not a setting a client could send.
- **One link, one customer:** Submit runs under a per-link lock; a second or concurrent submit gets "link expired or used", so a link can't create duplicates.
- **No personal data in logs:** the audit rows `customer_signup.submit` and `retention.customer_drafts` carry no values (`{}` and counts).
- **Accepted risks (DECISIONS #108):**
  - The link is a bearer secret for up to 7 days (`linkDays`): whoever has it can fill in the form and, after ticking the consent, read back the values already typed on it. The agent shares it with the customer directly; **Send a new link** voids the old one at once, and deleting the draft stops it.
  - The agent sees the values as they are typed, before the customer presses Submit, including values the customer later changes or never submits. The notice says so, and nothing is stored before consent.
  - A customer who never finishes leaves their partial values on the server for up to 30 days (as the notice says); until the purge is scheduled the rows stay longer, though the link stops working.
  - There is no per-site cap on sign-up calls (only per link, and the Worker's IP limit); a flood of links is bounded by `openLinksPerAgent` (25 open per agent).
  - Submit trusts the typed mobile number without an OTP; the agent knows the customer and checks the record.

### Backup risk (open, high priority)
`AGENT_PRO` is on Supabase's free plan, which keeps **no project backups** (DECISIONS #54: Pro, about US$25/month, isn't approved yet but is high priority).
- **What can go wrong:** a bad migration, an accidental delete or update, a compromised key, or a platform incident could **lose every agent's data permanently**: their sites, enquiries, customers, policies and payment history. Free projects also pause after about a week without activity.
- **Compliance gap:** Rule 6 lists backups among the reasonable security safeguards, so running real agents' data without backups is a gap against the Rules (from 13 May 2027) and against what the Terms will promise.
- **Until Pro is approved:** keep only test data in `AGENT_PRO`; **don't onboard real agents or publish real sites** (recommended launch gate); run every migration first on the local test harness, as today.
- **When approved:** turn on Pro, confirm daily backups appear, consider point-in-time recovery, and test a restore into a scratch project once.

### Breach runbook (planned; owner: the grievance officer)
| When | Step |
|---|---|
| T0 | Anyone who suspects a breach tells the owner admins at once. Open an entry in the **breach register** (admin panel, owner-only table `incidents`: what, when, data, principals affected, actions, notifications sent) |
| Within hours | Contain: rotate keys and the service role, revoke sessions, block the path. Preserve the logs |
| **≤ 6 hours** | Report to **CERT-In** if the incident is one of its listed types (counsel) |
| **Without delay** | **Initial intimation to the Board** (nature, extent, timing, location, likely impact) through its digital platform once it exists (counsel on the channel while the Board isn't staffed) |
| **Without delay** | **Tell affected principals in plain language:** what happened, the likely consequences, what we've done, what they can do, and whom to contact. Agents: in-app notice plus email. For enquirers and customers (the agent is their fiduciary): tell the affected agents **at once (target ≤ 12 hours)** with the list of affected people and ready-to-send EN/HI text, and offer to notify on their behalf by email where we have an address |
| **≤ 72 hours** | **Detailed report to the Board:** updated facts, cause, mitigation, findings about who caused it, remedial steps, and the report of notifications to principals (or the longer period the Board allows) |
| After | Post-incident review; update this doc, TESTING.md and the register |

### Children (Section 9)
- **By design, Agent PRO has no children as users:** agents confirm they're 18 or older at sign-up (insurance agents must be adults anyway), and the enquiry consent includes "I'm 18 or older".
- No tracking, behavioural monitoring or targeted advertising anywhere in the product, for anyone.
- Minors as an agent's customers: the guardian-consent flow above (counsel); on the customer sign-up link, the parent or guardian ticks the separate `customer_signup_guardian` notice before Submit (section 5 above).

### Significant data fiduciary
Agent PRO is **unlikely to be notified** as one: the Government notifies SDFs by volume and sensitivity of data, risk to principals and to the State, and Agent PRO is a small processor with no special categories of data. If notified: a Data Protection Officer in India, an independent data auditor, a yearly DPIA and audit (Rule 13), due diligence on algorithms, and any localisation the Government specifies.

## Contact details (Rule 9)
- Agent PRO publishes its **grievance officer's** name, email and response time in the app (Settings → About → **Privacy & grievance**), in the privacy policy, and on every site's privacy page, and repeats it in every reply to a rights request.
- **Placeholder until decided:** name `{GRIEVANCE_OFFICER_NAME}`, email `{GRIEVANCE_EMAIL}`, phone `{GRIEVANCE_PHONE}` (optional), response time "within 30 days" (the Rules allow up to 90). Nothing using these placeholders may ship.

> TODO(doc): the grievance officer (designated person under Rule 9) isn't chosen yet. Fill in their name, email and optional phone here, in the notices and in the privacy policy, and list it in STATUS.md's open questions until then.
- Each agent's privacy page publishes the agent's own contact as the fiduciary's contact.

## Concrete change list (planned)
### Database (migration `…_privacy.sql`, plus `catalog_sync`)
**The `audit_log` owner-admin select policy** came with the admin tables in 8b-2 (`20261017132241_companies_offers.sql`, OFFERS.md); admins see only the agent fields needed to verify a company link (name, designation, city, email and the agency code from the request).

**P1-2 built (2026-10-17, `20261016220600_privacy.sql` + `20261016220836_catalog_sync.sql`, not applied yet):** `consents`, `data_requests`, `audit_log`; `submit_enquiry` with `consent`; `submit_data_request`; `record_consent`; `update_data_request` (named in place of `resolve_data_request`); the sign-up consent in the signup trigger; the `payments.raw` trigger; the three privacy CHECKs. Deliberately left for P2: `enquiries.consent_id` (the link is `consents.enquiry_id`), the `erased_at` columns, dropping `profiles.branch`, `nominations`, `incidents`, `withdraw_consent`, the erase RPCs, `my_data_summary`, `create_platform_request`, the jobs and a realtime publication for `data_requests` (not required here; P2-2 decides the notification path).
- **New tables:** `consents`, `data_requests` (`agent_id` nullable for platform requests; `addressed_to` `agent|platform`; `kind` from `catalog.dataRequestKinds`; `status` from `catalog.dataRequestStatuses`; `name`, `phone`, `message`, `enquiry_id`, `customer_id`, `due_at`, `resolved_at`, `resolution_note`, `escalated_at`), `nominations`, `audit_log` (shared with OFFERS.md), `incidents` (owner admins only). Agent tables: owner-only RLS; platform rows: owner admins only.
- **Columns:** `enquiries.consent_id`, `enquiries.erased_at`, `customers.erased_at`, `policies.erased_at`, `premium_payments.erased_at`; drop `profiles.branch` (provisional).
- **RPCs:** `submit_enquiry` (adds `consent`, `notice_version`; the web and its tests change together), `submit_data_request` (anon), `record_consent`, `withdraw_consent`, `erase_enquiry`, `erase_enquiries(status_filter, older_than)` (the bulk Clear old enquiries tool; owner only, returns the count), `erase_customer`, `my_data_summary`, `resolve_data_request`, `create_platform_request`. The anon advisor list grows by one function.
- **Jobs:** `private.retention_run()` and its steps; `pg_cron` schedule; `cron` Edge Function tasks for emails and Storage.
- **Catalog (built, P1-1):** `privacyNotices`, `dataRequestKinds`, `dataRequestStatuses`, `retention` (with `app_config.retention`); i18n `privacy.*`, `data_request_kind.*`, `data_request_status.*`. `consentPurposes` wasn't needed (one purpose per notice). Added by P1-3 (2026-10-17): `site.privacy_*`, `site.request_*`, `site.policy_*`, `site.terms_*`, `site.legal_*`, `site.form_error_consent`, `site.footer_privacy` and `site.form_message_hint`, EN and HI.
- **`payments.raw`** allow-list in the `billing` and `razorpay-webhook` functions.

### App
- Create account: the consent checkbox; the full notice screen. **Built (P1-4).**
- The first-customer attestation sheet (**built**, P1-4; also on Add enquiry); the under-18 consent toggle on the customer form (**built**); **Share privacy note** on the attestation sheet (**built**) and on Customer/Enquiry detail overflow (not built yet).
- Settings → **Privacy notice** row (**built**).
- **Privacy requests** list and detail; **Export this person's data**; **Erase permanently** on Enquiry and Customer detail; **Clear old enquiries** (Enquiries tab overflow).
- Settings → **Your data** (Download my data, Nominate someone, Contact the grievance officer, Delete account) and About → **Privacy & grievance**; the CSV export warning.
- Notifications: privacy requests (new and due), the old-enquiries warning, the account-deletion notices.

### Web
- The enquiry form's consent checkbox and message hint; the per-site **privacy page** with the notice and the request form; a footer link "Privacy".
- Self-hosted fonts (done).
- Agent PRO's own **privacy policy and terms** pages (`/privacy`, `/terms` on the primary host; already reserved slugs) in EN and HI. STATUS.md already lists these as missing content.

### Admin panel
- Owner-only **Privacy** area: the platform request inbox (agents' grievances, escalated requests), the breach register, and the retention job's last-run report. Admins never see agents' customers or enquiries.

### Existing decisions and docs this changes (to record when accepted)
- **#20** "data is kept" on lapse → kept for 12 months after lapse, then deleted with notice (accepted in #54).
- **#26** fonts from Google → self-hosted (accepted in #54).
- **#27** soft-deleted rows are purged after 30 days.
- **#30** `payments.raw` is minimised at write time; tax records are kept 8 years, anonymised.
- **#32** the account-deletion Edge Function is also run by the retention job.
- **#42** the free plan → Supabase Pro before launch (backups): **not approved yet, high priority** (#54).
- CLAUDE.md's "owner-only RLS" rule gains the admin-only exception (OFFERS.md).
- BACKEND.md, WEB_RENDERER.md, UX.md, SHARED_DEFINITIONS.md, BILLING.md (receipts retention, `raw`), TESTING.md.

## Roadmap
| Phase | WP | Scope | Owns | Model |
|---|---|---|---|---|
| **P1 Privacy foundations** (before the first public deploy of the renderer) | P1-1 Shared — **done 2026-10-17** | `privacyNotices`, `dataRequestKinds/Statuses`, `retention`, notice texts EN + HI, codegen hash function, `verifyReleaseReady` | `shared/`, `mobile/build-logic/` | Opus |
| | P1-2 Backend — **done 2026-10-17** (not applied to `AGENT_PRO` yet) | `consents`, `data_requests`, `audit_log`, `submit_enquiry` consent, `submit_data_request`, `record_consent`, `payments.raw` rule; tests `96_privacy.sql` | `supabase/` | Opus |
| | P1-3 Web — **done 2026-10-17** (deploy together with P1-2) | Consent checkbox, privacy page and request form, footer link, self-hosted fonts, privacy policy and terms page shells | `web/`, `scripts/` | Sonnet |
| | P1-4 App — **done 2026-10-17** (not yet run against the real backend; needs P1-2) | Sign-up consent, the notice screen, the customer attestation, the guardian toggle, Settings → Privacy notice | `feature/auth`, `feature/customers` (coordinate with their owners) | Sonnet |
| **P2 Rights and retention** (with Phase 13) | P2-1 Backend | `erase_*`, `my_data_summary`, `nominations`, retention jobs, `pg_cron`, `incidents` | `supabase/` | Opus |
| | P2-2 App | Your data, Privacy requests, export one person, erase, notices and digest rows | `feature/settings`, `feature/privacy` (new) | Opus |
| | P2-3 Admin | The Privacy area | `admin/` | Sonnet |
| | P2-4 Content | Privacy policy, terms, DPA text drafts for counsel (EN + HI) | `web/legal/` | Opus |
| | P2-5 Verifier | TESTING.md privacy checks | read-only | Sonnet |

## P1-1 contract (for P1-2, P1-3 and P1-4)
Built 2026-10-17. Everything below is generated from `shared/`; never copy a hash or a key list by hand.

| Who | Uses | Exact names and shapes |
|---|---|---|
| All clients | The consent payload | `{notice_key, version, hash}`: `notice_key` ∈ `agent_signup`, `enquiry_form`, `agent_customer_duty`, `guardian_consent`; `version` an integer; `hash` 64 lowercase hex, for the text **in the language shown** |
| P1-2 (SQL) | Notice lookups (`language sql immutable`, `search_path = ''`, revoked from `public, anon, authenticated`; call them from `SECURITY DEFINER` RPCs) | `public.catalog_privacy_notice(p_key text) → jsonb` = `{"version":1,"hash_en":"…","hash_hi":"…"}` for the current version of an active notice, else null · `public.catalog_privacy_notice_hash(p_key text, p_version integer, p_locale text) → text` (any released version) · `public.catalog_privacy_notice_locale(p_key text, p_version integer, p_hash text) → text` (null = invalid hash) |
| P1-2 (SQL) | Validation recipe for `record_consent` / `submit_enquiry` | accept only if `catalog_privacy_notice(k)` is not null, `version = (catalog_privacy_notice(k)->>'version')::int` and `catalog_privacy_notice_locale(k, version, hash)` is not null; store `notice_key`, `notice_version`, `notice_hash` and the returned locale. Otherwise `{ok:false, error:'invalid', field:'consent'}` |
| P1-2 (SQL) | Data requests | `public.catalog_data_request_kinds() → text[]` (active: `withdraw_consent, access, correction, erasure, grievance`), `public.catalog_data_request_statuses() → text[]` (active: `open, done, not_genuine`); timing in `app_config.retention` → `dataRequests.responseDays` (30), `reminderDays` ([7, 21]), `escalateAfterDays` (30), `keepYearsAfterClose` (3) |
| P1-2 (SQL) | CHECKs | **Done (P1-2):** `consents_notice_key_check` ← `privacyNotices`, `data_requests_kind_check` ← `dataRequestKinds`, `data_requests_status_check` ← `dataRequestStatuses` are in the generator's `CHECKS` and in `20261016220836_catalog_sync.sql` (BACKEND.md, catalog_sync contract) |
| P1-2 (SQL) | Retention | `app_config.retention` (= `catalog.retention`): `softDeletedDays`, `erasedTombstoneDays`, `lapsedAccount.{deleteAfterMonths: 12, noticeDaysBefore: [30, 7, 2]}`, `unconfirmedSignupDays`, `billingRecordsYearsAfterFinancialYear`, `registrantContactsDaysAfterLastDomain`, `domainQuotesDays`, `siteDailyViewsMonths`, `consentsYearsAfterData`, `dataRequests.*`, `auditLogMonths`, `orphanMediaDays`, `endedPolicySuggestDeleteYears`. No enquiry period, by decision |
| P1-3 (web) | Notice text and hash | `GET /shared/generated/privacy-notices.json` → `notices.enquiry_form.{version, labelKey, textKeys, hash.{en,hi}}`; render `textKeys` with `t(key, {name, phone, email})` from `shared/i18n/<locale>.json` (`privacy.enquiry_form.consent` is the checkbox line and `privacy.enquiry_form.link` its link; the rest is the privacy page); send `{notice_key: "enquiry_form", version, hash: hash[siteLocale]}`. Request-form choices: `catalog.dataRequestKinds` labels (`data_request_kind.*`), `withdraw_consent` first. A non-dev build should fail while `releaseBlockers` isn't empty |
| P1-4 (app) | Generated Kotlin (`com.sledje.agentpro.core.catalog`) | `PrivacyNotice.AgentSignup` / `.AgentCustomerDuty` / `.GuardianConsent`: `label`, `version`, `texts: List<StringResource>`, `hash(locale)`, `consent(locale): NoticeConsent(noticeKey, version, hash)`; the strings are `Res.string.cat_privacy_<notice>_<part>` (e.g. `cat_privacy_agent_signup_consent`, `cat_privacy_agent_signup_link_terms`, `cat_privacy_agent_signup_link_notice`, `cat_privacy_agent_customer_duty_action`). `Retention.*`, `DataRequestKind`, `DataRequestStatus` are in `Catalog.kt` |
| Release | Gate | `./gradlew verifyReleaseReady` fails while `{GRIEVANCE_OFFICER_NAME}`, `{GRIEVANCE_EMAIL}`, `{GRIEVANCE_PHONE}` or `{EMAIL_PROVIDER}` remain in `shared/i18n`; Android release builds depend on it |

## Tests (to add to TESTING.md when built)
- **SQL** (the P1-2 part is built in `supabase/tests/96_privacy.sql`; erase and retention come with P2): `submit_enquiry` without consent or with an unknown notice version → `invalid`/`consent`, nothing inserted; with consent → one enquiry and one consent with the server hash; `submit_data_request` validation, honeypot and rate limit; `erase_customer` redacts every linked row and leaves no personal value (checked column by column), works when lapsed, and writes an audit row without personal data; the retention job with moved clocks (soft-deleted purge at 30 days, the lapsed-account notices at 30 days, 7 days and 48 hours and the deletion at 12 months, **no** enquiry ever deleted by the job, quotes, views, logs, unconfirmed users); another agent can't erase or read a request; anon can't read the new tables; `payments.raw` holds only allow-listed keys.
- **Web:** the form can't submit with the box unticked; the privacy page in both languages names the agent and the grievance contact; no request goes to `fonts.googleapis.com` (if self-hosting is approved); no cookies are set.
- **App:** the Create account button is disabled until the box is ticked; the attestation shows once per version; the under-18 toggle is required; the export of one person contains only that person; erase removes the person from Room after sync; Download my data contains the processor list.
- **Process:** a tabletop run of the breach runbook before launch.

## Decisions (2026-10-16)
The user's answers are recorded in DECISIONS #54:
- **Lapsed accounts** are deleted 12 months after lapse, with notices at 30 days, 7 days and 48 hours.
- **Never-converted enquiries are the agent's call:** no automatic deletion and no retention setting; the app provides erase and Clear old enquiries.
- **Public requests** go to the agent (30-day target, call-back verification, platform follow-up).
- **Approved:** self-hosted fonts (P1) and a transactional email provider (not chosen yet).
- **Not approved yet, high priority:** Supabase Pro for backups ([Backup risk](#backup-risk-open-high-priority)).
- **Still open:** the grievance officer (the TODO under Contact details).

## Sources (consulted 2026-10-16)
- KPMG India, "DPDP Rules 2025: Guidance to DPDP Act implementation", November 2025 — https://assets.kpmg.com/content/dam/kpmgsites/in/pdf/2025/11/dpdp-rules-2025-guidance-to-dpdp-act-implementation.pdf
- SCC Online, "MeitY notified Digital Personal Data Protection Rules, 2025", 14 November 2025 — https://www.scconline.com/blog/post/2025/11/14/meity-notified-digital-personal-data-protection-rules-2025/
- MirvoLegal, "DPDP Rules, 2025: Compliance Timeline for Data Fiduciaries (as at September 2026)" — https://www.mirvolegal.com/insights/dpdp-rules-2025-compliance-timeline/
- SS Rana & Co., "MeitY Notifies Final Digital Personal Data Protection Rules 2025" — https://ssrana.in/articles/meity-notifies-final-digital-personal-data-protection-rules-2025/
- DSCI, "FAQ on data retention and data erasure (v2)", 2025 — https://www.dsci.in/files/content/documents/2025/faq-on-data-retention-and-data-erasure-v2.pdf
- consentos.in, "DPDP Rules notification status (2025–2026)" — https://consentos.in/learn/dpdp-rules-notification-status/
- The official text (not opened, HTTP 403): MeitY, G.S.R. 846(E) — https://www.meity.gov.in/static/uploads/2025/11/53450e6e5dc0bfa85ebd78686cadad39.pdf; corrigendum G.S.R. 892(E) — https://www.meity.gov.in/static/uploads/2025/12/3c7ebbae0e5456f493f486e6845df86b.pdf
