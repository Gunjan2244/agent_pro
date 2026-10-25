import { hero } from "./hero.js";
import { about } from "./about.js";
import { services } from "./services.js";
import { offers } from "./offers.js";
import { highlights } from "./highlights.js";
import { achievements } from "./achievements.js";
import { testimonials } from "./testimonials.js";
import { faq } from "./faq.js";
import { contact } from "./contact.js";
import { companies } from "./companies.js";
import { portalPromo, portalBase } from "./portal-promo.js";
import { footer } from "./footer.js";
import { esc, url, tel, wa, firstName } from "./util.js";
import { pickOffers, todayIST } from "./offer.js";

export const PARTIALS = { hero, about, companies, services, offers, highlights, achievements, testimonials, faq, portal: portalPromo, contact };
const DEFAULT_ORDER = Object.keys(PARTIALS).filter((k) => k !== "companies" && k !== "portal");

// "Companies I represent" (DECISIONS #90) comes from the payload, not from the agent's content, so an order that
// doesn't name it (every site today) gets it right after the template's anchor section (`after`: about on "Meet me",
// the trust strip on "Best offers"); without the anchor, before services, the FAQ or contact. An order that names it
// is followed, and content.hidden can hide it, like any section.
export function placeCompanies(order, after = "about") {
  if (order.includes("companies")) return order;
  const out = order.slice();
  let at = out.indexOf(after) + 1;
  if (!at) { const k = ["services", "faq", "contact"].find((x) => out.includes(x)); at = k ? out.indexOf(k) : out.length; }
  // never above the hero (an odd order may put the anchor first)
  at = Math.max(at, out.indexOf("hero") + 1);
  out.splice(at, 0, "companies");
  return out;
}

// "For my customers" (DECISIONS #102) is like companies: an order that doesn't name it gets it right before contact
// (or last, when contact isn't in the order either: contact is pinned and is appended after it). An order that names
// it is followed, and content.hidden can hide it.
export function placePortal(order) {
  if (order.includes("portal")) return order;
  const out = order.slice(), at = out.indexOf("contact");
  out.splice(at < 0 ? out.length : at, 0, "portal");
  return out;
}

// Section order + visibility from content.sectionOrder / content.hidden. Pinned sections
// (schema `pinned`) are always shown and always present, even if missing from the order.
export function visibleKeys(content, schema, opts = {}) {
  // the portal section exists only when the payload says the agent's customers have a portal (opts.portal)
  const known = new Set(Object.keys(PARTIALS).filter((k) => k !== "portal" || opts.portal === true));
  const pinned = new Set((schema.sections || []).filter((s) => s.pinned).map((s) => s.key));
  const hidden = new Set(Array.isArray(content.hidden) ? content.hidden : []);
  const order = placePortal(placeCompanies(Array.isArray(content.sectionOrder) ? content.sectionOrder : DEFAULT_ORDER, opts.companiesAfter));
  const seen = new Set();
  const out = [];
  for (const k of order) if (known.has(k) && !seen.has(k)) { seen.add(k); out.push(k); }
  for (const k of pinned) if (known.has(k) && !seen.has(k)) { if (k === "hero") out.unshift(k); else out.push(k); }
  return out.filter((k) => pinned.has(k) || !hidden.has(k));
}

// tokens = { accent, schema, catalog, notice?, privacyHref?, family? ("personal" | "offers", from the templates
// registry), images? (web/images/manifest.json), seed? (slug or host: picks the hero scene), today? (YYYY-MM-DD) }
// opts.layout is the template's own layout for the offers section ("grid" | "needs"); opts.companiesAfter is the
// section the companies follow when the order doesn't name them (placeCompanies).
export function makeCtx(site, t, tokens, variant, opts = {}) {
  const content = site.content || {}, profile = site.profile || {};
  const ctx = {
    t, variant, site, profile, schema: tokens.schema || { sections: [] },
    catalog: tokens.catalog || { interests: [] }, accent: tokens.accent,
    // privacy: link to the site's privacy page + the enquiry_form notice's consent line / link keys (first two textKeys)
    privacyHref: tokens.privacyHref || "#", agentName: profile.full_name || "",
    consentKey: ((tokens.notice || {}).textKeys || [])[0] || "privacy.enquiry_form.consent",
    consentLinkKey: ((tokens.notice || {}).textKeys || [])[1] || "privacy.enquiry_form.link",
    family: tokens.family === "offers" ? "offers" : "personal", layout: opts.layout || "grid",
    images: tokens.images && typeof tokens.images === "object" ? tokens.images : {},
    seed: tokens.seed || "", today: tokens.today || todayIST(), locale: site.locale || "en",
    photo: url((content.hero || {}).photo) || url(profile.photo_url),
    firstName: firstName(profile.full_name),
    phone: (content.contact || {}).phone || profile.phone || "",
    portal: site.portal === true,
  };
  ctx.portalBase = portalBase(ctx.portal, ctx.privacyHref);
  ctx.visible = new Set(visibleKeys(content, ctx.schema, { companiesAfter: opts.companiesAfter, portal: ctx.portal }));
  ctx.offers = pickOffers(site, ctx);
  return ctx;
}

// Sticky Call / WhatsApp bar for phones (both families), only when the numbers exist. It is position:fixed from
// the first paint and `.tpl.has-bar` reserves its height at the bottom, so it never shifts the layout (CLS 0).
function callbar(ctx) {
  const c = (ctx.site.content || {}).contact || {};
  const call = tel(ctx.phone), chat = wa(c.whatsapp);
  if (!call && !chat) return "";
  return `<nav class="callbar" aria-label="${esc(ctx.t("section.contact"))}">${call ? `<a class="btn" href="${call}">${esc(ctx.t("site.call"))}</a>` : ""}${chat ? `<a class="btn ghost" href="${chat}" rel="noopener">${esc(ctx.t("site.whatsapp"))}</a>` : ""}</nav>`;
}

// opts.wrap(key, html, ctx) lets a template change the markup around a section; opts.partials = {key: fn(d, ctx)}
// replaces a section's partial with the template's own arrangement (DECISIONS #85); opts.layout see makeCtx.
export function compose(site, t, tokens, variant, opts = {}) {
  const ctx = makeCtx(site, t, tokens, variant, opts);
  const content = site.content || {};
  // Landmarks: the hero is the page <header> (its <h1> is the only one); every other section is a <section id> with its own <h2>.
  const wrap = opts.wrap || ((k, h) => (k === "hero" ? `<header class="sec sec-hero" id="hero">${h}</header>` : `<section class="sec sec-${k}" id="${k}">${h}</section>`));
  const body = [...ctx.visible]
    .map((k) => { const h = ((opts.partials && opts.partials[k]) || PARTIALS[k])(content[k] || {}, ctx); return h ? wrap(k, h, ctx) : ""; })
    .join("\n");
  const bar = callbar(ctx);
  return `<div class="tpl tpl-${variant} fam-${ctx.family}${bar ? " has-bar" : ""}" style="--accent:${/^#[0-9a-fA-F]{6}$/.test(tokens.accent) ? tokens.accent : "var(--c-primary)"}">${opts.before ? opts.before(ctx) : ""}${body}${footer(ctx)}${bar}</div>`;
}
