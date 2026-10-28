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
import { needGroups } from "../../sections/offers.js";
import { slider, ticker, topBar, tr } from "../../sections/ui.js";
import { needHref } from "../../sections/hero.js";
import { pageHref } from "../../sections/links.js";
import { INNER, seePlans } from "../../sections/pages.js";

const STEPS = ["site.step_1", "site.step_2", "site.step_3"];

export function hero(d, ctx) {
  const x = heroParts(d, ctx, { photo: [56, 56] });
  const groups = ctx.visible.has("offers") ? needGroups(ctx) : [];
  const q = tr(ctx, "site.guide_question");
  const see = tr(ctx, "site.see_plans");
  const tiles = groups.length
    ? `<p class="qr-h">${esc(q)}</p><ul class="needs-t stg">${groups.map((g) => `<li><a class="nt lift zoom" href="${esc(needHref(ctx, g.key))}">${interestPic(ctx, g.key, { sizes: "(min-width: 1024px) 340px, 50vw" })}<span class="nt-b"><strong>${esc(interestLabel(ctx, g.key))}</strong>${see ? `<span class="lnk">${esc(see)}</span>` : ""}</span></a></li>`).join("")}</ul>`
    : `${x.button}${chips(ctx, d)}`;
  // an explicit featured offer: a slim strip with a text link (no promoted card)
  const f = ctx.offers.hero;
  const strip = f ? `<p class="feat-s"><span class="pill">${esc(has(f.badge) ? f.badge : ctx.t("site.offer_pick"))}</span><span>${esc(f.title)}</span><a class="lnk" href="${esc(ctx.pages ? pageHref(ctx, "insurance", { anchor: "offers" }) || "#offers" : "#offers")}">${esc(ctx.t("offer_field.details"))}</a></p>` : "";
  const steps = STEPS.map((k) => tr(ctx, k)).filter(Boolean);
  const how = steps.length === 3 && tr(ctx, "site.steps_heading")
    ? `<div class="steps"><p class="st-h">${esc(tr(ctx, "site.steps_heading"))}</p><ol>${steps.map((s, i) => `<li><span class="st-n" aria-hidden="true">${i + 1}</span>${esc(s)}</li>`).join("")}</ol></div>`
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
    return `<h2>${esc(title)}</h2><ul class="offers stg">${list.map((x) => offerCard(x, ctx, { image: true }).replace('<li class="offer"', '<li class="offer lift"')).join("")}</ul>`;
  }
  const groups = needGroups(ctx);
  if (!groups.length) return "";
  ctx.plansShown = true;
  const o = ctx.offers;
  const title = o.grid.length ? (has(d.title) ? d.title : ctx.t("site.offers_title_default")) : ctx.t("site.plans_heading");
  const idx = `<div class="need-idx"><ul>${groups.map((g) => `<li><a href="#need-${esc(g.key)}">${esc(interestLabel(ctx, g.key))}${g.offers.length ? ` <span>${g.offers.length}</span>` : ""}</a></li>`).join("")}</ul></div>`;
  const body = groups.map((g) => {
    const cards = g.offers.length ? g.offers.map((x) => offerCard(x, ctx, { image: true }).replace('<li class="offer"', '<li class="offer lift"')).join("") : planCard(g.key, g.desc, ctx, { noTitle: true, noDesc: true });
    return `<div class="need" id="need-${esc(g.key)}"><div class="need-h">${interestPic(ctx, g.key, { sizes: "96px" })}<div><h3>${esc(interestLabel(ctx, g.key))}</h3>${has(g.desc) ? `<p class="need-d">${esc(g.desc)}</p>` : ""}${seePlans(ctx, g.key)}</div></div><ul class="offers">${cards}</ul></div>`;
  }).join("");
  const disc = o.all.length && has(ctx.site.company && ctx.site.company.disclaimer) ? `<p class="fine disc">${esc(ctx.site.company.disclaimer)}</p>` : "";
  return `<h2>${esc(title)}</h2><div class="needs">${idx}<div class="need-list">${body}</div></div>${disc}`;
}

// Trust: years and clients (counting) and the highlight titles, as a ticker band.
export function highlights(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  const st = stats((ctx.site.content || {}).about || {}, ctx).map((s) => s.replace("<strong>", '<strong class="cu">'));
  if (!items.length && !st.length) return "";
  const h = ctx.t("section.highlights");
  return `<h2>${esc(h)}</h2>${ticker([...st, ...items.map((i) => `${icon(i.icon)}${esc(i.title)}`)], { cls: "tk-trust", label: h })}`;
}

// Testimonials: one big quote at a time.
export function testimonials(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.quote) && has(i.name));
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

// Every inner page ends with the green trust ticker before the footer (not a section: no heading; the Home page
// has it as its highlights section).
export function afterInner(ctx) {
  const c = ctx.site.content || {};
  const st = stats(c.about || {}, ctx).map((s) => s.replace("<strong>", '<strong class="cu">'));
  const items = [...st, ...arr((c.highlights || {}).items).filter((i) => i && has(i.title)).map((i) => `${icon(i.icon)}${esc(i.title)}`)];
  return items.length ? `<div class="g-trust">${ticker(items, { cls: "tk-trust", label: ctx.t("section.highlights") })}</div>` : "";
}
