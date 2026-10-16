import { compose } from "../../sections/index.js";
// site = { content, template_key, accent, locale, profile }; t = i18n lookup; tokens = { accent, schema, catalog }
export function render(site, t, tokens) {
  return compose(site, t, tokens, "modern", { wrap: (k, h) => k === "hero" ? `<header class="sec sec-hero band" id="hero">${h}</header>` : `<section class="sec sec-${k}" id="${k}">${h}</section>` });
}
