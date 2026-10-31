// One offer card (OFFERS.md "Web renderer", DECISIONS #76/#77). Data comes only from the payload's resolved
// `offers` ({featured, items}); the public payload has no company name or logo, and this file never reads one.
import { esc, has, arr, tel, httpsUrl, interestLabel } from "./util.js";
import { pic, interestPic } from "./media.js";
import { contactHref } from "./links.js";
import { planById, planPageHref } from "./plan-data.js";
import { tr } from "./ui.js";

const DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
// Today in Asia/Kolkata as YYYY-MM-DD (a tab left open over midnight hides an offer that has just ended).
export const todayIST = (now = Date.now()) => new Date(now + 330 * 60000).toISOString().slice(0, 10);

// "31 Mar 2027" / "31 मार्च 2027"; "" for a malformed date.
export function formatDate(iso, locale) {
  const m = DATE.exec(String(iso || ""));
  if (!m) return "";
  try {
    return new Intl.DateTimeFormat(locale === "hi" ? "hi-IN" : "en-IN", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" })
      .format(new Date(Date.UTC(+m[1], +m[2] - 1, +m[3])));
  } catch { return iso; }
}

// A payload offer that can be shown: a title, and not ended (ends_on >= today IST).
function live(o, today) {
  if (!o || typeof o !== "object" || !has(o.title)) return false;
  return !DATE.test(String(o.ends_on || "")) || o.ends_on >= today;
}

// {hero, grid, all}: the offer shown in the hero banner and the ones for the offers section (no duplicates).
// The hero shows the agent's featured offer; on the offers family it falls back to the first offer, so a new
// "Best offers" site (auto offers, DECISIONS #79) still leads with an offer. The guide leads with needs instead.
export function pickOffers(site, ctx) {
  const p = site.offers || {};
  const items = arr(p.items).filter((o) => live(o, ctx.today));
  let hero = live(p.featured, ctx.today) ? p.featured : null;
  if (!hero && ctx.family === "offers" && ctx.layout !== "needs" && ctx.visible.has("offers") && items.length) hero = items[0];
  const grid = ctx.visible.has("offers") ? items.filter((o) => o !== hero && !(hero && has(hero.id) && o.id === hero.id)) : [];
  return { hero, grid, all: hero ? [hero, ...grid] : grid };
}

// The card's call to action; "" when it can't work (no phone, non-https link).
export function offerCta(o, ctx, cls = "lnk") {
  const c = (o && o.cta) || {};
  const { t } = ctx;
  if (c.kind === "enquire") {
    const k = c.interest ?? o.interest;
    const di = interestLabel(ctx, k) ? ` data-interest="${esc(k)}"` : "";
    return `<a class="${cls}" href="${esc(contactHref(ctx, di ? k : ""))}"${di}>${esc(t("site.offer_cta_enquire"))}</a>`;
  }
  if (c.kind === "call") {
    const href = tel(ctx.phone);
    return href ? `<a class="${cls}" href="${href}">${esc(t("site.offer_cta_call"))}</a>` : "";
  }
  if (c.kind === "link") {
    const href = httpsUrl(c.url);
    return href ? `<a class="${cls}" href="${href}" target="_blank" rel="noopener nofollow">${esc(t("site.offer_cta_link"))}</a>` : "";
  }
  return "";
}

// The offer's plan type: the payload's top-level `interest` (every resolved offer), else the enquire CTA's.
export const interestOf = (o) => (o && (o.interest ?? (o.cta && o.cta.interest))) || "";

// Card image (offers family only): the admin-uploaded https image_url; else a bundled demo image named by the
// payload's `image` (fixtures only, ^offer-…$, must be in the manifest); else the plan-type image of its interest.
function offerPic(o, ctx) {
  const sizes = "(min-width: 1024px) 280px, (min-width: 720px) 420px, 100vw";
  const up = httpsUrl(o.image_url);
  if (up) return `<img class="pic" src="${up}" width="800" height="450" alt="${esc(o.title)}" loading="lazy" decoding="async">`;
  if (/^offer-[a-z0-9-]+$/.test(String(o.image || ""))) { const b = pic(ctx, o.image, { alt: o.title, sizes }); if (b) return b; }
  return interestPic(ctx, interestOf(o), { sizes });
}

// opts: { tag: "li" | "div", image: bool, featured: bool (hero banner: no image, title not a heading),
//         button: bool (the CTA is the section's one button: the offers family's hero),
//         ttl: bool (the title is a <p class="ttl">, not a heading: a card inside the hero, which has only its <h1>),
//         noPlan: bool (no "View plan" link: the card is on that plan's own page) }
// An offer on an official plan the site shows (offers.plan_id, DECISIONS #130) links to the plan's page.
export function offerCard(o, ctx, opts = {}) {
  const { t } = ctx;
  const tag = opts.tag || "li";
  const src = o.from_agent ? t("site.offer_agent_note") : t("site.offer_pick");
  const ends = formatDate(o.ends_on, ctx.locale);
  const img = opts.image && !opts.featured ? offerPic(o, ctx) : "";
  const title = opts.featured || opts.ttl ? `<p class="ttl">${esc(o.title)}</p>` : `<h3>${esc(o.title)}</h3>`;
  const plan = opts.noPlan ? null : planById(ctx, o.plan_id);
  const planH = plan ? planPageHref(ctx, plan) : "";
  const onL = plan ? tr(ctx, "site.offer_on_plan", { plan: plan.name }) : "";
  const onPlan = onL ? `<p class="on-plan">${esc(onL)}</p>` : "";
  const viewL = tr(ctx, "site.pp_view");
  const view = planH && viewL ? `<a class="lnk" href="${esc(planH)}">${esc(viewL)}</a>` : "";
  return `<${tag} class="offer${opts.featured ? " feat" : ""}">${img}<div class="offer-b">
<p class="offer-top">${has(o.badge) ? `<span class="pill">${esc(o.badge)}</span>` : ""}<span class="src">${esc(src)}</span></p>
${title}${onPlan}${has(o.summary) ? `<p class="sum">${esc(o.summary)}</p>` : ""}
${has(o.details) ? `<details class="more"><summary>${esc(t("offer_field.details"))}</summary><p class="prose">${esc(o.details)}</p></details>` : ""}
<div class="offer-f">${ends ? `<p class="ends">${esc(t("site.offer_ends", { date: ends }))}</p>` : ""}
${view}${offerCta(o, ctx, opts.button ? "btn" : "lnk")}
${has(o.disclaimer) ? `<p class="fine">${esc(o.disclaimer)}</p>` : ""}</div>
</div></${tag}>`;
}

// A plan-type card (no offers yet): the interest's image, label, the agent's description and an enquire link.
export function planCard(key, desc, ctx, opts = {}) {
  const label = interestLabel(ctx, key);
  if (!label) return "";
  const img = interestPic(ctx, key, { sizes: "(min-width: 1024px) 280px, (min-width: 720px) 420px, 100vw" });
  return `<li class="offer plan">${img}<div class="offer-b">${opts.noTitle ? "" : `<h3>${esc(label)}</h3>`}${!opts.noDesc && has(desc) ? `<p class="sum">${esc(desc)}</p>` : ""}
<a class="lnk" href="${esc(contactHref(ctx, key))}" data-interest="${esc(key)}">${esc(ctx.t("site.offer_cta_enquire"))}</a></div></li>`;
}
