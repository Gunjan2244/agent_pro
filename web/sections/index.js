import { hero } from "./hero.js";
import { about } from "./about.js";
import { services } from "./services.js";
import { offers } from "./offers.js";
import { highlights } from "./highlights.js";
import { achievements } from "./achievements.js";
import { testimonials } from "./testimonials.js";
import { faq } from "./faq.js";
import { contact } from "./contact.js";
import { companies } from "./companies.js";
import { portalPromo, portalBase } from "./portal-promo.js";
import { footer } from "./footer.js";
import { insurerPlans, sitePlans, INSURER_PLANS } from "./insurer-plans.js";
import { FEATURED, PLAN_PAGE, GO, featured, planParts, defaultPlanPage, goPage, ppHead } from "./plan-page.js";
import { featuredPlans, planBySlug, docUrl } from "./plan-data.js";
import { esc, url, tel, wa, firstName } from "./util.js";
import { pickOffers, todayIST, interestOf } from "./offer.js";
import { pageHead, pageTitle, plans, planKeys, planOffers, helpBand, INNER, TEASERS } from "./pages.js";
import { interestLabel } from "./util.js";
import { linksFrom, planPath } from "../route.js";

export const PARTIALS = { hero, about, companies, services, offers, highlights, achievements, testimonials, faq, portal: portalPromo, contact };
// Parts that exist only on a page of a multi-page site (DECISIONS #122), never in content.sectionOrder.
// insurerPlans (DECISIONS #129): the official plans the agent chose, on Insurance and on each plan page.
// featuredPlans (DECISIONS #130): Home's "Plans I recommend", right after the plan types.
const PAGE_PARTIALS = { plans, [INSURER_PLANS]: insurerPlans, [FEATURED]: featured };
const DEFAULT_ORDER = Object.keys(PARTIALS).filter((k) => k !== "companies" && k !== "portal");

// "Companies I represent" (DECISIONS #90) comes from the payload, not from the agent's content, so an order that
// doesn't name it (every site today) gets it right after the template's anchor section (`after`: about on "Meet me",
// the trust strip on "Best offers"); without the anchor, before services, the FAQ or contact. An order that names it
// is followed, and content.hidden can hide it, like any section.
export function placeCompanies(order, after = "about") {
  if (order.includes("companies")) return order;
  const out = order.slice();
  let at = out.indexOf(after) + 1;
  if (!at) { const k = ["services", "faq", "contact"].find((x) => out.includes(x)); at = k ? out.indexOf(k) : out.length; }
  // never above the hero (an odd order may put the anchor first)
  at = Math.max(at, out.indexOf("hero") + 1);
  out.splice(at, 0, "companies");
  return out;
}

// "For my customers" (DECISIONS #102) is like companies: an order that doesn't name it gets it right before contact
// (or last, when contact isn't in the order either: contact is pinned and is appended after it). An order that names
// it is followed, and content.hidden can hide it.
export function placePortal(order) {
  if (order.includes("portal")) return order;
  const out = order.slice(), at = out.indexOf("contact");
  out.splice(at < 0 ? out.length : at, 0, "portal");
  return out;
}

// Section order + visibility from content.sectionOrder / content.hidden. Pinned sections
// (schema `pinned`) are always shown and always present, even if missing from the order.
export function visibleKeys(content, schema, opts = {}) {
  // the portal section exists only when the payload says the agent's customers have a portal (opts.portal)
  const known = new Set(Object.keys(PARTIALS).filter((k) => k !== "portal" || opts.portal === true));
  const pinned = new Set((schema.sections || []).filter((s) => s.pinned).map((s) => s.key));
  const hidden = new Set(Array.isArray(content.hidden) ? content.hidden : []);
  const order = placePortal(placeCompanies(Array.isArray(content.sectionOrder) ? content.sectionOrder : DEFAULT_ORDER, opts.companiesAfter));
  const seen = new Set();
  const out = [];
  for (const k of order) if (known.has(k) && !seen.has(k)) { seen.add(k); out.push(k); }
  for (const k of pinned) if (known.has(k) && !seen.has(k)) { if (k === "hero") out.unshift(k); else out.push(k); }
  return out.filter((k) => pinned.has(k) || !hidden.has(k));
}

// tokens = { accent, schema, catalog, notice?, privacyHref?, family? ("personal" | "offers", from the templates
// registry), images? (web/images/manifest.json), seed? (slug or host: picks the hero scene), today? (YYYY-MM-DD),
// sitePages? (shared/site-pages.json: the site has pages, DECISIONS #122), page? (route.js matchPage: which page;
// default Home) }
// opts.layout is the template's own layout for the offers section ("grid" | "needs"); opts.companiesAfter is the
// section the companies follow when the order doesn't name them (placeCompanies).
// P = { list, current } (sitePages below): the site's pages and the one being drawn; without it, one page.
export function makeCtx(site, t, tokens, variant, opts = {}, P = null) {
  const content = site.content || {}, profile = site.profile || {};
  const ctx = {
    t, variant, site, profile, schema: tokens.schema || { sections: [] },
    catalog: tokens.catalog || { interests: [] }, accent: tokens.accent,
    // privacy: link to the site's privacy page + the enquiry_form notice's consent line / link keys (first two textKeys)
    privacyHref: tokens.privacyHref || "#", agentName: profile.full_name || "",
    consentKey: ((tokens.notice || {}).textKeys || [])[0] || "privacy.enquiry_form.consent",
    consentLinkKey: ((tokens.notice || {}).textKeys || [])[1] || "privacy.enquiry_form.link",
    family: tokens.family === "offers" ? "offers" : "personal", layout: opts.layout || "grid",
    images: tokens.images && typeof tokens.images === "object" ? tokens.images : {},
    seed: tokens.seed || "", today: tokens.today || todayIST(), locale: site.locale || "en",
    photo: url((content.hero || {}).photo) || url(profile.photo_url),
    firstName: firstName(profile.full_name),
    phone: (content.contact || {}).phone || profile.phone || "",
    portal: site.portal === true,
  };
  ctx.portalBase = portalBase(ctx.portal, ctx.privacyHref);
  ctx.links = linksFrom(ctx.privacyHref);
  // allVisible: the site's sections (order + hidden); visible: those on the page being drawn (all of them on one page).
  ctx.allVisible = ctx.visible = new Set(visibleKeys(content, ctx.schema, { companiesAfter: opts.companiesAfter, portal: ctx.portal }));
  // The offers are picked over the whole site, so the enquiry form's options (formInterests) are the same on every page.
  ctx.offers = pickOffers(site, ctx);
  ctx.pages = P ? P.list : null;
  ctx.page = P ? P.current : null;
  ctx.sitePages = tokens.sitePages || null;
  if (ctx.page) {
    const def = ctx.page.def, inner = ctx.page.key !== "home";
    // An inner page has no hero, and the section its header tells (About: the about story) isn't repeated below it.
    let keys = [...ctx.allVisible].filter((k) => def.sections.includes(k) && !(inner && (k === "hero" || k === def.headerSection)));
    // `"order": "page"`: the page's own layout fixes the order (Contact: the contact block, then the FAQ beside the
    // portal card); otherwise the content's sectionOrder decides, as on one page.
    if (def.order === "page") keys.sort((a, b) => def.sections.indexOf(a) - def.sections.indexOf(b));
    if (ctx.page.key === PLAN_PAGE || ctx.page.key === GO) {
      // A plan's own page and the "We're taking you to …" page (DECISIONS #130) are drawn whole by compose: the plan
      // page's offers are those on that plan.
      const p = planBySlug(ctx, ctx.page.slug);
      const mine = p ? ctx.offers.all.filter((o) => o && o.plan_id === p.id) : [];
      ctx.offers = { hero: null, grid: mine, all: mine };
      keys = [];
    } else if (inner) {
      // Inner pages are shared layouts (DECISIONS #122): no hero, so no offer is held back for a hero banner.
      const all = ctx.offers.all;
      const mine = ctx.page.plan ? all.filter((o) => interestOf(o) === ctx.page.plan) : all;
      ctx.offers = { hero: null, grid: mine, all: mine };
      if (!(opts.inner && opts.inner[ctx.page.key])) ctx.layout = "grid";
      // A plan page: that type's offers (a note card when there are none) unless offers are hidden, then the others.
      if (ctx.page.key === "plan") keys = [...(ctx.allVisible.has("offers") ? ["offers"] : []), ...(sitePlans(ctx, ctx.page.plan).length ? [INSURER_PLANS] : []), "plans"];
      // Insurance: the official plans (DECISIONS #129) after the plan types and the offers.
      else if (ctx.page.key === "insurance" && sitePlans(ctx).length) keys.push(INSURER_PLANS);
    }
    // Home: "Plans I recommend" (the featured official plans, DECISIONS #130) right after the plan types (else after
    // the offers, else after the hero).
    if (!inner && featuredPlans(ctx).length) {
      let at = keys.indexOf("services");
      if (at < 0) at = keys.indexOf("offers");
      if (at < 0) at = keys.indexOf("hero");
      keys.splice(at + 1, 0, FEATURED);
    }
    ctx.visible = new Set(keys);
    ctx.teasers = new Set(def.teasers || []);
  }
  return ctx;
}

// The pages a site has (DECISIONS #122): Home and Contact always (their hero and contact are pinned); Insurance and
// About only when something on them renders; one page per plan type (pages.js planKeys) while Insurance exists.
// want = route.js matchPage() result (or {key: "home"}). -> { list: [{key, def, path, plan?}], current | null }.
function sitePages(site, t, tokens, variant, opts, defs) {
  const want = tokens.page && tokens.page.key ? tokens.page : { key: "home", path: "" };
  const base = makeCtx(site, t, tokens, variant, opts);
  const plansDef = defs.pages.find((d) => d.key === "plan");
  const detailDef = defs.pages.find((d) => d.key === PLAN_PAGE);
  const goDef = defs.pages.find((d) => d.key === GO);
  const keys = planKeys(base);
  const list = [];
  for (const def of defs.pages) {
    if (def === plansDef || def === detailDef || def === goDef) continue;
    const page = { key: def.key, def, path: def.path };
    let exists = def.key === "home" || def.key === "contact";
    if (!exists) exists = (def.key === "insurance" && keys.length > 0)
      || (def.headerSection && base.allVisible.has(def.headerSection) && Boolean(PARTIALS[def.headerSection]((site.content || {})[def.headerSection] || {}, base)))
      || drawSections(site, t, tokens, variant, opts, { list: [], current: page }).some(([, h]) => h);
    if (exists) list.push(page);
  }
  if (plansDef && list.some((p) => p.key === plansDef.parent)) {
    const at = list.findIndex((p) => p.key === plansDef.parent) + 1;
    // each plan type's page, then (DECISIONS #130) one page per official plan of that type the site shows
    list.splice(at, 0, ...keys.flatMap((k) => [
      { key: plansDef.key, def: plansDef, path: planPath(plansDef, k), plan: k },
      ...(detailDef ? sitePlans(base, k).filter((p) => p.slug).map((p) => ({ key: detailDef.key, def: detailDef, path: planPath(detailDef, k, p.slug), plan: k, slug: p.slug })) : []),
    ]));
  }
  let current = list.find((p) => p.key === want.key && (p.plan || "") === (want.plan || "") && (p.slug || "") === (want.slug || "")) || null;
  // The "We're taking you to …" page exists for every document of a plan the site shows; it is never listed (no
  // menu, no sitemap: noindex).
  if (!current && goDef && want.key === GO) {
    const p = planBySlug(base, want.slug);
    if (p && docUrl(p, want.doc)) current = { key: GO, def: goDef, path: want.path, slug: want.slug, doc: want.doc };
  }
  return { list, current };
}

// [key, html] for every section of the page: Home uses the template's partials (opts.partials) with the teasers of
// the page model; inner pages use the shared inner-page partials (pages.js INNER, then the ordinary shared ones; their
// look comes from the template's CSS), except where a template overrides one for a page (opts.inner[pageKey][key]).
function drawSections(site, t, tokens, variant, opts, P, ctx = makeCtx(site, t, tokens, variant, opts, P)) {
  const content = site.content || {};
  const inner = ctx.page && ctx.page.key !== "home";
  const own = inner ? { ...INNER, ...(ctx.page.key === "plan" ? { offers: planOffers } : {}), ...((opts.inner || {})[ctx.page.key] || {}) } : opts.partials || {};
  return [...ctx.visible].map((k) => {
    let fn = own[k] || PARTIALS[k] || PAGE_PARTIALS[k];
    if (!inner && ctx.teasers && ctx.teasers.has(k) && TEASERS[k]) fn = TEASERS[k](fn);
    return [k, fn(content[k] || {}, ctx)];
  });
}

// What head.js needs to know about a page (DECISIONS #122): its title (the <h1>), a description and the sections on
// it (FAQPage / Service JSON-LD only where those are shown). page = route.js matchPage(); null without pages. Home
// keeps the site's own title and description (title "").
export function pageMeta(site, t, tokens, page) {
  if (!page || !tokens.sitePages) return null;
  const ctx = makeCtx(site, t, tokens, "", {}, { list: [], current: page });
  if (page.key === "home") return { key: "home", path: "", plan: null, title: "", description: "", sections: [...ctx.visible], services: [] };
  const { title, lead } = pageTitle(ctx);
  const c = site.content || {}, name = ctx.profile.full_name || "";
  const tx = (k, p) => { const s = t(k, p); return s && s !== k ? s : ""; };
  let description = "";
  if (page.key === "insurance") description = [planKeys(ctx).map((k) => interestLabel(ctx, k)).join(", "), tx("site.page_insurance_lead")].filter(Boolean).join(". ");
  else if (page.key === "plan") description = lead || tx("site.plan_desc", { plan: title, name });
  else if (page.key === "about") description = (c.about || {}).bio || lead;
  else if (page.key === "contact") description = [tx("site.page_contact_lead"), (c.contact || {}).officeAddress].filter(Boolean).join(" ");
  else if (page.key === PLAN_PAGE) { const p = planBySlug(ctx, page.slug); description = p ? [p.company, ...(p.features || []).slice(0, 2)].filter(Boolean).join(". ") : ""; }
  const sections = page.key === "plan" || page.key === PLAN_PAGE || page.key === GO ? [] : [...ctx.visible];
  return { key: page.key, path: page.path, plan: page.plan || null, title, description: description || "", sections, services: page.key === "plan" || page.key === PLAN_PAGE ? [page.plan] : page.key === "insurance" ? planKeys(ctx) : [] };
}

// Sticky Call / WhatsApp bar for phones (both families), only when the numbers exist. It is position:fixed from
// the first paint and `.tpl.has-bar` reserves its height at the bottom, so it never shifts the layout (CLS 0).
function callbar(ctx) {
  const c = (ctx.site.content || {}).contact || {};
  const call = tel(ctx.phone), chat = wa(c.whatsapp);
  if (!call && !chat) return "";
  return `<nav class="callbar" aria-label="${esc(ctx.t("section.contact"))}">${call ? `<a class="btn" href="${call}">${esc(ctx.t("site.call"))}</a>` : ""}${chat ? `<a class="btn ghost" href="${chat}" rel="noopener">${esc(ctx.t("site.whatsapp"))}</a>` : ""}</nav>`;
}

// opts.wrap(key, html, ctx) lets a template change the markup around a section; opts.partials = {key: fn(d, ctx)}
// replaces a section's partial with the template's own arrangement (DECISIONS #85) on Home (and on a one-page render);
// opts.partials.pagehead replaces the shared inner-page header (minimal: its profile card); opts.layout see makeCtx.
// With tokens.sitePages the site has pages (DECISIONS #122): tokens.page picks one (default Home), and a page the site
// doesn't have renders "" (the Worker and client.js answer it with "This page isn't available"). tokens.pagesOnly
// returns the site's page list instead of HTML (the sitemap).
export function compose(site, t, tokens, variant, opts = {}) {
  const defs = tokens.sitePages && Array.isArray(tokens.sitePages.pages) ? tokens.sitePages : null;
  const P = defs ? sitePages(site, t, tokens, variant, opts, defs) : null;
  if (tokens.pagesOnly) return P ? P.list.map(({ key, path, plan }) => ({ key, path, plan })) : [];
  if (P && !P.current) return "";
  const ctx = makeCtx(site, t, tokens, variant, opts, P);
  const accent = /^#[0-9a-fA-F]{6}$/.test(tokens.accent) ? tokens.accent : "var(--c-primary)";
  // "We're taking you to <insurer>'s website" (DECISIONS #130): a page of its own, no template chrome.
  if (ctx.page && ctx.page.key === GO) { const g = goPage(ctx); return g ? `<div class="tpl tpl-${variant} pg-go" style="--accent:${accent}">${g}</div>` : ""; }
  // A plan's own page (DECISIONS #130): the template's page header (its pagehead, else the shared one) shows the plan
  // (ctx.ppHead), then the template's arrangement of the plan's parts (opts.planPage, else the shared one).
  const pp = ctx.page && ctx.page.key === PLAN_PAGE ? planParts(ctx) : null;
  if (ctx.page && ctx.page.key === PLAN_PAGE && !pp) return "";
  if (pp) ctx.ppHead = (pic) => ppHead(pp, { pic });
  // Landmarks: the hero (or an inner page's header) is the page <header> (its <h1> is the only one); every other
  // section is a <section id> with its own <h2>.
  const wrap = opts.wrap || ((k, h) => (k === "hero" ? `<header class="sec sec-hero" id="hero">${h}</header>` : `<section class="sec sec-${k}" id="${k}">${h}</section>`));
  // ctx.rendered: the sections that drew something, in page order.
  ctx.rendered = [];
  const inner = ctx.page && ctx.page.key !== "home";
  const head = inner ? `<header class="sec sec-ph" id="top">${((opts.partials && opts.partials.pagehead) || pageHead)(ctx)}</header>` : "";
  const parts = drawSections(site, t, tokens, variant, opts, P, ctx).filter(([, h]) => h);
  for (const [k] of parts) ctx.rendered.push(k);
  let html = parts.map(([k, h]) => wrap(k, h, ctx));
  if (pp) html = [(opts.planPage || defaultPlanPage)(pp, ctx)];
  // Two sections the page model pairs (Contact: the FAQ beside "For my customers") share one row when both are there.
  const pair = inner && ctx.page.def.pair;
  if (pair) {
    const at = parts.findIndex(([k]) => k === pair[0]);
    if (at >= 0 && parts[at + 1] && parts[at + 1][0] === pair[1]) html.splice(at, 2, `<div class="duo">${html[at]}${html[at + 1]}</div>`);
  }
  const help = inner && ctx.page.def.helpBand ? helpBand(ctx) : "";
  const after = inner && opts.afterInner ? opts.afterInner(ctx) : "";
  const bar = callbar(ctx);
  return `<div class="tpl tpl-${variant} fam-${ctx.family}${ctx.page ? " pg-" + ctx.page.key : ""}${bar ? " has-bar" : ""}" style="--accent:${accent}">${opts.before ? opts.before(ctx) : ""}${head}${html.join("\n")}${help}${after}${footer(ctx)}${bar}</div>`;
}
