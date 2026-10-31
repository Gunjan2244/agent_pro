import { compose } from "../../sections/index.js";
import { hero, offers, about, companies, services, highlights, testimonials, before, featuredPlans, planPage } from "./parts.js";
// "Meet me" · Trust (your Theme A, DECISIONS #117): top bar, banner + ticker, offers slider, introduction with a dark
// panel, companies ticker, plans grid, "why choose me" slider, policyholders slider, FAQ, contact, dark footer.
// site = { content, template_key, accent, locale, profile }; t = i18n lookup; tokens = { accent, schema, catalog }
export function render(site, t, tokens) {
  return compose(site, t, tokens, "classic", { before, planPage, partials: { hero, offers, about, companies, services, highlights, testimonials, featuredPlans } });
}
