// test/e2e/trace-rendering.spec.js
import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';

/**
 * Helper to get the BasicAgent row.
 * We'll make it specific to avoid dependencies on helpers for more complex agents.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<import('@playwright/test').Locator>}
 */
async function getBasicAgentRow(page) {
  const moduleNs = 'com.rpl.agent.basic.basic-agent';
  const moduleName = 'BasicAgentModule';
  const agentName = 'BasicAgent';

  // Use getByRole with the exact name to avoid matching multiple rows
  const agentRow = page.getByRole('row', { name: `${moduleNs}/${moduleName} ${agentName}` });
  
  // Wait up to 30 seconds for agents to appear on first load.
  await expect(agentRow).toBeVisible({ timeout: 30000 });
  console.log(`Found agent: ${moduleNs}/${moduleName}:${agentName}`);
  
  return agentRow;
}


test.describe('Invocation Trace Page Rendering', () => {
  
  // Increase the timeout for this specific test as it involves an agent run.
  test.setTimeout(120 * 1000); // 2 minutes

  test('should correctly display the final result panel for a fast-completing agent without a manual refresh', async ({ page }) => {
    console.log('--- Starting Fast Agent Trace Rendering Test ---');

    // --- 1. SETUP: Navigate to the agent detail page ---
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getBasicAgentRow(page);
    await agentRow.click();
    
    const agentDetailUrlRegex = /agents\/.*com\.rpl\.agent\.basic\.basic-agent.*BasicAgentModule\/agent\/BasicAgent/;
    await expect(page).toHaveURL(agentDetailUrlRegex);
    console.log('Successfully navigated to BasicAgent detail page.');
    
    // --- 2. ACTION: Manually run the agent ---
    const uniqueInput = `e2e-test-${randomUUID().substring(0, 8)}`;
    const expectedResult = `Welcome to agent-o-rama, ${uniqueInput}!`;

    console.log(`Running agent with input: "${uniqueInput}"`);
    const manualRunForm = page.locator('div').filter({ hasText: /^Manually Run Agent/ });
    
    // The BasicAgent expects a single string argument, so we wrap it in a JSON array.
    await manualRunForm.getByPlaceholder(/\[arg1, arg2, arg3, ...\]/).fill(JSON.stringify([uniqueInput]));
    await manualRunForm.getByRole('button', { name: 'Submit' }).click();

    // --- 3. ASSERTION: Verify the trace page renders the result correctly ---
    
    // 3a. Wait for navigation to the invocation trace page.
    await expect(page).toHaveURL(/\/invocations\//, { timeout: 30000 });
    console.log('Navigated to invocation trace page.');

    // 3b. **This is the key assertion.** We wait for the "Final Result" panel to be visible.
    // This will directly test if the race condition is fixed. If the panel doesn't appear,
    // the test will fail here. We give it a generous timeout to allow for network and processing.
    // Use getByText to target the specific header element with "Final Result"
    const finalResultHeader = page.getByText('Final Result', { exact: true });
    await expect(finalResultHeader).toBeVisible({ timeout: 30000 });
    console.log('Final Result panel is visible.');

    // 3c. Verify the content of the final result panel.
    // The result is displayed in the generic-data-viewer, which renders the content as text.
    // We scope the search to the Final Result panel and check the <pre> element specifically.
    const finalResultPanel = page.locator('div').filter({ hasText: 'Final Result' });
    await expect(finalResultPanel.locator('pre').filter({ hasText: expectedResult })).toBeVisible();
    console.log('Final Result content is correct.');

    // 3d. Additionally, check for the "Success" badge to be sure.
    const successBadge = page.locator('.bg-green-100.text-green-800').filter({ hasText: 'Success' });
    await expect(successBadge).toBeVisible();
    console.log('Success badge is visible.');
    
    console.log('--- Test successfully verified trace page renders final result immediately. ---');
  });
});