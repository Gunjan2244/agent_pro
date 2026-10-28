// Multi-page sites (DECISIONS #122; the page model is shared/site-pages.json). The inner pages (Insurance, one page
// per plan type, About me, Contact) are SHARED layouts: the same markup on all five templates, which restyle it in
// their own style.css. Only Home is template-specific (each template's hero and home sections are its identity); a
// template overrides a shared part only where its structure needs it (minimal's profile card, spotlight's trust bar,
// guide's need groups). This file holds the shared inner-page parts: the page header (breadcrumb, the page's one
// <h1>, lead; the plan page's split header; the About story), the plan-type cards, the plan page's offers and "Other
// plans", the About bands (companies ticker, icon cards, timeline), the Contact layout, the help band, and Home's
// teasers and contact band. Render-only.
import { esc, has, arr, tel, wa, mail, url, interestLabel } from "./util.js";
import { interestPic, initials } from "./media.js";
import { interestOf, offerCard } from "./offer.js";
import { aboutData } from "./about.js";
import { companyList, companiesHeading } from "./companies.js";
import { form } from "./contact.js";
import { icon } from "./icons.js";
import { tr, ticker, slider, numbers, bigNumber } from "./ui.js";
import { pageHref, planHref, contactHref } from "./links.js";
import { pageUrl } from "../route.js";

// The interests that get their own page: those the agent names (services items, then `offered`), then those of the
// live offers; on a "Best offers" site that names none, every active catalog interest (as planList does, so its
// Insurance page is never empty). Only real plan types: an interest with no insurance type ("other") has no page.
export function planKeys(ctx) {
  const all = arr(ctx.catalog.interests);
  const ok = (k) => { const i = all.find((x) => x.key === k); return Boolean(i && !i.deprecated && i.insuranceType != null && interestLabel(ctx, k)); };
  const sv = (ctx.site.content || {}).services || {};
  const out = [];
  const add = (k) => { if (ok(k) && !out.includes(k)) out.push(k); };
  for (const i of arr(sv.items)) if (i) add(i.interest);
  for (const k of arr(sv.offered)) add(k);
  for (const o of arr(ctx.offers && ctx.offers.all)) add(interestOf(o));
  if (!out.length && ctx.family === "offers") for (const i of all.slice().sort((a, b) => a.order - b.order)) add(i.key);
  return out;
}
// The agent's own description of a plan type (services items), or "".
export const planDesc = (ctx, key) => { const i = arr(((ctx.site.content || {}).services || {}).items).find((x) => x && x.interest === key); return i && has(i.description) ? i.description : ""; };

// The page's title (its <h1>) and lead line.
export function pageTitle(ctx, page = ctx.page) {
  if (page.key === "plan") return { title: interestLabel(ctx, page.plan), lead: planDesc(ctx, page.plan) };
  const d = page.def || {};
  const title = tr(ctx, d.titleKey || d.labelKey) || ctx.profile.full_name || "";
  let lead = d.leadKey ? tr(ctx, d.leadKey) : "";
  if (page.key === "about") lead = [((ctx.site.content || {}).about || {}).designation || ctx.profile.designation, ctx.profile.city].filter(has).join(" · ");
  return { title, lead };
}

// "Home › Insurance › Term insurance": a text line (the call bar stays the page's only <nav>).
export function crumb(ctx) {
  const parts = [];
  const add = (p) => { if (p && tr(ctx, p.def.labelKey)) parts.push(`<a href="${esc(pageUrl(ctx.links, p.path))}">${esc(tr(ctx, p.def.labelKey))}</a>`); };
  const pages = ctx.pages || [];
  add(pages.find((p) => p.key === "home"));
  if (ctx.page.def.parent) add(pages.find((p) => p.key === ctx.page.def.parent && !p.plan));
  parts.push(`<span aria-current="page">${esc(pageTitle(ctx).title)}</span>`);
  return `<p class="crumb">${parts.join('<span class="sep" aria-hidden="true">›</span>')}</p>`;
}

const waBtn = (ctx) => { const c = (ctx.site.content || {}).contact || {}; const h = wa(c.whatsapp); return h ? `<a class="btn ghost" href="${h}" rel="noopener">${esc(ctx.t("site.whatsapp"))}</a>` : ""; };
const formOn = (ctx) => ((ctx.site.content || {}).contact || {}).showEnquiryForm !== false;

// The header of an inner page (the page's <header>, one <h1>). opts.pic = false leaves the picture column out
// (minimal: its profile card already shows the photo).
export function pageHead(ctx, { pic = true } = {}) {
  const { page, t } = ctx;
  const { title, lead } = pageTitle(ctx);
  const h1 = `<h1>${esc(title)}</h1>`;
  if (page.key === "plan") {
    const k = page.plan, ask = tr(ctx, "site.plan_ask") || t("site.offer_cta_enquire");
    const img = pic ? interestPic(ctx, k, { sizes: "(min-width: 1024px) 420px, (min-width: 720px) 40vw, 100vw", eager: true }) : "";
    return `<div class="ph-in ph-split${img ? "" : " no-pic"}">${img ? `<div class="ph-pic fade">${img}</div>` : ""}<div class="ph-text enter">${crumb(ctx)}${h1}${has(lead) ? `<p class="lead">${esc(lead)}</p>` : ""}<p class="ph-act"><a class="btn" href="${esc(contactHref(ctx, k))}" data-interest="${esc(k)}">${esc(ask)}</a>${waBtn(ctx)}</p></div></div>`;
  }
  if (page.key === "about") {
    // The About story: the photo (4:5) beside the agent's own words, numbers that count up, languages, agency code.
    const a = (ctx.site.content || {}).about || {};
    const on = ctx.allVisible.has("about");
    const { langs, code } = on ? aboutData(a, ctx) : { langs: [], code: "" };
    const nums = on ? numbers(a, ctx) : [];
    const ini = initials(ctx.profile.full_name);
    const photo = !pic ? "" : ctx.photo
      ? `<img class="ph-photo" src="${ctx.photo}" width="640" height="800" alt="${esc(ctx.profile.full_name)}" fetchpriority="high" decoding="async">`
      : ini ? `<span class="ph-photo mono" aria-hidden="true">${esc(ini)}</span>` : "";
    const facts = [langs.length ? `<li><span class="fact-k">${esc(t("field.languages_spoken"))}</span> ${langs.map(esc).join(", ")}</li>` : "", code ? `<li>${esc(code)}</li>` : ""].join("");
    const kicker = on && has(a.designation) ? a.designation : lead;
    return `<div class="ph-in ph-split ph-story${photo ? "" : " no-pic"}">${photo ? `<div class="ph-pic fade">${photo}</div>` : ""}<div class="ph-text enter">${crumb(ctx)}${h1}${has(kicker) ? `<p class="kicker">${esc(kicker)}</p>` : ""}${on && has(a.bio) ? `<p class="prose">${esc(a.bio)}</p>` : ""}${nums.length ? `<ul class="ph-nums">${nums.map((n) => bigNumber(n)).join("")}</ul>` : ""}${facts ? `<ul class="facts">${facts}</ul>` : ""}</div></div>`;
  }
  // Insurance lists its plan types as chips here only when the plan cards (the services section) are hidden.
  const plans = page.key === "insurance" && !ctx.visible.has("services") ? (ctx.pages || []).filter((p) => p.plan) : [];
  const chips = plans.length ? `<ul class="qr">${plans.map((p) => `<li><a class="chip" href="${esc(pageUrl(ctx.links, p.path))}">${esc(interestLabel(ctx, p.plan))}</a></li>`).join("")}</ul>` : "";
  return `<div class="ph-in"><div class="ph-text enter">${crumb(ctx)}${h1}${has(lead) ? `<p class="lead">${esc(lead)}</p>` : ""}${chips}</div></div>`;
}

const offerCount = (ctx, key) => arr(ctx.offers && ctx.offers.all).filter((o) => interestOf(o) === key).length;

// Insurance: one card per plan type (picture, name, the agent's description, "See plan", "N offers"); the whole
// card is the link. 3 per row on desktop, compact rows with the picture on the left on phones (base.css).
export function planCards(d, ctx) {
  const list = (ctx.pages || []).filter((p) => p.plan);
  if (!list.length) return "";
  const see = tr(ctx, "site.see_plan") || tr(ctx, "site.see_plans");
  return `<h2>${esc(ctx.t("site.services_heading"))}</h2><ul class="ptypes stg">${list.map((p, n) => {
    const k = p.plan, c = offerCount(ctx, k), desc = planDesc(ctx, k);
    const count = c ? `<span class="pill">${esc(tr(ctx, c === 1 ? "site.plan_offer_one" : "site.plan_offers", { n: c }))}</span>` : "";
    return `<li class="ptype lift zoom"><a href="${esc(pageUrl(ctx.links, p.path))}">${interestPic(ctx, k, { sizes: "(min-width: 1024px) 340px, (min-width: 720px) 50vw, 112px" })}<span class="pt-b"><span class="pt-n" aria-hidden="true">${String(n + 1).padStart(2, "0")}</span><strong class="pt-h">${esc(interestLabel(ctx, k))}</strong>${has(desc) ? `<span class="pt-d">${esc(desc)}</span>` : ""}<span class="pt-f">${see ? `<span class="lnk">${esc(see)}</span>` : ""}${count}</span></span></a></li>`;
  }).join("")}</ul>`;
}

// A plan page's offers, two per row; with none, a dashed note card that asks the visitor to enquire.
export function planOffers(d, ctx) {
  const k = ctx.page.plan, list = ctx.offers.all;
  const title = has(d.title) ? d.title : ctx.t("site.offers_title_default");
  if (!list.length) {
    const note = tr(ctx, "site.plan_no_offer");
    return note ? `<h2>${esc(title)}</h2><div class="note"><p>${esc(note)}</p><a class="lnk" href="${esc(contactHref(ctx, k))}" data-interest="${esc(k)}">${esc(ctx.t("site.offer_cta_enquire"))}</a></div>` : "";
  }
  const disc = has(ctx.site.company && ctx.site.company.disclaimer) ? `<p class="fine disc">${esc(ctx.site.company.disclaimer)}</p>` : "";
  return `<h2>${esc(title)}</h2><ul class="offers two stg">${list.map((o) => offerCard(o, ctx, { image: true })).join("")}</ul>${disc}`;
}

// The plan page's "Other plans": small picture chips (thumbnail + name) in a row that scrolls sideways.
export function plans(d, ctx) {
  const h = tr(ctx, "site.plan_others");
  const others = (ctx.pages || []).filter((p) => p.plan && p.plan !== ctx.page.plan);
  if (!h || !others.length) return "";
  return `<h2>${esc(h)}</h2><ul class="pchips">${others.map((p) => `<li><a class="pchip" href="${esc(pageUrl(ctx.links, p.path))}">${interestPic(ctx, p.plan, { sizes: "48px", cls: "pic" })}<span>${esc(interestLabel(ctx, p.plan))}</span></a></li>`).join("")}</ul>`;
}

// About: the companies as a ticker band, "why choose me" as icon cards (4 across, 2 on phones), the achievements as a
// timeline (a row on desktop, year rows on phones). The testimonials slider is the shared testimonials partial.
export function companiesBand(d, ctx) {
  const list = companyList(ctx), h = companiesHeading(ctx);
  if (!list.length || !h) return "";
  return `<h2>${esc(h)}</h2>${ticker(list.map((c) => `<strong>${esc(c.name)}</strong>${c.types.length ? ` <span>${c.types.map(esc).join(", ")}</span>` : ""}`), { cls: "tk-cos", label: h })}`;
}
export function hlCards(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.highlights"))}</h2><ul class="icards stg">${items.map((i) => `<li class="card">${icon(i.icon)}<h3>${esc(i.title)}</h3>${has(i.text) ? `<p>${esc(i.text)}</p>` : ""}</li>`).join("")}</ul>`;
}
export function timeline(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.achievements"))}</h2><ol class="tline stg">${items.map((i) => `<li><span class="yr">${has(i.year) ? esc(i.year) : ""}</span><span>${esc(i.title)}</span></li>`).join("")}</ol>`;
}
export function quotes(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.quote) && has(i.name));
  if (!items.length) return "";
  const h = ctx.t("section.testimonials");
  return `<h2>${esc(h)}</h2>${slider(items.map((i) => `<figure class="card quote"><blockquote>${esc(i.quote)}</blockquote><figcaption class="meta">${esc(i.name)}${has(i.city) ? ", " + esc(i.city) : ""}</figcaption></figure>`), ctx, { label: h, cls: "sl-sq", auto: 6000 })}`;
}

// Contact: two columns (5 : 7). Left, "Ways to reach me": a card per way (icon, label, value, button), then the
// address / map / hours card and the Add to home screen block; right, the enquiry form (ids, consent and privacy
// link unchanged). Phones: the three buttons in a row above the form, the address card after it.
export function contactPage(d, ctx) {
  const { t } = ctx;
  const phone = d.phone || ctx.profile.phone;
  const way = (ico, label, value, href, btn, rel = "") => `<li class="card way">${icon(ico)}<span class="way-k">${esc(label)}</span><span class="way-v">${esc(value)}</span><a class="btn${ico === "call" ? "" : " ghost"}" href="${href}"${rel}>${esc(btn)}</a></li>`;
  const ways = [
    tel(phone) ? way("call", t("site.form_phone"), phone, tel(phone), t("site.call")) : "",
    wa(d.whatsapp) ? way("chat", t("site.whatsapp"), d.whatsapp, wa(d.whatsapp), t("site.whatsapp"), ' rel="noopener"') : "",
    mail(d.email) ? way("mail", t("site.email"), d.email, mail(d.email), t("site.email")) : "",
  ].filter(Boolean);
  const map = url(d.mapLink);
  const where = [
    has(d.officeAddress) ? `<p><strong>${esc(t("site.office_address"))}</strong><br><span class="prose">${esc(d.officeAddress)}</span>${map ? ` <a href="${map}" rel="noopener">${esc(t("site.view_map"))}</a>` : ""}</p>` : map ? `<p><a href="${map}" rel="noopener">${esc(t("site.view_map"))}</a></p>` : "",
    has(d.officeHours) ? `<p><strong>${esc(t("site.office_hours"))}</strong><br>${esc(d.officeHours)}</p>` : "",
  ].join("");
  const install = d.showInstallButton !== false ? `<div class="install" id="install" hidden><button class="btn ghost" type="button" id="install-btn">${esc(t("site.install_button"))}</button><p class="hint" id="install-how" hidden></p></div>` : "";
  const h = tr(ctx, "site.contact_ways") || t("section.contact");
  return `<div class="cp-grid"><div class="cp-info"><h2>${esc(h)}</h2>${ways.length ? `<ul class="ways ways-${ways.length}">${ways.join("")}</ul>` : ""}${where ? `<div class="card where">${where}</div>` : ""}${install}</div>
${d.showEnquiryForm !== false ? form(ctx) : ""}</div>`;
}

// The help band at the end of every inner page but Contact: a heading, a line, "Send an enquiry" and WhatsApp.
export function helpBand(ctx) {
  const h = tr(ctx, "site.help_heading");
  if (!h) return "";
  const lead = tr(ctx, "site.help_lead");
  const interest = ctx.page && ctx.page.plan ? ctx.page.plan : "";
  const go = formOn(ctx) ? contactHref(ctx, interest) : pageHref(ctx, "contact");
  const goL = formOn(ctx) ? tr(ctx, "site.help_enquire") || ctx.t("site.cta_default") : ctx.t("site.cta_default");
  return `<section class="sec sec-help" id="help"><div class="help"><h2>${esc(h)}</h2>${lead ? `<p class="lead">${esc(lead)}</p>` : ""}<p class="cb-acts">${go ? `<a class="btn" href="${esc(go)}"${interest ? ` data-interest="${esc(interest)}"` : ""}>${esc(goL)}</a>` : ""}${waBtn(ctx)}</p></div></section>`;
}

// The shared partials of the inner pages, by section key (sections/index.js drawSections). Sections not named here
// use their ordinary shared partial (offers, faq, portal).
export const INNER = { services: planCards, plans, companies: companiesBand, highlights: hlCards, achievements: timeline, testimonials: quotes, contact: contactPage };

// Home's contact band (the full contact section, form included, is on the Contact page): one line, Call, WhatsApp and
// a button to the form.
export function contactBand(d, ctx) {
  const { t } = ctx;
  const call = tel(d.phone || ctx.phone), chat = wa(d.whatsapp);
  const go = pageHref(ctx, "contact", formOn(ctx) ? { anchor: "enq" } : {});
  const goL = formOn(ctx) ? tr(ctx, "site.help_enquire") || t("site.cta_default") : t("site.cta_default");
  const lead = tr(ctx, "site.contact_band_lead");
  return `<h2>${esc(t("section.contact"))}</h2><div class="cband">${lead ? `<p class="lead">${esc(lead)}</p>` : ""}<p class="cb-acts">${call ? `<a class="btn" href="${call}">${esc(t("site.call"))}</a>` : ""}${chat ? `<a class="btn ghost" href="${chat}" rel="noopener">${esc(t("site.whatsapp"))}</a>` : ""}${go ? `<a class="btn ghost" href="${esc(go)}">${esc(goL)}</a>` : ""}</p></div>`;
}

const more = (href, label) => (href && label ? `<p class="more-l"><a class="lnk" href="${esc(href)}">${esc(label)}</a></p>` : "");
const cut = (s, max) => { const v = String(s ?? "").replace(/\s+/g, " ").trim(); if (v.length <= max) return v; const c = v.slice(0, max - 1); const sp = c.lastIndexOf(" "); return (sp > max / 2 ? c.slice(0, sp) : c).replace(/[\s,;:.\-–—]+$/, "") + "…"; };
const ABOUT_TEASER = 240; // characters of the bio on Home

// Home teasers: the template's own partial on shorter data, then a link on to the section's page.
export const TEASERS = {
  services: (fn) => (d, ctx) => {
    const n = Number((ctx.sitePages || {}).teaserItems) || 6;
    const items = arr(d.items).slice(0, n);
    const offered = arr(d.offered).filter((k) => !items.some((i) => i && i.interest === k)).slice(0, Math.max(0, n - items.length));
    const html = fn({ ...d, items, offered }, ctx);
    return html ? html + more(pageHref(ctx, "insurance"), tr(ctx, "site.plan_back")) : "";
  },
  about: (fn) => (d, ctx) => {
    const html = fn(has(d.bio) ? { ...d, bio: cut(d.bio, ABOUT_TEASER) } : d, ctx);
    return html ? html + more(pageHref(ctx, "about"), tr(ctx, "site.more_about")) : "";
  },
  contact: () => contactBand,
};

// The plan-type link a template's services card carries on a multi-page site ("" on one page).
export const seePlans = (ctx, key, cls = "lnk") => { const h = planHref(ctx, key), l = tr(ctx, "site.see_plan") || tr(ctx, "site.see_plans"); return h && l ? `<a class="${cls}" href="${esc(h)}">${esc(l)}</a>` : ""; };
// A template's service card link: to the plan type's page when the site has one, else "Enquire now" to the form.
export const planOrEnquire = (ctx, key, cls = "lnk") =>
  seePlans(ctx, key, cls) || `<a class="${cls}" href="${esc(contactHref(ctx, key))}" data-interest="${esc(key)}">${esc(ctx.t("site.offer_cta_enquire"))}</a>`;
