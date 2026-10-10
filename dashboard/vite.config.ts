/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// The dev server plays the gateway's role (same port, same routes) so the browser sees one origin,
// http://localhost:8080, which is also the token issuer. Use it with the services run from the IDE;
// with the compose "app" profile the real gateway owns :8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 8080,
    strictPort: true,
    proxy: {
      '/api/v1/alerts': 'http://localhost:8083',
      '/api/v1/accounts': 'http://localhost:8082',
      '/api/v1/transactions': 'http://localhost:8081',
      // Keycloak under the same origin, as behind the gateway (login pages, tokens, JWKS)
      '/realms': 'http://localhost:8180',
      '/resources': 'http://localhost:8180',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
  },
});
