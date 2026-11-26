import { defineConfig, devices } from '@playwright/test';

const browserChannel = process.env.PLAYWRIGHT_CHANNEL || (process.platform === 'win32' ? 'msedge' : undefined);

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  // The specs share one isolated application and database; keep browser load bounded by default.
  workers: Number(process.env.PLAYWRIGHT_WORKERS || 1),
  retries: 0,
  reporter: [['list']],
  use: {
    actionTimeout: 10_000,
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:8080',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    ...(browserChannel ? { channel: browserChannel } : {}),
    ...devices['Desktop Chrome'],
  },
});
