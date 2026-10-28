import { esc, has, arr, interestLabel } from "./util.js";
import { interestPic } from "./media.js";
import { planHref } from "./links.js";
import { seePlans } from "./pages.js";
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
  // The offers section already showed plan cards with these descriptions: list the interests compactly (on a
  // multi-page site, as chips to their plan pages).
  if (ctx.plansShown) return `${head}${tagList(rows.map((i) => i.interest), ctx)}`;
  // A small thumbnail beside the text (docs/IMAGES.md slot 3), so the section stays short on phones; on a multi-page
  // site each card links to its plan type's page (DECISIONS #122).
  return `${head}
<ul class="svcs${ctx.pages ? " stg" : ""}">${rows.map((i) => { const img = interestPic(ctx, i.interest, { sizes: "96px" }); return `<li class="card svc${img ? " has-pic" : ""}">${img}<div class="svc-b"><h3>${esc(interestLabel(ctx, i.interest))}</h3>${has(i.description) ? `<p>${esc(i.description)}</p>` : ""}${seePlans(ctx, i.interest)}</div></li>`; }).join("")}</ul>`;
}
// Interest labels as static tags, or (multi-page) chips to the plan pages.
export function tagList(keys, ctx) {
  const links = keys.map((k) => planHref(ctx, k));
  if (links.some(Boolean)) return `<ul class="qr">${keys.map((k, n) => (links[n] ? `<li><a class="chip" href="${esc(links[n])}">${esc(interestLabel(ctx, k))}</a></li>` : `<li><span class="chip">${esc(interestLabel(ctx, k))}</span></li>`)).join("")}</ul>`;
  return `<ul class="tags">${keys.map((k) => `<li>${esc(interestLabel(ctx, k))}</li>`).join("")}</ul>`;
}
