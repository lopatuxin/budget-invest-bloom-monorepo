#!/usr/bin/env bash
# One-time consolidation of the three microservice databases into the single
# `bib` database — one schema per former service (auth / budget / investment).
#
# Run at cutover, AFTER the four old app containers (gateway, auth, budget,
# investment) are stopped so writes are frozen, and AFTER the new `bib-postgres`
# container is up and healthy. The three source postgres containers must still be
# running (they are the dump source and remain the rollback copy).
#
# It uses `docker exec` to reach both the source containers and the target, so it
# does NOT depend on host->container port publishing.
#
# NOT idempotent by design: it refuses to run if the target schemas already exist,
# to prevent a double import. Run it once against a fresh, empty `bib`.
set -euo pipefail

TARGET_CONTAINER="${TARGET_CONTAINER:-bib-postgres}"
TARGET_DB="${TARGET_DB:-bib}"
TARGET_USER="${TARGET_USER:-bib}"

# One line per former service: "schema:source_container:source_db:source_user"
SOURCES="
auth:auth-postgres-dev:auth_dev:auth_user
budget:budget-postgres-dev:budget_dev:budget_user
investment:investment-postgres-dev:investment_dev:investment_user
"

tgt() { docker exec -i "$TARGET_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$TARGET_USER" -d "$TARGET_DB" "$@"; }

echo "=== Pre-flight: '$TARGET_DB' must not yet contain the three schemas ==="
for s in auth budget investment; do
  if tgt -tAc "SELECT 1 FROM information_schema.schemata WHERE schema_name='$s'" | grep -q 1; then
    echo "ERROR: schema '$s' already exists in '$TARGET_DB' — refusing (run once on a fresh bib)."
    exit 1
  fi
done

for entry in $SOURCES; do
  schema=$(echo "$entry" | cut -d: -f1)
  container=$(echo "$entry" | cut -d: -f2)
  db=$(echo "$entry" | cut -d: -f3)
  user=$(echo "$entry" | cut -d: -f4)

  echo "=== Migrating $db ($container) -> schema '$schema' ==="

  # Fresh, empty public in the target before importing this source.
  tgt -c "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;" >/dev/null

  # Dump the whole source database (plain SQL, owner/privileges stripped so the
  # single 'bib' role owns everything) and restore it into the target's public.
  docker exec "$container" pg_dump -U "$user" -d "$db" --no-owner --no-privileges | tgt >/dev/null

  # Move every imported object (tables, data, indexes, constraints, sequences and
  # the Liquibase history tables) into the module schema; leave a fresh public
  # for the next source.
  tgt -c "ALTER SCHEMA public RENAME TO $schema; CREATE SCHEMA public;" >/dev/null

  # If the source died under a Liquibase lock, release it so the monolith can start.
  tgt -c "UPDATE $schema.databasechangeloglock SET locked=false, lockgranted=NULL, lockedby=NULL WHERE id=1;" >/dev/null 2>&1 || true

  echo "  imported into schema '$schema'"
done

echo "=== Validation: per-table row counts (source public vs target schema) ==="
fail=0
for entry in $SOURCES; do
  schema=$(echo "$entry" | cut -d: -f1)
  container=$(echo "$entry" | cut -d: -f2)
  db=$(echo "$entry" | cut -d: -f3)
  user=$(echo "$entry" | cut -d: -f4)
  tables=$(docker exec "$container" psql -tAc \
    "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename" -U "$user" -d "$db")
  for t in $tables; do
    src=$(docker exec "$container" psql -tAc "SELECT count(*) FROM public.\"$t\"" -U "$user" -d "$db")
    dst=$(tgt -tAc "SELECT count(*) FROM $schema.\"$t\"")
    if [ "$src" != "$dst" ]; then
      echo "  MISMATCH $schema.$t: source=$src target=$dst"
      fail=1
    else
      echo "  ok $schema.$t: $src rows"
    fi
  done
done

if [ "$fail" -ne 0 ]; then
  echo "=== VALIDATION FAILED — do NOT switch the frontend to the monolith; investigate. ==="
  exit 1
fi

echo "=== Consolidation complete and row-count validated. ==="
echo "Next: start the monolith against 'bib', confirm Liquibase applies 0 changesets"
echo "(history was imported), then run the smoke checks in docs/Миграция-данных.md."
