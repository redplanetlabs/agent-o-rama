import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';

test.describe('Snapshot Auto-Select Feature', () => {
  test('should automatically select newly created snapshot', async ({ page }) => {
    // Navigate to the app
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    // Find and click the research agent
    const agentName = 'com.rpl.agent.research-agent/ResearchAgentModule:researcher';
    const agentLink = page.getByText(agentName);
    await expect(agentLink).toBeVisible({ timeout: 30000 });
    await agentLink.click();

    // Navigate to datasets
    await page.getByText('Datasets & Experiments').click();
    await expect(page.getByRole('heading', { name: 'Datasets' })).toBeVisible({ timeout: 30000 });

    // Check if we have existing datasets, if not create one
    const existingDataset = page.getByRole('link').first();
    const hasExistingDatasets = await existingDataset.isVisible({ timeout: 5000 });
    
    let datasetName;
    if (!hasExistingDatasets) {
      // Create a new dataset
      const createButton = page.getByRole('button', { name: 'Create Dataset' }).first();
      await createButton.click();
      
      datasetName = `Auto-Select Test ${randomUUID()}`;
      await page.getByLabel('Name').fill(datasetName);
      await page.locator('[role="dialog"]').getByRole('button', { name: 'Create Dataset' }).click();
      await expect(page.locator('[role="dialog"]')).toBeHidden({ timeout: 30000 });
      
      // Click on the created dataset
      await page.getByRole('link', { name: datasetName }).click();
    } else {
      // Use existing dataset
      await existingDataset.click();
    }

    // Verify we're on the dataset detail page
    await expect(page.getByText('Snapshot:')).toBeVisible({ timeout: 30000 });
    await expect(page.getByRole('button', { name: 'Latest (Working Copy)' })).toBeVisible({ timeout: 30000 });

    // Add an example first (needed for snapshots)
    const addExampleButton = page.getByRole('button', { name: 'Add Example' });
    await addExampleButton.click();
    
    const modal = page.locator('[role="dialog"]');
    await modal.getByLabel('Input (JSON)').fill('{"test": "input"}');
    await modal.getByRole('button', { name: 'Add Example' }).click();
    await expect(modal).toBeHidden({ timeout: 30000 });

    // Now test the auto-select feature
    const snapshotButton = page.getByRole('button', { name: 'Latest (Working Copy)' });
    await snapshotButton.click();
    await page.getByText('New snapshot').click();

    const snapshotModal = page.locator('[role="dialog"]');
    const snapshotName = `auto-select-${randomUUID()}`;
    await snapshotModal.getByLabel('New Snapshot Name').fill(snapshotName);
    await snapshotModal.getByRole('button', { name: 'Create Snapshot' }).click();

    // Wait for modal to close
    await expect(snapshotModal).toBeHidden({ timeout: 30000 });

    // VERIFY AUTO-SELECT: The new snapshot should be automatically selected
    await expect(page.getByRole('button', { name: snapshotName })).toBeVisible({ timeout: 30000 });
    
    // VERIFY READ-ONLY: Banner should appear
    await expect(page.getByText('Read-only: You are viewing an immutable snapshot. Editing is disabled.')).toBeVisible({ timeout: 10000 });
    
    // VERIFY READ-ONLY: Add Example button should be disabled
    const addExampleButtonReadOnly = page.getByRole('button', { name: 'Add Example' });
    await expect(addExampleButtonReadOnly).toBeDisabled({ timeout: 10000 });

    console.log('✅ Auto-select and read-only features verified successfully!');
  });
});
