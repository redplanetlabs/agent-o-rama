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
    const agentName = 'com.rpl.agent.fail-agent/RetryTestModule:RetryTestAgent';
    const agentLink = page.getByText(agentName);

    // Wait up to 30 seconds for the agent to appear. The first load can be slow.
    await expect(agentLink).toBeVisible({ timeout: 30000 });
    console.log(`Found agent: ${agentName}`);

    // Step 4: Click the agent link to navigate.
    await agentLink.click();

    // Step 5: Assert that the URL has changed correctly.
    const expectedPath = /.*\/agents\/.*\/agent\/.*\/invocations/;
    await expect(page).toHaveURL(expectedPath);
    console.log(`Navigated to agent detail page. Current URL: ${page.url()}`);

    // Step 6: Assert that the detail page has loaded by checking for a key heading.
    await expect(page.getByRole('heading', { name: 'Agent Invocation Graph' })).toBeVisible();
    
    // Step 7: Assert that the invocations table is visible.
    await expect(page.getByRole('table')).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Invoke ID' })).toBeVisible();
    
    console.log('Successfully verified agent detail page.');
  });
});
