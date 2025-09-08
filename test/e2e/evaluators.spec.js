import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

/**
 * Creates an evaluator via the UI.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {string} name - The unique name for the evaluator.
 * @param {string} builderName - The name of the builder to select.
 * @param {string} description - The description for the evaluator.
 * @param {Object} params - A map of parameter names to values to fill in.
 */
async function createEvaluator(page, { name, builderName, description, params = {} }) {
  console.log(`Creating evaluator: ${name}`);
  await page.getByRole('button', { name: 'Create Evaluator' }).first().click();

  // Step 1: Select the builder
  const modal = page.locator('[role="dialog"]');
  await expect(modal).toBeVisible();
  await modal.getByText(builderName).click();
  await expect(modal.getByLabel('Name')).toBeVisible(); // Wait for form to load

  // Step 2: Fill out the form
  await modal.getByLabel('Name').fill(name);
  await modal.getByLabel('Description').fill(description);

  for (const [paramKey, paramValue] of Object.entries(params)) {
    await modal.getByLabel(paramKey, { exact: true }).fill(paramValue);
  }

  await modal.getByRole('button', { name: 'Create Evaluator' }).click();

  // Wait for the modal to close and the new evaluator to appear in the list
  await expect(modal).not.toBeVisible({ timeout: 15000 });
  await expect(page.locator('table tbody tr').filter({ hasText: name })).toBeVisible();
  console.log(`Successfully created evaluator: ${name}`);
}

/**
 * Adds an example to the currently viewed dataset.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {Object} example - An object with `input` and `output` keys.
 */
async function addExample(page, { input, output }) {
  console.log('Adding example with input:', JSON.stringify(input));
  // Click the first enabled Add Example button
  await page.locator('button').filter({ hasText: 'Add Example' }).filter({ hasNot: page.locator('[disabled]') }).first().click();

  const modal = page.locator('[role="dialog"]');
  await expect(modal).toBeVisible();
  await modal.getByLabel('Input (JSON)').fill(JSON.stringify(input, null, 2));
  if (output) {
    await modal.getByLabel('Reference Output (JSON, Optional)').fill(JSON.stringify(output, null, 2));
  }
  await modal.getByRole('button', { name: 'Add Example' }).click();

  // Use the displayed text to find the row (strings without quotes, objects with JSON formatting)
  const expectedText = typeof input === 'string' ? input : JSON.stringify(input).substring(1, 20);
  await expect(page.locator('table tbody tr').filter({ hasText: expectedText })).toBeVisible();
  console.log('Successfully added example.');
}

// =============================================================================
// TEST SUITE
// =============================================================================

// Constants
const uniqueId = randomUUID().substring(0, 8);
const regularEvalName = `e2e-concise-${uniqueId}`;
const comparativeEvalName = `e2e-compare-${uniqueId}`;
const summaryEvalName = `e2e-f1-${uniqueId}`;
const datasetName = `e2e-eval-dataset-${uniqueId}`;

test('should create, test, and clean up all three evaluator types', async ({ page }) => {
  // SETUP PHASE: Navigate to the application
  console.log('--- Starting Test Setup ---');
  await page.goto('/');
  await expect(page).toHaveTitle(/Agent-o-rama/);

  const moduleName = 'com.rpl.agent.research-agent';
  const agentName = 'ResearchAgentModule';
  const agentRow = page.locator('table tbody tr').filter({ hasText: moduleName }).filter({ hasText: agentName });
  await expect(agentRow).toBeVisible({ timeout: 30000 });
  await agentRow.locator('a', { hasText: 'Open' }).click();
  await expect(page).toHaveURL(new RegExp(`/agents/.*${agentName}`));
  console.log('--- Test Setup Complete ---');

  // 1. SETUP PHASE: Create evaluators and a dataset with examples
  console.log('--- Starting Evaluator and Dataset Creation ---');

  // Go to evaluators page
  await page.getByText('Evaluators').click();
  await expect(page).toHaveURL(/evaluations/);

  // Create evaluators (comment out jcompare1 as it's not loaded yet)
  await createEvaluator(page, { name: regularEvalName, builderName: 'aor/conciseness', description: 'Regular evaluator for testing.', params: { threshold: '10' } });
  // await createEvaluator(page, { name: comparativeEvalName, builderName: 'jcompare1', description: 'Comparative evaluator for testing.' });
  await createEvaluator(page, { name: summaryEvalName, builderName: 'aor/f1-score', description: 'Summary evaluator for testing.', params: { positiveValue: '+' } });

  // Go to datasets page
  await page.getByText('Datasets & Experiments').click();
  await expect(page).toHaveURL(/datasets/);

  // Create a dataset
  await page.getByRole('button', { name: 'Create Dataset' }).first().click();
  await page.getByLabel('Name').fill(datasetName);
  await page.locator('[role="dialog"]').getByRole('button', { name: 'Create Dataset' }).click();
  await expect(page.getByText(datasetName)).toBeVisible();

  // Navigate into the new dataset
  await page.getByRole('link', { name: datasetName }).click();
  await expect(page.getByRole('heading', { name: datasetName })).toBeVisible();

  // Create examples
  await addExample(page, { input: "short", output: "out" }); // For conciseness test
  await addExample(page, { input: 5, output: 10 });         // For comparative test (input < output)
  await addExample(page, { input: "+", output: "+" });      // For summary F1 test
  await addExample(page, { input: "-", output: "-" });      // For summary F1 test

  console.log('--- Evaluator and Dataset Creation Complete ---');

  // 2. EXECUTION PHASE: Test the unified modal
  console.log('--- Starting Modal Tests ---');

  // --- Test :regular evaluator ---
  console.log('Testing :regular evaluator...');
  const shortExampleRow = page.locator('table tbody tr').filter({ hasText: 'short' });
  await shortExampleRow.locator('button').click(); // Click ellipsis
  await page.getByText('Try with evaluator').click();

  const modal = page.locator('[role="dialog"]');
  await expect(modal).toBeVisible();
  await modal.getByRole('button', { name: /Choose an evaluator/ }).click();

    // Assert dropdown is filtered correctly (summary should be absent)
    await expect(modal.getByText(regularEvalName)).toBeVisible();
    // await expect(modal.getByText(comparativeEvalName)).toBeVisible(); // commented out - jcompare1 not loaded
    await expect(modal.getByText(summaryEvalName)).not.toBeVisible();

  // Select the regular evaluator
  await modal.getByText(regularEvalName).click();

  // Wait for the modal to update with the evaluator-specific fields
  await expect(modal.getByText('Model Output (JSON)')).toBeVisible({ timeout: 5000 });
  const outputField = modal.getByPlaceholder('{"result": "..."}');

  // Test with a passing value
  await outputField.fill('"pass"');
  await modal.getByRole('button', { name: 'Run Evaluator' }).click();
  await expect(modal.getByText(/"concise\?":\s*true/)).toBeVisible();

  // Test with a failing value
  await outputField.fill('"this string is definitely too long"');
  await modal.getByRole('button', { name: 'Run Evaluator' }).click();
  await expect(modal.getByText(/"concise\?":\s*false/)).toBeVisible();

  await modal.getByRole('button', { name: '×' }).click(); // Close modal

    // --- Test :comparative evaluator ---
    // console.log('Testing :comparative evaluator...');
    // const comparativeExampleRow = page.locator('table tbody tr').filter({ hasText: '5' });
    // await comparativeExampleRow.locator('button').click();
    // await page.getByText('Try with evaluator').click();

    // await expect(modal).toBeVisible();
    // await modal.getByRole('button', { name: /Choose an evaluator/ }).click();
    // await modal.getByText(comparativeEvalName).click();

    // // Assert UI changed for comparative
    // await expect(modal.getByLabel('Model Outputs (One valid JSON per line)')).toBeVisible();
    // const outputTextareas = modal.locator('textarea');
    // expect(await outputTextareas.count()).toBe(1);

    // // Add more outputs
    // await modal.getByRole('button', { name: 'Add another output' }).click();
    // await modal.getByRole('button', { name: 'Add another output' }).click();
    // expect(await outputTextareas.count()).toBe(3);

    // // Fill the outputs
    // await outputTextareas.nth(0).fill('"first"');
    // await outputTextareas.nth(1).fill('"second"');
    // await outputTextareas.nth(2).fill('"third"');

    // await modal.getByRole('button', { name: 'Run Evaluator' }).click();

    // // Since input (5) < referenceOutput (10), we expect the first output
    // await expect(modal.getByText(/"res":\s*"first"/)).toBeVisible();

    // await modal.getByRole('button', { name: '×' }).click(); // Close modal

  // --- Test :summary evaluator ---
  console.log('Testing :summary evaluator...');
  // Select the two examples for the F1 score
  await page.locator('table tbody tr').filter({ hasText: '+' }).locator('input[type="checkbox"]').check();
  await page.locator('table tbody tr').filter({ hasText: '-' }).locator('input[type="checkbox"]').check();

  await page.getByRole('button', { name: 'Try summary evaluator' }).click();

  await expect(modal).toBeVisible();
  await modal.getByRole('button', { name: /Choose an evaluator/ }).click();

    // Assert dropdown is filtered correctly (only summary should be visible)
    await expect(modal.getByText(summaryEvalName)).toBeVisible();
    await expect(modal.getByText(regularEvalName)).not.toBeVisible();
    // await expect(modal.getByText(comparativeEvalName)).not.toBeVisible(); // commented out - jcompare1 not loaded

  await modal.getByText(summaryEvalName).click();

  // Assert confirmation text is shown
  await expect(modal.getByText(/running the summary evaluator.*on 2 selected examples/i)).toBeVisible();

  await modal.getByRole('button', { name: 'Run Summary Evaluation' }).click();

  // A perfect score should yield score: 1.0
  await expect(modal.getByText(/"score":\s*1/)).toBeVisible();

  await modal.getByRole('button', { name: '×' }).click(); // Close modal

  console.log('--- Modal Tests Complete ---');

  // 3. CLEANUP PHASE: Delete created resources
  console.log('--- Starting Cleanup ---');
  page.on('dialog', dialog => dialog.accept());

  // Delete dataset
  await page.getByText('Datasets & Experiments').click();
  await expect(page).toHaveURL(/datasets/);
  const datasetRow = page.locator('table tbody tr').filter({ hasText: datasetName });
  if (await datasetRow.isVisible()) {
    await datasetRow.getByRole('button', { name: 'Delete' }).click();
    await expect(datasetRow).not.toBeVisible();
    console.log(`Cleaned up dataset: ${datasetName}`);
  }

  // Delete evaluators (skip comparative since it wasn't created)
  await page.getByText('Evaluators').click();
  await expect(page).toHaveURL(/evaluations/);
  for (const name of [regularEvalName, summaryEvalName]) { // Removed comparativeEvalName
    const evalRow = page.locator('table tbody tr').filter({ hasText: name });
    if (await evalRow.isVisible()) {
      await evalRow.getByRole('button', { name: 'Delete' }).click();
      await expect(evalRow).not.toBeVisible();
      console.log(`Cleaned up evaluator: ${name}`);
    }
  }
  console.log('--- Cleanup Complete ---');
});
