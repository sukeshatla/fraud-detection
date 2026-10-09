# Fraud Alert Console (dashboard)

React 19 + TypeScript (strict) + Vite + TanStack Query. It shows the live analyst queue, alert details with the score breakdown, and review actions with optimistic concurrency.

```bash
npm install
npm run dev        # http://localhost:5173: proxies /api/v1/alerts → :8083, /api/v1/accounts → :8082
npm test           # Vitest + Testing Library + MSW
npm run lint && npm run typecheck && npm run build
```

| Feature        | How                                                                                                                                                                  |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Live queue     | `EventSource('/api/v1/alerts/stream')`. Each change is merged into the TanStack Query cache (`mergeChange`), with no refetch. The queue refetches after a reconnect. |
| Pagination     | `useInfiniteQuery` follows the server's opaque keyset cursor ("Load more")                                                                                           |
| Review actions | PATCH carries the `version` on screen. The three different 409s get three different messages, and a stale version immediately shows the server's current state.      |
| Accessibility  | Severity is shown as text + shape (never colour alone), the queue is a keyboard-navigable listbox (↑/↓), and controls are labelled                                   |
| Testing        | MSW mocks the network at the `fetch` level, so components are tested unmodified. A fake `EventSource` drives the SSE tests.                                          |
