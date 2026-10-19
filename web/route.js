// Pure routing helpers (testable in node).
// -> { slug | host, page } | { platform: "privacy"|"terms" } | null. page is "privacy" or undefined.
export function resolveTarget(loc, query, primaryHost) {
  if (loc.host === primaryHost) {
    const m = loc.pathname.match(/^\/a\/([^/]+?)(\/privacy)?\/?$/);
    const slug = m ? decodeURIComponent(m[1]) : query.get("s");
    if (slug) return { slug, page: (m && m[2]) || query.get("page") === "privacy" ? "privacy" : undefined };
    const p = loc.pathname.match(/^\/(privacy|terms)\/?$/);
    return p ? { platform: p[1] } : null;
  }
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

