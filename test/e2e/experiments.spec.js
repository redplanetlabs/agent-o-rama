import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import {
  getResearchAgentRow,
  createDataset,
  deleteDataset,
  createEvaluator,
  deleteEvaluator,
  addExample,
  addEvaluatorToExperiment,
} from './helpers.js';

// =============================================================================
// TEST CONSTANTS
// =============================================================================
const uniqueId = randomUUID().substring(0, 8);
const datasetName = `e2e-exp-dataset-${uniqueId}`;
const evaluatorNamePass = `e2e-exp-evaluator-pass-${uniqueId}`;
const evaluatorNameFail = `e2e-exp-evaluator-fail-${uniqueId}`;
const summaryEvaluatorName = `e2e-exp-summary-eval-${uniqueId}`;
const experimentName = `e2e-full-flow-experiment-${uniqueId}`;
const rerunExperimentName = `e2e-rerun-experiment-${uniqueId}`; // New constant for the re-run
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

test.describe('Full Experiment Flow E2E Test with Re-run', () => {
  // Use a single test block for this sequential flow.
  // We'll increase the timeout for the entire test to account for multiple long-running agent/experiment tasks.
  test.setTimeout(5 * 60 * 1000); // 5 minutes

  test('should create, run, re-run, and clean up an experiment successfully', async ({ page }) => {
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

    // 1b. Create Evaluators
    console.log(`Creating evaluators "${evaluatorNamePass}" and "${evaluatorNameFail}"...`);
    await page.getByText('Evaluators').click();
    // Passing conciseness (very high threshold)
    await createEvaluator(page, {
      name: evaluatorNamePass,
      builderName: 'aor/conciseness',
      params: { threshold: '5000' }, // High threshold to ensure it passes
      // Evaluate the first arg string from the first output item
      outputJsonPath: '$[0].args[0]'
    });
    // Failing conciseness (very low threshold)
    await createEvaluator(page, {
      name: evaluatorNameFail,
      builderName: 'aor/conciseness',
      params: { threshold: '10' },
      outputJsonPath: '$[0].args[0]'
    });
    // Summary evaluator (F1 score)
    await createEvaluator(page, {
      name: summaryEvaluatorName,
      builderName: 'aor/f1-score',
      description: 'Summary evaluator for F1 score.',
      params: { positiveValue: 'pass' }
    });
    console.log('All evaluators created successfully.');


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
    await expect(page.locator('.bg-indigo-50').getByRole('button', { name: 'Add node to Dataset' })).toBeVisible({ timeout: 10000 });
    await page.locator('.bg-indigo-50').getByRole('button', { name: 'Add node to Dataset' }).click();

    // Now the modal should appear
    const addToDatasetModal = page.locator('[role="dialog"]');
    await expect(addToDatasetModal.getByText('Add Node \'write-section\' to Dataset')).toBeVisible();
    console.log('Opened "Add to Dataset" modal.');

    // 3b. Interact with the modal form
    await addToDatasetModal.getByRole('button', { name: /Select a dataset/ }).click();
    await addToDatasetModal.getByText(datasetName).click();
    console.log('Selected target dataset.');

    // 3c. New simplified form: direct JSON textareas (pre-filled).
    console.log('Validating direct JSON textareas with schema feedback...');
    const inputTextarea = addToDatasetModal.getByLabel('Input Data');
    const outputTextarea = addToDatasetModal.getByLabel('Reference Output Data');
    await expect(inputTextarea).toBeVisible();
    await expect(outputTextarea).toBeVisible();
    await expect(inputTextarea).not.toHaveValue('');
    await expect(outputTextarea).not.toHaveValue('');

    // Enter JSON that is valid JSON but does NOT satisfy the dataset input schema (expects array)
    await inputTextarea.fill('"not-an-array"');
    await expect(addToDatasetModal.getByText(/Schema error/i)).toBeVisible({ timeout: 10000 });

    // Now enter JSON that satisfies the schema: [string, any, string]
    await inputTextarea.fill('["persona", [], "context"]');
    await expect(addToDatasetModal.getByText('✓ Valid input data')).toBeVisible({ timeout: 10000 });

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

    // Add a second example manually for the summary evaluator
    await addExample(page, { input: ["persona2", [], "context2"], output: "some-output" });
    console.log('Second example added manually.');
    
    // Verify we have 2 examples now
    await expect(page.locator('table tbody tr')).toHaveCount(2);
    console.log('Verified dataset has 2 examples.');


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
    // Select Target Type: Node (radio button)
    await expModal.getByLabel('Node').check();

    // Select the agent
    await expModal.getByTestId('agent-name-dropdown').click();
    await expModal.getByText(agentToRun, { exact: true }).click();
    
    // Select the node from dropdown
    await expModal.getByTestId('node-name-dropdown').click();
    await expModal.getByText('write-section', { exact: true }).click();

    // Configure input mappings (ensure we have 3 and fill them)
    const mappingsSection = expModal.locator('div').filter({ hasText: /^Input Mappings/ });
    // Click Add Mapping until at least 3 inputs exist
    for (let i = await mappingsSection.locator('input').count(); i < 3; i++) {
      await expModal.getByRole('button', { name: 'Add Mapping' }).click();
    }
    await mappingsSection.locator('input').nth(0).fill('$[0]'); // persona
    await mappingsSection.locator('input').nth(1).fill('$[1]'); // messages
    await mappingsSection.locator('input').nth(2).fill('$[2]'); // context

    // Select all three evaluators
    await addEvaluatorToExperiment(page, expModal, evaluatorNamePass);
    await addEvaluatorToExperiment(page, expModal, evaluatorNameFail);
    await addEvaluatorToExperiment(page, expModal, summaryEvaluatorName);
    console.log('All three evaluators selected.');

    // 5b. Start the experiment
    await expModal.getByRole('button', { name: 'Run Experiment' }).click();
    await expect(expModal).not.toBeVisible();
    console.log('Experiment started.');


    // ---
    // PHASE 6: VERIFY EXPERIMENT RESULTS
    // ---
    // We navigate directly to the experiment detail page after starting
    await expect(page).toHaveURL(/experiments\//, { timeout: 30000 });
    // Wait for the status badge (not the table cell)
    const statusBadge = page.locator('.bg-blue-100, .bg-green-100').filter({ hasText: /Running|Completed/ });
    await expect(statusBadge).toBeVisible({ timeout: 30000 });
    // Then wait for completion
    await expect(page.getByText('Completed').first()).toBeVisible({ timeout: 120000 });
    console.log('Experiment completed.');
    
    // Check for the evaluator results in the detailed view
    await expect(page.getByText('Detailed Results')).toBeVisible();
    const resultsTable = page.locator('table').filter({ hasText: 'Input' });
    const resultRow = resultsTable.locator('tbody tr').first();

    // Verify both evaluator chips (duplicate metric key forces label to include name)
    const outputCell = resultRow.locator('td').nth(2);
    const passChip = outputCell.locator('a').filter({ hasText: new RegExp(`${evaluatorNamePass}/concise\\?`) }).first();
    const failChip = outputCell.locator('a').filter({ hasText: new RegExp(`${evaluatorNameFail}/concise\\?`) }).first();
    await expect(passChip).toBeVisible();
    await expect(passChip).toContainText('✓T');
    await expect(failChip).toBeVisible();
    await expect(failChip).toContainText('✗F');
    console.log('Regular evaluator scores for both pass and fail correctly displayed.');

    // Verify summary evaluator results in the summary table
    const summaryTable = page.locator('table').filter({ hasText: 'precision' });
    await expect(summaryTable).toBeVisible();
    await expect(summaryTable.getByText('score')).toBeVisible();
    await expect(summaryTable.getByText('precision')).toBeVisible();
    await expect(summaryTable.getByText('recall')).toBeVisible();
    console.log('Summary evaluator scores verified.');

    // ---
    // NEW PHASE 7: RE-RUN EXPERIMENT
    // ---
    console.log('--- PHASE 7: RE-RUN EXPERIMENT ---');
    
    // 7a. Click the "Re-run Experiment" button
    await page.getByRole('button', { name: 'Re-run Experiment' }).click();
    const rerunModal = page.locator('[role="dialog"]');
    await expect(rerunModal).toBeVisible();
    console.log('Re-run modal opened.');

    // 7b. Verify the form is pre-filled with the original experiment's data
    await expect(rerunModal.getByLabel('Experiment Name')).toHaveValue(`Copy of ${experimentName}`);
    await expect(rerunModal.getByLabel('Node')).toBeChecked();
    await expect(rerunModal.getByTestId('agent-name-dropdown')).toHaveText(agentToRun);
    await expect(rerunModal.getByTestId('node-name-dropdown')).toHaveText('write-section');
    
    // Verify input mappings using more specific selectors
    const inputMappingsSection = rerunModal.locator('div').filter({ hasText: /^Input Mappings/ });
    await expect(inputMappingsSection.locator('input.font-mono').nth(0)).toHaveValue('$[0]');
    await expect(inputMappingsSection.locator('input.font-mono').nth(1)).toHaveValue('$[1]');
    await expect(inputMappingsSection.locator('input.font-mono').nth(2)).toHaveValue('$[2]');
    
    await expect(rerunModal.getByText(evaluatorNamePass, { exact: true })).toBeVisible();
    await expect(rerunModal.getByText(evaluatorNameFail, { exact: true })).toBeVisible();
    await expect(rerunModal.getByText(summaryEvaluatorName, { exact: true })).toBeVisible();
    console.log('Verified that the re-run form is correctly pre-filled with all three evaluators.');

    // 7c. Modify the name and run the new experiment
    await rerunModal.getByLabel('Experiment Name').fill(rerunExperimentName);
    await rerunModal.getByRole('button', { name: 'Run Experiment' }).click();
    await expect(rerunModal).not.toBeVisible();
    console.log('Re-run experiment started.');

    // ---
    // NEW PHASE 8: VERIFY RE-RUN EXPERIMENT RESULTS
    // ---
    console.log('--- PHASE 8: VERIFY RE-RUN EXPERIMENT RESULTS ---');
    
    // The page should navigate to the new experiment's detail page
    await expect(page).toHaveURL(/experiments\//, { timeout: 30000 });
    // The URL should NOT contain the old experiment's ID. This is a bit tricky to assert,
    // but we can ensure the new experiment's name is on the page.
    await expect(page.getByRole('heading', { name: rerunExperimentName })).toBeVisible();

    // Wait for completion and verify results, just like the first run
    await expect(page.getByText('Completed').first()).toBeVisible({ timeout: 120000 });
    console.log('Re-run experiment completed.');
    
    const rerunResultsTable = page.locator('table').filter({ hasText: 'Input' });
    const rerunResultRow = rerunResultsTable.locator('tbody tr').first();
    const rerunOutputCell = rerunResultRow.locator('td').nth(2);
    const rerunPassChip = rerunOutputCell.locator('a').filter({ hasText: new RegExp(`${evaluatorNamePass}/concise\\?`) }).first();
    const rerunFailChip = rerunOutputCell.locator('a').filter({ hasText: new RegExp(`${evaluatorNameFail}/concise\\?`) }).first();
    await expect(rerunPassChip).toBeVisible();
    await expect(rerunPassChip).toContainText('✓T');
    await expect(rerunFailChip).toBeVisible();
    await expect(rerunFailChip).toContainText('✗F');
    console.log('Regular evaluator scores for the re-run experiment verified.');

    // Verify summary evaluator results for re-run
    const rerunSummaryTable = page.locator('table').filter({ hasText: 'precision' });
    await expect(rerunSummaryTable).toBeVisible();
    await expect(rerunSummaryTable.getByText('score')).toBeVisible();
    await expect(rerunSummaryTable.getByText('precision')).toBeVisible();
    await expect(rerunSummaryTable.getByText('recall')).toBeVisible();
    console.log('Summary evaluator scores for re-run experiment verified.');


    // ---
    // PHASE 9: CLEANUP (Updated)
    // ---
    console.log('--- PHASE 9: CLEANUP ---');
    // Set up auto-accept for confirm dialogs
    page.on('dialog', dialog => dialog.accept());
    
    // Navigate back to the dataset page. Deleting the dataset will also delete BOTH experiments.
    await page.getByText('Datasets & Experiments').click();
    
    // Verify both experiments exist before deleting the dataset
    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Experiments', exact: true }).click();
    await expect(page.getByText(experimentName, { exact: true })).toBeVisible();
    await expect(page.getByText(rerunExperimentName, { exact: true })).toBeVisible();
    console.log('Verified both experiments are listed before cleanup.');

    // Navigate back again to delete the dataset
    // don't delete dataset, so I can look at the experiment results.
    // await page.getByText('Datasets & Experiments').click();
    // await deleteDataset(page, datasetName);
    
    // Delete all evaluators
    await page.getByText('Evaluators').click();
    await deleteEvaluator(page, evaluatorNamePass);
    await deleteEvaluator(page, evaluatorNameFail);
    await deleteEvaluator(page, summaryEvaluatorName);
    
    console.log('--- Test successfully completed and cleaned up all resources. ---');
  });
});