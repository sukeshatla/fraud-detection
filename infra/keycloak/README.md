# Keycloak realm `fraud` (local / CI only)

Imported on start by `docker-compose.yml` and the Kubernetes manifests (`k8s/base/keycloak/` holds an
identical copy; CI fails if they drift). **Every secret and password here is a throw-away dev value.**

| Client | Grant | Roles | Used by |
|---|---|---|---|
| `payment-gateway` | client credentials | INGEST | smoke tests, `curl` examples |
| `gw-premium`, `gw-standard` | client credentials | INGEST | Gatling (premium / default rate-limit tier) |
| `ops-automation` | client credentials | ANALYST, OPS | smoke tests reading the alert queue, DLT replay |
| `fraud-dashboard` | authorization code + PKCE (public) | — | the React dashboard |

| User / password | Roles |
|---|---|
| `analyst` / `analyst` | ANALYST |
| `supervisor` / `supervisor` | ANALYST, SUPERVISOR |
| `ops` / `ops` | OPS |

Tokens: `iss` is always `http://localhost:8080/realms/fraud` (the gateway) because `KC_HOSTNAME` is
fixed, while services fetch signing keys over the internal network (`OIDC_JWKS_URI`).

```bash
TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=payment-gateway \
  -d client_secret=dev-only-not-a-secret-gateway \
  http://localhost:8080/realms/fraud/protocol/openid-connect/token | jq -r .access_token)
```
