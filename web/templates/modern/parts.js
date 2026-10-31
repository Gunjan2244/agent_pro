// Fresh ("modern", editorial split; DECISIONS #85, #117). A sticky top bar, a big left-aligned headline beside a tall
// portrait with a floating badge of counting numbers, a companies marquee, about with big numbers, a bento grid of
// services, a wide lead offer then offer rows, numbered "why choose me" columns, a rotating pull quote, the FAQ
// beside its heading, and a dark contact section. Data handling and escaping come from the shared helpers.
import { esc, has, arr, interestLabel, wa, tel } from "../../sections/util.js";
import { heroParts } from "../../sections/hero.js";
import { aboutData } from "../../sections/about.js";
import { serviceList } from "../../sections/services.js";
import { interestPic } from "../../sections/media.js";
import { icon } from "../../sections/icons.js";
import { offerCard } from "../../sections/offer.js";
import { companyList, companiesHeading } from "../../sections/companies.js";
import { slider, ticker, numbers, bigNumber, topBar, tr } from "../../sections/ui.js";
import { planOrEnquire } from "../../sections/pages.js";
import { contactHref } from "../../sections/links.js";
import { sec, box, band, grid } from "../../sections/plan-page.js";
import { featuredPlans as featuredList, planFacts, planPageHref, planPicture, planOffers } from "../../sections/plan-data.js";

const num = (n) => String(n).padStart(2, "0");

// Phones: the photo full width (4:3) above the text. Wide: text left (7), the 4:5 portrait right (5) on a tinted
// block, with the badge of numbers overlapping its lower left corner. Explicit width/height (no CLS).
export function hero(d, ctx) {
  const x = heroParts(d, ctx, { photo: [640, 800] });
  const chat = wa(((ctx.site.content || {}).contact || {}).whatsapp);
  const nums = numbers((ctx.site.content || {}).about || {}, ctx);
  const badge = nums.length ? `<ul class="badge">${nums.map((n) => bigNumber(n)).join("")}</ul>` : "";
  return `<div class="hero-in">
<div class="hero-text enter">
${x.meta}
${x.h1}
${x.lead}
<p class="acts">${x.button}${chat ? `<a class="btn ghost" href="${chat}" rel="noopener">${esc(ctx.t("site.whatsapp"))}</a>` : ""}</p>
${x.chips}
</div>
<div class="cover fade">${x.face}${badge}</div>
</div>${ctx.visible.has("offers") ? "" : x.offer}`;
}

// About: the bio on the left; the numbers big on the right, then languages and the agency code.
export function about(d, ctx) {
  const { langs, code, any } = aboutData(d, ctx);
  if (!any) return "";
  const nums = numbers(d, ctx);
  const facts = [
    ...nums.map((n) => bigNumber(n, "fact-n")),
    langs.length ? `<li class="fact-t"><strong>${langs.map(esc).join(", ")}</strong><span>${esc(ctx.t("field.languages_spoken"))}</span></li>` : "",
    code ? `<li class="fact-t"><strong>${esc(code)}</strong></li>` : "",
  ].join("");
  return `<div class="about-grid"><div><h2>${esc(ctx.t("section.about"))}</h2>${has(d.designation) ? `<p class="kicker">${esc(d.designation)}</p>` : ""}${has(d.bio) ? `<p class="prose">${esc(d.bio)}</p>` : ""}</div>${facts ? `<ul class="facts">${facts}</ul>` : ""}</div>`;
}

// Services as a bento grid: numbered tiles; the first is large with its picture, the rest compact with a thumbnail.
export function services(d, ctx) {
  const { items, extra } = serviceList(d, ctx);
  const rows = [...items, ...extra.map((k) => ({ interest: k }))];
  if (!rows.length) return "";
  return `<h2>${esc(ctx.t("site.services_heading"))}</h2>
<ol class="bento stg">${rows.map((i, n) => `<li class="bt lift zoom${n === 0 ? " bt-big" : ""}">${interestPic(ctx, i.interest, { sizes: n === 0 ? "(min-width: 1024px) 360px, 100vw" : "128px" })}<div class="bt-b"><span class="bt-n" aria-hidden="true">${num(n + 1)}</span><h3>${esc(interestLabel(ctx, i.interest))}</h3>${has(i.description) ? `<p>${esc(i.description)}</p>` : ""}${planOrEnquire(ctx, i.interest)}</div></li>`).join("")}</ol>`;
}

// Offers: the lead offer (the featured one, else the first) wide with its picture, the rest as hairline rows.
export function offers(d, ctx) {
  const list = ctx.visible.has("offers") ? ctx.offers.all : [];
  if (!list.length) return "";
  const title = has(d.title) ? d.title : ctx.t("site.offers_title_default");
  const [lead, ...rest] = list;
  const disc = has(ctx.site.company && ctx.site.company.disclaimer) ? `<p class="fine disc">${esc(ctx.site.company.disclaimer)}</p>` : "";
  return `<h2>${esc(title)}</h2>${offerCard(lead, ctx, { tag: "div", image: true }).replace('class="offer"', 'class="offer of-lead lift zoom"')}${rest.length ? `<ul class="of-rows stg">${rest.map((o) => offerCard(o, ctx)).join("")}</ul>` : ""}${disc}`;
}

// Why choose me: numbered columns under a heavy rule; the icon beside the number.
export function highlights(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.highlights"))}</h2>
<ol class="hl-cols stg">${items.map((i, n) => `<li><p class="hl-n"><span aria-hidden="true">${num(n + 1)}</span>${icon(i.icon)}</p><h3>${esc(i.title)}</h3>${has(i.text) ? `<p>${esc(i.text)}</p>` : ""}</li>`).join("")}</ol>`;
}

// Testimonials: one large pull quote at a time, rotating.
export function testimonials(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.quote) && has(i.name));
  if (!items.length) return "";
  const h = ctx.t("section.testimonials");
  return `<h2>${esc(h)}</h2>${slider(items.map((i) => `<figure class="pull"><blockquote>${esc(i.quote)}</blockquote><figcaption>${esc(i.name)}${has(i.city) ? ", " + esc(i.city) : ""}</figcaption></figure>`), ctx, { label: h, cls: "sl-pull", auto: 6500 })}`;
}

// FAQ: the heading and a call button on the left (sticky on wide screens), the accordion on the right.
export function faq(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.question) && has(i.answer));
  if (!items.length) return "";
  const call = tel(ctx.phone);
  const btn = call ? `<a class="btn ghost" href="${call}">${esc(ctx.t("site.call"))}</a>` : `<a class="btn ghost" href="${esc(contactHref(ctx))}">${esc(ctx.t("site.cta_default"))}</a>`;
  return `<div class="faq-grid"><div class="faq-side"><h2>${esc(ctx.t("section.faq"))}</h2>${btn}</div><div class="faq">${items
    .map((i) => `<details><summary>${esc(i.question)}</summary><p class="prose">${esc(i.answer)}</p></details>`).join("")}</div></div>`;
}

// Companies: a full-width marquee band of names (with their insurance types).
export function companies(d, ctx) {
  const list = companyList(ctx), h = companiesHeading(ctx);
  if (!list.length || !h) return "";
  return `<h2>${esc(h)}</h2>${ticker(list.map((c) => `<h3>${esc(c.name)}</h3>${c.types.length ? `<span>${c.types.map(esc).join(", ")}</span>` : ""}`), { cls: "tk-cos", label: h })}`;
}

export const before = (ctx) => topBar(ctx, { cta: "contact" });

// Plans I recommend (DECISIONS #130): large editorial rows, the picture beside the plan type and company, the name in
// big type, its facts, the offer on it, "Read about the plan".
export function featuredPlans(d, ctx) {
  const list = featuredList(ctx), h = tr(ctx, "site.featured_heading");
  if (!list.length || !h) return "";
  const lead = tr(ctx, "site.featured_lead"), read = tr(ctx, "site.pp_view");
  const rows = list.map((p) => {
    const href = planPageHref(ctx, p), off = planOffers(ctx, p)[0], facts = planFacts(ctx, p);
    const body = `${planPicture(ctx, p, { sizes: "(min-width: 1024px) 420px, 100vw" })}<span class="fr-b"><span class="kicker">${esc(interestLabel(ctx, p.interest))} · ${esc(p.company)}</span><strong class="fr-h">${esc(p.name)}</strong>${facts ? `<span class="meta">${esc(facts)}</span>` : ""}${off ? `<span class="fr-off">${esc(off.title)}</span>` : ""}${href && read ? `<span class="lnk">${esc(read)}</span>` : ""}</span>`;
    return `<li class="frow">${href ? `<a href="${esc(href)}">${body}</a>` : `<div>${body}</div>`}</li>`;
  }).join("");
  return `<h2>${esc(h)}</h2>${lead ? `<p class="lead sub">${esc(lead)}</p>` : ""}<ul class="frows">${rows}</ul>`;
}

// A plan's page: the big title over a row of facts and a wide picture (style.css), then numbered key features, the
// agent's note and the offer beside the documents as a table and the buttons; related plans as numbered rows.
export function planPage(x) {
  const main = box("features", x.numbered, "pl-block") + box("about", x.about, "pl-block") + box("offer", x.offer, "pl-block pl-offer") + x.fine;
  const side = box("docs", x.docs, "pl-docs-box") + (x.ask ? `<p class="pl-acts">${x.ask}</p>` : "");
  return band("facts", x.facts) + grid(main, side) + sec("similar", x.similarRows, "alt");
}
