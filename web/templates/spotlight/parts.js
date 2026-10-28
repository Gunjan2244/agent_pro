// Spotlight ("Best offers", offer showcase; DECISIONS #76, #117). A dark hero whose right side is a slider of the
// first offers (the featured one first: the showcase), a trust bar riding on the hero's lower edge, then the offer
// wall (every offer but the featured one, the first double width; plan cards when there are no offers), picture
// tiles for services, icon cards for highlights, a testimonials slider and a two-column FAQ.
import { esc, has, arr, interestLabel } from "../../sections/util.js";
import { heroParts, chips } from "../../sections/hero.js";
import { stats } from "../../sections/about.js";
import { serviceList } from "../../sections/services.js";
import { pic, interestPic, fnv1a32 } from "../../sections/media.js";
import { icon } from "../../sections/icons.js";
import { offerCard, planCard } from "../../sections/offer.js";
import { planList } from "../../sections/offers.js";
import { slider, tr, topBar } from "../../sections/ui.js";
import { tagList } from "../../sections/services.js";
import { pageHead, planOrEnquire } from "../../sections/pages.js";

const HERO_MAX = 4;
const heroList = (ctx) => (ctx.visible.has("offers") ? ctx.offers.all : ctx.offers.hero ? [ctx.offers.hero] : []).slice(0, HERO_MAX);

export function hero(d, ctx) {
  const x = heroParts(d, ctx, { photo: [56, 56] });
  const list = heroList(ctx);
  const side = list.length
    ? slider(list.map((o) => offerCard(o, ctx, { tag: "div", image: true, button: true, ttl: true })), ctx, { label: tr(ctx, "section.offers"), cls: "sl-hero", auto: 5500 })
    : pic(ctx, `hero-scene-${(fnv1a32(ctx.seed || ctx.agentName || "") % 3) + 1}`, { cls: "scene", sizes: "(min-width: 1024px) 440px, 100vw", eager: true });
  // On a multi-page site the dark top bar above carries the name, the menu and the login (DECISIONS #122).
  const top = ctx.pages ? "" : `<div class="hero-top"><div class="hero-id">${x.face}${x.who}</div>${x.login}</div>\n`;
  return `<div class="hero-band"><div class="hero-in">
${top}<div class="hero-text enter">
${x.h1}
${x.lead}
${list.length ? chips(ctx, d) : x.button + x.chips}
</div>
<div class="hero-side">${side}</div>
</div></div>${trustBar(ctx)}`;
}

// The trust bar on the dark band's lower edge: years and clients (counting), then the highlight titles with icons.
function trustBar(ctx) {
  const c = ctx.site.content || {};
  const st = stats(c.about || {}, ctx).map((s) => `<li class="stat">${s.replace("<strong>", '<strong class="cu">')}</li>`);
  const hl = arr((c.highlights || {}).items).filter((i) => i && has(i.title)).map((i) => `<li>${icon(i.icon)}<span>${esc(i.title)}</span></li>`);
  return st.length || hl.length ? `<ul class="tbar">${[...st, ...hl].join("")}</ul>` : "";
}

// Inner pages: the shared page header on the dark band, with the trust bar on its edge (every page, DECISIONS #122).
export const pagehead = (ctx) => `<div class="hero-band">${pageHead(ctx)}</div>${trustBar(ctx)}`;
export const before = (ctx) => topBar(ctx);

// The offer wall: every offer but the featured one; the first spans two columns. With no offers, plan cards.
export function offers(d, ctx) {
  const wall = ctx.offers.grid;
  const title = has(d.title) ? d.title : ctx.t("site.offers_title_default");
  const disc = ctx.offers.all.length && has(ctx.site.company && ctx.site.company.disclaimer) ? `<p class="fine disc">${esc(ctx.site.company.disclaimer)}</p>` : "";
  if (wall.length) return `<h2>${esc(title)}</h2><ul class="offers wall stg">${wall.map((o) => offerCard(o, ctx, { image: true }).replace('<li class="offer"', '<li class="offer lift zoom"')).join("")}</ul>${disc}`;
  ctx.plansShown = true;
  const plans = planList(ctx).map((p) => planCard(p.key, p.desc, ctx).replace('<li class="offer plan"', '<li class="offer plan lift zoom"')).join("");
  return plans ? `<h2>${esc(ctx.t("site.plans_heading"))}</h2><ul class="offers stg">${plans}</ul>${disc}` : "";
}

// How I can help: picture tiles, four across on wide screens (compact tags when the plan cards already showed them).
export function services(d, ctx) {
  const { items, extra } = serviceList(d, ctx);
  const rows = [...items, ...extra.map((k) => ({ interest: k }))];
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
  const items = arr(d.items).filter((i) => i && has(i.quote) && has(i.name));
  if (!items.length) return "";
  const h = ctx.t("section.testimonials");
  return `<h2>${esc(h)}</h2>${slider(items.map((i) => `<figure class="card quote"><blockquote>${esc(i.quote)}</blockquote><figcaption class="meta">${esc(i.name)}${has(i.city) ? ", " + esc(i.city) : ""}</figcaption></figure>`), ctx, { label: h, cls: "sl-q", auto: 6000 })}`;
}
