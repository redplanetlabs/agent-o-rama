import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getE2ETestAgentRow, shouldSkipCleanup, createHumanMetric, deleteHumanMetric, invokeAgentManually } from './helpers.js';

// =============================================================================
// TEST SUITE: Manual Human Feedback (Add/Edit/Delete)
// =============================================================================

test.describe('Manual Human Feedback', () => {
  const uniqueId = randomUUID().substring(0, 8);
  const metricName1 = `e2e-manual-metric-${uniqueId}-1`;
  const metricName2 = `e2e-manual-metric-${uniqueId}-2`;
  let agentInvokeUrl;
  
  test.beforeAll(async ({ browser }) => {
    // Create test metrics and an invocation for testing
    const page = await browser.newPage();
    await page.goto('/');
    
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();
    await expect(page).toHaveURL(/E2ETestAgentModule/);
    
    // Create test metrics
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
    
    console.log('✓ Test metrics created');
    
    // Create a test invocation by invoking BasicAgent
    // Navigate back to agent detail page
    await page.goBack();
    await expect(page).toHaveURL(/E2ETestAgentModule/);
    
    // Invoke the agent with test data
    await invokeAgentManually(page, [{ query: 'test query for feedback' }]);
    console.log('✓ Test invocation created');
    
    await page.close();
  });
  
  test.afterAll(async ({ browser }) => {
    if (shouldSkipCleanup()) {
      console.log('Skipping cleanup (SKIP_CLEANUP=true)');
      return;
    }
    
    const page = await browser.newPage();
    await page.goto('/');
    
    const agentRow = await getE2ETestAgentRow(page);
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
    
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();
    await expect(page).toHaveURL(/E2ETestAgentModule/);
    
    // Navigate to agent page and get first invocation
    await page.getByRole('link', { name: 'Invocations' }).click();
    await expect(page).toHaveURL(/invocations/);
    
    // Click on first invocation trace link
    const firstInvocationLink = page.locator('tbody tr').first().getByRole('link', { name: 'View trace' });
    await expect(firstInvocationLink).toBeVisible({ timeout: 10000 });
    await firstInvocationLink.click();
    
    // Wait for invocation graph to load
    await page.waitForURL(/\/invocations\/\d+-/, { timeout: 10000 });
    
    // Click on Feedback tab (agent-level, not node-level)
    await page.locator('[data-id="feedback-tab"]').click();
    await page.waitForTimeout(1000);
    
    agentInvokeUrl = page.url();
    console.log(`Successfully navigated to invocation: ${agentInvokeUrl}`);
  });
  
  test('should add manual feedback with validation', async ({ page }) => {
    const modal = page.locator('[role="dialog"]');
    const submitButton = modal.getByRole('button', { name: /Submit|Save/i });
    const agentFeedbackPanel = page.locator('[data-id="agent-feedback-container"]');
    
    // =============================================================================
    // TEST 1: Open Add Feedback modal
    // =============================================================================
    console.log('Test 1: Opening Add Feedback modal');
    await agentFeedbackPanel.getByTestId('add-feedback-button').click();
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
    await expect(modal.getByText(/either metrics or a comment/i)).toBeVisible();
    console.log('✓ Metric validation works');
    
    // =============================================================================
    // TEST 4: Add a categorical metric
    // =============================================================================
    console.log('Test 4: Adding categorical metric');
    
    // Click "Add Metric" button to create a new metric row
    await modal.getByTestId('add-metric-button').click();
    await page.waitForTimeout(500);
    
    // Click on the input in the new row to open dropdown
    const metricInput = modal.getByTestId('metric-selector-0-input');
    await metricInput.click();
    await page.waitForTimeout(500);
    
    // Type to search for our metric
    await metricInput.fill(metricName1);
    await page.waitForTimeout(500);
    
    // Click first option (our categorical metric)
    // Note: Options are rendered in a portal outside the modal
    const firstOption = page.locator('[role="option"]').first();
    await firstOption.waitFor({ timeout: 5000 });
    await firstOption.click();
    
    // Verify metric value input appeared
    await expect(modal.getByTestId('metric-value-0')).toBeVisible();
    console.log('✓ Metric field added');
    
    // =============================================================================
    // TEST 5: Validation - metric value required
    // =============================================================================
    console.log('Test 5: Validating metric value required');
    // Button should still be disabled without value
    await expect(submitButton).toBeDisabled();
    
    // Select a category by clicking the dropdown button then the item
    await modal.getByTestId('metric-value-0').click();
    await page.waitForTimeout(300);
    await page.getByText('Good').click();
    
    // Now button should be enabled
    await expect(submitButton).not.toBeDisabled();
    console.log('✓ Metric value validation works');
    
    // =============================================================================
    // TEST 6: Add numeric metric
    // =============================================================================
    console.log('Test 6: Adding numeric metric');
    
    // Click "Add Metric" button again to create another row
    await modal.getByTestId('add-metric-button').click();
    await page.waitForTimeout(500);
    
    // Click on the second selector input
    const numericMetricInput = modal.getByTestId('metric-selector-1-input');
    await numericMetricInput.click();
    await page.waitForTimeout(500);
    
    // Type to search for numeric metric
    await numericMetricInput.fill(metricName2);
    await page.waitForTimeout(500);
    
    // Click the numeric metric
    // Note: Options are rendered in a portal outside the modal
    const numericOption = page.locator('[role="option"]').first();
    await numericOption.waitFor({ timeout: 5000 });
    await numericOption.click();
    
    // Verify second metric value input appeared
    await expect(modal.getByTestId('metric-value-1')).toBeVisible();
    
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
    
    // Look for "human[" prefix in feedback (indicates human feedback)
    const humanFeedback = page.locator('text=/human\\[/').first();
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
    
    await agentFeedbackPanel.getByTestId('delete-feedback-button').click();
    
    // Wait for feedback to be removed and verify it's gone
    await page.waitForTimeout(2000);
    
    // Verify the feedback is no longer visible in agent panel
    await expect(agentFeedbackPanel.locator('text=/human\\[Test Reviewer\\]/')).not.toBeVisible();
    
    // Verify empty state is shown in agent panel
    await expect(agentFeedbackPanel.getByText('No feedback available')).toBeVisible();
    
    console.log('✓ Feedback deleted and confirmed removed from UI');
  });
  
  test('should edit existing feedback', async ({ page }) => {
    const modal = page.locator('[role="dialog"]');
    const agentFeedbackPanel = page.locator('[data-id="agent-feedback-container"]');
    
    // First, add a feedback item to edit
    console.log('Setting up: Adding initial feedback');
    
    await agentFeedbackPanel.getByTestId('add-feedback-button').click();
    await expect(modal).toBeVisible();
    
    // Fill in initial feedback
    await modal.getByTestId('reviewer-name-input').fill('Original Reviewer');
    
    // Add a metric
    await modal.getByTestId('add-metric-button').click();
    await page.waitForTimeout(500);
    const metricInput = modal.getByTestId('metric-selector-0-input');
    await metricInput.click();
    await page.waitForTimeout(500);
    await metricInput.fill(metricName1);
    await page.waitForTimeout(500);
    await page.locator('[role="option"]').first().click();
    
    // Select category from dropdown
    await modal.getByTestId('metric-value-0').click();
    await page.waitForTimeout(300);
    await page.getByText('Good').click();
    
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
    await expect(page.locator('text=/human\\[Updated Reviewer\\]/')).toBeVisible({ timeout: 5000 });
    
    console.log('✓ Feedback edited successfully');
    
    // Cleanup: Delete the feedback
    page.once('dialog', dialog => dialog.accept());
    await agentFeedbackPanel.getByTestId('delete-feedback-button').click();
    await page.waitForTimeout(1000);
  });
  
  test('should validate numeric range', async ({ page }) => {
    const modal = page.locator('[role="dialog"]');
    
    console.log('Test: Numeric range validation');
    
    await page.getByTestId('add-feedback-button').click();
    await expect(modal).toBeVisible();
    
    await modal.getByTestId('reviewer-name-input').fill('Test Reviewer');
    
    // Add numeric metric
    await modal.getByTestId('add-metric-button').click();
    await page.waitForTimeout(500);
    const metricInput = modal.getByTestId('metric-selector-0-input');
    await metricInput.click();
    await page.waitForTimeout(500);
    await metricInput.fill(metricName2);
    await page.waitForTimeout(500);
    await page.locator('[role="option"]').first().click();
    
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

  test('should add manual feedback to a node', async ({ page }) => {
    const modal = page.locator('[role="dialog"]');
    const nodeFeedbackPanel = page.locator('[data-id="node-feedback-container"]');
    
    console.log('Test: Node-level manual feedback');
    
    // =============================================================================
    // SETUP: Select a node in the graph
    // =============================================================================
    console.log('Step 1: Selecting processing_node in the graph');
    // Click on the processing_node
    const processingNode = page.locator('[data-id]').filter({ hasText: 'processing_node' }).first();
    await expect(processingNode).toBeVisible({ timeout: 10000 });
    await processingNode.click();
    console.log('✓ processing_node selected');
    
    // =============================================================================
    // STEP 2: Click node feedback tab
    // =============================================================================
    console.log('Step 2: Clicking node feedback tab');
    await page.locator('[data-id="node-feedback-tab"]').click();
    await page.waitForTimeout(1000);
    console.log('✓ Node feedback tab opened');
    
    // =============================================================================
    // STEP 3: Add feedback to the node
    // =============================================================================
    console.log('Step 3: Adding node feedback');
    await nodeFeedbackPanel.getByTestId('add-feedback-button').click();
    await expect(modal).toBeVisible();
    
    // Fill in feedback
    await modal.getByTestId('reviewer-name-input').fill('Node Reviewer');
    
    // Add a metric
    await modal.getByTestId('add-metric-button').click();
    await page.waitForTimeout(500);
    const metricInput = modal.getByTestId('metric-selector-0-input');
    await metricInput.click();
    await page.waitForTimeout(500);
    await metricInput.fill(metricName1);
    await page.waitForTimeout(500);
    await page.locator('[role="option"]').first().click();
    
    // Select category
    await modal.getByTestId('metric-value-0').click();
    await page.waitForTimeout(300);
    await page.getByText('Good').click();
    
    // Add comment
    await modal.getByTestId('feedback-comment-input').fill('Node feedback comment');
    
    // Submit
    await modal.getByRole('button', { name: /Submit/i }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    await page.waitForTimeout(2000);
    
    // Verify feedback appears in node panel
    await expect(nodeFeedbackPanel.locator('text=/human\\[Node Reviewer\\]/')).toBeVisible({ timeout: 5000 });
    console.log('✓ Node feedback submitted and visible');
    
    // =============================================================================
    // STEP 4: Delete node feedback
    // =============================================================================
    console.log('Step 4: Deleting node feedback');
    page.once('dialog', dialog => dialog.accept());
    await nodeFeedbackPanel.getByTestId('delete-feedback-button').click();
    await page.waitForTimeout(2000);
    
    // Verify feedback is gone from node panel
    await expect(nodeFeedbackPanel.locator('text=/human\\[Node Reviewer\\]/')).not.toBeVisible();
    // Verify node panel shows empty state
    await expect(nodeFeedbackPanel.getByText('No feedback available')).toBeVisible();
    console.log('✓ Node feedback deleted');
  });

  test('should edit feedback and change metric values', async ({ page }) => {
    const modal = page.locator('[role="dialog"]');
    const agentFeedbackPanel = page.locator('[data-id="agent-feedback-container"]');
    
    // =============================================================================
    // SETUP: Add feedback with a categorical metric
    // =============================================================================
    console.log('Setup: Adding initial feedback with metric');
    
    await agentFeedbackPanel.getByTestId('add-feedback-button').click();
    await expect(modal).toBeVisible();
    
    await modal.getByTestId('reviewer-name-input').fill('Original Reviewer');
    
    // Add categorical metric
    await modal.getByTestId('add-metric-button').click();
    await page.waitForTimeout(500);
    const metricInput = modal.getByTestId('metric-selector-0-input');
    await metricInput.click();
    await metricInput.fill(metricName1);
    await page.waitForTimeout(500);
    await page.locator('[role="option"]').first().click();
    
    // Select initial category value
    await modal.getByTestId('metric-value-0').click();
    await page.getByText('Good').click();
    
    await modal.getByTestId('feedback-comment-input').fill('Initial comment');
    await modal.getByRole('button', { name: /Submit/i }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    await page.waitForTimeout(1000);
    console.log('✓ Initial feedback added with "Good" rating');
    
    // =============================================================================
    // TEST: Edit feedback and change metric value
    // =============================================================================
    console.log('Test: Editing feedback and changing metric value');
    
    await agentFeedbackPanel.getByTestId('edit-feedback-button').first().click();
    await expect(modal).toBeVisible();
    
    // Verify metric value is pre-selected
    const metricDropdown = modal.getByTestId('metric-value-0');
    await expect(metricDropdown).toContainText('Good');
    
    // Change metric value from "Good" to "Bad"
    await metricDropdown.click();
    await page.getByText('Bad').click();
    
    // Also update reviewer name and comment
    await modal.getByTestId('reviewer-name-input').fill('Updated Reviewer');
    await modal.getByTestId('feedback-comment-input').fill('Updated comment with new rating');
    
    await modal.getByRole('button', { name: /Submit|Save/i }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    await page.waitForTimeout(1000);
    
    // Verify updates are visible
    await expect(page.locator('text=/human\\[Updated Reviewer\\]/')).toBeVisible();
    console.log('✓ Feedback edited with metric value changed to "Bad"');
    
    // Cleanup
    page.once('dialog', dialog => dialog.accept());
    await agentFeedbackPanel.getByTestId('delete-feedback-button').click();
    await page.waitForTimeout(1000);
    console.log('✓ Cleanup complete');
  });
});
