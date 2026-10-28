// Links between a site's pages (DECISIONS #122). ctx.pages is the list of pages this site has (sections/pages.js) and
// ctx.links how they link (route.js linksFrom: path, query or the in-app preview's hash). Without pages (a render
// without tokens.sitePages: unit tests of the partials) everything stays on one page and these return the old
// in-page anchors. Render-only.
import { pageUrl } from "../route.js";

const find = (ctx, f) => (Array.isArray(ctx.pages) ? ctx.pages.find(f) : null);
// A page by key ("insurance", "about", "contact", "home"); "" when the site doesn't have it.
export const pageHref = (ctx, key, opts) => { const p = find(ctx, (x) => x.key === key && !x.plan); return p ? pageUrl(ctx.links, p.path, opts) : ""; };
// A plan type's own page ("" when the site has none for it).
export const planHref = (ctx, key) => { const p = find(ctx, (x) => x.plan === key); return p ? pageUrl(ctx.links, p.path) : ""; };
// The enquiry: the contact page (with the interest preselected and the form in view), or #contact on one page.
export const contactHref = (ctx, interest = "") =>
  Array.isArray(ctx.pages) ? pageHref(ctx, "contact", interest ? { interest, anchor: "enq" } : {}) || "#contact" : "#contact";
