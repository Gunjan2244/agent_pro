import { esc, has, arr, optionLabel } from "./util.js";
export function about(d, ctx) {
  const { t, profile: p } = ctx;
  const stats = [];
  if (has(d.yearsExperience)) stats.push(t("site.years_experience", { n: esc(d.yearsExperience) }));
  if (has(d.clientsServed)) stats.push(t("site.clients_served", { n: esc(d.clientsServed) }));
  const langs = arr(d.languagesSpoken).map((k) => optionLabel(ctx, "about", "languagesSpoken", null, k));
  const code = d.showAgencyCode && has(p.agency_code) ? t("site.agency_code", { code: esc(p.agency_code) }) : "";
  if (![d.bio, d.designation, stats.length, langs.length, code].some(Boolean)) return "";
  return `<h2>${esc(t("section.about"))}</h2>
${has(d.designation) ? `<p class="meta">${esc(d.designation)}</p>` : ""}
${has(d.bio) ? `<p class="prose">${esc(d.bio)}</p>` : ""}
${stats.length ? `<ul class="stats">${stats.map((s) => `<li>${esc(s)}</li>`).join("")}</ul>` : ""}
${langs.length ? `<ul class="chips">${langs.map((l) => `<li>${esc(l)}</li>`).join("")}</ul>` : ""}
${code ? `<p class="meta">${code}</p>` : ""}`;
}
