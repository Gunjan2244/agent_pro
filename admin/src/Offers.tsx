import { useCallback, useEffect, useState } from "react";
import { supabase } from "./supabase";
import { label } from "./catalog";
import catalog from "../../shared/catalog.json";
import schema from "../../shared/offer-schema.json";
import { toOfferWebp } from "./image";
import { SkeletonRows } from "./Skeleton";

type Fields = Record<string, string>;
type Offer = {
  id: string; company_id: string; status: string; content: { en?: Fields; hi?: Fields }; internal_name: string | null;
  image_url: string | null; cta_kind: string; cta_url: string | null; interest: string | null; starts_at: string; ends_at: string; sort: number;
};
type Company = { id: string; name: { en: string } };
type Entry = { key: string; labelKey: string };
const ctaKinds = (catalog as unknown as { offerCtaKinds: Entry[] }).offerCtaKinds;
const interests = (catalog as unknown as { interests: Entry[] }).interests;
const localized = schema.localized as { key: string; type: string; required?: boolean; maxLength: number; labelKey: string }[];
const BUCKET = "company-media";

const istDate = (iso: string) => new Date(new Date(iso).getTime() + 5.5 * 3600e3).toISOString().slice(0, 10);
const phase = (o: Offer) => { const n = Date.now(); return n < +new Date(o.starts_at) ? "upcoming" : n >= +new Date(o.ends_at) ? "ended" : "running"; };
const today = () => istDate(new Date().toISOString());

export function Offers() {
  const [rows, setRows] = useState<Offer[]>([]);
  const [companies, setCompanies] = useState<Company[]>([]);
  const [filter, setFilter] = useState("");
  const [edit, setEdit] = useState<{ o: Offer; isNew: boolean } | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const load = useCallback(async () => {
    const [o, c] = await Promise.all([
      supabase.from("offers").select("*").order("ends_at", { ascending: false }),
      supabase.from("companies").select("id,name").order("sort"),
    ]);
    setLoaded(true);
    if (o.error) setErr(o.error.message); else setRows(o.data as Offer[]);
    setCompanies((c.data as Company[]) ?? []);
  }, []);
  useEffect(() => { void load(); }, [load]);
  const name = (id: string) => companies.find((c) => c.id === id)?.name.en ?? id;

  if (edit) return <OfferForm {...edit} companies={companies} onDone={() => { setEdit(null); void load(); }} onCancel={() => setEdit(null)} />;
  const blank = (): Offer => ({
    id: crypto.randomUUID(), company_id: filter || companies[0]?.id || "", status: "draft", content: { en: {}, hi: {} }, internal_name: null,
    image_url: null, cta_kind: "enquire", cta_url: null, interest: null, starts_at: `${today()}T00:00:00+05:30`,
    ends_at: new Date(Date.now() + 90 * 864e5).toISOString(), sort: 0,
  });
  return (
    <>
      <h1>Offers</h1>
      <div className="row">
        <select value={filter} onChange={(e) => setFilter(e.target.value)}>
          <option value="">All companies</option>{companies.map((c) => <option key={c.id} value={c.id}>{c.name.en}</option>)}
        </select>
        <button className="primary" disabled={!companies.length} onClick={() => setEdit({ o: blank(), isNew: true })}>New offer</button>
      </div>
      {err && <div className="error" role="alert">{err}</div>}
      <table aria-busy={!loaded}>
        <thead><tr><th>Offer</th><th>Company</th><th>Status</th><th>Runs</th><th /></tr></thead>
        <tbody>{!loaded && <SkeletonRows cols={5} />}{rows.filter((o) => !filter || o.company_id === filter).map((o) => (
          <tr key={o.id}>
            <td>{o.content.en?.title || <span className="muted">Untitled</span>}<div className="muted">{o.internal_name}</div></td>
            <td>{name(o.company_id)}</td>
            <td><span className={`pill ${o.status === "live" ? "success" : ""}`}>{o.status}</span> <span className="muted">{phase(o)}</span></td>
            <td className="muted">{istDate(o.starts_at)} → {istDate(o.ends_at)}</td>
            <td><button onClick={() => setEdit({ o, isNew: false })}>Edit</button></td>
          </tr>
        ))}</tbody>
      </table>
    </>
  );
}

function OfferForm({ o: initial, isNew, companies, onDone, onCancel }: { o: Offer; isNew: boolean; companies: Company[]; onDone: () => void; onCancel: () => void }) {
  const [o, setO] = useState<Offer>(initial);
  const [start, setStart] = useState(istDate(initial.starts_at));
  const [end, setEnd] = useState(istDate(initial.ends_at));
  const [msg, setMsg] = useState<string | null>(null);
  const [usage, setUsage] = useState<{ published_sites: number; draft_sites: number } | null>(null);
  const set = <K extends keyof Offer>(k: K, v: Offer[K]) => setO((p) => ({ ...p, [k]: v }));
  const loc = (l: "en" | "hi", k: string, v: string) => setO((p) => ({ ...p, content: { ...p.content, [l]: { ...(p.content[l] ?? {}), [k]: v } } }));

  useEffect(() => {
    if (isNew) return;
    void supabase.rpc("admin_offer_usage", { offer_id: o.id }).then(({ data }) => setUsage(data as typeof usage));
  }, [isNew, o.id]);

  const row = () => {
    const content: Record<string, Fields> = {};
    for (const l of ["en", "hi"] as const) {
      const f: Fields = {};
      for (const { key } of localized) { const v = o.content[l]?.[key]?.trim(); if (v) f[key] = v; }
      content[l] = f;
    }
    return {
      company_id: o.company_id, content, internal_name: o.internal_name?.trim() || null, image_url: o.image_url, cta_kind: o.cta_kind,
      cta_url: o.cta_kind === "link" ? o.cta_url?.trim() || null : null, interest: o.interest || null, sort: o.sort,
      starts_at: `${start}T00:00:00+05:30`, ends_at: `${end}T23:59:59+05:30`,
    };
  };
  const save = async (status?: string) => {
    setMsg(null);
    const r = { ...row(), ...(status ? { status } : {}) };
    const { error } = isNew ? await supabase.from("offers").insert({ id: o.id, ...r }) : await supabase.from("offers").update(r).eq("id", o.id);
    if (error) setMsg(error.message); else onDone();
  };

  // One image per offer at offers/<id>/<n>.webp; a new <n> per upload so caches never serve the old file.
  const upload = async (file: File) => {
    setMsg(null);
    try {
      const blob = await toOfferWebp(file);
      const path = `offers/${o.id}/${Date.now()}.webp`;
      const { error } = await supabase.storage.from(BUCKET).upload(path, blob, { contentType: "image/webp" });
      if (error) { setMsg(error.message); return; }
      const old = o.image_url?.split(`/${BUCKET}/`)[1];
      set("image_url", supabase.storage.from(BUCKET).getPublicUrl(path).data.publicUrl);
      if (old) void supabase.storage.from(BUCKET).remove([old]);
    } catch (e) { setMsg((e as Error).message); }
  };

  return (
    <form onSubmit={(e) => { e.preventDefault(); void save(); }}>
      <h1>{isNew ? "New offer" : o.content.en?.title || "Offer"} <span className="pill">{o.status}</span></h1>
      <div className="card">
        <div className="grid2">
          <label>Company<select value={o.company_id} disabled={!isNew} onChange={(e) => set("company_id", e.target.value)}>{companies.map((c) => <option key={c.id} value={c.id}>{c.name.en}</option>)}</select></label>
          <label>Internal name (admins only; the real product name)<input maxLength={120} value={o.internal_name ?? ""} onChange={(e) => set("internal_name", e.target.value)} /></label>
        </div>
        <span className="muted">Public text must be generic: company and product names are rejected when saving.</span>
        <div className="grid2">
          {(["en", "hi"] as const).map((l) => (
            <div key={l} className="card" style={{ margin: 0 }}>
              <h2>{l === "en" ? "English" : "Hindi"}</h2>
              {localized.map((f) => {
                const v = o.content[l]?.[f.key] ?? "";
                const Tag = f.type === "multiline" ? "textarea" : "input";
                return (
                  <label key={f.key}>{label(f.labelKey)}{f.required && " *"} <span className="muted">{v.length}/{f.maxLength}</span>
                    <Tag lang={l} maxLength={f.maxLength} rows={3} value={v} onChange={(e) => loc(l, f.key, e.target.value)} />
                  </label>
                );
              })}
            </div>
          ))}
        </div>
        <div className="grid2">
          <label>Starts on (IST)<input type="date" required value={start} onChange={(e) => setStart(e.target.value)} /></label>
          <label>Ends on (IST)<input type="date" required value={end} onChange={(e) => setEnd(e.target.value)} /></label>
          <label>Button<select value={o.cta_kind} onChange={(e) => set("cta_kind", e.target.value)}>{ctaKinds.map((k) => <option key={k.key} value={k.key}>{label(k.labelKey)}</option>)}</select></label>
          {o.cta_kind === "link" && <label>Link (https)<input type="url" pattern="https://.*" value={o.cta_url ?? ""} onChange={(e) => set("cta_url", e.target.value)} /></label>}
          <label>Plan type<select value={o.interest ?? ""} onChange={(e) => set("interest", e.target.value || null)}><option value="">None</option>{interests.map((k) => <option key={k.key} value={k.key}>{label(k.labelKey)}</option>)}</select></label>
          <label>Sort<input type="number" value={o.sort} onChange={(e) => set("sort", Number(e.target.value))} /></label>
        </div>
        <div>
          <h2>Image (one per offer, 16:9)</h2>
          {o.image_url && <img src={o.image_url} alt="" width={320} height={180} style={{ display: "block", marginBottom: 8, objectFit: "cover" }} />}
          <input type="file" accept="image/png,image/jpeg,image/webp" onChange={(e) => e.target.files?.[0] && void upload(e.target.files[0])} />
          {o.image_url && <button type="button" onClick={() => set("image_url", null)}>Remove image</button>}
          <div className="muted">Cropped to 16:9 and saved as WebP under 512 KB. Use your own or licensed artwork, never an insurer's logo. Save the offer to keep the change.</div>
        </div>
        {usage && <div className="muted">Used on {usage.published_sites} published and {usage.draft_sites} draft websites.</div>}
        {msg && <div className="error" role="alert">{msg}</div>}
        <div className="row">
          <button className="primary">Save draft</button>
          {!isNew && o.status === "draft" && <button type="button" onClick={() => void save("live")}>Go live</button>}
          {!isNew && o.status === "live" && <button type="button" className="danger" onClick={() => { if (confirm(`Archive? It will disappear from ${usage?.published_sites ?? "all"} published websites now.`)) void save("archived"); }}>Archive</button>}
          {!isNew && o.status === "archived" && <button type="button" onClick={() => void save("draft")}>Restore to draft</button>}
          <button type="button" onClick={onCancel}>Cancel</button>
        </div>
      </div>
    </form>
  );
}
