import type { Alert, AlertChange } from './types';

export const STREAM_URL = '/api/v1/alerts/stream';

/**
 * Subscribes to the alert SSE stream. EventSource reconnects by itself after network errors;
 * {@code onReconnect} lets the caller refetch whatever it may have missed while disconnected.
 */
export function subscribeToAlerts(
  onChange: (change: AlertChange) => void,
  onReconnect: () => void,
): () => void {
  const source = new EventSource(STREAM_URL);
  let hadError = false;

  const handle = (type: AlertChange['type']) => (event: MessageEvent<string>) =>
    onChange({ type, alert: JSON.parse(event.data) as Alert });

  source.addEventListener('alert.created', handle('created'));
  source.addEventListener('alert.updated', handle('updated'));
  source.addEventListener('error', () => {
    hadError = true;
  });
  source.addEventListener('open', () => {
    if (hadError) {
      hadError = false;
      onReconnect();
    }
  });
  return () => source.close();
}
