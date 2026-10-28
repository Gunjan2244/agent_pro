// The five templates (DECISIONS #85, #117): each has its own structure, not only CSS. Checks the structure markers of
// every template, the shared lively parts (top bar, sliders, tickers, count-up numbers: sections/ui.js, motion.js),
// that they render only from content (escaped), keep the form, the chips' data-interest, the install block and the
// sticky bar, and render the seeded fixture and Hindi. Run by scripts/test-web.mjs.
import assert from "node:assert/strict";

export async function run({ ok, R, dicts, catalog, schema, fx, makeT }) {
  const { TEMPLATES, familyOf } = await import("../web/templates/index.js");
  const { esc } = await import("../web/sections/util.js");
  const { slider, ticker, topBar, numbers } = await import("../web/sections/ui.js");
  const { makeCtx } = await import("../web/sections/index.js");
  const registry = R("shared/templates/index.json");
  const MANIFEST = R("web/images/manifest.json");
  const ALL = registry.templates.map((x) => x.key);
  const tok = (site, key, extra = {}) => ({ accent: catalog.accentSwatches.find((x) => x.key === site.accent).hex, schema, catalog, family: familyOf(registry, key), images: MANIFEST, seed: "demo", today: "2026-10-18", ...extra });
  const html = (site, key, loc = site.locale || "en", extra) => TEMPLATES[key](site, makeT(dicts[loc]), tok(site, key, extra));
  const section = (h, id) => { const i = h.indexOf(`id="${id}"`); return i < 0 ? "" : h.slice(i, h.indexOf("</section>", i)); };
  const heroOf = (h) => h.slice(h.indexOf('id="hero"'), h.indexOf("</header>"));
  const topOf = (h) => h.slice(0, h.indexOf("<header"));
  const count = (h, re) => (h.match(re) || []).length;
  // The markup with text and attribute values removed: what is left is the structure.
  const shape = (h) => h.replace(/>[^<]*</g, "><").replace(/="[^"]*"/g, "").replace(/\s+/g, "");
  const MEET = ["classic", "modern", "minimal"];
  for (const k of MEET) assert.equal(familyOf(registry, k), "personal", k);
  const en = fx("sample-en"), seeded = fx("sample-seeded"), hi = fx("sample-hi"), offers = fx("sample-offers");
  const t = makeT(dicts.en);

  ok("meet me: the three templates differ in structure in the hero, about, services and highlights (EN and seeded), and in offers (EN)", () => {
    for (const [s, ids] of [[en, ["about", "services", "highlights", "offers"]], [seeded, ["about", "services", "highlights"]]]) {
      const out = Object.fromEntries(MEET.map((k) => [k, html(s, k)]));
      for (const part of [heroOf, ...ids.map((id) => (h) => section(h, id))]) {
        const sh = MEET.map((k) => shape(part(out[k])));
        assert.equal(new Set(sh).size, 3, "two templates share a structure: " + part.toString().slice(0, 40));
      }
    }
  });

  ok("all five: the heroes differ in structure (EN and offers fixture)", () => {
    for (const s of [en, offers]) assert.equal(new Set(ALL.map((k) => shape(heroOf(html(s, k))))).size, 5);
  });

  // ---- classic (Trust, Theme A) ----
  ok("classic: top bar (face, name, links to rendered sections, one Call button, a phone menu); banner with the scene, dark panel (56 px photo, name, <h1>, one button + WhatsApp, chips); trust ticker", () => {
    const h = html(en, "classic"), top = topOf(h), hero = heroOf(h);
    assert.ok(top.startsWith('<div class="tpl tpl-classic fam-personal has-bar" style="--accent:#') && top.includes('<div class="topbar"><div class="tb-in"><a class="tb-id" href="#hero"><img class="tb-ph"'));
    assert.deepEqual([...top.matchAll(/<ul class="tb-links">([\s\S]*?)<\/ul>/g)].length, 1);
    const links = [...top.match(/<ul class="tb-links">([\s\S]*?)<\/ul>/)[1].matchAll(/href="#([a-z]+)"/g)].map((m) => m[1]);
    assert.deepEqual(links, ["about", "services", "offers", "testimonials", "faq", "contact"]);
    for (const k of links) assert.ok(h.includes(`id="${k}"`), k);
    assert.ok(top.includes(`<a class="btn tb-cta" href="tel:+919876543210">${dicts.en["site.call"]}</a>`) && top.includes('<details class="tb-menu"><summary aria-label="' + dicts.en["site.menu"] + '">'));
    assert.ok(/^id="hero"><div class="banner"><img class="bn-img fade" src="\/images\/hero-scene-\d-800\.webp"/.test(hero));
    assert.ok(/<div class="bn-panel enter">\n<div class="hero-id"><img class="hero-photo" src="[^"]+" width="56" height="56"[^>]*fetchpriority="high"[^>]*><p class="who"><strong>Asha Verma<\/strong>/.test(hero));
    assert.equal(count(hero, /class="btn"/g), 1); assert.ok(hero.includes('<a class="btn ghost" href="https://wa.me/919876543210" rel="noopener">'));
    assert.ok(hero.includes('<div class="ticker tk-trust" style="--tk:') && hero.includes("<li><strong>12</strong> years of experience</li>"));
    assert.equal(count(hero, /<ul class="tk-run" aria-hidden="true">/g), 1, "the ticker's second copy is hidden from screen readers");
  });
  ok("classic: introduction (photo beside the dark panel, counting numbers), companies ticker, 2×3 plan cards, 'why choose me' and policyholders sliders", () => {
    const h = html(en, "classic");
    const ab = section(h, "about");
    assert.ok(ab.includes('<div class="intro"><div class="in-pic"><img class="in-img"') && ab.includes('<ul class="in-nums"><li class="num"><strong class="cu">12</strong><span>years of experience</span></li>'));
    assert.ok(section(h, "companies").includes('<div class="ticker tk-rev tk-cos"') && section(h, "companies").includes("<strong>Sample Life Assurance</strong>"));
    const sv = section(h, "services");
    assert.equal(count(sv, /<li class="plan-c lift zoom"><img class="pic"/g), 4);
    assert.ok(sv.includes('<ul class="plans stg">') && sv.includes(`<a class="lnk ask" href="#contact" data-interest="term">${dicts.en["site.offer_cta_enquire"]}</a>`));
    const hl = section(h, "highlights");
    assert.ok(hl.includes('<div class="slider sl-three" data-auto="4500"') && count(hl, /<li class="slide"><div class="card hl-c"><svg class="ico"/g) === 4);
    const tm = section(h, "testimonials");
    assert.ok(tm.includes(`<h2>${dicts.en["site.policyholders_heading"]}</h2>`) && tm.includes('<div class="slider sl-quotes"') && tm.includes('<figure class="card quote"><blockquote>'));
  });
  ok("classic without a photo: the initials on the panel; the introduction shows the 'let's talk' picture", () => {
    const s = fx("sample-en"); s.content = { ...s.content, hero: { ...s.content.hero, photo: "" } }; s.profile = { ...s.profile, photo_url: "" };
    const h = html(s, "classic");
    assert.ok(heroOf(h).includes('<div class="hero-id"><span class="hero-photo mono" aria-hidden="true">AV</span>'));
    assert.ok(section(h, "about").includes('<div class="in-pic"><img class="in-img" src="/images/contact-talk-800.webp"'));
    assert.ok(topOf(h).includes('<span class="tb-ph mono" aria-hidden="true">AV</span>'));
  });

  // ---- modern (Fresh) ----
  ok("modern: top bar with 'Get in touch'; text (kicker before the <h1>, button + WhatsApp, chips) then the 4:5 cover (640×800, high priority) with a badge of counting numbers", () => {
    const h = html(en, "modern"), top = topOf(h), hero = heroOf(h);
    assert.ok(top.includes(`<a class="btn tb-cta" href="#contact">${dicts.en["site.cta_default"]}</a>`));
    assert.ok(hero.indexOf('<p class="meta">Asha Verma · Insurance Advisor · Pune</p>') < hero.indexOf("<h1>"));
    assert.ok(hero.indexOf('<div class="hero-text enter">') < hero.indexOf('<div class="cover fade">'));
    assert.ok(/<div class="cover fade"><img class="hero-photo" src="[^"]+" width="640" height="800" alt="Asha Verma" fetchpriority="high" decoding="async"><ul class="badge"><li class="num"><strong class="cu">12<\/strong>/.test(hero));
  });
  ok("modern: about (bio left, big numbers right), bento services (the first large), a wide lead offer then rows, numbered highlights, a pull-quote slider, FAQ beside its heading, a companies marquee", () => {
    const h = html(en, "modern");
    const ab = section(h, "about");
    assert.ok(ab.includes('<div class="about-grid"><div><h2>') && ab.includes('<ul class="facts"><li class="fact-n"><strong class="cu">12</strong>'));
    assert.ok(ab.includes(`<li class="fact-t"><strong>English, Hindi, Marathi</strong><span>${dicts.en["field.languages_spoken"]}</span></li>`));
    const sv = section(h, "services");
    assert.deepEqual([...sv.matchAll(/<span class="bt-n" aria-hidden="true">(\d+)<\/span>/g)].map((m) => m[1]), ["01", "02", "03", "04"]);
    assert.ok(sv.includes('<ol class="bento stg"><li class="bt lift zoom bt-big"><img class="pic"'));
    const of = section(h, "offers");
    assert.ok(of.indexOf('class="offer of-lead lift zoom"') > 0 && of.indexOf('class="offer of-lead') < of.indexOf('<ul class="of-rows stg">'));
    assert.ok(of.includes(`<h3>${en.offers.featured.title}</h3>`), "the featured offer leads");
    assert.ok(section(h, "highlights").includes('<ol class="hl-cols stg"><li><p class="hl-n"><span aria-hidden="true">01</span><svg class="ico"'));
    assert.ok(section(h, "testimonials").includes('<div class="slider sl-pull"') && section(h, "testimonials").includes('<figure class="pull"><blockquote>'));
    const f = section(h, "faq");
    assert.ok(f.includes('<div class="faq-grid"><div class="faq-side"><h2>') && f.includes('<a class="btn ghost" href="tel:+919876543210">'));
    assert.ok(section(h, "companies").includes('<div class="ticker tk-cos"') && section(h, "companies").includes("<h3>Sample Life Assurance</h3>"));
  });
  ok("modern without a photo: the initials fill the cover; seeded facts show the languages", () => {
    const h = html(seeded, "modern");
    assert.ok(heroOf(h).includes('<div class="cover fade"><span class="hero-photo mono" aria-hidden="true">PN</span></div>'));
    assert.ok(section(h, "about").includes(`<li class="fact-t"><strong>English</strong><span>${dicts.en["field.languages_spoken"]}</span></li>`));
  });

  // ---- minimal (Simple) ----
  ok("minimal: no top bar; the header holds the profile card (96 px portrait, name, Call / WhatsApp / Email buttons, facts with counting numbers) and the intro (<h1>, lead, chips); the portrait is the only image", () => {
    const h = html(en, "minimal"), hero = heroOf(h);
    assert.ok(!h.includes('class="topbar"'));
    assert.ok(/^id="hero"><div class="hero-in">\n<aside class="pcard"><img class="hero-photo" src="http:\/\/localhost:8080\/dev-fixtures\/avatar\.svg" width="96" height="96"/.test(hero));
    assert.ok(hero.includes(`<p class="pc-acts"><a class="btn" href="tel:+919876543210">${dicts.en["site.call"]}</a><a class="btn ghost" href="https://wa.me/919876543210" rel="noopener">${dicts.en["site.whatsapp"]}</a><a class="btn ghost" href="mailto:asha@example.com">${dicts.en["site.email"]}</a></p>`));
    assert.ok(hero.includes('<ul class="pc-facts"><li><span>years of experience</span><strong class="cu">12</strong></li>'));
    assert.ok(hero.indexOf("</aside>") < hero.indexOf('<div class="intro enter">') && hero.indexOf('<div class="intro enter">') < hero.indexOf("<h1>"));
    assert.ok(!h.includes("/images/"), "no bundled illustrations even with a manifest");
    assert.equal(count(h, /<img /g), 1, "only the portrait");
    assert.ok(section(h, "services").includes(`<ul class="rows"><li><div><h3>${dicts.en["interest.term"]}</h3><p>Pure protection at low cost.</p></div><a class="lnk" href="#contact" data-interest="term">`));
    assert.ok(section(h, "highlights").includes('<ul class="ilist stg"><li><svg class="ico"'));
    assert.ok(section(h, "achievements").includes('<ul class="years"><li><span class="yr">2022</span>'));
    assert.ok(section(h, "testimonials").includes('<div class="slider sl-kw"'));
    assert.ok(!/class="(say|bubble)/.test(h), "no bubbles (DECISIONS #87)");
  });
  ok("minimal: with no contact links the card's one button is the hero CTA, linking to the form", () => {
    const s = fx("sample-en");
    s.content = { ...s.content, contact: { ...s.content.contact, phone: "", whatsapp: "", email: "" } }; s.profile = { ...s.profile, phone: "" };
    assert.ok(heroOf(html(s, "minimal")).includes(`<p class="pc-acts"><a class="btn" href="#contact">${dicts.en["site.cta_default"]}</a></p>`));
  });

  // ---- spotlight and guide (the rest is in test-web-offers.mjs) ----
  ok("spotlight: dark hero band (id row, text, offer slider), a trust bar after it, an offer wall of the non-featured offers, picture tiles, icon cards with text only", () => {
    const h = html(offers, "spotlight"), hero = heroOf(h);
    assert.ok(/^id="hero"><div class="hero-band"><div class="hero-in">\n<div class="hero-top"><div class="hero-id">/.test(hero));
    assert.ok(hero.indexOf('<div class="hero-side"><div class="slider sl-hero"') > hero.indexOf("<h1>") && hero.indexOf('<ul class="tbar">') > hero.indexOf("</div></div>"));
    const wall = section(h, "offers");
    assert.ok(wall.includes('<ul class="offers wall stg"><li class="offer lift zoom"><img class="pic"') && !wall.includes(`<h3>${offers.offers.featured.title}</h3>`));
    assert.ok(section(h, "services").includes('<ul class="tiles stg"><li class="tile zoom"><img class="pic"'));
    for (const i of offers.content.highlights.items) assert.equal(section(h, "highlights").includes(`<h3>${i.title}</h3>`), Boolean(i.text), i.title);
  });
  ok("guide: a top bar without section links (the login only); the index of needs is a plain list (the call bar stays the only <nav>); a trust ticker; one big quote at a time", () => {
    const h = html(offers, "guide");
    assert.ok(topOf(h).includes('<div class="topbar">') && !/href="#(about|services|offers|faq|contact)"/.test(topOf(h)));
    assert.ok(topOf(h).includes('<li class="tb-login">'), "the customer login stays reachable (row and phone menu)");
    assert.ok(section(h, "offers").includes('<div class="needs"><div class="need-idx"><ul><li><a href="#need-'));
    assert.equal(count(h, /<nav[ >]/g), 1);
    assert.ok(section(h, "highlights").includes('<div class="ticker tk-trust"'));
    const q = section(html(en, "guide"), "testimonials");
    assert.ok(q.includes('<div class="slider sl-bigq"') && q.includes('<figure class="bigq"><blockquote>'));
  });

  // ---- the lively parts (sections/ui.js) ----
  ok("ui: a slider is a scroll-snap list with hidden controls (motion.js shows them); one slide has no controls and no autoplay; content stays as given", () => {
    const ctx = { t };
    const two = slider(["<p>a</p>", "<p>b</p>", ""], ctx, { label: 'X "y"', cls: "sl-x", auto: 3000 });
    assert.ok(two.startsWith('<div class="slider sl-x" data-auto="3000" role="region" aria-roledescription="carousel" aria-label="X &quot;y&quot;"><ul class="sl-track"><li class="slide"><p>a</p></li><li class="slide"><p>b</p></li></ul>'));
    assert.ok(two.includes(`<div class="sl-nav" hidden><button class="sl-btn sl-prev" type="button" aria-label="${dicts.en["site.slider_prev"]}">`) && two.includes(`data-label="${esc(dicts.en["site.slider_dot"].replace("{n}", "#"))}"`));
    const one = slider(["<p>a</p>"], ctx, {});
    assert.ok(one.includes('data-auto="0"') && !one.includes("sl-nav"));
    assert.equal(slider([], ctx), "");
  });
  ok("ui: a ticker repeats its run once, aria-hidden; its speed follows the text length; numbers() and topBar() only use what the agent set", () => {
    const tk = ticker(["<strong>A</strong> b", "c"], { cls: "tk-x", label: "L" });
    assert.ok(tk.startsWith('<div class="ticker tk-x" style="--tk:20s" role="region" aria-label="L"><ul class="tk-run"><li><strong>A</strong> b</li><li>c</li></ul><ul class="tk-run" aria-hidden="true">'));
    assert.ok(/--tk:90s/.test(ticker(["x".repeat(2000)])));
    assert.equal(ticker([]), "");
    assert.deepEqual(numbers({ yearsExperience: 12 }, { t }), [{ n: "12", label: "years of experience" }]);
    assert.deepEqual(numbers({}, { t }), []);
    const s = fx("sample-empty"), ctx = makeCtx(s, t, tok(s, "classic"), "classic");
    ctx.rendered = ["hero", "contact"];
    const bar = topBar(ctx);
    assert.ok(!bar.includes('href="#about"') && bar.includes('<li><a href="#contact">'));
  });
  ok("motion.js: under prefers-reduced-motion nothing autoplays or counts; a slider of one slide is left alone", async () => {
    const { wireMotion } = await import("../web/motion.js");
    let observed = 0, intervals = 0;
    const win = { matchMedia: () => ({ matches: true }), IntersectionObserver: function () { this.observe = () => { observed++; }; }, addEventListener() {}, setInterval: () => { intervals++; }, scrollY: 0 };
    const doc = { querySelectorAll: () => [], querySelector: () => null, hidden: false };
    wireMotion({ doc, win });
    assert.equal(intervals, 0); assert.equal(observed, 0);
  });

  // ---- shared guarantees, every template ----
  for (const k of ALL) ok(`${k}: keeps the enquiry form ids, the install block, the chips' data-interest (guide: need links) and the sticky bar`, () => {
    const h = html(k === "guide" || k === "spotlight" ? offers : en, k);
    for (const id of ["enq", "f-name", "f-phone", "f-email", "f-interest", "f-message", "f-consent", "e-form", "install", "install-btn", "install-how"]) assert.ok(h.includes(`id="${id}"`), id);
    if (k === "guide") assert.ok(heroOf(h).includes('href="#need-'));
    else assert.ok(heroOf(h).includes('<a class="chip" href="#contact" data-interest="'));
    assert.ok(h.includes('<nav class="callbar"'));
    assert.equal(count(h, /<h1[ >]/g), 1);
  });

  ok("all five: hostile content in the template-specific parts is escaped (designation, services, highlights, FAQ, testimonials, name)", () => {
    const x = fx("sample-xss"), bad = "<img src=x onerror=alert(9)>";
    x.content = { ...x.content, hidden: [],
      about: { ...x.content.about, designation: bad, yearsExperience: 3 },
      services: { offered: ["term"], items: [{ interest: "term", description: bad }, { interest: "bogus\"><script>", description: bad }] },
      highlights: { items: [{ icon: "nope\"><script>", title: bad, text: bad }] },
      testimonials: { items: [{ name: bad, city: bad, quote: bad }] },
      faq: { items: [{ question: bad, answer: bad }] } };
    x.profile = { ...x.profile, full_name: "Asha <b>Verma</b>", designation: bad };
    for (const k of ALL) {
      const h = html(x, k);
      assert.ok(!/<img src=x|<script|<b>/i.test(h), k + ": raw tag");
      assert.ok(!/ on\w+\s*=/i.test(h.replace(/&lt;[^>]*?&gt;/g, "")), k + ": attribute breakout");
      assert.ok(h.includes(esc(bad)) && h.includes("Asha &lt;b&gt;Verma&lt;/b&gt;"), k);
      assert.ok(!h.includes("bogus"), k + ": unknown interest dropped");
    }
  });

  for (const k of ALL) ok(`${k}/hi: renders in Hindi from the dictionary (no English UI text)`, () => {
    const h = html(hi, k, "hi");
    assert.ok(h.includes("आशा वर्मा") && h.includes(esc(dicts.hi["section.faq"])));
    for (const key of ["site.services_heading", "section.faq", "section.about", "site.slider_prev", "site.menu"]) assert.ok(!h.includes(">" + dicts.en[key] + "<") && !h.includes(`"${dicts.en[key]}"`), key);
    if (k === "minimal") assert.ok(heroOf(h).includes(`>${dicts.hi["site.call"]}</a>`));
    if (k === "modern") assert.ok(section(h, "about").includes(dicts.hi["field.languages_spoken"]));
    if (k === "classic") assert.ok(section(h, "about").includes('<strong class="cu">12</strong>'), "the number comes out of the Hindi sentence");
  });

  for (const k of ALL) ok(`${k}: the seeded site keeps its structure markers`, () => {
    const h = html(seeded, k);
    const marker = { classic: '<li class="plan-c lift zoom">', modern: '<span class="bt-n" aria-hidden="true">04</span>', minimal: '<aside class="pcard"><span class="hero-photo mono" aria-hidden="true">PN</span>', spotlight: '<div class="hero-band">', guide: '<div class="steps">' }[k];
    assert.ok(h.includes(marker), marker);
  });
}
