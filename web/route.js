// Pure routing helpers (testable in node).
// -> { slug | host, page, portalPath? } | { platform: "privacy"|"terms" } | null.
// page is "privacy", "portal" (the customer login, DECISIONS #98) or undefined. portalPath is "" | "login" | "start" |
// "ask" | "request" | "logout" | "sw.js", or null for any other path under the portal base (a 404).
const PORTAL_PATHS = new Set(["", "login", "start", "ask", "request", "logout", "sw.js"]);
const JOIN_PATHS = new Set(["", "consent", "save", "submit", "done"]); // the customer sign-up page (DECISIONS #108); joinPath is null for any other path under it
const joinPathOf = (rest) => { const p = (rest || "").replace(/^\/+|\/+$/g, ""); return JOIN_PATHS.has(p) ? p : null; };
const portalPathOf = (rest) => { const p = (rest || "").replace(/^\/+|\/+$/g, ""); return PORTAL_PATHS.has(p) ? p : null; };

export function resolveTarget(loc, query, primaryHost) {
  if (loc.host === primaryHost) {
    const my = loc.pathname.match(/^\/a\/([^/]+)\/my(\/.*)?$/);
    if (my) return { slug: decodeURIComponent(my[1]), page: "portal", portalPath: portalPathOf(my[2]) };
    const jn = loc.pathname.match(/^\/a\/([^/]+)\/join(\/.*)?$/);
    if (jn) return { slug: decodeURIComponent(jn[1]), page: "join", joinPath: joinPathOf(jn[2]) };
    const m = loc.pathname.match(/^\/a\/([^/]+?)(\/privacy)?\/?$/);
    const slug = m ? decodeURIComponent(m[1]) : query.get("s");
    if (slug) return { slug, page: (m && m[2]) || query.get("page") === "privacy" ? "privacy" : undefined };
    const p = loc.pathname.match(/^\/(privacy|terms)\/?$/);
    return p ? { platform: p[1] } : null;
  }
  const my = loc.pathname.match(/^\/my(\/.*)?$/);
  if (my) return { host: loc.hostname, page: "portal", portalPath: portalPathOf(my[1]) };
  const jh = loc.pathname.match(/^\/join(\/.*)?$/);
  if (jh) return { host: loc.hostname, page: "join", joinPath: joinPathOf(jh[1]) };
  return { host: loc.hostname, page: /^\/privacy\/?$/.test(loc.pathname) ? "privacy" : undefined };
}

// Links between a site and its privacy page; /a/<slug>[/privacy] on the primary host, ?s=<slug>&page=privacy as the local fallback, /privacy on a custom domain.
export function siteLinks(target, loc = globalThis.location) {
  if (target.host) return { home: "/", privacy: "/privacy" };
  if (loc.pathname.startsWith("/a/")) { const b = "/a/" + encodeURIComponent(target.slug); return { home: b + "/", privacy: b + "/privacy" }; }
  const u = new URLSearchParams(loc.search); u.set("s", target.slug); u.delete("page");
  const home = "?" + u; u.set("page", "privacy");
  return { home, privacy: "?" + u };
}

// The customer portal's links (server-rendered only, so always the path form). base is also the cookie Path.
export function portalLinks(target) {
  const site = target.host ? "/" : "/a/" + encodeURIComponent(target.slug) + "/";
  const base = site + "my";
  return { base, home: base, login: base + "/login", start: base + "/start", ask: base + "/ask", request: base + "/request", logout: base + "/logout", sw: base + "/sw.js", site, privacy: site + "privacy" };
}
