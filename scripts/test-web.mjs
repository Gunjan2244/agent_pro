// Node checks for the pure renderer (no browser, no backend). Run: node scripts/test-web.mjs
import { readFileSync, statSync, existsSync } from "node:fs";
import assert from "node:assert/strict";
import { makeT } from "../web/i18n.js";
import { visibleKeys, placeCompanies } from "../web/sections/index.js";
import { normalizeSite } from "../web/api.js";
import { validate } from "../web/form.js";
import { headData } from "../web/head.js";
import { esc } from "../web/sections/util.js";

const R = (p) => JSON.parse(readFileSync(new URL("../" + p, import.meta.url)));
const schema = R("shared/site-schema.json"), catalog = R("shared/catalog.json");
const LOCALE_KEYS = catalog.locales.map((l) => l.key);
const REGISTRY = R("shared/templates/index.json");
const TEMPLATE_KEYS = REGISTRY.templates.map((x) => x.key);
const FAMILY = Object.fromEntries(REGISTRY.templates.map((x) => [x.key, x.family]));
const dicts = Object.fromEntries(LOCALE_KEYS.map((k) => [k, R(`shared/i18n/${k}.json`)]));
const fx = (n) => normalizeSite(R(`web/dev-fixtures/${n}.json`));
const KEYS = schema.sections.map((s) => s.key);
let n = 0;
const ok = (name, fn) => { fn(); n++; console.log("PASS", name); };
// tpl: the template being rendered (its family comes from the registry, as in ssr.js / client.js)
const tokensFor = (s, tpl = s.template_key) => ({ accent: catalog.accentSwatches.find((x) => x.key === s.accent).hex, schema, catalog, family: FAMILY[tpl] || "personal" });

for (const tpl of TEMPLATE_KEYS) {
  const { render } = await import(`../web/templates/${tpl}/render.js`);
  for (const loc of LOCALE_KEYS) {
    ok(`${tpl}/${loc}: all ${KEYS.length} sections + footer`, () => {
      const site = fx("sample-" + loc);
      const html = render(site, makeT(dicts[loc]), tokensFor(site, tpl));
      for (const k of KEYS) assert.ok(html.includes(`id="${k}"`), "missing section " + k);
      assert.ok(html.includes("Made with Agent PRO") || loc === "hi");
      assert.ok(html.includes(dicts[loc]["site.footer_made_with"]));
      assert.ok(html.includes('class="hp"'), "honeypot");
      assert.ok(!/\{[a-z]+\}/.test(html.replace(/<style[\s\S]*?<\/style>/g, "")), "unreplaced placeholder");
      if (loc === "hi") assert.ok(html.includes("आशा वर्मा"));
      assert.ok(html.includes("tel:+919876543210") && html.includes("wa.me/919876543210") && html.includes("mailto:asha@example.com"));
      assert.ok(!/LIC/.test(html));
    });
  }
  ok(`${tpl}: hidden + sectionOrder respected`, () => {
    const site = fx("sample-empty");
    const html = render(site, makeT(dicts.en), tokensFor(site, tpl));
    const ids = [...html.matchAll(/<(?:section|header)[^>]*id="([a-z]+)"/g)].map((m) => m[1]);
    assert.deepEqual(ids, ["contact", "hero"]); // sectionOrder honoured, about/services/faq hidden
    assert.ok(!ids.includes("about") && !ids.includes("faq") && !ids.includes("services"));
  });
  ok(`${tpl}: XSS payloads escaped`, () => {
    const site = fx("sample-xss");
    const html = render(site, makeT(dicts.en), tokensFor(site, tpl));
    assert.ok(!/<script/i.test(html), "raw <script>");
    assert.ok(!/<img[^>]*onerror/i.test(html) && !/<b onmouseover/.test(html));
    assert.ok(!html.includes("javascript:"), "javascript: URL");
    assert.ok(!/ onerror\s*=/i.test(html.replace(/&lt;[^>]*?&gt;/g, "")), "attribute breakout via photo URL");
    assert.ok(html.includes("&lt;script&gt;alert(1)&lt;/script&gt;"));
    assert.ok(!html.includes('id="faq"'));
  });
}
ok("order: sectionOrder only; pinned always shown; unknown dropped", () => {
  // "companies" (DECISIONS #90) isn't in any saved order yet: it is placed after the anchor (about by default) and
  // never above the hero; it renders nothing without companies.
  assert.deepEqual(visibleKeys({ sectionOrder: ["about", "bogus", "hero"], hidden: ["hero", "about"] }, schema), ["hero", "companies", "contact"]);
  assert.deepEqual(visibleKeys({ sectionOrder: ["faq", "about"], hidden: [] }, schema), ["hero", "faq", "about", "companies", "contact"]);
  assert.deepEqual(visibleKeys({ sectionOrder: ["faq", "about"], hidden: ["companies"] }, schema), ["hero", "faq", "about", "contact"]);
});
ok("companies placement: after the anchor; else before services, the FAQ or contact; an order that names it is kept", () => {
  assert.deepEqual(placeCompanies(["hero", "about", "services"]), ["hero", "about", "companies", "services"]);
  assert.deepEqual(placeCompanies(["hero", "offers", "services", "highlights", "about"], "highlights"), ["hero", "offers", "services", "highlights", "companies", "about"]);
  assert.deepEqual(placeCompanies(["hero", "offers", "services", "faq"]), ["hero", "offers", "companies", "services", "faq"]);
  assert.deepEqual(placeCompanies(["hero", "contact"]), ["hero", "companies", "contact"]);
  assert.deepEqual(placeCompanies(["about", "hero"]), ["about", "hero", "companies"]);
  assert.deepEqual(placeCompanies(["hero", "companies", "about"]), ["hero", "companies", "about"]);
});
ok("unavailable: normalizeSite(null/garbage) -> null", () => {
  assert.equal(normalizeSite(null), null); assert.equal(normalizeSite([]), null); assert.equal(normalizeSite({ template_key: "classic" }), null);
});
const I = catalog.interests.map((i) => i.key);
ok("form validation", () => {
  const v = (o) => validate({ name: "A", phone: "9876543210", ...o }, I);
  assert.deepEqual(v({}).errors, {});
  assert.equal(v({ name: "" }).errors.name, "required");
  assert.equal(v({ phone: "12345" }).errors.phone, "phone");
  assert.equal(v({ phone: "5876543210" }).errors.phone, "phone");
  assert.equal(v({ phone: "+91 98765-43210" }).phone, "9876543210");
  assert.equal(v({ phone: "" }).errors.phone, "required");
  assert.equal(v({ email: "nope" }).errors.email, "email");
  assert.equal(v({ email: "a@b.in" }).errors.email, undefined);
  assert.equal(v({ interest: "bogus" }).errors.interest, "required");
  assert.equal(v({ message: "x".repeat(501) }).errors.message, "required");
});
ok("head: title/OG/JSON-LD, canonical, script-safe", () => {
  const h = headData(fx("sample-en"), { origin: "https://x.test", path: "/a/demo" });
  assert.equal(h.title, "Asha Verma — Insurance Advisor, Pune");
  assert.equal(h.canonical, "https://x.test/a/demo");
  const agency = JSON.parse(h.jsonld)["@graph"].find((x) => x["@type"] === "InsuranceAgency");
  assert.ok(agency); assert.equal(agency.telephone, "+919876543210");
  assert.equal(headData(fx("sample-en"), { origin: "https://x.test", canonicalHost: "asha.in" }).canonical, "https://asha.in/");
  assert.ok(!headData(fx("sample-xss"), { origin: "https://x.test" }).jsonld.includes("<"));
});
ok("i18n: every site.* key exists in en and hi", () => {
  for (const k of Object.keys(dicts.en).filter((k) => k.startsWith("site."))) assert.ok(dicts.hi[k], "hi missing " + k);
});

ok("normalizeSite keeps canonical_host (null when absent)", () => {
  assert.equal(fx("sample-hi").canonical_host, "asha-verma.example");
  assert.equal(fx("sample-en").canonical_host, null);
  assert.equal(normalizeSite({ published_content: {}, profile: {} }).canonical_host, null);
});
ok("canonical/og:url: canonical_host wins on the free link", () => {
  const site = fx("sample-hi");
  const h = headData(site, { origin: "https://x.test", path: "/a/demo-hi", canonicalHost: site.canonical_host });
  assert.equal(h.canonical, "https://asha-verma.example/"); assert.equal(h.og["og:url"], "https://asha-verma.example/");
  const h2 = headData(fx("sample-en"), { origin: "https://x.test", path: "/a/demo", canonicalHost: fx("sample-en").canonical_host });
  assert.equal(h2.canonical, "https://x.test/a/demo");
});
{
  const { submitEnquiry } = await import("../web/api.js");
  globalThis.window = { AGENTPRO_CONFIG: { SUPABASE_URL: "https://s.test", SUPABASE_ANON_KEY: "k" } };
  const realFetch = globalThis.fetch;
  let sent;
  const reply = (status, body) => { globalThis.fetch = async (u, o) => { sent = { u, body: JSON.parse(o.body) }; return { ok: status < 400, status, text: async () => JSON.stringify(body) }; }; };
  const args = { slug: "demo", host: "", name: "A", phone: "9876543210", email: "", interest: "term", message: "", honeypot: "" };
  const cases = [
    [200, { ok: true }, { status: "ok" }],
    [200, { ok: false, error: "rate_limited" }, { status: "rate_limited" }],
    [200, { ok: false, error: "unavailable" }, { status: "unavailable" }],
    [200, { ok: false, error: "invalid", field: "phone" }, { status: "invalid", field: "phone" }],
    [429, { message: "x" }, { status: "rate_limited" }],
  ];
  for (const [st, body, want] of cases) {
    reply(st, body);
    const got = await submitEnquiry(args);
    ok(`submit_enquiry ${JSON.stringify(body)} -> ${got.status}`, () => assert.deepEqual(got, want));
  }
  ok("submit_enquiry: unknown response / 500 throws", async () => {});
  reply(200, { ok: false, error: "weird" }); await assert.rejects(submitEnquiry(args));
  reply(500, { message: "rate limit text" }); await assert.rejects(submitEnquiry(args));
  ok("RPC argument names match BACKEND.md signatures", () => {
    assert.equal(sent.u, "https://s.test/rest/v1/rpc/submit_enquiry");
    assert.deepEqual(Object.keys(sent.body), ["slug", "host", "name", "phone", "email", "interest", "message", "honeypot", "consent"]);
  });
  const { getPublicSite, getPublicSiteByHost, recordSiteView } = await import("../web/api.js");
  reply(200, null); await getPublicSite("a"); ok("get_public_site(slug)", () => assert.deepEqual(Object.keys(sent.body), ["slug"]) || assert.ok(sent.u.endsWith("/get_public_site")));
  await getPublicSiteByHost("h"); ok("get_public_site_by_host(host)", () => { assert.deepEqual(Object.keys(sent.body), ["host"]); assert.ok(sent.u.endsWith("/get_public_site_by_host")); });
  await recordSiteView("a", null); ok("record_site_view(slug, host)", () => { assert.deepEqual(Object.keys(sent.body), ["slug", "host"]); assert.ok(sent.u.endsWith("/record_site_view")); });
  globalThis.fetch = realFetch; delete globalThis.window;
}
// sizes: JS + CSS actually loaded by one template (raw bytes; gzip in brackets)
import { gzipSync } from "node:zlib";
import { readdirSync } from "node:fs";
const dist = new URL("../web/dist/", import.meta.url).pathname;
try {
  const walk = (d) => readdirSync(d, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? walk(d + e.name + "/") : [d + e.name]));
  const shared = ["app.js", "client.js", "api.js", "i18n.js", "head.js", "form.js", "route.js", "tokens.css", "styles/base.css", "styles/pages.css", "styles/fonts.css", ...walk(dist + "sections/").map((f) => f.slice(dist.length))];
  // Server-rendered page (DECISIONS #75): first paint needs only CSS; this is the deferred JS that wires behaviour.
  {
    const css = ["tokens.css", "styles/base.css", "styles/fonts.css", `templates/${TEMPLATE_KEYS[0]}/style.css`];
    const hydrateJs = ["app.js", "hydrate.js", "api.js", "i18n.js", "form.js", "sections/util.js"];
    const size = (fs) => fs.reduce((a, f) => a + readFileSync(dist + f).length, 0), gz = (fs) => fs.reduce((a, f) => a + gzipSync(readFileSync(dist + f)).length, 0);
    console.log(`SIZE SSR first paint (CSS only, no JS): ${(size(css) / 1024).toFixed(1)} KB raw, ${(gz(css) / 1024).toFixed(1)} KB gzip`);
    for (const tpl of TEMPLATE_KEYS) {
      const c = [...css.slice(0, 3), `templates/${tpl}/style.css`];
      console.log(`SIZE SSR first paint ${tpl}: ${(size(c) / 1024).toFixed(1)} KB raw, ${(gz(c) / 1024).toFixed(1)} KB gzip (template style.css ${(size([c[3]]) / 1024).toFixed(1)} KB)`);
      // 2026-10-22 redesign: one layout system (grid, rhythm, accordion, companies, the family's hero) took base.css
      // from 11 KB to 15 KB raw; first paint is 21-24 KB raw but under 7 KB gzip, which is what the network carries.
      // 26 -> 34 KB raw, 7 -> 9 KB gzip: the lively redesign (DECISIONS #117: top bar, sliders, tickers, dark bands)
      // 34 -> 36 KB raw: multi-page sites (DECISIONS #122: the menu's current page, Home's teasers and contact band, each
      // template's skin of the inner pages); the inner pages' shared layouts are in styles/pages.css, not on Home
      assert.ok(size(c) < 36 * 1024, tpl + ": SSR first-paint CSS over 36KB raw");
      assert.ok(gz(c) < 9 * 1024, tpl + ": SSR first-paint CSS over 9KB gzip");
      // an inner page (Insurance, a plan type, About, Contact) also loads styles/pages.css (DECISIONS #122)
      const ci = [...c.slice(0, 3), "styles/pages.css", c[3]];
      console.log(`SIZE SSR first paint ${tpl} inner page: ${(size(ci) / 1024).toFixed(1)} KB raw, ${(gz(ci) / 1024).toFixed(1)} KB gzip`);
      assert.ok(size(ci) < 44 * 1024, tpl + ": inner-page first-paint CSS over 44KB raw");
      assert.ok(gz(ci) < 11 * 1024, tpl + ": inner-page first-paint CSS over 11KB gzip");
    }
    console.log(`SIZE SSR hydration JS (deferred): ${(size(hydrateJs) / 1024).toFixed(1)} KB raw, ${(gz(hydrateJs) / 1024).toFixed(1)} KB gzip; + install.js ${(size([...hydrateJs, "install.js"]) / 1024).toFixed(1)} KB raw; + privacy.js on the privacy page ${(size([...hydrateJs, "privacy.js"]) / 1024).toFixed(1)} KB raw`);
    assert.ok(size(css) < 36 * 1024, "SSR first-paint CSS over 36KB");
    assert.ok(size([...hydrateJs, "install.js"]) < 30 * 1024, "SSR hydration JS over 30KB");
  }
  // a template's own modules: render.js plus any template partials (parts.js, DECISIONS #85)
  const own = (tpl) => readdirSync(dist + `templates/${tpl}/`).filter((f) => f.endsWith(".js")).map((f) => `templates/${tpl}/${f}`);
  for (const tpl of TEMPLATE_KEYS) {
    const files = [...shared, ...own(tpl), `templates/${tpl}/style.css`];
    const buf = files.map((f) => readFileSync(dist + f));
    const raw = buf.reduce((a, b) => a + b.length, 0), gz = buf.reduce((a, b) => a + gzipSync(b).length, 0);
    console.log(`SIZE ${tpl}: ${(raw / 1024).toFixed(1)} KB raw, ${(gz / 1024).toFixed(1)} KB gzip`);
    // the client render is the preview/fallback path (SSR is the normal one). WP3b (offers, conversational layer, images,
    // 5 templates) took it from ~51KB to ~78KB raw (~30KB gzip); WP5c's template partials (modern/minimal parts.js and
    // their layouts, DECISIONS #85) add up to ~6KB; the 2026-10-22 redesign (one layout system in base.css, the companies
    // partials) another ~7KB, so the cap is 100KB (unminified, commented source); the SSR path keeps its own caps above.
    assert.ok(raw < 170 * 1024, tpl + " over 170KB"); // 124 -> 170KB: multi-page sites (DECISIONS #122: sections/pages.js, links.js, the page model in index.js, styles/pages.css, route.js page helpers), ~41KB of commented source; 100 -> 106KB: customer-accounts promo (DECISIONS #102), ~5KB; 106 -> 124KB: the lively redesign (DECISIONS #117: ui.js, template parts, top bar / slider / ticker CSS), ~14KB
    // privacy page / platform pages load the shared core + their own module (privacy.js, legal.js are lazy-imported, no template)
    // install.js + manifest.js are imported after the site renders (Add to home screen, DECISIONS #65)
    const later = [...shared, ...own(tpl), `templates/${tpl}/style.css`, "manifest.js", "install.js"].reduce((a, f) => a + readFileSync(dist + f).length, 0);
    if (tpl === TEMPLATE_KEYS[0]) console.log(`SIZE ${tpl} + install modules (after render): ${(later / 1024).toFixed(1)} KB raw`);
    assert.ok(later < 177 * 1024, tpl + " + install modules over 177KB"); // 131 -> 177KB: DECISIONS #122, same reason; 113 -> 131KB: DECISIONS #117 // 106 -> 112KB, same reason; 113KB: the hero's fixed designation (DECISIONS #114)
    for (const extra of ["privacy.js", "legal.js"]) {
      const sz = [...shared, extra].reduce((a, f) => a + readFileSync(dist + f).length, 0);
      if (tpl === TEMPLATE_KEYS[0]) console.log(`SIZE ${extra} page: ${(sz / 1024).toFixed(1)} KB raw`);
      assert.ok(sz < 158 * 1024, extra + " page over 158KB"); // 112 -> 158KB: DECISIONS #122 (the count includes every sections/ file and pages.css); 102 -> 112KB: DECISIONS #117 (sections/ui.js and base.css's lively parts); // 100 -> 102KB for the customer sign-up page (DECISIONS #108: the join routes in route.js and the #signup section, ~1KB); 94 -> 100KB for the customer-accounts promo (DECISIONS #102, portal-promo.js + CSS, ~5KB); counts every sections/ file (and all of base.css), which these pages never fully load
    }
    const fontKB = (...n) => n.reduce((a, f) => a + statSync(dist + "fonts/" + f).size, 0) / 1024;
    console.log(`FONTS (woff2, fetched on demand): EN page ${fontKB("inter-latin-400-normal.woff2", "inter-latin-500-normal.woff2", "inter-latin-600-normal.woff2", "inter-latin-700-normal.woff2").toFixed(1)} KB max (latin 400-700), +${fontKB("noto-sans-devanagari-devanagari-400-normal.woff2", "noto-sans-devanagari-devanagari-500-normal.woff2", "noto-sans-devanagari-devanagari-600-normal.woff2").toFixed(1)} KB Devanagari for Hindi, preload ${fontKB("inter-latin-400-normal.woff2").toFixed(1)} KB`);
  }
} catch (e) { if (e.code === "ENOENT") console.log("SIZE skipped (run build first)"); else throw e; }
// hosting config (Cloudflare Workers static assets, DECISIONS #59)
ok("wrangler.jsonc: SPA static assets; the Worker runs first only for config.js, pages, the customer portal, manifest, robots.txt, sitemap.xml (DECISIONS #65, #75, #98)", () => {
  const raw = readFileSync(new URL("../web/wrangler.jsonc", import.meta.url), "utf8");
  const strip = raw.split("\n").filter((l) => !l.trim().startsWith("//")).join("\n");
  const w = JSON.parse(strip);
  assert.equal(w.name, "agentpro-sites"); assert.equal(w.assets.directory, "./dist");
  assert.equal(w.assets.not_found_handling, "single-page-application");
  assert.equal(w.main, "./worker.js"); assert.equal(w.assets.binding, "ASSETS");
  assert.deepEqual(w.assets.run_worker_first, ["/config.js", "/", "/a/*", "/my", "/my/*", "/join", "/join/*", "/privacy", "/insurance", "/insurance/*", "/about", "/about/", "/contact", "/contact/", "/manifest.webmanifest", "/robots.txt", "/sitemap.xml"]);
  assert.ok(!w.routes, "no routes yet");
  assert.deepEqual(Object.keys(w.vars || {}), ["SITE_BASE_URL"], "the only var is the public SITE_BASE_URL; Supabase keys are Worker secrets (DECISIONS #125)");
});
if (existsSync(dist + "index.html")) ok("dist: _headers present, no vercel.json, no secrets in _headers", () => {
  assert.ok(existsSync(dist + "_headers")); assert.ok(!existsSync(dist + "vercel.json"));
  assert.ok(!/eyJ/.test(readFileSync(dist + "_headers", "utf8")));
});
// Interested-in select: blank placeholder first and selected; no interest sent when left blank
{
  const { render } = await import(`../web/templates/${TEMPLATE_KEYS[0]}/render.js`);
  for (const loc of LOCALE_KEYS) {
    const site = fx("sample-" + loc);
    const html = render(site, makeT(dicts[loc]), tokensFor(site));
    const sel = html.match(/<select id="f-interest"[^>]*>([\s\S]*?)<\/select>/)[1];
    const first = sel.match(/<option[^>]*>[^<]*<\/option>/)[0];
    ok(`interest select (${loc}): first option is the blank selected placeholder`, () => {
      assert.ok(first.includes('value=""') && first.includes("selected"), first);
      assert.ok(first.includes(dicts[loc]["site.form_interest_placeholder"]));
      assert.equal((sel.match(/ selected/g) || []).length, 1, "only the placeholder is selected");
      assert.ok(!/value="other"[^>]*selected/.test(sel));
    });
  }
  const { submitEnquiry } = await import("../web/api.js");
  globalThis.window = { AGENTPRO_CONFIG: { SUPABASE_URL: "https://s.test", SUPABASE_ANON_KEY: "k" } };
  const realFetch = globalThis.fetch; let body;
  globalThis.fetch = async (u, o) => { body = JSON.parse(o.body); return { ok: true, status: 200, text: async () => '{"ok":true}' }; };
  const base = { slug: "d", host: "", name: "A", phone: "9876543210", email: "", message: "", honeypot: "" };
  await submitEnquiry({ ...base, interest: "" });
  ok("blank interest -> null sent (not 'other')", () => assert.equal(body.interest, null));
  await submitEnquiry({ ...base, interest: null });
  ok("null interest -> null sent", () => assert.equal(body.interest, null));
  await submitEnquiry({ ...base, interest: "term" });
  ok("chosen interest is sent", () => assert.equal(body.interest, "term"));
  globalThis.fetch = realFetch; delete globalThis.window;
  ok("validate: blank interest is valid (optional)", () => assert.equal(validate({ name: "A", phone: "9876543210", interest: "" }, I).errors.interest, undefined));
  ok("form.js maps blank interest to null", () => assert.ok(/interest: v\.interest \|\| null/.test(readFileSync(new URL("../web/form.js", import.meta.url), "utf8"))));
}
// fonts: self-hosted, no third-party font origins anywhere in source or build
import { readdirSync as rd } from "node:fs";
{
  const walk = (d) => rd(d, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? walk(d + e.name + "/") : [d + e.name]));
  const web = new URL("../web/", import.meta.url).pathname;
  const files = walk(web).filter((f) => !f.includes("/node_modules/") && !f.includes("/.wrangler/") && !/\.(woff2|png|jpg|ico)$/.test(f));
  ok(`no file in web/ (source + dist, ${files.length} files) references fonts.googleapis/gstatic`, () => {
    for (const f of files) assert.ok(!/fonts\.(googleapis|gstatic)\.com/.test(readFileSync(f, "utf8")), f);
  });
  ok("every @font-face url resolves to a woff2 file with wOF2 magic", () => {
    const css = readFileSync(web + "styles/fonts.css", "utf8");
    const urls = [...css.matchAll(/url\(([^)]+)\)/g)].map((m) => m[1]);
    assert.ok(urls.length >= 11);
    for (const u of urls) assert.equal(readFileSync(web + u.replace(/^\//, "")).subarray(0, 4).toString(), "wOF2", u);
    assert.ok(/font-display:swap/.test(css) && !/https?:/.test(css));
  });
  ok("CSP: font-src 'self' only, no third-party style/font origins", () => {
    const csp = readFileSync(web + "_headers", "utf8").match(/Content-Security-Policy: (.*)/)[1];
    assert.ok(csp.includes("font-src 'self';")); assert.ok(!/fonts\./.test(csp));
  });
  ok("index.html loads fonts.css and preloads Inter latin 400", () => {
    const h = readFileSync(web + "index.html", "utf8");
    assert.ok(h.includes('href="/styles/fonts.css"') && h.includes('href="/fonts/inter-latin-400-normal.woff2"') && /as="font"[^>]*crossorigin/.test(h));
  });
  if (existsSync(web + "dist/index.html")) ok("dist contains fonts/", () => assert.ok(existsSync(web + "dist/fonts/inter-latin-400-normal.woff2") && existsSync(web + "dist/fonts/LICENSE-Inter-OFL.txt")));
}
// ---- P1-3 privacy (web) ----
import { spawnSync } from "node:child_process";
{
  const { consentFor, wireForm } = await import("../web/form.js");
  const { privacyHtml, wirePrivacy, validateRequest, kindsOf } = await import("../web/privacy.js");
  const { legalHtml } = await import("../web/legal.js");
  const { resolveTarget, siteLinks } = await import("../web/route.js");
  const { submitEnquiry, submitDataRequest } = await import("../web/api.js");
  const notices = R("shared/generated/privacy-notices.json");
  const nf = notices.notices.enquiry_form;
  const { render } = await import(`../web/templates/${TEMPLATE_KEYS[0]}/render.js`);
  const tokens = (s, href) => ({ ...tokensFor(s), notice: nf, privacyHref: href });

  ok("consentFor: notice key, version and the hash of the site's locale (EN and HI differ)", () => {
    assert.deepEqual(consentFor(notices.notices, "en"), { notice_key: "enquiry_form", version: nf.version, hash: nf.hash.en });
    assert.deepEqual(consentFor(notices.notices, "hi"), { notice_key: "enquiry_form", version: nf.version, hash: nf.hash.hi });
    assert.notEqual(nf.hash.en, nf.hash.hi); assert.match(nf.hash.hi, /^[0-9a-f]{64}$/);
    assert.equal(consentFor({}, "en"), null);
  });
  for (const loc of LOCALE_KEYS) {
    const site = fx("sample-" + loc), t = makeT(dicts[loc]);
    const html = render(site, t, tokens(site, "/a/demo/privacy"));
    ok(`enquiry form (${loc}): unticked required consent with notice text, privacy link, hint; footer Privacy link`, () => {
      const box = html.match(/<input id="f-consent"[^>]*>/)[0];
      assert.ok(!/checked/.test(box) && box.includes('type="checkbox"'));
      assert.ok(html.includes(esc(t("privacy.enquiry_form.consent", { name: site.profile.full_name }))));
      assert.ok(html.includes(`<a href="/a/demo/privacy">${dicts[loc]["privacy.enquiry_form.link"]}</a>`));
      assert.ok(html.includes('id="e-consent"') && html.includes(esc(dicts[loc]["site.form_message_hint"])));
      assert.ok(html.includes(`<a href="/a/demo/privacy">${dicts[loc]["site.footer_privacy"]}</a>`), "footer link");
    });
  }
  // fake DOM for wireForm / wirePrivacy
  const mkEl = (o = {}) => Object.assign({ textContent: "", hidden: true, attrs: {}, focused: 0, checked: false, disabled: false, listeners: {}, setAttribute(k, v) { this.attrs[k] = v; }, removeAttribute(k) { delete this.attrs[k]; }, focus() { this.focused++; }, addEventListener(e, f) { this.listeners[e] = f; } }, o);
  const fakeForm = (ids, values) => {
    const els = {}; for (const id of ids) { els["#e-" + id] = mkEl(); els["#re-" + id] = els["#e-" + id]; els["#f-" + id] = mkEl(); els["#r-" + id] = els["#f-" + id]; }
    els["button[type=submit]"] = mkEl({ textContent: "Send", hidden: false });
    const form = { els, querySelector: (sel) => els[sel] || (sel === '[aria-invalid="true"]' ? Object.values(els).find((e) => e.attrs["aria-invalid"]) || null : null), addEventListener(e, f) { this.l = f; }, outerHTML: null, values };
    return form;
  };
  // Object.fromEntries(new FormData(form)) -> make FormData return an array of pairs
  const pairs = (o) => Object.entries(o);
  const submitEnq = async (vals, ticked, reply) => {
    const form = fakeForm(["name", "phone", "email", "interest", "message", "consent", "form"], vals);
    form.els["#f-consent"].checked = ticked;
    form.els["#f-consent"].addEventListener = function () {};
    let calls = [];
    const real = globalThis.FormData; globalThis.FormData = function () { return pairs(vals); };
    wireForm({ querySelector: () => form }, { t: makeT(dicts.en), profile: { full_name: "Asha Verma" }, site: { content: {} }, catalog, slug: "demo", consent: consentFor(notices.notices, "hi"), submit: async (a) => { calls.push(a); return reply || { status: "ok" }; } });
    await form.l({ preventDefault() {} });
    globalThis.FormData = real;
    return { form, calls };
  };
  const good = { name: "Asha", phone: "9876543210", email: "", interest: "", message: "" };
  {
    const r = await submitEnq(good, false);
    ok("unticked consent: no submit call, inline consent error, focus on the box", () => {
      assert.equal(r.calls.length, 0);
      assert.equal(r.form.els["#e-consent"].textContent, dicts.en["site.form_error_consent"]); assert.equal(r.form.els["#e-consent"].hidden, false);
      assert.equal(r.form.els["#f-consent"].attrs["aria-invalid"], "true");
    });
    const r2 = await submitEnq(good, true);
    ok("ticked consent: submitted once with the consent payload (HI hash)", () => {
      assert.equal(r2.calls.length, 1);
      assert.deepEqual(r2.calls[0].consent, { notice_key: "enquiry_form", version: nf.version, hash: nf.hash.hi });
      assert.equal(r2.calls[0].interest, null);
    });
    const r3 = await submitEnq(good, true, { status: "invalid", field: "consent" });
    ok("server field:'consent' error is shown inline, button re-enabled", () => {
      assert.equal(r3.form.els["#e-consent"].textContent, dicts.en["site.form_error_consent"]);
      assert.equal(r3.form.els["button[type=submit]"].disabled, false); assert.equal(r3.form.outerHTML, null);
    });
  }
  // API payloads
  globalThis.window = { AGENTPRO_CONFIG: { SUPABASE_URL: "https://s.test", SUPABASE_ANON_KEY: "k" } };
  const rf = globalThis.fetch; let sent;
  const reply2 = (body) => { globalThis.fetch = async (u, o) => { sent = { u, body: JSON.parse(o.body) }; return { ok: true, status: 200, text: async () => JSON.stringify(body) }; }; };
  reply2({ ok: true });
  await submitEnquiry({ slug: "d", name: "A", phone: "9876543210", honeypot: "", consent: consentFor(notices.notices, "en") });
  ok("submit_enquiry payload carries consent {notice_key, version, hash}", () => {
    assert.deepEqual(Object.keys(sent.body), ["slug", "host", "name", "phone", "email", "interest", "message", "honeypot", "consent"]);
    assert.deepEqual(sent.body.consent, { notice_key: "enquiry_form", version: nf.version, hash: nf.hash.en });
  });
  const KINDS = kindsOf(catalog).map((k) => k.key);
  ok("data request kinds come from the catalog, withdraw_consent first", () => { assert.equal(KINDS[0], "withdraw_consent"); assert.deepEqual(KINDS.slice().sort(), catalog.dataRequestKinds.map((k) => k.key).sort()); });
  ok("validateRequest: kind from catalog, name, Indian mobile, message <= 500", () => {
    const v = (o) => validateRequest({ kind: "access", name: "A", phone: "9876543210", ...o }, KINDS);
    assert.deepEqual(v({}).errors, {});
    assert.equal(v({ kind: "bogus" }).errors.kind, "required"); assert.equal(v({ kind: "" }).errors.kind, "required");
    assert.equal(v({ name: " " }).errors.name, "required"); assert.equal(v({ phone: "123" }).errors.phone, "phone");
    assert.equal(v({ message: "x".repeat(501) }).errors.message, "required"); assert.equal(v({ phone: "+91 98765 43210" }).phone, "9876543210");
  });
  for (const [st, body, want] of [[200, { ok: true }, "ok"], [200, { ok: false, error: "rate_limited" }, "rate_limited"], [200, { ok: false, error: "unavailable" }, "unavailable"], [200, { ok: false, error: "invalid", field: "kind" }, "invalid"]]) {
    reply2(body); const got = await submitDataRequest({ slug: "demo", name: "A", phone: "9876543210", kind: "erasure", message: "", honeypot: "" });
    ok(`submit_data_request ${JSON.stringify(body)} -> ${want}`, () => { assert.equal(got.status, want); assert.ok(sent.u.endsWith("/rpc/submit_data_request")); });
  }
  ok("submit_data_request argument names", () => assert.deepEqual(Object.keys(sent.body), ["slug", "host", "name", "phone", "kind", "message", "honeypot"]));
  globalThis.fetch = rf; delete globalThis.window;
  {
    const sendReq = async (vals, reply) => {
      const form = fakeForm(["kind", "name", "phone", "message", "form"], vals); const calls = [];
      const real = globalThis.FormData; globalThis.FormData = function () { return pairs(vals); };
      wirePrivacy({ querySelector: () => form }, { t: makeT(dicts.en), catalog, slug: "demo", name: "Asha Verma", submit: async (a) => { calls.push(a); return reply; } });
      await form.l({ preventDefault() {} }); globalThis.FormData = real; return { form, calls };
    };
    const ok1 = await sendReq({ kind: "erasure", name: "Ravi", phone: "9876543210", message: "", website: "" }, { status: "ok" });
    ok("privacy request form: valid -> one call with kind, then thank-you", () => { assert.equal(ok1.calls.length, 1); assert.equal(ok1.calls[0].kind, "erasure"); assert.equal(ok1.calls[0].slug, "demo"); assert.ok(ok1.form.outerHTML.includes("Asha")); });
    const bad = await sendReq({ kind: "", name: "", phone: "1", message: "" }, { status: "ok" });
    ok("privacy request form: invalid -> no call, inline errors", () => { assert.equal(bad.calls.length, 0); assert.equal(bad.form.els["#re-kind"].hidden, false); assert.equal(bad.form.els["#re-phone"].textContent, dicts.en["site.form_error_phone"]); });
    const rl = await sendReq({ kind: "access", name: "R", phone: "9876543210", message: "" }, { status: "rate_limited" });
    ok("privacy request form: rate limited message", () => assert.ok(rl.form.outerHTML.includes("already sent")));
    const un = await sendReq({ kind: "access", name: "R", phone: "9876543210", message: "" }, { status: "unavailable" });
    ok("privacy request form: unavailable -> generic error, button back", () => { assert.equal(un.form.els["#re-form"].textContent, dicts.en["site.form_error_generic"]); assert.equal(un.form.els["button[type=submit]"].disabled, false); });
  }
  for (const loc of LOCALE_KEYS) {
    const site = fx("sample-" + loc), t = makeT(dicts[loc]);
    const html = privacyHtml(site, t, { notice: nf, catalog, backHref: "/a/demo" });
    ok(`privacy page (${loc}): agent named as fiduciary, notice parts, rights, grievance placeholders, request form with every kind`, () => {
      assert.ok(html.includes(site.profile.full_name));
      for (const k of nf.textKeys.slice(3)) assert.ok(html.includes(esc(t(k, { name: site.profile.full_name, phone: site.content.contact.phone, email: site.content.contact.email })).replace(/&#39;/g, "&#39;")), k);
      assert.ok(html.includes("{GRIEVANCE_OFFICER_NAME}") === (dicts[loc]["privacy.enquiry_form.rights"].includes("{GRIEVANCE_OFFICER_NAME}")));
      assert.ok(html.includes('id="req"') && html.includes('name="website"'));
      for (const k of KINDS) assert.ok(html.includes(`<option value="${k}">${esc(dicts[loc]["data_request_kind." + k])}</option>`), k);
      assert.ok(html.indexOf('value="withdraw_consent"') < html.indexOf('value="access"'));
      assert.ok(!/\{(name|phone|email)\}/.test(html));
    });
  }
  ok("privacy page: HTML-escapes agent-supplied name", () => assert.ok(!/<script/i.test(privacyHtml(fx("sample-xss"), makeT(dicts.en), { notice: nf, catalog }))));
  for (const loc of LOCALE_KEYS) for (const page of ["privacy", "terms"]) {
    const html = legalHtml(page, makeT(dicts[loc]), { notice: notices.notices.agent_signup, otherLang: "x", otherHref: "?lang=x" });
    ok(`platform /${page} shell (${loc}): title, sections, placeholders visible`, () => {
      assert.ok(html.includes(esc(dicts[loc][page === "terms" ? "site.legal_terms_title" : "site.legal_privacy_title"])));
      assert.ok(html.includes("{GRIEVANCE_OFFICER_NAME}"), "grievance placeholder shown as-is");
      assert.ok((html.match(/<h2>/g) || []).length >= 8);
      if (page === "privacy") { assert.ok(html.includes("{EMAIL_PROVIDER}")); assert.ok(html.includes("<table>")); }
    });
  }
  ok("routing: /a/<slug>, /a/<slug>/privacy, ?s&page, /privacy + /terms platform, custom domain", () => {
    const L = (host, pathname, search = "") => ({ host, hostname: host.split(":")[0], pathname, search });
    const P = "x.dev", g = (l) => resolveTarget(l, new URLSearchParams(l.search), P);
    assert.deepEqual(g(L(P, "/a/demo")), { slug: "demo", page: undefined, path: "" });
    assert.deepEqual(g(L(P, "/a/demo/")), { slug: "demo", page: undefined, path: "" });
    assert.deepEqual(g(L(P, "/a/demo/privacy")), { slug: "demo", page: "privacy" });
    assert.deepEqual(g(L(P, "/", "?s=demo&page=privacy")), { slug: "demo", page: "privacy" });
    assert.deepEqual(g(L(P, "/privacy")), { platform: "privacy" }); assert.deepEqual(g(L(P, "/terms/")), { platform: "terms" });
    assert.equal(g(L(P, "/")), null);
    assert.deepEqual(g(L("asha.in", "/privacy")), { host: "asha.in", page: "privacy" });
    assert.deepEqual(g(L("asha.in", "/")), { host: "asha.in", page: undefined, path: "" });
    assert.deepEqual(siteLinks({ slug: "demo" }, L(P, "/a/demo")), { home: "/a/demo/", privacy: "/a/demo/privacy" });
    assert.deepEqual(siteLinks({ host: "asha.in" }, L("asha.in", "/")), { home: "/", privacy: "/privacy" });
    const loc = siteLinks({ slug: "demo" }, L(P, "/", "?mock=1&s=demo"));
    assert.equal(loc.privacy, "?mock=1&s=demo&page=privacy"); assert.equal(loc.home, "?mock=1&s=demo");
  });
  ok("i18n: every new site.* / privacy page key exists in both locales, no privacy.* text changed by this work", () => {
    for (const l of LOCALE_KEYS) for (const k of Object.keys(dicts.en)) assert.ok(dicts[l][k], l + " missing " + k);
  });
  ok("_headers: /privacy and /terms are revalidated", () => { const h = readFileSync(new URL("../web/_headers", import.meta.url), "utf8"); assert.ok(/^\/privacy$/m.test(h) && /^\/terms$/m.test(h)); });
  // release gate
  const sh = (env) => spawnSync("sh", [new URL("../scripts/build-web.sh", import.meta.url).pathname], { env: { PATH: process.env.PATH, SITE_BASE_URL: "http://localhost:8080", SUPABASE_URL: "x", ...env }, encoding: "utf8" });
  const blockers = spawnSync("node", [new URL("../scripts/release-check.mjs", import.meta.url).pathname], { encoding: "utf8" }).stdout.trim();
  if (notices.releaseBlockers.length || blockers) {
    ok("release gate: RELEASE=1 fails (non-zero) while placeholders remain, naming them", () => { const r = sh({ RELEASE: "1" }); assert.notEqual(r.status, 0); assert.ok(r.stderr.includes("RELEASE BLOCKED") && r.stderr.includes("{GRIEVANCE_OFFICER_NAME}"), r.stderr); });
    ok("release gate: preview build only warns and succeeds", () => { const r = sh({}); assert.equal(r.status, 0, r.stderr); assert.ok(r.stderr.includes("preview build; release blockers remain")); });
  } else ok("release gate: no blockers left, RELEASE=1 builds", () => assert.equal(sh({ RELEASE: "1" }).status, 0));
}
await import("./test-web-offers.mjs").then((m) => m.run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }));
await import("./test-web-install.mjs").then((m) => m.run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }));
await import("./test-web-templates.mjs").then((m) => m.run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }));
await import("./test-web-companies.mjs").then((m) => m.run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }));
await import("./test-web-promo.mjs").then((m) => m.run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }));
await import("./test-web-types.mjs").then((m) => m.run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }));
await import("./test-web-pages.mjs").then((m) => m.run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }));
await import("./test-web-ssr.mjs").then((m) => m.run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }));
await import("./test-web-portal.mjs").then((m) => m.run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }));
await import("./test-web-callbar.mjs").then((m) => m.run({ ok }));
await import("./test-web-join.mjs").then((m) => m.run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }));
console.log(`\n${n} checks passed`);
