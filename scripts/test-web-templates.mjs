// "Meet me" templates (DECISIONS #85): each one has its own structure, not only CSS. Checks the distinct structure
// markers of classic, modern and minimal, that they render only from content (escaped), keep the form, the chips'
// data-interest, the install block and the sticky bar, and render the seeded fixture and Hindi. Run by scripts/test-web.mjs.
import assert from "node:assert/strict";

export async function run({ ok, R, dicts, catalog, schema, fx, makeT }) {
  const { TEMPLATES, familyOf } = await import("../web/templates/index.js");
  const { esc } = await import("../web/sections/util.js");
  const registry = R("shared/templates/index.json");
  const MANIFEST = R("web/images/manifest.json");
  const tok = (site, key, extra = {}) => ({ accent: catalog.accentSwatches.find((x) => x.key === site.accent).hex, schema, catalog, family: familyOf(registry, key), images: MANIFEST, seed: "demo", today: "2026-10-18", ...extra });
  const html = (site, key, loc = site.locale || "en", extra) => TEMPLATES[key](site, makeT(dicts[loc]), tok(site, key, extra));
  const section = (h, id) => { const i = h.indexOf(`id="${id}"`); return i < 0 ? "" : h.slice(i, h.indexOf("</section>", i)); };
  const heroOf = (h) => h.slice(h.indexOf('id="hero"'), h.indexOf("</header>"));
  // The markup with text and attribute values removed: what is left is the structure.
  const shape = (h) => h.replace(/>[^<]*</g, "><").replace(/="[^"]*"/g, "").replace(/\s+/g, "");
  const MEET = ["classic", "modern", "minimal"];
  for (const k of MEET) assert.equal(familyOf(registry, k), "personal", k);
  const en = fx("sample-en"), seeded = fx("sample-seeded"), hi = fx("sample-hi");

  ok("meet me: the three templates differ in structure in the hero, about, services, highlights and FAQ (EN and seeded)", () => {
    for (const s of [en, seeded]) {
      const out = Object.fromEntries(MEET.map((k) => [k, html(s, k)]));
      for (const part of [heroOf, ...["about", "services", "highlights", "faq"].map((id) => (h) => section(h, id))]) {
        const sh = MEET.map((k) => shape(part(out[k])));
        assert.equal(new Set(sh).size, 3, "two templates share a structure: " + part.toString().slice(0, 40));
      }
    }
  });

  ok("classic: round 160 px portrait with the name under it, centred hero with one button, service cards with thumbnails (no orphan chips), highlights as a 4-card icon grid, FAQ accordion", () => {
    const h = html(en, "classic"), hero = heroOf(h);
    assert.ok(/<div class="hero-id"><img class="hero-photo" src="[^"]+" width="160" height="160"[^>]*fetchpriority="high"[^>]*><p class="who"><strong>Asha Verma<\/strong><span>Insurance Advisor · Pune<\/span><\/p><\/div>/.test(hero), hero.slice(0, 300));
    assert.equal((hero.match(/class="btn"/g) || []).length, 1);
    const sv = section(h, "services");
    // items term, health, child + offered pension (no item) = 4 cards, each with its thumbnail
    assert.equal((sv.match(/<li class="card svc has-pic"><img class="pic"/g) || []).length, 4);
    assert.ok(!sv.includes('class="tags"') && !sv.includes('class="chips"'));
    const hl = section(h, "highlights");
    assert.equal((hl.match(/<li class="card"><svg class="ico"/g) || []).length, 4);
    assert.ok(section(h, "faq").includes('<div class="faq"><details><summary>'));
  });

  ok("modern: full-width cover photo (640×480, high priority) then kicker before the <h1>; facts before the bio; numbered services; highlights row; open Q&A", () => {
    const h = html(en, "modern"), hero = heroOf(h);
    assert.ok(/^id="hero">(?:<p class="hero-login"><a [^>]*>[^<]*<\/a><\/p>)?<div class="hero-in">\n<div class="cover"><img class="hero-photo" src="[^"]+" width="640" height="480" alt="Asha Verma" fetchpriority="high" decoding="async"><\/div>/.test(hero), hero.slice(0, 200));
    assert.ok(hero.indexOf('<p class="meta">Asha Verma · Insurance Advisor · Pune</p>') < hero.indexOf("<h1>"));
    const ab = section(h, "about");
    assert.ok(ab.indexOf('<ul class="facts">') < ab.indexOf('<p class="prose">'));
    assert.ok(ab.includes('<li class="fact-d">Insurance Advisor</li>') && ab.includes('<li class="fact-n">' + esc(dicts.en["site.years_experience"]).replace("{n}", "<strong>12</strong>")));
    assert.ok(ab.includes(`<span class="fact-k">${dicts.en["field.languages_spoken"]}</span> English, Hindi, Marathi`));
    const sv = section(h, "services");
    // items term, health, child + offered pension (no item) = 4 numbered rows, each with its thumbnail
    assert.deepEqual([...sv.matchAll(/<span class="svc-num" aria-hidden="true">(\d+)<\/span>/g)].map((m) => m[1]), ["01", "02", "03", "04"]);
    assert.ok(sv.includes('<ol class="svc-list">') && !sv.includes('class="card') && (sv.match(/<img class="pic"/g) || []).length === 4);
    assert.ok(/<li class="svc-row"><span class="svc-num" aria-hidden="true">01<\/span><div class="svc-b"><h3>Term insurance<\/h3><p>Pure protection at low cost\.<\/p><\/div><img class="pic"[^>]*sizes="\(min-width: 720px\) 128px, 96px"/.test(sv));
    const hl = section(h, "highlights");
    assert.ok(hl.includes('<ul class="hl-row"><li><svg class="ico"') && !hl.includes('class="card'));
    assert.equal((section(h, "faq").match(/<dt>/g) || []).length, en.content.faq.items.length);
  });

  ok("modern without a photo: the initials on the tint fill the cover; seeded facts show only the languages", () => {
    const h = html(seeded, "modern");
    assert.ok(heroOf(h).includes('<div class="cover"><span class="hero-photo mono" aria-hidden="true">PN</span></div>'));
    assert.ok(section(h, "about").includes(`<ul class="facts"><li><span class="fact-k">${dicts.en["field.languages_spoken"]}</span> English</li></ul>`));
  });

  ok("minimal: compact header (56 px portrait, name · designation · city), Call · WhatsApp · Email links instead of a button; no cards, no images but the portrait; one Questions disclosure", () => {
    const h = html(en, "minimal"), hero = heroOf(h);
    assert.ok(hero.includes('<img class="hero-photo" src="http://localhost:8080/dev-fixtures/avatar.svg" width="56" height="56"'));
    assert.ok(hero.includes('<p class="who"><strong>Asha Verma</strong> · Insurance Advisor · Pune</p>'));
    assert.ok(hero.includes(`<ul class="links"><li><a href="tel:+919876543210">${dicts.en["site.call"]}</a></li><li><a href="https://wa.me/919876543210" rel="noopener">${dicts.en["site.whatsapp"]}</a></li><li><a href="mailto:asha@example.com">${dicts.en["site.email"]}</a></li></ul>`));
    assert.ok(!hero.includes('class="btn"') && hero.indexOf("<h1>") > hero.indexOf('class="who"'));
    assert.ok(!/class="cards?[ "]/.test(h), "no cards");
    assert.ok(!h.includes("/images/"), "no bundled illustrations even with a manifest");
    assert.equal((h.match(/<img /g) || []).length, 1, "only the portrait");
    assert.ok(section(h, "services").includes(`<p class="flow"><a href="#contact" data-interest="term">${dicts.en["interest.term"]}</a>, <a href="#contact" data-interest="health">`));
    assert.ok(section(h, "highlights").includes('<ul class="plain"><li><strong>Protection first</strong> <span>Plans matched to your needs.</span></li>'));
    const f = section(h, "faq");
    assert.ok(f.includes(`<details class="qs"><summary><h2>${dicts.en["section.faq"]}</h2></summary>`) && !f.includes(" open"));
    assert.ok(!/class="(say|bubble)/.test(h), "no bubbles (DECISIONS #87)");
  });

  ok("minimal: with no contact links the hero falls back to its CTA, linking to the form", () => {
    const s = fx("sample-en");
    s.content = { ...s.content, contact: { ...s.content.contact, phone: "", whatsapp: "", email: "" } }; s.profile = { ...s.profile, phone: "" };
    assert.ok(heroOf(html(s, "minimal")).includes(`<ul class="links"><li><a href="#contact">${dicts.en["site.cta_default"]}</a></li></ul>`));
  });

  for (const k of MEET) ok(`${k}: keeps the enquiry form ids, the install block, the chips' data-interest and the sticky bar`, () => {
    const h = html(en, k);
    for (const id of ["enq", "f-name", "f-phone", "f-email", "f-interest", "f-message", "f-consent", "e-form", "install", "install-btn", "install-how"]) assert.ok(h.includes(`id="${id}"`), id);
    assert.ok(heroOf(h).includes('<a class="chip" href="#contact" data-interest="term">'));
    assert.ok(h.includes('<nav class="callbar"'));
  });

  ok("meet me: hostile content in the template-specific parts is escaped (designation, services, highlights, FAQ, name)", () => {
    const x = fx("sample-xss"), bad = "<img src=x onerror=alert(9)>";
    x.content = { ...x.content, hidden: [],
      about: { ...x.content.about, designation: bad },
      services: { offered: ["term"], items: [{ interest: "term", description: bad }, { interest: "bogus\"><script>", description: bad }] },
      highlights: { items: [{ icon: "nope\"><script>", title: bad, text: bad }] },
      faq: { items: [{ question: bad, answer: bad }] } };
    x.profile = { ...x.profile, full_name: "Asha <b>Verma</b>", designation: bad };
    for (const k of MEET) {
      const h = html(x, k);
      assert.ok(!/<img src=x|<script|<b>/i.test(h), k + ": raw tag");
      assert.ok(!/ on\w+\s*=/i.test(h.replace(/&lt;[^>]*?&gt;/g, "")), k + ": attribute breakout");
      assert.ok(h.includes(esc(bad)) && h.includes("Asha &lt;b&gt;Verma&lt;/b&gt;"), k);
      assert.ok(!h.includes("bogus"), k + ": unknown interest dropped");
    }
  });

  for (const k of MEET) ok(`${k}/hi: renders in Hindi from the dictionary (no English UI text)`, () => {
    const h = html(hi, k, "hi");
    assert.ok(h.includes("आशा वर्मा") && h.includes(esc(dicts.hi["section.faq"])) && h.includes(esc(dicts.hi["site.services_heading"])));
    for (const key of ["site.services_heading", "section.faq", "section.about"]) assert.ok(!h.includes(">" + dicts.en[key] + "<"), key);
    if (k === "minimal") assert.ok(heroOf(h).includes(`>${dicts.hi["site.call"]}</a>`));
    if (k === "modern") assert.ok(section(h, "about").includes(dicts.hi["field.languages_spoken"]));
  });

  for (const k of MEET) ok(`${k}: the seeded site keeps its structure markers`, () => {
    const h = html(seeded, k);
    const marker = { classic: '<li class="card svc has-pic">', modern: '<span class="svc-num" aria-hidden="true">04</span>', minimal: '<details class="qs">' }[k];
    assert.ok(h.includes(marker), marker);
  });
}
