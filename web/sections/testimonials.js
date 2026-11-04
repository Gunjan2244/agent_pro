import { esc, has, arr } from "./util.js";
// The quotes to show: those with a quote and a name. No faces for customers, ever (docs/IMAGES.md): text only.
export const quoteItems = (d) => arr(d.items).filter((i) => i && has(i.quote) && has(i.name));
export function testimonials(d, ctx) {
  const items = quoteItems(d);
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.testimonials"))}</h2><ul class="cards quotes">${items
    .map((i) => `<li class="card"><blockquote>${esc(i.quote)}</blockquote><p class="meta">${esc(i.name)}${has(i.city) ? ", " + esc(i.city) : ""}</p></li>`).join("")}</ul>`;
}
