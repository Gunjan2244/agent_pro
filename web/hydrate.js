// Hydration for a server-rendered page (DECISIONS #75). The Worker already put the full page in #root; this only
// wires behaviour and never renders or replaces markup: the enquiry form (or the privacy request form), the quick-reply
// chips, Add to home screen, and the view count. The head links (manifest, icons, apple title) are already in the HTML.
import * as api from "./api.js";
import { loadDict, makeT } from "./i18n.js";
import { wireForm, consentFor, preselectInterest } from "./form.js";

const json = async (p) => (await fetch(p)).json();

// Clicking an <a data-interest="term"> chip jumps to the form (plain href="#contact") and also preselects that interest.
export function wireChips(root) {
  root.addEventListener("click", (ev) => {
    const chip = ev.target && ev.target.closest && ev.target.closest("a[data-interest]");
    if (!chip) return;
    const sel = root.querySelector("#f-interest");
    const v = chip.getAttribute("data-interest");
    if (sel && v && [...(sel.options || [])].some((o) => o.value === v)) sel.value = v;
  });
}

export async function hydrate({ doc = document, loc = location, backend = api, fetchJson = json } = {}) {
  const root = doc.getElementById("root");
  const boot = JSON.parse(doc.getElementById("boot").textContent);
  const q = new URLSearchParams(loc.search);
  const target = { slug: boot.slug || undefined, host: boot.host || undefined };
  const [dict, catalog, nf] = await Promise.all([
    loadDict(boot.locale, [boot.locale]),
    fetchJson("/shared/catalog.json"),
    fetchJson("/shared/generated/privacy-notices.json").catch(() => ({ notices: {} })),
  ]);
  const t = makeT(dict);
  if (boot.page === "privacy") {
    const { wirePrivacy } = await import("./privacy.js");
    wirePrivacy(root, { t, catalog, slug: target.slug, host: target.host, name: boot.name, submit: backend.submitDataRequest });
    return;
  }
  // wireForm reads the thank-you details (name, phone) from the profile; the page carries them in #boot.
  const profile = { full_name: boot.name || "", phone: boot.phone || "" };
  wireForm(root, { t, profile, site: { content: {} }, catalog, slug: target.slug, host: target.host, consent: consentFor(nf.notices, boot.locale), submit: backend.submitEnquiry });
  wireChips(root);
  // Arrived from an "Enquire" link on another page (contact?interest=<key>#enq, DECISIONS #122): preselect it.
  preselectInterest(root, q.get("interest"), catalog);
  // Never breaks the page. ?install=1 (the app's Website tab) opens the install sheet and isn't a visitor's view.
  import("./install.js").then((m) => m.wireInstall({ t, name: boot.name || "", forced: q.get("install") === "1" })).catch(() => {});
  if (q.get("install") !== "1") api.recordViewOnce(target, backend);
}
