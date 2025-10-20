import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import {
  getResearchAgentRow,
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
const agentToRun = 'researcher';

// Evaluator definitions
// - Two conciseness evaluators produce the same "concise?" metric → SHOULD be disambiguated
// - One custom evaluator produces unique "contains-keyword?" metric → should NOT be disambiguated
const evaluatorA = {
  name: `Evaluator-A-${uniqueId}`,
  builderName: 'aor/conciseness',
  description: 'Evaluator A - produces "concise?" metric with threshold 100.',
  outputJsonPath: '$[0].args[0]', // Extract first arg from node output
  params: {
    threshold: '100',
  },
};

const evaluatorB = {
  name: `Evaluator-B-${uniqueId}`,
  builderName: 'aor/conciseness',
  description: 'Evaluator B - produces "concise?" metric with threshold 200.',
  outputJsonPath: '$[0].args[0]', // Extract first arg from node output
  params: {
    threshold: '200',
  },
};

const evaluatorC = {
  name: `Evaluator-C-${uniqueId}`,
  builderName: 'contains-keyword',
  description: 'Evaluator C - produces unique "contains-keyword?" metric.',
  outputJsonPath: '$[0].args[0]', // Extract first arg from node output
  params: {
    keyword: 'the', // Common word likely to appear in output
  },
};


// =============================================================================
// TEST SUITE
// =============================================================================

test.describe('Evaluator Metric Name Disambiguation', () => {
  // This is a long test, so give it a generous timeout.
  test.setTimeout(5 * 60 * 1000); // 5 minutes

  test('should disambiguate conflicting metrics but not unique ones', async ({ page }) => {
    // ---
    // PHASE 1: SETUP
    // ---
    console.log('--- PHASE 1: SETUP ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getResearchAgentRow(page);
    await agentRow.click();
    
    // Create all three evaluators
    await page.getByText('Evaluators').click();
    await createEvaluator(page, evaluatorA);
    await createEvaluator(page, evaluatorB);
    await createEvaluator(page, evaluatorC);
    console.log('All three test evaluators created.');

    // Create dataset and add an example
    // Note: For node experiments, input is [persona, messages, context] (3 args for write-section node)
    await page.getByText('Datasets & Experiments').click();
    await createDataset(page, datasetName);
    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();
    await addExample(page, { 
      input: ["disambiguation-test-persona", [], `disambiguation-test-${uniqueId}`], 
      output: "expected-output" 
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
    
    // Select Target Type: Node (radio button)
    await expModal.getByLabel('Node').check();
    
    // Select the agent
    await expModal.getByTestId('agent-name-dropdown').click();
    await expModal.getByText(agentToRun, { exact: true }).click();
    
    // Select the node from dropdown
    await expModal.getByTestId('node-name-dropdown').click();
    await expModal.getByText('write-section', { exact: true }).click();

    // Configure input mappings for node (expects 3 args: persona, messages, context)
    const mappingsSection = expModal.locator('div').filter({ hasText: /^Input Mappings/ });
    // Click Add Mapping until we have 3 inputs
    for (let i = await mappingsSection.locator('input').count(); i < 3; i++) {
      await expModal.getByRole('button', { name: 'Add Mapping' }).click();
    }
    await mappingsSection.locator('input').nth(0).fill('$[0]'); // persona
    await mappingsSection.locator('input').nth(1).fill('$[1]'); // messages
    await mappingsSection.locator('input').nth(2).fill('$[2]'); // context
    console.log('Node experiment configured for write-section with 3 input mappings.');

    // Select all three evaluators
    await addEvaluatorToExperiment(page, expModal, evaluatorA.name);
    await addEvaluatorToExperiment(page, expModal, evaluatorB.name);
    await addEvaluatorToExperiment(page, expModal, evaluatorC.name);
    console.log('Experiment form filled with all three evaluators.');

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

    // Assert that the two conflicting "concise?" metrics ARE disambiguated
    const disambiguatedCapsuleA = outputCell.locator('a').filter({ hasText: new RegExp(`^${evaluatorA.name}/concise\\?`) });
    const disambiguatedCapsuleB = outputCell.locator('a').filter({ hasText: new RegExp(`^${evaluatorB.name}/concise\\?`) });
    await expect(disambiguatedCapsuleA).toBeVisible();
    await expect(disambiguatedCapsuleB).toBeVisible();
    console.log('Verified: Conflicting "concise?" metrics are disambiguated with evaluator name prefixes.');

    // Assert that the unique "contains-keyword?" metric is NOT disambiguated
    const uniqueCapsule = outputCell.locator('a').filter({ hasText: /^contains-keyword\?/ });
    await expect(uniqueCapsule).toBeVisible();
    console.log('Verified: Unique "contains-keyword?" metric is NOT disambiguated (no prefix).');
    
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
    await deleteEvaluator(page, evaluatorC.name);

    console.log('--- Test successfully completed and cleaned up. ---');
  });
});

