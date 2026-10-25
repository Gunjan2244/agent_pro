import { useEffect, useState } from "react";
import { supabase } from "./supabase";

export function SignIn({ denied }: { denied: boolean }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState<string | null>(denied ? "No access. This account isn't an admin." : null);
  const [busy, setBusy] = useState(false);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault(); setBusy(true); setErr(null);
    const { error } = await supabase.auth.signInWithPassword({ email, password });
    if (error) setErr("Wrong email or password."); // never say which one
    setBusy(false);
  };
  return (
    <form className="center" onSubmit={submit}>
      <h1>Agent PRO Admin</h1>
      <label>Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="username" /></label>
      <label>Password<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" /></label>
      {err && <div className="error" role="alert">{err}</div>}
      <button className="primary" disabled={busy}>Sign in</button>
      <span className="muted">Invite-only. Ask an owner for access.</span>
    </form>
  );
}

// Every session must reach aal2 before any screen loads: enrol a TOTP factor on first sign-in, otherwise verify.
export function MfaGate({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const [factorId, setFactorId] = useState<string | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [secret, setSecret] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    void (async () => {
      const { data } = await supabase.auth.mfa.listFactors();
      const verified = data?.totp.find((f) => f.status === "verified");
      if (verified) { if (live) setFactorId(verified.id); return; }
      for (const f of data?.all ?? []) if (f.status === "unverified") await supabase.auth.mfa.unenroll({ factorId: f.id });
      const { data: en, error } = await supabase.auth.mfa.enroll({ factorType: "totp" });
      if (!live) return;
      if (error || !en) { setErr("Couldn't start MFA setup."); return; }
      setFactorId(en.id); setQr(en.totp.qr_code); setSecret(en.totp.secret);
    })();
    return () => { live = false; };
  }, []);

  const verify = async (e: React.FormEvent) => {
    e.preventDefault(); if (!factorId) return; setErr(null);
    const { data: ch, error: ce } = await supabase.auth.mfa.challenge({ factorId });
    if (ce || !ch) { setErr("Couldn't start the check. Try again."); return; }
    const { error } = await supabase.auth.mfa.verify({ factorId, challengeId: ch.id, code: code.trim() });
    if (error) { setErr("That code didn't work."); setCode(""); return; }
    await supabase.auth.refreshSession();
    onDone();
  };
  return (
    <form className="center" onSubmit={verify}>
      <h1>{qr ? "Set up two-step sign-in" : "Enter your code"}</h1>
      {qr && <>
        <p>Scan this with an authenticator app, then enter the 6-digit code.</p>
        <img src={qr} alt="QR code for your authenticator app" width={180} height={180} />
        <span className="muted">Or enter this key: {secret}</span>
      </>}
      <label>6-digit code<input inputMode="numeric" pattern="[0-9]{6}" maxLength={6} value={code} onChange={(e) => setCode(e.target.value)} required autoFocus autoComplete="one-time-code" /></label>
      {err && <div className="error" role="alert">{err}</div>}
      <button className="primary" disabled={!factorId}>Verify</button>
      <button type="button" onClick={onCancel}>Sign out</button>
    </form>
  );
}
