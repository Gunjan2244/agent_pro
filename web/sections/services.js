import { esc, has, arr, interestLabel } from "./util.js";
export function services(d, ctx) {
  const items = arr(d.items).filter((i) => i && interestLabel(ctx, i.interest));
  const offered = arr(d.offered).filter((k) => interestLabel(ctx, k));
  if (!items.length && !offered.length) return "";
  const cards = items.map((i) => `<li class="card"><h3>${esc(interestLabel(ctx, i.interest))}</h3>${has(i.description) ? `<p>${esc(i.description)}</p>` : ""}</li>`).join("");
  const shown = new Set(items.map((i) => i.interest));
  const chips = offered.filter((k) => !shown.has(k)).map((k) => `<li>${esc(interestLabel(ctx, k))}</li>`).join("");
  return `<h2>${esc(ctx.t("site.services_heading"))}</h2>
${cards ? `<ul class="cards">${cards}</ul>` : ""}${chips ? `<ul class="chips">${chips}</ul>` : ""}`;
}
