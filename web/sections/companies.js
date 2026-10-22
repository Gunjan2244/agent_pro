// "Companies I represent" (DECISIONS #90): the agent's approved, active companies from the payload's `companies`
// ([{name, insurance_types}], already reduced by normalizeSite to those two fields). Names only, as plain text: no
// logos, links or slugs. The types are labelled from catalog.insuranceTypes (catalog order; unknown keys dropped).
// The section is hidden when there are no companies, and while the heading key is missing from the dictionary.
import { esc, arr } from "./util.js";

export const COMPANIES_HEADING = "site.companies_heading";

// [{name, types: [label]}]
export function companyList(ctx) {
  const types = arr(ctx.catalog.insuranceTypes).slice().sort((a, b) => a.order - b.order);
  return arr(ctx.site.companies).map((c) => ({
    name: c.name,
    types: types.filter((x) => arr(c.insurance_types).includes(x.key)).map((x) => ctx.t(x.labelKey)).filter((l, i, all) => l && !l.startsWith("insurance_type.") && all.indexOf(l) === i),
  }));
}
// The translated heading, or "" while shared/i18n lacks it (never a raw key on a site).
export function companiesHeading(ctx) {
  const h = ctx.t(COMPANIES_HEADING);
  return h && h !== COMPANIES_HEADING ? h : "";
}
export const tags = (types) => (types.length ? `<ul class="tags">${types.map((x) => `<li>${esc(x)}</li>`).join("")}</ul>` : "");

export function companies(d, ctx) {
  const list = companyList(ctx), h = companiesHeading(ctx);
  if (!list.length || !h) return "";
  return `<h2>${esc(h)}</h2>
<ul class="cos">${list.map((c) => `<li class="card co"><h3>${esc(c.name)}</h3>${tags(c.types)}</li>`).join("")}</ul>`;
}
