# Plan — Feature 015 Security

## 1. Token flow
```mermaid
sequenceDiagram
    autonumber
    participant GW as Payment gateway
    participant B as Analyst browser
    participant N as NGINX :8080
    participant K as Keycloak
    participant I as ingestion-service
    participant A as alert-service

    GW->>N: POST /realms/fraud/.../token (client_credentials)
    N->>K: proxy
    K-->>GW: JWT {azp: payment-gateway, realm_access.roles: [INGEST], iss: http://localhost:8080/realms/fraud}
    GW->>N: POST /api/v1/transactions  Authorization: Bearer …
    N->>I: proxy
    I->>K: GET /certs (internal, cached; on unknown kid only)
    I-->>GW: 202 (quota key = azp)

    B->>N: GET /  → no session → redirect to /realms/fraud/.../auth (PKCE S256)
    B->>N: login → code → POST /token (code + verifier)
    B->>N: GET /api/v1/alerts/feed  Authorization: Bearer …
    N->>A: proxy → 200
    B->>N: GET /api/v1/alerts/stream?access_token=… (EventSource)
    N->>A: proxy (query string not logged)
```

One public issuer (`http://localhost:8080/realms/fraud`, the gateway) for browsers **and**
machines: Keycloak runs with a fixed `KC_HOSTNAME`, so the `iss` claim doesn't depend on which
network path fetched the token. Services fetch signing keys over the internal network
(`OIDC_JWKS_URI=http://keycloak:8080/...`) and validate `iss`, `exp`, `nbf` and the signature.

## 2. Where checks live
| Concern | Where | Why there |
|---------|-------|-----------|
| Authentication, URL × method × role | each service's `infrastructure/security/SecurityConfig` | one deny-by-default table per service, readable in one screen |
| "Closing needs SUPERVISOR" | `AlertController` | depends on the request **body** (target status), which URL rules can't see |
| Role mapping, principal name, client id | `platform-security` (ADR-0008) | identical in three services; small and infrastructure-only |
| Audit actor | `Authentication.getName()` → `preferred_username` | can't be forged by a header |
| Quota identity | `azp` claim | the authenticated client, not a self-declared header |
| PII masking | `Pii.maskAccount` at log call sites | logs are shipped to third-party systems |

Method security (`@PreAuthorize`) was considered: use cases live in `application`, which ArchUnit
keeps free of Spring, so annotations would have to go on controllers anyway — URL rules + one
explicit body check are simpler to review.

## 3. Tests
| Level | What |
|-------|------|
| Unit (`platform-security`) | role mapping, principal for users vs service accounts, query-token only on listed paths, masking |
| Slice (`@WebMvcTest` + real `SecurityConfig` + `jwt()`) | role matrices, 401/403, actor from token, rate-limit key from `azp`, security headers |
| Integration (Testcontainers) | real HS256-signed tokens through the full filter chain (`TestJwts`), tampered token → 401, SSE with `?access_token=` |
| Dashboard (Vitest) | token on every call and after renewal, SSE reconnect with a fresh token, role-aware actions |
| End-to-end | compose smoke test and kind deploy smoke with real Keycloak tokens; 401/403 at the edge |
