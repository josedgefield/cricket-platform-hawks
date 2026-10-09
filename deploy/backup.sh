#!/usr/bin/env bash
# Nightly encrypted Postgres backup. Run from cron on the server, e.g.:
#   15 3 * * * cd /opt/hawks && ./backup.sh >> backup.log 2>&1
# Needs: docker, age (https://github.com/FiloSottile/age), and the OCI CLI configured
# for upload. The private age key that can decrypt backups must NOT live on this server.
set -euo pipefail
cd "$(dirname "$0")"
set -a; source .env; set +a

stamp=$(date -u +%Y%m%dT%H%M%SZ)
file="backups/hawks-${stamp}.sql.gz.age"
mkdir -p backups

docker compose -f compose.prod.yaml exec -T postgres pg_dump -U hawks -d hawks --no-owner \
  | gzip -9 \
  | age -r "${BACKUP_AGE_RECIPIENT}" > "${file}"

# Refuse to report success for an empty or truncated dump.
if [ "$(stat -c %s "${file}")" -lt 1024 ]; then
  echo "backup ${file} is suspiciously small; check the database" >&2
  exit 1
fi

oci os object put --bucket-name "${BACKUP_BUCKET}" --file "${file}" --name "$(basename "${file}")" --no-multipart
# Keep two weeks locally; the bucket's lifecycle policy handles long-term retention.
find backups -name 'hawks-*.sql.gz.age' -mtime +14 -delete
echo "backup ok: ${file}"
