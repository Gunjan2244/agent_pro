import { esc, has, arr } from "./util.js";
import { say, avatar } from "./say.js";
// A chat: each question is the visitor's bubble (right), each answer the agent's (left). Still <details>/<summary>.
export function faq(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.question) && has(i.answer));
  if (!items.length) return "";
  const av = avatar(ctx);
  return `${say(ctx, "site.say_faq")}<h2>${esc(ctx.t("section.faq"))}</h2><div class="faq">${items
    .map((i) => `<details><summary><span class="you">${esc(i.question)}</span></summary><div class="say">${av}<p class="bubble prose">${esc(i.answer)}</p></div></details>`).join("")}</div>`;
}
