import { compose } from "../../sections/index.js";
import { hero, about, services, highlights, faq, testimonials, companies } from "./parts.js";
// "Meet me" · Simple (DECISIONS #85): a one-page visiting card. Compact header with an inline Call · WhatsApp · Email
// row, about + services as flowing text, highlights as a plain list, every FAQ under one disclosure. No cards and
// no bundled illustrations (the manifest is emptied), so the agent's photo is the only image.
// site = { content, template_key, accent, locale, profile }; t = i18n lookup; tokens = { accent, schema, catalog }
export function render(site, t, tokens) {
  return compose(site, t, { ...tokens, images: {} }, "minimal", {
    partials: { hero, about, services, highlights, faq, testimonials, companies },
  });
}
