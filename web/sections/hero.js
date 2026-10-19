// The page <header>: photo (or initials monogram, never a stock face), the agent's greeting bubble, the single
// <h1>, quick-reply chips, the featured-offer banner (never an image) and, on the offers family, a hero scene.
import { esc, has, arr, textOr, interestLabel } from "./util.js";
import { initials, pic, fnv1a32 } from "./media.js";
import { offerCard } from "./offer.js";
import { needGroups } from "./offers.js";

const MAX_CHIPS = 4;

function chips(ctx, d) {
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

export function hero(d, ctx) {
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
  const face = photo
    ? `<img class="hero-photo" src="${photo}" width="160" height="160" alt="${esc(p.full_name)}" fetchpriority="high" decoding="async">`
    : ini ? `<span class="hero-photo mono" aria-hidden="true">${esc(ini)}</span>` : "";
  const greet = t("site.say_greeting", { firstName: ctx.firstName });
  const h1 = has(d.headline) ? d.headline : p.full_name;
  // No greeting when the headline already introduces the agent (e.g. the seeded "Hello, I'm Priya Nair").
  const hi = ctx.firstName && greet !== "site.say_greeting" && !String(h1 || "").includes(ctx.firstName) ? `<p class="bubble hi">${esc(greet)}</p>` : "";
  const banner = feat ? offerCard(feat, ctx, { tag: "div", featured: true, button: leadOffer }) + (!ctx.visible.has("offers") && has(ctx.site.company && ctx.site.company.disclaimer) ? `<p class="fine disc">${esc(ctx.site.company.disclaimer)}</p>` : "") : "";
  return `<div class="hero-in">
<div class="hero-id">${face}${hi}</div>
<div class="hero-text">
<h1>${esc(h1)}</h1>
${has(d.subheadline) ? `<p class="lead">${esc(d.subheadline)}</p>` : ""}
${sub ? `<p class="meta">${has(p.full_name) ? esc(p.full_name) + " · " : ""}${sub}</p>` : ""}
${cta ? `<a class="btn" href="#contact">${esc(cta)}</a>` : ""}
${leadOffer ? "" : chips(ctx, d)}
</div>
${ctx.family === "offers" ? side(banner, scene(ctx), leadOffer ? `<div class="hero-more">${chips(ctx, d)}</div>` : "") : banner ? `<div class="hero-offer">${banner}</div>` : ""}
</div>`;
}
