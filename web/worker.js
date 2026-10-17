// Cloudflare Worker for agentpro-sites (DECISIONS #65). It runs only for /manifest.webmanifest
// (wrangler.jsonc run_worker_first); every other path is served straight from static assets.
// Builds the agent's web app manifest from get_public_site, so a lapsed or unpublished site is never installable.
import { manifestFor, parseConfig, MANIFEST_PATH } from "./manifest.js";
import { resolveTarget } from "./route.js";
import { normalizeSite } from "./api.js";

// A cheap guard before the RPC, not the slug rule (that lives in SQL and the app).
const SLUG = /^[a-z0-9-]{1,64}$/;
const headers = (extra) => ({ "x-content-type-options": "nosniff", ...extra });
const missing = () => new Response("Not found", { status: 404, headers: headers({ "content-type": "text/plain; charset=utf-8", "cache-control": "public, max-age=60" }) });

async function asset(env, url, path) {
  const r = await env.ASSETS.fetch(new URL(path, url));
  if (!r.ok) throw new Error(`asset ${path} ${r.status}`);
  return r;
}

async function rpc(cfg, name, args) {
  const r = await fetch(`${cfg.SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: { "content-type": "application/json", apikey: cfg.SUPABASE_ANON_KEY, authorization: `Bearer ${cfg.SUPABASE_ANON_KEY}` },
    body: JSON.stringify(args),
  });
  if (!r.ok) throw new Error(`rpc ${name} ${r.status}`);
  return r.json();
}

export async function handleManifest(request, env) {
  const url = new URL(request.url);
  const [cfg, catalog, tokens] = await Promise.all([
    asset(env, url, "/config.js").then((r) => r.text()).then(parseConfig),
    asset(env, url, "/shared/catalog.json").then((r) => r.json()),
    asset(env, url, "/shared/design-tokens.json").then((r) => r.json()),
  ]);
  if (!cfg.SUPABASE_URL) throw new Error("config.js has no SUPABASE_URL");
  const primary = cfg.SITE_BASE_URL ? new URL(cfg.SITE_BASE_URL).host : url.host;
  const target = resolveTarget(url, url.searchParams, primary);
  if (!target || target.platform || (target.slug && !SLUG.test(target.slug))) return missing();
  const site = normalizeSite(target.slug ? await rpc(cfg, "get_public_site", { slug: target.slug }) : await rpc(cfg, "get_public_site_by_host", { host: target.host }));
  if (!site) return missing();
  return new Response(JSON.stringify(manifestFor(site, target, { catalog, tokens })), {
    headers: headers({ "content-type": "application/manifest+json; charset=utf-8", "cache-control": "public, max-age=300" }),
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname !== MANIFEST_PATH) return env.ASSETS.fetch(request);
    if (request.method !== "GET" && request.method !== "HEAD") return new Response(null, { status: 405, headers: headers({ allow: "GET, HEAD" }) });
    try {
      return await handleManifest(request, env);
    } catch (e) {
      console.error("manifest", e && e.message);
      return new Response("Unavailable", { status: 503, headers: headers({ "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" }) });
    }
  },
};
