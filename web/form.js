import { submitEnquiry } from "./api.js";
import { firstName, tenDigits, esc, tel } from "./sections/util.js";

// Same rules as submit_enquiry (BACKEND.md): lengths, Indian mobile, interest from the catalog whitelist.
const PHONE = /^[6-9]\d{9}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export function validate(v, interests) {
  const e = {};
  const name = (v.name || "").trim(), email = (v.email || "").trim(), msg = (v.message || "").trim();
  if (!name) e.name = "required"; else if (name.length > 80) e.name = "required";
  const ph = (v.phone || "").replace(/[\s-]/g, "").replace(/^(\+91|91|0)(?=\d{10}$)/, "");
  if (!ph) e.phone = "required"; else if (!PHONE.test(ph)) e.phone = "phone";
  if (email && (!EMAIL.test(email) || email.length > 120)) e.email = "email";
  if (v.interest && interests && !interests.includes(v.interest)) e.interest = "required";
  if (msg.length > 500) e.message = "required";
  return { errors: e, phone: PHONE.test(ph) ? ph : "" };
}
const MSG = { required: "site.form_error_required", phone: "site.form_error_phone", email: "site.form_error_email" };

export function wireForm(root, ctx) {
  const form = root.querySelector("#enq");
  if (!form) return;
  const { t, profile } = ctx;
  const interests = (ctx.catalog.interests || []).map((i) => i.key);
  const show = (id, text) => {
    const m = form.querySelector("#e-" + id), inp = form.querySelector("#f-" + id);
    if (m) { m.textContent = text || ""; m.hidden = !text; }
    if (inp) { if (text) inp.setAttribute("aria-invalid", "true"); else inp.removeAttribute("aria-invalid"); }
  };
  const read = () => Object.fromEntries(new FormData(form));
  const check = (only) => {
    const { errors, phone } = validate(read(), interests);
    for (const id of ["name", "phone", "email", "interest", "message"]) if (!only || only === id) show(id, errors[id] ? t(MSG[errors[id]]) : "");
    return { errors, phone };
  };
  form.addEventListener("focusout", (ev) => { if (ev.target.name && ev.target.value) check(ev.target.name); });
  form.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const { errors, phone } = check();
    if (Object.keys(errors).length) { form.querySelector('[aria-invalid="true"]')?.focus(); return; }
    const btn = form.querySelector("button[type=submit]");
    const label = btn.textContent;
    btn.disabled = true; btn.textContent = t("site.form_sending"); show("form", "");
    const v = read();
    try {
      const r = await (ctx.submit || submitEnquiry)({
        slug: ctx.slug, host: ctx.host, name: v.name.trim(), phone, email: (v.email || "").trim(),
        interest: v.interest || null, message: (v.message || "").trim(), honeypot: v.website || "",
      });
      if (r.status === "invalid" && ["name", "phone", "email", "interest", "message"].includes(r.field)) {
        btn.disabled = false; btn.textContent = label;
        show(r.field, t(r.field === "phone" ? "site.form_error_phone" : r.field === "email" ? "site.form_error_email" : "site.form_error_required"));
        form.querySelector("#f-" + r.field)?.focus();
        return;
      }
      if (r.status === "invalid" || r.status === "unavailable") throw new Error(r.status);
      const name = firstName(profile.full_name);
      const call = tel(profile.phone || (ctx.site.content.contact || {}).phone);
      const msg = r.status === "rate_limited" ? t("site.form_rate_limited", { name: esc(name) }) : t("site.form_thank_you", { name: esc(name) });
      form.outerHTML = `<div class="enq done" role="status"><p>${msg}</p>${call ? `<a class="btn" href="${call}">${esc(t("site.call"))}</a>` : ""}</div>`;
    } catch {
      btn.disabled = false; btn.textContent = label; show("form", t("site.form_error_generic"));
    }
  });
}
