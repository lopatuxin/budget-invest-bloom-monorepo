FROM alpine:3.19

# Install PostgreSQL 15 client (provides pg_dump), ca-certificates, tzdata
RUN apk add --no-cache postgresql15-client ca-certificates tzdata

# Download and install rclone (linux/amd64)
RUN wget -q https://downloads.rclone.org/rclone-current-linux-amd64.zip -O /tmp/rclone.zip && \
    unzip /tmp/rclone.zip -d /tmp/ && \
    mv /tmp/rclone-*-linux-amd64/rclone /usr/local/bin/rclone && \
    chmod +x /usr/local/bin/rclone && \
    rm -rf /tmp/rclone*

# Download and install supercronic v0.2.29 (linux-amd64)
RUN wget -q https://github.com/aptible/supercronic/releases/download/v0.2.29/supercronic-linux-amd64 -O /usr/local/bin/supercronic && \
    chmod +x /usr/local/bin/supercronic

# Create temp directory for dump files
RUN mkdir -p /backups

# Set timezone
ENV TZ=Europe/Moscow

# Copy scripts and crontab
COPY entrypoint.sh /entrypoint.sh
COPY backup.sh /backup.sh
COPY crontab /crontab

RUN chmod +x /entrypoint.sh /backup.sh

ENTRYPOINT ["/entrypoint.sh"]
