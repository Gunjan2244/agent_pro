# Web renderer — public agent sites

There's one static site for **every** agent. Content is fetched at runtime, so agents never need a redeploy when they edit or publish.

## Hosting
- **Vercel.** Its API can attach custom domains with automatic SSL, which DOMAINS.md depends on. It also offers domain registration, though full API coverage still has to be confirmed.
- **Build:**
  - `sh scripts/build-web.sh` copies `web/` and `shared/` into `web/dist/`.
  - It writes `web/dist/config.js` from the `SUPABASE_URL` and `SUPABASE_ANON_KEY` environment variables.
  - It generates `tokens.css` from `design-tokens.json`.
- `vercel.json` rewrites every path to `index.html`.
- **Deploying needs the user's approval each time.** Record each deploy in STATUS.md (Environment) and in CHANGELOG.md.

## Structure
```
web/
  index.html          shell: loading state, <main id="root">, error state
  app.js              resolve site → fetch → pick template → render → wire form → record view
  api.js              plain fetch() to {SUPABASE_URL}/rest/v1/rpc/* with the anon key (no SDK)
  i18n.js             loads shared/i18n/{locale}.json
  sections/           shared section partials: hero.js, about.js, services.js, highlights.js,
                      achievements.js, testimonials.js, faq.js, contact.js, footer.js
  templates/
    classic/  render.js style.css preview.webp     "Trust": centred portrait hero, stacked sections
    modern/   render.js style.css preview.webp     "Fresh": split hero, hairline cards
    minimal/  render.js style.css preview.webp     "Simple": type-led, single column
  config.example.js
```

## Resolution
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
  3. later, a Vercel Edge Function for server-side rendering, with no rewrite.
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
  - Fonts load with `display=swap`.
  - Target Lighthouse mobile scores of 90 or more for Performance, Accessibility and SEO.
- **Analytics:** one `record_site_view` call per page load, debounced per session with `sessionStorage`.
