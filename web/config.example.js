// Template only. scripts/build-web.sh writes web/dist/config.js from env vars:
//   SUPABASE_URL, SUPABASE_ANON_KEY, SITE_BASE_URL (+ BUILD_TIME, the build's ISO time, for the Worker's Last-Modified)
// Never commit real values. (web/config.js is git-ignored.)
window.AGENTPRO_CONFIG = {
  SUPABASE_URL: "https://YOUR-PROJECT.supabase.co",
  SUPABASE_ANON_KEY: "YOUR-ANON-KEY",
  SITE_BASE_URL: "https://sites.example.com",
  BUILD_TIME: "2026-01-01T00:00:00Z"
};
