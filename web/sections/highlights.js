import { esc, has, arr } from "./util.js";
import { icon } from "./icons.js";
export function highlights(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.highlights"))}</h2><ul class="cards">${items
    .map((i) => `<li class="card">${icon(i.icon)}<h3>${esc(i.title)}</h3>${has(i.text) ? `<p>${esc(i.text)}</p>` : ""}</li>`).join("")}</ul>`;
}
