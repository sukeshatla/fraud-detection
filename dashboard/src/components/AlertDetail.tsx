import { ApiError } from '../api/client';
import { canClose } from '../auth/session';
import { useSession } from '../auth/SessionContext';
import type { AlertStatus } from '../api/types';
import { useAccountHistory, useAccountRisk, useAlertDetails, useReviewAlert } from '../hooks/alerts';
import { formatMoney, formatTime, statusLabel } from './format';
import { SeverityBadge } from './SeverityBadge';

const TERMINAL: readonly AlertStatus[] = ['CONFIRMED_FRAUD', 'FALSE_POSITIVE'];

const ACTIONS: Record<AlertStatus, { label: string; to: AlertStatus; tone?: string }[]> = {
  OPEN: [{ label: 'Start review', to: 'UNDER_REVIEW' }],
  UNDER_REVIEW: [
    { label: 'Confirm fraud', to: 'CONFIRMED_FRAUD', tone: 'danger' },
    { label: 'False positive', to: 'FALSE_POSITIVE' },
    { label: 'Release', to: 'OPEN', tone: 'quiet' },
  ],
  CONFIRMED_FRAUD: [],
  FALSE_POSITIVE: [],
};

/** Maps the three distinct 409s (and anything else) to guidance an analyst can act on. */
function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.kind) {
      case 'stale-version':
        return 'Updated by someone else. Showing the latest version.';
      case 'alert-locked':
        return 'This alert is being updated right now. Try again in a moment.';
      case 'invalid-transition':
        return 'That action is no longer allowed for this alert.';
    }
  }
  return 'Something went wrong. Please try again.';
}

function Score({ label, value }: { label: string; value: string }) {
  return (
    <div role="group" aria-label={label} className="score-box">
      <span className="kpi-value">{value}</span>
      <span className="kpi-label">{label}</span>
    </div>
  );
}

export function AlertDetail({ alertId }: { alertId: string }) {
  const details = useAlertDetails(alertId);
  const alert = details.data?.alert;
  const history = useAccountHistory(alert?.accountId);
  const risk = useAccountRisk(alert?.accountId);
  const review = useReviewAlert(alertId);
  const session = useSession();

  if (details.isError) return <p role="alert">Could not load this alert.</p>;
  if (!alert) return <p className="empty">Loading…</p>;

  return (
    <article className="detail" aria-label="Alert detail">
      <header>
        <h2>
          {formatMoney(alert.amount, alert.currency)} <SeverityBadge severity={alert.severity} />
        </h2>
        <p className="meta">
          {alert.transactionId} · {alert.accountId} · merchant {alert.merchantId} (MCC{' '}
          {alert.merchantCategoryCode}) · {alert.country} · {alert.channel.replaceAll('_', ' ').toLowerCase()}
        </p>
        <p>
          <span className={`badge status status-${alert.status.toLowerCase()}`}>
            {statusLabel[alert.status]}
          </span>
          {risk.data?.highRisk && (
            <span className="badge high-risk">⚠ High-risk account (score {risk.data.riskScore})</span>
          )}
        </p>
      </header>

      {review.isError && (
        <p role="alert" className="error">
          {errorMessage(review.error)}
        </p>
      )}

      <div className="actions">
        {ACTIONS[alert.status]
          .filter((action) => !TERMINAL.includes(action.to) || canClose(session))
          .map((action) => (
            <button
              key={action.to}
              type="button"
              className={action.tone}
              disabled={review.isPending}
              onClick={() => review.mutate({ status: action.to, version: alert.version })}
            >
              {action.label}
            </button>
          ))}
      </div>
      {alert.status === 'UNDER_REVIEW' && !canClose(session) && (
        <p className="hint">Confirming fraud or a false positive requires the Supervisor role.</p>
      )}

      <section className="scores" aria-label="Score breakdown">
        <Score label="Risk score" value={String(alert.riskScore)} />
        <Score label="Rule score" value={String(alert.ruleScore)} />
        <Score
          label="ML probability"
          value={alert.mlProbability === null ? 'n/a' : `${Math.round(alert.mlProbability * 100)}%`}
        />
        <Score label="Decision" value={alert.decision} />
      </section>

      <section aria-label="Rule hits">
        <h3>Why it was flagged</h3>
        <ul className="hits">
          {alert.ruleHits.map((hit) => (
            <li key={hit.code}>
              <code>{hit.code}</code> <span className="weight">+{hit.weight}</span> <span>{hit.reason}</span>
            </li>
          ))}
        </ul>
      </section>

      <section aria-label="Account history">
        <h3>Recent transactions on {alert.accountId}</h3>
        <table>
          <thead>
            <tr>
              <th scope="col">Transaction</th>
              <th scope="col">Amount</th>
              <th scope="col">Country</th>
              <th scope="col">Score</th>
              <th scope="col">Time</th>
            </tr>
          </thead>
          <tbody>
            {(history.data ?? []).map((tx) => (
              <tr key={tx.transactionId}>
                <td>{tx.transactionId}</td>
                <td>{formatMoney(tx.amount, tx.currency)}</td>
                <td>{tx.country}</td>
                <td>{tx.riskScore}</td>
                <td>{formatTime(tx.occurredAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {details.data && details.data.history.length > 0 && (
        <section aria-label="Audit trail">
          <h3>Audit trail</h3>
          <ol className="audit">
            {details.data.history.map((entry) => (
              <li key={`${entry.at}-${entry.toStatus}`}>
                {formatTime(entry.at)}: {entry.actor} moved it from {statusLabel[entry.fromStatus]} to{' '}
                {statusLabel[entry.toStatus]}
              </li>
            ))}
          </ol>
        </section>
      )}
    </article>
  );
}
