#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-}"
POSTGRES_USER="${POSTGRES_USER:-coldguard}"
POSTGRES_DB="${POSTGRES_DB:-coldguard}"

usage() {
  echo "Usage: $0 {unit|integration|all}" >&2
  exit 1
}

check_postgres() {
  if ! docker exec coldguard-postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; then
    echo "ERROR: PostgreSQL is not reachable (container 'coldguard-postgres' not running or not ready)." >&2
    echo "Start it first with: deploy/scripts/bootstrap.sh" >&2
    exit 1
  fi
}

run_unit() {
  echo "Running unit tests (mvn -pl apps/incident-service -am test)..."
  mvn -pl apps/incident-service -am test
}

run_integration() {
  check_postgres
  echo "Running integration tests (mvn -pl apps/incident-service -am test -DexcludedGroups=)..."
  mvn -pl apps/incident-service -am test -DexcludedGroups=
}

case "$MODE" in
  unit) run_unit ;;
  integration) run_integration ;;
  all) run_unit; run_integration ;;
  *) usage ;;
esac
