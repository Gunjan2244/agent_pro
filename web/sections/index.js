import { hero } from "./hero.js";
import { about } from "./about.js";
import { services } from "./services.js";
import { highlights } from "./highlights.js";
import { achievements } from "./achievements.js";
import { testimonials } from "./testimonials.js";
import { faq } from "./faq.js";
import { contact } from "./contact.js";
import { footer } from "./footer.js";

export const PARTIALS = { hero, about, services, highlights, achievements, testimonials, faq, contact };
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

export function makeCtx(site, t, tokens, variant) {
  return {
    t, variant, site, profile: site.profile || {}, schema: tokens.schema || { sections: [] },
    catalog: tokens.catalog || { interests: [] }, accent: tokens.accent,
  };
}

// opts.wrap(key, html, ctx) lets a template change the markup around a section.
export function compose(site, t, tokens, variant, opts = {}) {
  const ctx = makeCtx(site, t, tokens, variant);
  const content = site.content || {};
  const wrap = opts.wrap || ((k, h) => `<section class="sec sec-${k}" id="${k}">${h}</section>`);
  const body = visibleKeys(content, ctx.schema)
    .map((k) => { const h = PARTIALS[k](content[k] || {}, ctx); return h ? wrap(k, h, ctx) : ""; })
    .join("\n");
  return `<div class="tpl tpl-${variant}" style="--accent:${/^#[0-9a-fA-F]{6}$/.test(tokens.accent) ? tokens.accent : "var(--c-primary)"}">${opts.before ? opts.before(ctx) : ""}${body}${footer(ctx)}</div>`;
}
