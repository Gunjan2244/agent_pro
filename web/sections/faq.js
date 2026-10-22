import { esc, has, arr } from "./util.js";
// A normal accordion (DECISIONS #87): each question is a <details> row; the answer opens below it.
export function faq(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.question) && has(i.answer));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.faq"))}</h2><div class="faq">${items
    .map((i) => `<details><summary>${esc(i.question)}</summary><p class="prose">${esc(i.answer)}</p></details>`).join("")}</div>`;
}
