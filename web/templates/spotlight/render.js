import { compose } from "../../sections/index.js";
import { hero, offers, services, highlights, testimonials, pagehead, before } from "./parts.js";
// "Best offers" family · Spotlight (DECISIONS #76, #117): a dark hero with an offer slider, a trust bar on its edge,
// an offer wall, picture tiles for services, icon cards, a testimonials slider, FAQ and contact.
// site = { content, template_key, accent, locale, profile, offers, company }; tokens see makeCtx.
export function render(site, t, tokens) {
  // The dark top bar (DECISIONS #122) only on a multi-page site; one page keeps the name row inside the hero.
  return compose(site, t, tokens, "spotlight", { layout: "grid", companiesAfter: "highlights", before: (ctx) => (ctx.pages ? before(ctx) : ""), partials: { hero, offers, services, highlights, testimonials, pagehead } });
}
