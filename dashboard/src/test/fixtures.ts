import type { Alert, AlertStats } from '../api/types';

let counter = 0;

export function anAlert(overrides: Partial<Alert> = {}): Alert {
  counter += 1;
  return {
    id: `00000000-0000-0000-0000-${String(counter).padStart(12, '0')}`,
    transactionId: `txn-${counter}`,
    accountId: 'acc-1001',
    amount: 7500,
    currency: 'USD',
    merchantId: 'm-7995',
    merchantCategoryCode: '7995',
    country: 'MT',
    channel: 'CARD_NOT_PRESENT',
    occurredAt: '2026-10-09T18:15:30Z',
    ruleScore: 85,
    riskScore: 88,
    mlProbability: 0.93,
    modelVersion: 'lr-v1',
    decision: 'DECLINE',
    severity: 'HIGH',
    status: 'OPEN',
    version: 0,
    createdAt: '2026-10-09T18:15:31Z',
    updatedAt: '2026-10-09T18:15:31Z',
    ruleHits: [
      { code: 'HIGH_AMOUNT', weight: 30, reason: 'amount 7500.00 USD >= 5000 USD' },
      { code: 'GEO_VELOCITY', weight: 35, reason: 'country changed US -> MT within 12 min' },
    ],
    ...overrides,
  };
}

export const stats: AlertStats = {
  openBySeverity: { HIGH: 7, MEDIUM: 12, LOW: 30 },
  underReview: 4,
  raised: 53,
  confirmedFraud: 9,
  falsePositives: 6,
  since: '2026-10-09T17:15:00Z',
};
