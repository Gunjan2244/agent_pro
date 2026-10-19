// Static map of the templates' pure render functions. The Worker is bundled by wrangler and cannot use a dynamic
// import() with a variable path, so web/ssr.js picks a template from here. The browser's client render still
// imports `./templates/<key>/render.js` on demand (app.js / client.js), so it never loads the other templates.
// Add a line here for every template that has a render.js (scripts/test-web.mjs checks the map against the registry).
import { render as classic } from "./classic/render.js";
import { render as modern } from "./modern/render.js";
import { render as minimal } from "./minimal/render.js";
import { render as spotlight } from "./spotlight/render.js";
import { render as guide } from "./guide/render.js";

export const TEMPLATES = { classic, modern, minimal, spotlight, guide };

// The template's family from shared/templates/index.json ("personal" when unknown).
export const familyOf = (registry, key) => (((registry && registry.templates) || []).find((x) => x.key === key) || {}).family || "personal";
