import type {
  AccountRisk,
  Alert,
  AlertDetails,
  AlertFeed,
  AlertFilters,
  AlertStats,
  AlertStatus,
  Problem,
  TransactionHistoryEntry,
} from './types';

/** Non-2xx response with its RFC 9457 body. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: Problem,
  ) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`);
  }

  /** Problem type suffix, e.g. "stale-version" from "urn:fraud-platform:problem:stale-version". */
  get kind(): string | undefined {
    return this.problem.type?.split(':').pop();
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(new URL(path, window.location.origin), {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  });
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as Problem;
    throw new ApiError(response.status, problem);
  }
  return (await response.json()) as T;
}

export function fetchAlertFeed(filters: AlertFilters, after?: string, size = 25): Promise<AlertFeed> {
  const params = new URLSearchParams({ status: filters.status, size: String(size) });
  if (filters.severity !== 'ALL') params.set('severity', filters.severity);
  if (after) params.set('after', after);
  return request(`/api/v1/alerts/feed?${params}`);
}

export const fetchAlertDetails = (id: string) => request<AlertDetails>(`/api/v1/alerts/${id}`);

export const fetchAlertStats = () => request<AlertStats>('/api/v1/alerts/stats');

export const fetchAccountHistory = (accountId: string) =>
  request<TransactionHistoryEntry[]>(
    `/api/v1/accounts/${encodeURIComponent(accountId)}/transactions?limit=20`,
  );

export const fetchAccountRisk = (accountId: string) =>
  request<AccountRisk>(`/api/v1/accounts/${encodeURIComponent(accountId)}/risk`);

/** Optimistic concurrency: send the version we looked at; the server answers 409 if it moved on. */
export function reviewAlert(id: string, status: AlertStatus, version: number, actor: string): Promise<Alert> {
  return request(`/api/v1/alerts/${id}`, {
    method: 'PATCH',
    headers: { 'X-Actor': actor },
    body: JSON.stringify({ status, version }),
  });
}
