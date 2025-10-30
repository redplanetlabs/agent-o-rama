import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getE2ETestAgentRow } from './helpers.js';

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

// =============================================================================
// TEST SUITE
// =============================================================================

test.describe('e2e test agent module exists', () => {

  test('should load the homepage and navigate to an agent detail page', async ({ page }) => {
    // Step 1: Go to the application's base URL
    await page.goto('/');

    // Step 2: Assert that the page title is correct. This is a good sanity check.
    await expect(page).toHaveTitle(/Agent-o-rama/);

    // Step 3: Get the agent row using the helper function.
    const agentRow = await getE2ETestAgentRow(page);

    // Step 4: Click the agent row to navigate.
    await agentRow.click();

    await expect(page).toHaveURL(/\/agents\/.*com\.rpl\.agent\.e2e-test-agent.*E2ETestAgentModule.*/i);
    console.log('Successfully verified agent detail page.');
  });
});

test.describe('Dataset crud', () => {

  test('should load the homepage and navigate to an agent detail page', async ({ page }) => {
    // Step 1: Go to the application's base URL.
    await page.goto('/');

    // Step 2: Assert that the page title is correct. This is a good sanity check.
    await expect(page).toHaveTitle(/Agent-o-rama/);

    // Step 3: Get the agent row using the helper function.
    const agentRow = await getE2ETestAgentRow(page);

    // Step 4: Click the agent row to navigate.
    await agentRow.click();

    await expect(page).toHaveURL(/\/agents\/.*com\.rpl\.agent\.e2e-test-agent.*E2ETestAgentModule.*/i);
    console.log('Successfully verified agent detail page.');

    const datasetsLink = page.getByText('Datasets & Experiments');
    await expect(datasetsLink).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified datasets link.');

    await datasetsLink.click();

    await expect(page).toHaveURL(/\/agents\/.*com\.rpl\.agent\.e2e-test-agent.*E2ETestAgentModule.*\/datasets.*/i);
    console.log('Successfully verified datasets page.');

    const newDatasetButton = page.getByRole('button', { name: 'Create Dataset' }).first();
    await expect(newDatasetButton).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified new dataset button.');

    await newDatasetButton.click();
    // fill in the forms
    const datasetName = `Test Dataset ${randomUUID()}`;
    await page.getByLabel('Name').fill(datasetName);
    await page.getByLabel('Description').fill('Test Description');
    await page.getByLabel('Input JSON Schema').fill('{}');
    await page.getByLabel('Output JSON Schema').fill('{}');
    await page.locator('[role="dialog"]').getByRole('button', { name: 'Create Dataset' }).click();

    // find the created datset in the invalidated/requeried table
    // the tilte is in an h3 tag
    // might be multiple from previous runs, so we need to find the one that is not loading
    await expect(page.getByText(datasetName)).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified created dataset.');

    // find the dataset row in the table and locate the edit button
    // First try to find by exact text, then fallback to partial text matching
    let datasetRow = page.locator('table tbody tr').filter({ hasText: datasetName });
    if (!(await datasetRow.isVisible({ timeout: 5000 }))) {
      // Try partial matching if exact match fails
      datasetRow = page.locator('table tbody tr').filter({ hasText: datasetName.split(' ')[0] });
    }
    const editButton = datasetRow.locator('button').filter({ hasText: 'Edit' });
    await expect(editButton).toBeVisible({ timeout: 30000 });
    await editButton.click();

    const newDatasetName = `Modified Dataset ${randomUUID()}`;

    // fill in the forms
    await page.getByLabel('Name').fill(newDatasetName);
    await page.getByLabel('Description').fill('New Description');
    await page.getByRole('button', { name: 'Save Changes' }).click();

    await expect(page.getByText(newDatasetName)).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified updated dataset.');

    page.on('dialog', dialog => dialog.accept());

    // get the delete button scoped to the table row containing this dataset name
    let deleteRow = page.locator('table tbody tr').filter({ hasText: newDatasetName });
    if (!(await deleteRow.isVisible({ timeout: 5000 }))) {
      // Try partial matching if exact match fails
      deleteRow = page.locator('table tbody tr').filter({ hasText: newDatasetName.split(' ')[0] });
    }
    const deleteButton = deleteRow.locator('button').filter({ hasText: 'Delete' });
    await expect(deleteButton).toBeVisible({ timeout: 30000 });
    await deleteButton.click();

    // wait for the dataset to be deleted
    await expect(page.getByText(newDatasetName)).not.toBeVisible({ timeout: 30000 });
    console.log('Successfully verified deleted dataset.');
  });
});

test.describe('Dataset example crud', () => {

  test('example CRUD flow: add, view, edit, tag, delete', async ({ page }) => {
    // Step 1: Go to the application's base URL.
    await page.goto('/');

    // Step 2: Assert that the page title is correct. This is a good sanity check.
    await expect(page).toHaveTitle(/Agent-o-rama/);

    // Step 3: Get the agent row using the helper function.
    const agentRow = await getE2ETestAgentRow(page);

    // Step 4: Click the agent row to navigate.
    await agentRow.click();

    await expect(page).toHaveURL(/\/agents\/.*com\.rpl\.agent\.e2e-test-agent.*E2ETestAgentModule.*/i);
    console.log('Successfully verified agent detail page.');

    const datasetsLink = page.getByText('Datasets & Experiments');
    await expect(datasetsLink).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified datasets link.');

    await datasetsLink.click();

    await expect(page).toHaveURL(/\/agents\/.*com\.rpl\.agent\.e2e-test-agent.*E2ETestAgentModule.*\/datasets.*/i);
    console.log('Successfully verified datasets page.');

    const newDatasetButton = page.getByRole('button', { name: 'Create Dataset' }).first();
    await expect(newDatasetButton).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified new dataset button.');

    await newDatasetButton.click();
    // fill in the forms
    const datasetName = `Test Dataset ${randomUUID()}`;
    await page.getByLabel('Name').fill(datasetName);
    await page.getByLabel('Description').fill('Test Description');
    await page.getByLabel('Input JSON Schema').fill('{}');
    await page.getByLabel('Output JSON Schema').fill('{}');
    await page.locator('[role="dialog"]').getByRole('button', { name: 'Create Dataset' }).click();

    // find the created datset in the invalidated/requeried table
    // the tilte is in an h3 tag
    // might be multiple from previous runs, so we need to find the one that is not loading
    await expect(page.getByText(datasetName)).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified created dataset.');

    // Navigate to dataset detail page by clicking the dataset link
    await page.getByRole('link', { name: datasetName }).first().click();
    await page.getByRole('link', { name: 'Examples' }).click();

    // Wait for the dataset detail page controls to appear
    const addExampleHeaderButton = page.getByRole('button', { name: 'Add Example' });
    await expect(addExampleHeaderButton).toBeVisible({ timeout: 30000 });

    // Open Add Example modal
    await addExampleHeaderButton.click();

    // Wait for modal to appear and locate it
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible({ timeout: 10000 });

    // Create a unique example
    const exampleId1 = randomUUID();
    const exampleInput = { message: 'hello-from-e2e', id: exampleId1 };
    const exampleOutput = { expected: true, id: exampleId1 };

    // Fill form fields within the modal
    await modal.getByLabel('Input (JSON)').fill(JSON.stringify(exampleInput, null, 2));
    await modal.getByLabel('Reference Output (JSON, Optional)').fill(JSON.stringify(exampleOutput, null, 2));

    // Submit Add Example form - find the button within the modal
    await modal.getByRole('button', { name: 'Add Example' }).click();

    // Verify the example appears in the table by looking for the input column specifically
    await expect(page.locator('table tbody tr').filter({ hasText: exampleId1 })).toBeVisible({ timeout: 30000 });

    // Open the Example Viewer modal by clicking on the example row
    await page.locator('table tbody tr').filter({ hasText: exampleId1 }).click();
    await expect(page.getByText('Example Details')).toBeVisible({ timeout: 30000 });

    // Start listening for confirm dialogs for destructive actions
    page.on('dialog', dialog => dialog.accept());

    // Edit the example using inline editing
    // Find the Input field's edit button and click it
    const inputEditButton = page.locator('[role="dialog"]').getByRole('button', { name: 'Edit' }).first();
    await expect(inputEditButton).toBeVisible({ timeout: 30000 });
    await inputEditButton.click();

    // Wait for the textarea to appear and be editable
    const inputTextarea = page.locator('[role="dialog"]').locator('textarea').first();
    await expect(inputTextarea).toBeVisible({ timeout: 30000 });

    // Clear and fill with new content
    const exampleId2 = randomUUID();
    const updatedInput = { message: 'updated-from-e2e', id: exampleId2 };
    await inputTextarea.fill(JSON.stringify(updatedInput, null, 2));

    // Click the Save button for this field
    const saveButton = page.locator('[role="dialog"]').getByRole('button', { name: 'Save' }).first();
    await expect(saveButton).toBeVisible({ timeout: 30000 });
    await saveButton.click();

    // Wait for the save to complete and the field to return to view mode
    await expect(inputTextarea).not.toBeVisible({ timeout: 30000 });

    // Verify the updated content is visible in the details modal (should show in the view mode)
    await expect(page.locator('[role="dialog"]').getByText(exampleId2)).toBeVisible({ timeout: 30000 });

    // Add a tag (tags are only available in the Details modal, not the Edit modal)
    const tagName = `e2e-tag-${randomUUID()}`;
    const tagInput = page.getByPlaceholder('Add a tag and press Enter...');
    await expect(tagInput).toBeVisible({ timeout: 30000 });
    await tagInput.fill(tagName);
    await page.keyboard.press('Enter');
    // Target the tag specifically within the modal dialog to avoid strict mode violation
    await expect(page.locator('[role="dialog"]').getByText(tagName)).toBeVisible({ timeout: 30000 });

    // Remove the tag
    await page.getByRole('button', { name: `Remove ${tagName}` }).click();
    // Also target the tag removal check specifically within the modal dialog
    await expect(page.locator('[role="dialog"]').getByText(tagName)).not.toBeVisible({ timeout: 30000 });

    // Delete the example
    await page.getByRole('button', { name: 'Delete' }).click();

    // Verify the example is gone from the table
    await expect(page.locator('table tbody tr').filter({ hasText: exampleId2 })).not.toBeVisible({ timeout: 30000 });

    // Cleanup: navigate back via sidebar link
    const datasetsLinkBack3 = page.getByText('Datasets & Experiments');
    await expect(datasetsLinkBack3).toBeVisible({ timeout: 30000 });
    await datasetsLinkBack3.click();

    const deleteButton = page.locator('table tbody tr').filter({ hasText: datasetName }).locator('button').filter({ hasText: 'Delete' });
    await expect(deleteButton).toBeVisible({ timeout: 30000 });
    await deleteButton.click();
    await expect(page.getByText(datasetName)).not.toBeVisible({ timeout: 30000 });
    console.log('Successfully cleaned up dataset.');
  });
});

test.describe('Inline editing validation', () => {

  test('should validate inline editing behavior with proper JSON formatting', async ({ page }) => {
    // Setup: Navigate to dataset detail page
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();

    const datasetsLink = page.getByText('Datasets & Experiments');
    await expect(datasetsLink).toBeVisible({ timeout: 30000 });
    await datasetsLink.click();

    // Create a test dataset
    const newDatasetButton = page.getByRole('button', { name: 'Create Dataset' }).first();
    await newDatasetButton.click();
    
    const datasetName = `Inline Edit Test ${randomUUID()}`;
    await page.getByLabel('Name').fill(datasetName);
    await page.getByLabel('Description').fill('Testing inline editing');
    await page.getByLabel('Input JSON Schema').fill('{}');
    await page.getByLabel('Output JSON Schema').fill('{}');
    await page.locator('[role="dialog"]').getByRole('button', { name: 'Create Dataset' }).click();

    await expect(page.getByText(datasetName)).toBeVisible({ timeout: 30000 });
    await page.getByRole('link', { name: datasetName }).first().click();
    await page.getByRole('link', { name: 'Examples' }).click();

    // Add an example with a string value to test JSON formatting
    const addExampleButton = page.getByRole('button', { name: 'Add Example' });
    await addExampleButton.click();

    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible({ timeout: 10000 });

    // Create example with string values that need proper JSON formatting
    const stringInput = "hello world";
    const stringOutput = "goodbye world";
    
    await modal.getByLabel('Input (JSON)').fill(`"${stringInput}"`);
    await modal.getByLabel('Reference Output (JSON, Optional)').fill(`"${stringOutput}"`);
    await modal.getByRole('button', { name: 'Add Example' }).click();

    // Open the example details
    await page.locator('table tbody tr').filter({ hasText: stringInput }).click();
    await expect(page.getByRole('dialog').getByRole('heading', { name: 'Example Details' })).toBeVisible({ timeout: 30000 });

    // Test inline editing of Input field
    const inputEditButton = page.locator('[role="dialog"]').getByRole('button', { name: 'Edit' }).first();
    await inputEditButton.click();

    const inputTextarea = page.locator('[role="dialog"]').locator('textarea').first();
    await expect(inputTextarea).toBeVisible({ timeout: 30000 });

    // Verify the textarea is initialized with properly formatted JSON (with quotes)
    const textareaValue = await inputTextarea.inputValue();
    console.log('Textarea initialized with:', textareaValue);
    
    // The value should be properly formatted JSON with quotes around the string
    expect(textareaValue).toContain('"hello world"');

    // Edit the value
    const newStringInput = "updated hello world";
    await inputTextarea.fill(`"${newStringInput}"`);

    // Save the changes
    const saveButton = page.locator('[role="dialog"]').getByRole('button', { name: 'Save' }).first();
    await saveButton.click();

    // Wait for save to complete
    await expect(inputTextarea).not.toBeVisible({ timeout: 30000 });

    // Verify the change is reflected in the UI
    await expect(page.locator('[role="dialog"]').getByText(newStringInput)).toBeVisible({ timeout: 30000 });

    // Test that the change is also reflected in the main table (query invalidation)
    await page.locator('[role="dialog"]').getByRole('button', { name: '×' }).click(); // Close modal
    await expect(page.locator('table tbody tr').filter({ hasText: newStringInput })).toBeVisible({ timeout: 30000 });

    // Cleanup
    page.on('dialog', dialog => dialog.accept());
    
    // Navigate back via sidebar link instead of history
    const datasetsLinkBack2 = page.getByText('Datasets & Experiments');
    await expect(datasetsLinkBack2).toBeVisible({ timeout: 30000 });
    await datasetsLinkBack2.click();
    
    const deleteButton = page.locator('table tbody tr').filter({ hasText: datasetName }).locator('button').filter({ hasText: 'Delete' });
    await deleteButton.click();
    await expect(page.getByText(datasetName)).not.toBeVisible({ timeout: 30000 });
  });
});

test.describe('Dataset snapshot dropdown', () => {

  test('create/select/delete snapshot via dropdown', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();

    await expect(page).toHaveURL(/\/agents\/.*com\.rpl\.agent\.e2e-test-agent.*E2ETestAgentModule.*/i);

    const datasetsLink = page.getByText('Datasets & Experiments');
    await expect(datasetsLink).toBeVisible({ timeout: 30000 });
    await datasetsLink.click();

    await expect(page).toHaveURL(/\/agents\/.*com\.rpl\.agent\.e2e-test-agent.*E2ETestAgentModule.*\/datasets.*/i);

    const newDatasetButton = page.getByRole('button', { name: 'Create Dataset' }).first();
    await expect(newDatasetButton).toBeVisible({ timeout: 30000 });
    await newDatasetButton.click();

    const datasetName = `Snapshot Test Dataset ${randomUUID()}`;
    await page.getByLabel('Name').fill(datasetName);
    await page.getByLabel('Description').fill('Snapshot dropdown e2e');
    await page.getByLabel('Input JSON Schema').fill('{}');
    await page.getByLabel('Output JSON Schema').fill('{}');
    await page.locator('[role="dialog"]').getByRole('button', { name: 'Create Dataset' }).click();

    // Wait for modal to close
    await expect(page.locator('[role="dialog"]')).toBeHidden({ timeout: 30000 });
    
    await expect(page.getByText(datasetName)).toBeVisible({ timeout: 30000 });

    await page.getByRole('link', { name: datasetName }).first().click();
    await page.getByRole('link', { name: 'Examples' }).click();

    // Ensure Examples tab controls are visible and snapshot dropdown shows Latest
    await expect(page.getByText('Snapshot:')).toBeVisible({ timeout: 30000 });
    await expect(page.getByRole('button', { name: 'Latest (Working Copy)' }).first()).toBeVisible({ timeout: 30000 });

    // Add a couple examples first (snapshots need examples to work)
    const addExampleButton = page.getByRole('button', { name: 'Add Example' });
    await expect(addExampleButton).toBeVisible({ timeout: 30000 });
    
    // Add first example
    await addExampleButton.click();
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible({ timeout: 10000 });
    await modal.getByLabel('Input (JSON)').fill('{"message": "test input 1"}');
    await modal.getByLabel('Reference Output (JSON, Optional)').fill('{"response": "test output 1"}');
    await modal.getByRole('button', { name: 'Add Example' }).click();
    await expect(modal).toBeHidden({ timeout: 30000 });

    // Add second example
    await addExampleButton.click();
    await expect(modal).toBeVisible({ timeout: 10000 });
    await modal.getByLabel('Input (JSON)').fill('{"message": "test input 2"}');
    await modal.getByLabel('Reference Output (JSON, Optional)').fill('{"response": "test output 2"}');
    await modal.getByRole('button', { name: 'Add Example' }).click();
    await expect(modal).toBeHidden({ timeout: 30000 });

    // Open dropdown and create new snapshot
    const snapshotButton = page.getByRole('button', { name: 'Latest (Working Copy)' }).first();
    await snapshotButton.click();
    await page.getByText('New snapshot').click();

    const snapshotModal = page.locator('[role="dialog"]');
    await expect(snapshotModal).toBeVisible({ timeout: 10000 });

    const snapshotName = `snap-${randomUUID()}`;
    await snapshotModal.getByLabel('New Snapshot Name').fill(snapshotName);
    await snapshotModal.getByRole('button', { name: 'Create Snapshot' }).click();

    // Wait for modal to close (create finished), then verify auto-select behavior
    await expect(snapshotModal).toBeHidden({ timeout: 30000 });
    
    // NEW: Verify the snapshot is automatically selected (auto-select feature)
    await expect(page.getByRole('button', { name: snapshotName })).toBeVisible({ timeout: 30000 });
    
    // NEW: Verify read-only banner appears when viewing the snapshot
    await expect(page.getByText('Read-only: You are viewing an immutable snapshot. Editing is disabled.')).toBeVisible({ timeout: 10000 });
    
    // NEW: Verify Add Example button is disabled in read-only mode
    const addExampleButtonReadOnly = page.getByRole('button', { name: 'Add Example' });
    await expect(addExampleButtonReadOnly).toBeDisabled({ timeout: 10000 });

    // Delete the snapshot via dropdown delete control
    page.on('dialog', dialog => dialog.accept());
    const selectedSnapshotButton = page.getByRole('button', { name: snapshotName }).first();
    await selectedSnapshotButton.click();
    await expect(page.getByTitle(`Delete ${snapshotName}`)).toBeVisible({ timeout: 30000 });
    await page.getByTitle(`Delete ${snapshotName}`).click();

    // After deletion, the dropdown should return to Latest (Working Copy)
    await expect(page.getByRole('button', { name: 'Latest (Working Copy)' }).first()).toBeVisible({ timeout: 30000 });

    // Cleanup: navigate back via sidebar link
    const datasetsLinkBack3 = page.getByText('Datasets & Experiments');
    await expect(datasetsLinkBack3).toBeVisible({ timeout: 30000 });
    await datasetsLinkBack3.click();
    
    const deleteButton = page.locator('table tbody tr').filter({ hasText: datasetName }).locator('button').filter({ hasText: 'Delete' });
    await expect(deleteButton).toBeVisible({ timeout: 30000 });
    await deleteButton.click();
    await expect(page.getByText(datasetName)).not.toBeVisible({ timeout: 30000 });
  });
});

test.describe('Form Validation and Error Handling', () => {

  test('should display a backend validation error on the create dataset form', async ({ page }) => {
    // 1. SETUP: Navigate to the datasets page
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    // Find the agent using the helper function
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();

    const datasetsLink = page.getByText('Datasets & Experiments');
    await expect(datasetsLink).toBeVisible({ timeout: 30000 });
    await datasetsLink.click();

    await expect(page).toHaveURL(/\/agents\/.*com\.rpl\.agent\.e2e-test-agent.*E2ETestAgentModule.*\/datasets.*/i);
    console.log('Successfully navigated to datasets page.');

    // 2. ACTION: Open the modal and submit an invalid form
    const newDatasetButton = page.getByRole('button', { name: 'Create Dataset' }).first();
    await newDatasetButton.click();

    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();

    // Fill in valid data for required fields
    const datasetName = `Invalid Schema Test ${randomUUID()}`;
    await modal.getByLabel('Name').fill(datasetName);
    await modal.getByLabel('Description').fill('This form submission should fail.');

    // INTENTIONALLY provide an invalid JSON schema, just like in your bug report
    await modal.getByLabel('Input JSON Schema').fill('[]');

    // Find and click the submit button
    const submitButton = modal.getByRole('button', { name: 'Create Dataset' });
    await submitButton.click();

    // 3. ASSERTION: Verify the UI handles the error correctly

    // B) Check that the modal is still visible.
    await expect(modal).toBeVisible();
    console.log('Modal remained open after failed submission.');

    // C) Check that the specific backend error message is displayed within the modal.
    // We use a regular expression to make the test robust against minor text changes.
    const errorMessage = modal.getByText(/Invalid JSON schema:.*array found, \[object, boolean\] expected/i);
    await expect(errorMessage).toBeVisible();
    console.log('Backend error message is visible to the user.');
    
    // D) (Optional but good) Verify we haven't been navigated away
    await expect(page).toHaveURL(/\/datasets/);
    console.log('Page URL did not change.');

    // Cleanup: Close the modal
    await modal.getByRole('button', { name: '×' }).click();
    await expect(modal).not.toBeVisible();
  });
});
