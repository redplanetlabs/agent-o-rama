import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getE2ETestAgentRow, addExample, deleteDataset, createEvaluator, deleteEvaluator, addEvaluatorToExperiment, shouldSkipCleanup} from './helpers.js';

// =============================================================================
// TEST SUITE
// =============================================================================

test.describe('Dataset Example Tagging and Bulk Operations', () => {

  test('should handle bulk tagging and deletion of examples', async ({ page }) => {
    const uniqueId = randomUUID().substring(0, 8);
    const datasetName = `Tagging Test Dataset ${uniqueId}`;
    const tagA = `tag-A-${uniqueId}`;
    const tagB = `tag-B-${uniqueId}`;

    // --- 1. SETUP: Navigate and create a new dataset ---
    console.log('--- Starting Test Setup ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();

    await page.getByText('Datasets & Experiments').click();
    await expect(page).toHaveURL(new RegExp(`/agents/.*com\\.rpl\\.agent\\.e2e-test-agent.*E2ETestAgentModule.*/datasets`));

    // Create a new dataset
    await page.getByRole('button', { name: 'Create Dataset' }).first().click();
    const createModal = page.locator('[role="dialog"]');
    await createModal.getByLabel('Name').fill(datasetName);
    await createModal.getByRole('button', { name: 'Create Dataset' }).click();
    await expect(createModal).not.toBeVisible();
    await expect(page.getByText(datasetName)).toBeVisible();
    console.log(`Created dataset: ${datasetName}`);

    // Navigate into the new dataset
    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();
    await expect(page.getByRole('heading', { name: datasetName })).toBeVisible();
    console.log('--- Test Setup Complete ---');


    // --- 2. CREATE EXAMPLES ---
    console.log('--- Creating Examples ---');
    const example1 = { input: { "run-id": `ex1-${uniqueId}`, "output-value": "output for example 1" }, output: "output for example 1" };
    const example2 = { input: { "run-id": `ex2-${uniqueId}`, "output-value": "output for example 2" }, output: "output for example 2" };
    const example3 = { input: { "run-id": `ex3-${uniqueId}`, "output-value": "output for example 3" }, output: "output for example 3" };

    await addExample(page, { ...example1, searchText: example1.input["run-id"] });
    await addExample(page, { ...example2, searchText: example2.input["run-id"] });
    await addExample(page, { ...example3, searchText: example3.input["run-id"] });

    const row1 = page.locator('table tbody tr').filter({ hasText: example1.input["run-id"] });
    const row2 = page.locator('table tbody tr').filter({ hasText: example2.input["run-id"] });
    const row3 = page.locator('table tbody tr').filter({ hasText: example3.input["run-id"] });
    console.log('--- Examples Created ---');


    // --- 3. SELECT EXAMPLES AND ADD TAG A ---
    console.log('--- Testing Add Tag A ---');
    // Select examples 1 and 3
    await row1.locator('td').first().click();
    await row3.locator('td').first().click();

    await page.getByRole('button', { name: 'Add Tag...' }).click();
    const tagModal = page.locator('[role="dialog"]');
    await expect(tagModal).toBeVisible();
    await tagModal.getByLabel('Tag to add').fill(tagA);
    await tagModal.getByRole('button', { name: 'Add Tag' }).click();
    await expect(tagModal).not.toBeVisible();

    // Verify tags
    await expect(row1.locator('td').nth(3)).toHaveText(new RegExp(tagA));
    await expect(row2.locator('td').nth(3)).not.toHaveText(new RegExp(tagA));
    await expect(row3.locator('td').nth(3)).toHaveText(new RegExp(tagA));
    console.log('--- Tag A Added and Verified ---');

    // Deselect all by clicking the header checkbox twice
    const headerCheckbox = page.locator('table thead th').first();
    await headerCheckbox.click();
    await headerCheckbox.click();

    // --- 4. SELECT EXAMPLES AND ADD TAG B ---
    console.log('--- Testing Add Tag B ---');
    // Select examples 1 and 3 again
    await row1.locator('td').first().click();
    await row3.locator('td').first().click();

    await page.getByRole('button', { name: 'Add Tag...' }).click();
    await expect(tagModal).toBeVisible();
    await tagModal.getByLabel('Tag to add').fill(tagB);
    await tagModal.getByRole('button', { name: 'Add Tag' }).click();
    await expect(tagModal).not.toBeVisible();

    // Verify both tags are present on selected rows
    await expect(row1.locator('td').nth(3)).toHaveText(new RegExp(`${tagA}, ${tagB}|${tagB}, ${tagA}`));
    await expect(row2.locator('td').nth(3)).not.toHaveText(new RegExp(`${tagA}|${tagB}`));
    await expect(row3.locator('td').nth(3)).toHaveText(new RegExp(`${tagA}, ${tagB}|${tagB}, ${tagA}`));
    console.log('--- Tag B Added and Verified ---');


    // --- 5. SELECT EXAMPLES AND REMOVE TAG A ---
    console.log('--- Testing Remove Tag A ---');
    // Examples 1 and 3 are still selected from the previous step

    await page.getByRole('button', { name: 'Remove Tag...' }).click();
    await expect(tagModal).toBeVisible();

    // The remove modal has a select dropdown, not a text input
    await tagModal.locator('select').selectOption(tagA);
    await tagModal.getByRole('button', { name: 'Remove Tag' }).click();
    await expect(tagModal).not.toBeVisible();

    // Verify tag A is removed, but tag B remains
    await expect(row1.locator('td').nth(3)).not.toHaveText(new RegExp(tagA));
    await expect(row1.locator('td').nth(3)).toHaveText(new RegExp(tagB));
    await expect(row2.locator('td').nth(3)).not.toHaveText(new RegExp(`${tagA}|${tagB}`));
    await expect(row3.locator('td').nth(3)).not.toHaveText(new RegExp(tagA));
    await expect(row3.locator('td').nth(3)).toHaveText(new RegExp(tagB));
    console.log('--- Tag A Removed and Verified ---');


    // --- 6. SELECT ALL AND DELETE ---
    console.log('--- Testing Bulk Deletion ---');
    page.on('dialog', dialog => dialog.accept()); // Auto-accept confirm dialog

    // Select all examples on the page
    await headerCheckbox.click();

    await page.getByRole('button', { name: 'Delete Selected' }).click();

    // Verify all rows are gone
    await expect(row1).not.toBeVisible();
    await expect(row2).not.toBeVisible();
    await expect(row3).not.toBeVisible();
    await expect(page.getByText('No examples yet.')).toBeVisible();
    console.log('--- Bulk Deletion Verified ---');


    // --- 7. CLEANUP: Delete the dataset ---
    console.log('--- Starting Cleanup ---');
    await page.getByText('Datasets & Experiments').click();
    await expect(page).toHaveURL(/datasets/);
    await deleteDataset(page, datasetName);
    console.log('--- Cleanup Complete ---');
  });

  test('should show and use experiment buttons when examples are selected', async ({ page }) => {
    const uniqueId = randomUUID().substring(0, 8);
    const datasetName = `Experiment Buttons Test ${uniqueId}`;
    const evaluatorName = `e2e-test-evaluator-${uniqueId}`;
    const agentToRun = 'E2ETestAgent';

    // --- 1. SETUP ---
    console.log('--- Starting Test Setup ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();

    // Create evaluator first
    await page.getByText('Evaluators').click();
    await createEvaluator(page, {
      name: evaluatorName,
      builderName: 'random-float',
      description: 'Test evaluator for button tests',
    });

    // Create dataset and examples
    await page.getByText('Datasets & Experiments').click();
    await page.getByRole('button', { name: 'Create Dataset' }).first().click();
    const createModal = page.locator('[role="dialog"]');
    await createModal.getByLabel('Name').fill(datasetName);
    await createModal.getByRole('button', { name: 'Create Dataset' }).click();
    await expect(createModal).not.toBeVisible();

    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();

    const example1 = { input: { "run-id": `ex1-${uniqueId}`, "output-value": "output 1" }, output: "output 1" };
    const example2 = { input: { "run-id": `ex2-${uniqueId}`, "output-value": "output 2" }, output: "output 2" };
    const example3 = { input: { "run-id": `ex3-${uniqueId}`, "output-value": "output 3" }, output: "output 3" };

    await addExample(page, { ...example1, searchText: example1.input["run-id"] });
    await addExample(page, { ...example2, searchText: example2.input["run-id"] });
    await addExample(page, { ...example3, searchText: example3.input["run-id"] });
    console.log('--- Test Setup Complete ---');

    // --- 2. VERIFY BUTTONS APPEAR WHEN EXAMPLES SELECTED ---
    console.log('--- Testing Button Visibility ---');
    
    // Initially, buttons should not be visible (no selection)
    await expect(page.getByRole('button', { name: 'Run Experiment', exact: true })).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'Run Comparative Experiment' })).not.toBeVisible();

    // Select examples
    const row1 = page.locator('table tbody tr').filter({ hasText: example1.input["run-id"] });
    const row2 = page.locator('table tbody tr').filter({ hasText: example2.input["run-id"] });
    await row1.locator('td').first().click();
    await row2.locator('td').first().click();

    // Buttons should now be visible
    await expect(page.getByRole('button', { name: 'Run Experiment', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Run Comparative Experiment' })).toBeVisible();
    console.log('--- Buttons Visible After Selection ---');

    // --- 3. TEST RUN EXPERIMENT BUTTON ---
    console.log('--- Testing Run Experiment Button ---');
    await page.getByRole('button', { name: 'Run Experiment', exact: true }).click();
    
    const expModal = page.locator('[role="dialog"]');
    await expect(expModal).toBeVisible();

    // Verify the form is pre-configured
    await expect(expModal.getByLabel(/Only the 2 selected examples/)).toBeChecked();
    console.log('Verified selector is pre-set to selected examples');

    // Fill out the experiment form
    await expModal.getByLabel('Experiment Name').fill(`Regular Exp ${uniqueId}`);
    await expModal.getByTestId('agent-name-dropdown').click();
    await expModal.getByText(agentToRun, { exact: true }).click();
    await expModal.locator('div').filter({ hasText: /^Input Arguments/ }).getByRole('textbox').fill('$');
    await addEvaluatorToExperiment(page, expModal, evaluatorName);
    
    await expModal.getByRole('button', { name: 'Run Experiment' }).click();
    await expect(expModal).not.toBeVisible();

    // Wait for experiment to complete
    await expect(page).toHaveURL(/experiments\//, { timeout: 30000 });
    await expect(page.getByText('Completed').first()).toBeVisible({ timeout: 120000 });

    // Verify it ran on 2 examples
    const summaryTable = page.locator('table').filter({ hasText: '# Examples' });
    await expect(summaryTable.locator('td').first().locator('div').nth(1)).toHaveText('2');
    console.log('--- Run Experiment Button Test Complete ---');

    // Navigate back to examples
    await page.getByText('Datasets & Experiments').click();
    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();

    // --- 4. TEST RUN COMPARATIVE EXPERIMENT BUTTON ---
    console.log('--- Testing Run Comparative Experiment Button ---');
    
    // Select examples again (selection was cleared)
    await row1.locator('td').first().click();
    await row2.locator('td').first().click();

    await page.getByRole('button', { name: 'Run Comparative Experiment' }).click();
    
    const compModal = page.locator('[role="dialog"]');
    await expect(compModal).toBeVisible();

    // Verify the form is pre-configured for comparative
    await expect(compModal.getByLabel(/Only the 2 selected examples/)).toBeChecked();
    await expect(compModal.getByRole('heading', { name: 'Target 1' })).toBeVisible();
    await expect(compModal.getByRole('heading', { name: 'Target 2' })).toBeVisible();
    console.log('Verified comparative form is pre-set with 2 targets and selected examples');

    // Close the modal without running
    await compModal.getByRole('button', { name: '×' }).click();
    await expect(compModal).not.toBeVisible();
    console.log('--- Run Comparative Experiment Button Test Complete ---');

    // --- 5. CLEANUP ---
    console.log('--- Starting Cleanup ---');
    page.on('dialog', dialog => dialog.accept());
    
    await page.getByText('Datasets & Experiments').click();
    await deleteDataset(page, datasetName);
    
    await page.getByText('Evaluators').click();
    await deleteEvaluator(page, evaluatorName);
    console.log('--- Cleanup Complete ---');
  });

});
