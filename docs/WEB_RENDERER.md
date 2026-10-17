# Web renderer — public agent sites

There's one static site for **every** agent. Content is fetched at runtime, so agents never need a redeploy when they edit or publish.

## Hosting
- **Current deployment (2026-10-17):** a preview at `https://agentpro-sites.sledjesoftwares.workers.dev` (demo data only), built with `SITE_BASE_URL` set to that URL. `_headers` uses `! Cache-Control` so `/config.js` (`no-store`) and `/a/*` (`no-cache`) get exactly one value.
- **Cloudflare Workers static assets** (DECISIONS #59): a Worker named `agentpro-sites`, config in `web/wrangler.jsonc`, assets served from `web/dist`. There is no Worker script. Agents' own domains attach as Cloudflare for SaaS custom hostnames (DOMAINS.md). The platform domain is not bought yet, so there are no routes or custom domains in the config; the preview lives at `agentpro-sites.<account-subdomain>.workers.dev` (`workers_dev = true`).
- **Build:**
  - `sh scripts/build-web.sh` copies `web/` and `shared/` into `web/dist/`.
  - It writes `web/dist/config.js` from the `SUPABASE_URL` and `SUPABASE_ANON_KEY` environment variables.
  - It generates `tokens.css` from `design-tokens.json`.
- **SPA routing:** `assets.not_found_handling = "single-page-application"` serves `/index.html` for any path that is not a real file, so `/a/<slug>` works.
- **Headers:** `web/_headers` (copied into `dist/`, not served as an asset) sets `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, a minimal `Permissions-Policy` and a CSP (`self` + `*.supabase.co` for `connect-src`/`img-src` ; `style-src 'self' 'unsafe-inline'`, `font-src 'self'`: no third-party font origins, the fonts are self-hosted). Filenames are not hashed, so assets get `max-age=300, stale-while-revalidate=86400`; `/index.html` is `no-cache` and `/config.js` is `no-store`. `_headers` allows 100 rules and applies only to static-asset responses.
- **Preview deploy (user approval each time; the user logs in once):** `sh scripts/build-web.sh` (real env, never `WEB_DEV=1`), then `npx wrangler@4.151.1 login` once, then `npx wrangler@4.151.1 deploy --config web/wrangler.jsonc`. Check config without deploying: `npx wrangler@4.151.1 deploy --dry-run --config web/wrangler.jsonc`. Public launch waits for the P1 privacy items; until then the preview shows demo data only.
- Build env: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SITE_BASE_URL` (the primary host, default `http://localhost:8080`). `web/dist/` and `web/config.js` are git-ignored.
- **Deploying needs the user's approval each time.** Record each deploy in STATUS.md (Environment) and in CHANGELOG.md.

## Structure
```
web/
  index.html          shell: loading state, <main id="root">, noscript; links tokens.css + styles/base.css
  app.js              resolve site -> fetch -> pick template -> render -> wire form -> record view; window.renderSite(json)
  api.js              plain fetch() to {SUPABASE_URL}/rest/v1/rpc/* with the anon key (no SDK); normalizeSite()
  i18n.js             loads shared/i18n/{locale}.json; t(key, params); localeKeys(catalog)/pickLocale (supported locales from catalog.locales, first = default)
  head.js             pure headData() (title, description, canonical, OG, JSON-LD) + applyHead()
  form.js             enquiry validation (same rules as submit_enquiry) + wireForm()
  styles/base.css     shared component styles (tokens only)
  sections/           util.js (escaping, URL/phone helpers, schema lookups), icons.js, index.js (order/visibility, compose),
                      hero.js about.js services.js highlights.js achievements.js testimonials.js faq.js contact.js footer.js
  templates/
    classic/  render.js style.css preview.webp     "Trust": centred portrait hero, stacked sections
    modern/   render.js style.css preview.webp     "Fresh": split hero band, hairline cards
    minimal/  render.js style.css preview.webp     "Simple": type-led, single column
  config.example.js   template for config.js (the real one is generated into web/dist; git-ignored)
  wrangler.jsonc      Cloudflare Worker config (static assets, SPA fallback)
  _headers            security + cache headers (copied into dist/)
  dev-mock.js, dev-fixtures/   LOCAL DEV ONLY (see "Local development")
  package.json        {"type":"module"} only, so Node can import the pure render code in tests; no dependencies
scripts/
  build-web.sh        copies web/ + shared/ into web/dist, writes config.js, generates tokens.css (gen-tokens.mjs)
  gen-tokens.mjs      shared/design-tokens.json -> tokens.css (--c-*, --accent, --text-s|m|l|xl, --line-*, --space-s|m|l, --shape-*, --motion-*)
  lint-web-css.mjs    fails on raw px/rem/em in margin/padding/gap/font-size
  test-web.mjs        Node checks of the pure renderer, validation, head data and per-template size budget
```
The sections are rendered from the data in `site-schema.json` (default CTA via `defaultKey`, option labels for languages and highlight icons, the pinned sections). Interests come from `catalog.json`; the accent hex comes from `catalog.accentSwatches`; supported locales from `catalog.locales`; valid template keys from `shared/templates/index.json`. `app.js` loads site-schema, catalog and the templates registry at runtime; nothing in `web/` hard-codes them (the accent fallback is `var(--c-primary)`). `tokens` passed to `render` is `{ accent, schema, catalog }`.

## Resolution
0. A `?preview=1` page does no resolution at all; it waits for `window.renderSite(json)` (the in-app preview). The preview never submits enquiries or records views.
1. If the host is the primary host (`SITE_BASE_URL`), read the slug from `/a/{slug}`, falling back to `?s={slug}`. Then call `get_public_site`.
2. Any other host is a custom domain: call `get_public_site_by_host(location.hostname)` and render at `/`. Only `active` and `renew_soon` domains resolve; an expired domain shows "This page isn't available", while the free link keeps working.
3. If the site isn't found, isn't published, or **its owner's plan has lapsed** (BILLING.md), show a friendly "This page isn't available" message with no agent data.

## Templates
Templates are **pure functions**:
```js
export function render(site, t, tokens) { return htmlString }
// site = { content, template_key, accent, locale, profile }, t = i18n lookup
```
- A template only arranges the shared section partials and supplies its CSS layout.
  - Section order and visibility come from `content.sectionOrder` and `content.hidden`.
  - Fields come from `site-schema.json`.
- Because they're pure, the same code runs in three places:
  1. the browser,
  2. the in-app preview (`SitePreview`: an Android WebView now, WKWebView on iOS later), where the bundled web files call `window.renderSite(json)` with the unsaved draft, and
  3. later, a Cloudflare Worker for server-side rendering, with no rewrite.
- **Colours** always come from `tokens.css` plus `--accent`, set from the agent's swatch.
- **Look:** clean and professional, matching the app (DESIGN_SYSTEM.md).
  - White background, the Inter font and generous whitespace.
  - One call to action per section.
  - The accent is only for buttons, links and small highlights, never for large colour blocks.
  - The three templates differ in **layout** only, not in colour scheme.

## Page features
- **Head tags:** `<title>` ("{Name} — {designation}, {city}"), meta description from the hero subheadline, `<link rel="canonical">` (the active custom domain if there is one), Open Graph tags (hero photo) and JSON-LD `InsuranceAgency` with the address and phone.
- **Enquiry form:** name, phone, email (optional), interest (from `catalog.interests`), message, and a hidden honeypot field.
  - Validation is inline, using the same rules as `submit_enquiry`.
  - The button shows a loading state.
  - On success: "Thank you — {first name} will contact you soon", with a Call button.
  - When rate-limited: "You've already sent a request. {name} will be in touch."
- **Contact:** tap-to-call, email and map links.
- **Branding footer:** "{Name} · Independent insurance advisor", plus "Made with Agent PRO". **No LIC logo or trademark artwork.**
- **Performance:**
  - No framework and no SDK; aim for under 50KB of JS and CSS per template.
  - Images are lazy-loaded with explicit dimensions.
  - Fonts are **self-hosted** (DECISIONS #54): `web/styles/fonts.css` declares `@font-face` rules (`font-display: swap`, fontsource `unicode-range`s) for files in `web/fonts/` (copied to `dist/fonts/` by `build-web.sh`). Source: npm `@fontsource/inter@5.3.0` (latin + latin-ext, 400/500/600/700; latin-ext carries the rupee sign U+20B9) and `@fontsource/noto-sans-devanagari@5.3.0` (devanagari, 400/500/600), woff2 only, with the OFL licence files. Only the Inter latin 400 file is preloaded; the browser fetches other files when the text needs them (latin 94.5 KB for all four weights, Devanagari 154 KB for Hindi pages). No request leaves the origin for fonts. To upgrade, re-pack the fontsource packages and replace the files and `fonts.css`.
  - The enquiry form's "Interested in" select starts on a blank placeholder (`site.form_interest_placeholder`, value `""`); left blank, no interest is sent (`null`), it is never defaulted to "other".
  - Target Lighthouse mobile scores of 90 or more for Performance, Accessibility and SEO.
- **Analytics:** one `record_site_view` call per page load, debounced per session with `sessionStorage`.

### Planned (designed, not built)
- **Offers (OFFERS.md, Phase 8b):** a featured-offer banner in the hero (no image), a new `offers` section (partials `sections/offer.js` and `sections/offers.js`), rendered only from the payload's resolved `offers`; ended offers are hidden by the RPC and again on the client. Company offers and logos come only from the payload, which includes them only once an admin has approved the agent's company link (`company.logo_url`, admin-uploaded with permission), at a fixed 24px height; custom offers carry "Details provided by the agent". No `Offer` JSON-LD. A **slot mode** for the in-app preview only (`?preview=1` plus `renderSite(json, {slots: true})`) draws tap targets and posts messages through `preview-bridge.js`, which never loads on public pages. On Android the preview already loads from `https://appassets.androidplatform.net`, so the bridge uses `addWebMessageListener` restricted to that origin.
- **Privacy (PRIVACY.md, P1-3, built 2026-10-17; needs backend P1-2 deployed together):**
  - **Consent checkbox:** the enquiry form has an unticked, required checkbox. Its text is the `enquiry_form` notice's first two `textKeys` (consent line and link) rendered with `t(key, {name})`, and the link goes to the site's privacy page. Unticked: an inline error and no network call. Ticked: `submit_enquiry` also gets `consent = {notice_key: "enquiry_form", version, hash[siteLocale]}` from `shared/generated/privacy-notices.json` (`form.js consentFor`). A server `{error:'invalid', field:'consent'}` shows the same inline error. The message field has the hint `site.form_message_hint`.
  - **Privacy page:** `/a/<slug>/privacy` (local fallback `?s=<slug>&page=privacy`; `/privacy` on a custom domain), `web/privacy.js`, lazy-imported. It shows the agent as data fiduciary, the notice parts (`textKeys[3..]`: what, why, who, how long, rights, with `{name}`, `{phone}`, `{email}` from the Contact section/profile), the agent's contact, and a request form (kind from `catalog.dataRequestKinds`, `withdraw_consent` first; name, mobile, message, honeypot; no consent box) calling `submit_data_request` with thank-you, rate-limited, invalid-field and generic-error states.
  - **Footer:** "Privacy" link on every site page. Routing is in `web/route.js` (`resolveTarget`, `siteLinks`).
  - **Platform pages:** `/privacy` and `/terms` on the primary host (`web/legal.js`, EN/HI, `?lang=hi|en`). They are shells: the policy reuses the `agent_signup` notice texts and the terms are headings with "being finalised" bodies (final drafts are P2-4). Grievance officer and email provider placeholders show as-is.
  - **Release gate:** `RELEASE=1 sh scripts/build-web.sh` exits 1 while `releaseBlockers` in `privacy-notices.json` or any `{UPPER_CASE}` token in `shared/i18n` remains (`scripts/release-check.mjs` lists them). Default builds only warn. Production deploys must use `RELEASE=1`.
  - **Self-hosted fonts** instead of Google Fonts (DECISIONS #54; done).

## Behaviour notes
- **Section order/visibility:** `content.sectionOrder` is followed (unknown keys dropped); `content.hidden` hides a section unless the schema marks it `pinned` (hero, contact), which is always shown (hero first, contact last if missing from the order). A section with no content renders nothing.
- **Escaping:** every agent string goes through `esc()`; links accept only `http(s)` (map, photo); phone/email are rebuilt from digits/validated before becoming `tel:`/`wa.me`/`mailto:`; JSON-LD escapes `<`. Multiline text uses `white-space: pre-line`, not `<br>`.
- **Images:** the hero photo is `loading` default (eager) with `fetchpriority=high` because it is the LCP element; it has explicit `width`/`height`. No other section has images.
- **Form:** posts `name, phone, email, interest (default "other"), message, honeypot` to `submit_enquiry` with the exact argument names from BACKEND.md. The client rules match the RPC (name 1-80, Indian mobile, email <= 120, message <= 500, interest from the catalog). The response is read by its exact `error` value: `{ok:true}` shows the thank-you; `rate_limited` shows the rate-limited message; `invalid` + `field` shows the inline error on that field (`site.form_error_phone`/`_email`/`_required`); `unavailable` or anything unexpected shows the generic error. HTTP 429 is also treated as rate-limited.
- **Footer:** `site.footer_advisor` + `site.footer_made_with`. No logos.
- **Errors:** a network/RPC error shows the unavailable page with the generic error text; a missing site shows the unavailable text. Both set `noindex` and show no agent data.
- **Agency code:** shown when `about.showAgencyCode` is on and `profile.agency_code` is present (the RPC includes it only when that toggle is on).
- **Canonical:** `canonical_host` from the RPC (the active custom domain, or null) gives `https://<canonical_host>/` for `<link rel="canonical">` and `og:url`, also when the visitor is on the free link; on a custom host it is that host; otherwise the page's own URL.

## Local development
- `WEB_DEV=1 sh scripts/build-web.sh && python3 -m http.server -d web/dist 8080` then open `http://localhost:8080/?mock=1&s=demo`.
- Mock mode needs all three of: host `localhost`/`127.0.0.1`, `?mock=1`, and `dev-mock.js` present. `dev-mock.js` and `dev-fixtures/` are copied only when `WEB_DEV=1`, so a normal (production) build cannot activate it.
- Slugs: `demo` (EN), `demo-hi` (HI), `xss` (escaping test), `minimal-empty` (hidden/ordering), `unpublished` (unavailable), `error` (RPC failure). `&t=classic|modern|minimal` overrides the template. A phone ending `0000` simulates rate limiting, `1111` a transport error, `2222` `unavailable`, `3333` `invalid` (email). Fixture `demo-hi` has a `canonical_host`.
- `python3 -m http.server` does not rewrite `/a/{slug}`; use `?s=`. Test `/a/{slug}` on the `*.workers.dev` preview.
- `node scripts/test-web.mjs` (after a build, for sizes) and `node scripts/lint-web-css.mjs`.

## Measured size (JS + CSS actually loaded for one template, incl. tokens.css and base.css)
| template | raw | gzip |
|---|---|---|
| classic | 32.1 KB | 14.2 KB |
| modern | 32.6 KB | 14.4 KB |
| minimal | 32.2 KB | 14.2 KB |
Budget is 50 KB. Font files (woff2, self-hosted, loaded on demand) and `fonts.css` (2 KB) are extra; the budget covers JS and CSS only.

## Preview images and Lighthouse
- `web/templates/{classic,modern,minimal}/preview.webp`: 390x844 phone screenshots (25 KB, 25 KB, 28 KB) of fixture `demo` (EN), taken with headless Chrome (`--window-size=390,844 --virtual-time-budget=5000`) against a `WEB_DEV=1` build served on port 8080 (it must be 8080 so the host matches `SITE_BASE_URL`), then PNG -> WebP with Pillow. Regenerate them whenever a template's look changes.
- Lighthouse 13.5 mobile, classic template, EN, local server, mock data: Performance 90, Accessibility 100, Best practices 96, SEO 100. FCP 2.8 s and LCP 3.0 s under simulated slow 4G; TBT 0, CLS 0. Open items: Google Fonts CSS was render-blocking (est. 1.9 s saving in simulation; fixed by self-hosting, re-measure); the local python server sends no cache headers (Cloudflare applies `_headers`, so ignore). The only console error was a `favicon.ico` 404, fixed with an empty `data:` icon link. Re-run on the real Cloudflare preview before launch.

> **Deploy order (2026-10-17):** `scripts/test-web.mjs` rebuilds `web/dist` with dummy config (`SUPABASE_URL: "x"`). Always run tests first, **then** the real build with `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SITE_BASE_URL` set, check `web/dist/config.js`, and deploy straight away. A planned fix is for the test script to build into a temp directory.
