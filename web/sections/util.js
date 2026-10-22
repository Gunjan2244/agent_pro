// Escaping and small helpers. Every agent-supplied string goes through esc()/url().
const MAP = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;", "`": "&#96;" };
export const esc = (v) => (v == null ? "" : String(v)).replace(/[&<>"'`]/g, (c) => MAP[c]);
// Only http(s) URLs are allowed in href/src.
export const url = (v) => {
  const s = String(v ?? "").trim();
  return /^https?:\/\/[^\s"'<>]+$/i.test(s) ? esc(s) : "";
};
export const digits = (v) => String(v ?? "").replace(/\D/g, "");
export const tenDigits = (v) => { const d = digits(v); return d.length >= 10 ? d.slice(-10) : ""; };
export const tel = (v) => { const d = tenDigits(v); return d ? `tel:+91${d}` : ""; };
export const wa = (v) => { const d = tenDigits(v); return d ? `https://wa.me/91${d}` : ""; };
export const mail = (v) => (/^[^\s@<>"']+@[^\s@<>"']+\.[^\s@<>"']+$/.test(String(v ?? "")) ? `mailto:${esc(v)}` : "");
export const firstName = (n) => String(n ?? "").trim().split(/\s+/)[0] || "";
export const arr = (v) => (Array.isArray(v) ? v : []);
export const has = (v) => v != null && String(v).trim() !== "";

// site-schema.json lookups (sections are rendered from schema definitions).
export function field(schema, section, key) {
  const s = (schema.sections || []).find((x) => x.key === section);
  return s && (s.fields || []).find((f) => f.key === key);
}
export function optionLabel(ctx, section, fieldKey, itemKey, optKey) {
  let f = field(ctx.schema, section, fieldKey);
  if (f && itemKey) f = (f.itemFields || []).find((x) => x.key === itemKey);
  const o = f && (f.options || []).find((x) => x.key === optKey);
  return o ? ctx.t(o.labelKey) : optKey;
}
// The enquiry form's "Interested in" options (DECISIONS #93): the interests the site offers (services items and
// `offered`), those of the offers on the page (so an offer's Enquire link can preselect its interest), and the
// interests of no insurance type (`other`), which are always available; in catalog order, deprecated ones left out.
// A site that names none of its own gets every active interest, as before.
export function formInterests(ctx) {
  const all = arr(ctx.catalog.interests).filter((i) => i && !i.deprecated).slice().sort((a, b) => a.order - b.order);
  const sv = (ctx.site && ctx.site.content && ctx.site.content.services) || {};
  const own = new Set([...arr(sv.offered), ...arr(sv.items).map((i) => i && i.interest)]);
  const o = ctx.offers || {};
  for (const x of [...arr(o.grid), ...(o.hero ? [o.hero] : [])]) { const k = x && (x.interest ?? (x.cta && x.cta.interest)); if (k) own.add(k); }
  if (!all.some((i) => own.has(i.key))) return all;
  return all.filter((i) => own.has(i.key) || i.insuranceType == null);
}
export const interestLabel = (ctx, key) => {
  const i = (ctx.catalog.interests || []).find((x) => x.key === key);
  return i ? ctx.t(i.labelKey) : "";
};
// Text with default: schema `defaultKey` gives the i18n key used when the agent left it empty.
export function textOr(ctx, section, key, value) {
  if (has(value)) return value;
  const f = field(ctx.schema, section, key);
  return f && f.defaultKey ? ctx.t(f.defaultKey) : "";
}

// Only https URLs (offer CTA links, uploaded offer images).
export const httpsUrl = (v) => {
  const s = String(v ?? "").trim();
  return /^https:\/\/[^\s"'<>]+$/i.test(s) ? esc(s) : "";
};
