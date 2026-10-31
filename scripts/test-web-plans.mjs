// Official plans from the insurers and the agent's disclosure footer (DECISIONS #129), and plan pages (DECISIONS #130):
// normalisation, the "Official plans" cards on the Insurance page and on each plan-type page of all five templates in
// both languages, each plan's own page (facts, default picture, the insurer's or the agent's key features, the
// documents through the "We're taking you to …" page, the offers on it), Home's "Plans I recommend", the go page,
// plan-type pages for types only the official plans name, the footer disclosure on every page, escaping and unsafe
// links. Run by scripts/test-web.mjs.
import assert from "node:assert/strict";

export async function run({ ok, R, dicts, catalog, schema, fx, makeT, TEMPLATE_KEYS, LOCALE_KEYS }) {
  const { TEMPLATES, familyOf } = await import("../web/templates/index.js");
  const { normalizeSite, normalizePlans, normalizeCompanies } = await import("../web/api.js");
  const { matchPage } = await import("../web/route.js");
  const { esc } = await import("../web/sections/util.js");
  const { renderDocument } = await import("../web/ssr.js");
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
    assert.deepEqual(normalizePlans([good]), [{ id: good.id, company: "Acme", name: "A plan", plan_no: "914", uin: "512N339V02", interest: "term", url: "https://acme.example/a", image_url: "https://cdn.example/x.webp",
      slug: null, features: [], features_own: false, features_url: null, features_checked_on: null, docs: {}, featured: null }]);
    // the plan page's data (DECISIONS #130): slug, up to 5 features, https documents only, featured 1–6
    const full = normalizePlans([{ ...good, slug: "a-plan", features: [" One ", "", 3, "Two", "3", "4", "5", "6"], features_own: true, features_url: "https://acme.example/a#f", features_checked_on: "2026-10-30",
      docs: { brochure: "https://acme.example/b.pdf", policy: "javascript:x", cis: "http://acme.example/c.pdf", other: "https://acme.example/o" }, featured: 2 }])[0];
    assert.deepEqual([full.slug, full.features, full.features_own, full.features_url, full.features_checked_on, full.docs, full.featured],
      ["a-plan", ["One", "Two", "3", "4", "5"], true, "https://acme.example/a#f", "2026-10-30", { brochure: "https://acme.example/b.pdf" }, 2]);
    assert.deepEqual([normalizePlans([{ ...good, slug: "A Plan", featured: 7 }])[0].slug, normalizePlans([{ ...good, featured: 7 }])[0].featured], [null, null]);
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
    ok(`${k}/${l}: Insurance lists every official plan, grouped by company (first group open), with its facts, linking to its own page`, () => {
      const sec = section(page(s, k, "insurance", l), "insurerPlans");
      assert.ok(sec, "section present");
      assert.equal((sec.match(/<h2>/g) || []).length, 1);
      assert.equal((sec.match(/<details class="ip-co"/g) || []).length, new Set(s.plans.map((p) => p.company)).size);
      assert.equal((sec.match(/<details class="ip-co" open>/g) || []).length, 1);
      for (const p of s.plans) {
        assert.ok(sec.includes(esc(p.name)), p.name);
        // a card opens the plan's own page on the site (DECISIONS #130), never the insurer's site directly
        assert.ok(sec.includes(`href="/a/demo/insurance/${p.interest}/${p.slug}"`), p.slug);
        assert.ok(!sec.includes(esc(p.url)), p.url);
        if (p.uin) assert.ok(sec.includes(esc(p.uin)), p.uin);
      }
      assert.ok(!/logo/i.test(sec));
    });
    ok(`${k}/${l}: a plan page shows only that type's official plans, without fold-outs`, () => {
      const sec = section(page(s, k, "insurance/term", l), "insurerPlans");
      assert.ok(sec.includes(esc("Sample Life Secure Term")));
      assert.ok(!sec.includes("Family Saver") && !sec.includes("Golden Years") && !sec.includes("<details"));
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

  // ---- plan pages (DECISIONS #130) ----
  const shared = { schema, catalog, templates: registry, images: R("web/images/manifest.json"), pages: PAGES };
  const notices = R("shared/generated/privacy-notices.json");
  const doc = (site, key, path, l = "en") => renderDocument({ site: { ...site, template_key: key }, target: { slug: "demo", path }, url: new URL("https://sites.test/a/demo/" + path), dict: dicts[l], shared, notices, config: {} });

  ok("i18n: every plan-page, featured-plans and go-page key exists in every locale", () => {
    const keys = Object.keys(dicts.en).filter((x) => /^site\.(pp_|featured_|go_)/.test(x) || x === "site.offer_on_plan");
    assert.ok(keys.length > 40, String(keys.length));
    for (const l of LOCALE_KEYS) for (const k of keys) assert.ok(typeof dicts[l][k] === "string" && dicts[l][k].trim(), `${l} ${k}`);
  });

  for (const k of TEMPLATE_KEYS) for (const l of LOCALE_KEYS) {
    const s = fx("sample-" + l);
    const saver = s.plans.find((p) => p.slug === "sample-life-family-saver");
    ok(`${k}/${l}: a plan's own page: its name as the <h1>, company and facts, the insurer's key features with source and date, documents through /go/, its offers, similar plans`, () => {
      const h = page(s, k, "insurance/endowment/" + saver.slug, l);
      assert.ok(h.includes(`<h1>${esc(saver.name)}</h1>`) && h.includes(esc(saver.company)) && h.includes(esc(saver.uin)));
      for (const f of saver.features) assert.ok(h.includes(esc(f)), f);
      assert.ok(h.includes(`href="/a/demo/go/${saver.slug}/features" rel="nofollow"`), "the source links through the go page");
      assert.ok(h.includes(esc(dicts[l]["site.pp_src_checked"].replace("{date}", ""))) || h.includes("2026"), "the date it was checked");
      for (const d of ["plan", "brochure", "calculator"]) assert.ok(h.includes(`href="/a/demo/go/${saver.slug}/${d}"`), d);
      assert.ok(!h.includes(`href="/a/demo/go/${saver.slug}/policy"`), "only the documents the admin added");
      const body = h.slice(0, h.indexOf('<footer class="foot">'));
      assert.ok(!body.includes(`href="${esc(saver.url)}"`) && !body.includes('href="https://www.samplelife.example'), "no direct insurer link on the page (the footer's official-sites line aside)");
      const offers = s.offers.items.filter((o) => o.plan_id === saver.id && o.ends_on >= "2026-10-18");
      assert.ok(offers.length && offers.every((o) => h.includes(esc(o.title))), "the offers on this plan");
      assert.ok(!h.includes(esc(s.offers.items.find((o) => o.plan_id && o.plan_id !== saver.id).title)), "not the offers on other plans");
      assert.ok(h.includes(esc(dicts[l]["site.pp_similar"])) || h.includes(esc(dicts[l]["site.pp_compare"])), "similar plans");
      assert.ok(h.includes('class="pic dpic"'), "the default picture (no admin picture)");
      assert.ok(h.includes('<section class="sec sec-help" id="help">'), "the help band");
    });
    ok(`${k}/${l}: the agent's own wording is marked as theirs and points to the official terms`, () => {
      const shield = s.plans.find((p) => p.features_own);
      const h = page(s, k, `insurance/${shield.interest}/${shield.slug}`, l);
      assert.ok(h.includes(esc(dicts[l]["site.pp_own_badge"].replace("{name}", "Asha"))) || h.includes('class="pill own"'));
      assert.ok(!h.includes(`/go/${shield.slug}/features"`), "no 'from the insurer's website' line on the agent's words");
    });
    ok(`${k}/${l}: Home's "Plans I recommend" shows the featured plans in their order, each linking to its page`, () => {
      const h = page(s, k, "", l);
      const sec = section(h, "featuredPlans");
      const feat = s.plans.filter((p) => p.featured).sort((a, b) => a.featured - b.featured);
      assert.ok(sec.includes(esc(dicts[l]["site.featured_heading"])), "heading");
      const at = feat.map((p) => sec.indexOf(`/insurance/${p.interest}/${p.slug}"`));
      assert.ok(at.every((x) => x > 0) && at.every((x, i) => !i || x > at[i - 1]), at.join());
      assert.ok(!sec.includes(s.plans.find((p) => !p.featured).slug));
    });
  }

  ok("the plan page exists only for a plan the site shows; a wrong type or slug renders \"\"", () => {
    const s = fx("sample-en");
    for (const k of TEMPLATE_KEYS) {
      assert.equal(page(s, k, "insurance/term/sample-life-family-saver"), "", k + ": wrong type");
      assert.equal(page(s, k, "insurance/endowment/no-such-plan"), "", k);
      assert.equal(page({ ...s, plans: s.plans.filter((p) => p.slug !== "sample-life-family-saver") }, k, "insurance/endowment/sample-life-family-saver"), "", k + ": not shown");
    }
  });

  ok("go page: \"We're taking you to <insurer>'s website\", the plan, the exact address, Go now and Stay here; the server adds noindex and a 5 s meta refresh to the payload's own address", () => {
    const s = fx("sample-en");
    const saver = s.plans.find((p) => p.slug === "sample-life-family-saver");
    for (const k of TEMPLATE_KEYS) {
      const h = page(s, k, "go/sample-life-family-saver/brochure");
      assert.ok(h.includes(`<h1>${esc(dicts.en["site.go_title"].replace("{company}", saver.company))}</h1>`), k);
      assert.ok(h.includes(esc(saver.docs.brochure)) && h.includes(`href="${esc(saver.docs.brochure)}"`) && h.includes('href="/a/demo/insurance/endowment/sample-life-family-saver"'), k);
      assert.ok(!h.includes('class="topbar"') && !h.includes('<nav class="callbar"'), "no site chrome");
      assert.equal(page(s, k, "go/sample-life-family-saver/policy"), "", k + ": a document the plan doesn't have");
      assert.equal(page(s, k, "go/no-such-plan/plan"), "", k);
      const d = doc(s, k, "go/sample-life-family-saver/brochure");
      assert.ok(d.includes(`<meta http-equiv="refresh" content="5;url=${esc(saver.docs.brochure)}">`) && d.includes('<meta name="robots" content="noindex">'), k);
      const sm = pagesOf(s, k).map((p) => p.path);
      assert.ok(!sm.some((p) => p.startsWith("go/")), "never in the sitemap");
    }
  });

  ok("an offer on a plan the site shows says so and links to the plan's page", () => {
    const s = fx("sample-en");
    for (const k of TEMPLATE_KEYS) {
      const h = page(s, k, "insurance");
      assert.ok(h.includes(esc(dicts.en["site.offer_on_plan"].replace("{plan}", "Sample Life Family Saver"))), k);
      assert.ok(h.includes('<a class="lnk" href="/a/demo/insurance/endowment/sample-life-family-saver">'), k);
    }
  });

  ok("default picture: the plan type's catalog icon on a tinted panel; an admin picture (https) replaces it", () => {
    const s = fx("sample-en");
    const pic = { ...s, plans: s.plans.map((p) => (p.slug === "sample-life-secure-term" ? { ...p, image_url: "https://cdn.example/t.webp" } : p)) };
    for (const k of TEMPLATE_KEYS) {
      const h = page(pic, k, "insurance/term/sample-life-secure-term");
      assert.ok(h.includes('src="https://cdn.example/t.webp"') && !section(h, "top").includes("dpic"), k);
      const h2 = page(s, k, "insurance/term/sample-life-secure-term");
      assert.ok(/<span class="pic dpic" aria-hidden="true"><svg/.test(h2), k);
    }
  });
}
