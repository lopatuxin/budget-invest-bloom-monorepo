#!/bin/sh
set -e

# Validate required configuration (fatal: nothing can work without the token)
if [ -z "$YADISK_OAUTH_TOKEN" ]; then
  echo "ERROR: Missing required variable YADISK_OAUTH_TOKEN."
  echo "Generate it on a machine with a browser via: rclone authorize \"yandex\""
  echo "then paste the printed JSON into YADISK_OAUTH_TOKEN in your .env file."
  exit 1
fi

# Generate rclone config for the native Yandex Disk backend (REST API via OAuth).
# NOTE: the legacy 'webdav' backend was dropped because Yandex disabled WebDAV
# on the free tariff (HTTP 402). The 'yandex' backend uses the official REST API.
mkdir -p /root/.config/rclone
cat > /root/.config/rclone/rclone.conf <<EOF
[yadisk]
type = yandex
token = ${YADISK_OAUTH_TOKEN}
EOF

echo "rclone config generated (backend: yandex)"

# Probe remote storage. Non-fatal on purpose: a remote outage (402, network, expired
# token) must NOT crash the container, otherwise restart:unless-stopped turns it into
# an endless crash-loop. We warn and still start the scheduler; each run retries.
BACKUP_DIR="${YADISK_BACKUP_DIR:-backups/budget-invest-bloom}"
echo "Ensuring remote directory exists: yadisk:${BACKUP_DIR}"
if rclone mkdir "yadisk:${BACKUP_DIR}"; then
  echo "Remote storage reachable."
else
  echo "WARNING: remote storage not reachable at startup (rclone exit $?)."
  echo "WARNING: scheduler will start anyway and retry on each scheduled run."
fi

echo "Starting supercronic..."
exec supercronic /crontab
