import { useCallback, useEffect, useState } from "react";
import { supabase } from "./supabase";
import { SkeletonRows } from "./Skeleton";

const PAGE = 50;
type Entry = {
  id: number; at: string; actor_id: string | null; actor_kind: string; action: string;
  entity: string | null; entity_id: string | null; changes: unknown; request_ip: string | null;
};

export function AuditLog({ adminEmails }: { adminEmails: Record<string, string> }) {
  const [rows, setRows] = useState<Entry[]>([]);
  const [page, setPage] = useState(0);
  const [kind, setKind] = useState("");
  const [action, setAction] = useState("");
  const [entity, setEntity] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);

  const load = useCallback(async () => {
    setErr(null);
    let q = supabase.from("audit_log").select("*").order("at", { ascending: false }).order("id", { ascending: false });
    if (kind) q = q.eq("actor_kind", kind);
    if (action.trim()) q = q.ilike("action", `%${action.trim()}%`);
    if (entity.trim()) q = q.eq("entity", entity.trim());
    if (from) q = q.gte("at", new Date(`${from}T00:00:00`).toISOString());
    if (to) q = q.lt("at", new Date(new Date(`${to}T00:00:00`).getTime() + 86400000).toISOString());
    const { data, error } = await q.range(page * PAGE, page * PAGE + PAGE - 1);
    setLoaded(true);
    if (error) { setErr(error.message); return; }
    setRows((data as Entry[]) ?? []);
  }, [kind, action, entity, from, to, page]);
  useEffect(() => { void load(); }, [load]);

  const f = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(0); };
  const who = (id: string | null) => (id ? adminEmails[id] ?? id.slice(0, 8) : "-");

  return (
    <>
      <h1>Audit log</h1>
      <div className="row">
        <label>Actor kind
          <select value={kind} onChange={(e) => f(setKind)(e.target.value)}>
            <option value="">Any</option><option value="admin">Admin</option><option value="agent">Agent</option><option value="system">System</option>
          </select>
        </label>
        <label>Action contains<input value={action} onChange={(e) => f(setAction)(e.target.value)} /></label>
        <label>Entity<input value={entity} onChange={(e) => f(setEntity)(e.target.value)} /></label>
        <label>From<input type="date" value={from} onChange={(e) => f(setFrom)(e.target.value)} /></label>
        <label>To<input type="date" value={to} onChange={(e) => f(setTo)(e.target.value)} /></label>
      </div>
      {err && <div className="error" role="alert">{err}</div>}
      <table aria-busy={!loaded}>
        <thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Entity</th><th>Changes</th><th>IP</th></tr></thead>
        <tbody>
          {!loaded && <SkeletonRows cols={6} />}
          {rows.map((r) => (
            <tr key={r.id}>
              <td>{new Date(r.at).toLocaleString("en-IN")}</td>
              <td>{who(r.actor_id)} <span className="pill">{r.actor_kind}</span></td>
              <td>{r.action}</td>
              <td>{r.entity ?? "-"}{r.entity_id && <div className="muted">{r.entity_id.slice(0, 8)}</div>}</td>
              <td>{r.changes ? <details><summary>View</summary><code>{JSON.stringify(r.changes)}</code></details> : "-"}</td>
              <td>{r.request_ip ?? "-"}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {loaded && rows.length === 0 && !err && <p className="muted">Nothing here.</p>}
      <div className="row" style={{ marginTop: "var(--space-m)" }}>
        <button disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
        <button disabled={rows.length < PAGE} onClick={() => setPage(page + 1)}>Older</button>
        <span className="muted">Page {page + 1}</span>
      </div>
    </>
  );
}
