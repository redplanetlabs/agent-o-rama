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
    
    // Open the metric dropdown (using Downshift pattern)
    await firstRubric.getByTestId('metric-dropdown-toggle').click();
    await expect(modal.getByTestId('metric-dropdown-menu')).toBeVisible();
    
    // Search for a metric
    await firstRubric.getByTestId('metric-search-input').fill('help');
    
    // Select first matching metric
    await modal.getByTestId('metric-dropdown-menu').locator('[role="option"]').first().click();
    
    // Toggle required checkbox
    await firstRubric.getByTestId('metric-required-checkbox').check();
    await expect(firstRubric.getByTestId('metric-required-checkbox')).toBeChecked();
    console.log('✓ Added first rubric with required checkbox');

    // Add a second rubric
    await modal.getByTestId('add-rubric-button').click();
    const secondRubric = modal.getByTestId('rubric-1');
    await expect(secondRubric).toBeVisible();
    
    // Select metric for second rubric (not required)
    await secondRubric.getByTestId('metric-dropdown-toggle').click();
    await modal.getByTestId('metric-dropdown-menu').locator('[role="option"]').first().click();
    
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
    await modal.getByTestId('rubric-0').getByTestId('metric-dropdown-toggle').click();
    await modal.getByTestId('metric-dropdown-menu').locator('[role="option"]').first().click();
    await modal.getByTestId('rubric-0').getByTestId('metric-required-checkbox').check();
    
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
    await modal.getByTestId('rubric-0').getByTestId('metric-dropdown-toggle').click();
    await modal.getByTestId('metric-dropdown-menu').locator('[role="option"]').first().click();
    
    await modal.getByTestId('add-rubric-button').click();
    await modal.getByTestId('rubric-1').getByTestId('metric-dropdown-toggle').click();
    await modal.getByTestId('metric-dropdown-menu').locator('[role="option"]').nth(1).click();
    
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
});

