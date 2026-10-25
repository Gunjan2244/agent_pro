// CORS for the `admin` Edge Function: only the admin panel's origins, from the ADMIN_ORIGINS secret (comma-separated,
// for example "https://agentpro-admin.sledjesoftwares.workers.dev,http://localhost:5173"). Never "*": a "*" entry is
// ignored, and with ADMIN_ORIGINS unset no browser origin is allowed at all.

const ALLOWED = new Set(
  (Deno.env.get("ADMIN_ORIGINS") ?? "")
    .split(",")
    .map((o) => o.trim().replace(/\/+$/, ""))
    .filter((o) => o !== "" && o !== "*"),
);

// supabase-js functions.invoke sends authorization, apikey, x-client-info and content-type (x-region when set).
const ALLOW_HEADERS = "authorization, apikey, x-client-info, content-type, x-region";

export function isAllowedOrigin(origin: string | null): boolean {
  return origin !== null && ALLOWED.has(origin);
}

// The CORS headers for a response to this origin: the echo of the origin when it's allowed, otherwise none.
export function corsHeaders(origin: string | null): Record<string, string> {
  const headers: Record<string, string> = { Vary: "Origin" };
  if (origin !== null && isAllowedOrigin(origin)) {
    headers["Access-Control-Allow-Origin"] = origin;
    headers["Access-Control-Allow-Headers"] = ALLOW_HEADERS;
    headers["Access-Control-Allow-Methods"] = "POST, OPTIONS";
    headers["Access-Control-Max-Age"] = "600";
  }
  return headers;
}

// The answer to an OPTIONS preflight: 204 with the CORS headers for an allowed origin, 403 without them otherwise.
export function preflight(origin: string | null): Response {
  return isAllowedOrigin(origin)
    ? new Response(null, { status: 204, headers: corsHeaders(origin) })
    : new Response(null, { status: 403, headers: { Vary: "Origin" } });
}
