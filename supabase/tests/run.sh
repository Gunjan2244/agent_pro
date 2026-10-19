#!/usr/bin/env bash
# LOCAL TESTS ONLY — runs the database checks (docs/TESTING.md, "Database") against a throwaway
# postgres container: shim -> migrations (including the generated *_catalog_sync.sql) -> tests.
# Prints PASS/FAIL per check; exits non-zero on any FAIL or unexpected SQL error.
# The generated catalog_sync is required (run `./gradlew generateShared` in mobile/ if it's missing).
# Tests can re-apply the latest catalog_sync with `\i :catalog_sync` (copied into the container), and run the
# demo seed with `\i :demo_offers` (supabase/demo/demo_offers.sql, copied too; inside a transaction they roll back).
#
#   supabase/tests/run.sh            # uses postgres:15 (cached image), removes the container afterwards
#   KEEP=1 supabase/tests/run.sh     # keep the container for debugging
#   PG_IMAGE=postgres:17 supabase/tests/run.sh
#   CONTAINER_NAME=my-sqltest supabase/tests/run.sh   # own container name (parallel runs)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TESTS="$ROOT/supabase/tests"
NAME="${CONTAINER_NAME:-agentpro-sqltest}"
IMAGE="${PG_IMAGE:-postgres:15}"
KEEP="${KEEP:-0}"
LOG="$(mktemp -t agentpro-sqltest.XXXXXX)"

cleanup() {
  if [ "$KEEP" != "1" ]; then docker rm -f "$NAME" >/dev/null 2>&1 || true; fi
  rm -f "$LOG"
}
trap cleanup EXIT

docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --name "$NAME" --memory=384m -e POSTGRES_PASSWORD=postgres "$IMAGE" \
  -c shared_buffers=32MB -c max_connections=20 -c wal_level=logical >/dev/null

# Wait for the entrypoint's init + restart to finish.
for _ in $(seq 1 60); do
  if docker logs "$NAME" 2>&1 | grep -q "PostgreSQL init process complete" \
     && docker exec "$NAME" pg_isready -U postgres -q 2>/dev/null; then
    break
  fi
  sleep 1
done
docker exec "$NAME" pg_isready -U postgres -q

CATALOG_SYNC_IN_CONTAINER=/tmp/catalog_sync.sql
DEMO_OFFERS_IN_CONTAINER=/tmp/demo_offers.sql

psql_file() {  # psql_file <on_error_stop 0|1> <file...>  (files are streamed as one session)
  local stop="$1"; shift
  cat "$@" | docker exec -i "$NAME" psql -U postgres -d postgres -q -X -v ON_ERROR_STOP="$stop" \
    -v catalog_sync="$CATALOG_SYNC_IN_CONTAINER" -v demo_offers="$DEMO_OFFERS_IN_CONTAINER" -f - 2>&1
}

# 1. shim + migrations (the generated catalog_sync files sort last among them)
shopt -s nullglob
MIGRATIONS=("$ROOT"/supabase/migrations/*.sql)
SYNC=("$ROOT"/supabase/migrations/*_catalog_sync.sql)
if [ "${#SYNC[@]}" -eq 0 ]; then
  echo "No generated supabase/migrations/*_catalog_sync.sql. Run ./gradlew generateShared in mobile/ first."
  exit 1
fi
echo "catalog_sync: latest is ${SYNC[-1]#"$ROOT"/}"
APPLY=("$TESTS/00_supabase_shim.sql" "${MIGRATIONS[@]}")

for f in "${APPLY[@]}"; do
  if ! out="$(psql_file 1 "$f")"; then
    echo "APPLY FAILED: ${f#"$ROOT"/}"
    echo "$out"
    exit 1
  fi
  echo "applied: ${f#"$ROOT"/}"
done

# 2. tests (one session so psql variables from the seed carry over). The latest catalog_sync is
#    copied in so a test can re-apply it (`\i :catalog_sync`), e.g. to prove overrides survive it.
docker cp "${SYNC[-1]}" "$NAME:$CATALOG_SYNC_IN_CONTAINER" >/dev/null
docker cp "$ROOT/supabase/demo/demo_offers.sql" "$NAME:$DEMO_OFFERS_IN_CONTAINER" >/dev/null
psql_file 0 "$TESTS"/[1-9]*.sql > "$LOG" || true

grep -E 'PASS \||FAIL \|' "$LOG" | sed -E 's/^.*NOTICE:  //'
ERRORS="$(grep -E '(^|: )ERROR:' "$LOG" || true)"
PASSES="$(grep -c 'PASS |' "$LOG" || true)"
FAILS="$(grep -c 'FAIL |' "$LOG" || true)"

if [ -n "$ERRORS" ]; then
  echo
  echo "Unexpected SQL errors:"
  echo "$ERRORS"
fi
echo
echo "Summary: $PASSES passed, $FAILS failed, $(printf '%s' "$ERRORS" | grep -c . || true) unexpected errors"
[ "$FAILS" -eq 0 ] && [ -z "$ERRORS" ]
