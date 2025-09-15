import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { 
  getResearchAgentRow, 
  createEvaluator, 
  addExample, 
  createDataset, 
  deleteDataset, 
  deleteEvaluator 
} from './helpers.js';

// =============================================================================
// TEST SUITE
// =============================================================================

const uniqueId = randomUUID().substring(0, 8);
const datasetName = `e2e-exp-fail-dataset-${uniqueId}`;
const regularEvalName = `e2e-fail-regular-eval-${uniqueId}`;
const experimentName = `e2e-failed-experiment-${uniqueId}`;

test.describe('Experiment Validation Error Handling', () => {

  test('should display a validation error when a comparative experiment is run with a regular evaluator', async ({ page }) => {
    // -------------------------------------------------------------------------
    // 1. SETUP PHASE: Create all necessary resources (dataset, examples, evaluators)
    // -------------------------------------------------------------------------
    console.log('--- Starting Test Setup ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getResearchAgentRow(page);
    await agentRow.click();

    // Go to evaluators page and create a regular evaluator
    await page.getByText('Evaluators').click();
    await createEvaluator(page, {
      name: regularEvalName,
      builderName: 'aor/conciseness',
      params: { threshold: '100' },
    });

    // Go to datasets page to create a dataset and example
    await page.getByText('Datasets & Experiments').click();
    await expect(page.getByRole('heading', { name: 'Datasets' })).toBeVisible();
    
    await createDataset(page, datasetName);
    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();
    await addExample(page, { input: { id: `ex1-${uniqueId}` } });
    console.log('--- Test Setup Complete ---');


    // -------------------------------------------------------------------------
    // 2. TRIGGER FAILURE: Configure and run an invalid experiment
    // -------------------------------------------------------------------------
    console.log('--- Triggering Experiment Failure ---');

    await page.getByRole('link', { name: 'Experiments', exact: true }).click();
    await page.getByRole('button', { name: 'Run New Experiment' }).click();

    const expModal = page.locator('[role="dialog"]');
    await expect(expModal).toBeVisible();

    // Fill out the form to create an invalid configuration
    await expModal.getByLabel('Experiment Name').fill(experimentName);
    
    // Select "Comparative" experiment type
    await expModal.getByLabel('Comparative (A/B Test Multiple Targets)').check();
    
    // Add a second target to make it a valid comparative spec structure
    await expModal.getByRole('button', { name: 'Add Another Target' }).click();

    // Configure both targets to use the same agent (common for prompt testing)
    const agentSelectors = expModal.locator('button').filter({ hasText: 'Select an agent' });
    await agentSelectors.nth(0).click();
    await expModal.getByText('researcher').click();
    // scroll to the second agent selector
    await agentSelectors.nth(1).scrollIntoViewIfNeeded();
    await agentSelectors.nth(1).click();
    // nth(1) because nth(0) is the first agent selector, being selected above
    await expModal.getByText('researcher').nth(1).click();
    
    // Select the REGULAR evaluator for this COMPARATIVE experiment
    await expModal.getByRole('button', { name: 'Add Evaluator' }).click();
    await expModal.getByText(regularEvalName, { exact: true }).click();

    // Run the experiment, which should fail validation
    await expModal.getByRole('button', { name: 'Run Experiment' }).click();
    await expect(expModal).not.toBeVisible();
    console.log('--- Invalid Experiment Submitted ---');


    // -------------------------------------------------------------------------
    // 3. VERIFICATION: Check for the error message in the UI
    // -------------------------------------------------------------------------
    console.log('--- Verifying Error Display ---');
    // Wait for navigation to the experiment results page.
    await expect(page.getByText(experimentName, { exact: true })).toBeVisible({ timeout: 15000 });
    const experimentRow = page.locator('table tbody tr').filter({ hasText: experimentName });
    await expect(experimentRow).toBeVisible();
    await experimentRow.click();

    await expect(page.getByText('❌ Failed')).toBeVisible();

    // Assert that the specific error panel is visible.
    const errorPanel = page.locator('.bg-red-50.p-6.rounded-lg.border.border-red-200');
    await expect(errorPanel).toBeVisible();

    // Assert that the correct error message is displayed.
    await expect(errorPanel.getByText('Problem with one or more evaluators')).toBeVisible();
    await expect(errorPanel.getByText('Evaluator type does not match experiment')).toBeVisible();
    await expect(errorPanel.getByText(`Evaluator: ${regularEvalName} (type: regular)`)).toBeVisible();
    console.log('--- Error Display Verified ---');

    
    // -------------------------------------------------------------------------
    // 4. CLEANUP: Delete the created resources
    // -------------------------------------------------------------------------
    console.log('--- Starting Cleanup ---');
    page.on('dialog', dialog => dialog.accept());

    // Delete dataset (which also deletes experiments)
    await page.getByText('Datasets & Experiments').click();
    await expect(page).toHaveURL(/datasets/);
    await deleteDataset(page, datasetName);

    // Delete evaluator
    await page.getByText('Evaluators').click();
    await expect(page).toHaveURL(/evaluations/);
    await deleteEvaluator(page, regularEvalName);
    console.log('--- Cleanup Complete ---');
  });
});
