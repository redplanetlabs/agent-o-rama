import { test, expect } from '@playwright/test';

test.describe('Agent-O-Rama Navigation', () => {

  test('should load the homepage and navigate to an agent detail page', async ({ page }) => {
    // Step 1: Go to the application's base URL.
    await page.goto('/');

    // Step 2: Assert that the page title is correct. This is a good sanity check.
    await expect(page).toHaveTitle(/Agent-o-rama/);

    // Step 3: Wait for an agent link to be visible on the page.
    // The UI fetches this data asynchronously, so Playwright's auto-waiting is essential here.
    // We'll look for an agent from your examples.
    const agentName = 'com.rpl.agent.research-agent/ResearchAgentModule:researcher';
    const agentLink = page.getByText(agentName);

    // Wait up to 30 seconds for the agent to appear. The first load can be slow.
    await expect(agentLink).toBeVisible({ timeout: 30000 });
    console.log(`Found agent: ${agentName}`);

    // Step 4: Click the agent link to navigate.
    await agentLink.click();

    await expect(page).toHaveURL(/\/agents\/.*ResearchAgentModule.*/i);
    console.log('Successfully verified agent detail page.');
  });
});
