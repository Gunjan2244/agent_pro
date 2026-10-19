// Add to home screen checks (DECISIONS #65): manifest, Worker, icons, contact button, install.js. Run by scripts/test-web.mjs.
import assert from "node:assert/strict";
import { readFileSync, existsSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { spawnSync } from "node:child_process";

export async function run({ ok, R, dicts, catalog, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }) {
  const { manifestFor, manifestHref, appBase, appNames, swatchFor, parseConfig, iconPath, ICON_SIZES, MANIFEST_PATH } = await import("../web/manifest.js");
  const tokens = R("shared/design-tokens.json");
  const site = fx("sample-en");

  // --- manifest ---
  const m = manifestFor(site, { slug: "asha-verma" }, { catalog, tokens });
  ok("manifest (free link): id/start_url/scope /a/<slug>/, standalone, colours from tokens, no related apps", () => {
    assert.equal(m.id, "/a/asha-verma/"); assert.equal(m.start_url, m.id); assert.equal(m.scope, m.id);
    assert.equal(m.display, "standalone"); assert.equal(m.prefer_related_applications, false);
    assert.equal(m.background_color, tokens.color.light.background); assert.equal(m.theme_color, tokens.color.light.surface);
    assert.equal(m.name, site.profile.full_name); assert.equal(m.lang, "en");
  });
  ok("manifest: 192 + 512 PNG icons of the site's swatch, each as any and maskable", () => {
    assert.deepEqual(ICON_SIZES, [192, 512]);
    for (const n of ICON_SIZES) for (const purpose of ["any", "maskable"]) assert.ok(m.icons.some((i) => i.src === iconPath(site.accent, n) && i.sizes === `${n}x${n}` && i.type === "image/png" && i.purpose === purpose), `${n} ${purpose}`);
    assert.equal(m.icons.length, 4);
    assert.equal(manifestFor({ ...site, accent: "nope" }, { slug: "x" }, { catalog, tokens }).icons[0].src, iconPath(swatchFor(catalog, null).key, 192), "unknown accent -> first swatch");
  });
  ok("manifest (custom domain): scope /, link /manifest.webmanifest", () => {
    const d = manifestFor(site, { host: "asha.in" }, { catalog, tokens });
    assert.equal(d.scope, "/"); assert.equal(d.start_url, "/");
    assert.equal(manifestHref({ host: "asha.in" }), MANIFEST_PATH);
    assert.equal(manifestHref({ slug: "a b" }), "/manifest.webmanifest?s=a%20b"); assert.equal(appBase({ slug: "a b" }), "/a/a%20b/");
  });
  ok("manifest: short_name is the name, or the first name when over 12 characters; Hindi site -> lang hi", () => {
    assert.equal(appNames({ profile: { full_name: "Asha Rao" } }).short, "Asha Rao");
    assert.equal(appNames({ profile: { full_name: "Ramesh Kumar Sharma" } }).short, "Ramesh");
    assert.equal(manifestFor(fx("sample-hi"), { slug: "x" }, { catalog, tokens }).lang, "hi");
  });
  ok("manifest: agent text is JSON data only (XSS fixture survives as plain strings)", () => {
    const x = JSON.parse(JSON.stringify(manifestFor(fx("sample-xss"), { slug: "x" }, { catalog, tokens })));
    assert.equal(typeof x.name, "string"); assert.ok(x.name.length <= 45);
  });
  ok("parseConfig reads what build-web.sh writes (quotes and backslashes escaped)", () => {
    const text = 'window.AGENTPRO_CONFIG = {\n  SUPABASE_URL: "https://p.supabase.co",\n  SUPABASE_ANON_KEY: "a\\"b\\\\c",\n  SITE_BASE_URL: "https://x.dev"\n};\n';
    assert.deepEqual(parseConfig(text), { SUPABASE_URL: "https://p.supabase.co", SUPABASE_ANON_KEY: 'a"b\\c', SITE_BASE_URL: "https://x.dev", BUILD_TIME: "" });
    assert.equal(parseConfig("").SUPABASE_URL, "");
    assert.equal(parseConfig(text.replace("\n};", ',\n  BUILD_TIME: "2026-10-18T10:00:00Z"\n};')).BUILD_TIME, "2026-10-18T10:00:00Z");
  });

  // --- Worker ---
  {
    const worker = (await import("../web/worker.js")).default;
    const files = {
      "/config.js": 'window.AGENTPRO_CONFIG = {\n  SUPABASE_URL: "https://s.test",\n  SUPABASE_ANON_KEY: "anon",\n  SITE_BASE_URL: "https://sites.test"\n};\n',
      "/shared/catalog.json": JSON.stringify(catalog), "/shared/design-tokens.json": JSON.stringify(tokens),
    };
    const assets = [];
    const env = { ASSETS: { fetch: async (req) => { const p = new URL(req.url || req).pathname; assets.push(p); return files[p] ? new Response(files[p]) : new Response("asset:" + p, { status: p === "/index.html" ? 200 : 404 }); } } };
    const realFetch = globalThis.fetch;
    let rpcs = [], rpcReply = () => new Response(JSON.stringify([{ published_content: site.content, template_key: "classic", accent: "teal", locale: "en", profile: site.profile }]));
    globalThis.fetch = async (u, o) => { rpcs.push({ u: String(u), body: JSON.parse(o.body), key: o.headers.apikey }); return rpcReply(); };
    const get = (u, method = "GET") => worker.fetch(new Request(u, { method }), env);
    const live = await get("https://sites.test/manifest.webmanifest?s=asha-verma");
    const liveJson = await live.json();
    ok("worker: published site -> 200 manifest+json from get_public_site(slug) with the anon key from config.js", () => {
      assert.equal(live.status, 200); assert.match(live.headers.get("content-type"), /^application\/manifest\+json/);
      assert.equal(live.headers.get("x-content-type-options"), "nosniff"); assert.match(live.headers.get("cache-control"), /max-age=300/);
      assert.equal(rpcs[0].u, "https://s.test/rest/v1/rpc/get_public_site"); assert.deepEqual(rpcs[0].body, { slug: "asha-verma" }); assert.equal(rpcs[0].key, "anon");
      assert.equal(liveJson.scope, "/a/asha-verma/"); assert.equal(liveJson.icons[0].src, iconPath("teal", 192));
    });
    rpcs = [];
    const dom = await get("https://asha.in/manifest.webmanifest");
    const domJson = await dom.json();
    ok("worker: custom domain -> get_public_site_by_host(host), scope /", () => {
      assert.equal(dom.status, 200); assert.deepEqual(rpcs[0].body, { host: "asha.in" }); assert.ok(rpcs[0].u.endsWith("/get_public_site_by_host"));
      assert.equal(domJson.scope, "/");
    });
    rpcReply = () => new Response("null");
    const gone = await get("https://sites.test/manifest.webmanifest?s=lapsed");
    ok("worker: unpublished / lapsed / unknown site -> 404, so it can't be installed", () => assert.equal(gone.status, 404));
    rpcs = [];
    const bad = await Promise.all([get("https://sites.test/manifest.webmanifest?s=Bad%20Slug"), get("https://sites.test/manifest.webmanifest")]);
    ok("worker: malformed or missing slug -> 404 without calling the RPC", () => { assert.deepEqual(bad.map((r) => r.status), [404, 404]); assert.equal(rpcs.length, 0); });
    rpcReply = () => new Response("{}", { status: 500 });
    const down = await get("https://sites.test/manifest.webmanifest?s=asha-verma");
    ok("worker: backend error -> 503, not cached", () => { assert.equal(down.status, 503); assert.equal(down.headers.get("cache-control"), "no-store"); });
    const post = await get("https://sites.test/manifest.webmanifest?s=asha-verma", "POST");
    assets.length = 0;
    const page = await get("https://sites.test/index.html");
    const pageText = await page.text();
    ok("worker: POST -> 405; any other path is handed to static assets untouched", () => {
      assert.equal(post.status, 405); assert.equal(page.status, 200); assert.equal(pageText, "asset:/index.html"); assert.deepEqual(assets, ["/index.html"]);
    });
    globalThis.fetch = realFetch;
  }

  // --- icons ---
  {
    const { iconPng, inFigure, SIZES } = await import("./gen-icons.mjs");
    ok("icons: valid PNG of the requested size", () => {
      const b = iconPng("#1F4AA8", 192);
      assert.deepEqual([...b.subarray(0, 8)], [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
      assert.equal(b.toString("ascii", 12, 16), "IHDR"); assert.equal(b.readUInt32BE(16), 192); assert.equal(b.readUInt32BE(20), 192);
      assert.deepEqual(SIZES, [192, 512]);
    });
    ok("icons: the figure stays inside the maskable safe zone (radius 0.4) and the corners are background", () => {
      for (let i = 0; i <= 200; i++) for (let j = 0; j <= 200; j++) {
        const x = i / 200, y = j / 200;
        if (inFigure(x, y)) assert.ok(Math.hypot(x - 0.5, y - 0.5) <= 0.4, `${x},${y}`);
      }
      assert.ok(inFigure(0.5, 0.4) && inFigure(0.5, 0.65) && !inFigure(0.02, 0.02));
    });
    const out = mkdtempSync(tmpdir() + "/icons-");
    const r = spawnSync("node", [new URL("./gen-icons.mjs", import.meta.url).pathname, out], { encoding: "utf8" });
    ok("icons: gen-icons.mjs writes 192 and 512 for every catalog swatch", () => {
      assert.equal(r.status, 0, r.stderr);
      for (const s of catalog.accentSwatches) for (const n of SIZES) assert.ok(existsSync(`${out}/${s.key}-${n}.png`), s.key + n);
    });
    rmSync(out, { recursive: true, force: true });
  }

  // --- page: contact button and install.js ---
  for (const tpl of TEMPLATE_KEYS) {
    const { render } = await import(`../web/templates/${tpl}/render.js`);
    const tk = { accent: catalog.accentSwatches[0].hex, schema: R("shared/site-schema.json"), catalog };
    ok(`${tpl}: "Add to home screen" button rendered hidden; absent when contact.showInstallButton is off`, () => {
      for (const loc of LOCALE_KEYS) {
        const html = render(fx("sample-" + loc), makeT(dicts[loc]), tk);
        assert.ok(html.includes(`<div class="install" id="install" hidden><button class="btn ghost" type="button" id="install-btn">${dicts[loc]["site.install_button"]}</button>`), loc);
      }
      const s = fx("sample-en"); s.content = { ...s.content, contact: { ...s.content.contact, showInstallButton: false } };
      assert.ok(!render(s, makeT(dicts.en), tk).includes('id="install"'));
    });
  }
  ok("schema: contact.showInstallButton is a toggle, default on, labelled in EN and HI", () => {
    const f = R("shared/site-schema.json").sections.find((s) => s.key === "contact").fields.find((x) => x.key === "showInstallButton");
    assert.equal(f.type, "toggle"); assert.equal(f.default, true);
    for (const l of LOCALE_KEYS) assert.ok(dicts[l][f.labelKey], l);
  });
  {
    const { wireInstall, sheetHtml, howToKey, isIos } = await import("../web/install.js");
    const t = makeT(dicts.en);
    // minimal fake DOM: elements by id, listeners, hidden flags
    const el = (id) => ({ id, hidden: true, textContent: "", l: {}, addEventListener(e, f) { this.l[e] = f; }, querySelector: (s) => els[s.slice(1)] });
    let els;
    const mk = ({ box = true, ua = "Mozilla/5.0 (Linux; Android 14) Chrome/140", coarse = true, standalone = false } = {}) => {
      els = {};
      if (box) for (const id of ["install", "install-btn", "install-how"]) els[id] = el(id);
      const winL = {};
      const doc = {
        getElementById: (id) => els[id] || null, querySelector: () => tpl, body: null,
      };
      const tpl = { insertAdjacentHTML(_, html) { this.html = html; for (const id of ["install-sheet", "install-sheet-go", "install-sheet-how", "install-sheet-close"]) els[id] = el(id); els["install-sheet"].hidden = false; } };
      const win = { navigator: { userAgent: ua, platform: "Linux", maxTouchPoints: 5 }, matchMedia: (q) => ({ matches: q.includes("standalone") ? standalone : coarse }), addEventListener(e, f) { winL[e] = f; } };
      return { doc, win, winL, tpl };
    };
    const prompt = () => { const e = { prevented: false, prompted: 0, preventDefault() { this.prevented = true; }, prompt() { this.prompted++; }, userChoice: Promise.resolve({ outcome: "accepted" }) }; return e; };

    let w = mk({ standalone: true });
    ok("install: nothing is wired inside the installed app (display-mode standalone)", () => assert.equal(wireInstall({ t, doc: w.doc, win: w.win }), null));

    w = mk();
    const api = wireInstall({ t, name: "Asha", doc: w.doc, win: w.win });
    ok("install (phone): button shown even before Chrome offers a prompt", () => assert.equal(els.install.hidden, false));
    await els["install-btn"].l.click();
    ok("install (phone, no prompt yet): tapping shows the browser-menu steps", () => { assert.equal(els["install-how"].hidden, false); assert.equal(els["install-how"].textContent, dicts.en["site.install_menu"]); });
    const e1 = prompt(); w.winL.beforeinstallprompt(e1);
    await els["install-btn"].l.click();
    ok("install: beforeinstallprompt is captured (no mini-infobar) and the button opens Chrome's prompt once", () => { assert.ok(e1.prevented); assert.equal(e1.prompted, 1); });
    w.winL.appinstalled();
    ok("install: after appinstalled the button hides", () => assert.equal(els.install.hidden, true));
    assert.ok(api.install);

    w = mk({ coarse: false });
    wireInstall({ t, doc: w.doc, win: w.win });
    ok("install (desktop): button hidden until the browser offers a prompt", () => assert.equal(els.install.hidden, true));
    w.winL.beforeinstallprompt(prompt());
    ok("install (desktop): shown once the prompt is available", () => assert.equal(els.install.hidden, false));

    w = mk({ box: false });
    wireInstall({ t, name: "Asha <b>", forced: true, doc: w.doc, win: w.win });
    ok("install ?install=1: the sheet opens even with the button turned off; no prompt yet -> menu steps, no Install button", () => {
      assert.ok(w.tpl.html.includes('id="install-sheet"') && w.tpl.html.includes("Add Asha &lt;b&gt; to your home screen"));
      assert.equal(els["install-sheet-go"].hidden, true); assert.equal(els["install-sheet-how"].hidden, false);
    });
    const e2 = prompt(); w.winL.beforeinstallprompt(e2);
    ok("install ?install=1: when the prompt arrives, Install appears and the steps hide", () => { assert.equal(els["install-sheet-go"].hidden, false); assert.equal(els["install-sheet-how"].hidden, true); });
    await els["install-sheet-go"].l.click(); els["install-sheet-close"].l.click();
    ok("install ?install=1: Install prompts, Close hides the sheet", () => { assert.equal(e2.prompted, 1); assert.equal(els["install-sheet"].hidden, true); });

    ok("install: iPhone/iPad (incl. iPadOS desktop UA) get the Share-sheet steps", () => {
      assert.equal(howToKey({ userAgent: "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)" }), "site.install_ios");
      assert.ok(isIos({ userAgent: "Mozilla/5.0 (Macintosh)", platform: "MacIntel", maxTouchPoints: 5 }));
      assert.equal(howToKey({ userAgent: "Android", platform: "Linux" }), "site.install_menu");
    });
    ok("install: sheet text in both locales, no unreplaced placeholders", () => {
      for (const l of LOCALE_KEYS) assert.ok(!/\{[a-z]+\}/.test(sheetHtml(makeT(dicts[l]), "X")), l);
    });
  }
  ok("hydrate.js / client.js: ?install=1 skips the view count; fallback adds manifest link + icons; no history.replaceState (the Worker 301s /a/<slug>)", () => {
    const h = readFileSync(new URL("../web/hydrate.js", import.meta.url), "utf8"), c = readFileSync(new URL("../web/client.js", import.meta.url), "utf8");
    assert.ok(/if \(q\.get\("install"\) !== "1"\) api\.recordViewOnce\(target, backend\)/.test(h));
    assert.ok(/if \(q\.get\("install"\) !== "1"\) recordView\(target\)/.test(c));
    assert.ok(c.includes('link("manifest", manifestHref(target))') && c.includes('link("apple-touch-icon"'));
    assert.ok(!h.includes('link("manifest"'), "hydrate must not re-add the head links the Worker already rendered");
    for (const f of ["app.js", "client.js", "hydrate.js"]) assert.ok(!readFileSync(new URL("../web/" + f, import.meta.url), "utf8").includes("history.replaceState"), f);
  });
  const dist = new URL("../web/dist/", import.meta.url).pathname;
  if (existsSync(dist + "index.html")) ok("dist: icons for every swatch, manifest.js + install.js copied, worker.js not shipped as an asset", () => {
    for (const s of catalog.accentSwatches) for (const n of [192, 512]) assert.ok(existsSync(`${dist}icons/${s.key}-${n}.png`));
    assert.ok(existsSync(dist + "manifest.js") && existsSync(dist + "install.js") && existsSync(dist + "hydrate.js") && existsSync(dist + "client.js")); assert.ok(!existsSync(dist + "worker.js") && !existsSync(dist + "ssr.js") && !existsSync(dist + "headers.js"));
  });
  if (existsSync(dist + "index.html")) ok("dist: config.js carries an ISO BUILD_TIME (Last-Modified after a deploy); web/images and its manifest are copied", () => {
    const cfg = parseConfig(readFileSync(dist + "config.js", "utf8"));
    assert.match(cfg.BUILD_TIME, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/); assert.ok(Math.abs(Date.parse(cfg.BUILD_TIME) - Date.now()) < 86400000 * 365);
    assert.deepEqual(JSON.parse(readFileSync(dist + "images/manifest.json", "utf8")), R("web/images/manifest.json"));
  });
}
