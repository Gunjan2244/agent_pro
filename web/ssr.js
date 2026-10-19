// Server-side rendering (DECISIONS #75). Pure: no fetch, no DOM. worker.js loads the data and calls this.
// The page is the same pure template render() the browser uses, inside <main id="root" data-ssr>; hydrate.js
// then only wires behaviour (form, install, view count). The shell mirrors web/index.html (fonts, tokens, base.css).
import { TEMPLATES, familyOf } from "./templates/index.js";
import { headData } from "./head.js";
import { makeT, localeKeys, pickLocale } from "./i18n.js";
import { siteLinks } from "./route.js";
import { manifestHref, appBase, appNames, swatchFor, iconPath } from "./manifest.js";
import { privacyHtml } from "./privacy.js";
import { esc } from "./sections/util.js";

// Template keys the registry lists and this bundle can render, in registry order.
export function templateKeys(registry) {
  return ((registry && registry.templates) || []).slice().sort((a, b) => a.order - b.order).map((x) => x.key).filter((k) => Object.hasOwn(TEMPLATES, k));
}
export function pickTemplate(registry, wanted) {
  const keys = templateKeys(registry);
  return keys.includes(wanted) ? wanted : keys[0];
}

// JSON that is safe inside <script>: "<" and the line separators are escaped.
export const safeJson = (v) => JSON.stringify(v).replace(/</g, "\\u003c").replace(/\u2028/g, "\\u2028").replace(/\u2029/g, "\\u2029");

const attr = esc;
const FONT_PRELOAD = '<link rel="preload" href="/fonts/inter-latin-400-normal.woff2" as="font" type="font/woff2" crossorigin>';
const BASE_CSS = ["/styles/fonts.css", "/tokens.css", "/styles/base.css"];

function head({ locale, title, description, robots, canonical, og = {}, twitter = {}, jsonld, extra = [], css }) {
  const meta = (obj, key) => Object.entries(obj).map(([k, v]) => `<meta ${key}="${attr(k)}" content="${attr(v)}">`);
  return [
    '<meta charset="utf-8">',
    '<meta name="viewport" content="width=device-width, initial-scale=1">',
    `<title>${esc(title)}</title>`,
    description ? `<meta name="description" content="${attr(description)}">` : "",
    `<meta name="robots" content="${attr(robots)}">`,
    canonical ? `<link rel="canonical" href="${attr(canonical)}">` : "",
    ...meta(og, "property"), ...meta(twitter, "name"),
    ...extra,
    FONT_PRELOAD,
    ...[...BASE_CSS, ...css].map((h) => `<link rel="stylesheet" href="${attr(h)}">`),
    jsonld ? `<script type="application/ld+json">${jsonld}</script>` : "",
  ].filter(Boolean).join("\n");
}

const page = ({ locale, headHtml, body }) => `<!doctype html>
<html lang="${attr(locale)}">
<head>
${headHtml}
</head>
<body>
${body}
</body>
</html>
`;

const asNotices = (n) => (n && n.notices) || n || {};

// -> full HTML string for a published site (home or privacy page). shared = { schema, catalog, templates, images? }.
export function renderDocument({ site, target, url, dict, shared, notices, config = {} }) {
  const { schema, catalog, templates } = shared;
  const keys = localeKeys(catalog), locale = pickLocale(keys, site.locale);
  const t = makeT(dict);
  const key = pickTemplate(templates, site.template_key);
  if (!key) throw new Error("no renderable template");
  const q = url.searchParams;
  const noindex = q.get("install") === "1" || q.get("preview") === "1" || q.has("mock");
  const origin = url.origin;
  const canonicalHost = target.host || site.canonical_host || null;
  const base = appBase(target); // /a/<slug>/ or /
  // The links must be the clean site URLs even when the page was reached through ?s=<slug>.
  const links = siteLinks(target, { pathname: target.slug ? "/a/" + encodeURIComponent(target.slug) : "/", search: "" });
  const nf = asNotices(notices);
  const name = (site.profile || {}).full_name || "";
  const contactPhone = (site.profile || {}).phone || ((site.content || {}).contact || {}).phone || "";
  // name and phone are what wireForm's thank-you message needs; they are already visible in the page.
  const boot = { slug: target.slug || null, host: target.host || null, locale, template: key, page: target.page || null, name, phone: contactPhone };

  const bootScript = `<script type="application/json" id="boot">${safeJson(boot)}</script>
<script src="/config.js"></script>
<script type="module" src="/app.js"></script>`;

  if (target.page === "privacy") {
    const title = t("site.privacy_page_title", { name });
    const canonical = canonicalHost ? `https://${canonicalHost}/privacy` : origin + base + "privacy";
    const inner = privacyHtml(site, t, { notice: nf.enquiry_form, catalog, backHref: links.home });
    return page({
      locale,
      headHtml: head({ locale, title, description: title, robots: noindex ? "noindex" : "index,follow", canonical, og: { "og:type": "website", "og:title": title, "og:url": canonical }, twitter: { "twitter:card": "summary" }, css: [] }),
      body: `<main id="root" data-ssr>${inner}</main>\n${bootScript}`,
    });
  }

  const swatch = swatchFor(catalog, site.accent);
  const family = familyOf(templates, key), images = shared.images || {};
  const tokens = { accent: swatch ? swatch.hex : "var(--c-primary)", schema, catalog, notice: nf.enquiry_form, privacyHref: links.privacy, family, images, seed: target.slug || target.host || "" };
  const inner = TEMPLATES[key](site, t, tokens);
  const d = headData(site, { origin, path: base, canonicalHost, noindex, t, catalog, schema, images, family });
  // What installable() adds client-side in the fallback render: manifest, icons, apple title (DECISIONS #65).
  const extra = [`<link rel="manifest" href="${attr(manifestHref(target))}">`];
  if (swatch) extra.push(`<link rel="icon" href="${attr(iconPath(swatch.key, 192))}">`, `<link rel="apple-touch-icon" href="${attr(iconPath(swatch.key, 192))}">`);
  extra.push(`<meta name="apple-mobile-web-app-title" content="${attr(appNames(site).short)}">`);
  return page({
    locale,
    headHtml: head({ locale, ...d, extra, css: [`/templates/${key}/style.css`] }),
    body: `<main id="root" data-ssr>${inner}</main>\n${bootScript}`,
  });
}

// 404 body for an unknown, unpublished or lapsed site: no agent data, noindex, no scripts.
export function unavailableDocument({ dict, locale = "en", generic = false }) {
  const t = makeT(dict || {}, {
    "site.not_available_title": "This page isn't available", "site.not_available_text": "The link may be wrong, or the page is not published.", "site.form_error_generic": "Something went wrong. Please try again.",
  });
  const title = t("site.not_available_title");
  return page({
    locale,
    headHtml: head({ locale, title, description: "", robots: "noindex", css: [] }),
    body: `<main id="root"><div class="state"><h1>${esc(title)}</h1><p>${esc(generic ? t("site.form_error_generic") : t("site.not_available_text"))}</p></div></main>`,
  });
}
