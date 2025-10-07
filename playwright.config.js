// playwright.config.js
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  // The directory where your E2E test files are located.
  testDir: './test/e2e',

  // Run all tests in parallel for maximum speed.
  fullyParallel: false,

  // In CI, retry failed tests up to 2 times.
  retries: process.env.CI ? 2 : 0,

  // Reporter to use. `html` puts results in a nice folder.
  reporter: 'html',

  use: {
    // The base URL for your dev server. This matches your UI's default port.
    baseURL: 'http://localhost:1974',

    // This is a killer feature: creates a trace file on the first retry of a failed test.
    // This file is a time-travel debugger for your test run.
    trace: 'on-first-retry',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    // You can uncomment these to test against other browsers:
    // {
    //   name: 'firefox',
    //   use: { ...devices['Desktop Firefox'] },
    // },
    // {
    //   name: 'webkit',
    //   use: { ...devices['Desktop Safari'] },
    // },
  ],

  // This is the magic part! It tells Playwright how to start your app.
  webServer: {
    // The exact command to start your Shadow-cljs dev server.
    command: 'lein with-profile +ui run -m shadow.cljs.devtools.cli watch frontend',

    // The URL Playwright will poll to know when the server is ready.
    url: 'http://localhost:1974',

    // If you already have a dev server running, Playwright will use it instead of starting a new one.
    // This is disabled in CI to ensure a clean state.
    reuseExistingServer: !process.env.CI,

    // Give your server up to 2 minutes to start up. Shadow-cljs can sometimes be slow on the first compile.
    timeout: 120 * 1000,

    // Pipe server output to console for debugging.
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
