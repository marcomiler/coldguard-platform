#!/usr/bin/env bash
# Starts the Sensor Simulator with the demo timeline, on demand. The scenario clock starts when the
# container starts, so run this when the demo begins (stack already up and seeded). Running it again
# restarts the timeline from zero; incidents already opened stay open (use reset-demo.sh to clean).
#
# Usage: deploy/scripts/start-demo-sim.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCAL_DIR="$REPO_ROOT/deploy/local"
ENV_FILE="$LOCAL_DIR/.env"

command -v docker >/dev/null || { echo "ERROR: 'docker' is required." >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || { echo "ERROR: $ENV_FILE not found; run deploy/scripts/up.sh first." >&2; exit 1; }

"$REPO_ROOT/deploy/scripts/generate-simulator-scenario.sh" --demo

# --no-deps: only the simulator is recreated, never the services it depends on.
docker compose -f "$LOCAL_DIR/docker-compose.yml" --env-file "$ENV_FILE" --profile sim \
  up -d --no-deps --force-recreate sensor-simulator

echo "Waiting for the simulator to be healthy..."
state=""
for _ in $(seq 1 30); do
  state="$(docker inspect -f '{{.State.Health.Status}}' coldguard-sensor-simulator 2>/dev/null || true)"
  [[ "$state" == "healthy" ]] && break
  sleep 2
done
[[ "$state" == "healthy" ]] || { echo "ERROR: simulator is not healthy (docker logs coldguard-sensor-simulator)." >&2; exit 1; }

echo "Simulator running. Timeline (approximate, since the simulator started):"
echo "  0:00  all quiet (SN-VAC-001 nominal)"
echo "  0:15  SN-LAC-001: two out-of-range readings, recovers   -> incident P2 (not persistent)"
echo "  0:40  SN-VAC-002: 15 C in the vaccine chamber            -> incident ~0:55 (critical impact)"
echo "  1:40  SN-PES-001: -10 C in the fish freezer              -> incident ~1:55"
echo "  2:30  SN-FRU-001: goes silent                            -> connectivity lost ~2:40..3:10"
echo "  4:10  SN-FRU-001: resumes"
