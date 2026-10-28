// "For my customers" (DECISIONS #102): every template tells visitors that the agent's customers can sign in and see
// their policies - the #portal section and the header's "Customer login" link (in the top bar, the hero or the
// profile card, by template: DECISIONS #117) - in both languages, placed before contact, hideable, host-aware,
// escaped. Run by scripts/test-web.mjs.
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { esc } from "../web/sections/util.js";

export async function run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }) {
  const { TEMPLATES, familyOf } = await import("../web/templates/index.js");
  const { placePortal, visibleKeys } = await import("../web/sections/index.js");
  const registry = R("shared/templates/index.json");
  const tok = (key, privacyHref = "/a/asha/privacy") => ({ accent: "#1F4AA8", schema, catalog, family: familyOf(registry, key), images: R("web/images/manifest.json"), seed: "demo", today: "2026-10-18", privacyHref });
  const base = (l) => ({ ...fx("sample-" + l) });
  const html = (site, key, l = site.locale || "en", privacy) => TEMPLATES[key](site, makeT(dicts[l]), tok(key, privacy));
  const section = (h, id) => { const i = h.indexOf(`id="${id}"`); return i < 0 ? "" : h.slice(i, h.indexOf("</section>", i)); };
  // the page top: the template's top bar (if any) and the <header> (hero)
  const header = (h) => h.slice(0, h.indexOf("</header>"));
  const LOGIN = /class="(?:hero|tb|pc)-login"/;
  const login = (hd, href, label) => new RegExp(`class="(?:hero|tb|pc)-login"><a href="${href}" rel="nofollow">${label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}</a>`).test(hd);
  const order = (h) => [...h.matchAll(/<(?:section|header)[^>]* id="([a-z]+)"/g)].map((m) => m[1]);
  const count = (h, re) => (h.match(re) || []).length;

  ok("promo: placePortal puts it right before contact; an order that names it is kept", () => {
    assert.deepEqual(placePortal(["hero", "about", "contact"]), ["hero", "about", "portal", "contact"]);
    assert.deepEqual(placePortal(["hero", "about"]), ["hero", "about", "portal"]);
    assert.deepEqual(placePortal(["contact", "hero"]), ["portal", "contact", "hero"]);
    assert.deepEqual(placePortal(["hero", "portal", "about", "contact"]), ["hero", "portal", "about", "contact"]);
    assert.deepEqual(visibleKeys({ sectionOrder: ["hero", "faq"] }, schema, { portal: true }), ["hero", "companies", "faq", "portal", "contact"]);
    assert.ok(!visibleKeys({ sectionOrder: ["hero", "faq"] }, schema, { portal: false }).includes("portal"));
  });

  for (const key of TEMPLATE_KEYS) for (const l of LOCALE_KEYS) {
    const d = dicts[l], L = `${key}/${l}`;
    const on = () => ({ ...base(l), portal: true });
    ok(`promo ${L}: portal on -> the section (one h2, 4 points, nofollow sign-in, WhatsApp line) and the hero link`, () => {
      const h = html(on(), key, l), s = section(h, "portal");
      assert.ok(s, "no #portal section");
      assert.ok(s.includes(`<h2>${esc(d["site.portal_heading"])}</h2>`) && s.includes(esc(d["site.portal_lead"])));
      assert.equal(count(s, /<h2/g), 1); assert.equal(count(s, /<h3/g), 0);
      for (const k of ["policies", "dues", "payments", "ask"]) assert.ok(s.includes(esc(d["site.portal_point_" + k])), k);
      assert.equal(count(s, /<li>/g), 4); assert.equal(count(s, /<svg/g), 4);
      assert.ok(s.includes(`<a class="btn" href="/a/asha/my/login" rel="nofollow">${esc(d["site.portal_sign_in"])}</a>`));
      assert.ok(s.includes(esc(d["site.portal_first_time"])) && s.includes("https://wa.me/919876543210?text="));
      const hd = header(h);
      assert.ok(login(hd, "/a/asha/my/login", esc(d["site.header_portal"])), "header login link");
      assert.ok(!/<nav|<h2|<h3/.test(hd), "hero has no nav / h2 / h3");
      assert.equal(count(h, /<h1[ >]/g), 1); assert.equal(count(h, /<nav/g), 1, "the call bar is the only nav");
      assert.ok(h.includes(`href="/a/asha/my">${esc(d["site.footer_portal"])}`), "footer link kept");
      assert.ok(!/(?<![\w.])(site|portal|section)\.[a-z_]+/.test(h.replace(/<style[\s\S]*?<\/style>/g, "").replace(/https?:\/\/[^\s"']+/g, "")), "raw i18n key");
    });
    ok(`promo ${L}: portal false or missing -> neither the section nor the header link`, () => {
      for (const site of [{ ...base(l), portal: false }, (({ portal, ...x }) => x)(base(l))]) {
        const h = html(site, key, l);
        assert.ok(!h.includes('id="portal"') && !LOGIN.test(h) && !h.includes("/login"));
      }
    });
    ok(`promo ${L}: hidden via content.hidden -> no section, the header link stays`, () => {
      const s = on(); s.content = { ...s.content, hidden: [...(s.content.hidden || []), "portal"] };
      const h = html(s, key, l);
      assert.ok(!h.includes('id="portal"')); assert.ok(LOGIN.test(header(h)));
    });
    ok(`promo ${L}: placed before contact, with and without a saved order; a saved order that names it is followed`, () => {
      const s = on(), saved = order(html(s, key, l));
      assert.equal(saved.indexOf("portal") + 1, saved.indexOf("contact"), saved.join());
      const noOrder = on(); noOrder.content = { ...noOrder.content }; delete noOrder.content.sectionOrder;
      const o = order(html(noOrder, key, l));
      assert.equal(o.indexOf("portal") + 1, o.indexOf("contact"), o.join());
      const named = on(); named.content = { ...named.content, sectionOrder: ["hero", "portal", "about", "services", "faq", "contact"], hidden: [] };
      const n = order(html(named, key, l));
      assert.deepEqual(n.filter((x) => ["hero", "portal", "about", "services", "faq", "contact"].includes(x)), ["hero", "portal", "about", "services", "faq", "contact"]);
    });
    ok(`promo ${L}: custom host -> /my/login; the ?s= fallback (no path) leaves the links out`, () => {
      const h = html(on(), key, l, "/privacy");
      assert.ok(section(h, "portal").includes('href="/my/login"') && header(h).includes('href="/my/login"') && !h.includes("/a/asha"));
      const f = html(on(), key, l, "?s=asha&page=privacy");
      assert.ok(f.includes('id="portal"') && !f.includes("/login") && !section(f, "portal").includes("wa.me") && !LOGIN.test(header(f)));
    });
    ok(`promo ${L}: the in-app preview (privacyHref "#") shows the sign-in button and the hero link as inert "#" links`, () => {
      const p = html(on(), key, l, "#");
      assert.ok(section(p, "portal").includes('<a class="btn" href="#"'), "no sign-in button in the preview");
      assert.ok(/class="(?:hero|tb|pc)-login"><a href="#"/.test(header(p)), "no header login link in the preview");
      assert.ok(!p.includes("/login") && !p.includes("/my"));
      const off = html({ ...on(), portal: false }, key, l, "#");
      assert.ok(!LOGIN.test(off) && !off.includes('id="portal"'));
    });
    ok(`promo ${L}: the WhatsApp text is URL-encoded and the name escaped`, () => {
      const s = on(); s.profile = { ...s.profile, full_name: 'A&B "<i>" Verma' };
      const h = html(s, key, l), sec = section(h, "portal");
      const href = (sec.match(/<p class="pp-first"><a href="([^"]+)"/) || [])[1];
      assert.ok(href, "no first-time link");
      const msg = decodeURIComponent(href.replace(/&amp;/g, "&").split("?text=")[1]);
      assert.equal(msg, d["portal.ask_link_message"].replace("{name}", 'A&B "<i>" Verma'));
      assert.ok(href.includes("text=" + encodeURIComponent(msg)) && !/[<>"' ]/.test(href.replace(/&amp;/g, "")));
      assert.ok(!sec.includes("<i>"));
    });
    ok(`promo ${L}: no number -> no WhatsApp line, the sign-in button stays`, () => {
      const s = on(); s.content = { ...s.content, contact: { ...s.content.contact, whatsapp: "", phone: "" } }; s.profile = { ...s.profile, phone: "" };
      const sec = section(html(s, key, l), "portal");
      assert.ok(sec.includes("rel=\"nofollow\"") && !sec.includes("wa.me") && !sec.includes("pp-first"));
    });
  }
  ok("promo: the section is skipped while the heading key is missing from the dictionary", () => {
    const { "site.portal_heading": _, ...rest } = dicts.en;
    const h = TEMPLATES.classic({ ...base("en"), portal: true }, makeT(rest), tok("classic"));
    assert.ok(!h.includes('id="portal"'));
  });
  ok("promo: the XSS fixture stays escaped with the portal on", () => {
    for (const key of TEMPLATE_KEYS) {
      const h = html({ ...fx("sample-xss"), portal: true }, key, "en");
      assert.ok(!/<script/i.test(h));
    }
  });
  ok("promo: the demo fixtures have the portal on, one keeps it off", () => {
    const flags = readdirSync(new URL("../web/dev-fixtures/", import.meta.url)).filter((f) => /^sample-(en|hi|offers|seeded|empty|xss)\.json$/.test(f)).map((f) => R("web/dev-fixtures/" + f).portal === true);
    assert.ok(flags.includes(true) && flags.includes(false));
  });
  ok("promo: CSS stays on tokens (no raw sizes, colours) in the promo rules", () => {
    const css = ["web/styles/base.css", ...TEMPLATE_KEYS.map((k) => `web/templates/${k}/style.css`)].map((f) => readFileSync(new URL("../" + f, import.meta.url), "utf8")).join("\n");
    const rules = css.split("\n").filter((x) => /\.pp|-login/.test(x));
    assert.ok(rules.length > 10);
    for (const r of rules) assert.ok(!/#[0-9a-fA-F]{3,6}\b|\b\d+(rem|em)\b/.test(r), r);
  });
}
