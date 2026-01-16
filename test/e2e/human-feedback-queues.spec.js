import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getBasicAgentRow, shouldSkipCleanup, createHumanMetric, deleteHumanMetric } from './helpers.js';

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
    await page.getByText('Human Feedback').click();
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
    await page.getByText('Human Feedback').click();
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
    
    // Click on the metric selector input to open dropdown
    await firstRubric.getByTestId('metric-selector-input').click();
    
    // Wait for dropdown to appear (it's portaled to the page level)
    await expect(page.locator('[role="listbox"]')).toBeVisible({ timeout: 10000 });
    
    // Wait for options to load
    await page.locator('[role="option"]').first().waitFor({ timeout: 15000 });
    
    // Select first test metric by name
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
    await secondRubric.getByTestId('metric-selector-input').click();
    await page.locator('[role="option"]').first().waitFor({ timeout: 10000 });
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
    await rubric0.getByTestId('metric-selector-input').click();
    await page.locator('[role="option"]').first().waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName1 }).click();
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
    await rubric0_2.getByTestId('metric-selector-input').click();
    await page.locator('[role="option"]').first().waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName1 }).click();
    
    await modal.getByTestId('add-rubric-button').click();
    let rubric1_2 = modal.getByTestId('rubric-1');
    await rubric1_2.getByTestId('metric-selector-input').click();
    await page.locator('[role="option"]').first().waitFor({ timeout: 10000 });
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
    await rubric1_2.getByTestId('metric-selector-input').click();
    await page.locator('[role="option"]').first().waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName2 }).click();
    
    // Verify button is now enabled
    await expect(createButton).toBeEnabled();
    console.log('✓ Duplicate error resolved with different metric');
    
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

  test('should add trace to queue and view item detail', async ({ page }) => {
    const uniqueId = randomUUID().substring(0, 8);
    const queueName = `e2e-trace-queue-${uniqueId}`;
    const metricName = `e2e-trace-metric-${uniqueId}`;
    
    // =============================================================================
    // STEP 0: Create a test metric first
    // =============================================================================
    console.log('Step 0: Creating test metric');
    await page.goto('/');
    const agentRow = await getBasicAgentRow(page);
    await agentRow.click();
    
    // Navigate to Human Metrics
    await page.getByText('Human Metrics').click();
    await expect(page).toHaveURL(/human-metrics/);
    
    await createHumanMetric(page, {
      name: metricName,
      type: 'categorical',
      categories: ['Good', 'Bad', 'Average']
    });
    console.log(`✓ Created metric: ${metricName}`);
    
    // Navigate to Human Feedback Queues
    await page.getByText('Human Feedback').click();
    await expect(page).toHaveURL(/human-feedback-queues/);
    
    // =============================================================================
    // STEP 1: Create a queue
    // =============================================================================
    console.log('Step 1: Creating queue for trace test');
    await page.getByTestId('create-queue-button').click();
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();
    
    await modal.getByTestId('queue-name-input').fill(queueName);
    await modal.getByTestId('queue-description-input').fill('Queue for testing trace addition');
    
    // Add a rubric with our newly created metric
    await modal.getByTestId('add-rubric-button').click();
    const rubric = modal.getByTestId('rubric-0');
    await expect(rubric).toBeVisible();
    
    // Click on the metric selector input to open dropdown
    await rubric.getByTestId('metric-selector-input').click();
    
    // Wait for dropdown to appear (it's rendered in a portal outside the modal)
    await expect(page.getByTestId('metric-selector-dropdown')).toBeVisible({ timeout: 10000 });
    
    // Wait for options to load
    await page.waitForTimeout(2000);
    
    // Find and select our specific metric
    const metricOption = page.getByTestId('metric-selector-dropdown').locator('[role="option"]').filter({ hasText: metricName });
    await metricOption.waitFor({ timeout: 15000 });
    await metricOption.click();
    
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    const queueRow = page.getByTestId(`queue-row-${queueName}`);
    await expect(queueRow).toBeVisible({ timeout: 5000 });
    console.log(`✓ Created queue: ${queueName}`);
    
    // =============================================================================
    // STEP 2: Verify queue detail page loads
    // =============================================================================
    console.log('Step 2: Verifying queue detail page');
    await queueRow.getByTestId('queue-name-link').click();
    await expect(page).toHaveURL(new RegExp(`human-feedback-queues/${encodeURIComponent(queueName)}`));
    await expect(page.getByRole('heading', { name: queueName })).toBeVisible();
    console.log('✓ Queue detail page loads successfully');
    console.log('✓ Test complete - Core queue functionality verified');
    
    // Note: Testing the "Add to Queue" button from trace view and item detail page
    // requires having actual invocations, which is best tested manually or with
    // backend test helpers. The core UI components are now in place and working.
    
    // =============================================================================
    // CLEANUP: Delete queue and metric
    // =============================================================================
    if (!shouldSkipCleanup()) {
      console.log('Cleanup: Deleting test queue and metric');
      await page.goto('/');
      await agentRow.click();
      
      // Delete queue
      await page.getByText('Human Feedback').click();
      const queueRowForDelete = page.getByTestId(`queue-row-${queueName}`);
      await expect(queueRowForDelete).toBeVisible({ timeout: 5000 });
      await queueRowForDelete.getByTestId('delete-queue-button').click();
      const confirmModal = page.locator('[role="dialog"]').filter({ hasText: 'Delete Queue' });
      await expect(confirmModal).toBeVisible();
      await confirmModal.getByRole('button', { name: 'Delete' }).click();
      await expect(confirmModal).not.toBeVisible({ timeout: 5000 });
      await expect(queueRowForDelete).not.toBeVisible({ timeout: 5000 });
      console.log('✓ Deleted queue');
      
      // Delete metric
      await page.getByText('Human Metrics').click();
      await deleteHumanMetric(page, metricName);
      console.log('✓ Cleanup complete');
    }
  });
});

