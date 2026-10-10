import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { establishSession } from './auth/oidc';
import { SessionContext } from './auth/SessionContext';
import './styles.css';

const client = new QueryClient({ defaultOptions: { queries: { staleTime: 5_000 } } });

const root = document.getElementById('root');
if (!root) throw new Error('#root element missing from index.html');

// Nothing renders until the user is signed in (null = we are being redirected to the login page).
void establishSession().then((session) => {
  if (!session) return;
  createRoot(root).render(
    <StrictMode>
      <SessionContext.Provider value={session}>
        <QueryClientProvider client={client}>
          <App />
        </QueryClientProvider>
      </SessionContext.Provider>
    </StrictMode>,
  );
});
