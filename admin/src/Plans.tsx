// Official plans per insurer (DECISIONS #129; docs/OFFERS.md "Plans"). Admins keep each company's plans as the insurer
// publishes them: the official name, LIC's plan number, the UIN, the plan type, the plan's page on the insurer's own
// website, and optionally a picture. Agents then choose which of their companies' plans (for the insurance types they
// deal in there) appear on their websites. No benefits or descriptions are stored: a site shows only these facts and
// links to the insurer. Never deleted: "Withdrawn" hides a plan from every site at once.
// Plan pages (DECISIONS #130): each plan also has its address on the sites (slug, set once from the name), up to 5 key
// features copied WORD FOR WORD from the insurer's plan page (every agent's default; agents may reword them on their
// own site), where and when they were copied, and links to the insurer's documents (brochure, policy document,
// customer information sheet, premium calculator), each on the company's website. Without a picture, sites show the
// default picture of the plan type. The offers on a plan are listed here (they are made in Offers, on a plan).
import { useCallback, useEffect, useState } from "react";
import { supabase } from "./supabase";
import { label, typeLabel } from "./catalog";
import catalog from "../../shared/catalog.json";
import { toOfferWebp } from "./image";
import { SkeletonRows } from "./Skeleton";

type Plan = {
  id: string; company_id: string; name: string; plan_no: string | null; uin: string | null; interest: string; insurance_type: string;
  url: string; image_url: string | null; is_active: boolean; sort: number; checked_on: string;
  slug?: string; key_features: string[]; features_url: string | null; features_checked_on: string | null;
  brochure_url: string | null; policy_url: string | null; cis_url: string | null; calculator_url: string | null;
};
const DOCS: [keyof Plan, string][] = [["brochure_url", "Sales brochure"], ["policy_url", "Policy document"], ["cis_url", "Customer information sheet"], ["calculator_url", "Premium calculator"]];
type Company = { id: string; name: { en: string }; website_url: string | null };
type Interest = { key: string; labelKey: string; insuranceType: string | null; deprecated?: boolean };
// Plan types: catalog interests that have an insurance type ("other" has none and is never a plan type).
const planTypes = ((catalog as unknown as { interests: Interest[] }).interests).filter((i) => i.insuranceType && !i.deprecated);
const typeOf = (interest: string) => planTypes.find((i) => i.key === interest)?.insuranceType ?? "";
const BUCKET = "company-media";
const today = () => new Date(Date.now() + 5.5 * 3600e3).toISOString().slice(0, 10);
const host = (u: string | null) => { const m = /^https:\/\/([^/:?#]+)/i.exec(u ?? ""); return m ? m[1].toLowerCase().replace(/^www\./, "") : ""; };
// Readable messages for the guard's errors (supabase/migrations/20261031090000_company_plans.sql).
const ERRORS: Record<string, string> = {
  company_website_missing: "Set the company's official website first (Companies), so plan links can be checked against it.",
  url_not_company_site: "The link must be a page on the company's own website.",
  invalid_url: "The link must start with https://",
  invalid_interest: "Choose a plan type.",
  invalid_name: "The name must be 2 to 120 characters.",
  invalid_image_url: "Upload the picture again.",
  company_plans_uin_format: "A UIN has only capital letters and digits (6 to 24), e.g. 512N339V02.",
  company_plans_plan_no_format: "A plan number has only letters, digits and hyphens (up to 12).",
  company_plans_company_name_key: "This company already has a plan with this name.",
  invalid_features: "Up to 5 key features, each 3 to 400 characters.",
  invalid_slug: "The address may use only lower-case letters, digits and single hyphens.",
  company_plans_slug_key: "Another plan already uses this address.",
  company_plans_doc_urls_https: "Document links must start with https://",
};
const readable = (m: string) => Object.entries(ERRORS).find(([k]) => m.includes(k))?.[1] ?? m;

export function Plans() {
  const [rows, setRows] = useState<Plan[]>([]);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [company, setCompany] = useState("");
  const [type, setType] = useState("");
  const [q, setQ] = useState("");
  const [showWithdrawn, setShowWithdrawn] = useState(false);
  const [edit, setEdit] = useState<{ p: Plan; isNew: boolean } | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const load = useCallback(async () => {
    const [p, c] = await Promise.all([
      supabase.from("company_plans").select("*").order("sort").order("name"),
      supabase.from("companies").select("id,name,website_url").eq("is_active", true).order("sort"),
    ]);
    setLoaded(true);
    if (p.error) setErr(p.error.message); else setRows(p.data as Plan[]);
    const list = (c.data as Company[]) ?? [];
    setCompanies(list);
    setCompany((cur) => cur || list[0]?.id || "");
  }, []);
  useEffect(() => { void load(); }, [load]);

  const co = companies.find((c) => c.id === company);
  if (edit) return <PlanForm {...edit} company={companies.find((c) => c.id === edit.p.company_id)} onDone={() => { setEdit(null); void load(); }} onCancel={() => setEdit(null)} />;
  const blank = (): Plan => ({
    id: crypto.randomUUID(), company_id: company, name: "", plan_no: null, uin: null, interest: planTypes[0]?.key ?? "term",
    insurance_type: planTypes[0]?.insuranceType ?? "life", url: co?.website_url ?? "https://", image_url: null, is_active: true, sort: 0, checked_on: today(),
    key_features: [], features_url: null, features_checked_on: null, brochure_url: null, policy_url: null, cis_url: null, calculator_url: null,
  });
  const needle = q.trim().toLowerCase();
  const shown = rows.filter((p) => p.company_id === company && (!type || p.interest === type) && (showWithdrawn || p.is_active)
    && (!needle || p.name.toLowerCase().includes(needle) || (p.uin ?? "").toLowerCase().includes(needle) || (p.plan_no ?? "").includes(needle)));
  const stale = shown.filter((p) => p.is_active && Date.now() - +new Date(p.checked_on) > 92 * 864e5).length;
  return (
    <>
      <h1>Plans</h1>
      <p className="muted">Each company's plans exactly as the company publishes them. Agents choose from the plans of their approved companies, for the insurance types they deal in there. Only the name, plan number, UIN and the link to the company's page appear on websites.</p>
      <div className="row">
        <select aria-label="Company" value={company} onChange={(e) => setCompany(e.target.value)}>{companies.map((c) => <option key={c.id} value={c.id}>{c.name.en}</option>)}</select>
        <select aria-label="Plan type" value={type} onChange={(e) => setType(e.target.value)}><option value="">All plan types</option>{planTypes.map((t) => <option key={t.key} value={t.key}>{label(t.labelKey)}</option>)}</select>
        <input aria-label="Search" placeholder="Search name, UIN or plan no." value={q} onChange={(e) => setQ(e.target.value)} />
        <label className="row"><input type="checkbox" checked={showWithdrawn} onChange={(e) => setShowWithdrawn(e.target.checked)} /> Show withdrawn</label>
        <button className="primary" disabled={!co || !co.website_url} onClick={() => setEdit({ p: blank(), isNew: true })}>New plan</button>
      </div>
      {co && !co.website_url && <div className="error" role="alert">{co.name.en} has no official website yet. Add it under Companies before adding plans.</div>}
      {stale > 0 && <div className="muted">{stale} plan{stale === 1 ? "" : "s"} last checked over 3 months ago. Open the company's website and confirm they are still sold.</div>}
      {err && <div className="error" role="alert">{err}</div>}
      <table aria-busy={!loaded}>
        <thead><tr><th>Plan</th><th>Plan type</th><th>Plan no. / UIN</th><th>Checked</th><th /></tr></thead>
        <tbody>{!loaded && <SkeletonRows cols={5} />}{shown.map((p) => (
          <tr key={p.id}>
            <td>{p.name} {!p.is_active && <span className="pill">withdrawn</span>}<div className="muted"><a href={p.url} target="_blank" rel="noopener noreferrer">{host(p.url)}</a></div></td>
            <td>{label(planTypes.find((t) => t.key === p.interest)?.labelKey ?? p.interest)} <span className="muted">({typeLabel(p.insurance_type)})</span></td>
            <td className="muted">{[p.plan_no && `No. ${p.plan_no}`, p.uin].filter(Boolean).join(" · ") || "—"}</td>
            <td className="muted">{p.checked_on}</td>
            <td><button onClick={() => setEdit({ p, isNew: false })}>Edit</button></td>
          </tr>
        ))}{loaded && !shown.length && <tr><td colSpan={5} className="muted">No plans here yet.</td></tr>}</tbody>
      </table>
    </>
  );
}

function PlanForm({ p: initial, isNew, company, onDone, onCancel }: { p: Plan; isNew: boolean; company?: Company; onDone: () => void; onCancel: () => void }) {
  const [p, setP] = useState<Plan>(initial);
  const [msg, setMsg] = useState<string | null>(null);
  const set = <K extends keyof Plan>(k: K, v: Plan[K]) => setP((x) => ({ ...x, [k]: v }));
  const site = host(company?.website_url ?? null);
  const onSite = (u: string | null) => { const h = host(u); return Boolean(h && site && (h === site || h.endsWith("." + site))); };
  const urlOk = onSite(p.url) && [p.features_url, ...DOCS.map(([k]) => p[k] as string | null)].every((u) => !u || onSite(u));
  const kf = p.key_features.length ? p.key_features : [""];
  const setKf = (i: number, v: string) => set("key_features", Object.assign([...kf], { [i]: v }));
  const [offers, setOffers] = useState<{ id: string; internal_name: string | null; status: string; ends_at: string }[]>([]);
  useEffect(() => {
    if (isNew) return;
    void supabase.from("offers").select("id, internal_name, status, ends_at").eq("plan_id", p.id).order("ends_at", { ascending: false }).then(({ data }) => setOffers(data ?? []));
  }, [isNew, p.id]);

  const save = async (patch: Partial<Plan> = {}) => {
    setMsg(null);
    const r = {
      name: p.name.trim(), plan_no: p.plan_no?.trim() || null, uin: p.uin?.trim().toUpperCase() || null, interest: p.interest,
      insurance_type: typeOf(p.interest), url: p.url.trim(), image_url: p.image_url, is_active: p.is_active, sort: p.sort, checked_on: p.checked_on,
      key_features: p.key_features.map((f) => f.trim()).filter(Boolean), features_url: p.features_url?.trim() || null,
      features_checked_on: p.key_features.some((f) => f.trim()) ? p.features_checked_on || today() : null,
      brochure_url: p.brochure_url?.trim() || null, policy_url: p.policy_url?.trim() || null, cis_url: p.cis_url?.trim() || null, calculator_url: p.calculator_url?.trim() || null, ...patch,
    };
    const { error } = isNew ? await supabase.from("company_plans").insert({ id: p.id, company_id: p.company_id, ...r }) : await supabase.from("company_plans").update(r).eq("id", p.id);
    if (error) setMsg(readable(error.message)); else onDone();
  };

  // One picture per plan at plans/<id>/<n>.webp (the plan must be saved first: storage checks it exists).
  const upload = async (file: File) => {
    setMsg(null);
    try {
      const blob = await toOfferWebp(file);
      const path = `plans/${p.id}/${Date.now()}.webp`;
      const { error } = await supabase.storage.from(BUCKET).upload(path, blob, { contentType: "image/webp" });
      if (error) { setMsg(error.message); return; }
      const old = p.image_url?.split(`/${BUCKET}/`)[1];
      set("image_url", supabase.storage.from(BUCKET).getPublicUrl(path).data.publicUrl);
      if (old) void supabase.storage.from(BUCKET).remove([old]);
    } catch (e) { setMsg((e as Error).message); }
  };

  return (
    <form onSubmit={(e) => { e.preventDefault(); void save(); }}>
      <h1>{isNew ? "New plan" : p.name} {!p.is_active && <span className="pill">withdrawn</span>}</h1>
      <div className="card">
        <div className="muted">Company: <strong>{company?.name.en}</strong> · official website {company?.website_url}</div>
        <div className="grid2">
          <label>Official plan name *<input required minLength={2} maxLength={120} value={p.name} onChange={(e) => set("name", e.target.value)} placeholder="As written on the company's website" /></label>
          <label>Plan type *<select value={p.interest} onChange={(e) => set("interest", e.target.value)}>{planTypes.map((t) => <option key={t.key} value={t.key}>{label(t.labelKey)}</option>)}</select>
            <span className="muted">Insurance type: {typeLabel(typeOf(p.interest))}. Agents see it only if they deal in this type with the company.</span></label>
          <label>Plan number (LIC)<input maxLength={12} pattern="[A-Za-z0-9-]{1,12}" value={p.plan_no ?? ""} onChange={(e) => set("plan_no", e.target.value)} /></label>
          <label>UIN<input maxLength={24} pattern="[A-Za-z0-9]{6,24}" value={p.uin ?? ""} onChange={(e) => set("uin", e.target.value.toUpperCase())} placeholder="e.g. 512N339V02" /></label>
          <label>Plan page on the company's website *<input type="url" required pattern="https://.*" value={p.url} onChange={(e) => set("url", e.target.value)} />
            {!urlOk && p.url.length > 8 && <span className="error">Must be a page on {site || "the company's website"}.</span>}</label>
          <label>Last checked on the company's website<input type="date" required value={p.checked_on} onChange={(e) => set("checked_on", e.target.value)} /></label>
          <label>Sort<input type="number" value={p.sort} onChange={(e) => set("sort", Number(e.target.value))} /></label>
        </div>
        <div>
          <h2>Key features (the company's own words)</h2>
          <div className="muted">Copy up to 5 points WORD FOR WORD from the plan page on {site || "the company's website"}; never write your own. Every agent's site shows them, with "From the company's website · checked on …". Agents may replace them with their own words on their own site and go back to these at any time.</div>
          {kf.map((f, i) => <label key={i}>Point {i + 1}<textarea rows={2} maxLength={400} value={f} onChange={(e) => setKf(i, e.target.value)} /></label>)}
          {kf.length < 5 && <button type="button" onClick={() => set("key_features", [...kf, ""])}>Add a point</button>}
          <div className="grid2">
            <label>Copied from (page on the company's website)<input type="url" pattern="https://.*" value={p.features_url ?? ""} onChange={(e) => set("features_url", e.target.value || null)} placeholder="Default: the plan page" />
              {p.features_url && !onSite(p.features_url) && <span className="error">Must be a page on {site}.</span>}</label>
            <label>Copied on<input type="date" value={p.features_checked_on ?? ""} onChange={(e) => set("features_checked_on", e.target.value || null)} /></label>
          </div>
        </div>
        <div>
          <h2>Documents (links to the company's website)</h2>
          <div className="muted">Visitors open each one through a page that says "We're taking you to the company's website". Never upload the company's files here.</div>
          <div className="grid2">{DOCS.map(([k, l]) => (
            <label key={k}>{l}<input type="url" pattern="https://.*" value={(p[k] as string | null) ?? ""} onChange={(e) => set(k, (e.target.value || null) as never)} />
              {p[k] && !onSite(p[k] as string) && <span className="error">Must be a page on {site}.</span>}</label>))}</div>
          {p.slug && <div className="muted">Address on agents' sites: /insurance/{p.interest}/{p.slug}</div>}
        </div>
        <div>
          <h2>Picture (optional, 16:9)</h2>
          {p.image_url && <img src={p.image_url} alt="" width={320} height={180} style={{ display: "block", marginBottom: 8, objectFit: "cover" }} />}
          {isNew ? <div className="muted">Save the plan first, then add a picture.</div> : <input type="file" accept="image/png,image/jpeg,image/webp" onChange={(e) => e.target.files?.[0] && void upload(e.target.files[0])} />}
          {p.image_url && <button type="button" onClick={() => set("image_url", null)}>Remove picture</button>}
          <div className="muted">Cropped to 16:9 and saved as WebP under 512 KB. Use your own or licensed artwork: never the company's logo or its brochure images. Without a picture, websites show the default picture of the plan type ({label(planTypes.find((t) => t.key === p.interest)?.labelKey ?? "")}).</div>
        </div>
        {!isNew && <div>
          <h2>Offers on this plan</h2>
          <div className="muted">An offer on a plan appears only on the sites of agents who show this plan. Make offers in Offers and choose this plan there.</div>
          {offers.length ? <ul>{offers.map((o) => <li key={o.id}>{o.internal_name || o.id} · {o.status} · ends {o.ends_at.slice(0, 10)}</li>)}</ul> : <div className="muted">No offers on this plan yet.</div>}
        </div>}
        {msg && <div className="error" role="alert">{msg}</div>}
        <div className="row">
          <button className="primary" disabled={!urlOk}>Save</button>
          {!isNew && <button type="button" onClick={() => void save({ checked_on: today(), ...(p.key_features.some((f) => f.trim()) ? { features_checked_on: today() } : {}) })}>Checked today</button>}
          {!isNew && p.is_active && <button type="button" className="danger" onClick={() => { if (confirm("Withdraw this plan? It disappears from every agent's website now.")) void save({ is_active: false }); }}>Withdraw</button>}
          {!isNew && !p.is_active && <button type="button" onClick={() => void save({ is_active: true })}>Restore</button>}
          <button type="button" onClick={onCancel}>Cancel</button>
        </div>
      </div>
    </form>
  );
}
