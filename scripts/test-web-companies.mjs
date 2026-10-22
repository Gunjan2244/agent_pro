// "Companies I represent" (DECISIONS #90): the payload's approved companies as plain text with their insurance types,
// in every template and locale; normalisation (names and type keys only), placement, hiding, escaping and JSON-LD.
// Run by scripts/test-web.mjs.
import assert from "node:assert/strict";

// Until shared/i18n has the heading, the section renders nothing (a raw key never reaches a site); these checks then
// supply it so they still exercise the markup. The proposed copy is in WEB_RENDERER.md.
const HEADING = "site.companies_heading";
const PROPOSED = { en: "Companies I represent", hi: "मैं इन कंपनियों का प्रतिनिधि हूँ" };

export async function run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }) {
  const { TEMPLATES, familyOf } = await import("../web/templates/index.js");
  const { normalizeSite, normalizeCompanies } = await import("../web/api.js");
  const { headData } = await import("../web/head.js");
  const { esc } = await import("../web/sections/util.js");
  const registry = R("shared/templates/index.json");
  const withHead = (l) => ({ ...dicts[l], [HEADING]: dicts[l][HEADING] || PROPOSED[l] });
  const tok = (key) => ({ accent: "#1F4AA8", schema, catalog, family: familyOf(registry, key), images: R("web/images/manifest.json"), seed: "demo", today: "2026-10-18" });
  const html = (site, key, l = site.locale || "en", dict = withHead(l)) => TEMPLATES[key](site, makeT(dict), tok(key));
  const section = (h, id) => { const i = h.indexOf(`id="${id}"`); return i < 0 ? "" : h.slice(i, h.indexOf("</section>", i)); };
  const order = (h) => [...h.matchAll(/<(?:section|header)[^>]* id="([a-z]+)"/g)].map((m) => m[1]);
  const typeLabel = (l, k) => dicts[l][(catalog.insuranceTypes || []).find((x) => x.key === k).labelKey];

  ok("insurance types: catalog.insuranceTypes has the 7 keys, each with an EN and HI label", () => {
    assert.deepEqual((catalog.insuranceTypes || []).map((x) => x.key).sort(), ["accident", "business", "health", "home", "life", "motor", "travel"]);
    for (const x of catalog.insuranceTypes) for (const l of LOCALE_KEYS) assert.ok(dicts[l][x.labelKey], `${l} ${x.labelKey}`);
  });

  ok("normalizeCompanies: names and type keys only (no logo, slug or id), malformed entries dropped, at most 20", () => {
    const row = R("web/dev-fixtures/sample-xss.json");
    assert.deepEqual(normalizeSite(row).companies, [{ name: "<script>alert(21)</script>", insurance_types: ["life", "bogus"] }]);
    assert.deepEqual(normalizeCompanies(null), []); assert.deepEqual(normalizeCompanies({ name: "x" }), []);
    assert.deepEqual(normalizeCompanies([{ name: " A ", insurance_types: ["life", "life", 3, "Health"] }]), [{ name: "A", insurance_types: ["life"] }]);
    assert.equal(normalizeCompanies(Array.from({ length: 30 }, (_, i) => ({ name: "C" + i }))).length, 20);
    assert.deepEqual(normalizeSite({ ...R("web/dev-fixtures/sample-en.json"), companies: undefined }).companies, []);
  });

  for (const k of TEMPLATE_KEYS) for (const l of LOCALE_KEYS) ok(`${k}/${l}: one "Companies I represent" section, every name as text with its labelled types (catalog order), no logo or link`, () => {
    const s = fx("sample-" + l), h = html(s, k, l), sec = section(h, "companies");
    assert.ok(sec, "section present");
    assert.equal((sec.match(/<h2>/g) || []).length, 1);
    assert.ok(sec.includes(`<h2>${esc(withHead(l)[HEADING])}</h2>`));
    for (const c of s.companies) {
      assert.ok(sec.includes(esc(c.name)), c.name);
      const want = catalog.insuranceTypes.filter((x) => c.insurance_types.includes(x.key)).map((x) => esc(typeLabel(l, x.key)));
      const at = sec.indexOf(esc(c.name)), next = sec.indexOf("</li>", sec.indexOf(want[want.length - 1], at));
      const row = sec.slice(at, next);
      let from = 0; for (const lab of want) { const i = row.indexOf(lab, from); assert.ok(i >= 0, `${c.name}: ${lab}`); from = i; }
    }
    assert.ok(!/<img|<a |href=|logo/i.test(sec), "plain text only");
  });

  ok("placement: after About on the Meet me templates, after the trust strip on the Best offers ones", () => {
    for (const k of TEMPLATE_KEYS) {
      const o = order(html(fx("sample-en"), k));
      const anchor = familyOf(registry, k) === "offers" ? "highlights" : "about";
      assert.equal(o[o.indexOf("companies") - 1], anchor, k + ": " + o.join());
    }
    const o = order(html(fx("sample-offers"), "spotlight"));
    assert.deepEqual(o.slice(0, 6), ["hero", "offers", "services", "highlights", "companies", "about"]);
  });

  ok("hidden: no companies, an empty list, content.hidden, or the heading missing from the dictionary", () => {
    for (const k of TEMPLATE_KEYS) {
      const s = fx("sample-en");
      assert.ok(!html({ ...s, companies: [] }, k).includes('id="companies"'), k + " empty");
      assert.ok(!html({ ...s, companies: undefined }, k).includes('id="companies"'), k + " missing");
      assert.ok(!html({ ...s, content: { ...s.content, hidden: [...(s.content.hidden || []), "companies"] } }, k).includes('id="companies"'), k + " hidden");
      const noHead = { ...dicts.en }; delete noHead[HEADING];
      const h = html(s, k, "en", noHead);
      assert.ok(!h.includes('id="companies"') && !h.includes(HEADING), k + " heading missing");
    }
    for (const k of TEMPLATE_KEYS) assert.ok(!html(fx("sample-seeded"), k).includes('id="companies"'), k + " seeded (no approved company yet)");
  });

  ok("xss: a hostile company name is escaped; unknown type keys dropped", () => {
    for (const k of TEMPLATE_KEYS) {
      const sec = section(html(fx("sample-xss"), k), "companies");
      assert.ok(sec.includes("&lt;script&gt;alert(21)&lt;/script&gt;") && !/<script/.test(sec), k);
      assert.ok(!sec.includes("bogus") && sec.includes(esc(typeLabel("en", "life"))), k);
    }
  });

  ok("JSON-LD: the Person's affiliation lists the companies as Organizations, only when the section shows", () => {
    const s = fx("sample-en"), t = makeT(withHead("en"));
    const ld = (site, tt = t) => JSON.parse(headData(site, { origin: "https://x.test", t: tt, catalog, schema }).jsonld)["@graph"].find((x) => x["@type"] === "Person");
    assert.deepEqual(ld(s).affiliation, s.companies.map((c) => ({ "@type": "Organization", name: c.name })));
    assert.equal(ld({ ...s, companies: [] }).affiliation, undefined);
    assert.equal(ld({ ...s, content: { ...s.content, hidden: ["companies"] } }).affiliation, undefined);
    const noHead = { ...dicts.en }; delete noHead[HEADING];
    assert.equal(ld(s, makeT(noHead)).affiliation, undefined, "no section, no claim");
    const agency = JSON.parse(headData(s, { origin: "https://x.test", t, catalog, schema }).jsonld)["@graph"].find((x) => x["@type"] === "InsuranceAgency");
    assert.ok(!JSON.stringify(agency).includes(s.companies[0].name), "the agency is the agent's practice, never named after an insurer");
  });
}
