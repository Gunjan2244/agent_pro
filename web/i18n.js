// Loads shared/i18n/{locale}.json; t(key, params) replaces {param} placeholders.
// Supported locales come from shared/catalog.json `locales` (ordered); the first one is the default.
export const localeKeys = (catalog) => ((catalog && catalog.locales) || []).slice().sort((a, b) => a.order - b.order).map((l) => l.key);
export const pickLocale = (keys, wanted) => (keys.includes(wanted) ? wanted : keys[0] || "en");
export async function loadDict(locale, keys = []) {
  const l = pickLocale(keys, locale);
  const r = await fetch(`/shared/i18n/${l}.json`);
  if (!r.ok) throw new Error("i18n " + r.status);
  return r.json();
}
export function makeT(dict, fallback = {}) {
  return (key, params) => {
    let s = dict[key] ?? fallback[key] ?? key;
    if (params) for (const k in params) s = s.split(`{${k}}`).join(params[k]);
    return s;
  };
}
