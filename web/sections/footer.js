// The footer of every page of every template. Besides the agent's name, "My policies", Privacy and "Made with Agent
// PRO", it carries the disclosure an insurance agent's own website needs (DECISIONS #129; PRODUCT.md 2026-10-31):
// whose agent this is (the approved companies by name, as plain text, with the agent's agency code there when the
// agent shows it), that this is the agent's personal website and not an official website of any insurer, where the
// official information is (each insurer's own website, from the payload), and the standard "Insurance is the
// subject matter of solicitation." No logo or trademark artwork, ever (DECISIONS #47).
import { esc, url } from "./util.js";
import { tr } from "./ui.js";

// "A (Agency code: X), B and C" in the site's language.
function companyNames(ctx, list) {
  const named = list.map((c) => (c.agency_code ? tr(ctx, "site.footer_company_code", { company: c.name, code: c.agency_code }) || `${c.name} (${c.agency_code})` : c.name));
  if (named.length < 2) return named.join("");
  return tr(ctx, "site.footer_and", { list: named.slice(0, -1).join(", "), last: named[named.length - 1] }) || named.join(", ");
}

// HTML lines (each already escaped).
export function disclosure(ctx) {
  const list = Array.isArray(ctx.site.companies) ? ctx.site.companies.filter((c) => c && c.name) : [];
  const name = ctx.profile.full_name || "";
  const lines = [];
  const agentOf = list.length && name ? tr(ctx, "site.footer_agent_of", { name, companies: companyNames(ctx, list) }) : "";
  if (agentOf) lines.push(esc(agentOf));
  const notOfficial = tr(ctx, list.length ? "site.footer_not_official" : "site.footer_not_official_any");
  if (notOfficial) lines.push(esc(notOfficial));
  const sites = list.filter((c) => url(c.website));
  const label = tr(ctx, "site.footer_official_sites");
  if (sites.length && label) lines.push(`${esc(label)} ${sites.map((c) => `<a href="${url(c.website)}" target="_blank" rel="noopener nofollow external">${esc(c.name)}</a>`).join(", ")}`);
  const sol = tr(ctx, "site.footer_solicitation");
  if (sol) lines.push(`<strong>${esc(sol)}</strong>`);
  return lines.length ? `<div class="disclose">${lines.map((l) => `<p>${l}</p>`).join("")}</div>` : "";
}

export function footer(ctx) {
  const { t, profile: p } = ctx, my = ctx.portalBase;
  return `<footer class="foot"><p>${esc(t("site.footer_advisor", { name: p.full_name || "" }))}</p>${disclosure(ctx)}<p class="meta">${my ? `<a href="${esc(my)}">${esc(t("site.footer_portal"))}</a> · ` : ""}<a href="${esc(ctx.privacyHref || "#")}">${esc(t("site.footer_privacy"))}</a> · ${esc(t("site.footer_made_with"))}</p></footer>`;
}
