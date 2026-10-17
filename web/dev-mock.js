// DEV ONLY. Not copied into production builds (scripts/build-web.sh copies it only when WEB_DEV=1),
// and app.js only imports it on localhost with ?mock=1. Serves web/dev-fixtures/*.json.
// Slugs: demo (EN), demo-hi (HI), xss, minimal-empty, unpublished (-> null), error (-> throws).
// ?t=classic|modern|minimal overrides the template. Phone ending 0000 => rate limited; 1111 => transport error, 2222 => unavailable, 3333 => invalid(email), 4444 => invalid(consent) on the enquiry form. Enquiry without a consent object => invalid(consent). Same 0000/1111/2222 for submit_data_request. Slug demo-hi has canonical_host.
export function mockApi(real, q) {
  const FIX = { demo: "sample-en", "demo-hi": "sample-hi", xss: "sample-xss", "minimal-empty": "sample-empty" };
  const log = (...a) => console.info("[mock]", ...a);
  return {
    async getPublicSite(slug) {
      if (slug === "error") throw new Error("boom");
      if (!FIX[slug]) return null;
      const row = await (await fetch(`/dev-fixtures/${FIX[slug]}.json`)).json();
      if (q.get("t")) row.template_key = q.get("t");
      return real.normalizeSite(row);
    },
    getPublicSiteByHost: (host) => (host === "localhost" || host === "127.0.0.1" ? Promise.resolve(null) : Promise.resolve(null)),
    async submitEnquiry(a) {
      log("submit_enquiry", a);
      await new Promise((r) => setTimeout(r, 400));
      if (a.phone.endsWith("0000")) return { status: "rate_limited" };
      if (a.phone.endsWith("1111")) throw new Error("fail");
      if (a.phone.endsWith("2222")) return { status: "unavailable" };
      if (!a.consent || a.consent.notice_key !== "enquiry_form" || !/^[0-9a-f]{64}$/.test(a.consent.hash || "") || a.phone.endsWith("4444")) return { status: "invalid", field: "consent" };
      if (a.phone.endsWith("3333")) return { status: "invalid", field: "email" };
      return { status: "ok" };
    },
    async submitDataRequest(a) {
      log("submit_data_request", a);
      await new Promise((r) => setTimeout(r, 400));
      if (a.phone.endsWith("0000")) return { status: "rate_limited" };
      if (a.phone.endsWith("1111")) throw new Error("fail");
      if (a.phone.endsWith("2222")) return { status: "unavailable" };
      return { status: "ok" };
    },
    async recordSiteView(slug, host) { log("record_site_view", slug, host); },
  };
}
