# Feature 008 — Real-time analyst dashboard

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 007 |
| Concepts | Server-Sent Events, optimistic UI, component testing |

## 1. Problem / motivation
Analysts need to see new alerts the moment they are raised, triage them quickly, and act on them safely when colleagues are working the same queue.

## 2. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-008-01 | React 19 + TypeScript + Vite app in `dashboard/`. Data fetching uses TanStack Query. Strict TS, ESLint and Prettier are enforced in CI. |
| AC-008-02 | **Live feed:** subscribes to `GET /api/v1/alerts/stream` (SSE). New alerts and status changes appear within 1 s, without a page refresh. It reconnects automatically (browser `EventSource`) and refetches the queue on reconnect. Every alert-service instance pushes every change: changes fan out over Redis pub/sub, because each Kafka record is consumed by only one instance. |
| AC-008-03 | **Queue view:** filter by status/severity, sorting, keyset pagination ("Load more"). |
| AC-008-04 | **Alert detail:** transaction, score breakdown (rule hits + ML probability), and the account's last 20 transactions. |
| AC-008-05 | **Actions:** Start review / Confirm fraud / Mark false positive. A `409` response shows "Updated by someone else", then refetches and shows the latest state. |
| AC-008-06 | **KPIs:** open alerts by severity, alerts raised and resolved in the last hour (`GET /api/v1/alerts/stats`, one grouped SQL query), plus live alerts/min counted from the stream. |
| AC-008-07 | Component tests (Vitest + React Testing Library + MSW) cover the queue, the detail view, and the 409 handling. |
| AC-008-08 | Accessible: keyboard navigation through the queue, colour is never the only severity signal. |

## 3. Out of scope
Authentication UI (Feature 015 adds the OIDC login).
