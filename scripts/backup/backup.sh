#!/bin/sh
set -e

DATE=$(date +%Y-%m-%d_%H-%M)
BACKUP_DIR=/backups
REMOTE_DIR="${YADISK_BACKUP_DIR:-backups/budget-invest-bloom}"

echo "=== Step: Starting backup at $DATE ==="

# --- Auth DB ---
echo "=== Step: Dumping auth database ==="
PGPASSWORD="$AUTH_POSTGRES_PASSWORD" pg_dump \
  -h auth-postgres \
  -p 5432 \
  -U "$AUTH_POSTGRES_USER" \
  -d "$AUTH_POSTGRES_DB" \
  -F c \
  -f "$BACKUP_DIR/auth_dev_$DATE.dump"
echo "Auth dump created: auth_dev_$DATE.dump"

# --- Budget DB ---
echo "=== Step: Dumping budget database ==="
PGPASSWORD="$BUDGET_POSTGRES_PASSWORD" pg_dump \
  -h budget-postgres \
  -p 5432 \
  -U "$BUDGET_POSTGRES_USER" \
  -d "$BUDGET_POSTGRES_DB" \
  -F c \
  -f "$BACKUP_DIR/budget_dev_$DATE.dump"
echo "Budget dump created: budget_dev_$DATE.dump"

# --- Upload to Yandex Disk ---
echo "=== Step: Uploading dumps to Yandex Disk ==="
rclone copy "$BACKUP_DIR/" "yadisk:${REMOTE_DIR}/" \
  --include "*.dump" \
  --log-level INFO

# --- Cleanup old backups (older than 7 days) ---
echo "=== Step: Removing backups older than 7 days from Yandex Disk ==="
rclone delete "yadisk:${REMOTE_DIR}/" \
  --min-age 7d \
  --log-level INFO

# --- Remove local temp files ---
echo "=== Step: Cleaning up local temp files ==="
rm -f "$BACKUP_DIR"/*.dump

echo "Backup completed: $DATE"
