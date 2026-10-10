import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { FakeEventSource } from './fakeEventSource';
import { server } from './server';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
beforeEach(() => {
  FakeEventSource.instances = [];
  vi.stubGlobal('EventSource', FakeEventSource);
});
afterEach(() => {
  cleanup();
  server.resetHandlers();
});
afterAll(() => server.close());
