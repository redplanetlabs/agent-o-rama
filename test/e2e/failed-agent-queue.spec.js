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
  
  test('should display FAILED for failed agent output in queue', async ({ page }) => {
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
    await rubric.getByTestId('metric-selector-input').click();
    await page.locator('[role="option"]').first().waitFor({ timeout: 15000 });
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
    
    const queueRow = page.getByTestId(`queue-row-${queueName}`);
    await expect(queueRow).toBeVisible({ timeout: 10000 });
    await queueRow.getByTestId('queue-name-link').click();
    
    // Wait for queue detail page
    await expect(page).toHaveURL(new RegExp(`human-feedback-queues/${encodeURIComponent(queueName)}`));
    
    // Poll for item to appear - rule processing can take a few seconds
    const itemRows = page.locator('tbody').getByRole('row');
    
    // Retry up to 30 times with full page navigation if queue is empty
    const queueDetailUrl = page.url();
    for (let attempt = 1; attempt <= 30; attempt++) {
      console.log(`  Checking for queue items (attempt ${attempt}/30)...`);
      
    await page.waitForTimeout(2000);
    await page.goto(queueDetailUrl);
      
      // Check if we have items
      const hasItems = await itemRows.first().isVisible().catch(() => false);
      if (hasItems) {
        console.log('✓ Queue has items');
        break;
      }
      
      // If empty and not last attempt, navigate to force fresh fetch
      if (attempt < 30) {
        console.log('  Queue empty, reloading via navigation...');
      } else {
        // Last attempt - fail with assertion
        await expect(itemRows.first()).toBeVisible({ timeout: 5000 });
      }
    }
    
    // Verify the output column shows "FAILED" (not the raw JSON)
    // Use .first() since there may be multiple items in queue
    const failedText = page.locator('td').getByText('FAILED', { exact: true }).first();
    await expect(failedText).toBeVisible({ timeout: 5000 });
    console.log('✓ Output shows "FAILED" in queue table');
    
    // =============================================================================
    // STEP 8: Click into item detail and verify FAILED display
    // =============================================================================
    console.log('Step 8: Verifying FAILED in item detail view');
    await itemRows.first().click();
    await expect(page).toHaveURL(/item/);
    
    // Find the Output section and verify it shows FAILED
    const outputSection = page.locator('[data-id="item-output"]');
    await expect(outputSection).toBeVisible();
    await expect(outputSection.getByText('FAILED')).toBeVisible();
    console.log('✓ Output shows "FAILED" in item detail view');
    
    // Verify it's styled in red
    const failedElement = outputSection.locator('.text-red-600, .text-red-500').filter({ hasText: 'FAILED' });
    await expect(failedElement).toBeVisible();
    console.log('✓ FAILED text is styled in red');
    
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
  
  test('should show successful agent output normally (not FAILED)', async ({ page }) => {
    // This test verifies that successful agents still show their output normally
    // (contrasting with the FAILED display)
    
    console.log('Step 1: Navigating to E2ETestAgent module');
    await page.goto('/');
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();
    
    // Create metric and queue
    console.log('Step 2: Creating metric and queue');
    await page.getByRole('link', { name: 'Human Metrics' }).click();
    
    const successMetricName = `e2e-success-metric-${uniqueId}`;
    const successQueueName = `e2e-success-queue-${uniqueId}`;
    
    await createHumanMetric(page, {
      name: successMetricName,
      type: 'categorical',
      categories: ['Good', 'Bad']
    });
    
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await page.getByTestId('create-queue-button').click();
    const modal = page.locator('[role="dialog"]');
    await modal.getByTestId('queue-name-input').fill(successQueueName);
    await modal.getByTestId('queue-description-input').fill('Queue for successful traces');
    await modal.getByTestId('add-rubric-button').click();
    await modal.getByTestId('rubric-0').getByTestId('metric-selector-input').click();
    await page.locator('[role="option"]').filter({ hasText: successMetricName }).click();
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    // Invoke successful agent and manually add to queue
    console.log('Step 3: Invoking successful agent');
    await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
    
    // Use params that make the agent succeed with a specific output
    const successArgs = [{
      "query": "test successful query",
      "output-value": ["success", "result", 123]  // This will be the agent output
    }];
    
    await invokeAgentManually(page, successArgs);
    
    // Wait for agent to complete successfully
    await page.waitForTimeout(3000);
    
    // Manually add to queue
    console.log('Step 4: Adding successful trace to queue');
    await page.locator('[data-id="feedback-tab"]').click();
    const agentFeedbackPanel = page.locator('[data-id="agent-feedback-container"]');
    await agentFeedbackPanel.getByRole('button', { name: 'Add to Queue' }).click();
    await expect(modal).toBeVisible();
    await modal.getByPlaceholder(/Type to search queues/).fill(successQueueName);
    await page.locator('[role="option"]').filter({ hasText: successQueueName }).click();
    await modal.getByRole('button', { name: 'Add to Queue' }).click();
    await expect(modal).not.toBeVisible({ timeout: 5000 });
    
    // Verify output shows the actual value, not "FAILED"
    console.log('Step 5: Verifying successful output in queue');
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    const queueRow = page.getByTestId(`queue-row-${successQueueName}`);
    await queueRow.getByTestId('queue-name-link').click();
    
    const itemRows = page.locator('tbody').getByRole('row');
    await expect(itemRows.first()).toBeVisible({ timeout: 10000 });
    
    // Should NOT show FAILED
    const failedText = page.locator('td').getByText('FAILED', { exact: true });
    await expect(failedText).not.toBeVisible();
    
    // Should show the actual output value (contains "success")
    await expect(page.locator('td').filter({ hasText: /success/ })).toBeVisible();
    console.log('✓ Successful output shows value, not FAILED');
    
    // Cleanup
    if (!shouldSkipCleanup()) {
      console.log('--- Cleanup ---');
      await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
      const queueRowForDelete = page.getByTestId(`queue-row-${successQueueName}`);
      page.once('dialog', dialog => dialog.accept());
      await queueRowForDelete.getByTestId('delete-queue-button').click();
      await expect(queueRowForDelete).not.toBeVisible({ timeout: 5000 });
      
      await page.getByRole('link', { name: 'Human Metrics' }).click();
      await deleteHumanMetric(page, successMetricName);
      console.log('✓ Cleanup complete');
    }
  });
});
