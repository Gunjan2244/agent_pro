// Pure web app manifest for one agent's site (docs/WEB_RENDERER.md, Add to home screen). Used by worker.js; testable in node.
import { headData } from "./head.js";
import { firstName, has } from "./sections/util.js";
import { localeKeys, pickLocale } from "./i18n.js";

export const MANIFEST_PATH = "/manifest.webmanifest";
export const ICON_SIZES = [192, 512];
// Built by scripts/gen-icons.mjs, one per catalog.accentSwatches key.
export const iconPath = (swatchKey, size) => `/icons/${swatchKey}-${size}.png`;

// The installed app's own pages: /a/<slug>/ on the primary host, / on a custom domain.
export const appBase = (target) => (target.slug ? `/a/${encodeURIComponent(target.slug)}/` : "/");
// What the page links to; the Worker reads the same ?s= back through resolveTarget.
export const manifestHref = (target) => (target.slug ? `${MANIFEST_PATH}?s=${encodeURIComponent(target.slug)}` : MANIFEST_PATH);

const SHORT_MAX = 12; // launchers cut labels at about 12 characters

// The installed app's label: the agent's name, or their first name when it's too long for a launcher.
export function appNames(site) {
  const p = site.profile || {}, h = (site.content || {}).hero || {};
  const name = (has(p.full_name) ? p.full_name : h.headline || "Agent PRO").trim().slice(0, 45);
  return { name, short: name.length <= SHORT_MAX ? name : firstName(name).slice(0, SHORT_MAX) };
}
// The site's accent swatch (catalog.accentSwatches), the first one when unknown.
export function swatchFor(catalog, accent) {
  const swatches = ((catalog || {}).accentSwatches || []).slice().sort((a, b) => a.order - b.order);
  return swatches.find((s) => s.key === accent) || swatches[0] || null;
}

export function manifestFor(site, target, { catalog, tokens }) {
  const { name, short } = appNames(site);
  const swatch = swatchFor(catalog, site.accent);
  const light = ((tokens || {}).color || {}).light || {};
  const base = appBase(target);
  const icons = swatch ? ICON_SIZES.flatMap((n) => ["any", "maskable"].map((purpose) => ({ src: iconPath(swatch.key, n), sizes: `${n}x${n}`, type: "image/png", purpose }))) : [];
  const m = {
    id: base, start_url: base, scope: base,
    name, short_name: short,
    description: headData(site, { origin: "" }).description,
    lang: pickLocale(localeKeys(catalog), site.locale), dir: "ltr",
    display: "standalone",
    background_color: light.background, theme_color: light.surface,
    icons, prefer_related_applications: false,
  };
  if (!m.description) delete m.description;
  return m;
}

// web/dist/config.js (written by scripts/build-web.sh) -> { SUPABASE_URL, SUPABASE_ANON_KEY, SITE_BASE_URL, BUILD_TIME }.
// BUILD_TIME (ISO) feeds the Worker's Last-Modified; "" when absent.
export function parseConfig(text) {
  const out = {};
  for (const k of ["SUPABASE_URL", "SUPABASE_ANON_KEY", "SITE_BASE_URL", "BUILD_TIME"]) {
    const m = String(text).match(new RegExp(k + ':\\s*"((?:[^"\\\\]|\\\\.)*)"'));
    out[k] = m ? JSON.parse(`"${m[1]}"`) : "";
  }
  return out;
}
