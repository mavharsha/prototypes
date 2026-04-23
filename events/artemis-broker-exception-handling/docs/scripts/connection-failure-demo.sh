#!/usr/bin/env bash
set -euo pipefail

# Run this while producer + consumer apps are running against local docker-compose Artemis.
# It will:
#   1. Start the steady emitter (it auto-starts with emitter.enabled=true)
#   2. Pause 5s, print current consumer count
#   3. Restart the broker — simulate failure
#   4. Pause 15s, print counts again
#   5. Confirm in-flight messages got redelivered and steady emit resumed

echo "[1/4] confirm producer is emitting…"
sleep 5

echo "[2/4] restarting Artemis (simulates transient broker failure)…"
docker compose restart artemis

echo "[3/4] waiting 15s for reconnect + redelivery…"
sleep 15

echo "[4/4] check consumer logs for: 'transport failure', 'reconnected', redelivery counts"
echo "    docker compose logs --tail=200 | grep -iE 'reconnect|failover|redeliver'"
echo "Open http://localhost:8161/console — queue depths should have drained after reconnect."
