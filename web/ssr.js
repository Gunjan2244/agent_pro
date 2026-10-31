// Server-side rendering (DECISIONS #75). Pure: no fetch, no DOM. worker.js loads the data and calls this.
// The page is the same pure template render() the browser uses, inside <main id="root" data-ssr>; hydrate.js
// then only wires behaviour (form, install, view count). The shell mirrors web/index.html (fonts, tokens, base.css).
import { TEMPLATES, familyOf } from "./templates/index.js";
import { headData } from "./head.js";
import { makeT, localeKeys, pickLocale } from "./i18n.js";
import { siteLinks, matchPage } from "./route.js";
import { pageMeta } from "./sections/index.js";
import { manifestHref, appBase, appNames, swatchFor, iconPath } from "./manifest.js";
import { privacyHtml } from "./privacy.js";
import { esc } from "./sections/util.js";
import { docUrl } from "./sections/plan-data.js";

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

// The render tokens of a site page (the same as client.js show() builds).
function siteTokens(site, target, shared, notices) {
  const { schema, catalog, templates } = shared;
  const key = pickTemplate(templates, site.template_key);
  const links = siteLinks(target, { pathname: target.slug ? "/a/" + encodeURIComponent(target.slug) : "/", search: "" });
  const swatch = swatchFor(catalog, site.accent);
  const family = familyOf(templates, key), images = shared.images || {};
  const tokens = { accent: swatch ? swatch.hex : "var(--c-primary)", schema, catalog, notice: asNotices(notices).enquiry_form, privacyHref: links.privacy, family, images, seed: target.slug || target.host || "", sitePages: shared.pages };
  return { key, swatch, family, images, links, tokens };
}

// The pages a published site has (DECISIONS #122), for the sitemap: [{key, path, plan?}] ("" = home).
export function sitePageList({ site, target, dict, shared }) {
  const { key, tokens } = siteTokens(site, target, shared, null);
  if (!key || !shared.pages) return [{ key: "home", path: "" }];
  return TEMPLATES[key](site, makeT(dict), { ...tokens, pagesOnly: true });
}

// -> full HTML string for a published site (a site page or the privacy page), or null when the site has no such page
// (target.path names no page, or a page this site doesn't have: the Worker answers 404).
// shared = { schema, catalog, templates, images?, pages? (shared/site-pages.json) }.
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
  // the site page (DECISIONS #122): route.js matchPage over shared/site-pages.json; without it, the one page
  const sitePage = target.page === "privacy" ? null : shared.pages ? matchPage(shared.pages, target.path || "") : null;
  if (shared.pages && target.page !== "privacy" && !sitePage) return null;
  const bootPage = target.page === "privacy" ? "privacy" : sitePage && sitePage.key !== "home" ? sitePage.key : null;
  const boot = { slug: target.slug || null, host: target.host || null, locale, template: key, page: bootPage, name, phone: contactPhone };

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

  const { swatch, family, images, tokens } = siteTokens(site, target, shared, notices);
  if (sitePage) tokens.page = sitePage;
  const inner = TEMPLATES[key](site, t, tokens);
  if (!inner) return null;
  // "We're taking you to <insurer>'s website" (DECISIONS #130): never indexed; opens the insurer's page after 5 s
  // with a meta refresh (no script), to the plan's own address from the payload (never one from the URL).
  const go = sitePage && sitePage.key === "go" ? goUrl(site, sitePage) : "";
  const d = headData(site, { origin, path: base, canonicalHost, noindex: noindex || Boolean(go), t, catalog, schema, images, family, page: pageMeta(site, t, tokens, sitePage) });
  // What installable() adds client-side in the fallback render: manifest, icons, apple title (DECISIONS #65).
  const extra = [`<link rel="manifest" href="${attr(manifestHref(target))}">`];
  if (go) extra.push(`<meta http-equiv="refresh" content="5;url=${attr(go)}">`, '<meta name="referrer" content="no-referrer">');
  if (swatch) extra.push(`<link rel="icon" href="${attr(iconPath(swatch.key, 192))}">`, `<link rel="apple-touch-icon" href="${attr(iconPath(swatch.key, 192))}">`);
  extra.push(`<meta name="apple-mobile-web-app-title" content="${attr(appNames(site).short)}">`);
  return page({
    locale,
    // the inner pages' shared layouts (DECISIONS #122) only where they are used; Home's first paint stays as it was
    headHtml: head({ locale, ...d, extra, css: [...(sitePage && sitePage.key !== "home" ? ["/styles/pages.css"] : []), `/templates/${key}/style.css`] }),
    body: `<main id="root" data-ssr>${inner}</main>\n${bootScript}`,
  });
}

// The insurer address a /go/<slug>/<doc> page opens: the payload plan's own link for that document ("" if none).
function goUrl(site, page) {
  const p = ((site && site.plans) || []).find((x) => x && x.slug === page.slug);
  return p ? docUrl(p, page.doc).replace(/&amp;/g, "&") : "";
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
