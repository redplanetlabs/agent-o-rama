import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import {
  getResearchAgentRow,
  createDataset,
  deleteDataset,
  createEvaluator,
  deleteEvaluator,
  addExample,
} from './helpers.js';

// =============================================================================
// TEST CONSTANTS & CONFIGURATION
// =============================================================================
const uniqueId = randomUUID().substring(0, 8);
const datasetName = `e2e-disambiguation-dataset-${uniqueId}`;
const experimentName = `e2e-disambiguation-experiment-${uniqueId}`;
const agentToRun = 'researcher';

// Evaluator definitions
const evaluatorA = {
  name: `Evaluator-A-${uniqueId}`,
  builderName: 'aor/llm-judge',
  description: 'Evaluator A with a "score" metric.',
  outputJsonPath: '$[0].args[0]', // Extract first arg from node output
  params: {
    prompt: 'Just score it.',
    model: 'openai-non-streaming', // This is a mock, doesn't need to exist for UI test
    temperature: '0.0',
    outputSchema: JSON.stringify({
      type: 'object',
      properties: { score: { type: 'integer' } },
    }),
  },
};

const evaluatorB = {
  name: `Evaluator-B-${uniqueId}`,
  builderName: 'aor/llm-judge',
  description: 'Evaluator B, also with a "score" metric to create a conflict.',
  outputJsonPath: '$[0].args[0]', // Extract first arg from node output
  params: {
    prompt: 'Score it differently.',
    model: 'openai-non-streaming',
    temperature: '0.0',
    outputSchema: JSON.stringify({
      type: 'object',
      properties: { score: { type: 'integer' } },
    }),
  },
};

const evaluatorC = {
  name: `Evaluator-C-${uniqueId}`,
  builderName: 'aor/llm-judge',
  description: 'Evaluator C with a unique metric name.',
  outputJsonPath: '$[0].args[0]', // Extract first arg from node output
  params: {
    prompt: 'Give a uniqueness score.',
    model: 'openai-non-streaming',
    temperature: '0.0',
    outputSchema: JSON.stringify({
      type: 'object',
      properties: { uniqueness_score: { type: 'integer' } },
    }),
  },
};


// =============================================================================
// TEST SUITE
// =============================================================================

test.describe('Evaluator Metric Name Disambiguation', () => {
  // This is a long test, so give it a generous timeout.
  test.setTimeout(5 * 60 * 1000); // 5 minutes

  test('should disambiguate metric names only when necessary', async ({ page }) => {
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
    
    // Select Target Type: Node
    await expModal.locator('select').first().selectOption('node');
    
    // Select the agent and node
    await expModal.getByRole('button', { name: /Select an agent/i }).click();
    await expModal.getByText(agentToRun, { exact: true }).click();
    await expModal.getByLabel('Node Name').fill('write-section');

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
    await expModal.getByRole('button', { name: 'Add Evaluator' }).click();
    await page.getByText(evaluatorA.name, { exact: true }).click();
    await expModal.getByRole('button', { name: 'Add Evaluator' }).click();
    await page.getByText(evaluatorB.name, { exact: true }).click();
    await expModal.getByRole('button', { name: 'Add Evaluator' }).click();
    await page.getByText(evaluatorC.name, { exact: true }).click();
    console.log('Experiment form filled with all three evaluators.');

    await expModal.getByRole('button', { name: 'Run Experiment' }).click();
    await expect(expModal).not.toBeVisible();
    console.log('Experiment started.');

    // ---
    // PHASE 3: VERIFICATION
    // ---
    console.log('--- PHASE 3: VERIFICATION ---');
    await expect(page).toHaveURL(/experiments\//, { timeout: 30000 });
    await expect(page.getByText('Completed')).toBeVisible({ timeout: 120000 });
    console.log('Experiment completed.');

    const resultsTable = page.locator('table').filter({ hasText: 'Input' });
    const resultRow = resultsTable.locator('tbody tr').first();
    const outputCell = resultRow.locator('td').nth(2);

    // Assert that the conflicting metric "score" IS disambiguated
    const disambiguatedCapsuleA = outputCell.locator('a').filter({ hasText: new RegExp(`^${evaluatorA.name}/score`) });
    const disambiguatedCapsuleB = outputCell.locator('a').filter({ hasText: new RegExp(`^${evaluatorB.name}/score`) });
    await expect(disambiguatedCapsuleA).toBeVisible();
    await expect(disambiguatedCapsuleB).toBeVisible();
    console.log('Verified: Conflicting metric "score" is disambiguated.');

    // Assert that the unique metric "uniqueness_score" IS NOT disambiguated
    const uniqueCapsule = outputCell.locator('a').filter({ hasText: /^uniqueness_score$/ });
    await expect(uniqueCapsule).toBeVisible();
    console.log('Verified: Unique metric "uniqueness_score" is NOT disambiguated.');
    
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

