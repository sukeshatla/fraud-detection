import type { AlertStatus, Severity } from '../api/types';

export const formatMoney = (amount: number, currency: string) =>
  `${new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(amount)} ${currency}`;

export const formatTime = (iso: string) =>
  new Date(iso).toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });

export const statusLabel: Record<AlertStatus, string> = {
  OPEN: 'Open',
  UNDER_REVIEW: 'Under review',
  CONFIRMED_FRAUD: 'Confirmed fraud',
  FALSE_POSITIVE: 'False positive',
};

export const severityLabel: Record<Severity, string> = { HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' };
