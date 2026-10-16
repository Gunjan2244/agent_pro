// Template only. scripts/build-web.sh writes web/dist/config.js from env vars:
//   SUPABASE_URL, SUPABASE_ANON_KEY, SITE_BASE_URL
// Never commit real values. (web/config.js is git-ignored.)
window.AGENTPRO_CONFIG = {
  SUPABASE_URL: "https://YOUR-PROJECT.supabase.co",
  SUPABASE_ANON_KEY: "YOUR-ANON-KEY",
  SITE_BASE_URL: "https://sites.example.com"
};
