import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getBasicAgentRow, getE2ETestAgentRow, shouldSkipCleanup, createHumanMetric, deleteHumanMetric, invokeAgentManually } from './helpers.js';

// =============================================================================
// TEST SUITE: Human Feedback Queues
// =============================================================================

test.describe('Human Feedback Queues', () => {
  const uniqueId = randomUUID().substring(0, 8);
  const queueName1 = `e2e-queue-${uniqueId}-1`;
  const queueName2 = `e2e-queue-${uniqueId}-2`;
  const metricName1 = `e2e-queue-metric-${uniqueId}-1`;
  const metricName2 = `e2e-queue-metric-${uniqueId}-2`;
  
  test.beforeEach(async ({ page }) => {
    console.log('--- Starting Test Setup ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);
    
    const agentRow = await getBasicAgentRow(page);
    await agentRow.click();
    await expect(page).toHaveURL(/BasicAgentModule/);
    
    // Navigate to Human Feedback Queues page
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await expect(page).toHaveURL(/human-feedback-queues/);
    await expect(page.getByRole('heading', { name: 'Human Feedback Queues' })).toBeVisible();
    console.log('Successfully navigated to Human Feedback Queues page');
  });

  test('should handle validation and create queues with rubrics', async ({ page }) => {
    // =============================================================================
    // SETUP: Create test metrics
    // =============================================================================
    console.log('--- Creating test metrics ---');
    await page.getByText('Human Metrics').click();
    await expect(page).toHaveURL(/human-metrics/);
    
    await createHumanMetric(page, {
      name: metricName1,
      type: 'numeric',
      min: 1,
      max: 5
    });
    console.log(`✓ Created metric: ${metricName1}`);
    
    await createHumanMetric(page, {
      name: metricName2,
      type: 'categorical',
      categories: ['Good', 'Bad', 'Average']
    });
    console.log(`✓ Created metric: ${metricName2}`);
    
    // Navigate back to Human Feedback Queues page
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await expect(page).toHaveURL(/human-feedback-queues/);
    
    const modal = page.locator('[role="dialog"]');
    const createButton = modal.getByRole('button', { name: 'Create' });

    // =============================================================================
    // TEST 1: Empty name validation
    // =============================================================================
    console.log('Test 1: Validating empty name error');
    await page.getByTestId('create-queue-button').click();
    await expect(modal).toBeVisible();
    await expect(modal.getByRole('heading', { name: 'Create Human Feedback Queue' })).toBeVisible();
    
    // Create button should be disabled with empty name
    await expect(createButton).toBeDisabled();
    await expect(modal.getByText('This field is required')).toBeVisible();
    console.log('✓ Empty name validation works');

    // =============================================================================
    // TEST 2: At least one rubric required
    // =============================================================================
    console.log('Test 2: Validating at least one rubric required');
    await modal.getByTestId('queue-name-input').fill('test-queue');
    await modal.getByTestId('queue-description-input').fill('Test description');
    
    // Button should still be disabled with no rubrics
    await expect(createButton).toBeDisabled();
    await expect(modal.getByText('At least one rubric is required')).toBeVisible();
    console.log('✓ Rubric validation works');

    // =============================================================================
    // TEST 3: Add rubrics with metric dropdown
    // =============================================================================
    console.log('Test 3: Adding rubrics');
    
    // Click "Add Rubric" button
    await modal.getByTestId('add-rubric-button').click();
    
    // First rubric should appear
    const firstRubric = modal.getByTestId('rubric-0');
    await expect(firstRubric).toBeVisible();
    
    // Click on the metric selector input to open dropdown and search for metric
    const metricInput1 = firstRubric.getByTestId('metric-selector-input');
    await metricInput1.click();
    await metricInput1.fill(metricName1);
    
    // Wait for dropdown to appear (it's portaled to the page level)
    await expect(page.locator('[role="listbox"]')).toBeVisible({ timeout: 10000 });
    
    // Wait for the specific metric to appear and select it
    await page.locator('[role="option"]').filter({ hasText: metricName1 }).waitFor({ timeout: 15000 });
    await page.locator('[role="option"]').filter({ hasText: metricName1 }).click();
    
    // Toggle required checkbox
    await firstRubric.getByTestId('metric-required-checkbox').check();
    await expect(firstRubric.getByTestId('metric-required-checkbox')).toBeChecked();
    console.log('✓ Added first rubric with required checkbox');

    // Add a second rubric
    await modal.getByTestId('add-rubric-button').click();
    const secondRubric = modal.getByTestId('rubric-1');
    await expect(secondRubric).toBeVisible();
    
    // Select metric for second rubric (not required)
    const metricInput2 = secondRubric.getByTestId('metric-selector-input');
    await metricInput2.click();
    await metricInput2.fill(metricName2);
    await page.locator('[role="option"]').filter({ hasText: metricName2 }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName2 }).click();
    
    console.log('✓ Added second rubric');

    // =============================================================================
    // TEST 4: Remove a rubric
    // =============================================================================
    console.log('Test 4: Removing rubric');
    await secondRubric.getByTestId('remove-rubric-button').click();
    await expect(secondRubric).not.toBeVisible();
    console.log('✓ Rubric removed');

    // Close modal to reset
    await modal.getByRole('button', { name: '×' }).click();
    await expect(modal).not.toBeVisible();

    // =============================================================================
    // TEST 5: Create first queue successfully
    // =============================================================================
    console.log('Test 5: Creating first queue');
    await page.getByTestId('create-queue-button').click();
    await expect(modal).toBeVisible();
    
    await modal.getByTestId('queue-name-input').fill(queueName1);
    await modal.getByTestId('queue-description-input').fill('First test queue');
    
    // Add rubric
    await modal.getByTestId('add-rubric-button').click();
    const rubric0 = modal.getByTestId('rubric-0');
    const metricInput = rubric0.getByTestId('metric-selector-input');
    await metricInput.click();
    await metricInput.fill(metricName1);
    await page.locator('[role="option"]').filter({ hasText: metricName1 }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName1 }).click();
    await rubric0.getByTestId('metric-required-checkbox').check();
    
    // Submit
    await createButton.click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    // Search for the queue to ensure it's visible
    await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName1);
    await page.waitForTimeout(500);
    
    // Verify queue appears in table
    const queue1Row = page.getByTestId(`queue-row-${queueName1}`);
    await expect(queue1Row).toBeVisible({ timeout: 5000 });
    await expect(queue1Row.getByTestId('queue-name-link')).toHaveText(queueName1);
    await expect(queue1Row.getByTestId('queue-rubric-count')).toContainText('1');
    console.log(`✓ Successfully created queue: ${queueName1}`);

    // =============================================================================
    // TEST 6: Create second queue with duplicate validation
    // =============================================================================
    console.log('Test 6: Creating second queue with duplicate validation');
    await page.getByTestId('create-queue-button').click();
    await expect(modal).toBeVisible();
    
    await modal.getByTestId('queue-name-input').fill(queueName2);
    await modal.getByTestId('queue-description-input').fill('Second test queue');
    
    // Add two rubrics with the SAME metric (to trigger duplicate validation)
    await modal.getByTestId('add-rubric-button').click();
    const rubric0_2 = modal.getByTestId('rubric-0');
    const metricInput0_2 = rubric0_2.getByTestId('metric-selector-input');
    await metricInput0_2.click();
    await metricInput0_2.fill(metricName1);
    await page.locator('[role="option"]').filter({ hasText: metricName1 }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName1 }).click();
    
    await modal.getByTestId('add-rubric-button').click();
    let rubric1_2 = modal.getByTestId('rubric-1');
    const metricInput1_2 = rubric1_2.getByTestId('metric-selector-input');
    await metricInput1_2.click();
    await metricInput1_2.fill(metricName1);
    await page.locator('[role="option"]').filter({ hasText: metricName1 }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName1 }).click();
    
    // Verify duplicate validation error appears
    await expect(modal.getByText(/Duplicate metrics/)).toBeVisible();
    await expect(createButton).toBeDisabled();
    console.log('✓ Duplicate validation error works');
    
    // Fix the error by removing the second rubric
    await rubric1_2.getByTestId('remove-rubric-button').click();
    await expect(rubric1_2).not.toBeVisible();
    
    // Add second rubric with a DIFFERENT metric
    await modal.getByTestId('add-rubric-button').click();
    rubric1_2 = modal.getByTestId('rubric-1');
    const metricInput1_2_new = rubric1_2.getByTestId('metric-selector-input');
    await metricInput1_2_new.click();
    await metricInput1_2_new.fill(metricName2);
    await page.locator('[role="option"]').filter({ hasText: metricName2 }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName2 }).click();
    
    // Verify button is now enabled
    await expect(createButton).toBeEnabled();
    console.log('✓ Duplicate error resolved with different metric');
    
    await createButton.click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    // Search for the second queue
    await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName2);
    await page.waitForTimeout(500);
    
    const queue2Row = page.getByTestId(`queue-row-${queueName2}`);
    await expect(queue2Row).toBeVisible({ timeout: 5000 });
    await expect(queue2Row.getByTestId('queue-rubric-count')).toContainText('2');
    console.log(`✓ Successfully created queue: ${queueName2}`);

    // =============================================================================
    // TEST 7: Search functionality
    // =============================================================================
    console.log('Test 7: Testing search functionality');
    const searchInput = page.getByTestId('search-queues-input');
    await searchInput.fill(queueName1);
    
    // Wait for debounce
    await page.waitForTimeout(500);
    
    // Should show only first queue
    await expect(queue1Row).toBeVisible();
    // Second queue should be hidden (or not in results)
    const allVisibleRows = page.getByTestId(/^queue-row-/);
    const visibleCount = await allVisibleRows.count();
    expect(visibleCount).toBe(1);
    console.log('✓ Search filtering works');
    
    // Clear search
    await searchInput.clear();
    await page.waitForTimeout(500);

    // =============================================================================
    // TEST 8: Click queue to navigate to detail page
    // =============================================================================
    console.log('Test 8: Navigating to queue detail page');
    await queue1Row.getByTestId('queue-name-link').click();
    await expect(page).toHaveURL(new RegExp(`human-feedback-queues/${encodeURIComponent(queueName1)}`));
    await expect(page.getByRole('heading', { name: queueName1 })).toBeVisible();
    console.log('✓ Queue detail page navigation works');
    
    // Navigate back to queue list
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await expect(page).toHaveURL(/human-feedback-queues$/);

    // =============================================================================
    // TEST 9: Delete queues
    // =============================================================================
    if (!shouldSkipCleanup()) {
      console.log('Test 9: Deleting queues');
      
      // Delete first queue
      page.once('dialog', dialog => dialog.accept());
      await queue1Row.getByTestId('delete-queue-button').click();
      await expect(queue1Row).not.toBeVisible({ timeout: 5000 });
      console.log(`✓ Deleted queue: ${queueName1}`);
      
      // Delete second queue
      page.once('dialog', dialog => dialog.accept());
      await queue2Row.getByTestId('delete-queue-button').click();
      await expect(queue2Row).not.toBeVisible({ timeout: 5000 });
      console.log(`✓ Deleted queue: ${queueName2}`);
      
      // Delete test metrics
      console.log('--- Cleaning up test metrics ---');
      await page.getByText('Human Metrics').click();
      await expect(page).toHaveURL(/human-metrics/);
      await deleteHumanMetric(page, metricName1);
      await deleteHumanMetric(page, metricName2);
      console.log('✓ Deleted test metrics');
      
      console.log('✓ Cleanup complete');
    }
  });

  test('should handle edge cases', async ({ page }) => {
    console.log('--- Testing Edge Cases ---');
    
    // Test empty state message
    const searchInput = page.getByTestId('search-queues-input');
    await searchInput.fill('nonexistent-queue-xyz-999');
    await page.waitForTimeout(500);
    
    const emptyState = page.getByTestId('empty-state');
    await expect(emptyState).toBeVisible();
    await expect(emptyState).toContainText('No queues found');
    
    console.log('✓ Empty state works correctly');
  });

  test('should edit queue', async ({ page }) => {
    const uniqueId = randomUUID().substring(0, 8);
    const queueName = `e2e-edit-queue-${uniqueId}`;
    const metricName1 = `e2e-edit-metric-${uniqueId}-1`;
    const metricName2 = `e2e-edit-metric-${uniqueId}-2`;
    
    // Create test metrics
    console.log('--- Creating test metrics ---');
    await page.getByText('Human Metrics').click();
    await expect(page).toHaveURL(/human-metrics/);
    
    await createHumanMetric(page, {
      name: metricName1,
      type: 'numeric',
      min: 1,
      max: 5
    });
    
    await createHumanMetric(page, {
      name: metricName2,
      type: 'categorical',
      categories: ['Excellent', 'Good', 'Poor']
    });
    
    // Navigate to Human Feedback Queues
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    
    // Create a queue
    console.log('Creating queue to edit');
    await page.getByTestId('create-queue-button').click();
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();
    
    await modal.getByTestId('queue-name-input').fill(queueName);
    await modal.getByTestId('queue-description-input').fill('Original description');
    
    // Add one rubric
    await modal.getByTestId('add-rubric-button').click();
    const rubric = modal.getByTestId('rubric-0');
    const metricInputRubric = rubric.getByTestId('metric-selector-input');
    await metricInputRubric.click();
    await metricInputRubric.fill(metricName1);
    await page.locator('[role="option"]').filter({ hasText: metricName1 }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName1 }).click();
    
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    // Search for the queue to ensure it's visible
    await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName);
    await page.waitForTimeout(500);
    
    // Navigate to queue detail page
    const queueRow = page.getByTestId(`queue-row-${queueName}`);
    await expect(queueRow).toBeVisible({ timeout: 5000 });
    await queueRow.getByTestId('queue-name-link').click();
    await expect(page).toHaveURL(new RegExp(`human-feedback-queues/${encodeURIComponent(queueName)}`));
    
    // Click Edit button
    console.log('Editing queue');
    await page.getByTestId('edit-queue-button').click();
    await expect(modal).toBeVisible();
    await expect(modal.getByRole('heading', { name: 'Edit Human Feedback Queue' })).toBeVisible();
    
    // Verify name field is disabled
    const nameInput = modal.getByTestId('queue-name-input');
    await expect(nameInput).toBeDisabled();
    await expect(nameInput).toHaveValue(queueName);
    
    // Update description
    await modal.getByTestId('queue-description-input').fill('Updated description');
    
    // Remove the first rubric (metricName1)
    const rubric0 = modal.getByTestId('rubric-0');
    await rubric0.getByTestId('remove-rubric-button').click();
    await expect(rubric0).not.toBeVisible();
    console.log('✓ Removed first metric');
    
    // Add a new rubric with metricName2 and mark it as required
    await modal.getByTestId('add-rubric-button').click();
    const newRubric = modal.getByTestId('rubric-0');
    const metricInput2 = newRubric.getByTestId('metric-selector-input');
    await metricInput2.click();
    await metricInput2.fill(metricName2);
    await page.locator('[role="option"]').filter({ hasText: metricName2 }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName2 }).click();
    await newRubric.getByTestId('metric-required-checkbox').check();
    await expect(newRubric.getByTestId('metric-required-checkbox')).toBeChecked();
    console.log('✓ Added new metric with required status');
    
    // Submit update
    await modal.getByRole('button', { name: 'Update' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    // Verify updates
    await expect(page.getByText('Updated description')).toBeVisible();
    await expect(page.getByText(metricName1)).not.toBeVisible(); // Should be removed
    await expect(page.getByText(metricName2)).toBeVisible(); // Should be present
    await expect(page.getByText('Required')).toBeVisible(); // Should show required status
    console.log('✓ Queue updated successfully - removed old metric, added new required metric');
    
    // Cleanup
    if (!shouldSkipCleanup()) {
      console.log('--- Cleanup ---');
      await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
      page.once('dialog', dialog => dialog.accept());
      await queueRow.getByTestId('delete-queue-button').click();
      await expect(queueRow).not.toBeVisible({ timeout: 5000 });
      
      await page.getByText('Human Metrics').click();
      await deleteHumanMetric(page, metricName1);
      await deleteHumanMetric(page, metricName2);
      console.log('✓ Cleanup complete');
    }
  });

  test('should add trace to queue and view item detail', async ({ page }) => {
    const uniqueId = randomUUID().substring(0, 8);
    const queueName = `e2e-trace-queue-${uniqueId}`;
    const metricName = `e2e-trace-metric-${uniqueId}`;
    const longOutputLineCount = 60;
    const longOutput = Array.from({ length: longOutputLineCount }, (_, idx) =>
      `Line ${idx + 1}: ${'x'.repeat(80)}`
    ).join('\n');
    
    // =============================================================================
    // STEP 0: Create a test metric using E2ETestAgent module
    // =============================================================================
    console.log('Step 0: Creating test metric');
    await page.goto('/');
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();
    await expect(page).toHaveURL(/E2ETestAgentModule/);
    
    // Navigate to Human Metrics
    await page.getByText('Human Metrics').click();
    await expect(page).toHaveURL(/human-metrics/);
    
    await createHumanMetric(page, {
      name: metricName,
      type: 'categorical',
      categories: ['Good', 'Bad', 'Average']
    });
    console.log(`✓ Created metric: ${metricName}`);
    
    // =============================================================================
    // STEP 1: Create a queue
    // =============================================================================
    console.log('Step 1: Creating queue for trace test');
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await expect(page).toHaveURL(/human-feedback-queues/);
    
    await page.getByTestId('create-queue-button').click();
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();
    
    await modal.getByTestId('queue-name-input').fill(queueName);
    await modal.getByTestId('queue-description-input').fill('Queue for testing trace addition');
    
    // Add a rubric with our newly created metric
    await modal.getByTestId('add-rubric-button').click();
    const rubric = modal.getByTestId('rubric-0');
    const metricInput = rubric.getByTestId('metric-selector-input');
    await metricInput.click();
    await metricInput.fill(metricName);
    await page.locator('[role="option"]').filter({ hasText: metricName }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName }).click();
    
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    console.log(`✓ Created queue: ${queueName}`);
    
    // =============================================================================
    // STEP 2: Invoke E2ETestAgent to create a trace
    // =============================================================================
    console.log('Step 2: Invoking E2ETestAgent');
    await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
    await expect(page).toHaveURL(/agent\/E2ETestAgent$/);
    
    await invokeAgentManually(page, [{ query: 'test query for queue', "output-value": longOutput }]);
    
    // invokeAgentManually already navigates to the trace page
    await expect(page).toHaveURL(/invocations/);
    console.log('✓ Invoked agent and navigated to trace page');
    
    // =============================================================================
    // STEP 3: Add agent invocation to queue
    // =============================================================================
    console.log('Step 3: Adding agent invocation to queue');
    
    // Click the Feedback tab in the agent panel
    await page.locator('[data-id="feedback-tab"]').click();
    
    // Click "Add to Queue" button in agent feedback panel
    const agentFeedbackPanel = page.locator('[data-id="agent-feedback-container"]');
    await agentFeedbackPanel.getByRole('button', { name: 'Add to Queue' }).click();
    
    // Select queue from modal using searchable selector
    await expect(modal).toBeVisible();
    await modal.getByPlaceholder(/Type to search queues/).fill(queueName);
    await page.locator('[role="option"]').filter({ hasText: queueName }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: queueName }).click();
    
    // Click "Add to Queue" submit button
    await modal.getByRole('button', { name: 'Add to Queue' }).click();
    await expect(modal).not.toBeVisible({ timeout: 5000 });
    console.log('✓ Added agent invocation to queue');
    
    // =============================================================================
    // STEP 4: Add node invocation to queue
    // =============================================================================
    console.log('Step 4: Adding node invocation to queue');
    
    // Select a node (processing_node)
    await page.locator('[data-id]').filter({ hasText: 'processing_node' }).first().click();
    
    // Click the Feedback tab in the node panel
    await page.locator('[data-id="node-feedback-tab"]').click();
    
    // Click "Add to Queue" button in node feedback panel
    const nodeFeedbackPanel = page.locator('[data-id="node-feedback-container"]');
    await nodeFeedbackPanel.getByRole('button', { name: 'Add to Queue' }).click();
    
    // Select queue from modal using searchable selector
    await expect(modal).toBeVisible();
    await modal.getByPlaceholder(/Type to search queues/).fill(queueName);
    await page.locator('[role="option"]').filter({ hasText: queueName }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: queueName }).click();
    
    // Click "Add to Queue" submit button
    await modal.getByRole('button', { name: 'Add to Queue' }).click();
    await expect(modal).not.toBeVisible({ timeout: 5000 });
    console.log('✓ Added node invocation to queue');
    
    // =============================================================================
    // STEP 5: Navigate to queue and verify items
    // =============================================================================
    console.log('Step 5: Verifying queue items');
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    
    // Search for the queue to ensure it's visible
    await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName);
    await page.waitForTimeout(500);
    
    const queueRow = page.getByTestId(`queue-row-${queueName}`);
    await expect(queueRow).toBeVisible({ timeout: 5000 });
    await queueRow.getByTestId('queue-name-link').click();
    
    // Verify 2 items are in the queue (check table body rows)
    const itemRows = page.locator('tbody').getByRole('row');
    await expect(itemRows).toHaveCount(2, { timeout: 5000 });
    console.log('✓ Queue shows 2 items');
    
    const longOutputRow = itemRows.filter({ hasText: 'test query for queue' }).first();
    await expect(longOutputRow).toBeVisible({ timeout: 5000 });
    
    const outputCell = longOutputRow.locator('td').nth(3);
    await expect(outputCell).toContainText('Line 1:');
    const outputPreview = outputCell.locator('div').first();
    const truncateInfo = await outputPreview.evaluate((el) => ({
      clientWidth: el.clientWidth,
      scrollWidth: el.scrollWidth,
      overflow: getComputedStyle(el).overflow,
      textOverflow: getComputedStyle(el).textOverflow,
      whiteSpace: getComputedStyle(el).whiteSpace
    }));
    expect(truncateInfo.textOverflow).toBe('ellipsis');
    expect(truncateInfo.whiteSpace).toBe('nowrap');
    expect(truncateInfo.overflow).toBe('hidden');
    expect(truncateInfo.scrollWidth).toBeGreaterThan(truncateInfo.clientWidth);
    console.log('✓ Output column truncates long content');
    
    // =============================================================================
    // STEP 6: Review first item (agent invocation)
    // =============================================================================
    console.log('Step 6: Reviewing first item (agent invocation)');
    
    // Click on first item row to view detail
    await itemRows.first().click();
    await expect(page).toHaveURL(/item/);
    
    // Verify item detail page elements
    await expect(page.getByText('Target Information')).toBeVisible();
    await expect(page.getByText('Input')).toBeVisible();
    await expect(page.locator('[data-id="item-output"]').getByRole('heading', { name: 'Output' })).toBeVisible();
    await expect(page.getByText(metricName)).toBeVisible();
    
    // Fill out the review form
    const metricDropdown = page.getByTestId('metric-value-0');
    await metricDropdown.click();
    await page.getByText('Good').click();
    
    // Add optional comment
    await page.getByPlaceholder(/Add any additional notes/).fill('Test review for agent invocation');
    
    // Fill in reviewer name (required)
    await page.getByPlaceholder('Your name').fill('Test Reviewer');
    
    // Submit review - this navigates to the next item automatically
    await page.getByRole('button', { name: 'Submit & Continue' }).click();
    await page.waitForTimeout(1000);
    console.log('✓ Reviewed agent invocation');
    
    // =============================================================================
    // STEP 7: Review second item (node invocation) - auto-navigated here
    // =============================================================================
    console.log('Step 7: Reviewing second item (node invocation)');
    
    // Should already be on the next item's review page
    await expect(page).toHaveURL(/item/);
    await expect(page.getByText('Target Information')).toBeVisible();
    
    // Fill out the review form
    const metricDropdown2 = page.getByTestId('metric-value-0');
    await metricDropdown2.click();
    await page.getByText('Average').click();
    
    await page.getByPlaceholder(/Add any additional notes/).fill('Test review for node invocation');
    
    // Fill in reviewer name (should be pre-filled from previous review)
    await page.getByPlaceholder('Your name').fill('Test Reviewer');
    
    // Submit review - should show "Reached end of queue" message
    await page.getByRole('button', { name: 'Submit & Continue' }).click();
    await page.waitForTimeout(1000);
    
    // Verify "Reached end of queue" message appears
    await expect(page.getByText(/Reached end of.*queue|No more items|All items reviewed/i)).toBeVisible({ timeout: 5000 });
    console.log('✓ Reviewed node invocation - reached end of queue');
    
    // Navigate back and verify queue is empty
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName);
    await page.waitForTimeout(500);
    const queueRowFinal = page.getByTestId(`queue-row-${queueName}`);
    await queueRowFinal.getByTestId('queue-name-link').click();
    await expect(page.getByText('No items in this queue yet')).toBeVisible();
    console.log('✓ Queue is now empty after reviewing all items');
    
    // =============================================================================
    // CLEANUP: Delete queue and metric
    // =============================================================================
    if (!shouldSkipCleanup()) {
      console.log('Cleanup: Deleting test queue and metric');
      await page.goto('/');
      await agentRow.click();
      
      // Delete queue
      await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
      await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName);
      await page.waitForTimeout(500);
      const queueRowForDelete = page.getByTestId(`queue-row-${queueName}`);
      await expect(queueRowForDelete).toBeVisible({ timeout: 5000 });
      page.once('dialog', dialog => dialog.accept());
      await queueRowForDelete.getByTestId('delete-queue-button').click();
      await expect(queueRowForDelete).not.toBeVisible({ timeout: 5000 });
      console.log('✓ Deleted queue');
      
      // Delete metric
      await page.getByText('Human Metrics').click();
      await deleteHumanMetric(page, metricName);
      console.log('✓ Cleanup complete');
    }
  });

  test('should handle queue item navigation and dismiss', async ({ page }) => {
    const uniqueId = randomUUID().substring(0, 8);
    const queueName = `e2e-nav-queue-${uniqueId}`;
    const metricName = `e2e-nav-metric-${uniqueId}`;
    
    // =============================================================================
    // SETUP: Create metric, queue, and add 3 items to queue
    // =============================================================================
    console.log('--- Setup: Creating metric and queue ---');
    await page.goto('/');
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();
    
    // Create metric
    await page.getByText('Human Metrics').click();
    await createHumanMetric(page, {
      name: metricName,
      type: 'categorical',
      categories: ['Good', 'Bad']
    });
    
    // Create queue
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await page.getByTestId('create-queue-button').click();
    const modal = page.locator('[role="dialog"]');
    await modal.getByTestId('queue-name-input').fill(queueName);
    await modal.getByTestId('queue-description-input').fill('Navigation test queue');
    await modal.getByTestId('add-rubric-button').click();
    const metricInputNav = modal.getByTestId('rubric-0').getByTestId('metric-selector-input');
    await metricInputNav.click();
    await metricInputNav.fill(metricName);
    await page.locator('[role="option"]').filter({ hasText: metricName }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName }).click();
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    // Add 3 invocations to queue
    console.log('Adding 3 items to queue');
    await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
    
    for (let i = 0; i < 3; i++) {
      await invokeAgentManually(page, [{ query: `test query ${i}`, "output-value": `nav output ${i}` }]);
      await page.locator('[data-id="feedback-tab"]').click();
      await page.locator('[data-id="agent-feedback-container"]').getByRole('button', { name: 'Add to Queue' }).click();
      await expect(modal).toBeVisible();
      await modal.getByPlaceholder(/Type to search queues/).fill(queueName);
      await page.locator('[role="option"]').filter({ hasText: queueName }).waitFor({ timeout: 10000 });
      await page.locator('[role="option"]').filter({ hasText: queueName }).click();
      await modal.getByRole('button', { name: 'Add to Queue' }).click();
      await expect(modal).not.toBeVisible({ timeout: 5000 });
      
      if (i < 2) {
        await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
      }
    }
    console.log('✓ Added 3 items to queue');
    
    // =============================================================================
    // TEST 1: Navigate to queue and test Previous/Next buttons
    // =============================================================================
    console.log('Test 1: Testing Previous/Next navigation');
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName);
    await page.waitForTimeout(500);
    const queueRow = page.getByTestId(`queue-row-${queueName}`);
    await queueRow.getByTestId('queue-name-link').click();
    
    // Click first item
    const firstItem = page.locator('tbody').getByRole('row').first();
    await firstItem.click();
    await expect(page).toHaveURL(/item/);
    
    // Previous button should be disabled (we're on first item)
    const prevButton = page.getByTestId('previous-item-button');
    await expect(prevButton).toBeDisabled();
    
    // Next button should be enabled
    const nextButton = page.getByTestId('next-item-button');
    await expect(nextButton).toBeEnabled();
    
    // Click Next to go to second item
    await nextButton.click();
    await page.waitForTimeout(500);
    
    // Now both buttons should be enabled (we're in the middle)
    await expect(prevButton).toBeEnabled();
    await expect(nextButton).toBeEnabled();
    console.log('✓ Previous/Next buttons work correctly');
    
    // Go back to first item
    await prevButton.click();
    await page.waitForTimeout(500);
    await expect(prevButton).toBeDisabled();
    console.log('✓ Previous button navigation works');
    
    // =============================================================================
    // TEST 2: Test Dismiss button
    // =============================================================================
    console.log('Test 2: Testing Dismiss button');
    
    // Click Dismiss
    page.once('dialog', dialog => {
      expect(dialog.message()).toMatch(/dismiss|remove/i);
      dialog.accept();
    });
    await page.getByRole('button', { name: 'Dismiss' }).click();
    
    // Should navigate to next item or back to queue
    await page.waitForTimeout(1000);
    
    // Verify we moved to next item (2 items should remain)
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName);
    await page.waitForTimeout(500);
    await queueRow.getByTestId('queue-name-link').click();
    const remainingItems = page.locator('tbody').getByRole('row');
    await expect(remainingItems).toHaveCount(2);
    console.log('✓ Dismiss button removes item from queue');
    
    // =============================================================================
    // CLEANUP
    // =============================================================================
    if (!shouldSkipCleanup()) {
      console.log('--- Cleanup ---');
      
      // Navigate back to queue list page first
      await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
      await expect(page).toHaveURL(/human-feedback-queues$/);
      await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName);
      await page.waitForTimeout(500);
      
      const queueRowForDelete = page.getByTestId(`queue-row-${queueName}`);
      await expect(queueRowForDelete).toBeVisible({ timeout: 5000 });
      
      page.once('dialog', dialog => dialog.accept());
      await queueRowForDelete.getByTestId('delete-queue-button').click();
      await expect(queueRowForDelete).not.toBeVisible({ timeout: 5000 });
      
      await page.getByText('Human Metrics').click();
      await deleteHumanMetric(page, metricName);
      console.log('✓ Cleanup complete');
    }
  });

  test('should clear form when dismissing a queue item', async ({ page }) => {
    const uniqueId = randomUUID().substring(0, 8);
    const queueName = `e2e-dismiss-clear-${uniqueId}`;
    const metricName = `e2e-dismiss-metric-${uniqueId}`;

    // =============================================================================
    // SETUP: Create metric, queue, and add 2 items
    // =============================================================================
    console.log('--- Setup: Creating metric and queue ---');
    await page.goto('/');
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();

    await page.getByText('Human Metrics').click();
    await createHumanMetric(page, {
      name: metricName,
      type: 'categorical',
      categories: ['Good', 'Bad']
    });

    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await page.getByTestId('create-queue-button').click();
    const modal = page.locator('[role="dialog"]');
    await modal.getByTestId('queue-name-input').fill(queueName);
    await modal.getByTestId('queue-description-input').fill('Dismiss clear test queue');
    await modal.getByTestId('add-rubric-button').click();
    const metricInput = modal.getByTestId('rubric-0').getByTestId('metric-selector-input');
    await metricInput.click();
    await metricInput.fill(metricName);
    await page.locator('[role="option"]').filter({ hasText: metricName }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName }).click();
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });

    // Add 2 items to the queue
    console.log('Adding 2 items to queue');
    await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
    for (let i = 0; i < 2; i++) {
      await invokeAgentManually(page, [{ query: `dismiss test ${i}`, 'output-value': `output ${i}` }]);
      await page.locator('[data-id="feedback-tab"]').click();
      await page.locator('[data-id="agent-feedback-container"]').getByRole('button', { name: 'Add to Queue' }).click();
      await expect(modal).toBeVisible();
      await modal.getByPlaceholder(/Type to search queues/).fill(queueName);
      await page.locator('[role="option"]').filter({ hasText: queueName }).waitFor({ timeout: 10000 });
      await page.locator('[role="option"]').filter({ hasText: queueName }).click();
      await modal.getByRole('button', { name: 'Add to Queue' }).click();
      await expect(modal).not.toBeVisible({ timeout: 5000 });
      if (i < 1) {
        await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
      }
    }
    console.log('✓ Added 2 items to queue');

    // =============================================================================
    // TEST: Fill out form on first item, dismiss, verify form is cleared on next item
    // =============================================================================
    console.log('Test: Fill form, dismiss, verify form cleared');
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName);
    await page.waitForTimeout(500);
    const queueRow = page.getByTestId(`queue-row-${queueName}`);
    await queueRow.getByTestId('queue-name-link').click();

    // Click first item
    await page.locator('tbody').getByRole('row').first().click();
    await expect(page).toHaveURL(/item/);

    // Fill in the metric and comment fields
    const metricDropdown = page.getByTestId('metric-value-0');
    await metricDropdown.click();
    await page.getByText('Good').click();
    // Verify "Good" is now selected
    await expect(metricDropdown).toContainText('Good');

    const commentField = page.getByPlaceholder(/Add any additional notes/);
    await commentField.fill('some review notes');
    await expect(commentField).toHaveValue('some review notes');
    console.log('✓ Filled in metric and comment');

    // Dismiss the item — this should navigate to item 2 and clear the form
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: 'Dismiss' }).click();
    await expect(page).toHaveURL(/item/, { timeout: 10000 });
    console.log('✓ Dismissed item, now on next item page');

    // Verify the form is cleared
    await expect(metricDropdown).toContainText('-- Select --');
    await expect(commentField).toHaveValue('');
    console.log('✓ Form is cleared after dismiss');

    // =============================================================================
    // CLEANUP
    // =============================================================================
    if (!shouldSkipCleanup()) {
      console.log('--- Cleanup ---');
      await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
      await page.getByRole('textbox', { name: /Search queues/ }).fill(queueName);
      await page.waitForTimeout(500);
      const queueRowForDelete = page.getByTestId(`queue-row-${queueName}`);
      await expect(queueRowForDelete).toBeVisible({ timeout: 5000 });
      page.once('dialog', dialog => dialog.accept());
      await queueRowForDelete.getByTestId('delete-queue-button').click();
      await expect(queueRowForDelete).not.toBeVisible({ timeout: 5000 });
      await page.getByText('Human Metrics').click();
      await deleteHumanMetric(page, metricName);
      console.log('✓ Cleanup complete');
    }
  });

});

