// "For my customers" (DECISIONS #102): tells visitors that the agent's customers can sign in and see their policies.
// Field-less, like companies: the copy is i18n-only (site.portal_*), the section comes from the payload's `portal`
// flag (ctx.portal), is hidden with content.hidden like any section, and renders nothing while the heading key is
// missing from the dictionary. The sign-in link needs the portal base (ctx.portalBase: /a/<slug>/my on the primary
// host, /my on a custom host); on the ?s= local fallback there is none, so the links are left out. The in-app preview
// (renderSite, no target: privacyHref "#") draws them as inert "#" links so the agent sees what customers will see.
import { esc, wa, tenDigits } from "./util.js";
import { icon } from "./icons.js";

export const PORTAL_HEADING = "site.portal_heading";
const POINTS = [
  ["site.portal_point_policies", "description"],
  ["site.portal_point_dues", "event_upcoming"],
  ["site.portal_point_payments", "payments"],
  ["site.portal_point_ask", "chat"],
];

// The portal's base path, from the site's privacy URL (its sibling, like route.js portalLinks); "" when the portal is
// off or the URL isn't a path (the ?s= fallback); "#" in the in-app preview (no target, privacyHref "#").
export const portalBase = (portal, privacyHref) => {
  if (portal !== true) return "";
  if (privacyHref === "#") return "#";
  return /^\/.*privacy$/.test(privacyHref || "") ? privacyHref.slice(0, -7) + "my" : "";
};

// A translation, or "" while the dictionary lacks it (never a raw key on a site).
const tr = (ctx, key) => { const s = ctx.t(key); return s && s !== key ? s : ""; };

export const portalLogin = (ctx) => (ctx.portalBase === "#" ? "#" : ctx.portalBase ? ctx.portalBase + "/login" : "");

// F4: the small "Customer login" text link at the top of the hero (a <p>, not a nav: the call bar stays the only <nav>).
export function heroLogin(ctx) {
  const href = portalLogin(ctx), label = tr(ctx, "site.header_portal");
  return href && label ? `<p class="hero-login"><a href="${esc(href)}" rel="nofollow">${esc(label)}</a></p>` : "";
}

export function portalPromo(d, ctx) {
  const h = tr(ctx, PORTAL_HEADING);
  if (ctx.portal !== true || !h) return "";
  const { t } = ctx, href = portalLogin(ctx);
  const c = (ctx.site.content || {}).contact || {};
  const chat = tenDigits(c.whatsapp) ? wa(c.whatsapp) : wa(ctx.phone);
  const ask = href && chat ? `${chat}?text=${encodeURIComponent(t("portal.ask_link_message", { name: ctx.agentName }))}` : "";
  const first = tr(ctx, "site.portal_first_time");
  return `<div class="pp"><div class="pp-head"><h2>${esc(h)}</h2><p class="pp-lead">${esc(tr(ctx, "site.portal_lead"))}</p></div>
<ul class="pp-pts">${POINTS.map(([k, i]) => `<li>${icon(i)}<span>${esc(tr(ctx, k))}</span></li>`).join("")}</ul>${href ? `
<p class="pp-go"><a class="btn" href="${esc(href)}" rel="nofollow">${esc(tr(ctx, "site.portal_sign_in"))}</a></p>` : ""}${ask && first ? `
<p class="pp-first"><a href="${esc(ask)}" rel="noopener">${esc(first)}</a></p>` : ""}</div>`;
}
