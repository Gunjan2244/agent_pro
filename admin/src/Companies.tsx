import { useCallback, useEffect, useState } from "react";
import { supabase } from "./supabase";
import { toLogoWebp } from "./logo";

const BUCKET = "company-media";
const pathOf = (url: string | null) => url?.split(`/${BUCKET}/`)[1];

type Loc = { en?: string; hi?: string };
type Company = {
  id: string; slug: string; name: Loc; website_url: string | null; disclaimer: Loc | null;
  allow_custom_offers: boolean; is_active: boolean; sort: number;
  logo_url: string | null; logo_permission_at: string | null; logo_permission_ref: string | null; logo_permission_until: string | null;
};
const today = () => new Date().toISOString().slice(0, 10);
const ended = (c: Company) => !!c.logo_url && !!c.logo_permission_until && c.logo_permission_until < today();
const blank = (): Company => ({ id: crypto.randomUUID(), slug: "", name: { en: "", hi: "" }, website_url: null, disclaimer: { en: "", hi: "" }, allow_custom_offers: true, is_active: true, sort: 0, logo_url: null, logo_permission_at: null, logo_permission_ref: null, logo_permission_until: null });

export function Companies() {
  const [rows, setRows] = useState<Company[]>([]);
  const [edit, setEdit] = useState<{ c: Company; isNew: boolean } | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const load = useCallback(async () => {
    const { data, error } = await supabase.from("companies").select("id,slug,name,website_url,disclaimer,allow_custom_offers,is_active,sort,logo_url,logo_permission_at,logo_permission_ref,logo_permission_until").order("sort").order("slug");
    if (error) setErr(error.message); else setRows(data as Company[]);
  }, []);
  useEffect(() => { void load(); }, [load]);

  if (edit) return <CompanyForm {...edit} onDone={() => { setEdit(null); void load(); }} onCancel={() => setEdit(null)} />;
  return (
    <>
      <h1>Companies</h1>
      <div className="row"><button className="primary" onClick={() => setEdit({ c: blank(), isNew: true })}>New company</button></div>
      {err && <div className="error" role="alert">{err}</div>}
      <table>
        <thead><tr><th>Logo</th><th>Name</th><th>Slug</th><th>Status</th><th>Sort</th><th /></tr></thead>
        <tbody>{rows.map((c) => (
          <tr key={c.id}>
            <td>{c.logo_url && !ended(c)
              ? <img className="logo-img" src={c.logo_url} alt="" width={24} height={24} />
              : <span className="logo-initials" aria-hidden="true">{(c.name.en ?? "?").trim().slice(0, 2).toUpperCase()}</span>}</td>
            <td>{c.name.en}</td><td className="muted">{c.slug}</td>
            <td><span className={`pill ${c.is_active ? "success" : ""}`}>{c.is_active ? "Active" : "Inactive"}</span></td>
            <td>{c.sort}</td><td><button onClick={() => setEdit({ c, isNew: false })}>Edit</button></td>
          </tr>
        ))}</tbody>
      </table>
    </>
  );
}

function CompanyForm({ c: initial, isNew, onDone, onCancel }: { c: Company; isNew: boolean; onDone: () => void; onCancel: () => void }) {
  const [c, setC] = useState<Company>({ ...initial, disclaimer: initial.disclaimer ?? { en: "", hi: "" } });
  const [err, setErr] = useState<string | null>(null);
  const [permitted, setPermitted] = useState(!!initial.logo_permission_at);
  const [busy, setBusy] = useState(false);
  // Stored files to delete once the row is saved, and files uploaded in this session (deleted on cancel).
  const [stale, setStale] = useState<string[]>([]);
  const [fresh, setFresh] = useState<string[]>([]);
  const set = <K extends keyof Company>(k: K, v: Company[K]) => setC((p) => ({ ...p, [k]: v }));
  const loc = (k: "name" | "disclaimer", l: "en" | "hi", v: string) => setC((p) => ({ ...p, [k]: { ...(p[k] ?? {}), [l]: v } }));
  const save = async (e: React.FormEvent) => {
    e.preventDefault(); setErr(null);
    const clean = (o: Loc | null) => (o && (o.en?.trim() || o.hi?.trim()) ? { en: o.en?.trim() ?? "", hi: o.hi?.trim() ?? "" } : null);
    const withLogo = permitted && !!c.logo_permission_at;
    if (permitted && (!c.logo_permission_at || !c.logo_permission_ref?.trim())) { setErr("Enter the permission date and reference."); return; }
    const row = {
      logo_url: withLogo ? c.logo_url : null,
      logo_permission_at: withLogo ? c.logo_permission_at : null,
      logo_permission_ref: withLogo ? c.logo_permission_ref?.trim() || null : null,
      logo_permission_until: withLogo ? c.logo_permission_until || null : null,
      slug: c.slug.trim(), name: { en: c.name.en?.trim(), hi: c.name.hi?.trim() }, website_url: c.website_url?.trim() || null,
      disclaimer: clean(c.disclaimer), allow_custom_offers: c.allow_custom_offers, is_active: c.is_active, sort: c.sort,
    };
    const { error } = isNew ? await supabase.from("companies").insert({ id: c.id, ...row }) : await supabase.from("companies").update(row).eq("id", c.id);
    if (error) { setErr(error.message); return; }
    // Files no longer referenced: replaced logos, a removed logo, or a withdrawn permission.
    const gone = [...stale, ...(!row.logo_url ? fresh.concat(pathOf(initial.logo_url) ?? []) : [])];
    const keep = pathOf(row.logo_url);
    const del = [...new Set(gone)].filter((p) => p !== keep);
    if (del.length) await supabase.storage.from(BUCKET).remove(del);
    onDone();
  };
  const cancel = () => { if (fresh.length) void supabase.storage.from(BUCKET).remove(fresh); onCancel(); };
  // logos/<company_id>/<n>.webp; a new <n> per upload so caches never serve the old file.
  const upload = async (file: File) => {
    setErr(null); setBusy(true);
    try {
      const blob = await toLogoWebp(file);
      const path = `logos/${c.id}/${Date.now()}.webp`;
      const { error } = await supabase.storage.from(BUCKET).upload(path, blob, { contentType: "image/webp" });
      if (error) { setErr(error.message); return; }
      const old = pathOf(c.logo_url);
      if (old) setStale((p) => [...p, old]);
      setFresh((p) => [...p, path]);
      set("logo_url", supabase.storage.from(BUCKET).getPublicUrl(path).data.publicUrl);
    } catch (e) { setErr((e as Error).message); } finally { setBusy(false); }
  };
  const removeLogo = () => { const old = pathOf(c.logo_url); if (old) setStale((p) => [...p, old]); set("logo_url", null); };
  const togglePermission = (on: boolean) => {
    setPermitted(on);
    if (!on) { removeLogo(); setC((p) => ({ ...p, logo_url: null, logo_permission_at: null, logo_permission_ref: null, logo_permission_until: null })); }
  };
  return (
    <form onSubmit={save}>
      <h1>{isNew ? "New company" : c.name.en}</h1>
      <div className="card">
        <div className="grid2">
          <label>Name (English)<input required maxLength={80} value={c.name.en ?? ""} onChange={(e) => loc("name", "en", e.target.value)} /></label>
          <label>Name (Hindi)<input required maxLength={80} lang="hi" value={c.name.hi ?? ""} onChange={(e) => loc("name", "hi", e.target.value)} /></label>
          <label>Slug<input required pattern="[a-z0-9\-]{2,40}" value={c.slug} onChange={(e) => set("slug", e.target.value)} /></label>
          <label>Website (https)<input type="url" pattern="https://.*" value={c.website_url ?? ""} onChange={(e) => set("website_url", e.target.value)} /></label>
          <label>Disclaimer (English)<textarea maxLength={300} rows={3} value={c.disclaimer?.en ?? ""} onChange={(e) => loc("disclaimer", "en", e.target.value)} /></label>
          <label>Disclaimer (Hindi)<textarea maxLength={300} rows={3} lang="hi" value={c.disclaimer?.hi ?? ""} onChange={(e) => loc("disclaimer", "hi", e.target.value)} /></label>
          <label>Sort<input type="number" value={c.sort} onChange={(e) => set("sort", Number(e.target.value))} /></label>
        </div>
        <label className="check"><input type="checkbox" checked={c.is_active} onChange={(e) => set("is_active", e.target.checked)} />Active (inactive companies leave pickers and sites; never deleted)</label>
        <label className="check"><input type="checkbox" checked={c.allow_custom_offers} onChange={(e) => set("allow_custom_offers", e.target.checked)} />Allow agents' custom offers</label>
        {err && <div className="error" role="alert">{err}</div>}
        <div className="row"><button className="primary">Save</button><button type="button" onClick={cancel}>Cancel</button></div>
      </div>
      <div className="card">
        <h2>Logo</h2>
        <label className="check"><input type="checkbox" checked={permitted} onChange={(e) => togglePermission(e.target.checked)} />The company has given written permission to show this logo</label>
        {permitted && (
          <div className="grid2">
            <label>Permission date<input type="date" required value={c.logo_permission_at ?? ""} onChange={(e) => set("logo_permission_at", e.target.value || null)} /></label>
            <label>Reference (email or letter number)<input required maxLength={200} value={c.logo_permission_ref ?? ""} onChange={(e) => set("logo_permission_ref", e.target.value)} /></label>
            <label>Permission ends on (optional)<input type="date" value={c.logo_permission_until ?? ""} onChange={(e) => set("logo_permission_until", e.target.value || null)} /></label>
          </div>
        )}
        {isNew
          ? <span className="muted">Save the company first, then add its logo.</span>
          : <label>Logo file (PNG, JPEG or WebP; made square)
              <input type="file" accept="image/png,image/jpeg,image/webp" disabled={!permitted || !c.logo_permission_at || busy}
                onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ""; if (f) void upload(f); }} />
            </label>}
        {c.logo_url && (
          <div className="row">
            <img className="logo-img" src={c.logo_url} alt="Logo at 24 pixels" width={24} height={24} />
            <img className="logo-img" src={c.logo_url} alt="Logo at 40 pixels" width={40} height={40} />
            <button type="button" onClick={removeLogo}>Remove logo</button>
          </div>
        )}
        {ended(c) && <span className="muted">Permission ended: the logo isn't shown anywhere.</span>}
        <span className="muted">Unticking permission or removing the logo clears it from every site when you save.</span>
      </div>
    </form>
  );
}
