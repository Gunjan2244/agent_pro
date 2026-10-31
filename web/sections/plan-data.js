// The official plans a site shows (DECISIONS #129, #130) and how pages link to them. Pure helpers shared by the plan
// cards, the plan page, the "We're taking you to …" page, the offer cards and every template. The payload's `plans`
// (api.js normalizePlans) are already the agent's choice: only plans of their approved companies, for the insurance
// types they deal in there; `featured` is the plan's position on Home (1–6) or null. Render-only.
import { esc, arr, httpsUrl, interestLabel } from "./util.js";
import { icon } from "./icons.js";
import { tr } from "./ui.js";
import { pageUrl, DOCS } from "../route.js";

// The site's plans, optionally of one plan type (interest key).
export const sitePlans = (ctx, interest = "") => arr(ctx.site.plans).filter((p) => p && p.name && p.url && (!interest || p.interest === interest));
export const planById = (ctx, id) => (id ? sitePlans(ctx).find((p) => p.id === id) || null : null);
export const planBySlug = (ctx, slug) => (slug ? sitePlans(ctx).find((p) => p.slug === slug) || null : null);

// Home's "Plans I recommend": the featured plans in the agent's order (at most site-pages.json featuredItems).
export function featuredPlans(ctx) {
  const n = Number((ctx.sitePages || {}).featuredItems) || 6;
  return sitePlans(ctx).filter((p) => Number(p.featured) > 0).sort((a, b) => a.featured - b.featured).slice(0, n);
}

// "Plan No. 914 · UIN 512N339V02" ("" when neither is known).
export function planFacts(ctx, p) {
  const parts = [];
  if (p.plan_no) parts.push(tr(ctx, "site.iplan_no", { no: p.plan_no }) || `Plan No. ${p.plan_no}`);
  if (p.uin) parts.push(tr(ctx, "site.iplan_uin", { uin: p.uin }) || `UIN ${p.uin}`);
  return parts.join(" · ");
}

// The plan's own page on this site, /insurance/<type>/<slug> ("" when the site has no such page: one-page renders).
export function planPageHref(ctx, p) {
  const pg = p && Array.isArray(ctx.pages) ? ctx.pages.find((x) => x.key === "planDetail" && x.slug === p.slug) : null;
  return pg ? pageUrl(ctx.links, pg.path) : "";
}

// The insurer's address for one of a plan's documents (route.js DOCS), or "".
export function docUrl(p, doc) {
  if (!p) return "";
  if (doc === "plan") return httpsUrl(p.url);
  if (doc === "features") return httpsUrl(p.features_url || p.url);
  return DOCS.includes(doc) ? httpsUrl((p.docs || {})[doc]) : "";
}
// The documents a plan page lists, in order (the plan page first, then those the admin added).
export const planDocs = (p) => ["plan", "brochure", "policy", "cis", "calculator"].filter((d) => docUrl(p, d));

// Every link to an insurer goes through the "We're taking you to …" page (/go/<slug>/<doc>, DECISIONS #130). Without
// pages (one-page renders) the insurer's address itself, opened in a new tab.
export function goHref(ctx, p, doc) {
  if (!docUrl(p, doc)) return "";
  return Array.isArray(ctx.pages) && ctx.sitePages ? pageUrl(ctx.links, `go/${p.slug}/${doc}`) : docUrl(p, doc);
}
export const goAttrs = (ctx) => (Array.isArray(ctx.pages) && ctx.sitePages ? ' rel="nofollow"' : ' target="_blank" rel="noopener nofollow external"');

// Every plan has a picture (DECISIONS #130): the admin's upload (https, company-media), else the default picture of
// its plan type, drawn from the catalog's icon for that type (catalog.interests[].icon) on an accent-tinted panel.
// The default is decorative (the plan's name and type are in the text next to it).
export function planPicture(ctx, p, { sizes = "(min-width: 720px) 360px, 100vw", eager = false, cls = "" } = {}) {
  const up = httpsUrl(p.image_url);
  if (up) return `<img class="pic${cls ? " " + cls : ""}" src="${up}" width="800" height="450" alt="" sizes="${esc(sizes)}"${eager ? ' fetchpriority="high"' : ' loading="lazy"'} decoding="async">`;
  const i = arr(ctx.catalog.interests).find((x) => x.key === p.interest) || {};
  return `<span class="pic dpic${cls ? " " + cls : ""}" aria-hidden="true">${icon(i.icon || "shield")}<span class="dpic-t">${esc(interestLabel(ctx, p.interest))}</span></span>`;
}

// The live offers on a plan (the payload already left out offers on plans the site doesn't show).
export const planOffers = (ctx, p) => arr(ctx.offers && ctx.offers.all).filter((o) => o && o.plan_id && p && o.plan_id === p.id);
