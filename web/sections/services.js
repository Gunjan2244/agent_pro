import { esc, has, arr, interestLabel } from "./util.js";
import { interestPic } from "./media.js";
import { say } from "./say.js";
export function services(d, ctx) {
  const items = arr(d.items).filter((i) => i && interestLabel(ctx, i.interest));
  const offered = arr(d.offered).filter((k) => interestLabel(ctx, k));
  if (!items.length && !offered.length) return "";
  const head = `${say(ctx, "site.say_services")}<h2>${esc(ctx.t("site.services_heading"))}</h2>`;
  // The offers section already showed plan cards with these descriptions: list the interests compactly.
  if (ctx.plansShown) {
    const keys = [...new Set([...items.map((i) => i.interest), ...offered])];
    return `${head}<ul class="chips">${keys.map((k) => `<li>${esc(interestLabel(ctx, k))}</li>`).join("")}</ul>`;
  }
  // A small 16:9 thumbnail beside the text (docs/IMAGES.md slot 3), so the section stays short on phones.
  const cards = items.map((i) => { const img = interestPic(ctx, i.interest, { sizes: "128px" }); return `<li class="card svc${img ? " has-pic" : ""}">${img}<h3>${esc(interestLabel(ctx, i.interest))}</h3>${has(i.description) ? `<p>${esc(i.description)}</p>` : ""}</li>`; }).join("");
  const shown = new Set(items.map((i) => i.interest));
  const chips = offered.filter((k) => !shown.has(k)).map((k) => `<li>${esc(interestLabel(ctx, k))}</li>`).join("");
  return `${head}
${cards ? `<ul class="cards">${cards}</ul>` : ""}${chips ? `<ul class="chips">${chips}</ul>` : ""}`;
}
