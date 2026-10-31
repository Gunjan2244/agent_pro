// Plan pages (DECISIONS #130; docs/WEB_RENDERER.md "Plan pages"). Every official plan a site shows has its own page,
// /insurance/<type>/<slug>: its facts, its picture (the admin's, else the default picture of its type), its key
// features (the insurer's own words with their source and date, or the agent's own words, marked as theirs), the
// agent's note on the plan type, the live offers on it, the insurer's documents and similar plans. Every link to an
// insurer goes through /go/<slug>/<doc>: "We're taking you to <insurer>'s website", which then opens the insurer's
// page for that plan or document. The parts are shared; each template arranges them (its render.js `planPage`) and
// restyles them (its style.css). Also here: the plan card and plan row every template uses on Home, Insurance and the
// plan-type pages, and the default "Plans I recommend" section. Render-only.
import { esc, has, arr, tel, wa, interestLabel } from "./util.js";
import { icon } from "./icons.js";
import { tr, slider } from "./ui.js";
import { offerCard, formatDate } from "./offer.js";
import { contactHref, pageHref } from "./links.js";
import { crumb, planDesc } from "./pages.js";
import { sitePlans, planBySlug, planFacts, planPageHref, planPicture, planDocs, goHref, goAttrs, docUrl, planOffers, featuredPlans } from "./plan-data.js";

export const FEATURED = "featuredPlans";
export const PLAN_PAGE = "planDetail";

// A plan card: picture, plan type (and "Offer" when one is live on it), name, company, facts; the whole card links
// to the plan's page. opts.h = the heading tag of the name ("h3" in a section; "strong" in a slider).
export function planCard(ctx, p, { sizes = "(min-width: 1024px) 340px, (min-width: 720px) 50vw, 100vw", tag = "li", cls = "" } = {}) {
  const href = planPageHref(ctx, p);
  const off = planOffers(ctx, p)[0];
  const offL = off ? (has(off.badge) ? off.badge : tr(ctx, "site.pp_offer_pill")) : "";
  const facts = planFacts(ctx, p);
  const view = tr(ctx, "site.pp_view");
  const body = `${planPicture(ctx, p, { sizes })}<span class="pc-b"><span class="pc-tags"><span class="pill">${esc(interestLabel(ctx, p.interest))}</span>${offL ? `<span class="pill ok">${esc(offL)}</span>` : ""}</span><strong class="pc-h">${esc(p.name)}</strong><span class="pc-co">${esc(p.company)}</span>${facts ? `<span class="pc-f">${esc(facts)}</span>` : ""}${view && href ? `<span class="lnk pc-go">${esc(view)}</span>` : ""}</span>`;
  return `<${tag} class="pcard lift zoom${cls ? " " + cls : ""}">${href ? `<a href="${esc(href)}">${body}</a>` : `<div>${body}</div>`}</${tag}>`;
}

// A plan row: a small picture, the name, company and facts, "View" (Simple's lists, Fresh's numbered rows).
export function planRow(ctx, p, { n = 0 } = {}) {
  const href = planPageHref(ctx, p);
  const facts = [p.company, planFacts(ctx, p)].filter(Boolean).join(" · ");
  const off = planOffers(ctx, p).length ? `<span class="pill ok">${esc(tr(ctx, "site.pp_offer_pill"))}</span>` : "";
  const inner = `${n ? `<span class="pr-n" aria-hidden="true">${String(n).padStart(2, "0")}</span>` : ""}${planPicture(ctx, p, { sizes: "96px", cls: "thumb" })}<span class="pr-b"><strong>${esc(p.name)}</strong><span class="meta">${esc(facts)}</span>${off}</span>${href ? `<span class="lnk pr-go">${esc(tr(ctx, "site.pp_view"))}</span>` : ""}`;
  return `<li class="prow">${href ? `<a href="${esc(href)}">${inner}</a>` : `<div>${inner}</div>`}</li>`;
}

// "Plans I recommend" (Home): the agent's featured plans. layout "grid" (default: cards), "slider" (cards that slide),
// "rows" (plan rows), "list" (numbered rows).
export function featuredSection(d, ctx, { layout = "grid" } = {}) {
  const list = featuredPlans(ctx);
  const h = tr(ctx, "site.featured_heading");
  if (!list.length || !h) return "";
  const lead = tr(ctx, "site.featured_lead");
  const all = pageHref(ctx, "insurance"), allL = tr(ctx, "site.featured_all");
  const more = all && allL ? `<p class="more-l"><a class="lnk" href="${esc(all)}">${esc(allL)}</a></p>` : "";
  let body;
  if (layout === "slider") body = slider(list.map((p) => planCard(ctx, p, { tag: "div", sizes: "340px" })), ctx, { label: h, cls: "sl-plans", auto: 0 });
  else if (layout === "rows" || layout === "list") body = `<ul class="prows${layout === "list" ? " numbered" : ""}">${list.map((p, i) => planRow(ctx, p, { n: layout === "list" ? i + 1 : 0 })).join("")}</ul>`;
  else body = `<ul class="pcards stg">${list.map((p) => planCard(ctx, p)).join("")}</ul>`;
  return `<h2>${esc(h)}</h2>${lead ? `<p class="lead sub">${esc(lead)}</p>` : ""}${body}${more}`;
}
export const featured = (d, ctx) => featuredSection(d, ctx);

// ---------------------------------------------------------------------------------------------------------------
// The plan page

// The plan the page shows (ctx.page.slug), or null.
export const pagePlan = (ctx) => (ctx.page && ctx.page.key === PLAN_PAGE ? planBySlug(ctx, ctx.page.slug) : null);

const waLink = (ctx, cls = "btn ghost") => { const h = wa(((ctx.site.content || {}).contact || {}).whatsapp); return h ? `<a class="${cls}" href="${h}" rel="noopener">${esc(ctx.t("site.whatsapp"))}</a>` : ""; };
const askLink = (ctx, p, cls = "btn") => { const h = contactHref(ctx, p.interest); return h ? `<a class="${cls}" href="${esc(h)}" data-interest="${esc(p.interest)}">${esc(tr(ctx, "site.plan_ask") || ctx.t("site.offer_cta_enquire"))}</a>` : ""; };

// The parts of a plan page, each an HTML string ("" when it has nothing to show). Templates arrange them.
//   head: the page header's text column (crumb, <h1>, company, facts, actions); pic: the plan's picture;
//   facts: a facts row (plan number, UIN, type, company); features, about, offer, docs, talk, similar: sections'
//   inner HTML (each starts with its <h2>); checks: the features as a checklist (Spotlight); docTiles: the documents
//   as tiles (Spotlight); compare: similar plans as rows (Guide); ask: Call / WhatsApp / Ask buttons.
export function planParts(ctx) {
  const p = pagePlan(ctx);
  if (!p) return null;
  const { t } = ctx;
  const co = p.company;
  const type = interestLabel(ctx, p.interest);
  const facts = planFacts(ctx, p);
  const tags = [type, ...(facts ? facts.split(" · ") : [])].filter(Boolean).map((x) => `<span class="pill">${esc(x)}</span>`).join("");
  const actions = [askLink(ctx, p), waLink(ctx)].filter(Boolean).join("");
  const head = `${crumb(ctx)}<h1>${esc(p.name)}</h1><p class="pl-co">${esc(co)}</p>${tags ? `<p class="pl-tags">${tags}</p>` : ""}${actions ? `<p class="ph-act">${actions}</p>` : ""}`;
  const pic = planPicture(ctx, p, { sizes: "(min-width: 1024px) 520px, (min-width: 720px) 45vw, 100vw", eager: true });
  const factRows = [
    p.plan_no ? [tr(ctx, "site.pp_fact_no"), p.plan_no] : null,
    p.uin ? [tr(ctx, "site.pp_fact_uin"), p.uin] : null,
    [tr(ctx, "site.pp_fact_type"), type],
    [tr(ctx, "site.pp_fact_company"), co],
  ].filter((r) => r && r[0] && r[1]);
  const factsRow = `<dl class="pl-facts">${factRows.map(([k, v]) => `<div><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join("")}</dl>`;

  // Key features: the agent's words (marked as theirs, with the insurer's documents for the official terms) or the
  // insurer's words (with where and when they were copied).
  const kf = arr(p.features).filter(has).slice(0, 5);
  let src = "";
  if (kf.length && p.features_own) {
    const badge = tr(ctx, "site.pp_own_badge", { name: ctx.firstName || ctx.agentName });
    const terms = goHref(ctx, p, docUrl(p, "policy") ? "policy" : "plan");
    src = `<p class="pl-src">${badge ? `<span class="pill own">${esc(badge)}</span> ` : ""}${esc(tr(ctx, "site.pp_own_note", { company: co }))}${terms ? ` <a href="${esc(terms)}"${goAttrs(ctx)}>${esc(tr(ctx, "site.pp_official_terms"))}</a>` : ""}</p>`;
  } else if (kf.length) {
    const when = formatDate(p.features_checked_on, ctx.locale);
    const from = goHref(ctx, p, "features");
    const site = tr(ctx, "site.pp_src_site", { company: co });
    src = `<p class="pl-src">${from ? `<a href="${esc(from)}"${goAttrs(ctx)}>${esc(site)}</a>` : esc(site)}${when ? ` · ${esc(tr(ctx, "site.pp_src_checked", { date: when }))}` : ""}</p>`;
  }
  const fh = `<h2>${esc(tr(ctx, "site.pp_features"))}</h2>`;
  const features = kf.length ? `${fh}<ul class="pl-kf">${kf.map((f) => `<li>${esc(f)}</li>`).join("")}</ul>${src}` : "";
  const numbered = kf.length ? `${fh}<ol class="pl-kf num">${kf.map((f) => `<li>${esc(f)}</li>`).join("")}</ol>${src}` : "";
  const checks = kf.length ? `${fh}<ul class="pl-checks">${kf.map((f) => `<li>${icon("check")}<span>${esc(f)}</span></li>`).join("")}</ul>${src}` : "";

  const desc = planDesc(ctx, p.interest);
  const about = has(desc) ? `<h2>${esc(tr(ctx, "site.pp_about", { type }))}</h2><p class="prose">${esc(desc)}</p>` : "";

  const offers = planOffers(ctx, p);
  const disc = offers.length && has(ctx.site.company && ctx.site.company.disclaimer) ? `<p class="fine disc">${esc(ctx.site.company.disclaimer)}</p>` : "";
  const offer = offers.length ? `<h2>${esc(tr(ctx, offers.length === 1 ? "site.pp_offer" : "site.pp_offers"))}</h2><ul class="offers${offers.length > 1 ? " two" : ""}">${offers.map((o) => offerCard(o, ctx, { image: false, noPlan: true })).join("")}</ul>${disc}` : "";

  const docs = planDocs(p);
  const dh = `<h2>${esc(tr(ctx, "site.pp_docs", { company: co }))}</h2><p class="pl-docs-l">${esc(tr(ctx, "site.pp_docs_lead", { company: co }))}</p>`;
  const docLabel = (d) => tr(ctx, "site.pp_doc_" + d);
  const docsHtml = docs.length ? `${dh}<ul class="pl-docs">${docs.map((d) => `<li><a href="${esc(goHref(ctx, p, d))}"${goAttrs(ctx)}>${icon("description")}<span class="pd-n">${esc(docLabel(d))}</span><span class="pd-w">${icon("open_in_new")}<span class="vh">${esc(tr(ctx, "site.pp_doc_on", { company: co }))}</span></span></a></li>`).join("")}</ul>` : "";
  const docTiles = docs.length ? `${dh}<ul class="pl-tiles">${docs.map((d) => `<li><a class="card" href="${esc(goHref(ctx, p, d))}"${goAttrs(ctx)}>${icon("description")}<strong>${esc(docLabel(d))}</strong><span class="pd-w">${esc(tr(ctx, "site.pp_doc_on", { company: co }))}</span></a></li>`).join("")}</ul>` : "";

  const call = tel(ctx.phone);
  const ask = [call ? `<a class="btn" href="${call}">${esc(t("site.call"))}</a>` : "", waLink(ctx), askLink(ctx, p, "btn ghost")].filter(Boolean).join("");
  const talkH = tr(ctx, "site.pp_talk", { name: ctx.firstName || ctx.agentName });
  const talk = ask && talkH ? `<h2>${esc(talkH)}</h2><p class="pl-talk-l">${esc(tr(ctx, "site.pp_talk_lead"))}</p><p class="pl-acts">${ask}</p>` : "";

  // Similar plans: others of the same type, then others of the same company, at most 3.
  const others = sitePlans(ctx).filter((x) => x.id !== p.id);
  const sim = [...others.filter((x) => x.interest === p.interest), ...others.filter((x) => x.interest !== p.interest && x.company === co)].slice(0, 3);
  const simH = `<h2>${esc(tr(ctx, "site.pp_similar"))}</h2>`;
  const similar = sim.length ? `${simH}<ul class="pcards">${sim.map((x) => planCard(ctx, x)).join("")}</ul>` : "";
  const compare = sim.length ? `<h2>${esc(tr(ctx, "site.pp_compare"))}</h2><ul class="prows">${sim.map((x) => planRow(ctx, x)).join("")}</ul>` : "";
  const similarRows = sim.length ? `${simH}<ul class="prows numbered">${sim.map((x, i) => planRow(ctx, x, { n: i + 1 })).join("")}</ul>` : "";
  const similarList = sim.length ? `${simH}<ul class="prows">${sim.map((x) => planRow(ctx, x)).join("")}</ul>` : "";
  const fine = tr(ctx, "site.iplans_fine");

  return { p, head, pic, tags, actions, facts: factsRow, features, numbered, checks, about, offer, offers, docs: docsHtml, docTiles, talk, ask, similar, similarRows, similarList, compare, fine: fine ? `<p class="fine disc">${esc(fine)}</p>` : "" };
}

// Helpers for the templates' arrangements. Every element with an <h2> is a <section> (one heading per section);
// sec(): a full-width section of the page; box(): a section inside a column; band(): a full-width block without a
// heading (a facts row, a picture); grid(): the main column beside the side column, as a band.
export const sec = (key, inner, cls = "") => (inner ? `<section class="sec sec-${key}${cls ? " " + cls : ""}" id="${key}">${inner}</section>` : "");
export const box = (id, inner, cls = "") => (inner ? `<section class="${cls}" id="${id}">${inner}</section>` : "");
export const band = (key, inner, cls = "") => (inner ? `<div class="sec sec-${key}${cls ? " " + cls : ""}" id="${key}">${inner}</div>` : "");
export const grid = (main, side, key = "plan") => band(key, main || side ? `<div class="pl-grid${side ? "" : " solo"}"><div class="pl-main">${main}</div>${side ? `<aside class="pl-side">${side}</aside>` : ""}</div>` : "");
export const ppHead = (x, { pic = true, cls = "" } = {}) => `<div class="ph-in ph-split pl-hd${pic && x.pic ? "" : " no-pic"}${cls ? " " + cls : ""}">${pic && x.pic ? `<div class="ph-pic fade">${x.pic}</div>` : ""}<div class="ph-text enter">${x.head}</div></div>`;

// The shared arrangement (below the page header): the features, the agent's note and the offer beside the documents
// and "Talk to …", then similar plans. A template's planPage(x, ctx) returns its own sections instead.
export function defaultPlanPage(x) {
  const main = box("features", x.features, "pl-block") + box("about", x.about, "pl-block") + box("offer", x.offer, "pl-block pl-offer") + (x.features || x.offer ? x.fine : "");
  const side = box("docs", x.docs, "pl-card") + box("talk", x.talk, "pl-card pl-talk");
  return grid(main, side) + sec("similar", x.similar);
}

// ---------------------------------------------------------------------------------------------------------------
// "We're taking you to <insurer>'s website" (/go/<slug>/<doc>): the plan, the exact address, whose page it is, a
// short countdown (the server adds <meta http-equiv="refresh">: no script), "Go to … now" and "Stay here".
export const GO = "go";
export function goTarget(ctx) {
  if (!ctx.page || ctx.page.key !== GO) return null;
  const p = planBySlug(ctx, ctx.page.slug);
  const to = p ? docUrl(p, ctx.page.doc) : "";
  return to ? { p, doc: ctx.page.doc, to: to.replace(/&amp;/g, "&") } : null;
}
export function goPage(ctx) {
  const g = goTarget(ctx);
  if (!g) return "";
  const { p, doc } = g, co = p.company;
  const back = planPageHref(ctx, p) || "#";
  const what = doc === "plan" || doc === "features"
    ? tr(ctx, "site.go_to_plan", { plan: p.name })
    : tr(ctx, "site.go_to_doc", { doc: tr(ctx, "site.pp_doc_" + doc).toLowerCase(), plan: p.name });
  const facts = planFacts(ctx, p);
  return `<main class="go-wrap"><div class="go-card" role="status">
<p class="go-who">${esc(tr(ctx, "site.footer_advisor", { name: ctx.agentName }))}</p>
<h1>${esc(tr(ctx, "site.go_title", { company: co }))}</h1>
<p class="go-what">${esc(what)}${facts ? ` <span class="meta">(${esc(facts)})</span>` : ""}</p>
<p class="go-url"><span>${esc(tr(ctx, "site.go_address"))}</span>${esc(g.to)}</p>
<p class="go-note">${esc(tr(ctx, "site.go_note", { company: co, name: ctx.agentName }))}</p>
<div class="go-bar" aria-hidden="true"><span></span></div>
<p class="go-count">${esc(tr(ctx, "site.go_opening"))}</p>
<p class="go-acts"><a class="btn" href="${esc(g.to)}" rel="nofollow noopener external" referrerpolicy="no-referrer">${esc(tr(ctx, "site.go_now", { company: co }))}</a><a class="btn ghost" href="${esc(back)}">${esc(tr(ctx, "site.go_stay"))}</a></p>
<p class="fine">${esc(tr(ctx, "site.footer_solicitation"))}</p>
</div></main>`;
}
