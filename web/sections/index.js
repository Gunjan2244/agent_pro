import { hero } from "./hero.js";
import { about } from "./about.js";
import { services } from "./services.js";
import { offers } from "./offers.js";
import { highlights } from "./highlights.js";
import { achievements } from "./achievements.js";
import { testimonials } from "./testimonials.js";
import { faq } from "./faq.js";
import { contact } from "./contact.js";
import { footer } from "./footer.js";
import { esc, url, tel, wa, firstName } from "./util.js";
import { pickOffers, todayIST } from "./offer.js";

export const PARTIALS = { hero, about, services, offers, highlights, achievements, testimonials, faq, contact };
const DEFAULT_ORDER = Object.keys(PARTIALS);

// Section order + visibility from content.sectionOrder / content.hidden. Pinned sections
// (schema `pinned`) are always shown and always present, even if missing from the order.
export function visibleKeys(content, schema) {
  const known = new Set(DEFAULT_ORDER);
  const pinned = new Set((schema.sections || []).filter((s) => s.pinned).map((s) => s.key));
  const hidden = new Set(Array.isArray(content.hidden) ? content.hidden : []);
  const order = Array.isArray(content.sectionOrder) ? content.sectionOrder : DEFAULT_ORDER;
  const seen = new Set();
  const out = [];
  for (const k of order) if (known.has(k) && !seen.has(k)) { seen.add(k); out.push(k); }
  for (const k of pinned) if (known.has(k) && !seen.has(k)) { if (k === "hero") out.unshift(k); else out.push(k); }
  return out.filter((k) => pinned.has(k) || !hidden.has(k));
}

// tokens = { accent, schema, catalog, notice?, privacyHref?, family? ("personal" | "offers", from the templates
// registry), images? (web/images/manifest.json), seed? (slug or host: picks the hero scene), today? (YYYY-MM-DD) }
// opts.layout is the template's own layout for the offers section ("grid" | "needs").
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
  };
  ctx.visible = new Set(visibleKeys(content, ctx.schema));
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

// opts.wrap(key, html, ctx) lets a template change the markup around a section; opts.layout see makeCtx.
export function compose(site, t, tokens, variant, opts = {}) {
  const ctx = makeCtx(site, t, tokens, variant, opts);
  const content = site.content || {};
  // Landmarks: the hero is the page <header> (its <h1> is the only one); every other section is a <section id> with its own <h2>.
  const wrap = opts.wrap || ((k, h) => (k === "hero" ? `<header class="sec sec-hero" id="hero">${h}</header>` : `<section class="sec sec-${k}" id="${k}">${h}</section>`));
  const body = [...ctx.visible]
    .map((k) => { const h = PARTIALS[k](content[k] || {}, ctx); return h ? wrap(k, h, ctx) : ""; })
    .join("\n");
  const bar = callbar(ctx);
  return `<div class="tpl tpl-${variant} fam-${ctx.family}${bar ? " has-bar" : ""}" style="--accent:${/^#[0-9a-fA-F]{6}$/.test(tokens.accent) ? tokens.accent : "var(--c-primary)"}">${opts.before ? opts.before(ctx) : ""}${body}${footer(ctx)}${bar}</div>`;
}
