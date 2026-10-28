// Render-only helpers (never loaded by hydrate.js): bundled illustrations, the initials monogram, FNV-1a.
import { esc } from "./util.js";

// 32-bit FNV-1a over the UTF-8 bytes (the same function as SiteSeed.fnv1a32; SHARED_DEFINITIONS.md).
export function fnv1a32(value) {
  let h = 0x811c9dc5;
  for (const b of new TextEncoder().encode(String(value))) { h ^= b; h = Math.imul(h, 0x01000193) >>> 0; }
  return h >>> 0;
}
// Up to two initials for the monogram (first letters of the first and last words).
export function initials(name) {
  const w = String(name ?? "").trim().split(/\s+/).filter(Boolean);
  if (!w.length) return "";
  const first = (s) => Array.from(s)[0] || "";
  return (first(w[0]) + (w.length > 1 ? first(w[w.length - 1]) : "")).toUpperCase();
}
// A bundled illustration (docs/IMAGES.md): drawn only when `name` is listed in web/images/manifest.json
// (ctx.images = {name: {w, h, sizes}}). Always lazy, async, explicit size; alt "" unless given.
export function pic(ctx, name, { alt = "", sizes = "(min-width: 720px) 400px, 100vw", cls = "pic", low = false, eager = false } = {}) {
  const m = ctx.images && /^[a-z0-9-]+$/.test(String(name || "")) ? ctx.images[name] : null;
  const ws = m && Array.isArray(m.sizes) ? m.sizes.filter((w) => Number.isInteger(w) && w > 0) : [];
  if (!m || !ws.length || !(m.w > 0) || !(m.h > 0)) return "";
  const src = (w) => `/images/${name}-${w}.webp`;
  return `<img class="${cls}" src="${src(ws[ws.length - 1])}" srcset="${ws.map((w) => `${src(w)} ${w}w`).join(", ")}" sizes="${sizes}" width="${m.w}" height="${m.h}" alt="${esc(alt)}"${eager ? "" : ' loading="lazy"'} decoding="async"${low ? ' fetchpriority="low"' : ""}>`;
}
// The catalog's image name for an interest, and its translated alt text ("{interest} illustration"). An interest
// whose own illustration isn't shipped yet (not in the manifest; e.g. motor, home, travel, business, accident until
// they are generated, docs/IMAGES.md) gets the generic one, the image of the interest with no insurance type (other),
// with alt="" since it doesn't show that interest.
export function interestPic(ctx, key, opts = {}) {
  const list = ctx.catalog.interests || [];
  const i = list.find((x) => x.key === key);
  if (!i || !i.image) return "";
  const own = pic(ctx, i.image, { alt: ctx.t("site.image_alt_interest", { interest: ctx.t(i.labelKey) }), ...opts });
  if (own) return own;
  const generic = list.find((x) => x.insuranceType === null && x.image && x.image !== i.image);
  return generic ? pic(ctx, generic.image, { ...opts, alt: "" }) : "";
}
