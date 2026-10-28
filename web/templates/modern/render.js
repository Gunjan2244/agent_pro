import { compose } from "../../sections/index.js";
import { hero, about, services, offers, highlights, testimonials, faq, companies, before } from "./parts.js";
// "Meet me" · Fresh (DECISIONS #85, #117): editorial split. Sticky top bar, big headline beside a tall portrait,
// companies marquee, big numbers, bento services, a wide lead offer, numbered highlights, a rotating pull quote,
// FAQ beside its heading, dark contact and footer.
// site = { content, template_key, accent, locale, profile }; t = i18n lookup; tokens = { accent, schema, catalog }
export function render(site, t, tokens) {
  return compose(site, t, tokens, "modern", { before, partials: { hero, about, services, offers, highlights, testimonials, faq, companies } });
}
