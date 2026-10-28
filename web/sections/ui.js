// Lively building blocks shared by the templates (DECISIONS #117): the top bar, sliders, tickers and count-up numbers.
// Each one is complete without JavaScript: a slider is a scroll-snap row you can swipe, a ticker is a row of text, a
// number shows its final value. web/motion.js adds autoplay, arrows, dots, count-up and the nav menu behaviour, and
// nothing moves under prefers-reduced-motion. Render-only (never loaded by hydrate.js).
import { esc, has, tel } from "./util.js";
import { initials } from "./media.js";
import { portalLogin } from "./portal-promo.js";

// A translation, or "" while the dictionary lacks it (never a raw key on a site).
export const tr = (ctx, key, p) => { const s = ctx.t(key, p); return s && s !== key ? s : ""; };

// slides: HTML strings, each becomes one <li class="slide">. opts.label names the carousel; opts.cls adds a class
// (the template sizes slides with it); opts.auto is the autoplay interval in ms (0: none). One slide: no controls.
export function slider(slides, ctx, { label = "", cls = "", auto = 6000 } = {}) {
  const list = slides.filter(Boolean);
  if (!list.length) return "";
  const many = list.length > 1;
  const nav = many
    ? `<div class="sl-nav" hidden><button class="sl-btn sl-prev" type="button" aria-label="${esc(tr(ctx, "site.slider_prev"))}">‹</button><div class="sl-dots" data-label="${esc(tr(ctx, "site.slider_dot", { n: "#" }))}"></div><button class="sl-btn sl-next" type="button" aria-label="${esc(tr(ctx, "site.slider_next"))}">›</button></div>`
    : "";
  return `<div class="slider${cls ? " " + cls : ""}" data-auto="${many ? auto : 0}" role="region" aria-roledescription="carousel"${label ? ` aria-label="${esc(label)}"` : ""}><ul class="sl-track">${list.map((s) => `<li class="slide">${s}</li>`).join("")}</ul>${nav}</div>`;
}

// items: HTML strings. Two copies of the run scroll as one seamless loop (CSS); the copy is aria-hidden, so a screen
// reader reads the list once. The speed follows the text length, so long and short tickers move alike.
export function ticker(items, { cls = "", label = "" } = {}) {
  const list = items.filter(Boolean);
  if (!list.length) return "";
  const run = list.map((x) => `<li>${x}</li>`).join("");
  const chars = list.join("").replace(/<[^>]*>/g, "").length;
  const secs = Math.max(20, Math.min(90, Math.round(chars / 4)));
  return `<div class="ticker${cls ? " " + cls : ""}" style="--tk:${secs}s"${label ? ` role="region" aria-label="${esc(label)}"` : ""}><ul class="tk-run">${run}</ul><ul class="tk-run" aria-hidden="true">${run}</ul></div>`;
}

// The sections a top bar may link to, in page order; only sections that rendered get a link (ctx.rendered, compose).
const NAV = [["about", "section.about"], ["services", "section.services"], ["offers", "section.offers"], ["testimonials", "section.testimonials"], ["faq", "section.faq"], ["contact", "section.contact"]];

// The top bar: the agent's face and name (a link to the top), section links (a row from 1024 px, a menu on phones),
// "Customer login" when the portal is on, and one Call button. opts.links = false: no section links (guide).
export function topBar(ctx, { links = true, cta = "call" } = {}) {
  const { t, profile: p } = ctx;
  const ini = initials(p.full_name);
  const face = ctx.photo
    ? `<img class="tb-ph" src="${ctx.photo}" width="36" height="36" alt="" decoding="async">`
    : ini ? `<span class="tb-ph mono" aria-hidden="true">${esc(ini)}</span>` : "";
  const order = [...(ctx.rendered || [])];
  const items = links ? NAV.filter(([k]) => order.includes(k)).sort((a, b) => order.indexOf(a[0]) - order.indexOf(b[0])) : [];
  const li = items.map(([k, key]) => `<li><a href="#${k}">${esc(t(key))}</a></li>`).join("");
  const login = portalLogin(ctx), loginLabel = tr(ctx, "site.header_portal");
  const loginLi = login && loginLabel ? `<li class="tb-login"><a href="${esc(login)}" rel="nofollow">${esc(loginLabel)}</a></li>` : "";
  const call = tel(ctx.phone);
  const btn = cta === "call" && call
    ? `<a class="btn tb-cta" href="${call}">${esc(t("site.call"))}</a>`
    : ctx.visible.has("contact") ? `<a class="btn tb-cta" href="#contact">${esc(t("site.cta_default"))}</a>` : "";
  const menu = li || loginLi ? `<details class="tb-menu"><summary aria-label="${esc(tr(ctx, "site.menu") || t("site.cta_default"))}"><span></span></summary><ul>${li}${loginLi}</ul></details>` : "";
  return `<div class="topbar"><div class="tb-in"><a class="tb-id" href="#hero">${face}<span>${esc(p.full_name || "")}</span></a>${li || loginLi ? `<ul class="tb-links">${li}${loginLi}</ul>` : ""}${btn}${menu}</div></div>`;
}

// Years and clients as {n, label} (only when set; never invented, DECISIONS #78). label is the translated text
// without the number ("years of experience"), for the big-number layouts.
export function numbers(about, ctx) {
  const out = [];
  const one = (key, n) => {
    const s = tr(ctx, key, { n: "\u0000" });
    if (s) out.push({ n: String(n), label: s.replace("\u0000", "").replace(/\s+/g, " ").trim() });
  };
  if (has(about.yearsExperience)) one("site.years_experience", about.yearsExperience);
  if (has(about.clientsServed)) one("site.clients_served", about.clientsServed);
  return out;
}

// One big number (counts up in motion.js when it is a plain integer) with its label under it.
export const bigNumber = (x, cls = "num") => `<li class="${cls}"><strong class="cu">${esc(x.n)}</strong><span>${esc(x.label)}</span></li>`;
