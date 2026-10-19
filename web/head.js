import { tenDigits, has, arr, optionLabel, interestLabel } from "./sections/util.js";

const DESC_MAX = 155;
// Cut to `max` characters on a word boundary (an ellipsis replaces the cut text).
export function trimWords(text, max = DESC_MAX) {
  const s = String(text ?? "").replace(/\s+/g, " ").trim();
  if (s.length <= max) return s;
  const cut = s.slice(0, max - 1);
  const sp = cut.lastIndexOf(" ");
  return (sp > max * 0.5 ? cut.slice(0, sp) : cut).replace(/[\s,;:.\-–—]+$/, "") + "…";
}
// A section is on the page when it is in the order (if there is one) and not hidden. faq and services are never pinned.
const shown = (c, key) => (!Array.isArray(c.sectionOrder) || c.sectionOrder.includes(key)) && !arr(c.hidden).includes(key);

// Pure: returns what app.js writes into <head> (and web/ssr.js serialises). `origin`+`path` describe the page's own URL
// (with the trailing slash for /a/<slug>/). Optional `t`, `catalog`, `schema` add the translated labels used by the
// Service and knowsLanguage JSON-LD; `noindex` is for ?preview/?install pages.
// `images` (web/images/manifest.json) and `family` give the bundled share image (og-<family>) when there is no photo.
export function headData(site, { origin, path = "/", canonicalHost = null, noindex = false, t = null, catalog = null, schema = null, images = null, family = "personal" }) {
  const c = site.content || {}, p = site.profile || {}, h = c.hero || {}, a = c.about || {}, k = c.contact || {};
  const desig = a.designation || p.designation || "";
  const fbKey = has(p.city) ? "site.seo_title_fallback" : "site.seo_title_fallback_nocity";
  const fallbackDesig = !has(desig) && t && t(fbKey, { city: p.city || "" }) !== fbKey ? t(fbKey, { city: p.city || "" }) : "";
  const title = [p.full_name, has(desig) ? [desig, p.city].filter(has).join(", ") : fallbackDesig].filter(has).join(" — ") || h.headline || "Agent PRO";
  const description = trimWords(h.subheadline || a.bio || h.headline || "");
  const canonical = canonicalHost ? `https://${canonicalHost}/` : origin + path;
  const photoRaw = (h.photo || p.photo_url || "");
  const image = /^https?:\/\//i.test(photoRaw) ? photoRaw : "";
  const phone10 = tenDigits(k.phone || p.phone);
  const agencyId = canonical + "#agency", personId = canonical + "#agent";

  // The agency is the agent's own practice. No insurer or company name, and no Offer markup (OFFERS.md).
  const agency = { "@type": "InsuranceAgency", "@id": agencyId, name: p.full_name || title, url: canonical };
  if (description) agency.description = description;
  if (image) agency.image = image;
  if (phone10) agency.telephone = "+91" + phone10;
  if (k.officeAddress || p.city) agency.address = { "@type": "PostalAddress", streetAddress: k.officeAddress || undefined, addressLocality: p.city || undefined, addressCountry: "IN" };
  if (p.city) agency.areaServed = { "@type": "City", name: p.city };
  // Free text, so only passed on when it is a plain, sane string.
  if (has(k.officeHours) && String(k.officeHours).length <= 100) agency.openingHours = String(k.officeHours).trim();
  if (t && schema) {
    const langs = arr(a.languagesSpoken).map((l) => optionLabel({ schema, t }, "about", "languagesSpoken", null, l)).filter(has);
    if (langs.length) agency.knowsLanguage = langs;
  }
  const graph = [agency];
  if (has(p.full_name)) {
    const person = { "@type": "Person", "@id": personId, name: p.full_name, worksFor: { "@id": agencyId } };
    if (has(desig)) person.jobTitle = desig;
    if (image) person.image = image;
    if (phone10) person.telephone = agency.telephone;
    person.url = canonical;
    agency.founder = { "@id": personId };
    graph.push(person);
  }
  const faq = arr((c.faq || {}).items).filter((i) => i && has(i.question) && has(i.answer));
  if (faq.length && shown(c, "faq")) graph.push({ "@type": "FAQPage", "@id": canonical + "#faq", mainEntity: faq.map((i) => ({ "@type": "Question", name: i.question, acceptedAnswer: { "@type": "Answer", text: i.answer } })) });
  if (catalog && t && shown(c, "services")) {
    const sv = c.services || {}, ctx = { catalog, t };
    const items = arr(sv.items).filter((i) => i && interestLabel(ctx, i.interest));
    const done = new Set(items.map((i) => i.interest));
    const listed = [...items, ...arr(sv.offered).filter((x) => !done.has(x) && interestLabel(ctx, x)).map((x) => ({ interest: x }))];
    for (const i of listed) {
      const s = { "@type": "Service", name: interestLabel(ctx, i.interest), provider: { "@id": agencyId } };
      if (has(i.description)) s.description = i.description;
      if (p.city) s.areaServed = { "@type": "City", name: p.city };
      graph.push(s);
    }
  }
  const ld = { "@context": "https://schema.org", "@graph": graph };
  const og = { "og:type": "website", "og:title": title, "og:description": description, "og:url": canonical };
  // No photo: the family's bundled share image (absolute URL on this origin), when it is in the manifest.
  const ogName = "og-" + (family === "offers" ? "offers" : "personal"), ogm = images && images[ogName];
  const shareImage = image || (ogm && Array.isArray(ogm.sizes) && ogm.sizes.length && origin ? `${origin}/images/${ogName}-${ogm.sizes[ogm.sizes.length - 1]}.webp` : "");
  if (shareImage) og["og:image"] = shareImage;
  if (!image && shareImage) { og["og:image:width"] = String(ogm.w); og["og:image:height"] = String(ogm.h); }
  const twitter = { "twitter:card": "summary_large_image", "twitter:title": title, "twitter:description": description };
  if (shareImage) twitter["twitter:image"] = shareImage;
  // "<" escaped so agent text can never close the script element
  const jsonld = JSON.stringify(ld).replace(/</g, "\\u003c");
  return { title, description, canonical, og, twitter, robots: noindex ? "noindex" : "index,follow", jsonld };
}
export function applyHead(d, doc = document) {
  doc.title = d.title;
  const set = (sel, make) => { let e = doc.head.querySelector(sel); if (!e) { e = make(); doc.head.appendChild(e); } return e; };
  set('meta[name="description"]', () => Object.assign(doc.createElement("meta"), { name: "description" })).content = d.description;
  if (d.robots) set('meta[name="robots"]', () => Object.assign(doc.createElement("meta"), { name: "robots" })).content = d.robots;
  set('link[rel="canonical"]', () => Object.assign(doc.createElement("link"), { rel: "canonical" })).href = d.canonical;
  for (const [prop, val] of Object.entries(d.og)) {
    const e = set(`meta[property="${prop}"]`, () => { const m = doc.createElement("meta"); m.setAttribute("property", prop); return m; });
    e.content = val;
  }
  for (const [name, val] of Object.entries(d.twitter || {})) set(`meta[name="${name}"]`, () => Object.assign(doc.createElement("meta"), { name })).content = val;
  const s = set('script[type="application/ld+json"]', () => Object.assign(doc.createElement("script"), { type: "application/ld+json" }));
  s.textContent = d.jsonld;
}
