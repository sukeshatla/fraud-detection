import { accessToken } from '../auth/session';
import type { Alert, AlertChange } from './types';

export const STREAM_URL = '/api/v1/alerts/stream';
const RETRY_AFTER_CLOSE_MS = 3_000;

/**
 * EventSource can't send an Authorization header, so the token goes in the query string; the API
 * accepts that on this path only and the gateway doesn't log it.
 */
const streamUrl = () => {
  const token = accessToken();
  return token ? `${STREAM_URL}?access_token=${encodeURIComponent(token)}` : STREAM_URL;
};

/**
 * Subscribes to the alert SSE stream. EventSource reconnects by itself after network errors;
 * {@code onReconnect} lets the caller refetch whatever it may have missed while disconnected.
 * A non-200 answer (e.g. 401 once the token in the URL has expired) closes the EventSource for
 * good, so then we open a new one with the current token.
 */
export function subscribeToAlerts(
  onChange: (change: AlertChange) => void,
  onReconnect: () => void,
): () => void {
  let source: EventSource;
  let hadError = false;
  let stopped = false;
  let retry: ReturnType<typeof setTimeout> | undefined;

  const handle = (type: AlertChange['type']) => (event: MessageEvent<string>) =>
    onChange({ type, alert: JSON.parse(event.data) as Alert });

  const connect = () => {
    source = new EventSource(streamUrl());
    source.addEventListener('alert.created', handle('created'));
    source.addEventListener('alert.updated', handle('updated'));
    source.addEventListener('error', () => {
      hadError = true;
      if (source.readyState === EventSource.CLOSED && !stopped) {
        retry = setTimeout(connect, RETRY_AFTER_CLOSE_MS);
      }
    });
    source.addEventListener('open', () => {
      if (hadError) {
        hadError = false;
        onReconnect();
      }
    });
  };

  connect();
  return () => {
    stopped = true;
    clearTimeout(retry);
    source.close();
  };
}
