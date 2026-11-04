// Spotlight ("Best offers", offer showcase; DECISIONS #76, #117). A dark hero whose right side is a slider of the
// first offers (the featured one first: the showcase), a trust bar riding on the hero's lower edge, then the offer
// wall (every offer but the featured one, the first double width; plan cards when there are no offers), picture
// tiles for services, icon cards for highlights, a testimonials slider and a two-column FAQ.
import { esc, has, arr, interestLabel } from "../../sections/util.js";
import { heroParts, chips } from "../../sections/hero.js";
import { stats } from "../../sections/about.js";
import { pic, interestPic, fnv1a32 } from "../../sections/media.js";
import { icon } from "../../sections/icons.js";
import { offerCard, planCard } from "../../sections/offer.js";
import { planList, disclaimer } from "../../sections/offers.js";
import { slider, tr, topBar } from "../../sections/ui.js";
import { tagList, serviceRows } from "../../sections/services.js";
import { quoteItems } from "../../sections/testimonials.js";
import { pageHead, planOrEnquire } from "../../sections/pages.js";
import { featuredSection, sec, planCard as featCard } from "../../sections/plan-page.js";
import { featuredPlans as featList } from "../../sections/plan-data.js";
import { companyList } from "../../sections/companies.js";

const HERO_MAX = 4;
// Offer-led order (DECISIONS #132): the agent's featured offer first, then the rest by soonest end date (none last).
// A sorted copy: other sections read ctx.offers in its own order.
function ordered(ctx) {
  const f = ctx.offers.hero, end = (o) => (has(o.ends_on) ? o.ends_on : "9999-12-31");
  return [...(f ? [f] : []), ...ctx.offers.all.filter((o) => o !== f).sort((a, b) => (end(a) < end(b) ? -1 : end(a) > end(b) ? 1 : 0))];
}
const heroList = (ctx) => (ctx.visible.has("offers") ? ordered(ctx) : ctx.offers.hero ? [ctx.offers.hero] : []).slice(0, HERO_MAX);

export function hero(d, ctx) {
  const x = heroParts(d, ctx, { photo: [56, 56] });
  const list = heroList(ctx);
  const feat = list.length ? [] : featList(ctx);
  // no offers and no featured plans (a new agent): the plan types, each with an enquire button, so the hero still
  // leads with what the visitor can get (DECISIONS #132); the scene only when there is nothing at all
  const types = list.length || feat.length ? [] : planList(ctx);
  const side = list.length
    ? slider(list.map((o) => offerCard(o, ctx, { tag: "div", image: true, button: true, ttl: true, countdown: true })), ctx, { label: tr(ctx, "section.offers"), cls: "sl-hero", auto: 5500 })
    : feat.length
    ? slider(feat.map((p) => featCard(ctx, p, { tag: "div", sizes: "440px" })), ctx, { label: tr(ctx, "site.featured_heading"), cls: "sl-hero", auto: 5500 })
    : types.length
    ? slider(types.map((p) => planCard(p.key, p.desc, ctx, { tag: "div", ttl: true, button: true })), ctx, { label: tr(ctx, "site.services_heading"), cls: "sl-hero", auto: 5500 })
    : pic(ctx, `hero-scene-${(fnv1a32(ctx.seed || ctx.agentName || "") % 3) + 1}`, { cls: "scene", sizes: "(min-width: 1024px) 440px, 100vw", eager: true });
  // On a multi-page site the dark top bar above carries the name, the menu and the login (DECISIONS #122).
  const cos = companyList(ctx).map((c) => c.name).filter(has).join(", ");
  const strip = ctx.pages ? `<div class="sp-agent">${x.face}<div>${x.who}${cos ? `<p class="sp-cos">${esc(tr(ctx, "site.agent_of", { companies: cos }))}</p>` : ""}</div></div>\n` : "";
  const top = ctx.pages ? "" : `<div class="hero-top"><div class="hero-id">${x.face}${x.who}</div>${x.login}</div>\n`;
  return `<div class="hero-band"><div class="hero-in">
${top}<div class="hero-text enter">
${strip}${x.h1}
${x.lead}
${list.length ? chips(ctx, d) : x.button + x.chips}
</div>
<div class="hero-side">${side}</div>
</div></div>${trustBar(ctx)}`;
}

// The trust bar on the dark band's lower edge: years and clients (counting), then the highlight titles with icons.
function trustBar(ctx) {
  const c = ctx.site.content || {};
  const st = stats(c.about || {}, ctx, { count: true }).map((s) => `<li class="stat">${s}</li>`);
  const hl = arr((c.highlights || {}).items).filter((i) => i && has(i.title)).map((i) => `<li>${icon(i.icon)}<span>${esc(i.title)}</span></li>`);
  const hrs = has((c.contact || {}).officeHours) ? [`<li><span>${esc(tr(ctx, "site.reachable", { hours: c.contact.officeHours }))}</span></li>`] : [];
  return st.length || hl.length || hrs.length ? `<ul class="tbar">${[...st, ...hl, ...hrs].join("")}</ul>` : "";
}

// Inner pages: the shared page header on the dark band, with the trust bar on its edge (every page, DECISIONS #122).
export const pagehead = (ctx) => `<div class="hero-band">${pageHead(ctx)}</div>${trustBar(ctx)}`;
export const before = (ctx) => topBar(ctx);

// The offer wall: the offers the hero slider doesn't already show; the first spans two columns. With none left (no
// offers, or all of them in the hero), plan cards.
export function offers(d, ctx) {
  const wall = ordered(ctx).slice(heroList(ctx).length);
  const title = has(d.title) ? d.title : ctx.t("site.offers_title_default");
  if (wall.length) return `<h2>${esc(title)}</h2><ul class="offers wall stg">${wall.map((o) => offerCard(o, ctx, { image: true, cls: "lift zoom", countdown: true })).join("")}</ul>${disclaimer(ctx)}`;
  ctx.plansShown = true;
  const plans = planList(ctx).map((p) => planCard(p.key, p.desc, ctx, { cls: "lift zoom" })).join("");
  return plans ? `<h2>${esc(ctx.t("site.plans_heading"))}</h2><ul class="offers stg">${plans}</ul>${disclaimer(ctx)}` : "";
}

// How I can help: picture tiles, four across on wide screens (compact tags when the plan cards already showed them).
export function services(d, ctx) {
  const rows = serviceRows(d, ctx);
  if (!rows.length) return "";
  const head = `<h2>${esc(ctx.t("site.services_heading"))}</h2>`;
  if (ctx.plansShown) return `${head}${tagList(rows.map((i) => i.interest), ctx)}`;
  return `${head}<ul class="tiles stg">${rows.map((i) => `<li class="tile zoom">${interestPic(ctx, i.interest, { sizes: "(min-width: 1024px) 260px, 50vw" })}<h3>${esc(interestLabel(ctx, i.interest))}</h3>${has(i.description) ? `<p>${esc(i.description)}</p>` : ""}${planOrEnquire(ctx, i.interest)}</li>`).join("")}</ul>`;
}

// Why choose me: icon cards (the trust bar already carries the numbers and titles; these add the text).
export function highlights(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title) && has(i.text));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.highlights"))}</h2><ul class="cards hl stg">${items.map((i) => `<li class="card lift">${icon(i.icon)}<h3>${esc(i.title)}</h3><p>${esc(i.text)}</p></li>`).join("")}</ul>`;
}

// Testimonials: a slider, three per view on wide screens.
export function testimonials(d, ctx) {
  const items = quoteItems(d);
  if (!items.length) return "";
  const h = ctx.t("section.testimonials");
  return `<h2>${esc(h)}</h2>${slider(items.map((i) => `<figure class="card quote"><blockquote>${esc(i.quote)}</blockquote><figcaption class="meta">${esc(i.name)}${has(i.city) ? ", " + esc(i.city) : ""}</figcaption></figure>`), ctx, { label: h, cls: "sl-q", auto: 6000 })}`;
}

// Plans I recommend (DECISIONS #130): picture cards, three across.
export const featuredPlans = (d, ctx) => featuredSection(d, ctx, { layout: "grid" });

// A plan's page: the offer on it first, in the tinted band (the page is about offers), then the key features as a
// checklist, the documents as tiles and similar plans as cards; "Talk to …" on the tinted band closes the page (its
// one closing call to action, DECISIONS #131).
export function planPage(x, ctx) {
  const band = x.offer ? sec("offer", x.offer, "band") : "";
  return band + sec("features", x.checks) + sec("about", x.about) + sec("docs", x.docTiles, "alt") + sec("similar", x.similar) + sec("talk", x.talk, "band");
}

// The slot contract (sections/index.js SLOTS, DECISIONS #131): the sections this template draws itself, and those it
// leaves to the shared partials on purpose.
export const PARTS = { hero, offers, services, highlights, testimonials, pagehead, featuredPlans };
export const INHERITS = ["about", "companies", "achievements", "faq", "process"];
