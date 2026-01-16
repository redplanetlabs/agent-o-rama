import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getE2ETestAgentRow, shouldSkipCleanup, createHumanMetric, deleteHumanMetric, invokeAgentManually } from './helpers.js';

// =============================================================================
// TEST SUITE: Human Feedback Queue Item Review Pagination
// =============================================================================

test.describe('Queue Review Pagination', () => {
  const uniqueId = randomUUID().substring(0, 8);
  const queueName = `e2e-pagination-queue-${uniqueId}`;
  const metricName = `e2e-pagination-metric-${uniqueId}`;
  const NUM_ITEMS = 25; // More than one page worth of items

  test('should handle pagination when reviewing queue items', async ({ page }) => {
    test.setTimeout(180000); // 3 minutes - creating 25 items takes time
    
    // =============================================================================
    // SETUP: Create metric and queue
    // =============================================================================
    console.log('--- Setup: Creating metric and queue ---');
    await page.goto('/');
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();
    await expect(page).toHaveURL(/E2ETestAgentModule/);
    
    // Create metric
    await page.getByText('Human Metrics').click();
    await expect(page).toHaveURL(/human-metrics/);
    
    await createHumanMetric(page, {
      name: metricName,
      type: 'categorical',
      categories: ['Pass', 'Fail']
    });
    console.log(`✓ Created metric: ${metricName}`);
    
    // Create queue
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await expect(page).toHaveURL(/human-feedback-queues/);
    
    await page.getByTestId('create-queue-button').click();
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();
    
    await modal.getByTestId('queue-name-input').fill(queueName);
    await modal.getByTestId('queue-description-input').fill('Pagination test queue');
    
    await modal.getByTestId('add-rubric-button').click();
    const rubric = modal.getByTestId('rubric-0');
    await rubric.getByTestId('metric-selector-input').click();
    await page.locator('[role="option"]').filter({ hasText: metricName }).click();
    
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    console.log(`✓ Created queue: ${queueName}`);
    
    // =============================================================================
    // STEP 1: Add 25 items to the queue
    // =============================================================================
    console.log(`Step 1: Adding ${NUM_ITEMS} items to queue (this will take a while)...`);
    await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
    await expect(page).toHaveURL(/agent\/E2ETestAgent$/);
    
    for (let i = 0; i < NUM_ITEMS; i++) {
      if (i % 5 === 0) {
        console.log(`  Adding item ${i + 1}/${NUM_ITEMS}...`);
      }
      
      await invokeAgentManually(page, [{ query: `pagination test ${i}` }]);
      
      // Add to queue
      await page.locator('[data-id="feedback-tab"]').click();
      await page.locator('[data-id="agent-feedback-container"]').getByRole('button', { name: 'Add to Queue' }).click();
      await expect(modal).toBeVisible();
      await modal.getByPlaceholder(/Type to search queues/).fill(queueName);
      await page.locator('[role="option"]').filter({ hasText: queueName }).click();
      await modal.getByRole('button', { name: 'Add to Queue' }).click();
      await expect(modal).not.toBeVisible({ timeout: 5000 });
      
      // Navigate back to agent page for next iteration
      if (i < NUM_ITEMS - 1) {
        await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
      }
    }
    console.log(`✓ Added ${NUM_ITEMS} items to queue`);
    
    // =============================================================================
    // STEP 2: Navigate to queue and verify pagination on queue list
    // =============================================================================
    console.log('Step 2: Verifying queue list shows pagination');
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    
    const queueRow = page.getByTestId(`queue-row-${queueName}`);
    await expect(queueRow).toBeVisible({ timeout: 5000 });
    await queueRow.getByTestId('queue-name-link').click();
    await expect(page).toHaveURL(new RegExp(`human-feedback-queues/${encodeURIComponent(queueName)}`));
    
    // Verify initial page of items is visible
    const itemsTable = page.locator('tbody');
    const initialItems = itemsTable.getByRole('row');
    const initialCount = await initialItems.count();
    console.log(`✓ Initial page shows ${initialCount} items`);
    
    // Verify Load More exists (if there are more than one page)
    if (NUM_ITEMS > initialCount) {
      const loadMoreCell = page.getByRole('cell', { name: 'Load More' });
      await expect(loadMoreCell).toBeVisible();
      
      // Click Load More
      await loadMoreCell.click();
      await page.waitForTimeout(1000);
      
      // Verify more items loaded
      const afterLoadCount = await itemsTable.getByRole('row').count();
      expect(afterLoadCount).toBeGreaterThan(initialCount);
      console.log(`✓ Load More works - now showing ${afterLoadCount} items`);
    }
    
    // =============================================================================
    // STEP 3: Test reviewing beyond initial page boundary
    // =============================================================================
    console.log('Step 3: Testing review workflow across page boundary');
    
    // Click first item to start review
    await itemsTable.getByRole('row').first().click();
    await expect(page).toHaveURL(/item/);
    
    // Review items up to and past the initial page boundary
    // This tests that pagination automatically loads more items during review
    const numToReview = initialCount + 5; // Go past the first page
    
    for (let i = 0; i < numToReview; i++) {
      if (i % 5 === 0) {
        console.log(`  Reviewing item ${i + 1}/${numToReview} (initial page had ${initialCount} items)...`);
      }
      
      // Verify we're on an item review page
      await expect(page.getByText('Target Information')).toBeVisible();
      
      // Quick review - just fill required fields
      await page.getByTestId('metric-value-0').click();
      await page.getByText('Pass').click();
      await page.getByPlaceholder('Your name').fill('Pagination Tester');
      
      // Submit and continue
      await page.getByRole('button', { name: 'Submit & Continue' }).click();
      await page.waitForTimeout(800);
      
      // CRITICAL: After reviewing item #initialCount, we should automatically
      // load the next page and continue to item #(initialCount+1) seamlessly
      if (i === initialCount - 1) {
        console.log(`  ✓ Crossed page boundary! Now on item ${i + 2} (beyond initial ${initialCount})`);
      }
    }
    
    console.log(`✓ Successfully reviewed ${numToReview} items across page boundary`);
    console.log('✓ Pagination automatically loads more items during review');
    
    // Verify we can still navigate back (including to items from previous page)
    const prevButton = page.getByTestId('previous-item-button');
    
    // Click Previous several times to go back across page boundary
    for (let i = 0; i < 3; i++) {
      await expect(prevButton).toBeEnabled();
      await prevButton.click();
      await page.waitForTimeout(500);
      await expect(page.getByText('Target Information')).toBeVisible();
    }
    console.log('✓ Previous button works across page boundaries');
    
    // =============================================================================
    // STEP 4: Test URL refresh on a deep item (cursor-based loading)
    // =============================================================================
    console.log('Step 4: Testing URL refresh with cursor-based pagination');
    
    // Navigate to queue list
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    const queueRow = page.getByTestId(`queue-row-${queueName}`);
    await queueRow.getByTestId('queue-name-link').click();
    
    // Load all items on the queue page
    let loadMoreCell = page.getByRole('cell', { name: 'Load More' });
    while (await loadMoreCell.isVisible()) {
      await loadMoreCell.click();
      await page.waitForTimeout(1000);
      loadMoreCell = page.getByRole('cell', { name: 'Load More' });
    }
    
    // Get a deep item (should have at least initialCount + numToReview items remaining)
    // Use item #15 (should be safe - we added 25 and reviewed ~15)
    const deepItemRow = page.locator('tbody').getByRole('row').nth(14); // 0-indexed
    await expect(deepItemRow).toBeVisible();
    await deepItemRow.click();
    
    // Get current URL to use for refresh test
    const currentUrl = page.url();
    const itemIdMatch = currentUrl.match(/item\/([^/?]+)/);
    expect(itemIdMatch).toBeTruthy(); // Fail test if URL format unexpected
    
    const deepItemId = itemIdMatch[1];
    console.log(`✓ Navigated to deep item #15: ${deepItemId}`);
    
    // Refresh the page
    await page.reload();
    await page.waitForTimeout(1500);
    
    // Verify page loaded correctly with cursor-based pagination
    await expect(page.getByText('Target Information')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(metricName)).toBeVisible();
    
    // Previous button should be disabled (loaded from cursor, no earlier items)
    await expect(page.getByTestId('previous-item-button')).toBeDisabled();
    
    // Next button should be enabled (more items available)
    const nextBtn = page.getByTestId('next-item-button');
    await expect(nextBtn).toBeEnabled();
    
    // Can navigate forward
    await nextBtn.click();
    await page.waitForTimeout(500);
    await expect(page.getByText('Target Information')).toBeVisible();
    
    console.log('✓ URL refresh works - loaded from cursor, Previous disabled, Next enabled');
    
    // =============================================================================
    // CLEANUP: Delete queue and metric
    // =============================================================================
    if (!shouldSkipCleanup()) {
      console.log('--- Cleanup ---');
      await page.goto('/');
      await agentRow.click();
      
      // Delete queue
      await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
      await expect(page).toHaveURL(/human-feedback-queues$/);
      
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
});
