// Customer sign-up pages (DECISIONS #108, docs/WEB_RENDERER.md "Customer sign-up page"). Pure and server-rendered: no fetch, no DOM.
// worker.js loads the data and calls these; every string goes through esc() and the i18n dict. The only script is /join-client.js
// (autosave); every page also works without it.
import { makeT } from "./i18n.js";
import { ctxOf, header } from "./portal.js";
import { esc, wa, tel } from "./sections/util.js";
import { isMinor, todayIST } from "./join-age.js";

const FONT_PRELOAD = '<link rel="preload" href="/fonts/inter-latin-400-normal.woff2" as="font" type="font/woff2" crossorigin>';
const CSS = ["/styles/fonts.css", "/tokens.css", "/styles/base.css", "/styles/portal.css", "/styles/join.css"];

// The customer sign-up page's links (server-rendered only). base is also the ap_join cookie Path.
export function joinLinks(target) {
  const site = target.host ? "/" : "/a/" + encodeURIComponent(target.slug) + "/";
  const base = site + "join";
  return { base, home: base, consent: base + "/consent", save: base + "/save", submit: base + "/submit", done: base + "/done", site, privacy: site + "privacy" };
}

// The form's fields in order: key (= the RPC's field key), i18n label, input attributes.
export const JOIN_FIELDS = [
  { key: "name", attrs: 'type="text" autocomplete="name" maxlength="80" required' },
  { key: "phone", attrs: 'type="tel" inputmode="tel" autocomplete="tel" maxlength="20" required', hint: true },
  { key: "email", attrs: 'type="email" inputmode="email" autocomplete="email" maxlength="120"' },
  { key: "dob", attrs: 'type="date" min="1900-01-01" autocomplete="bday"' },
  { key: "address", attrs: 'type="text" autocomplete="street-address" maxlength="200"' },
  { key: "city", attrs: 'type="text" autocomplete="address-level2" maxlength="60"' },
  { key: "nominee", attrs: 'type="text" maxlength="80"' },
];
export const JOIN_KEYS = JOIN_FIELDS.map((f) => f.key);

function shell({ locale, title, accent, body, script }) {
  const style = accent ? ` style="--accent:${/^#[0-9a-fA-F]{6}$/.test(accent) ? accent : "var(--c-primary)"}"` : "";
  return `<!doctype html>
<html lang="${esc(locale)}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title>
<meta name="robots" content="noindex,nofollow">
${[FONT_PRELOAD, ...CSS.map((h) => `<link rel="stylesheet" href="${esc(h)}">`)].join("\n")}
</head>
<body>
<div class="tpl portal join"${style}>
${body}
</div>
${script ? '<script type="module" src="/join-client.js"></script>\n' : ""}</body>
</html>
`;
}

function ctx({ site, target, dict, shared, config }) {
  const x = ctxOf({ site, target, dict, shared, config });
  x.links = joinLinks(target);
  const k = (site.content || {}).contact || {}, p = site.profile || {};
  const none = x.t("site.privacy_not_given");
  x.params = { name: x.name, phone: k.phone || p.phone || none, email: k.email || none };
  return x;
}
const page = (x, inner, script) => shell({ locale: x.locale, title: x.t("join.page_title", { name: x.name }), accent: x.accent, script, body: `${header(x)}\n<main class="pt-main">\n${inner}\n</main>` });
const noticeKeys = (notices, key, fallback) => {
  const n = ((notices && notices.notices) || notices || {})[key];
  return (n && n.textKeys) || fallback;
};
const errText = (x, key) => (/^join\.err_[a-z_]+$/.test(String(key)) ? x.t(key, { name: x.name }) : x.t("join.err_generic"));
const topErr = (x, key) => (key ? `<p class="err pt-err" role="alert">${esc(errText(x, key))}</p>` : "");

function agentActions(x) {
  const call = tel(x.phone), chat = wa(x.whatsapp);
  return call || chat ? `<p class="pt-acts">${call ? `<a class="btn" href="${esc(call)}">${esc(x.t("site.call"))}</a>` : ""}${chat ? `<a class="btn ghost" href="${esc(chat)}" rel="noopener">${esc(x.t("site.whatsapp"))}</a>` : ""}</p>` : "";
}

// ---- consent: the notice, then Start ----
export function joinConsentDocument({ site, target, dict, shared, config, notices, error }) {
  const x = ctx({ site, target, dict, shared, config }), { t } = x;
  const k = noticeKeys(notices, "customer_signup", ["privacy.customer_signup.consent", "privacy.customer_signup.link", "privacy.customer_signup.title"]);
  const body = k.slice(3).map((key) => `<p>${esc(t(key, x.params))}</p>`).join("");
  const cErr = !!error;
  const inner = `<h1>${esc(t("join.consent_heading"))}</h1>
<p class="lead">${esc(t("join.consent_intro", { name: x.name }))}</p>
<section class="card jn-notice" aria-labelledby="h-notice"><h2 id="h-notice">${esc(t(k[2] || "privacy.customer_signup.title"))}</h2>${body}<p><a class="lnk" href="${esc(x.links.privacy + "#signup")}">${esc(t(k[1] || "privacy.customer_signup.link"))}</a></p></section>
<form class="enq pt-form" method="post" action="${esc(x.links.consent)}">
<div class="fld"><label class="chk" for="f-consent"><input type="checkbox" id="f-consent" name="consent" value="1" required${cErr ? ' aria-invalid="true" aria-describedby="e-consent"' : ""}><span>${esc(t(k[0], x.params))}</span></label>${cErr ? `<p class="err" id="e-consent" role="alert">${esc(errText(x, error))}</p>` : ""}</div>
<button class="btn" type="submit">${esc(t("join.start"))}</button></form>`;
  return page(x, inner);
}

// ---- the form ----
// values: the saved or typed values; error: { key, field } from a rejected submit (field = an RPC field key or "guardian").
export function joinFormDocument({ site, target, dict, shared, config, notices, values = {}, error, today }) {
  const x = ctx({ site, target, dict, shared, config }), { t } = x;
  const gk = noticeKeys(notices, "customer_signup_guardian", ["privacy.customer_signup_guardian.title", "privacy.customer_signup_guardian.notice", "privacy.customer_signup_guardian.consent"]);
  const v = (k) => (typeof values[k] === "string" ? values[k] : "");
  const errField = error && error.field, errKey = error && error.key;
  const fields = JOIN_FIELDS.map((f) => {
    const here = errField === f.key && errKey;
    const desc = [f.hint ? "h-phone" : "", here ? `e-${f.key}` : ""].filter(Boolean).join(" ");
    const val = v(f.key);
    return `<div class="fld"><label for="f-${f.key}">${esc(t("join.field_" + f.key))}</label><input id="f-${f.key}" name="${f.key}" ${f.attrs}${desc ? ` aria-describedby="${desc}"` : ""}${here ? ' aria-invalid="true"' : ""}${val !== "" ? ` value="${esc(val)}"` : ""}>${f.hint ? `<p class="hint" id="h-phone">${esc(t("join.hint_phone"))}</p>` : ""}${here ? `<p class="err" id="e-${f.key}" role="alert">${esc(errText(x, errKey))}</p>` : ""}</div>`;
  }).join("\n");
  // The guardian block is always in the page (hidden until the date of birth makes the person a minor; join-client.js toggles it).
  const minor = isMinor(v("dob"), today || todayIST()) || errField === "guardian";
  const gErr = errField === "guardian" && errKey;
  const guardian = `<div class="fld jn-guardian" id="guardian"${minor ? "" : " hidden"}><p class="hint" id="h-guardian">${esc(t(gk[1], x.params))}</p><label class="chk" for="f-guardian"><input type="checkbox" id="f-guardian" name="guardian" value="1" aria-describedby="h-guardian${gErr ? " e-guardian" : ""}"${gErr ? ' aria-invalid="true"' : ""}${values.guardian === "1" ? " checked" : ""}><span>${esc(t(gk[2], x.params))}</span></label>${gErr ? `<p class="err" id="e-guardian" role="alert">${esc(errText(x, errKey))}</p>` : ""}</div>`;
  const top = errKey && !errField ? topErr(x, errKey) : "";
  const inner = `<h1>${esc(t("join.heading"))}</h1>
<p class="lead">${esc(t("join.intro", { name: x.name }))}</p>
${top}<form class="enq pt-form" id="join-form" method="post" action="${esc(x.links.submit)}" data-save="${esc(x.links.save)}" data-saving="${esc(t("join.status_saving"))}" data-saved="${esc(t("join.status_saved"))}" data-failed="${esc(t("join.status_failed"))}">
${fields}
${guardian}
<p class="hint jn-status" id="join-status" role="status" aria-live="polite"></p>
<button class="btn" type="submit">${esc(t("join.submit"))}</button></form>`;
  return page(x, inner, true);
}

// ---- invalid / expired / used link ----
export function joinInvalidDocument({ site, target, dict, shared, config }) {
  const x = ctx({ site, target, dict, shared, config });
  const inner = `<h1>${esc(x.t("join.heading"))}</h1>
<p class="err pt-err" role="alert">${esc(x.t("join.err_link", { name: x.name }))}</p>
<section class="card pt-adv"><p><strong>${esc(x.name)}</strong></p>${agentActions(x)}</section>`;
  return page(x, inner);
}

// ---- thank-you ----
export function joinDoneDocument({ site, target, dict, shared, config }) {
  const x = ctx({ site, target, dict, shared, config });
  const inner = `<h1>${esc(x.t("join.done_title"))}</h1>
<p class="lead" role="status">${esc(x.t("join.done_text", { name: x.name }))}</p>${agentActions(x)}`;
  return page(x, inner);
}

// ---- error page: no agent data ----
export function joinErrorDocument({ dict, locale = "en" }) {
  const t = makeT(dict || {}, { "join.err_generic": "Something went wrong. Please try again." });
  const l = /^[a-z]{2,3}$/.test(locale) ? locale : "en";
  const msg = t("join.err_generic");
  return shell({ locale: l, title: msg, body: `<main class="pt-main"><div class="state"><h1>${esc(msg)}</h1></div></main>` });
}
