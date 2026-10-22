import { esc, has, arr, interestLabel } from "./util.js";
import { interestPic } from "./media.js";
// The services to show: items with a known interest, plus offered interests that have no item (each key once).
export function serviceList(d, ctx) {
  const items = arr(d.items).filter((i) => i && interestLabel(ctx, i.interest));
  const offered = arr(d.offered).filter((k) => interestLabel(ctx, k));
  const shown = new Set(items.map((i) => i.interest));
  return { items, extra: [...new Set(offered.filter((k) => !shown.has(k)))] };
}
// Shared (classic, spotlight, guide): one card per service, each with its plan-type thumbnail; an offered interest
// without a description is a card too, so the grid has no orphan chips.
export function services(d, ctx) {
  const { items, extra } = serviceList(d, ctx);
  const rows = [...items, ...extra.map((k) => ({ interest: k }))];
  if (!rows.length) return "";
  const head = `<h2>${esc(ctx.t("site.services_heading"))}</h2>`;
  // The offers section already showed plan cards with these descriptions: list the interests compactly.
  if (ctx.plansShown) return `${head}<ul class="tags">${rows.map((i) => `<li>${esc(interestLabel(ctx, i.interest))}</li>`).join("")}</ul>`;
  // A small thumbnail beside the text (docs/IMAGES.md slot 3), so the section stays short on phones.
  return `${head}
<ul class="svcs">${rows.map((i) => { const img = interestPic(ctx, i.interest, { sizes: "96px" }); return `<li class="card svc${img ? " has-pic" : ""}">${img}<div class="svc-b"><h3>${esc(interestLabel(ctx, i.interest))}</h3>${has(i.description) ? `<p>${esc(i.description)}</p>` : ""}</div></li>`; }).join("")}</ul>`;
}
