# Feature 015 — Security

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 007 · 008 · 014 |

## 1. Goal
Every API call is authenticated with an OAuth2 access token issued by Keycloak and authorized by
role. Identities used for quotas and audit trails come from verified tokens, never from headers a
client can set. Personal data stays out of logs.

## 2. Roles
| Role | Who | May |
|------|-----|-----|
| `INGEST` | payment gateways (machines, client credentials) | `POST /api/v1/transactions` |
| `ANALYST` | fraud analysts (browser, authorization code + PKCE) | read alerts, account history and risk; start/release reviews; SSE stream |
| `SUPERVISOR` | senior analysts | everything ANALYST may, plus close alerts (CONFIRMED_FRAUD / FALSE_POSITIVE) and clear an account's risk flag |
| `OPS` | operators, automation | `/admin/**` (DLT replay), non-public actuator endpoints |

## 3. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-015-01 | **Given** a payment gateway with a client-credentials token carrying `INGEST`, **when** it posts a transaction, **then** 202; a tampered signature → 401; a user token without `INGEST` → 403. The rate-limit identity is the token's `azp` (client id); an `X-Client-Id` header is ignored. |
| AC-015-02 | Every service denies by default: no token → 401, wrong role → 403, per the role table above (asserted as a matrix test). The SSE stream is the only endpoint that accepts the token as `?access_token=` (EventSource can't send headers); anywhere else that parameter is ignored → 401. |
| AC-015-03 | An `ANALYST` may move an alert OPEN ↔ UNDER_REVIEW but closing it requires `SUPERVISOR` (403 otherwise). The audit actor is the token's user, never a client header. The dashboard signs in with authorization code + PKCE, sends the token on every call, renews it silently, and only offers the closing actions to supervisors. |
| AC-015-04 | PII: no card numbers anywhere; account ids in log statements are masked (`acc-****7788`). A test captures log output and asserts the raw id is absent. |
| AC-015-05 | Security headers (nosniff, frame-deny, no-cache on API responses), CORS restricted to the dashboard origin, stateless sessions, CSRF off (no cookies). Health and Prometheus endpoints are public for probes/scrapes; other actuator endpoints need `OPS`; the gateway doesn't expose actuator at all. |
| AC-015-06 | Keycloak runs in compose and in the kind deployment with an imported dev-only realm; smoke tests and Gatling obtain client-credentials tokens. Secrets come from env/secret stores; gitleaks scans history (dev-only values are allow-listed by name). |

## 4. Out of scope
mTLS between services, token exchange for service-to-service calls (services talk via Kafka),
fine-grained per-tenant data isolation, production Keycloak (HA, database, TLS).
