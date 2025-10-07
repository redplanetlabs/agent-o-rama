import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import {
  getE2ETestAgentRow,
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
const datasetName = `e2e-full-flow-dataset-${uniqueId}`;
const experimentName = `e2e-full-flow-experiment-${uniqueId}`;
const agentToRun = 'E2ETestAgent';

// Evaluator Definitions
const randomFloatEvaluator = {
  name: `e2e-random-float-${uniqueId}`,
  builderName: 'e2e/random-float',
  description: 'Returns a random float for sorting tests.',
};

const failingEvaluator = {
  name: `e2e-failing-eval-${uniqueId}`,
  builderName: 'e2e/fail-on-output',
  description: 'Fails when output contains a specific trigger.',
  params: { fail_if_contains: 'trigger-eval-failure' },
};

// Dataset Examples
const examples = [
  {
    // #1: Pure success with long node name path
    input: {
      control_params: {
        'long-node-names?': true,
        'run-id': `success-long-${uniqueId}`,
        'output-value': 'A successful run!',
      },
    },
    reference_output: 'A successful run!',
  },
  {
    // #2: Node failure with successful retry
    input: {
      control_params: {
        'fail-at-node': 'processing_node',
        'retries-before-success': 1,
        'run-id': `node-fail-retry-${uniqueId}`,
        'output-value': 'Succeeded after one retry.',
      },
    },
    reference_output: 'Succeeded after one retry.',
  },
  {
    // #3: Agent failure (exceeds max retries)
    input: {
      control_params: {
        'fail-at-node': 'start',
        'retries-before-success': 5, // Assumes max retries is < 5
        'run-id': `agent-fail-${uniqueId}`,
        'output-value': 'This should never be reached.',
      },
    },
    reference_output: 'N/A',
  },
  {
    // #4: Successful agent run that triggers an evaluator failure
    input: {
      control_params: {
        'run-id': `eval-fail-${uniqueId}`,
        'output-value': 'trigger-eval-failure',
      },
    },
    reference_output: 'trigger-eval-failure',
  },
];

// =============================================================================
// TEST SUITE
// =============================================================================

test.describe('Full Experiment Flow with E2E Test Agent', () => {
  test.setTimeout(5 * 60 * 1000); // 5 minutes for the entire flow

  test.afterAll(async ({ page }) => {
    console.log('--- Starting Cleanup ---');
    page.on('dialog', (dialog) => dialog.accept());

    await page.goto('/');
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();
    
    // Delete dataset (which will delete the experiment)
    await page.getByText('Datasets & Experiments').click();
    await deleteDataset(page, datasetName);

    // Delete evaluators
    await page.getByText('Evaluators').click();
    await deleteEvaluator(page, randomFloatEvaluator.name);
    await deleteEvaluator(page, failingEvaluator.name);
    
    console.log('--- Cleanup Complete ---');
  });

  test('should verify experiment results UI for filtering, sorting, and failure display', async ({ page }) => {
    // --- PHASE 1: SETUP ---
    console.log('--- PHASE 1: SETUP ---');
    await page.goto('/');
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();
    
    // Create Evaluators
    await page.getByText('Evaluators').click();
    await createEvaluator(page, randomFloatEvaluator);
    await createEvaluator(page, failingEvaluator);

    // Create Dataset and Examples
    await page.getByText('Datasets & Experiments').click();
    await createDataset(page, datasetName);
    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();
    for (const ex of examples) {
      await addExample(page, ex);
    }
    await expect(page.locator('table tbody tr')).toHaveCount(4);
    console.log('Setup complete: All resources created.');

    // --- PHASE 2: RUN EXPERIMENT ---
    console.log('--- PHASE 2: RUN EXPERIMENT ---');
    await page.getByRole('link', { name: 'Experiments', exact: true }).click();
    await page.getByRole('button', { name: 'Run New Experiment' }).click();

    const expModal = page.locator('[role="dialog"]');
    await expModal.getByLabel('Experiment Name').fill(experimentName);
    await expModal.getByTestId('agent-name-dropdown').click();
    await expModal.getByText(agentToRun, { exact: true }).click();
    await expModal.locator('div').filter({ hasText: /^Input Mappings/ }).getByRole('textbox').fill('$');
    await expModal.getByRole('button', { name: 'Add Evaluator' }).click();
    await page.getByText(randomFloatEvaluator.name, { exact: true }).click();
    await expModal.getByRole('button', { name: 'Add Evaluator' }).click();
    await page.getByText(failingEvaluator.name, { exact: true }).click();
    
    await expModal.getByRole('button', { name: 'Run Experiment' }).click();
    console.log('Experiment started...');

    // --- PHASE 3: VERIFICATION ---
    console.log('--- PHASE 3: VERIFICATION ---');
    await expect(page).toHaveURL(/experiments\//, { timeout: 30000 });
    await expect(page.getByText('Completed').first()).toBeVisible({ timeout: 120000 });
    console.log('Experiment completed.');

    // Verify #101: Failure Filter
    console.log('Verifying #101: Failure filter...');
    const resultsTable = page.locator('table').filter({ hasText: 'Input' });
    await expect(resultsTable.locator('tbody tr')).toHaveCount(4);

    await page.getByRole('button', { name: 'Failure' }).click();
    await expect(resultsTable.locator('tbody tr')).toHaveCount(2);
    await expect(resultsTable.getByText(`agent-fail-${uniqueId}`)).toBeVisible();
    await expect(resultsTable.getByText(`eval-fail-${uniqueId}`)).toBeVisible();
    
    await page.getByRole('button', { name: 'Success' }).click();
    await expect(resultsTable.locator('tbody tr')).toHaveCount(2);
    await expect(resultsTable.getByText(`success-long-${uniqueId}`)).toBeVisible();
    await expect(resultsTable.getByText(`node-fail-retry-${uniqueId}`)).toBeVisible();
    
    await page.getByRole('button', { name: 'All' }).click();
    await expect(resultsTable.locator('tbody tr')).toHaveCount(4);
    console.log('Failure filter verified.');

    // Verify #102: Evaluator Failure Display
    console.log('Verifying #102: Evaluator failure display...');
    const evalFailRow = resultsTable.locator('tr').filter({ hasText: `eval-fail-${uniqueId}` });
    const failedCapsule = evalFailRow.locator('a').filter({ hasText: new RegExp(failingEvaluator.name) });
    await expect(failedCapsule).toBeVisible();
    await expect(failedCapsule).toHaveClass(/bg-red-100/);
    await expect(failedCapsule).toContainText('Failed');
    
    // Check that both agent and eval trace links exist
    await expect(evalFailRow.getByTitle(/View execution trace/)).toBeVisible();
    await expect(failedCapsule).toHaveAttribute('href', /invocations\//);
    console.log('Evaluator failure display verified.');

    // Verify #121: Sort by Eval
    console.log('Verifying #121: Sort by evaluator results...');
    const scoreHeader = page.getByRole('columnheader', { name: 'score' });
    
    // Helper to get scores from the table
    const getScores = async () => {
      const scoreCells = await resultsTable.locator('td:nth-child(8)').all(); // Assuming score is 8th col
      return Promise.all(scoreCells.map(async cell => parseFloat(await cell.innerText())));
    };

    // Ascending sort
    await scoreHeader.click();
    await page.waitForTimeout(500); // Wait for sort animation
    let scores = await getScores();
    expect(scores.every((val, i, arr) => i === 0 || val >= arr[i - 1])).toBe(true);
    console.log('Ascending sort verified.');
    
    // Descending sort
    await scoreHeader.click();
    await page.waitForTimeout(500);
    scores = await getScores();
    expect(scores.every((val, i, arr) => i === 0 || val <= arr[i - 1])).toBe(true);
    console.log('Descending sort verified.');

    // Verify #118: Long Node Names
    console.log('Verifying #118: Long node names...');
    const longNameRow = resultsTable.locator('tr').filter({ hasText: `success-long-${uniqueId}` });
    await longNameRow.getByTitle(/View execution trace/).click();
    
    await expect(page).toHaveURL(/\/invocations\//, { timeout: 30000 });
    const longNode = page.locator('.react-flow__node').filter({ hasText: /a_very_long_node_name/ });
    await expect(longNode).toBeVisible({ timeout: 10000 });
    
    // Check if the text is truncated (usually by checking if the full text isn't directly visible but the element is)
    const nodeLabel = longNode.locator('div').first(); // The visible part of the node
    const fullText = await nodeLabel.innerText();
    expect(fullText.length).toBeLessThan(100); // The full name is > 100 chars
    console.log('Long node name rendering verified in trace.');

    await page.goBack(); // Return to experiment results for cleanup phase
    console.log('--- Verification Complete ---');
  });
});

