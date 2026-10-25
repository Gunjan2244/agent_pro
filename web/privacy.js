// Per-site privacy page (/a/<slug>/privacy, /privacy on a custom domain) and its data-request form.
// The notice text comes from shared/generated/privacy-notices.json (enquiry_form textKeys) rendered with t(); never hand-written here.
import { submitDataRequest } from "./api.js";
import { validate } from "./form.js";
import { esc, firstName, has } from "./sections/util.js";

export const kindsOf = (catalog) => ((catalog && catalog.dataRequestKinds) || []).slice().sort((a, b) => a.order - b.order);

// Same field rules as submit_data_request: name <= 80, Indian mobile, kind from the catalog, message <= 500.
export function validateRequest(v, kinds) {
  const { errors, phone } = validate({ name: v.name, phone: v.phone, message: v.message }, null);
  if (!v.kind || !kinds.includes(v.kind)) errors.kind = "required";
  return { errors, phone };
}

export function agentContact(site) {
  const k = (site.content || {}).contact || {}, p = site.profile || {};
  return { name: p.full_name || "", phone: k.phone || p.phone || "", email: k.email || "" };
}

export function privacyHtml(site, t, { notice, catalog, backHref }) {
  const a = agentContact(site), none = t("site.privacy_not_given");
  const params = { name: a.name, phone: a.phone || none, email: a.email || none };
  const keys = (notice && notice.textKeys) || [];
  const body = keys.slice(3).map((k) => `<p>${esc(t(k, params))}</p>`).join("");
  const nf = (key) => ((catalog && catalog.privacyNotices) || []).find((n) => n.key === key);
  const sec = (id, on) => { const k = (on && nf(id === "portal" ? "customer_portal" : "customer_signup") || {}).textKeys || []; return k.length > 2 ? `<section id="${id}"><h2>${esc(t(k[2]))}</h2>${k.slice(3).map((x) => `<p>${esc(t(x, params))}</p>`).join("")}</section>` : ""; };
  const opts = kindsOf(catalog).map((k) => `<option value="${esc(k.key)}">${esc(t(k.labelKey))}</option>`).join("");
  const f = (id, label, input) => `<div class="fld"><label for="r-${id}">${esc(label)}</label>${input}<p class="err" id="re-${id}" role="alert" hidden></p></div>`;
  return `<div class="doc"><nav class="nav"><a href="${esc(backHref || "/")}">${esc(t("site.privacy_back"))}</a></nav>
<h1>${esc(t(keys[2] || "privacy.enquiry_form.title"))}</h1>
<p>${esc(t("site.privacy_fiduciary", params))}</p>${body}
<p>${esc(t("site.privacy_contact", params))}</p>
${sec("portal", site.portal === true)}
${sec("signup", true)}
<h2>${esc(t("site.privacy_how_title"))}</h2><p>${esc(t("site.privacy_how_text", params))}</p>
<form class="enq" id="req" novalidate><h3>${esc(t("site.request_title"))}</h3>
${f("kind", t("site.request_kind"), `<select id="r-kind" name="kind">${opts}</select>`)}
${f("name", t("site.form_name"), '<input id="r-name" name="name" autocomplete="name" maxlength="80" required>')}
${f("phone", t("site.form_phone"), '<input id="r-phone" name="phone" type="tel" inputmode="numeric" autocomplete="tel" maxlength="16" required>')}
${f("message", t("site.request_message"), '<textarea id="r-message" name="message" rows="3" maxlength="500"></textarea>')}
<p class="hint">${esc(t("site.request_note"))}</p>
<div class="hp" aria-hidden="true"><label>Website<input name="website" tabindex="-1" autocomplete="off"></label></div>
<p class="err" id="re-form" role="alert" hidden></p>
<button class="btn" type="submit">${esc(t("site.request_submit"))}</button></form></div>`;
}

export function wirePrivacy(root, ctx) {
  const form = root.querySelector("#req");
  if (!form) return;
  const { t } = ctx;
  const kinds = kindsOf(ctx.catalog).map((k) => k.key);
  const show = (id, text) => {
    const m = form.querySelector("#re-" + id), inp = form.querySelector("#r-" + id);
    if (m) { m.textContent = text || ""; m.hidden = !text; }
    if (inp) { if (text) inp.setAttribute("aria-invalid", "true"); else inp.removeAttribute("aria-invalid"); }
  };
  const msg = (e) => t(e === "phone" ? "site.form_error_phone" : "site.form_error_required");
  form.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const v = Object.fromEntries(new FormData(form));
    const { errors, phone } = validateRequest(v, kinds);
    for (const id of ["kind", "name", "phone", "message"]) show(id, errors[id] ? msg(errors[id]) : "");
    if (Object.keys(errors).length) { form.querySelector('[aria-invalid="true"]')?.focus(); return; }
    const btn = form.querySelector("button[type=submit]"), label = btn.textContent;
    btn.disabled = true; btn.textContent = t("site.form_sending"); show("form", "");
    try {
      const r = await (ctx.submit || submitDataRequest)({ slug: ctx.slug, host: ctx.host, name: v.name.trim(), phone, kind: v.kind, message: (v.message || "").trim(), honeypot: v.website || "" });
      if (r.status === "invalid" && ["kind", "name", "phone", "message"].includes(r.field)) {
        btn.disabled = false; btn.textContent = label; show(r.field, msg(r.field === "phone" ? "phone" : "required")); form.querySelector("#r-" + r.field)?.focus(); return;
      }
      if (r.status === "invalid" || r.status === "unavailable") throw new Error(r.status);
      const name = has(ctx.name) ? firstName(ctx.name) : "";
      form.outerHTML = `<div class="enq done" role="status"><p>${esc(t(r.status === "rate_limited" ? "site.request_rate_limited" : "site.request_sent", { name }))}</p></div>`;
    } catch {
      btn.disabled = false; btn.textContent = label; show("form", t("site.form_error_generic"));
    }
  });
}
