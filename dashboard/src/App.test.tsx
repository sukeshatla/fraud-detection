import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { act } from 'react';
import { App } from './App';
import { FakeEventSource } from './test/fakeEventSource';
import { anAlert } from './test/fixtures';
import { renderWithClient } from './test/render';
import { server } from './test/server';

describe('App: live updates', () => {
  it('AC-008-02: an alert pushed over SSE appears at the top of the queue without a refetch', async () => {
    const existing = anAlert({ transactionId: 'txn-existing', amount: 100 });
    let feedCalls = 0;
    server.use(
      http.get('*/api/v1/alerts/feed', () => {
        feedCalls++;
        return HttpResponse.json({ items: [existing], nextCursor: null });
      }),
    );
    renderWithClient(<App />);
    await screen.findByRole('option', { name: /100\.00/ });

    act(() => FakeEventSource.latest().emit('alert.created', anAlert({ amount: 4321 })));

    const options = await within(screen.getByRole('listbox', { name: 'Alerts' })).findAllByRole('option');
    expect(options[0]).toHaveTextContent('4,321.00');
    expect(within(options[0] as HTMLElement).getByText(/new/i)).toBeInTheDocument();
    expect(feedCalls).toBe(1);
    expect(FakeEventSource.latest().url).toBe('/api/v1/alerts/stream');
  });

  it('AC-008-02: after a reconnect the queue is refetched (events may have been missed)', async () => {
    let feedCalls = 0;
    server.use(
      http.get('*/api/v1/alerts/feed', () => {
        feedCalls++;
        return HttpResponse.json({ items: [], nextCursor: null });
      }),
    );
    renderWithClient(<App />);
    await screen.findByText(/no alerts/i);

    act(() => {
      FakeEventSource.latest().emit('error');
      FakeEventSource.latest().emit('open');
    });

    await vi.waitFor(() => expect(feedCalls).toBe(2));
  });
});
