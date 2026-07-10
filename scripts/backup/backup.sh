#!/bin/sh
# Guaranteed daily backup of the single `bib` database (all three schemas —
# auth/budget/investment — plus their Liquibase history) in ONE cross-schema
# consistent snapshot, followed by a weekly restore test.
#
# The scheduler ticks hourly and this script ensures exactly ONE successful
# backup exists per daily 21:00 slot. If the host or the database was down at
# 21:00, a later tick (or the next container startup) catches up.
#
# Guarantees (unchanged from the microservices version, now for one database):
#   * idempotent  — a slot already backed up is skipped (state marker in /state);
#   * catch-up    — a missed slot is completed as soon as things come back up;
#   * retry       — the dump is retried with backoff (DB may still be starting);
#   * mark-after-upload — the slot is marked done only after the dump reached Yandex;
#   * single-run  — a lock prevents an overlapping run from colliding on files.
#
# Restore test (weekly): the fresh dump is restored into a throwaway database and
# a smoke row-count check runs against a key table of each schema, then the
# database is dropped. This makes "zero data loss" a verified property, not a
# declaration. It piggybacks on a real backup, so it never needs a separate dump.

DATE=$(date +%Y-%m-%d_%H-%M)
BACKUP_DIR=/backups
STATE_DIR=/state
STATE_FILE="$STATE_DIR/last_success"
RESTORE_STATE_FILE="$STATE_DIR/last_restore_test"
LOCK_DIR="$STATE_DIR/lock"
REMOTE_DIR="${YADISK_BACKUP_DIR:-backups/budget-invest-bloom}"
RETRY_MAX=5

HOST="${BIB_POSTGRES_HOST:-postgres}"
DBUSER="${BIB_POSTGRES_USER:-bib}"
DB="${BIB_POSTGRES_DB:-bib}"
PASS="${BIB_POSTGRES_PASSWORD}"

mkdir -p "$STATE_DIR"

# --- Single-run lock (mkdir is atomic). A stale lock from a run killed mid-backup
# is cleared by entrypoint.sh on every container (re)start, so it can never outlive
# the container and permanently block backups. ---
if mkdir "$LOCK_DIR" 2>/dev/null; then
  trap 'rmdir "$LOCK_DIR" 2>/dev/null' EXIT
else
  echo "=== Another backup run holds the lock ($LOCK_DIR); skipping this tick. ==="
  exit 0
fi

# Misconfiguration (empty credentials) is NOT transient — fail fast with a clear
# message instead of wasting the retry/backoff budget on it.
if [ -z "$DBUSER" ] || [ -z "$DB" ] || [ -z "$PASS" ]; then
  echo "ERROR: backup misconfigured (empty BIB_POSTGRES_USER/DB/PASSWORD); aborting (not transient)."
  exit 1
fi

# --- Determine the target daily slot (the most recent elapsed 21:00 boundary) ---
# Before 21:00 the target is yesterday's slot (so a slot missed overnight is still
# caught up the next morning); from 21:00 onward it is today's slot.
HOUR=$(date +%H)
if [ "$HOUR" -ge 21 ]; then
  TARGET=$(date +%F)
else
  TARGET=$(date -d @$(( $(date +%s) - 86400 )) +%F)
fi

# --- Weekly restore test: restore the given dump into a throwaway database,
# smoke-check a key table of each schema, then drop it. Runs at most once per ISO
# week; failure is non-fatal to the backup (logged, retried next run). ---
restore_test() {
  dumpfile=$1
  week=$(date +%G-W%V)
  last_week=""
  [ -f "$RESTORE_STATE_FILE" ] && last_week=$(cat "$RESTORE_STATE_FILE")
  if [ "$last_week" = "$week" ]; then
    return 0
  fi
  [ -f "$dumpfile" ] || return 0

  echo "=== Step: Weekly restore test (week $week) ==="
  TESTDB="bib_restore_test_$(date +%s)"
  export PGPASSWORD="$PASS"

  if ! psql -h "$HOST" -p 5432 -U "$DBUSER" -d "$DB" -c "CREATE DATABASE $TESTDB" >/dev/null 2>&1; then
    echo "WARNING: restore test — could not create $TESTDB; skipping this week."
    unset PGPASSWORD
    return 0
  fi

  ok=1
  if pg_restore -h "$HOST" -p 5432 -U "$DBUSER" -d "$TESTDB" --no-owner --no-privileges "$dumpfile" >/dev/null 2>&1; then
    # One key table per schema must be readable (proves the schema restored).
    for pair in "auth.users" "budget.categories" "investment.securities"; do
      schema=${pair%.*}
      table=${pair#*.}
      cnt=$(psql -h "$HOST" -p 5432 -U "$DBUSER" -d "$TESTDB" -tAc "SELECT count(*) FROM ${schema}.${table}" 2>/dev/null)
      if [ -z "$cnt" ]; then
        echo "WARNING: restore test — could not read ${schema}.${table}."
        ok=0
      else
        echo "restore test — ${schema}.${table}: ${cnt} rows"
      fi
    done
  else
    echo "WARNING: restore test — pg_restore failed."
    ok=0
  fi

  # Always drop the throwaway database, whatever happened.
  psql -h "$HOST" -p 5432 -U "$DBUSER" -d "$DB" -c "DROP DATABASE IF EXISTS $TESTDB" >/dev/null 2>&1
  unset PGPASSWORD

  if [ "$ok" -eq 1 ]; then
    echo "=== Restore test PASSED for week $week ==="
    echo "$week" > "$RESTORE_STATE_FILE"
  else
    echo "=== Restore test FAILED for week $week (will retry on the next backup) ==="
  fi
}

LAST_SUCCESS=""
[ -f "$STATE_FILE" ] && LAST_SUCCESS=$(cat "$STATE_FILE")

if [ "$LAST_SUCCESS" = "$TARGET" ]; then
  echo "=== Backup for slot $TARGET already done (last_success=$LAST_SUCCESS). Nothing to do. ==="
  exit 0
fi

echo "=== Step: Starting backup at $DATE (target slot: $TARGET, last success: ${LAST_SUCCESS:-none}) ==="

# The dump file is named by the SLOT (not wall-clock), so a re-run for the same
# slot overwrites the same object instead of piling up copies.
OUTFILE="$BACKUP_DIR/bib_$TARGET.dump"

# --- One cross-schema consistent snapshot of the whole `bib` (custom format:
# restorable in full or per-schema by pg_restore). NOT --schema=public and NOT a
# loop over schemas — a single pg_dump of the database captures all three schemas
# and their Liquibase history tables atomically. ---
echo "=== Step: Dumping bib database ($HOST/$DB) ==="
attempt=1
while true; do
  if PGPASSWORD="$PASS" pg_dump -h "$HOST" -p 5432 -U "$DBUSER" -d "$DB" -F c -f "$OUTFILE"; then
    echo "bib dump created: $(basename "$OUTFILE")"
    break
  fi
  if [ "$attempt" -ge "$RETRY_MAX" ]; then
    echo "ERROR: pg_dump failed after $RETRY_MAX attempts; NOT marking success."
    rm -f "$OUTFILE"
    exit 1
  fi
  delay=$((attempt * 15))
  echo "WARNING: pg_dump failed (attempt $attempt/$RETRY_MAX); retrying in ${delay}s (DB may be starting up)."
  sleep "$delay"
  attempt=$((attempt + 1))
done

echo "=== Step: Uploading bib dump to Yandex Disk ==="
if ! rclone copy "$OUTFILE" "yadisk:${REMOTE_DIR}/" --log-level INFO; then
  echo "ERROR: upload of bib dump failed; will retry on next tick. NOT marking success."
  rm -f "$OUTFILE"
  exit 1
fi
echo "bib dump uploaded."

# Dump reached Yandex — mark the slot done and prune old remote backups.
if ! echo "$TARGET" > "$STATE_FILE"; then
  echo "WARNING: could not persist success marker to $STATE_FILE; next tick may re-run the slot."
fi

echo "=== Step: Removing backups older than ${BACKUP_RETENTION_DAYS:-7} days from Yandex Disk ==="
rclone delete "yadisk:${REMOTE_DIR}/" --min-age "${BACKUP_RETENTION_DAYS:-7}d" --log-level INFO ||
  echo "WARNING: pruning old backups failed (non-fatal)."

# Verify the freshly-produced dump actually restores (weekly), using it before removal.
restore_test "$OUTFILE"

rm -f "$OUTFILE"
echo "=== Backup completed for slot $TARGET at $DATE ==="
