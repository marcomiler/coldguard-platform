#!/usr/bin/env bash
# Stops the local stack. Data is kept: volumes are only deleted with --purge, after an explicit
# confirmation (or with --yes, when the caller has already confirmed, e.g. reset-demo.sh).
#
# Usage: deploy/scripts/down.sh [--purge [--yes]]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCAL_DIR="$REPO_ROOT/deploy/local"
ENV_FILE="$LOCAL_DIR/.env"

PURGE=false
ASSUME_YES=false
for arg in "$@"; do
  case "$arg" in
    --purge) PURGE=true ;;
    --yes) ASSUME_YES=true ;;
    -h|--help) sed -n '2,7p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "ERROR: unknown argument '$arg' (try --help)." >&2; exit 1 ;;
  esac
done

command -v docker >/dev/null || { echo "ERROR: 'docker' is required." >&2; exit 1; }
# Every profile, so a stack started with --with-observability / --with-simulator stops completely.
# DEMO_USERS_PASSWORD only has to be non-empty for Compose to read the file.
export DEMO_USERS_PASSWORD="${DEMO_USERS_PASSWORD:-unused-by-down}"
ENV_ARGS=(); [[ -f "$ENV_FILE" ]] && ENV_ARGS=(--env-file "$ENV_FILE")
compose() {
  docker compose -f "$LOCAL_DIR/docker-compose.yml" -f "$LOCAL_DIR/docker-compose.mail-relay.yml" ${ENV_ARGS[@]+"${ENV_ARGS[@]}"} \
    --profile observability --profile sim "$@"
}

if [[ "$PURGE" == true ]]; then
  echo "This deletes the Compose volumes: PostgreSQL data, RabbitMQ messages, Grafana, Loki, Tempo."
  if [[ "$ASSUME_YES" != true ]]; then
    read -r -p "Type 'yes' to delete them: " answer
    if [[ "$answer" != "yes" ]]; then
      echo "Aborted; nothing was deleted."
      exit 1
    fi
  fi
  compose down --volumes --remove-orphans
  echo "Stack stopped and volumes deleted."
else
  compose down --remove-orphans
  echo "Stack stopped; volumes kept."
fi
