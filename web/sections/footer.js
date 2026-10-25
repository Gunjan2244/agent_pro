import { esc } from "./util.js";
export function footer(ctx) {
  const { t, profile: p } = ctx, my = ctx.portalBase;
  return `<footer class="foot"><p>${esc(t("site.footer_advisor", { name: p.full_name || "" }))}</p><p class="meta">${my ? `<a href="${esc(my)}">${esc(t("site.footer_portal"))}</a> · ` : ""}<a href="${esc(ctx.privacyHref || "#")}">${esc(t("site.footer_privacy"))}</a> · ${esc(t("site.footer_made_with"))}</p></footer>`;
}
