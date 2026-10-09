import { useAlertStats } from '../hooks/alerts';

function Kpi({ label, value, tone }: { label: string; value: number | undefined; tone?: string }) {
  return (
    <div role="group" aria-label={label} className={`kpi ${tone ?? ''}`}>
      <span className="kpi-value">{value ?? '–'}</span>
      <span className="kpi-label">{label}</span>
    </div>
  );
}

export function KpiBar({ liveAlertsPerMinute }: { liveAlertsPerMinute: number }) {
  const { data } = useAlertStats();
  return (
    <section className="kpi-bar" aria-label="Key metrics">
      <Kpi label="Open HIGH" value={data?.openBySeverity.HIGH} tone="tone-high" />
      <Kpi label="Open MEDIUM" value={data?.openBySeverity.MEDIUM} tone="tone-medium" />
      <Kpi label="Open LOW" value={data?.openBySeverity.LOW} />
      <Kpi label="Under review" value={data?.underReview} />
      <Kpi label="Raised (1h)" value={data?.raised} />
      <Kpi label="Confirmed fraud (1h)" value={data?.confirmedFraud} />
      <Kpi label="False positives (1h)" value={data?.falsePositives} />
      <Kpi label="Live alerts/min" value={liveAlertsPerMinute} tone="tone-live" />
    </section>
  );
}
