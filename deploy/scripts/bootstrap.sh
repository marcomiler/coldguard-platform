#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/deploy/local/docker-compose.yml"
ENV_FILE="$REPO_ROOT/deploy/local/.env"
ENV_EXAMPLE="$REPO_ROOT/deploy/local/.env.example"

HEALTHCHECK_TIMEOUT_SECONDS="${HEALTHCHECK_TIMEOUT_SECONDS:-60}"
POSTGRES_USER="${POSTGRES_USER:-coldguard}"
POSTGRES_DB="${POSTGRES_DB:-coldguard}"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  echo "Created $ENV_FILE from .env.example"
else
  echo "$ENV_FILE already exists, leaving it untouched"
fi

echo "Starting PostgreSQL container..."
docker compose -f "$COMPOSE_FILE" up -d postgres

echo "Waiting for PostgreSQL healthcheck (timeout: ${HEALTHCHECK_TIMEOUT_SECONDS}s)..."
elapsed=0
until docker exec coldguard-postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; do
  if (( elapsed >= HEALTHCHECK_TIMEOUT_SECONDS )); then
    echo "ERROR: PostgreSQL did not become healthy within ${HEALTHCHECK_TIMEOUT_SECONDS}s." >&2
    exit 1
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done

echo "PostgreSQL is ready."
echo ""
echo "To stop it manually when done:"
echo "  docker compose -f deploy/local/docker-compose.yml stop postgres"
echo "(Volumes are preserved. Do not run 'docker compose ... down -v' unless you intend to delete all local data.)"
