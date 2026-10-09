import type { InfiniteData } from '@tanstack/react-query';
import type { AlertFeed, AlertFilters } from '../api/types';
import { anAlert } from '../test/fixtures';
import { mergeChange } from './mergeChange';

const openHigh: AlertFilters = { status: 'OPEN', severity: 'HIGH' };

function feed(...pages: AlertFeed['items'][]): InfiniteData<AlertFeed, string | undefined> {
  return {
    pages: pages.map((items) => ({ items, nextCursor: null })),
    pageParams: pages.map(() => undefined),
  };
}

describe('mergeChange (live SSE updates into the cached queue)', () => {
  it('AC-008-02: prepends a created alert that matches the filters', () => {
    const existing = anAlert();
    const created = anAlert();

    const result = mergeChange(feed([existing]), openHigh, { type: 'created', alert: created });

    expect(result.pages[0]?.items.map((a) => a.id)).toEqual([created.id, existing.id]);
  });

  it('ignores a created alert that does not match the filters', () => {
    const result = mergeChange(feed([]), openHigh, { type: 'created', alert: anAlert({ severity: 'LOW' }) });

    expect(result.pages[0]?.items).toHaveLength(0);
  });

  it('does not duplicate an alert that is already in the queue', () => {
    const alert = anAlert();

    const result = mergeChange(feed([alert]), openHigh, { type: 'created', alert });

    expect(result.pages[0]?.items).toHaveLength(1);
  });

  it('AC-008-05: an update that leaves the filter (OPEN → UNDER_REVIEW) removes the alert from every page', () => {
    const a = anAlert();
    const b = anAlert();

    const result = mergeChange(feed([a], [b]), openHigh, {
      type: 'updated',
      alert: { ...b, status: 'UNDER_REVIEW', version: 1 },
    });

    expect(result.pages.flatMap((p) => p.items).map((x) => x.id)).toEqual([a.id]);
  });

  it('an update that still matches replaces the alert in place', () => {
    const a = anAlert();
    const all: AlertFilters = { status: 'OPEN', severity: 'ALL' };

    const result = mergeChange(feed([a]), all, {
      type: 'updated',
      alert: { ...a, riskScore: 99, version: 3 },
    });

    expect(result.pages[0]?.items[0]).toMatchObject({ id: a.id, riskScore: 99, version: 3 });
  });
});
