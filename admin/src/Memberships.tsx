import { useCallback, useEffect, useState } from "react";
import { supabase } from "./supabase";
import { SkeletonCards } from "./Skeleton";
import { label, membershipStatuses, rejectReasons, statusEntry, typeLabel } from "./catalog";

type Row = {
  id: string; company_id: string | null; suggested_name: string | null; status: string; agency_code: string | null;
  requested_at: string; reject_reason: string | null; review_note: string | null; insurance_types?: string[];
  agent: { full_name: string | null; designation: string | null; city: string | null; email: string | null } | null;
  previous: { status: string } | null;
};
type Company = { id: string; slug: string; name: { en: string } };

export function Memberships() {
  const [status, setStatus] = useState("pending");
  const [rows, setRows] = useState<Row[]>([]);
  const [companies, setCompanies] = useState<Record<string, string>>({});
  const [err, setErr] = useState<string | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);

  const load = useCallback(async () => {
    setErr(null);
    const [q, c] = await Promise.all([
      supabase.rpc("admin_membership_queue", { company_id: null, status }),
      supabase.from("companies").select("id,slug,name"),
    ]);
    setLoaded(true);
    if (q.error) { setErr(q.error.message); return; }
    setRows((q.data as Row[]) ?? []);
    setCompanies(Object.fromEntries(((c.data as Company[]) ?? []).map((x) => [x.id, x.name.en])));
  }, [status]);
  useEffect(() => { void load(); }, [load]);

  return (
    <>
      <h1>Membership requests</h1>
      <div className="row">
        {membershipStatuses.map((s) => (
          <button key={s.key} className={status === s.key ? "active" : ""} onClick={() => setStatus(s.key)}>{label(s.labelKey)}</button>
        ))}
      </div>
      {err && <div className="error" role="alert">{err}</div>}
      {!loaded ? <SkeletonCards /> : rows.length === 0 && !err && <p className="muted">Nothing here.</p>}
      {rows.map((r) => (
        <div className="card" key={r.id}>
          <div className="row" style={{ marginBottom: 0 }}>
            <strong>{r.agent?.full_name ?? "Unnamed agent"}</strong>
            <span className="muted">{[r.agent?.designation, r.agent?.city].filter(Boolean).join(" · ")}</span>
            <span className={`pill ${statusEntry(r.status)?.tone ?? ""}`}>{label(statusEntry(r.status)?.labelKey ?? r.status)}</span>
            {r.previous && <span className="pill">Previously {r.previous.status}</span>}
          </div>
          <div>
            {r.company_id ? <>Company: <strong>{companies[r.company_id] ?? r.company_id}</strong></> : <>Not listed: <strong>{r.suggested_name}</strong> (create the company, then link)</>}
            {r.agency_code && <> · Agency code: <strong>{r.agency_code}</strong></>}
            {r.insurance_types?.length ? <> · {r.insurance_types.map(typeLabel).join(", ")}</> : null}
          </div>
          <div className="muted">{r.agent?.email} · requested {new Date(r.requested_at).toLocaleDateString("en-IN")}</div>
          {r.review_note && <div className="muted">Note: {r.review_note}</div>}
          {open === r.id
            ? <Review row={r} onDone={() => { setOpen(null); void load(); }} onCancel={() => setOpen(null)} />
            : r.company_id && (r.status === "pending" || r.status === "approved") && <div><button onClick={() => setOpen(r.id)}>Review</button></div>}
        </div>
      ))}
    </>
  );
}

function Review({ row, onDone, onCancel }: { row: Row; onDone: () => void; onCancel: () => void }) {
  const [decision, setDecision] = useState(row.status === "approved" ? "revoke" : "approve");
  const [reason, setReason] = useState(rejectReasons[0]?.key ?? "");
  const [note, setNote] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const needNote = decision === "revoke";
  const save = async () => {
    const { error } = await supabase.rpc("admin_review_membership", {
      membership_id: row.id, decision, reject_reason: decision === "reject" ? reason : null, note: note.trim() || null,
    });
    if (error) setErr(error.message); else onDone();
  };
  return (
    <div className="grid2">
      <label>Decision
        <select value={decision} onChange={(e) => setDecision(e.target.value)}>
          {row.status === "pending" && <><option value="approve">Approve</option><option value="reject">Reject</option></>}
          {row.status === "approved" && <option value="revoke">Revoke</option>}
        </select>
      </label>
      {decision === "reject" && <label>Reason<select value={reason} onChange={(e) => setReason(e.target.value)}>{rejectReasons.map((r) => <option key={r.key} value={r.key}>{label(r.labelKey)}</option>)}</select></label>}
      <label>Note shown to the agent{needNote && " (required)"}<input maxLength={200} value={note} onChange={(e) => setNote(e.target.value)} /></label>
      {err && <div className="error" role="alert">{err}</div>}
      <div className="row">
        <button className={decision === "approve" ? "primary" : "danger"} disabled={needNote && !note.trim()} onClick={save}>Confirm</button>
        <button onClick={onCancel}>Cancel</button>
      </div>
    </div>
  );
}
