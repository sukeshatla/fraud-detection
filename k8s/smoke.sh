#!/usr/bin/env bash
# Post-deploy smoke test for any environment: push a velocity burst through the gateway and
# require an alert in the analyst queue. Exit code drives automatic rollback in the pipeline.
#   k8s/smoke.sh http://localhost:8080
set -euo pipefail
GATEWAY=${1:-http://localhost:8080}
ACCOUNT="acc-deploy-$RANDOM$RANDOM"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)

for i in $(seq 1 8); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/api/v1/transactions" \
    -H 'Content-Type: application/json' -H 'X-Client-Id: deploy-smoke' -H "Idempotency-Key: $ACCOUNT-$i" \
    -d "{\"transactionId\":\"$ACCOUNT-$i\",\"accountId\":\"$ACCOUNT\",\"amount\":25.00,\"currency\":\"USD\",
         \"merchantId\":\"m-1\",\"merchantCategoryCode\":\"5411\",\"country\":\"US\",\"channel\":\"CARD_NOT_PRESENT\",
         \"occurredAt\":\"$NOW\"}")
  [[ "$code" == "202" ]] || { echo "✗ POST $i → $code"; exit 1; }
done
for attempt in $(seq 1 60); do
  if curl -s "$GATEWAY/api/v1/alerts/feed?status=OPEN&size=100" | grep -q "\"accountId\":\"$ACCOUNT\""; then
    echo "✓ deploy smoke test passed: alert raised for $ACCOUNT"
    exit 0
  fi
  sleep 2
done
echo "✗ no alert for $ACCOUNT within 120 s"
exit 1
