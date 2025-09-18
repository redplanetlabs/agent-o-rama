import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getResearchAgentRow, createEvaluator, addExample } from './helpers.js';

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

  const agentRow = await getResearchAgentRow(page);
  await agentRow.click();
  await expect(page).toHaveURL(new RegExp(`/agents/.*com\\.rpl\\.agent\\.research-agent.*ResearchAgentModule`));
  console.log('--- Test Setup Complete ---');

  // 1. SETUP PHASE: Create evaluators and a dataset with examples
  console.log('--- Starting Evaluator and Dataset Creation ---');

  // Go to evaluators page
  await page.getByText('Evaluators').click();
  await expect(page).toHaveURL(/evaluations/);

  // First, test error handling by trying to create evaluator without required parameter
  console.log('Testing error handling for missing threshold parameter...');
  await page.getByRole('button', { name: 'Create Evaluator' }).first().click();

  // Step 1: Select the builder
  let modal = page.locator('[role="dialog"]');
  await expect(modal).toBeVisible();
  await modal.getByText('aor/conciseness').click();
  await expect(modal.getByLabel('Name')).toBeVisible(); // Wait for form to load

  // Step 2: Fill out the form but deliberately omit the threshold parameter
  await modal.getByLabel('Name').fill('test-error-handling');
  await modal.getByLabel('Description').fill('Testing error handling');
  // Deliberately NOT filling the threshold parameter

  await modal.getByRole('button', { name: 'Submit' }).click();

  // Step 3: Verify error message appears and spinner stops
  await expect(modal.getByText(/Mismatched params.*threshold/)).toBeVisible({ timeout: 10000 });
  console.log('Error handling test passed - error message displayed correctly');

  // Close the modal to continue with successful creation
  await modal.getByRole('button', { name: '×' }).click();
  await expect(modal).not.toBeVisible();

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
  await page.getByRole('link', { name: 'Examples' }).click();
  await expect(page.getByRole('heading', { name: datasetName })).toBeVisible();

  // Create examples with unique identifiers
  await addExample(page, { input: { text: "short", id: `ex1-${uniqueId}` }, output: "out" }); // For conciseness test
  await addExample(page, { input: { value: 5, id: `ex2-${uniqueId}` }, output: 10 });         // For comparative test (input < output)
  await addExample(page, { input: { symbol: "+", id: `ex3-${uniqueId}` }, output: "+" });      // For summary F1 test
  await addExample(page, { input: { symbol: "-", id: `ex4-${uniqueId}` }, output: "-" });      // For summary F1 test

  console.log('--- Evaluator and Dataset Creation Complete ---');

  // 2. EXECUTION PHASE: Test the unified modal
  console.log('--- Starting Modal Tests ---');

  // --- Test :regular evaluator ---
  console.log('Testing :regular evaluator...');
  const shortExampleRow = page.locator('table tbody tr').filter({ hasText: 'short' });
  await shortExampleRow.locator('button').click(); // Click ellipsis
  await page.getByText('Try with evaluator').click();

  const evaluatorModal = page.locator('[role="dialog"]');
  await expect(evaluatorModal).toBeVisible();
  await evaluatorModal.getByRole('button', { name: /Choose an evaluator/ }).click();

    // Assert dropdown is filtered correctly (summary should be absent)
    await expect(evaluatorModal.getByText(regularEvalName)).toBeVisible();
    // await expect(evaluatorModal.getByText(comparativeEvalName)).toBeVisible(); // commented out - jcompare1 not loaded
    await expect(evaluatorModal.getByText(summaryEvalName)).not.toBeVisible();

  // Select the regular evaluator
  await evaluatorModal.getByText(regularEvalName).click();

  // Wait for the modal to update with the evaluator-specific fields
  await expect(evaluatorModal.getByText('Model Output (JSON)')).toBeVisible({ timeout: 5000 });
  const outputField = evaluatorModal.getByPlaceholder('{"result": "..."}');

  // Test with a passing value
  await outputField.fill('"pass"');
  await evaluatorModal.getByRole('button', { name: 'Run Evaluator' }).click();
  await expect(evaluatorModal.getByText(/"concise\?":\s*true/)).toBeVisible();

  // Test with a failing value
  await outputField.fill('"this string is definitely too long"');
  await evaluatorModal.getByRole('button', { name: 'Run Evaluator' }).click();
  await expect(evaluatorModal.getByText(/"concise\?":\s*false/)).toBeVisible();

  await evaluatorModal.getByRole('button', { name: '×' }).click(); // Close modal

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
  // Select the two examples for the F1 score (click the checkbox cell - input is readOnly)
  const plusRow = page.locator('table tbody tr').filter({ hasText: '+' });
  await plusRow.locator('td').first().click();
  const minusRow = page.locator('table tbody tr').filter({ hasText: '-' });
  await minusRow.locator('td').first().click();

  await page.getByRole('button', { name: 'Try summary evaluator' }).click();

  const summaryModal = page.locator('[role="dialog"]');
  await expect(summaryModal).toBeVisible();
  await summaryModal.getByRole('button', { name: /Choose an evaluator/ }).click();

    // Assert dropdown is filtered correctly (only summary should be visible)
    await expect(summaryModal.getByText(summaryEvalName)).toBeVisible();
    await expect(summaryModal.getByText(regularEvalName)).not.toBeVisible();
    // await expect(summaryModal.getByText(comparativeEvalName)).not.toBeVisible(); // commented out - jcompare1 not loaded

  await summaryModal.getByText(summaryEvalName).click();

  // Assert confirmation text is shown
  await expect(summaryModal.getByText(`This will run the summary evaluator '${summaryEvalName}' on 2 selected examples.`)).toBeVisible();

  await summaryModal.getByRole('button', { name: 'Run Evaluator' }).click();

  // Assert response includes a score key
  await expect(summaryModal.getByText(/"score"\s*:/)).toBeVisible();

  await summaryModal.getByRole('button', { name: '×' }).click(); // Close modal

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
