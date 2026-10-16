// Node checks for the pure renderer (no browser, no backend). Run: node scripts/test-web.mjs
import { readFileSync, statSync } from "node:fs";
import assert from "node:assert/strict";
import { makeT } from "../web/i18n.js";
import { visibleKeys } from "../web/sections/index.js";
import { normalizeSite } from "../web/api.js";
import { validate } from "../web/form.js";
import { headData } from "../web/head.js";

const R = (p) => JSON.parse(readFileSync(new URL("../" + p, import.meta.url)));
const schema = R("shared/site-schema.json"), catalog = R("shared/catalog.json");
const LOCALE_KEYS = catalog.locales.map((l) => l.key);
const TEMPLATE_KEYS = R("shared/templates/index.json").templates.map((x) => x.key);
const dicts = Object.fromEntries(LOCALE_KEYS.map((k) => [k, R(`shared/i18n/${k}.json`)]));
const fx = (n) => normalizeSite(R(`web/dev-fixtures/${n}.json`));
const KEYS = schema.sections.map((s) => s.key);
let n = 0;
const ok = (name, fn) => { fn(); n++; console.log("PASS", name); };
const tokensFor = (s) => ({ accent: catalog.accentSwatches.find((x) => x.key === s.accent).hex, schema, catalog });

for (const tpl of TEMPLATE_KEYS) {
  const { render } = await import(`../web/templates/${tpl}/render.js`);
  for (const loc of LOCALE_KEYS) {
    ok(`${tpl}/${loc}: all 8 sections + footer`, () => {
      const site = fx("sample-" + loc);
      const html = render(site, makeT(dicts[loc]), tokensFor(site));
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
    const html = render(site, makeT(dicts.en), tokensFor(site));
    const ids = [...html.matchAll(/<(?:section|header)[^>]*id="([a-z]+)"/g)].map((m) => m[1]);
    assert.deepEqual(ids, ["contact", "hero"]); // sectionOrder honoured, about/services/faq hidden
    assert.ok(!ids.includes("about") && !ids.includes("faq") && !ids.includes("services"));
  });
  ok(`${tpl}: XSS payloads escaped`, () => {
    const site = fx("sample-xss");
    const html = render(site, makeT(dicts.en), tokensFor(site));
    assert.ok(!/<script/i.test(html), "raw <script>");
    assert.ok(!/<img[^>]*onerror/i.test(html) && !/<b onmouseover/.test(html));
    assert.ok(!html.includes("javascript:"), "javascript: URL");
    assert.ok(!/ onerror\s*=/i.test(html.replace(/&lt;[^>]*?&gt;/g, "")), "attribute breakout via photo URL");
    assert.ok(html.includes("&lt;script&gt;alert(1)&lt;/script&gt;"));
    assert.ok(!html.includes('id="faq"'));
  });
}
ok("order: sectionOrder only; pinned always shown; unknown dropped", () => {
  assert.deepEqual(visibleKeys({ sectionOrder: ["about", "bogus", "hero"], hidden: ["hero", "about"] }, schema), ["hero", "contact"]);
  assert.deepEqual(visibleKeys({ sectionOrder: ["faq", "about"], hidden: [] }, schema), ["hero", "faq", "about", "contact"]);
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
  assert.equal(JSON.parse(h.jsonld)["@type"], "InsuranceAgency");
  assert.equal(JSON.parse(h.jsonld).telephone, "+919876543210");
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
    assert.deepEqual(Object.keys(sent.body), ["slug", "host", "name", "phone", "email", "interest", "message", "honeypot"]);
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
  const shared = ["app.js", "api.js", "i18n.js", "head.js", "form.js", "tokens.css", "styles/base.css", "styles/fonts.css", ...walk(dist + "sections/").map((f) => f.slice(dist.length))];
  for (const tpl of TEMPLATE_KEYS) {
    const files = [...shared, `templates/${tpl}/render.js`, `templates/${tpl}/style.css`];
    const buf = files.map((f) => readFileSync(dist + f));
    const raw = buf.reduce((a, b) => a + b.length, 0), gz = buf.reduce((a, b) => a + gzipSync(b).length, 0);
    console.log(`SIZE ${tpl}: ${(raw / 1024).toFixed(1)} KB raw, ${(gz / 1024).toFixed(1)} KB gzip`);
    assert.ok(raw < 50 * 1024, tpl + " over 50KB");
    const fontKB = (...n) => n.reduce((a, f) => a + statSync(dist + "fonts/" + f).size, 0) / 1024;
    console.log(`FONTS (woff2, fetched on demand): EN page ${fontKB("inter-latin-400-normal.woff2", "inter-latin-500-normal.woff2", "inter-latin-600-normal.woff2", "inter-latin-700-normal.woff2").toFixed(1)} KB max (latin 400-700), +${fontKB("noto-sans-devanagari-devanagari-400-normal.woff2", "noto-sans-devanagari-devanagari-500-normal.woff2", "noto-sans-devanagari-devanagari-600-normal.woff2").toFixed(1)} KB Devanagari for Hindi, preload ${fontKB("inter-latin-400-normal.woff2").toFixed(1)} KB`);
  }
} catch (e) { if (e.code === "ENOENT") console.log("SIZE skipped (run build first)"); else throw e; }
// hosting config (Cloudflare Workers static assets, DECISIONS #59)
import { existsSync } from "node:fs";
ok("wrangler.jsonc: SPA static assets only", () => {
  const raw = readFileSync(new URL("../web/wrangler.jsonc", import.meta.url), "utf8");
  const strip = raw.split("\n").filter((l) => !l.trim().startsWith("//")).join("\n");
  const w = JSON.parse(strip);
  assert.equal(w.name, "agentpro-sites"); assert.equal(w.assets.directory, "./dist");
  assert.equal(w.assets.not_found_handling, "single-page-application");
  assert.ok(!w.main && !w.routes, "static only, no routes yet");
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
console.log(`\n${n} checks passed`);
