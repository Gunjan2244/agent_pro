// Customer portal pages (DECISIONS #96-#100, docs/WEB_RENDERER.md). Pure and server-rendered: no fetch, no DOM; the only script is the deferred /portal-client.js module.
// worker.js loads the data and calls these; every string goes through esc() and the i18n dict (shared/i18n).
import { makeT, localeKeys, pickLocale } from "./i18n.js";
import { portalLinks } from "./route.js";
import { swatchFor, iconPath, portalManifestHref } from "./manifest.js";
import { esc, firstName, has, arr, wa, tel, digits } from "./sections/util.js";
import { formatDate, todayIST } from "./sections/offer.js";
import { initials } from "./sections/media.js";

const FONT_PRELOAD = '<link rel="preload" href="/fonts/inter-latin-400-normal.woff2" as="font" type="font/woff2" crossorigin>';
const CSS = ["/styles/fonts.css", "/tokens.css", "/styles/base.css", "/styles/portal.css"];
const FLASH = { asked: "portal.asked", requested: "portal.requested", signed_out: "portal.signed_out" };
const byOrder = (a, b) => a.order - b.order;

// ---- head ----
// The one place the head's extra links are built: the portal's own manifest, icons, the iOS app title.
function portalHeadLinks({ swatch, target, t }) {
  const out = [`<link rel="manifest" href="${esc(portalManifestHref(target))}">`, `<meta name="apple-mobile-web-app-title" content="${esc(t("portal.app_short_name"))}">`];
  if (swatch) out.push(`<link rel="icon" href="${esc(iconPath(swatch.key, 192))}">`, `<link rel="apple-touch-icon" href="${esc(iconPath(swatch.key, 192))}">`);
  return out;
}

// links (portalLinks) -> the one deferred client module (SW registration, install button); omitted on the error page.
function shell({ locale, title, links = [], accent, body, app, signedOut }) {
  const style = accent ? ` style="--accent:${/^#[0-9a-fA-F]{6}$/.test(accent) ? accent : "var(--c-primary)"}"` : "";
  return `<!doctype html>
<html lang="${esc(locale)}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title>
<meta name="robots" content="noindex,nofollow">
${[...links, FONT_PRELOAD, ...CSS.map((h) => `<link rel="stylesheet" href="${esc(h)}">`)].join("\n")}
</head>
<body>
<div class="tpl portal"${style}${app ? ` data-sw="${esc(app.sw)}" data-base="${esc(app.base)}"` : ""}${app && signedOut ? " data-signed-out" : ""}>
${body}
</div>
${app ? '<script type="module" src="/portal-client.js"></script>\n' : ""}</body>
</html>
`;
}

// ---- shared context ----
export function ctxOf({ site, target, dict, shared, config }) {
  const catalog = (shared && shared.catalog) || {};
  const locale = pickLocale(localeKeys(catalog), site.locale);
  const t = makeT(dict || {});
  const p = site.profile || {}, c = (site.content || {}).contact || {};
  const name = p.full_name || "";
  const swatch = swatchFor(catalog, site.accent);
  const phone = c.phone || p.phone || "";
  return {
    site, target, catalog, locale, t, name, swatch, config: config || {}, links: portalLinks(target), photo: p.photo_url,
    phone, whatsapp: c.whatsapp || phone,
    accent: swatch ? swatch.hex : "var(--c-primary)",
    p: { name },
  };
}
const title = (x) => x.t("portal.page_title", { name: x.name });

export function header(x) {
  const ini = initials(x.name);
  const ph = /^https?:\/\/[^\s"'<>]+$/i.test(String(x.photo || "")) ? `<img class="pt-photo" src="${esc(x.photo)}" alt="" width="40" height="40">` : ini ? `<span class="pt-photo mono" aria-hidden="true">${esc(ini)}</span>` : "";
  return `<header class="pt-head"><p class="pt-id">${ph}<strong>${esc(x.name)}</strong></p><a class="lnk" href="${esc(x.links.site)}">${esc(x.t("portal.back"))}</a></header>`;
}
const page = (x, inner, links, signedOut) => shell({ signedOut, locale: x.locale, title: title(x), links: [...portalHeadLinks(x), ...(links || [])], accent: x.accent, app: x.links, body: `${header(x)}\n<main class="pt-main">\n${inner}\n</main>` });

// ---- errors, flash, fields ----
const errKey = (e) => (e && /^portal\.err_[a-z_]+$/.test(String(e.key)) ? e.key : null);
function errText(x, e) {
  const k = errKey(e);
  return k ? x.t(k, { name: x.name, ...((e && e.params) || {}) }) : "";
}
const errTop = (x, e) => (errKey(e) && !(e && e.field) ? `<p class="err pt-err" role="alert">${esc(errText(x, e))}</p>` : "");
function flash(x, f, allowed) {
  const k = allowed.includes(f) ? FLASH[f] : null;
  return k ? `<p class="pt-flash" role="status">${esc(x.t(k, { name: x.name }))}</p>` : "";
}
// A labelled input with an optional hint and the error bound by aria-describedby.
function fld(x, { id, name = id, label, error, hint, attrs = "", tag = "input", value = "" }) {
  const here = errKey(error) && error.field === id;
  const desc = [hint ? `h-${id}` : "", here ? `e-${id}` : ""].filter(Boolean).join(" ");
  const common = `id="f-${id}" name="${esc(name)}"${desc ? ` aria-describedby="${desc}"` : ""}${here ? ' aria-invalid="true"' : ""} ${attrs}`;
  const control = tag === "textarea" ? `<textarea ${common}>${esc(value)}</textarea>` : `<input ${common}${value !== "" ? ` value="${esc(value)}"` : ""}>`;
  return `<div class="fld"><label for="f-${id}">${esc(label)}</label>${control}${hint ? `<p class="hint" id="h-${id}">${esc(hint)}</p>` : ""}${here ? `<p class="err" id="e-${id}" role="alert">${esc(errText(x, error))}</p>` : ""}</div>`;
}
const PIN = 'type="password" inputmode="numeric" pattern="[0-9]{6}" minlength="6" maxlength="6" required';
const PHONE = 'type="tel" inputmode="tel" autocomplete="tel" maxlength="16" required';
const hidden = (n, v) => `<input type="hidden" name="${n}" value="${esc(v)}">`;

// ---- login ----
export function portalLoginDocument({ site, target, dict, shared, config, error, values = {}, flash: f }) {
  const x = ctxOf({ site, target, dict, shared, config }), { t } = x;
  const num = tel(x.whatsapp) ? wa(x.whatsapp) : "";
  const ask = num ? `<p class="pt-ask">${esc(t("portal.no_pin_yet", { name: x.name }))}</p><p><a class="btn ghost" href="${esc(num + "?text=" + encodeURIComponent(t("portal.ask_link_message", { name: x.name })))}" rel="noopener">${esc(t("portal.ask_link_whatsapp"))}</a></p>` : "";
  const inner = `<h1>${esc(t("portal.login_heading"))}</h1>
${flash(x, f, ["signed_out"])}<p class="lead">${esc(t("portal.login_intro", { name: x.name }))}</p>
${errTop(x, error)}<form class="enq pt-form" method="post" action="${esc(x.links.login)}">
${fld(x, { id: "phone", label: t("portal.phone"), error, value: values.phone || "", attrs: PHONE })}
${fld(x, { id: "pin", label: t("portal.pin"), error, attrs: PIN + ' autocomplete="current-password"' })}
<button class="btn" type="submit">${esc(t("portal.sign_in"))}</button></form>
<section class="card pt-help">${ask}</section>`;
  return page(x, inner, null, f === "signed_out"); // the marker tells portal-client.js to wipe the service worker's cache
}

// ---- start (first login from an invite link) ----
export function portalStartDocument({ site, target, dict, shared, config, notices, error, values = {} }) {
  const x = ctxOf({ site, target, dict, shared, config }), { t } = x;
  const nf = (notices && notices.notices) || notices || {};
  const keys = ((nf.customer_portal || {}).textKeys) || ["privacy.customer_portal.consent", "privacy.customer_portal.link"];
  const cErr = errKey(error) && error.field === "consent";
  const inner = `<h1>${esc(t("portal.start_heading"))}</h1>
<p class="lead">${esc(t("portal.start_intro"))}</p>
${errTop(x, error)}<form class="enq pt-form" method="post" action="${esc(x.links.start)}">
${fld(x, { id: "phone", label: t("portal.phone"), error, value: values.phone || "", attrs: PHONE })}
${fld(x, { id: "pin", label: t("portal.new_pin"), error, hint: t("portal.pin_hint"), attrs: PIN + ' autocomplete="new-password"' })}
${fld(x, { id: "pin2", label: t("portal.confirm_pin"), error, attrs: PIN + ' autocomplete="new-password"' })}
<div class="fld"><label class="chk" for="f-consent"><input type="checkbox" id="f-consent" name="consent" value="1" required${cErr ? ' aria-invalid="true" aria-describedby="e-consent"' : ""}><span>${esc(t(keys[0], { name: x.name }))} <a href="${esc(x.links.privacy + "#portal")}">${esc(t(keys[1]))}</a></span></label>${cErr ? `<p class="err" id="e-consent" role="alert">${esc(errText(x, error))}</p>` : ""}</div>
<button class="btn" type="submit">${esc(t("portal.start_button"))}</button></form>`;
  return page(x, inner);
}

// ---- home ----
const nf = (loc, cur) => new Intl.NumberFormat(loc === "hi" ? "hi-IN" : "en-IN", { style: "currency", currency: "INR", maximumFractionDigits: cur % 1 ? 2 : 0, minimumFractionDigits: cur % 1 ? 2 : 0 });
export function money(v, locale) {
  const n = Number(v);
  if (v == null || v === "" || !Number.isFinite(n)) return "";
  try { return nf(locale, n).format(n); } catch { return String(n); }
}
const DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
const dayNo = (iso) => { const m = DATE.exec(String(iso || "")); return m ? Date.UTC(+m[1], +m[2] - 1, +m[3]) / 86400000 : null; };
// The catalog due bucket for a date, by whole days from today (negative = overdue); null for a bad date.
export function dueBucket(catalog, iso, today) {
  const a = dayNo(iso), b = dayNo(today);
  if (a == null || b == null) return null;
  const d = a - b;
  return arr(catalog.dueBuckets).slice().sort(byOrder).find((k) => (k.minDays == null || d >= k.minDays) && (k.maxDays == null || d <= k.maxDays)) || null;
}
const pill = (x, item) => (item ? `<span class="pill tone-${esc(item.tone || "neutral")}">${esc(x.t(item.labelKey))}</span>` : "");
const find = (list, key) => arr(list).find((k) => k.key === key);

// L3: which form a rejected ask/request belongs to. x.formErr = { error, values } from the Worker's re-render; the matching form
// opens, shows the error as an alert and keeps the typed message. Unmatched errors are shown at the top of the page instead.
function formErr(x, idField, id, kind, kinds) {
  const fe = x.formErr, none = { open: "", alert: "", message: undefined };
  if (!fe || !errKey(fe.error) || !fe.values || !id || fe.values[idField] !== id) return none;
  const k = kinds.includes(fe.values.kind) ? fe.values.kind : kinds[0];
  if (k !== kind) return none;
  fe.shown = true;
  return { open: " open", alert: `<p class="err pt-err" role="alert">${esc(errText(x, fe.error))}</p>`, message: fe.values.message };
}

function policyCard(x, pol, today, idx) {
  const { t, locale, catalog } = x;
  const mode = find(catalog.premiumModes, pol.premium_mode);
  const amt = money(pol.premium_amount, locale), modeLabel = mode ? t(mode.labelKey) : pol.premium_mode || "";
  const premium = amt ? t("portal.premium_line", { amount: amt, mode: modeLabel }).replace(/ · $/, "") : modeLabel;
  const sum = money(pol.sum_assured, locale);
  const status = pol.status && pol.status !== "active" ? find(catalog.policyStatuses, pol.status) : null;
  const bucket = !status && pol.next_due_date ? dueBucket(catalog, pol.next_due_date, today) : null;
  const dueDate = formatDate(pol.next_due_date, locale);
  const dueLine = status ? pill(x, status) : dueDate ? `<span>${esc(t("portal.next_due", { date: dueDate }))}</span>${pill(x, bucket)}` : `<span>${esc(t("portal.no_due_date"))}</span>`;
  const pays = arr(pol.payments);
  const payHtml = pays.length ? `<ul class="list pt-pays">${pays.map((p) => `<li>${esc(t("portal.payment_line", { amount: money(p.amount, locale), date: formatDate(p.paid_on, locale) }))}</li>`).join("")}</ul>` : `<p>${esc(t("portal.no_payments"))}</p>`;
  const prefill = t("portal.ask_paid_prefill", { date: dueDate || formatDate(today, locale) });
  const askForm = (kind, label, text) => { const fe = formErr(x, "policy_id", pol.id, kind, ["question", "paid"]); return `<details class="more pt-more"${fe.open}><summary>${esc(label)}</summary>${fe.alert}
<form class="pt-sub" method="post" action="${esc(x.links.ask)}">${hidden("policy_id", pol.id)}${hidden("kind", kind)}
${fld(x, { id: `m-${kind}-${idx}`, name: "message", label: t("portal.ask_message"), tag: "textarea", value: fe.message ?? text, attrs: 'rows="3" maxlength="500" required' })}
<button class="btn ghost" type="submit">${esc(t("portal.ask_send", { name: x.name }))}</button></form></details>`; };
  return `<li class="card pt-pol">
<h3>${esc(pol.plan_name)}</h3>
${has(pol.policy_number) ? `<p>${esc(t("portal.policy_number", { number: pol.policy_number }))}</p>` : ""}
${premium ? `<p class="pt-prem">${esc(premium)}</p>` : ""}${sum ? `<p>${esc(t("portal.sum_assured", { amount: sum }))}</p>` : ""}
<p class="pt-due">${dueLine}</p>
<details class="more pt-more"><summary>${esc(t("portal.payments"))}</summary>${payHtml}</details>
${askForm("question", t("portal.ask_question"), "")}${askForm("paid", t("portal.ask_paid"), prefill)}
</li>`;
}

const isActive = (p) => !p.status || p.status === "active";
// F6 summary figures over active policies only; modes missing from the catalog are skipped for the yearly premium.
export function portalSummary(catalog, customers, today) {
  const months = {}; for (const m of arr(catalog.premiumModes)) if (Number(m.months) > 0) months[m.key] = Number(m.months);
  let count = 0, perYear = 0, cover = 0, due = null;
  for (const c of customers) for (const p of arr(c.policies)) {
    if (!isActive(p)) continue;
    count++;
    const amt = Number(p.premium_amount), mo = months[p.premium_mode];
    if (p.premium_amount != null && p.premium_amount !== "" && Number.isFinite(amt) && mo) perYear += amt * (12 / mo);
    const sa = Number(p.sum_assured); if (p.sum_assured != null && p.sum_assured !== "" && Number.isFinite(sa)) cover += sa;
    if (DATE.test(String(p.next_due_date || "")) && (!due || p.next_due_date < due)) due = p.next_due_date;
  }
  return { count, perYear: Math.round(perYear * 100) / 100, cover, due, bucket: due ? dueBucket(catalog, due, today) : null, people: customers.length };
}

function summaryTiles(x, customers, today) {
  const { t, locale } = x, s = portalSummary(x.catalog, customers, today);
  const tile = (key, value, tone) => `<div class="pt-tile${tone ? " tone-" + esc(tone) : ""}"><dt>${esc(t(key))}</dt><dd>${value}</dd></div>`;
  const due = s.due ? `${esc(formatDate(s.due, locale))}${s.bucket ? " " + pill(x, s.bucket) : ""}` : esc(t("portal.no_dues"));
  return `<dl class="pt-tiles">${tile("portal.summary_policies", esc(new Intl.NumberFormat(locale === "hi" ? "hi-IN" : "en-IN").format(s.count)))}${tile("portal.summary_premium_year", esc(money(s.perYear, locale)))}${s.cover > 0 ? tile("portal.summary_cover", esc(money(s.cover, locale))) : ""}${tile("portal.summary_next_due", due, s.bucket && s.bucket.tone)}${customers.length > 1 ? tile("portal.summary_people", esc(new Intl.NumberFormat(locale === "hi" ? "hi-IN" : "en-IN").format(customers.length))) : ""}</dl>`;
}

function detailsCard(x, c, idx) {
  const { t, locale } = x;
  const row = (k, v) => `<div class="pt-row"><dt>${esc(t(k))}</dt><dd>${has(v) ? esc(v) : `<span class="meta">${esc(t("portal.not_given"))}</span>`}</dd></div>`;
  const req = (kind, summary, body) => { const fe = formErr(x, "customer_id", c.id, kind, ["correction", "erasure"]); return `<details class="more pt-more"${fe.open}><summary>${esc(summary)}</summary>${fe.alert}<form class="pt-sub" method="post" action="${esc(x.links.request)}">${hidden("customer_id", c.id)}${hidden("kind", kind)}${kind === "correction" ? body(fe.message) : ""}<button class="btn ghost" type="submit">${esc(t("portal.request_send"))}</button></form></details>`; };
  const msg = (m) => fld(x, { id: `r-${idx}`, name: "message", label: t("portal.request_message"), tag: "textarea", value: m ?? "", attrs: 'rows="3" maxlength="500" required' });
  return `<div class="card pt-det"><h3>${esc(c.name)}</h3><dl>
${row("portal.field_name", c.name)}${row("portal.field_phone", c.phone)}${row("portal.field_email", c.email)}${row("portal.field_dob", formatDate(c.dob, locale))}${row("portal.field_city", c.city)}${row("portal.field_address", c.address)}${row("portal.field_nominee", c.nominee)}</dl>
${req("correction", t("portal.ask_correct"), msg)}${req("erasure", t("portal.ask_delete"), null)}</div>`;
}

// "Add to home screen", home page only: hidden until portal-client.js (install.js) knows the browser can install.
const installBox = (x) => `<div class="install pt-install" id="install" hidden data-ios="${esc(x.t("site.install_ios"))}" data-menu="${esc(x.t("site.install_menu"))}" data-note="${esc(x.t("portal.install_ios_note"))}"><button class="btn ghost" type="button" id="install-btn">${esc(x.t("portal.install_button"))}</button><p class="hint" id="install-how" hidden></p></div>`;

export function portalHomeDocument({ site, target, dict, shared, config, home, flash: f, today, error, values }) {
  const x = ctxOf({ site, target, dict, shared, config }), { t, locale } = x;
  x.formErr = { error, values };
  today = DATE.test(String(today || "")) ? today : todayIST();
  const customers = arr(home && home.customers);
  const multi = customers.length > 1;
  const hello = firstName((customers[0] || {}).name);
  const tiles = summaryTiles(x, customers, today);
  const lists = customers.map((c, i) => {
    const pols = arr(c.policies);
    const h = multi ? t("portal.policies_of", { name: c.name }) : t("portal.policies_heading");
    return `<section aria-labelledby="h-pol-${i}"><h2 id="h-pol-${i}">${esc(h)}</h2>${pols.length ? `<ul class="pt-pols">${pols.map((p, j) => policyCard(x, p, today, `${i}-${j}`)).join("")}</ul>` : `<p class="card">${esc(t("portal.no_policies", { name: c.name }))}</p>`}</section>`;
  }).join("\n");
  const call = tel(x.phone), chat = wa(x.whatsapp);
  const advisor = `<section class="card pt-adv" aria-labelledby="h-adv"><h2 id="h-adv">${esc(t("portal.agent_heading"))}</h2><p><strong>${esc(x.name)}</strong></p><p class="pt-acts">${call ? `<a class="btn" href="${esc(call)}">${esc(t("site.call"))}</a>` : ""}${chat ? `<a class="btn ghost" href="${esc(chat)}" rel="noopener">${esc(t("site.whatsapp"))}</a>` : ""}</p></section>`;
  const details = `<section aria-labelledby="h-det"><h2 id="h-det">${esc(t("portal.details_heading"))}</h2><p class="hint">${esc(t("portal.details_intro", { name: x.name }))}</p>${customers.map((c, i) => detailsCard(x, c, i)).join("")}</section>`;
  const out = (label, every) => `<form method="post" action="${esc(x.links.logout)}">${every ? hidden("everywhere", "1") : ""}<button class="btn ghost" type="submit">${esc(label)}</button></form>`;
  const inner = `<h1>${esc(has(hello) ? t("portal.hello", { name: hello }) : t("portal.title"))}</h1>
${flash(x, f, ["asked", "requested"])}${x.formErr.shown ? "" : errTop(x, error ? { ...error, field: undefined } : null)}${tiles}
${installBox(x)}
${lists}
${advisor}
${details}
<div class="pt-out">${out(t("portal.sign_out"), false)}${out(t("portal.sign_out_all"), true)}</div>`;
  return page(x, inner);
}

// ---- error page: no agent data ----
export function portalErrorDocument({ dict, locale = "en" }) {
  const t = makeT(dict || {}, { "portal.err_generic": "Something went wrong. Please try again." });
  const l = /^[a-z]{2,3}$/.test(locale) ? locale : "en";
  const msg = t("portal.err_generic");
  return shell({ locale: l, title: msg, body: `<main class="pt-main"><div class="state"><h1>${esc(msg)}</h1></div></main>` });
}
