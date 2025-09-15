import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getResearchAgentRow, addExample } from './helpers.js';

// =============================================================================
// TEST SUITE
// =============================================================================

test.describe('Dataset Example Tagging and Bulk Operations', () => {

  test('should handle bulk tagging and deletion of examples', async ({ page }) => {
    const uniqueId = randomUUID().substring(0, 8);
    const datasetName = `Tagging Test Dataset ${uniqueId}`;
    const tagA = `tag-A-${uniqueId}`;
    const tagB = `tag-B-${uniqueId}`;

    // --- 1. SETUP: Navigate and create a new dataset ---
    console.log('--- Starting Test Setup ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getResearchAgentRow(page);
    await agentRow.click();

    await page.getByText('Datasets & Experiments').click();
    await expect(page).toHaveURL(new RegExp(`/agents/.*com\\.rpl\\.agent\\.research-agent.*ResearchAgentModule.*/datasets`));

    // Create a new dataset
    await page.getByRole('button', { name: 'Create Dataset' }).first().click();
    const createModal = page.locator('[role="dialog"]');
    await createModal.getByLabel('Name').fill(datasetName);
    await createModal.getByRole('button', { name: 'Create Dataset' }).click();
    await expect(createModal).not.toBeVisible();
    await expect(page.getByText(datasetName)).toBeVisible();
    console.log(`Created dataset: ${datasetName}`);

    // Navigate into the new dataset
    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();
    await expect(page.getByRole('heading', { name: datasetName })).toBeVisible();
    console.log('--- Test Setup Complete ---');


    // --- 2. CREATE EXAMPLES ---
    console.log('--- Creating Examples ---');
    const example1 = { input: { id: `ex1-${uniqueId}` }, output: { res: 1 } };
    const example2 = { input: { id: `ex2-${uniqueId}` }, output: { res: 2 } };
    const example3 = { input: { id: `ex3-${uniqueId}` }, output: { res: 3 } };

    await addExample(page, example1);
    await addExample(page, example2);
    await addExample(page, example3);

    const row1 = page.locator('table tbody tr').filter({ hasText: example1.input.id });
    const row2 = page.locator('table tbody tr').filter({ hasText: example2.input.id });
    const row3 = page.locator('table tbody tr').filter({ hasText: example3.input.id });
    console.log('--- Examples Created ---');


    // --- 3. SELECT EXAMPLES AND ADD TAG A ---
    console.log('--- Testing Add Tag A ---');
    // Select examples 1 and 3
    await row1.locator('td').first().click();
    await row3.locator('td').first().click();

    await page.getByRole('button', { name: 'Add Tag...' }).click();
    const tagModal = page.locator('[role="dialog"]');
    await expect(tagModal).toBeVisible();
    await tagModal.getByLabel('Tag to add').fill(tagA);
    await tagModal.getByRole('button', { name: 'Add Tag' }).click();
    await expect(tagModal).not.toBeVisible();

    // Verify tags
    await expect(row1.locator('td').nth(3)).toHaveText(new RegExp(tagA));
    await expect(row2.locator('td').nth(3)).not.toHaveText(new RegExp(tagA));
    await expect(row3.locator('td').nth(3)).toHaveText(new RegExp(tagA));
    console.log('--- Tag A Added and Verified ---');

    // Deselect all by clicking the header checkbox twice
    const headerCheckbox = page.locator('table thead th').first();
    await headerCheckbox.click();
    await headerCheckbox.click();

    // --- 4. SELECT EXAMPLES AND ADD TAG B ---
    console.log('--- Testing Add Tag B ---');
    // Select examples 1 and 3 again
    await row1.locator('td').first().click();
    await row3.locator('td').first().click();

    await page.getByRole('button', { name: 'Add Tag...' }).click();
    await expect(tagModal).toBeVisible();
    await tagModal.getByLabel('Tag to add').fill(tagB);
    await tagModal.getByRole('button', { name: 'Add Tag' }).click();
    await expect(tagModal).not.toBeVisible();

    // Verify both tags are present on selected rows
    await expect(row1.locator('td').nth(3)).toHaveText(new RegExp(`${tagA}, ${tagB}|${tagB}, ${tagA}`));
    await expect(row2.locator('td').nth(3)).not.toHaveText(new RegExp(`${tagA}|${tagB}`));
    await expect(row3.locator('td').nth(3)).toHaveText(new RegExp(`${tagA}, ${tagB}|${tagB}, ${tagA}`));
    console.log('--- Tag B Added and Verified ---');


    // --- 5. SELECT EXAMPLES AND REMOVE TAG A ---
    console.log('--- Testing Remove Tag A ---');
    // Examples 1 and 3 should still be selected from the previous step
    // But let's make sure they are selected before proceeding
    await row1.locator('td').first().click();
    await row3.locator('td').first().click();

    await page.getByRole('button', { name: 'Remove Tag...' }).click();
    await expect(tagModal).toBeVisible();

    // The remove modal has a select dropdown, not a text input
    await tagModal.locator('select').selectOption(tagA);
    await tagModal.getByRole('button', { name: 'Remove Tag' }).click();
    await expect(tagModal).not.toBeVisible();

    // Verify tag A is removed, but tag B remains
    await expect(row1.locator('td').nth(3)).not.toHaveText(new RegExp(tagA));
    await expect(row1.locator('td').nth(3)).toHaveText(new RegExp(tagB));
    await expect(row2.locator('td').nth(3)).not.toHaveText(new RegExp(`${tagA}|${tagB}`));
    await expect(row3.locator('td').nth(3)).not.toHaveText(new RegExp(tagA));
    await expect(row3.locator('td').nth(3)).toHaveText(new RegExp(tagB));
    console.log('--- Tag A Removed and Verified ---');


    // --- 6. SELECT ALL AND DELETE ---
    console.log('--- Testing Bulk Deletion ---');
    page.on('dialog', dialog => dialog.accept()); // Auto-accept confirm dialog

    // Select all examples on the page
    await headerCheckbox.click();

    await page.getByRole('button', { name: 'Delete Selected' }).click();

    // Verify all rows are gone
    await expect(row1).not.toBeVisible();
    await expect(row2).not.toBeVisible();
    await expect(row3).not.toBeVisible();
    await expect(page.getByText('No examples yet.')).toBeVisible();
    console.log('--- Bulk Deletion Verified ---');


    // --- 7. CLEANUP: Delete the dataset ---
    console.log('--- Starting Cleanup ---');
    await page.getByText('Datasets & Experiments').click();
    await expect(page).toHaveURL(/datasets/);

    const datasetRow = page.locator('table tbody tr').filter({ hasText: datasetName });
    if (await datasetRow.isVisible()) {
      await datasetRow.getByRole('button', { name: 'Delete' }).click();
      await expect(datasetRow).not.toBeVisible();
      console.log(`Cleaned up dataset: ${datasetName}`);
    }
    console.log('--- Cleanup Complete ---');
  });

});
