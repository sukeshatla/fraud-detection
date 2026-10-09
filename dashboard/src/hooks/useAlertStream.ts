import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { subscribeToAlerts } from '../api/stream';
import { applyAlertChange } from './mergeChange';

const MINUTE = 60_000;

/** Live updates: merges SSE changes into the cache, highlights new alerts, counts alerts/min. */
export function useAlertStream() {
  const client = useQueryClient();
  const [newIds, setNewIds] = useState<ReadonlySet<string>>(new Set());
  const [createdAt, setCreatedAt] = useState<number[]>([]);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    return subscribeToAlerts(
      (change) => {
        applyAlertChange(client, change);
        if (change.type === 'created') {
          setNewIds((ids) => new Set(ids).add(change.alert.id));
          setCreatedAt((times) => [...times.filter((t) => t > Date.now() - MINUTE), Date.now()]);
        }
      },
      () => void client.invalidateQueries(), // reconnected: we may have missed events
    );
  }, [client]);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 5_000);
    return () => clearInterval(timer);
  }, []);

  return { newIds, alertsPerMinute: createdAt.filter((t) => t > now - MINUTE).length };
}
