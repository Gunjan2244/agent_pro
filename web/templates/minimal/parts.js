// Minimal ("Simple", DECISIONS #85): a one-page visiting card. Template-specific arrangements of the shared section
// data; the data handling and escaping come from the shared partials' helpers. No cards and no images except the
// portrait (the contact partial is the shared one, restyled).
import { esc, has, arr, tel, wa, mail, interestLabel } from "../../sections/util.js";
import { heroParts } from "../../sections/hero.js";
import { aboutData } from "../../sections/about.js";
import { serviceList } from "../../sections/services.js";
import { companyList, companiesHeading } from "../../sections/companies.js";

// A compact header: small portrait + "Name · designation · city" on one line, a smaller <h1>, the
// lead, then an inline Call · WhatsApp · Email link row (the hero's call to action; the separators are CSS) and
// the quick replies as text links. With no contact links, the hero CTA links to the form instead.
export function hero(d, ctx) {
  const x = heroParts(d, ctx, { photo: [56, 56] });
  const { t, profile: p } = ctx;
  const c = (ctx.site.content || {}).contact || {};
  const links = [
    tel(ctx.phone) ? `<li><a href="${tel(ctx.phone)}">${esc(t("site.call"))}</a></li>` : "",
    wa(c.whatsapp) ? `<li><a href="${wa(c.whatsapp)}" rel="noopener">${esc(t("site.whatsapp"))}</a></li>` : "",
    mail(c.email) ? `<li><a href="${mail(c.email)}">${esc(t("site.email"))}</a></li>` : "",
  ].join("");
  const row = links || (x.cta ? `<li><a href="#contact">${esc(x.cta)}</a></li>` : "");
  const who = [has(p.full_name) ? `<strong>${esc(p.full_name)}</strong>` : "", x.sub].filter(Boolean).join(" · ");
  return `<div class="hero-in">
<div class="hero-id">${x.face}${who ? `<p class="who">${who}</p>` : ""}</div>
${x.h1}
${x.lead}
${row ? `<ul class="links">${row}</ul>` : ""}
${x.chips}
${x.offer}
</div>`;
}

// One flowing text: designation, bio and the facts as a single line (no stats boxes, no chips).
export function about(d, ctx) {
  const { st, langs, code, any } = aboutData(d, ctx);
  if (!any) return "";
  const langLine = langs.length ? `<span class="fact-k">${esc(ctx.t("field.languages_spoken"))}</span> ${langs.map(esc).join(", ")}` : "";
  const facts = [...st, langLine, code ? esc(code) : ""].filter(Boolean);
  return `<h2>${esc(ctx.t("section.about"))}</h2>
${has(d.designation) ? `<p class="meta">${esc(d.designation)}</p>` : ""}
${has(d.bio) ? `<p class="prose">${esc(d.bio)}</p>` : ""}
${facts.length ? `<ul class="facts">${facts.map((f) => `<li>${f}</li>`).join("")}</ul>` : ""}`;
}

// "How I can help": the services as one sentence of inline links (each jumps to the form and preselects it).
export function services(d, ctx) {
  const { items, extra } = serviceList(d, ctx);
  const keys = [...items.map((i) => i.interest), ...extra];
  if (!keys.length) return "";
  return `<h2>${esc(ctx.t("site.services_heading"))}</h2>
<p class="flow">${[...new Set(keys)].map((k) => `<a href="#contact" data-interest="${esc(k)}">${esc(interestLabel(ctx, k))}</a>`).join(", ")}</p>`;
}

// A plain list: the title in bold, then its text.
export function highlights(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.highlights"))}</h2>
<ul class="plain">${items.map((i) => `<li><strong>${esc(i.title)}</strong>${has(i.text) ? ` <span>${esc(i.text)}</span>` : ""}</li>`).join("")}</ul>`;
}

// Every question under one disclosure; its <summary> is the section's <h2>.
export function faq(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.question) && has(i.answer));
  if (!items.length) return "";
  return `<details class="qs"><summary><h2>${esc(ctx.t("section.faq"))}</h2></summary>
<dl class="qa">${items.map((i) => `<div><dt>${esc(i.question)}</dt><dd class="prose">${esc(i.answer)}</dd></div>`).join("")}</dl></details>`;
}

// Quotes as plain text with the client's name under each (no cards, no faces).
export function testimonials(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.quote) && has(i.name));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.testimonials"))}</h2>
<ul class="plain">${items.map((i) => `<li><blockquote>${esc(i.quote)}</blockquote><p class="meta">${esc(i.name)}${has(i.city) ? ", " + esc(i.city) : ""}</p></li>`).join("")}</ul>`;
}

// A plain list: the company name in bold, then its insurance types as text.
export function companies(d, ctx) {
  const list = companyList(ctx), h = companiesHeading(ctx);
  if (!list.length || !h) return "";
  return `<h2>${esc(h)}</h2>
<ul class="plain">${list.map((c) => `<li><strong>${esc(c.name)}</strong>${c.types.length ? ` <span>${c.types.map(esc).join(", ")}</span>` : ""}</li>`).join("")}</ul>`;
}
