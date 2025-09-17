import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import {
  getResearchAgentRow,
  createDataset,
  deleteDataset,
  createEvaluator,
  deleteEvaluator,
} from './helpers.js';

// =============================================================================
// TEST CONSTANTS
// =============================================================================
const uniqueId = randomUUID().substring(0, 8);
const datasetName = `e2e-exp-dataset-${uniqueId}`;
const evaluatorName = `e2e-exp-evaluator-${uniqueId}`;
const experimentName = `e2e-full-flow-experiment-${uniqueId}`;
const agentToRun = 'researcher'; // As defined in the research_agent.clj example

// Define a schema that matches the node's full input shape:
// [persona:string, messages:any, context:string] and prevents additional items.
const failingInputSchema = JSON.stringify({
  type: 'array',
  prefixItems: [
    { type: 'string' },
    {}, // allow any type for messages (array of complex objects)
    { type: 'string' }
  ],
  items: false,
});

// =============================================================================
// TEST SUITE
// =============================================================================

test.describe('Full Experiment Flow E2E Test', () => {
  // Use a single test block for this sequential flow.
  // We'll increase the timeout for the entire test to account for multiple long-running agent/experiment tasks.
  test.setTimeout(5 * 60 * 1000); // 5 minutes

  test('should create resources, generate a trace, add to dataset, and run a successful experiment', async ({ page }) => {
    // ---
    // PHASE 1: SETUP - Create Dataset and Evaluator
    // ---
    console.log('--- PHASE 1: SETUP ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getResearchAgentRow(page);
    await agentRow.click();

    // 1a. Create Dataset with a specific schema
    console.log(`Creating dataset "${datasetName}" with schema...`);
    await page.getByText('Datasets & Experiments').click();
    await page.getByRole('button', { name: 'Create Dataset' }).first().click();
    const datasetModal = page.locator('[role="dialog"]');
    await datasetModal.getByLabel('Name').fill(datasetName);
    await datasetModal.getByLabel('Input JSON Schema').fill(failingInputSchema); // Using the schema designed to fail
    await datasetModal.getByRole('button', { name: 'Create Dataset' }).click();
    await expect(page.getByText(datasetName)).toBeVisible();
    console.log('Dataset created successfully.');

    // 1b. Create Evaluator
    console.log(`Creating evaluator "${evaluatorName}"...`);
    await page.getByText('Evaluators').click();
    await createEvaluator(page, {
      name: evaluatorName,
      builderName: 'aor/conciseness',
      params: { threshold: '5000' }, // High threshold to ensure it passes
    });
    console.log('Evaluator created successfully.');


    // ---
    // PHASE 2: GET TRACE DATA - Use existing completed invocation or run agent manually
    // ---
    console.log('--- PHASE 2: GET TRACE DATA ---');
    await page.getByText('Overview').click();
    const row = await getResearchAgentRow(page); // Wait for overview to be ready
    await row.click();

    // 2a. Check if there are any existing completed invocations
    const completedInvocations = page.locator('table tbody tr').filter({ hasText: 'Success' });
    const hasCompletedInvocations = await completedInvocations.count() > 0;

    if (hasCompletedInvocations) {
      // Use existing completed invocation
      console.log('Found existing completed invocation, navigating to it...');
      await completedInvocations.first().click();
      await expect(page).toHaveURL(/\/invocations\//, { timeout: 30000 });
      console.log('Navigated to existing invocation trace page.');
    } else {
      // Manually run the agent
      console.log(`No completed invocations found. Running agent "${agentToRun}"...`);
      const manualRunForm = page.locator('div').filter({ hasText: /^Manually Run Agent/ });
      await manualRunForm.getByPlaceholder(/\[arg1, arg2, arg3, ...\]/).fill('["", {"topic": "Rama"}]');
      await manualRunForm.getByRole('button', { name: 'Submit' }).click();

      // Wait for navigation to the trace page and handle HITL prompt
      await expect(page).toHaveURL(/\/invocations\//, { timeout: 30000 });
      console.log('Navigated to invocation trace page.');

      const feedbackNode = page.locator('.react-flow__node').filter({ hasText: 'feedback' });
      await feedbackNode.click({ timeout: 60000 }); // wait for first node.
      const hitlPrompt = page.locator('.bg-amber-50');
      await expect(hitlPrompt).toBeVisible({ timeout: 60000 }); // Wait up to a minute for the first prompt
      await hitlPrompt.getByPlaceholder('Type your response...').fill('no');
      await hitlPrompt.getByRole('button', { name: 'Submit Response' }).click();

      // Wait for the agent to finish - look for the "Success" badge in the Final Result section
      await expect(page.locator('.bg-green-100.text-green-800').filter({ hasText: 'Success' })).toBeVisible({ timeout: 120000 }); // Wait up to 2 minutes
      console.log('Agent run completed.');
    }


    // ---
    // PHASE 3: "ADD TO DATASET" FLOW - Extract data from the trace
    // ---
    console.log('--- PHASE 3: ADD TO DATASET ---');

    // 3a. Select the first 'write-section' node and open the "Add to Dataset" modal
    const writeSectionNode = page.locator('.react-flow__node').filter({ hasText: 'write-section' }).first();
    await writeSectionNode.click();
    
    // Wait for the node details panel to appear and the Add to Dataset button to be available
    await expect(page.locator('.bg-indigo-50').getByRole('button', { name: 'Add to Dataset' })).toBeVisible({ timeout: 10000 });
    await page.locator('.bg-indigo-50').getByRole('button', { name: 'Add to Dataset' }).click();

    // Now the modal should appear
    const addToDatasetModal = page.locator('[role="dialog"]');
    await expect(addToDatasetModal.getByText('Add Node \'write-section\' to Dataset')).toBeVisible();
    console.log('Opened "Add to Dataset" modal.');

    // 3b. Interact with the modal form
    await addToDatasetModal.getByRole('button', { name: /Select a dataset/ }).click();
    await addToDatasetModal.getByText(datasetName).click();
    console.log('Selected target dataset.');

    // 3c. Test JSONPath and preview pane
    console.log('Testing JSONPath templates...');
    const inputTemplate = addToDatasetModal.getByLabel('Input Template (JSONPath)');
    const outputTemplate = addToDatasetModal.getByLabel('Reference Output Template (JSONPath)');
    const previewPane = addToDatasetModal.locator('div').filter({ hasText: /^Live Preview/ });
    // Use header rows to scope: they have class "flex justify-between items-center"
    const headerRows = previewPane.locator('div.flex.justify-between.items-center');
    const inputHeader = headerRows.nth(0);
    const outputHeader = headerRows.nth(1);

    // Test valid path for full input array (schema expects an array)
    await inputTemplate.fill('$');
    await expect(inputHeader.getByText('Valid')).toBeVisible({ timeout: 10000 });

    // Test invalid path
    await inputTemplate.fill('invalid-jsonpath');
    await expect(inputHeader.getByText('Invalid')).toBeVisible({ timeout: 10000 });

    // Test schema validation failure (string does not satisfy array schema)
    await inputTemplate.fill('$[0]');
    await expect(inputHeader.getByText('Invalid')).toBeVisible({ timeout: 10000 });
    await expect(inputSection.getByText(/array/i)).toBeVisible({ timeout: 10000 });
    console.log('Schema validation failure correctly detected and displayed.');

    // Fix the input to be valid for submission (capture entire input array)
    await inputTemplate.fill('$');
    await outputTemplate.fill('$'); // The full emits string

    // 3d. Submit to add the example
    await addToDatasetModal.getByRole('button', { name: 'Add Example' }).click();
    await expect(addToDatasetModal).not.toBeVisible();
    console.log('Example added from trace.');


    // ---
    // PHASE 4: VERIFY ADDED EXAMPLE
    // ---
    console.log('--- PHASE 4: VERIFY ADDED EXAMPLE ---');
    await page.getByText('Datasets & Experiments').click();
    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();

    // The added example should appear in the table
    await expect(page.locator('table tbody tr').first()).toBeVisible();
    console.log('Verified example in dataset.');


    // ---
    // PHASE 5: CREATE AND RUN EXPERIMENT
    // ---
    console.log('--- PHASE 5: CREATE AND RUN EXPERIMENT ---');
    await page.getByRole('link', { name: 'Experiments', exact: true }).click();
    await page.getByRole('button', { name: 'Run New Experiment' }).click();

    const expModal = page.locator('[role="dialog"]');
    await expect(expModal).toBeVisible();

    // 5a. Fill out the experiment form
    await expModal.getByLabel('Experiment Name').fill(experimentName);
    await expModal.getByLabel('Node').check(); // Select Node target type

    // Select the agent and node
    await expModal.getByRole('button', { name: 'Select an agent' }).click();
    await expModal.getByText(agentToRun).click();
    await expModal.getByLabel('Node Name').fill('write-section');

    // Configure input mappings
    const inputMappingField = expModal.getByLabel('Input Mappings').locator('input').first();
    await inputMappingField.fill('$[0]'); // The first argument for write-section is `persona`
    await expModal.getByRole('button', { name: 'Add Mapping' }).click();
    await expModal.getByLabel('Input Mappings').locator('input').nth(1).fill('$[1]'); // second arg is `messages`
    await expModal.getByLabel('Input Mappings').locator('input').nth(2).fill('$[2]'); // third arg is `context`

    // Select the evaluator
    await expModal.getByRole('button', { name: 'Add Evaluator' }).click();
    await expModal.getByText(evaluatorName).click();

    // 5b. Start the experiment
    await expModal.getByRole('button', { name: 'Run Experiment' }).click();
    await expect(expModal).not.toBeVisible();
    console.log('Experiment started.');


    // ---
    // PHASE 6: VERIFY EXPERIMENT RESULTS
    // ---
    console.log('--- PHASE 6: VERIFY EXPERIMENT RESULTS ---');
    const experimentRow = page.locator('table tbody tr').filter({ hasText: experimentName });
    await expect(experimentRow).toBeVisible({ timeout: 15000 });

    // Wait for completion and navigate to results
    await expect(experimentRow.getByText('Completed')).toBeVisible({ timeout: 120000 });
    console.log('Experiment completed.');
    await experimentRow.click();
    
    // Check for the evaluator results in the detailed view
    await expect(page.getByText('Detailed Results')).toBeVisible();
    const resultsTable = page.locator('table').filter({ hasText: 'Input' });
    const resultRow = resultsTable.locator('tbody tr').first();

    // Verify the evaluator score is present
    const evaluatorScore = resultRow.locator('div').filter({ hasText: new RegExp(evaluatorName) });
    await expect(evaluatorScore).toBeVisible();
    await expect(evaluatorScore).toContainText('concise?');
    await expect(evaluatorScore).toContainText('✓'); // The checkmark for a passing boolean score
    console.log('Evaluator scores correctly displayed.');

    
    // ---
    // PHASE 7: CLEANUP
    // ---
    console.log('--- PHASE 7: CLEANUP ---');
    // Set up auto-accept for confirm dialogs
    page.on('dialog', dialog => dialog.accept());
    
    // Delete the dataset (which also deletes the experiment)
    await page.getByText('Datasets & Experiments').click();
    await deleteDataset(page, datasetName);
    
    // Delete the evaluator
    await page.getByText('Evaluators').click();
    await deleteEvaluator(page, evaluatorName);
    
    console.log('--- Test successfully completed and cleaned up. ---');
  });
});
