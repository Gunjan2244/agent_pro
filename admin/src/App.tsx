import { useCallback, useEffect, useRef, useState } from "react";
import type { Session } from "@supabase/supabase-js";
import { configured, supabase } from "./supabase";
import { SignIn, MfaGate } from "./Auth";
import { Companies } from "./Companies";
import { Memberships } from "./Memberships";
import { Offers } from "./Offers";
import { Plans } from "./Plans";
import { Admins, type AdminRow } from "./Admins";
import { AuditLog } from "./AuditLog";
import { ShellSkeleton } from "./Skeleton";

const IDLE_MS = 30 * 60 * 1000;
type Access = "checking" | "none" | "ok";
type Page = "memberships" | "companies" | "plans" | "offers" | "admins" | "audit";

export function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(false);
  const [aal2, setAal2] = useState(false);
  const [access, setAccess] = useState<Access>("checking");
  const [role, setRole] = useState<string | null>(null);
  const [emails, setEmails] = useState<Record<string, string>>({});
  const onAdmins = useCallback((rows: AdminRow[]) => setEmails(Object.fromEntries(rows.map((r) => [r.user_id, r.email]))), []);
  const [page, setPage] = useState<Page>("memberships");
  const idle = useRef<number>(0);

  const signOut = useCallback(() => { void supabase.auth.signOut(); }, []);

  useEffect(() => {
    void supabase.auth.getSession().then(({ data }) => { setSession(data.session); setReady(true); });
    const { data } = supabase.auth.onAuthStateChange((_e, s) => setSession(s));
    return () => data.subscription.unsubscribe();
  }, []);

  // MFA level and the allow-list are re-checked whenever the session changes. RLS enforces both too (is_admin()).
  useEffect(() => {
    if (!session) { setAal2(false); setAccess("checking"); return; }
    let live = true;
    void (async () => {
      const { data: lvl } = await supabase.auth.mfa.getAuthenticatorAssuranceLevel();
      const ok = lvl?.currentLevel === "aal2";
      if (!live) return;
      setAal2(ok);
      if (!ok) return;
      const { data: row } = await supabase.from("admins").select("role,revoked_at").eq("user_id", session.user.id).maybeSingle();
      if (!live) return;
      if (!row || row.revoked_at) { setAccess("none"); void supabase.auth.signOut(); } else { setRole(row.role as string); setAccess("ok"); }
    })();
    return () => { live = false; };
  }, [session]);

  // Idle timeout: 30 minutes without input signs out (docs/OFFERS.md, Auth).
  useEffect(() => {
    if (!session) return;
    const reset = () => { window.clearTimeout(idle.current); idle.current = window.setTimeout(signOut, IDLE_MS); };
    const ev = ["pointerdown", "keydown", "scroll"] as const;
    ev.forEach((e) => window.addEventListener(e, reset));
    reset();
    return () => { ev.forEach((e) => window.removeEventListener(e, reset)); window.clearTimeout(idle.current); };
  }, [session, signOut]);

  if (!configured) return <div className="center"><h1>Not configured</h1><p>Set VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY (see .env.example).</p></div>;
  if (!ready) return <ShellSkeleton />;
  if (!session) return <SignIn denied={access === "none"} />;
  if (!aal2) return <MfaGate onDone={() => setSession({ ...session })} onCancel={signOut} />;
  if (access !== "ok") return <ShellSkeleton />;

  return (
    <div className="shell">
      <nav className="nav">
        <strong>Agent PRO Admin</strong>
        <button className={page === "memberships" ? "active" : ""} onClick={() => setPage("memberships")}>Membership requests</button>
        <button className={page === "companies" ? "active" : ""} onClick={() => setPage("companies")}>Companies</button>
        <button className={page === "plans" ? "active" : ""} onClick={() => setPage("plans")}>Plans</button>
        <button className={page === "offers" ? "active" : ""} onClick={() => setPage("offers")}>Offers</button>
        {role === "owner" && <button className={page === "admins" ? "active" : ""} onClick={() => setPage("admins")}>Admins</button>}
        {role === "owner" && <button className={page === "audit" ? "active" : ""} onClick={() => setPage("audit")}>Audit log</button>}
        <span className="muted">{session.user.email}</span>
        <button onClick={signOut}>Sign out</button>
      </nav>
      <main className="main">{page === "memberships" ? <Memberships /> : page === "offers" ? <Offers /> : page === "plans" ? <Plans />
        : page === "admins" && role === "owner" ? <Admins selfId={session.user.id} onLoaded={onAdmins} />
        : page === "audit" && role === "owner" ? <AuditLog adminEmails={emails} /> : <Companies />}</main>
    </div>
  );
}
