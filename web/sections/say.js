// The conversational layer (DECISIONS #76): the agent "speaks" one short line above a section's normal <h2>,
// so headings (and SEO) stay normal. The text is real DOM text; the avatar is the agent's photo (32 CSS px,
// decorative, lazy) or a CSS initials monogram, never a stock face.
import { esc } from "./util.js";
import { initials } from "./media.js";

// The 32px avatar used by every bubble (the hero photo itself is rendered by hero.js).
export function avatar(ctx) {
  if (ctx.photo) return `<img class="av" src="${ctx.photo}" width="32" height="32" alt="" loading="lazy" decoding="async">`;
  const ini = initials(ctx.agentName);
  return `<span class="av mono" aria-hidden="true">${esc(ini)}</span>`;
}

// An agent bubble with an i18n line (`site.say_<section>`); "" when the key is missing from the dictionary.
export function say(ctx, key, params) {
  const text = ctx.t(key, { firstName: ctx.firstName, ...(params || {}) });
  if (!text || text === key) return "";
  return sayText(ctx, text);
}
// An agent bubble with given (already plain) text, e.g. an agent-written description.
export const sayText = (ctx, text, cls = "") => `<div class="say${cls ? " " + cls : ""}">${avatar(ctx)}<p class="bubble">${esc(text)}</p></div>`;
