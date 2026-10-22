import { compose } from "../../sections/index.js";
import { hero, about, services, highlights, faq, companies } from "./parts.js";
// "Meet me" · Fresh (DECISIONS #85): a magazine layout. Full-width photo crop + large left-aligned headline (photo
// beside the text on wide screens), about with a facts column, numbered services, a row of highlights, open Q&A, companies as rows.
// site = { content, template_key, accent, locale, profile }; t = i18n lookup; tokens = { accent, schema, catalog }
export function render(site, t, tokens) {
  return compose(site, t, tokens, "modern", { partials: { hero, about, services, highlights, faq, companies } });
}
