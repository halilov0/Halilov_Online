# Tiny pg_dump → S3-compatible (Cloudflare R2 / Oracle OS / B2) sidecar.
# Built daily by docker compose. busybox crond fires backup.sh on BACKUP_CRON.
FROM alpine:3.20

RUN apk add --no-cache \
        bash \
        postgresql16-client \
        aws-cli \
        ca-certificates \
        tzdata \
    && rm -rf /var/cache/apk/*

ENV TZ=Asia/Jerusalem

COPY backup.sh /usr/local/bin/backup.sh
COPY audit-export.sh /usr/local/bin/audit-export.sh
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/audit-export.sh /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
