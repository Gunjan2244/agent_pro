import { esc, has, arr } from "./util.js";
import { icon } from "./icons.js";
import { stats } from "./about.js";
export function highlights(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  const head = `<h2>${esc(ctx.t("section.highlights"))}</h2>`;
  // Offers family: a compact trust strip (years and clients only when set, plus the highlight titles).
  if (ctx.family === "offers") {
    const st = stats((ctx.site.content || {}).about || {}, ctx);
    if (!items.length && !st.length) return "";
    return `${head}<ul class="trust">${st.map((s) => `<li class="stat">${s}</li>`).join("")}${items.map((i) => `<li>${icon(i.icon)}<span>${esc(i.title)}</span></li>`).join("")}</ul>`;
  }
  if (!items.length) return "";
  return `${head}<ul class="cards hl">${items
    .map((i) => `<li class="card">${icon(i.icon)}<h3>${esc(i.title)}</h3>${has(i.text) ? `<p>${esc(i.text)}</p>` : ""}</li>`).join("")}</ul>`;
}
