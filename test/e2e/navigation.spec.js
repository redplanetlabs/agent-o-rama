import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';

test.describe('research agent module exists', () => {

  test('should load the homepage and navigate to an agent detail page', async ({ page }) => {
    // Step 1: Go to the application's base URL.
    await page.goto('/');

    // Step 2: Assert that the page title is correct. This is a good sanity check.
    await expect(page).toHaveTitle(/Agent-o-rama/);

    // Step 3: Wait for an agent link to be visible on the page.
    // The UI fetches this data asynchronously, so Playwright's auto-waiting is essential here.
    // We'll look for an agent from your examples.
    const agentName = 'com.rpl.agent.research-agent/ResearchAgentModule:researcher';
    const agentLink = page.getByText(agentName);

    // Wait up to 30 seconds for the agent to appear. The first load can be slow.
    await expect(agentLink).toBeVisible({ timeout: 30000 });
    console.log(`Found agent: ${agentName}`);

    // Step 4: Click the agent link to navigate.
    await agentLink.click();

    await expect(page).toHaveURL(/\/agents\/.*ResearchAgentModule.*/i);
    console.log('Successfully verified agent detail page.');
  });
});

test.describe('Dataset crud', () => {

  test('should load the homepage and navigate to an agent detail page', async ({ page }) => {
    // Step 1: Go to the application's base URL.
    await page.goto('/');

    // Step 2: Assert that the page title is correct. This is a good sanity check.
    await expect(page).toHaveTitle(/Agent-o-rama/);

    // Step 3: Wait for an agent link to be visible on the page.
    // The UI fetches this data asynchronously, so Playwright's auto-waiting is essential here.
    // We'll look for an agent from your examples.
    const agentName = 'com.rpl.agent.research-agent/ResearchAgentModule:researcher';
    const agentLink = page.getByText(agentName);

    // Wait up to 30 seconds for the agent to appear. The first load can be slow.
    await expect(agentLink).toBeVisible({ timeout: 30000 });
    console.log(`Found agent: ${agentName}`);

    // Step 4: Click the agent link to navigate.
    await agentLink.click();

    await expect(page).toHaveURL(/\/agents\/.*ResearchAgentModule.*/i);
    console.log('Successfully verified agent detail page.');

    const datasetsLink = page.getByText('Datasets & Experiments');
    await expect(datasetsLink).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified datasets link.');

    await datasetsLink.click();

    await expect(page).toHaveURL(/\/agents\/.*ResearchAgentModule\/datasets.*/i);
    console.log('Successfully verified datasets page.');

    const newDatasetButton = page.getByText('New Dataset');
    await expect(newDatasetButton).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified new dataset button.');

    await newDatasetButton.click();
    // fill in the forms
    const datasetName = `Test Dataset ${randomUUID()}`;
    await page.getByLabel('Name').fill(datasetName);
    await page.getByLabel('Description').fill('Test Description');
    await page.getByLabel('Input JSON Schema').fill('{}');
    await page.getByLabel('Output JSON Schema').fill('{}');
    await page.getByRole('button', { name: 'Create Dataset' }).click();

    // find the created datset in the invalidated/requeried table
    // the tilte is in an h3 tag
    // might be multiple from previous runs, so we need to find the one that is not loading
    await expect(page.getByRole('heading', { name: datasetName })).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified created dataset.');

    // find the dataset card by the heading link, then go up to the card and find the edit button
    const datasetCard = page.getByRole('heading', { name: datasetName }).locator('..').locator('..');
    const editButton = datasetCard.getByTitle('Edit Dataset');
    await expect(editButton).toBeVisible({ timeout: 30000 });
    await editButton.click();

    const newDatasetName = `Modified Dataset ${randomUUID()}`;

    // fill in the forms
    await page.getByLabel('Name').fill(newDatasetName);
    await page.getByLabel('Description').fill('New Description');
    await page.getByRole('button', { name: 'Save Changes' }).click();

    await expect(page.getByRole('heading', { name: newDatasetName })).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified updated dataset.');

    page.on('dialog', dialog => dialog.accept());

    // get the delete button scoped to the card containing this dataset name
    const deleteButton = page.getByRole('heading', { name: newDatasetName }).locator('..').locator('..').getByTitle('Delete Dataset');
    await expect(deleteButton).toBeVisible({ timeout: 30000 });
    await deleteButton.click();

    // wait for the dataset to be deleted
    await expect(page.getByRole('heading', { name: newDatasetName })).not.toBeVisible({ timeout: 30000 });
    console.log('Successfully verified deleted dataset.');
  });
});

test.describe('Dataset example crud', () => {

  test('example CRUD flow: add, view, edit, tag, delete', async ({ page }) => {
    // Step 1: Go to the application's base URL.
    await page.goto('/');

    // Step 2: Assert that the page title is correct. This is a good sanity check.
    await expect(page).toHaveTitle(/Agent-o-rama/);

    // Step 3: Wait for an agent link to be visible on the page.
    // The UI fetches this data asynchronously, so Playwright's auto-waiting is essential here.
    // We'll look for an agent from your examples.
    const agentName = 'com.rpl.agent.research-agent/ResearchAgentModule:researcher';
    const agentLink = page.getByText(agentName);

    // Wait up to 30 seconds for the agent to appear. The first load can be slow.
    await expect(agentLink).toBeVisible({ timeout: 30000 });
    console.log(`Found agent: ${agentName}`);

    // Step 4: Click the agent link to navigate.
    await agentLink.click();

    await expect(page).toHaveURL(/\/agents\/.*ResearchAgentModule.*/i);
    console.log('Successfully verified agent detail page.');

    const datasetsLink = page.getByText('Datasets & Experiments');
    await expect(datasetsLink).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified datasets link.');

    await datasetsLink.click();

    await expect(page).toHaveURL(/\/agents\/.*ResearchAgentModule\/datasets.*/i);
    console.log('Successfully verified datasets page.');

    const newDatasetButton = page.getByText('New Dataset');
    await expect(newDatasetButton).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified new dataset button.');

    await newDatasetButton.click();
    // fill in the forms
    const datasetName = `Test Dataset ${randomUUID()}`;
    await page.getByLabel('Name').fill(datasetName);
    await page.getByLabel('Description').fill('Test Description');
    await page.getByLabel('Input JSON Schema').fill('{}');
    await page.getByLabel('Output JSON Schema').fill('{}');
    await page.getByRole('button', { name: 'Create Dataset' }).click();

    // find the created datset in the invalidated/requeried table
    // the tilte is in an h3 tag
    // might be multiple from previous runs, so we need to find the one that is not loading
    await expect(page.getByRole('heading', { name: datasetName })).toBeVisible({ timeout: 30000 });
    console.log('Successfully verified created dataset.');

    // Navigate to dataset detail page by clicking the dataset link
    await page.getByRole('link', { name: datasetName }).first().click();

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

    // Edit the example
    // TODO test edit from the ... menu, not just the dialog box menu
    await page.getByRole('button', { name: 'Edit' }).first().click();
    const exampleId2 = randomUUID();
    const updatedInput = { message: 'updated-from-e2e', id: exampleId2 };
    await page.getByLabel('Input (JSON)').fill(JSON.stringify(updatedInput, null, 2));
    await page.getByRole('button', { name: 'Save Changes' }).click();

    // After editing and saving, the modal closes completely
    // We need to reopen the Example Details modal by clicking the table row again
    await page.locator('table tbody tr').filter({ hasText: exampleId2 }).click();
    await expect(page.getByText('Example Details')).toBeVisible({ timeout: 30000 });

    // Verify the updated content is visible in the details modal
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

    // Cleanup: go back to dataset list and delete the dataset
    await page.goBack();
    const deleteButton = page.getByRole('heading', { name: datasetName }).locator('..').locator('..').getByTitle('Delete Dataset');
    await expect(deleteButton).toBeVisible({ timeout: 30000 });
    await deleteButton.click();
    await expect(page.getByRole('heading', { name: datasetName })).not.toBeVisible({ timeout: 30000 });
    console.log('Successfully cleaned up dataset.');
  });
});

test.describe('Dataset snapshot dropdown', () => {

  test('create/select/delete snapshot via dropdown', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentName = 'com.rpl.agent.research-agent/ResearchAgentModule:researcher';
    const agentLink = page.getByText(agentName);
    await expect(agentLink).toBeVisible({ timeout: 30000 });
    await agentLink.click();

    await expect(page).toHaveURL(/\/agents\/.*ResearchAgentModule.*/i);

    const datasetsLink = page.getByText('Datasets & Experiments');
    await expect(datasetsLink).toBeVisible({ timeout: 30000 });
    await datasetsLink.click();

    await expect(page).toHaveURL(/\/agents\/.*ResearchAgentModule\/datasets.*/i);

    const newDatasetButton = page.getByText('New Dataset');
    await expect(newDatasetButton).toBeVisible({ timeout: 30000 });
    await newDatasetButton.click();

    const datasetName = `Snapshot Test Dataset ${randomUUID()}`;
    await page.getByLabel('Name').fill(datasetName);
    await page.getByLabel('Description').fill('Snapshot dropdown e2e');
    await page.getByLabel('Input JSON Schema').fill('{}');
    await page.getByLabel('Output JSON Schema').fill('{}');
    await page.getByRole('button', { name: 'Create Dataset' }).click();

    await expect(page.getByRole('heading', { name: datasetName })).toBeVisible({ timeout: 30000 });

    await page.getByRole('link', { name: datasetName }).first().click();

    // Ensure Examples tab controls are visible and snapshot dropdown shows Latest
    await expect(page.getByText('Snapshot:')).toBeVisible({ timeout: 30000 });
    await expect(page.getByRole('button', { name: 'Latest (Working Copy)' }).first()).toBeVisible({ timeout: 30000 });

    // Open dropdown and create new snapshot
    const snapshotButton = page.getByRole('button', { name: 'Latest (Working Copy)' }).first();
    await snapshotButton.click();
    await page.getByText('New snapshot').click();

    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible({ timeout: 10000 });

    const snapshotName = `snap-${randomUUID()}`;
    await modal.getByLabel('New Snapshot Name').fill(snapshotName);
    await modal.getByRole('button', { name: 'Create Snapshot' }).click();

    // Re-open dropdown and select the newly created snapshot
    await snapshotButton.click();
    await expect(page.getByText(snapshotName)).toBeVisible({ timeout: 30000 });
    await page.getByText(snapshotName).click();

    // Verify the button now shows the selected snapshot
    await expect(page.getByRole('button', { name: snapshotName })).toBeVisible({ timeout: 30000 });

    // Delete the snapshot via dropdown delete control
    page.on('dialog', dialog => dialog.accept());
    const selectedSnapshotButton = page.getByRole('button', { name: snapshotName }).first();
    await selectedSnapshotButton.click();
    await expect(page.getByTitle(`Delete ${snapshotName}`)).toBeVisible({ timeout: 30000 });
    await page.getByTitle(`Delete ${snapshotName}`).click();

    // After deletion, the dropdown should return to Latest (Working Copy)
    await expect(page.getByRole('button', { name: 'Latest (Working Copy)' }).first()).toBeVisible({ timeout: 30000 });

    // Cleanup: go back and delete dataset
    await page.goBack();
    const deleteButton = page.getByRole('heading', { name: datasetName }).locator('..').locator('..').getByTitle('Delete Dataset');
    await expect(deleteButton).toBeVisible({ timeout: 30000 });
    await deleteButton.click();
    await expect(page.getByRole('heading', { name: datasetName })).not.toBeVisible({ timeout: 30000 });
  });
});
