import { esc } from "./util.js";
export function footer(ctx) {
  const { t, profile: p } = ctx;
  return `<footer class="foot"><p>${esc(t("site.footer_advisor", { name: p.full_name || "" }))}</p><p class="meta">${esc(t("site.footer_made_with"))}</p></footer>`;
}
