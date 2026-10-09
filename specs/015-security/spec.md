# Feature 015 — Security

| Field    | Value |
|----------|-------|
| Status   | Spec |
| Depends  | 007 |

## 1. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-015-01 | Ingestion: machine-to-machine OAuth2 **client-credentials** JWT. `X-Client-Id` for rate limiting is derived from the token `sub`/`azp`, not trusted from a header. |
| AC-015-02 | Alert API: analyst JWT with roles `ANALYST` (read, review) and `SUPERVISOR` (confirm/close). Method security on the use cases. |
| AC-015-03 | Keycloak runs in compose for local development. Tests use mocked JWTs (`jwt()` post-processor). |
| AC-015-04 | PII: no PAN anywhere. Account IDs are masked in logs (`acc-****1001`). A log-scanning test asserts this. |
| AC-015-05 | Security headers, CORS restricted to the dashboard origin, actuator endpoints are not exposed publicly. |
| AC-015-06 | Secrets come only from env/secret stores. A `gitleaks` CI step catches leaks. |
