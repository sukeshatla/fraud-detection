import { useState } from 'react';
import { useSession } from './auth/SessionContext';
import { AlertDetail } from './components/AlertDetail';
import { AlertQueue } from './components/AlertQueue';
import { KpiBar } from './components/KpiBar';
import { useAlertStream } from './hooks/useAlertStream';

export function App() {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const { newIds, alertsPerMinute } = useAlertStream();
  const session = useSession();

  return (
    <div className="app">
      <header className="top">
        <h1>Fraud Alert Console</h1>
        <KpiBar liveAlertsPerMinute={alertsPerMinute} />
        <div className="user" aria-label="Signed-in user">
          <span>
            {session.username}{' '}
            <span className="roles">({session.roles.filter((r) => r === r.toUpperCase()).join(', ')})</span>
          </span>
          <button type="button" className="quiet" onClick={session.signOut}>
            Sign out
          </button>
        </div>
      </header>
      <main className="layout">
        <AlertQueue selectedId={selectedId} onSelect={setSelectedId} newIds={newIds} />
        {selectedId ? (
          <AlertDetail alertId={selectedId} />
        ) : (
          <p className="empty detail-placeholder">Select an alert (↑/↓ to move through the queue).</p>
        )}
      </main>
    </div>
  );
}
