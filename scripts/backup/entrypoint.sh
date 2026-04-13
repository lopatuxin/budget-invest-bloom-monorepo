#!/bin/sh
set -e

# Validate required environment variables
if [ -z "$YADISK_LOGIN" ] || [ -z "$YADISK_APP_PASSWORD" ]; then
  echo "ERROR: Missing required variables."
  echo "Please set YADISK_LOGIN and YADISK_APP_PASSWORD in your .env file."
  echo "See instructions in docs/PLAN-db-backup.md for how to get an app password."
  exit 1
fi

# Generate rclone config with obscured password
mkdir -p /root/.config/rclone
cat > /root/.config/rclone/rclone.conf <<EOF
[yadisk]
type = webdav
url = https://webdav.yandex.ru
vendor = other
user = ${YADISK_LOGIN}
pass = $(rclone obscure "$YADISK_APP_PASSWORD")
EOF

echo "rclone config generated for user: ${YADISK_LOGIN}"

# Ensure backup directory exists on Yandex Disk
BACKUP_DIR="${YADISK_BACKUP_DIR:-backups/budget-invest-bloom}"
echo "Creating remote directory if not exists: yadisk:${BACKUP_DIR}"
rclone mkdir "yadisk:${BACKUP_DIR}"

echo "Starting supercronic..."
exec supercronic /crontab
