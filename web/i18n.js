// Loads shared/i18n/{locale}.json; t(key, params) replaces {param} placeholders.
// Supported locales come from shared/catalog.json `locales` (ordered); the first one is the default.
export const localeKeys = (catalog) => ((catalog && catalog.locales) || []).slice().sort((a, b) => a.order - b.order).map((l) => l.key);
export const pickLocale = (keys, wanted) => (keys.includes(wanted) ? wanted : keys[0] || "en");
// fetcher(path) -> Response, so the Worker can read the files through env.ASSETS (no root-relative fetch).
// `keys` (the supported locales) is optional; without it the locale must look like a language code.
export async function loadDictWith(fetcher, locale, keys) {
  const l = keys && keys.length ? pickLocale(keys, locale) : /^[a-z]{2,3}$/.test(locale) ? locale : "en";
  const r = await fetcher(`/shared/i18n/${l}.json`);
  if (!r.ok) throw new Error("i18n " + r.status);
  return r.json();
}
export const loadDict = (locale, keys = []) => loadDictWith((p) => fetch(p), locale, keys);
export function makeT(dict, fallback = {}) {
  return (key, params) => {
    let s = dict[key] ?? fallback[key] ?? key;
    if (params) for (const k in params) s = s.split(`{${k}}`).join(params[k]);
    return s;
  };
}
