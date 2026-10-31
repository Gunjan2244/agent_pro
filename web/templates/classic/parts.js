// Trust ("classic", your Theme A; DECISIONS #117): a top bar, a full-width banner with the headline on a dark panel,
// a ticker of highlights, a "today's offers" slider, an introduction (photo beside a dark panel with counting
// numbers), a ticker of companies, a 2×3 plans grid, a three-up "why choose me" slider and a policyholders slider.
// The data handling and escaping come from the shared helpers (heroParts, aboutData, serviceList, ...).
import { esc, has, arr, interestLabel, wa } from "../../sections/util.js";
import { heroParts } from "../../sections/hero.js";
import { aboutData } from "../../sections/about.js";
import { serviceList } from "../../sections/services.js";
import { pic, interestPic, fnv1a32 } from "../../sections/media.js";
import { icon } from "../../sections/icons.js";
import { offerCard } from "../../sections/offer.js";
import { companyList, companiesHeading } from "../../sections/companies.js";
import { slider, ticker, numbers, bigNumber, topBar } from "../../sections/ui.js";
import { planOrEnquire } from "../../sections/pages.js";
import { featuredSection, defaultPlanPage } from "../../sections/plan-page.js";

const scene = (ctx) => `hero-scene-${(fnv1a32(ctx.seed || ctx.agentName || "") % 3) + 1}`;

// The ticker under the banner: the agent's numbers, languages and highlight titles (only what the agent set).
function trustItems(ctx) {
  const c = ctx.site.content || {}, a = c.about || {};
  const out = numbers(a, ctx).map((x) => `<strong>${esc(x.n)}</strong> ${esc(x.label)}`);
  const { langs } = aboutData(a, ctx);
  if (langs.length) out.push(`${esc(ctx.t("field.languages_spoken"))}: ${langs.map(esc).join(", ")}`);
  for (const i of arr((c.highlights || {}).items)) if (i && has(i.title)) out.push(`${icon(i.icon)}${esc(i.title)}`);
  return out;
}

export function hero(d, ctx) {
  const x = heroParts(d, ctx, { photo: [56, 56] });
  const c = (ctx.site.content || {}).contact || {};
  const chat = wa(c.whatsapp);
  const img = pic(ctx, scene(ctx), { cls: "bn-img fade", sizes: "100vw", eager: true });
  const tk = ticker(trustItems(ctx), { cls: "tk-trust" });
  // the featured offer leads the offers slider; only when that section is off does it stay in the header
  const feat = !ctx.visible.has("offers") ? x.offer : "";
  return `<div class="banner${img ? "" : " no-img"}">${img}<div class="bn-panel enter">
<div class="hero-id">${x.face}${x.who}</div>
${x.h1}
${x.lead}
<p class="bn-act">${x.button}${chat ? `<a class="btn ghost" href="${chat}" rel="noopener">${esc(ctx.t("site.whatsapp"))}</a>` : ""}</p>
${x.chips}
</div></div>${tk}${feat}`;
}

// Today's offers: every live offer (the featured one first) as a slider of wide picture cards.
export function offers(d, ctx) {
  const list = ctx.offers.all;
  if (!list.length || !ctx.visible.has("offers")) return "";
  const title = has(d.title) ? d.title : ctx.t("site.offers_title_default");
  const disc = has(ctx.site.company && ctx.site.company.disclaimer) ? `<p class="fine disc">${esc(ctx.site.company.disclaimer)}</p>` : "";
  return `<h2>${esc(title)}</h2>${slider(list.map((o) => offerCard(o, ctx, { tag: "div", image: true })), ctx, { label: title, cls: "sl-offers", auto: 5000 })}${disc}`;
}

// Introduction: the agent's photo (or the "let's talk" picture) beside a dark panel: heading, designation, bio,
// counting numbers, languages and agency code.
export function about(d, ctx) {
  const { langs, code, any } = aboutData(d, ctx);
  if (!any) return "";
  const nums = numbers(d, ctx);
  const photo = ctx.photo
    ? `<img class="in-img" src="${ctx.photo}" width="640" height="640" alt="${esc(ctx.profile.full_name)}" loading="lazy" decoding="async">`
    : pic(ctx, "contact-talk", { cls: "in-img", sizes: "(min-width: 1024px) 540px, 100vw" });
  const facts = [
    langs.length ? `<li><span class="fact-k">${esc(ctx.t("field.languages_spoken"))}</span> ${langs.map(esc).join(", ")}</li>` : "",
    code ? `<li>${esc(code)}</li>` : "",
  ].join("");
  return `<div class="intro">${photo ? `<div class="in-pic">${photo}</div>` : ""}<div class="in-panel">
<h2>${esc(ctx.t("section.about"))}</h2>
${has(d.designation) ? `<p class="kicker">${esc(d.designation)}</p>` : ""}${has(d.bio) ? `<p class="prose">${esc(d.bio)}</p>` : ""}
${nums.length ? `<ul class="in-nums">${nums.map((n) => bigNumber(n)).join("")}</ul>` : ""}${facts ? `<ul class="facts">${facts}</ul>` : ""}
</div></div>`;
}

// Companies I represent: a ticker band ("Name · Life, Health") that scrolls the other way.
export function companies(d, ctx) {
  const list = companyList(ctx), h = companiesHeading(ctx);
  if (!list.length || !h) return "";
  return `<h2>${esc(h)}</h2>${ticker(list.map((c) => `<strong>${esc(c.name)}</strong>${c.types.length ? ` <span>${c.types.map(esc).join(", ")}</span>` : ""}`), { cls: "tk-rev tk-cos", label: h })}`;
}

// Insurance plans: a 2 × 3 grid (1 column on phones, 2 from 720 px, 3 from 1024 px) of picture cards that lift.
export function services(d, ctx) {
  const { items, extra } = serviceList(d, ctx);
  const rows = [...items, ...extra.map((k) => ({ interest: k }))];
  if (!rows.length) return "";
  return `<h2>${esc(ctx.t("site.services_heading"))}</h2>
<ul class="plans stg">${rows.map((i) => `<li class="plan-c lift zoom">${interestPic(ctx, i.interest, { sizes: "(min-width: 1024px) 340px, (min-width: 720px) 50vw, 100vw" })}<div class="plan-b"><h3>${esc(interestLabel(ctx, i.interest))}</h3>${has(i.description) ? `<p>${esc(i.description)}</p>` : ""}${planOrEnquire(ctx, i.interest, "lnk ask")}</div></li>`).join("")}</ul>`;
}

// Why choose me: the highlights as icon cards, three per view, sliding one card at a time.
export function highlights(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  if (!items.length) return "";
  const h = ctx.t("section.highlights");
  return `<h2>${esc(h)}</h2>${slider(items.map((i) => `<div class="card hl-c">${icon(i.icon)}<h3>${esc(i.title)}</h3>${has(i.text) ? `<p>${esc(i.text)}</p>` : ""}</div>`), ctx, { label: h, cls: "sl-three", auto: 4500 })}`;
}

// Our policyholders: quote cards in a slider that runs off the right edge on wide screens.
export function testimonials(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.quote) && has(i.name));
  if (!items.length) return "";
  const h = ctx.t("site.policyholders_heading");
  const head = h && h !== "site.policyholders_heading" ? h : ctx.t("section.testimonials");
  return `<h2>${esc(head)}</h2>${slider(items.map((i) => `<figure class="card quote"><blockquote>${esc(i.quote)}</blockquote><figcaption class="meta">${esc(i.name)}${has(i.city) ? ", " + esc(i.city) : ""}</figcaption></figure>`), ctx, { label: head, cls: "sl-quotes", auto: 6000 })}`;
}

export const before = (ctx) => topBar(ctx);

// Plans I recommend (DECISIONS #130): the featured official plans as picture cards in a slider, like the offers.
export const featuredPlans = (d, ctx) => featuredSection(d, ctx, { layout: "slider" });

// A plan's page: the shared arrangement (features, note and offer beside the documents and a dark "Talk to …" card
// that stays in view), under a dark header with the picture on the right (style.css).
export const planPage = (x) => defaultPlanPage(x);
