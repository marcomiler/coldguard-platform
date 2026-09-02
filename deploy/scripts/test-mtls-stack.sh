#!/usr/bin/env bash
# stack test: starts the real local Docker Compose stack with mTLS enabled between
# Gateway and Incident Service and confirms:
#   a) both services start successfully with TLS active;
#   b) a valid call travels the real (mTLS) gRPC channel end to end;
#   c) a client without a certificate is rejected by Incident Service's gRPC port.
#
# This is a runtime check against the real stack, not a substitute for the isolated,
# Docker-free tests in MutualTlsHandshakeTest — those exercise the mTLS
# handshake logic itself with fully in-memory, ephemeral certificates; this script exercises
# the actual configuration (application.yml + docker-compose.yml + generated certificates)
# wired together the way a developer would run it.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/deploy/local/docker-compose.yml"
ENV_FILE="$REPO_ROOT/deploy/local/.env"
ENV_EXAMPLE="$REPO_ROOT/deploy/local/.env.example"
CERTS_DIR="$REPO_ROOT/deploy/local/certs"

TIMEOUT_SECONDS="${MTLS_STACK_TEST_TIMEOUT_SECONDS:-120}"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  echo "Created $ENV_FILE from .env.example"
fi

if [[ ! -f "$CERTS_DIR/ca/ca.crt" ]]; then
  echo "Development certificates not found, generating them..."
  "$REPO_ROOT/deploy/scripts/generate-dev-certs.sh"
fi

echo "Starting postgres, rabbitmq, incident-service and gateway (with mTLS)..."
docker compose -f "$COMPOSE_FILE" up -d --build postgres rabbitmq incident-service gateway

wait_for() {
  local description="$1" check_command="$2"
  local elapsed=0
  echo "Waiting for: $description (timeout ${TIMEOUT_SECONDS}s)..."
  until eval "$check_command" >/dev/null 2>&1; do
    if (( elapsed >= TIMEOUT_SECONDS )); then
      echo "ERROR: timed out waiting for $description" >&2
      docker compose -f "$COMPOSE_FILE" logs incident-service gateway >&2 || true
      exit 1
    fi
    sleep 3
    elapsed=$((elapsed + 3))
  done
  echo "OK: $description"
}

# a) Both services report healthy/started with TLS active.
wait_for "Incident Service healthcheck (Actuator, confirms the process is up with the TLS gRPC listener started)" \
  "[[ \"\$(docker inspect -f '{{.State.Health.Status}}' coldguard-incident-service)\" == healthy ]]"
wait_for "Gateway Actuator health endpoint" \
  "curl -sf http://localhost:8080/actuator/health | grep -q '\"status\":\"UP\"'"

# b) A valid call travels the real mTLS channel: Gateway -> Incident Service over TLS.
# A unique asset/sensor id per run avoids colliding with an incident already created by a
# previous run (an asset can only have one open incident at a time).
echo "Sending a real CreateIncident request through the Gateway (exercises the mTLS channel end to end)..."
run_id="mtls-stack-test-$(date +%s)-$$"
create_response="$(curl -s -w '\n%{http_code}' -X POST http://localhost:8080/api/v1/incidents \
  -H 'Content-Type: application/json' \
  -d '{
        "assetId": "asset-'"$run_id"'",
        "assetCriticality": "CRITICALITY_HIGH",
        "sensorId": "sensor-'"$run_id"'",
        "anomalyType": "high-temperature",
        "magnitude": "MAGNITUDE_HIGH",
        "persistent": false,
        "correlationId": "'"$run_id"'"
      }')"
create_status="$(echo "$create_response" | tail -n1)"
if [[ "$create_status" != "201" ]]; then
  echo "ERROR: expected 201 from POST /api/v1/incidents, got $create_status" >&2
  echo "$create_response" >&2
  exit 1
fi
echo "OK: valid request succeeded over the real mTLS channel (HTTP $create_status)."

# c) A client without a certificate is rejected by Incident Service's gRPC port. The port is
# never exposed to the host (by design), so the check runs from a disposable container on the
# same Docker network Compose created, using openssl's client with no certificate presented.
# TLS 1.3 defers client-certificate enforcement until the connection is actually used, so an
# empty stdin (a bare handshake with no data exchanged) completes "successfully" even without a
# client certificate — one byte of input is sent to force that enforcement to trigger.
echo "Confirming a client without a certificate is rejected at the TLS handshake..."
network_name="$(docker compose -f "$COMPOSE_FILE" ps --format '{{.Networks}}' incident-service | head -n1)"
if printf 'x' | docker run --rm -i --network "$network_name" -v "$CERTS_DIR/ca/ca.crt:/ca.crt:ro" alpine/openssl s_client \
    -connect incident-service:9090 -CAfile /ca.crt -verify_return_error -quiet >/tmp/mtls-stack-test-no-cert.log 2>&1; then
  echo "ERROR: connection without a client certificate was NOT rejected." >&2
  cat /tmp/mtls-stack-test-no-cert.log >&2
  exit 1
fi
if ! grep -qiE "alert|handshake failure|certificate required|no certificate" /tmp/mtls-stack-test-no-cert.log; then
  echo "ERROR: connection failed, but not with a certificate-related TLS error — inspect the log:" >&2
  cat /tmp/mtls-stack-test-no-cert.log >&2
  exit 1
fi
rm -f /tmp/mtls-stack-test-no-cert.log
echo "OK: client without a certificate was rejected at the TLS handshake, as expected."

echo ""
echo "mTLS stack test PASSED."
echo "Services are still running. Stop them with:"
echo "  docker compose -f deploy/local/docker-compose.yml stop postgres rabbitmq incident-service gateway"
echo "(Do not run 'docker compose ... down -v' — that deletes local data volumes.)"
