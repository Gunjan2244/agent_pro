// Agent PRO's own legal pages: /privacy and /terms on the primary host. SHELLS: structure only, bodies are drafted for counsel review (P2-4).
// The privacy policy reuses the agent_signup notice texts (single source: shared/i18n), so placeholders such as
// {GRIEVANCE_OFFICER_NAME} show as-is in preview builds and block RELEASE builds (scripts/build-web.sh).
import { esc } from "./sections/util.js";

export function legalHtml(page, t, { notice, otherLang, otherHref }) {
  const p = (k) => `<p>${esc(t(k))}</p>`;
  const sec = (title, body) => `<section><h2>${esc(t(title))}</h2>${body}</section>`;
  const pending = p("site.legal_pending");
  const nav = `<nav class="nav"><a href="/">Agent PRO</a><a href="${esc(otherHref)}" lang="${otherLang}">${esc(t("site.legal_other_language"))}</a></nav>`;
  let main;
  if (page === "terms") {
    main = `<h1>${esc(t("site.legal_terms_title"))}</h1><p class="draft">${esc(t("site.legal_draft"))}</p>`
      + ["service", "accounts", "plans", "content"].map((k) => sec("site.terms_" + k, pending)).join("")
      + sec("site.terms_customers", p("privacy.agent_customer_duty.text"))
      + ["acceptable", "liability", "law"].map((k) => sec("site.terms_" + k, pending)).join("")
      + sec("site.terms_contact", p("privacy.agent_signup.grievance"));
  } else {
    const keys = (notice && notice.textKeys) || [];
    const base = (k) => "privacy.agent_signup." + k;
    const rows = ["account", "profile", "website", "billing", "registrant", "session", "customers"].filter((k) => keys.includes(base(k + "_data")))
      .map((k) => `<tr><td>${esc(t(base(k + "_data")))}</td><td>${esc(t(base(k + "_purpose")))}</td><td>${esc(t(base(k + "_kept")))}</td></tr>`).join("");
    main = `<h1>${esc(t("site.legal_privacy_title"))}</h1><p class="draft">${esc(t("site.legal_draft"))}</p>`
      + sec("site.policy_who", p("site.policy_who_text"))
      + sec("site.policy_collect", `${p(base("intro"))}<table><thead><tr><th>${esc(t("privacy.notice.col_data"))}</th><th>${esc(t("privacy.notice.col_purpose"))}</th><th>${esc(t("privacy.notice.col_kept"))}</th></tr></thead><tbody>${rows}</tbody></table>${p(base("customers_note"))}`)
      + sec("site.policy_enquirers", p("site.policy_enquirers_text"))
      + sec("site.policy_processors", p(base("processors")))
      + sec("site.policy_rights", p(base("rights")) + p(base("withdraw")) + p(base("board")))
      + sec("site.policy_retention", p(base("lapsed")) + p(base("no_ads")))
      + sec("site.policy_grievance", p(base("grievance")))
      + sec("site.policy_changes", p("site.policy_changes_text"));
  }
  return `<div class="doc">${nav}${main}</div>`;
}
