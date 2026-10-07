#!/usr/bin/env bash
# Starts the local stack and waits until every container that has a healthcheck is healthy.
#
# Usage: deploy/scripts/up.sh [--with-observability] [--with-simulator] [--no-build]
#   --with-observability  also Prometheus, Grafana, Loki, Tempo, the OpenTelemetry Collector and Alloy,
#                         and turns on trace export in every service
#   --with-simulator      also seeds the demo data (through the Gateway) and starts the Sensor
#                         Simulator, so the system keeps generating data without intervention
#   --no-build            reuse the images already built
#
# Needs: docker (with compose), curl, jq, openssl. Never deletes volumes (see down.sh --purge).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCAL_DIR="$REPO_ROOT/deploy/local"
ENV_FILE="$LOCAL_DIR/.env"
TIMEOUT_SECONDS="${UP_TIMEOUT_SECONDS:-300}"

OBSERVABILITY=false
SIMULATOR=false
BUILD=true
for arg in "$@"; do
  case "$arg" in
    --with-observability) OBSERVABILITY=true ;;
    --with-simulator) SIMULATOR=true ;;
    --no-build) BUILD=false ;;
    -h|--help) sed -n '2,13p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "ERROR: unknown argument '$arg' (try --help)." >&2; exit 1 ;;
  esac
done

for tool in docker curl jq openssl; do
  command -v "$tool" >/dev/null || { echo "ERROR: '$tool' is required." >&2; exit 1; }
done
docker compose version >/dev/null 2>&1 || { echo "ERROR: 'docker compose' is not available." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "ERROR: the Docker daemon is not running." >&2; exit 1; }

# --- .env ---------------------------------------------------------------------------------------
if [[ ! -f "$ENV_FILE" ]]; then
  cp "$LOCAL_DIR/.env.example" "$ENV_FILE"
  echo "Created ${ENV_FILE#"$REPO_ROOT"/} from .env.example"
fi
# The only value Compose refuses to start without: the password of the demo users. A random one is
# generated for this machine; it is never printed, read it from the (git-ignored) file when needed.
# A DEMO_USERS_PASSWORD already in the environment is used as is and the file is left alone.
if [[ -z "${DEMO_USERS_PASSWORD:-}" ]] && ! grep -Eq '^DEMO_USERS_PASSWORD=.{8,}' "$ENV_FILE"; then
  sed -i.bak '/^DEMO_USERS_PASSWORD=/d' "$ENV_FILE" && rm -f "$ENV_FILE.bak"
  printf 'DEMO_USERS_PASSWORD=%s\n' "$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-20)" >> "$ENV_FILE"
  echo "Generated DEMO_USERS_PASSWORD in ${ENV_FILE#"$REPO_ROOT"/} (local demo users only)"
fi

# --- certificates and JWT keys -------------------------------------------------------------------
if [[ ! -f "$LOCAL_DIR/certs/ca/ca.crt" || ! -f "$LOCAL_DIR/jwt/jwt-private.pem" ]]; then
  echo "Generating development certificates and JWT keys..."
  "$REPO_ROOT/deploy/scripts/generate-dev-certs.sh"
fi

# --- compose ---------------------------------------------------------------------------------------
PROFILES=()
if [[ "$OBSERVABILITY" == true ]]; then
  PROFILES+=(--profile observability)
  export TRACING_EXPORT_ENABLED=true
fi
if [[ "$SIMULATOR" == true ]]; then PROFILES+=(--profile sim); fi

compose() { docker compose -f "$LOCAL_DIR/docker-compose.yml" --env-file "$ENV_FILE" ${PROFILES[@]+"${PROFILES[@]}"} "$@"; }

wait_healthy() {
  echo "Waiting for the containers to be healthy (timeout ${TIMEOUT_SECONDS}s)..."
  local elapsed=0 unhealthy
  while :; do
    unhealthy="$(compose ps --format '{{.Name}} {{.Health}}' | awk '$2 != "" && $2 != "healthy" {print $1}')"
    [[ -z "$unhealthy" ]] && break
    if (( elapsed >= TIMEOUT_SECONDS )); then
      echo "ERROR: not healthy after ${TIMEOUT_SECONDS}s:" >&2
      echo "$unhealthy" | sed 's/^/  - /' >&2
      exit 1
    fi
    sleep 5; elapsed=$((elapsed + 5))
  done
  echo "All containers with a healthcheck are healthy."
}

# The simulator needs the demo data and its scenario file, which exist only after the stack is up:
# start everything else first, seed through the Gateway, then start the simulator.
BUILD_FLAG=(); [[ "$BUILD" == true ]] && BUILD_FLAG=(--build)
if [[ "$SIMULATOR" == true ]]; then
  # A bind-mounted file that does not exist would be created as a directory.
  mkdir -p "$LOCAL_DIR/simulator"
  if [[ ! -f "$LOCAL_DIR/simulator/scenario.yml" ]]; then
    : > "$LOCAL_DIR/simulator/scenario.yml"
  fi
  compose up -d ${BUILD_FLAG[@]+"${BUILD_FLAG[@]}"} $(compose config --services | grep -vx 'sensor-simulator')
  wait_healthy
  echo "Seeding the demo data..."
  "$REPO_ROOT/deploy/scripts/seed-demo.sh"
  compose up -d ${BUILD_FLAG[@]+"${BUILD_FLAG[@]}"} sensor-simulator
else
  compose up -d ${BUILD_FLAG[@]+"${BUILD_FLAG[@]}"}
fi
wait_healthy

echo
echo "Stack is up."
echo "  Gateway (REST)  http://localhost:8080/api/v1"
echo "  Mailpit         http://localhost:8025"
echo "  RabbitMQ        http://localhost:15672"
if [[ "$OBSERVABILITY" == true ]]; then
  echo "  Grafana         http://localhost:3000   Prometheus http://localhost:9090"
fi
if [[ "$SIMULATOR" != true ]]; then
  echo "Next: deploy/scripts/seed-demo.sh (demo data), deploy/scripts/smoke-e2e.sh (end-to-end check)."
fi
