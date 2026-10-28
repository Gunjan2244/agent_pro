import { compose } from "../../sections/index.js";
import { hero, about, services, offers, highlights, achievements, testimonials, companies, pagehead } from "./parts.js";
// "Meet me" · Simple (DECISIONS #85, #117): a profile sidebar. The agent's card is pinned on the left on wide
// screens (a compact header on phones), the content reads like a résumé on the right. No bundled illustrations (the
// manifest is emptied), so the agent's photo is the only image.
// site = { content, template_key, accent, locale, profile }; t = i18n lookup; tokens = { accent, schema, catalog }
export function render(site, t, tokens) {
  return compose(site, t, { ...tokens, images: {} }, "minimal", {
    partials: { hero, about, services, offers, highlights, achievements, testimonials, companies, pagehead },
  });
}
