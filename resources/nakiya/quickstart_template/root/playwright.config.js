// @ts-check
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './test/e2e',
  timeout: 30000,
  retries: 0,
  use: {
    baseURL: 'http://localhost:3001',
    headless: true,
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
    },
  ],
  webServer: {
    command: 'clojure -M:e2e 3001',
    url: 'http://localhost:3001/api/health',
    reuseExistingServer: !process.env.CI,
    timeout: 60000,
  },
});
