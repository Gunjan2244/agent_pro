import { esc, url, has, tel, wa, mail, arr } from "./util.js";
function form(ctx) {
  const { t, catalog } = ctx;
  const opts = arr(catalog.interests).slice().sort((a, b) => a.order - b.order)
    .map((i) => `<option value="${esc(i.key)}">${esc(t(i.labelKey))}</option>`).join("");
  const blank = `<option value="" selected>${esc(t("site.form_interest_placeholder"))}</option>`;
  const f = (id, label, input) => `<div class="fld"><label for="f-${id}">${esc(label)}</label>${input}<p class="err" id="e-${id}" role="alert" hidden></p></div>`;
  return `<form class="enq" id="enq" novalidate>
<h3>${esc(t("site.form_title"))}</h3>
${f("name", t("site.form_name"), '<input id="f-name" name="name" autocomplete="name" maxlength="80" required>')}
${f("phone", t("site.form_phone"), '<input id="f-phone" name="phone" type="tel" inputmode="numeric" autocomplete="tel" maxlength="16" required>')}
${f("email", t("site.form_email"), '<input id="f-email" name="email" type="email" autocomplete="email" maxlength="120">')}
${f("interest", t("site.form_interest"), `<select id="f-interest" name="interest">${blank}${opts}</select>`)}
${f("message", t("site.form_message"), '<textarea id="f-message" name="message" rows="3" maxlength="500" aria-describedby="h-message"></textarea>')}
<p class="hint" id="h-message">${esc(t("site.form_message_hint"))}</p>
<div class="hp" aria-hidden="true"><label>Website<input name="website" tabindex="-1" autocomplete="off"></label></div>
<div class="fld consent"><label class="chk" for="f-consent"><input id="f-consent" name="consent" type="checkbox" aria-describedby="e-consent"><span>${esc(t(ctx.consentKey, { name: ctx.agentName }))} <a href="${esc(ctx.privacyHref || "#")}">${esc(t(ctx.consentLinkKey))}</a></span></label><p class="err" id="e-consent" role="alert" hidden></p></div>
<p class="err" id="e-form" role="alert" hidden></p>
<button class="btn" type="submit">${esc(t("site.form_submit"))}</button>
</form>`;
}
export function contact(d, ctx) {
  const { t } = ctx;
  const phone = d.phone || ctx.profile.phone;
  const map = url(d.mapLink);
  const rows = [];
  if (tel(phone)) rows.push(`<a class="btn" href="${tel(phone)}">${esc(t("site.call"))}: ${esc(phone)}</a>`);
  if (wa(d.whatsapp)) rows.push(`<a class="btn ghost" href="${wa(d.whatsapp)}" rel="noopener">${esc(t("site.whatsapp"))}</a>`);
  if (mail(d.email)) rows.push(`<a class="btn ghost" href="${mail(d.email)}">${esc(t("site.email"))}: ${esc(d.email)}</a>`);
  const info = [];
  if (has(d.officeAddress)) info.push(`<p><strong>${esc(t("site.office_address"))}</strong><br><span class="prose">${esc(d.officeAddress)}</span>${map ? ` <a href="${map}" rel="noopener">${esc(t("site.view_map"))}</a>` : ""}</p>`);
  else if (map) info.push(`<p><a href="${map}" rel="noopener">${esc(t("site.view_map"))}</a></p>`);
  if (has(d.officeHours)) info.push(`<p><strong>${esc(t("site.office_hours"))}</strong><br>${esc(d.officeHours)}</p>`);
  return `<h2>${esc(t("section.contact"))}</h2>
<div class="contact-grid"><div class="contact-info">${rows.length ? `<div class="actions">${rows.join("")}</div>` : ""}${info.join("")}</div>
${d.showEnquiryForm !== false ? form(ctx) : ""}</div>`;
}
