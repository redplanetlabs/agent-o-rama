// test/e2e/experiments-filtering.spec.js
import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import {
  getBasicAgentRow,
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
const datasetName = `e2e-filter-test-dataset-${uniqueId}`;
const evaluatorName = `e2e-filter-evaluator-${uniqueId}`;
const snapshotName = 'v1.0';
const agentToRun = 'BasicAgent';

// Dataset structure and test cases
const examples = [
  { id: `ex1-${uniqueId}`, input: "A", tags: ["tag-a"] },
  { id: `ex2-${uniqueId}`, input: "B", tags: ["tag-b"] },
  { id: `ex3-${uniqueId}`, input: "C", tags: ["tag-a", "tag-c"] },
  // Snapshot will be taken after these 3 examples
  { id: `ex4-${uniqueId}`, input: "D", tags: ["tag-b"] },
  { id: `ex5-${uniqueId}`, input: "E", tags: ["tag-c"] },
];

const testCases = [
  { name: 'Snapshot v1.0 - All Examples', snapshot: snapshotName, selectorType: 'all', expectedCount: 3 },
  { name: 'Snapshot v1.0 - Tag A', snapshot: snapshotName, selectorType: 'tag', selectorTag: 'tag-a', expectedCount: 2 },
  { name: 'Latest - All Examples', snapshot: 'Latest (Working Copy)', selectorType: 'all', expectedCount: 5 },
  { name: 'Latest - Tag B', snapshot: 'Latest (Working Copy)', selectorType: 'tag', selectorTag: 'tag-b', expectedCount: 2 },
  { name: 'Latest - Tag C', snapshot: 'Latest (Working Copy)', selectorType: 'tag', selectorTag: 'tag-c', expectedCount: 2 },
  { name: 'Latest - Selected Examples (A & D)', snapshot: 'Latest (Working Copy)', selectorType: 'selected', selectedExamples: ['A', 'D'], expectedCount: 2 },
];

// =============================================================================
// HELPER FUNCTIONS FOR EXPERIMENTS
// =============================================================================

async function selectExamplesInUI(page, { snapshot, selectedExamples }) {
  console.log(`Selecting examples: ${selectedExamples.join(', ')} in snapshot: ${snapshot}`);
  
  // Navigate to examples tab
  await page.getByRole('link', { name: 'Examples' }).click();
  
  // Select the correct snapshot
  await page.getByRole('button', { name: /Latest|v1\.0/ }).click();
  if (snapshot === 'Latest (Working Copy)') {
    await page.getByTestId('snapshot-option-latest').click();
  } else {
    await page.getByTestId(`snapshot-option-${snapshot}`).click();
  }
  
  // Wait for examples to load - number depends on which snapshot is selected
  const expectedExampleCount = snapshot === 'Latest (Working Copy)' ? 5 : 3;
  await expect(page.locator('table tbody tr')).toHaveCount(expectedExampleCount);
  
  // Select specific examples by clicking their checkboxes
  for (const exampleInput of selectedExamples) {
    // Find the row by targeting the input cell specifically (2nd column, index 1)
    const exampleRow = page.locator('table tbody tr').filter({ 
      has: page.locator('td').nth(1).filter({ hasText: new RegExp(`^${exampleInput}$`) })
    });
    await expect(exampleRow).toBeVisible();
    await exampleRow.locator('td').first().click(); // Click the checkbox cell
    console.log(`Selected example: ${exampleInput}`);
  }
}

async function runAndVerifyExperiment(page, { experimentName, snapshot, selectorType, selectorTag, selectedExamples, expectedCount, module_id, dataset_id }) {
  console.log(`--- Running Experiment: "${experimentName}" ---`);
  
  await page.getByRole('button', { name: 'Run New Experiment' }).click();
  const modal = page.locator('[role="dialog"]');
  await expect(modal).toBeVisible();

  // 1. Fill out the form
  await modal.getByLabel('Experiment Name').fill(experimentName);
  
  // Select Snapshot
  await modal.getByRole('button', { name: /Latest/ }).click();
  // Use data-testid to target the specific snapshot option
  if (snapshot === 'Latest (Working Copy)') {
    await page.getByTestId('snapshot-option-latest').click();
  } else {
    await page.getByTestId(`snapshot-option-${snapshot}`).click();
  }
  console.log(`Selected snapshot: ${snapshot}`);

  // Select Examples
  if (selectorType === 'tag') {
    await modal.getByLabel('Only examples with tag:').check();
    await modal.getByPlaceholder('e.g., hard-case').fill(selectorTag);
    console.log(`Selected tag: ${selectorTag}`);
  } else if (selectorType === 'selected') {
    // For selected examples, the radio button should already be enabled since examples are pre-selected
    await modal.getByLabel(/Only the \d+ selected examples/).check();
    console.log(`Selected examples option chosen (${selectedExamples.length} examples)`);
  } else {
    await modal.getByLabel('All examples in snapshot').check();
    console.log('Selected all examples.');
  }

  // Configure Target Agent
  await modal.getByTestId('agent-name-dropdown').click();
  await modal.getByText(agentToRun, { exact: true }).click();
  await modal.locator('div').filter({ hasText: /^Input Mappings/ }).getByRole('textbox').fill('$');
  
  // Select Evaluator
  await addEvaluatorToExperiment(page, modal, evaluatorName);
  
  // 2. Run the experiment
  await modal.getByRole('button', { name: 'Run Experiment' }).click();
  await expect(modal).not.toBeVisible();
  console.log('Experiment started...');

  // 3. Wait for completion and verify results
  await expect(page).toHaveURL(/experiments\//, { timeout: 30000 });
  await expect(page.getByText('Completed').first()).toBeVisible({ timeout: 120000 }); // Wait up to 2 mins for completion
  console.log('Experiment completed.');

  // The most important check: verify the number of examples it ran on.
  const summaryTable = page.locator('table').filter({ hasText: '# Examples' });
  await expect(summaryTable.locator('td').first().locator('div').nth(1)).toHaveText(String(expectedCount));
  
  const resultsTable = page.locator('table').filter({ hasText: 'Input' });
  await expect(resultsTable.locator('tbody tr')).toHaveCount(expectedCount);
  console.log(`Verified experiment ran on ${expectedCount} examples as expected.`);

  // 4. Navigate back to the experiments list
  await page.getByText('Back').click();
  console.log(`--- Finished Experiment: "${experimentName}" ---`);
}


// =============================================================================
// TEST SUITE
// =============================================================================

test.describe('Experiment Filtering with Tags and Snapshots', () => {
  // Set a long timeout for the entire suite, as it involves many sequential steps.
  test.setTimeout(10 * 60 * 1000); // 10 minutes

  test('should create dataset, run all filtering experiments, and clean up', async ({ page }) => {
    let module_id;
    let dataset_id;
    
    // =============================================================================
    // PHASE 1: SETUP - Create Dataset, Examples, Snapshot, and Evaluator
    // =============================================================================
    console.log('--- PHASE 1: SETUP ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getBasicAgentRow(page);
    await agentRow.click();
    
    // Capture module_id from the URL for later use
    const url = new URL(page.url());
    module_id = url.pathname.split('/')[2];

    // Create Dataset and Examples
    await page.getByText('Datasets & Experiments').click();
    await createDataset(page, datasetName);
    
    const datasetRow = page.locator('table tbody tr').filter({ hasText: datasetName });
    const datasetLink = await datasetRow.locator('a').getAttribute('href');
    dataset_id = datasetLink.split('/')[4];
    
    await datasetRow.click();
    await page.getByRole('link', { name: 'Examples' }).click();

    // Add first 3 examples
    for (const ex of examples.slice(0, 3)) {
      await addExample(page, { input: ex.input, output: "dummy output", tags: ex.tags });
    }

    // Create Snapshot
    await page.getByRole('button', { name: /Latest/ }).click();
    await page.getByText('New snapshot').click();
    const snapshotModal = page.locator('[role="dialog"]');
    await snapshotModal.getByLabel('New Snapshot Name').fill(snapshotName);
    await snapshotModal.getByRole('button', { name: 'Create Snapshot' }).click();
    await expect(snapshotModal).not.toBeVisible();
    await expect(page.getByRole('button', { name: snapshotName })).toBeVisible(); // Verify it was created and selected
    console.log(`Created snapshot: ${snapshotName}`);
    
    // Switch back to "Latest" to add more examples
    await page.getByRole('button', { name: snapshotName }).click();
    await page.getByTestId('snapshot-option-latest').click();
    await expect(page.getByRole('button', { name: /Latest/ })).toBeVisible();

    // Add last 2 examples
    for (const ex of examples.slice(3, 5)) {
      await addExample(page, { input: ex.input, output: "dummy output", tags: ex.tags });
    }
    console.log('All examples added.');

    // Create Evaluator
    await page.getByText('Evaluators').click();
    await expect(page).toHaveURL(/evaluations/);
    await createEvaluator(page, {
      name: evaluatorName,
      builderName: 'aor/conciseness',
      description: 'Checks if output is short.',
      params: { threshold: '30' },
      outputJsonPath: '$' // The agent output is a plain string
    });
    console.log('--- Setup Complete ---');

    // =============================================================================
    // PHASE 2: RUN ALL FILTERING EXPERIMENTS
    // =============================================================================
    console.log('--- PHASE 2: RUNNING ALL EXPERIMENTS ---');
    
    for (const tc of testCases) {
      // Navigate to the correct dataset page before each experiment
      await page.goto('/');
      const agentRow = await getBasicAgentRow(page);
      await agentRow.click();
      await page.getByText('Datasets & Experiments').click();
      await page.getByRole('link', { name: datasetName }).click();
      
      // If this is a "selected" test case, we need to select examples first
      if (tc.selectorType === 'selected') {
        await selectExamplesInUI(page, {
          snapshot: tc.snapshot,
          selectedExamples: tc.selectedExamples
        });
      } 

      // select experiments tab
      await page.getByRole('link', { name: 'Experiments', exact: true }).click();

      await runAndVerifyExperiment(page, {
        experimentName: tc.name,
        snapshot: tc.snapshot,
        selectorType: tc.selectorType,
        selectorTag: tc.selectorTag,
        selectedExamples: tc.selectedExamples,
        expectedCount: tc.expectedCount,
        module_id: module_id,
        dataset_id: dataset_id,
      });
    }

    // =============================================================================
    // PHASE 3: CLEANUP
    // =============================================================================
    console.log('--- PHASE 3: CLEANUP ---');
    page.on('dialog', dialog => dialog.accept());

    // Navigate to datasets page and delete the dataset
    await page.goto('/'); // Start from a known place
    const cleanupAgentRow = await getBasicAgentRow(page);
    await cleanupAgentRow.click();
    await page.getByText('Datasets & Experiments').click();
    await deleteDataset(page, datasetName);

    // Navigate to evaluators page and delete the evaluator
    await page.getByText('Evaluators').click();
    await deleteEvaluator(page, evaluatorName);
    console.log('--- Cleanup Complete ---');
    console.log('--- All filtering experiments completed successfully! ---');
  });
});