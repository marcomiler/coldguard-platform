#!/usr/bin/env bash
# Dumps each logical schema of the local PostgreSQL to its own file under deploy/local/backups/
# (git-ignored). Read-only on the database; the stack must be running.
#
# Usage: deploy/scripts/backup-db.sh [output-dir]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$REPO_ROOT/deploy/local/.env"
OUT_DIR="${1:-$REPO_ROOT/deploy/local/backups/$(date -u +%Y%m%dT%H%M%SZ)}"

env_value() { [[ -f "$ENV_FILE" ]] && grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2- || true; }
DB_USER="${POSTGRES_USER:-$(env_value POSTGRES_USER)}"; DB_USER="${DB_USER:-coldguard}"
DB_NAME="${POSTGRES_DB:-$(env_value POSTGRES_DB)}"; DB_NAME="${DB_NAME:-coldguard}"

docker exec coldguard-postgres pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 \
  || { echo "ERROR: PostgreSQL is not running (container 'coldguard-postgres')." >&2; exit 1; }

mkdir -p "$OUT_DIR"
for schema in asset telemetry incident identity auditlog notification; do
  docker exec coldguard-postgres pg_dump -U "$DB_USER" -d "$DB_NAME" --schema="$schema" --no-owner \
    > "$OUT_DIR/$schema.sql"
  echo "  $schema -> ${OUT_DIR#"$REPO_ROOT"/}/$schema.sql"
done
echo "Backup written to ${OUT_DIR#"$REPO_ROOT"/} (not versioned)."
