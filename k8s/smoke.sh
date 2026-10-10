#!/usr/bin/env bash
# Post-deploy smoke test for any environment: push a velocity burst through the gateway and
# require an alert in the analyst queue. Exit code drives automatic rollback in the pipeline.
#   k8s/smoke.sh http://localhost:8080
set -euo pipefail
GATEWAY=${1:-http://localhost:8080}
# OAuth2 client-credentials token from Keycloak, via the gateway (dev-only secrets, see infra/keycloak).
token() {
  curl -fsS -d grant_type=client_credentials -d "client_id=$1" -d "client_secret=$2" \
    "$GATEWAY/realms/fraud/protocol/openid-connect/token" | sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}
INGEST_TOKEN=$(token payment-gateway dev-only-not-a-secret-gateway)
READ_TOKEN=$(token ops-automation dev-only-not-a-secret-ops)
ACCOUNT="acc-deploy-$RANDOM$RANDOM"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)

for i in $(seq 1 8); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/api/v1/transactions" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $INGEST_TOKEN" -H "Idempotency-Key: $ACCOUNT-$i" \
    -d "{\"transactionId\":\"$ACCOUNT-$i\",\"accountId\":\"$ACCOUNT\",\"amount\":25.00,\"currency\":\"USD\",
         \"merchantId\":\"m-1\",\"merchantCategoryCode\":\"5411\",\"country\":\"US\",\"channel\":\"CARD_NOT_PRESENT\",
         \"occurredAt\":\"$NOW\"}")
  [[ "$code" == "202" ]] || { echo "✗ POST $i → $code"; exit 1; }
done
for attempt in $(seq 1 60); do
  if curl -s -H "Authorization: Bearer $READ_TOKEN" "$GATEWAY/api/v1/alerts/feed?status=OPEN&size=100" | grep -q "\"accountId\":\"$ACCOUNT\""; then
    echo "✓ deploy smoke test passed: alert raised for $ACCOUNT"
    exit 0
  fi
  sleep 2
done
echo "✗ no alert for $ACCOUNT within 120 s"
exit 1
