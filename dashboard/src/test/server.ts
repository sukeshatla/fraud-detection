import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { stats } from './fixtures';

/** Default handlers: an empty but healthy backend. Tests override per scenario with server.use(...). */
export const server = setupServer(
  http.get('*/api/v1/alerts/feed', () => HttpResponse.json({ items: [], nextCursor: null })),
  http.get('*/api/v1/alerts/stats', () => HttpResponse.json(stats)),
  http.get('*/api/v1/accounts/:id/transactions', () => HttpResponse.json([])),
  http.get('*/api/v1/accounts/:id/risk', ({ params }) =>
    HttpResponse.json({
      accountId: params.id,
      highRisk: false,
      riskScore: null,
      reason: null,
      flaggedAt: null,
    }),
  ),
);
