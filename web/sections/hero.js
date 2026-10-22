// The page <header>: photo (or initials monogram, never a stock face) with the agent's name, designation and city,
// the single <h1>, the lead, one button, quick-interest chips, the featured-offer banner (never an image) and, on the
// offers family, a hero scene. No greeting bubble (DECISIONS #87).
import { esc, has, arr, textOr, interestLabel } from "./util.js";
import { initials, pic, fnv1a32 } from "./media.js";
import { offerCard } from "./offer.js";
import { needGroups } from "./offers.js";

const MAX_CHIPS = 4;

export function chips(ctx, d) {
  const { t } = ctx;
  // Guide: "What would you like to protect?" jumps to the anchored groups of the offers section (no JS needed).
  if (ctx.layout === "needs" && ctx.visible.has("offers")) {
    const g = needGroups(ctx);
    if (g.length) return `<p class="qr-h">${esc(t("site.guide_question"))}</p><ul class="qr">${g.map((x) => `<li><a class="chip" href="#need-${esc(x.key)}">${esc(interestLabel(ctx, x.key))}</a></li>`).join("")}</ul>`;
  }
  const sv = (ctx.site.content || {}).services || {};
  const keys = [...new Set(arr(sv.offered))].filter((k) => interestLabel(ctx, k)).slice(0, MAX_CHIPS);
  if (!keys.length) return "";
  // Without JS they just jump to the form; hydrate.js also preselects the interest.
  return `<p class="qr-h">${esc(t("site.quick_reply_heading"))}</p><ul class="qr">${keys.map((k) => `<li><a class="chip" href="#contact" data-interest="${esc(k)}">${esc(interestLabel(ctx, k))}</a></li>`).join("")}</ul>`;
}

// Offers family: one of the 3 bundled scenes, picked from the slug (or host) so it is stable per site.
function scene(ctx) {
  if (ctx.family !== "offers") return "";
  const n = (fnv1a32(ctx.seed || ctx.agentName || "") % 3) + 1;
  return pic(ctx, `hero-scene-${n}`, { cls: "scene", sizes: "(min-width: 720px) 440px, 100vw", low: true });
}

// Offers family: the featured card and the scene share one column (`.hero-side`): the right-hand column on wide
// screens; on phones it is `display: contents`, so CSS `order` puts the chips before the scene.
const side = (banner, sceneHtml, more) => `${more}<div class="hero-side">${banner ? `<div class="hero-offer">${banner}</div>` : ""}${sceneHtml}</div>`;

// The hero's pieces, shared by every template; templates that arrange the hero differently (modern, minimal) take
// these instead of re-deriving them, so the data handling and escaping live here once.
// opts.photo = [w, h]: the photo's width/height attributes (a template whose CSS gives it another aspect ratio).
export function heroParts(d, ctx, opts = {}) {
  const { t, profile: p } = ctx;
  const photo = ctx.photo;
  const sub = [p.designation, p.city].filter(has).map(esc).join(" · ");
  const feat = ctx.offers.hero;
  // One call to action: on the offers family the featured offer's button replaces the generic one.
  // The guide leads with its needs chips instead (they jump to the offer groups), and its banner uses a link.
  const needs = ctx.layout === "needs" && ctx.visible.has("offers") && needGroups(ctx).length > 0;
  const leadOffer = Boolean(feat) && ctx.family === "offers" && !needs;
  const cta = leadOffer || needs ? "" : textOr(ctx, "hero", "ctaLabel", d.ctaLabel);
  const ini = initials(p.full_name);
  const [pw, ph] = opts.photo || [160, 160];
  const face = photo
    ? `<img class="hero-photo" src="${photo}" width="${pw}" height="${ph}" alt="${esc(p.full_name)}" fetchpriority="high" decoding="async">`
    : ini ? `<span class="hero-photo mono" aria-hidden="true">${esc(ini)}</span>` : "";
  const h1 = has(d.headline) ? d.headline : p.full_name;
  const banner = feat ? offerCard(feat, ctx, { tag: "div", featured: true, button: leadOffer }) + (!ctx.visible.has("offers") && has(ctx.site.company && ctx.site.company.disclaimer) ? `<p class="fine disc">${esc(ctx.site.company.disclaimer)}</p>` : "") : "";
  return {
    face, ini, sub, cta, leadOffer, banner,
    // "Asha Verma" over "Insurance Advisor · Pune": who the page is about, beside or under the portrait.
    who: has(p.full_name) || sub ? `<p class="who">${has(p.full_name) ? `<strong>${esc(p.full_name)}</strong>` : ""}${sub ? `<span>${sub}</span>` : ""}</p>` : "",
    h1: `<h1>${esc(h1)}</h1>`,
    lead: has(d.subheadline) ? `<p class="lead">${esc(d.subheadline)}</p>` : "",
    meta: sub ? `<p class="meta">${has(p.full_name) ? esc(p.full_name) + " · " : ""}${sub}</p>` : "",
    button: cta ? `<a class="btn" href="#contact">${esc(cta)}</a>` : "",
    chips: leadOffer ? "" : chips(ctx, d),
    offer: banner ? `<div class="hero-offer">${banner}</div>` : "",
  };
}

export function hero(d, ctx) {
  const x = heroParts(d, ctx);
  return `<div class="hero-in">
<div class="hero-id">${x.face}${x.who}</div>
<div class="hero-text">
${x.h1}
${x.lead}
${x.button}
${x.chips}
</div>
${ctx.family === "offers" ? side(x.banner, scene(ctx), x.leadOffer ? `<div class="hero-more">${chips(ctx, d)}</div>` : "") : x.offer}
</div>`;
}
