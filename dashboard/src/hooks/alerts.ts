import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ApiError,
  fetchAccountHistory,
  fetchAccountRisk,
  fetchAlertDetails,
  fetchAlertFeed,
  fetchAlertStats,
  reviewAlert,
} from '../api/client';
import type { AlertDetails, AlertFilters, AlertStatus } from '../api/types';

export const alertKeys = {
  feed: (filters: AlertFilters) => ['alerts', filters] as const,
  details: (id: string) => ['alert', id] as const,
  stats: ['stats'] as const,
};

/** Keyset-paginated queue: each "Load more" follows the server's opaque cursor. */
export const useAlertFeed = (filters: AlertFilters) =>
  useInfiniteQuery({
    queryKey: alertKeys.feed(filters),
    queryFn: ({ pageParam }) => fetchAlertFeed(filters, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });

export const useAlertDetails = (id: string | null) =>
  useQuery({
    queryKey: alertKeys.details(id ?? ''),
    queryFn: () => fetchAlertDetails(id ?? ''),
    enabled: id !== null,
  });

export const useAlertStats = () =>
  useQuery({ queryKey: alertKeys.stats, queryFn: fetchAlertStats, refetchInterval: 30_000 });

export const useAccountHistory = (accountId: string | undefined) =>
  useQuery({
    queryKey: ['account', accountId, 'history'],
    queryFn: () => fetchAccountHistory(accountId ?? ''),
    enabled: accountId !== undefined,
  });

export const useAccountRisk = (accountId: string | undefined) =>
  useQuery({
    queryKey: ['account', accountId, 'risk'],
    queryFn: () => fetchAccountRisk(accountId ?? ''),
    enabled: accountId !== undefined,
  });

/**
 * PATCH with the version on screen. On 409 the server returns why; for a stale version it
 * includes the current alert, which we show immediately (then refetch to get the audit trail too).
 */
export function useReviewAlert(id: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ status, version }: { status: AlertStatus; version: number }) =>
      reviewAlert(id, status, version),
    onSuccess: (updated) => {
      client.setQueryData<AlertDetails>(alertKeys.details(id), (old) =>
        old ? { ...old, alert: updated } : old,
      );
    },
    onError: (error) => {
      const current = error instanceof ApiError ? error.problem.current : undefined;
      if (current) {
        client.setQueryData<AlertDetails>(alertKeys.details(id), (old) =>
          old ? { ...old, alert: current } : old,
        );
      }
    },
    onSettled: () => {
      void client.invalidateQueries({ queryKey: alertKeys.details(id) });
      void client.invalidateQueries({ queryKey: alertKeys.stats });
    },
  });
}
