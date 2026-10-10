/** The signed-in user as the UI needs it. Roles come from the access token (Keycloak realm roles). */
export interface Session {
  readonly username: string;
  readonly roles: readonly string[];
  signOut(): void;
}

export const hasRole = (session: Session, role: string) => session.roles.includes(role);

/** Closing an alert (confirm fraud / false positive) is a supervisor decision; the API enforces it too. */
export const canClose = (session: Session) => hasRole(session, 'SUPERVISOR');

/**
 * Where API calls get the current access token. A function (not a value) so a silently renewed
 * token is picked up by the next request. Unset in tests: requests go out without a header.
 */
let tokenSource: () => string | undefined = () => undefined;

export function setAccessTokenSource(source: () => string | undefined) {
  tokenSource = source;
}

export const accessToken = () => tokenSource();

/** Realm roles from a Keycloak access token's payload (the signature is the API's job to verify). */
export function rolesFromToken(token: string): string[] {
  try {
    const payload = token.split('.')[1] ?? '';
    const json = atob(payload.replaceAll('-', '+').replaceAll('_', '/'));
    const claims = JSON.parse(json) as { realm_access?: { roles?: string[] } };
    return claims.realm_access?.roles ?? [];
  } catch {
    return [];
  }
}
