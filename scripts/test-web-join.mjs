// Customer sign-up page checks (DECISIONS #108): route.js, cookies.js, join-age.js, the Worker's /join routes (mocked RPCs), robots,
// headers, escaping, the privacy page's #signup section and the build. Run by scripts/test-web.mjs.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

export async function run({ ok, R, dicts, catalog, makeT, LOCALE_KEYS }) {
  const root = new URL("../", import.meta.url);
  const fails = [];
  const check = async (name, fn) => {
    try { await fn(); ok(name, () => {}); } catch (e) { fails.push(name); console.log("FAIL", name, "\n     ", String(e && e.message).split("\n").slice(0, 6).join("\n      ")); }
  };
  const { resolveTarget } = await import("../web/route.js");
  const { joinLinks } = await import("../web/join.js");
  const { joinToken, joinCookie, clearJoinCookie } = await import("../web/cookies.js");
  const { isMinor, todayIST } = await import("../web/join-age.js");
  const { SECURITY_HEADERS } = await import("../web/headers.js");
  const { privacyHtml } = await import("../web/privacy.js");
  const { normalizeSite } = await import("../web/api.js");
  const worker = (await import("../web/worker.js")).default;
  const notices = R("shared/generated/privacy-notices.json");

  // ---------- route.js ----------
  const P = "sites.test";
  const rt = (u) => { const x = new URL(u); return resolveTarget(x, x.searchParams, P); };
  await check("route: primary host /a/x/join[/consent|save|submit|done] -> join targets; unknown subpath -> null; /joinfoo is not join", () => {
    for (const [path, jp] of [["/a/x/join", ""], ["/a/x/join/", ""], ["/a/x/join/consent", "consent"], ["/a/x/join/save", "save"], ["/a/x/join/submit", "submit"], ["/a/x/join/done", "done"]]) assert.deepEqual(rt(`https://${P}${path}`), { slug: "x", page: "join", joinPath: jp }, path);
    assert.equal(rt(`https://${P}/a/x/join/zzz`).joinPath, null); assert.equal(rt(`https://${P}/a/x/join/save/x`).joinPath, null);
    assert.notEqual((rt(`https://${P}/a/x/joinfoo`) || {}).page, "join");
    assert.equal(rt(`https://${P}/a/x/privacy`).page, "privacy"); assert.equal(rt(`https://${P}/a/x/my`).page, "portal");
  });
  await check("route: custom host /join[/...] -> join targets; / and /privacy unchanged", () => {
    assert.deepEqual(rt("https://asha.in/join"), { host: "asha.in", page: "join", joinPath: "" });
    assert.deepEqual(rt("https://asha.in/join/submit"), { host: "asha.in", page: "join", joinPath: "submit" });
    assert.equal(rt("https://asha.in/join/nope").joinPath, null);
    assert.notEqual(rt("https://asha.in/joinfoo").page, "join");
    assert.equal(rt("https://asha.in/privacy").page, "privacy"); assert.equal(rt("https://asha.in/").page, undefined);
  });
  await check("route: joinLinks for the primary host and a custom host", () => {
    assert.deepEqual(joinLinks({ slug: "asha" }), { base: "/a/asha/join", home: "/a/asha/join", consent: "/a/asha/join/consent", save: "/a/asha/join/save", submit: "/a/asha/join/submit", done: "/a/asha/join/done", site: "/a/asha/", privacy: "/a/asha/privacy" });
    assert.deepEqual(joinLinks({ host: "asha.in" }), { base: "/join", home: "/join", consent: "/join/consent", save: "/join/save", submit: "/join/submit", done: "/join/done", site: "/", privacy: "/privacy" });
  });

  // ---------- cookies.js, join-age.js ----------
  const TOK = "J".repeat(43);
  await check("cookies: ap_join is HttpOnly/Secure/Lax, 7 days, Path scoped; joinToken rejects malformed", () => {
    assert.equal(joinCookie(TOK, "/a/asha/join"), `ap_join=${TOK}; Path=/a/asha/join; Max-Age=604800; HttpOnly; Secure; SameSite=Lax`);
    assert.equal(clearJoinCookie("/join"), "ap_join=; Path=/join; Max-Age=0; HttpOnly; Secure; SameSite=Lax");
    assert.equal(joinToken(`a=1; ap_join=${TOK}`), TOK); assert.equal(joinToken(`ap_portal=${TOK}`), null);
    for (const bad of ["", "ap_join=short", "ap_join=" + "a".repeat(30) + "!"]) assert.equal(joinToken(bad), null, bad);
  });
  await check("join-age: isMinor by India's date (18th birthday is adult, the day before is a minor); bad dates are not minors", () => {
    assert.equal(isMinor("2008-10-25", "2026-10-25"), false); assert.equal(isMinor("2008-10-26", "2026-10-25"), true);
    assert.equal(isMinor("2020-01-01", "2026-10-25"), true); assert.equal(isMinor("1980-02-03", "2026-10-25"), false);
    assert.equal(isMinor("", "2026-10-25"), false); assert.equal(isMinor("25/10/2010", "2026-10-25"), false);
    assert.equal(todayIST(Date.parse("2026-10-24T20:00:00Z")), "2026-10-25"); // 01:30 IST next day
  });

  // ---------- fake platform ----------
  const files = { "/config.js": 'window.AGENTPRO_CONFIG = {\n  SUPABASE_URL: "https://s.test",\n  SUPABASE_ANON_KEY: "anon",\n  SITE_BASE_URL: "https://sites.test"\n};\n' };
  const KEY = "k-test-secret-0123456789";
  const env = { PORTAL_RPC_KEY: KEY, ASSETS: { fetch: async (req) => {
    const p = new URL(req.url || req).pathname;
    if (files[p]) return new Response(files[p]);
    if (p.startsWith("/shared/")) { try { return new Response(readFileSync(new URL("." + p, root))); } catch { return new Response("nf", { status: 404 }); } }
    return new Response("<!doctype html><main id=root>SHELL</main>", { status: 200, headers: { "content-type": "text/html" } });
  } } };
  const realFetch = globalThis.fetch, realErr = console.error;
  let rpcs = [], reply = {};
  globalThis.fetch = async (u, o) => {
    const name = String(u).split("/rpc/")[1], body = JSON.parse(o.body); rpcs.push({ name, body, key: (o.headers || {})["x-portal-key"] });
    const r = reply[name]; if (typeof r === "function") return r(body);
    return new Response(JSON.stringify(r === undefined ? null : r));
  };
  const logged = []; console.error = (...a) => logged.push(a.map(String).join(" "));
  const signupRpcs = () => rpcs.filter((r) => r.name.startsWith("signup_"));
  const BASE = "https://sites.test", JB = "/a/demo/join", ORIGIN = BASE;
  const siteRow = (extra = {}) => ({ ...R("web/dev-fixtures/sample-en.json"), published_at: "2026-10-01T10:00:00.000Z", ...extra });
  const hostile = "<script>alert(1)</script>\"'";
  const send = (path, { host = "sites.test", method = "GET", cookie, origin, body, ctype = "application/x-www-form-urlencoded", e = env } = {}) => {
    const h = {};
    if (cookie) h.cookie = cookie; if (origin) h.origin = origin; if (body != null) h["content-type"] = ctype;
    return worker.fetch(new Request(`https://${host}${path}`, { method, headers: h, body: body == null ? undefined : body, redirect: "manual" }), e);
  };
  const post = (path, fields, o = {}) => send(path, { method: "POST", origin: ORIGIN, body: new URLSearchParams(fields).toString(), ...o });
  const setup = () => { rpcs = []; logged.length = 0; reply = { get_public_site: [siteRow()], get_public_site_by_host: [siteRow()] }; };
  const COOKIE = `ap_join=${TOK}`;
  const loc = (r) => r.headers.get("location") || "";
  const allOut = async (r) => `${await r.clone().text()}\n${loc(r)}\n${[...r.headers].map(([k, v]) => k + ": " + v).join("\n")}`;
  const responses = [];
  const collect = (r) => { responses.push(r.clone()); return r; };
  const SAVED = { name: "Ravi Kumar", phone: "9876500000" };

  // ---------- GET ?t= and the invalid-link page ----------
  setup();
  let r = collect(await send(`${JB}?t=${TOK}`));
  await check("worker: GET join?t= -> 303 to the bare join with the ap_join cookie (Path=base, HttpOnly, Secure, Lax); token not in Location; no RPC", async () => {
    assert.equal(r.status, 303); assert.equal(loc(r), JB);
    assert.equal(r.headers.get("set-cookie"), `ap_join=${TOK}; Path=${JB}; Max-Age=604800; HttpOnly; Secure; SameSite=Lax`);
    assert.equal(signupRpcs().length, 0);
  });
  setup(); r = collect(await send("/join?t=" + TOK, { host: "asha.in" }));
  await check("worker: custom domain /join?t= -> cookie Path=/join, 303 to /join", () => { assert.equal(r.status, 303); assert.equal(loc(r), "/join"); assert.match(r.headers.get("set-cookie"), /^ap_join=J+; Path=\/join;/); });
  setup(); r = collect(await send(`${JB}?t=bad`));
  await check("worker: GET join?t=<malformed> -> the invalid-link page (no cookie set, no RPC)", async () => {
    const t = await r.text(); assert.equal(r.status, 200); assert.ok(t.includes("This link has expired or was already used. Ask "), t.slice(0, 300)); assert.ok(!/ap_join=J/.test(r.headers.get("set-cookie") || "")); assert.equal(signupRpcs().length, 0);
  });
  setup(); r = collect(await send(JB));
  await check("worker: GET join without a cookie -> the invalid-link page with Call and WhatsApp to the agent, no signup RPC", async () => {
    const t = await r.text(); assert.equal(r.status, 200); assert.ok(t.includes("This link has expired"));
    assert.ok(/href="tel:\+91/.test(t) && /href="https:\/\/wa\.me\/91/.test(t), "call/whatsapp"); assert.equal(signupRpcs().length, 0);
  });
  setup(); reply.signup_open = { ok: false, error: "invalid_link" }; r = collect(await send(JB, { cookie: COOKIE }));
  await check("worker: signup_open invalid_link -> the invalid-link page and the cookie is cleared", async () => {
    assert.ok((await r.text()).includes("This link has expired")); assert.match(r.headers.get("set-cookie"), /^ap_join=; Path=\/a\/demo\/join; Max-Age=0;/);
  });
  setup(); reply.signup_open = { ok: false, error: "unavailable" }; r = collect(await send(JB, { cookie: COOKIE }));
  await check("worker: signup_open unavailable -> the 404 not-available page", () => assert.equal(r.status, 404));
  setup(); reply.get_public_site = [null]; reply.get_public_site = []; r = collect(await send(JB, { cookie: COOKIE }));
  await check("worker: unknown/unpublished site -> 404, no signup RPC", () => { assert.equal(r.status, 404); assert.equal(signupRpcs().length, 0); });

  // ---------- consent gate ----------
  setup(); reply.signup_open = { ok: true, fields: {}, consented: false, expires_at: "2026-11-01T00:00:00Z" };
  r = collect(await send(JB, { cookie: COOKIE })); let t = await r.clone().text();
  await check("worker: GET join before consent -> the notice, an unticked required checkbox and Start; NO form fields", () => {
    assert.equal(r.status, 200);
    assert.deepEqual(signupRpcs().map((x) => x.name), ["signup_open"]);
    const o = signupRpcs()[0]; assert.equal(o.body.token, TOK); assert.equal(o.body.slug, "demo"); assert.equal(o.body.host, null); assert.equal(o.key, KEY);
    const d = dicts.en;
    for (const k of ["title", "what", "why", "who", "how_long", "rights"]) assert.ok(t.includes(d["privacy.customer_signup." + k].split("{")[0].replace(/&/g, "&amp;").slice(0, 20)), k);
    assert.ok(/<input type="checkbox" id="f-consent" name="consent" value="1" required>/.test(t) && !/checked/.test(t));
    assert.ok(t.includes(`action="${JB}/consent"`) && t.includes(">Start<"));
    assert.ok(t.includes(`href="/a/demo/privacy#signup"`));
    assert.ok(!/name="(name|phone|email|dob|address|city|nominee)"/.test(t) && !t.includes("join-form") && !t.includes("join-client.js"), "form leaked before consent");
  });
  setup(); r = await post(`${JB}/consent`, { consent: "1" }, { cookie: COOKIE }); reply.signup_consent = { ok: true };
  setup(); reply.signup_consent = { ok: true };
  r = collect(await post(`${JB}/consent`, { consent: "1" }, { cookie: COOKIE }));
  await check("worker: POST consent -> signup_consent with the customer_signup notice key, version and the site-locale hash (gate header sent) -> 303 to join", () => {
    assert.equal(r.status, 303); assert.equal(loc(r), JB);
    const c = signupRpcs(); assert.equal(c.length, 1); assert.equal(c[0].name, "signup_consent"); assert.equal(c[0].key, KEY); assert.equal(c[0].body.token, TOK);
    const n = notices.notices.customer_signup;
    assert.deepEqual(c[0].body.consent, { notice_key: "customer_signup", version: n.version, hash: n.hash.en });
  });
  setup(); reply.get_public_site = [siteRow({ locale: "hi" })]; reply.signup_consent = { ok: true };
  r = await post(`${JB}/consent`, { consent: "1" }, { cookie: COOKIE });
  await check("worker: a Hindi site sends the hi hash", () => assert.equal(signupRpcs()[0].body.consent.hash, notices.notices.customer_signup.hash.hi));
  setup(); r = collect(await post(`${JB}/consent`, {}, { cookie: COOKIE }));
  await check("worker: POST consent without the tick -> 400 with the error, no RPC", async () => {
    assert.equal(r.status, 400); assert.ok((await r.text()).includes("Please tick the box to continue.")); assert.equal(signupRpcs().length, 0);
  });
  setup(); reply.signup_consent = { ok: false, error: "invalid_link" }; r = await post(`${JB}/consent`, { consent: "1" }, { cookie: COOKIE });
  await check("worker: consent on a dead link -> the invalid-link page, cookie cleared", async () => { assert.ok((await r.text()).includes("This link has expired")); assert.match(r.headers.get("set-cookie"), /Max-Age=0/); });
  setup(); r = await post(`${JB}/consent`, { consent: "1" });
  await check("worker: POST consent without a cookie -> the invalid-link page, no RPC", async () => { assert.ok((await r.text()).includes("This link has expired")); assert.equal(signupRpcs().length, 0); });

  // ---------- the form ----------
  setup(); reply.signup_open = { ok: true, consented: true, fields: { ...SAVED, city: hostile, dob: "2015-01-01" } };
  r = collect(await send(JB, { cookie: COOKIE })); t = await r.clone().text();
  await check("worker: GET join after consent -> the form prefilled with the saved values, the status line, Submit, the one script; XSS values escaped", () => {
    assert.equal(r.status, 200);
    for (const k of ["name", "phone", "email", "dob", "address", "city", "nominee"]) assert.ok(new RegExp(`<input id="f-${k}" name="${k}"`).test(t), k);
    assert.ok(t.includes('value="Ravi Kumar"') && t.includes('value="9876500000"') && t.includes('value="2015-01-01"'));
    assert.ok(t.includes(`action="${JB}/submit"`) && t.includes(`data-save="${JB}/save"`) && t.includes('id="join-status"') && t.includes(">Submit<"));
    assert.ok(t.includes('<script type="module" src="/join-client.js"></script>') && (t.match(/<script/g) || []).length === 1);
    assert.ok(!t.includes(hostile) && !/<script>alert/.test(t) && t.includes("&lt;script&gt;alert(1)&lt;/script&gt;&quot;&#39;"), "escaping");
    assert.ok(/class="fld jn-guardian" id="guardian">/.test(t), "a 2015 dob shows the guardian block server-side");
  });
  setup(); reply.signup_open = { ok: true, consented: true, fields: { ...SAVED, dob: "1980-02-03" } }; r = await send(JB, { cookie: COOKIE }); t = await r.text();
  await check("worker: the guardian block is present but hidden for an adult; it is never required in markup", () => {
    assert.ok(/id="guardian" hidden>/.test(t)); assert.ok(/name="guardian" value="1"/.test(t)); assert.ok(!/name="guardian"[^>]*required/.test(t));
  });
  setup(); reply.get_public_site = [siteRow({ locale: "hi" })]; reply.signup_open = { ok: true, consented: true, fields: {} }; r = await send(JB, { cookie: COOKIE }); t = await r.text();
  await check("worker: Hindi site renders Hindi labels", () => { assert.ok(t.includes('lang="hi"') && t.includes(dicts.hi["join.field_name"]) && t.includes(dicts.hi["join.submit"])); assert.ok(!/\bjoin\.(?!css)[a-z_]+/.test(t), "raw key"); });
  setup(); reply.signup_open = { ok: true, consented: true, fields: {} }; r = await send("/join", { host: "asha.in", cookie: COOKIE }); t = await r.text();
  await check("worker: custom domain form posts to /join/submit and resolves by host", () => {
    assert.ok(t.includes('action="/join/submit"')); assert.equal(signupRpcs()[0].body.host, "asha.in"); assert.equal(signupRpcs()[0].body.slug, null);
  });

  // ---------- save (JSON) ----------
  setup(); reply.signup_save = { ok: true };
  r = collect(await post(`${JB}/save`, { name: "Ravi", phone: "98", bogus: "x", active: "phone" }, { cookie: COOKIE }));
  await check("worker: POST save -> signup_save with only known fields + active_field, gate header, JSON {ok:true}, no-store", async () => {
    assert.equal(r.status, 200); assert.match(r.headers.get("content-type"), /^application\/json/); assert.deepEqual(await r.json(), { ok: true });
    assert.equal(r.headers.get("cache-control"), "no-store"); assert.match(r.headers.get("x-robots-tag"), /noindex/);
    const c = rpcs; assert.equal(c.length, 1, "save needs no site lookup"); assert.equal(c[0].name, "signup_save"); assert.equal(c[0].key, KEY);
    assert.deepEqual(c[0].body, { slug: "demo", host: null, token: TOK, fields: { name: "Ravi", phone: "98" }, active_field: "phone" });
  });
  setup(); reply.signup_save = { ok: true }; r = await post(`${JB}/save`, {}, { cookie: COOKIE });
  await check("worker: a heartbeat (empty body) sends {} and a null active_field", () => { assert.deepEqual(rpcs[0].body.fields, {}); assert.equal(rpcs[0].body.active_field, null); });
  setup(); reply.signup_save = { ok: false, error: "consent_required" }; r = await post(`${JB}/save`, { name: "x" }, { cookie: COOKIE });
  await check("worker: save answers pass the RPC's error through as JSON", async () => assert.deepEqual(await r.json(), { ok: false, error: "consent_required" }));
  setup(); reply.signup_save = { ok: false, error: "rate_limited" }; r = await post(`${JB}/save`, { name: "x" }, { cookie: COOKIE });
  await check("worker: save rate_limited -> 429 JSON", async () => { assert.equal(r.status, 429); assert.deepEqual(await r.json(), { ok: false, error: "rate_limited" }); });
  setup(); r = await post(`${JB}/save`, { name: "x" });
  await check("worker: save without a cookie -> 401 JSON invalid_link, no RPC", async () => { assert.equal(r.status, 401); assert.deepEqual(await r.json(), { ok: false, error: "invalid_link" }); assert.equal(rpcs.length, 0); });
  setup(); reply.signup_save = () => { throw new Error("boom"); }; r = await post(`${JB}/save`, { name: "x" }, { cookie: COOKIE });
  await check("worker: save backend failure -> 503 JSON, not HTML; nothing sensitive logged", async () => { assert.equal(r.status, 503); assert.equal((await r.json()).ok, false); assert.ok(!logged.join(" ").includes(TOK)); });
  setup(); const lim = { limit: async () => ({ success: false }) }; r = await post(`${JB}/save`, { name: "x" }, { cookie: COOKIE, e: { ...env, JOIN_LIMIT: lim } });
  await check("worker: JOIN_LIMIT over the limit -> 429, no RPC; an allowing binding passes; an absent binding is skipped", async () => {
    assert.equal(r.status, 429); assert.equal(rpcs.length, 0);
    setup(); reply.signup_save = { ok: true }; r = await post(`${JB}/save`, { name: "x" }, { cookie: COOKIE, e: { ...env, JOIN_LIMIT: { limit: async () => ({ success: true }) } } }); assert.equal(r.status, 200);
  });

  // ---------- POST checks ----------
  setup();
  await check("worker: POST checks - wrong Origin 403, missing Origin 403, wrong content-type 415, over 4 KB 413, wrong methods 405; no RPC", async () => {
    assert.equal((await post(`${JB}/save`, { name: "x" }, { cookie: COOKIE, origin: "https://evil.test" })).status, 403);
    assert.equal((await send(`${JB}/save`, { method: "POST", cookie: COOKIE, body: "name=x" })).status, 403);
    assert.equal((await post(`${JB}/save`, { name: "x" }, { cookie: COOKIE, ctype: "application/json" })).status, 415);
    assert.equal((await send(`${JB}/submit`, { method: "POST", cookie: COOKIE, origin: ORIGIN, body: "name=x", ctype: "text/plain" })).status, 415);
    assert.equal((await post(`${JB}/save`, { name: "x".repeat(5000) }, { cookie: COOKIE })).status, 413);
    assert.equal((await post(`${JB}/submit`, { name: "x".repeat(5000) }, { cookie: COOKIE })).status, 413);
    assert.equal((await post(JB, {}, { cookie: COOKIE })).status, 405); assert.equal((await post(`${JB}/done`, {})).status, 405);
    assert.equal((await send(JB, { method: "PUT" })).status, 405); assert.equal((await send(JB, { method: "DELETE" })).status, 405);
    const g = await send(`${JB}/save`, { cookie: COOKIE }); assert.equal(g.status, 303); assert.equal(loc(g), JB);
    assert.equal(rpcs.length, 0);
  });
  await check("worker: an unknown subpath is the 404 page", async () => assert.equal((await send(`${JB}/zzz`)).status, 404));

  // ---------- submit ----------
  const FORM = { name: "Ravi Kumar", phone: "9876500000", email: "", dob: "1980-02-03", address: "", city: "Pune", nominee: "" };
  setup(); reply.signup_submit = { ok: true, agent_name: "Asha" };
  r = collect(await post(`${JB}/submit`, FORM, { cookie: COOKIE }));
  await check("worker: POST submit -> signup_submit with the fields, guardian null, gate header; 303 to done and the cookie cleared", () => {
    assert.equal(r.status, 303); assert.equal(loc(r), `${JB}/done`); assert.match(r.headers.get("set-cookie"), /^ap_join=; Path=\/a\/demo\/join; Max-Age=0;/);
    const c = signupRpcs(); assert.equal(c.length, 1); assert.equal(c[0].name, "signup_submit"); assert.equal(c[0].key, KEY);
    assert.deepEqual(c[0].body, { slug: "demo", host: null, token: TOK, fields: FORM, guardian: null });
  });
  setup(); reply.signup_submit = { ok: true, agent_name: "Asha" };
  r = await post(`${JB}/submit`, { ...FORM, dob: "2015-01-01", guardian: "1" }, { cookie: COOKIE });
  await check("worker: submit with the guardian box ticked sends the customer_signup_guardian consent for the site locale", () => {
    const n = notices.notices.customer_signup_guardian;
    assert.deepEqual(signupRpcs()[0].body.guardian, { notice_key: "customer_signup_guardian", version: n.version, hash: n.hash.en });
  });
  setup(); reply.signup_submit = { ok: true }; r = await post(`${JB}/submit`, { ...FORM, guardian: "0" }, { cookie: COOKIE });
  await check("worker: guardian is sent only when ticked (value 1)", () => assert.equal(signupRpcs()[0].body.guardian, null));
  setup(); reply.signup_submit = { ok: false, error: "invalid", field: "phone" };
  r = collect(await post(`${JB}/submit`, { ...FORM, phone: "12<b>3" }, { cookie: COOKIE })); t = await r.text();
  await check("worker: submit invalid -> 400 re-rendered form with the field error and the typed values (escaped)", () => {
    assert.equal(r.status, 400); assert.ok(t.includes('id="e-phone"') && t.includes(dicts.en["join.err_phone"]) && t.includes('aria-invalid="true"'));
    assert.ok(t.includes('value="Ravi Kumar"') && t.includes('value="12&lt;b&gt;3"') && !t.includes("12<b>3"));
  });
  for (const [f, key] of [["name", "join.err_name"], ["email", "join.err_email"], ["dob", "join.err_dob"], ["city", "join.err_long"]]) {
    setup(); reply.signup_submit = { ok: false, error: "invalid", field: f }; r = await post(`${JB}/submit`, FORM, { cookie: COOKIE }); t = await r.text();
    await check(`worker: submit invalid field ${f} -> its message`, () => assert.ok(t.includes(`id="e-${f}"`) && t.includes(dicts.en[key])));
  }
  setup(); reply.signup_submit = { ok: false, error: "invalid", field: "guardian" }; r = await post(`${JB}/submit`, { ...FORM, dob: "2015-01-01" }, { cookie: COOKIE }); t = await r.text();
  await check("worker: submit invalid guardian -> the guardian block shown with its error", () => {
    assert.equal(r.status, 400); assert.ok(/id="guardian">/.test(t) && t.includes('id="e-guardian"') && t.includes(dicts.en["join.err_guardian"]));
    const h = (k) => dicts.en[k].replace(/&/g, "&amp;").replace(/'/g, "&#39;").replace(/"/g, "&quot;");
    assert.ok(t.includes(h("privacy.customer_signup_guardian.notice")) && t.includes(h("privacy.customer_signup_guardian.consent")));
  });
  setup(); reply.signup_submit = { ok: false, error: "invalid_link" }; r = await post(`${JB}/submit`, FORM, { cookie: COOKIE });
  await check("worker: submit on a used/expired link -> the invalid-link page and the cookie cleared", async () => { assert.ok((await r.text()).includes("This link has expired")); assert.match(r.headers.get("set-cookie"), /Max-Age=0/); });
  setup(); reply.signup_submit = { ok: false, error: "consent_required" }; r = await post(`${JB}/submit`, FORM, { cookie: COOKIE });
  await check("worker: submit before consent -> 303 back to the join page (the consent step)", () => { assert.equal(r.status, 303); assert.equal(loc(r), JB); });
  setup(); reply.signup_submit = { ok: false, error: "rate_limited" }; r = await post(`${JB}/submit`, FORM, { cookie: COOKIE });
  await check("worker: submit rate_limited -> 429 form with the message", async () => { assert.equal(r.status, 429); assert.ok((await r.text()).includes(dicts.en["join.err_rate"])); });
  setup(); reply.signup_submit = () => { throw new Error("boom"); }; r = collect(await post(`${JB}/submit`, FORM, { cookie: COOKIE })); t = await r.text();
  await check("worker: a backend failure -> 503 error page (never the shell), no typed value or token in the log", () => {
    assert.equal(r.status, 503); assert.ok(!t.includes("SHELL") && !t.includes("Ravi") && t.includes(dicts.en["join.err_generic"]));
    assert.ok(!logged.join(" ").includes(TOK) && !logged.join(" ").includes("9876500000"));
  });
  setup(); r = collect(await send(`${JB}/done`)); t = await r.text();
  await check("worker: GET done -> the thank-you page naming the agent, no signup RPC, the cookie cleared", () => {
    assert.equal(r.status, 200); assert.ok(t.includes("Thank you.") && t.includes(" has your details and will be in touch.")); assert.equal(signupRpcs().length, 0); assert.match(r.headers.get("set-cookie"), /Max-Age=0/);
  });

  // ---------- gate and failure modes ----------
  setup(); r = await send(JB, { cookie: COOKIE, e: { ASSETS: env.ASSETS } }); t = await r.text();
  await check("worker: no PORTAL_RPC_KEY -> fails closed: 503, no RPC at all", () => { assert.equal(r.status, 503); assert.equal(rpcs.length, 0); assert.ok(!t.includes("SHELL")); });
  setup(); r = await post(`${JB}/save`, { name: "x" }, { cookie: COOKIE, e: { ASSETS: env.ASSETS } });
  await check("worker: save without the key -> 503 JSON, no RPC", async () => { assert.equal(r.status, 503); assert.equal((await r.json()).ok, false); assert.equal(rpcs.length, 0); });
  setup(); reply.get_public_site = () => { throw new Error("down"); }; r = await send(JB, { cookie: COOKIE }); t = await r.text();
  await check("worker: site lookup failure -> 503 error page, never the shell", () => { assert.equal(r.status, 503); assert.ok(!t.includes("SHELL")); });
  setup(); await send(JB, { cookie: COOKIE }); reply.signup_open = { ok: true, consented: true, fields: SAVED }; await send(JB, { cookie: COOKIE });
  await check("worker: every signup_* RPC call carried x-portal-key and the token never appears in any response", async () => {
    for (const x of rpcs.filter((q) => q.name.startsWith("signup_"))) assert.equal(x.key, KEY);
    for (const x of responses) assert.ok(!(await allOut(x)).replace(/ap_join=J+; Path/g, "").includes(TOK), "token leaked");
  });
  await check("worker: RPC key is not sent to the public-site RPCs", () => { setup(); return send(JB, { cookie: COOKIE }).then(() => { for (const x of rpcs.filter((q) => q.name.startsWith("get_public"))) assert.equal(x.key, undefined); }); });

  // ---------- headers ----------
  await check("headers: every join response is no-store, noindex, with the security headers; pages carry the noindex meta", async () => {
    assert.ok(responses.length > 5);
    for (const x of responses) {
      assert.equal(x.headers.get("cache-control"), "no-store"); assert.match(x.headers.get("x-robots-tag") || "", /noindex/);
      if (/html/.test(x.headers.get("content-type") || "")) {
        for (const [k, v] of Object.entries(SECURITY_HEADERS)) if (k !== "Referrer-Policy") assert.equal(x.headers.get(k), v, k);
        const html = await x.clone().text();
        if (html.includes('class="tpl portal join"')) assert.equal(x.headers.get("referrer-policy"), "strict-origin"); // the join pages (not the 404 page) assert.ok(/<meta name="robots" content="noindex/.test(html)); assert.ok((html.match(/<script/g) || []).length <= 1); assert.ok(!/<script>|\son[a-z]+=/i.test(html.replace(/<script type="module" src="\/join-client.js"><\/script>/, "")));
      }
    }
  });

  // ---------- robots, wrangler, privacy page, build, assets ----------
  r = await send("/robots.txt"); t = await r.text();
  await check("robots.txt disallows /a/*/join and /join", () => assert.ok(t.includes("Disallow: /a/*/join\n") && t.includes("Disallow: /join\n")));
  await check("wrangler.jsonc: run_worker_first has /join and /join/*; JOIN_LIMIT is a ratelimit of 120 per 60 s with its own namespace", () => {
    const w = JSON.parse(readFileSync(new URL("web/wrangler.jsonc", root), "utf8").split("\n").filter((l) => !l.trim().startsWith("//")).join("\n"));
    assert.ok(w.assets.run_worker_first.includes("/join") && w.assets.run_worker_first.includes("/join/*"));
    const j = w.ratelimits.find((x) => x.name === "JOIN_LIMIT"); assert.deepEqual(j.simple, { limit: 120, period: 60 });
    assert.equal(new Set(w.ratelimits.map((x) => x.namespace_id)).size, w.ratelimits.length);
  });
  await check("privacy page: #signup section renders the customer_signup notice (title, what, why, who, how_long, rights) with the agent's details", () => {
    const site = normalizeSite(siteRow()), d = dicts.en, tt = makeT(d);
    const html = privacyHtml(site, tt, { notice: notices.notices.enquiry_form, catalog });
    assert.ok(/<section id="signup"><h2>Your sign-up form<\/h2>/.test(html));
    const sec = html.slice(html.indexOf('<section id="signup">'), html.indexOf("</section>", html.indexOf('<section id="signup">')));
    assert.equal((sec.match(/<p>/g) || []).length, 5); assert.ok(!sec.includes("{name}") && !sec.includes("{phone}") && !sec.includes("{email}"));
    const xss = privacyHtml(normalizeSite(siteRow({ profile: { ...siteRow().profile, full_name: hostile } })), tt, { notice: notices.notices.enquiry_form, catalog });
    assert.ok(!xss.includes(hostile));
  });
  await check("i18n: every join.* key exists in every locale, no empty values; the notice text keys too", () => {
    const keys = Object.keys(dicts.en).filter((k) => k.startsWith("join."));
    assert.ok(keys.length >= 25);
    for (const l of LOCALE_KEYS) for (const k of [...keys, ...notices.notices.customer_signup.textKeys, ...notices.notices.customer_signup_guardian.textKeys]) assert.ok(dicts[l][k] && dicts[l][k].trim(), `${l} ${k}`);
    for (const l of LOCALE_KEYS) assert.deepEqual(Object.keys(dicts[l]).filter((k) => k.startsWith("join.")), keys, "same join keys in " + l);
  });
  await check("build: build-web.sh copies join-client.js and join-age.js; join.css is in styles/; the client is small and imports only join-age.js", () => {
    const sh = readFileSync(new URL("scripts/build-web.sh", root), "utf8");
    assert.ok(/ join-client\.js /.test(sh) && / join-age\.js /.test(sh));
    const c = readFileSync(new URL("web/join-client.js", root), "utf8"); assert.ok(c.length < 6 * 1024, `${c.length}`);
    assert.deepEqual([...c.matchAll(/from "([^"]+)"/g)].map((m) => m[1]), ["./join-age.js"]);
    assert.ok(!/localStorage|innerHTML|eval\(/.test(c));
  });
  await check("join.css uses tokens only (no raw px/rem in spacing or type)", () => {
    const css = readFileSync(new URL("web/styles/join.css", root), "utf8"); assert.ok(!/(margin|padding|gap|font-size)[^;{}]*\d(px|rem|em)/.test(css));
  });

  globalThis.fetch = realFetch; console.error = realErr;
  if (fails.length) throw new Error(`${fails.length} join check(s) failed:\n - ${fails.join("\n - ")}`);
}
