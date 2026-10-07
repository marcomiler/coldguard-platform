#!/usr/bin/env bash
# End-to-end check of the whole platform through the Gateway, role by role:
#   login -> out-of-range reading (CU-015) -> incident created -> acknowledged (Supervisor) ->
#   escalated (Supervisor) -> emails in Mailpit -> closed (Technician) -> audit trail (Auditor) ->
#   metrics (Supervisor).
# Exits with a non-zero code at the first step that fails. Needs the stack up and seeded
# (deploy/scripts/up.sh, deploy/scripts/seed-demo.sh), plus curl and jq. It uses one demo sensor and
# closes any open incident of its asset first, so it can be run repeatedly.
#
# Usage: deploy/scripts/smoke-e2e.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$REPO_ROOT/deploy/local/.env"
SEED="$REPO_ROOT/deploy/local/demo-seed.json"
API="${COLDGUARD_API_URL:-http://localhost:8080}/api/v1"
MAILPIT="${MAILPIT_URL:-http://localhost:8025}/api/v1"
WAIT_SECONDS="${SMOKE_WAIT_SECONDS:-60}"

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "ERROR: '$tool' is required." >&2; exit 1; }
done
[[ -f "$SEED" ]] || { echo "ERROR: $SEED not found; run deploy/scripts/seed-demo.sh first." >&2; exit 1; }

PASSWORD="${DEMO_USERS_PASSWORD:-}"
if [[ -z "$PASSWORD" && -f "$ENV_FILE" ]]; then
  PASSWORD="$(grep -E '^DEMO_USERS_PASSWORD=' "$ENV_FILE" | head -1 | cut -d= -f2-)"
fi
[[ -n "$PASSWORD" ]] || { echo "ERROR: DEMO_USERS_PASSWORD is not set (environment or $ENV_FILE)." >&2; exit 1; }

BODY="$(mktemp)"; trap 'rm -f "$BODY"' EXIT
STEP=0
pass() { STEP=$((STEP + 1)); printf '  [%02d] OK    %s\n' "$STEP" "$1"; }
fail() { printf '  [%02d] FAIL  %s\n' "$((STEP + 1))" "$1" >&2; [[ -s "$BODY" ]] && { echo "       response: $(head -c 400 "$BODY")" >&2; }; exit 1; }

# call TOKEN METHOD PATH [JSON]: body in $BODY, status in $STATUS.
call() {
  local token="$1" method="$2" path="$3" data="${4:-}" args
  args=(-s -o "$BODY" -w '%{http_code}' -X "$method" "$API$path" -H "Authorization: Bearer $token")
  [[ -n "$data" ]] && args+=(-H 'Content-Type: application/json' -d "$data")
  STATUS="$(curl "${args[@]}")" || { echo "ERROR: cannot reach $API (is the stack up?)." >&2; exit 1; }
}
expect() { [[ "$STATUS" == "$1" ]] || fail "$2 (expected HTTP $1, got $STATUS)"; }

login() {
  local status
  status="$(curl -s -o "$BODY" -w '%{http_code}' -X POST "$API/auth/login" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg u "$1" --arg p "$PASSWORD" '{username: $u, password: $p}')")" \
    || { echo "ERROR: cannot reach $API (is the stack up?)." >&2; exit 1; }
  [[ "$status" == 200 ]] || { STATUS="$status"; fail "login as $1 (HTTP $status)"; }
  jq -r .accessToken "$BODY"
}

mail_count() { # recipient
  curl -s "$MAILPIT/search?query=$(printf 'to:%s' "$1")" | jq -r '.messages_count // 0'
}

wait_for() { # description command...
  local what="$1"; shift; local waited=0
  until "$@"; do
    (( waited >= WAIT_SECONDS )) && fail "timed out after ${WAIT_SECONDS}s waiting for $what"
    sleep 2; waited=$((waited + 2))
  done
}

echo "Smoke test against $API"
SUPERVISOR="$(login supervisor)"; pass "login as Supervisor"
TECHNICIAN="$(login technician)"; pass "login as Technician"
AUDITOR="$(login auditor)";       pass "login as Auditor"
ADMIN="$(login admin)";           pass "login as Administrator"

SENSOR="$(jq -er '.sensors[] | select(.serialNumber == "SN-FRU-001") | .id' "$SEED")"
ASSET="$(jq -er '.sensors[] | select(.serialNumber == "SN-FRU-001") | .assetId' "$SEED")"

# --- a clean start: close what is open for this asset -------------------------------------------
call "$SUPERVISOR" GET "/incidents?assetId=$ASSET&status=CREATED&status=ACKNOWLEDGED&status=ESCALATED&size=100"
expect 200 "list open incidents of the asset"
for open in $(jq -r '.items[].id' "$BODY"); do
  call "$TECHNICIAN" POST "/incidents/$open/close" '{"cause":"Smoke test cleanup","resolutionComment":"Closed before a new run"}'
  expect 200 "close the leftover incident $open"
done
pass "no open incident left for the asset"

SUP_MAILS="$(mail_count supervisor@coldguard.local)"
TECH_MAILS="$(mail_count technician@coldguard.local)"

# --- reading out of range (CU-015) ---------------------------------------------------------------
call "$ADMIN" POST /telemetry/test-readings "$(jq -n --arg s "$SENSOR" --arg id "$(uuidgen | tr 'A-Z' 'a-z')" \
  '{readings: [{readingId: $id, sensorId: $s, value: 40.0, unit: "CELSIUS"}]}')"
expect 200 "inject the out-of-range reading"
[[ "$(jq -r '.results[0].outcome' "$BODY")" == ACCEPTED && "$(jq -r '.results[0].breached' "$BODY")" == true ]] \
  || fail "the reading was not accepted as an anomaly"
pass "out-of-range reading accepted as an anomaly"

incident_created() {
  call "$SUPERVISOR" GET "/incidents?assetId=$ASSET&status=CREATED"
  [[ "$STATUS" == 200 && "$(jq -r '.items | length' "$BODY")" -ge 1 ]]
}
wait_for "the incident to be created" incident_created
ID="$(jq -r '.items[0].id' "$BODY")"
pass "incident $ID created ($(jq -r '.items[0].priority' "$BODY"))"

# --- acknowledge / escalate (Supervisor), role checks ----------------------------------------------
call "$TECHNICIAN" POST "/incidents/$ID/acknowledgement"
expect 403 "a Technician must not acknowledge"
call "$SUPERVISOR" POST "/incidents/$ID/acknowledgement"
expect 200 "acknowledge"
[[ "$(jq -r .status "$BODY")" == ACKNOWLEDGED ]] || fail "status is not ACKNOWLEDGED"
call "$SUPERVISOR" POST "/incidents/$ID/acknowledgement"
expect 409 "a second acknowledgement"
pass "acknowledged by the Supervisor (second attempt refused, Technician forbidden)"

call "$SUPERVISOR" POST "/incidents/$ID/escalation" '{"reason":"Smoke test: no technician on site"}'
expect 200 "escalate"
[[ "$(jq -r .status "$BODY")" == ESCALATED ]] || fail "status is not ESCALATED"
pass "escalated by the Supervisor"

# --- emails ------------------------------------------------------------------------------------------
mails_arrived() {
  [[ "$(mail_count supervisor@coldguard.local)" -gt "$SUP_MAILS" && "$(mail_count technician@coldguard.local)" -gt "$TECH_MAILS" ]]
}
wait_for "the emails in Mailpit (Supervisor on creation, Technician on escalation)" mails_arrived
pass "emails delivered to Mailpit (Supervisor and Technician)"

# --- close (Technician) -----------------------------------------------------------------------------
call "$SUPERVISOR" POST "/incidents/$ID/close" '{"cause":"x","resolutionComment":"y"}'
expect 403 "a Supervisor must not close"
call "$TECHNICIAN" POST "/incidents/$ID/close" '{"cause":"Smoke test cause","resolutionComment":"Smoke test resolution"}'
expect 200 "close"
[[ "$(jq -r .status "$BODY")" == CLOSED && "$(jq -r .cause "$BODY")" == "Smoke test cause" ]] || fail "the closed incident lacks its evidence"
pass "closed by the Technician with cause and resolution comment"

# --- audit (Auditor) and metrics (Supervisor) ---------------------------------------------------------
FROM="$(date -u -d '-1 hour' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v-1H +%Y-%m-%dT%H:%M:%SZ)"
TO="$(date -u -d '+1 hour' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+1H +%Y-%m-%dT%H:%M:%SZ)"
call "$AUDITOR" GET "/audit-records?entityType=Incident&entityId=$ID&from=$FROM&to=$TO"
expect 200 "read the audit trail"
for action in CREATED ACKNOWLEDGED ESCALATED CLOSED; do
  jq -e --arg a "$action" '[.items[].action] | index($a)' "$BODY" >/dev/null || fail "the audit trail has no $action"
done
call "$SUPERVISOR" GET "/audit-records?from=$FROM&to=$TO"
expect 403 "a Supervisor must not read the audit trail"
pass "audit trail has CREATED, ACKNOWLEDGED, ESCALATED and CLOSED (and only the Auditor reads it)"

call "$SUPERVISOR" GET "/metrics/incidents?from=$FROM&to=$TO"
expect 200 "read the metrics"
jq -e '.countByStatus.CLOSED >= 1 and .mttaSeconds != null and .mttrSeconds != null' "$BODY" >/dev/null \
  || fail "the metrics do not reflect the closed incident"
pass "operational metrics reflect the closed incident (MTTA and MTTR present)"

echo "Smoke test passed ($STEP checks)."
