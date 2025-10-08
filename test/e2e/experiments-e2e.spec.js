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
  builderName: 'random-float',
  description: 'Returns a random float for sorting tests.',
};

const failingEvaluator = {
  name: `e2e-failing-eval-${uniqueId}`,
  builderName: 'fail-on-output',
  description: 'Fails when output contains a specific trigger.',
  params: { fail_if_contains: 'trigger-eval-failure' },
};

// Dataset Examples
const examples = [
  {
    // #1: Pure success with long node name path
    input: {
      'long-node-names?': true,
      'run-id': `success-long-${uniqueId}`,
      'output-value': 'A successful run!',
    },
    output: 'A successful run!',
  },
  {
    // #2: Node failure with successful retry
    input: {
      'fail-at-node': 'processing_node',
      'retries-before-success': 1,
      'run-id': `node-fail-retry-${uniqueId}`,
      'output-value': 'Succeeded after one retry.',
    },
    output: 'Succeeded after one retry.',
  },
  {
    // #3: Agent failure (exceeds max retries)
    input: {
      'fail-at-node': 'start',
      'retries-before-success': 5, // Assumes max retries is < 5
      'run-id': `agent-fail-${uniqueId}`,
      'output-value': 'This should never be reached.',
    },
    output: 'N/A',
  },
  {
    // #4: Successful agent run that triggers an evaluator failure
    input: {
      'run-id': `eval-fail-${uniqueId}`,
      'output-value': 'trigger-eval-failure',
    },
    output: 'trigger-eval-failure',
  },
];

// =============================================================================
// TEST SUITE
// =============================================================================

test.describe('Full Experiment Flow with E2E Test Agent', () => {
  test.setTimeout(5 * 60 * 1000); // 5 minutes for the entire flow

  test.afterEach(async ({ page }) => {
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

    // Verify #121: Sort by Eval using dropdown
    console.log('Verifying #121: Sort by evaluator results...');
    
    // Helper to get score capsule values in order from the table
    const getScoreValues = async () => {
      const rows = await resultsTable.locator('tbody tr').all();
      const scores = [];
      for (const row of rows) {
        const scoreCapsule = row.locator('a').filter({ hasText: /score/ });
        const count = await scoreCapsule.count();
        if (count > 0) {
          const capsuleText = await scoreCapsule.first().innerText();
          const scoreMatch = capsuleText.match(/score\s*([\d.]+)/);
          if (scoreMatch) {
            scores.push(parseFloat(scoreMatch[1]));
          } else {
            scores.push(null);
          }
        } else {
          scores.push(null); // For rows without scores
        }
      }
      return scores;
    };

    // Open sort dropdown and select "score"
    const sortDropdown = page.getByTestId('sort-by-dropdown');
    await sortDropdown.click();
    await page.locator('.origin-top-right').getByText('score', { exact: true }).click();
    await page.waitForTimeout(300);
    
    // Verify ascending sort
    let scores = await getScoreValues();
    const validScores = scores.filter(s => s !== null);
    expect(validScores.every((val, i, arr) => i === 0 || val >= arr[i - 1])).toBe(true);
    console.log('Ascending sort by score verified.');
    
    // Click reverse checkbox for descending sort
    await page.getByLabel('Reverse').click();
    await page.waitForTimeout(300);
    scores = await getScoreValues();
    const validScoresDesc = scores.filter(s => s !== null);
    expect(validScoresDesc.every((val, i, arr) => i === 0 || val <= arr[i - 1])).toBe(true);
    console.log('Descending sort by score verified.');
    
    // Test sorting by passed? evaluator
    await sortDropdown.click();
    await page.locator('.origin-top-right').getByText('passed?', { exact: true }).click();
    await page.waitForTimeout(300);
    
    // Uncheck reverse for ascending
    await page.getByLabel('Reverse').click();
    await page.waitForTimeout(300);
    
    // Verify that runs are sorted (nil/missing should come first ascending)
    // Both agent-fail and eval-fail have missing passed? values, so they should be first
    // success-long and node-fail-retry have passed?=true, so they should be last
    const firstRow = resultsTable.locator('tbody tr').first();
    const lastRow = resultsTable.locator('tbody tr').last();
    // First row should be one of the failed runs (agent-fail or eval-fail)
    await expect(firstRow).toContainText(/agent-fail-|eval-fail-/);
    // Last row should be one of the successful runs
    await expect(lastRow).toContainText(/success-long-|node-fail-retry-/);
    console.log('Sort by passed? verified.');
    
    // Reset sort to None
    await sortDropdown.click();
    await page.locator('.origin-top-right').getByText('None', { exact: true }).click();
    await page.waitForTimeout(300);
    
    // Reverse checkbox should disappear when sort is None
    await expect(page.getByLabel('Reverse')).not.toBeVisible();
    console.log('Sort reset verified.');

    await page.goBack(); // Return to experiment results for cleanup phase
    console.log('--- Verification Complete ---');
  });
});

