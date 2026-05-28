// test/e2e/trace-rendering.spec.js
import { test, expect } from '@playwright/test';

/**
 * GanttStressAgent: 30-way agg + nested BranchAgent with 10 leaf invokes each.
 * @param {import('@playwright/test').Page} page
 */
async function getGanttStressAgentRow(page) {
  const moduleNs = 'com.rpl.agent.gantt-stress-agent';
  const moduleName = 'GanttStressModule';
  const agentName = 'GanttStressAgent';

  const agentRow = page.getByRole('row', { name: `${moduleNs}/${moduleName} ${agentName}` });

  await expect(agentRow).toBeVisible({ timeout: 120000 });
  console.log(`Found agent: ${moduleNs}/${moduleName}:${agentName}`);

  return agentRow;
}

test.describe('Invocation Trace Page Rendering', () => {
  // Gantt stress run can take tens of seconds (many nested invokes).
  test.setTimeout(240 * 1000);

  test('should render final result and Gantt timeline for nested agg stress agent', async ({ page }) => {
    console.log('--- Starting Gantt stress trace test ---');

    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getGanttStressAgentRow(page);
    await agentRow.click();

    const agentDetailUrlRegex =
      /agents\/.*com\.rpl\.agent\.gantt-stress-agent.*GanttStressModule\/agent\/GanttStressAgent/;
    await expect(page).toHaveURL(agentDetailUrlRegex);
    console.log('Navigated to GanttStressAgent detail page.');

    const seed = Math.floor(Math.random() * 1_000_000_000);
    console.log(`Running GanttStressAgent with seed: ${seed}`);
    const manualRunForm = page.locator('div').filter({ hasText: /^Manually Run Agent/ });
    await manualRunForm.getByPlaceholder(/\[arg1, arg2, arg3, ...\]/).fill(JSON.stringify([seed]));
    await manualRunForm.getByRole('button', { name: 'Submit' }).click();

    await expect(page).toHaveURL(/\/invocations\//, { timeout: 120000 });
    console.log('On invocation trace page.');

    const finalResultHeader = page.getByText('Final Result', { exact: true });
    await expect(finalResultHeader).toBeVisible({ timeout: 120000 });
    console.log('Final Result panel is visible.');

    const finalResultPanel = page.locator('[data-id="final-result-section"]');
    await expect(finalResultPanel.locator('pre').filter({ hasText: /gantt-stress-done/ })).toBeVisible({
      timeout: 120000,
    });
    await expect(finalResultPanel.locator('pre').filter({ hasText: new RegExp(String(seed)) })).toBeVisible();

    const successBadge = page.locator('.bg-green-100.text-green-800').filter({ hasText: 'Success' });
    await expect(successBadge).toBeVisible();

    await page.getByTestId('trace-view-gantt').click();
    const gantt = page.getByTestId('gantt-trace-view');
    await expect(gantt).toBeVisible({ timeout: 30000 });
    await expect(gantt).toContainText('stress-fanout');
    await expect(gantt).toContainText('stress-worker');

    await page.getByTestId('trace-view-graph').click();
    await expect(page.locator('.react-flow')).toBeVisible();

    console.log('--- Gantt stress trace test passed. ---');
  });
});
