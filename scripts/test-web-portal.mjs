// Customer portal checks (DECISIONS #96-#100): route.js, cookies.js, the Worker's /my routes, robots, footer link, headers,
// escaping and the CSS budget. Written against docs contract C/D. Run by scripts/test-web.mjs.
import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { formatDate } from "../web/sections/offer.js";

export async function run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }) {
  const formatDateFor = (iso, loc) => formatDate(iso, loc);
  const root = new URL("../", import.meta.url);
  const fails = [];
  // A check that records its failure instead of aborting, so one run reports every result.
  const check = async (name, fn) => {
    try { await fn(); ok(name, () => {}); } catch (e) { fails.push(name); console.log("FAIL", name, "\n     ", String(e && e.message).split("\n").slice(0, 6).join("\n      ")); }
  };
  const pending = (name, why) => console.log("PENDING", name, "-", why);

  const { resolveTarget, portalLinks } = await import("../web/route.js");
  const { COOKIE_NAME, parseCookies, portalToken, portalCookie, clearPortalCookie, inviteToken, inviteCookie, clearInviteCookie } = await import("../web/cookies.js");
  const { SECURITY_HEADERS } = await import("../web/headers.js");
  const { renderDocument } = await import("../web/ssr.js");
  const worker = (await import("../web/worker.js")).default;

  // ---------- route.js ----------
  const P = "sites.test";
  const rt = (u) => { const x = new URL(u); return resolveTarget(x, x.searchParams, P); };
  await check("route: primary host /a/x/my, /my/, /my/login, /start, /ask, /request, /logout, /sw.js -> portal targets", () => {
    for (const [path, pp] of [["/a/x/my", ""], ["/a/x/my/", ""], ["/a/x/my/login", "login"], ["/a/x/my/start", "start"], ["/a/x/my/ask", "ask"], ["/a/x/my/request", "request"], ["/a/x/my/logout", "logout"], ["/a/x/my/sw.js", "sw.js"]]) {
      assert.deepEqual(rt(`https://${P}${path}`), { slug: "x", page: "portal", portalPath: pp }, path);
    }
  });
  await check("route: unknown portal subpath -> portalPath null; /a/x/myfoo is not the portal", () => {
    assert.equal(rt(`https://${P}/a/x/my/zzz`).portalPath, null); assert.equal(rt(`https://${P}/a/x/my/login/extra`).portalPath, null);
    assert.equal(rt(`https://${P}/a/x/my/zzz`).page, "portal");
    const f = rt(`https://${P}/a/x/myfoo`); assert.notEqual(f && f.page, "portal");
    assert.equal(rt(`https://${P}/a/x/privacy`).page, "privacy"); assert.equal(rt(`https://${P}/a/x/`).page, undefined);
  });
  await check("route: custom host /my, /my/, /my/login, unknown -> null, /myfoo not portal, / and /privacy unchanged", () => {
    assert.deepEqual(rt("https://asha.in/my"), { host: "asha.in", page: "portal", portalPath: "" });
    assert.deepEqual(rt("https://asha.in/my/"), { host: "asha.in", page: "portal", portalPath: "" });
    assert.deepEqual(rt("https://asha.in/my/login"), { host: "asha.in", page: "portal", portalPath: "login" });
    assert.equal(rt("https://asha.in/my/nope").portalPath, null);
    assert.notEqual(rt("https://asha.in/myfoo").page, "portal");
    assert.equal(rt("https://asha.in/privacy").page, "privacy"); assert.equal(rt("https://asha.in/").page, undefined);
  });
  await check("route: portalLinks for primary host and custom host", () => {
    assert.deepEqual(portalLinks({ slug: "asha" }), { base: "/a/asha/my", home: "/a/asha/my", login: "/a/asha/my/login", start: "/a/asha/my/start", ask: "/a/asha/my/ask", request: "/a/asha/my/request", logout: "/a/asha/my/logout", sw: "/a/asha/my/sw.js", site: "/a/asha/", privacy: "/a/asha/privacy" });
    assert.deepEqual(portalLinks({ host: "asha.in" }), { base: "/my", home: "/my", login: "/my/login", start: "/my/start", ask: "/my/ask", request: "/my/request", logout: "/my/logout", sw: "/my/sw.js", site: "/", privacy: "/privacy" });
  });

  // ---------- cookies.js ----------
  const TOK = "Abc_def-0123456789ABCDEFghijklmnopqrstuv".slice(0, 43);
  await check("cookies: COOKIE_NAME and parseCookies", () => {
    assert.equal(COOKIE_NAME, "ap_portal");
    assert.deepEqual(parseCookies("a=1; b=two=2;  c = 3 ;junk"), { a: "1", b: "two=2", c: "3" });
    assert.deepEqual(parseCookies(""), {}); assert.deepEqual(parseCookies(null), {});
    assert.equal(parseCookies("a=1; a=2").a, "1");
  });
  await check("cookies: portalToken accepts base64url 20-128, rejects malformed or missing", () => {
    assert.equal(portalToken(`x=1; ap_portal=${TOK}`), TOK);
    for (const bad of ["", "ap_portal=", "ap_portal=short", "ap_portal=" + "a".repeat(129), "ap_portal=" + "a".repeat(30) + "!", "other=" + TOK]) assert.equal(portalToken(bad), null, bad);
    assert.equal(portalToken(undefined), null);
  });
  await check("cookies: Set-Cookie has exactly HttpOnly; Secure; SameSite=Lax, Path scoped to the base", () => {
    assert.equal(portalCookie(TOK, "/a/asha/my", 7776000.9), `ap_portal=${TOK}; Path=/a/asha/my; Max-Age=7776000; HttpOnly; Secure; SameSite=Lax`);
    const c = clearPortalCookie("/my");
    assert.equal(c, "ap_portal=; Path=/my; Max-Age=0; HttpOnly; Secure; SameSite=Lax");
    assert.ok(!/Domain=/i.test(portalCookie(TOK, "/my", 5)));
    assert.match(portalCookie(TOK, "/my", -5), /Max-Age=0;/);
  });

  await check("cookies: invite cookie is HttpOnly/Secure/Lax, 15 min, scoped to the start path; inviteToken parses it", () => {
    assert.equal(inviteCookie(TOK, "/a/asha/my/start"), `ap_invite=${TOK}; Path=/a/asha/my/start; Max-Age=900; HttpOnly; Secure; SameSite=Lax`);
    assert.equal(clearInviteCookie("/my/start"), "ap_invite=; Path=/my/start; Max-Age=0; HttpOnly; Secure; SameSite=Lax");
    assert.equal(inviteToken(`a=1; ap_invite=${TOK}`), TOK); assert.equal(inviteToken(`ap_portal=${TOK}`), null);
    for (const bad of ["", "ap_invite=short", "ap_invite=" + "a".repeat(30) + "!"]) assert.equal(inviteToken(bad), null, bad);
  });

  // ---------- fake platform ----------
  const SHELL = "<!doctype html><main id=root>SHELL</main>";
  const files = { "/config.js": 'window.AGENTPRO_CONFIG = {\n  SUPABASE_URL: "https://s.test",\n  SUPABASE_ANON_KEY: "anon",\n  SITE_BASE_URL: "https://sites.test"\n};\n' };
  const KEY = "k-test-secret-0123456789";
  const env = { PORTAL_RPC_KEY: KEY, ASSETS: { fetch: async (req) => {
    const p = new URL(req.url || req).pathname;
    if (files[p]) return new Response(files[p]);
    if (p.startsWith("/shared/")) { try { return new Response(readFileSync(new URL("." + p, root))); } catch { return new Response("nf", { status: 404 }); } }
    return new Response(SHELL, { status: 200, headers: { "content-type": "text/html" } });
  } } };
  const realFetch = globalThis.fetch;
  let rpcs = [], reply = {};
  globalThis.fetch = async (u, o) => {
    const name = String(u).split("/rpc/")[1], body = JSON.parse(o.body); rpcs.push({ name, body, key: (o.headers || {})["x-portal-key"] });
    const r = reply[name]; if (typeof r === "function") return r(body);
    return new Response(JSON.stringify(r === undefined ? null : r));
  };
  const reset = () => { rpcs = []; };
  const portalRpcs = () => rpcs.filter((r) => r.name.startsWith("portal_"));
  const BASE = "https://sites.test", PB = "/a/demo/my", ORIGIN = BASE;
  const siteRow = (extra = {}) => ({ ...R("web/dev-fixtures/sample-en.json"), published_at: "2026-10-01T10:00:00.000Z", portal: true, ...extra });
  const SESSION = "S".repeat(43), PIN = "739158", INVITE = "I".repeat(43);
  const hostile = "<script>alert(1)</script>\"'";
  const home = (overrides = {}) => ({ ok: true, customers: [{
    id: "11111111-1111-1111-1111-111111111111", name: "Ravi Kumar", phone: "+919876500000", email: "r@example.com", dob: "1980-02-03", city: "Pune", address: "12 MG Road", nominee: "Sita Kumar", notes: "SECRETNOTE",
    policies: [
      { id: "22222222-2222-2222-2222-222222222222", plan_name: "Jeevan Anand Plan", policy_number: "PN-0001", sum_assured: 500000, premium_amount: 12000, premium_mode: "yearly", start_date: "2020-01-15", next_due_date: "2026-12-15", status: "active", notes: "SECRETNOTE", payments: [{ amount: 12000, paid_on: "2025-12-14" }] },
      { id: "33333333-3333-3333-3333-333333333333", plan_name: "Tech Term Cover", policy_number: "PN-0002", sum_assured: 10000000, premium_amount: 800, premium_mode: "monthly", start_date: "2022-05-01", next_due_date: null, status: "active", payments: [] },
    ] }], ...overrides });
  const send = (path, { host = "sites.test", method = "GET", cookie, origin, body, ctype = "application/x-www-form-urlencoded", extraHeaders = {}, e = env } = {}) => {
    const h = { ...extraHeaders };
    if (cookie) h.cookie = cookie; if (origin) h.origin = origin; if (body != null) h["content-type"] = ctype;
    return worker.fetch(new Request(`https://${host}${path}`, { method, headers: h, body: body == null ? undefined : body, redirect: "manual" }), e);
  };
  const form = (o) => new URLSearchParams(o).toString();
  const post = (path, fields, o = {}) => send(path, { method: "POST", origin: ORIGIN, body: form(fields), ...o });
  const setup = () => { reset(); reply = { get_public_site: [siteRow()], get_public_site_by_host: [siteRow()] }; };
  const COOKIE = `ap_portal=${SESSION}`;
  const loc = (r) => r.headers.get("location") || "";
  const allOut = async (r) => `${await r.clone().text()}\n${loc(r)}\n${[...r.headers].map(([k, v]) => k + ": " + v).join("\n")}`;
  const portalResponses = [];   // collected for the cross-cutting header checks
  const collect = (r) => { portalResponses.push(r); return r; };

  // ---------- GET /my ----------
  setup();
  let r = collect(await send(PB));
  await check("worker: GET /my without cookie -> 303 to /my/login, no portal RPC", () => {
    assert.equal(r.status, 303); assert.ok(loc(r).endsWith(PB + "/login"), loc(r)); assert.equal(portalRpcs().length, 0);
  });
  setup(); reply.portal_home = home();
  r = collect(await send(PB, { cookie: COOKIE }));
  let htmlHome = await r.clone().text();
  await check("worker: GET /my with cookie -> 200 html with policy plan names, portal_home(token, slug, host), no notes", async () => {
    assert.equal(r.status, 200); assert.match(r.headers.get("content-type"), /^text\/html/);
    assert.ok(htmlHome.includes("Jeevan Anand Plan") && htmlHome.includes("Tech Term Cover") && htmlHome.includes("Ravi Kumar"));
    assert.ok(!/notes/i.test(htmlHome) && !htmlHome.includes("SECRETNOTE"));
    const c = portalRpcs(); assert.equal(c.length, 1); assert.equal(c[0].name, "portal_home"); assert.equal(c[0].body.token, SESSION); assert.equal(c[0].body.slug, "demo");
    assert.ok(!(await allOut(r)).includes(SESSION), "session token leaked into the response");
  });
  if (existsSync(new URL("web/dev-fixtures/sample-portal.json", root))) {
    await check("worker: the sample-portal.json fixture renders as the home (its plan names appear)", async () => {
      const fxp = JSON.parse(readFileSync(new URL("web/dev-fixtures/sample-portal.json", root), "utf8"));
      const data = fxp.home || fxp.portal_home || (fxp.customers ? fxp : null);
      if (!data) return;
      setup(); reply.portal_home = { ok: true, ...data };
      const html = await (await send(PB, { cookie: COOKIE })).text();
      for (const c of data.customers) for (const p of c.policies || []) assert.ok(html.includes(p.plan_name.replace(/&/g, "&amp;")), p.plan_name);
    });
  }
  setup(); reply.portal_home = { ok: false, error: "session" };
  r = collect(await send(PB, { cookie: COOKIE }));
  await check("worker: portal_home 'session' error -> 303 login + clearing Set-Cookie (Path=/a/demo/my)", () => {
    assert.equal(r.status, 303); assert.ok(loc(r).endsWith(PB + "/login"));
    assert.equal(r.headers.get("set-cookie"), clearPortalCookie(PB));
  });
  setup(); reply.portal_home = { ok: false, error: "unavailable" };
  r = collect(await send(PB, { cookie: COOKIE }));
  await check("worker: portal_home 'unavailable' -> 404", () => assert.equal(r.status, 404));
  for (const [label, rowv] of [["portal:false", [siteRow({ portal: false })]], ["portal missing", [siteRow({ portal: undefined })]], ["unknown site (null)", null]]) {
    setup(); reply.get_public_site = rowv; reply.portal_home = home();
    r = collect(await send(PB, { cookie: COOKIE }));
    await check(`worker: site ${label} -> 404 for /my, no portal RPC`, () => { assert.equal(r.status, 404); assert.equal(portalRpcs().length, 0); });
  }
  setup(); reply.get_public_site = [siteRow({ portal: false })];
  r = await send(PB + "/login");
  await check("worker: portal:false -> login page also 404; POST login too", async () => {
    assert.equal(r.status, 404);
    const p = await post(PB + "/login", { phone: "9876500000", pin: PIN }); assert.equal(p.status, 404); assert.equal(portalRpcs().length, 0);
  });
  setup(); reply.portal_home = () => new Response("{}", { status: 500 });
  r = collect(await send(PB, { cookie: COOKIE }));
  await check("worker: portal_home HTTP 500 -> 503 with no-store", () => { assert.equal(r.status, 503); assert.match(r.headers.get("cache-control"), /no-store/); });
  setup(); reply.get_public_site = () => new Response("{}", { status: 500 });
  r = await send(PB + "/login");
  await check("worker: site RPC HTTP 500 -> 503 no-store (not the static shell)", () => { assert.equal(r.status, 503); assert.match(r.headers.get("cache-control"), /no-store/); });
  setup();
  r = await send(PB + "/nope");
  await check("worker: unknown portal subpath -> 404", () => assert.equal(r.status, 404));
  r = await send("/a/Bad%20Slug/my/login");
  await check("worker: malformed slug on the portal -> 404 without a portal RPC", () => { assert.equal(r.status, 404); assert.equal(portalRpcs().length, 0); });

  // ---------- login page + POST login ----------
  setup(); r = collect(await send(PB + "/login"));
  const loginHtml = await r.clone().text();
  await check("worker: GET /my/login -> 200 form posting to the login path with phone and pin fields, no RPC besides the site", () => {
    assert.equal(r.status, 200); assert.ok(loginHtml.includes(`action="${PB}/login"`) && /method="post"/i.test(loginHtml));
    assert.ok(/name="phone"/.test(loginHtml) && /name="pin"/.test(loginHtml)); assert.equal(portalRpcs().length, 0);
    assert.ok(/type="password"/.test(loginHtml) || /inputmode="numeric"/.test(loginHtml), "pin field should be a password/numeric input");
  });
  setup(); r = collect(await send(PB + "/login", { cookie: COOKIE }));
  await check("worker: GET /my/login keeps showing the form (no RPC, no redirect loop)", () => assert.ok([200, 303].includes(r.status)));

  setup(); reply.portal_login = { ok: true, session_token: SESSION, expires_at: new Date(Date.now() + 90 * 86400e3).toISOString() };
  r = collect(await post(PB + "/login", { phone: "98765 00000", pin: PIN }));
  await check("worker: POST login ok -> 303 to /my with Set-Cookie (token, Path, exact attributes) and the PIN nowhere in the response", async () => {
    assert.equal(r.status, 303); assert.ok(loc(r).endsWith(PB) || loc(r).endsWith(PB + "/"), loc(r));
    const c = r.headers.get("set-cookie");
    assert.ok(c.startsWith(`ap_portal=${SESSION}; Path=${PB}; Max-Age=`), c); assert.ok(c.endsWith("; HttpOnly; Secure; SameSite=Lax"), c);
    const age = Number(c.match(/Max-Age=(\d+)/)[1]); assert.ok(age > 80 * 86400 && age <= 91 * 86400, "Max-Age " + age);
    const calls = portalRpcs(); assert.equal(calls.length, 1); assert.equal(calls[0].name, "portal_login"); assert.equal(calls[0].body.slug, "demo"); assert.equal(calls[0].body.pin, PIN);
    assert.ok(!(await allOut(r)).includes(PIN));
  });
  setup(); reply.portal_login = { ok: false, error: "invalid" };
  r = collect(await post(PB + "/login", { phone: "9876500000", pin: PIN }));
  let t = await r.clone().text();
  await check("worker: POST login invalid -> 400 with the error text, phone echoed, PIN never echoed, no cookie", async () => {
    assert.equal(r.status, 400); assert.ok(t.includes("That mobile number and PIN don"), "err_invalid text");
    assert.ok(t.includes("9876500000")); assert.ok(!(await allOut(r)).includes(PIN)); assert.equal(r.headers.get("set-cookie"), null);
  });
  setup(); reply.portal_login = { ok: false, error: "locked", until: new Date(Date.now() + 14.5 * 60000).toISOString() };
  r = collect(await post(PB + "/login", { phone: "9876500000", pin: PIN })); t = await r.clone().text();
  await check("worker: POST login locked with until -> 4xx with 'Wait 15 minutes' text", () => {
    assert.ok(r.status === 429 || r.status === 423 || r.status === 400 || r.status === 403, "status " + r.status); assert.ok(t.includes("Wait 15 minutes"), t.match(/Too many tries[^<]*/)?.[0]);
    assert.ok(!t.includes(PIN));
  });
  setup(); reply.portal_login = { ok: false, error: "locked", until: null };
  r = collect(await post(PB + "/login", { phone: "9876500000", pin: PIN })); t = await r.clone().text();
  await check("worker: POST login hard lock (until null) -> 4xx with 'This login is locked' text", () => {
    assert.ok(r.status >= 400 && r.status < 500, "status " + r.status); assert.ok(t.includes("This login is locked")); assert.ok(!/Wait \d+ minutes/.test(t));
  });
  setup(); reply.portal_login = { ok: false, error: "unavailable" };
  r = await post(PB + "/login", { phone: "9876500000", pin: PIN });
  await check("worker: POST login 'unavailable' -> 404", () => assert.equal(r.status, 404));
  setup(); reply.portal_login = () => new Response("{}", { status: 500 });
  r = await post(PB + "/login", { phone: "9876500000", pin: PIN });
  await check("worker: POST login backend 500 -> 503 no-store", () => { assert.equal(r.status, 503); assert.match(r.headers.get("cache-control"), /no-store/); });

  // ---------- POST guards ----------
  for (const [label, o] of [["no Origin", { origin: "" }], ["foreign Origin", { origin: "https://evil.test" }], ["Origin 'null'", { origin: "null" }], ["same host other scheme", { origin: "http://sites.test" }]]) {
    setup(); reply.portal_login = { ok: true, session_token: SESSION, expires_at: new Date(Date.now() + 1e9).toISOString() };
    const hdr = { method: "POST", body: form({ phone: "9876500000", pin: PIN }) }; if (o.origin) hdr.origin = o.origin;
    r = collect(await send(PB + "/login", hdr));
    await check(`worker: POST login with ${label} -> 403 and no RPC at all`, () => { assert.equal(r.status, 403); assert.equal(rpcs.length, 0); assert.equal(r.headers.get("set-cookie"), null); });
  }
  for (const p of ["/start", "/ask", "/request", "/logout"]) {
    setup(); r = await send(PB + p, { method: "POST", origin: "https://evil.test", cookie: COOKIE, body: form({ x: "1" }) });
    await check(`worker: POST ${p} with a foreign Origin -> 403 and no RPC`, () => { assert.equal(r.status, 403); assert.equal(rpcs.length, 0); });
  }
  setup(); r = collect(await post(PB + "/login", { phone: "9876500000", pin: PIN, junk: "x".repeat(5000) }));
  await check("worker: body over 4096 bytes -> 413 and no RPC", () => { assert.equal(r.status, 413); assert.equal(rpcs.length, 0); });
  setup(); r = await send(PB + "/login", { method: "POST", origin: ORIGIN, body: form({ phone: "9876500000", pin: PIN }), extraHeaders: { "content-length": "99999" } }).catch((e) => e);
  await check("worker: oversized Content-Length header is refused (413) without an RPC", () => { if (r instanceof Error) return; assert.ok([400, 413].includes(r.status), "status " + r.status); assert.equal(rpcs.length, 0); });
  for (const ct of ["application/json", "text/plain", "multipart/form-data; boundary=x"]) {
    setup(); r = await send(PB + "/login", { method: "POST", origin: ORIGIN, body: JSON.stringify({ phone: "9876500000", pin: PIN }), ctype: ct });
    await check(`worker: POST login with content-type ${ct} -> rejected (400/415), no RPC`, () => { assert.ok([400, 415].includes(r.status), "status " + r.status); assert.equal(portalRpcs().length, 0); });
  }
  setup(); r = await send(PB + "/login", { method: "PUT", origin: ORIGIN, body: "x" });
  await check("worker: PUT on a portal path -> 405", () => assert.equal(r.status, 405));
  setup(); r = await send(PB + "/login", { method: "POST", origin: ORIGIN, body: form({ phone: "x", pin: PIN }), e: { ...env, PORTAL_LIMIT: undefined } });
  await check("worker: PORTAL_LIMIT absent -> no limiting (request proceeds)", () => assert.notEqual(r.status, 429));
  setup(); const limits = []; const limited = { ...env, PORTAL_LIMIT: { limit: async (x) => { limits.push(x); return { success: false }; } } };
  r = collect(await send(PB + "/login", { method: "POST", origin: ORIGIN, body: form({ phone: "9876500000", pin: PIN }), e: limited }));
  await check("worker: PORTAL_LIMIT returning {success:false} -> 429 and no portal RPC", () => { assert.equal(r.status, 429); assert.equal(portalRpcs().length, 0); assert.ok(limits.length >= 1); assert.ok(!JSON.stringify(limits).includes(PIN)); });
  setup(); reply.portal_login = { ok: false, error: "invalid" };
  r = await send(PB + "/login", { method: "POST", origin: ORIGIN, body: form({ phone: "9876500000", pin: PIN }), e: { ...env, PORTAL_LIMIT: { limit: async () => ({ success: true }) } } });
  await check("worker: PORTAL_LIMIT success:true -> request proceeds to the RPC", () => { assert.equal(r.status, 400); assert.equal(portalRpcs().length, 1); });

  // ---------- start ----------
  setup(); r = collect(await send(`${PB}/start?t=${INVITE}`));
  await check("worker: GET /my/start?t=<token> -> 303 to the bare /start, ap_invite cookie, token not in Location, no RPC", () => {
    assert.equal(r.status, 303); assert.equal(loc(r), PB + "/start"); assert.ok(!loc(r).includes(INVITE) && !loc(r).includes("?"));
    assert.equal(r.headers.get("set-cookie"), inviteCookie(INVITE, PB + "/start")); assert.equal(portalRpcs().length, 0);
  });
  setup(); r = collect(await send(`${PB}/start?t=short`)); t = await r.clone().text();
  await check("worker: GET /my/start?t=<malformed> -> start page with the invite error, no cookie", () => {
    assert.equal(r.status, 200); assert.ok(t.includes("This link has expired")); assert.equal(r.headers.get("set-cookie"), null);
  });
  const INV = `ap_invite=${INVITE}`;
  setup(); r = collect(await send(`${PB}/start`, { cookie: INV })); t = await r.clone().text();
  // The start page must not use no-referrer (nor same-origin): Chromium then sends Origin: null on the same-origin form POST,
  // which the Origin check rejects, so first login would break. strict-origin sends only the origin, never a path or token.
  await check("worker: GET /my/start with the cookie -> 200, Referrer-Policy strict-origin (not no-referrer/same-origin), form without t", () => {
    assert.equal(r.status, 200); const rp = r.headers.get("referrer-policy"); assert.equal(rp, "strict-origin"); assert.ok(rp !== "no-referrer" && rp !== "same-origin");
    for (const n of ["phone", "pin", "pin2", "consent"]) assert.ok(new RegExp(`name="${n}"`).test(t), n);
    assert.ok(!/name="t"/.test(t) && !t.includes(INVITE) && !t.includes("This link has expired")); assert.equal(portalRpcs().length, 0);
  });
  setup(); r = collect(await send(`${PB}/start`)); t = await r.clone().text();
  await check("worker: GET /my/start without the cookie -> invite-expired page, no RPC", () => { assert.equal(r.status, 200); assert.ok(t.includes("This link has expired")); assert.equal(portalRpcs().length, 0); });
  const startFields = (o = {}) => ({ phone: "9876500000", pin: PIN, pin2: PIN, consent: "1", ...o });
  const postStart = (fields, o = {}) => post(PB + "/start", fields, { cookie: INV, ...o });
  setup(); r = collect(await post(PB + "/start", startFields()));
  await check("worker: POST start without the ap_invite cookie -> 400 invite error and no RPC", async () => {
    assert.equal(r.status, 400); assert.ok((await r.clone().text()).includes("This link has expired")); assert.equal(rpcs.length, 1 /* site lookup only */); assert.equal(portalRpcs().length, 0);
  });
  setup(); r = await post(PB + "/start", startFields({ t: INVITE }));
  await check("worker: a t= form field is ignored (the token only comes from the cookie)", () => { assert.equal(r.status, 400); assert.equal(portalRpcs().length, 0); });
  setup(); r = collect(await postStart(startFields({ pin2: "111111" }))); t = await r.clone().text();
  await check("worker: POST start with PIN mismatch -> 400 'PINs don't match', no RPC, PIN not echoed", async () => {
    assert.equal(r.status, 400); assert.ok(t.includes("The two PINs don"), "err_pin_mismatch"); assert.equal(rpcs.filter((x) => x.name === "portal_activate").length, 0);
    assert.ok(!(await allOut(r)).includes(PIN)); assert.equal(r.headers.get("referrer-policy"), "strict-origin");
  });
  setup(); const nc = startFields(); delete nc.consent; r = collect(await postStart(nc)); t = await r.clone().text();
  await check("worker: POST start without consent -> 400 'tick the box', no RPC", () => { assert.equal(r.status, 400); assert.ok(t.includes("Please tick the box")); assert.equal(rpcs.filter((x) => x.name === "portal_activate").length, 0); });
  const notices = R("shared/generated/privacy-notices.json").notices || {};
  const haveNotice = !!notices.customer_portal; // the Worker answers 503 on /start until generateShared has produced it
  setup(); reply.portal_activate = { ok: true, session_token: SESSION, expires_at: new Date(Date.now() + 90 * 86400e3).toISOString() };
  r = collect(await postStart(startFields()));
  const act = portalRpcs().find((x) => x.name === "portal_activate");
  if (!haveNotice) pending("worker: POST start ok / activate error pages (4 checks)", "customer_portal notice not generated yet; Worker returns 503");
  const checkN = haveNotice ? check : async () => {};
  await checkN("worker: POST start ok -> portal_activate(token, phone, pin) -> 303 + Set-Cookie, PIN not in response", async () => {
    assert.equal(r.status, 303); assert.ok(loc(r).endsWith(PB) || loc(r).endsWith(PB + "/"), loc(r));
    const sc = r.headers.getSetCookie(); assert.equal(sc.length, 2); assert.ok(sc[0].startsWith(`ap_portal=${SESSION}; Path=${PB};`)); assert.equal(sc[1], clearInviteCookie(PB + "/start"));
    assert.ok(act, "portal_activate not called"); assert.equal(act.body.token, INVITE); assert.equal(act.body.pin, PIN); assert.equal(act.body.slug, "demo");
    assert.equal(act.body.consent.notice_key, "customer_portal"); assert.ok(!(await allOut(r)).includes(PIN));
  });
  if (notices.customer_portal) {
    await check("worker: portal_activate consent {notice_key, version, hash} matches privacy-notices.json for the site locale", () => {
      const n = notices.customer_portal;
      assert.deepEqual(act.body.consent, { notice_key: "customer_portal", version: n.version, hash: n.hash.en });
      assert.match(act.body.consent.hash, /^[0-9a-f]{64}$/);
    });
    setup(); reply.get_public_site = [siteRow({ locale: "hi" })]; reply.portal_activate = { ok: true, session_token: SESSION, expires_at: new Date(Date.now() + 1e9).toISOString() };
    await postStart(startFields());
    const hi = portalRpcs().find((x) => x.name === "portal_activate");
    await check("worker: Hindi site sends the Hindi notice hash", () => assert.equal(hi.body.consent.hash, notices.customer_portal.hash.hi));
  } else pending("worker: consent hash matches privacy-notices.json", "shared/generated/privacy-notices.json has no customer_portal yet (orchestrator regenerates)");
  for (const [code, key, status] of [["invalid_invite", "This link has expired", 4], ["invalid", "", 4]]) {
    setup(); reply.portal_activate = code === "invalid" ? { ok: false, error: "invalid", field: "pin" } : { ok: false, error: code };
    r = await postStart(startFields()); t = await r.text();
    await checkN(`worker: portal_activate ${code} -> 4xx page, no cookie, PIN not echoed${key ? ", text shown" : ""}`, () => {
      assert.ok(r.status >= 400 && r.status < 500, "status " + r.status); assert.ok(!r.headers.getSetCookie().some((c) => c.startsWith("ap_portal="))); if (code === "invalid_invite") assert.equal(r.headers.get("set-cookie"), clearInviteCookie(PB + "/start")); assert.ok(!t.includes(PIN)); if (key) assert.ok(t.includes(key));
    });
  }
  setup(); reply.portal_activate = { ok: false, error: "invalid", field: "phone" };
  r = await postStart(startFields()); t = await r.text();
  await checkN("worker: activate phone mismatch -> 400 with the phone-match error", () => { assert.equal(r.status, 400); assert.ok(t.includes("This isn") && t.includes("mobile number")); });

  // ---------- ask / request / logout ----------
  setup(); reply.portal_ask = { ok: true };
  r = collect(await post(PB + "/ask", { policy_id: "22222222-2222-2222-2222-222222222222", kind: "paid", message: "paid" }, { cookie: COOKIE }));
  await check("worker: POST ask ok -> portal_ask(token, slug, policy_id, kind, message) and 303 to /my?done=asked", () => {
    assert.equal(r.status, 303); assert.ok(loc(r).includes(PB + "?done=asked"), loc(r));
    const c = portalRpcs()[0]; assert.equal(c.name, "portal_ask"); assert.equal(c.body.token, SESSION); assert.equal(c.body.kind, "paid"); assert.equal(c.body.policy_id, "22222222-2222-2222-2222-222222222222");
  });
  setup(); reply.portal_ask = { ok: false, error: "session" };
  r = await post(PB + "/ask", { policy_id: "22222222-2222-2222-2222-222222222222", kind: "question", message: "hi" }, { cookie: COOKIE });
  await check("worker: POST ask with an expired session -> 303 login + cleared cookie", () => { assert.equal(r.status, 303); assert.ok(loc(r).endsWith(PB + "/login")); assert.equal(r.headers.get("set-cookie"), clearPortalCookie(PB)); });
  setup(); reply.portal_ask = { ok: false, error: "rate_limited" }; reply.portal_home = home();
  r = await post(PB + "/ask", { policy_id: "22222222-2222-2222-2222-222222222222", kind: "question", message: "hi" }, { cookie: COOKIE });
  await check("worker: POST ask rate_limited -> 429", () => assert.equal(r.status, 429));
  setup(); r = await post(PB + "/ask", { policy_id: "x", kind: "question", message: "hi" });
  await check("worker: POST ask without a cookie -> 303 login, no RPC", () => { assert.equal(r.status, 303); assert.ok(loc(r).endsWith(PB + "/login")); assert.equal(portalRpcs().length, 0); });
  setup(); reply.portal_request = { ok: true };
  r = await post(PB + "/request", { customer_id: "11111111-1111-1111-1111-111111111111", kind: "correct", message: "fix" }, { cookie: COOKIE });
  await check("worker: POST request ok -> portal_request called and 303 to /my?done=requested", () => { assert.equal(r.status, 303); assert.ok(loc(r).includes("done=requested")); assert.equal(portalRpcs()[0].name, "portal_request"); assert.equal(portalRpcs()[0].body.customer_id, "11111111-1111-1111-1111-111111111111"); });

  setup(); reply.portal_logout = { ok: true };
  r = collect(await post(PB + "/logout", { everywhere: "1" }, { cookie: COOKIE }));
  await check("worker: logout everywhere -> portal_logout(token, true), cleared cookie, no Clear-Site-Data, 303 login?done=signed_out", () => {
    assert.equal(r.status, 303); assert.ok(loc(r).endsWith(PB + "/login?done=signed_out"), loc(r));
    assert.equal(r.headers.get("set-cookie"), clearPortalCookie(PB)); assert.equal(r.headers.get("clear-site-data"), null);
    const c = portalRpcs(); assert.equal(c.length, 1); assert.deepEqual(c[0].body, { token: SESSION, everywhere: true });
  });
  setup(); reply.portal_logout = { ok: true };
  r = await post(PB + "/logout", {}, { cookie: COOKIE });
  await check("worker: logout without the flag -> everywhere:false", () => assert.equal(portalRpcs()[0].body.everywhere, false));
  setup(); reply.portal_logout = () => new Response("{}", { status: 500 });
  r = await post(PB + "/logout", {}, { cookie: COOKIE });
  await check("worker: logout still clears the cookie even if the RPC fails", () => { assert.equal(r.headers.get("set-cookie"), clearPortalCookie(PB)); assert.ok([303, 503].includes(r.status)); });
  setup(); r = await send(PB + "/login?done=signed_out"); t = await r.text();
  await check("worker: login?done=signed_out shows the signed-out flash and the data-signed-out marker (plain login has none)", async () => {
    assert.ok(t.includes("You&#39;re signed out.") || t.includes("You're signed out.")); assert.ok(/<div class="tpl portal"[^>]* data-signed-out[ >]/.test(t));
    setup(); assert.ok(!/data-signed-out/.test(await (await send(PB + "/login")).text()));
  });
  const clientSrc = readFileSync(new URL("web/portal-client.js", root), "utf8");
  await check("portal-client.js posts {type:signout} on the marker and on the logout submit", () => {
    assert.ok(/signedOut/.test(clientSrc) && (clientSrc.match(/type: "signout"/g) || []).length >= 2);
  });

  // ---------- E1/E2: shared secret, rate_limited ----------
  setup(); reply.portal_home = home(); reply.portal_login = { ok: false, error: "invalid" }; reply.portal_ask = { ok: true };
  await send(PB, { cookie: COOKIE }); await post(PB + "/login", { phone: "9876500000", pin: PIN }); await post(PB + "/ask", { policy_id: "22222222-2222-2222-2222-222222222222", kind: "question", message: "m" }, { cookie: COOKIE });
  await check("worker: every portal RPC carries x-portal-key = env.PORTAL_RPC_KEY; get_public_site does not", () => {
    assert.ok(portalRpcs().length >= 3); for (const x of portalRpcs()) assert.equal(x.key, KEY, x.name);
    const site = rpcs.filter((x) => !x.name.startsWith("portal_")); assert.ok(site.length >= 1); for (const x of site) assert.equal(x.key, undefined, x.name);
  });
  setup(); const noKey = { ...env, PORTAL_RPC_KEY: undefined };
  const errLog = console.error; let logged = []; console.error = (...a) => logged.push(a.join(" "));
  const rNoKey = collect(await send(PB, { cookie: COOKIE, e: noKey })); const rNoKey2 = await post(PB + "/login", { phone: "9876500000", pin: PIN }, { e: noKey });
  console.error = errLog;
  await check("worker: missing PORTAL_RPC_KEY -> 503 portal error page, no RPC at all, logged once without a value", () => {
    assert.equal(rNoKey.status, 503); assert.equal(rNoKey2.status, 503); assert.equal(rpcs.length, 0);
    assert.ok(logged.filter((l) => /PORTAL_RPC_KEY/.test(l)).length <= 1); assert.ok(!logged.join(" ").includes(KEY));
  });
  setup(); reply.portal_login = { ok: false, error: "rate_limited" };
  r = await post(PB + "/login", { phone: "9876500000", pin: PIN }); t = await r.text();
  await check("worker: portal_login rate_limited -> 429 with the too-many-tries text", () => { assert.equal(r.status, 429); assert.ok(t.includes(dicts.en["portal.err_rate"].slice(0, 20))); });
  setup(); reply.portal_activate = { ok: false, error: "rate_limited" };
  r = await postStart(startFields()); t = await r.text();
  await check("worker: portal_activate rate_limited -> 429 start page", () => { assert.ok(haveNotice, "customer_portal notice must be generated"); assert.equal(r.status, 429); });

  // ---------- L1 / L3 ----------
  const POL = "22222222-2222-2222-2222-222222222222", CUS = "11111111-1111-1111-1111-111111111111";
  for (const [p, f] of [["/ask", { policy_id: "not-a-uuid", kind: "question", message: "KEEPME" }], ["/request", { customer_id: "1; drop", kind: "correction", message: "KEEPME" }]]) {
    setup(); reply.portal_home = home();
    r = await post(PB + p, f, { cookie: COOKIE }); t = await r.text();
    await check(`worker: POST ${p} with a non-uuid id -> 400 home with err_generic near the form, message kept, no ${p} RPC`, () => {
      assert.equal(r.status, 400); assert.equal(rpcs.filter((x) => x.name === "portal_ask" || x.name === "portal_request").length, 0);
      assert.ok(t.includes(dicts.en["portal.err_generic"])); assert.ok(t.includes("Jeevan Anand Plan"));
    });
  }
  setup(); reply.portal_home = home(); reply.portal_ask = { ok: false, error: "rate_limited" };
  r = await post(PB + "/ask", { policy_id: POL, kind: "paid", message: "KEEP <b>me</b>" }, { cookie: COOKIE }); t = await r.text();
  await check("worker: 429 ask shows err_rate as an alert inside the paid form's open <details>, keeps the (escaped) message", () => {
    assert.equal(r.status, 429);
    const i = t.indexOf('<details class="more pt-more" open>'); assert.ok(i > 0, "details not open");
    const seg = t.slice(i, t.indexOf("</details>", i)); assert.ok(seg.includes('role="alert"') && seg.includes(dicts.en["portal.err_rate"].slice(0, 20)), seg.slice(0, 300));
    assert.ok(seg.includes("KEEP &lt;b&gt;me&lt;/b&gt;")); assert.equal((t.match(/<details class="more pt-more" open>/g) || []).length, 1);
  });
  setup(); reply.portal_home = home(); reply.portal_ask = { ok: false, error: "invalid", field: "message" };
  r = await post(PB + "/ask", { policy_id: POL, kind: "question", message: " " }, { cookie: COOKIE }); t = await r.text();
  await check("worker: 400 ask (field message) shows the required error inside the opened form", () => {
    assert.equal(r.status, 400); const i = t.indexOf('<details class="more pt-more" open>'); assert.ok(i > 0);
    assert.ok(t.slice(i, t.indexOf("</details>", i)).includes('role="alert"'));
  });
  setup(); reply.portal_home = home(); reply.portal_request = { ok: false, error: "rate_limited" };
  r = await post(PB + "/request", { customer_id: CUS, kind: "correction", message: "FIXME" }, { cookie: COOKIE }); t = await r.text();
  await check("worker: 429 request keeps the correction message and opens that form", () => { assert.equal(r.status, 429); assert.ok(t.includes(">FIXME</textarea>")); assert.equal((t.match(/pt-more" open>/g) || []).length, 1); });

  // ---------- HEAD, headers, no scripts ----------
  setup(); reply.portal_home = home();
  const heads = await Promise.all([send(PB + "/login", { method: "HEAD" }), send(PB, { method: "HEAD", cookie: COOKIE })]);
  await check("worker: HEAD returns the headers and no body", async () => { for (const h of heads) { assert.ok([200, 303].includes(h.status)); assert.equal(await h.text(), ""); } assert.equal(heads[0].headers.get("cache-control"), "no-store"); });

  await check("every portal response: Cache-Control no-store, X-Robots-Tag noindex, SECURITY_HEADERS (CSP with form-action 'self'), Referrer-Policy not weaker", () => {
    assert.ok(portalResponses.length >= 15, "collected " + portalResponses.length);
    const bad = new Set();
    for (const x of portalResponses) {
      const tag = `${x.status}${loc(x) ? " " + loc(x) : ""}`;
      if (!/no-store/.test(x.headers.get("cache-control") || "")) bad.add(`cache-control=${x.headers.get("cache-control")} [${tag}]`);
      if (!/noindex/.test(x.headers.get("x-robots-tag") || "")) bad.add(`x-robots-tag=${x.headers.get("x-robots-tag")} [${tag}]`);
      for (const [k, v] of Object.entries(SECURITY_HEADERS)) { if (k === "Referrer-Policy") continue; if (x.headers.get(k) !== v) bad.add(`${k} missing/different [${tag}]`); }
      if (!["strict-origin-when-cross-origin", "strict-origin", "no-referrer"].includes(x.headers.get("referrer-policy"))) bad.add(`referrer-policy=${x.headers.get("referrer-policy")} [${tag}]`);
    }
    assert.equal([...bad].join("\n"), "");
  });
  await check("portal HTML pages: only the one deferred client module script, no inline handlers", async () => {
    let n = 0, withScript = 0;
    for (const x of portalResponses) {
      if (!(x.headers.get("content-type") || "").startsWith("text/html")) continue;
      const h = await x.clone().text(); n++;
      const scripts = h.match(/<script\b[^>]*>/gi) || [];
      assert.ok(scripts.length <= 1, `status ${x.status}: ${scripts.length} scripts`);
      if (scripts.length) { withScript++; assert.equal(scripts[0], '<script type="module" src="/portal-client.js">'); assert.ok(!/<script[^>]*>[^<]/i.test(h), "inline script body"); }
      assert.ok(!/\son[a-z]+\s*=/i.test(h.replace(/<style[\s\S]*?<\/style>/g, "")), "inline handler");
    }
    assert.ok(n >= 8, "html pages checked " + n); assert.ok(withScript >= 3, "pages with the client module " + withScript);
  });
  await check("the PIN and session token never appear in any portal response (body, URL or header, apart from the cookie)", async () => {
    for (const x of portalResponses) { const o = await allOut(x); assert.ok(!o.includes(PIN), "PIN in " + x.status); assert.ok(!o.replace(/ap_portal=[^\s;]*/g, "").includes(SESSION), "token outside the cookie"); }
  });

  // ---------- escaping ----------
  const xssSite = siteRow({ profile: { ...siteRow().profile, full_name: hostile, phone: "9876543210" } });
  setup(); reply.get_public_site = [xssSite];
  reply.portal_home = home({ customers: [{ ...home().customers[0], name: hostile, city: "<img src=x onerror=alert(1)>", nominee: "\"><svg onload=1>",
    policies: [{ ...home().customers[0].policies[0], plan_name: "\"><img src=x onerror=alert(2)>" + hostile, policy_number: "<b>PN</b>" }] }] });
  const xr = await send(PB, { cookie: COOKIE }); const xh = await xr.text();
  const xl = await (await send(PB + "/login")).text();
  await check("escaping: hostile agent name, customer and policy fields render escaped (home and login)", () => {
    assert.equal(xr.status, 200);
    for (const h0 of [xh, xl]) { const h = h0.replace('<script type="module" src="/portal-client.js"></script>', ""); for (const [re, w] of [[/<script/i, "script"], [/<img[^>]*(?:onerror|src=x)/i, "img"], [/<svg/i, "svg"], [/<b>PN/, "b"]]) assert.ok(!re.test(h), "raw " + w + ": " + (h.match(new RegExp(".{40}" + re.source + ".{40}", "i")) || [""])[0]); assert.ok(!/\sonerror\s*=/i.test(h.replace(/&lt;[^>]*?&gt;/g, "")) ); }
    assert.ok(xh.includes("&lt;script&gt;alert(1)&lt;/script&gt;")); assert.ok(xh.includes("&lt;img src=x onerror=alert(2)&gt;") || xh.includes("&lt;img"));
  });

  // ---------- custom host ----------
  setup(); reply.portal_login = { ok: true, session_token: SESSION, expires_at: new Date(Date.now() + 90 * 86400e3).toISOString() }; reply.portal_home = home();
  const dh = { host: "asha.in" };
  r = collect(await send("/my", dh));
  const rc = await send("/my", { ...dh, cookie: COOKIE }); const dhtml = await rc.text();
  const rl = collect(await send("/my/login", dh)); const lhtml = await rl.text();
  const rp = collect(await send("/my/login", { ...dh, method: "POST", origin: "https://asha.in", body: form({ phone: "9876500000", pin: PIN }) }));
  await check("custom host: /my -> login redirect on /my/login, resolved by host (get_public_site_by_host)", () => {
    assert.equal(r.status, 303); assert.ok(loc(r).endsWith("/my/login") && !loc(r).includes("/a/"), loc(r)); assert.equal(rpcs.find((x) => x.name === "get_public_site_by_host").body.host, "asha.in");
  });
  await check("custom host: home 200, links and form action use /my (no /a/<slug>)", () => {
    assert.equal(rc.status, 200); assert.ok(dhtml.includes('"/my/logout"') || dhtml.includes("/my/logout")); assert.ok(!dhtml.includes("/a/"), "primary-style link on a custom host");
    assert.ok(lhtml.includes('action="/my/login"') && !lhtml.includes("/a/"));
  });
  await check("custom host: login sets cookie with Path=/my and the exact attributes; portal_login gets the host", () => {
    assert.equal(rp.status, 303); const c = rp.headers.get("set-cookie"); assert.ok(c.startsWith(`ap_portal=${SESSION}; Path=/my; Max-Age=`), c); assert.ok(c.endsWith("; HttpOnly; Secure; SameSite=Lax"));
    const l = portalRpcs().find((x) => x.name === "portal_login"); assert.equal(l.body.host, "asha.in"); assert.ok(!l.body.slug);
  });
  setup(); reply.portal_home = { ok: false, error: "session" };
  r = await send("/my", { ...dh, cookie: COOKIE });
  await check("custom host: expired session clears with Path=/my", () => assert.equal(r.headers.get("set-cookie"), clearPortalCookie("/my")));
  setup(); r = await send("/my/login", { ...dh, method: "POST", origin: "https://sites.test", body: form({ phone: "1", pin: "2" }) });
  await check("custom host: POST with the primary host as Origin -> 403 (Origin must equal the request origin)", () => { assert.equal(r.status, 403); assert.equal(rpcs.length, 0); });

  // ---------- robots ----------
  r = await send("/robots.txt"); t = await r.text();
  await check("robots.txt disallows /a/*/my and /my", () => {
    assert.ok(/^Disallow: \/a\/\*\/my\/?\*?$/m.test(t) || /^Disallow: \/a\/\*\/my/m.test(t), t); assert.ok(/^Disallow: \/my/m.test(t), t); assert.ok(/^Allow: \/$/m.test(t));
  });

  // ---------- footer link + privacy anchor ----------
  const tokensFor = (s) => ({ accent: "#1F4AA8", schema, catalog });
  const sharedAll = { schema, catalog, templates: R("shared/templates/index.json"), images: {} };
  const renderHome = (portal, template_key, page) => {
    const s = { ...fx("sample-en"), portal, template_key };
    const target = { slug: "demo", ...(page ? { page } : {}) };
    return renderDocument({ site: s, target, url: new URL("https://sites.test/a/demo/" + (page === "privacy" ? "privacy" : "")), dict: dicts.en, shared: sharedAll, notices: R("shared/generated/privacy-notices.json"), config: {} });
  };
  for (const tpl of TEMPLATE_KEYS) {
    await check(`footer (${tpl}): "My policies" link to /a/demo/my only when site.portal === true`, () => {
      const label = dicts.en["site.footer_portal"]; assert.ok(label, "site.footer_portal missing from the dictionary");
      const on = renderHome(true, tpl), off = renderHome(false, tpl), none = renderHome(undefined, tpl);
      assert.ok(on.includes('href="/a/demo/my"') && on.includes(label), "link missing when portal is true");
      for (const h of [off, none]) assert.ok(!h.includes('href="/a/demo/my"') && !h.includes(label), "link present when portal is not true");
    });
  }
  await check("footer: custom host links /my", () => {
    const s = { ...fx("sample-en"), portal: true };
    const h = renderDocument({ site: s, target: { host: "asha.in" }, url: new URL("https://asha.in/"), dict: dicts.en, shared: sharedAll, notices: R("shared/generated/privacy-notices.json"), config: {} });
    assert.ok(h.includes('href="/my"'));
  });
  await check("footer: Hindi label comes from the Hindi dictionary", () => {
    assert.ok(dicts.hi["site.footer_portal"]); const s = { ...fx("sample-hi"), portal: true };
    const h = renderDocument({ site: s, target: { slug: "demo" }, url: new URL("https://sites.test/a/demo/"), dict: dicts.hi, shared: sharedAll, notices: {}, config: {} });
    assert.ok(h.includes(dicts.hi["site.footer_portal"]));
  });
  await check("privacy page: id=\"portal\" section only when portal is true", () => {
    assert.ok(/id="portal"/.test(renderHome(true, TEMPLATE_KEYS[0], "privacy")), "missing for portal:true");
    for (const v of [false, undefined]) assert.ok(!/id="portal"/.test(renderHome(v, TEMPLATE_KEYS[0], "privacy")), "present for " + v);
  });

  // ---------- PWA: manifest variant, service worker, client (DECISIONS #100) ----------
  const { portalServiceWorker, swSource } = await import("../web/portal-sw.js");
  const mf = (q, host = "sites.test") => worker.fetch(new Request(`https://${host}/manifest.webmanifest${q}`), env);
  setup(); reply.get_public_site = [siteRow()];
  let pm = await mf("?s=demo&app=portal"), pj = await pm.json();
  await check("manifest ?app=portal: id = start_url = scope = the portal base; portal names, site locale, site icons", () => {
    assert.equal(pm.status, 200); assert.match(pm.headers.get("content-type"), /^application\/manifest\+json/);
    for (const k of ["id", "start_url", "scope"]) assert.equal(pj[k], "/a/demo/my", k);
    assert.equal(pj.short_name, dicts.en["portal.app_short_name"]);
    assert.equal(pj.name, dicts.en["portal.app_name"].replace("{name}", siteRow().profile.full_name).slice(0, 45 + 20));
    assert.ok(pj.name.length <= 45 + dicts.en["portal.app_name"].length); assert.equal(pj.lang, siteRow().locale || "en"); assert.equal(pj.display, "standalone");
    assert.ok(pj.icons.length === 4 && pj.icons.every((i) => /^\/icons\/.+-(192|512)\.png$/.test(i.src)) && pj.background_color && pj.theme_color);
    assert.ok(!("description" in pj));
  });
  setup(); reply.get_public_site_by_host = [siteRow()];
  pj = await (await mf("?app=portal", "asha.in")).json();
  await check("manifest ?app=portal on a custom host: scope /my", () => { assert.equal(pj.id, "/my"); assert.equal(pj.start_url, "/my"); assert.equal(pj.scope, "/my"); });
  setup(); reply.get_public_site = [siteRow({ portal: false })]; reply.get_public_site_by_host = [siteRow({ portal: false })];
  await check("manifest ?app=portal -> 404 when portal is false; the site manifest still works", async () => {
    assert.equal((await mf("?s=demo&app=portal")).status, 404); assert.equal((await mf("?app=portal", "asha.in")).status, 404);
    const site = await mf("?s=demo"); assert.equal(site.status, 200); assert.equal((await site.json()).scope, "/a/demo/");
  });
  setup(); reply.portal_home = home();
  await check("portal pages link the portal manifest, icons and apple title; the site manifest link is unchanged", async () => {
    const h = await (await send(PB + "/login")).text();
    assert.ok(h.includes('<link rel="manifest" href="/manifest.webmanifest?s=demo&amp;app=portal">'), "manifest link");
    assert.ok(/<link rel="apple-touch-icon" href="\/icons\//.test(h) && /<link rel="icon" href="\/icons\//.test(h));
    assert.ok(h.includes(`<meta name="apple-mobile-web-app-title" content="${dicts.en["portal.app_short_name"]}">`));
    const hc = await (await send("/my/login", { host: "asha.in" })).text(); assert.ok(hc.includes('href="/manifest.webmanifest?app=portal"'), "custom host manifest link");
  });
  await check("home page has the install box (strings from portal.*/site.* keys); login and start do not", async () => {
    const h = await (await send(PB, { cookie: COOKIE })).text();
    assert.ok(h.includes('id="install" hidden') && h.includes(dicts.en["portal.install_button"]) && h.includes(dicts.en["portal.install_ios_note"]) && h.includes(dicts.en["site.install_ios"]));
    assert.ok(!(await (await send(PB + "/login")).text()).includes('id="install"'));
  });
  await check("site pages register no service worker and load no portal client", () => {
    for (const f of ["web/hydrate.js", "web/install.js", "web/client.js", "web/app.js", "web/ssr.js", "web/head.js"]) assert.ok(!/serviceWorker|navigator\.serviceWorker/.test(readFileSync(new URL(f, root), "utf8")), f);
    const sitePage = renderDocument({ site: { ...fx("sample-en"), portal: true }, target: { slug: "demo" }, url: new URL("https://sites.test/a/demo/"), dict: dicts.en, shared: sharedAll, notices: {}, config: {} });
    assert.ok(!/serviceWorker|portal-client|\/sw\.js/.test(sitePage), "site page");
  });

  // sw.js
  setup(); reply.get_public_site = [siteRow()];
  const swr = await send(PB + "/sw.js"), swText = await swr.text();
  await check("sw.js: JS content type, no-cache, Service-Worker-Allowed = the base, security headers; custom host scope /my", async () => {
    assert.equal(swr.status, 200); assert.equal(swr.headers.get("content-type"), "application/javascript; charset=utf-8");
    assert.equal(swr.headers.get("cache-control"), "no-cache"); assert.equal(swr.headers.get("service-worker-allowed"), "/a/demo/my");
    assert.equal(swr.headers.get("x-content-type-options"), "nosniff");
    reply.get_public_site_by_host = [siteRow()];
    const c = await send("/my/sw.js", { host: "asha.in" }); assert.equal(c.headers.get("service-worker-allowed"), "/my"); assert.ok((await c.text()).includes('"portal:/my:'));
    const hd = await send(PB + "/sw.js", { method: "HEAD" }); assert.equal(hd.status, 200); assert.equal(await hd.text(), "");
    assert.equal((await send(PB + "/sw.js", { method: "POST", origin: ORIGIN, body: "" })).status, 405);
  });
  await check("sw.js: 404 when the portal is off or the site is unknown; no portal RPC", async () => {
    setup(); reply.get_public_site = [siteRow({ portal: false })];
    assert.equal((await send(PB + "/sw.js")).status, 404);
    setup(); reply.get_public_site = [];
    assert.equal((await send(PB + "/sw.js")).status, 404); assert.equal(portalRpcs().length, 0);
  });
  await check("sw source: bakes the cache name, base and the localised offline page; Hindi page for a Hindi site", async () => {
    assert.ok(swText.includes('"portal:/a/demo/my:dev"') && swText.includes('BASE="/a/demo/my"') && swText.includes(dicts.en["portal.offline_page"].replace("'", "&#39;")) || swText.includes(dicts.en["portal.offline_page"]));
    assert.ok(swText.includes("Offline \u2014 last updated {time}") || swText.includes("Offline — last updated {time}"));
    setup(); reply.get_public_site = [siteRow({ locale: "hi" })];
    const hi = await (await send(PB + "/sw.js")).text(); assert.ok(hi.includes(dicts.hi["portal.offline_page"]) && !hi.includes(dicts.en["portal.offline_page"]));
    for (const bad of ["Ravi", "Kumar", siteRow().profile.full_name]) assert.ok(!swText.includes(bad), "agent/customer data in the SW");
  });
  await check("sw source (static): only GETs are handled, never start/login/POST, and no cookie-bearing responses are stored", () => {
    assert.ok(/r\.method!=="GET"\)return/.test(swText)); assert.ok(!/\/start|\/login"?\s*\)\s*\{?\s*c\.put/.test(swText.replace('BASE+"/login"', "")));
    assert.ok(swText.includes('has("set-cookie")') && swText.includes('type:"signout"') === false && swText.includes('"signout"'));
    assert.equal((swText.match(/\.put\(/g) || []).length, 2, "exactly two cache writes: the home page and static assets");
    assert.ok(!/\.put\([^)]*(start|login)/.test(swText));
  });

  // Executed: the generated script in a vm with a fake platform.
  const { runInNewContext } = await import("node:vm");
  const mkSw = () => {
    const store = new Map(), listeners = {}; let net = null, log = [];
    const cacheOf = (n) => { if (!store.has(n)) store.set(n, new Map()); const m = store.get(n); return { match: async (k) => m.get(String(k)), put: async (k, v) => { m.set(String(k), v); }, delete: async (k) => m.delete(String(k)) }; };
    const caches = { open: async (n) => cacheOf(n), keys: async () => [...store.keys()], delete: async (n) => store.delete(n) };
    const self = { addEventListener: (t, f) => { listeners[t] = f; }, skipWaiting: () => {}, clients: { claim: async () => {} } };
    const fetch = async (r) => { log.push(r.url || String(r)); if (net instanceof Error) throw net; return typeof net === "function" ? net(r) : net; };
    runInNewContext(swSource({ base: "/a/demo/my", version: "v1", lang: "en", offline: "<p>OFFLINE-PAGE</p>", banner: "Offline - last updated {time}" }), { self, caches, fetch, location: { origin: "https://sites.test" }, URL, Headers, Response, Request, Intl, Date, Promise, console });
    const fire = async (type, ev) => { const waits = []; let resp; listeners[type]({ ...ev, respondWith: (p) => { resp = p; }, waitUntil: (p) => waits.push(p) }); await Promise.all(waits); return resp ? await resp : undefined; };
    return { store, fire, setNet: (n) => { net = n; }, log, fetchEv: (url, o = {}) => fire("fetch", { request: { url: "https://sites.test" + url, method: "GET", mode: "navigate", ...o } }) };
  };
  const htmlRes = (body, o = {}) => new Response(body, { status: 200, headers: { "content-type": "text/html; charset=utf-8", date: "Fri, 23 Oct 2026 10:30:00 GMT", ...(o.headers || {}) }, ...o });
  await check("sw (executed): network ok -> served and stored as last home; network fail -> cached home with the offline banner", async () => {
    const sw = mkSw(); sw.setNet(() => htmlRes('<html><body><main class="pt-main"><h1>HOME-BODY</h1></main></body></html>'));
    const live = await sw.fetchEv("/a/demo/my"); assert.match(await live.text(), /HOME-BODY/);
    assert.ok(sw.store.get("portal:/a/demo/my:v1").has("https://sites.test/a/demo/my"), "last home stored");
    sw.setNet(new Error("offline"));
    const off = await (await sw.fetchEv("/a/demo/my?done=asked")).text();
    assert.ok(off.includes("HOME-BODY") && /<main class="pt-main">\n<p class="pt-flash" role="status">Offline - last updated .*2026.*<\/p>/.test(off), off);
    assert.match(off, /3:00|3:00 pm|15:00/i.test(off) ? /./ : /4:00|3:00/);
  });
  await check("sw (executed): offline without a stored home, and login/start offline -> the baked offline page", async () => {
    const sw = mkSw(); sw.setNet(new Error("offline"));
    for (const p of ["/a/demo/my", "/a/demo/my/login", "/a/demo/my/start?t=x"]) assert.match(await (await sw.fetchEv(p)).text(), /OFFLINE-PAGE/, p);
  });
  await check("sw (executed): POST passthrough, login/start never stored, other origins and out-of-scope ignored", async () => {
    const sw = mkSw(); sw.setNet(() => htmlRes("<main>x</main>"));
    assert.equal(await sw.fetchEv("/a/demo/my/ask", { method: "POST" }), undefined);
    assert.equal(await sw.fetchEv("/a/other/my", {}), undefined);
    assert.equal(await sw.fetchEv("/a/demo/", {}), undefined);
    assert.equal(await sw.fire("fetch", { request: { url: "https://evil.test/a/demo/my", method: "GET", mode: "navigate" } }), undefined);
    for (const p of ["/a/demo/my/login", "/a/demo/my/start?t=" + "I".repeat(43), "/a/demo/my?x=1"]) await sw.fetchEv(p);
    const c = sw.store.get("portal:/a/demo/my:v1"); assert.ok(!c || c.size === 0, "cache holds " + (c && [...c.keys()]));
    assert.equal(sw.log.length, 3, "POST/foreign/out-of-scope never fetched by the SW");
  });
  await check("sw (executed): static assets are cache-first; cache name carries the build", async () => {
    const sw = mkSw(); let n = 0; sw.setNet(() => new Response("css" + ++n, { status: 200 }));
    const a = await sw.fetchEv("/styles/portal.css", { mode: "no-cors" }), b = await sw.fetchEv("/styles/portal.css", { mode: "no-cors" });
    assert.equal(await a.text(), "css1"); assert.equal(await b.text(), "css1"); assert.equal(n, 1); assert.deepEqual([...sw.store.keys()], ["portal:/a/demo/my:v1"]);
    assert.equal(await sw.fetchEv("/other.js", { mode: "no-cors" }), undefined);
  });
  await check("sw (executed): redirect to login, 404 and 503 delete the last home; signout message clears the caches; activate drops old caches", async () => {
    for (const bad of [() => ({ type: "opaqueredirect", status: 0, headers: new Headers() }), () => new Response("nf", { status: 404 }), () => new Response("x", { status: 503 }), () => Object.defineProperties(new Response("l", { status: 200, headers: { "content-type": "text/html" } }), { redirected: { value: true }, url: { value: "https://sites.test/a/demo/my/login" } })]) {
      const sw = mkSw(); sw.setNet(() => htmlRes('<main class="pt-main">h</main>')); await sw.fetchEv("/a/demo/my");
      assert.equal(sw.store.get("portal:/a/demo/my:v1").size, 1); sw.setNet(bad); await sw.fetchEv("/a/demo/my");
      assert.equal(sw.store.get("portal:/a/demo/my:v1").size, 0);
      sw.setNet(new Error("off")); assert.match(await (await sw.fetchEv("/a/demo/my")).text(), /OFFLINE-PAGE/, "revoked home must not be served offline");
    }
    const sw = mkSw(); sw.setNet(() => htmlRes('<main class="pt-main">h</main>')); await sw.fetchEv("/a/demo/my"); await sw.fetchEv("/styles/base.css", { mode: "no-cors" });
    sw.store.set("portal:/a/demo/my:v0", new Map([["k", 1]])); sw.store.set("portal:/a/other/my:v1", new Map([["k", 1]])); sw.store.set("unrelated", new Map([["k", 1]]));
    await sw.fire("activate", {}); assert.deepEqual([...sw.store.keys()].sort(), ["portal:/a/demo/my:v1", "portal:/a/other/my:v1", "unrelated"]);
    await sw.fire("message", { data: { type: "signout" } }); assert.deepEqual([...sw.store.keys()].sort(), ["portal:/a/other/my:v1", "unrelated"]);
  });
  await check("size: portal-client.js + the generated service worker <= 8 KB raw", () => {
    const n = readFileSync(new URL("web/portal-client.js", root)).length + Buffer.byteLength(swText);
    console.log(`SIZE portal client + service worker: ${(n / 1024).toFixed(1)} KB raw`); assert.ok(n <= 8 * 1024, `${n} bytes`);
  });
  await check("build-web.sh copies portal-client.js", () => assert.ok(/hydrate\.js portal-client\.js/.test(readFileSync(new URL("scripts/build-web.sh", root), "utf8"))));

  // ---------- F6 summary tiles ----------
  const pmod = await import("../web/portal.js");
  const tiles = (home, loc = "en", today = "2026-10-23") => {
    const html = pmod.portalHomeDocument({ site: { ...siteRow(), locale: loc }, target: { slug: "demo" }, dict: dicts[loc], shared: { catalog }, config: {}, home: { ok: true, ...home }, today });
    const m = /<dl class="pt-tiles">([\s\S]*?)<\/dl>/.exec(html);
    const map = {}; if (m) for (const t of m[1].matchAll(/<dt>([^<]*)<\/dt><dd>([\s\S]*?)<\/dd>/g)) map[t[1]] = t[2].replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
    return { html, map, raw: m && m[1] };
  };
  const pol = (o) => ({ id: "p" + Math.random(), plan_name: "Plan", status: "active", payments: [], ...o });
  const cust = (name, policies) => ({ id: "c" + name, name, policies });
  const monthsOf = (k) => catalog.premiumModes.find((m) => m.key === k).months;
  const perYear = (amt, k) => amt * (12 / monthsOf(k));
  const mixed = [cust("Asha", [
    pol({ premium_amount: 24000, premium_mode: "yearly", sum_assured: 1000000, next_due_date: "2026-12-01" }),
    pol({ premium_amount: 12000, premium_mode: "half_yearly", sum_assured: 500000, next_due_date: "2026-11-05" }),
    pol({ premium_amount: 1250, premium_mode: "quarterly", sum_assured: null, next_due_date: "2027-01-01" }),
    pol({ premium_amount: 100, premium_mode: "monthly", sum_assured: 250000, next_due_date: "2026-10-20" }),
    pol({ premium_amount: 99999, premium_mode: "yearly", sum_assured: 9999999, next_due_date: "2026-01-01", status: "lapsed" }),
    pol({ premium_amount: 5000, premium_mode: "weekly_unknown", sum_assured: 1, next_due_date: null }),
  ])];
  await check("tiles: counts active policies only; yearly premium uses catalog months for every mode, skips unknown modes", () => {
    const { map, raw } = tiles({ customers: mixed });
    const expectYear = perYear(24000, "yearly") + perYear(12000, "half_yearly") + perYear(1250, "quarterly") + perYear(100, "monthly");
    assert.equal(map["Policies"], "5");
    assert.equal(map["Premium per year"], pmod.money(expectYear, "en"));
    assert.ok(!raw.includes(pmod.money(99999, "en")) && !raw.includes("99,99"), "lapsed policy leaked");
    assert.equal(map["People"], undefined, "single customer shows no People tile");
  });
  await check("tiles: total cover sums active sum_assured, skips nulls and inactive", () => {
    assert.equal(tiles({ customers: mixed }).map["Total cover"], pmod.money(1000000 + 500000 + 250000 + 1, "en"));
  });
  await check("tiles: zero cover hides the Total cover tile", () => {
    const { map } = tiles({ customers: [cust("A", [pol({ premium_amount: 100, premium_mode: "monthly", sum_assured: null }), pol({ premium_amount: 5, premium_mode: "yearly", sum_assured: 0 })])] });
    assert.equal(map["Total cover"], undefined); assert.equal(map["Policies"], "2");
  });
  await check("tiles: next due is the earliest active due date with its due-bucket pill; none -> no_dues", () => {
    const { map, raw } = tiles({ customers: mixed });
    assert.ok(map["Next due"].startsWith(formatDateFor("2026-10-20", "en")), map["Next due"]);
    const b = pmod.dueBucket(catalog, "2026-10-20", "2026-10-23");
    assert.ok(b && raw.includes(`tone-${b.tone}`) && raw.includes(dicts.en[b.labelKey]));
    assert.equal(tiles({ customers: [cust("A", [pol({ premium_mode: "yearly", premium_amount: 1, next_due_date: null })])] }).map["Next due"], dicts.en["portal.no_dues"]);
    assert.equal(tiles({ customers: [cust("A", [pol({ premium_mode: "yearly", premium_amount: 1, next_due_date: "2026-01-01", status: "lapsed" })])] }).map["Next due"], dicts.en["portal.no_dues"]);
  });
  await check("tiles: People tile only with more than one customer, counting all customers", () => {
    assert.equal(tiles({ customers: [cust("A", []), cust("B", [])] }).map["People"], "2");
    assert.equal(tiles({ customers: [cust("A", [])] }).map["People"], undefined);
    assert.equal(tiles({ customers: [cust("A", [])] }).map["Policies"], "0");
  });
  await check("tiles: Hindi uses hi labels and hi-IN number formatting, no raw i18n keys, markup is a dl and adds no headings", () => {
    const hi = tiles({ customers: [...mixed, cust("B", [])] }, "hi");
    for (const k of ["portal.summary_policies", "portal.summary_premium_year", "portal.summary_cover", "portal.summary_next_due", "portal.summary_people"]) assert.ok(dicts.hi[k] && hi.map[dicts.hi[k]] !== undefined, k);
    assert.ok(hi.map[dicts.hi["portal.summary_premium_year"]].startsWith(pmod.money(perYear(24000, "yearly") + perYear(12000, "half_yearly") + perYear(1250, "quarterly") + perYear(100, "monthly"), "hi")));
    assert.ok(!/portal\.[a-z_]+/.test(hi.raw.replace(/class="[^"]*"/g, "")), "raw key in tiles");
    const en = tiles({ customers: mixed }); assert.ok(!/<h[1-6]/.test(en.raw));
    assert.ok(!/portal\.[a-z_]+/.test(en.raw.replace(/class="[^"]*"/g, "")));
  });

  // ---------- CSS budget ----------
  const dist = new URL("web/dist/", root).pathname, src = new URL("web/", root).pathname;
  const pick = (...c) => c.find((p) => existsSync(p));
  const cssFiles = [pick(dist + "tokens.css", src + "tokens.css"), pick(dist + "styles/base.css", src + "styles/base.css"), pick(dist + "styles/fonts.css", src + "styles/fonts.css"), pick(dist + "styles/portal.css", src + "styles/portal.css")];
  if (!cssFiles[3]) pending("portal first-paint CSS budget", "no styles/portal.css yet");
  else if (cssFiles.some((f) => !f)) pending("portal first-paint CSS budget", "tokens/base/fonts css not found");
  else await check("portal first-paint CSS (tokens+base+fonts+portal.css) <= 26 KB raw", () => {
    const n = cssFiles.reduce((a, f) => a + readFileSync(f).length, 0); console.log(`SIZE portal first paint: ${(n / 1024).toFixed(1)} KB raw`); assert.ok(n <= 26 * 1024, `${n} bytes`);
  });
  await check("portal pages link only same-origin CSS (tokens, base, fonts, portal) and no external resources", () => {
    assert.ok(/href="\/styles\/portal\.css"/.test(loginHtml), "portal.css not linked");
    assert.ok(!/<(?:link|script|iframe|source)[^>]*(?:src|href)="https?:\/\/(?!sites\.test)/.test(loginHtml.replace(/<link rel="canonical"[^>]*>/, "")), "external resource");
    assert.ok(/<meta name="robots" content="noindex/.test(loginHtml), "robots meta noindex");
  });

  globalThis.fetch = realFetch;
  if (fails.length) throw new Error(`${fails.length} portal check(s) failed:\n - ${fails.join("\n - ")}`);
}
