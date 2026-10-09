import type { InfiniteData, QueryClient } from '@tanstack/react-query';
import type { Alert, AlertChange, AlertFeed, AlertFilters } from '../api/types';

type FeedData = InfiniteData<AlertFeed, string | undefined>;

const matches = (alert: Alert, filters: AlertFilters) =>
  alert.status === filters.status && (filters.severity === 'ALL' || alert.severity === filters.severity);

/**
 * Applies one live change to a cached queue page-set, without a refetch:
 * created + matching → prepend; updated + still matching → replace in place; no longer matching → remove.
 */
export function mergeChange(data: FeedData, filters: AlertFilters, change: AlertChange): FeedData {
  const { alert } = change;
  const present = data.pages.some((page) => page.items.some((a) => a.id === alert.id));
  const keep = matches(alert, filters);

  if (!present) {
    if (change.type !== 'created' || !keep) return data;
    const [first, ...rest] = data.pages;
    return {
      ...data,
      pages: [{ ...(first ?? { nextCursor: null }), items: [alert, ...(first?.items ?? [])] }, ...rest],
    };
  }
  return {
    ...data,
    pages: data.pages.map((page) => ({
      ...page,
      items: keep
        ? page.items.map((a) => (a.id === alert.id ? alert : a))
        : page.items.filter((a) => a.id !== alert.id),
    })),
  };
}

/** Merges a change into every cached queue (one per filter combination) and refreshes dependants. */
export function applyAlertChange(client: QueryClient, change: AlertChange) {
  for (const [key, data] of client.getQueriesData<FeedData>({ queryKey: ['alerts'] })) {
    const filters = key[1] as AlertFilters | undefined;
    if (data && filters) client.setQueryData(key, mergeChange(data, filters, change));
  }
  void client.invalidateQueries({ queryKey: ['alert', change.alert.id] });
  void client.invalidateQueries({ queryKey: ['stats'] });
}
