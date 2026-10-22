import { esc, has, arr } from "./util.js";
// No faces for customers, ever (docs/IMAGES.md): quotes are text only.
export function testimonials(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.quote) && has(i.name));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.testimonials"))}</h2><ul class="cards quotes">${items
    .map((i) => `<li class="card"><blockquote>${esc(i.quote)}</blockquote><p class="meta">${esc(i.name)}${has(i.city) ? ", " + esc(i.city) : ""}</p></li>`).join("")}</ul>`;
}
