// test/e2e/metadata-editing.spec.js
import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getE2ETestAgentRow } from './helpers.js';

test.describe('Metadata Editing on Invocation Trace', () => {
  // Use a longer timeout as this test involves a full agent run.
  test.setTimeout(120 * 1000);

  let invokeURL;

  // This setup runs an agent with initial metadata before each test.
  test.beforeEach(async ({ page }) => {
    console.log('--- Setting up for Metadata test ---');
    await page.goto('/');
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();

    // Use the manual run form to initiate an agent with metadata.
    const manualRunForm = page.locator('div').filter({ hasText: /^Manually Run Agent/ });
    
    // Agent input
    const runId = `metadata-test-${randomUUID().substring(0, 8)}`;
    await manualRunForm.getByPlaceholder(/\[arg1, arg2, arg3, ...\]/).fill(JSON.stringify([{
      "run-id": runId,
      "output-value": "metadata-test-output"
    }]));

    // The crucial metadata payload
    const initialMetadata = {
      "user_id": "e2e-tester",
      "priority": 100,
      "is_test_run": true
    };
    await manualRunForm.getByPlaceholder('Metadata (JSON map, optional)').fill(JSON.stringify(initialMetadata));
    
    // Submit and wait for navigation to the trace page
    await manualRunForm.getByRole('button', { name: 'Submit' }).click();
    await expect(page).toHaveURL(/\/invocations\//, { timeout: 30000 });
    
    // Wait for the agent to complete
    await expect(page.getByText('Final Result').first()).toBeVisible({ timeout: 60000 });
    
    invokeURL = page.url(); // Store the URL for subsequent tests
    console.log(`Setup complete. Agent run with metadata at: ${invokeURL}`);
  });

  test('should display, edit, and delete metadata', async ({ page }) => {
    // The beforeEach already sets up the page, so we just verify and interact.
    await page.goto(invokeURL);

    // --- 1. Verify Initial Display ---
    console.log('Verifying initial metadata display...');
    const metadataPanel = page.locator('div').filter({ hasText: /^Metadata/ }).first();
    await expect(metadataPanel).toBeVisible();

    const userRow = metadataPanel.locator('div').filter({ hasText: 'user_id' });
    const priorityRow = metadataPanel.locator('div').filter({ hasText: 'priority' });
    const isTestRunRow = metadataPanel.locator('div').filter({ hasText: 'is_test_run' });

    await expect(userRow).toContainText('e2e-tester');
    await expect(priorityRow).toContainText('100');
    await expect(isTestRunRow).toContainText('true');
    console.log('Initial display verified.');

    // --- 2. Test Editing a Metadata Value ---
    console.log('Testing metadata editing...');
    // Hover over the priority row to reveal the edit button
    await priorityRow.hover();
    await priorityRow.getByTitle('Edit value').click();

    const textarea = priorityRow.locator('textarea');
    await expect(textarea).toBeVisible();
    expect(await textarea.inputValue()).toBe('100'); // Verify it's pre-filled

    // Edit the value
    await textarea.fill('250');
    await priorityRow.getByRole('button', { name: 'Save' }).click();

    // Verification: The textarea should disappear, and the new value should be displayed.
    await expect(textarea).not.toBeVisible();
    await expect(priorityRow).toContainText('250');
    console.log('Metadata editing verified.');

    // --- 3. Test Deleting a Metadata Value ---
    console.log('Testing metadata deletion...');
    // Auto-accept the confirmation dialog
    page.on('dialog', dialog => dialog.accept());
    
    // Hover to reveal the delete button
    await isTestRunRow.hover();
    await isTestRunRow.getByTitle('Remove key').click();

    // Verification: The row for 'is_test_run' should no longer exist.
    await expect(isTestRunRow).not.toBeVisible();
    // The other rows should still be there.
    await expect(userRow).toBeVisible();
    await expect(priorityRow).toBeVisible(); // With the edited value
    console.log('Metadata deletion verified.');

    // --- 4. Test Invalid JSON on Edit ---
    console.log('Testing invalid JSON handling...');
    await userRow.hover();
    await userRow.getByTitle('Edit value').click();
    const userTextarea = userRow.locator('textarea');
    await userTextarea.fill('this is not valid json');
    await userRow.getByRole('button', { name: 'Save' }).click();

    // Verification: The modal should NOT close, and an error message should appear.
    await expect(userTextarea).toBeVisible(); // Still in edit mode
    await expect(userRow.getByText(/Invalid JSON/)).toBeVisible();

    // Cancel the edit
    await userRow.getByRole('button', { name: 'Cancel' }).click();
    await expect(userTextarea).not.toBeVisible();
    console.log('Invalid JSON handling verified.');
  });

  test('should show empty state when no metadata exists', async ({ page }) => {
    console.log('Testing empty metadata state...');
    
    // Run an agent WITHOUT metadata
    await page.goto('/');
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();

    const manualRunForm = page.locator('div').filter({ hasText: /^Manually Run Agent/ });
    const runId = `no-metadata-test-${randomUUID().substring(0, 8)}`;
    await manualRunForm.getByPlaceholder(/\[arg1, arg2, arg3, ...\]/).fill(JSON.stringify([{
      "run-id": runId,
      "output-value": "no-metadata-output"
    }]));
    
    // Don't fill in metadata field
    await manualRunForm.getByRole('button', { name: 'Submit' }).click();
    await expect(page).toHaveURL(/\/invocations\//, { timeout: 30000 });
    await expect(page.getByText('Final Result').first()).toBeVisible({ timeout: 60000 });

    // Verify the metadata panel shows the empty state message
    const metadataPanel = page.locator('div').filter({ hasText: /^Metadata/ }).first();
    await expect(metadataPanel).toBeVisible();
    await expect(metadataPanel.getByText('No metadata exists')).toBeVisible();
    console.log('Empty metadata state verified.');
  });
});

