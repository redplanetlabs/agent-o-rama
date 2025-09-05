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

    // Verify the example appears in the table (look for unique id text)
    await expect(page.getByText(exampleId1)).toBeVisible({ timeout: 30000 });

    // Open the Example Viewer modal by clicking on the example row
    await page.getByText(exampleId1).first().click();
    await expect(page.getByText('Example Details')).toBeVisible({ timeout: 30000 });

    // Start listening for confirm dialogs for destructive actions
    page.on('dialog', dialog => dialog.accept());

    // Edit the example
    await page.getByRole('button', { name: 'Edit' }).first().click();
    const exampleId2 = randomUUID();
    const updatedInput = { message: 'updated-from-e2e', id: exampleId2 };
    await page.getByLabel('Input (JSON)').fill(JSON.stringify(updatedInput, null, 2));
    await page.getByRole('button', { name: 'Save Changes' }).click();

    // Verify the updated content is visible in the viewer
    await expect(page.getByText(exampleId2)).toBeVisible({ timeout: 30000 });

    // Add a tag
    const tagName = `e2e-tag-${randomUUID()}`;
    const tagInput = page.getByPlaceholder('Add a tag and press Enter...');
    await expect(tagInput).toBeVisible({ timeout: 30000 });
    await tagInput.fill(tagName);
    await page.keyboard.press('Enter');
    await expect(page.getByText(tagName)).toBeVisible({ timeout: 30000 });

    // Remove the tag
    await page.getByRole('button', { name: `Remove ${tagName}` }).click();
    await expect(page.getByText(tagName)).not.toBeVisible({ timeout: 30000 });

    // Delete the example
    await page.getByRole('button', { name: 'Delete' }).click();

    // Close the viewer if still open
    await page.keyboard.press('Escape');

    // Verify the example is gone from the list
    await expect(page.getByText(exampleId2)).not.toBeVisible({ timeout: 30000 });

    // Cleanup: go back to dataset list and delete the dataset
    await page.goBack();
    const deleteButton = page.getByRole('heading', { name: datasetName }).locator('..').locator('..').getByTitle('Delete Dataset');
    await expect(deleteButton).toBeVisible({ timeout: 30000 });
    await deleteButton.click();
    await expect(page.getByRole('heading', { name: datasetName })).not.toBeVisible({ timeout: 30000 });
    console.log('Successfully cleaned up dataset.');
  });
});
