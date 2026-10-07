#!/usr/bin/env bash
# Seeds demonstration data through the Gateway REST API (never SQL), so it also exercises the real
# routes, security and validation. Idempotent: whatever already exists, by name or serial number,
# is reused. All values are academic placeholders (DEC-022), not confirmed business data.
#
# Usage: deploy/scripts/seed-demo.sh [--new-expiring]
#   --new-expiring  also create a fresh sensor whose calibration expires in 3 minutes, to show the
#                   expiry job moving it to maintenance (set ASSET_CALIBRATION_EXPIRY_CRON to
#                   '0 * * * * *' in deploy/local/.env so the job runs every minute).
#
# Needs: curl, jq, and the stack up (docker compose in deploy/local). The password of the demo
# users comes from DEMO_USERS_PASSWORD (environment, or deploy/local/.env). It is never printed.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$REPO_ROOT/deploy/local/.env"
OUTPUT_FILE="$REPO_ROOT/deploy/local/demo-seed.json"
API="${COLDGUARD_API_URL:-http://localhost:8080}/api/v1"
NEW_EXPIRING=false
[[ "${1:-}" == "--new-expiring" ]] && NEW_EXPIRING=true

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "ERROR: '$tool' is required." >&2; exit 1; }
done

PASSWORD="${DEMO_USERS_PASSWORD:-}"
if [[ -z "$PASSWORD" && -f "$ENV_FILE" ]]; then
  PASSWORD="$(grep -E '^DEMO_USERS_PASSWORD=' "$ENV_FILE" | head -1 | cut -d= -f2-)"
fi
if [[ -z "$PASSWORD" ]]; then
  echo "ERROR: DEMO_USERS_PASSWORD is not set (environment or $ENV_FILE)." >&2
  exit 1
fi

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

# call METHOD PATH [JSON]: leaves the response in $BODY_FILE and fails on anything but 2xx.
call() {
  local method="$1" path="$2" data="${3:-}" status
  local args=(-s -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$API$path")
  [[ -n "${TOKEN:-}" ]] && args+=(-H "Authorization: Bearer $TOKEN")
  [[ -n "$data" ]] && args+=(-H 'Content-Type: application/json' -d "$data")
  status="$(curl "${args[@]}")" || { echo "ERROR: cannot reach $API (is the stack up?)." >&2; exit 1; }
  if [[ "$status" != 2* ]]; then
    echo "ERROR: $method $path -> HTTP $status" >&2
    jq -r '"  \(.code // "-"): \(.detail // "no detail")"' "$BODY_FILE" >&2 2>/dev/null || cat "$BODY_FILE" >&2
    exit 1
  fi
}

iso_ago() { # seconds ago, as an ISO-8601 UTC instant (GNU or BSD date)
  local at=$(( $(date +%s) - $1 ))
  date -u -d "@$at" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -r "$at" +%Y-%m-%dT%H:%M:%SZ
}

# find_id PATH JQ_FILTER: walks every page of a listing and prints the id of the first match.
find_id() {
  local path="$1" filter="$2" page=0 sep='?' found total
  [[ "$path" == *\?* ]] && sep='&'
  while :; do
    call GET "$path${sep}page=$page&size=100"
    found="$(jq -r "[.items[] | select($filter)][0].id // empty" "$BODY_FILE")"
    if [[ -n "$found" ]]; then echo "$found"; return; fi
    total="$(jq -r '.page.totalPages' "$BODY_FILE")"
    page=$((page + 1))
    (( page >= total )) && return
  done
}

echo "Signing in as the demo administrator..."
TOKEN=""
call POST /auth/login "$(jq -n --arg u admin --arg p "$PASSWORD" '{username: $u, password: $p}')"
TOKEN="$(jq -r .accessToken "$BODY_FILE")"

CREATED=0
REUSED=0
note() { echo "  $1 $2"; }

ensure_organization() {
  local id; id="$(find_id /organizations ".name == \"$1\"")"
  if [[ -n "$id" ]]; then note "=" "organization $1"; REUSED=$((REUSED + 1)); else
    call POST /organizations "$(jq -n --arg n "$1" '{name: $n}')"; id="$(jq -r .id "$BODY_FILE")"
    note "+" "organization $1"; CREATED=$((CREATED + 1)); fi
  RESULT="$id"
}

ensure_site() { # organization_id name address
  local id; id="$(find_id "/organizations/$1/sites" ".name == \"$2\"")"
  if [[ -n "$id" ]]; then note "=" "site $2"; REUSED=$((REUSED + 1)); else
    call POST "/organizations/$1/sites" "$(jq -n --arg n "$2" --arg a "$3" '{name: $n, address: $a}')"
    id="$(jq -r .id "$BODY_FILE")"; note "+" "site $2"; CREATED=$((CREATED + 1)); fi
  RESULT="$id"
}

ensure_asset() { # site_id name description criticality
  local id; id="$(find_id "/assets?siteId=$1" ".name == \"$2\"")"
  if [[ -n "$id" ]]; then note "=" "asset $2"; REUSED=$((REUSED + 1)); else
    call POST /assets "$(jq -n --arg s "$1" --arg n "$2" --arg d "$3" --arg c "$4" \
      '{siteId: $s, name: $n, description: $d, criticality: $c}')"
    id="$(jq -r .id "$BODY_FILE")"; note "+" "asset $2 ($4)"; CREATED=$((CREATED + 1)); fi
  RESULT="$id"
}

# ensure_sensor ASSET_ID SERIAL MIN MAX CALIBRATION_AGE_S VALIDITY_S: sets SENSOR_ID and
# SENSOR_IS_NEW. VALIDITY_S empty means "use the service default".
ensure_sensor() {
  local asset="$1" serial="$2" min="$3" max="$4" age="$5" validity="$6" id
  id="$(find_id /sensors ".serialNumber == \"$serial\"")"
  SENSOR_IS_NEW=false
  if [[ -n "$id" ]]; then note "=" "sensor $serial"; REUSED=$((REUSED + 1)); SENSOR_ID="$id"; return; fi
  call POST /sensors "$(jq -n --arg a "$asset" --arg s "$serial" --argjson min "$min" --argjson max "$max" \
    --arg performed "$(iso_ago "$age")" --arg validity "$validity" '
    {assetId: $a, serialNumber: $s, model: "Sonda térmica demo", measurementUnit: "CELSIUS",
     initialCalibration: {kind: "CALIBRATION", performedAt: $performed,
                          reason: "Calibración inicial de demostración"},
     profile: ({minTemperature: $min, maxTemperature: $max, unit: "CELSIUS",
                magnitudeBands: {mediumFrom: 1, highFrom: 3, criticalFrom: 6},
                persistence: {minConsecutiveBreaches: 3, windowSeconds: 300},
                expectedReadingIntervalSeconds: 5}
               + (if $validity == "" then {} else {calibrationValiditySeconds: ($validity | tonumber)} end))}')"
  SENSOR_ID="$(jq -r .id "$BODY_FILE")"; SENSOR_IS_NEW=true
  note "+" "sensor $serial"; CREATED=$((CREATED + 1))
}

change_status() { # sensor_id status reason (only used right after creating the sensor)
  call POST "/sensors/$1/status" "$(jq -n --arg s "$2" --arg r "$3" '{targetStatus: $s, reason: $r}')"
  note "~" "  -> $2"
}

echo "Seeding..."
ensure_organization "ColdGuard Demo Logística"; ORG="$RESULT"
ensure_site "$ORG" "Centro de distribución Lima" "Av. Argentina 1234, Callao"; SITE="$RESULT"

ensure_asset "$SITE" "Cámara de vacunas" "Cadena de frío de vacunas, criticidad máxima" CRITICAL; A_VAC="$RESULT"
ensure_asset "$SITE" "Cámara de lácteos" "Productos lácteos refrigerados" HIGH; A_LAC="$RESULT"
ensure_asset "$SITE" "Congelador de pescado" "Pescado congelado" MEDIUM; A_PES="$RESULT"
ensure_asset "$SITE" "Almacén de frutas" "Frutas de rotación rápida" LOW; A_FRU="$RESULT"

ensure_sensor "$A_VAC" "SN-VAC-001" 2 8 3600 ""; S_VAC1="$SENSOR_ID"
ensure_sensor "$A_VAC" "SN-VAC-002" 2 8 3600 ""; S_VAC2="$SENSOR_ID"
ensure_sensor "$A_LAC" "SN-LAC-001" 2 8 3600 ""; S_LAC1="$SENSOR_ID"
ensure_sensor "$A_LAC" "SN-LAC-002" 2 8 3600 ""; S_LAC2="$SENSOR_ID"
[[ "$SENSOR_IS_NEW" == true ]] && change_status "$S_LAC2" IN_MAINTENANCE "Demo: sensor en revisión"
ensure_sensor "$A_PES" "SN-PES-001" -25 -15 3600 ""; S_PES1="$SENSOR_ID"
ensure_sensor "$A_FRU" "SN-FRU-001" 4 12 3600 ""; S_FRU1="$SENSOR_ID"
ensure_sensor "$A_FRU" "SN-FRU-002" 4 12 3600 ""; S_FRU2="$SENSOR_ID"
[[ "$SENSOR_IS_NEW" == true ]] && change_status "$S_FRU2" INACTIVE "Demo: fuera de temporada"

EXPIRING_SERIAL="SN-DEMO-EXPIRA"
[[ "$NEW_EXPIRING" == true ]] && EXPIRING_SERIAL="SN-DEMO-EXPIRA-$(date +%s)"
ensure_sensor "$A_VAC" "$EXPIRING_SERIAL" 2 8 30 180; S_EXP="$SENSOR_ID"
[[ "$SENSOR_IS_NEW" == true ]] && echo "     (its calibration expires in about 150 seconds)"

jq -n --arg org "$ORG" --arg site "$SITE" \
  --arg aVac "$A_VAC" --arg aLac "$A_LAC" --arg aPes "$A_PES" --arg aFru "$A_FRU" \
  --arg s1 "$S_VAC1" --arg s2 "$S_VAC2" --arg s3 "$S_LAC1" --arg s4 "$S_LAC2" \
  --arg s5 "$S_PES1" --arg s6 "$S_FRU1" --arg s7 "$S_FRU2" --arg s8 "$S_EXP" --arg es "$EXPIRING_SERIAL" '
  {organizationId: $org, siteId: $site,
   assets: [{name: "Cámara de vacunas", id: $aVac}, {name: "Cámara de lácteos", id: $aLac},
            {name: "Congelador de pescado", id: $aPes}, {name: "Almacén de frutas", id: $aFru}],
   sensors: [{serialNumber: "SN-VAC-001", id: $s1, assetId: $aVac},
             {serialNumber: "SN-VAC-002", id: $s2, assetId: $aVac},
             {serialNumber: "SN-LAC-001", id: $s3, assetId: $aLac},
             {serialNumber: "SN-LAC-002", id: $s4, assetId: $aLac},
             {serialNumber: "SN-PES-001", id: $s5, assetId: $aPes},
             {serialNumber: "SN-FRU-001", id: $s6, assetId: $aFru},
             {serialNumber: "SN-FRU-002", id: $s7, assetId: $aFru},
             {serialNumber: $es, id: $s8, assetId: $aVac}]}' > "$OUTPUT_FILE"

echo
echo "Done: $CREATED created, $REUSED reused."
echo "Ids written to ${OUTPUT_FILE#"$REPO_ROOT"/} (not versioned)."

# The Sensor Simulator reads its scenario from the ids just written.
"$REPO_ROOT/deploy/scripts/generate-simulator-scenario.sh"
