import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import type { Session } from '../auth/session';
import { SessionContext } from '../auth/SessionContext';

export const supervisor: Session = { username: 'sam', roles: ['ANALYST', 'SUPERVISOR'], signOut: () => {} };
export const analyst: Session = { username: 'ada', roles: ['ANALYST'], signOut: () => {} };

export function renderWithClient(ui: ReactElement, session: Session = supervisor) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return {
    client,
    ...render(
      <SessionContext.Provider value={session}>
        <QueryClientProvider client={client}>{ui}</QueryClientProvider>
      </SessionContext.Provider>,
    ),
  };
}
