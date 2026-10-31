// "Plans I can help you with" from the insurers themselves (DECISIONS #129): the official plans the agent chose for
// their site, from their approved companies and only for the insurance types they deal in there (the backend decides;
// the payload's `plans`, reduced by api.js normalizePlans). Each plan shows only facts — the official name, LIC's plan
// number, the UIN — and links to the plan's page on the insurer's own website, where the benefits, brochure and terms
// are. Nothing about a plan is written by us or by the agent, so nothing here needs an insurer's approval as an
// advertisement (PRODUCT.md 2026-10-31). The admin's picture of a plan (https) is shown; otherwise the plan type's
// picture. Shared by all five templates on the Insurance page (every plan, one fold-out group per company) and on
// each plan page (that type's plans); each template restyles it in its style.css. Render-only.
import { esc, arr, url, interestLabel } from "./util.js";
import { interestPic } from "./media.js";
import { tr } from "./ui.js";
import { contactHref } from "./links.js";

export const INSURER_PLANS = "insurerPlans";

// The site's plans, optionally of one plan type (interest key).
export const sitePlans = (ctx, interest = "") => arr(ctx.site.plans).filter((p) => p && p.name && p.url && (!interest || p.interest === interest));

// "Plan No. 914 · UIN 512N339V02" ("" when neither is known).
export function planFacts(ctx, p) {
  const parts = [];
  if (p.plan_no) parts.push(tr(ctx, "site.iplan_no", { no: p.plan_no }) || `Plan No. ${p.plan_no}`);
  if (p.uin) parts.push(tr(ctx, "site.iplan_uin", { uin: p.uin }) || `UIN ${p.uin}`);
  return parts.join(" · ");
}

function row(ctx, p, { showType }) {
  const pic = url(p.image_url)
    ? `<img class="pic" src="${url(p.image_url)}" width="160" height="90" alt="" loading="lazy" decoding="async">`
    : interestPic(ctx, p.interest, { sizes: "96px", cls: "pic" });
  const facts = planFacts(ctx, p);
  const type = showType ? interestLabel(ctx, p.interest) : "";
  const go = tr(ctx, "site.iplan_official") || "Official details";
  const opens = tr(ctx, "site.iplan_opens", { company: p.company });
  const ask = tr(ctx, "site.iplan_ask");
  const askHref = contactHref(ctx, p.interest);
  return `<li class="iplan">${pic}<div class="ip-b"><strong>${esc(p.name)}</strong>${facts ? `<span class="ip-m">${facts.split(" · ").map((f) => `<span>${esc(f)}</span>`).join(" · ")}</span>` : ""}${type ? `<span class="pill">${esc(type)}</span>` : ""}</div><p class="ip-a"><a class="lnk ext" href="${url(p.url)}" target="_blank" rel="noopener nofollow external">${esc(go)}${opens ? `<span class="vh"> ${esc(opens)}</span>` : ""}</a>${ask && askHref ? `<a class="lnk ip-ask" href="${esc(askHref)}" data-interest="${esc(p.interest)}">${esc(ask)}</a>` : ""}</p></li>`;
}

// The section: one group per company, in the payload's order (the agent's order, or approval order). On the Insurance
// page each company is a fold-out group (the first open) and every row names its plan type; on a plan page the
// groups are plain lists of that type's plans.
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
  const body = groups.map((g, n) => {
    const rows = `<ul class="iplans">${g.items.map((p) => row(ctx, p, { showType: !k })).join("")}</ul>`;
    const count = tr(ctx, g.items.length === 1 ? "site.iplans_one" : "site.iplans_n", { n: g.items.length });
    if (k) return `<div class="ip-co"><h3>${esc(g.company)}</h3>${rows}</div>`;
    return `<details class="ip-co"${n === 0 ? " open" : ""}><summary><h3>${esc(g.company)}</h3>${count ? `<span class="pill">${esc(count)}</span>` : ""}</summary>${rows}</details>`;
  }).join("");
  return `<h2>${esc(h)}</h2>${lead ? `<p class="lead">${esc(lead)}</p>` : ""}<div class="ip-groups">${body}</div>${fine ? `<p class="fine disc">${esc(fine)}</p>` : ""}`;
}
