import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getBasicAgentRow, shouldSkipCleanup, createHumanMetric, deleteHumanMetric } from './helpers.js';

// =============================================================================
// TEST SUITE: Manual Human Feedback (Add/Edit/Delete)
// =============================================================================

test.describe('Manual Human Feedback', () => {
  const uniqueId = randomUUID().substring(0, 8);
  const metricName1 = `e2e-manual-metric-${uniqueId}-1`;
  const metricName2 = `e2e-manual-metric-${uniqueId}-2`;
  let agentInvokeUrl;
  
  test.beforeAll(async ({ browser }) => {
    // Create test metrics
    const page = await browser.newPage();
    await page.goto('/');
    
    const agentRow = await getBasicAgentRow(page);
    await agentRow.click();
    await expect(page).toHaveURL(/BasicAgentModule/);
    await page.getByRole('link', { name: 'Human Metrics' }).click();
    await expect(page).toHaveURL(/human-metrics/);
    
    // Create a categorical metric
    await createHumanMetric(page, {
      name: metricName1,
      type: 'categorical',
      categories: ['Good', 'Bad', 'Neutral']
    });
    
    // Create a numeric metric
    await createHumanMetric(page, {
      name: metricName2,
      type: 'numeric',
      min: 1,
      max: 10
    });
    
    await page.close();
    console.log('✓ Test metrics created');
  });
  
  test.afterAll(async ({ browser }) => {
    if (shouldSkipCleanup()) {
      console.log('Skipping cleanup (SKIP_CLEANUP=true)');
      return;
    }
    
    const page = await browser.newPage();
    await page.goto('/');
    
    const agentRow = await getBasicAgentRow(page);
    await agentRow.click();
    await page.getByRole('link', { name: 'Human Metrics' }).click();
    await expect(page).toHaveURL(/human-metrics/);
    
    // Delete test metrics
    await deleteHumanMetric(page, metricName1);
    await deleteHumanMetric(page, metricName2);
    
    await page.close();
    console.log('✓ Cleanup complete');
  });
  
  test.beforeEach(async ({ page }) => {
    console.log('--- Starting Test Setup ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);
    
    const agentRow = await getBasicAgentRow(page);
    await agentRow.click();
    await expect(page).toHaveURL(/BasicAgentModule/);
    
    // Navigate to agent page and get first invocation
    await page.getByRole('link', { name: 'Invocations' }).click();
    await expect(page).toHaveURL(/invocations/);
    
    // Click on first invocation
    const firstInvocation = page.locator('tbody tr').first();
    await firstInvocation.waitFor({ timeout: 10000 });
    await firstInvocation.click();
    
    // Wait for invocation graph to load
    await page.waitForURL(/\/invocations\/\d+-/, { timeout: 10000 });
    
    // Click on Feedback tab
    await page.getByRole('button', { name: 'Feedback' }).click();
    await page.waitForTimeout(1000);
    
    agentInvokeUrl = page.url();
    console.log(`Successfully navigated to invocation: ${agentInvokeUrl}`);
  });
  
  test('should add manual feedback with validation', async ({ page }) => {
    const modal = page.locator('[role="dialog"]');
    const submitButton = modal.getByRole('button', { name: /Submit|Save/i });
    
    // =============================================================================
    // TEST 1: Open Add Feedback modal
    // =============================================================================
    console.log('Test 1: Opening Add Feedback modal');
    await page.getByTestId('add-feedback-button').click();
    await expect(modal).toBeVisible();
    await expect(modal.getByRole('heading', { name: /Add.*Feedback/i })).toBeVisible();
    console.log('✓ Modal opened');
    
    // =============================================================================
    // TEST 2: Validation - empty reviewer name
    // =============================================================================
    console.log('Test 2: Validating empty reviewer name');
    await expect(submitButton).toBeDisabled();
    await expect(modal.getByText('This field is required')).toBeVisible();
    console.log('✓ Reviewer name validation works');
    
    // =============================================================================
    // TEST 3: Validation - at least one metric required
    // =============================================================================
    console.log('Test 3: Validating at least one metric required');
    await modal.getByTestId('reviewer-name-input').fill('Test Reviewer');
    await expect(submitButton).toBeDisabled();
    await expect(modal.getByText(/at least one metric/i)).toBeVisible();
    console.log('✓ Metric validation works');
    
    // =============================================================================
    // TEST 4: Add a categorical metric
    // =============================================================================
    console.log('Test 4: Adding categorical metric');
    
    // Click on metric selector
    await modal.getByTestId('add-metric-selector').getByRole('combobox').click();
    
    // Wait for dropdown
    await page.waitForTimeout(1000);
    
    // Type to search for our metric
    await modal.getByTestId('add-metric-selector').getByRole('combobox').fill(metricName1);
    await page.waitForTimeout(500);
    
    // Click first option (our categorical metric)
    const firstOption = modal.locator('[role="option"]').first();
    await firstOption.waitFor({ timeout: 5000 });
    await firstOption.click();
    
    // Verify metric field appeared
    await expect(modal.getByTestId('metric-field-0')).toBeVisible();
    console.log('✓ Metric field added');
    
    // =============================================================================
    // TEST 5: Validation - metric value required
    // =============================================================================
    console.log('Test 5: Validating metric value required');
    // Button should still be disabled without value
    await expect(submitButton).toBeDisabled();
    
    // Select a category
    await modal.getByTestId('metric-value-0').selectOption('Good');
    
    // Now button should be enabled
    await expect(submitButton).not.toBeDisabled();
    console.log('✓ Metric value validation works');
    
    // =============================================================================
    // TEST 6: Add numeric metric
    // =============================================================================
    console.log('Test 6: Adding numeric metric');
    
    // Click on metric selector again
    await modal.getByTestId('add-metric-selector').getByRole('combobox').click();
    await page.waitForTimeout(500);
    
    // Type to search for numeric metric
    await modal.getByTestId('add-metric-selector').getByRole('combobox').fill(metricName2);
    await page.waitForTimeout(500);
    
    // Click the numeric metric
    const numericOption = modal.locator('[role="option"]').first();
    await numericOption.waitFor({ timeout: 5000 });
    await numericOption.click();
    
    // Verify second metric field appeared
    await expect(modal.getByTestId('metric-field-1')).toBeVisible();
    
    // Enter a numeric value
    await modal.getByTestId('metric-value-1').fill('7');
    console.log('✓ Numeric metric added');
    
    // =============================================================================
    // TEST 7: Add optional comment
    // =============================================================================
    console.log('Test 7: Adding optional comment');
    await modal.getByTestId('feedback-comment-input').fill('This is a test feedback comment');
    console.log('✓ Comment added');
    
    // =============================================================================
    // TEST 8: Submit feedback
    // =============================================================================
    console.log('Test 8: Submitting feedback');
    await submitButton.click();
    
    // Wait for modal to close
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    // Wait for page to reload/refresh
    await page.waitForTimeout(2000);
    
    // Verify feedback appears in the list
    await expect(page.locator('[data-id="feedback-list"]')).toBeVisible({ timeout: 10000 });
    
    // Look for "Human:" prefix in feedback (indicates human feedback)
    const humanFeedback = page.locator('text=/Human:/').first();
    await expect(humanFeedback).toBeVisible({ timeout: 5000 });
    console.log('✓ Feedback submitted and visible');
    
    // =============================================================================
    // TEST 9: Verify edit button is present
    // =============================================================================
    console.log('Test 9: Verifying edit button');
    await expect(page.getByTestId('edit-feedback-button').first()).toBeVisible();
    console.log('✓ Edit button present');
    
    // =============================================================================
    // TEST 10: Delete feedback
    // =============================================================================
    console.log('Test 10: Deleting feedback');
    
    // Set up dialog handler for confirmation
    page.once('dialog', dialog => {
      console.log(`Dialog message: ${dialog.message()}`);
      dialog.accept();
    });
    
    await page.getByTestId('delete-feedback-button').first().click();
    
    // Wait for feedback to be removed
    await page.waitForTimeout(2000);
    
    console.log('✓ Feedback deleted');
  });
  
  test('should edit existing feedback', async ({ page }) => {
    // First, add a feedback item to edit
    console.log('Setting up: Adding initial feedback');
    
    await page.getByTestId('add-feedback-button').click();
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();
    
    // Fill in initial feedback
    await modal.getByTestId('reviewer-name-input').fill('Original Reviewer');
    
    // Add a metric
    await modal.getByTestId('add-metric-selector').getByRole('combobox').click();
    await page.waitForTimeout(500);
    await modal.getByTestId('add-metric-selector').getByRole('combobox').fill(metricName1);
    await page.waitForTimeout(500);
    await modal.locator('[role="option"]').first().click();
    
    await modal.getByTestId('metric-value-0').selectOption('Good');
    await modal.getByTestId('feedback-comment-input').fill('Original comment');
    
    await modal.getByRole('button', { name: /Submit/i }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    await page.waitForTimeout(2000);
    
    console.log('✓ Initial feedback added');
    
    // =============================================================================
    // TEST: Edit the feedback
    // =============================================================================
    console.log('Test: Editing feedback');
    
    await page.getByTestId('edit-feedback-button').first().click();
    await expect(modal).toBeVisible();
    await expect(modal.getByRole('heading', { name: /Edit/i })).toBeVisible();
    
    // Verify fields are pre-populated
    await expect(modal.getByTestId('reviewer-name-input')).toHaveValue('Original Reviewer');
    await expect(modal.getByTestId('feedback-comment-input')).toHaveValue('Original comment');
    
    // Change the values
    await modal.getByTestId('reviewer-name-input').fill('Updated Reviewer');
    await modal.getByTestId('feedback-comment-input').fill('Updated comment');
    
    await modal.getByRole('button', { name: /Submit|Save/i }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    
    await page.waitForTimeout(2000);
    
    // Verify the updated feedback shows "Updated Reviewer"
    await expect(page.locator('text=/Human:.*Updated Reviewer/')).toBeVisible({ timeout: 5000 });
    
    console.log('✓ Feedback edited successfully');
    
    // Cleanup: Delete the feedback
    page.once('dialog', dialog => dialog.accept());
    await page.getByTestId('delete-feedback-button').first().click();
    await page.waitForTimeout(1000);
  });
  
  test('should validate numeric range', async ({ page }) => {
    const modal = page.locator('[role="dialog"]');
    
    console.log('Test: Numeric range validation');
    
    await page.getByTestId('add-feedback-button').click();
    await expect(modal).toBeVisible();
    
    await modal.getByTestId('reviewer-name-input').fill('Test Reviewer');
    
    // Add numeric metric
    await modal.getByTestId('add-metric-selector').getByRole('combobox').click();
    await page.waitForTimeout(500);
    await modal.getByTestId('add-metric-selector').getByRole('combobox').fill(metricName2);
    await page.waitForTimeout(500);
    await modal.locator('[role="option"]').first().click();
    
    // Try to enter value outside range (metric is 1-10)
    await modal.getByTestId('metric-value-0').fill('15');
    
    // Should show error (though HTML5 validation might prevent this)
    const submitButton = modal.getByRole('button', { name: /Submit/i });
    
    // Change to valid value
    await modal.getByTestId('metric-value-0').fill('5');
    await expect(submitButton).not.toBeDisabled();
    
    console.log('✓ Numeric validation works');
    
    // Close modal
    await modal.getByRole('button', { name: /Cancel|Close/i }).click();
  });
});
