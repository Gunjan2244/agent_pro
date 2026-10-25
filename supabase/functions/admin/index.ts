// Agent PRO — Edge Function `admin` (Phase 8c-1; docs/OFFERS.md "Edge Function `admin`", docs/BACKEND.md
// "Edge Functions → admin"). The admin panel's owner-only actions on the `admins` allow-list.
//
// Contract (fixed; the panel's admin/src/adminApi.ts depends on it):
//   POST {action, ...args} -> 200 {ok: true, ...} | 4xx {error: <code>}
//   codes: unauthorized, forbidden, mfa_required, invalid, not_found, last_owner, self, failed
//   actions: list, invite {email, role, company_id?}, set-role {user_id, role, company_id?}, revoke {user_id},
//            reset-mfa {user_id}
//
// Authorisation, in this order, for every action: a valid caller JWT (Auth getUser), aal2 in its claims
// (mfa_required), and is_admin('owner') evaluated by PostgREST with the caller's own JWT (forbidden). Only then is the
// service-role client used. The SQL helpers (supabase/migrations/20261022183528_admin_function.sql) re-check that the
// actor is an active owner, enforce self / last_owner under a lock, and write the audit_log rows.
//
// verify_jwt stays at the platform default (on).

import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2.58.0";
import { corsHeaders, isAllowedOrigin, preflight } from "./cors.ts";

// shared/catalog.json `adminRoles` (the keys). The function can't read shared/ at runtime, so the keys are repeated
// here; the database's admins_role_check (generated from the same catalog list by catalog_sync) is the authority and
// refuses any other value. Update this list when adminRoles changes.
const ADMIN_ROLES: readonly string[] = ["owner", "editor"];

const CODES = [
  "unauthorized",
  "forbidden",
  "mfa_required",
  "invalid",
  "not_found",
  "last_owner",
  "self",
  "failed",
] as const;
type Code = typeof CODES[number];

// Never 404: the panel reads a 404 as "the function isn't deployed".
const STATUS: Record<Code, number> = {
  unauthorized: 401,
  forbidden: 403,
  mfa_required: 403,
  invalid: 400,
  not_found: 410,
  last_owner: 409,
  self: 409,
  failed: 422,
};

class Fail extends Error {
  constructor(readonly code: Code, detail?: string) {
    super(detail ?? code);
  }
}

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
// Optional: where the invite email's link lands (the panel). Must be on Auth → URL Configuration → Redirect URLs.
const INVITE_REDIRECT_TO = Deno.env.get("ADMIN_INVITE_REDIRECT_TO") ?? "";

const CLIENT_OPTIONS = { auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false } };
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const MAX_BODY = 4096;

type Body = Record<string, unknown>;
type Caller = { id: string };

// ---------------------------------------------------------------------------
// HTTP
// ---------------------------------------------------------------------------

function respond(payload: unknown, status: number, cors: Record<string, string>): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      ...cors,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

Deno.serve(async (req: Request): Promise<Response> => {
  const origin = req.headers.get("Origin");
  if (req.method === "OPTIONS") return preflight(origin);
  const cors = corsHeaders(origin);
  // A browser on any other origin is refused outright (server-to-server calls send no Origin).
  if (origin !== null && !isAllowedOrigin(origin)) return respond({ error: "forbidden" }, STATUS.forbidden, cors);
  if (req.method !== "POST") return respond({ error: "invalid" }, 405, cors);

  try {
    if (!SUPABASE_URL || !ANON_KEY || !SERVICE_KEY) throw new Error("missing SUPABASE_URL / keys in the environment");
    const caller = await authorise(req);
    const body = await readBody(req);
    const result = await dispatch(body, caller, requestIp(req));
    return respond({ ok: true, ...result }, 200, cors);
  } catch (e) {
    if (e instanceof Fail) {
      if (e.code === "failed") console.error(`admin: failed: ${e.message}`);
      return respond({ error: e.code }, STATUS[e.code], cors);
    }
    console.error("admin: unexpected error", e instanceof Error ? e.message : e);
    return respond({ error: "failed" }, STATUS.failed, cors);
  }
});

async function readBody(req: Request): Promise<Body> {
  const text = await req.text();
  if (text.length > MAX_BODY) throw new Fail("invalid", "body too large");
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Fail("invalid", "body is not JSON");
  }
  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) throw new Fail("invalid", "body");
  return parsed as Body;
}

// The first x-forwarded-for entry (the client, as seen by the platform's proxy). The SQL side stores null when it
// isn't an IP address.
function requestIp(req: Request): string | null {
  const first = (req.headers.get("x-forwarded-for") ?? "").split(",")[0].trim();
  return first === "" ? null : first.slice(0, 64);
}

// ---------------------------------------------------------------------------
// Authorisation
// ---------------------------------------------------------------------------

// The JWT's payload, unverified: only read after Auth has accepted the same token in getUser().
function jwtClaims(jwt: string): Record<string, unknown> | null {
  const part = jwt.split(".")[1];
  if (!part) return null;
  try {
    const b64 = part.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(part.length / 4) * 4, "=");
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    const claims = JSON.parse(new TextDecoder().decode(bytes));
    return claims !== null && typeof claims === "object" ? claims as Record<string, unknown> : null;
  } catch {
    return null;
  }
}

async function authorise(req: Request): Promise<Caller> {
  const match = /^Bearer\s+(\S+)$/i.exec(req.headers.get("Authorization") ?? "");
  if (!match) throw new Fail("unauthorized");
  const jwt = match[1];

  // A client that acts as the caller (anon key + their JWT): RLS and is_admin() see their auth.uid() and aal.
  const asCaller = createClient(SUPABASE_URL, ANON_KEY, {
    ...CLIENT_OPTIONS,
    global: { headers: { Authorization: `Bearer ${jwt}` } },
  });

  const { data, error } = await asCaller.auth.getUser(jwt);
  if (error || !data.user) throw new Fail("unauthorized");
  const claims = jwtClaims(jwt);
  if (!claims || claims.sub !== data.user.id) throw new Fail("unauthorized");
  if (claims.aal !== "aal2") throw new Fail("mfa_required");

  const owner = await asCaller.rpc("is_admin", { p_role: "owner" });
  if (owner.error) throw new Fail("failed", `is_admin: ${owner.error.message}`);
  if (owner.data !== true) throw new Fail("forbidden");
  return { id: data.user.id };
}

// The service-role client: created only for a caller who passed authorise().
function serviceClient(): SupabaseClient {
  return createClient(SUPABASE_URL, SERVICE_KEY, CLIENT_OPTIONS);
}

// ---------------------------------------------------------------------------
// Input
// ---------------------------------------------------------------------------

function uuidArg(body: Body, key: string): string {
  const v = body[key];
  if (typeof v !== "string" || !UUID.test(v)) throw new Fail("invalid", key);
  return v.toLowerCase();
}

function roleArg(body: Body): string {
  const v = body.role;
  if (typeof v !== "string" || !ADMIN_ROLES.includes(v)) throw new Fail("invalid", "role");
  return v;
}

// company_id: a uuid, or null / "" for every company.
function companyArg(body: Body): string | null {
  const v = body.company_id;
  if (v === undefined || v === null || v === "") return null;
  if (typeof v !== "string" || !UUID.test(v)) throw new Fail("invalid", "company_id");
  return v.toLowerCase();
}

function emailArg(body: Body): string {
  const v = body.email;
  if (typeof v !== "string") throw new Fail("invalid", "email");
  const email = v.trim().toLowerCase();
  if (email.length > 254 || !EMAIL.test(email)) throw new Fail("invalid", "email");
  return email;
}

// ---------------------------------------------------------------------------
// Service-role calls
// ---------------------------------------------------------------------------

// Calls a svc_admin_* helper. Its exception message is the error code (forbidden, self, last_owner, not_found,
// invalid); anything else is `failed`.
async function svc<T>(db: SupabaseClient, fn: string, args: Record<string, unknown>): Promise<T> {
  const { data, error } = await db.rpc(fn, args);
  if (error) {
    const code = (CODES as readonly string[]).includes(error.message) ? error.message as Code : "failed";
    throw new Fail(code, `${fn}: ${error.code ?? ""} ${error.message}`);
  }
  return data as T;
}

// An Auth admin API error: a refused address is `invalid`, the rest (rate limits, outages) `failed`.
function authFail(what: string, error: { status?: number; code?: string; message: string } | null): Fail {
  const status = error?.status ?? 0;
  const code = status === 400 || status === 422 ? "invalid" : "failed";
  return new Fail(code, `${what}: ${status} ${error?.code ?? ""} ${error?.message ?? "no user returned"}`);
}

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

async function dispatch(body: Body, caller: Caller, ip: string | null): Promise<Record<string, unknown>> {
  const db = serviceClient();
  switch (body.action) {
    case "list":
      return await list(db);
    case "invite":
      return await invite(db, caller, body, ip);
    case "set-role":
      return await setRole(db, caller, body, ip);
    case "revoke":
      return await revoke(db, caller, body, ip);
    case "reset-mfa":
      return await resetMfa(db, caller, body, ip);
    default:
      throw new Fail("invalid", "action");
  }
}

type AdminRow = {
  user_id: string;
  role: string;
  company_id: string | null;
  invited_by: string | null;
  revoked_at: string | null;
  created_at: string;
};

// Every admins row (revoked ones too) with the account's email and last sign-in, and whether it has a verified TOTP
// factor.
async function list(db: SupabaseClient) {
  const { data, error } = await db
    .from("admins")
    .select("user_id, role, company_id, invited_by, revoked_at, created_at")
    .order("created_at", { ascending: true });
  if (error) throw new Fail("failed", `admins: ${error.message}`);
  const admins = await Promise.all((data as AdminRow[]).map(async (row) => {
    const [user, factors] = await Promise.all([
      db.auth.admin.getUserById(row.user_id),
      db.auth.admin.mfa.listFactors({ userId: row.user_id }),
    ]);
    if (user.error) throw authFail("getUserById", user.error);
    if (factors.error) throw authFail("listFactors", factors.error);
    return {
      ...row,
      email: user.data.user?.email ?? null,
      last_sign_in_at: user.data.user?.last_sign_in_at ?? null,
      mfa: (factors.data?.factors ?? []).some((f) => f.factor_type === "totp" && f.status === "verified"),
    };
  }));
  return { admins };
}

type Prepared =
  | { state: "new" }
  | { state: "existing"; user_id: string; confirmed: boolean; active: boolean };

// 1. svc_admin_invite_prepare: checks the email (an agent's account is refused) and the company; for a new address
//    it stores a 15-minute marker so the signup trigger creates no agent profile for the account GoTrue is about to
//    insert (GoTrue applies app_metadata only after the insert, so account_kind can't do it alone).
// 2. inviteUserByEmail (data left empty) for a new address, or again for an earlier invitee who never accepted.
// 3. app_metadata.account_kind = 'admin' (updateUserById), which the trigger honours from then on.
// 4. svc_admin_invite_finish: the admins row (invited_by = caller; a revoked admin is reactivated), the marker
//    deleted, and the admin.invite audit row.
async function invite(db: SupabaseClient, caller: Caller, body: Body, ip: string | null) {
  const email = emailArg(body);
  const role = roleArg(body);
  const companyId = companyArg(body);

  const prepared = await svc<Prepared>(db, "svc_admin_invite_prepare", {
    p_actor: caller.id,
    p_email: email,
    p_company_id: companyId,
  });

  const inviteOptions = INVITE_REDIRECT_TO ? { redirectTo: INVITE_REDIRECT_TO } : {};
  let userId: string;
  if (prepared.state === "new") {
    const { data, error } = await db.auth.admin.inviteUserByEmail(email, inviteOptions);
    if (error || !data.user) {
      await db.rpc("svc_admin_invite_cancel", { p_email: email });
      throw authFail("inviteUserByEmail", error);
    }
    userId = data.user.id;
  } else {
    userId = prepared.user_id;
    if (!prepared.confirmed) {
      const { error } = await db.auth.admin.inviteUserByEmail(email, inviteOptions);
      if (error) throw authFail("inviteUserByEmail (resend)", error);
    }
  }

  // Merged into the existing app_metadata (provider, providers stay).
  const updated = await db.auth.admin.updateUserById(userId, { app_metadata: { account_kind: "admin" } });
  if (updated.error) {
    // The account exists without a profile; inviting the address again finishes it.
    await db.rpc("svc_admin_invite_cancel", { p_email: email });
    throw authFail("updateUserById", updated.error);
  }

  await svc(db, "svc_admin_invite_finish", {
    p_actor: caller.id,
    p_user_id: userId,
    p_email: email,
    p_role: role,
    p_company_id: companyId,
    p_ip: ip,
  });
  return { user_id: userId };
}

// company_id absent keeps the admin's scope; null (or "") means every company.
async function setRole(db: SupabaseClient, caller: Caller, body: Body, ip: string | null) {
  const userId = uuidArg(body, "user_id");
  const role = roleArg(body);
  const setCompany = Object.prototype.hasOwnProperty.call(body, "company_id");
  const companyId = companyArg(body);
  if (userId === caller.id) throw new Fail("self");
  await svc(db, "svc_admin_set_role", {
    p_actor: caller.id,
    p_user_id: userId,
    p_role: role,
    p_company_id: companyId,
    p_set_company: setCompany,
    p_ip: ip,
  });
  return {};
}

// revoked_at = now() and every session ended, in one transaction (svc_admin_revoke deletes the user's
// auth.sessions and auth.refresh_tokens rows; auth.admin.signOut would need the user's own JWT).
async function revoke(db: SupabaseClient, caller: Caller, body: Body, ip: string | null) {
  const userId = uuidArg(body, "user_id");
  if (userId === caller.id) throw new Fail("self");
  const result = await svc<{ changed: boolean; signed_out: boolean }>(db, "svc_admin_revoke", {
    p_actor: caller.id,
    p_user_id: userId,
    p_ip: ip,
  });
  if (!result.signed_out) console.error("admin: revoke could not end the sessions (auth.sessions not writable)");
  return { signed_out: result.signed_out };
}

// Deletes every MFA factor of an admin (Auth also downgrades their sessions to aal1), then writes admin.reset_mfa.
// They enrol again at their next sign-in. Not on yourself: your own session would drop to aal1 mid-action.
async function resetMfa(db: SupabaseClient, caller: Caller, body: Body, ip: string | null) {
  const userId = uuidArg(body, "user_id");
  if (userId === caller.id) throw new Fail("self");
  const target = await db.from("admins").select("user_id").eq("user_id", userId).maybeSingle();
  if (target.error) throw new Fail("failed", `admins: ${target.error.message}`);
  if (!target.data) throw new Fail("not_found");

  const listed = await db.auth.admin.mfa.listFactors({ userId });
  if (listed.error) throw authFail("listFactors", listed.error);
  let removed = 0;
  let failure: Fail | null = null;
  for (const factor of listed.data?.factors ?? []) {
    const { error } = await db.auth.admin.mfa.deleteFactor({ userId, id: factor.id });
    if (error) {
      failure = authFail("deleteFactor", error);
      break;
    }
    removed++;
  }
  // Audited even after a partial failure, with the number actually removed.
  await svc(db, "svc_admin_log_reset_mfa", {
    p_actor: caller.id,
    p_user_id: userId,
    p_factors_removed: removed,
    p_ip: ip,
  });
  if (failure) throw failure;
  return {};
}
