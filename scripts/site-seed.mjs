// Node mirror of the generated Kotlin SiteSeed + StarterContent.from (docs/SHARED_DEFINITIONS.md "site-seed.json",
// DECISIONS #78): what a brand-new agent's site looks like. Used to build web/dev-fixtures/sample-seeded.json and
// checked against the spec's test vectors by scripts/test-web-ssr.mjs. Keep it in step with StarterContent.kt.
//   node scripts/site-seed.mjs            rewrites web/dev-fixtures/sample-seeded.json
//   node scripts/site-seed.mjs --check    exits 1 when the committed fixture is stale
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { fnv1a32 } from "../web/sections/media.js";

export { fnv1a32 };
const R = (p) => JSON.parse(readFileSync(new URL("../" + p, import.meta.url)));

export const salt = (section, field, index, itemField) => (index == null || itemField == null ? `${section}.${field}` : `${section}.${field}.${index}.${itemField}`);
export const pick = (seed, s, count) => { if (!(count > 0)) throw new Error("count must be > 0"); return fnv1a32(`${seed}:${s}`) % count; };
const PH = /\{([A-Za-z0-9_]+)\}/g;
export const placeholdersIn = (text) => new Set([...String(text).matchAll(PH)].map((m) => m[1]));
export const fill = (text, values) => String(text).replace(PH, (_, k) => String(values[k] ?? "").trim());
const blank = (v) => v == null || String(v).trim() === "";
// The variant's text with values filled; its fallback when the text uses a blank placeholder; null when neither works.
export function fillVariant(variant, locale, values, dicts) {
  const raw = (key) => (dicts[locale] && dicts[locale][key]) ?? dicts.en[key];
  const complete = (text) => [...placeholdersIn(text)].every((k) => !blank(values[k]));
  const v = typeof variant === "string" ? { key: variant } : variant;
  const main = raw(v.key);
  if (main != null && complete(main)) return fill(main, values);
  if (!v.fallback) return null;
  const fb = raw(v.fallback);
  return fb != null && complete(fb) ? fill(fb, values) : null;
}
export const resolve = (variants, seed, s, locale, values, dicts) => fillVariant(variants[pick(seed, s, variants.length)], locale, values, dicts);

export function placeholderValues(profile) {
  const name = String(profile.fullName || "").trim();
  const out = { name, firstName: name.split(/\s+/)[0] || "", city: String(profile.city || "").trim(), designation: String(profile.designation || "").trim() };
  return Object.fromEntries(Object.entries(out).filter(([, v]) => v));
}
const PHONE = /^[6-9]\d{9}$/, EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
function textJson(spec, text, validate = false) {
  let s = String(text).trim();
  if (spec.maxLength != null) s = s.slice(0, spec.maxLength).trimEnd();
  if (!s) return undefined;
  if (validate && spec.type === "phone" && !PHONE.test(s.replace(/[\s-]/g, "").replace(/^(\+91|91|0)(?=\d{10}$)/, ""))) return undefined;
  if (validate && spec.type === "email" && !EMAIL.test(s)) return undefined;
  return s;
}
const profileValue = (profile, f) => {
  const v = f === "firstName" ? placeholderValues(profile).firstName : profile[f];
  return blank(v) ? null : String(v).trim();
};

// -> the draft content StarterContent.from(profile, template, locale, agentId) builds.
export function starterContent({ profile, templateKey, locale, agentId, seed = R("shared/site-seed.json"), schema = R("shared/site-schema.json"), registry = R("shared/templates/index.json"), dicts }) {
  const values = placeholderValues(profile);
  const value = (spec, form, s) => {
    if (form.variants) { const r = resolve(form.variants, agentId, s, locale, values, dicts); return r == null ? undefined : textJson(spec, r); }
    if (form.fromProfile) { const v = profileValue(profile, form.fromProfile); return v == null ? undefined : textJson(spec, v, true); }
    if ("value" in form) return form.value;
    if (form.byLocale) return form.byLocale[locale] ?? form.byLocale[Object.keys(form.byLocale)[0]];
    return undefined;
  };
  const content = {};
  for (const [secKey, fields] of Object.entries(seed.sections)) {
    const sec = schema.sections.find((x) => x.key === secKey);
    if (!sec) continue;
    const out = {};
    for (const spec of sec.fields) {
      const form = fields[spec.key];
      if (!form) continue;
      let json;
      if (form.items) {
        let items = form.items.map((item, i) => {
          const o = {};
          for (const f of spec.itemFields || []) { if (!item[f.key]) continue; const v = value(f, item[f.key], salt(secKey, spec.key, i, f.key)); if (v !== undefined) o[f.key] = v; }
          return (spec.itemFields || []).some((f) => f.required && !(f.key in o)) ? null : o;
        }).filter(Boolean);
        if (spec.maxItems != null) items = items.slice(0, spec.maxItems);
        json = items.length ? items : undefined;
      } else json = value(spec, form, salt(secKey, spec.key));
      if (json !== undefined) out[spec.key] = json;
    }
    if (Object.keys(out).length) content[secKey] = out;
  }
  const tpl = registry.templates.find((x) => x.key === templateKey);
  content.sectionOrder = tpl.sections.slice();
  const vis = seed.visibility || {};
  content.hidden = [...new Set([...(vis.hidden || []), ...Object.entries(vis.visibleOnlyInFamily || {}).filter(([, fam]) => fam !== tpl.family).map(([k]) => k)])];
  return content;
}

// The committed fixture: a new agent on the default "Meet me" template, EN, fixed id and profile.
export const SEEDED = {
  agentId: "00000000-0000-0000-0000-000000000001", templateKey: "classic", locale: "en",
  profile: { fullName: "Priya Nair", city: "Kochi", designation: "", phone: "9847012345", email: "priya.nair@example.com" },
};
export function seededFixture() {
  const dicts = { en: R("shared/i18n/en.json"), hi: R("shared/i18n/hi.json") };
  const p = SEEDED.profile;
  const reg = R("shared/templates/index.json");
  return {
    template_key: SEEDED.templateKey, accent: reg.templates.find((x) => x.key === SEEDED.templateKey).defaultAccent, locale: SEEDED.locale,
    published_content: starterContent({ ...SEEDED, dicts }),
    profile: { full_name: p.fullName, designation: p.designation || null, city: p.city, photo_url: "", phone: p.phone, agency_code: null },
    canonical_host: null, published_at: "2026-10-18T09:00:00.000Z", offers: { featured: null, items: [] }, company: null,
  };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const out = new URL("../web/dev-fixtures/sample-seeded.json", import.meta.url);
  const text = JSON.stringify(seededFixture(), null, 1) + "\n";
  if (process.argv.includes("--check")) {
    let cur = ""; try { cur = readFileSync(out, "utf8"); } catch { /* missing */ }
    if (cur !== text) { console.error("sample-seeded.json is stale: run node scripts/site-seed.mjs"); process.exit(1); }
    console.log("sample-seeded.json is current");
  } else { writeFileSync(out, text); console.log("wrote", fileURLToPath(out)); }
}
