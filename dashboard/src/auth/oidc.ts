import { UserManager, WebStorageStateStore } from 'oidc-client-ts';
import { rolesFromToken, setAccessTokenSource, type Session } from './session';

/**
 * Authorization Code + PKCE against Keycloak, served under the same origin by the gateway
 * (/realms/...), so no CORS and the issuer the APIs expect. Tokens live in sessionStorage (per
 * tab, gone when it closes) and are renewed silently with the refresh token before they expire.
 */
const manager = new UserManager({
  authority: `${window.location.origin}/realms/fraud`,
  client_id: 'fraud-dashboard',
  redirect_uri: `${window.location.origin}/`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  scope: 'openid profile',
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
});

/** Completes a login redirect, or reuses a stored session; otherwise starts the login (no return). */
export async function establishSession(): Promise<Session | null> {
  const params = new URLSearchParams(window.location.search);
  let user =
    params.has('code') && params.has('state')
      ? await manager.signinRedirectCallback()
      : await manager.getUser();
  if (params.has('code')) window.history.replaceState({}, '', window.location.pathname); // drop code/state

  if (!user || user.expired) {
    user = await manager.signinSilent().catch(() => null);
  }
  if (!user) {
    await manager.signinRedirect();
    return null;
  }

  let current = user;
  manager.events.addUserLoaded((renewed) => {
    current = renewed;
  });
  setAccessTokenSource(() => current.access_token);
  return {
    username: String(user.profile.preferred_username ?? user.profile.sub),
    roles: rolesFromToken(user.access_token),
    signOut: () => void manager.signoutRedirect(),
  };
}
