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
  addEvaluatorToExperiment,
} from './helpers.js';

// =============================================================================
// TEST CONSTANTS & CONFIGURATION
// =============================================================================
const uniqueId = randomUUID().substring(0, 8);
const datasetName = `e2e-comparative-dataset-${uniqueId}`;
const experimentName = `e2e-comparative-experiment-${uniqueId}`;
const agentToRun = 'E2ETestAgent';

const selectLongestEvaluator = {
  name: `e2e-select-longest-${uniqueId}`,
  builderName: 'select-longest',
  description: 'Comparative evaluator that selects the longest output.',
};

const selectRandomEvaluator = {
  name: `e2e-select-random-${uniqueId}`,
  builderName: 'select-random',
  description: 'Comparative evaluator that randomly selects an output.',
};

const otherEvaluator = {
  name: `e2e-random-float-comp-${uniqueId}`,
  builderName: 'random-float-comparative',
  description: 'Comparative evaluator that does NOT return an index.',
};

// Multiple examples to test different winners
const examples = [
  {
    input: {
      "run-id": `ex1-${uniqueId}`,
      "output-value": "example 1 output",
      target1_output: 'short',
      target2_output: 'this is the longest output and should be the winner for example 1',
      target3_output: 'medium length',
    },
    output: 'reference output 1',
  },
  {
    input: {
      "run-id": `ex2-${uniqueId}`,
      "output-value": "example 2 output",
      target1_output: 'this is the longest output and should be the winner for example 2',
      target2_output: 'short',
      target3_output: 'medium length',
    },
    output: 'reference output 2',
  },
  {
    input: {
      "run-id": `ex3-${uniqueId}`,
      "output-value": "example 3 output",
      target1_output: 'short',
      target2_output: 'medium length',
      target3_output: 'this is the longest output and should be the winner for example 3',
    },
    output: 'reference output 3',
  },
];

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
    await createEvaluator(page, selectLongestEvaluator);
    await createEvaluator(page, selectRandomEvaluator);
    await createEvaluator(page, otherEvaluator);

    // Create Dataset and Examples
    await page.getByText('Datasets & Experiments').click();
    await createDataset(page, datasetName);
    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();
    for (const example of examples) {
      await addExample(page, example);
    }
    console.log('Setup complete: All resources created with 3 examples.');

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
    await target0.locator('div').filter({ hasText: /^Input Arguments/ }).getByRole('textbox').fill('{"output-value": "$.target1_output"}');

    // Configure Target 2
    const target1 = expModal.locator('.bg-gray-50.border.rounded-lg').filter({ hasText: 'Target 2' }).first();
    await target1.getByTestId('agent-name-dropdown').click();
    await target1.getByText(agentToRun, { exact: true }).click();
    await target1.locator('div').filter({ hasText: /^Input Arguments/ }).getByRole('textbox').fill('{"output-value": "$.target2_output"}');
    
    // Add and Configure Target 3
    await expModal.getByRole('button', { name: 'Add Another Target' }).click();
    const target2 = expModal.locator('.bg-gray-50.border.rounded-lg').filter({ hasText: 'Target 3' }).first();
    await expect(target2).toBeVisible();
    await target2.getByTestId('agent-name-dropdown').click();
    await target2.getByText(agentToRun, { exact: true }).click();
    await target2.locator('div').filter({ hasText: /^Input Arguments/ }).getByRole('textbox').fill('{"output-value": "$.target3_output"}');
    console.log('Configured 3 targets for the experiment.');

    // Configure Evaluators (only 1 selector + 1 non-selector for first run)
    await addEvaluatorToExperiment(page, expModal, selectLongestEvaluator.name);
    await addEvaluatorToExperiment(page, expModal, otherEvaluator.name);
    console.log('Evaluators configured (1 selector + 1 non-selector for first run).');

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

    // Verify table structure
    const resultsTable = page.locator('table').filter({ hasText: 'Input' });
    await expect(resultsTable.locator('th').nth(0)).toHaveText('Input');
    await expect(resultsTable.locator('th').nth(1)).toHaveText('Reference Output');
    await expect(resultsTable.locator('th').nth(2)).toHaveText('Output 1');
    await expect(resultsTable.locator('th').nth(3)).toHaveText('Output 2');
    await expect(resultsTable.locator('th').nth(4)).toHaveText('Output 3');
    await expect(resultsTable.locator('th').nth(5)).toHaveText('Evals');
    console.log('Verified: Table structure with dynamic output columns.');

    // Verify all three result rows
    const allRows = resultsTable.locator('tbody tr');
    await expect(allRows).toHaveCount(3);
    console.log('Verified: All 3 examples are displayed.');

    // Verify Row 1: Output 2 should win (longest)
    const row1 = allRows.nth(0);
    await expect(row1.locator('td').nth(2)).toContainText('short');
    await expect(row1.locator('td').nth(3)).toContainText('winner for example 1');
    await expect(row1.locator('td').nth(4)).toContainText('medium length');
    await expect(row1.locator('td').nth(2)).not.toHaveClass(/bg-green-50/);
    await expect(row1.locator('td').nth(3)).toHaveClass(/bg-green-50/); // Winner
    await expect(row1.locator('td').nth(4)).not.toHaveClass(/bg-green-50/);
    console.log('Verified: Row 1 - Output 2 is highlighted as winner.');

    // Verify Row 2: Output 1 should win (longest)
    const row2 = allRows.nth(1);
    await expect(row2.locator('td').nth(2)).toContainText('winner for example 2');
    await expect(row2.locator('td').nth(3)).toContainText('short');
    await expect(row2.locator('td').nth(4)).toContainText('medium length');
    await expect(row2.locator('td').nth(2)).toHaveClass(/bg-green-50/); // Winner
    await expect(row2.locator('td').nth(3)).not.toHaveClass(/bg-green-50/);
    await expect(row2.locator('td').nth(4)).not.toHaveClass(/bg-green-50/);
    console.log('Verified: Row 2 - Output 1 is highlighted as winner.');

    // Verify Row 3: Output 3 should win (longest)
    const row3 = allRows.nth(2);
    await expect(row3.locator('td').nth(2)).toContainText('short');
    await expect(row3.locator('td').nth(3)).toContainText('medium length');
    await expect(row3.locator('td').nth(4)).toContainText('winner for example 3');
    await expect(row3.locator('td').nth(2)).not.toHaveClass(/bg-green-50/);
    await expect(row3.locator('td').nth(3)).not.toHaveClass(/bg-green-50/);
    await expect(row3.locator('td').nth(4)).toHaveClass(/bg-green-50/); // Winner
    console.log('Verified: Row 3 - Output 3 is highlighted as winner.');

    // Verify Selector Evaluator Dropdown (Single Selector Case)
    await expect(page.getByText('Highlighting:')).toBeVisible();
    const selectorDropdown = page.getByTestId('selector-evaluator-dropdown');
    await expect(selectorDropdown).toBeVisible();
    await expect(selectorDropdown).toContainText(selectLongestEvaluator.name);
    await expect(selectorDropdown).not.toContainText('None');
    console.log('Verified: Selector evaluator dropdown is visible even with only one selector.');
    
    // Verify dropdown only shows one selector evaluator
    await selectorDropdown.click();
    let dropdownMenu = page.locator('.origin-top-right');
    await expect(dropdownMenu.getByText(selectLongestEvaluator.name, { exact: true })).toBeVisible();
    await expect(dropdownMenu.getByText(selectRandomEvaluator.name, { exact: true })).not.toBeVisible();
    await page.keyboard.press('Escape'); // Close dropdown
    console.log('Verified: Dropdown shows only the single selector evaluator.');

    // Verify "Evals" column content (check any row)
    const evalsCell = row1.locator('td').nth(5);
    // The selector evaluator (with "index" key) should NOT appear in the Evals column
    await expect(evalsCell.locator('a').filter({ hasText: /index/ })).not.toBeVisible();
    await expect(evalsCell.locator('a').filter({ hasText: /longest_value/ })).not.toBeVisible();
    // The non-selector evaluator should appear, showing just the metric name (no collision)
    await expect(evalsCell.locator('a').filter({ hasText: /random_score/ })).toBeVisible();
    console.log('Verified: "Evals" column correctly displays non-indexing evaluator results.');

    // ---
    // PHASE 3.5: RE-RUN WITH MULTIPLE SELECTOR EVALUATORS
    // ---
    console.log('--- PHASE 3.5: RE-RUN WITH MULTIPLE SELECTORS ---');
    
    // Click Re-run Experiment button
    await page.getByRole('button', { name: 'Re-run Experiment' }).click();
    const rerunModal = page.locator('[role="dialog"]').filter({ hasText: 'Run Experiment' });
    await expect(rerunModal).toBeVisible();
    console.log('Re-run modal opened with pre-filled form state.');
    
    // Verify existing evaluators are present
    await expect(rerunModal.getByText(selectLongestEvaluator.name)).toBeVisible();
    await expect(rerunModal.getByText(otherEvaluator.name)).toBeVisible();
    console.log('Verified: Existing evaluators are pre-filled in the form.');
    
    // Add the second selector evaluator
    await addEvaluatorToExperiment(page, rerunModal, selectRandomEvaluator.name);
    console.log('Added second selector evaluator to the form.');
    
    // Update experiment name to distinguish from first run
    const nameField = rerunModal.getByLabel('Experiment Name');
    await nameField.clear();
    await nameField.fill(experimentName + ' (multi-selector)');
    
    // Run the updated experiment
    await rerunModal.getByRole('button', { name: 'Run Experiment' }).click();
    await expect(rerunModal).not.toBeVisible();
    console.log('Re-run experiment started.');
    
    // Navigate to the new experiment
    await expect(page).toHaveURL(/comparative-experiments$/, { timeout: 30000 });
    await page.getByRole('row').filter({ hasText: experimentName + ' (multi-selector)' }).click();
    await expect(page.getByText('Completed').first()).toBeVisible({ timeout: 120000 });
    console.log('Re-run experiment completed.');
    
    // Verify Selector Evaluator Dropdown (Multiple Selectors Case)
    await expect(page.getByText('Highlighting:')).toBeVisible();
    const multiSelectorDropdown = page.getByTestId('selector-evaluator-dropdown');
    await expect(multiSelectorDropdown).toBeVisible();
    console.log('Verified: Selector evaluator dropdown is visible with multiple selectors.');
    
    // Test switching between selector evaluators
    await multiSelectorDropdown.click();
    const multiDropdownMenu = page.locator('.origin-top-right');
    await expect(multiDropdownMenu.getByText(selectLongestEvaluator.name, { exact: true })).toBeVisible();
    await expect(multiDropdownMenu.getByText(selectRandomEvaluator.name, { exact: true })).toBeVisible();
    console.log('Verified: Dropdown shows both selector evaluators.');
    
    // Switch to the random selector
    await multiDropdownMenu.getByText(selectRandomEvaluator.name, { exact: true }).click();
    await expect(multiSelectorDropdown).toContainText(selectRandomEvaluator.name);
    console.log('Verified: Successfully switched to random selector evaluator.');
    
    // Switch back to longest selector
    await multiSelectorDropdown.click();
    await multiDropdownMenu.getByText(selectLongestEvaluator.name, { exact: true }).click();
    await expect(multiSelectorDropdown).toContainText(selectLongestEvaluator.name);
    console.log('Verified: Successfully switched back to longest selector evaluator.');
    
    // Verify results are still correct with multiple selectors
    const multiResultsTable = page.locator('table').filter({ hasText: 'Input' });
    const multiRow1 = multiResultsTable.locator('tbody tr').nth(0);
    await expect(multiRow1.locator('td').nth(3)).toHaveClass(/bg-green-50/); // Output 2 should still win
    console.log('Verified: Winner highlighting still works correctly with multiple selectors.');

    // ---
    // PHASE 4: TEARDOWN
    // ---
    console.log('--- PHASE 4: TEARDOWN ---');
    page.on('dialog', dialog => dialog.accept());

    await page.getByText('Datasets & Experiments').click();
    await deleteDataset(page, datasetName);

    await page.getByText('Evaluators').click();
    await deleteEvaluator(page, selectLongestEvaluator.name);
    await deleteEvaluator(page, selectRandomEvaluator.name);
    await deleteEvaluator(page, otherEvaluator.name);

    console.log('--- Test successfully completed and cleaned up. ---');
  });
});

