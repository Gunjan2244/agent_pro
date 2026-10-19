import { compose } from "../../sections/index.js";
// "Best offers" family. Spotlight: the featured offer front and centre in the hero (on the pale tint), then an
// offer grid with images and "ends on" dates (or plan cards when there are no offers), a trust strip, services,
// FAQ and contact. site = { content, template_key, accent, locale, profile, offers, company }; tokens see makeCtx.
export function render(site, t, tokens) {
  return compose(site, t, tokens, "spotlight", { layout: "grid" });
}
