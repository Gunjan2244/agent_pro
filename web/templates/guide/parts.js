// Guide ("Best offers", needs finder; DECISIONS #76, #117). The page opens with one question, "What would you like
// to protect?", answered with picture tiles that jump to the matching group; a three-step "How it works" strip; then
// the plans grouped by need, with an index that follows the group in view (a sticky side list on wide screens, a
// sticky swipeable strip on phones); a trust ticker; one big rotating quote.
import { esc, has, arr, interestLabel } from "../../sections/util.js";
import { heroParts, chips } from "../../sections/hero.js";
import { stats } from "../../sections/about.js";
import { interestPic } from "../../sections/media.js";
import { icon } from "../../sections/icons.js";
import { offerCard, planCard } from "../../sections/offer.js";
import { needGroups, disclaimer } from "../../sections/offers.js";
import { quoteItems } from "../../sections/testimonials.js";
import { slider, ticker, topBar, tr } from "../../sections/ui.js";
import { needHref } from "../../sections/hero.js";
import { pageHref } from "../../sections/links.js";
import { INNER, seePlans, feeNote } from "../../sections/pages.js";
import { planCard as officialCard, box, grid } from "../../sections/plan-page.js";
import { featuredPlans as featuredList } from "../../sections/plan-data.js";
import { planHref } from "../../sections/links.js";
import { services as sharedServices } from "../../sections/services.js";

const STEPS = ["site.step_1", "site.step_2", "site.step_3"];
// the hero answers "What would you like to protect?" with one picture tile per need group
// (any agent with plan types; the tiles link to the plan pages, or to the groups when the offers section shows)
const heroTiles = (ctx) => needGroups(ctx).length > 0 && (ctx.visible.has("offers") || Boolean(ctx.pages) || Boolean(planHref(ctx, needGroups(ctx)[0].key)));

export function hero(d, ctx) {
  const x = heroParts(d, ctx, { photo: [56, 56] });
  const groups = heroTiles(ctx) ? needGroups(ctx) : [];
  const q = tr(ctx, "site.guide_question");
  const see = tr(ctx, "site.see_plans");
  const tiles = groups.length
    ? `<p class="qr-h">${esc(q)}</p><ul class="needs-t stg">${groups.map((g) => `<li><a class="nt lift zoom" href="${esc(needHref(ctx, g.key))}">${interestPic(ctx, g.key, { sizes: "(min-width: 1024px) 340px, 50vw" })}<span class="nt-b"><strong>${esc(interestLabel(ctx, g.key))}</strong>${see ? `<span class="lnk">${esc(see)}</span>` : ""}</span></a></li>`).join("")}</ul>`
    : `${x.button}${chips(ctx, d)}`;
  // an explicit featured offer: a slim strip with a text link (no promoted card)
  const f = ctx.offers.hero;
  const strip = f ? `<p class="feat-s"><span class="pill">${esc(has(f.badge) ? f.badge : ctx.t("site.offer_pick"))}</span><span>${esc(f.title)}</span><a class="lnk" href="${esc(ctx.pages ? pageHref(ctx, "insurance", { anchor: "offers" }) || "#offers" : "#offers")}">${esc(tr(ctx, "site.see_offer") || ctx.t("offer_field.details"))}</a></p>` : "";
  const texts = [1, 2, 3].map((n) => tr(ctx, `site.step_${n}_text`));
  const steps = STEPS.map((k) => tr(ctx, k)).filter(Boolean);
  const how = steps.length === 3 && tr(ctx, "site.steps_heading")
    ? `<div class="steps"><p class="st-h">${esc(tr(ctx, "site.steps_heading"))}</p><ol>${steps.map((s, i) => `<li><span class="st-n" aria-hidden="true">${i + 1}</span><span class="st-t"><strong>${esc(s)}</strong>${texts[i] ? `<span>${esc(texts[i])}</span>` : ""}</span></li>`).join("")}</ol>${feeNote(ctx)}</div>`
    : "";
  return `<div class="hero-in">
<div class="hero-text enter">
<div class="hero-id">${x.face}${x.who}</div>
${x.h1}
${x.lead}
</div>
${tiles}
${strip}
</div>${how}`;
}

// The plans by need: an index (links to each group) beside the groups. Each group: its picture, heading, the
// agent's own description, then its offer cards (or its plan card).
export function offers(d, ctx) {
  // Home of a multi-page site (DECISIONS #122): the picture tiles already lead to the plan pages, so the section is
  // only the current offers as cards (none: nothing); the groups are on the Insurance page.
  if (ctx.page && ctx.page.key === "home") {
    const list = ctx.offers.grid;
    if (!list.length) return "";
    const title = has(d.title) ? d.title : ctx.t("site.offers_title_default");
    return `<h2>${esc(title)}</h2><ul class="offers stg">${list.map((x) => offerCard(x, ctx, { image: true, cls: "lift" })).join("")}</ul>`;
  }
  const groups = needGroups(ctx);
  if (!groups.length) return "";
  ctx.plansShown = true;
  const o = ctx.offers;
  const title = o.grid.length ? (has(d.title) ? d.title : ctx.t("site.offers_title_default")) : ctx.t("site.plans_heading");
  const idx = `<div class="need-idx"><ul>${groups.map((g) => `<li><a href="#need-${esc(g.key)}">${esc(interestLabel(ctx, g.key))}${g.offers.length ? ` <span>${g.offers.length}</span>` : ""}</a></li>`).join("")}</ul></div>`;
  const body = groups.map((g) => {
    const cards = g.offers.length ? g.offers.map((x) => offerCard(x, ctx, { image: true, cls: "lift" })).join("") : planCard(g.key, g.desc, ctx, { noTitle: true, noDesc: true });
    return `<div class="need" id="need-${esc(g.key)}"><div class="need-h">${interestPic(ctx, g.key, { sizes: "96px" })}<div><h3>${esc(interestLabel(ctx, g.key))}</h3>${has(g.desc) ? `<p class="need-d">${esc(g.desc)}</p>` : ""}${seePlans(ctx, g.key)}</div></div><ul class="offers">${cards}</ul></div>`;
  }).join("");
  return `<h2>${esc(title)}</h2><div class="needs">${idx}<div class="need-list">${body}</div></div>${disclaimer(ctx)}`;
}

// Trust: years and clients (counting) and the highlight titles, as a ticker band.
export function highlights(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  const st = stats((ctx.site.content || {}).about || {}, ctx, { count: true });
  if (!items.length && !st.length) return "";
  const h = ctx.t("section.highlights");
  return `<h2>${esc(h)}</h2>${ticker([...st, ...items.map((i) => `${icon(i.icon)}${esc(i.title)}`)], { cls: "tk-trust", label: h })}`;
}

// Testimonials: one big quote at a time.
export function testimonials(d, ctx) {
  const items = quoteItems(d);
  if (!items.length) return "";
  const h = ctx.t("section.testimonials");
  return `<h2>${esc(h)}</h2>${slider(items.map((i) => `<figure class="bigq"><blockquote>${esc(i.quote)}</blockquote><figcaption>${esc(i.name)}${has(i.city) ? ", " + esc(i.city) : ""}</figcaption></figure>`), ctx, { label: h, cls: "sl-bigq", auto: 6000 })}`;
}

export const before = (ctx) => topBar(ctx);

// The Insurance page keeps the guide's structure (DECISIONS #122): the plans grouped by need beside the sticky index
// (the groups carry each plan type's heading and description, and link on to its page), instead of the shared plan
// cards; with the offers section hidden, the shared cards.
export const insurance = {
  offers,
  services: (d, ctx) => (ctx.visible.has("offers") && needGroups(ctx).length ? "" : INNER.services(d, ctx)),
};

// Every inner page ends with the green trust ticker before the footer (not a section: no heading), except one whose
// own highlights section already draws it (About, DECISIONS #131; Home has it as its highlights section).
export function afterInner(ctx) {
  if (ctx.rendered.includes("highlights")) return "";
  const c = ctx.site.content || {};
  const st = stats(c.about || {}, ctx, { count: true });
  const items = [...st, ...arr((c.highlights || {}).items).filter((i) => i && has(i.title)).map((i) => `${icon(i.icon)}${esc(i.title)}`)];
  return items.length ? `<div class="g-trust">${ticker(items, { cls: "tk-trust", label: ctx.t("section.highlights") })}</div>` : "";
}

// Plans I recommend (DECISIONS #130), grouped by need: each plan type's picture and name, the agent's featured plans
// of that type as cards, and a link on to all its plans.
export function featuredPlans(d, ctx) {
  const list = featuredList(ctx), h = tr(ctx, "site.featured_heading");
  if (!list.length || !h) return "";
  const groups = [];
  for (const p of list) { let g = groups.find((x) => x.key === p.interest); if (!g) groups.push((g = { key: p.interest, items: [] })); g.items.push(p); }
  const see = tr(ctx, "site.see_plans");
  const body = groups.map((g) => { const all = planHref(ctx, g.key); return `<div class="fneed"><div class="need-h">${interestPic(ctx, g.key, { sizes: "96px" })}<div><h3>${esc(interestLabel(ctx, g.key))}</h3>${all && see ? `<a class="lnk" href="${esc(all)}">${esc(see)}</a>` : ""}</div></div><ul class="pcards">${g.items.map((p) => officialCard(ctx, p)).join("")}</ul></div>`; }).join("");
  const lead = tr(ctx, "site.featured_lead");
  // then "All insurance plans" (Guide's Home has no "How I can help" under its tiles, so this is Home's link to Insurance)
  const all = pageHref(ctx, "insurance"), allL = tr(ctx, "site.plan_back");
  const more = all && allL ? `<p class="more-l"><a class="lnk" href="${esc(all)}">${esc(allL)}</a></p>` : "";
  return `<h2>${esc(h)}</h2>${lead ? `<p class="lead sub">${esc(lead)}</p>` : ""}<div class="fneeds">${body}</div>${more}`;
}

// How I can help: on a multi-page site's Home the hero's picture tiles already are the plan types (each to its page),
// so nothing (DECISIONS #131); anywhere else the shared cards.
export const services = (d, ctx) => (heroTiles(ctx) && ctx.page && ctx.page.key === "home" ? "" : sharedServices(d, ctx));

// A plan's page as three steps (what the plan offers, the official documents, ask me) beside the picture, the offer
// on it and plans to compare with.
export function planPage(x, ctx) {
  const step = (n, inner) => (inner ? `<li class="pl-step"><span class="n" aria-hidden="true">${n}</span><section id="step-${n}">${inner}</section></li>` : "");
  const askH = `<h2>${esc(tr(ctx, "site.pp_step_ask"))}</h2><p class="pl-talk-l">${esc(tr(ctx, "site.pp_step_ask_lead"))}</p>${x.ask ? `<p class="pl-acts">${x.ask}</p>` : ""}`;
  const titled = (key, body) => (body ? `<h2>${esc(tr(ctx, key))}</h2>${body}` : "");
  const items = [titled("site.pp_step_features", x.featuresBody), titled("site.pp_step_docs", x.docsBody), askH].filter(Boolean);
  const steps = `<ol class="pl-steps">${items.map((h, i) => step(i + 1, h)).join("")}</ol>`;
  const side = (x.pic ? `<div class="pl-pic">${x.pic}</div>` : "") + box("offer", x.offer, "pl-card pl-offer") + box("compare", x.compare, "pl-card");
  return grid(box("about", x.about, "pl-block") + steps + x.fine, side);
}

// The slot contract (sections/index.js SLOTS, DECISIONS #131): the sections this template draws itself, and those it
// leaves to the shared partials on purpose.
export const PARTS = { hero, offers, services, highlights, testimonials, featuredPlans };
export const INHERITS = ["about", "companies", "achievements", "faq", "process"];
