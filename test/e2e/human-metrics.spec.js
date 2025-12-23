import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getBasicAgentRow, shouldSkipCleanup } from './helpers.js';

// =============================================================================
// TEST SUITE: Human Metrics
// =============================================================================

test.describe('Human Metrics', () => {
  const uniqueId = randomUUID().substring(0, 8);
  const numericMetricName = `e2e-numeric-${uniqueId}`;
  const categoricalMetricName = `e2e-categorical-${uniqueId}`;
  
  test('should handle validation errors and successfully create metrics', async ({ page }) => {
    // SETUP: Navigate to BasicAgentModule
    console.log('--- Starting Human Metrics Test ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getBasicAgentRow(page);
    await agentRow.click();
    await expect(page).toHaveURL(/BasicAgentModule/);
    
    // Navigate to Human Metrics page
    await page.getByText('Human Metrics').click();
    await expect(page).toHaveURL(/human-metrics/);
    await expect(page.getByRole('heading', { name: 'Human Metrics' })).toBeVisible();
    console.log('Successfully navigated to Human Metrics page');

    // =============================================================================
    // VALIDATION TESTS
    // =============================================================================
    
    // TEST 1: Empty name validation
    console.log('Test 1: Validating empty name error');
    await page.getByRole('button', { name: '+ Create Metric' }).click();
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();
    await expect(modal.getByRole('heading', { name: 'Create Human Metric' })).toBeVisible();
    
    // Verify button is initially disabled (form starts with empty required field)
    const createButton = modal.getByRole('button', { name: 'Create' });
    await expect(createButton).toBeDisabled();
    console.log('✓ Create button disabled with empty name');
    
    // Fill name to enable button, then clear to see error
    await modal.getByLabel('Metric Name').fill('test');
    await expect(createButton).toBeEnabled();
    await modal.getByLabel('Metric Name').clear();
    await expect(modal.getByText('This field is required')).toBeVisible();
    await expect(createButton).toBeDisabled();
    console.log('✓ Empty name validation works');
    
    // Close modal
    await modal.getByRole('button', { name: '×' }).click();
    await expect(modal).not.toBeVisible();

    // TEST 2: Categorical - empty categories validation
    console.log('Test 2: Validating empty categories error');
    await page.getByRole('button', { name: '+ Create Metric' }).click();
    await expect(modal).toBeVisible();
    
    // Fill name and switch to categorical
    await modal.getByLabel('Metric Name').fill('test-metric');
    await modal.getByRole('combobox').selectOption('categorical');
    
    // Verify empty categories shows error and button is disabled
    await expect(modal.getByText('Categories are required for categorical metrics')).toBeVisible();
    await expect(createButton).toBeDisabled();
    console.log('✓ Empty categories validation works');
    
    // TEST 3: Categorical - single category validation
    console.log('Test 3: Validating single category error');
    await modal.getByLabel('Options (comma separated)').fill('OnlyOne');
    await expect(modal.getByText('At least two categories are required')).toBeVisible();
    await expect(createButton).toBeDisabled();
    console.log('✓ Single category validation works');
    
    // TEST 4: Categorical - duplicate categories validation
    console.log('Test 4: Validating duplicate categories error');
    await modal.getByLabel('Options (comma separated)').fill('Good, Bad, Good');
    await expect(modal.getByText('Duplicate categories are not allowed')).toBeVisible();
    await expect(createButton).toBeDisabled();
    console.log('✓ Duplicate categories validation works');
    
    // TEST 5: Categorical - preview pillboxes
    console.log('Test 5: Testing category preview');
    await modal.getByLabel('Options (comma separated)').fill('Good, Bad, Average');
    // Verify preview pillboxes appear
    await expect(modal.getByText('Preview:')).toBeVisible();
    await expect(modal.getByText('Good', { exact: true })).toBeVisible();
    await expect(modal.getByText('Bad', { exact: true })).toBeVisible();
    await expect(modal.getByText('Average', { exact: true })).toBeVisible();
    console.log('✓ Category preview pillboxes work');
    
    // Close modal
    await modal.getByRole('button', { name: '×' }).click();
    await expect(modal).not.toBeVisible();

    // TEST 6: Numeric - invalid range validation (min >= max)
    console.log('Test 6: Validating numeric range errors');
    await page.getByRole('button', { name: '+ Create Metric' }).click();
    await expect(modal).toBeVisible();
    
    // Fill name and keep numeric type
    await modal.getByLabel('Metric Name').fill('test-numeric');
    await modal.getByRole('combobox').selectOption('numeric');
    
    // Set min >= max
    await modal.getByLabel('Min').fill('10');
    await modal.getByLabel('Max').fill('5');
    
    // Should show error on both fields and button disabled
    await expect(modal.getByText('Min must be less than Max')).toBeVisible();
    await expect(modal.getByText('Max must be greater than Min')).toBeVisible();
    await expect(createButton).toBeDisabled();
    console.log('✓ Numeric range validation works');
    
    // TEST 7: Numeric - equal values validation
    console.log('Test 7: Validating equal min/max error');
    await modal.getByLabel('Min').fill('5');
    await modal.getByLabel('Max').fill('5');
    await expect(modal.getByText('Min must be less than Max')).toBeVisible();
    await expect(modal.getByText('Max must be greater than Min')).toBeVisible();
    await expect(createButton).toBeDisabled();
    console.log('✓ Equal min/max validation works');
    
    // Close modal
    await modal.getByRole('button', { name: '×' }).click();
    await expect(modal).not.toBeVisible();

    // =============================================================================
    // CREATION TESTS
    // =============================================================================
    
    // TEST 8: Successfully create a numeric metric
    console.log('Test 8: Creating numeric metric');
    await page.getByRole('button', { name: '+ Create Metric' }).click();
    await expect(modal).toBeVisible();
    
    await modal.getByLabel('Metric Name').fill(numericMetricName);
    // Explicitly select numeric type (form might retain state from previous tests)
    await modal.getByRole('combobox').selectOption('numeric');
    // Wait for the form to update
    await expect(modal.getByLabel('Min')).toBeVisible();
    await modal.getByLabel('Min').fill('1');
    await modal.getByLabel('Max').fill('10');
    await modal.getByRole('button', { name: 'Create' }).click();
    
    // Modal should close on success
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    // Verify metric appears in table
    const numericRow = page.locator('table tbody tr').filter({ hasText: numericMetricName });
    await expect(numericRow).toBeVisible({ timeout: 5000 });
    console.log(`✓ Successfully created metric: ${numericMetricName}`);

    // TEST 9: Successfully create another metric with different name
    console.log('Test 9: Creating second metric');
    await page.getByRole('button', { name: '+ Create Metric' }).click();
    await expect(modal).toBeVisible();
    
    await modal.getByLabel('Metric Name').fill(categoricalMetricName);
    await modal.getByRole('combobox').selectOption('categorical');
    await expect(modal.getByLabel('Options (comma separated)')).toBeVisible();
    await modal.getByLabel('Options (comma separated)').fill('Excellent, Good, Fair, Poor');
    
    // Verify preview shows all categories
    await expect(modal.getByText('Excellent', { exact: true })).toBeVisible();
    await expect(modal.getByText('Good', { exact: true })).toBeVisible();
    await expect(modal.getByText('Fair', { exact: true })).toBeVisible();
    await expect(modal.getByText('Poor', { exact: true })).toBeVisible();
    
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    // Verify metric appears in table
    const categoricalRow = page.locator('table tbody tr').filter({ hasText: categoricalMetricName });
    await expect(categoricalRow).toBeVisible({ timeout: 5000 });
    console.log(`✓ Successfully created metric: ${categoricalMetricName}`);

    // =============================================================================
    // SEARCH TESTS
    // =============================================================================
    
    // TEST 10: Search functionality
    console.log('Test 10: Testing search functionality');
    const searchInput = page.getByPlaceholder('Search metrics...');
    
    // Search for numeric metric
    await searchInput.fill('numeric');
    await page.waitForTimeout(400); // Wait for debounce
    await expect(numericRow).toBeVisible();
    await expect(categoricalRow).not.toBeVisible();
    console.log('✓ Search filters to numeric metric');
    
    // Search for categorical metric
    await searchInput.clear();
    await searchInput.fill('categorical');
    await page.waitForTimeout(400);
    await expect(categoricalRow).toBeVisible();
    await expect(numericRow).not.toBeVisible();
    console.log('✓ Search filters to categorical metric');
    
    // Clear search
    await searchInput.clear();
    await page.waitForTimeout(400);
    await expect(numericRow).toBeVisible();
    await expect(categoricalRow).toBeVisible();
    console.log('✓ Search cleared successfully');

    // =============================================================================
    // DELETION TESTS
    // =============================================================================
    
    if (!shouldSkipCleanup()) {
      // TEST 11: Delete metrics
      console.log('Test 11: Deleting metrics');
      
      // Set up dialog handler
      page.on('dialog', dialog => {
        console.log(`Accepting deletion dialog: ${dialog.message()}`);
        dialog.accept();
      });
      
      // Delete numeric metric
      await numericRow.getByRole('button', { name: 'Delete' }).click();
      await expect(numericRow).not.toBeVisible({ timeout: 10000 });
      console.log(`✓ Deleted numeric metric: ${numericMetricName}`);
      
      // Delete categorical metric
      await categoricalRow.getByRole('button', { name: 'Delete' }).click();
      await expect(categoricalRow).not.toBeVisible({ timeout: 10000 });
      console.log(`✓ Deleted categorical metric: ${categoricalMetricName}`);
      
      console.log('--- Cleanup Complete ---');
    } else {
      console.log('⏭️  Skipping cleanup (SKIP_CLEANUP=true)');
      console.log(`   Keeping metrics: ${numericMetricName}, ${categoricalMetricName}`);
    }
    
    console.log('--- Human Metrics Test Complete ---');
  });

  test('should handle edge cases for category parsing', async ({ page }) => {
    console.log('--- Starting Edge Cases Test ---');
    await page.goto('/');
    const agentRow = await getBasicAgentRow(page);
    await agentRow.click();
    
    await page.getByText('Human Metrics').click();
    await expect(page).toHaveURL(/human-metrics/);
    
    const edgeCaseMetric = `e2e-edge-${uniqueId}`;
    
    // Open modal
    await page.getByRole('button', { name: '+ Create Metric' }).click();
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();
    
    await modal.getByLabel('Metric Name').fill(edgeCaseMetric);
    await modal.getByRole('combobox').selectOption('categorical');
    
    // TEST: Extra commas and whitespace
    console.log('Testing edge case: extra commas and whitespace');
    await modal.getByLabel('Options (comma separated)').fill('  Good  ,  ,  Bad  ,  ,,  Average  ');
    
    // Should still show 3 preview pills (empty entries filtered out)
    await expect(modal.getByText('Preview:')).toBeVisible();
    await expect(modal.getByText('Good', { exact: true })).toBeVisible();
    await expect(modal.getByText('Bad', { exact: true })).toBeVisible();
    await expect(modal.getByText('Average', { exact: true })).toBeVisible();
    
    // Should successfully create with trimmed categories
    await modal.getByRole('button', { name: 'Create' }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    const edgeRow = page.locator('table tbody tr').filter({ hasText: edgeCaseMetric });
    await expect(edgeRow).toBeVisible({ timeout: 5000 });
    console.log('✓ Edge case handling works correctly');
    
    // Cleanup
    if (!shouldSkipCleanup()) {
      page.on('dialog', dialog => dialog.accept());
      await edgeRow.getByRole('button', { name: 'Delete' }).click();
      await expect(edgeRow).not.toBeVisible({ timeout: 10000 });
      console.log(`✓ Cleaned up edge case metric: ${edgeCaseMetric}`);
    }
    
    console.log('--- Edge Cases Test Complete ---');
  });
});

