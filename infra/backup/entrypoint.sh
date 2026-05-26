#!/bin/bash
# BACKUP_CRON: standard 5-field cron spec for the daily DB dump
#   (default: 03:15 Asia/Jerusalem daily)
# AUDIT_EXPORT_CRON: standard 5-field cron spec for hourly audit_log export
#   (default: 5 minutes past every hour, so it doesn't collide with the DB dump)
# BACKUP_RUN_ONCE=true: run backup.sh once and exit
#   (handy for `docker compose run backup`)
# AUDIT_RUN_ONCE=true: run audit-export.sh once and exit
set -euo pipefail

if [[ "${BACKUP_RUN_ONCE:-false}" == "true" ]]; then
    exec /usr/local/bin/backup.sh
fi
if [[ "${AUDIT_RUN_ONCE:-false}" == "true" ]]; then
    exec /usr/local/bin/audit-export.sh
fi

BACKUP_SPEC="${BACKUP_CRON:-15 3 * * *}"
AUDIT_SPEC="${AUDIT_EXPORT_CRON:-5 * * * *}"

# busybox crond strips env on fork. Snapshot the vars we need into shell-safe
# wrappers so cron can re-source them. printf %q handles quoting for AWS secrets.
write_wrapper() {
    local out="$1"; shift
    local exec_path="$1"; shift
    {
        echo "#!/bin/bash"
        echo "set -euo pipefail"
        for var in TZ PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE \
                   BACKUP_S3_ENDPOINT BACKUP_S3_BUCKET BACKUP_S3_PREFIX \
                   BACKUP_S3_REGION BACKUP_RETENTION_DAYS AUDIT_S3_PREFIX \
                   AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY; do
            if [[ -n "${!var:-}" ]]; then
                printf 'export %s=%q\n' "$var" "${!var}"
            fi
        done
        echo "exec ${exec_path}"
    } > "${out}"
    chmod +x "${out}"
}

write_wrapper /usr/local/bin/run-backup.sh       /usr/local/bin/backup.sh
write_wrapper /usr/local/bin/run-audit-export.sh /usr/local/bin/audit-export.sh

mkdir -p /etc/crontabs
{
    echo "${BACKUP_SPEC} /usr/local/bin/run-backup.sh >> /proc/1/fd/1 2>&1"
    echo "${AUDIT_SPEC}  /usr/local/bin/run-audit-export.sh >> /proc/1/fd/1 2>&1"
} > /etc/crontabs/root

echo "[entrypoint] starting crond — backup: ${BACKUP_SPEC}, audit: ${AUDIT_SPEC} (TZ=${TZ:-UTC})"
exec crond -f -l 8
