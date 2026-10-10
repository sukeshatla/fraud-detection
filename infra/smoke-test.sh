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

# OAuth2 client-credentials token from Keycloak, via the gateway (dev-only secrets, see infra/keycloak).
token() {
  curl -fsS -d grant_type=client_credentials -d "client_id=$1" -d "client_secret=$2" \
    "$GATEWAY/realms/fraud/protocol/openid-connect/token" | sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}
INGEST_TOKEN=$(token payment-gateway dev-only-not-a-secret-gateway)
READ_TOKEN=$(token ops-automation dev-only-not-a-secret-ops)
ACCOUNT="acc-smoke-$RANDOM$RANDOM"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
echo "▶ sending a velocity burst for $ACCOUNT through the gateway"
for i in $(seq 1 8); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/api/v1/transactions" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $INGEST_TOKEN" -H "Idempotency-Key: $ACCOUNT-$i" \
    -d "{\"transactionId\":\"$ACCOUNT-$i\",\"accountId\":\"$ACCOUNT\",\"amount\":25.00,\"currency\":\"USD\",
         \"merchantId\":\"m-1\",\"merchantCategoryCode\":\"5411\",\"country\":\"US\",\"channel\":\"CARD_NOT_PRESENT\",
         \"occurredAt\":\"$NOW\"}")
  [[ "$code" == "202" ]] || { echo "✗ POST $i returned $code"; exit 1; }
done

echo "▶ waiting for the alert to reach the analyst queue"
for attempt in $(seq 1 30); do
  if curl -s -H "Authorization: Bearer $READ_TOKEN" "$GATEWAY/api/v1/alerts/feed?status=OPEN&size=100" | grep -q "\"accountId\":\"$ACCOUNT\""; then
    echo "✓ alert raised for $ACCOUNT"
    break
  fi
  [[ $attempt == 30 ]] && { echo "✗ no alert after 30 s"; exit 1; }
  sleep 1
done

echo "▶ account history via the gateway (scoring-service)"
curl -s -H "Authorization: Bearer $READ_TOKEN" "$GATEWAY/api/v1/accounts/$ACCOUNT/transactions?limit=3" | head -c 300; echo

echo "▶ security: no token → 401, wrong role → 403"
[[ $(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/api/v1/alerts") == "401" ]] || { echo "✗ expected 401"; exit 1; }
[[ $(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $INGEST_TOKEN" "$GATEWAY/api/v1/alerts") == "403" ]] \
  || { echo "✗ expected 403"; exit 1; }

echo "▶ requests per ingestion instance (NGINX least_conn)"
docker logs fraud-gateway 2>/dev/null | grep 'POST /api/v1/transactions' | grep -o 'upstream=[0-9.:]*' | sort | uniq -c

echo "▶ observability: every replica scraped by Prometheus"
sleep 6
for job in ingestion-service scoring-service alert-service; do
  up=$(curl -s "http://localhost:9090/api/v1/query" --data-urlencode "query=sum(up{job=\"$job\"})" \
       | sed -nE 's/.*,"([0-9]+)"\]\}.*/\1/p')
  echo "  $job: ${up:-0} instance(s) up"
  [[ "${up:-0}" -ge 2 ]] || { echo "✗ expected ≥2 scraped instances of $job"; exit 1; }
done

echo "▶ observability: traces in Jaeger"
for attempt in $(seq 1 20); do
  services=$(curl -s http://localhost:16686/api/services)
  if echo "$services" | grep -q ingestion-service && echo "$services" | grep -q alert-service; then
    echo "  $services"
    break
  fi
  [[ $attempt == 20 ]] && { echo "✗ traces did not reach Jaeger: $services"; exit 1; }
  sleep 2
done
echo "  Grafana: http://localhost:3000 · Prometheus: http://localhost:9090 · Jaeger: http://localhost:16686"

if [[ "${1:-}" == "--down" ]]; then
  $COMPOSE down
fi
echo "✓ smoke test passed"
