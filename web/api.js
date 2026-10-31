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

// The payload's approved companies (DECISIONS #90): [{name, insurance_types}], plus (DECISIONS #129) the insurer's
// official `website` (https only) for the disclosure footer and the agent's `agency_code` there (only when the agent
// shows it). A logo, slug or id never reaches a site even if a payload carried one. Malformed entries are dropped;
// at most 20.
const httpsUrl = (v, max = 500) => (typeof v === "string" && v.length <= max && /^https:\/\/[^\s"'<>]+$/i.test(v.trim()) ? v.trim() : null);
export function normalizeCompanies(v) {
  if (!Array.isArray(v)) return [];
  return v.filter((c) => c && typeof c === "object" && typeof c.name === "string" && c.name.trim()).slice(0, 20).map((c) => {
    const out = {
      name: c.name.trim().slice(0, 120),
      insurance_types: Array.isArray(c.insurance_types) ? [...new Set(c.insurance_types.filter((k) => typeof k === "string" && /^[a-z_]{1,40}$/.test(k)))] : [],
    };
    const site = httpsUrl(c.website, 200);
    if (site) out.website = site;
    if (typeof c.agency_code === "string" && /^[A-Za-z0-9 ./-]{1,30}$/.test(c.agency_code.trim())) out.agency_code = c.agency_code.trim();
    return out;
  });
}

// The payload's official plans (DECISIONS #129): the plans the agent chose (or, with no choice, every plan of their
// approved companies for the insurance types they deal in there), already resolved by the backend. Only facts and the
// insurer's own link: {id, company, name, plan_no, uin, interest, url, image_url}; at most 100; malformed ones dropped.
export function normalizePlans(v) {
  if (!Array.isArray(v)) return [];
  const str = (x, max, re) => (typeof x === "string" && x.trim() && x.trim().length <= max && (!re || re.test(x.trim())) ? x.trim() : null);
  return v.filter((p) => p && typeof p === "object").map((p) => ({
    id: str(p.id, 40, /^[0-9a-f-]{36}$/i),
    company: str(p.company, 120),
    name: str(p.name, 120),
    plan_no: str(p.plan_no, 12, /^[A-Za-z0-9-]+$/),
    uin: str(p.uin, 24, /^[A-Z0-9]+$/),
    interest: str(p.interest, 40, /^[a-z_]+$/),
    url: httpsUrl(p.url),
    image_url: httpsUrl(p.image_url),
    // the plan page (DECISIONS #130): its address, key features (the agent's own words when features_own), where and
    // when the insurer's words were copied, the insurer's documents, and its place on Home (featured 1–6)
    slug: str(p.slug, 80, /^[a-z0-9]+(-[a-z0-9]+)*$/),
    features: Array.isArray(p.features) ? p.features.map((f) => str(f, 400)).filter(Boolean).slice(0, 5) : [],
    features_own: p.features_own === true,
    features_url: httpsUrl(p.features_url),
    features_checked_on: str(p.features_checked_on, 10, /^\d{4}-\d{2}-\d{2}$/),
    docs: Object.fromEntries(["brochure", "policy", "cis", "calculator"].map((k) => [k, httpsUrl((p.docs || {})[k])]).filter(([, u]) => u)),
    featured: Number.isInteger(p.featured) && p.featured >= 1 && p.featured <= 6 ? p.featured : null,
  })).filter((p) => p.name && p.company && p.url && p.interest).slice(0, 100);
}

// RPC row -> { portal, content, template_key, accent, locale, canonical_host, published_at, profile, offers, company, companies, plans }.
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
    companies: normalizeCompanies(row.companies), plans: normalizePlans(row.plans), portal: row.portal === true,
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
