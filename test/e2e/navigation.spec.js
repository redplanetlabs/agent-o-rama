import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';

test.describe('Agent-O-Rama Navigation', () => {

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

test.describe('Agent-O-Rama Navigation to Datasets', () => {

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
  });
});
