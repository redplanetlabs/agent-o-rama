import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getE2ETestAgentRow, shouldSkipCleanup, createHumanMetric, deleteHumanMetric, invokeAgentManually } from './helpers.js';

// =============================================================================
// TEST SUITE: Failed Agent Traces in Human Feedback Queue
// =============================================================================

test.describe('Failed Agent Traces in Human Feedback Queue', () => {
  const uniqueId = randomUUID().substring(0, 8);
  const queueName = `e2e-failed-queue-${uniqueId}`;
  const metricName = `e2e-failed-metric-${uniqueId}`;
  const ruleName = `e2e-failed-rule-${uniqueId}`;
  
  // Increase timeout - this test involves rule processing which can take time
  test('should display failed agent error in red', { timeout: 120000 }, async ({ page }) => {
    // =============================================================================
    // STEP 1: Setup - Navigate to E2ETestAgent module
    // =============================================================================
    console.log('Step 1: Navigating to E2ETestAgent module');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);
    
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();
    await expect(page).toHaveURL(/E2ETestAgentModule/);
    console.log('✓ Navigated to E2ETestAgent module');
    
    // =============================================================================
    // STEP 2: Create a human metric
    // =============================================================================
    console.log('Step 2: Creating human metric');
    await page.getByRole('link', { name: 'Human Metrics' }).click();
    await expect(page).toHaveURL(/human-metrics/);
    
    await createHumanMetric(page, {
      name: metricName,
      type: 'categorical',
      categories: ['Recoverable', 'Critical', 'Expected']
    });
    console.log(`✓ Created metric: ${metricName}`);
    
    // =============================================================================
    // STEP 3: Create a human feedback queue
    // =============================================================================
    console.log('Step 3: Creating human feedback queue');
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await expect(page).toHaveURL(/human-feedback-queues/);
    
    await page.getByTestId('create-queue-button').click();
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();
    
    await modal.getByTestId('queue-name-input').fill(queueName);
    await modal.getByTestId('queue-description-input').fill('Queue for failed agent traces');
    
    // Add rubric
    await modal.getByTestId('add-rubric-button').click();
    const rubric = modal.getByTestId('rubric-0');
    const metricInput = rubric.getByTestId('metric-selector-input');
    await metricInput.click();
    await metricInput.fill(metricName);
    await page.locator('[role="option"]').filter({ hasText: metricName }).waitFor({ timeout: 15000 });
    await page.locator('[role="option"]').filter({ hasText: metricName }).click();
    
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    console.log(`✓ Created queue: ${queueName}`);
    
    // =============================================================================
    // STEP 4: Create a rule that triggers on failed agents and adds to queue
    // =============================================================================
    console.log('Step 4: Creating rule to capture failed agent traces');
    await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
    await expect(page).toHaveURL(/agent\/E2ETestAgent$/);
    
    // Click on Rules tab
    await page.getByRole('link', { name: 'Rules' }).click();
    await expect(page).toHaveURL(/rules/);
    
    // Click Add Rule button
    await page.getByRole('button', { name: '+ Add Rule' }).click();
    await expect(modal).toBeVisible();
    
    // Fill rule name
    await modal.locator('[data-id="rule-name"]').fill(ruleName);
    
    // Select "Failure" status filter to only trigger on failures
    const statusSelect = modal.locator('select').filter({ has: page.locator('option[value="fail"]') });
    await statusSelect.selectOption('fail');
    
    // Select action: Add to human feedback queue
    await modal.locator('[data-id="action-selector"]').selectOption('aor/add-to-human-feedback-queue');
    
    // Wait for queue selector to appear and select the queue
    const queueSelector = modal.getByPlaceholder(/Type to search queues/);
    await expect(queueSelector).toBeVisible({ timeout: 5000 });
    // Click to open dropdown, then type to filter
    await queueSelector.click();
    await queueSelector.pressSequentially(queueName, { delay: 50 });
    // Wait for option to appear and click it
    await page.locator('[role="option"]').filter({ hasText: queueName }).waitFor({ timeout: 15000 });
    await page.locator('[role="option"]').filter({ hasText: queueName }).click();
    
    // Submit rule
    await modal.getByRole('button', { name: 'Add Rule' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    // Verify rule appears in table
    await expect(page.locator('table tbody tr').filter({ hasText: ruleName })).toBeVisible({ timeout: 5000 });
    console.log(`✓ Created rule: ${ruleName}`);
    
    // =============================================================================
    // STEP 5: Invoke agent with parameters that will make it fail permanently
    // =============================================================================
    console.log('Step 5: Invoking E2ETestAgent with failure params');
    await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
    await expect(page).toHaveURL(/agent\/E2ETestAgent$/);
    
    // The E2ETestAgent fails when:
    // - fail-at-node is set to a node name
    // - retries-before-success is higher than max retries (default 3)
    // This will cause permanent failure
    const failingArgs = [{
      "run-id": `fail-test-${uniqueId}`,
      "fail-at-node": "start",
      "retries-before-success": 999  // Much higher than default max retries
    }];
    
    await invokeAgentManually(page, failingArgs);
    
    // The agent should fail - wait for the "Failed" status badge in result panel
    // The invocation graph shows "Failed" in a red badge when the agent fails
    await expect(page.locator('[data-id="final-result-section"]').getByText('Failed')).toBeVisible({ timeout: 30000 });
    console.log('✓ Agent invocation failed as expected');
    
    // =============================================================================
    // STEP 6: Wait for rule to process and add to queue
    // =============================================================================
    console.log('Step 6: Waiting for rule to process...');
    // Initial wait for rule processing
    await page.waitForTimeout(3000);
    
    // =============================================================================
    // STEP 7: Navigate to queue and verify item shows FAILED
    // =============================================================================
    console.log('Step 7: Verifying failed trace appears in queue');
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    
    // Search for the queue to ensure it's visible
    await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName);
    await page.waitForTimeout(500);
    
    const queueRow = page.getByTestId(`queue-row-${queueName}`);
    await expect(queueRow).toBeVisible({ timeout: 10000 });
    await queueRow.getByTestId('queue-name-link').click();
    
    // Wait for queue detail page
    await expect(page).toHaveURL(new RegExp(`human-feedback-queues/${encodeURIComponent(queueName)}`));
    
    // Poll for item to appear - rule processing can take a few seconds
    const itemRows = page.locator('tbody').getByRole('row');
    
    // Retry up to 15 times with full page navigation if queue is empty
    const queueDetailUrl = page.url();
    for (let attempt = 1; attempt <= 15; attempt++) {
      console.log(`  Checking for queue items (attempt ${attempt}/15)...`);
      
      await page.waitForTimeout(2000);
      await page.goto(queueDetailUrl);
      
      // Check if we have items
      const hasItems = await itemRows.first().isVisible().catch(() => false);
      if (hasItems) {
        console.log('✓ Queue has items');
        break;
      }
      
      // Last attempt - fail with assertion
      if (attempt === 15) {
        await expect(itemRows.first()).toBeVisible({ timeout: 5000 });
      }
    }
    
    // Verify the output column shows the error message in red (not raw JSON with failure? key)
    // The error message should be "Max retry limit exceeded"
    const errorText = page.locator('td.text-red-600').filter({ hasText: /Max retry limit exceeded/ }).first();
    await expect(errorText).toBeVisible({ timeout: 5000 });
    console.log('✓ Output shows error message in red in queue table');
    
    // =============================================================================
    // STEP 8: Click into item detail and verify failed output display
    // =============================================================================
    console.log('Step 8: Verifying failed output in item detail view');
    await itemRows.first().click();
    await expect(page).toHaveURL(/item/);
    
    // Find the Output section - header should show "Output (Failed)" in red
    const outputSection = page.locator('[data-id="item-output"]');
    await expect(outputSection).toBeVisible();
    await expect(outputSection.getByText('Output (Failed)')).toBeVisible();
    console.log('✓ Output header shows "Output (Failed)" in item detail view');
    
    // Verify the header is styled in red
    const failedHeader = outputSection.locator('h3.text-red-600').filter({ hasText: 'Output (Failed)' });
    await expect(failedHeader).toBeVisible();
    console.log('✓ Failed output header is styled in red');
    
    // =============================================================================
    // CLEANUP
    // =============================================================================
    if (!shouldSkipCleanup()) {
      console.log('--- Cleanup ---');
      
      // Delete rule
      await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
      await page.getByRole('link', { name: 'Rules' }).click();
      await expect(page).toHaveURL(/rules/);
      
      page.once('dialog', dialog => dialog.accept());
      const ruleRow = page.locator('table tbody tr').filter({ hasText: ruleName });
      await ruleRow.getByTitle('Delete rule').click();
      await expect(ruleRow).not.toBeVisible({ timeout: 5000 });
      console.log(`✓ Deleted rule: ${ruleName}`);
      
      // Delete queue
      await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
      await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName);
      await page.waitForTimeout(500);
      const queueRowForDelete = page.getByTestId(`queue-row-${queueName}`);
      await expect(queueRowForDelete).toBeVisible({ timeout: 5000 });
      page.once('dialog', dialog => dialog.accept());
      await queueRowForDelete.getByTestId('delete-queue-button').click();
      await expect(queueRowForDelete).not.toBeVisible({ timeout: 5000 });
      console.log(`✓ Deleted queue: ${queueName}`);
      
      // Delete metric
      await page.getByRole('link', { name: 'Human Metrics' }).click();
      await deleteHumanMetric(page, metricName);
      console.log(`✓ Deleted metric: ${metricName}`);
      
      console.log('✓ Cleanup complete');
    }
  });
});