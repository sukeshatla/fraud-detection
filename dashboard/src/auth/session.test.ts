import { http, HttpResponse } from 'msw';
import { fetchAlertStats } from '../api/client';
import { subscribeToAlerts } from '../api/stream';
import { FakeEventSource } from '../test/fakeEventSource';
import { server } from '../test/server';
import { rolesFromToken, setAccessTokenSource } from './session';

/** A JWT-shaped string with the given payload (unsigned: the UI never verifies, the API does). */
const token = (payload: object) =>
  `e30.${btoa(JSON.stringify(payload)).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '')}.sig`;

describe('session', () => {
  afterEach(() => setAccessTokenSource(() => undefined));

  it('AC-015-03: reads Keycloak realm roles from the access token', () => {
    expect(rolesFromToken(token({ realm_access: { roles: ['ANALYST', 'SUPERVISOR'] } }))).toEqual([
      'ANALYST',
      'SUPERVISOR',
    ]);
    expect(rolesFromToken('not-a-jwt')).toEqual([]);
  });

  it('AC-015-03: API calls carry the CURRENT token (picks up silent renewals)', async () => {
    const seen: (string | null)[] = [];
    server.use(
      http.get('*/api/v1/alerts/stats', ({ request }) => {
        seen.push(request.headers.get('Authorization'));
        return HttpResponse.json({});
      }),
    );
    let current = 'token-1';
    setAccessTokenSource(() => current);

    await fetchAlertStats();
    current = 'token-2';
    await fetchAlertStats();

    expect(seen).toEqual(['Bearer token-1', 'Bearer token-2']);
  });

  it('AC-015-02: the SSE stream sends the token as access_token and reopens with a fresh one after a 401', () => {
    vi.useFakeTimers();
    let current = 'token-1';
    setAccessTokenSource(() => current);

    const unsubscribe = subscribeToAlerts(
      () => {},
      () => {},
    );
    expect(FakeEventSource.latest().url).toBe('/api/v1/alerts/stream?access_token=token-1');

    // the server rejected the (expired) token: EventSource gives up for good
    current = 'token-2';
    FakeEventSource.latest().readyState = FakeEventSource.CLOSED;
    FakeEventSource.latest().emit('error');
    vi.advanceTimersByTime(3_000);

    expect(FakeEventSource.instances).toHaveLength(2);
    expect(FakeEventSource.latest().url).toBe('/api/v1/alerts/stream?access_token=token-2');
    unsubscribe();
    vi.useRealTimers();
  });
});
