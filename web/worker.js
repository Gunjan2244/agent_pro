// Cloudflare Worker for agentpro-sites (DECISIONS #65, #75). It runs only for the paths in wrangler.jsonc
// `run_worker_first`: /manifest.webmanifest, and the page routes /, /a/*, /privacy plus /robots.txt and /sitemap.xml.
// Everything else (fonts, CSS, JS, JSON) is served straight from static assets without invoking it.
//   page      GET|HEAD  server-side render (web/ssr.js); 301 /a/<slug> -> /a/<slug>/; 404 + noindex when unavailable;
//                       backend error -> the static shell (index.html), so the browser retries; platform pages pass through
//   manifest  the agent's web app manifest (a lapsed or unpublished site is never installable)
//   robots.txt, sitemap.xml  see docs/WEB_RENDERER.md
import { manifestFor, parseConfig, MANIFEST_PATH } from "./manifest.js";
import { resolveTarget } from "./route.js";
import { normalizeSite } from "./api.js";
import { loadDictWith, localeKeys, pickLocale } from "./i18n.js";
import { renderDocument, unavailableDocument } from "./ssr.js";
import { SECURITY_HEADERS } from "./headers.js";

// A cheap guard before the RPC, not the slug rule (that lives in SQL and the app).
const SLUG = /^[a-z0-9-]{1,64}$/;
const SITEMAP_MAX = 50000; // sitemaps.org limit per file
const headers = (extra) => ({ "x-content-type-options": "nosniff", ...extra });
const missing = () => new Response("Not found", { status: 404, headers: headers({ "content-type": "text/plain; charset=utf-8", "cache-control": "public, max-age=60" }) });
const unavailable503 = () => new Response("Unavailable", { status: 503, headers: headers({ "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" }) });

async function asset(env, url, path) {
  const r = await env.ASSETS.fetch(new URL(path, url));
  if (!r.ok) throw new Error(`asset ${path} ${r.status}`);
  return r;
}

async function rpc(cfg, name, args) {
  const r = await fetch(`${cfg.SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: { "content-type": "application/json", apikey: cfg.SUPABASE_ANON_KEY, authorization: `Bearer ${cfg.SUPABASE_ANON_KEY}` },
    body: JSON.stringify(args),
  });
  if (!r.ok) throw new Error(`rpc ${name} ${r.status}`);
  return r.json();
}

// Static files the Worker reads through env.ASSETS, cached per isolate (they only change on a deploy, which restarts
// isolates). Keyed by the ASSETS binding so tests with different bindings stay apart; a failed load is not cached.
const caches = new WeakMap();
function cached(env, key, load) {
  let m = caches.get(env.ASSETS);
  if (!m) caches.set(env.ASSETS, (m = new Map()));
  if (!m.has(key)) m.set(key, load().catch((e) => { m.delete(key); throw e; }));
  return m.get(key);
}
const json = (env, url, path) => cached(env, path, () => asset(env, url, path).then((r) => r.json()));
async function loadConfig(env, url) {
  const cfg = await cached(env, "/config.js", () => asset(env, url, "/config.js").then((r) => r.text()).then(parseConfig));
  if (!cfg.SUPABASE_URL) throw new Error("config.js has no SUPABASE_URL");
  return cfg;
}
// web/images/manifest.json is optional: without it the page simply has no bundled illustrations.
const loadImages = (env, url) => json(env, url, "/images/manifest.json").then((m) => (m && typeof m === "object" && !Array.isArray(m) ? m : {})).catch(() => ({}));
const loadShared = (env, url) => Promise.all([json(env, url, "/shared/site-schema.json"), json(env, url, "/shared/catalog.json"), json(env, url, "/shared/templates/index.json"), loadImages(env, url)])
  .then(([schema, catalog, templates, images]) => ({ schema, catalog, templates, images }));
const loadNotices = (env, url) => json(env, url, "/shared/generated/privacy-notices.json").catch(() => ({ notices: {} }));
const loadDict = (env, url, locale) => cached(env, "dict:" + locale, () => loadDictWith((p) => asset(env, url, p), locale, [locale]));

const primaryHostOf = (cfg, url) => (cfg.SITE_BASE_URL ? new URL(cfg.SITE_BASE_URL).host : url.host);

export async function handleManifest(request, env) {
  const url = new URL(request.url);
  const [cfg, catalog, tokens] = await Promise.all([loadConfig(env, url), json(env, url, "/shared/catalog.json"), json(env, url, "/shared/design-tokens.json")]);
  const target = resolveTarget(url, url.searchParams, primaryHostOf(cfg, url));
  if (!target || target.platform || (target.slug && !SLUG.test(target.slug))) return missing();
  const site = normalizeSite(target.slug ? await rpc(cfg, "get_public_site", { slug: target.slug }) : await rpc(cfg, "get_public_site_by_host", { host: target.host }));
  if (!site) return missing();
  return new Response(JSON.stringify(manifestFor(site, target, { catalog, tokens })), {
    headers: headers({ "content-type": "application/manifest+json; charset=utf-8", "cache-control": "public, max-age=300" }),
  });
}

// --- pages ---
const htmlHeaders = (extra = {}) => ({ ...SECURITY_HEADERS, "content-type": "text/html; charset=utf-8", "cache-control": "no-cache", ...extra });
const acceptLocale = (request) => ((request.headers.get("accept-language") || "").split(",")[0] || "").trim().slice(0, 2).toLowerCase();

async function unavailablePage(request, env, url) {
  const { catalog } = await loadShared(env, url);
  const locale = pickLocale(localeKeys(catalog), acceptLocale(request));
  const dict = await loadDict(env, url, locale).catch(() => ({}));
  const body = unavailableDocument({ dict, locale });
  return new Response(request.method === "HEAD" ? null : body, { status: 404, headers: htmlHeaders() });
}

// The static shell: the browser resolves and renders the page itself (preview, mock, or a backend error).
const shell = (request, env) => env.ASSETS.fetch(new Request(request.url, { method: "GET" }));

// max(published_at, BUILD_TIME): a deploy that changes a template or its CSS also invalidates browsers' copies,
// not only a re-publish. No published_at -> no Last-Modified (nothing to compare the content against).
export const lastModified = (site, cfg = {}) => {
  const ms = site.published_at ? Date.parse(site.published_at) : NaN;
  if (Number.isNaN(ms)) return null;
  const built = cfg.BUILD_TIME ? Date.parse(cfg.BUILD_TIME) : NaN;
  return Math.floor(Math.max(ms, Number.isNaN(built) ? ms : built) / 1000) * 1000;
};

export async function handlePage(request, env) {
  const url = new URL(request.url), q = url.searchParams;
  // The in-app preview and the local mock backend always render in the browser.
  if (q.get("preview") === "1" || q.has("mock")) return shell(request, env);
  let cfg;
  try { cfg = await loadConfig(env, url); } catch (e) { console.error("page config", e && e.message); return shell(request, env); }
  const target = resolveTarget(url, q, primaryHostOf(cfg, url));
  if (!target || target.platform) return shell(request, env); // platform pages (/terms, /privacy on the primary host) and "/" with no slug
  // A custom domain serves its site at / and /privacy only.
  if (target.host && !/^\/(privacy\/?)?$/.test(url.pathname)) return shell(request, env);
  if (target.slug && !SLUG.test(target.slug)) return unavailablePage(request, env, url).catch(() => shell(request, env));
  // One URL form: /a/<slug>/ (the manifest scope and the canonical URL).
  const bare = url.pathname.match(/^\/a\/([^/]+)$/);
  if (bare) return new Response(null, { status: 301, headers: headers({ location: `${url.origin}${url.pathname}/${url.search}`, "cache-control": "public, max-age=300" }) });

  let site;
  try {
    site = normalizeSite(target.slug ? await rpc(cfg, "get_public_site", { slug: target.slug }) : await rpc(cfg, "get_public_site_by_host", { host: target.host }));
  } catch (e) { console.error("page rpc", e && e.message); return shell(request, env); }
  if (!site) return unavailablePage(request, env, url).catch(() => shell(request, env));

  try {
    const lm = lastModified(site, cfg);
    const extra = lm == null ? {} : { "last-modified": new Date(lm).toUTCString() };
    const ims = Date.parse(request.headers.get("if-modified-since") || "");
    if (lm != null && !Number.isNaN(ims) && ims >= lm) return new Response(null, { status: 304, headers: htmlHeaders(extra) });
    const shared = await loadShared(env, url);
    const locale = pickLocale(localeKeys(shared.catalog), site.locale);
    const [dict, notices] = await Promise.all([loadDict(env, url, locale), loadNotices(env, url)]);
    const body = renderDocument({ site, target, url, dict, shared, notices, config: cfg });
    return new Response(request.method === "HEAD" ? null : body, { status: 200, headers: htmlHeaders(extra) });
  } catch (e) {
    console.error("render", e && e.message);
    return shell(request, env);
  }
}

// --- crawling ---
const text = (body, type, cache) => new Response(body, { headers: headers({ "content-type": type, "cache-control": cache }) });
const xml = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&apos;" })[c]);
const iso = (v) => { const ms = Date.parse(v); return Number.isNaN(ms) ? null : new Date(ms).toISOString(); };

export function handleRobots(request) {
  const url = new URL(request.url);
  // Preview, install and mock pages are for the agent and developers, never for search.
  const body = ["User-agent: *", "Allow: /", "Disallow: /*?preview", "Disallow: /*&preview", "Disallow: /*?install", "Disallow: /*&install", "Disallow: /*?mock", "Disallow: /*&mock", "", `Sitemap: ${url.origin}/sitemap.xml`, ""].join("\n");
  return text(body, "text/plain; charset=utf-8", "public, max-age=3600");
}

export async function handleSitemap(request, env) {
  const url = new URL(request.url);
  const cfg = await loadConfig(env, url);
  const urlset = (entries) => `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${entries.map(([loc, lm]) => `<url><loc>${xml(loc)}</loc>${lm ? `<lastmod>${lm}</lastmod>` : ""}</url>`).join("\n")}\n</urlset>\n`;
  if (url.host !== primaryHostOf(cfg, url)) {
    // A custom domain lists its own two pages.
    const site = normalizeSite(await rpc(cfg, "get_public_site_by_host", { host: url.hostname }));
    if (!site) return missing();
    const lm = iso(site.published_at), base = `https://${url.hostname}`;
    return text(urlset([[base + "/", lm], [base + "/privacy", lm]]), "application/xml; charset=utf-8", "public, max-age=300");
  }
  const rows = await rpc(cfg, "list_public_sites", {});
  if (!Array.isArray(rows)) throw new Error("list_public_sites: not an array");
  const base = (cfg.SITE_BASE_URL || url.origin).replace(/\/+$/, "");
  const entries = rows.filter((r) => r && typeof r.slug === "string" && SLUG.test(r.slug)).slice(0, SITEMAP_MAX).map((r) => [`${base}/a/${r.slug}/`, iso(r.published_at)]);
  return text(urlset(entries), "application/xml; charset=utf-8", "public, max-age=300");
}

const PAGE_PATH = /^\/(a\/.*|privacy\/?)?$/;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;
    const isGetHead = request.method === "GET" || request.method === "HEAD";
    if (path === MANIFEST_PATH || path === "/robots.txt" || path === "/sitemap.xml") {
      if (!isGetHead) return new Response(null, { status: 405, headers: headers({ allow: "GET, HEAD" }) });
      try {
        if (path === "/robots.txt") return handleRobots(request);
        return await (path === "/sitemap.xml" ? handleSitemap : handleManifest)(request, env);
      } catch (e) {
        console.error(path, e && e.message);
        return unavailable503();
      }
    }
    if (isGetHead && PAGE_PATH.test(path)) return handlePage(request, env);
    return env.ASSETS.fetch(request);
  },
};
