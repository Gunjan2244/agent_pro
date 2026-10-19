import { esc, has, arr, optionLabel } from "./util.js";
import { say } from "./say.js";
// Years and clients, only when the agent set them (never invented, DECISIONS #78). `strong` wraps the number.
export function stats(d, ctx) {
  const out = [];
  const one = (key, n) => esc(ctx.t(key, { n: "\u0000" })).replace("\u0000", `<strong>${esc(n)}</strong>`);
  if (has(d.yearsExperience)) out.push(one("site.years_experience", d.yearsExperience));
  if (has(d.clientsServed)) out.push(one("site.clients_served", d.clientsServed));
  return out;
}
export function about(d, ctx) {
  const { t, profile: p } = ctx;
  // On the offers family the trust strip (highlights) carries the numbers, so they aren't repeated here.
  const st = ctx.family === "offers" && ctx.visible.has("highlights") ? [] : stats(d, ctx);
  const langs = arr(d.languagesSpoken).map((k) => optionLabel(ctx, "about", "languagesSpoken", null, k));
  const code = d.showAgencyCode && has(p.agency_code) ? t("site.agency_code", { code: p.agency_code }) : "";
  if (![d.bio, d.designation, st.length, langs.length, code].some(Boolean)) return "";
  return `${say(ctx, "site.say_about")}<h2>${esc(t("section.about"))}</h2>
${has(d.designation) ? `<p class="meta">${esc(d.designation)}</p>` : ""}
${has(d.bio) ? `<p class="prose">${esc(d.bio)}</p>` : ""}
${st.length ? `<ul class="stats">${st.map((s) => `<li>${s}</li>`).join("")}</ul>` : ""}
${langs.length ? `<ul class="chips">${langs.map((l) => `<li>${esc(l)}</li>`).join("")}</ul>` : ""}
${code ? `<p class="meta">${esc(code)}</p>` : ""}`;
}
