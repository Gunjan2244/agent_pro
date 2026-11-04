import { compose } from "../../sections/index.js";
import { PARTS, before, insurance, afterInner, planPage } from "./parts.js";
// "Best offers" family · Guide (DECISIONS #76, #117): needs first. Picture tiles answer "What would you like to
// protect?", a "How it works" strip, then plans grouped by need with an index that follows the reader.
// site = { content, template_key, accent, locale, profile, offers, company }; tokens see makeCtx.
export function render(site, t, tokens) {
  return compose(site, t, tokens, "guide", { layout: "needs", companiesAfter: "highlights", before, afterInner, planPage, inner: { insurance }, partials: PARTS,
    homeOrder: ["hero", "featuredPlans", "offers", "about", "companies", "testimonials", "highlights", "faq", "contact"], homeOffers: 3 });
}
