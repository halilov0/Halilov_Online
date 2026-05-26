#!/bin/bash
# Hourly snapshot of audit_log → S3-compatible bucket. Each run uploads
# the full audit_log table as a timestamped CSV under AUDIT_S3_PREFIX.
# Intent: forensics survive a Postgres compromise. If an attacker wipes
# audit_log to hide tracks, every prior hour's snapshot is already off-VM.
#
# Hardening note: the main backup credentials are reused here for
# convenience. A stronger setup is a dedicated R2 token scoped write-only
# to AUDIT_S3_PREFIX, so compromise of the primary backup creds can't also
# delete the audit trail. Provision in the R2 dashboard and point a future
# AUDIT_AWS_* env var pair at it.
#
# No retention pruning — audit snapshots are tiny (KBs) and meant to be
# kept indefinitely.
set -euo pipefail

: "${PGHOST:?PGHOST required}"
: "${PGUSER:?PGUSER required}"
: "${PGPASSWORD:?PGPASSWORD required}"
: "${PGDATABASE:?PGDATABASE required}"
: "${BACKUP_S3_ENDPOINT:?BACKUP_S3_ENDPOINT required}"
: "${BACKUP_S3_BUCKET:?BACKUP_S3_BUCKET required}"
: "${AUDIT_S3_PREFIX:?AUDIT_S3_PREFIX required}"
: "${AWS_ACCESS_KEY_ID:?AWS_ACCESS_KEY_ID required}"
: "${AWS_SECRET_ACCESS_KEY:?AWS_SECRET_ACCESS_KEY required}"

export PGPORT="${PGPORT:-5432}"
export AWS_DEFAULT_REGION="${BACKUP_S3_REGION:-auto}"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DATE_PATH="$(date -u +%Y/%m/%d)"
FILE="audit-${STAMP}.csv.gz"
TMP="/tmp/${FILE}"
KEY="${AUDIT_S3_PREFIX}/${DATE_PATH}/${FILE}"

echo "[audit-export $(date -Iseconds)] dumping audit_log"
psql -c "COPY (SELECT * FROM audit_log ORDER BY id) TO STDOUT WITH (FORMAT csv, HEADER true)" \
    | gzip -9 > "${TMP}"

SIZE=$(stat -c%s "${TMP}" 2>/dev/null || stat -f%z "${TMP}")
echo "[audit-export] size ${SIZE} bytes — uploading to s3://${BACKUP_S3_BUCKET}/${KEY}"
aws s3 cp "${TMP}" "s3://${BACKUP_S3_BUCKET}/${KEY}" \
    --endpoint-url "${BACKUP_S3_ENDPOINT}" \
    --only-show-errors

rm -f "${TMP}"
echo "[audit-export $(date -Iseconds)] done"
