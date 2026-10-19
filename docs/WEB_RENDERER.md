# Web renderer — public agent sites

There's one static site for **every** agent. Content is fetched at runtime, so agents never need a redeploy when they edit or publish.

## Hosting
- **Current deployment (2026-10-17):** a preview at `https://agentpro-sites.sledjesoftwares.workers.dev` (demo data only), built with `SITE_BASE_URL` set to that URL. `_headers` uses `! Cache-Control` so `/config.js` (`no-store`) and `/a/*` (`no-cache`) get exactly one value.
- **Cloudflare Workers static assets** (DECISIONS #59): a Worker named `agentpro-sites`, config in `web/wrangler.jsonc`, assets served from `web/dist`. The Worker script (`web/worker.js`, with `ssr.js`, `headers.js`, `templates/index.js` and the pure code they import; bundled by wrangler, not copied into `dist`) runs **only** for `assets.run_worker_first = ["/", "/a/*", "/privacy", "/manifest.webmanifest", "/robots.txt", "/sitemap.xml"]` (DECISIONS #65, #75). Every other request (fonts, CSS, JS, JSON, `/terms`) is served straight from the assets without invoking it. Agents' own domains attach as Cloudflare for SaaS custom hostnames (DOMAINS.md) and use the same paths. The platform domain is not bought yet, so there are no routes or custom domains in the config; the preview lives at `agentpro-sites.<account-subdomain>.workers.dev` (`workers_dev = true`).
- **Routes (Worker):**
  | request | response |
  |---|---|
  | `GET\|HEAD /a/<slug>` | **301** to `/a/<slug>/`, query kept, no backend call |
  | `GET\|HEAD /a/<slug>/`, `/a/<slug>/privacy`; custom host `/`, `/privacy` | **200** server-rendered HTML (`Cache-Control: no-cache`, `Last-Modified`, or **304** on `If-Modified-Since`) |
  | same, but unknown, unpublished or lapsed site, or a malformed slug | **404** SSR "This page isn't available", `noindex`, no agent data, no scripts |
  | same, but the backend (or `config.js`, or rendering) fails | the static shell `index.html` (200), so the browser renders and retries |
  | `?preview=1` or any `?mock` | the shell, never SSR'd, no backend call |
  | `?install=1` | SSR'd, with `<meta name="robots" content="noindex">` (canonical stays the clean URL) |
  | `/` or `/privacy` or `/terms` on the primary host with no slug (platform pages); a custom host's other paths (`/a/*`) | the assets (shell) as before |
  | `GET /manifest.webmanifest` | the agent's manifest (below) |
  | `GET /robots.txt` | allow `/`; disallow `/*?preview`, `/*?install`, `/*?mock` (and the `&` forms); `Sitemap: <request origin>/sitemap.xml` |
  | `GET /sitemap.xml` | see "Crawling" |
  | other methods | 405 on manifest/robots/sitemap; page routes pass to the assets |
- **Static data (per isolate):** the Worker reads `config.js`, `site-schema.json`, `catalog.json`, the templates registry, `design-tokens.json`, the privacy notices and each locale's dictionary through `env.ASSETS`, and caches them per isolate (keyed by the binding; a failed load is not cached). Changes to those files only arrive with a deploy, which restarts isolates.
- **Build:**
  - `sh scripts/build-web.sh` copies `web/` and `shared/` into `web/dist/`.
  - It writes `web/dist/config.js` from the `SUPABASE_URL` and `SUPABASE_ANON_KEY` environment variables, plus `BUILD_TIME` (the build's UTC time, ISO), which the Worker uses for `Last-Modified`.
  - It copies `web/images/` (the bundled illustrations and their `manifest.json`, docs/IMAGES.md).
  - It generates `tokens.css` from `design-tokens.json`.
  - It generates `icons/<swatch>-192.png` and `-512.png` for every `catalog.accentSwatches` entry (`scripts/gen-icons.mjs`, the web app manifest icons).
- **SPA routing:** `assets.not_found_handling = "single-page-application"` serves `/index.html` for any path that is not a real file. The Worker relies on it for the shell (`/a/<slug>/`, `/terms`). Because `run_worker_first` is an array, the routing is explicit: only the paths above reach the Worker.
- **Headers:** `web/_headers` (copied into `dist/`, not served as an asset) sets the same security headers for static assets; **the Worker's HTML responses carry them from `web/headers.js`** (`_headers` does not apply to Worker responses; `test-web.mjs` asserts the two are equal). It sets `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, a minimal `Permissions-Policy` and a CSP (`self` + `*.supabase.co` for `connect-src`/`img-src` ; `style-src 'self' 'unsafe-inline'`, `font-src 'self'`: no third-party font origins, the fonts are self-hosted). Filenames are not hashed, so assets get `max-age=300, stale-while-revalidate=86400`; `/index.html` is `no-cache` and `/config.js` is `no-store`. Worker HTML is `no-cache` (no edge cache in v1, DECISIONS #75). `_headers` allows 100 rules and applies only to static-asset responses.
- **Preview deploy (user approval each time; the user logs in once):** `sh scripts/build-web.sh` (real env, never `WEB_DEV=1`), then `npx wrangler@4.151.1 login` once, then `npx wrangler@4.151.1 deploy --config web/wrangler.jsonc`. Check config without deploying: `npx wrangler@4.151.1 deploy --dry-run --config web/wrangler.jsonc`. Public launch waits for the P1 privacy items; until then the preview shows demo data only.
- Build env: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SITE_BASE_URL` (the primary host, default `http://localhost:8080`). `web/dist/` and `web/config.js` are git-ignored.
- **Deploying needs the user's approval each time.** Record each deploy in STATUS.md (Environment) and in CHANGELOG.md.

## Structure
```
web/
  index.html          static shell (preview, ?mock, backend-error fallback): loading state, <main id="root">, noscript; links tokens.css + styles/base.css
  app.js              entry (~1 KB): defines window.renderSite; if #root[data-ssr] imports hydrate.js, else (not ?preview=1) client.js boot()
  hydrate.js          SSR page: reads #boot, loads dict/catalog/notices, wireForm (or wirePrivacy), interest chips, install wiring, one view count. Never renders
  images/             bundled illustrations (WebP 400/800, og-* 1200x630) + manifest.json, generated by scripts/gen-site-images.py; the renderer draws only names listed in the manifest
  client.js           client render: resolve site -> fetch -> pick template -> render -> wire form -> record view; renderSite(json) for the preview
  api.js              plain fetch() to {SUPABASE_URL}/rest/v1/rpc/* with the anon key (no SDK); normalizeSite()
  manifest.js         pure manifestFor(site, target, {catalog, tokens}), manifestHref, appNames, swatchFor, iconPath, parseConfig (Add to home screen)
  install.js          wireInstall(): the contact-section button, the ?install=1 sheet, beforeinstallprompt (lazy-imported after render)
  worker.js           Cloudflare Worker: pages (SSR), manifest, robots.txt, sitemap.xml; not copied into dist (wrangler bundles it)
  ssr.js              pure renderDocument({site, target, url, dict, shared, notices, config}) and unavailableDocument(); Worker only
  headers.js          SECURITY_HEADERS (CSP etc.), identical to the /* block of _headers; Worker only
  i18n.js             loadDict (browser) / loadDictWith(fetcher, locale) (Worker, through env.ASSETS); t(key, params); localeKeys(catalog)/pickLocale (supported locales from catalog.locales, first = default)
  head.js             pure headData() (title, description, canonical, OG, twitter, robots, JSON-LD @graph) + applyHead()
  form.js             enquiry validation (same rules as submit_enquiry) + wireForm() (the thank-you is an agent bubble)
  styles/base.css     shared component styles (tokens only)
  sections/           util.js (escaping, URL/phone helpers, schema lookups; loaded by hydrate.js, so kept small), media.js (render only:
                      pic()/interestPic() bundled images, initials(), fnv1a32()), icons.js, index.js (order/visibility, makeCtx, compose,
                      the sticky call bar), say.js (agent bubbles), offer.js (one offer card, plan card, CTA, expiry, dates), offers.js
                      (the section, plan list, guide groups), hero.js about.js services.js highlights.js achievements.js testimonials.js
                      faq.js contact.js footer.js
  templates/
    index.js  static map {key: render} for the Worker (no dynamic import()); keys must equal the registry's templates; familyOf()
    classic/    render.js style.css preview.webp   "Meet me" · "Trust": centred portrait and greeting, stacked sections
    modern/     render.js style.css preview.webp   "Meet me" · "Fresh": split hero band (text left, portrait + greeting right), 2-column cards
    minimal/    render.js style.css preview.webp   "Meet me" · "Simple": type-led single column, small square portrait, text-link chips
    spotlight/  render.js style.css preview.webp   "Best offers": featured offer on the tint above the fold, offer grid, trust strip
    guide/      render.js style.css preview.webp   "Best offers": "What would you like to protect?" chips -> anchored groups per interest
  config.example.js   template for config.js (the real one is generated into web/dist; git-ignored)
  wrangler.jsonc      Cloudflare Worker config (static assets, SPA fallback)
  _headers            security + cache headers (copied into dist/)
  dev-mock.js, dev-fixtures/   LOCAL DEV ONLY (see "Local development"): sample-en/-hi (Meet me + demo offers), sample-offers (spotlight),
                      sample-seeded (a brand-new agent, built by scripts/site-seed.mjs), sample-xss, sample-empty
  package.json        {"type":"module"} only, so Node can import the pure render code in tests; no dependencies
scripts/
  build-web.sh        copies web/ + shared/ into web/dist, writes config.js, generates tokens.css (gen-tokens.mjs)
  gen-icons.mjs       catalog.accentSwatches -> dist/icons/<swatch>-{192,512}.png (dependency-free PNG encoder)
  gen-tokens.mjs      shared/design-tokens.json -> tokens.css (--c-*, --accent, --text-s|m|l|xl, --line-*, --space-s|m|l, --shape-*, --motion-*)
  lint-web-css.mjs    fails on raw px/rem/em in margin/padding/gap/font-size
  gen-site-images.py  design/site-images/source/<slot>.png -> web/images/*.webp + manifest.json (python3 -I, Pillow; size budgets)
  site-seed.mjs       Node mirror of SiteSeed/StarterContent: writes dev-fixtures/sample-seeded.json (--check: fails when stale)
  test-web.mjs        Node checks of the pure renderer, validation, head data and per-template size budget
  test-web-install.mjs  Add to home screen checks (manifest, Worker with a fake ASSETS binding, icons, button, install.js), run by test-web.mjs
  test-web-ssr.mjs    SSR, SEO, robots/sitemap, headers, SSR/client parity, semantic HTML, Last-Modified, hydrate.js, run by test-web.mjs
  test-web-offers.mjs offers + disguise, CTA kinds, plan fallback, bubbles, call bar, images, hero scene, seed helper, run by test-web.mjs
```
The sections are rendered from the data in `site-schema.json` (default CTA via `defaultKey`, option labels for languages and highlight icons, the pinned sections). Interests come from `catalog.json`; the accent hex comes from `catalog.accentSwatches`; supported locales from `catalog.locales`; valid template keys from `shared/templates/index.json`. `app.js` loads site-schema, catalog and the templates registry at runtime; nothing in `web/` hard-codes them (the accent fallback is `var(--c-primary)`). `tokens` passed to `render` is `{ accent, schema, catalog, notice, privacyHref, family, images, seed, today? }`: `family` is the template's registry family (`personal` | `offers`, `familyOf`), `images` the parsed `web/images/manifest.json` (`{}` when absent), `seed` the slug or custom host (it picks the hero scene), `today` an optional `YYYY-MM-DD` for tests (default: today in Asia/Kolkata). `ssr.js` and `client.js` fill them the same way, so the SSR body still equals `render()`.

## Resolution
0. A `?preview=1` page does no resolution at all, on the server or the client; it waits for `window.renderSite(json)` (the in-app preview). The preview never submits enquiries or records views.
1. If the host is the primary host (`SITE_BASE_URL`), read the slug from `/a/{slug}`, falling back to `?s={slug}`. Then call `get_public_site`.
2. Any other host is a custom domain: call `get_public_site_by_host(location.hostname)` and render at `/`. Only `active` and `renew_soon` domains resolve; an expired domain shows "This page isn't available", while the free link keeps working.
3. If the site isn't found, isn't published, or **its owner's plan has lapsed** (BILLING.md), show a friendly "This page isn't available" message with no agent data. The Worker does steps 1 to 3 on the server (same `resolveTarget`, same `get_public_site*` RPCs through its own `rpc()`); the client repeats them only in the fallback render.

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
  3. the Cloudflare Worker, which server-renders every public page (next section).
- **Colours** always come from `tokens.css` plus `--accent`, set from the agent's swatch.
- **Look:** clean and professional, matching the app (DESIGN_SYSTEM.md "Website theme").
  - White background, the Inter font and generous whitespace.
  - One call to action per section.
  - The accent is only for buttons, links and small highlights, never for large colour blocks.
  - **Two families (DECISIONS #76).** The wrapper is `<div class="tpl tpl-<key> fam-<family> [has-bar]">`.
    - **"Meet me"** (`classic`, `modern`, `minimal`): about the agent. Section order is the content's `sectionOrder` (the seed puts About second). They differ in layout only: classic centres a 160 px round portrait with the greeting under it; modern has the grey hero band with the text left and the portrait and greeting right (stacked on phones as a photo + bubble row); minimal is one 640 px type-led column with an 80 px square portrait, text-link chips, a lighter `<h1>` and hairline lists. Offer cards are white hairline cards without images.
    - **"Best offers"** (`spotlight`, `guide`): to win clients. Offer cards may carry a 16:9 image, and only the hero's featured card uses the pale `--tint`; no saturated blocks and no image in the hero banner. The about stats move into a **trust strip** (the highlights section: years and clients only when set, plus the highlight titles with their icons). A **hero scene** (`hero-scene-1..3`, from `fnv1a32(seed) % 3`), lazy and `fetchpriority="low"`, shares a `.hero-side` column with the featured card: on phones `.hero-side` is `display: contents` and the scene comes last in the hero (the phone LCP element is the `<h1>`); from 1024 px the hero is two columns, text on the left and the featured card with the scene under it (same 400 px width) on the right. On desktop the scene is then the largest element in view and Lighthouse reports it as the LCP element (desktop LCP 0.5–0.7 s, Performance 100).
      - **spotlight** (`layout: "grid"`): photo + greeting row, `<h1>`, lead, then the featured offer card on the tint with its badge, title, summary, "Ends {date}" and **the hero's one button** (the generic hero CTA is dropped). With no featured offer it leads with the first offer of the section (auto offers, DECISIONS #79). Then quick replies, the scene, and the offer grid (1, 2, 3 columns), trust strip, services, FAQ, contact. Phones order the hero with CSS `order`: id, text, featured card, chips, scene. Desktop: the featured card and the scene in the 400 px right column.
      - **guide** (`layout: "needs"`): photo + greeting, `<h1>`, then `site.guide_question` with one big chip per interest that has an offer or a service (2 columns on phones, 4 from 720 px), each a plain `#need-<interest>` link (no JS; `.need:target` tints that group's bubble). The offers section is one group per interest (`<div class="need" id="need-…">`; an offer's interest is `offer.interest ?? offer.cta.interest`): an agent bubble (the agent's own service description, else `site.say_offers`/`say_services`), an `<h3>`, then that interest's offer cards or its plan card. Offers without an interest (e.g. a custom offer with none) go in "other", last. No generic hero button and no promoted offer; an explicit featured offer shows after the chips with a text link. Desktop: two columns like spotlight (text and 2-column chips left; the featured card, if any, and the scene right).

## Server-side rendering and hydration (DECISIONS #75)
- **Flow:** request → `worker.js` resolves the target → `get_public_site*` → `ssr.js renderDocument()` → HTML. Nothing in the first response needs JS.
- **The document** contains, in order: `<html lang>`; `<title>`, `description`, `robots`, `canonical`, `og:*`, `twitter:*` from `headData()`; the manifest, icon, apple-touch-icon and `apple-mobile-web-app-title` links (what the fallback `installable()` adds in the browser); the Inter latin-400 font preload; `fonts.css`, `tokens.css`, `base.css` and the template's `style.css`; the JSON-LD; then `<main id="root" data-ssr>` holding **exactly** the template's `render()` output (a test asserts this for every template and locale), `<script type="application/json" id="boot">` (slug, host, locale, template key, `page` = `privacy` or null, plus the agent's `name` and `phone`, which the thank-you message needs; `<` and the line separators are escaped so agent text cannot close the script), `config.js` and `app.js` (a deferred module).
- **Privacy page:** `privacyHtml()` is pure, so `/a/<slug>/privacy` and a custom host's `/privacy` are server-rendered too (indexable, canonical = itself, no template CSS). The request form is wired by `hydrate.js` (`wirePrivacy`).
- **Hydration (`hydrate.js`):** reads `#boot`; loads the dictionary, catalog and notices; `wireForm` (consent, interest, submit); a delegated click handler for `a[data-interest]` chips that preselects the form's interest when the option exists; `wireInstall` (the button and `?install=1` sheet; no head links, the HTML already has them); one `record_site_view` per session (not with `?install=1`). It never writes markup (a test checks the source and runs it against a fake DOM). A normal page therefore loads only `app.js` + `hydrate.js` + `api.js`, `i18n.js`, `form.js`, `sections/util.js` (+ `install.js`); `client.js`, the sections and the templates are never downloaded.
- **Client render (`client.js`)** is kept for the in-app preview (`renderSite`), `?mock=1`, and the fallback when the Worker could not render. `app.js` defines `window.renderSite` immediately and loads `client.js` on the first call, so the app can call it as soon as the page has loaded.
- **Conditional requests:** `Last-Modified` is **max(`site.published_at`, `BUILD_TIME`)** (`lastModified(site, cfg)` in `worker.js`; `BUILD_TIME` comes from `config.js` through `parseConfig`), so a deploy that changes a template or its CSS also invalidates browsers' copies, not only a re-publish. A matching `If-Modified-Since` gets a 304 before any rendering. Without `published_at` there is no `Last-Modified`.
- **One URL form:** `/a/<slug>/`. The Worker 301s the slashless form; the canonical URL, the manifest `scope`/`start_url`, the back link from the privacy page and the client fallback all use it. (There is no client `history.replaceState` any more.) `/a/<slug>/privacy` has no trailing slash.

## SEO
- **Crawlable HTML first:** the full content, head tags and JSON-LD are in the first response.
- **Semantic HTML (enforced by `test-web-ssr.mjs` for every template over the EN, HI, offers and seeded fixtures):** exactly one `<h1>` (the hero headline; an empty headline falls back to the agent's name); every other section is a `<section id="…">` with one `<h2>`; the hero is the `<header id="hero">` and holds no `<h2>`/`<h3>` (the featured offer's title is a `<p class="ttl">`, so heading levels never skip); one `<footer>`; the sticky bar is the only `<nav>`; the `.tpl` wrapper holds them all. The agent's bubbles are real text above each `<h2>`, so headings stay plain. FAQs are real `<details>`. A template that wraps sections differently must keep these rules.
- **Head (`headData`):** title "{Name} — {designation}, {city}" (`site.seo_title_fallback` "Insurance advisor in {city}" replaces a missing designation, or `site.seo_title_fallback_nocity` without a city); description = the hero subheadline, else the about bio, cut to 155 characters on a word boundary (ellipsis); canonical (with the trailing slash, or `https://<canonical_host>/`); `og:type|title|description|url|image` (the photo; with no photo, the family's bundled share image `https://<origin>/images/og-personal|og-offers-1200.webp` with `og:image:width/height`, when it is in the manifest); `twitter:card=summary_large_image` (+ title, description, image); `robots` = `index,follow`, or `noindex` for `?install=1`, `?preview=1` and `?mock`.
- **JSON-LD:** one `@graph`, `<` escaped:
  - `InsuranceAgency` (`@id` = canonical + `#agency`): name, url, description, image, telephone, address, `areaServed` (city), `openingHours` = `contact.officeHours` as plain text (only when non-empty and at most 100 characters; it is free text, so it is not guaranteed to be schema.org's `Mo-Fr 09:00-18:00` format), `knowsLanguage` (labels of `about.languagesSpoken`), `founder` → the Person;
  - `Person` (the agent; `worksFor` → the agency; jobTitle, image, telephone);
  - `FAQPage` from the faq items, and one `Service` per services item or offered interest (name = the catalog interest label), each only when its section is on the page;
  - never an insurer or company name, never `Offer`.

## Crawling
- **`robots.txt`:** see the route table. It is the same on every host and names the requested origin's sitemap.
- **`sitemap.xml`:**
  - *custom domain:* `https://<host>/` and `https://<host>/privacy`, `lastmod` = `published_at` when known (`get_public_site_by_host`; an unknown domain gets a 404).
  - *primary host:* the anon RPC `list_public_sites()` (returns `[{slug, published_at}]`, DECISIONS #79); one `<SITE_BASE_URL>/a/<slug>/` per valid slug, at most 50,000.
  - An RPC failure or a non-array result gives **503** `no-store`. Both are cached for 5 minutes.

## Page features
- **Head tags:** `<title>` ("{Name} — {designation}, {city}"), meta description, canonical, Open Graph and Twitter tags and the JSON-LD graph: see "SEO".
- **Enquiry form:** name, phone, email (optional), interest (from `catalog.interests`), message, and a hidden honeypot field.
  - Validation is inline, using the same rules as `submit_enquiry`.
  - The button shows a loading state.
  - On success: the agent's bubble "Thank you! I'll be in touch soon. — {firstName}" (`site.say_thanks`), with a Call button.
  - When rate-limited: "You've already sent a request. {name} will be in touch."
- **Contact:** tap-to-call, email and map links.
- **Branding footer:** "{Name} · Independent insurance advisor", plus "Made with Agent PRO". **No LIC logo or trademark artwork.**
- **Performance:**
  - No framework and no SDK; aim for under 50KB of JS and CSS per template.
  - Images are lazy-loaded with explicit dimensions.
  - Fonts are **self-hosted** (DECISIONS #54): `web/styles/fonts.css` declares `@font-face` rules (`font-display: swap`, fontsource `unicode-range`s) for files in `web/fonts/` (copied to `dist/fonts/` by `build-web.sh`). Source: npm `@fontsource/inter@5.3.0` (latin + latin-ext, 400/500/600/700; latin-ext carries the rupee sign U+20B9) and `@fontsource/noto-sans-devanagari@5.3.0` (devanagari, 400/500/600), woff2 only, with the OFL licence files. Only the Inter latin 400 file is preloaded; the browser fetches other files when the text needs them (latin 94.5 KB for all four weights, Devanagari 154 KB for Hindi pages). No request leaves the origin for fonts. To upgrade, re-pack the fontsource packages and replace the files and `fonts.css`.
  - The enquiry form's "Interested in" select starts on a blank placeholder (`site.form_interest_placeholder`, value `""`); left blank, no interest is sent (`null`), it is never defaulted to "other".
  - Target Lighthouse mobile scores of 90 or more for Performance, Accessibility and SEO.
- **Analytics:** one `record_site_view` call per page load, debounced per session with `sessionStorage`. A visit with `?install=1` (the agent coming from the app) isn't counted.
- **Add to home screen (DECISIONS #65):** every published site can be installed as an app.
  - **Manifest:** the server-rendered HTML contains `<link rel="manifest" href="/manifest.webmanifest?s=<slug>">` (just `/manifest.webmanifest` on a custom domain); the client fallback adds it after render. The Worker resolves the target with the same `resolveTarget` as the page, calls `get_public_site` / `get_public_site_by_host` with the anon key, and returns `manifestFor(...)`:
    - `id` = `start_url` = `scope` = `/a/<slug>/` (`/` on a custom domain), so each agent is a separate app on the shared origin;
    - `name` is the agent's name (≤ 45 characters), and `short_name` is that name or, when it's over 12 characters, the first name;
    - `description` is the same as the meta description, and `lang` is the site's locale;
    - `display: standalone`; `background_color` and `theme_color` are `design-tokens.json` light `background` and `surface`;
    - `icons`: `/icons/<accent>-192.png` and `-512.png`, each as `any` and `maskable`; `prefer_related_applications: false`.
    - **Responses:** `200 application/manifest+json`, `max-age=300`; **404** for an unknown, unpublished or lapsed site, or a malformed slug (so it can't be installed); **503** `no-store` if the backend fails; 405 for anything but GET/HEAD.
    - **Config:** the Worker reads `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SITE_BASE_URL` from the deployed `config.js` asset (`parseConfig`), so `wrangler.jsonc` holds no keys and there's no `--var` step.
  - **Scope:** pages live at `/a/<slug>/` (the Worker 301s the slashless URL), so they are inside the app's scope; the canonical URL has the slash too.
  - **Head:** `link rel="icon"` and `rel="apple-touch-icon"` point to the accent's 192 PNG; `meta apple-mobile-web-app-title` is the short name (in the SSR HTML; `client.js` adds them in the fallback).
  - **Button:** the contact section renders a hidden ghost button, "Add to home screen" (`site.install_button`), unless `contact.showInstallButton` is `false` (default on). `hydrate.js` wires `install.js`, which shows it on touch devices, and on desktops only once the browser offers a prompt. Tapping it calls the saved `beforeinstallprompt` event's `prompt()`. With no prompt (iOS, or Chrome before its engagement check of about 30 seconds and one tap), it shows the steps: `site.install_ios` (Share → Add to Home Screen) or `site.install_menu` (⋮ → Install app / Add to Home screen). It hides after `appinstalled`.
  - **Sheet:** `?install=1` (the app's Website tab) opens a bottom sheet inside `.tpl` (so it has the accent): "Add {name} to your home screen", the text, **Install** when a prompt is available (otherwise the steps), and **Close**. It opens whatever the toggle says.
  - **Always:** the page calls `preventDefault()` on `beforeinstallprompt`, so Chrome's own mini-infobar never shows; the agent's toggle therefore means no install promotion at all. Inside the installed app (`display-mode: standalone`) nothing is wired. Nothing is stored in the browser.
  - **No service worker:** Chrome no longer requires one to install, and caching could show a lapsed or changed site. Offline, the installed app shows the browser's own offline page.
  - **iOS:** Safari reads the manifest (16.4+) and the apple-touch-icon; there's no prompt API, so the button and sheet show the Share-sheet steps.

### Conversational layer (DECISIONS #76; `sections/say.js`)
- **Agent bubble:** `say(ctx, "site.say_<section>")` renders `<div class="say">` with the 32 px avatar (the agent's photo, `alt=""`, `loading="lazy"`, or a CSS initials monogram, never a stock face) and one `<p class="bubble">` line, `{firstName}` filled. Every section partial puts it **above** its normal `<h2>`. A missing key renders no bubble.
- **Hero:** the photo (160 px, eager, `fetchpriority="high"`; or a 160 px monogram) and a greeting bubble (`site.say_greeting`, `--text-m`) beside it; no greeting when the headline already contains the first name (the seeded "Hello, I'm Priya Nair"). The headline is the single `<h1>`. Under it the meta line ("{Name} · {designation} · {city}"), the CTA, and **quick replies**: `site.quick_reply_heading` and up to 4 of `services.offered` as `<a class="chip" href="#contact" data-interest="<key>">` (catalog label); without JS they jump to the form, `hydrate.js` also preselects the interest.
- **FAQ:** each item stays `<details>`: the `<summary>` is the visitor's bubble on the right (`.you`, tint, "+"/"−"), the answer the agent's bubble with the avatar.
- **Contact:** `site.say_contact` above the heading; after a successful enquiry the form becomes `<div class="enq done" role="status">` holding an agent bubble with `site.say_thanks` (`{firstName}`) and the page's avatar (copied from `#contact .say .av`), plus the Call button. Rate limiting keeps `site.form_rate_limited`.
- **Sticky Call / WhatsApp bar:** `<nav class="callbar" aria-label="{section.contact}">` after the footer, with `site.call` (tel:) and/or `site.whatsapp` (wa.me), only when those numbers exist; both families. It is `position: fixed` from the first paint and `.tpl.has-bar` reserves its height at the bottom (`calc(var(--space-l)*2 + var(--space-m))` + the safe area), so it never shifts the layout (CLS 0); hidden from 720 px.
- **Motion:** chip hover fades with `--motion-short`; an opened FAQ answer rises in with `--motion-medium`; smooth scrolling for the chip jumps. All of it only under `prefers-reduced-motion: no-preference`; `reduce` turns transitions and animations off.

### Offers (OFFERS.md "Web renderer", DECISIONS #77, #79)
- **Data:** only the payload's resolved `offers` (`{featured, items}`) and `company`. `normalizeSite` keeps `offers` (malformed → empty) and reduces `company` to `{disclaimer}`, so **a company name or logo is never rendered, even if a payload carried one.** `renderSite` (the preview) normalises them the same way.
- **Which offers (`pickOffers`):** an offer shows when it has a title and `ends_on` is not before today in Asia/Kolkata. The hero banner shows `featured`; on spotlight, with no featured offer, the first item (only while the offers section is visible). The section's grid is the items minus the one in the hero.
- **Card (`offerCard`):** neutral badge pill; the source line `site.offer_pick` ("My pick for you"), or `site.offer_agent_note` for `from_agent`; the public name as `<h3>` (a `<p class="ttl">` in the hero); summary; `details` behind `<details class="more">` labelled `offer_field.details` ("Details"); "Ends {date}" (`site.offer_ends`, `Intl` `en-IN`/`hi-IN`, e.g. "31 Mar 2027" / "31 मार्च 2027"); the CTA; the offer's disclaimer as fine print. The company disclaimer shows **once**, under the section (or under the hero banner when the section isn't on the page).
- **Interest:** `interestOf(offer)` = the payload's top-level `interest` (every resolved offer has it), else `cta.interest`. It drives the image fallback and the guide grouping.
- **CTA by kind:** `enquire` → `href="#contact"` with `data-interest` = `cta.interest ?? interest` (only a catalog key); `call` → `tel:` from the contact or profile phone (no CTA without one); `link` → the URL only if `https`, `target="_blank" rel="noopener nofollow"`. A text link (`.lnk`) everywhere except the offers family's hero banner, where it is the hero's one button.
- **Card image (offers family only, never the hero banner):** the admin-uploaded `image_url` if `https` (alt = the title, 800×450 box); else, for **demo fixtures only**, a bundled image named by the payload's optional `image` when it matches `^offer-[a-z0-9-]+$` and is in the manifest; else the plan-type image of the offer's interest (`catalog.interests[].image`); else none.
- **No offers:** offers family → plan-type cards (`planCard`: interest image, label, the agent's service description, an "Enquire now" link with `data-interest`) from `services.items` then `services.offered` (every catalog interest except "other" when there are no services), under `site.plans_heading`; the services section then lists its interests as compact chips (the cards were just shown). Personal family → the section renders nothing.
- **Layouts:** classic one column; modern and minimal two columns from 720 px (minimal as a hairline list); offers family 1/2/3 columns. No `Offer` JSON-LD.

### Bundled images (docs/IMAGES.md, DECISIONS #81, #82)
- `web/images/manifest.json` = `{ "<name>": { "w": 800, "h": 450, "sizes": [400, 800] } }` (`og-*`: `{ "w": 1200, "h": 630, "sizes": [1200] }`). `pic(ctx, name, opts)` draws `<img src="/images/<name>-<largest>.webp" srcset="…-400.webp 400w, …-800.webp 800w" sizes width height alt loading="lazy" decoding="async">` **only when the name is listed** (and well-formed); otherwise nothing. The manifest is loaded like the other static JSON: by the Worker through `env.ASSETS` (cached per isolate; missing → `{}`) and by `client.js` (missing → `{}`), and passed as `tokens.images`.
- **Slots as built:** interest images as a 128 px 16:9 thumbnail beside the text on Services cards (all templates; alt `site.image_alt_interest`); on offers-family offer and plan cards (16:9, full card width); `contact-talk` in the contact column (`alt=""`, up to 360 px); the hero scene (offers family only, see Templates); `og-personal`/`og-offers` as `og:image` when the agent has no photo. The agent's photo stays the only eager, high-priority image.
- 18 images ship (2026-10-18): 3 hero scenes, 7 interest images (no `interest-money-back` yet), 5 demo offer images (no `offer-simple-protection`: that offer shows `interest-term`), `contact-talk`, 2 OG images; 640 KB in all, each within budget (see IMAGES.md).

### Planned (designed, not built)
- **Offers slot mode (OFFERS.md, 8b-4):** a **slot mode** for the in-app preview only (`?preview=1` plus `renderSite(json, {slots: true})`) draws tap targets and posts messages through `preview-bridge.js`, which never loads on public pages. On Android the preview already loads from `https://appassets.androidplatform.net`, so the bridge uses `addWebMessageListener` restricted to that origin.
- **Privacy (PRIVACY.md, P1-3, built 2026-10-17; needs backend P1-2 deployed together):**
  - **Consent checkbox:** the enquiry form has an unticked, required checkbox. Its text is the `enquiry_form` notice's first two `textKeys` (consent line and link) rendered with `t(key, {name})`, and the link goes to the site's privacy page. Unticked: an inline error and no network call. Ticked: `submit_enquiry` also gets `consent = {notice_key: "enquiry_form", version, hash[siteLocale]}` from `shared/generated/privacy-notices.json` (`form.js consentFor`). A server `{error:'invalid', field:'consent'}` shows the same inline error. The message field has the hint `site.form_message_hint`.
  - **Privacy page:** `/a/<slug>/privacy` (local fallback `?s=<slug>&page=privacy`; `/privacy` on a custom domain), `web/privacy.js` (server-rendered by the Worker; lazy-imported only by the client fallback and `hydrate.js`). It shows the agent as data fiduciary, the notice parts (`textKeys[3..]`: what, why, who, how long, rights, with `{name}`, `{phone}`, `{email}` from the Contact section/profile), the agent's contact, and a request form (kind from `catalog.dataRequestKinds`, `withdraw_consent` first; name, mobile, message, honeypot; no consent box) calling `submit_data_request` with thank-you, rate-limited, invalid-field and generic-error states.
  - **Footer:** "Privacy" link on every site page. Routing is in `web/route.js` (`resolveTarget`, `siteLinks`).
  - **Platform pages:** `/privacy` and `/terms` on the primary host (`web/legal.js`, EN/HI, `?lang=hi|en`). They are shells: the policy reuses the `agent_signup` notice texts and the terms are headings with "being finalised" bodies (final drafts are P2-4). Grievance officer and email provider placeholders show as-is.
  - **Release gate:** `RELEASE=1 sh scripts/build-web.sh` exits 1 while `releaseBlockers` in `privacy-notices.json` or any `{UPPER_CASE}` token in `shared/i18n` remains (`scripts/release-check.mjs` lists them). Default builds only warn. Production deploys must use `RELEASE=1`.
  - **Self-hosted fonts** instead of Google Fonts (DECISIONS #54; done).

## Behaviour notes
- **Section order/visibility:** `content.sectionOrder` is followed (unknown keys dropped); `content.hidden` hides a section unless the schema marks it `pinned` (hero, contact), which is always shown (hero first, contact last if missing from the order). A section with no content renders nothing.
- **Escaping:** every agent string goes through `esc()`; links accept only `http(s)` (map, photo); phone/email are rebuilt from digits/validated before becoming `tel:`/`wa.me`/`mailto:`; JSON-LD escapes `<`. Multiline text uses `white-space: pre-line`, not `<br>`.
- **Images:** the hero photo is `loading` default (eager) with `fetchpriority=high`; it has explicit `width`/`height`. Every other image (bubble avatars, bundled illustrations, uploaded offer images) is lazy with explicit size (see "Bundled images"). On the SSR page the measured LCP element is the `<h1>`.
- **Form:** posts `name, phone, email, interest (default "other"), message, honeypot` to `submit_enquiry` with the exact argument names from BACKEND.md. The client rules match the RPC (name 1-80, Indian mobile, email <= 120, message <= 500, interest from the catalog). The response is read by its exact `error` value: `{ok:true}` shows the thank-you; `rate_limited` shows the rate-limited message; `invalid` + `field` shows the inline error on that field (`site.form_error_phone`/`_email`/`_required`); `unavailable` or anything unexpected shows the generic error. HTTP 429 is also treated as rate-limited.
- **Footer:** `site.footer_advisor` + `site.footer_made_with`. No logos, ever: no insurer logo or name anywhere on a site (DECISIONS #77).
- **Errors:** on the server a missing, unpublished or lapsed site is a 404 SSR unavailable page (`noindex`, no agent data, no scripts), and a backend error serves the static shell, whose `client.js` retries and shows the unavailable page (generic error text) if it fails again. Both set `noindex` and show no agent data.
- **Agency code:** shown when `about.showAgencyCode` is on and `profile.agency_code` is present (the RPC includes it only when that toggle is on).
- **Canonical:** `canonical_host` from the RPC (the active custom domain, or null) gives `https://<canonical_host>/` for `<link rel="canonical">` and `og:url`, also when the visitor is on the free link; on a custom host it is that host; otherwise the page's own URL (`/a/<slug>/`, with the slash).

## Local development
- `WEB_DEV=1 sh scripts/build-web.sh && python3 -m http.server -d web/dist 8080` then open `http://localhost:8080/?mock=1&s=demo`.
- Mock mode needs all three of: host `localhost`/`127.0.0.1`, `?mock=1`, and `dev-mock.js` present. `dev-mock.js` and `dev-fixtures/` are copied only when `WEB_DEV=1`, so a normal (production) build cannot activate it.
- Slugs: `demo` (EN, classic, demo offers incl. a featured one, a custom call offer and an ended one that must not show), `demo-hi` (HI), `offers` (spotlight with the 6 demo offers, one with a link CTA), `seeded` (exactly what a brand-new agent sees: `sample-seeded.json`, built by `node scripts/site-seed.mjs` from `shared/site-seed.json` for a fixed agent id; rerun it after changing the seed, a test fails when it is stale), `xss` (escaping test, hostile offers too), `minimal-empty` (hidden/ordering), `unpublished` (unavailable), `error` (RPC failure). `&t=classic|modern|minimal|spotlight|guide` overrides the template. A phone ending `0000` simulates rate limiting, `1111` a transport error, `2222` `unavailable`, `3333` `invalid` (email). Fixture `demo-hi` has a `canonical_host`.
- `python3 -m http.server` does not rewrite `/a/{slug}` and doesn't run the Worker, so there is no SSR, `/manifest.webmanifest` is a 404, and the client render is used (`?s=`, `?mock=1`). To run the Worker (SSR, robots, sitemap, manifest) locally, build with real env vars and run `npx wrangler@4.151.1 dev --config web/wrangler.jsonc` (it serves `web/dist` plus the Worker on port 8787; build with `SITE_BASE_URL=http://localhost:8787` so the host matches). `?mock=1` and `?preview=1` are never server-rendered, so the mock still works there. Checked 2026-10-18 against the live project's anon `get_public_site` (`ramesh-kumar-pune`): 301, 200 SSR with all headers, 404 for an unknown slug; `/sitemap.xml` gave 503 until `list_public_sites()` exists live. Test `/a/{slug}` and installing on the `*.workers.dev` preview.
- `node scripts/test-web.mjs` (after a build, for sizes) and `node scripts/lint-web-css.mjs`.

## Measured size
Measured 2026-10-18 after WP3b (5 templates, offers, conversational layer, images).

**Server-rendered page (the normal path):** first paint needs **CSS only**: `tokens.css` + `base.css` + `fonts.css` + the template's `style.css`:
| template | first-paint CSS raw | gzip | template style.css |
|---|---|---|---|
| classic | 15.8 KB | 4.0 KB | 0.7 KB |
| modern | 16.3 KB | 4.2 KB | 1.2 KB |
| minimal | 16.1 KB | 4.1 KB | 1.1 KB |
| spotlight | 16.1 KB | 4.1 KB | 1.1 KB |
| guide | 16.0 KB | 4.1 KB | 0.9 KB |

Deferred JS for hydration: `app.js` + `hydrate.js` + `api.js` + `i18n.js` + `form.js` + `sections/util.js` = 16.5 KB raw / 7.1 KB gzip (render-only helpers live in `sections/media.js` so `util.js` stays small); `install.js` adds 3.5 KB (20.0 KB), `privacy.js` instead on the privacy page (21.4 KB). The test caps each template's first-paint CSS at 20 KB and hydration JS (with install.js) at 30 KB. Images are extra and lazy (only those in view load; 400 px files 6–12 KB, 800 px 15–33 KB).

**Client render (preview, `?mock`, fallback):** JS + CSS actually loaded for one template, including `tokens.css` and `base.css`:
| template | raw | gzip |
|---|---|---|
| classic | 78.5 KB | 30.8 KB |
| modern | 79.2 KB | 31.1 KB |
| minimal | 78.9 KB | 30.9 KB |
| spotlight | 79.1 KB | 31.1 KB |
| guide | 78.9 KB | 31.0 KB |
The offers partials, the conversational layer and the image helpers added about 27 KB raw (unminified, commented source). This is the secondary path, so the test caps it at 85 KB (was 55 KB); with `manifest.js` and `install.js` (imported after render) at 92 KB (classic: 84.9 KB); the privacy and legal client pages at 85 KB (82.4 / 80.1 KB). Font files (woff2, self-hosted, loaded on demand) and `fonts.css` are extra; the budget covers JS and CSS only.

## Preview images and Lighthouse
- `web/templates/<key>/preview.webp` for all 5: 390×844 phone screenshots (classic 25 KB, modern 29 KB, minimal 31 KB, spotlight 32 KB, guide 32 KB), regenerated 2026-10-18. The Meet me templates show fixture `demo` (EN), spotlight and guide fixture `offers` (`/?mock=1&s=<slug>&t=<key>`). Taken with headless Chrome (`--headless=new --window-size=390,844 --run-all-compositor-stages-before-draw --virtual-time-budget=20000 --screenshot`). For full-page review shots, use a CDP harness that sets every `img.loading = "eager"` and waits for the images before capturing (headless Chrome otherwise leaves below-the-fold lazy images blank); that is a screenshot-only measure, the pages keep `loading="lazy"` against a `WEB_DEV=1` build served on port 8080 (it must be 8080 so the host matches `SITE_BASE_URL`), then PNG → WebP (quality 80) with Pillow. Regenerate them whenever a template's look changes.
- **Lighthouse 13.5 mobile (2026-10-18), local Python server:** mock (client render): classic 93–96 / 100 / 96 / 100 (Performance / Accessibility / Best practices / SEO), modern 95 / 100 / 96 / 100, minimal 93 / 100 / 96 / 100, spotlight 90 / 100 / 96 / 100, guide 96 / 100 / 96 / 100. The SSR document (`renderDocument` output served statically, hydration running): spotlight 99–100 / 100 / 96 / 100 (LCP 1.5–1.9 s, CLS 0.001, TBT 0, LCP element the `<h1>`), guide 95 / 100 / 96 / 100 (LCP 2.7 s, the `<h1>`), classic 96 / 100 / 96 / 100 (LCP 2.6 s). Desktop preset: spotlight and guide 100 / 100 / 96 / 100, LCP 0.5 / 0.7 s, CLS 0, LCP element the hero scene (see Templates). Best practices 96 is the console error for `/manifest.webmanifest`, which the Python server can't serve (the Worker does). The mock's CLS 0.059 is the client render replacing "Loading…", which the SSR path doesn't do. A first run found muted text on the tint at 4.05:1; the featured card now uses `onSurfaceVariant` there. Re-run on the Cloudflare preview before launch.

> **Deploy order (2026-10-17):** `scripts/test-web.mjs` rebuilds `web/dist` with dummy config (`SUPABASE_URL: "x"`). Always run tests first, **then** the real build with `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SITE_BASE_URL` set, check `web/dist/config.js`, and deploy straight away. A planned fix is for the test script to build into a temp directory.
