import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getBasicAgentRow, deleteHumanMetric, shouldSkipCleanup } from './helpers.js';

// =============================================================================
// HUMAN METRICS PAGINATION TEST
// Tests that "Load More" pagination works correctly for human metrics
// =============================================================================

const uniqueId = randomUUID().substring(0, 8);

test.describe('Human Metrics Pagination', () => {
  // Increase timeout since this test creates many items
  test.setTimeout(120000); // 2 minutes
  
  test.beforeEach(async ({ page }) => {
    console.log('--- Starting Test Setup ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);
    
    const agentRow = await getBasicAgentRow(page);
    await agentRow.click();
    await expect(page).toHaveURL(/BasicAgentModule/);
    
    // Navigate to Human Metrics page
    await page.getByText('Human Metrics').click();
    await expect(page).toHaveURL(/human-metrics/);
    console.log('--- Test Setup Complete ---');
  });

  test('should paginate human metrics correctly with Load More button', async ({ page }) => {
    console.log('--- Testing Human Metrics Pagination ---');
    
    const metricNames = [];
    const namePrefix = `pagination-test-metric-${uniqueId}`;
    
    console.log('Creating metrics until pagination is triggered...');
    
    let itemCount = 0;
    const loadMoreButton = page.locator('tfoot tr').filter({ hasText: 'Load More' });
    
    // Keep creating until Load More appears (page size is 20)
    // Note: We don't use search filter here so Load More triggers at actual page size
    while (!(await loadMoreButton.isVisible()) && itemCount < 50) {
      itemCount++;
      const name = `${namePrefix}-${String(itemCount).padStart(3, '0')}`;
      metricNames.push(name);
      
      // Alternate between numeric and categorical metrics
      const isNumeric = itemCount % 2 === 0;
      
      await page.getByRole('button', { name: '+ Create Metric' }).click();
      const modal = page.locator('[role="dialog"]');
      await expect(modal).toBeVisible();
      
      await modal.getByLabel('Metric Name').fill(name);
      
      if (isNumeric) {
        await modal.getByRole('combobox').selectOption('numeric');
        await expect(modal.getByLabel('Min')).toBeVisible();
        await modal.getByLabel('Min').fill('1');
        await modal.getByLabel('Max').fill('10');
      } else {
        await modal.getByRole('combobox').selectOption('categorical');
        await expect(modal.getByLabel('Options (comma separated)')).toBeVisible();
        await modal.getByLabel('Options (comma separated)').fill('Good, Bad, Average');
      }
      
      await modal.getByRole('button', { name: 'Create' }).click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });
      
      if (itemCount % 5 === 0) {
        console.log(`Created ${itemCount} metrics, checking for Load More button...`);
        await page.waitForTimeout(300);
      }
    }
    
    // Create additional metrics to ensure there's data on page 2
    const itemsToAddForSecondPage = 5;
    console.log(`Load More appeared at ${itemCount} items. Creating ${itemsToAddForSecondPage} more for second page...`);
    for (let i = 0; i < itemsToAddForSecondPage; i++) {
      itemCount++;
      const name = `${namePrefix}-${String(itemCount).padStart(3, '0')}`;
      metricNames.push(name);
      
      await page.getByRole('button', { name: '+ Create Metric' }).click();
      const modal = page.locator('[role="dialog"]');
      await expect(modal).toBeVisible();
      await modal.getByLabel('Metric Name').fill(name);
      await modal.getByRole('combobox').selectOption('numeric');
      await expect(modal.getByLabel('Min')).toBeVisible();
      await modal.getByLabel('Min').fill('1');
      await modal.getByLabel('Max').fill('5');
      await modal.getByRole('button', { name: 'Create' }).click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });
    }
    await page.waitForTimeout(300);
    
    console.log(`✓ Created ${itemCount} total metrics, ensuring items exist beyond first page`);
    
    const initialCount = await page.locator('table tbody tr').count();
    console.log(`Initial visible count: ${initialCount}`);
    
    // Keep clicking "Load More" until exhausted
    let loadMoreClicks = 0;
    while (await loadMoreButton.isVisible()) {
      loadMoreClicks++;
      const countBeforeClick = await page.locator('table tbody tr').count();
      console.log(`Clicking Load More (click #${loadMoreClicks})... (current count: ${countBeforeClick})`);
      await loadMoreButton.click();

      // Wait for loading state to complete AND for rows to be added
      await expect(page.locator('tfoot').filter({ hasText: 'Loading...' })).not.toBeVisible({ timeout: 10000 });

      // Then wait for row count to actually increase
      await expect(async () => {
        const currentCount = await page.locator('table tbody tr').count();
        expect(currentCount).toBeGreaterThan(countBeforeClick);
      }).toPass({ timeout: 5000 });

      const currentCount = await page.locator('table tbody tr').count();
      console.log(`After click #${loadMoreClicks}: ${currentCount} items visible`);

      // Safety check to prevent infinite loop
      if (loadMoreClicks > 5) {
        throw new Error('Too many Load More clicks - possible infinite loop');
      }
    }
    
    const finalCount = await page.locator('table tbody tr').count();
    expect(finalCount).toBeGreaterThan(initialCount);
    console.log(`✓ Load More exhausted after ${loadMoreClicks} clicks (${initialCount} → ${finalCount} items)`);
    
    // Verify we can see at least one of our created items
    const firstItem = metricNames[0];
    await expect(page.getByText(firstItem)).toBeVisible();
    console.log(`✓ At least one created metric (${firstItem}) is visible`);
    
    // Cleanup: Delete all created metrics
    if (!shouldSkipCleanup()) {
      console.log('Cleaning up metrics...');
      for (let i = 0; i < metricNames.length; i++) {
        await deleteHumanMetric(page, metricNames[i]);
        if ((i + 1) % 10 === 0) {
          console.log(`Deleted ${i + 1}/${metricNames.length} metrics...`);
        }
      }
      console.log(`✓ Cleanup complete - deleted ${metricNames.length} metrics`);
    }
  });

  test('should handle search with pagination for human metrics', async ({ page }) => {
    console.log('--- Testing Search with Pagination ---');
    
    const metricNames = [];
    const searchPrefix = `searchtest-metric-${uniqueId}`;
    
    console.log('Creating metrics until pagination is triggered...');
    const searchInput = page.getByPlaceholder('Search metrics...');
    await searchInput.fill(searchPrefix);
    await page.waitForTimeout(500);
    
    let itemCount = 0;
    const loadMoreButton = page.locator('tfoot tr').filter({ hasText: 'Load More' });
    
    // Keep creating until Load More appears
    while (!(await loadMoreButton.isVisible()) && itemCount < 50) {
      itemCount++;
      const name = `${searchPrefix}-${String(itemCount).padStart(3, '0')}`;
      metricNames.push(name);
      
      await page.getByRole('button', { name: '+ Create Metric' }).click();
      const modal = page.locator('[role="dialog"]');
      await expect(modal).toBeVisible();
      
      await modal.getByLabel('Metric Name').fill(name);
      await modal.getByRole('combobox').selectOption('categorical');
      await expect(modal.getByLabel('Options (comma separated)')).toBeVisible();
      await modal.getByLabel('Options (comma separated)').fill('Yes, No, Maybe');
      await modal.getByRole('button', { name: 'Create' }).click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });
      
      if (itemCount % 5 === 0) {
        console.log(`Created ${itemCount} metrics, checking for Load More button...`);
        await page.waitForTimeout(300);
      }
    }
    
    // Create additional items beyond the first page
    const itemsToAddForSecondPage = 5;
    console.log(`Load More appeared at ${itemCount} items. Creating ${itemsToAddForSecondPage} more for second page...`);
    for (let i = 0; i < itemsToAddForSecondPage; i++) {
      itemCount++;
      const name = `${searchPrefix}-${String(itemCount).padStart(3, '0')}`;
      metricNames.push(name);
      
      await page.getByRole('button', { name: '+ Create Metric' }).click();
      const modal = page.locator('[role="dialog"]');
      await expect(modal).toBeVisible();
      await modal.getByLabel('Metric Name').fill(name);
      await modal.getByRole('combobox').selectOption('categorical');
      await expect(modal.getByLabel('Options (comma separated)')).toBeVisible();
      await modal.getByLabel('Options (comma separated)').fill('Option A, Option B');
      await modal.getByRole('button', { name: 'Create' }).click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });
    }
    await page.waitForTimeout(300);
    
    console.log(`✓ Created ${itemCount} total metrics with search active`);
    
    const initialCount = await page.locator('table tbody tr').count();
    console.log(`Initial visible count: ${initialCount}`);
    
    // Click "Load More" and verify it works with search
    let loadMoreClicks = 0;
    while (await loadMoreButton.isVisible()) {
      loadMoreClicks++;
      const countBeforeClick = await page.locator('table tbody tr').count();
      console.log(`Clicking Load More (click #${loadMoreClicks})... (current count: ${countBeforeClick})`);
      await loadMoreButton.click();

      await expect(page.locator('tfoot').filter({ hasText: 'Loading...' })).not.toBeVisible({ timeout: 10000 });

      await expect(async () => {
        const currentCount = await page.locator('table tbody tr').count();
        expect(currentCount).toBeGreaterThan(countBeforeClick);
      }).toPass({ timeout: 5000 });

      const currentCount = await page.locator('table tbody tr').count();
      console.log(`After click #${loadMoreClicks}: ${currentCount} items visible`);

      if (loadMoreClicks > 5) {
        throw new Error('Too many Load More clicks - possible infinite loop');
      }
    }
    
    const finalCount = await page.locator('table tbody tr').count();
    expect(finalCount).toBeGreaterThan(initialCount);
    console.log(`✓ Load More exhausted after ${loadMoreClicks} clicks (${initialCount} → ${finalCount} items)`);
    
    // Verify all visible items match the search
    const allRows = await page.locator('table tbody tr').all();
    for (const row of allRows) {
      const text = await row.textContent();
      expect(text).toContain(searchPrefix);
    }
    console.log(`✓ All ${finalCount} visible items match search prefix "${searchPrefix}"`);
    
    // Cleanup
    if (!shouldSkipCleanup()) {
      console.log('Cleaning up...');
      for (const name of metricNames) {
        await deleteHumanMetric(page, name);
      }
      console.log(`✓ Cleanup complete - deleted ${metricNames.length} metrics`);
    }
  });
});

