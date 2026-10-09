# Tasks — Feature 008 Real-time analyst dashboard

- [x] T1 — `AlertChangeBus` port; use cases publish CREATED / UPDATED
- [x] T2 — `RedisAlertChangeBus` (pub/sub fan-out) + IT
- [x] T3 — `GET /alerts/stream` (SseEmitter, heartbeats, cleanup) + IT
- [x] T4 — `GET /alerts/stats` + IT
- [x] T5 — Dashboard scaffold: Vite, TS strict, ESLint, Prettier, Vitest, MSW
- [x] T6 — API client + hooks (TanStack Query, infinite keyset query, SSE merge)
- [x] T7 — Components: KpiBar, AlertQueue (filters, load more, keyboard), AlertDetail (actions, 409 handling)
- [x] T8 — Component tests
- [x] T9 — CI job for the dashboard; docs
