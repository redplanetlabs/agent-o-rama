import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import {
  getE2ETestAgentRow,
  createDataset,
  deleteDataset,
  createEvaluator,
  deleteEvaluator,
  addExample,
  addEvaluatorToExperiment,
} from './helpers.js';

// =============================================================================
// TEST CONSTANTS & CONFIGURATION
// =============================================================================
const uniqueId = randomUUID().substring(0, 8);
const datasetName = `e2e-disambiguation-dataset-${uniqueId}`;
const experimentName = `e2e-disambiguation-experiment-${uniqueId}`;

// Evaluator definitions
// - Two evaluators produce the same "score" metric → SHOULD be disambiguated
const evaluatorA = {
  name: `Evaluator-A-${uniqueId}`,
  builderName: 'score-by-length',
  description: 'Evaluator A - produces "score" metric based on string length.',
  outputJsonPath: '$',
};

const evaluatorB = {
  name: `Evaluator-B-${uniqueId}`,
  builderName: 'score-by-vowel-count',
  description: 'Evaluator B - produces "score" metric based on vowel count.',
  outputJsonPath: '$',
};


// =============================================================================
// TEST SUITE
// =============================================================================

test.describe('Evaluator Metric Name Disambiguation', () => {
  // This is a long test, so give it a generous timeout.
  test.setTimeout(5 * 60 * 1000); // 5 minutes

  test('should disambiguate conflicting metrics', async ({ page }) => {
    // ---
    // PHASE 1: SETUP
    // ---
    console.log('--- PHASE 1: SETUP ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();
    
    // Create two evaluators that both produce "score" metric
    await page.getByText('Evaluators').click();
    await createEvaluator(page, evaluatorA);
    await createEvaluator(page, evaluatorB);
    console.log('Test evaluators created.');

    // Create dataset and add an example
    await page.getByText('Datasets & Experiments').click();
    await createDataset(page, datasetName);
    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();
    await addExample(page, { 
      input: { 
        "run-id": `disambiguation-test-${uniqueId}`, 
        "output-value": "A test sentence with vowels." 
      }, 
      output: "A test sentence with vowels."
    });
    console.log('Dataset with example created.');

    // ---
    // PHASE 2: EXECUTION
    // ---
    console.log('--- PHASE 2: EXECUTION ---');
    await page.getByRole('link', { name: 'Experiments', exact: true }).click();
    await page.getByRole('button', { name: 'Run New Experiment' }).click();

    const expModal = page.locator('[role="dialog"]');
    await expect(expModal).toBeVisible();

    await expModal.getByLabel('Experiment Name').fill(experimentName);
    
    // Select the E2ETestAgent
    await expModal.getByTestId('agent-name-dropdown').click();
    await expModal.getByText('E2ETestAgent', { exact: true }).click();
    
    // Configure input mappings (E2ETestAgent expects a map, so we use $ to pass the whole input)
    await expModal.locator('div').filter({ hasText: /^Input Arguments/ }).getByRole('textbox').fill('$');
    console.log('Agent experiment configured for E2ETestAgent.');

    // Select both evaluators
    await addEvaluatorToExperiment(page, expModal, evaluatorA.name);
    await addEvaluatorToExperiment(page, expModal, evaluatorB.name);
    console.log('Experiment form filled with both evaluators.');

    await expModal.getByRole('button', { name: 'Run Experiment' }).click();
    await expect(expModal).not.toBeVisible();
    console.log('Experiment started.');

    // ---
    // PHASE 3: VERIFICATION
    // ---
    console.log('--- PHASE 3: VERIFICATION ---');
    await expect(page).toHaveURL(/experiments\//, { timeout: 30000 });
    await expect(page.getByText('Completed').first()).toBeVisible({ timeout: 120000 });
    console.log('Experiment completed.');

    const resultsTable = page.locator('table').filter({ hasText: 'Input' });
    const resultRow = resultsTable.locator('tbody tr').first();
    const outputCell = resultRow.locator('td').nth(2);

    // Assert that the two conflicting "score" metrics ARE disambiguated
    const disambiguatedCapsuleA = outputCell.locator('a').filter({ hasText: new RegExp(`^${evaluatorA.name}/score`) });
    const disambiguatedCapsuleB = outputCell.locator('a').filter({ hasText: new RegExp(`^${evaluatorB.name}/score`) });
    await expect(disambiguatedCapsuleA).toBeVisible();
    await expect(disambiguatedCapsuleB).toBeVisible();
    console.log('Verified: Conflicting "score" metrics are disambiguated with evaluator name prefixes.');
    
    // ---
    // PHASE 4: TEARDOWN
    // ---
    console.log('--- PHASE 4: TEARDOWN ---');
    page.on('dialog', dialog => dialog.accept());

    await page.getByText('Datasets & Experiments').click();
    await deleteDataset(page, datasetName);

    await page.getByText('Evaluators').click();
    await deleteEvaluator(page, evaluatorA.name);
    await deleteEvaluator(page, evaluatorB.name);

    console.log('--- Test successfully completed and cleaned up. ---');
  });
});

