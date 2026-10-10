#!/usr/bin/env bash
# AC-011-02 + AC-013-06: kill an ingestion replica in the middle of steady load, twice:
#   A) as configured: NGINX retries a failed request on the next replica (proxy_next_upstream … non_idempotent)
#   B) with retries switched OFF at runtime (nginx -s reload), the "before" state
# and report the client-visible error rate of each. Requires the stack from infra/smoke-test.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
RATE=${RATE:-100}
DURATION=${DURATION:-60}
GATEWAY=fraud-gateway
RETRY_ON='proxy_next_upstream error timeout http_502 http_503 non_idempotent;'
RETRY_OFF='proxy_next_upstream off;'

latest_stats() { ls -td load-tests/target/gatling/failoversimulation-*/ | head -1; }

error_rate() {
  python3 - "$(latest_stats)js/stats.json" <<'PY'
import json, sys
s = json.load(open(sys.argv[1]))["stats"]
total, ko = s["numberOfRequests"]["total"], s["numberOfRequests"]["ko"]
print(f"{ko}/{total} failed = {100.0 * ko / max(total, 1):.2f}%  (p99 {s['percentiles4']['total']} ms)")
PY
}

run_with_kill() {
  local label=$1
  ./mvnw -q -B -pl load-tests gatling:test -Dgatling.simulationClass=com.fraudplatform.load.FailoverSimulation \
    -Drate="$RATE" -DdurationSeconds="$DURATION" -DmaxErrorPct=100 >/dev/null 2>&1 &
  local gatling=$!
  sleep $((DURATION / 3))
  local victim
  victim=$(docker ps --filter "name=fraud-platform-ingestion-service" --format '{{.Names}}' | head -1)
  echo "  killing $victim at t≈$((DURATION / 3))s"
  docker kill "$victim" >/dev/null
  wait $gatling || true
  docker start "$victim" >/dev/null
  printf "  %-34s %s\n" "$label" "$(error_rate)"
  sleep 15   # let the replica rejoin before the next run
}

echo "▶ A: retry on next upstream (as configured)"
run_with_kill "retry ON"

echo "▶ B: retries disabled"
docker exec $GATEWAY sh -c "sed -i 's/$RETRY_ON/$RETRY_OFF/' /etc/nginx/nginx.conf && nginx -s reload"
trap 'docker exec $GATEWAY sh -c "sed -i \"s/$RETRY_OFF/$RETRY_ON/\" /etc/nginx/nginx.conf && nginx -s reload"' EXIT
sleep 2
run_with_kill "retry OFF"
