import { screen } from '@testing-library/react';
import { renderWithClient } from '../test/render';
import { KpiBar } from './KpiBar';

describe('KpiBar', () => {
  it('AC-008-06: shows open alerts by severity, review load and last-hour outcomes', async () => {
    renderWithClient(<KpiBar liveAlertsPerMinute={4} />);

    await screen.findByText('53'); // wait for the stats request to resolve
    expect(screen.getByRole('group', { name: /open high/i })).toHaveTextContent('7');
    expect(screen.getByRole('group', { name: /open medium/i })).toHaveTextContent('12');
    expect(screen.getByRole('group', { name: /under review/i })).toHaveTextContent('4');
    expect(screen.getByRole('group', { name: /raised \(1h\)/i })).toHaveTextContent('53');
    expect(screen.getByRole('group', { name: /confirmed fraud/i })).toHaveTextContent('9');
    expect(screen.getByRole('group', { name: /false positives/i })).toHaveTextContent('6');
    expect(screen.getByRole('group', { name: /live alerts\/min/i })).toHaveTextContent('4');
  });
});
