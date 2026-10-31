// Official plans from the insurers and the agent's disclosure footer (DECISIONS #129): normalisation, the
// "Official plans" section on the Insurance page and on each plan page of all five templates in both languages,
// plan pages for plan types that only the official plans name, the footer disclosure on every page, escaping and
// unsafe links. Run by scripts/test-web.mjs.
import assert from "node:assert/strict";

export async function run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }) {
  const { TEMPLATES, familyOf } = await import("../web/templates/index.js");
  const { normalizeSite, normalizePlans, normalizeCompanies } = await import("../web/api.js");
  const { matchPage } = await import("../web/route.js");
  const { esc } = await import("../web/sections/util.js");
  const registry = R("shared/templates/index.json");
  const PAGES = R("shared/site-pages.json");
  const tok = (key, path) => ({ accent: "#1F4AA8", schema, catalog, family: familyOf(registry, key), images: R("web/images/manifest.json"), seed: "demo", today: "2026-10-18", privacyHref: "/a/demo/privacy", sitePages: PAGES, page: matchPage(PAGES, path) });
  const page = (site, key, path, l = site.locale || "en") => TEMPLATES[key]({ ...site, template_key: key }, makeT(dicts[l]), tok(key, path));
  const pagesOf = (site, key) => TEMPLATES[key]({ ...site, template_key: key }, makeT(dicts[site.locale || "en"]), { ...tok(key, ""), pagesOnly: true });
  const section = (h, id) => { const i = h.indexOf(`id="${id}"`); return i < 0 ? "" : h.slice(i, h.indexOf("</section>", i)); };
  const foot = (h) => { const i = h.indexOf('<footer class="foot">'); return i < 0 ? "" : h.slice(i, h.indexOf("</footer>", i)); };

  ok("i18n: every plans and disclosure key exists in every locale, no empty values; the footer no longer says Independent", () => {
    const keys = ["site.footer_advisor", "site.footer_agent_of", "site.footer_company_code", "site.footer_and", "site.footer_not_official", "site.footer_not_official_any",
      "site.footer_official_sites", "site.footer_solicitation", "site.iplans_heading", "site.iplans_heading_type", "site.iplans_lead", "site.iplans_fine", "site.iplans_one",
      "site.iplans_n", "site.iplan_no", "site.iplan_uin", "site.iplan_official", "site.iplan_opens", "site.iplan_ask"];
    for (const l of LOCALE_KEYS) for (const k of keys) assert.ok(typeof dicts[l][k] === "string" && dicts[l][k].trim(), `${l} ${k}`);
    assert.ok(!/independent/i.test(dicts.en["site.footer_advisor"]) && !dicts.hi["site.footer_advisor"].includes("स्वतंत्र"));
  });

  ok("normalizePlans: facts and https links only; bad entries dropped; at most 100", () => {
    const good = { id: "9e000001-0000-4000-8000-000000000001", company: " Acme ", name: " A plan ", plan_no: "914", uin: "512N339V02", interest: "term", url: "https://acme.example/a", image_url: "https://cdn.example/x.webp", logo: "https://x/logo.png", benefits: "Big money" };
    assert.deepEqual(normalizePlans([good]), [{ id: good.id, company: "Acme", name: "A plan", plan_no: "914", uin: "512N339V02", interest: "term", url: "https://acme.example/a", image_url: "https://cdn.example/x.webp" }]);
    for (const bad of [{ ...good, url: "javascript:alert(1)" }, { ...good, url: "http://acme.example/a" }, { ...good, name: "" }, { ...good, interest: "Term!" }, { ...good, company: null }, null, 3])
      assert.deepEqual(normalizePlans([bad]), [], JSON.stringify(bad));
    assert.equal(normalizePlans([{ ...good, image_url: "http://x/y.png", uin: "bad uin", plan_no: "<9>" }])[0].image_url, null);
    assert.equal(normalizePlans([{ ...good, uin: "bad uin" }])[0].uin, null);
    assert.equal(normalizePlans(Array.from({ length: 140 }, () => good)).length, 100);
    assert.deepEqual(normalizePlans(undefined), []);
    assert.deepEqual(normalizeSite({ ...R("web/dev-fixtures/sample-en.json"), plans: undefined }).plans, []);
  });

  ok("normalizeCompanies: the insurer's website (https only) and the agent's agency code (when sent) are kept", () => {
    assert.deepEqual(normalizeCompanies([{ name: "A", insurance_types: ["life"], website: "https://a.example", agency_code: "KN-77" }]),
      [{ name: "A", insurance_types: ["life"], website: "https://a.example", agency_code: "KN-77" }]);
    assert.deepEqual(normalizeCompanies([{ name: "A", website: "javascript:x", agency_code: "<b>" }]), [{ name: "A", insurance_types: [] }]);
  });

  for (const k of TEMPLATE_KEYS) for (const l of LOCALE_KEYS) {
    const s = fx("sample-" + l);
    ok(`${k}/${l}: Insurance lists every official plan, grouped by company (first group open), with its facts and the insurer's link`, () => {
      const sec = section(page(s, k, "insurance", l), "insurerPlans");
      assert.ok(sec, "section present");
      assert.equal((sec.match(/<h2>/g) || []).length, 1);
      assert.equal((sec.match(/<details class="ip-co"/g) || []).length, new Set(s.plans.map((p) => p.company)).size);
      assert.equal((sec.match(/<details class="ip-co" open>/g) || []).length, 1);
      for (const p of s.plans) {
        assert.ok(sec.includes(esc(p.name)), p.name);
        assert.ok(sec.includes(`href="${esc(p.url)}" target="_blank" rel="noopener nofollow external"`), p.url);
        if (p.uin) assert.ok(sec.includes(esc(p.uin)), p.uin);
      }
      assert.ok(!/logo/i.test(sec));
    });
    ok(`${k}/${l}: a plan page shows only that type's official plans, without fold-outs`, () => {
      const sec = section(page(s, k, "insurance/term", l), "insurerPlans");
      assert.ok(sec.includes(esc("Sample Life Secure Term")));
      assert.ok(!sec.includes("Family Saver") && !sec.includes("Golden Years") && !sec.includes("<details"));
      assert.ok(!section(page(s, k, "insurance/term", l), "insurerPlans").includes(`class="pill"`), "no type tag on its own page");
    });
    ok(`${k}/${l}: every page's footer discloses whose agent this is, that it is not an insurer's site, the official sites and the solicitation line`, () => {
      for (const path of ["", "insurance", "insurance/term", "about", "contact"]) {
        const f = foot(page(s, k, path, l));
        assert.ok(f, path || "home");
        for (const c of s.companies) assert.ok(f.includes(esc(c.name)), `${path} ${c.name}`);
        assert.ok(f.includes(esc(dicts[l]["site.footer_not_official"])), path);
        assert.ok(f.includes(esc(dicts[l]["site.footer_solicitation"])), path);
        for (const c of s.companies) assert.ok(f.includes(`href="${esc(c.website)}" target="_blank" rel="noopener nofollow external"`), c.website);
        assert.ok(!/independent|स्वतंत्र/i.test(f));
        assert.ok(!/<img/i.test(f), "no logo");
      }
    });
  }

  ok("a plan type named only by the official plans gets its own page (planKeys)", () => {
    const s = fx("sample-en");
    const bare = { ...s, content: { ...s.content, services: { ...(s.content.services || {}), items: [], offered: [] } }, offers: { featured: null, items: [] } };
    for (const k of TEMPLATE_KEYS) {
      const plans = pagesOf(bare, k).filter((p) => p.plan).map((p) => p.plan);
      for (const want of ["term", "endowment", "pension", "health"]) assert.ok(plans.includes(want), `${k}: ${plans.join()}`);
    }
  });

  ok("no plans: no section, the Insurance page and the footer still work", () => {
    const s = { ...fx("sample-en"), plans: [] };
    for (const k of TEMPLATE_KEYS) {
      const h = page(s, k, "insurance");
      assert.ok(h && !h.includes('id="insurerPlans"'), k);
      assert.ok(foot(h).includes(esc(dicts.en["site.footer_solicitation"])));
    }
  });

  ok("no company: the footer says it is not an insurer's site, names nobody and links nowhere official", () => {
    const s = { ...fx("sample-en"), companies: [], plans: [] };
    for (const k of TEMPLATE_KEYS) {
      const f = foot(page(s, k, ""));
      assert.ok(f.includes(esc(dicts.en["site.footer_not_official_any"])) && !f.includes("nofollow external"), k);
    }
  });

  ok("agency codes appear in the footer only when the payload carries them", () => {
    const s = fx("sample-en");
    const coded = { ...s, companies: s.companies.map((c, i) => (i === 0 ? { ...c, agency_code: "SL-42" } : c)) };
    for (const k of TEMPLATE_KEYS) {
      assert.ok(foot(page(coded, k, "")).includes("SL-42"), k);
      assert.ok(!foot(page(s, k, "")).includes("SL-42"), k);
    }
  });

  ok("escaping: a plan's name and company are escaped; an admin picture is used only when https", () => {
    const s = fx("sample-en");
    const evil = { ...s, plans: normalizePlans([{ id: "9e0000ff-0000-4000-8000-0000000000ff", company: "<b>Co</b>", name: "<script>alert(1)</script>", interest: "term", url: "https://x.example/p", image_url: "https://cdn.example/p.webp" }]) };
    for (const k of TEMPLATE_KEYS) {
      const sec = section(page(evil, k, "insurance"), "insurerPlans");
      assert.ok(!sec.includes("<script>alert") && sec.includes("&lt;script&gt;") && sec.includes("&lt;b&gt;Co&lt;/b&gt;"), k);
      assert.ok(sec.includes('src="https://cdn.example/p.webp"'), k);
    }
  });
}
