// Cloudflare Worker for agentpro-sites (DECISIONS #65, #75). It runs only for the paths in wrangler.jsonc
// `run_worker_first`: /config.js, /manifest.webmanifest, and the page routes /, /a/*, /privacy plus /robots.txt and /sitemap.xml.
// Everything else (fonts, CSS, JS, JSON) is served straight from static assets without invoking it.
//   page      GET|HEAD  server-side render (web/ssr.js) of a site page (DECISIONS #122: Home, /insurance,
//                       /insurance/<type>, /about, /contact; shared/site-pages.json) or the privacy page; 301 /a/<slug> ->
//                       /a/<slug>/ and …/about/ -> …/about; 404 + noindex when unavailable or not a page of the site;
//                       backend error -> the static shell (index.html), so the browser retries; platform pages pass through
//   sitemap   /sitemap.xml (primary host: an index of /a/<slug>/sitemap.xml; custom domain: its pages) and /a/<slug>/sitemap.xml
//   manifest  the agent's web app manifest (a lapsed or unpublished site is never installable)
//   portal    the customer login at /a/<slug>/my/* (custom domain /my/*), GET|HEAD|POST, server-rendered, never cached (DECISIONS #98)
//   join      the customer sign-up page at /a/<slug>/join/* (custom domain /join/*), GET|HEAD|POST, server-rendered, never cached (DECISIONS #108)
//   robots.txt, sitemap.xml  see docs/WEB_RENDERER.md
//   config.js  the built config.js with the Worker's env (secrets/vars) merged over it, no-store (DECISIONS #125)
import { manifestFor, parseConfig, mergeConfig, configScript, MANIFEST_PATH } from "./manifest.js";
import { portalServiceWorker } from "./portal-sw.js";
import { resolveTarget, portalLinks, matchPage } from "./route.js";
import { portalToken, portalCookie, clearPortalCookie, inviteToken, inviteCookie, clearInviteCookie, isInviteToken, joinToken, joinCookie, clearJoinCookie } from "./cookies.js";
import * as portal from "./portal.js";
import * as join from "./join.js";
import { JOIN_KEYS, joinLinks } from "./join.js";
import { normalizeSite } from "./api.js";
import { loadDictWith, localeKeys, makeT, pickLocale } from "./i18n.js";
import { renderDocument, unavailableDocument, sitePageList } from "./ssr.js";
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

// `portalKey` (the PORTAL_RPC_KEY secret) is sent as x-portal-key on the six anon portal RPCs only (DECISIONS #101, H2).
async function rpc(cfg, name, args, portalKey) {
  const r = await fetch(`${cfg.SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: { "content-type": "application/json", apikey: cfg.SUPABASE_ANON_KEY, authorization: `Bearer ${cfg.SUPABASE_ANON_KEY}`, ...(portalKey ? { "x-portal-key": portalKey } : {}) },
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
// The built config.js merged with the Worker's env (mergeConfig); a missing config.js counts as empty when env has the keys.
const loadBuiltConfig = (env, url) => cached(env, "/config.js", () => asset(env, url, "/config.js").then((r) => r.text()).then(parseConfig)).catch(() => ({}));
async function loadConfig(env, url) {
  const cfg = mergeConfig(await loadBuiltConfig(env, url), env);
  if (!cfg.SUPABASE_URL) throw new Error("no SUPABASE_URL in the Worker env or config.js");
  return cfg;
}

// /config.js for the browser, from the same merged config, so the page and the Worker never disagree.
async function handleConfig(request, env) {
  const url = new URL(request.url);
  const body = configScript(mergeConfig(await loadBuiltConfig(env, url), env));
  return new Response(request.method === "HEAD" ? null : body, { headers: headers({ "content-type": "text/javascript; charset=utf-8", "cache-control": "no-store" }) });
}
// web/images/manifest.json is optional: without it the page simply has no bundled illustrations.
const loadImages = (env, url) => json(env, url, "/images/manifest.json").then((m) => (m && typeof m === "object" && !Array.isArray(m) ? m : {})).catch(() => ({}));
const loadPages = (env, url) => json(env, url, "/shared/site-pages.json"); // the page model (DECISIONS #122)
const loadShared = (env, url) => Promise.all([json(env, url, "/shared/site-schema.json"), json(env, url, "/shared/catalog.json"), json(env, url, "/shared/templates/index.json"), loadImages(env, url), loadPages(env, url)])
  .then(([schema, catalog, templates, images, pages]) => ({ schema, catalog, templates, images, pages }));
const loadNotices = (env, url) => json(env, url, "/shared/generated/privacy-notices.json").catch(() => ({ notices: {} }));
const loadDict = (env, url, locale) => cached(env, "dict:" + locale, () => loadDictWith((p) => asset(env, url, p), locale, [locale]));

const primaryHostOf = (cfg, url) => (cfg.SITE_BASE_URL ? new URL(cfg.SITE_BASE_URL).host : url.host);

export async function handleManifest(request, env) {
  const url = new URL(request.url);
  const [cfg, catalog, tokens] = await Promise.all([loadConfig(env, url), json(env, url, "/shared/catalog.json"), json(env, url, "/shared/design-tokens.json")]);
  const target = resolveTarget(url, url.searchParams, primaryHostOf(cfg, url));
  if (!target || target.platform || (target.slug && !SLUG.test(target.slug))) return missing();
  const raw = target.slug ? await rpc(cfg, "get_public_site", { slug: target.slug }) : await rpc(cfg, "get_public_site_by_host", { host: target.host });
  const site = normalizeSite(raw);
  if (!site) return missing();
  const portalApp = url.searchParams.get("app") === "portal";
  let t;
  if (portalApp) { // the customer portal's installable app: only while the portal is available (the RPC's flag)
    const row = Array.isArray(raw) ? raw[0] : raw;
    if (!row || row.portal !== true) return missing();
    t = makeT(await loadDict(env, url, pickLocale(localeKeys(catalog), site.locale)).catch(() => ({})));
  }
  return new Response(JSON.stringify(manifestFor(site, target, { catalog, tokens, portal: portalApp, t })), {
    headers: headers({ "content-type": "application/manifest+json; charset=utf-8", "cache-control": "public, max-age=300" }),
  });
}

// --- pages ---
const htmlHeaders = (extra = {}) => ({ ...SECURITY_HEADERS, "content-type": "text/html; charset=utf-8", "cache-control": "no-cache", ...extra });
const acceptLocale = (request) => ((request.headers.get("accept-language") || "").split(",")[0] || "").trim().slice(0, 2).toLowerCase();

async function unavailablePage(request, env, url, extra = {}) {
  const { catalog } = await loadShared(env, url);
  const locale = pickLocale(localeKeys(catalog), acceptLocale(request));
  const dict = await loadDict(env, url, locale).catch(() => ({}));
  const body = unavailableDocument({ dict, locale });
  return new Response(request.method === "HEAD" ? null : body, { status: 404, headers: htmlHeaders(extra) });
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
  // The site's pages (DECISIONS #122): which paths are pages is shared/site-pages.json.
  let defs;
  try { defs = await loadPages(env, url); } catch (e) { console.error("page defs", e && e.message); return shell(request, env); }
  const path = target.path || "";
  const clean = path.replace(/\/+$/, "");
  const known = target.page === "privacy" || Boolean(matchPage(defs, clean));
  // A custom domain serves its site's pages and /privacy; any other path passes through to the assets.
  if (target.host && !known) return shell(request, env);
  if (target.slug && !SLUG.test(target.slug)) return unavailablePage(request, env, url).catch(() => shell(request, env));
  // One URL form: /a/<slug>/ for Home (the manifest scope and the canonical URL), and no trailing slash on the other
  // pages (/a/<slug>/about, /about); the other form 301s, query kept, before any backend call.
  const bare = url.pathname.match(/^\/a\/([^/]+)$/);
  if (bare) return new Response(null, { status: 301, headers: headers({ location: `${url.origin}${url.pathname}/${url.search}`, "cache-control": "public, max-age=300" }) });
  if (target.page !== "privacy" && clean && clean !== path && known) return new Response(null, { status: 301, headers: headers({ location: `${url.origin}${url.pathname.replace(/\/+$/, "")}${url.search}`, "cache-control": "public, max-age=300" }) });
  // An unknown page of a site: 404 without a backend call.
  if (!known) return unavailablePage(request, env, url).catch(() => shell(request, env));

  let site;
  try {
    site = normalizeSite(target.slug ? await rpc(cfg, "get_public_site", { slug: target.slug }) : await rpc(cfg, "get_public_site_by_host", { host: target.host }));
  } catch (e) { console.error("page rpc", e && e.message); return shell(request, env); }
  if (!site) return unavailablePage(request, env, url).catch(() => shell(request, env));

  try {
    const lm = lastModified(site, cfg);
    const extra = lm == null ? {} : { "last-modified": new Date(lm).toUTCString() };
    const shared = await loadShared(env, url);
    const locale = pickLocale(localeKeys(shared.catalog), site.locale);
    const [dict, notices] = await Promise.all([loadDict(env, url, locale), loadNotices(env, url)]);
    // Rendered before the If-Modified-Since check: whether a page exists (a plan type the agent offers, a page with
    // content) is only known from the render, and a page that no longer exists must 404, never 304.
    const body = renderDocument({ site, target: { ...target, path: clean }, url, dict, shared, notices, config: cfg });
    if (body == null) return unavailablePage(request, env, url);
    const ims = Date.parse(request.headers.get("if-modified-since") || "");
    if (lm != null && !Number.isNaN(ims) && ims >= lm) return new Response(null, { status: 304, headers: htmlHeaders(extra) });
    return new Response(request.method === "HEAD" ? null : body, { status: 200, headers: htmlHeaders(extra) });
  } catch (e) {
    console.error("render", e && e.message);
    return shell(request, env);
  }
}

// --- customer portal (DECISIONS #98; web/portal.js renders, this file handles the session, forms and RPCs) ---
const PORTAL_FORM_MAX = 4096; // bytes; the biggest legitimate form is a 500-character message
const PORTAL_POST = new Set(["login", "start", "ask", "request", "logout"]);
const PORTAL_FLASH = new Set(["asked", "requested", "signed_out"]);
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
let keyMissingLogged = false; // log the missing secret once per isolate, never its value
const PORTAL_NO_STORE = { "cache-control": "no-store", "x-robots-tag": "noindex, nofollow" };
const portalHeaders = (extra = {}) => htmlHeaders({ "cache-control": "no-store", "x-robots-tag": "noindex, nofollow", ...extra });
const redirectTo = (location, cookie) => {
  const h = new Headers(portalHeaders({ location }));
  h.delete("content-type");
  for (const c of [].concat(cookie || [])) h.append("set-cookie", c);
  return h;
};
const seeOther = (location, cookie, extra = {}) => {
  const h = redirectTo(location, cookie);
  for (const [k, v] of Object.entries(extra)) h.set(k, v);
  return new Response(null, { status: 303, headers: h });
};
const plain = (status, body) => new Response(body, { status, headers: { ...SECURITY_HEADERS, "content-type": "text/plain; charset=utf-8", ...PORTAL_NO_STORE } });
const todayIST = () => new Date(Date.now() + 19800000).toISOString().slice(0, 10);

// Reads a small urlencoded body with a hard byte cap (a lying or missing Content-Length cannot get past it).
async function readForm(request) {
  const len = Number(request.headers.get("content-length"));
  if (Number.isFinite(len) && len > PORTAL_FORM_MAX) return null;
  if (!request.body) return new URLSearchParams();
  const reader = request.body.getReader(), chunks = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.length;
    if (total > PORTAL_FORM_MAX) { await reader.cancel().catch(() => {}); return null; }
    chunks.push(value);
  }
  const buf = new Uint8Array(total);
  let o = 0;
  for (const c of chunks) { buf.set(c, o); o += c.length; }
  return new URLSearchParams(new TextDecoder().decode(buf));
}

// The consent the start form sends: the current notice's version and hash for the site's language (see form.js consentFor).
const portalConsent = (notices, locale) => {
  const n = notices && ((notices.notices || notices).customer_portal);
  if (!n || !n.hash) return null;
  return { notice_key: "customer_portal", version: n.version, hash: n.hash[locale] || n.hash.en };
};

export async function handlePortal(request, env) {
  const url = new URL(request.url);
  const method = request.method, isGetHead = method === "GET" || method === "HEAD", isPost = method === "POST";
  const respond = (body, status = 200, extra, cookie) => {
    const h = new Headers(portalHeaders(extra));
    if (cookie) h.append("set-cookie", cookie);
    return new Response(method === "HEAD" ? null : body, { status, headers: h });
  };
  // A backend or asset failure never falls back to the shell: the shell would show a logged-out page (and cache).
  const failure = async (cfgLocale) => {
    let dict = {}, locale = cfgLocale || "en";
    try { const { catalog } = await loadShared(env, url); locale = pickLocale(localeKeys(catalog), cfgLocale || acceptLocale(request)); dict = await loadDict(env, url, locale); } catch { /* bare error page */ }
    let body;
    try { body = portal.portalErrorDocument({ dict, locale }); } catch { body = "Unavailable"; }
    return respond(body, 503);
  };
  try {
    // Fail closed without the shared secret: the portal RPCs would all be refused anyway (E1), so do not even try.
    const portalKey = env.PORTAL_RPC_KEY;
    if (!portalKey) {
      if (!keyMissingLogged) { keyMissingLogged = true; console.error("portal disabled: PORTAL_RPC_KEY secret is not set"); }
      return failure();
    }
    let cfg;
    try { cfg = await loadConfig(env, url); } catch (e) { console.error("portal config", e && e.message); return failure(); }
    const target = resolveTarget(url, url.searchParams, primaryHostOf(cfg, url));
    if (!target || target.platform || target.page !== "portal") return unavailablePage(request, env, url, PORTAL_NO_STORE);
    if (target.slug && !SLUG.test(target.slug)) return unavailablePage(request, env, url, PORTAL_NO_STORE);
    const path = target.portalPath;
    if (path === null || path === undefined) return unavailablePage(request, env, url, PORTAL_NO_STORE);
    const links = portalLinks(target);
    const idArgs = { slug: target.slug || null, host: target.slug ? null : target.host };

    // Method rules first, so a wrong request costs no RPC.
    if (path === "sw.js") {
      if (!isGetHead) return plain(405, "Method not allowed");
      // served after the site/portal-flag check below (404 while the portal is off)
    }
    if (PORTAL_POST.has(path)) {
      if (!isPost && !isGetHead) return plain(405, "Method not allowed");
      // A GET on the action URLs (ask, request, logout) is just a stray link: send it home.
      if (isGetHead && path !== "login" && path !== "start") return seeOther(links.home);
    } else if (!isGetHead) {
      return plain(405, "Method not allowed");
    }
    let form = null;
    if (isPost) {
      if (!/^application\/x-www-form-urlencoded\b/i.test(request.headers.get("content-type") || "")) return plain(415, "Unsupported media type");
      if (request.headers.get("origin") !== url.origin) return plain(403, "Forbidden");
      form = await readForm(request);
      if (!form) return plain(413, "Payload too large");
    }

    let site;
    try {
      const raw = target.slug ? await rpc(cfg, "get_public_site", { slug: target.slug }) : await rpc(cfg, "get_public_site_by_host", { host: target.host });
      // normalizeSite may not carry `portal` (the RPC's "portal available" flag), so read it from the raw row.
      const row = Array.isArray(raw) ? raw[0] : raw;
      site = normalizeSite(raw);
      if (site) site = { ...site, portal: !!row && row.portal === true };
    } catch (e) { console.error("portal site rpc", e && e.message); return failure(); }
    if (!site || site.portal !== true) return unavailablePage(request, env, url, PORTAL_NO_STORE);

    const shared = await loadShared(env, url);
    const locale = pickLocale(localeKeys(shared.catalog), site.locale);
    const [dict, notices] = await Promise.all([loadDict(env, url, locale), loadNotices(env, url)]);
    const ctx = { site, target, dict, shared, config: cfg };
    const notFound = () => unavailablePage(request, env, url, PORTAL_NO_STORE);
    if (path === "sw.js") return portalServiceWorker({ request, env, url, target, links, dict, locale, config: cfg });
    const cookieFor = (r) => {
      const secs = Math.floor((Date.parse(r.expires_at) - Date.now()) / 1000);
      return portalCookie(r.session_token, links.base, Number.isFinite(secs) ? Math.max(0, secs) : 0);
    };

    const renderLogin = (status, error, values, flash) => respond(portal.portalLoginDocument({ ...ctx, error, values, flash }), status);
    // The start page must NOT use no-referrer: Chromium sends an `Origin: null` header on the same-origin form POST under
    // no-referrer (and same-origin has similar quirks), which the Origin check below rightly rejects, so the first login would
    // fail. strict-origin sends only the origin (no path, no token) and is never downgraded on https. The invite token is not in
    // the URL anyway (it travels in the ap_invite cookie, DECISIONS #101).
    const renderStart = (status, error, values, cookie) =>
      respond(portal.portalStartDocument({ ...ctx, notices, error, values }), status, { "Referrer-Policy": "strict-origin" }, cookie);
    // The signed-in home. `error` re-renders it after a rejected ask/request (a fresh fetch, so the page is never stale).
    const renderHome = async (token, { status = 200, flash, error, values } = {}) => {
      const home = await rpc(cfg, "portal_home", { token, ...idArgs }, portalKey);
      if (home && home.ok === true) return respond(portal.portalHomeDocument({ ...ctx, home, flash, error, values, today: todayIST() }), status);
      if (home && home.error === "session") return seeOther(links.login, clearPortalCookie(links.base));
      return notFound();
    };

    if (isPost) {
      const limiter = env.PORTAL_LIMIT;
      const val = (k) => String(form.get(k) || "");
      const token = portalToken(request.headers.get("cookie"));
      const limited = limiter ? !(await limiter.limit({ key: request.headers.get("cf-connecting-ip") || "unknown" })).success : false;
      const rate = { key: "portal.err_rate" };

      if (path === "login") {
        const values = { phone: val("phone") };
        if (limited) return renderLogin(429, rate, values);
        const r = await rpc(cfg, "portal_login", { ...idArgs, phone: val("phone"), pin: val("pin") }, portalKey);
        if (r && r.ok === true) return seeOther(links.home, cookieFor(r));
        if (r && r.error === "rate_limited") return renderLogin(429, rate, values);
        if (r && r.error === "locked") {
          const ms = r.until ? Date.parse(r.until) - Date.now() : NaN;
          if (!r.until || Number.isNaN(ms)) return renderLogin(400, { key: "portal.err_hard_locked" }, values);
          return renderLogin(400, { key: "portal.err_locked", params: { minutes: Math.max(1, Math.ceil(ms / 60000)) } }, values);
        }
        if (r && r.error === "invalid") return renderLogin(400, { key: "portal.err_invalid" }, values);
        return notFound();
      }

      if (path === "start") {
        // The invite token comes from the ap_invite cookie set by GET start?t= (never from the form).
        const t = inviteToken(request.headers.get("cookie")), values = { phone: val("phone") };
        if (!t) return renderStart(400, { key: "portal.err_invite" }, values);
        if (limited) return renderStart(429, rate, values);
        if (val("pin") !== val("pin2")) return renderStart(400, { key: "portal.err_pin_mismatch", field: "pin2" }, values);
        if (val("consent") !== "1") return renderStart(400, { key: "portal.err_consent", field: "consent" }, values);
        const consent = portalConsent(notices, locale);
        if (!consent) console.error("portal notice missing for", locale); // the RPC then rejects with field consent
        const r = await rpc(cfg, "portal_activate", { ...idArgs, token: t, phone: val("phone"), pin: val("pin"), consent }, portalKey);
        if (r && r.ok === true) return seeOther(links.home, [cookieFor(r), clearInviteCookie(links.start)]);
        if (r && r.error === "rate_limited") return renderStart(429, rate, values);
        if (r && r.error === "invalid_invite") return renderStart(400, { key: "portal.err_invite" }, values, clearInviteCookie(links.start));
        if (r && r.error === "invalid") {
          const key = { phone: "portal.err_phone_match", pin: "portal.err_pin_rule", consent: "portal.err_consent" }[r.field] || "portal.err_generic";
          return renderStart(400, { key, field: r.field }, values);
        }
        return notFound();
      }

      // ask / request / logout need the session cookie
      if (!token) return seeOther(links.login);
      if (path === "logout") {
        // The server revokes the session; the browser forgets the cookie, and the signed-out login page tells the service worker
        // to wipe its cache (portal-client.js). No Clear-Site-Data: on the shared origin it would clear every agent's site cache.
        // Even if the RPC fails the browser still forgets the cookie (the session then just expires on its own).
        try { await rpc(cfg, "portal_logout", { token, everywhere: val("everywhere") === "1" }, portalKey); } catch (e) { console.error("portal logout rpc", e && e.message); }
        return seeOther(links.login + "?done=signed_out", clearPortalCookie(links.base));
      }
      const values = { message: val("message"), kind: val("kind"), policy_id: val("policy_id"), customer_id: val("customer_id") };
      if (limited) return renderHome(token, { status: 429, error: rate, values });
      // L1: ids must be uuids before they reach the database (a malformed one would be a 500 from Postgres).
      if (!UUID.test(path === "ask" ? values.policy_id : values.customer_id)) return renderHome(token, { status: 400, error: { key: "portal.err_generic", field: path === "ask" ? "policy_id" : "customer_id" }, values });
      const r = path === "ask"
        ? await rpc(cfg, "portal_ask", { token, ...idArgs, policy_id: val("policy_id"), kind: val("kind"), message: val("message") }, portalKey)
        : await rpc(cfg, "portal_request", { token, ...idArgs, customer_id: val("customer_id"), kind: val("kind"), message: val("message") }, portalKey);
      if (r && r.ok === true) return seeOther(`${links.home}?done=${path === "ask" ? "asked" : "requested"}`);
      if (r && r.error === "session") return seeOther(links.login, clearPortalCookie(links.base));
      if (r && r.error === "rate_limited") return renderHome(token, { status: 429, error: rate, values });
      if (r && r.error === "invalid") return renderHome(token, { status: 400, error: { key: r.field === "message" ? "portal.err_required" : "portal.err_generic", field: r.field }, values });
      return notFound();
    }

    // GET / HEAD
    const flashRaw = url.searchParams.get("done");
    const flash = PORTAL_FLASH.has(flashRaw) ? flashRaw : undefined;
    if (path === "login") return renderLogin(200, undefined, undefined, flash === "signed_out" ? flash : undefined);
    if (path === "start") {
      if (url.searchParams.has("t")) {
        // Move the token out of the URL into a short-lived path-scoped cookie, then redirect to the bare start page.
        const t = url.searchParams.get("t") || "";
        return isInviteToken(t) ? seeOther(links.start, inviteCookie(t, links.start)) : renderStart(200, { key: "portal.err_invite" });
      }
      return inviteToken(request.headers.get("cookie")) ? renderStart(200) : renderStart(200, { key: "portal.err_invite" });
    }
    // home
    const token = portalToken(request.headers.get("cookie"));
    if (!token) return seeOther(links.login);
    return await renderHome(token, { flash });
  } catch (e) {
    console.error("portal", e && e.message); // message only: never a body, token, phone or PIN
    return failure();
  }
}

// --- customer sign-up page (DECISIONS #108; web/join.js renders, join-client.js autosaves; same gate, cookie and POST rules as the portal) ---
const JOIN_POST = new Set(["consent", "save", "submit"]);
// The consent payload for a notice: the current version and hash for the site's language (like portalConsent).
const noticeConsent = (notices, key, locale) => {
  const n = notices && ((notices.notices || notices)[key]);
  if (!n || !n.hash) return null;
  return { notice_key: key, version: n.version, hash: n.hash[locale] || n.hash.en };
};
const JOIN_ERR = { name: "join.err_name", phone: "join.err_phone", email: "join.err_email", dob: "join.err_dob", address: "join.err_long", city: "join.err_long", nominee: "join.err_long", guardian: "join.err_guardian" };

export async function handleJoin(request, env) {
  const url = new URL(request.url);
  const method = request.method, isGetHead = method === "GET" || method === "HEAD", isPost = method === "POST";
  const jsonOut = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: { ...SECURITY_HEADERS, "content-type": "application/json; charset=utf-8", ...PORTAL_NO_STORE } });
  // strict-origin, not no-referrer: Chromium sends `Origin: null` on same-origin form posts under no-referrer (see the portal's start page).
  const respond = (body, status = 200, cookie) => {
    const h = new Headers(portalHeaders({ "Referrer-Policy": "strict-origin" }));
    if (cookie) h.append("set-cookie", cookie);
    return new Response(method === "HEAD" ? null : body, { status, headers: h });
  };
  let wantsJson = /\/join\/save\/?$/.test(url.pathname); // the autosave answers JSON, even when it fails
  const failure = async (cfgLocale) => {
    if (wantsJson) return jsonOut({ ok: false, error: "unavailable" }, 503);
    let dict = {}, locale = cfgLocale || "en";
    try { const { catalog } = await loadShared(env, url); locale = pickLocale(localeKeys(catalog), cfgLocale || acceptLocale(request)); dict = await loadDict(env, url, locale); } catch { /* bare error page */ }
    let body;
    try { body = join.joinErrorDocument({ dict, locale }); } catch { body = "Unavailable"; }
    return respond(body, 503);
  };
  try {
    const portalKey = env.PORTAL_RPC_KEY; // the same gate secret as the portal; fail closed without it
    if (!portalKey) {
      if (!keyMissingLogged) { keyMissingLogged = true; console.error("sign-up disabled: PORTAL_RPC_KEY secret is not set"); }
      return failure();
    }
    let cfg;
    try { cfg = await loadConfig(env, url); } catch (e) { console.error("join config", e && e.message); return failure(); }
    const target = resolveTarget(url, url.searchParams, primaryHostOf(cfg, url));
    if (!target || target.platform || target.page !== "join") return unavailablePage(request, env, url, PORTAL_NO_STORE);
    if (target.slug && !SLUG.test(target.slug)) return unavailablePage(request, env, url, PORTAL_NO_STORE);
    const path = target.joinPath;
    if (path === null || path === undefined) return unavailablePage(request, env, url, PORTAL_NO_STORE);
    const links = joinLinks(target);
    const idArgs = { slug: target.slug || null, host: target.slug ? null : target.host };

    if (JOIN_POST.has(path)) {
      if (!isPost && !isGetHead) return plain(405, "Method not allowed");
      if (isGetHead) return seeOther(links.home); // a stray GET on an action URL
    } else if (!isGetHead) {
      return plain(405, "Method not allowed");
    }
    let form = null;
    if (isPost) {
      if (!/^application\/x-www-form-urlencoded\b/i.test(request.headers.get("content-type") || "")) return plain(415, "Unsupported media type");
      if (request.headers.get("origin") !== url.origin) return plain(403, "Forbidden");
      form = await readForm(request);
      if (!form) return plain(413, "Payload too large");
    }
    const token = joinToken(request.headers.get("cookie"));
    const limiter = env.JOIN_LIMIT;
    const limited = isPost && limiter ? !(await limiter.limit({ key: request.headers.get("cf-connecting-ip") || "unknown" })).success : false;

    // The autosave: no page, only the RPC; JSON answers.
    if (path === "save") {
      if (limited) return jsonOut({ ok: false, error: "rate_limited" }, 429);
      if (!token) return jsonOut({ ok: false, error: "invalid_link" }, 401);
      const fields = {};
      for (const k of JOIN_KEYS) if (form.has(k)) fields[k] = String(form.get(k));
      const active = form.get("active");
      const r = await rpc(cfg, "signup_save", { ...idArgs, token, fields, active_field: active ? String(active) : null }, portalKey);
      if (r && r.ok === true) return jsonOut({ ok: true });
      const e = r && typeof r.error === "string" && /^[a-z_]+$/.test(r.error) ? r.error : "unavailable";
      return jsonOut({ ok: false, error: e }, e === "rate_limited" ? 429 : 200);
    }

    // Everything else renders the agent's site, so it needs the site.
    let site;
    try {
      site = normalizeSite(target.slug ? await rpc(cfg, "get_public_site", { slug: target.slug }) : await rpc(cfg, "get_public_site_by_host", { host: target.host }));
    } catch (e) { console.error("join site rpc", e && e.message); return failure(); }
    if (!site) return unavailablePage(request, env, url, PORTAL_NO_STORE);
    const shared = await loadShared(env, url);
    const locale = pickLocale(localeKeys(shared.catalog), site.locale);
    const [dict, notices] = await Promise.all([loadDict(env, url, locale), loadNotices(env, url)]);
    const ctx = { site, target, dict, shared, config: cfg };
    const clear = clearJoinCookie(links.base);
    const invalid = () => respond(join.joinInvalidDocument(ctx), 200, clear);
    const notFound = () => unavailablePage(request, env, url, PORTAL_NO_STORE);
    const renderConsent = (status, error) => respond(join.joinConsentDocument({ ...ctx, notices, error }), status);
    const renderForm = (status, values, error) => respond(join.joinFormDocument({ ...ctx, notices, values, error, today: todayIST() }), status);

    if (isPost) {
      if (!token) return invalid();
      const rate = { key: "join.err_rate" };
      if (path === "consent") {
        if (limited) return renderConsent(429, "join.err_rate");
        if (String(form.get("consent") || "") !== "1") return renderConsent(400, "join.err_consent");
        const consent = noticeConsent(notices, "customer_signup", locale);
        if (!consent) console.error("sign-up notice missing for", locale); // the RPC then answers invalid
        const r = await rpc(cfg, "signup_consent", { ...idArgs, token, consent }, portalKey);
        if (r && r.ok === true) return seeOther(links.home);
        if (r && r.error === "invalid_link") return invalid();
        if (r && r.error === "invalid") return renderConsent(400, "join.err_generic");
        return notFound();
      }
      // submit
      const fields = {};
      for (const k of JOIN_KEYS) if (form.has(k)) fields[k] = String(form.get(k));
      const ticked = String(form.get("guardian") || "") === "1";
      const values = { ...fields, guardian: ticked ? "1" : "" };
      if (limited) return renderForm(429, values, rate);
      const guardian = ticked ? noticeConsent(notices, "customer_signup_guardian", locale) : null; // sent only when the box is ticked
      const r = await rpc(cfg, "signup_submit", { ...idArgs, token, fields, guardian }, portalKey);
      if (r && r.ok === true) return seeOther(links.done, clear);
      if (r && r.error === "invalid_link") return invalid();
      if (r && r.error === "consent_required") return seeOther(links.home);
      if (r && r.error === "rate_limited") return renderForm(429, values, rate);
      if (r && r.error === "invalid") return renderForm(400, values, { key: JOIN_ERR[r.field] || "join.err_generic", field: JOIN_ERR[r.field] ? r.field : undefined });
      return notFound();
    }

    // GET / HEAD
    if (path === "done") return respond(join.joinDoneDocument(ctx), 200, clear);
    if (url.searchParams.has("t")) {
      // Move the token out of the URL into a path-scoped cookie, then redirect to the bare page.
      const t = url.searchParams.get("t") || "";
      return isInviteToken(t) ? seeOther(links.home, joinCookie(t, links.base)) : invalid();
    }
    if (!token) return invalid();
    const r = await rpc(cfg, "signup_open", { ...idArgs, token }, portalKey);
    if (r && r.ok === true) return r.consented === true ? renderForm(200, r.fields && typeof r.fields === "object" ? r.fields : {}) : renderConsent(200);
    if (r && r.error === "invalid_link") return invalid();
    return notFound();
  } catch (e) {
    console.error("join", e && e.message); // message only: never a body, token or field value
    return failure();
  }
}

// --- crawling ---
const text = (body, type, cache) => new Response(body, { headers: headers({ "content-type": type, "cache-control": cache }) });
const xml = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&apos;" })[c]);
const iso = (v) => { const ms = Date.parse(v); return Number.isNaN(ms) ? null : new Date(ms).toISOString(); };

export function handleRobots(request) {
  const url = new URL(request.url);
  // Preview, install and mock pages are for the agent and developers, never for search.
  const body = ["User-agent: *", "Allow: /", "Disallow: /*?preview", "Disallow: /*&preview", "Disallow: /*?install", "Disallow: /*&install", "Disallow: /*?mock", "Disallow: /*&mock", "Disallow: /a/*/my", "Disallow: /my", "Disallow: /a/*/join", "Disallow: /join", "", `Sitemap: ${url.origin}/sitemap.xml`, ""].join("\n");
  return text(body, "text/plain; charset=utf-8", "public, max-age=3600");
}

const urlset = (entries) => `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${entries.map(([loc, lm]) => `<url><loc>${xml(loc)}</loc>${lm ? `<lastmod>${lm}</lastmod>` : ""}</url>`).join("\n")}\n</urlset>\n`;
const XML = "application/xml; charset=utf-8";

// One site's sitemap entries: every page it has (DECISIONS #122: Home, Insurance, each plan type, About, Contact, as
// the renderer decides) and its privacy page, all with lastmod = published_at. base ends with "/".
async function sitePageEntries(env, url, site, target, base) {
  const shared = await loadShared(env, url);
  const dict = await loadDict(env, url, pickLocale(localeKeys(shared.catalog), site.locale)).catch(() => ({}));
  const lm = iso(site.published_at);
  return [...sitePageList({ site, target, dict, shared }).map((p) => [base + p.path, lm]), [base + "privacy", lm]];
}

// /sitemap.xml: on a custom domain the urlset of that site's pages; on the primary host a sitemap index (one entry per
// published site, from list_public_sites()) pointing at each site's own /a/<slug>/sitemap.xml (handleSiteSitemap),
// because which pages a site has is only known from its content.
export async function handleSitemap(request, env) {
  const url = new URL(request.url);
  const cfg = await loadConfig(env, url);
  if (url.host !== primaryHostOf(cfg, url)) {
    const site = normalizeSite(await rpc(cfg, "get_public_site_by_host", { host: url.hostname }));
    if (!site) return missing();
    return text(urlset(await sitePageEntries(env, url, site, { host: url.hostname }, `https://${url.hostname}/`)), XML, "public, max-age=300");
  }
  const rows = await rpc(cfg, "list_public_sites", {});
  if (!Array.isArray(rows)) throw new Error("list_public_sites: not an array");
  const base = (cfg.SITE_BASE_URL || url.origin).replace(/\/+$/, "");
  const entries = rows.filter((r) => r && typeof r.slug === "string" && SLUG.test(r.slug)).slice(0, SITEMAP_MAX).map((r) => [`${base}/a/${r.slug}/sitemap.xml`, iso(r.published_at)]);
  const body = `<?xml version="1.0" encoding="UTF-8"?>\n<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${entries.map(([loc, lm]) => `<sitemap><loc>${xml(loc)}</loc>${lm ? `<lastmod>${lm}</lastmod>` : ""}</sitemap>`).join("\n")}\n</sitemapindex>\n`;
  return text(body, XML, "public, max-age=300");
}

// /a/<slug>/sitemap.xml (primary host): the urlset of one site's pages, with the canonical base (SITE_BASE_URL).
export async function handleSiteSitemap(request, env) {
  const url = new URL(request.url);
  const cfg = await loadConfig(env, url);
  const slug = decodeURIComponent(url.pathname.split("/")[2] || "");
  if (url.host !== primaryHostOf(cfg, url) || !SLUG.test(slug)) return missing();
  const site = normalizeSite(await rpc(cfg, "get_public_site", { slug }));
  if (!site) return missing();
  const base = (cfg.SITE_BASE_URL || url.origin).replace(/\/+$/, "") + `/a/${slug}/`;
  return text(urlset(await sitePageEntries(env, url, site, { slug }, base)), XML, "public, max-age=300");
}

const PORTAL_PATH = /^(\/a\/[^/]+)?\/my(\/.*)?$/;
const JOIN_PATH = /^(\/a\/[^/]+)?\/join(\/.*)?$/;
// Page routes: /a/<slug>/…, and on a custom domain / and its page paths (shared/site-pages.json; which ones reach the
// Worker at all is wrangler.jsonc run_worker_first). A path with a dot (a file) is never a page.
const PAGE_PATH = /^\/(a\/.*|[a-z][a-z0-9_/-]*)?$/;
const SITE_SITEMAP = /^\/a\/[^/]+\/sitemap\.xml$/;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;
    const isGetHead = request.method === "GET" || request.method === "HEAD";
    if (path === "/config.js") return isGetHead ? handleConfig(request, env) : new Response(null, { status: 405, headers: headers({ allow: "GET, HEAD" }) });
    if (path === MANIFEST_PATH || path === "/robots.txt" || path === "/sitemap.xml" || SITE_SITEMAP.test(path)) {
      if (!isGetHead) return new Response(null, { status: 405, headers: headers({ allow: "GET, HEAD" }) });
      try {
        if (path === "/robots.txt") return handleRobots(request);
        return await (path === "/sitemap.xml" ? handleSitemap : SITE_SITEMAP.test(path) ? handleSiteSitemap : handleManifest)(request, env);
      } catch (e) {
        console.error(path, e && e.message);
        return unavailable503();
      }
    }
    // The customer portal: /a/<slug>/my[/...] and (custom domain) /my[/...]. GET, HEAD and POST; checked before the page routes.
    if (PORTAL_PATH.test(path)) return handlePortal(request, env);
    // The customer sign-up page: /a/<slug>/join[/...] and (custom domain) /join[/...]. GET, HEAD and POST.
    if (JOIN_PATH.test(path)) return handleJoin(request, env);
    if (isGetHead && PAGE_PATH.test(path)) return handlePage(request, env);
    return env.ASSETS.fetch(request);
  },
};
