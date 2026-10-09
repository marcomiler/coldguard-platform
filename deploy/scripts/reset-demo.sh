#!/usr/bin/env bash
# Resets the local stack to a clean, seeded state for a demo rehearsal: deletes ALL Compose volumes
# (PostgreSQL, RabbitMQ, Grafana...), starts the stack again and seeds it. Does not start the
# simulator: do that when the demo begins with deploy/scripts/start-demo-sim.sh.
#
# Usage: deploy/scripts/reset-demo.sh [up.sh options, e.g. --with-observability]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPTS="$REPO_ROOT/deploy/scripts"

for arg in "$@"; do
  if [[ "$arg" == "--with-simulator" ]]; then
    echo "ERROR: --with-simulator is not allowed here; use start-demo-sim.sh." >&2
    exit 1
  fi
done

echo "This deletes the local Compose volumes (all local data)."
read -r -p "Type 'yes' to continue: " answer
[[ "$answer" == "yes" ]] || { echo "Aborted; nothing was deleted."; exit 1; }

"$SCRIPTS/down.sh" --purge --yes
"$SCRIPTS/up.sh" "$@"
"$SCRIPTS/seed-demo.sh"
echo
echo "Clean and seeded. When the demo starts: deploy/scripts/start-demo-sim.sh"
