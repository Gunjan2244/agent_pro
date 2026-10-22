// The offers section (OFFERS.md, DECISIONS #76/#77/#79). Renders the payload's offers; never a company name or
// logo (the payload has none, and `company` is reduced to {disclaimer} by normalizeSite). On the offers family:
//   - spotlight (layout "grid"): an image grid, or plan-type cards from services when there are no offers;
//   - guide (layout "needs"): one anchored group per interest (#need-<key>): its heading, the agent's own service
//     description (if any), then its offer cards or plan card.
// On the personal family an empty section renders nothing.
import { esc, has, arr, interestLabel, textOr } from "./util.js";
import { offerCard, planCard, interestOf } from "./offer.js";

// The interests the agent offers, with their descriptions: services items first, then `offered`; else every
// active catalog interest of an insurance type (not "other"), so a Best offers page is never empty.
export function planList(ctx) {
  const sv = (ctx.site.content || {}).services || {};
  const out = new Map();
  for (const i of arr(sv.items)) if (i && interestLabel(ctx, i.interest) && !out.has(i.interest)) out.set(i.interest, i.description || "");
  for (const k of arr(sv.offered)) if (interestLabel(ctx, k) && !out.has(k)) out.set(k, "");
  if (!out.size) for (const i of arr(ctx.catalog.interests).slice().sort((a, b) => a.order - b.order)) if (i.insuranceType != null && !i.deprecated) out.set(i.key, "");
  return [...out].map(([key, desc]) => ({ key, desc }));
}

// Guide groups: one per interest that has an offer (in offer order) or a service (in services order).
export function needGroups(ctx) {
  const groups = new Map();
  const add = (key) => { if (!groups.has(key)) groups.set(key, { key, offers: [], desc: "" }); return groups.get(key); };
  for (const o of ctx.offers.grid) {
    const k = interestOf(o);
    add(interestLabel(ctx, k) ? k : "other").offers.push(o);
  }
  for (const p of planList(ctx)) add(p.key).desc = p.desc;
  // "other" (offers without a plan type) goes last.
  return [...groups.values()].filter((g) => interestLabel(ctx, g.key)).sort((a, b) => (a.key === "other") - (b.key === "other"));
}

const disclaimer = (ctx) => {
  const d = ctx.site.company && ctx.site.company.disclaimer;
  return ctx.offers.all.length && has(d) ? `<p class="fine disc">${esc(d)}</p>` : "";
};

export function offers(d, ctx) {
  const { t, offers: o } = ctx;
  const title = esc(textOr(ctx, "offers", "title", d.title));
  if (ctx.family !== "offers") {
    if (!o.grid.length) return "";
    return `<h2>${title}</h2><ul class="offers">${o.grid.map((x) => offerCard(x, ctx)).join("")}</ul>${disclaimer(ctx)}`;
  }
  if (ctx.layout === "needs") {
    const groups = needGroups(ctx);
    if (!groups.length) return "";
    ctx.plansShown = true;
    const body = groups.map((g) => {
      const cards = g.offers.length ? g.offers.map((x) => offerCard(x, ctx, { image: true })).join("") : planCard(g.key, g.desc, ctx, { noTitle: true, noDesc: true });
      return `<div class="need" id="need-${esc(g.key)}"><h3>${esc(interestLabel(ctx, g.key))}</h3>${has(g.desc) ? `<p class="need-d">${esc(g.desc)}</p>` : ""}<ul class="offers">${cards}</ul></div>`;
    }).join("");
    return `<h2>${o.grid.length ? title : esc(t("site.plans_heading"))}</h2>${body}${disclaimer(ctx)}`;
  }
  if (o.grid.length) return `<h2>${title}</h2><ul class="offers">${o.grid.map((x) => offerCard(x, ctx, { image: true })).join("")}</ul>${disclaimer(ctx)}`;
  ctx.plansShown = true;
  const plans = planList(ctx).map((p) => planCard(p.key, p.desc, ctx)).join("");
  return plans ? `<h2>${esc(t("site.plans_heading"))}</h2><ul class="offers">${plans}</ul>${disclaimer(ctx)}` : "";
}
