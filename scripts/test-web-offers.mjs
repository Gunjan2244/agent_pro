// WP3b checks (DECISIONS #76/#77/#79/#81): offers and the disguise, CTA kinds, the plan-card fallback, the
// absence of the chat-style layer (DECISIONS #87), the sticky call bar, bundled images and the hero scene, and the
// seeded fixture.
// Run by scripts/test-web.mjs.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

export async function run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }) {
  const { TEMPLATES, familyOf } = await import("../web/templates/index.js");
  const { normalizeSite } = await import("../web/api.js");
  const { headData } = await import("../web/head.js");
  const { fnv1a32 } = await import("../web/sections/media.js");
  const { esc } = await import("../web/sections/util.js");
  const { formatDate, todayIST } = await import("../web/sections/offer.js");
  const seedLib = await import("./site-seed.mjs");
  const registry = R("shared/templates/index.json");
  const MANIFEST = R("web/images/manifest.json");
  const raw = (n) => R(`web/dev-fixtures/${n}.json`);
  const PERSONAL = TEMPLATE_KEYS.filter((k) => familyOf(registry, k) === "personal");
  const OFFERS = TEMPLATE_KEYS.filter((k) => familyOf(registry, k) === "offers");
  const tok = (site, key, extra = {}) => ({ accent: catalog.accentSwatches.find((x) => x.key === site.accent).hex, schema, catalog, family: familyOf(registry, key), images: {}, seed: "demo", today: "2026-10-18", ...extra });
  const html = (site, key, loc = site.locale || "en", extra) => TEMPLATES[key](site, makeT(dicts[loc]), tok(site, key, extra));
  const section = (h, id) => { const i = h.indexOf(`id="${id}"`); return i < 0 ? "" : h.slice(i, h.indexOf("</section>", i)); };
  const heroOf = (h) => h.slice(h.indexOf('id="hero"'), h.indexOf("</header>"));
  const textOf = (h) => h.replace(/<script[\s\S]*?<\/script>/g, " ").replace(/<[^>]+>/g, " ").replace(/&amp;/g, "&").replace(/&#39;/g, "'").replace(/&quot;/g, '"');
  const en = makeT(dicts.en);

  // ---- offers ----
  {
    const site = fx("sample-en"), h = html(site, "classic");
    const sec = section(h, "offers"), hero = heroOf(h);
    ok("offers (classic): one slider of every live offer, the featured one first, 'My pick for you' / 'Details provided by the agent', no ended offer, disclaimer once", () => {
      assert.ok(sec.includes('<div class="slider sl-offers" data-auto="5000" role="region" aria-roledescription="carousel"'));
      const titles = [...sec.matchAll(/<h3>([^<]+)<\/h3>/g)].map((m) => m[1]);
      assert.deepEqual(titles.slice(0, 4), [site.offers.featured.title, ...site.offers.items.slice(0, 3).map((o) => o.title)]);
      assert.equal((sec.match(new RegExp(dicts.en["site.offer_pick"], "g")) || []).length, 4);
      assert.ok(sec.includes(dicts.en["site.offer_agent_note"]) && sec.includes("Free policy review this month"));
      assert.ok(!h.includes("Last season"), "ended offer (ends_on < today IST) is hidden");
      assert.equal(h.split(site.company.disclaimer).length - 1, 1, "company disclaimer once");
      assert.ok(sec.includes("Ends 31 Mar 2027") && sec.includes('<details class="more"><summary>' + dicts.en["offer_field.details"]));
      assert.ok(sec.includes('<button class="sl-btn sl-prev" type="button" aria-label="' + dicts.en["site.slider_prev"] + '">'), "slider controls (shown by motion.js)");
      assert.ok(sec.includes('<div class="sl-nav" hidden>'), "controls hidden without JS");
    });
    ok("hero (classic): no offer card while the offers section is on; with it off, the featured offer stays in the header (not a heading, a text-link CTA, no image)", () => {
      assert.ok(!hero.includes('class="offer'), "featured leads the slider instead");
      assert.equal((hero.match(/class="btn"/g) || []).length, 1, "the hero's one button (WhatsApp is a ghost button)");
      const off = fx("sample-en"); off.content = { ...off.content, hidden: ["offers"] };
      const hh = heroOf(html(off, "classic"));
      assert.ok(hh.includes(`<p class="ttl">${site.offers.featured.title}</p>`) && !/<h[23][ >]/.test(hh));
      assert.ok(/<div class="offer feat">[\s\S]*<a class="lnk" href="#contact" data-interest="pension">/.test(hh));
      assert.ok(!/<div class="offer feat"><img/.test(hh));
    });
    const so = fx("sample-offers"), hs = html(so, "spotlight", "en", { images: MANIFEST });
    ok("spotlight: an offer slider in the dark hero (featured first, titles not headings, a button each); grid with images; trust bar with counting years and clients", () => {
      const hero = heroOf(hs);
      assert.ok(hs.includes("fam-offers") && hero.includes('<div class="slider sl-hero"'));
      const ttl = [...hero.matchAll(/<p class="ttl">([^<]+)<\/p>/g)].map((m) => m[1]);
      assert.equal(ttl[0], so.offers.featured.title); assert.ok(ttl.length >= 2 && ttl.length <= 4, ttl.join());
      assert.ok(/<p class="ttl">[^<]+<\/p>[\s\S]*<a class="btn" href="#contact" data-interest="health">/.test(hero));
      assert.ok(!/<h[23][ >]/.test(hero), "the hero has only its <h1>");
      const grid = section(hs, "offers");
      assert.ok(grid.includes('src="/images/offer-pension-locked-rate-800.webp"'), "bundled demo image by key");
      assert.ok(grid.includes('src="/images/offer-simple-protection-800.webp"'), "offer-simple-protection by key");
      // An offer whose bundled image isn't in the manifest falls back to its interest's image.
      const { ["offer-simple-protection"]: _gone, ...noSimple } = MANIFEST;
      const fallback = section(html(so, "spotlight", "en", { images: noSimple }), "offers");
      assert.ok(fallback.includes('src="/images/interest-term-800.webp"'), "offer-simple-protection (not in manifest) -> interest-term");
      assert.ok(hero.includes('<ul class="tbar">') && hero.includes('<strong class="cu">12</strong>') && hero.includes('<strong class="cu">850</strong>'));
      assert.ok(!section(hs, "about").includes("<strong>12</strong>"), "numbers not repeated in about");
    });
    ok("spotlight with no featured offer leads with the first offer; guide never promotes one", () => {
      const s2 = { ...so, offers: { featured: null, items: so.offers.items } };
      assert.ok(heroOf(html(s2, "spotlight")).includes(`<p class="ttl">${so.offers.items[0].title}</p>`));
      assert.ok(!heroOf(html(s2, "guide")).includes('class="offer feat"'));
    });
    ok("guide: 'What would you like to protect?' picture tiles jump to #need-<interest> groups (each with its picture and <h3>), an index links them too; 'other' last; 'How it works' in 3 steps", () => {
      const g = html(so, "guide", "en", { images: MANIFEST }), hero = heroOf(g), sec = section(g, "offers");
      assert.ok(hero.includes(dicts.en["site.guide_question"]));
      const tiles = [...hero.matchAll(/<a class="nt lift zoom" href="#need-([a-z_]+)"><img class="pic"/g)].map((m) => m[1]);
      assert.ok(tiles.length >= 4 && tiles.at(-1) === "other", tiles.join());
      for (const k of tiles) assert.ok(new RegExp(`<div class="need" id="need-${k}"><div class="need-h"><img class="pic"[^>]*><div><h3>`).test(sec), k);
      assert.deepEqual([...sec.matchAll(/<li><a href="#need-([a-z_]+)">/g)].map((m) => m[1]), tiles, "index = tiles");
      assert.ok(g.includes(`<p class="st-h">${dicts.en["site.steps_heading"]}</p><ol><li><span class="st-n" aria-hidden="true">1</span>${dicts.en["site.step_1"]}</li>`));
      // the agent's own service description is a plain paragraph under the group heading
      const term = (so.content.services.items || []).find((i) => i.interest === "term");
      if (term && term.description) assert.ok(sec.includes(`<p class="need-d">${esc(term.description)}</p>`));
      assert.ok(!/class="btn" href="#contact">/.test(hero), "no generic hero button on the guide");
    });
    ok("offers family, no offers: plan-type cards from services under 'Plans I can help you with'; personal: no offers section", () => {
      const none = { ...fx("sample-offers"), offers: { featured: null, items: [] } };
      for (const k of OFFERS) {
        const sec = section(html(none, k, "en", { images: MANIFEST }), "offers");
        assert.ok(sec.includes(`<h2>${dicts.en["site.plans_heading"]}</h2>`), k);
        for (const i of none.content.services.items) assert.ok(sec.includes(`data-interest="${i.interest}"`), k + i.interest);
        assert.ok(sec.includes("interest-term-800.webp"), k + ": plan cards carry the interest image");
      }
      const p = { ...fx("sample-en"), offers: { featured: null, items: [] } };
      for (const k of PERSONAL) assert.ok(!html(p, k).includes('id="offers"'), k);
      const noServices = { ...none, content: { ...none.content, services: {} } };
      assert.ok(section(html(noServices, "spotlight"), "offers").includes('data-interest="term"'), "no services: every catalog interest but other");
    });
    ok("offers past their end date (IST) are hidden on render", () => {
      assert.ok(!html(fx("sample-en"), "classic", "en", { today: "2027-04-01" }).includes('id="offers"'));
      assert.match(todayIST(Date.UTC(2026, 9, 18, 19, 0)), /^2026-10-19$/, "19:00 UTC is already the next day in IST");
      assert.equal(formatDate("2027-03-31", "en"), "31 Mar 2027"); assert.ok(formatDate("2027-03-31", "hi").includes("2027")); assert.equal(formatDate("bad", "en"), "");
    });
  }
  // ---- CTA kinds and https ----
  {
    const base = fx("sample-offers");
    const one = (cta, extra = {}) => { const o = { ...base.offers.items[0], id: "x", cta, ...extra }; return section(html({ ...base, offers: { featured: null, items: [base.offers.items[1], o] } }, "spotlight", "en", { images: MANIFEST }), "offers"); };
    ok("CTA: enquire -> #contact + data-interest; call -> tel: (none without a phone); link -> https only, new tab, noopener", () => {
      assert.ok(one({ kind: "enquire", interest: "child" }).includes('<a class="lnk" href="#contact" data-interest="child">'));
      assert.ok(!one({ kind: "enquire", interest: "bogus" }).includes('data-interest="bogus"'));
      assert.ok(one({ kind: "call" }).includes('<a class="lnk" href="tel:+919876543210">' + dicts.en["site.offer_cta_call"]));
      const noPhone = { ...base, profile: { ...base.profile, phone: "" }, content: { ...base.content, contact: { ...base.content.contact, phone: "" } }, offers: { featured: null, items: [base.offers.items[1], { ...base.offers.items[0], cta: { kind: "call" } }] } };
      assert.ok(!section(html(noPhone, "spotlight"), "offers").includes("tel:"));
      assert.ok(one({ kind: "link", url: "https://example.com/b" }).includes('href="https://example.com/b" target="_blank" rel="noopener nofollow"'));
      for (const u of ["http://example.com/b", "javascript:alert(1)", "https://x.test/\"onclick=1"]) assert.ok(!one({ kind: "link", url: u }).includes(dicts.en["site.offer_cta_link"]), u);
      assert.ok(one({ kind: "link", url: "https://e.com" }, { image_url: "http://x.test/a.webp" }).includes("offer-pension-locked-rate"), "http image_url ignored");
      assert.ok(one({ kind: "enquire", interest: "term" }, { image_url: "https://p.supabase.co/o/1.webp", image: null }).includes('<img class="pic" src="https://p.supabase.co/o/1.webp" width="800" height="450" alt="Lifelong pension'));
    });
  }
  ok("top-level offer.interest (every resolved offer): a call/link offer gets its interest image and lands in its guide group", () => {
    const base = fx("sample-offers");
    const call = { ...base.offers.items[0], id: "c1", title: "Call me about child plans", image: null, image_url: null, interest: "child", cta: { kind: "call" } };
    const s2 = { ...base, offers: { featured: base.offers.items[1], items: [call] } };
    const sp = section(html(s2, "spotlight", "en", { images: MANIFEST }), "offers");
    assert.ok(sp.includes('src="/images/interest-child-800.webp"') && sp.includes('href="tel:+919876543210"'));
    const g = html(s2, "guide", "en", { images: MANIFEST });
    const grp = section(g, "offers").split('<div class="need" id="need-child">')[1] || "";
    assert.ok(grp.split('<div class="need"')[0].includes("Call me about child plans"), "in the child group");
    assert.ok(!g.includes('id="need-other"'), "not filed under other");
    assert.ok(section(html({ ...base, offers: { featured: null, items: [{ ...call, interest: undefined, cta: { kind: "enquire", interest: "pension" } }] } }, "guide"), "offers").includes('id="need-pension"'), "falls back to cta.interest");
    const cancer = section(html(base, "guide", "en", { images: MANIFEST }), "offers").split('<div class="need" id="need-health">')[1].split('<div class="need"')[0];
    assert.ok(cancer.includes("Cancer care cover"), "the link-CTA demo offer is in health via its top-level interest");
  });
  // ---- the disguise (DECISIONS #77) ----
  ok("disguise: a payload company name or logo is never rendered (normalizeSite keeps only the disclaimer)", () => {
    const row = raw("sample-offers");
    row.company = { name: "Demo Life Insurer", logo_url: "https://p.supabase.co/storage/v1/object/public/company-media/logos/1/3.webp", disclaimer: "Read the brochure." };
    const s = normalizeSite(row);
    assert.deepEqual(s.company, { disclaimer: "Read the brochure." });
    for (const k of TEMPLATE_KEYS) for (const l of LOCALE_KEYS) {
      const h = html({ ...s, locale: l }, k, l, { images: MANIFEST });
      assert.ok(!h.includes("Demo Life Insurer") && !h.includes("company-media") && !h.includes("logo"), k + l);
    }
  });
  {
    // the matching rule of PublicText (SHARED_DEFINITIONS.md publicTextRules): NFC + lower case, words separated by
    // any whitespace, no letter/mark/digit right before or after
    const terms = catalog.publicTextRules.blockedTerms;
    const re = terms.map((t) => new RegExp(`(?<![\\p{L}\\p{M}\\p{N}])${t.normalize("NFC").toLowerCase().split(/\s+/).map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("\\s+")}(?![\\p{L}\\p{M}\\p{N}])`, "u"));
    const hits = (text) => { const s = text.normalize("NFC").toLowerCase(); return terms.filter((t, i) => re[i].test(s)); };
    ok("blocked terms: the matcher works (a sample term hits; a longer word does not)", () => { assert.deepEqual(hits("Ask about Jeevan  Anand"), ["jeevan anand"]); assert.deepEqual(hits("जीवन आनंदमय"), []); });
    ok("blocked terms: no fixture renders a blocked term in any template or locale", () => {
      for (const n of ["sample-en", "sample-hi", "sample-offers", "sample-seeded"]) for (const k of TEMPLATE_KEYS) {
        const s = fx(n);
        // The companies section shows the insurers' names on purpose (DECISIONS #90); everything else must stay clean.
        const page = html(s, k, s.locale, { images: MANIFEST }), cs = section(page, "companies");
        assert.deepEqual(hits(textOf(cs ? page.replace(cs, "") : page)), [], n + "/" + k);
      }
    });
  }
  // ---- no chat-style layer (DECISIONS #87) ----
  for (const k of TEMPLATE_KEYS) for (const l of LOCALE_KEYS) {
    const s = fx("sample-" + l), t = makeT(dicts[l]), h = html(s, k, l);
    ok(`${k}/${l}: no speech bubbles, avatars or greeting; quick-interest chips (guide: need tiles) stay; FAQ as an accordion`, () => {
      assert.ok(!/class="(say|bubble|you|av)[ "]/.test(h), "no chat markup");
      for (const key of Object.keys(dicts[l]).filter((x) => x.startsWith("site.say_"))) assert.ok(!h.includes(esc(t(key, { firstName: s.profile.full_name.split(" ")[0] }))), key);
      const chips = [...heroOf(h).matchAll(k === "guide" ? /<a class="nt[ "][^>]*href="#need-[a-z_]+"/g : /<a class="chip" href="#contact"/g)];
      assert.ok(chips.length >= 1 && chips.length <= (k === "guide" ? 9 : 4));
      if (k !== "guide") assert.ok(heroOf(h).includes(`<a class="chip" href="#contact" data-interest="term">${t("interest.term")}</a>`));
      const f = section(h, "faq");
      assert.ok(/<div class="faq"><details><summary>[^<]+<\/summary><p class="prose">[^<]+<\/p><\/details>/.test(f), "accordion rows");
      assert.equal((f.match(/<details>/g) || []).length, s.content.faq.items.length);
    });
  }
  ok("no photo: a CSS initials monogram in the hero, never a stock face", () => {
    const s = fx("sample-en"); s.content = { ...s.content, hero: { ...s.content.hero, photo: "" } }; s.profile = { ...s.profile, photo_url: "" };
    const h = html(s, "classic");
    assert.ok(h.includes('<span class="hero-photo mono" aria-hidden="true">AV</span>'));
    assert.ok(!/<img class="hero-photo"/.test(h));
  });
  ok("xss: chips, monogram, companies and offers are escaped; hostile offer URLs, images and interests dropped", () => {
    for (const k of TEMPLATE_KEYS) {
      const h = html(fx("sample-xss"), k, "en", { images: MANIFEST });
      assert.ok(h.includes("&lt;script&gt;alert(7)&lt;/script&gt;") && h.includes("&lt;script&gt;alert(15)&lt;/script&gt;"), k);
      assert.ok(!/<script|javascript:|insecure\.test|x\.test\/o\.png|\.\.\/\.\.\/etc|onclick=|<i>new/.test(h), k);
      assert.ok(!/ on\w+\s*=/i.test(h.replace(/&lt;[^>]*?&gt;/g, "")), k + ": attribute breakout");
      assert.ok(/<span class="hero-photo mono" aria-hidden="true">[^<>]{1,4}<\/span>/.test(h), k + ": monogram is plain text");
      assert.ok(!h.includes("Evil Co"), k + ": company name");
    }
  });
  // ---- sticky call bar ----
  ok("sticky Call/WhatsApp bar: a <nav aria-label> after the footer when the numbers exist, and the space reserved; none without numbers", () => {
    const h = html(fx("sample-en"), "classic");
    assert.ok(h.includes(`<nav class="callbar" aria-label="${dicts.en["section.contact"]}"><a class="btn" href="tel:+919876543210">${dicts.en["site.call"]}</a><a class="btn ghost" href="https://wa.me/919876543210" rel="noopener">${dicts.en["site.whatsapp"]}</a></nav>`));
    assert.ok(/class="tpl tpl-classic fam-personal has-bar"/.test(h) && h.indexOf("</footer>") < h.indexOf('class="callbar"'));
    const s = fx("sample-en"); s.content = { ...s.content, contact: { ...s.content.contact, phone: "", whatsapp: "" } }; s.profile = { ...s.profile, phone: "" };
    const n = html(s, "spotlight");
    assert.ok(!n.includes("callbar") && !n.includes("has-bar"));
    const w = fx("sample-en"); w.content = { ...w.content, contact: { ...w.content.contact, phone: "", whatsapp: "9876543210" } }; w.profile = { ...w.profile, phone: "" };
    assert.ok(/<nav class="callbar"[^>]*><a class="btn ghost" href="https:\/\/wa\.me/.test(html(w, "classic")), "WhatsApp only");
    const css = readFileSync(new URL("../web/styles/base.css", import.meta.url), "utf8");
    assert.ok(/\.callbar\{position:fixed/.test(css) && /\.tpl\.has-bar\{padding-bottom:/.test(css) && /min-width:720px\)\{\.callbar\{display:none\}/.test(css));
  });
  // ---- bundled images (docs/IMAGES.md) ----
  ok("images: none when the manifest is empty (only the agent's photo)", () => {
    for (const k of TEMPLATE_KEYS) {
      const h = html(fx("sample-offers"), k, "en", { images: {} });
      assert.ok(!h.includes("/images/"), k);
    }
  });
  ok("images: listed ones get srcset/sizes, explicit size, lazy + async, translated alt (decorative ones alt=\"\")", () => {
    const h = html(fx("sample-en"), "classic", "en", { images: MANIFEST });
    const label = dicts.en["interest.term"];
    assert.ok(section(h, "services").includes(`<img class="pic" src="/images/interest-term-800.webp" srcset="/images/interest-term-400.webp 400w, /images/interest-term-800.webp 800w" sizes="(min-width: 1024px) 340px, (min-width: 720px) 50vw, 100vw" width="${MANIFEST["interest-term"].w}" height="${MANIFEST["interest-term"].h}" alt="${dicts.en["site.image_alt_interest"].replace("{interest}", label)}" loading="lazy" decoding="async">`));
    assert.ok(/<img class="pic talk" src="\/images\/contact-talk-800\.webp"[^>]*width="800" height="600" alt="" loading="lazy" decoding="async">/.test(section(h, "contact")));
    const hi = html(fx("sample-hi"), "classic", "hi", { images: MANIFEST });
    assert.ok(hi.includes(`alt="${dicts.hi["site.image_alt_interest"].replace("{interest}", dicts.hi["interest.term"])}"`));
    const partial = html(fx("sample-en"), "classic", "en", { images: { "interest-health": MANIFEST["interest-health"] } });
    assert.ok(partial.includes("interest-health-400.webp") && !partial.includes("interest-term-") && !partial.includes("contact-talk"), "only listed names");
    assert.ok(!html(fx("sample-en"), "classic", "en", { images: { "interest-term": { w: 800, h: 450, sizes: "nope" } } }).includes("interest-term-"), "malformed entry ignored");
  });
  ok("images: the manifest lists every shipped WebP and nothing else; masters stay out of web/images", () => {
    const { readdirSync } = await_fs();
    const files = readdirSync(new URL("../web/images/", import.meta.url)).filter((f) => f !== "manifest.json").sort();
    assert.ok(!files.some((f) => !f.endsWith(".webp")), "only WebP");
    const want = Object.entries(MANIFEST).flatMap(([n, m]) => m.sizes.map((w) => `${n}-${w}.webp`)).sort();
    assert.deepEqual(files, want);
    for (const i of catalog.interests) if (MANIFEST[i.image]) assert.equal(MANIFEST[i.image].h * 16, MANIFEST[i.image].w * 9, i.image);
  });
  ok("hero scene (DECISIONS #117): classic's banner picture and spotlight's picture when it has no offers, picked from the slug, eager (above the fold, not high priority); none elsewhere", () => {
    const n = (fnv1a32("demo") % 3) + 1;
    const none = { ...fx("sample-offers"), offers: { featured: null, items: [] } };
    for (const k of TEMPLATE_KEYS) {
      const h = heroOf(html(fx("sample-offers"), k, "en", { images: MANIFEST, seed: "demo" }));
      if (k === "classic") assert.ok(new RegExp(`<img class="bn-img fade" src="/images/hero-scene-${n}-800\\.webp"[^>]*alt="" decoding="async">`).test(h), k);
      else assert.ok(!h.includes("hero-scene"), k);
      assert.equal((h.match(/fetchpriority="high"/g) || []).length, 1, k + ": the photo stays the only high-priority image");
    }
    assert.ok(new RegExp(`<img class="scene" src="/images/hero-scene-${n}-800\\.webp"[^>]*alt="" decoding="async">`).test(heroOf(html(none, "spotlight", "en", { images: MANIFEST, seed: "demo" }))), "spotlight without offers");
    const picks = new Set(["a", "b", "c", "d", "e", "f"].map((s) => heroOf(html(fx("sample-offers"), "classic", "en", { images: MANIFEST, seed: s })).match(/hero-scene-(\d)/)[1]));
    assert.ok(picks.size >= 2, "different slugs get different scenes");
  });
  ok("og:image: the agent's photo; without one, the family's bundled share image (absolute URL); none without a manifest", () => {
    const s = fx("sample-en"), np = { ...s, content: { ...s.content, hero: { ...s.content.hero, photo: "" } }, profile: { ...s.profile, photo_url: "" } };
    assert.equal(headData(s, { origin: "https://x.test", images: MANIFEST }).og["og:image"], s.content.hero.photo);
    const p = headData(np, { origin: "https://x.test", images: MANIFEST, family: "personal" });
    assert.equal(p.og["og:image"], "https://x.test/images/og-personal-1200.webp"); assert.equal(p.og["og:image:width"], "1200"); assert.equal(p.twitter["twitter:image"], p.og["og:image"]);
    assert.equal(headData(np, { origin: "https://x.test", images: MANIFEST, family: "offers" }).og["og:image"], "https://x.test/images/og-offers-1200.webp");
    assert.equal(headData(np, { origin: "https://x.test" }).og["og:image"], undefined);
  });
  // ---- thank-you message ----
  {
    const { wireForm } = await import("../web/form.js");
    const vals = { name: "Ravi", phone: "9876543210", email: "", interest: "", message: "" };
    const el = () => ({ textContent: "", hidden: true, setAttribute() {}, removeAttribute() {}, focus() {}, addEventListener() {} });
    const box = { ...el(), checked: true };
    const btn = { ...el(), textContent: "Send" };
    const form = { outerHTML: null, addEventListener(e, f) { this.l = f; }, querySelector: (q) => (q === "#f-consent" ? box : q === "button[type=submit]" ? btn : el()) };
    const root = { querySelector: (q) => (q === "#enq" ? form : null) };
    const real = globalThis.FormData; globalThis.FormData = function () { return Object.entries(vals); };
    wireForm(root, { t: en, profile: { full_name: "Asha <b>Verma", phone: "9876543210" }, site: { content: {} }, catalog, slug: "d", submit: async () => ({ status: "ok" }) });
    await form.l({ preventDefault() {} });
    globalThis.FormData = real;
    ok("thank-you: a normal success message (site.form_thank_you with the first name, no bubble) and a Call button", () => {
      assert.ok(form.outerHTML.startsWith(`<div class="enq done" role="status"><p class="done-t">${esc(en("site.form_thank_you", { name: "Asha" }))}</p>`));
      assert.ok(!/say|bubble|class="av/.test(form.outerHTML) && form.outerHTML.includes('href="tel:+919876543210"'));
    });
  }
  // ---- seeded fixture ----
  ok("seed helper matches the SiteSeed test vectors (FNV-1a 32, salts, picks)", () => {
    assert.equal(seedLib.fnv1a32(""), 0x811c9dc5); assert.equal(seedLib.fnv1a32("a"), 0xe40c292c); assert.equal(seedLib.fnv1a32("foobar"), 0xbf9cf968);
    const id = "00000000-0000-0000-0000-000000000001";
    assert.deepEqual(["hero.headline", "hero.subheadline", "about.bio", "faq.items.2.answer"].map((s) => seedLib.pick(id, s, 3)), [1, 0, 0, 2]);
    assert.equal(seedLib.salt("faq", "items", 2, "answer"), "faq.items.2.answer"); assert.equal(seedLib.salt("hero", "headline"), "hero.headline");
    assert.equal(seedLib.fillVariant({ key: "k", fallback: "f" }, "en", { name: "A" }, { en: { k: "{name} in {city}", f: "{name} here" } }), "A here");
    assert.equal(seedLib.fillVariant("k", "en", { name: "A" }, { en: { k: "{name} in {city}" } }), null);
  });
  ok("sample-seeded.json is what site-seed.mjs builds (run `node scripts/site-seed.mjs` after changing the seed)", () => {
    assert.equal(readFileSync(new URL("../web/dev-fixtures/sample-seeded.json", import.meta.url), "utf8"), JSON.stringify(seedLib.seededFixture(), null, 1) + "\n");
  });
  for (const k of TEMPLATE_KEYS) ok(`${k}: the seeded site (a brand-new agent) renders every general section, no testimonials/achievements`, () => {
    const s = fx("sample-seeded"), h = html(s, k, "en", { images: MANIFEST });
    for (const id of ["about", "services", "highlights", "faq", "contact"]) assert.ok(h.includes(`id="${id}"`), id);
    assert.ok(/<header [^>]*id="hero"/.test(h) && !h.includes('id="testimonials"') && !h.includes('id="achievements"'));
    assert.ok(!/\{[a-zA-Z]+\}/.test(textOf(h)), "no unfilled placeholder");
    assert.ok(h.includes("Hello, I&#39;m Priya Nair") && h.includes('<span class="hero-photo mono" aria-hidden="true">PN</span>'));
    assert.ok(!/class="(say|bubble)/.test(h), "no bubbles");
  });
}
import * as fs from "node:fs";
const await_fs = () => fs;
