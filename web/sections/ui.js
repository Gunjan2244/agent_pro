// Lively building blocks shared by the templates (DECISIONS #117): the top bar, sliders, tickers and count-up numbers.
// Each one is complete without JavaScript: a slider is a scroll-snap row you can swipe, a ticker is a row of text, a
// number shows its final value. web/motion.js adds autoplay, arrows, dots, count-up and the nav menu behaviour, and
// nothing moves under prefers-reduced-motion. Render-only (never loaded by hydrate.js).
import { esc, has, tel } from "./util.js";
import { initials } from "./media.js";
import { portalLogin } from "./portal-promo.js";
import { pageHref, contactHref } from "./links.js";
import { pageUrl } from "../route.js";

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

// The page menu (DECISIONS #122): one link per page of the site that has a menu label (Home, Insurance, About me,
// Contact; plan-type pages are reached from Insurance), the current page marked aria-current="page" (its parent
// "true" on a plan page), then "Customer login" while the portal is on. One page (no ctx.pages): the login only.
export function menuItems(ctx, { login = true } = {}) {
  const li = (ctx.pages || []).filter((p) => !p.plan && tr(ctx, p.def.labelKey)).map((p) => {
    const cur = ctx.page && ctx.page.key === p.key ? ' aria-current="page"' : ctx.page && ctx.page.def.parent === p.key ? ' aria-current="true"' : "";
    return `<li><a href="${esc(pageUrl(ctx.links, p.path))}"${cur}>${esc(tr(ctx, p.def.labelKey))}</a></li>`;
  }).join("");
  const href = portalLogin(ctx), label = tr(ctx, "site.header_portal");
  return li + (login && href && label ? `<li class="tb-login"><a href="${esc(href)}" rel="nofollow">${esc(label)}</a></li>` : "");
}
// The phone menu: a <details> button that drops the same list down (motion.js closes it after a tap).
export const phoneMenu = (ctx, items, cls = "tb-menu") =>
  items ? `<details class="${cls}"><summary aria-label="${esc(tr(ctx, "site.menu") || ctx.t("site.cta_default"))}"><span></span></summary><ul>${items}</ul></details>` : "";

// The top bar: the agent's face and name (a link to Home), the page menu (a row from 1024 px, a menu button on
// phones) and one button: Call, or (cta "contact", or no phone) the way to the contact form.
export function topBar(ctx, { cta = "call" } = {}) {
  const { t, profile: p } = ctx;
  const ini = initials(p.full_name);
  const face = ctx.photo
    ? `<img class="tb-ph" src="${ctx.photo}" width="36" height="36" alt="" decoding="async">`
    : ini ? `<span class="tb-ph mono" aria-hidden="true">${esc(ini)}</span>` : "";
  const items = menuItems(ctx);
  const call = tel(ctx.phone);
  const btn = cta === "call" && call
    ? `<a class="btn tb-cta" href="${call}">${esc(t("site.call"))}</a>`
    : ctx.allVisible.has("contact") ? `<a class="btn tb-cta" href="${esc(contactHref(ctx))}">${esc(t("site.cta_default"))}</a>` : "";
  const home = ctx.pages ? pageHref(ctx, "home") || "#top" : "#hero";
  return `<div class="topbar"><div class="tb-in"><a class="tb-id" href="${esc(home)}">${face}<span>${esc(p.full_name || "")}</span></a>${items ? `<ul class="tb-links">${items}</ul>` : ""}${btn}${phoneMenu(ctx, items)}</div></div>`;
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
