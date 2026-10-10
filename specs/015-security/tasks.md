# Tasks — Feature 015 Security

- [x] T1 — `platform-security`: Keycloak role converter, principal/client-id rules, `StreamTokenResolver`, `Pii` (ADR-0008)
- [x] T2 — ingestion: resource server, INGEST rule, rate-limit key from `azp`, security headers/CORS (AC-015-01, 05)
- [x] T3 — scoring: ANALYST reads, SUPERVISOR clears risk, OPS admin; role-matrix test (AC-015-02)
- [x] T4 — alerts: ANALYST/SUPERVISOR, terminal-status check, actor from token, SSE query token (AC-015-02, 03)
- [x] T5 — `TestJwts` + real-token ITs in all services
- [x] T6 — PII masking at log sites + log-capture test (AC-015-04)
- [x] T7 — Keycloak in compose and k8s (realm import), gateway routes `/realms`, query string not logged (AC-015-06)
- [x] T8 — smoke tests and Gatling use client-credentials tokens (token cache with refresh)
- [x] T9 — dashboard: OIDC code + PKCE, bearer on calls, SSE token + reconnect, role-aware actions (AC-015-03)
- [x] T10 — ADR-0008, concept 16, README
