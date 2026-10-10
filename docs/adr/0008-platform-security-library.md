# ADR-0008: OAuth2 resource servers with Keycloak and a shared `platform-security` library

- **Status:** Accepted
- **Date:** 2026-10-10

## Context
Feature 015 adds authentication and authorization to three services that are called by machines
(payment gateways) and by people (analysts in a browser). Before it, the rate-limit identity and
the audit actor were plain headers (`X-Client-Id`, `X-Actor`) that any client could set.

## Decision
- **Keycloak** issues OAuth2/OIDC tokens. Machines use the **client-credentials** grant; the
  dashboard uses **authorization code + PKCE** (public client, no secret in the browser).
- Each service is a stateless **resource server**: it validates JWTs locally against cached JWKS
  keys (no call to Keycloak per request) and authorizes by realm role with a deny-by-default
  `SecurityFilterChain` in its `infrastructure/security` package.
- Identities come from verified claims only: the quota key is `azp`, the audit actor is
  `preferred_username`.
- Shared, infrastructure-only helpers live in **`platform-security`**: the Keycloak role
  converter, the stream token resolver and PII masking. As with ADR-0007 there's no
  auto-configuration; each service wires its own filter chain, so the rules for a service are
  readable in that service.

## Consequences
- ✅ Forged identities are impossible; per-client quotas and audit trails become trustworthy.
- ✅ No network hop per request: tokens are verified with cached public keys.
- ❌ Revocation isn't instant: a stolen token works until it expires (5 minutes here). Short
  lifetimes and refresh tokens are the mitigation; introspection would add a hop per request.
- ❌ Every environment needs an IdP. Local, CI and kind runs import the same dev-only realm.

## Alternatives considered
- **API keys** for gateways: simple, but long-lived shared secrets with no standard rotation or
  scopes, and a second mechanism next to OIDC for people.
- **Session cookies** for the dashboard: needs CSRF protection and sticky or shared sessions
  behind a load balancer; bearer tokens keep every service stateless.
- **Validation at the gateway only**: one place to configure, but services would trust whatever
  reaches them (no defense in depth), and roles would still be needed inside services.
