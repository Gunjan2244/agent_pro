import { url, tenDigits, has } from "./sections/util.js";
// Pure: returns what app.js writes into <head>. `origin`+`path` describe the page's own URL.
export function headData(site, { origin, path = "/", canonicalHost = null }) {
  const c = site.content || {}, p = site.profile || {}, h = c.hero || {}, a = c.about || {}, k = c.contact || {};
  const desig = a.designation || p.designation || "";
  const title = [p.full_name, [desig, p.city].filter(has).join(", ")].filter(has).join(" — ") || h.headline || "Agent PRO";
  const description = (h.subheadline || h.headline || "").slice(0, 160);
  const canonical = canonicalHost ? `https://${canonicalHost}/` : origin + path;
  const photoRaw = (h.photo || p.photo_url || "");
  const image = /^https?:\/\//i.test(photoRaw) ? photoRaw : "";
  const phone10 = tenDigits(k.phone || p.phone);
  const ld = { "@context": "https://schema.org", "@type": "InsuranceAgency", name: p.full_name || title, url: canonical };
  if (description) ld.description = description;
  if (image) ld.image = image;
  if (phone10) ld.telephone = "+91" + phone10;
  if (k.officeAddress || p.city) ld.address = { "@type": "PostalAddress", streetAddress: k.officeAddress || undefined, addressLocality: p.city || undefined, addressCountry: "IN" };
  const og = { "og:type": "website", "og:title": title, "og:description": description, "og:url": canonical };
  if (image) og["og:image"] = image;
  // "<" escaped so agent text can never close the script element
  const jsonld = JSON.stringify(ld).replace(/</g, "\\u003c");
  return { title, description, canonical, og, jsonld };
}
export function applyHead(d, doc = document) {
  doc.title = d.title;
  const set = (sel, make) => { let e = doc.head.querySelector(sel); if (!e) { e = make(); doc.head.appendChild(e); } return e; };
  set('meta[name="description"]', () => Object.assign(doc.createElement("meta"), { name: "description" })).content = d.description;
  set('link[rel="canonical"]', () => Object.assign(doc.createElement("link"), { rel: "canonical" })).href = d.canonical;
  for (const [prop, val] of Object.entries(d.og)) {
    const e = set(`meta[property="${prop}"]`, () => { const m = doc.createElement("meta"); m.setAttribute("property", prop); return m; });
    e.content = val;
  }
  const s = set('script[type="application/ld+json"]', () => Object.assign(doc.createElement("script"), { type: "application/ld+json" }));
  s.textContent = d.jsonld;
}
