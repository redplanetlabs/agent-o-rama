import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getE2ETestAgentRow, invokeAgentManually, createDataset, deleteDataset, shouldSkipCleanup } from './helpers.js';

test.describe('Dataset Trace Links', () => {
  test.setTimeout(180 * 1000); // 3 minutes — involves agent run + navigation

  test('should show a trace link in source column when example is added from a trace', async ({ page }) => {
    const uniqueId = randomUUID().substring(0, 8);
    const datasetName = `Trace Link Test ${uniqueId}`;

    // --- 1. SETUP: navigate to agent, create dataset ---
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();

    await page.getByText('Datasets & Experiments').click();
    await createDataset(page, datasetName);

    // Navigate back to agent detail so we can run it
    await agentRow.click();
    console.log('Navigated back to agent detail page.');

    // --- 2. Run the agent to produce a trace ---
    const traceUrl = await invokeAgentManually(page, [`trace-link-test-${uniqueId}`]);
    console.log('Trace URL:', traceUrl);

    // Wait for the trace to finish (result panel appears)
    await expect(page.getByText('Final Result', { exact: true })).toBeVisible({ timeout: 30000 });
    console.log('Agent trace completed.');

    // --- 3. Click "Add to Dataset" from the agent-level info panel ---
    await page.getByRole('button', { name: 'Add to Dataset' }).first().click();

    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();
    console.log('Add to Dataset modal opened.');

    // Search for and select the dataset using the searchable selector
    const datasetSearchInput = modal.getByTestId('dataset-selector-input');
    await expect(datasetSearchInput).toBeVisible({ timeout: 10000 });
    await datasetSearchInput.fill(datasetName.substring(0, 15));

    // Wait for a dropdown option matching the dataset name to appear
    const datasetOption = page.getByTestId('dataset-selector-dropdown')
      .getByText(datasetName, { exact: false });
    await expect(datasetOption).toBeVisible({ timeout: 10000 });
    await datasetOption.click();
    console.log('Dataset selected.');

    // Submit — input/output are pre-filled from the trace
    await modal.getByRole('button', { name: 'Add Example' }).click();
    await expect(modal).not.toBeVisible({ timeout: 15000 });
    console.log('Example submitted.');

    // --- 4. Navigate to the dataset examples page ---
    await page.getByText('Datasets & Experiments').click();
    await expect(page).toHaveURL(/datasets/);

    await page.getByRole('link', { name: datasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();

    // Wait for the table to load
    await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 15000 });
    console.log('Navigated to dataset examples.');

    // --- 5. Verify the Source column contains a clickable link ---
    // The source cell should contain an <a> element (not just plain text)
    // The link text should be "agent[...]" format
    const sourceCell = page.locator('table tbody tr').first().locator('td').nth(6);
    const traceLink = sourceCell.locator('a');
    await expect(traceLink).toBeVisible({ timeout: 10000 });
    await expect(traceLink).toContainText('agent[');
    console.log('Source column shows trace link:', await traceLink.textContent());

    // --- 6. Verify the link href matches the trace URL pattern ---
    const href = await traceLink.getAttribute('href');
    expect(href).toMatch(/\/invocations\//);
    console.log('Link href:', href);

    // --- 7. Click the link and verify it navigates to the trace page ---
    await traceLink.click();
    await expect(page).toHaveURL(/\/invocations\//, { timeout: 10000 });
    // The trace page should show the Final Result panel we saw before
    await expect(page.getByText('Final Result', { exact: true })).toBeVisible({ timeout: 30000 });
    console.log('Trace link navigates correctly to the invocation trace page.');

    // --- 8. CLEANUP ---
    if (!shouldSkipCleanup()) {
      await page.getByText('Datasets & Experiments').click();
      await deleteDataset(page, datasetName);
    }
  });
});
