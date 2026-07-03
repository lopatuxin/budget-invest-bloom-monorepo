#!/bin/sh
# Guaranteed daily backup: idempotent and self-healing.
#
# Instead of firing once at 21:00 and losing the whole day on any transient
# failure, the scheduler ticks hourly and this script ensures exactly ONE
# successful backup exists per daily 21:00 slot. If the host or a database was
# down at 21:00, a later tick (or the next container startup) catches up.
#
# Guarantees:
#   * idempotent  — a slot already backed up is skipped (state marker in /state);
#   * catch-up    — a missed slot is completed as soon as things come back up;
#   * per-DB      — each database is dumped and uploaded independently with
#                   retry/backoff, so one DB being down never blocks the others
#                   nor loses an already-produced dump;
#   * all-or-mark — the slot is marked done only after EVERY dump reached Yandex;
#   * single-run  — a lock prevents an overlapping run from colliding on files.

DATE=$(date +%Y-%m-%d_%H-%M)
BACKUP_DIR=/backups
STATE_DIR=/state
STATE_FILE="$STATE_DIR/last_success"
LOCK_DIR="$STATE_DIR/lock"
REMOTE_DIR="${YADISK_BACKUP_DIR:-backups/budget-invest-bloom}"
RETRY_MAX=5

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

# --- Determine the target daily slot (the most recent elapsed 21:00 boundary) ---
# Before 21:00 the target is yesterday's slot (so a slot missed overnight is still
# caught up the next morning); from 21:00 onward it is today's slot.
HOUR=$(date +%H)
if [ "$HOUR" -ge 21 ]; then
  TARGET=$(date +%F)
else
  TARGET=$(date -d @$(( $(date +%s) - 86400 )) +%F)
fi

LAST_SUCCESS=""
[ -f "$STATE_FILE" ] && LAST_SUCCESS=$(cat "$STATE_FILE")

if [ "$LAST_SUCCESS" = "$TARGET" ]; then
  echo "=== Backup for slot $TARGET already done (last_success=$LAST_SUCCESS). Nothing to do. ==="
  exit 0
fi

echo "=== Step: Starting backup at $DATE (target slot: $TARGET, last success: ${LAST_SUCCESS:-none}) ==="

# --- Dump one database with retry/backoff, then upload it independently. ---
# A transient outage of one DB (e.g. it is still starting up and its host name is
# not yet resolvable) must neither block the other databases nor lose a dump that
# already succeeded. The dump file is named by the SLOT (not wall-clock), so a
# re-run for the same slot overwrites the same object instead of piling up copies.
# Returns 0 only if BOTH the dump and its upload succeed.
backup_db() {
  label=$1; host=$2; user=$3; db=$4; pass=$5
  outfile="$BACKUP_DIR/${db}_$TARGET.dump"

  # Misconfiguration (empty credentials) is NOT a transient error — fail fast with
  # a clear message instead of wasting the whole retry/backoff budget on it.
  if [ -z "$user" ] || [ -z "$db" ] || [ -z "$pass" ]; then
    echo "ERROR: $label backup misconfigured (empty user/db/password); skipping (not transient)."
    return 1
  fi

  echo "=== Step: Dumping $label database ($host/$db) ==="
  attempt=1
  while true; do
    if PGPASSWORD="$pass" pg_dump -h "$host" -p 5432 -U "$user" -d "$db" -F c -f "$outfile"; then
      echo "$label dump created: $(basename "$outfile")"
      break
    fi
    if [ "$attempt" -ge "$RETRY_MAX" ]; then
      echo "ERROR: pg_dump $label failed after $RETRY_MAX attempts; skipping."
      rm -f "$outfile"
      return 1
    fi
    delay=$((attempt * 15))
    echo "WARNING: pg_dump $label failed (attempt $attempt/$RETRY_MAX); retrying in ${delay}s (DB may be starting up)."
    sleep "$delay"
    attempt=$((attempt + 1))
  done

  echo "=== Step: Uploading $label dump to Yandex Disk ==="
  if rclone copy "$outfile" "yadisk:${REMOTE_DIR}/" --log-level INFO; then
    echo "$label dump uploaded."
    rm -f "$outfile"
    return 0
  fi
  echo "ERROR: upload of $label dump failed; will retry on next tick."
  rm -f "$outfile"
  return 1
}

FAILED=0
backup_db auth       "${AUTH_POSTGRES_HOST:-auth-postgres}"             "$AUTH_POSTGRES_USER"       "$AUTH_POSTGRES_DB"       "$AUTH_POSTGRES_PASSWORD"       || FAILED=1
backup_db budget     "${BUDGET_POSTGRES_HOST:-budget-postgres}"         "$BUDGET_POSTGRES_USER"     "$BUDGET_POSTGRES_DB"     "$BUDGET_POSTGRES_PASSWORD"     || FAILED=1
backup_db investment "${INVESTMENT_POSTGRES_HOST:-investment-postgres}" "$INVESTMENT_POSTGRES_USER" "$INVESTMENT_POSTGRES_DB" "$INVESTMENT_POSTGRES_PASSWORD" || FAILED=1

if [ "$FAILED" -ne 0 ]; then
  echo "=== Backup INCOMPLETE for slot $TARGET; NOT marking success. Next tick will retry. ==="
  exit 1
fi

# Every dump reached Yandex — mark the slot done and prune old remote backups.
if ! echo "$TARGET" > "$STATE_FILE"; then
  echo "WARNING: could not persist success marker to $STATE_FILE; next tick may re-run the slot."
fi

echo "=== Step: Removing backups older than 7 days from Yandex Disk ==="
rclone delete "yadisk:${REMOTE_DIR}/" --min-age 7d --log-level INFO ||
  echo "WARNING: pruning old backups failed (non-fatal)."

echo "=== Backup completed for slot $TARGET at $DATE ==="
