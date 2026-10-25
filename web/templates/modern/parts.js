// Modern ("Fresh", DECISIONS #85): a magazine layout. Template-specific arrangements of the shared section data;
// the data handling and escaping come from the shared partials' helpers (heroParts, aboutData, serviceList, ...).
import { esc, has, arr, interestLabel } from "../../sections/util.js";
import { heroParts } from "../../sections/hero.js";
import { aboutData } from "../../sections/about.js";
import { serviceList } from "../../sections/services.js";
import { interestPic } from "../../sections/media.js";
import { icon } from "../../sections/icons.js";
import { companyList, companiesHeading, tags } from "../../sections/companies.js";

// Phones: a full-width 4:3 photo crop (or the initials on the tint), then the kicker (name · designation · city),
// the large left-aligned <h1>, lead, one button, quick replies.
// Wide: the photo (4:5) beside the text, two columns. The photo has explicit width/height and a CSS aspect ratio (no CLS).
export function hero(d, ctx) {
  const x = heroParts(d, ctx, { photo: [640, 480] });
  return `${x.login}<div class="hero-in">
<div class="cover">${x.face}</div>
<div class="hero-text">
${x.meta}
${x.h1}
${x.lead}
${x.button}
${x.chips}
</div>
${x.offer}
</div>`;
}

// Facts first (a compact row on phones, a side column on wide screens), then the bio.
export function about(d, ctx) {
  const { st, langs, code, any } = aboutData(d, ctx);
  if (!any) return "";
  const facts = [
    has(d.designation) ? `<li class="fact-d">${esc(d.designation)}</li>` : "",
    ...st.map((s) => `<li class="fact-n">${s}</li>`),
    langs.length ? `<li><span class="fact-k">${esc(ctx.t("field.languages_spoken"))}</span> ${langs.map(esc).join(", ")}</li>` : "",
    code ? `<li>${esc(code)}</li>` : "",
  ].join("");
  return `<h2>${esc(ctx.t("section.about"))}</h2>
<div class="about-grid">${facts ? `<ul class="facts">${facts}</ul>` : ""}${has(d.bio) ? `<p class="prose">${esc(d.bio)}</p>` : ""}</div>`;
}

// A numbered list (01, 02, …) with hairline dividers instead of cards; the interest thumbnail on the right.
export function services(d, ctx) {
  const { items, extra } = serviceList(d, ctx);
  const rows = [...items, ...extra.map((k) => ({ interest: k }))];
  if (!rows.length) return "";
  const num = (n) => String(n).padStart(2, "0");
  return `<h2>${esc(ctx.t("site.services_heading"))}</h2>
<ol class="svc-list">${rows.map((i, n) => `<li class="svc-row"><span class="svc-num" aria-hidden="true">${num(n + 1)}</span><div class="svc-b"><h3>${esc(interestLabel(ctx, i.interest))}</h3>${has(i.description) ? `<p>${esc(i.description)}</p>` : ""}</div>${interestPic(ctx, i.interest, { sizes: "(min-width: 720px) 128px, 96px" })}</li>`).join("")}</ol>`;
}

// A row of big icons with short text: 2 columns on phones, 4 across on wide screens; no cards.
export function highlights(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.title));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.highlights"))}</h2>
<ul class="hl-row">${items.map((i) => `<li>${icon(i.icon)}<h3>${esc(i.title)}</h3>${has(i.text) ? `<p>${esc(i.text)}</p>` : ""}</li>`).join("")}</ul>`;
}

// Open Q&A: question and answer stacked on phones, two columns (question left) on wide screens.
export function faq(d, ctx) {
  const items = arr(d.items).filter((i) => i && has(i.question) && has(i.answer));
  if (!items.length) return "";
  return `<h2>${esc(ctx.t("section.faq"))}</h2>
<dl class="qa">${items.map((i) => `<div><dt>${esc(i.question)}</dt><dd class="prose">${esc(i.answer)}</dd></div>`).join("")}</dl>`;
}

// Companies as hairline rows: the name, then its insurance types (two columns on wide screens, like the Q&A).
export function companies(d, ctx) {
  const list = companyList(ctx), h = companiesHeading(ctx);
  if (!list.length || !h) return "";
  return `<h2>${esc(h)}</h2>
<ul class="co-rows">${list.map((c) => `<li><h3>${esc(c.name)}</h3>${tags(c.types)}</li>`).join("")}</ul>`;
}
