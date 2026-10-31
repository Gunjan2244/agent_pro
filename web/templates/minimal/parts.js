// Simple ("minimal", profile sidebar; DECISIONS #85, #117). On wide screens the agent's card (photo, name, Call /
// WhatsApp / Email, key facts) stays pinned on the left while the page reads like a tidy résumé on the right; on
// phones the card is a compact header. Text-led: no bundled illustrations, the portrait is the only image.
import { esc, has, arr, tel, wa, mail, interestLabel } from "../../sections/util.js";
import { heroParts } from "../../sections/hero.js";
import { aboutData } from "../../sections/about.js";
import { serviceList } from "../../sections/services.js";
import { icon } from "../../sections/icons.js";
import { offerCard } from "../../sections/offer.js";
import { companyList, companiesHeading } from "../../sections/companies.js";
import { slider, numbers, menuItems } from "../../sections/ui.js";
import { pageHead, planOrEnquire } from "../../sections/pages.js";
import { featuredSection, sec, band } from "../../sections/plan-page.js";

// The page <header> holds both columns' tops: the profile card (pinned) and the intro (h1, lead, chips, featured).
// With no contact links the card's one button is the hero CTA, linking to the form.
export function hero(d, ctx) {
  const x = heroParts(d, ctx, { photo: [96, 96] });
  return `<div class="hero-in">
${pcard(x, ctx)}
<div class="intro enter">
${x.h1}
${x.lead}
${x.chips}
${x.offer}
</div>
</div>`;
}

// Inner pages (DECISIONS #122): the same profile card column, the shared page header in the résumé column (without
// its picture column: the card shows the photo).
export function pagehead(ctx) {
  const x = heroParts((ctx.site.content || {}).hero || {}, ctx, { photo: [96, 96] });
  return `<div class="hero-in">
${pcard(x, ctx)}
<div class="intro">${pageHead(ctx, { pic: false })}</div>
</div>`;
}

// The profile card: photo, name, Call / WhatsApp / Email (or the hero CTA), facts with counting numbers, the page menu
// (a list in the pinned card; a row of tabs under the card on phones) and the customer login.
function pcard(x, ctx) {
  const { t, profile: p } = ctx;
  const c = (ctx.site.content || {}).contact || {}, a = (ctx.site.content || {}).about || {};
  const acts = [
    tel(ctx.phone) ? `<a class="btn" href="${tel(ctx.phone)}">${esc(t("site.call"))}</a>` : "",
    wa(c.whatsapp) ? `<a class="btn ghost" href="${wa(c.whatsapp)}" rel="noopener">${esc(t("site.whatsapp"))}</a>` : "",
    mail(c.email) ? `<a class="btn ghost" href="${mail(c.email)}">${esc(t("site.email"))}</a>` : "",
  ].filter(Boolean);
  const btns = acts.length ? acts.join("") : x.button;
  const { langs, code } = aboutData(a, ctx);
  const facts = [
    ...numbers(a, ctx).map((n) => `<li><span>${esc(n.label)}</span><strong class="cu">${esc(n.n)}</strong></li>`),
    langs.length ? `<li><span>${esc(t("field.languages_spoken"))}</span><strong>${langs.map(esc).join(", ")}</strong></li>` : "",
    has(c.officeHours) ? `<li><span>${esc(t("site.office_hours"))}</span><strong>${esc(c.officeHours)}</strong></li>` : "",
    code ? `<li><strong>${esc(code)}</strong></li>` : "",
  ].join("");
  const nav = menuItems(ctx, { login: false });
  return `<aside class="pcard">${x.face}<p class="who">${has(p.full_name) ? `<strong>${esc(p.full_name)}</strong>` : ""}${x.sub ? `<span>${x.sub}</span>` : ""}</p>${nav ? `<ul class="pc-nav">${nav}</ul>` : ""}${btns ? `<p class="pc-acts">${btns}</p>` : ""}${facts ? `<ul class="pc-facts">${facts}</ul>` : ""}${x.login.replace('class="hero-login"', 'class="pc-login"')}</aside>`;
}

// About: the bio as prose under a small label (the numbers live in the card).
export function about(d, ctx) {
  const { any } = aboutData(d, ctx);
  if (!any || !(has(d.bio) || has(d.designation))) return "";
  return `<h2>${esc(ctx.t("section.about"))}</h2>${has(d.designation) ? `<p class="kicker">${esc(d.designation)}</p>` : ""}${has(d.bio) ? `<p class="prose">${esc(d.bio)}</p>` : ""}`;
}

// How I can help: rows (name, description) each with an "Enquire" link that preselects the form.
export function services(d, ctx) {
  const { items, extra } = serviceList(d, ctx);
  const rows = [...items, ...extra.map((k) => ({ interest: k }))];
  if (!rows.length) return "";
  return `<h2>${esc(ctx.t("site.services_heading"))}</h2>
<ul class="rows">${rows.map((i) => `<li><div><h3>${esc(interestLabel(ctx, i.interest))}</h3>${has(i.description) ? `<p>${esc(i.description)}</p>` : ""}</div>${planOrEnquire(ctx, i.interest)}</li>`).join("")}</ul>`;
}

// Offers: bordered rows (no pictures).
export function offers(d, ctx) {
  const list = ctx.offers.grid;
  if (!list.length) return "";
  const title = has(d.title) ? d.title : ctx.t("site.offers_title_default");
  const disc = has(ctx.site.company && ctx.site.company.disclaimer) ? `<p class="fine disc">${esc(ctx.site.company.disclaimer)}</p>` : "";
  return `<h2>${esc(title)}</h2><ul class="offers stg">${list.map((o) => offerCard(o, ctx)).join("")}</ul>${disc}`;
}

// Why choose me: a two-column list, each with its icon.
export function highlights(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.highlights"))}</h2>
<ul class="ilist stg">${items.map((i) => `<li>${icon(i.icon)}<div><h3>${esc(i.title)}</h3>${has(i.text) ? `<p>${esc(i.text)}</p>` : ""}</div></li>`).join("")}</ul>`;
}

// Achievements: year | title rows.
export function achievements(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.achievements"))}</h2><ul class="years">${items.map((i) => `<li><span class="yr">${has(i.year) ? esc(i.year) : ""}</span><span>${esc(i.title)}</span></li>`).join("")}</ul>`;
}

// Kind words: a slider, two quotes per view on wide screens.
export function testimonials(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.quote) && has(i.name));
  if (!items.length) return "";
  const h = ctx.t("section.testimonials");
  return `<h2>${esc(h)}</h2>${slider(items.map((i) => `<figure class="kw"><blockquote>${esc(i.quote)}</blockquote><figcaption class="meta">${esc(i.name)}${has(i.city) ? ", " + esc(i.city) : ""}</figcaption></figure>`), ctx, { label: h, cls: "sl-kw", auto: 7000 })}`;
}

// Companies: a plain list, the name in bold, then its insurance types as text.
export function companies(d, ctx) {
  const list = companyList(ctx), h = companiesHeading(ctx);
  if (!list.length || !h) return "";
  return `<h2>${esc(h)}</h2>
<ul class="plain">${list.map((c) => `<li><strong>${esc(c.name)}</strong>${c.types.length ? ` <span>${c.types.map(esc).join(", ")}</span>` : ""}</li>`).join("")}</ul>`;
}

// Plans I recommend (DECISIONS #130): hairline rows with a small picture, like the rest of the résumé column.
export const featuredPlans = (d, ctx) => featuredSection(d, ctx, { layout: "rows" });

// A plan's page in the résumé column (the profile card stays pinned): the picture, then one labelled block each for
// the key features, the offer, the documents and similar plans.
export const planPage = (x) => band("pic", x.pic && `<div class="pl-pic">${x.pic}</div>`) + sec("features", x.features) + sec("about", x.about) + sec("offer", x.offer) + sec("docs", x.docs) + sec("similar", x.similarList) + band("fine", x.fine);
