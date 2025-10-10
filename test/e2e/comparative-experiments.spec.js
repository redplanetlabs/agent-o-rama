// test/e2e/comparative-experiments.spec.js
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
const datasetName = `e2e-comparative-dataset-${uniqueId}`;
const experimentName = `e2e-comparative-experiment-${uniqueId}`;
const agentToRun = 'E2ETestAgent';

const winningEvaluator = {
  name: `e2e-select-longest-${uniqueId}`,
  builderName: 'select-longest',
  description: 'Comparative evaluator that returns an index.',
};

const otherEvaluator = {
  name: `e2e-random-float-comp-${uniqueId}`,
  builderName: 'random-float-comparative',
  description: 'Comparative evaluator that does NOT return an index.',
};

const example = {
  input: {
    target1_output: 'short output',
    target2_output: 'this is the longest output and should be the winner',
    target3_output: 'a medium length output',
  },
  output: 'some reference output',
};

// =============================================================================
// TEST SUITE
// =============================================================================

test.describe('Comparative Experiment Flow', () => {
  test.setTimeout(5 * 60 * 1000); // 5 minutes

  test('should create, run, and verify a comparative experiment', async ({ page }) => {
    // ---
    // PHASE 1: SETUP
    // ---
    console.log('--- PHASE 1: SETUP ---');
    await page.goto('/');
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();

    // Create Evaluators
    await page.getByText('Evaluators').click();
    await createEvaluator(page, winningEvaluator);
    await createEvaluator(page, otherEvaluator);

    // Create Dataset and Example
    await page.getByText('Datasets & Experiments').click();
    await createDataset(page, datasetName);
    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();
    await addExample(page, example);
    console.log('Setup complete: All resources created.');

    // ---
    // PHASE 2: EXECUTION
    // ---
    console.log('--- PHASE 2: EXECUTION ---');
    await page.getByRole('link', { name: 'Comparative Experiments' }).click();
    await expect(page.getByRole('heading', { name: 'Comparative Experiments', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Run New Comparative Experiment' }).click();

    const expModal = page.locator('[role="dialog"]');
    await expect(expModal).toBeVisible();

    // Verify form defaults to 2 targets for comparative experiments
    await expect(expModal.getByRole('heading', { name: 'Target 1' })).toBeVisible();
    await expect(expModal.getByRole('heading', { name: 'Target 2' })).toBeVisible();
    console.log('Verified comparative experiment form defaults to 2 targets.');

    await expModal.getByLabel('Experiment Name').fill(experimentName);

    // Configure Target 1
    const target0 = expModal.locator('.bg-gray-50.border.rounded-lg').filter({ hasText: 'Target 1' }).first();
    await target0.getByTestId('agent-name-dropdown').click();
    await target0.getByText(agentToRun, { exact: true }).click();
    await target0.locator('div').filter({ hasText: /^Input Mappings/ }).getByRole('textbox').fill('{"output-value": "$.target1_output"}');

    // Configure Target 2
    const target1 = expModal.locator('.bg-gray-50.border.rounded-lg').filter({ hasText: 'Target 2' }).first();
    await target1.getByTestId('agent-name-dropdown').click();
    await target1.getByText(agentToRun, { exact: true }).click();
    await target1.locator('div').filter({ hasText: /^Input Mappings/ }).getByRole('textbox').fill('{"output-value": "$.target2_output"}');
    
    // Add and Configure Target 3
    await expModal.getByRole('button', { name: 'Add Another Target' }).click();
    const target2 = expModal.locator('.bg-gray-50.border.rounded-lg').filter({ hasText: 'Target 3' }).first();
    await expect(target2).toBeVisible();
    await target2.getByTestId('agent-name-dropdown').click();
    await target2.getByText(agentToRun, { exact: true }).click();
    await target2.locator('div').filter({ hasText: /^Input Mappings/ }).getByRole('textbox').fill('{"output-value": "$.target3_output"}');
    console.log('Configured 3 targets for the experiment.');

    // Configure Evaluators
    await expModal.getByRole('button', { name: 'Add Evaluator' }).click();
    const evaluatorDropdown = page.locator('.origin-top-left');
    await expect(evaluatorDropdown.getByText(winningEvaluator.name, { exact: true })).toBeVisible();
    await expect(evaluatorDropdown.getByText(otherEvaluator.name, { exact: true })).toBeVisible();
    await expect(evaluatorDropdown.getByText('aor/conciseness')).not.toBeVisible(); // Verify filtering
    await evaluatorDropdown.getByText(winningEvaluator.name, { exact: true }).click();
    
    await expModal.getByRole('button', { name: 'Add Evaluator' }).click();
    await evaluatorDropdown.getByText(otherEvaluator.name, { exact: true }).click();
    console.log('Evaluators configured.');

    // Run Experiment
    await expModal.getByRole('button', { name: 'Run Experiment' }).click();
    await expect(expModal).not.toBeVisible();
    console.log('Experiment started.');

    // Wait for redirect to comparative experiments list and then navigate to the experiment
    await expect(page).toHaveURL(/comparative-experiments$/, { timeout: 30000 });
    await page.getByRole('row').filter({ hasText: experimentName }).click();
    await expect(page.getByText('Completed').first()).toBeVisible({ timeout: 120000 });
    console.log('Experiment completed.');

    // ---
    // PHASE 3: VERIFICATION
    // ---
    console.log('--- PHASE 3: VERIFICATION ---');
    
    // Verify no summary stats table is present
    await expect(page.locator('table').filter({ hasText: '# Examples' })).not.toBeVisible();
    console.log('Verified: No summary stats table is displayed.');

    // Verify table structure and content
    const resultsTable = page.locator('table').filter({ hasText: 'Input' });
    await expect(resultsTable.locator('th').nth(0)).toHaveText('Input');
    await expect(resultsTable.locator('th').nth(1)).toHaveText('Reference Output');
    await expect(resultsTable.locator('th').nth(2)).toHaveText('Output 1');
    await expect(resultsTable.locator('th').nth(3)).toHaveText('Output 2');
    await expect(resultsTable.locator('th').nth(4)).toHaveText('Output 3');
    await expect(resultsTable.locator('th').nth(5)).toHaveText('Evals');

    const resultRow = resultsTable.locator('tbody tr').first();
    const output1Cell = resultRow.locator('td').nth(2);
    const output2Cell = resultRow.locator('td').nth(3);
    const output3Cell = resultRow.locator('td').nth(4);
    const evalsCell = resultRow.locator('td').nth(5);

    await expect(output1Cell).toContainText(example.input.target1_output);
    await expect(output2Cell).toContainText(example.input.target2_output);
    await expect(output3Cell).toContainText(example.input.target3_output);
    console.log('Verified: Output columns contain the correct agent results.');

    // Verify winner highlighting
    await expect(output1Cell).not.toHaveClass(/bg-green-50/);
    await expect(output2Cell).toHaveClass(/bg-green-50/); // Output 2 is the winner
    await expect(output3Cell).not.toHaveClass(/bg-green-50/);
    console.log('Verified: "Winner" output cell is correctly highlighted.');

    // Verify "Evals" column content
    await expect(evalsCell.locator('a').filter({ hasText: new RegExp(winningEvaluator.name) })).not.toBeVisible();
    await expect(evalsCell.locator('a').filter({ hasText: new RegExp(otherEvaluator.name) })).toBeVisible();
    await expect(evalsCell.locator('a').filter({ hasText: /random_score/ })).toBeVisible();
    console.log('Verified: "Evals" column correctly displays non-indexing evaluator results.');

    // ---
    // PHASE 4: TEARDOWN
    // ---
    console.log('--- PHASE 4: TEARDOWN ---');
    page.on('dialog', dialog => dialog.accept());

    await page.getByText('Datasets & Experiments').click();
    await deleteDataset(page, datasetName);

    await page.getByText('Evaluators').click();
    await deleteEvaluator(page, winningEvaluator.name);
    await deleteEvaluator(page, otherEvaluator.name);

    console.log('--- Test successfully completed and cleaned up. ---');
  });
});

