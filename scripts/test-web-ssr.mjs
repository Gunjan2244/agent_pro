// Server-side rendering + SEO checks (DECISIONS #75): the Worker's pages, headers, robots.txt, sitemap.xml, the
// SSR/client parity of every template, semantic HTML, and hydrate.js. Run by scripts/test-web.mjs.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

export async function run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }) {
  const root = new URL("../", import.meta.url);
  const worker = (await import("../web/worker.js")).default;
  const { renderDocument, unavailableDocument } = await import("../web/ssr.js");
  const { SECURITY_HEADERS } = await import("../web/headers.js");
  const { TEMPLATES, familyOf } = await import("../web/templates/index.js");
  const { lastModified } = await import("../web/worker.js");
  const { headData, trimWords } = await import("../web/head.js");
  const { loadDictWith } = await import("../web/i18n.js");
  const registry = R("shared/templates/index.json");
  const raw = (n) => R(`web/dev-fixtures/${n}.json`);
  const PUBLISHED = "2026-10-01T10:00:00.000Z";
  const row = (n = "sample-en", extra = {}) => ({ ...raw(n), published_at: PUBLISHED, ...extra });

  // ---- fake platform: ASSETS binding (real shared/ files + a shell), Supabase RPCs ----
  const SHELL = "<!doctype html><main id=root>SHELL</main>";
  const files = { "/config.js": 'window.AGENTPRO_CONFIG = {\n  SUPABASE_URL: "https://s.test",\n  SUPABASE_ANON_KEY: "anon",\n  SITE_BASE_URL: "https://sites.test"\n};\n' };
  const assetPaths = [];
  const env = { ASSETS: { fetch: async (req) => {
    const p = new URL(req.url || req).pathname; assetPaths.push(p);
    if (files[p]) return new Response(files[p]);
    if (p.startsWith("/shared/")) { try { return new Response(readFileSync(new URL("." + p, root))); } catch { return new Response("nf", { status: 404 }); } }
    return new Response(SHELL, { status: 200, headers: { "content-type": "text/html" } });
  } } };
  const realFetch = globalThis.fetch;
  let rpcs = [], reply = {};
  globalThis.fetch = async (u, o) => {
    const name = String(u).split("/rpc/")[1]; rpcs.push({ name, body: JSON.parse(o.body) });
    const r = reply[name]; if (typeof r === "function") return r(JSON.parse(o.body));
    return new Response(JSON.stringify(r === undefined ? null : r));
  };
  const get = (u, init = {}) => worker.fetch(new Request(u, init), env);
  const reset = () => { rpcs = []; assetPaths.length = 0; };
  const between = (html, a, b) => html.slice(html.indexOf(a) + a.length, html.indexOf(b, html.indexOf(a)));
  const meta = (html, attr, name) => (html.match(new RegExp(`<meta ${attr}="${name}" content="([^"]*)"`)) || [])[1];
  const ld = (html) => JSON.parse(between(html, '<script type="application/ld+json">', "</script>"));
  const bootOf = (html) => JSON.parse(between(html, '<script type="application/json" id="boot">', "</script>"));
  const unesc = (s) => s.replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">");

  const okA = async (name, fn) => { await fn(); ok(name, () => {}); }; // async body: awaited, then counted
  // ---- the page ----
  reply.get_public_site = [row()];
  reset();
  const res = await get("https://sites.test/a/demo/");
  const html = await res.text();
  const contactHtml = await (await get("https://sites.test/a/demo/contact")).text();
  rpcs = rpcs.slice(0, 1); // keep the Home request's RPC for the check below
  const site = fx("sample-en");
  ok("SSR /a/<slug>/: 200 html with the agent's content, lang, root marker, no-cache", () => {
    assert.equal(res.status, 200); assert.match(res.headers.get("content-type"), /^text\/html/); assert.equal(res.headers.get("cache-control"), "no-cache");
    assert.ok(html.startsWith("<!doctype html>") && html.includes('<html lang="en">'));
    assert.ok(html.includes('<main id="root" data-ssr>'));
    assert.ok(html.includes("Insurance made simple for your family") && html.includes("Asha Verma") && html.includes("tel:+919876543210"));
    assert.deepEqual(rpcs.map((r) => [r.name, r.body]), [["get_public_site", { slug: "demo" }]]);
  });
  ok("SSR head: title, description, canonical (with trailing slash), og:*, twitter:card, robots, manifest/icon/apple links, font preload, css, scripts", () => {
    assert.ok(html.includes("<title>Asha Verma — Insurance Advisor, Pune</title>"));
    assert.equal(meta(html, "name", "description"), site.content.hero.subheadline);
    assert.ok(html.includes('<link rel="canonical" href="https://sites.test/a/demo/">'));
    assert.equal(meta(html, "property", "og:title"), "Asha Verma — Insurance Advisor, Pune");
    assert.equal(meta(html, "property", "og:url"), "https://sites.test/a/demo/");
    assert.equal(meta(html, "property", "og:type"), "website"); assert.ok(meta(html, "property", "og:image"));
    assert.equal(meta(html, "name", "twitter:card"), "summary_large_image");
    assert.equal(meta(html, "name", "robots"), "index,follow");
    assert.ok(html.includes('<link rel="manifest" href="/manifest.webmanifest?s=demo">'));
    assert.ok(html.includes('<link rel="icon" href="/icons/indigo-192.png">') && html.includes('<link rel="apple-touch-icon" href="/icons/indigo-192.png">'));
    assert.equal(meta(html, "name", "apple-mobile-web-app-title"), "Asha Verma");
    assert.ok(html.includes('<link rel="preload" href="/fonts/inter-latin-400-normal.woff2" as="font" type="font/woff2" crossorigin>'));
    for (const css of ["/styles/fonts.css", "/tokens.css", "/styles/base.css", "/templates/classic/style.css"]) assert.ok(html.includes(`<link rel="stylesheet" href="${css}">`), css);
    assert.ok(html.includes('<script src="/config.js"></script>') && html.includes('<script type="module" src="/app.js"></script>'));
    assert.ok(html.indexOf('<script src="/config.js">') > html.indexOf("</main>"), "scripts after the content");
  });
  ok("SSR #boot: slug, host, locale, template, page, name, phone", () => assert.deepEqual(bootOf(html), { slug: "demo", host: null, locale: "en", template: "classic", page: null, name: "Asha Verma", phone: "+91 98765 43210" === "" ? "" : site.profile.phone || site.content.contact.phone }));
  ok("SSR JSON-LD parses: InsuranceAgency + Person (linked), FAQPage, Service per interest; no Offer, no insurer name", () => {
    const g = ld(html)["@graph"], by = (t) => g.filter((x) => x["@type"] === t);
    const agency = by("InsuranceAgency")[0], person = by("Person")[0];
    assert.equal(agency.name, "Asha Verma"); assert.equal(agency.telephone, "+919876543210"); assert.equal(agency.areaServed.name, "Pune");
    assert.equal(agency.address.addressLocality, "Pune"); assert.deepEqual(agency.knowsLanguage, ["English", "Hindi", "Marathi"]);
    assert.equal(agency.openingHours, "Mon–Sat, 10am–6pm"); assert.ok(agency.image);
    assert.equal(person.name, "Asha Verma"); assert.equal(person.worksFor["@id"], agency["@id"]); assert.equal(agency.founder["@id"], person["@id"]);
    // the FAQ is on the Contact page (DECISIONS #122): FAQPage only there
    assert.equal(by("FAQPage").length, 0, "no FAQPage on Home");
    const faq = ld(contactHtml)["@graph"].find((x) => x["@type"] === "FAQPage");
    assert.equal(faq["@id"], "https://sites.test/a/demo/contact#faq");
    assert.equal(faq.mainEntity.length, site.content.faq.items.length); assert.equal(faq.mainEntity[0].name, site.content.faq.items[0].question);
    assert.equal(faq.mainEntity[0].acceptedAnswer.text, site.content.faq.items[0].answer);
    const svc = by("Service").map((s) => s.name);
    assert.deepEqual(svc, ["term", "health", "child", "pension"].map((k) => dicts.en[catalog.interests.find((i) => i.key === k).labelKey]));
    assert.equal(by("Service")[0].provider["@id"], agency["@id"]);
    assert.ok(!/"Offer"|"AggregateOffer"|\bLIC\b|Life Insurance Corporation/i.test(JSON.stringify(g)));
  });
  ok("headers: the Worker's security headers equal the /* block of _headers (minus Cache-Control)", () => {
    const text = readFileSync(new URL("web/_headers", root), "utf8");
    const block = text.split(/\n\s*\n/).find((b) => /^\/\*$/m.test(b));
    const fromFile = Object.fromEntries(block.split("\n").slice(1).filter((l) => /^\s+\S/.test(l)).map((l) => l.trim().match(/^([^:]+):\s*(.*)$/).slice(1)).filter(([k]) => k !== "Cache-Control"));
    assert.deepEqual(fromFile, SECURITY_HEADERS);
    for (const [k, v] of Object.entries(SECURITY_HEADERS)) assert.equal(res.headers.get(k), v, k);
  });
  await okA("Last-Modified from published_at; If-Modified-Since -> 304 (no body); older -> 200", async () => {
    assert.equal(res.headers.get("last-modified"), new Date(PUBLISHED).toUTCString());
    const lm = res.headers.get("last-modified");
    const nm = await get("https://sites.test/a/demo/", { headers: { "if-modified-since": lm } });
    assert.equal(nm.status, 304); assert.equal(await nm.text(), ""); assert.equal(nm.headers.get("content-security-policy"), SECURITY_HEADERS["Content-Security-Policy"]);
    const old = await get("https://sites.test/a/demo/", { headers: { "if-modified-since": new Date(Date.parse(PUBLISHED) - 60000).toUTCString() } });
    assert.equal(old.status, 200);
    reply.get_public_site = [{ ...row(), published_at: undefined }];
    const none = await get("https://sites.test/a/demo/", { headers: { "if-modified-since": lm } });
    assert.equal(none.status, 200); assert.equal(none.headers.get("last-modified"), null);
    reply.get_public_site = [row()];
  });
  ok("lastModified = max(published_at, BUILD_TIME): a deploy after the last publish moves it forward; an older build doesn't", () => {
    const site = { published_at: PUBLISHED };
    assert.equal(lastModified(site, { BUILD_TIME: "2026-10-05T00:00:00Z" }), Date.parse("2026-10-05T00:00:00Z"));
    assert.equal(lastModified(site, { BUILD_TIME: "2026-09-01T00:00:00Z" }), Date.parse(PUBLISHED));
    assert.equal(lastModified(site, { BUILD_TIME: "" }), Date.parse(PUBLISHED)); assert.equal(lastModified(site, { BUILD_TIME: "garbage" }), Date.parse(PUBLISHED));
    assert.equal(lastModified({ published_at: null }, { BUILD_TIME: "2026-10-05T00:00:00Z" }), null, "no published_at, no Last-Modified");
  });
  await okA("Worker: BUILD_TIME after published_at -> Last-Modified is the build time, and an If-Modified-Since from before the deploy gets 200", async () => {
    const BUILT = "2026-10-05T08:30:00Z";
    const cfgText = files["/config.js"].replace("\n};", `,\n  BUILD_TIME: "${BUILT}"\n};`);
    const env2 = { ASSETS: { fetch: async (req) => { const p = new URL(req.url || req).pathname; return p === "/config.js" ? new Response(cfgText) : env.ASSETS.fetch(req); } } };
    const r = await worker.fetch(new Request("https://sites.test/a/demo/"), env2);
    assert.equal(r.headers.get("last-modified"), new Date(BUILT).toUTCString());
    const before = await worker.fetch(new Request("https://sites.test/a/demo/", { headers: { "if-modified-since": new Date(PUBLISHED).toUTCString() } }), env2);
    assert.equal(before.status, 200, "a browser copy from before the deploy is refreshed");
    const after = await worker.fetch(new Request("https://sites.test/a/demo/", { headers: { "if-modified-since": new Date(BUILT).toUTCString() } }), env2);
    assert.equal(after.status, 304);
    const old = cfgText.replace(BUILT, "2026-09-01T00:00:00Z");
    const env3 = { ASSETS: { fetch: async (req) => { const p = new URL(req.url || req).pathname; return p === "/config.js" ? new Response(old) : env.ASSETS.fetch(req); } } };
    assert.equal((await worker.fetch(new Request("https://sites.test/a/demo/"), env3)).headers.get("last-modified"), new Date(PUBLISHED).toUTCString(), "an older build: published_at wins");
  });
  await okA("301: /a/<slug> -> /a/<slug>/ keeping the query, without calling the backend", async () => {
    reset();
    const r = await get("https://sites.test/a/demo?x=1&install=1");
    assert.equal(r.status, 301); assert.equal(r.headers.get("location"), "https://sites.test/a/demo/?x=1&install=1"); assert.equal(rpcs.length, 0);
    const r2 = await get("https://sites.test/a/demo", { method: "HEAD" });
    assert.equal(r2.status, 301);
  });
  await okA("HEAD: same status and headers, no body", async () => {
    const h = await get("https://sites.test/a/demo/", { method: "HEAD" });
    assert.equal(h.status, 200); assert.equal(await h.text(), ""); assert.equal(h.headers.get("cache-control"), "no-cache");
  });
  await okA("404 for an unknown, unpublished or lapsed site: SSR unavailable page, noindex, no agent data", async () => {
    reply.get_public_site = null; reset();
    const r = await get("https://sites.test/a/lapsed/", { headers: { "accept-language": "hi-IN,hi;q=0.9" } });
    const t = await r.text();
    assert.equal(r.status, 404); assert.equal(meta(t, "name", "robots"), "noindex"); assert.match(r.headers.get("content-type"), /^text\/html/);
    assert.ok(t.includes("<h1>") && t.includes(dicts.hi["site.not_available_title"]) && t.includes('<html lang="hi">'));
    assert.ok(!t.includes("<script") && !t.includes('data-ssr') && !t.includes("Asha"), "no scripts, no agent data");
    assert.equal(r.headers.get("x-frame-options"), "DENY");
    reset();
    const bad = await Promise.all(["https://sites.test/a/Bad%20Slug/", "https://sites.test/a/UPPER/"].map((u) => get(u)));
    assert.deepEqual(bad.map((b) => b.status), [404, 404]); assert.equal(rpcs.length, 0);
    assert.equal((await get("https://sites.test/a/lapsed/", { method: "HEAD" })).status, 404);
    reply.get_public_site = [row()];
  });
  await okA("backend error or missing config -> the static shell is served (the browser retries)", async () => {
    reply.get_public_site = () => new Response("{}", { status: 500 }); reset();
    const r = await get("https://sites.test/a/demo/");
    assert.equal(r.status, 200); assert.equal(await r.text(), SHELL);
    reply.get_public_site = [{ ...row(), template_key: "classic", published_content: "broken" }];
    assert.equal((await get("https://sites.test/a/demo/")).status, 404, "an unusable payload is a miss, not a crash");
    const bare = { ASSETS: { fetch: async (req) => (new URL(req.url || req).pathname === "/config.js" ? new Response("nf", { status: 404 }) : new Response(SHELL)) } };
    assert.equal(await (await worker.fetch(new Request("https://sites.test/a/other/"), bare)).text(), SHELL, "no config.js: shell");
    reply.get_public_site = [row()];
  });
  await okA("?preview=1 and ?mock are never SSR'd (shell, no RPC); ?install=1 is SSR'd with noindex", async () => {
    reset();
    for (const u of ["https://sites.test/a/demo/?preview=1", "https://sites.test/?preview=1", "https://sites.test/a/demo/?mock=1"]) assert.equal(await (await get(u)).text(), SHELL, u);
    assert.equal(rpcs.length, 0);
    const i = await (await get("https://sites.test/a/demo/?install=1")).text();
    assert.equal(meta(i, "name", "robots"), "noindex"); assert.ok(i.includes('data-ssr') && i.includes("Asha Verma"));
    assert.ok(i.includes('<link rel="canonical" href="https://sites.test/a/demo/">'), "canonical stays the clean URL");
  });
  await okA("platform pages and non-site paths pass through to ASSETS: / on the primary host, /terms, /privacy, fonts; no RPC", async () => {
    reset();
    for (const u of ["https://sites.test/", "https://sites.test/terms", "https://sites.test/privacy", "https://sites.test/fonts/x.woff2"]) assert.equal(await (await get(u)).text(), SHELL, u);
    assert.equal(rpcs.length, 0);
    const post = await get("https://sites.test/a/demo/", { method: "POST" });
    assert.equal(await post.text(), SHELL, "POST goes to assets untouched");
    const s = await get("https://sites.test/?s=demo");
    assert.ok((await s.text()).includes('<link rel="canonical" href="https://sites.test/a/demo/">'), "?s=<slug> renders with the clean canonical");
  });
  await okA("custom domain: / uses get_public_site_by_host, canonical https://<host>/, manifest without ?s, other paths pass through", async () => {
    reply.get_public_site_by_host = [row("sample-hi", { canonical_host: null })]; reset();
    const r = await get("https://asha.in/");
    const t = await r.text();
    assert.equal(r.status, 200); assert.deepEqual(rpcs.map((x) => [x.name, x.body]), [["get_public_site_by_host", { host: "asha.in" }]]);
    assert.ok(t.includes('<link rel="canonical" href="https://asha.in/">') && t.includes('<link rel="manifest" href="/manifest.webmanifest">') && t.includes('<html lang="hi">'));
    assert.deepEqual(bootOf(t).host, "asha.in"); assert.equal(bootOf(t).slug, null);
    assert.ok(t.includes('href="/privacy"'), "footer links to /privacy");
    reset();
    assert.equal(await (await get("https://asha.in/a/whatever")).text(), SHELL); assert.equal(rpcs.length, 0);
    reply.get_public_site_by_host = null;
    assert.equal((await get("https://asha.in/")).status, 404);
  });
  await okA("privacy page: SSR with the data-request form, indexable, canonical /a/<slug>/privacy; custom domain /privacy", async () => {
    reset();
    const r = await get("https://sites.test/a/demo/privacy");
    const t = await r.text();
    assert.equal(r.status, 200); assert.ok(t.includes('data-ssr') && t.includes('id="req"') && (t.match(/<h1>/g) || []).length === 1);
    assert.ok(t.includes("<title>" + dicts.en["site.privacy_page_title"].replace("{name}", "Asha Verma") + "</title>"));
    assert.ok(t.includes('<link rel="canonical" href="https://sites.test/a/demo/privacy">') && meta(t, "name", "robots") === "index,follow");
    assert.equal(bootOf(t).page, "privacy"); assert.ok(t.includes('<a href="/a/demo/">'), "back link has the trailing slash");
    reply.get_public_site_by_host = [row("sample-hi")];
    const d = await (await get("https://asha.in/privacy")).text();
    assert.ok(d.includes('<link rel="canonical" href="https://asha.in/privacy">') && bootOf(d).page === "privacy");
    assert.equal(rpcs.filter((x) => x.name === "get_public_site").length, 1);
  });

  // ---- robots.txt / sitemap.xml ----
  const robots = await (await get("https://sites.test/robots.txt")).text();
  ok("robots.txt: allows /, disallows ?preview/?install/?mock, points at the sitemap of the requested origin", () => {
    assert.ok(/^User-agent: \*$/m.test(robots) && /^Allow: \/$/m.test(robots));
    for (const p of ["preview", "install", "mock"]) assert.ok(robots.includes(`Disallow: /*?${p}`) && robots.includes(`Disallow: /*&${p}`), p);
    assert.ok(robots.includes("Sitemap: https://sites.test/sitemap.xml"));
  });
  ok("robots.txt on a custom domain names that domain's sitemap; POST -> 405", async () => {
    assert.ok((await (await get("https://asha.in/robots.txt")).text()).includes("Sitemap: https://asha.in/sitemap.xml"));
    assert.equal((await get("https://sites.test/robots.txt", { method: "POST" })).status, 405);
  });
  await okA("sitemap.xml (primary host): a sitemap index, <SITE_BASE_URL>/a/<slug>/sitemap.xml for every list_public_sites() row, lastmod, bad slugs and XML escaped", async () => {
    reply.list_public_sites = [{ slug: "ramesh-kumar-pune", published_at: PUBLISHED }, { slug: "asha", published_at: null }, { slug: "bad<slug>", published_at: PUBLISHED }];
    reset();
    const r = await get("https://sites.test/sitemap.xml");
    const x = await r.text();
    assert.equal(r.status, 200); assert.match(r.headers.get("content-type"), /^application\/xml/);
    assert.deepEqual(rpcs.map((q) => q.name), ["list_public_sites"]);
    assert.ok(x.includes('<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">'));
    assert.ok(x.includes("<sitemap><loc>https://sites.test/a/ramesh-kumar-pune/sitemap.xml</loc><lastmod>2026-10-01T10:00:00.000Z</lastmod></sitemap>"));
    assert.ok(x.includes("<sitemap><loc>https://sites.test/a/asha/sitemap.xml</loc></sitemap>")); assert.ok(!x.includes("bad"));
    reply.list_public_sites = () => new Response("{}", { status: 500 });
    const f = await get("https://sites.test/sitemap.xml");
    assert.equal(f.status, 503); assert.equal(f.headers.get("cache-control"), "no-store");
    reply.list_public_sites = { not: "an array" };
    assert.equal((await get("https://sites.test/sitemap.xml")).status, 503);
  });
  await okA("/a/<slug>/sitemap.xml: every page the site has (Home, Insurance, each plan type, About, Contact) and its privacy page; unknown site 404; bad slug 404 without RPC", async () => {
    reply.get_public_site = [row()]; reset();
    const r = await get("https://sites.test/a/demo/sitemap.xml");
    const x = await r.text();
    assert.equal(r.status, 200); assert.match(r.headers.get("content-type"), /^application\/xml/);
    const locs = [...x.matchAll(/<loc>([^<]+)<\/loc><lastmod>2026-10-01T10:00:00.000Z<\/lastmod>/g)].map((m) => m[1].replace("https://sites.test/a/demo/", ""));
    assert.deepEqual(locs, ["", "insurance", "insurance/term", "insurance/health", "insurance/child", "insurance/pension", "insurance/endowment", "about", "contact", "privacy"]);
    // every listed page is served (200) and has its own canonical
    for (const p of locs) {
      const pr = await get("https://sites.test/a/demo/" + p);
      assert.equal(pr.status, 200, p);
      assert.ok((await pr.text()).includes(`<link rel="canonical" href="https://sites.test/a/demo/${p}">`), p);
    }
    reply.get_public_site = null; assert.equal((await get("https://sites.test/a/gone/sitemap.xml")).status, 404);
    reset(); assert.equal((await get("https://sites.test/a/Bad%20Slug/sitemap.xml")).status, 404); assert.equal(rpcs.length, 0);
    reply.get_public_site = [row()];
  });
  await okA("sitemap.xml (custom domain): every page and /privacy with lastmod = published_at; unknown domain 404; backend error 503", async () => {
    reply.get_public_site_by_host = [row("sample-hi")]; reset();
    const x = await (await get("https://asha.in/sitemap.xml")).text();
    assert.ok(x.includes("<loc>https://asha.in/</loc><lastmod>2026-10-01T10:00:00.000Z</lastmod>") && x.includes("<loc>https://asha.in/privacy</loc><lastmod>2026-10-01T10:00:00.000Z</lastmod>"));
    for (const p of ["insurance", "about", "contact"]) assert.ok(x.includes(`<loc>https://asha.in/${p}</loc>`), p);
    assert.deepEqual(rpcs.map((q) => q.name), ["get_public_site_by_host"]);
    reply.get_public_site_by_host = null; assert.equal((await get("https://asha.in/sitemap.xml")).status, 404);
    reply.get_public_site_by_host = () => new Response("{}", { status: 500 });
    assert.equal((await get("https://asha.in/sitemap.xml")).status, 503);
  });
  globalThis.fetch = realFetch;

  // ---- SSR output equals the client render, per template and locale; semantic HTML ----
  ok("templates/index.js maps exactly the templates that have a render.js", () => assert.deepEqual(Object.keys(TEMPLATES).sort(), TEMPLATE_KEYS.slice().sort()));
  const IMAGES = R("web/images/manifest.json");
  const shared = { schema, catalog, templates: registry, images: IMAGES };
  const notices = R("shared/generated/privacy-notices.json");
  const docFor = (siteObj, loc, key) => renderDocument({ site: { ...siteObj, template_key: key }, target: { slug: "demo" }, url: new URL("https://sites.test/a/demo/"), dict: dicts[loc], shared, notices, config: {} });
  const PARITY = [...LOCALE_KEYS.map((l) => ["sample-" + l, l]), ["sample-offers", "en"], ["sample-seeded", "en"]];
  for (const key of TEMPLATE_KEYS) for (const [fixture, loc] of PARITY) {
    const siteObj = fx(fixture);
    const d = docFor(siteObj, loc, key);
    ok(`${key}/${fixture}: SSR #root inner HTML equals render(); one <h1>, an <h2> per section, landmarks`, () => {
      const inner = between(d, '<main id="root" data-ssr>', '</main>\n<script type="application/json" id="boot">');
      const tokens = { accent: catalog.accentSwatches.find((x) => x.key === siteObj.accent).hex, schema, catalog, notice: notices.notices.enquiry_form, privacyHref: "/a/demo/privacy", family: familyOf(registry, key), images: IMAGES, seed: "demo" };
      assert.equal(inner, TEMPLATES[key]({ ...siteObj, template_key: key }, makeT(dicts[loc]), tokens));
      assert.equal((inner.match(/<h1[ >]/g) || []).length, 1, "exactly one h1");
      const sections = [...inner.matchAll(/<section [^>]*\bid="([a-z]+)"/g)].map((m) => m[1]);
      assert.ok(sections.length >= 5); // the seeded site: about, services, highlights, faq, contact
      assert.equal((inner.match(/<h2[ >]/g) || []).length, sections.length, "an h2 per section");
      for (const sec of inner.split(/(?=<section )/).slice(1)) assert.ok(/<h2[ >]/.test(sec.split("</section>")[0]), "section without h2");
      assert.ok(/<header [^>]*id="hero"/.test(inner) && /<footer /.test(inner));
      assert.ok(/^<div class="tpl tpl-/.test(inner) && inner.endsWith("</div>") && inner.indexOf("<header") > 0 && inner.indexOf("</footer>") < inner.length, ".tpl wraps them");
      assert.equal((inner.match(/<nav /g) || []).length, inner.includes("callbar") ? 1 : 0);
      assert.ok(inner.includes(`tpl-${key} fam-${familyOf(registry, key)}`));
      assert.ok(d.indexOf("<h1") < d.indexOf("<h2"));
    });
  }
  for (const n of ["sample-empty", "sample-xss"]) ok(`${n}: hidden/escaped content still gives one <h1> and an <h2> per section`, () => {
    const inner = between(docFor(fx(n), "en", TEMPLATE_KEYS[0]), '<main id="root" data-ssr>', '</main>\n<script type="application/json" id="boot">');
    assert.equal((inner.match(/<h1[ >]/g) || []).length, 1);
    assert.equal((inner.match(/<h2[ >]/g) || []).length, (inner.match(/<section /g) || []).length);
  });
  ok("hero: an empty headline falls back to the agent's name, never an empty <h1>", () => {
    const s = fx("sample-en"); s.content = { ...s.content, hero: { ...s.content.hero, headline: "" } };
    assert.ok(/<h1>Asha Verma<\/h1>/.test(docFor(s, "en", TEMPLATE_KEYS[0])));
  });
  ok("SSR escaping: a hostile name/headline can't break out of #boot, JSON-LD, <title> or attributes", () => {
    const d = docFor(fx("sample-xss"), "en", TEMPLATE_KEYS[0]);
    const scripts = d.match(/<\/script>/g) || [];
    assert.equal(scripts.length, 4, "ld+json, boot, config.js, app.js");
    assert.ok(!/<script(?![^>]*(?:type="application\/(?:ld\+)?json"|src=|type="module"))/i.test(d));
    assert.ok(bootOf(d).name.includes("<")); assert.ok(typeof ld(d)["@graph"][0].name === "string");
    assert.ok(!/<title>[^<]*<[^/]/.test(d) && !d.includes("<script>alert") && !/<img[^>]*onerror/i.test(d), "no injected tags");
    const body = between(d, "<body>", "</body>").replace(/<script[\s\S]*?<\/script>/g, "").replace(/&lt;[^>]*?&gt;/g, "");
    assert.ok(!/<[a-z][^>]*\son\w+=/i.test(body), "no event-handler attributes");
  });
  ok("unavailableDocument: noindex 404 shell in both locales, generic error variant, no scripts", () => {
    for (const l of LOCALE_KEYS) {
      const d = unavailableDocument({ dict: dicts[l], locale: l });
      assert.equal(meta(d, "name", "robots"), "noindex"); assert.ok(d.includes(`<html lang="${l}">`) && !d.includes("<script") && d.includes(dicts[l]["site.not_available_text"]));
    }
    assert.ok(unavailableDocument({ dict: dicts.en, generic: true }).includes(dicts.en["site.form_error_generic"]));
  });

  // ---- head data (SEO) ----
  ok("head: description = subheadline, else the bio, cut to 155 characters on a word boundary", () => {
    const base = fx("sample-en");
    const long = "word ".repeat(80);
    const a = headData({ ...base, content: { ...base.content, hero: { ...base.content.hero, subheadline: long } } }, { origin: "https://x" });
    assert.ok(a.description.length <= 155 && a.description.endsWith("…") && !/\sword$|wor…$/.test(a.description) && /word…$/.test(a.description), a.description);
    const b = headData({ ...base, content: { ...base.content, hero: { ...base.content.hero, subheadline: "" } } }, { origin: "https://x" });
    assert.equal(b.description, trimWords(base.content.about.bio)); assert.ok(b.description.startsWith("I have helped families in Pune"));
    assert.equal(trimWords("short text"), "short text"); assert.equal(headData(base, { origin: "https://x", noindex: true }).robots, "noindex");
  });
  ok("head JSON-LD: FAQPage/Service only when their sections show; every `<` escaped; openingHours only when sane", () => {
    const base = fx("sample-en"), tk = { origin: "https://x", path: "/a/d/", t: makeT(dicts.en), catalog, schema };
    const hid = headData({ ...base, content: { ...base.content, hidden: ["faq", "services"] } }, tk);
    assert.deepEqual(JSON.parse(hid.jsonld)["@graph"].map((x) => x["@type"]), ["InsuranceAgency", "Person"]);
    const long = headData({ ...base, content: { ...base.content, contact: { ...base.content.contact, officeHours: "x".repeat(101) } } }, tk);
    assert.equal(JSON.parse(long.jsonld)["@graph"][0].openingHours, undefined);
    const x = headData(fx("sample-xss"), tk); assert.ok(!x.jsonld.includes("<"));
    assert.equal(JSON.parse(headData(base, { origin: "https://x", path: "/a/d/" }).jsonld)["@graph"].filter((e) => e["@type"] === "Service").length, 0, "no Service without labels");
  });
  ok("i18n: loadDictWith uses the given fetcher (no root-relative fetch) and rejects a failing response", async () => {
    const seen = [];
    const d = await loadDictWith(async (p) => { seen.push(p); return new Response(JSON.stringify({ a: "b" })); }, "hi");
    assert.deepEqual(d, { a: "b" }); assert.deepEqual(seen, ["/shared/i18n/hi.json"]);
    await loadDictWith(async (p) => { seen.push(p); return new Response("{}"); }, "../etc"); assert.equal(seen[1], "/shared/i18n/en.json");
    await assert.rejects(loadDictWith(async () => new Response("", { status: 404 }), "en"));
  });

  // ---- reveal.js: below-the-fold sections fade in; never hides anything above the fold or under reduced motion ----
  {
    const { revealSections } = await import("../web/reveal.js");
    const mkEl = (top) => ({ classes: new Set(), getBoundingClientRect: () => ({ top }), classList: { add(c) { this.owner.classes.add(c); } } });
    const run = (reduce, hasIO = true) => {
      const els = [mkEl(100), mkEl(900), mkEl(1800)];
      els.forEach((e) => { e.classList.owner = e; });
      const styles = [], observed = [];
      const doc = { querySelectorAll: () => els, createElement: () => ({}), head: { appendChild: (n) => styles.push(n) } };
      let cb;
      const win = { innerHeight: 800, matchMedia: () => ({ matches: reduce }), IntersectionObserver: hasIO ? class { constructor(f) { cb = f; } observe(e) { observed.push(e); } unobserve() {} } : undefined };
      revealSections({ doc, win });
      return { els, styles, observed, fire: (e) => cb([{ isIntersecting: true, target: e }]) };
    };
    ok("reveal.js hides only sections below the fold, then reveals them on entry", () => {
      const r = run(false);
      assert.ok(!r.els[0].classes.has("rv"), "in-view section stays visible");
      assert.ok(r.els[1].classes.has("rv") && r.els[2].classes.has("rv"));
      assert.equal(r.styles.length, 1);
      assert.match(r.styles[0].textContent, /prefers-reduced-motion:no-preference/);
      r.fire(r.els[1]);
      assert.ok(r.els[1].classes.has("rv-in") && !r.els[2].classes.has("rv-in"));
    });
    ok("reveal.js does nothing under reduced motion or without IntersectionObserver", () => {
      for (const r of [run(true), run(false, false)]) { assert.equal(r.styles.length, 0); assert.ok(r.els.every((e) => !e.classes.has("rv"))); }
    });
  }

  // ---- hydrate.js: wires behaviour, never renders ----
  {
    const { hydrate, wireChips } = await import("../web/hydrate.js");
    const hsrc = readFileSync(new URL("web/hydrate.js", root), "utf8");
    ok("hydrate.js never renders: no template/section import, no render() call, no innerHTML/outerHTML write", () => {
      assert.ok(!/templates\/|sections\/index|\.render\(|innerHTML|outerHTML|insertAdjacentHTML/.test(hsrc));
    });
    const bootJson = JSON.stringify({ slug: "demo", host: null, locale: "en", template: "classic", page: null, name: "Asha Verma", phone: "9876543210" });
    const mk = () => {
      const log = { innerHTML: 0, views: [], listeners: {} };
      const sel = { value: "", options: [{ value: "" }, { value: "term" }, { value: "health" }] };
      const form = { addEventListener: (e, f) => { log.listeners[e] = f; }, querySelector: () => null };
      const rootEl = { set innerHTML(v) { log.innerHTML++; }, querySelector: (s) => (s === "#enq" ? form : s === "#f-interest" ? sel : null), addEventListener: (e, f) => { log.click = f; } };
      const doc = { getElementById: (id) => (id === "root" ? rootEl : { textContent: bootJson }) };
      return { log, sel, doc, rootEl };
    };
    const rf = globalThis.fetch;
    globalThis.fetch = async (u) => new Response(readFileSync(new URL("." + u, root)));
    const backend = (log) => ({ recordSiteView: async (s, h) => { log.views.push([s, h]); }, submitEnquiry: async () => ({ status: "ok" }) });
    let m = mk();
    await hydrate({ doc: m.doc, loc: { search: "" }, backend: backend(m.log) });
    ok("hydrate: wires the form and the chips, records one view, writes no markup", () => {
      assert.equal(m.log.innerHTML, 0); assert.ok(m.log.listeners.submit && m.log.listeners.focusout, "wireForm attached");
      assert.deepEqual(m.log.views, [["demo", undefined]]);
      m.log.click({ target: { closest: () => ({ getAttribute: () => "health" }) } });
      assert.equal(m.sel.value, "health");
      m.log.click({ target: { closest: () => ({ getAttribute: () => "bogus" }) } });
      assert.equal(m.sel.value, "health", "unknown interest ignored");
      m.log.click({ target: { closest: () => null } });
    });
    m = mk();
    await hydrate({ doc: m.doc, loc: { search: "?install=1" }, backend: backend(m.log) });
    ok("hydrate ?install=1: no view is recorded", () => assert.deepEqual(m.log.views, []));
    ok("wireChips: a chip with no matching option leaves the select alone", () => {
      const m2 = mk(); wireChips(m2.rootEl); m2.log.click({ target: { closest: () => ({ getAttribute: () => "pension" }) } }); assert.equal(m2.sel.value, "");
    });
    globalThis.fetch = rf;
  }
}
