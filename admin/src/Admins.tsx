import { useCallback, useEffect, useState } from "react";
import { supabase } from "./supabase";
import { adminRoles, label } from "./catalog";
import { callAdmin } from "./adminApi";

export type AdminRow = {
  user_id: string; email: string; role: string; company_id: string | null; invited_by: string | null;
  revoked_at: string | null; created_at: string; mfa: boolean; last_sign_in_at: string | null;
};
type Company = { id: string; name: { en: string } };
const roleLabel = (k: string) => label(adminRoles.find((r) => r.key === k)?.labelKey ?? k);
const when = (s: string | null) => (s ? new Date(s).toLocaleString("en-IN") : "Never");

export function Admins({ selfId, onLoaded }: { selfId: string; onLoaded?: (rows: AdminRow[]) => void }) {
  const [rows, setRows] = useState<AdminRow[]>([]);
  const [companies, setCompanies] = useState<Record<string, string>>({});
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [edit, setEdit] = useState<string | null>(null);

  const load = useCallback(async () => {
    setErr(null);
    const c = await supabase.from("companies").select("id,name");
    setCompanies(Object.fromEntries(((c.data as Company[]) ?? []).map((x) => [x.id, x.name.en])));
    try {
      const r = await callAdmin<{ admins: AdminRow[] }>("list");
      setRows(r.admins ?? []);
      onLoaded?.(r.admins ?? []);
    } catch (e) { setErr((e as Error).message); }
  }, [onLoaded]);
  useEffect(() => { void load(); }, [load]);

  const run = async (action: string, args: Record<string, unknown>, confirmMsg?: string) => {
    if (confirmMsg && !window.confirm(confirmMsg)) return;
    setBusy(true); setErr(null);
    try { await callAdmin(action, args); setEdit(null); await load(); }
    catch (e) { setErr((e as Error).message); }
    setBusy(false);
  };

  return (
    <>
      <h1>Admins</h1>
      {err && <div className="error" role="alert">{err}</div>}
      <Invite companies={companies} busy={busy} onInvite={(a) => run("invite", a)} />
      <table>
        <thead><tr><th>Email</th><th>Role</th><th>Scope</th><th>MFA</th><th>Last sign-in</th><th>Status</th><th></th></tr></thead>
        <tbody>
          {rows.map((r) => {
            const self = r.user_id === selfId;
            return (
              <tr key={r.user_id}>
                <td>{r.email}</td>
                <td>{roleLabel(r.role)}</td>
                <td>{r.company_id ? companies[r.company_id] ?? r.company_id : "All companies"}</td>
                <td>{r.mfa ? "On" : "Not set up"}</td>
                <td>{when(r.last_sign_in_at)}</td>
                <td>{r.revoked_at ? `Revoked on ${new Date(r.revoked_at).toLocaleDateString("en-IN")}` : "Active"}</td>
                <td>
                  {self ? <span className="muted">You</span> : !r.revoked_at && (edit === r.user_id
                    ? <RoleEdit row={r} companies={companies} busy={busy} onCancel={() => setEdit(null)}
                        onSave={(role, company_id) => run("set-role", { user_id: r.user_id, role, company_id })} />
                    : <div className="row" style={{ marginBottom: 0 }}>
                        <button disabled={busy} onClick={() => setEdit(r.user_id)}>Change role</button>
                        <button disabled={busy} onClick={() => run("reset-mfa", { user_id: r.user_id }, "They'll set up a new authenticator at next sign-in.")}>Reset MFA</button>
                        <button className="danger" disabled={busy} onClick={() => run("revoke", { user_id: r.user_id }, "They'll be signed out everywhere now.")}>Revoke</button>
                      </div>)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {rows.length === 0 && !err && <p className="muted">No admins loaded.</p>}
    </>
  );
}

function Scope({ value, companies, onChange }: { value: string; companies: Record<string, string>; onChange: (v: string) => void }) {
  return (
    <select value={value} onChange={(e) => onChange(e.target.value)} aria-label="Scope">
      <option value="">All companies</option>
      {Object.entries(companies).map(([id, n]) => <option key={id} value={id}>{n}</option>)}
    </select>
  );
}
function Roles({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <select value={value} onChange={(e) => onChange(e.target.value)} aria-label="Role">
      {adminRoles.map((r) => <option key={r.key} value={r.key}>{label(r.labelKey)}</option>)}
    </select>
  );
}

function RoleEdit({ row, companies, busy, onSave, onCancel }: {
  row: AdminRow; companies: Record<string, string>; busy: boolean;
  onSave: (role: string, companyId: string | null) => void; onCancel: () => void;
}) {
  const [role, setRole] = useState(row.role);
  const [scope, setScope] = useState(row.company_id ?? "");
  return (
    <div className="row" style={{ marginBottom: 0 }}>
      <Roles value={role} onChange={setRole} />
      <Scope value={scope} companies={companies} onChange={setScope} />
      <button className="primary" disabled={busy} onClick={() => onSave(role, scope || null)}>Save</button>
      <button onClick={onCancel}>Cancel</button>
    </div>
  );
}

function Invite({ companies, busy, onInvite }: { companies: Record<string, string>; busy: boolean; onInvite: (a: Record<string, unknown>) => void }) {
  const [email, setEmail] = useState("");
  const [role, setRole] = useState(adminRoles[adminRoles.length - 1]?.key ?? "editor");
  const [scope, setScope] = useState("");
  return (
    <form className="card" onSubmit={(e) => { e.preventDefault(); onInvite({ email: email.trim(), role, company_id: scope || null }); setEmail(""); }}>
      <h2>Invite an admin</h2>
      <div className="row" style={{ marginBottom: 0 }}>
        <label>Email<input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} /></label>
        <label>Role<Roles value={role} onChange={setRole} /></label>
        <label>Scope<Scope value={scope} companies={companies} onChange={setScope} /></label>
        <button className="primary" type="submit" disabled={busy || !email.trim()}>Invite</button>
      </div>
    </form>
  );
}
