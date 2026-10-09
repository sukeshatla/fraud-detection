import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { Alert } from '../api/types';
import { anAlert } from '../test/fixtures';
import { renderWithClient } from '../test/render';
import { server } from '../test/server';
import { AlertDetail } from './AlertDetail';

function serveDetails(...versions: Alert[]) {
  let call = 0;
  server.use(
    http.get('*/api/v1/alerts/:id', () => {
      const alert = versions[Math.min(call++, versions.length - 1)];
      return HttpResponse.json({ alert, history: [] });
    }),
  );
}

describe('AlertDetail', () => {
  it('AC-008-04: shows the score breakdown, rule hits and account history', async () => {
    const alert = anAlert();
    serveDetails(alert);
    server.use(
      http.get('*/api/v1/accounts/:id/transactions', () =>
        HttpResponse.json([
          {
            transactionId: 'txn-older',
            amount: 12,
            currency: 'USD',
            merchantId: 'm',
            merchantCategoryCode: '5411',
            country: 'US',
            channel: 'CARD_PRESENT',
            occurredAt: '2026-10-09T18:00:00Z',
            riskScore: 0,
            decision: 'APPROVE',
          },
        ]),
      ),
    );

    renderWithClient(<AlertDetail alertId={alert.id} />);

    expect(await screen.findByText('HIGH_AMOUNT')).toBeInTheDocument();
    expect(screen.getByText(/country changed US -> MT/)).toBeInTheDocument();
    expect(screen.getByRole('group', { name: /ml probability/i })).toHaveTextContent('93%');
    expect(screen.getByRole('group', { name: /rule score/i })).toHaveTextContent('85');
    expect(await screen.findByText('txn-older')).toBeInTheDocument();
  });

  it('AC-008-05: OPEN alert offers "Start review" and sends the version we are looking at', async () => {
    const alert = anAlert({ status: 'OPEN', version: 0 });
    serveDetails(alert, { ...alert, status: 'UNDER_REVIEW', version: 1 });
    let body: unknown;
    server.use(
      http.patch('*/api/v1/alerts/:id', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...alert, status: 'UNDER_REVIEW', version: 1 });
      }),
    );
    renderWithClient(<AlertDetail alertId={alert.id} />);

    await userEvent.click(await screen.findByRole('button', { name: /start review/i }));

    await waitFor(() => expect(body).toEqual({ status: 'UNDER_REVIEW', version: 0 }));
    expect(await screen.findByRole('button', { name: /confirm fraud/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /false positive/i })).toBeInTheDocument();
  });

  it('AC-008-05: a 409 stale-version says someone else updated it and shows the latest state', async () => {
    const alert = anAlert({ status: 'UNDER_REVIEW', version: 1 });
    const theirs = { ...alert, status: 'CONFIRMED_FRAUD' as const, version: 2 };
    serveDetails(alert, theirs);
    server.use(
      http.patch('*/api/v1/alerts/:id', () =>
        HttpResponse.json(
          {
            type: 'urn:fraud-platform:problem:stale-version',
            status: 409,
            detail: 'modified concurrently',
            current: theirs,
          },
          { status: 409 },
        ),
      ),
    );
    renderWithClient(<AlertDetail alertId={alert.id} />);

    await userEvent.click(await screen.findByRole('button', { name: /false positive/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/updated by someone else/i);
    expect(await screen.findByText('Confirmed fraud')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /false positive/i })).not.toBeInTheDocument();
  });

  it('AC-008-05: a 409 alert-locked asks the analyst to retry', async () => {
    const alert = anAlert({ status: 'OPEN', version: 0 });
    serveDetails(alert);
    server.use(
      http.patch('*/api/v1/alerts/:id', () =>
        HttpResponse.json({ type: 'urn:fraud-platform:problem:alert-locked', status: 409 }, { status: 409 }),
      ),
    );
    renderWithClient(<AlertDetail alertId={alert.id} />);

    await userEvent.click(await screen.findByRole('button', { name: /start review/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/being updated right now/i);
  });

  it('flags a high-risk account', async () => {
    const alert = anAlert();
    serveDetails(alert);
    server.use(
      http.get('*/api/v1/accounts/:id/risk', () =>
        HttpResponse.json({
          accountId: 'acc-1001',
          highRisk: true,
          riskScore: 85,
          reason: 'HIGH_AMOUNT',
          flaggedAt: '2026-10-09T18:00:00Z',
        }),
      ),
    );

    renderWithClient(<AlertDetail alertId={alert.id} />);

    expect(await screen.findByText(/high-risk account/i)).toBeInTheDocument();
  });
});
