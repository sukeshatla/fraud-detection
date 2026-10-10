#!/usr/bin/env bash
# End-to-end smoke test of the full, load-balanced platform:
#   build → compose up (3×ingestion, 3×scoring, 2×alerts, NGINX) → push a fraud pattern through
#   the gateway → expect an alert in the queue → show how requests were spread across instances.
#
#   infra/smoke-test.sh            # build, start, test (leaves the stack running)
#   infra/smoke-test.sh --down     # …and tear it down afterwards
set -euo pipefail
cd "$(dirname "$0")/.."
COMPOSE="docker compose -f infra/docker-compose.yml --profile app"
GATEWAY="http://localhost:8080"

echo "▶ building jars"
./mvnw -q -B -DskipTests package

echo "▶ starting the platform (waits for healthchecks)"
$COMPOSE up -d --build --wait --wait-timeout 300

ACCOUNT="acc-smoke-$RANDOM$RANDOM"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
echo "▶ sending a velocity burst for $ACCOUNT through the gateway"
for i in $(seq 1 8); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/api/v1/transactions" \
    -H 'Content-Type: application/json' -H 'X-Client-Id: smoke' -H "Idempotency-Key: $ACCOUNT-$i" \
    -d "{\"transactionId\":\"$ACCOUNT-$i\",\"accountId\":\"$ACCOUNT\",\"amount\":25.00,\"currency\":\"USD\",
         \"merchantId\":\"m-1\",\"merchantCategoryCode\":\"5411\",\"country\":\"US\",\"channel\":\"CARD_NOT_PRESENT\",
         \"occurredAt\":\"$NOW\"}")
  [[ "$code" == "202" ]] || { echo "✗ POST $i returned $code"; exit 1; }
done

echo "▶ waiting for the alert to reach the analyst queue"
for attempt in $(seq 1 30); do
  if curl -s "$GATEWAY/api/v1/alerts/feed?status=OPEN&size=100" | grep -q "\"accountId\":\"$ACCOUNT\""; then
    echo "✓ alert raised for $ACCOUNT"
    break
  fi
  [[ $attempt == 30 ]] && { echo "✗ no alert after 30 s"; exit 1; }
  sleep 1
done

echo "▶ account history via the gateway (scoring-service)"
curl -s "$GATEWAY/api/v1/accounts/$ACCOUNT/transactions?limit=3" | head -c 300; echo

echo "▶ requests per ingestion instance (NGINX least_conn)"
docker logs fraud-gateway 2>/dev/null | grep 'POST /api/v1/transactions' | grep -o 'upstream=[0-9.:]*' | sort | uniq -c

if [[ "${1:-}" == "--down" ]]; then
  $COMPOSE down
fi
echo "✓ smoke test passed"
