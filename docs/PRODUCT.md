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
| Customers | Add manually or from the system contact picker<br>Detail screen with Policies, Details and Activity tabs |
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
