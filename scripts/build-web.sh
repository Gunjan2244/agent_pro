#!/bin/sh
# Builds web/dist: web/ + shared/, config.js from env, generated tokens.css.
# Env: RELEASE=1 (fails while privacy-notices.json releaseBlockers or any {UPPER_CASE} i18n placeholder remains; otherwise only warns), SUPABASE_URL, SUPABASE_ANON_KEY, SITE_BASE_URL (default http://localhost:8080), WEB_DEV=1 (local only: adds mock backend + fixtures)
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/web/dist"
# Release gate (PRIVACY.md, DECISIONS #61): unfilled placeholders (grievance officer, email provider) must never ship.
LEFT="$(node "$ROOT/scripts/release-check.mjs" || true)"
if [ -n "$LEFT" ]; then
  if [ "${RELEASE:-}" = "1" ]; then echo "RELEASE BLOCKED: unfilled placeholders: $LEFT" >&2; exit 1; fi
  echo "WARNING: preview build; release blockers remain: $LEFT" >&2
fi
rm -rf "$OUT"; mkdir -p "$OUT"
cd "$ROOT/web"
for f in index.html app.js api.js i18n.js head.js form.js route.js privacy.js legal.js _headers sections styles templates fonts; do cp -R "$f" "$OUT/"; done
cp -R "$ROOT/shared" "$OUT/shared"
if [ "${WEB_DEV:-}" = "1" ]; then
  cp dev-mock.js "$OUT/"; cp -R dev-fixtures "$OUT/dev-fixtures"
  echo "WARNING: dev build (mock backend included). Never deploy this." >&2
fi
node "$ROOT/scripts/gen-tokens.mjs" "$OUT/tokens.css"
esc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
cat > "$OUT/config.js" <<EOF
window.AGENTPRO_CONFIG = {
  SUPABASE_URL: "$(esc "${SUPABASE_URL:-}")",
  SUPABASE_ANON_KEY: "$(esc "${SUPABASE_ANON_KEY:-}")",
  SITE_BASE_URL: "$(esc "${SITE_BASE_URL:-http://localhost:8080}")"
};
EOF
if [ -z "${SUPABASE_URL:-}" ] && [ "${WEB_DEV:-}" != "1" ]; then echo "WARNING: SUPABASE_URL is empty" >&2; fi
echo "built $OUT"
