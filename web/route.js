// Pure routing helpers (testable in node).
// -> { slug | host, page, path?, portalPath?, joinPath? } | { platform: "privacy"|"terms" } | null.
// page is "privacy", "portal" (the customer login, DECISIONS #98), "join" (DECISIONS #108) or undefined for a site
// page; then `path` is the page's path under the site's base ("" = home, "about", "insurance/term", …; a trailing
// slash is kept so the Worker can 301 it). Which paths are pages is shared/site-pages.json (matchPage, DECISIONS #122).
// portalPath is "" | "login" | "start" | "ask" | "request" | "logout" | "sw.js", or null for any other path under the
// portal base (a 404).
const PORTAL_PATHS = new Set(["", "login", "start", "ask", "request", "logout", "sw.js"]);
const JOIN_PATHS = new Set(["", "consent", "save", "submit", "done"]); // the customer sign-up page (DECISIONS #108); joinPath is null for any other path under it
const joinPathOf = (rest) => { const p = (rest || "").replace(/^\/+|\/+$/g, ""); return JOIN_PATHS.has(p) ? p : null; };
const portalPathOf = (rest) => { const p = (rest || "").replace(/^\/+|\/+$/g, ""); return PORTAL_PATHS.has(p) ? p : null; };
const safeDecode = (s) => { try { return decodeURIComponent(s); } catch { return "\u0000"; } };
const sitePathOf = (rest) => { const p = String(rest || "").replace(/^\/+/, ""); return /^privacy\/?$/.test(p) ? { page: "privacy" } : { page: undefined, path: safeDecode(p) }; };

export function resolveTarget(loc, query, primaryHost) {
  if (loc.host === primaryHost) {
    const my = loc.pathname.match(/^\/a\/([^/]+)\/my(\/.*)?$/);
    if (my) return { slug: decodeURIComponent(my[1]), page: "portal", portalPath: portalPathOf(my[2]) };
    const jn = loc.pathname.match(/^\/a\/([^/]+)\/join(\/.*)?$/);
    if (jn) return { slug: decodeURIComponent(jn[1]), page: "join", joinPath: joinPathOf(jn[2]) };
    const m = loc.pathname.match(/^\/a\/([^/]+)(\/.*)?$/);
    if (m) return { slug: safeDecode(m[1]), ...sitePathOf(m[2]) };
    // the local fallback and the mock: ?s=<slug>&page=<path> (page=privacy is the privacy page)
    const slug = query.get("s");
    if (slug) return { slug, ...sitePathOf(query.get("page") || "") };
    const p = loc.pathname.match(/^\/(privacy|terms)\/?$/);
    return p ? { platform: p[1] } : null;
  }
  const my = loc.pathname.match(/^\/my(\/.*)?$/);
  if (my) return { host: loc.hostname, page: "portal", portalPath: portalPathOf(my[1]) };
  const jh = loc.pathname.match(/^\/join(\/.*)?$/);
  if (jh) return { host: loc.hostname, page: "join", joinPath: joinPathOf(jh[1]) };
  return { host: loc.hostname, ...sitePathOf(loc.pathname) };
}

// Links between a site and its privacy page; /a/<slug>[/privacy] on the primary host, ?s=<slug>&page=privacy as the local fallback, /privacy on a custom domain.
export function siteLinks(target, loc = globalThis.location) {
  if (target.host) return { home: "/", privacy: "/privacy" };
  if (loc.pathname.startsWith("/a/")) { const b = "/a/" + encodeURIComponent(target.slug); return { home: b + "/", privacy: b + "/privacy" }; }
  const u = new URLSearchParams(loc.search); u.set("s", target.slug); u.delete("page"); u.delete("interest");
  const home = "?" + u; u.set("page", "privacy");
  return { home, privacy: "?" + u };
}

// The customer portal's links (server-rendered only, so always the path form). base is also the cookie Path.
export function portalLinks(target) {
  const site = target.host ? "/" : "/a/" + encodeURIComponent(target.slug) + "/";
  const base = site + "my";
  return { base, home: base, login: base + "/login", start: base + "/start", ask: base + "/ask", request: base + "/request", logout: base + "/logout", sw: base + "/sw.js", site, privacy: site + "privacy" };
}

// ---- site pages (DECISIONS #122; the definitions are shared/site-pages.json) ----
// A page path's placeholders: {interest} a catalog interest key (a plan type), {plan} a plan's slug (DECISIONS #130:
// company_plans.slug), {doc} one of the documents a plan page links to (DOCS, the "We're taking you to …" page).
const PLAN_KEY = /^[a-z][a-z0-9_]*$/;
const SLUG_RE = /^[a-z0-9]+(-[a-z0-9]+)*$/;
export const DOCS = ["plan", "brochure", "policy", "cis", "calculator", "features"];
const HOLE = { interest: (v) => PLAN_KEY.test(v), plan: (v) => SLUG_RE.test(v) && v.length <= 80, doc: (v) => DOCS.includes(v) };
// -> { key, def, path, plan?, slug?, doc? } for a page path ("" = home, "about", "insurance/term",
// "insurance/term/lics-digi-term", "go/lics-digi-term/brochure"), or null when it names no page. `plan` is the plan
// type (interest key), `slug` the plan, `doc` the document. Only the syntax is checked here; whether the page exists
// for a site (a plan type it offers, a plan it shows) is decided by the renderer (sections/index.js), which renders
// "" for a page that doesn't exist.
export function matchPage(defs, path) {
  const p = String(path ?? "");
  for (const def of (defs && defs.pages) || []) {
    if (!def.path.includes("{")) { if (def.path === p) return { key: def.key, def, path: p }; continue; }
    const want = def.path.split("/"), got = p.split("/");
    if (want.length !== got.length) continue;
    const out = { key: def.key, def, path: p };
    let ok = true;
    want.forEach((w, i) => {
      const m = /^\{(interest|plan|doc)\}$/.exec(w);
      if (!m) { ok = ok && w === got[i]; return; }
      if (!HOLE[m[1]](got[i])) { ok = false; return; }
      out[m[1] === "interest" ? "plan" : m[1] === "plan" ? "slug" : "doc"] = got[i];
    });
    if (ok) return out;
  }
  return null;
}
export const planPath = (def, key, slug = "", doc = "") => def.path.replace("{interest}", key).replace("{plan}", slug).replace("{doc}", doc);

// How a site's pages link to each other, derived from its privacy link (like portalBase): "/a/<slug>/privacy" or
// "/privacy" -> path links; "?…&page=privacy" (the local ?s= fallback, ?mock) -> query links; "#" (the in-app preview,
// no target) -> hash links ("#p=<path>") that client.js turns into a re-render of the draft, never a network fetch.
export function linksFrom(privacyHref) {
  const h = String(privacyHref || "#");
  if (h.startsWith("/")) return { mode: "path", base: h.replace(/privacy$/, "") };
  if (h.startsWith("?")) { const u = new URLSearchParams(h.slice(1)); u.delete("page"); u.delete("interest"); return { mode: "query", base: u.toString() }; }
  return { mode: "hash", base: "" };
}
// The URL of a page (path "" = home); `interest` preselects the enquiry form on the contact page, `anchor` jumps to an id.
export function pageUrl(L, path = "", { interest = "", anchor = "" } = {}) {
  const i = interest ? encodeURIComponent(interest) : "";
  if (L.mode === "path") return L.base + path + (i ? "?interest=" + i : "") + (anchor ? "#" + anchor : "");
  if (L.mode === "query") {
    const u = new URLSearchParams(L.base);
    if (path) u.set("page", path);
    if (interest) u.set("interest", interest);
    return "?" + u + (anchor ? "#" + anchor : "");
  }
  return "#p=" + path + (i ? "&interest=" + i : "");
}
// The in-app preview's hash ("#p=about&interest=term") -> { path, interest }, or null for any other hash.
export function previewHash(hash) {
  const m = /^#p=([^&]*)(?:&interest=([a-z0-9_]*))?$/.exec(String(hash || ""));
  return m ? { path: safeDecode(m[1]), interest: m[2] || "" } : null;
}
