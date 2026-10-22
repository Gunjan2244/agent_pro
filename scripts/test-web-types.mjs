// Services and the enquiry form follow the agent's insurance types (DECISIONS #93): the catalog's insuranceType per
// interest, the new interests' labels and fallback image, the "Interested in" options (SSR and the client render
// agree), and the Node mirror of the type-driven seed (scripts/site-seed.mjs). Run by scripts/test-web.mjs.
import assert from "node:assert/strict";

const NEW = ["motor", "home", "travel", "business", "accident"];

export async function run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }) {
  const { TEMPLATES, familyOf } = await import("../web/templates/index.js");
  const { formInterests } = await import("../web/sections/util.js");
  const { makeCtx } = await import("../web/sections/index.js");
  const { starterContent, servicesFor, mixFor } = await import("./site-seed.mjs");
  const registry = R("shared/templates/index.json");
  const seed = R("shared/site-seed.json");
  const MANIFEST = R("web/images/manifest.json");
  const tok = (key, images = MANIFEST) => ({ accent: "#1F4AA8", schema, catalog, family: familyOf(registry, key), images, seed: "demo", today: "2026-10-18" });
  const html = (site, key, l = site.locale || "en", images) => TEMPLATES[key](site, makeT(dicts[l]), tok(key, images));
  const section = (h, id) => { const i = h.indexOf(`id="${id}"`); return i < 0 ? "" : h.slice(i, h.indexOf("</section>", i)); };
  const options = (h) => [...(h.match(/<select id="f-interest"[^>]*>([\s\S]*?)<\/select>/) || ["", ""])[1].matchAll(/<option value="([^"]*)"/g)].map((m) => m[1]);
  const withServices = (base, services) => ({ ...base, content: { ...base.content, services }, offers: { featured: null, items: [] } });
  const i = (k) => catalog.interests.find((x) => x.key === k);

  ok("catalog: every interest names its insurance type (life plans, health, the 5 new ones; other has none, and stays last)", () => {
    for (const k of ["term", "endowment", "money_back", "child", "pension", "ulip"]) assert.equal(i(k).insuranceType, "life", k);
    assert.equal(i("health").insuranceType, "health");
    for (const k of NEW) assert.equal(i(k).insuranceType, k, k);
    assert.equal(i("other").insuranceType, null);
    assert.equal(catalog.interests.slice().sort((a, b) => a.order - b.order).at(-1).key, "other");
    for (const k of NEW) for (const l of LOCALE_KEYS) assert.ok(dicts[l][i(k).labelKey], `${l} ${k}`);
  });

  ok("seed: every insurance type has seed interests of that type, each with a description in every locale", () => {
    for (const t of catalog.insuranceTypes) {
      const list = seed.insuranceTypes.seedInterests[t.key];
      assert.ok(list && list.length, t.key);
      for (const k of list) {
        assert.equal(i(k).insuranceType, t.key, k);
        for (const key of seed.insuranceTypes.serviceDescriptions[k].variants) for (const l of LOCALE_KEYS) assert.ok(dicts[l][key], `${l} ${key}`);
      }
    }
  });

  ok("site-seed.mjs mirrors SiteSeed: mixes, round-robin offered, grouped items, capped at maxItems", () => {
    assert.equal(mixFor([], seed, catalog), "life");
    assert.equal(mixFor(["life"], seed, catalog), "life");
    assert.equal(mixFor(["life", "motor"], seed, catalog), "general");
    assert.deepEqual(servicesFor(["motor", "life", "health"], 8, seed, catalog), {
      offered: ["term", "health", "motor", "endowment", "child", "pension"], items: ["term", "endowment", "child", "pension", "health", "motor"],
    });
    const all = servicesFor(catalog.insuranceTypes.map((t) => t.key), 8, seed, catalog);
    assert.deepEqual(all.items, ["term", "endowment", "health", "motor", "home", "travel", "business", "accident"]);
  });

  const agent = { agentId: "00000000-0000-0000-0000-000000000001", templateKey: "classic", locale: "en", dicts,
    profile: { fullName: "Priya Nair", city: "Kochi", designation: "", phone: "9847012345", email: "p@example.com" } };
  for (const l of LOCALE_KEYS) ok(`seed (${l}): a motor agent gets motor services and the general copy; a life agent the life copy`, () => {
    const motor = starterContent({ ...agent, locale: l, types: ["motor"] });
    const life = starterContent({ ...agent, locale: l, types: ["life"] });
    assert.deepEqual(motor.services.offered, ["motor"]);
    assert.deepEqual(motor.services.items.map((x) => x.interest), ["motor"]);
    assert.equal(motor.services.items[0].description, dicts[l]["seed.services.motor"]);
    assert.notEqual(motor.hero.subheadline, life.hero.subheadline);
    assert.notEqual(motor.about.bio, life.about.bio);
    assert.deepEqual(life.services.offered, ["term", "endowment", "child", "pension"]);
    assert.ok(!JSON.stringify(motor.faq).includes(dicts[l]["seed.faq.term_vs_savings.question"]), "no life-only FAQ");
    assert.equal(motor.hero.headline, life.hero.headline, "the greeting is the same");
  });

  ok("form: 'Interested in' lists the site's offered interests plus Other, in catalog order (blank first)", () => {
    const base = fx("sample-en");
    const h = html(withServices(base, { offered: ["motor", "term"], items: [{ interest: "travel" }] }), "classic");
    assert.deepEqual(options(h), ["", "term", "motor", "travel", "other"]);
  });

  ok("form: an offer's interest is an option too (its Enquire link preselects it); no services → every active interest", () => {
    const base = fx("sample-offers");
    const site = { ...base, content: { ...base.content, services: { offered: ["motor"] } } };
    const ctx = makeCtx(site, makeT(dicts.en), tok("spotlight"), "spotlight");
    const keys = formInterests(ctx).map((x) => x.key);
    for (const o of ctx.offers.grid) { const k = o.interest ?? (o.cta && o.cta.interest); if (k) assert.ok(keys.includes(k), k); }
    assert.ok(keys.includes("motor") && keys.includes("other"));
    const none = withServices(fx("sample-en"), {});
    assert.deepEqual(options(html(none, "classic")), ["", ...catalog.interests.filter((x) => !x.deprecated).sort((a, b) => a.order - b.order).map((x) => x.key)]);
  });

  for (const k of TEMPLATE_KEYS) ok(`${k}: the form options are the same in every template, and every hero chip is an option`, () => {
    const s = withServices(fx("sample-en"), { offered: ["motor", "health"], items: [{ interest: "motor", description: "Car cover" }] });
    const h = html(s, k);
    assert.deepEqual(options(h), ["", "health", "motor", "other"]);
    for (const m of h.matchAll(/data-interest="([a-z_]+)"/g)) assert.ok(options(h).includes(m[1]), m[1]);
  });

  for (const l of LOCALE_KEYS) ok(`services (${l}): the new interests render with their labels and the generic image (alt="") until theirs exists`, () => {
    const s = { ...withServices(fx("sample-" + l), { offered: NEW, items: NEW.map((interest) => ({ interest })) }), locale: l };
    const sec = section(html(s, "classic", l), "services");
    for (const k of NEW) assert.ok(sec.includes(`<h3>${dicts[l][i(k).labelKey]}</h3>`), k);
    const generic = catalog.interests.find((x) => x.insuranceType === null).image;
    assert.ok(sec.includes(`src="/images/${generic}-800.webp"`) && /alt=""/.test(sec), "fallback image, decorative");
    for (const k of NEW) assert.ok(!sec.includes(`/images/interest-${k}-`), `${k}: not shipped yet`);
    // Once an illustration is in the manifest, it is used, with its alt text.
    const shipped = section(html(s, "classic", l, { ...MANIFEST, "interest-motor": { w: 800, h: 450, sizes: [400, 800] } }), "services");
    assert.ok(shipped.includes('src="/images/interest-motor-800.webp"') && shipped.includes(`alt="${dicts[l]["site.image_alt_interest"].replace("{interest}", dicts[l][i("motor").labelKey])}"`));
  });
}
