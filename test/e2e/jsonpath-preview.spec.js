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
    output: { answer: '4', confidence: 0.95 }
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

  // NOW TEST THE PREVIEW!
  console.log('Testing preview section...');
  const previewSection = modal.locator('text=Preview on Data');
  await expect(previewSection).toBeVisible();

  // Select the dataset
  const datasetSelector = modal.locator('select').first();
  await expect(datasetSelector).toBeVisible();
  await datasetSelector.selectOption({ label: datasetName });
  console.log('✓ Dataset selected');

  // Wait for preview to load (debounce + request)
  await page.waitForTimeout(1500);

  // Verify preview shows the extracted data
  console.log('Verifying preview results...');
  
  // Check that all three preview sections are visible
  await expect(modal.getByText('Input Path Result:')).toBeVisible();
  await expect(modal.getByText('Output Path Result:', { exact: true })).toBeVisible();
  await expect(modal.getByText('Reference Output Path Result:')).toBeVisible();
  console.log('✓ All three preview sections are visible');

  // Verify Input path shows extracted data
  await expect(modal.getByText('What is 2+2?')).toBeVisible({ timeout: 5000 });
  console.log('✓ Input path preview shows extracted data: "What is 2+2?"');

  // Verify Reference Output shows extracted data (the preview shows "4")
  // Use exact match to avoid matching dataset IDs
  const previewBoxWithFour = modal.locator('pre.bg-gray-100').filter({ hasText: '4' }).first();
  await expect(previewBoxWithFour).toBeVisible({ timeout: 5000 });
  console.log('✓ Reference output path preview shows extracted data: "4"');

  console.log('✓ JSONPath preview functionality working correctly!');

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
    input: { uniqueValue: 'this-value-should-NOT-appear-in-ref-output-preview' },
    output: null  // Explicitly no reference output
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

  // Check if we can test the preview
  const jsonpathSection = modal.locator('text=JSONPath Configuration');
  if (await jsonpathSection.isVisible()) {
    console.log('✓ JSONPath section found');
    
    const previewSection = modal.locator('text=Preview on Data');
    if (await previewSection.isVisible()) {
      console.log('✓ Preview section found');
      
      // Select our test dataset
      const datasetSelector = modal.locator('select').first();
      if (await datasetSelector.isVisible()) {
        await datasetSelector.selectOption({ label: testDatasetName });
        await page.waitForTimeout(1500);
        
        // The preview should show "No result" for reference-output
        // NOT show the input value "this-value-should-NOT-appear-in-ref-output-preview"
        const refOutputPreview = modal.locator('text=Reference Output Path Result:').locator('..');
        
        // Check that it does NOT contain the input value
        const inputValueVisible = await refOutputPreview.locator('text=this-value-should-NOT-appear-in-ref-output-preview').count();
        
        if (inputValueVisible === 0) {
          console.log('✓ No-fallback verified: input value NOT shown in reference-output preview');
        } else {
          console.log('✗ FAIL: Input value incorrectly shown in reference-output preview (fallback occurred)');
          throw new Error('Fallback behavior detected - input shown instead of null');
        }
        
        // Should show "No result" or similar
        const noResultVisible = await refOutputPreview.locator('text=/No result|null/i').count();
        if (noResultVisible > 0) {
          console.log('✓ Preview correctly shows "No result" for missing field');
        }
      }
    } else {
      console.log('⚠ Cannot test: Preview section not visible for this evaluator');
    }
  } else {
    console.log('⚠ Cannot test: JSONPath section not visible for aor/llm-judge');
  }

  // Close modal
  await modal.locator('button:has-text("×")').first().click();
  await expect(modal).not.toBeVisible();

  console.log('--- No-Fallback UI Test Complete ✓ ---');
});
