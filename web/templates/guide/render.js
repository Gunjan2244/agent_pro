import { compose } from "../../sections/index.js";
// "Best offers" family. Guide: needs first. The hero asks "What would you like to protect?" with one chip per
// interest that has an offer or a service; each chip jumps (plain #anchor, no JS) to that interest's group of
// offer and plan cards in the offers section. Then the trust strip, FAQ and contact.
export function render(site, t, tokens) {
  return compose(site, t, tokens, "guide", { layout: "needs" });
}
