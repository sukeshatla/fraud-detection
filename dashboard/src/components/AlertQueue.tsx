import { useState, type KeyboardEvent } from 'react';
import type { AlertFilters, AlertStatus, Severity } from '../api/types';
import { useAlertFeed } from '../hooks/alerts';
import { formatMoney, formatTime, statusLabel } from './format';
import { SeverityBadge } from './SeverityBadge';

interface Props {
  selectedId: string | null;
  onSelect: (id: string) => void;
  newIds?: ReadonlySet<string>;
}

const STATUSES: AlertStatus[] = ['OPEN', 'UNDER_REVIEW', 'CONFIRMED_FRAUD', 'FALSE_POSITIVE'];
const SEVERITIES: (Severity | 'ALL')[] = ['ALL', 'HIGH', 'MEDIUM', 'LOW'];

export function AlertQueue({ selectedId, onSelect, newIds = new Set() }: Props) {
  const [filters, setFilters] = useState<AlertFilters>({ status: 'OPEN', severity: 'ALL' });
  const feed = useAlertFeed(filters);
  const alerts = feed.data?.pages.flatMap((page) => page.items) ?? [];

  // Keyboard navigation: ↑/↓ move through the queue (listbox pattern, WAI-ARIA APG).
  const onKeyDown = (event: KeyboardEvent) => {
    const index = alerts.findIndex((a) => a.id === selectedId);
    const next = event.key === 'ArrowDown' ? index + 1 : event.key === 'ArrowUp' ? index - 1 : null;
    const target = next === null ? undefined : alerts[Math.max(0, next)];
    if (target) {
      event.preventDefault();
      onSelect(target.id);
    }
  };

  return (
    <section className="queue" aria-label="Alert queue">
      <div className="filters">
        <label>
          Status
          <select
            value={filters.status}
            onChange={(e) => setFilters({ ...filters, status: e.target.value as AlertStatus })}
          >
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {statusLabel[s]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Severity
          <select
            value={filters.severity}
            onChange={(e) => setFilters({ ...filters, severity: e.target.value as Severity | 'ALL' })}
          >
            {SEVERITIES.map((s) => (
              <option key={s} value={s}>
                {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </label>
      </div>

      {feed.isError && <p role="alert">Could not load alerts.</p>}
      {feed.isSuccess && alerts.length === 0 && <p className="empty">No alerts match these filters.</p>}

      <ul
        role="listbox"
        tabIndex={0}
        aria-label="Alerts"
        aria-activedescendant={selectedId ?? undefined}
        onKeyDown={onKeyDown}
      >
        {alerts.map((alert) => (
          <li
            key={alert.id}
            id={alert.id}
            role="option"
            aria-selected={alert.id === selectedId}
            className={`queue-row ${alert.id === selectedId ? 'selected' : ''}`}
            onClick={() => onSelect(alert.id)}
          >
            <SeverityBadge severity={alert.severity} />
            <span className="amount">{formatMoney(alert.amount, alert.currency)}</span>
            <span className="meta">
              {alert.accountId} · MCC {alert.merchantCategoryCode} · {alert.country}
            </span>
            <span className="score" title="Risk score">
              {alert.riskScore}
            </span>
            <span className="time">{formatTime(alert.createdAt)}</span>
            {newIds.has(alert.id) && <span className="badge new">New</span>}
          </li>
        ))}
      </ul>

      {feed.hasNextPage && (
        <button
          type="button"
          className="load-more"
          onClick={() => void feed.fetchNextPage()}
          disabled={feed.isFetchingNextPage}
        >
          {feed.isFetchingNextPage ? 'Loading…' : 'Load more'}
        </button>
      )}
    </section>
  );
}
