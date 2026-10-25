# Product

## Who it's for
Agent PRO is for insurance agents in India (LIC and other insurers; each agent belongs to one company, DECISIONS #47): independent insurance advisors, mostly working from a phone, often in the field with patchy network. They need to:
- look credible online,
- capture leads,
- remember follow-ups and premium due dates, and
- see how their business is doing.

## Platforms
- **Android:** v1.
- **iOS:** a later phase, built from the same Kotlin Multiplatform code (DECISIONS #22).

## Goals
1. A professional personal website in minutes, from templates, with no technical skills needed.
2. Every website enquiry lands in the app instantly, with a notification.
3. One place to manage customers, their policies and premium dues.
4. Clear charts showing enquiry trends, conversion, dues and collections.
5. A uniform, trustworthy look across the app and the websites, in English and Hindi.

## v1 features
| Area | Features |
|---|---|
| Account | Sign-up with full name, mobile, email and password (typed twice), automatic sign-in once the email is confirmed; sign-in, password reset, change password, delete account |
| Onboarding | Profile → pick template → choose link (slug) → publish now or later; setup checklist on Home |
| Website builder | 3 templates (Classic "Trust", Modern "Fresh", Minimal "Simple")<br>Section editor driven by the site schema<br>Show/hide and reorder sections<br>8 accent swatches; site language EN or HI<br>Draft vs published, with preview before publishing<br>Change link, share, copy, QR code<br>**Add to home screen:** every site can be installed on a phone like an app (an icon with the agent's name); the agent installs their own from the Website tab, and customers get an "Add to home screen" button the agent can turn off<br>View stats |
| Enquiries | Arrive from the website form or are added manually (walk-in, referral, phone)<br>Statuses: New, Contacted, Follow-up, Converted, Closed<br>Follow-up date with reminder; notes<br>Convert to customer |
| Customers | Add manually or from the system contact picker, or send the customer a sign-up link that the agent watches fill in live; Submit adds them automatically<br>The form saves itself as it's typed; unfinished forms (by the agent or the customer) are listed under Incomplete forms, to finish or delete, and are deleted after 30 days<br>Detail screen with Policies, Details and Activity tabs |
| Policies and premiums | Plan, policy number, sum assured, premium, mode (yearly, half-yearly, quarterly, monthly), start date<br>Next due date calculated automatically<br>Record a premium as paid, which moves the due date forward; payment history |
| Dues | A segment inside the Customers tab: Overdue, This week, This month and Next 3 months, with ₹ totals and record paid |
| Insights | Home KPIs and 2 charts; Insights screen with 6 charts, a period filter and a table view for each chart |
| Notifications | New enquiry (realtime while the app is open, otherwise polled every 15 min), follow-up reminder, daily digest of dues and follow-ups |
| Service plan | **14-day free trial**, then **₹499 for the first year** and **₹299 per year to renew** (incl. GST)<br>15-day grace period, then the site goes offline and the app becomes read-only until renewed; data is kept<br>Manual renewal with reminders. See BILLING.md |
| Custom domain | Paid plan only; an **extra cost** at the **registrar's live price plus a fixed service fee** (₹99, configurable)<br>Search, buy and renew in the app; connect a domain you already own for free<br>HTTPS set up automatically; live setup progress; full refund if registration fails. See DOMAINS.md |
| Payments | Razorpay checkout (UPI, cards, netbanking, wallets); receipts in Plan & billing |
| Data | CSV export (customers, enquiries, policies, payments) as one zip |
| Offline | Read-only cache: the app opens and shows data offline; edits need a connection |
| Languages | English and Hindi at launch, with an in-app switch |
| Customer login | The agent gives a customer access from Customer detail (a link sent by WhatsApp/SMS); the customer sets a 6-digit PIN and then sees their policies, dues and payments on the agent's site, can ask about a policy, and can request corrections or deletion. Installable as "{Agent} · My policies", with opt-in premium reminders. See WEB_RENDERER.md, Customer login |

## Design principles
- **Easy to use, professional, close to white, uncluttered.**
- One job and one primary action per screen, with details one tap away. See the calm-screen rules in DESIGN_SYSTEM.md.

## Designed in, built later
- **Domain auto-renewal** through Razorpay Subscriptions or a UPI mandate. v1 renews manually after reminders.
- **Service plan auto-renewal** through a mandate or subscription. v1 renews manually.
- **Other paid add-ons.** They would reuse `PaymentProvider`, `orders` and `payments` with a new order `kind`.
- **FCM server push.** v1 uses realtime plus polling.
- **Server-side rendering of sites** for better SEO. The templates are already pure functions, so the same code can run on the server.

## Out of scope for v1
- WhatsApp integration (not chosen).
- Phone OTP login (it needs a paid SMS provider).
- Full offline editing with sync.
- Multiple agents per account, or agency/team management.
- Integration with official LIC systems.

## Constraints
- **No insurer logo or trademark artwork is bundled** in templates or app assets. Company logos appear only as admin-uploaded content, with the company's permission (DECISIONS #47). The agent enters their own designation (for example "LIC Agent · Club Member"), and site footers say "Independent insurance advisor".
- The app must comply with Play Store policy: in-app account deletion, a privacy policy, and a sensible moment to ask for the notification permission.

## Requirement log
Add new requirements here, with the date, before implementing them.
- 2026-10-15: initial requirements (the website from templates, enquiries, customers, data visualisation).
- 2026-10-15: user chose Supabase, a single renderer site, email login, policy tracking, reminders and CSV export.
- 2026-10-15: user chose Hindi at launch and a read-only offline cache. Custom domain purchase must be easy to add later.
- 2026-10-15: user asked that everything be documented and that the docs be updated with every change (see CLAUDE.md).
- 2026-10-15: payments are a working v1 feature: ₹499 to buy, ₹299 for yearly renewal. This was first read as custom-domain pricing, which the next entry corrects.
- 2026-10-15: the app is **Kotlin Multiplatform**, with Android shipping first and iOS later.
- 2026-10-15 (correction): **₹499/₹299 are for the Agent PRO service**, not the domain. A custom domain is an **additional cost**, priced from the **registrar's live price**.
  - The user chose a **free trial, then pay**.
  - On lapse: **grace, then the site goes offline and the app becomes read-only**.
  - Domain price: **registrar price plus a fixed fee**.
- 2026-10-15: the theme must favour ease of use and professionalism, be close to white, and keep screens well planned and uncluttered. This replaces the earlier "Trust & Warmth" theme.
- 2026-10-15: use 3 standard spacing values, a defined font, and at most 4 font sizes, each 1.67× the previous. Implemented as 8/16/24 dp and 14/23/39/65 sp, with Inter and Noto Sans Devanagari (DECISIONS #19).
- 2026-10-15: the user asked that the work be split across parallel sub-agents run by an orchestrator, using Sonnet or Opus depending on how complex each task is (DECISIONS #24).
- 2026-10-15: website enquiries accept **Indian mobile numbers only**. A premium payment **can be undone only if it's the latest one**. **Account deletion runs through a server-side function** that also removes the agent's uploaded media (DECISIONS #31, #32).
- 2026-10-16: a password-reset link must open a **Set new password** screen (UX.md). The app's language switch must use Android's per-app language (AppCompat), as ARCHITECTURE.md says. The **login session must be encrypted at rest** with an Android Keystore key, behind the `SecureSettings` seam (DECISIONS #44).
- 2026-10-16: **multiple insurance companies and their offers** (user request):
  - An agent may be connected to one or more insurance companies (LIC and others).
  - Each company is a separate entity with its own **ongoing offers**.
  - Offers are entered and updated by an **admin**, in a **separate admin terminal** where the admin also adds and updates companies.
  - The agent chooses which offers to display on their website and **where**, and can also add **custom offers** of their own.
  - **It must be super easy:** the template is kept on the agent's device; the agent taps the part of the site where an offer should appear, then picks one from the list of ongoing offers.
- 2026-10-16 (answers on companies and offers, DECISIONS #47):
  - The platform serves agents of **many insurance companies**, and **each agent belongs to exactly one company**.
  - The admin terminal is a **separate web admin panel**.
  - Offers go into **slots**: a featured offer in the hero, plus an Offers section the agent can place anywhere. The agent taps the slot on the on-device preview, then picks an offer.
  - **Company logos are allowed**, uploaded by the admin only for companies that have given permission.
  - Custom offers stay, with a "Details provided by the agent" note (provisional).
- 2026-10-16: **the whole product must comply with India's DPDP Act 2023 (Digital Personal Data Protection) and its Rules** (user request). This covers the app, the agent websites and their enquiry form, the backend, and the admin panel. The design goes in `docs/PRIVACY.md` (DECISIONS #48); it isn't legal advice, and counsel should review it before launch.
- 2026-10-17: **auth emails (sign-up confirmation, password reset) must allow at least 100 per hour.** Supabase's built-in sender allows only about 2 per hour and blocked a test sign-up. This needs custom SMTP through a transactional email provider (already approved, DECISIONS #54), after which the limit is raised in Supabase Auth → Rate Limits.
- 2026-10-17: **auth emails (confirmation, password reset, invite, email change, code) use a branded HTML design** in the app's colours, type and shapes, in English and Hindi in one email, and readable in dark mode. Requested by the user.
- 2026-10-17: **an auth email that wasn't sent, or never arrived, must always be sendable again from the app.** Reported by the user: after a refused or lost confirmation email there was no way to get it again. Resend must be offered on "Check your email", on "Reset link sent" and on Sign in when the email isn't confirmed yet. It must say honestly when Supabase is refusing (a countdown for the per-address wait, an "about an hour" message for the hourly quota), and never fail silently.
- 2026-10-17: **Create account asks for the full name, mobile number, email, password and Confirm password** (user request: "the sign-up screen is very naive"). Details given at sign-up must **not be asked again** later in onboarding, but must stay **editable**. **After signing up the agent is signed in automatically**, without retyping the email and password. The link back to Sign in reads **"Sign in instead"**, so it isn't mistaken for the form's own button (DECISIONS #70).
- 2026-10-17: **when the site is created (Publish now), three screens follow one after another, very smoothly** (user request): **1 Add the site to your phone**, **2 a small preview** of the live site, **3 Share it through other apps**. Then Home. Skipping the first is allowed ("Not now"). "Later" (a draft, not live) skips all three, since there is nothing to add or share yet (DECISIONS #71).
- 2026-10-17: the website enquiry form's "Interested in" must start on a blank "Select…" option rather than "Other" (user request). Interest stays optional.
- 2026-10-17: **install the website on the phone like an app** (user request, "like Chrome's install web app"). After the link exists, the app offers **Add to home screen**, which opens the site in the browser with an install sheet. The user also chose: **customers see an "Add to home screen" button on agents' sites**, and **each agent can turn it off** (DECISIONS #65).
- 2026-10-18: **the agent website, v2** (user request; DECISIONS #75–#79):
  - **Server-side rendering:** the public site is rendered on the server (the Cloudflare Worker), so the full page arrives in the first response.
  - **A new site already looks finished:** every general section (hero, about, services, highlights, FAQ, contact) is filled with good default copy built from the agent's profile, in the site's language, so the agent can see and approve the whole site before typing anything. The copy is stored as normal content and stays editable. Testimonials, achievements and years of experience are never invented; they start hidden and empty.
  - **Two design families:** **"Meet me"** (about the agent as a person; the 3 current templates, refreshed) and **"Best offers"** (a persuasive showcase of the best offers from the agent's company, to win clients; 2 new templates). The agent picks the family first, then a template.
  - **The agent as the person providing the service:** the site reads partly as a conversation with the agent (a greeting and short "speech bubble" lines from the agent, FAQs as questions and answers, quick-reply chips), while still working like a normal website (headings, sections, contact form, call and WhatsApp).
  - **Disguised names:** offers and policies shown on sites use generic names, never the insurer's product names, to avoid copyright and trademark issues. The admin writes each offer's public name, and an automatic check rejects known product and company names. **The insurer's name and logo aren't shown on sites either.**
  - **Easy for SEO:** crawlable server-rendered HTML, semantic headings, complete head tags and structured data, `robots.txt` and sitemaps, fast loading, and default copy that varies between agents so sites don't duplicate each other.
- 2026-10-18: **images on agent websites** (user request: "analyse where it needs images and give different prompts for the images"): a site needs images in specific places (plan types, offers, the Best offers hero, the contact section, share previews). Each gets its own generation prompt in one house style (docs/IMAGES.md, DECISIONS #81). **Demo offers are modelled on the insurer's current public offers** ("for the offers go to the LIC site"), with disguised names and our own wording (`supabase/demo/demo-offers.json`).
- 2026-10-20: **the app's site previews must show what customers will see** (user, after a review of the in-app previews): (1) previews show the agent's real company offers, as the live site does; (2) in Website → Appearance, previewing a "Best offers" template from a "Meet me" site shows the page as it will be after switching; (3) **the three "Meet me" templates (Classic, Modern, Minimal) must look genuinely different**, not just restyled, and the user sees screenshots before they go live (DECISIONS #84–#86).
- 2026-10-21: **no chat-style look on agent sites** (user: "I told you to implement a chat-like feature on the site but it looks horrible, keep it normal"). The speech bubbles, the greeting bubble, the FAQ-as-chat and the chat-style thank-you are removed from every template; sites look like normal websites (headings, sections, an FAQ accordion, a normal success message). The quick-interest links, the sticky Call/WhatsApp bar, offers and images stay (DECISIONS #87). This replaces the "conversation with the agent" part of the 2026-10-18 entry.
- 2026-10-22: **OTP instead of links for Auth emails** (user: "implement otp authentication in supabase instead of links"): the sign-up confirmation and the password reset email carry a one-time code the agent types into the app, not a link (DECISIONS #88). Password sign-in stays.
- 2026-10-22: **companies and insurance types at sign-up** (user: "for the user signing up make them select their companies as well, and particular types of insurance they deal with in each company"): in onboarding step 1, straight after Create account, the agent picks **one or more companies** and, **for each company, the types of insurance they deal with there** (a new list, `catalog.insuranceTypes`; provisional values: life, health, motor, home, travel, business, accident). Each company needs the agent's agency code there; "My company isn't listed" stays. Every link is verified by an admin separately (DECISIONS #53 still holds per link). Editable later in Settings → Profile (DECISIONS #89 replaces the one-company rule).
- 2026-10-22: **companies and insurance types on the site, and a visual redesign** (user: "integrate them in the generated site; visually redesign the site and see everything looks standard and planned"): the site shows the agent's **approved companies by name (plain text, no logos)**, each with the insurance types the agent deals in there (DECISIONS #90). All five templates get a visual redesign so every section looks consistent, standard and deliberately laid out (one grid, one rhythm, consistent cards, headings and buttons, using only the design tokens). As with 2026-10-20, the user sees screenshots before anything goes live.
- 2026-10-22: **the site must match the companies and insurance types the agent deals in** (user: "the website created right now is not in accordance to the company and insurance type it deals with"). Today every new site gets the same life-insurance services (term, endowment, child, pension, health), life-and-family copy, and an enquiry form that only knows life plans, whatever the agent picked in step 1. Instead, the services, the hero's quick links, the enquiry form's "Interested in" options and the starter copy follow the agent's insurance types (motor, home, travel, business and accident included), and change with them when the agent edits their companies (DECISIONS #93).
- 2026-10-22: **use real insurance companies, not a demo placeholder** (user: "instead of demo use real insurance companies as per the plan", said while finishing the companies/insurance-types rollout above). The live company picker (onboarding, Settings → Profile) and the "Companies I represent" section (#90) should offer genuine insurers, not the fake "Demo Life Insurer" test row. The live `companies` table now holds 11 real IRDAI-registered insurers; see DECISIONS #94 for the mechanics (why offers and memberships had to be re-created rather than updated) and STATUS.md for what the user still needs to apply.
- 2026-10-23: **customers log in to their agent's site** (user: "each agent's customers should be able to login into their sites; for now decide everything", then "what stack will you use for the site to be most useful when the customer has to download it as a PWA"). Each agent's customer signs in **on that agent's site** (`/a/<slug>/my`, or `/my` on a custom domain) and sees **their policies, next due dates and payment history**, contacts the agent about a policy ("Ask about this policy", "I've paid this premium"), and sees their details with requests to correct or delete them (DPDP rights; the agent is the fiduciary). Access is **given by the agent** from Customer detail (a login link shared by WhatsApp or SMS); the customer confirms their mobile number and sets a **6-digit PIN**, then signs in with mobile + PIN. Read-only in v1: no online payment, uploads or self-editing. The portal is **installable as its own app** ("{Agent} · My policies"), opens offline on the last-seen page, and can send **premium due reminders** (Web Push, opt-in). DECISIONS #96–#100.
- 2026-10-23: **agent sites must tell visitors about customer accounts** (user: "does the agent's website endorse the customers' accounts and dashboards? If not, plan it well, then implement it, in the sites and the templates as well"). Until now a site only had a small "My policies" footer link. Every template now gets a "For my customers" section (what customers can see after signing in, a Sign in button, and "First time? Ask me for your login link") and a "Customer login" link at the top, both shown only when customer login is on; agents can move or hide the section. The customer's "My policies" page opens with a dashboard row (policies, premium per year, total cover, next due). DECISIONS #102.
- 2026-10-24: **already registered agents must be led to new features** (user: "how do already registered users get onboarded on the new features"). Agents who signed up before companies/insurance types and customer login existed got no prompt in the app. On the first open after the update they see a one-time "What's new" sheet (only agents who already have a site; new sign-ups skip it), and Home's "Finish setup" checklist gains "Add your companies" and "Give a customer their login", each opening the right screen. Also reported the same day: the Home avatar ignored the agent's photo, and the checklist rows weren't tappable (both fixed). DECISIONS #103.
- 2026-10-24: **agents must be able to manage the offers on their site from the app** (user: "in the website I see the 'My pick for you' section; I can't find a way to add or edit it through the app"). The site shows company offers automatically, but the app had no Offers editor (OFFERS.md 8b-4 was never built): the agent couldn't hide or move the section, pick offers, set the featured offer or add their own. The user chose a **list editor first, tap-to-place on the preview next** (DECISIONS #104).
- 2026-10-25: **two ways to add a customer, and nothing typed is lost** (user: "when we tap on add customers there should be 2 options: first to add manually, second to give the link to the sign up screen; let all the data they are entering be saved"; DECISIONS #108). The user then chose: the customer's Submit **adds the customer automatically**, unfinished forms are **kept 30 days**, and the agent sees **the values and the field the customer is on**.
  - **Add customer** offers **Fill it in myself** (with Pick from contacts as a shortcut) and **Send the customer a link**.
  - **Fill it in myself:** the form saves itself to the database as the agent types. A form left unfinished appears under **Incomplete forms** in the Customers tab, where the agent can carry on with it or **delete** it.
  - **Send the customer a link:** the agent shares a link (WhatsApp or other apps) to a sign-up page on the agent's website. Everything the customer types is **saved live** in the database, so a form left half-done is kept and listed under Incomplete forms. The agent **watches the form fill in live** in the app (each field as it's typed, which field the customer is on, and whether they're on the page now), so they can guide the customer, e.g. on a call. When the customer taps **Submit**, they're added to the agent's customers straight away.
  - **Privacy (DPDP):** the sign-up page shows a notice and asks for consent **before** any field can be filled, so nothing is stored without consent. A customer under 18 needs a parent's or guardian's agreement on the page. Unfinished forms are deleted after 30 days. The agent confirms their one-time customer-data duty before sending a link, as for adding a customer.
