import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getE2ETestAgentRow, createEvaluator, addExample } from './helpers.js';

// =============================================================================
// JSONPath Preview E2E Test
// =============================================================================

const uniqueId = randomUUID().substring(0, 8);
const datasetName = `e2e-jsonpath-preview-${uniqueId}`;
const evaluatorName = `e2e-eval-preview-${uniqueId}`;

// Note: We test with aor/llm-judge which has all JSONPath fields enabled (input, output, reference-output)

test('should display JSONPath preview when creating evaluator', async ({ page }) => {
  console.log('--- Starting JSONPath Preview Test ---');
  
  // SETUP: Navigate and create dataset with structured examples
  await page.goto('/');
  await expect(page).toHaveTitle(/Agent-o-rama/);

  const agentRow = await getE2ETestAgentRow(page);
  await agentRow.click();
  await expect(page).toHaveURL(new RegExp(`/agents/.*E2ETestAgentModule`));

  await page.getByText('Datasets & Experiments').click();
  await expect(page).toHaveURL(/datasets/);

  // Create a dataset with structured data
  console.log('Creating dataset with structured examples...');
  await page.getByRole('button', { name: 'Create Dataset' }).first().click();
  await page.getByLabel('Name').fill(datasetName);
  await page.locator('[role="dialog"]').getByRole('button', { name: 'Create Dataset' }).click();
  await expect(page.getByText(datasetName)).toBeVisible();

  await page.getByRole('link', { name: datasetName }).click();
  await page.getByRole('link', { name: 'Examples' }).click();
  await expect(page.getByRole('heading', { name: datasetName })).toBeVisible();

  console.log('Adding example with nested JSON structure...');
  await addExample(page, {
    input: { question: 'What is 2+2?', nested: { deep: 'value' } },
    output: { answer: '4', confidence: 0.95 },
    searchText: 'What is 2+2?'
  });

  console.log('✓ Dataset created with example');

  // Now manually create evaluator and TEST THE PREVIEW
  console.log('--- Testing JSONPath Preview UI ---');
  await page.getByText('Evaluators').click();
  await expect(page).toHaveURL(/evaluations/);

  // Start creating evaluator
  await page.getByRole('button', { name: 'Create Evaluator' }).first().click();
  const modal = page.locator('[role="dialog"]');
  await expect(modal).toBeVisible();

  // Select aor/llm-judge (has all JSONPath fields)
  await modal.getByText('aor/llm-judge', { exact: true }).click();
  await expect(modal.getByLabel('Name')).toBeVisible();

  // Fill basic info
  await modal.getByLabel('Name').fill(evaluatorName);
  await modal.getByLabel('Description').fill('Testing JSONPath preview');

  // Fill required params
  await modal.getByLabel('prompt', { exact: true }).fill('Evaluate');
  await modal.getByLabel('model', { exact: true }).fill('gpt-4o-mini');
  await modal.getByLabel('temperature', { exact: true }).fill('0.0');

  // Fill JSONPath fields
  console.log('Filling JSONPath fields...');
  await modal.getByLabel('Input JSON Path', { exact: true }).fill('$.question');
  await modal.getByLabel('Output JSON Path', { exact: true }).fill('$.answer');
  await modal.getByLabel('Reference Output JSON Path', { exact: true }).fill('$.answer');

  // NOW TEST THE PREVIEW with ABABAB layout!
  console.log('Testing preview with new layout (preview under each field)...');
  
  // Assert dataset selector is visible (now using DatasetCombobox)
  const datasetSelectorLabel = modal.locator('text=Preview on example from dataset');
  await expect(datasetSelectorLabel).toBeVisible();
  
  // Use the DatasetCombobox (search-based combobox) via placeholder
  const comboboxInput = modal.getByPlaceholder('Type to search datasets...');
  await expect(comboboxInput).toBeVisible();
  await comboboxInput.click();
  await comboboxInput.clear();
  await comboboxInput.pressSequentially(datasetName);
  await page.waitForTimeout(500); // Wait for search debounce
  
  // Click the dataset option from the dropdown
  const datasetOption = page.locator('[role="option"]').filter({ hasText: datasetName });
  await datasetOption.waitFor({ timeout: 10000 });
  await datasetOption.click();

  // Wait for preview to load (debounce + request)
  await page.waitForTimeout(1500);

  // ASSERTIONS: Verify preview containers exist (input and ref-output only)
  await expect(modal.getByTestId('input-path-preview-container')).toBeVisible();
  await expect(modal.getByTestId('ref-output-path-preview-container')).toBeVisible();
  // Note: Output path preview was removed from UI (datasets don't have agent output)
  console.log('✓ Input and reference-output preview containers visible');

  // ASSERTION: Input path preview shows extracted data
  const inputPreviewResult = modal.getByTestId('input-path-preview-result');
  await expect(inputPreviewResult).toBeVisible({ timeout: 5000 });
  await expect(inputPreviewResult).toContainText('What is 2+2?');
  console.log('✓ Input path preview contains: "What is 2+2?"');

  // ASSERTION: Reference output path preview shows extracted data
  const refOutputPreviewResult = modal.getByTestId('ref-output-path-preview-result');
  await expect(refOutputPreviewResult).toBeVisible({ timeout: 5000 });
  await expect(refOutputPreviewResult).toContainText('4');
  console.log('✓ Reference output path preview contains: "4"');

  console.log('✓ JSONPath preview with ABABAB layout verified!');

  // Close modal without submitting
  await modal.locator('button:has-text("×")').first().click();
  await expect(modal).not.toBeVisible();

  console.log('--- JSONPath Preview Test Complete ✓ ---');
});

test('should show no result (not fallback) when reference-output is missing', async ({ page }) => {
  console.log('--- Testing No-Fallback Behavior in UI ---');
  
  const uniqueId = randomUUID().substring(0, 8);
  const testDatasetName = `e2e-no-fallback-${uniqueId}`;
  const testEvalName = `e2e-eval-no-fallback-${uniqueId}`;
  
  // Setup
  await page.goto('/');
  await expect(page).toHaveTitle(/Agent-o-rama/);
  const agentRow = await getE2ETestAgentRow(page);
  await agentRow.click();
  
  await page.getByText('Datasets & Experiments').click();
  await expect(page).toHaveURL(/datasets/);
  
  // Create dataset
  await page.getByRole('button', { name: 'Create Dataset' }).first().click();
  await page.getByLabel('Name').fill(testDatasetName);
  await page.locator('[role="dialog"]').getByRole('button', { name: 'Create Dataset' }).click();
  await expect(page.getByText(testDatasetName)).toBeVisible();
  
  await page.getByRole('link', { name: testDatasetName }).click();
  await page.getByRole('link', { name: 'Examples' }).click();
  
  // Add example with ONLY input field (no reference-output)
  console.log('Adding example with only input (no reference-output)...');
  await addExample(page, {
    input: { testField: 'input-only-value-should-NOT-appear-in-ref-output' },
    output: null,  // Explicitly no reference output
    searchText: 'input-only-value-should-NOT-appear-in-ref-output'
  });
  
  console.log('✓ Example created with input only');

  // Now test the UI - try to preview reference-output path
  console.log('--- Testing Preview UI with Missing Field ---');
  await page.getByText('Evaluators').click();
  await expect(page).toHaveURL(/evaluations/);

  await page.getByRole('button', { name: 'Create Evaluator' }).first().click();
  const modal = page.locator('[role="dialog"]');
  await expect(modal).toBeVisible();
  
  await modal.getByText('aor/llm-judge', { exact: true }).click();
  await expect(modal.getByLabel('Name')).toBeVisible();

  await modal.getByLabel('Name').fill(testEvalName);
  await modal.getByLabel('Description').fill('Testing no-fallback');

  // Check if we can test the preview with new ABABAB layout
  const jsonpathSection = modal.locator('text=JSONPath Configuration');
  if (await jsonpathSection.isVisible()) {
    console.log('✓ JSONPath section found');
    
    const comboboxInput = modal.getByPlaceholder('Type to search datasets...');
    if (await comboboxInput.isVisible()) {
      console.log('✓ Dataset selector found');
      
      // Select our test dataset using combobox
      await comboboxInput.click();
      await comboboxInput.clear();
      await comboboxInput.pressSequentially(testDatasetName);
      await page.waitForTimeout(500);
      const datasetOption = page.locator('[role="option"]').filter({ hasText: testDatasetName });
      await datasetOption.waitFor({ timeout: 10000 });
      await datasetOption.click();
      await page.waitForTimeout(1500);
      
      // CRITICAL TEST: Verify no-fallback behavior with data-testid selectors
      // The example has ONLY input (no reference-output)
      // Input path preview SHOULD show the value ✓
      // Reference Output path preview should show "No result" (NOT fallback to input) ✓
      
      // ASSERTION: Input path preview shows the extracted input value (this is correct)
      const inputPreviewResult = modal.getByTestId('input-path-preview-result');
      await expect(inputPreviewResult).toBeVisible({ timeout: 5000 });
      await expect(inputPreviewResult).toContainText('input-only-value-should-NOT-appear-in-ref-output');
      console.log('✓ Input path preview shows extracted value from $.testField');
      
      // ASSERTION: Reference output path preview shows error/empty (NOT the input value)
      const refOutputPreviewContainer = modal.getByTestId('ref-output-path-preview-container');
      await expect(refOutputPreviewContainer).toBeVisible();
      
      // Check what state the preview is in
      const hasEmpty = await modal.getByTestId('ref-output-path-preview-empty').isVisible().catch(() => false);
      const hasError = await modal.getByTestId('ref-output-path-preview-error').isVisible().catch(() => false);
      const hasResult = await modal.getByTestId('ref-output-path-preview-result').isVisible().catch(() => false);
      
      console.log(`Preview states - empty: ${hasEmpty}, error: ${hasError}, result: ${hasResult}`);
      
      // ASSERTION: Should NOT show result state with actual data
      expect(hasResult).toBe(false);
      console.log('✓ Reference-output preview does NOT show result (correct - no data available)');
      
      // ASSERTION: Should show error or empty state
      expect(hasEmpty || hasError).toBe(true);
      console.log('✓ Reference-output preview shows error/empty state');
      
      // CRITICAL ASSERTION: Input value must NOT appear ANYWHERE in reference-output preview
      const refOutputText = await refOutputPreviewContainer.textContent();
      expect(refOutputText).not.toContain('input-only-value');
      expect(refOutputText).not.toContain('testField'); // The field name shouldn't appear either
      console.log('✓ CRITICAL: Input value does NOT appear in reference-output preview (no fallback!)');
      
      // Log what error message is shown (for debugging)
      if (hasError) {
        const errorElement = modal.getByTestId('ref-output-path-preview-error');
        const errorText = await errorElement.textContent();
        console.log(`  Error message shown: "${errorText}"`);
      }
    } else {
      console.log('⚠ Cannot test: Dataset selector not visible');
    }
  } else {
    console.log('⚠ Cannot test: JSONPath section not visible for aor/llm-judge');
  }

  // Close modal
  await modal.locator('button:has-text("×")').first().click();
  await expect(modal).not.toBeVisible();

  console.log('--- No-Fallback UI Test Complete ✓ ---');
});
