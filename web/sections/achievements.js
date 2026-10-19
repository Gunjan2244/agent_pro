import { esc, has, arr } from "./util.js";
import { say } from "./say.js";
export function achievements(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  if (!items.length) return "";
  return `${say(ctx, "site.say_achievements")}<h2>${esc(ctx.t("section.achievements"))}</h2><ul class="list">${items
    .map((i) => `<li><span>${esc(i.title)}</span>${has(i.year) ? `<span class="meta">${esc(i.year)}</span>` : ""}</li>`).join("")}</ul>`;
}
