// Mirrors the alert-service and scoring-service REST contracts.

export type AlertStatus = 'OPEN' | 'UNDER_REVIEW' | 'CONFIRMED_FRAUD' | 'FALSE_POSITIVE';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH';

export interface RuleHit {
  code: string;
  weight: number;
  reason: string;
}

export interface Alert {
  id: string;
  transactionId: string;
  accountId: string;
  amount: number;
  currency: string;
  merchantId: string;
  merchantCategoryCode: string;
  country: string;
  channel: string;
  occurredAt: string;
  ruleScore: number;
  riskScore: number;
  mlProbability: number | null;
  modelVersion: string | null;
  decision: string;
  severity: Severity;
  status: AlertStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
  ruleHits: RuleHit[];
}

export interface AlertFeed {
  items: Alert[];
  nextCursor: string | null;
}

export interface AuditEntry {
  fromStatus: AlertStatus;
  toStatus: AlertStatus;
  actor: string;
  at: string;
}

export interface AlertDetails {
  alert: Alert;
  history: AuditEntry[];
}

export interface AlertStats {
  openBySeverity: Record<Severity, number>;
  underReview: number;
  raised: number;
  confirmedFraud: number;
  falsePositives: number;
  since: string;
}

export interface TransactionHistoryEntry {
  transactionId: string;
  amount: number;
  currency: string;
  merchantId: string;
  merchantCategoryCode: string;
  country: string;
  channel: string;
  occurredAt: string;
  riskScore: number;
  decision: string;
}

export interface AccountRisk {
  accountId: string;
  highRisk: boolean;
  riskScore: number | null;
  reason: string | null;
  flaggedAt: string | null;
}

/** RFC 9457 problem details, as returned by every service. */
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  current?: Alert;
}

export interface AlertFilters {
  status: AlertStatus;
  severity: Severity | 'ALL';
}

export type AlertChange = { type: 'created' | 'updated'; alert: Alert };
