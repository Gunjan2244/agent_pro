// Multi-page sites (DECISIONS #122; shared/site-pages.json): routing on both hosts and the local fallback, the Worker
// serving every page (headers, 301, 404, 304, sitemap), per-page head (title, canonical, description, h1), SSR/client
// parity per page, the page menu with aria-current on every template, the plan pages, cross-page enquiry links and the
// query preselect, the in-app preview's hash links, escaping and Hindi. Run by scripts/test-web.mjs.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

export async function run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }) {
  const root = new URL("../", import.meta.url);
  const worker = (await import("../web/worker.js")).default;
  const { renderDocument } = await import("../web/ssr.js");
  const { TEMPLATES, familyOf } = await import("../web/templates/index.js");
  const { resolveTarget, matchPage, pageUrl, linksFrom, previewHash, siteLinks } = await import("../web/route.js");
  const { preselectInterest } = await import("../web/form.js");
  const { esc } = await import("../web/sections/util.js");
  const PAGES = R("shared/site-pages.json"), registry = R("shared/templates/index.json"), IMAGES = R("web/images/manifest.json");
  const notices = R("shared/generated/privacy-notices.json");
  const shared = { schema, catalog, templates: registry, images: IMAGES, pages: PAGES };
  const between = (h, a, b) => h.slice(h.indexOf(a) + a.length, h.indexOf(b, h.indexOf(a)));
  const meta = (h, attr, name) => (h.match(new RegExp(`<meta ${attr}="${name}" content="([^"]*)"`)) || [])[1];
  const count = (h, re) => (h.match(re) || []).length;
  const tokens = (site, key, extra = {}) => ({ accent: catalog.accentSwatches.find((x) => x.key === site.accent).hex, schema, catalog, notice: notices.notices.enquiry_form, privacyHref: "/a/demo/privacy", family: familyOf(registry, key), images: IMAGES, seed: "demo", sitePages: PAGES, ...extra });
  const page = (site, key, path, loc = site.locale || "en", extra) => TEMPLATES[key]({ ...site, template_key: key }, makeT(dicts[loc]), tokens(site, key, { page: matchPage(PAGES, path), ...extra }));
  const pagesOf = (site, key, loc = site.locale || "en") => TEMPLATES[key]({ ...site, template_key: key }, makeT(dicts[loc]), tokens(site, key, { pagesOnly: true }));
  const en = fx("sample-en");

  // ---- the page model and routing ----
  ok("site-pages.json: Home, Insurance, a plan page per type, About, Contact; every label/title/lead key in EN and HI; sections are schema sections", () => {
    assert.deepEqual(PAGES.pages.map((p) => p.key), ["home", "insurance", "plan", "planDetail", "about", "contact", "go"]); // planDetail and go: DECISIONS #130
    const known = new Set([...schema.sections.map((s) => s.key), "plans"]);
    for (const p of PAGES.pages) {
      for (const k of ["labelKey", "titleKey", "leadKey"]) if (p[k]) for (const l of LOCALE_KEYS) assert.ok(dicts[l][p[k]], `${l} ${p[k]}`);
      for (const s of p.sections) assert.ok(known.has(s), s);
    }
    assert.ok(PAGES.pages.find((p) => p.key === "home").sections.includes("hero") && PAGES.pages.find((p) => p.key === "contact").sections.includes("contact"), "the pinned sections have a page");
  });
  ok("wrangler.jsonc run_worker_first lists every page path of a custom domain (and the trailing-slash forms)", () => {
    const w = JSON.parse(readFileSync(new URL("web/wrangler.jsonc", root), "utf8").split("\n").filter((l) => !l.trim().startsWith("//")).join("\n"));
    const rwf = w.assets.run_worker_first;
    for (const p of PAGES.pages.filter((x) => x.path)) {
      const path = "/" + (p.path.includes("{") ? p.path.slice(0, p.path.indexOf("/{")) + "/*" : p.path);
      assert.ok(rwf.includes(path) || rwf.includes(path.replace(/\/\*$/, "/*")), path);
      if (!p.path.includes("{")) assert.ok(rwf.includes(path + "/") || rwf.includes(path + "/*"), path + "/");
    }
  });
  ok("route: page paths on the primary host, a custom domain and the ?s= fallback; matchPage; trailing slash kept for the 301", () => {
    const L = (host, pathname, search = "") => ({ host, hostname: host, pathname, search });
    const P = "sites.test", g = (l) => resolveTarget(l, new URLSearchParams(l.search), P);
    assert.deepEqual(g(L(P, "/a/demo/about")), { slug: "demo", page: undefined, path: "about" });
    assert.deepEqual(g(L(P, "/a/demo/insurance/term")), { slug: "demo", page: undefined, path: "insurance/term" });
    assert.deepEqual(g(L(P, "/a/demo/contact/")), { slug: "demo", page: undefined, path: "contact/" });
    assert.deepEqual(g(L(P, "/", "?s=demo&page=insurance%2Fhealth")), { slug: "demo", page: undefined, path: "insurance/health" });
    assert.deepEqual(g(L(P, "/", "?s=demo&page=privacy")), { slug: "demo", page: "privacy" });
    assert.deepEqual(g(L("asha.in", "/insurance/child")), { host: "asha.in", page: undefined, path: "insurance/child" });
    assert.deepEqual(g(L("asha.in", "/about")), { host: "asha.in", page: undefined, path: "about" });
    assert.equal(g(L(P, "/a/demo/my")).page, "portal"); assert.equal(g(L(P, "/a/demo/join")).page, "join");
    const m = (p) => { const x = matchPage(PAGES, p); return x && [x.key, x.plan || null]; };
    assert.deepEqual([m(""), m("insurance"), m("insurance/term"), m("about"), m("contact")], [["home", null], ["insurance", null], ["plan", "term"], ["about", null], ["contact", null]]);
    for (const bad of ["foo", "insurance/", "insurance/Term", "insurance/a/B", "insurance/a/b/c", "about/x", "insurance/te rm", "insurance/<x>", "go/x", "go/x/unknown", "go/X/plan", "insurance/term/-x"]) assert.equal(matchPage(PAGES, bad), null, bad);
    // a plan's own page and the "We're taking you to …" page (DECISIONS #130)
    const d = matchPage(PAGES, "insurance/term/lics-digi-term"), g2 = matchPage(PAGES, "go/lics-digi-term/brochure");
    assert.deepEqual([d.key, d.plan, d.slug], ["planDetail", "term", "lics-digi-term"]);
    assert.deepEqual([g2.key, g2.slug, g2.doc], ["go", "lics-digi-term", "brochure"]);
  });
  ok("links: path (primary, custom), query (?s= fallback / mock), hash (in-app preview); interest + anchor; previewHash", () => {
    assert.deepEqual(linksFrom("/a/demo/privacy"), { mode: "path", base: "/a/demo/" });
    assert.deepEqual(linksFrom("/privacy"), { mode: "path", base: "/" });
    assert.deepEqual(linksFrom("?mock=1&s=demo&t=classic&page=privacy"), { mode: "query", base: "mock=1&s=demo&t=classic" });
    assert.deepEqual(linksFrom("#"), { mode: "hash", base: "" });
    assert.equal(pageUrl(linksFrom("/a/demo/privacy"), "contact", { interest: "term", anchor: "enq" }), "/a/demo/contact?interest=term#enq");
    assert.equal(pageUrl(linksFrom("/privacy"), ""), "/");
    assert.equal(pageUrl(linksFrom("?mock=1&s=demo&page=privacy"), "insurance/term"), "?mock=1&s=demo&page=insurance%2Fterm");
    assert.equal(pageUrl(linksFrom("?mock=1&s=demo&page=privacy"), ""), "?mock=1&s=demo");
    assert.equal(pageUrl(linksFrom("#"), "contact", { interest: "health", anchor: "enq" }), "#p=contact&interest=health");
    assert.deepEqual(previewHash("#p=insurance/term"), { path: "insurance/term", interest: "" });
    assert.deepEqual(previewHash("#p=contact&interest=health"), { path: "contact", interest: "health" });
    assert.equal(previewHash("#need-term"), null); assert.equal(previewHash(""), null);
    assert.equal(siteLinks({ slug: "demo" }, { pathname: "/", search: "?s=demo&page=about&interest=term" }).home, "?s=demo");
  });

  // ---- which pages a site has ----
  ok("pages: the EN demo has Home, Insurance, one page per plan type it offers (each followed by its official plans' own pages, DECISIONS #130), About and Contact (all five templates)", () => {
    for (const k of TEMPLATE_KEYS) assert.deepEqual(pagesOf(en, k).map((p) => p.path), ["", "insurance", "insurance/term", "insurance/term/sample-life-secure-term", "insurance/health", "insurance/health/example-family-health-shield", "insurance/child", "insurance/pension", "insurance/pension/sample-life-golden-years-pension", "insurance/endowment", "insurance/endowment/sample-life-family-saver", "about", "contact"], k);
  });
  ok("pages: a page left with no content is dropped (and renders \"\", so it 404s); not-offered or unknown plan types have no page", () => {
    const s = fx("sample-en");
    s.content = { ...s.content, hidden: ["about", "highlights", "achievements", "testimonials", "companies"] };
    for (const k of TEMPLATE_KEYS) {
      assert.ok(!pagesOf(s, k).some((p) => p.key === "about"), k);
      assert.equal(page(s, k, "about"), "", k);
      assert.ok(!page(s, k, "").includes("/a/demo/about\""), k + ": no menu link to it");
      for (const plan of ["insurance/motor", "insurance/other", "insurance/bogus"]) assert.equal(page(en, k, plan), "", k + plan);
    }
  });

  // ---- each page: semantics, menu, parity ----
  const FIX = [["sample-en", "en"], ["sample-hi", "hi"], ["sample-offers", "en"], ["sample-seeded", "en"]];
  for (const k of TEMPLATE_KEYS) for (const [name, loc] of FIX) {
    const site = { ...fx(name), template_key: k };
    for (const p of pagesOf(site, k, loc)) {
      const label = `${k}/${name}/${p.path || "home"}`;
      ok(`${label}: SSR equals render(); one <h1>, an <h2> per section, header, footer, the call bar the only <nav>; menu marks the page`, () => {
        const doc = renderDocument({ site, target: { slug: "demo", path: p.path }, url: new URL("https://sites.test/a/demo/" + p.path), dict: dicts[loc], shared, notices, config: {} });
        const inner = between(doc, '<main id="root" data-ssr>', '</main>\n<script type="application/json" id="boot">');
        assert.equal(inner, page(site, k, p.path, loc), "SSR/client parity");
        assert.equal(count(inner, /<h1[ >]/g), 1, "one h1");
        const secs = count(inner, /<section /g);
        assert.equal(count(inner, /<h2[ >]/g), secs, "an h2 per section");
        for (const sec of inner.split(/(?=<section )/).slice(1)) assert.ok(/<h2[ >]/.test(sec.split("</section>")[0]), "section without h2");
        assert.ok(p.key === "home" ? /<header class="sec sec-hero" id="hero">/.test(inner) : /<header class="sec sec-ph" id="top">/.test(inner));
        assert.equal(count(inner, /<header /g), 1); assert.equal(count(inner, /<footer /g), 1);
        assert.equal(count(inner, /<nav /g), inner.includes('class="callbar"') ? 1 : 0, "the call bar stays the only nav");
        assert.ok(inner.includes('<nav class="callbar"'), "the sticky bar is on every page");
        // the menu: top bar (or minimal's card) with the pages, the current one aria-current="page" (plan: Insurance "true")
        const menu = k === "minimal" ? between(inner, '<ul class="pc-nav">', "</ul>") : between(inner, '<ul class="tb-links">', "</ul>");
        const sub = p.key === "plan" || p.key === "planDetail"; // a plan type's page and a plan's own page (DECISIONS #130)
        const cur = sub ? "insurance" : p.key;
        const href = "/a/demo/" + (cur === "home" ? "" : cur);
        assert.ok(menu.includes(`<a href="${href}" aria-current="${sub ? "true" : "page"}">${esc(dicts[loc][PAGES.pages.find((x) => x.key === cur).labelKey])}</a>`), label + " menu");
        assert.equal(count(menu, /aria-current/g), 1);
        assert.ok(!/href="#(about|services|offers|faq|contact)"/.test(inner), "no in-page section anchors in the menu or links");
        assert.ok(!/>(site|section|field)\.[a-z_]+</.test(inner) && !/\{[a-z]+\}/.test(inner), "no raw keys or placeholders");
        assert.ok(!/\bLIC\b/.test(inner));
        // head: title, canonical, og:url, description; boot page
        const canonical = "https://sites.test/a/demo/" + p.path;
        const hc = site.canonical_host ? `https://${site.canonical_host}/${p.path}` : canonical;
        assert.ok(doc.includes(`<link rel="canonical" href="${hc}">`), label + " canonical");
        assert.equal(meta(doc, "property", "og:url"), hc);
        assert.ok(meta(doc, "name", "description"), "description");
        const boot = JSON.parse(between(doc, '<script type="application/json" id="boot">', "</script>"));
        assert.equal(boot.page, p.key === "home" ? null : p.key);
        if (p.key !== "home") assert.ok(doc.includes("<title>" + esc(dicts[loc]["site.page_title"].replace("{page}", between(inner, "<h1>", "</h1>").replace(/&amp;/g, "&")).replace("{name}", site.profile.full_name))), label + " title");
        if (loc === "hi") for (const key of ["site.page_home", "site.page_insurance", "site.page_about", "site.page_contact"]) assert.ok(!menu.includes(">" + dicts.en[key] + "<"), key);
      });
    }
  }

  // ---- page contents ----
  ok("Insurance: plan-type cards linking to their pages with 'N offers'; the offers; the help band", () => {
    const h = page(en, "classic", "insurance");
    const links = [...h.matchAll(/<li class="ptype lift zoom"><a href="([^"]+)">/g)].map((m) => m[1]);
    assert.deepEqual(links, ["term", "health", "child", "pension", "endowment"].map((x) => "/a/demo/insurance/" + x));
    assert.ok(h.includes(`<span class="pill">${dicts.en["site.plan_offers"].replace("{n}", "2")}</span>`) && h.includes(`<span class="pill">${dicts.en["site.plan_offer_one"]}</span>`));
    assert.ok(h.includes('id="offers"') && h.includes('<section class="sec sec-help" id="help">') && h.includes(`href="/a/demo/contact?interest=`) === h.includes("data-interest"));
    assert.ok(h.includes('<p class="crumb"><a href="/a/demo/">' + dicts.en["site.page_home"] + "</a>"));
  });
  ok("plan page: picture, the type as <h1>, the agent's description, 'Ask about this plan' to the contact form with the type preselected, its offers two per row, other plans as picture chips, the help band", () => {
    for (const k of TEMPLATE_KEYS) {
      const h = page(en, k, "insurance/health");
      assert.ok(h.includes(`<h1>${dicts.en["interest.health"]}</h1>`) && h.includes('<p class="lead">Cover for hospital bills.</p>'), k);
      assert.ok(h.includes(`<a class="btn" href="/a/demo/contact?interest=health#enq" data-interest="health">${dicts.en["site.plan_ask"]}</a>`), k);
      if (k !== "minimal") assert.ok(/<div class="ph-pic fade"><img class="pic" src="\/images\/interest-health-800\.webp"/.test(h), k + " picture");
      assert.ok(h.includes('<ul class="offers two stg">') && h.includes("Family health cash plan") && !h.includes("Lifelong pension"), k + " only that type's offers");
      assert.ok(h.includes('<ul class="pchips">') && h.includes('href="/a/demo/insurance/term"') && !h.includes('<a class="pchip" href="/a/demo/insurance/health"'), k + " other plans");
      assert.ok(h.includes('<p class="crumb"><a href="/a/demo/">') && h.includes(`<a href="/a/demo/insurance">${dicts.en["site.page_insurance"]}</a>`), k + " crumb");
      assert.ok(h.includes('id="help"'), k);
    }
    const none = page(en, "classic", "insurance/term");
    assert.ok(none.includes(`<div class="note"><p>${esc(dicts.en["site.plan_no_offer"])}</p><a class="lnk" href="/a/demo/contact?interest=term#enq" data-interest="term">`), "no offer: the dashed note");
  });
  ok("About: the story header (photo 4:5, <h1>, designation, bio, counting numbers, languages, agency code), companies ticker, icon cards, timeline, quotes slider, help band", () => {
    for (const k of TEMPLATE_KEYS) {
      const h = page(en, k, "about");
      const head = between(h, '<header class="sec sec-ph" id="top">', "</header>");
      assert.ok(head.includes(`<h1>${dicts.en["site.page_about"]}</h1>`) && head.includes('<p class="kicker">Insurance Advisor</p>') && head.includes("I have helped families"), k);
      assert.ok(head.includes('<strong class="cu">12</strong>') && head.includes("English, Hindi, Marathi"), k);
      if (k !== "minimal") assert.ok(head.includes('<img class="ph-photo"') && head.includes('width="640" height="800"'), k);
      assert.ok(!h.includes('id="about"'), k + ": the about section isn't repeated under its own header");
      assert.ok(h.includes('<div class="ticker tk-cos"') && h.includes('<ul class="icards stg">') && h.includes('<ol class="tline stg">') && h.includes('<div class="slider sl-sq"'), k);
    }
  });
  ok("Contact: header, ways to reach me (Call / WhatsApp / Email cards), the address card, the enquiry form unchanged (ids, consent, privacy link), FAQ beside the portal card; no help band", () => {
    const s = { ...fx("sample-en"), portal: true };
    for (const k of TEMPLATE_KEYS) {
      const h = page(s, k, "contact");
      for (const id of ["enq", "f-name", "f-phone", "f-email", "f-interest", "f-message", "f-consent", "e-consent", "e-form", "install", "install-btn", "install-how"]) assert.ok(h.includes(`id="${id}"`), k + id);
      assert.ok(h.includes('<a href="/a/demo/privacy">' + dicts.en["privacy.enquiry_form.link"] + "</a>"), k + " consent link");
      assert.ok(h.includes('<ul class="ways ways-3">') && h.includes('href="tel:+919876543210"') && h.includes('href="mailto:asha@example.com"') && h.includes('<div class="card where">'), k);
      assert.ok(h.indexOf('id="contact"') < h.indexOf('id="faq"') && /<div class="duo"><section class="sec sec-faq" id="faq">[\s\S]*<section class="sec sec-portal" id="portal">/.test(h), k + " faq beside portal");
      assert.ok(!h.includes('id="help"'), k);
      assert.ok(h.includes('href="/a/demo/my/login"'), k + ": the customer login stays in the menu");
    }
  });
  ok("Home: the template's hero and teasers (a few plan cards to their pages, 'All insurance plans', a short about with 'More about me', the contact band); no FAQ or form", () => {
    for (const k of TEMPLATE_KEYS) {
      const h = page(en, k, "");
      assert.ok(h.includes('id="hero"') && !h.includes('id="faq"') && !h.includes('id="enq"'), k);
      assert.ok(h.includes(`<a class="lnk" href="/a/demo/insurance">${dicts.en["site.plan_back"]}</a>`), k + " all plans");
      assert.ok(h.includes(`<a class="lnk" href="/a/demo/about">${dicts.en["site.more_about"]}</a>`), k + " more about me");
      assert.ok(h.includes('<div class="cband">') && h.includes('href="/a/demo/contact#enq"'), k + " contact band");
      if (k !== "guide") assert.ok(h.includes('href="/a/demo/insurance/term"'), k + " a plan card links to its page");
    }
    const long = fx("sample-en"); long.content = { ...long.content, about: { ...long.content.about, bio: "word ".repeat(200) } };
    assert.ok(/word…</.test(page(long, "classic", "")) && !/word…</.test(page(long, "classic", "about")), "the bio is cut on Home only");
  });
  ok("cross-page enquiry links: chips, offer and plan 'Enquire now' go to the contact page with the interest (and #enq), keeping data-interest", () => {
    for (const k of TEMPLATE_KEYS) {
      const s = k === "spotlight" || k === "guide" ? fx("sample-offers") : en;
      const h = page(s, k, "");
      for (const m of h.matchAll(/<a [^>]*data-interest="([a-z_]+)"[^>]*>/g)) assert.ok(m[0].includes(`href="/a/demo/contact?interest=${m[1]}#enq"`), k + m[0]);
      assert.ok(!h.includes('href="#contact"'), k);
    }
    // guide's picture tiles go to the plan pages
    assert.ok(page(fx("sample-offers"), "guide", "").includes('<a class="nt lift zoom" href="/a/demo/insurance/health">'));
  });
  ok("custom domain and preview links: /contact?interest=… on a custom host; #p=… in the in-app preview (no network)", () => {
    const h = page(en, "modern", "insurance", "en", { privacyHref: "/privacy" });
    assert.ok(h.includes('href="/insurance/term"') && h.includes('<a href="/about">'));
    const pv = page(en, "classic", "", "en", { privacyHref: "#" });
    assert.ok(pv.includes('<a href="#p=about">') && pv.includes('href="#p=contact&amp;interest=term"') && pv.includes('<a href="#p=insurance" aria-current="true">') === false);
    assert.ok(pv.includes('<a href="#p=" aria-current="page">'));
  });
  ok("preselectInterest: only an active catalog interest that the form offers", () => {
    const sel = { value: "", options: [{ value: "" }, { value: "term" }, { value: "health" }] };
    const r = { querySelector: (q) => (q === "#f-interest" ? sel : null) };
    assert.equal(preselectInterest(r, "health", catalog), true); assert.equal(sel.value, "health");
    assert.equal(preselectInterest(r, "bogus", catalog), false); assert.equal(sel.value, "health");
    assert.equal(preselectInterest(r, "pension", catalog), false, "not offered by the form");
    assert.equal(preselectInterest(r, null, catalog), false); assert.equal(preselectInterest({ querySelector: () => null }, "term", catalog), false);
  });
  ok("hydrate.js preselects the interest from the query (contact?interest=…)", async () => {
    const src = readFileSync(new URL("web/hydrate.js", root), "utf8");
    assert.ok(/preselectInterest\(root, q\.get\("interest"\), catalog\)/.test(src));
  });
  ok("escaping: hostile content on every page of every template (sample-xss), plan pages included", () => {
    const x = fx("sample-xss");
    for (const k of TEMPLATE_KEYS) for (const p of pagesOf(x, k)) {
      const h = page(x, k, p.path);
      assert.ok(h, k + p.path);
      assert.ok(!/<script/i.test(h) && !/<img[^>]*onerror/i.test(h) && !h.includes("javascript:"), k + "/" + p.path);
      assert.ok(!/ on\w+\s*=/i.test(h.replace(/&lt;[^>]*?&gt;/g, "")), k + "/" + p.path + ": attribute breakout");
    }
  });

  // ---- the Worker ----
  const PUBLISHED = "2026-10-01T10:00:00.000Z";
  const files = { "/config.js": 'window.AGENTPRO_CONFIG = {\n  SUPABASE_URL: "https://s.test",\n  SUPABASE_ANON_KEY: "anon",\n  SITE_BASE_URL: "https://sites.test"\n};\n' };
  const SHELL = "<!doctype html><main id=root>SHELL</main>";
  const env = { ASSETS: { fetch: async (req) => {
    const p = new URL(req.url || req).pathname;
    if (files[p]) return new Response(files[p]);
    if (p.startsWith("/shared/") || p.startsWith("/images/")) { try { return new Response(readFileSync(new URL((p.startsWith("/images/") ? "./web" : ".") + p, root))); } catch { return new Response("nf", { status: 404 }); } }
    return new Response(SHELL, { status: 200 });
  } } };
  const realFetch = globalThis.fetch;
  let rpcs = [], reply = {};
  globalThis.fetch = async (u, o) => { const name = String(u).split("/rpc/")[1]; rpcs.push(name); return new Response(JSON.stringify(reply[name] ?? null)); };
  const get = (u, init) => worker.fetch(new Request(u, init), env);
  reply.get_public_site = [{ ...R("web/dev-fixtures/sample-en.json"), published_at: PUBLISHED }];
  reply.get_public_site_by_host = [{ ...R("web/dev-fixtures/sample-hi.json"), canonical_host: null, published_at: PUBLISHED }];
  const results = {};
  for (const p of ["", "insurance", "insurance/term", "about", "contact"]) {
    const r = await get("https://sites.test/a/demo/" + p);
    results[p] = { status: r.status, cc: r.headers.get("cache-control"), lm: r.headers.get("last-modified"), csp: r.headers.get("content-security-policy"), body: await r.text() };
  }
  ok("Worker: every page 200, server-rendered, no-cache, Last-Modified, the security headers, its own canonical", () => {
    for (const [p, r] of Object.entries(results)) {
      assert.equal(r.status, 200, p); assert.equal(r.cc, "no-cache"); assert.equal(r.lm, new Date(PUBLISHED).toUTCString()); assert.ok(r.csp);
      assert.ok(r.body.includes('<main id="root" data-ssr>') && r.body.includes(`<link rel="canonical" href="https://sites.test/a/demo/${p}">`), p);
    }
  });
  const statuses = {};
  rpcs = [];
  for (const [n, u] of [["slash", "https://sites.test/a/demo/about/?x=1"], ["unknown", "https://sites.test/a/demo/nope"], ["deep", "https://sites.test/a/demo/about/more"]]) {
    const r = await get(u); statuses[n] = [r.status, r.headers.get("location")];
  }
  const rpcsBefore = rpcs.length;
  const motor = await get("https://sites.test/a/demo/insurance/motor");
  const ims = await get("https://sites.test/a/demo/contact", { headers: { "if-modified-since": new Date(PUBLISHED).toUTCString() } });
  const motorIms = await get("https://sites.test/a/demo/insurance/motor", { headers: { "if-modified-since": new Date(PUBLISHED).toUTCString() } });
  const head = await get("https://sites.test/a/demo/about", { method: "HEAD" });
  ok("Worker: /about/ 301s to /about (query kept); unknown pages 404 with no backend call; a plan type the agent doesn't offer 404s (never 304); 304 and HEAD on a page", () => {
    assert.deepEqual(statuses.slash, [301, "https://sites.test/a/demo/about?x=1"]);
    assert.equal(statuses.unknown[0], 404); assert.equal(statuses.deep[0], 404); assert.equal(rpcsBefore, 0, "no RPC for an unknown path");
    assert.equal(motor.status, 404); assert.equal(motorIms.status, 404);
    assert.equal(ims.status, 304); assert.equal(head.status, 200);
  });
  const ch = {};
  for (const p of ["/", "/insurance", "/insurance/health", "/about", "/contact", "/contact/", "/a/whatever", "/nope"]) { const r = await get("https://asha.in" + p); ch[p] = [r.status, r.headers.get("location"), await r.text()]; }
  ok("Worker on a custom domain: /, /insurance, /insurance/<type>, /about, /contact server-rendered (Hindi), canonical on the domain; /contact/ 301; other paths pass through", () => {
    for (const p of ["/", "/insurance", "/insurance/health", "/about", "/contact"]) {
      assert.equal(ch[p][0], 200, p); assert.ok(ch[p][2].includes(`<link rel="canonical" href="https://asha.in${p}">`) && ch[p][2].includes('<html lang="hi">'), p);
    }
    assert.ok(ch["/about"][2].includes(`<a href="/about" aria-current="page">${dicts.hi["site.page_about"]}</a>`));
    assert.deepEqual(ch["/contact/"].slice(0, 2), [301, "https://asha.in/contact"]);
    assert.equal(ch["/a/whatever"][2], SHELL); assert.equal(ch["/nope"][2], SHELL);
  });
  globalThis.fetch = realFetch;
}
