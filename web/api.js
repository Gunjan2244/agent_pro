// Plain fetch to Supabase RPCs with the anon key. No SDK.
// Argument names follow docs/BACKEND.md. If the SQL uses a prefix (e.g. p_slug), change ARGS here only.
const cfg = () => window.AGENTPRO_CONFIG || {};

async function rpc(name, args) {
  const c = cfg();
  const r = await fetch(`${c.SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: { "content-type": "application/json", apikey: c.SUPABASE_ANON_KEY, authorization: `Bearer ${c.SUPABASE_ANON_KEY}` },
    body: JSON.stringify(args),
  });
  const text = await r.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { /* non-JSON */ }
  if (!r.ok) { const e = new Error((body && body.message) || "rpc " + r.status); e.status = r.status; e.body = body; throw e; }
  return body;
}

// The payload's resolved offers ({featured, items}, OFFERS.md); anything else becomes "no offers".
export function normalizeOffers(o) {
  const ok = (x) => (x && typeof x === "object" && !Array.isArray(x) ? x : null);
  o = ok(o) || {};
  return { featured: ok(o.featured), items: Array.isArray(o.items) ? o.items.map(ok).filter(Boolean) : [] };
}

// The payload's approved companies (DECISIONS #90): [{name, insurance_types}] only, so a logo, slug or id never reaches
// a site even if a payload carried one. Malformed entries are dropped; at most 20.
export function normalizeCompanies(v) {
  if (!Array.isArray(v)) return [];
  return v.filter((c) => c && typeof c === "object" && typeof c.name === "string" && c.name.trim()).slice(0, 20).map((c) => ({
    name: c.name.trim().slice(0, 120),
    insurance_types: Array.isArray(c.insurance_types) ? [...new Set(c.insurance_types.filter((k) => typeof k === "string" && /^[a-z_]{1,40}$/.test(k)))] : [],
  }));
}

// RPC row -> { content, template_key, accent, locale, canonical_host, published_at, profile, offers, company, companies }.
// `company` keeps only the disclaimer (the offers' fine print); the company *names* come only through `companies`.
export function normalizeSite(row) {
  if (Array.isArray(row)) row = row[0];
  if (!row || typeof row !== "object") return null;
  const content = row.published_content ?? row.content;
  if (!content || typeof content !== "object") return null;
  const disclaimer = row.company && typeof row.company.disclaimer === "string" ? row.company.disclaimer : null;
  return {
    content, template_key: row.template_key, accent: row.accent, locale: row.locale, canonical_host: row.canonical_host || null,
    published_at: row.published_at || null, profile: row.profile || {}, offers: normalizeOffers(row.offers), company: disclaimer ? { disclaimer } : null,
    companies: normalizeCompanies(row.companies),
  };
}

export const getPublicSite = async (slug) => normalizeSite(await rpc("get_public_site", { slug }));
export const getPublicSiteByHost = async (host) => normalizeSite(await rpc("get_public_site_by_host", { host }));
export const recordSiteView = (slug, host) => rpc("record_site_view", { slug: slug || null, host: host || null });

// Returns { status: "ok" | "rate_limited" | "unavailable" | "invalid", field? }; throws on transport failures.
// Contract (BACKEND.md): {ok:true} | {ok:false, error:'rate_limited'|'unavailable'|'invalid', field}
export async function submitEnquiry(a) {
  let r;
  try {
    r = await rpc("submit_enquiry", {
      slug: a.slug || null, host: a.host || null, name: a.name, phone: a.phone,
      email: a.email || null, interest: a.interest || null, message: a.message || null, honeypot: a.honeypot || "",
      consent: a.consent || null, // {notice_key, version, hash}; the RPC rejects a missing/invalid one with field "consent"
    });
  } catch (e) {
    if (e.status === 429) return { status: "rate_limited" }; // fallback if a proxy rate-limits
    throw e;
  }
  if (r && r.ok === true) return { status: "ok" };
  if (r && r.error === "rate_limited") return { status: "rate_limited" };
  if (r && r.error === "invalid") return { status: "invalid", field: r.field };
  if (r && r.error === "unavailable") return { status: "unavailable" };
  throw new Error("unexpected submit_enquiry response");
}

// Privacy page request form (PRIVACY.md). Same result shape as submitEnquiry.
// Contract: {ok:true} | {ok:false, error:'invalid', field} | {ok:false, error:'rate_limited'} | {ok:false, error:'unavailable'}
export async function submitDataRequest(a) {
  let r;
  try {
    r = await rpc("submit_data_request", {
      slug: a.slug || null, host: a.host || null, name: a.name, phone: a.phone,
      kind: a.kind, message: a.message || null, honeypot: a.honeypot || "",
    });
  } catch (e) {
    if (e.status === 429) return { status: "rate_limited" };
    throw e;
  }
  if (r && r.ok === true) return { status: "ok" };
  if (r && r.error === "rate_limited") return { status: "rate_limited" };
  if (r && r.error === "invalid") return { status: "invalid", field: r.field };
  if (r && r.error === "unavailable") return { status: "unavailable" };
  throw new Error("unexpected submit_data_request response");
}

// One view per session and site (debounced with sessionStorage); never throws.
export function recordViewOnce(target, backend = { recordSiteView }, storage = globalThis.sessionStorage) {
  const k = "apv:" + (target.slug || target.host);
  try { if (storage.getItem(k)) return; storage.setItem(k, "1"); } catch { /* storage blocked: still count once per load */ }
  backend.recordSiteView(target.slug, target.host).catch(() => {});
}
