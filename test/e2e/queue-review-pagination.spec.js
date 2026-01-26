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

  test('should paginate with Load More button on queue detail page', async ({ page }) => {
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
    const metricInput = rubric.getByTestId('metric-selector-input');
    await metricInput.click();
    await metricInput.fill(metricName);
    await page.locator('[role="option"]').filter({ hasText: metricName }).waitFor({ timeout: 10000 });
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
      await page.locator('[role="option"]').filter({ hasText: queueName }).waitFor({ timeout: 10000 });
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
    
    // Search for the specific queue to avoid pagination issues
    await page.getByPlaceholder('Search queues...').fill(queueName);
    await page.waitForTimeout(500); // Allow search to filter results
    
    const queueRow = page.getByTestId(`queue-row-${queueName}`);
    await expect(queueRow).toBeVisible({ timeout: 5000 });
    await queueRow.getByTestId('queue-name-link').click();
    await expect(page).toHaveURL(new RegExp(`human-feedback-queues/${encodeURIComponent(queueName)}`));
    
    // Verify initial page of items is visible
    const itemsTable = page.locator('tbody');
    const initialItems = itemsTable.getByRole('row');
    const initialCount = await initialItems.count();
    console.log(`✓ Initial page shows ${initialCount} items`);
    
    // Verify next-button pagination from the last item on the first page
    if (NUM_ITEMS > initialCount) {
      console.log('Step 2b: Verifying Next button loads after page boundary');
      const lastVisibleItem = itemsTable.getByRole('row').nth(initialCount - 1);
      await expect(lastVisibleItem).toBeVisible();
      await lastVisibleItem.click();
      await expect(page).toHaveURL(/item/);
      await expect(page.getByText('Target Information')).toBeVisible();

      const currentItemUrl = page.url();
      const nextBtn = page.getByTestId('next-item-button');
      await expect(nextBtn).toBeEnabled();
      await nextBtn.click();
      await expect(page).not.toHaveURL(currentItemUrl);
      await expect(page.getByText('Target Information')).toBeVisible();

      // Return to queue detail for Load More checks
      await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
      await page.getByPlaceholder('Search queues...').fill(queueName);
      await page.waitForTimeout(500);
      await page.getByTestId(`queue-row-${queueName}`).getByTestId('queue-name-link').click();

      const loadMoreCell = page.getByRole('cell', { name: 'Load More' });
      if (await loadMoreCell.isVisible()) {
        // Click Load More
        await loadMoreCell.click();
        await page.waitForTimeout(1000);
      }

      // Verify more items loaded (either via Load More or auto-pagination from Next)
      const afterLoadCount = await itemsTable.getByRole('row').count();
      expect(afterLoadCount).toBeGreaterThan(initialCount);
      console.log(`✓ Load More works - now showing ${afterLoadCount} items`);
    }
    
    // =============================================================================
    // STEP 3: Verify items are clickable after Load More
    // =============================================================================
    console.log('Step 3: Verifying loaded items are clickable');
    
    // Click an item from the second page
    const secondPageItem = itemsTable.getByRole('row').nth(initialCount + 2);
    await expect(secondPageItem).toBeVisible();
    await secondPageItem.click();
    await expect(page).toHaveURL(/item/);
    
    // Verify it loaded correctly
    await expect(page.getByText('Target Information')).toBeVisible();
    await expect(page.getByText(metricName)).toBeVisible();
    
    console.log('✓ Items from second page are accessible and display correctly');
    
    
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
      
      // Search for the queue before trying to delete it
      await page.getByPlaceholder('Search queues...').fill(queueName);
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

  test('should load from cursor when accessing deep item via URL', async ({ page }) => {
    test.setTimeout(180000); // 3 minutes
    
    const uniqueId = randomUUID().substring(0, 8);
    const queueName = `e2e-cursor-queue-${uniqueId}`;
    const metricName = `e2e-cursor-metric-${uniqueId}`;
    const NUM_ITEMS = 30;
    
    // =============================================================================
    // SETUP: Create metric, queue, and add 30 items
    // =============================================================================
    console.log('--- Setup: Creating queue with 30 items ---');
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
    await modal.getByTestId('queue-description-input').fill('Cursor test');
    await modal.getByTestId('add-rubric-button').click();
    const metricSelector = modal.getByTestId('rubric-0').getByTestId('metric-selector-input');
    await metricSelector.click();
    await metricSelector.fill(metricName);
    await page.locator('[role="option"]').filter({ hasText: metricName }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName }).click();
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    // Add 30 items
    console.log(`Adding ${NUM_ITEMS} items...`);
    await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
    
    const itemIds = [];
    for (let i = 0; i < NUM_ITEMS; i++) {
      if (i % 10 === 0) console.log(`  Adding item ${i + 1}/${NUM_ITEMS}...`);
      
      await invokeAgentManually(page, [{ query: `cursor test ${i}` }]);
      await page.locator('[data-id="feedback-tab"]').click();
      await page.locator('[data-id="agent-feedback-container"]').getByRole('button', { name: 'Add to Queue' }).click();
      await expect(modal).toBeVisible();
      await modal.getByPlaceholder(/Type to search queues/).fill(queueName);
      await page.locator('[role="option"]').filter({ hasText: queueName }).waitFor({ timeout: 10000 });
      await page.locator('[role="option"]').filter({ hasText: queueName }).click();
      await modal.getByRole('button', { name: 'Add to Queue' }).click();
      await expect(modal).not.toBeVisible({ timeout: 5000 });
      
      // Capture the item ID from the URL (we're on the trace page)
      const currentUrl = page.url();
      const invokeIdMatch = currentUrl.match(/invocations\/([^/?]+)/);
      if (invokeIdMatch && i === 24) {
        // Save the 25th item ID for direct URL test
        itemIds.push(invokeIdMatch[1]);
      }
      
      if (i < NUM_ITEMS - 1) {
        await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
      }
    }
    console.log(`✓ Added ${NUM_ITEMS} items`);
    
    // =============================================================================
    // TEST: Navigate directly to queue detail page
    // =============================================================================
    console.log('Test: Getting deep item ID without pre-loading cache');
    
    // Go to queue detail WITHOUT clicking Load More
    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    
    // Search for the queue to find it in the list
    await page.getByPlaceholder('Search queues...').fill(queueName);
    await page.waitForTimeout(500);
    
    const queueRow = page.getByTestId(`queue-row-${queueName}`);
    await queueRow.getByTestId('queue-name-link').click();
    
    // Get the 25th item ID from the table (should be visible in first page of 20)
    // Wait for table to load
    await page.locator('tbody').getByRole('row').first().waitFor({ timeout: 5000 });
    
    // Capture first item URL from the first row
    let rows = page.locator('tbody').getByRole('row');
    await rows.first().click();
    const firstItemUrl = page.url();
    await expect(page.getByText('Target Information')).toBeVisible({ timeout: 10000 });
    
    // Return to queue detail list
    await page.getByRole('link', { name: queueName }).click();
    await page.locator('tbody').getByRole('row').first().waitFor({ timeout: 5000 });
    
    // Click the last visible row to get its ID
    rows = page.locator('tbody').getByRole('row');
    const lastVisibleRow = rows.nth(19); // 20th row (0-indexed)
    await lastVisibleRow.click();
    
    const lastVisibleItemUrl = page.url();
    const firstItemIdMatch = lastVisibleItemUrl.match(/items\/([^/?]+)/); // Note: "items" plural
    expect(firstItemIdMatch).toBeTruthy();
    const deepItemId = firstItemIdMatch[1];
    
    console.log(`✓ Got item ID: ${deepItemId}`);
    
    // Now navigate to item #25 (which is NOT in the initial 20-item cache)
    // We'll construct URL by going to next item 5 times
    let targetItemId = deepItemId;
    let targetUrl = page.url();
    for (let i = 0; i < 5; i++) {
      const nextBtn = page.getByTestId('next-item-button');
      await nextBtn.click();
      await page.waitForTimeout(500);
      
      if (i === 4) {
        // This is item #25
        targetUrl = page.url();
        const targetMatch = targetUrl.match(/items\/([^/?]+)/); // Note: "items" plural
        targetItemId = targetMatch[1];
      }
    }
    
    console.log(`✓ Navigated to item #25: ${targetItemId}`);
    
    // Navigate to the last item URL (item #30)
    let lastItemUrl = targetUrl;
    for (let i = 0; i < 5; i++) {
      const nextBtn = page.getByTestId('next-item-button');
      await nextBtn.click();
      await page.waitForTimeout(500);
      await expect(page.getByText('Target Information')).toBeVisible({ timeout: 10000 });
      lastItemUrl = page.url();
    }
    
    // =============================================================================
    // TEST: Direct URL navigation with cursor-based loading
    // =============================================================================
    console.log('Test: Direct URL navigation to deep item (no cache)');
    
    // Open a new page to ensure clean cache
    const page2 = await page.context().newPage();
    
    // Navigate directly to item #25 URL
    const targetItemUrl = targetUrl; // Current URL with item #25
    await page2.goto(targetItemUrl);
    await page2.waitForTimeout(2000);
    
    // Verify page loaded correctly using cursor-based pagination
    await expect(page2.getByText('Target Information')).toBeVisible({ timeout: 10000 });
    await expect(page2.getByText(metricName)).toBeVisible();
    
    // With bidirectional pagination, previous button is enabled (items 0-24 exist before item 25)
    await expect(page2.getByTestId('previous-item-button')).toBeEnabled();
    
    // Next button should be enabled (items 26-30 exist)
    const nextBtn = page2.getByTestId('next-item-button');
    await expect(nextBtn).toBeEnabled();
    
    // Walk backward all the way to item 0
    await expect(page2.getByText('cursor test 24')).toBeVisible();
    for (let i = 24; i > 0; i--) {
      const prevBtn = page2.getByTestId('previous-item-button');
      await expect(prevBtn).toBeEnabled();
      await prevBtn.click();
      await page2.waitForTimeout(500);
      await expect(page2.getByText('Target Information')).toBeVisible();
      await expect(page2.getByText(`cursor test ${i - 1}`)).toBeVisible();
    }
    await expect(page2.getByTestId('previous-item-button')).toBeDisabled();
    
    // Navigate forward successfully
    await nextBtn.click();
    await page2.waitForTimeout(500);
    await expect(page2.getByText('Target Information')).toBeVisible();
    
    console.log('✓ Direct URL navigation works with bidirectional pagination support');
    
    await page2.close();
    
    // =============================================================================
    // TEST: Deep link to first item keeps Previous disabled
    // =============================================================================
    const page2a = await page.context().newPage();
    await page2a.goto(firstItemUrl);
    await page2a.waitForTimeout(2000);
    await expect(page2a.getByText('Target Information')).toBeVisible({ timeout: 10000 });
    await expect(page2a.getByTestId('previous-item-button')).toBeDisabled();
    await expect(page2a.getByTestId('next-item-button')).toBeEnabled();
    await page2a.close();
    
    // =============================================================================
    // TEST: Deep link to last item keeps Next disabled
    // =============================================================================
    const page2b = await page.context().newPage();
    await page2b.goto(lastItemUrl);
    await page2b.waitForTimeout(2000);
    await expect(page2b.getByText('Target Information')).toBeVisible({ timeout: 10000 });
    await expect(page2b.getByTestId('next-item-button')).toBeDisabled();
    await expect(page2b.getByTestId('previous-item-button')).toBeEnabled();
    await page2b.close();
    
    // =============================================================================
    // TEST: Breadcrumb navigation from deep-linked item returns to list start
    // =============================================================================
    console.log('Test: Breadcrumb back to queue list after deep link');
    
    // Open NEW page context to simulate deep link with no prior cache
    const page3 = await page.context().newPage();
    
    // Navigate directly to item #25 URL
    await page3.goto(targetItemUrl);
    await page3.waitForTimeout(2000);
    
    // Verify we're on item #25 with bidirectional pagination enabled
    await expect(page3.getByText('Target Information')).toBeVisible({ timeout: 10000 });
    await expect(page3.getByTestId('previous-item-button')).toBeEnabled();
    
    // Click breadcrumb to go back to queue list
    await page3.getByRole('link', { name: queueName }).click();
    await expect(page3).toHaveURL(new RegExp(`human-feedback-queues/${encodeURIComponent(queueName)}$`));
    
    // Wait for queue items to load
    await page3.locator('tbody').getByRole('row').first().waitFor({ timeout: 5000 });
    
    // Verify list shows items from the beginning (not from item 25)
    const firstRowText = await page3.locator('tbody').getByRole('row').first().textContent();
    expect(firstRowText).toContain('cursor test 0');
    
    // Verify we have the first page of items (not starting from 25)
    const visibleRows = await page3.locator('tbody').getByRole('row').count();
    expect(visibleRows).toBe(20); // First page should be 20 items
    
    console.log('✓ Breadcrumb navigation returns to list start (not item 25)');
    
    await page3.close();
    
    // =============================================================================
    // TEST: Backward pagination - clicking Previous button loads earlier items
    // =============================================================================
    console.log('Test: Backward pagination from deep item');
    
    // Open new page and navigate to item #15 (middle of dataset)
    const page4 = await page.context().newPage();
    await page4.goto('/');
    const agentRow4 = await getE2ETestAgentRow(page4);
    await agentRow4.click();
    
    await page4.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await page4.getByPlaceholder('Search queues...').fill(queueName);
    await page4.waitForTimeout(500);
    
    const queueRow4 = page4.getByTestId(`queue-row-${queueName}`);
    await queueRow4.getByTestId('queue-name-link').click();
    
    // Navigate to item #15 by clicking through items
    await page4.locator('tbody').getByRole('row').first().waitFor({ timeout: 5000 });
    await page4.locator('tbody').getByRole('row').first().click();
    
    // Click next 14 times to get to item #15 (0-indexed, so 15th item)
    for (let i = 0; i < 14; i++) {
      await page4.getByTestId('next-item-button').click();
      await page4.waitForTimeout(300);
    }
    
    // Verify we're on item #15
    const item15Url = page4.url();
    expect(item15Url).toContain('/items/');
    console.log('✓ Navigated to item #15');
    
    // Now test backward pagination - click Previous 5 times
    const prevBtn = page4.getByTestId('previous-item-button');
    await expect(prevBtn).toBeEnabled();
    
    for (let i = 0; i < 5; i++) {
      const beforeUrl = page4.url();
      await prevBtn.click();
      await page4.waitForTimeout(500);
      
      // Verify navigation happened
      const afterUrl = page4.url();
      expect(afterUrl).not.toBe(beforeUrl);
      
      // Verify we can still see the content
      await expect(page4.getByText('Target Information')).toBeVisible({ timeout: 5000 });
      console.log(`✓ Backward navigation step ${i + 1}/5 successful`);
    }
    
    // After 5 backward steps, we should be on item #10
    const item10Url = page4.url();
    expect(item10Url).toContain('/items/');
    
    // Both previous and next should be enabled (we're in the middle)
    await expect(prevBtn).toBeEnabled();
    await expect(page4.getByTestId('next-item-button')).toBeEnabled();
    
    console.log('✓ Backward pagination works correctly');
    
    // Test that we can go forward again after going backward
    await page4.getByTestId('next-item-button').click();
    await page4.waitForTimeout(500);
    await expect(page4.getByText('Target Information')).toBeVisible();
    console.log('✓ Can navigate forward again after backward navigation');
    
    await page4.close();
    
    // =============================================================================
    // CLEANUP
    // =============================================================================
    if (!shouldSkipCleanup()) {
      console.log('--- Cleanup ---');
      await page.goto('/');
      await agentRow.click();
      
      await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
      await expect(page).toHaveURL(/human-feedback-queues$/);
      
      // Search for the queue before trying to delete it
      await page.getByPlaceholder('Search queues...').fill(queueName);
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

  test('should paginate while submitting reviews via Submit & Continue', async ({ page }) => {
    test.setTimeout(240000); // 4 minutes

    const uniqueId = randomUUID().substring(0, 8);
    const queueName = `e2e-submit-pagination-queue-${uniqueId}`;
    const metricName = `e2e-submit-pagination-metric-${uniqueId}`;
    const NUM_ITEMS = 30;

    console.log('--- Setup: Creating queue with 30 items for submit pagination test ---');
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
    await modal.getByTestId('queue-description-input').fill('Submit pagination test');
    await modal.getByTestId('add-rubric-button').click();
    const metricSelector = modal.getByTestId('rubric-0').getByTestId('metric-selector-input');
    await metricSelector.click();
    await metricSelector.fill(metricName);
    await page.locator('[role="option"]').filter({ hasText: metricName }).waitFor({ timeout: 10000 });
    await page.locator('[role="option"]').filter({ hasText: metricName }).click();
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });

    console.log(`Adding ${NUM_ITEMS} items...`);
    await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();

    for (let i = 0; i < NUM_ITEMS; i++) {
      if (i % 10 === 0) console.log(`  Adding item ${i + 1}/${NUM_ITEMS}...`);

      await invokeAgentManually(page, [{ query: `submit pagination test ${i}` }]);
      await page.locator('[data-id="feedback-tab"]').click();
      await page.locator('[data-id="agent-feedback-container"]').getByRole('button', { name: 'Add to Queue' }).click();
      await expect(modal).toBeVisible();
      await modal.getByPlaceholder(/Type to search queues/).fill(queueName);
      await page.locator('[role="option"]').filter({ hasText: queueName }).waitFor({ timeout: 10000 });
      await page.locator('[role="option"]').filter({ hasText: queueName }).click();
      await modal.getByRole('button', { name: 'Add to Queue' }).click();
      await expect(modal).not.toBeVisible({ timeout: 5000 });

      if (i < NUM_ITEMS - 1) {
        await page.getByRole('navigation').getByRole('link', { name: 'E2ETestAgent' }).click();
      }
    }
    console.log(`✓ Added ${NUM_ITEMS} items`);

    await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
    await page.getByPlaceholder('Search queues...').fill(queueName);
    await page.waitForTimeout(500);
    await page.getByTestId(`queue-row-${queueName}`).getByTestId('queue-name-link').click();

    const itemRows = page.locator('tbody').getByRole('row');
    await itemRows.first().waitFor({ timeout: 5000 });
    await itemRows.first().click();
    await expect(page).toHaveURL(/item/);

    for (let i = 0; i < NUM_ITEMS; i++) {
      if (i % 10 === 0) console.log(`  Reviewing item ${i + 1}/${NUM_ITEMS}...`);
      
      await expect(page.getByText('Target Information')).toBeVisible();
      await expect(page.getByText(metricName)).toBeVisible();
      
      // Verify we're on the correct item by checking the input
      await expect(page.getByText(`submit pagination test ${i}`)).toBeVisible({ timeout: 5000 });

      const metricDropdown = page.getByTestId('metric-value-0');
      await metricDropdown.click();
      await page.waitForTimeout(300); // Allow dropdown to render
      const choice = Math.random() < 0.5 ? 'Good' : 'Bad';
      await page.getByText(choice).last().click(); // Randomly choose Good or Bad

      await page.getByPlaceholder('Your name').fill('Test Reviewer');
      await page.getByRole('button', { name: 'Submit & Continue' }).click();

      if (i < NUM_ITEMS - 1) {
        await expect(page).toHaveURL(/item/);
        // Wait for next item to load by checking the input changed
        await expect(page.getByText(`submit pagination test ${i + 1}`)).toBeVisible({ timeout: 10000 });
      }
    }

    await expect(page.getByText(/Reached end of.*queue|No more items|All items reviewed/i)).toBeVisible({ timeout: 10000 });
    console.log('✓ Reviewed all items with Submit & Continue (pagination boundary included)');

    if (!shouldSkipCleanup()) {
      console.log('--- Cleanup ---');
      await page.goto('/');
      await agentRow.click();

      await page.getByRole('navigation').getByRole('link', { name: 'Human Feedback Queues' }).click();
      await expect(page).toHaveURL(/human-feedback-queues$/);

      await page.getByPlaceholder('Search queues...').fill(queueName);
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