// Client-side render: resolve site -> fetch -> pick template -> render -> wire form -> record view.
// Used by the in-app preview (renderSite), ?mock=1, and as the fallback when the Worker could not server-render
// (it serves the static shell on a backend error). Normal page loads are server-rendered and use hydrate.js.
// Loaded on demand by app.js, which defines window.renderSite immediately.
import * as api from "./api.js";
import { loadDict, makeT, localeKeys, pickLocale } from "./i18n.js";
import { headData, applyHead } from "./head.js";
import { wireForm, consentFor } from "./form.js";
import { recordViewOnce, normalizeOffers, normalizeCompanies } from "./api.js";
import { resolveTarget as resolve, siteLinks } from "./route.js";
import { makeCtx } from "./sections/index.js";
import { esc } from "./sections/util.js";

const cfg = window.AGENTPRO_CONFIG || {};
const root = document.getElementById("root");
const q = new URLSearchParams(location.search);
const json = async (p) => (await fetch(p)).json();

// Mock backend: localhost only AND ?mock=1 AND dev-mock.js shipped (dev builds only, see scripts/build-web.sh).
const isLocal = ["localhost", "127.0.0.1"].includes(location.hostname);
let backend = api;
if (isLocal && q.get("mock") === "1") backend = await import("./dev-mock.js").then((m) => m.mockApi(api, q)).catch(() => api);

function stylesheet(href) {
  return new Promise((res) => {
    const l = Object.assign(document.createElement("link"), { rel: "stylesheet", href });
    l.onload = l.onerror = res;
    setTimeout(res, 3000); // never block rendering on a slow stylesheet
    document.head.appendChild(l);
  });
}

async function unavailable(wanted, generic) {
  const catalog = await loadShared().then((x) => x[1]).catch(() => null);
  const keys = localeKeys(catalog), locale = pickLocale(keys, wanted);
  const t = makeT(await loadDict(locale, keys).catch(() => ({})), {
    "site.not_available_title": "This page isn't available", "site.not_available_text": "The link may be wrong, or the page is not published.", "site.form_error_generic": "Something went wrong. Please try again.",
  });
  document.documentElement.lang = locale;
  document.title = t("site.not_available_title");
  const robots = document.head.querySelector('meta[name="robots"]'); if (robots) robots.content = "noindex";
  root.innerHTML = `<div class="state"><h1>${esc(t("site.not_available_title"))}</h1><p>${esc(generic ? t("site.form_error_generic") : t("site.not_available_text"))}</p></div>`;
  root.removeAttribute("aria-busy");
}

let notices;
const loadNotices = () => (notices ||= json("/shared/generated/privacy-notices.json").catch(() => ({ notices: {} })));
let shared;
const loadShared = () => (shared ||= Promise.all([json("/shared/site-schema.json"), json("/shared/catalog.json"), json("/shared/templates/index.json")]));
// web/images/manifest.json: optional; the renderer draws only the illustrations listed there.
let images;
const loadImages = () => (images ||= json("/images/manifest.json").then((m) => (m && typeof m === "object" && !Array.isArray(m) ? m : {})).catch(() => ({})));
const familyOf = (templates, key) => ((templates.templates || []).find((x) => x.key === key) || {}).family || "personal";

export async function show(site, target = {}) {
  const [schema, catalog, templates] = await loadShared();
  const tkeys = templates.templates.slice().sort((a, b) => a.order - b.order).map((x) => x.key);
  const key = tkeys.includes(site.template_key) ? site.template_key : tkeys[0];
  const swatch = (catalog.accentSwatches || []).find((s) => s.key === site.accent) || (catalog.accentSwatches || [])[0];
  const keys = localeKeys(catalog), locale = pickLocale(keys, site.locale);
  const [dict, mod] = await Promise.all([loadDict(locale, keys), import(`./templates/${key}/render.js`), stylesheet(`/templates/${key}/style.css`)]);
  const t = makeT(dict);
  const [nf, imgs] = await Promise.all([loadNotices(), loadImages()]);
  const notice = (nf.notices || {}).enquiry_form;
  const links = target.slug || target.host ? siteLinks(target) : { privacy: "#" };
  const family = familyOf(templates, key);
  const tokens = { accent: swatch ? swatch.hex : "var(--c-primary)", schema, catalog, notice, privacyHref: links.privacy, family, images: imgs, seed: target.slug || target.host || "" };
  document.documentElement.lang = locale;
  root.innerHTML = mod.render(site, t, tokens);
  root.removeAttribute("aria-busy");
  import("./motion.js").then((m) => m.wireMotion()).catch(() => {}); // sliders, count-up, top bar: cosmetic, never blocks
  import("./reveal.js").then((m) => m.revealSections()).catch(() => {});
  const ctx = Object.assign(makeCtx(site, t, tokens, key), { slug: target.slug, host: target.host, consent: consentFor(nf.notices, locale), images: imgs, family });
  return ctx;
}

async function showPrivacy(site, target) {
  const [, catalog] = await loadShared();
  const keys = localeKeys(catalog), locale = pickLocale(keys, site.locale);
  const [dict, nf, { privacyHtml, wirePrivacy }] = await Promise.all([loadDict(locale, keys), loadNotices(), import("./privacy.js")]);
  const t = makeT(dict), links = siteLinks(target);
  document.documentElement.lang = locale;
  root.innerHTML = privacyHtml(site, t, { notice: (nf.notices || {}).enquiry_form, catalog, backHref: links.home });
  root.removeAttribute("aria-busy");
  document.title = t("site.privacy_page_title", { name: (site.profile || {}).full_name || "" });
  wirePrivacy(root, { t, catalog, slug: target.slug, host: target.host, name: (site.profile || {}).full_name, submit: backend.submitDataRequest });
}

async function showPlatform(page) {
  const [, catalog] = await loadShared();
  const keys = localeKeys(catalog);
  const locale = pickLocale(keys, q.get("lang") || (navigator.language || "").slice(0, 2));
  const [dict, nf, { legalHtml }] = await Promise.all([loadDict(locale, keys), loadNotices(), import("./legal.js")]);
  const t = makeT(dict), other = keys.find((k) => k !== locale) || locale;
  document.documentElement.lang = locale;
  root.innerHTML = legalHtml(page, t, { notice: (nf.notices || {}).agent_signup, otherLang: other, otherHref: `?lang=${other}` });
  root.removeAttribute("aria-busy");
  document.title = t(page === "terms" ? "site.legal_terms_title" : "site.legal_privacy_title");
}

async function boot() {
  const lang = (navigator.language || "").slice(0, 2);
  const target = resolve(location, q, cfg.SITE_BASE_URL ? new URL(cfg.SITE_BASE_URL).host : location.host);
  if (!target) return unavailable(lang);
  if (target.platform) return showPlatform(target.platform).catch(() => unavailable(lang, true));
  let site;
  try {
    site = target.slug ? await backend.getPublicSite(target.slug) : await backend.getPublicSiteByHost(target.host);
  } catch { return unavailable(lang, true); }
  if (!site) return unavailable(lang);
  if (target.page === "privacy") return showPrivacy(site, target).catch(() => unavailable(site.locale || lang, true));
  const ctx = await show(site, target);
  // One URL form: /a/<slug>/ (the same as the Worker's 301, the canonical URL and the manifest scope).
  const path = target.slug ? "/a/" + encodeURIComponent(target.slug) + "/" : location.pathname;
  applyHead(headData(site, { origin: location.origin, path, canonicalHost: target.host || site.canonical_host || null, noindex: q.get("install") === "1", t: ctx.t, catalog: ctx.catalog, schema: ctx.schema, images: ctx.images, family: ctx.family }));
  wireForm(root, Object.assign(ctx, { submit: backend.submitEnquiry }));
  installable(site, target, ctx.t).catch(() => {}); // never breaks the page
  // ?install=1 is the agent coming from the app (Website tab: Add to home screen), so it isn't a visitor's view.
  if (q.get("install") !== "1") recordView(target);
}

// Add to home screen (DECISIONS #65): the manifest (built by worker.js), icons, and the install button/sheet.
// Loaded after the page renders, so it doesn't count against first paint.
async function installable(site, target, t) {
  const [{ manifestHref, appNames, swatchFor, iconPath }, { wireInstall }] = await Promise.all([import("./manifest.js"), import("./install.js")]);
  const link = (rel, href) => { const e = document.head.querySelector(`link[rel="${rel}"]`) || document.head.appendChild(Object.assign(document.createElement("link"), { rel })); e.href = href; };
  const meta = (name, content) => { const e = document.head.querySelector(`meta[name="${name}"]`) || document.head.appendChild(Object.assign(document.createElement("meta"), { name })); e.content = content; };
  link("manifest", manifestHref(target));
  const swatch = swatchFor((await loadShared())[1], site.accent);
  if (swatch) { link("icon", iconPath(swatch.key, 192)); link("apple-touch-icon", iconPath(swatch.key, 192)); }
  meta("apple-mobile-web-app-title", appNames(site).short);
  wireInstall({ t, name: (site.profile || {}).full_name || "", forced: q.get("install") === "1" });
}

const recordView = (target) => recordViewOnce(target, backend);

// In-app preview (WebView): ?preview=1 skips resolution; the app calls renderSite(draftJson) (defined by app.js).
export async function renderSite(input) {
  const site = typeof input === "string" ? JSON.parse(input) : input;
  const company = site.company && typeof site.company.disclaimer === "string" ? { disclaimer: site.company.disclaimer } : null;
  const ctx = await show({ ...site, profile: site.profile || {}, offers: normalizeOffers(site.offers), company, companies: normalizeCompanies(site.companies) });
  wireForm(root, Object.assign(ctx, { submit: async () => ({ status: "ok" }) }));
}
export { boot };
