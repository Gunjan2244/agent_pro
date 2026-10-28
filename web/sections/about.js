import { esc, has, arr, optionLabel } from "./util.js";
// Years and clients, only when the agent set them (never invented, DECISIONS #78). `strong` wraps the number.
export function stats(d, ctx) {
  const out = [];
  const one = (key, n) => esc(ctx.t(key, { n: "\u0000" })).replace("\u0000", `<strong>${esc(n)}</strong>`);
  if (has(d.yearsExperience)) out.push(one("site.years_experience", d.yearsExperience));
  if (has(d.clientsServed)) out.push(one("site.clients_served", d.clientsServed));
  return out;
}
// The about facts every template draws from (escaped later by the caller; `st` items are already HTML).
export function aboutData(d, ctx) {
  const { t, profile: p } = ctx;
  // On the offers family the trust strip (highlights) carries the numbers, so they aren't repeated here.
  const st = ctx.family === "offers" && ctx.visible.has("highlights") ? [] : stats(d, ctx);
  const langs = arr(d.languagesSpoken).map((k) => optionLabel(ctx, "about", "languagesSpoken", null, k));
  const code = d.showAgencyCode && has(p.agency_code) ? t("site.agency_code", { code: p.agency_code }) : "";
  return { st, langs, code, any: [d.bio, d.designation, st.length, langs.length, code].some(Boolean) };
}
// Shared (classic, spotlight, guide): designation as a kicker, the bio, the numbers as stat tiles, then labelled facts.
export function about(d, ctx) {
  const { st, langs, code, any } = aboutData(d, ctx);
  if (!any) return "";
  const facts = [
    langs.length ? `<li><span class="fact-k">${esc(ctx.t("field.languages_spoken"))}</span> ${langs.map(esc).join(", ")}</li>` : "",
    code ? `<li>${esc(code)}</li>` : "",
  ].join("");
  return `<h2>${esc(ctx.t("section.about"))}</h2>
<div class="about-b">${has(d.designation) ? `<p class="kicker">${esc(d.designation)}</p>` : ""}${has(d.bio) ? `<p class="prose">${esc(d.bio)}</p>` : ""}${st.length ? `<ul class="stats">${st.map((s) => `<li>${s.replace("<strong>", '<strong class="cu">')}</li>`).join("")}</ul>` : ""}${facts ? `<ul class="facts">${facts}</ul>` : ""}</div>`;
}
