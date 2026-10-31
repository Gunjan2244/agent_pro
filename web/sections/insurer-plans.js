// "Official plans from my companies" (DECISIONS #129, #130): the official plans the agent shows, as plan cards that
// open each plan's own page on this site (sections/plan-page.js), never the insurer's site directly. On the Insurance
// page every plan, one fold-out group per company (the first open); on a plan-type page that type's plans, under a
// heading per company when there are several. Shared by all five templates; each restyles it in its style.css.
// Render-only.
import { esc, interestLabel } from "./util.js";
import { tr } from "./ui.js";
import { sitePlans, planFacts } from "./plan-data.js";
import { planCard } from "./plan-page.js";

export { sitePlans, planFacts };
export const INSURER_PLANS = "insurerPlans";

export function insurerPlans(d, ctx) {
  const k = ctx.page && ctx.page.key === "plan" ? ctx.page.plan : "";
  const list = sitePlans(ctx, k);
  if (!list.length) return "";
  const h = k ? tr(ctx, "site.iplans_heading_type", { type: interestLabel(ctx, k) }) : tr(ctx, "site.iplans_heading");
  if (!h) return "";
  const lead = tr(ctx, "site.iplans_lead"), fine = tr(ctx, "site.iplans_fine");
  const groups = [];
  for (const p of list) {
    let g = groups.find((x) => x.company === p.company);
    if (!g) groups.push((g = { company: p.company, items: [] }));
    g.items.push(p);
  }
  const cards = (g) => `<ul class="pcards">${g.items.map((p) => planCard(ctx, p)).join("")}</ul>`;
  const body = groups.map((g, n) => {
    if (k) return groups.length > 1 ? `<div class="ip-co"><h3>${esc(g.company)}</h3>${cards(g)}</div>` : cards(g);
    const count = tr(ctx, g.items.length === 1 ? "site.iplans_one" : "site.iplans_n", { n: g.items.length });
    return `<details class="ip-co"${n === 0 ? " open" : ""}><summary><h3>${esc(g.company)}</h3>${count ? `<span class="pill">${esc(count)}</span>` : ""}</summary>${cards(g)}</details>`;
  }).join("");
  return `<h2>${esc(h)}</h2>${lead ? `<p class="lead sub">${esc(lead)}</p>` : ""}<div class="ip-groups">${body}</div>${fine ? `<p class="fine disc">${esc(fine)}</p>` : ""}`;
}
