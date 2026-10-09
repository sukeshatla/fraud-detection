import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { anAlert } from '../test/fixtures';
import { renderWithClient } from '../test/render';
import { server } from '../test/server';
import { AlertQueue } from './AlertQueue';

describe('AlertQueue', () => {
  it('AC-008-03: lists alerts with severity as TEXT (not colour alone), amount and score', async () => {
    server.use(
      http.get('*/api/v1/alerts/feed', () =>
        HttpResponse.json({ items: [anAlert({ amount: 9000 })], nextCursor: null }),
      ),
    );

    renderWithClient(<AlertQueue selectedId={null} onSelect={() => {}} />);

    const row = await screen.findByRole('option', { name: /9,000\.00/ });
    expect(within(row).getByText('High')).toBeInTheDocument();
    expect(row).toHaveTextContent('88');
  });

  it('AC-008-03: changing the severity filter refetches with that filter', async () => {
    const seen: string[] = [];
    server.use(
      http.get('*/api/v1/alerts/feed', ({ request }) => {
        seen.push(new URL(request.url).searchParams.get('severity') ?? 'ALL');
        return HttpResponse.json({ items: [], nextCursor: null });
      }),
    );
    renderWithClient(<AlertQueue selectedId={null} onSelect={() => {}} />);

    await userEvent.selectOptions(await screen.findByLabelText('Severity'), 'MEDIUM');

    await waitFor(() => expect(seen).toContain('MEDIUM'));
  });

  it('AC-008-03: "Load more" follows the keyset cursor and appends', async () => {
    const first = anAlert({ transactionId: 'txn-first' });
    const second = anAlert({ transactionId: 'txn-second' });
    server.use(
      http.get('*/api/v1/alerts/feed', ({ request }) =>
        new URL(request.url).searchParams.get('after') === 'cursor-1'
          ? HttpResponse.json({ items: [second], nextCursor: null })
          : HttpResponse.json({ items: [first], nextCursor: 'cursor-1' }),
      ),
    );
    renderWithClient(<AlertQueue selectedId={null} onSelect={() => {}} />);

    await userEvent.click(await screen.findByRole('button', { name: /load more/i }));

    await waitFor(() =>
      expect(within(screen.getByRole('listbox', { name: 'Alerts' })).getAllByRole('option')).toHaveLength(2),
    );
    expect(screen.queryByRole('button', { name: /load more/i })).not.toBeInTheDocument();
  });

  it('AC-008-08: arrow keys move the selection through the queue', async () => {
    const a = anAlert();
    const b = anAlert();
    server.use(
      http.get('*/api/v1/alerts/feed', () => HttpResponse.json({ items: [a, b], nextCursor: null })),
    );
    const onSelect = vi.fn();
    renderWithClient(<AlertQueue selectedId={a.id} onSelect={onSelect} />);

    (await screen.findByRole('listbox')).focus();
    await userEvent.keyboard('{ArrowDown}');

    expect(onSelect).toHaveBeenCalledWith(b.id);
  });
});
