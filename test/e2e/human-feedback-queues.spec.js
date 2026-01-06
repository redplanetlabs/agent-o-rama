import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getBasicAgentRow, shouldSkipCleanup } from './helpers.js';

// =============================================================================
// TEST SUITE: Human Feedback Queues
// =============================================================================

test.describe('Human Feedback Queues', () => {
  const uniqueId = randomUUID().substring(0, 8);
  const queueName1 = `e2e-queue-${uniqueId}-1`;
  const queueName2 = `e2e-queue-${uniqueId}-2`;
  const testMetricName = `e2e-queue-metric-${uniqueId}`;
  
  test.beforeEach(async ({ page }) => {
    console.log('--- Starting Test Setup ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);
    
    const agentRow = await getBasicAgentRow(page);
    await agentRow.click();
    await expect(page).toHaveURL(/BasicAgentModule/);
    
    // Navigate to Human Feedback Queues page
    await page.getByText('Human Feedback').click();
    await expect(page).toHaveURL(/human-feedback-queues/);
    await expect(page.getByRole('heading', { name: 'Human Feedback Queues' })).toBeVisible();
    console.log('Successfully navigated to Human Feedback Queues page');
  });

  test('should handle validation and create queues with rubrics', async ({ page }) => {
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
    
    // Click on the metric selector input to open dropdown
    await firstRubric.getByTestId('metric-selector-input').click();
    
    // Wait for dropdown to appear
    await expect(firstRubric.getByTestId('metric-selector-dropdown')).toBeVisible({ timeout: 10000 });
    
    // Wait a bit for the query to complete and items to load
    await page.waitForTimeout(2000);
    
    // Check if there's a loading state or if options appeared
    const hasOptions = await firstRubric.getByTestId('metric-selector-dropdown').locator('[role="option"]').count() > 0;
    const hasLoading = await firstRubric.getByTestId('metric-selector-loading').isVisible().catch(() => false);
    const hasEmpty = await firstRubric.getByTestId('metric-selector-empty-state').isVisible().catch(() => false);
    
    console.log(`Options: ${hasOptions ? 'YES' : 'NO'}, Loading: ${hasLoading ? 'YES' : 'NO'}, Empty: ${hasEmpty ? 'YES' : 'NO'}`);
    
    // Wait for options to load
    await firstRubric.getByTestId('metric-selector-dropdown').locator('[role="option"]').first().waitFor({ timeout: 15000 });
    
    // Select first matching metric
    await firstRubric.getByTestId('metric-selector-dropdown').locator('[role="option"]').first().click();
    
    // Toggle required checkbox
    await firstRubric.getByTestId('metric-required-checkbox').check();
    await expect(firstRubric.getByTestId('metric-required-checkbox')).toBeChecked();
    console.log('✓ Added first rubric with required checkbox');

    // Add a second rubric
    await modal.getByTestId('add-rubric-button').click();
    const secondRubric = modal.getByTestId('rubric-1');
    await expect(secondRubric).toBeVisible();
    
    // Select metric for second rubric (not required)
    await secondRubric.getByTestId('metric-selector-input').click();
    await secondRubric.getByTestId('metric-selector-dropdown').locator('[role="option"]').first().waitFor({ timeout: 10000 });
    await secondRubric.getByTestId('metric-selector-dropdown').locator('[role="option"]').first().click();
    
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
    await rubric0.getByTestId('metric-selector-input').click();
    await rubric0.getByTestId('metric-selector-dropdown').locator('[role="option"]').first().waitFor({ timeout: 10000 });
    await rubric0.getByTestId('metric-selector-dropdown').locator('[role="option"]').first().click();
    await rubric0.getByTestId('metric-required-checkbox').check();
    
    // Submit
    await createButton.click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    // Verify queue appears in table
    const queue1Row = page.getByTestId(`queue-row-${queueName1}`);
    await expect(queue1Row).toBeVisible({ timeout: 5000 });
    await expect(queue1Row.getByTestId('queue-name-link')).toHaveText(queueName1);
    await expect(queue1Row.getByTestId('queue-rubric-count')).toContainText('1');
    console.log(`✓ Successfully created queue: ${queueName1}`);

    // =============================================================================
    // TEST 6: Create second queue
    // =============================================================================
    console.log('Test 6: Creating second queue');
    await page.getByTestId('create-queue-button').click();
    await expect(modal).toBeVisible();
    
    await modal.getByTestId('queue-name-input').fill(queueName2);
    await modal.getByTestId('queue-description-input').fill('Second test queue');
    
    // Add two rubrics
    await modal.getByTestId('add-rubric-button').click();
    const rubric0_2 = modal.getByTestId('rubric-0');
    await rubric0_2.getByTestId('metric-selector-input').click();
    await rubric0_2.getByTestId('metric-selector-dropdown').locator('[role="option"]').first().waitFor({ timeout: 10000 });
    await rubric0_2.getByTestId('metric-selector-dropdown').locator('[role="option"]').first().click();
    
    await modal.getByTestId('add-rubric-button').click();
    const rubric1_2 = modal.getByTestId('rubric-1');
    await rubric1_2.getByTestId('metric-selector-input').click();
    await rubric1_2.getByTestId('metric-selector-dropdown').locator('[role="option"]').first().waitFor({ timeout: 10000 });
    await rubric1_2.getByTestId('metric-selector-dropdown').locator('[role="option"]').first().click();
    
    await createButton.click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
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
    await expect(page).toHaveURL(new RegExp(`human-feedback-queues/queue/${encodeURIComponent(queueName1)}`));
    await expect(page.getByRole('heading', { name: queueName1 })).toBeVisible();
    console.log('✓ Queue detail page navigation works');
    
    // Go back to index
    await page.goBack();
    await expect(page).toHaveURL(/human-feedback-queues$/);

    // =============================================================================
    // TEST 9: Delete queues
    // =============================================================================
    if (!shouldSkipCleanup()) {
      console.log('Test 9: Deleting queues');
      
      // Delete first queue
      await queue1Row.getByTestId('delete-queue-button').click();
      const confirmModal = page.locator('[role="dialog"]').filter({ hasText: 'Delete Queue' });
      await expect(confirmModal).toBeVisible();
      await confirmModal.getByRole('button', { name: 'Delete' }).click();
      await expect(confirmModal).not.toBeVisible({ timeout: 5000 });
      await expect(queue1Row).not.toBeVisible({ timeout: 5000 });
      console.log(`✓ Deleted queue: ${queueName1}`);
      
      // Delete second queue
      await queue2Row.getByTestId('delete-queue-button').click();
      await expect(confirmModal).toBeVisible();
      await confirmModal.getByRole('button', { name: 'Delete' }).click();
      await expect(confirmModal).not.toBeVisible({ timeout: 5000 });
      await expect(queue2Row).not.toBeVisible({ timeout: 5000 });
      console.log(`✓ Deleted queue: ${queueName2}`);
      
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

  test('should add trace to queue and view item detail', async ({ page }) => {
    const uniqueId = randomUUID().substring(0, 8);
    const queueName = `e2e-trace-queue-${uniqueId}`;
    
    // =============================================================================
    // STEP 1: Create a queue
    // =============================================================================
    console.log('Step 1: Creating queue for trace test');
    await page.getByTestId('create-queue-button').click();
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();
    
    await modal.getByTestId('queue-name-input').fill(queueName);
    await modal.getByTestId('queue-description-input').fill('Queue for testing trace addition');
    
    // Add a rubric
    await modal.getByTestId('add-rubric-button').click();
    const rubric = modal.getByTestId('rubric-0');
    await rubric.getByTestId('metric-selector-input').click();
    await rubric.getByTestId('metric-selector-dropdown').locator('[role="option"]').first().waitFor({ timeout: 10000 });
    await rubric.getByTestId('metric-selector-dropdown').locator('[role="option"]').first().click();
    
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    const queueRow = page.getByTestId(`queue-row-${queueName}`);
    await expect(queueRow).toBeVisible({ timeout: 5000 });
    console.log(`✓ Created queue: ${queueName}`);
    
    // =============================================================================
    // STEP 2: Navigate to E2E Test Agent and run an agent
    // =============================================================================
    console.log('Step 2: Navigating to E2E Test Agent');
    await page.goto('/');
    await page.getByText('E2ETestAgentModule').first().click();
    await expect(page).toHaveURL(/E2ETestAgentModule/);
    
    // Navigate to E2ETestAgent
    await page.getByText('E2ETestAgent', { exact: true }).click();
    await expect(page).toHaveURL(/E2ETestAgent/);
    
    // Run the agent
    console.log('Running agent invocation...');
    await page.getByTestId('run-agent-button').click();
    await page.getByTestId('agent-args-input').fill('{"input": "test trace for queue"}');
    await page.getByRole('button', { name: 'Run' }).click();
    
    // Wait for invocation to complete
    await page.waitForTimeout(5000);
    
    // Click on the first invocation to view its trace
    const firstInvocation = page.locator('table tbody tr').first();
    await firstInvocation.click();
    
    // Wait for trace page to load
    await expect(page).toHaveURL(/invocations/);
    await page.waitForTimeout(2000);
    console.log('✓ Viewing trace page');
    
    // =============================================================================
    // STEP 3: Add trace to queue using "Add to Queue" button
    // =============================================================================
    console.log('Step 3: Adding trace to queue');
    const addToQueueButton = page.getByRole('button', { name: 'Add to Queue' });
    await expect(addToQueueButton).toBeVisible({ timeout: 5000 });
    await addToQueueButton.click();
    
    // Modal should open
    const addModal = page.locator('[role="dialog"]').filter({ hasText: 'Add to Human Feedback Queue' });
    await expect(addModal).toBeVisible();
    
    // Select the queue from dropdown
    await addModal.getByTestId('queue-selector-input').click();
    await addModal.getByTestId('queue-selector-dropdown').waitFor({ timeout: 10000 });
    
    // Find and click our queue
    const queueOption = addModal.getByTestId('queue-selector-dropdown').locator('[role="option"]').filter({ hasText: queueName });
    await queueOption.waitFor({ timeout: 10000 });
    await queueOption.click();
    
    // Add optional comment
    await addModal.getByTestId('comment-input').fill('Test comment for this trace');
    
    // Submit
    await addModal.getByRole('button', { name: 'Add to Queue' }).click();
    await expect(addModal).not.toBeVisible({ timeout: 10000 });
    console.log('✓ Added trace to queue');
    
    // =============================================================================
    // STEP 4: Navigate back to queue detail page
    // =============================================================================
    console.log('Step 4: Navigating to queue detail page');
    await page.goto('/');
    const agentRow = await getBasicAgentRow(page);
    await agentRow.click();
    await page.getByText('Human Feedback').click();
    await expect(page).toHaveURL(/human-feedback-queues/);
    
    // Click on our queue
    await queueRow.getByTestId('queue-name-link').click();
    await expect(page).toHaveURL(new RegExp(`human-feedback-queues/queue/${encodeURIComponent(queueName)}`));
    await expect(page.getByRole('heading', { name: queueName })).toBeVisible();
    console.log('✓ On queue detail page');
    
    // =============================================================================
    // STEP 5: Verify item appears in queue
    // =============================================================================
    console.log('Step 5: Verifying item in queue');
    await page.waitForTimeout(2000);
    
    // Should have at least one item
    const itemRows = page.locator('table tbody tr');
    const itemCount = await itemRows.count();
    expect(itemCount).toBeGreaterThan(0);
    console.log(`✓ Found ${itemCount} item(s) in queue`);
    
    // =============================================================================
    // STEP 6: Click on item to view detail page
    // =============================================================================
    console.log('Step 6: Viewing item detail page');
    const firstItem = itemRows.first();
    await firstItem.click();
    
    // Should navigate to item detail page
    await expect(page).toHaveURL(new RegExp(`human-feedback-queues/queue/${encodeURIComponent(queueName)}/items/`));
    
    // Verify item detail page elements
    await expect(page.getByText('Input')).toBeVisible();
    await expect(page.getByText('Output')).toBeVisible();
    await expect(page.getByText('Evaluation')).toBeVisible();
    
    // Verify input/output contain data
    const inputSection = page.locator('[data-id="item-input"]');
    const outputSection = page.locator('[data-id="item-output"]');
    await expect(inputSection).toBeVisible();
    await expect(outputSection).toBeVisible();
    
    console.log('✓ Item detail page loaded successfully');
    
    // =============================================================================
    // CLEANUP: Delete queue
    // =============================================================================
    if (!shouldSkipCleanup()) {
      console.log('Cleanup: Deleting test queue');
      await page.goto('/');
      const agentRow = await getBasicAgentRow(page);
      await agentRow.click();
      await page.getByText('Human Feedback').click();
      
      await queueRow.getByTestId('delete-queue-button').click();
      const confirmModal = page.locator('[role="dialog"]').filter({ hasText: 'Delete Queue' });
      await expect(confirmModal).toBeVisible();
      await confirmModal.getByRole('button', { name: 'Delete' }).click();
      await expect(confirmModal).not.toBeVisible({ timeout: 5000 });
      await expect(queueRow).not.toBeVisible({ timeout: 5000 });
      console.log('✓ Cleanup complete');
    }
  });
});

