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

// RPC row -> { content, template_key, accent, locale, canonical_host, profile }
export function normalizeSite(row) {
  if (Array.isArray(row)) row = row[0];
  if (!row || typeof row !== "object") return null;
  const content = row.published_content ?? row.content;
  if (!content || typeof content !== "object") return null;
  return { content, template_key: row.template_key, accent: row.accent, locale: row.locale, canonical_host: row.canonical_host || null, profile: row.profile || {} };
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
