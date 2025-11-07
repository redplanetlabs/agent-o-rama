import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getE2ETestAgentRow, createEvaluator, createDataset, deleteEvaluator, deleteDataset } from './helpers.js';

// =============================================================================
// PAGINATION TEST SUITE
// Tests that "Load More" pagination works correctly for datasets and evaluators
// =============================================================================

const uniqueId = randomUUID().substring(0, 8);

test.describe('Pagination Tests', () => {
  // Increase timeout since these tests create many items
  test.setTimeout(120000); // 2 minutes
  
  test.beforeEach(async ({ page }) => {
    console.log('--- Starting Test Setup ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);
    
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();
    await expect(page).toHaveURL(new RegExp(`/agents/.*com\\.rpl\\.agent\\.e2e-test-agent.*E2ETestAgentModule`));
    console.log('--- Test Setup Complete ---');
  });

  test('should paginate evaluators correctly with Load More button', async ({ page }) => {
    test.setTimeout(120000); // 2 minutes
    console.log('--- Testing Evaluators Pagination ---');
    
    // Navigate to evaluators page
    await page.getByText('Evaluators').click();
    await expect(page).toHaveURL(/evaluations/);
    
    const evaluatorNames = [];
    const searchPrefix = `pagination-test-eval-${uniqueId}`;
    
    // Create evaluators one at a time until Load More button appears
    console.log('Creating evaluators until pagination is triggered...');
    const searchInput = page.getByPlaceholder('Search evaluators...');
    await searchInput.fill(searchPrefix);
    await page.waitForTimeout(500);
    
    let itemCount = 0;
    const loadMoreButton = page.locator('tfoot tr').filter({ hasText: 'Load More' });
    
    // Keep creating until Load More appears (backend returns up to 40 items, so we need 41+)
    while (!(await loadMoreButton.isVisible()) && itemCount < 50) {
      itemCount++;
      const name = `${searchPrefix}-${String(itemCount).padStart(3, '0')}`;
      evaluatorNames.push(name);
      
      await createEvaluator(page, {
        name,
        builderName: 'aor/conciseness',
        description: `Pagination test evaluator ${itemCount}`,
        params: { threshold: '10' }
      });
      
      if (itemCount % 5 === 0) {
        console.log(`Created ${itemCount} evaluators, checking for Load More button...`);
        await page.waitForTimeout(300);
      }
    }

    // create one more evaluator, so it goes one over the page size.
    await createEvaluator(page, {
      name: `${searchPrefix}-${String(itemCount + 1).padStart(3, '0')}`,
      builderName: 'aor/conciseness',
      description: `Pagination test evaluator ${itemCount + 1}`,
      params: { threshold: '10' }
    });
    itemCount++;
    
    console.log(`✓ Created ${itemCount} evaluators, Load More button is now visible`);
    
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
      // First wait for loading indicator to disappear (if it appears)
      await expect(page.locator('tfoot').filter({ hasText: 'Loading...' })).not.toBeVisible({ timeout: 10000 });

      // Then wait for row count to actually increase (DOM update may lag behind state)
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
    const firstItem = evaluatorNames[0];
    await expect(page.getByText(firstItem)).toBeVisible();
    console.log(`✓ At least one created evaluator (${firstItem}) is visible`);
    
    // Cleanup: Delete all created evaluators
    console.log('Cleaning up evaluators...');
    for (let i = 0; i < evaluatorNames.length; i++) {
      await deleteEvaluator(page, evaluatorNames[i]);
      if ((i + 1) % 10 === 0) {
        console.log(`Deleted ${i + 1}/${evaluatorNames.length} evaluators...`);
      }
    }
    console.log(`✓ Cleanup complete - deleted ${evaluatorNames.length} evaluators`);
  });

  test('should paginate datasets correctly with Load More button', async ({ page }) => {
    console.log('--- Testing Datasets Pagination ---');
    test.setTimeout(120000); // 2 minutes
    
    // Navigate to datasets page
    await page.getByText('Datasets & Experiments').click();
    await expect(page).toHaveURL(/datasets/);
    
    const datasetNames = [];
    const namePrefix = `pagination-test-ds-${uniqueId}`;
    
    // Note: Dataset search doesn't support pagination on the backend, so we can't use search
    // to filter. We'll create datasets until Load More appears in the general list.
    console.log('Creating datasets until pagination is triggered...');
    
    let itemCount = 0;
    const loadMoreButton = page.locator('tfoot tr').filter({ hasText: 'Load More' });

    // Ensure we create at least 1 dataset for verification, even if Load More is already visible
    const minDatasets = 1;
    while ((!(await loadMoreButton.isVisible()) || itemCount < minDatasets) && itemCount < 50) {
      itemCount++;
      const name = `${namePrefix}-${String(itemCount).padStart(3, '0')}`;
      datasetNames.push(name);

      await createDataset(page, name);

      if (itemCount % 5 === 0) {
        console.log(`Created ${itemCount} datasets, checking for Load More button...`);
        await page.waitForTimeout(300);
      }
    }
    
    console.log(`✓ Created ${itemCount} datasets, Load More button is now visible`);
    
    const initialCount = await page.locator('table tbody tr').count();
    console.log(`Initial visible count: ${initialCount} (includes datasets from other tests)`);
    
    // Keep clicking "Load More" until exhausted
    let loadMoreClicks = 0;
    while (await loadMoreButton.isVisible()) {
      loadMoreClicks++;
      const countBeforeClick = await page.locator('table tbody tr').count();
      console.log(`Clicking Load More (click #${loadMoreClicks})... (current count: ${countBeforeClick})`);
      await loadMoreButton.click();

      // Wait for loading state to complete AND for rows to be added
      // First wait for loading indicator to disappear (if it appears)
      await expect(page.locator('tfoot').filter({ hasText: 'Loading...' })).not.toBeVisible({ timeout: 10000 });

      // Then wait for row count to actually increase (DOM update may lag behind state)
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
    
    // Verify at least one of our test datasets is visible
    const firstTestDataset = datasetNames[0];
    await expect(page.getByText(firstTestDataset)).toBeVisible();
    console.log(`✓ At least one of our test datasets (${firstTestDataset}) is visible`);
    
    // Cleanup: Delete all created datasets
    console.log('Cleaning up datasets...');
    for (let i = 0; i < datasetNames.length; i++) {
      await deleteDataset(page, datasetNames[i]);
      if ((i + 1) % 10 === 0) {
        console.log(`Deleted ${i + 1}/${datasetNames.length} datasets...`);
      }
    }
    console.log(`✓ Cleanup complete - deleted ${datasetNames.length} datasets`);
  });

  test('should handle search with pagination', async ({ page }) => {
    console.log('--- Testing Search with Pagination ---');
    test.setTimeout(120000); // 2 minutes
    
    // Navigate to evaluators page
    await page.getByText('Evaluators').click();
    await expect(page).toHaveURL(/evaluations/);
    
    const evaluatorNames = [];
    const searchPrefix = `searchtest-eval-${uniqueId}`;
    
    // Create evaluators one at a time until Load More button appears
    console.log('Creating evaluators until pagination is triggered...');
    const searchInput = page.getByPlaceholder('Search evaluators...');
    await searchInput.fill(searchPrefix);
    await page.waitForTimeout(500);
    
    let itemCount = 0;
    const loadMoreButton = page.locator('tfoot tr').filter({ hasText: 'Load More' });
    
    // Keep creating until Load More appears (backend returns up to 40 items, so we need 41+)
    while (!(await loadMoreButton.isVisible()) && itemCount < 50) {
      itemCount++;
      const name = `${searchPrefix}-${String(itemCount).padStart(3, '0')}`;
      evaluatorNames.push(name);
      
      await createEvaluator(page, {
        name,
        builderName: 'aor/conciseness',
        description: `Search pagination test ${itemCount}`,
        params: { threshold: '10' }
      });
      
      if (itemCount % 5 === 0) {
        console.log(`Created ${itemCount} evaluators, checking for Load More button...`);
        // The search should already be active from earlier, just wait for results
        await page.waitForTimeout(300);
      }
    }
    
    console.log(`✓ Created ${itemCount} evaluators, Load More button is now visible`);
    
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
      // First wait for loading indicator to disappear (if it appears)
      await expect(page.locator('tfoot').filter({ hasText: 'Loading...' })).not.toBeVisible({ timeout: 10000 });

      // Then wait for row count to actually increase (DOM update may lag behind state)
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
    const firstItem = evaluatorNames[0];
    await expect(page.getByText(firstItem)).toBeVisible();
    console.log(`✓ At least one created evaluator (${firstItem}) is visible`);
    
    // Cleanup
    console.log('Cleaning up...');
    for (const name of evaluatorNames) {
      await deleteEvaluator(page, name);
    }
    console.log(`✓ Cleanup complete - deleted ${evaluatorNames.length} evaluators`);
  });
});

