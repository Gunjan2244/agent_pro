// Boot: resolve site -> fetch -> pick template -> render -> wire form -> record view.
import * as api from "./api.js";
import { loadDict, makeT, localeKeys, pickLocale } from "./i18n.js";
import { headData, applyHead } from "./head.js";
import { wireForm } from "./form.js";
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

function resolveTarget() {
  const primary = cfg.SITE_BASE_URL ? new URL(cfg.SITE_BASE_URL).host : location.host;
  if (location.host === primary) {
    const m = location.pathname.match(/^\/a\/([^/]+)\/?$/);
    const slug = m ? decodeURIComponent(m[1]) : q.get("s");
    return slug ? { slug } : null;
  }
  return { host: location.hostname };
}

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

let shared;
const loadShared = () => (shared ||= Promise.all([json("/shared/site-schema.json"), json("/shared/catalog.json"), json("/shared/templates/index.json")]));

export async function show(site, target = {}) {
  const [schema, catalog, templates] = await loadShared();
  const tkeys = templates.templates.slice().sort((a, b) => a.order - b.order).map((x) => x.key);
  const key = tkeys.includes(site.template_key) ? site.template_key : tkeys[0];
  const swatch = (catalog.accentSwatches || []).find((s) => s.key === site.accent) || (catalog.accentSwatches || [])[0];
  const keys = localeKeys(catalog), locale = pickLocale(keys, site.locale);
  const [dict, mod] = await Promise.all([loadDict(locale, keys), import(`./templates/${key}/render.js`), stylesheet(`/templates/${key}/style.css`)]);
  const t = makeT(dict);
  const tokens = { accent: swatch ? swatch.hex : "var(--c-primary)", schema, catalog };
  document.documentElement.lang = locale;
  root.innerHTML = mod.render(site, t, tokens);
  root.removeAttribute("aria-busy");
  const ctx = Object.assign(makeCtx(site, t, tokens, key), { slug: target.slug, host: target.host });
  return ctx;
}

async function boot() {
  const lang = (navigator.language || "").slice(0, 2);
  const target = resolveTarget();
  if (!target) return unavailable(lang);
  let site;
  try {
    site = target.slug ? await backend.getPublicSite(target.slug) : await backend.getPublicSiteByHost(target.host);
  } catch { return unavailable(lang, true); }
  if (!site) return unavailable(lang);
  const ctx = await show(site, target);
  applyHead(headData(site, { origin: location.origin, path: location.pathname + (target.slug && !location.pathname.startsWith("/a/") ? "?s=" + encodeURIComponent(target.slug) : ""), canonicalHost: target.host || site.canonical_host || null }));
  wireForm(root, Object.assign(ctx, { submit: backend.submitEnquiry }));
  recordView(target);
}

function recordView(target) {
  const k = "apv:" + (target.slug || target.host);
  try { if (sessionStorage.getItem(k)) return; sessionStorage.setItem(k, "1"); } catch { /* storage blocked: still count once per load */ }
  backend.recordSiteView(target.slug, target.host).catch(() => {});
}

// In-app preview (WebView): ?preview=1 skips resolution; the app calls renderSite(draftJson).
window.renderSite = async (input) => {
  const site = typeof input === "string" ? JSON.parse(input) : input;
  const ctx = await show({ ...site, profile: site.profile || {} });
  wireForm(root, Object.assign(ctx, { submit: async () => ({ status: "ok" }) }));
};
if (q.get("preview") !== "1") boot();
