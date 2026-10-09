import type { Severity } from '../api/types';
import { severityLabel } from './format';

const icon: Record<Severity, string> = { HIGH: '▲', MEDIUM: '◆', LOW: '▼' };

/** Severity is conveyed by text and shape, with colour only as reinforcement (WCAG 1.4.1). */
export function SeverityBadge({ severity }: { severity: Severity }) {
  return (
    <span className={`badge severity-${severity.toLowerCase()}`}>
      <span aria-hidden="true">{icon[severity]} </span>
      {severityLabel[severity]}
    </span>
  );
}
