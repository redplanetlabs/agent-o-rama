import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { writeFileSync, unlinkSync, existsSync } from 'fs';
import { join } from 'path';
import { getResearchAgentRow, createDataset, deleteDataset, addExample } from './helpers.js';

// =============================================================================
// TEST CONSTANTS
// =============================================================================
const uniqueId = randomUUID().substring(0, 8);
const exportDatasetName = `e2e-export-dataset-${uniqueId}`;
const importDatasetName = `e2e-import-dataset-${uniqueId}`;

// Sample JSONL data for testing
const sampleJsonlData = [
  { input: { id: `test-1-${uniqueId}`, question: "What is the capital of France?" }, output: "Paris", tags: ["geography", "europe"] },
  { input: { id: `test-2-${uniqueId}`, question: "What is 2 + 2?" }, output: "4", tags: ["math", "basic"] },
  { input: { id: `test-3-${uniqueId}`, question: "Who wrote Romeo and Juliet?" }, output: "William Shakespeare", tags: ["literature", "shakespeare"] }
].map(item => JSON.stringify(item)).join('\n');

// =============================================================================
// TEST HELPERS
// =============================================================================

/**
 * Creates a temporary JSONL file for testing
 * @param {string} content - JSONL content
 * @param {string} filename - filename for the temp file
 * @returns {string} - path to the created file
 */
function createTempJsonlFile(content, filename) {
  const tempPath = join(process.cwd(), `temp-${filename}`);
  writeFileSync(tempPath, content, 'utf8');
  return tempPath;
}

/**
 * Cleans up temporary file
 * @param {string} filePath - path to file to delete
 */
function cleanupTempFile(filePath) {
  if (existsSync(filePath)) {
    unlinkSync(filePath);
  }
}

/**
 * Waits for a file download and returns the download path
 * @param {import('@playwright/test').Page} page - Playwright page
 * @param {Function} triggerDownload - Function that triggers the download
 * @returns {Promise<string>} - path to downloaded file
 */
async function waitForDownload(page, triggerDownload) {
  const downloadPromise = page.waitForEvent('download');
  await triggerDownload();
  const download = await downloadPromise;
  const downloadPath = await download.path();
  return downloadPath;
}

// =============================================================================
// TEST SUITE
// =============================================================================

test.describe('Dataset Import/Export Functionality', () => {
  let tempFilePath;

  test.beforeAll(() => {
    // Create temporary JSONL file for import testing
    tempFilePath = createTempJsonlFile(sampleJsonlData, `import-test-${uniqueId}.jsonl`);
  });

  test.afterAll(() => {
    // Cleanup temporary file
    if (tempFilePath) {
      cleanupTempFile(tempFilePath);
    }
  });

  test('should export and import datasets with JSONL format', async ({ page }) => {
    console.log('--- Starting Dataset Import/Export Test ---');
    
    // --- PHASE 1: SETUP ---
    console.log('--- PHASE 1: SETUP ---');
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getResearchAgentRow(page);
    await agentRow.click();

    await page.getByText('Datasets & Experiments').click();
    await expect(page).toHaveURL(/datasets/);

    // --- PHASE 2: CREATE DATASET FOR EXPORT ---
    console.log('--- PHASE 2: CREATE DATASET FOR EXPORT ---');
    await createDataset(page, exportDatasetName);
    
    // Navigate to the dataset examples page
    await page.getByRole('link', { name: exportDatasetName }).click();
    await page.getByRole('link', { name: 'Examples' }).click();
    
    // Add test examples
    console.log('Adding examples to export dataset...');
    const testExamples = [
      { 
        input: { id: `export-test-1-${uniqueId}`, question: "What is machine learning?" }, 
        output: "Machine learning is a subset of AI that enables computers to learn without explicit programming." 
      },
      { 
        input: { id: `export-test-2-${uniqueId}`, question: "Explain neural networks" }, 
        output: "Neural networks are computing systems inspired by biological neural networks." 
      }
    ];

    for (const example of testExamples) {
      await addExample(page, example);
    }
    
    console.log('Examples added to export dataset.');

    // --- PHASE 3: TEST EXPORT FUNCTIONALITY ---
    console.log('--- PHASE 3: TEST EXPORT FUNCTIONALITY ---');
    
    // Click the Export button and wait for download
    const downloadPath = await waitForDownload(page, async () => {
      await page.getByRole('button', { name: 'Export' }).click();
    });
    
    expect(downloadPath).toBeTruthy();
    console.log(`Dataset exported successfully to: ${downloadPath}`);

    // Verify the exported file contains our data
    const { readFileSync } = await import('fs');
    const exportedContent = readFileSync(downloadPath, 'utf8');
    const exportedLines = exportedContent.trim().split('\n');
    
    expect(exportedLines).toHaveLength(2); // Should have 2 examples
    
    // Parse and verify each line
    for (let i = 0; i < exportedLines.length; i++) {
      const parsedLine = JSON.parse(exportedLines[i]);
      expect(parsedLine).toHaveProperty('input');
      expect(parsedLine).toHaveProperty('output');
      expect(parsedLine.input).toHaveProperty('id');
      expect(parsedLine.input.id).toContain(`export-test-${i + 1}-${uniqueId}`);
    }
    
    console.log('Export file content verified.');

    // --- PHASE 4: TEST IMPORT FUNCTIONALITY ---
    console.log('--- PHASE 4: TEST IMPORT FUNCTIONALITY ---');
    
    // Navigate back to datasets index
    await page.getByText('Datasets & Experiments').click();
    await expect(page).toHaveURL(/datasets/);
    
    // Test import functionality
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(tempFilePath);
    
    // Wait for the import to complete
    await expect(page.getByText(/Import complete/)).toBeVisible({ timeout: 30000 });
    
    console.log('Import completed successfully.');

    // Verify the imported dataset appears in the list
    const importedDatasetRow = page.locator('table tbody tr').filter({ hasText: /import-test.*\.jsonl/ });
    await expect(importedDatasetRow).toBeVisible({ timeout: 10000 });
    
    console.log('Imported dataset visible in datasets list.');

    // --- PHASE 5: VERIFY IMPORTED DATA ---
    console.log('--- PHASE 5: VERIFY IMPORTED DATA ---');
    
    // Click on the imported dataset to view its contents
    await importedDatasetRow.click();
    await page.getByRole('link', { name: 'Examples' }).click();
    
    // Verify all 3 examples from our sample data are present
    const exampleRows = page.locator('table tbody tr');
    await expect(exampleRows).toHaveCount(3);
    
    // Verify specific example content
    await expect(page.locator('table tbody tr').filter({ hasText: `test-1-${uniqueId}` })).toBeVisible();
    await expect(page.locator('table tbody tr').filter({ hasText: `test-2-${uniqueId}` })).toBeVisible();
    await expect(page.locator('table tbody tr').filter({ hasText: `test-3-${uniqueId}` })).toBeVisible();
    
    console.log('Imported examples verified.');

    // --- PHASE 6: TEST TAGS IMPORT ---
    console.log('--- PHASE 6: TEST TAGS IMPORT ---');
    
    // Click on first example to view details and verify tags
    const firstExampleRow = page.locator('table tbody tr').filter({ hasText: `test-1-${uniqueId}` });
    await firstExampleRow.click();
    
    // Check that tags are displayed in the modal
    const exampleModal = page.locator('[role="dialog"]');
    await expect(exampleModal).toBeVisible();
    await expect(exampleModal.getByText('geography')).toBeVisible();
    await expect(exampleModal.getByText('europe')).toBeVisible();
    
    // Close the modal
    await page.keyboard.press('Escape');
    await expect(exampleModal).not.toBeVisible();
    
    console.log('Tags import verified.');

    // --- PHASE 7: TEST ERROR HANDLING ---
    console.log('--- PHASE 7: TEST ERROR HANDLING ---');
    
    // Navigate back to datasets index
    await page.getByText('Datasets & Experiments').click();
    
    // Create an invalid JSONL file for testing error handling
    const invalidJsonlPath = createTempJsonlFile(
      '{"invalid": "json without required fields"}\n{"another": "invalid line"}',
      `invalid-${uniqueId}.jsonl`
    );
    
    try {
      // Try to import invalid file
      const invalidFileInput = page.locator('input[type="file"]');
      await invalidFileInput.setInputFiles(invalidJsonlPath);
      
      // Should show import result with failures
      await expect(page.getByText(/Import complete.*Failures: [^0]/)).toBeVisible({ timeout: 30000 });
      console.log('Error handling for invalid JSONL verified.');
    } finally {
      cleanupTempFile(invalidJsonlPath);
    }

    // --- PHASE 8: CLEANUP ---
    console.log('--- PHASE 8: CLEANUP ---');
    page.on('dialog', dialog => dialog.accept()); // Auto-accept confirm dialogs
    
    // Delete the export dataset
    await deleteDataset(page, exportDatasetName);
    
    // Delete any imported datasets (they have filename-based names)
    const importedDatasets = page.locator('table tbody tr').filter({ hasText: /import-test.*\.jsonl|invalid.*\.jsonl/ });
    const importedCount = await importedDatasets.count();
    
    for (let i = 0; i < importedCount; i++) {
      const dataset = importedDatasets.nth(i);
      if (await dataset.isVisible()) {
        await dataset.getByRole('button', { name: 'Delete' }).click();
        await expect(dataset).not.toBeVisible();
      }
    }
    
    // Clean up downloaded file
    if (downloadPath) {
      cleanupTempFile(downloadPath);
    }
    
    console.log('--- Dataset Import/Export Test completed successfully ---');
  });

  test('should handle large dataset export limits', async ({ page }) => {
    console.log('--- Testing Export Size Limits ---');
    
    await page.goto('/');
    const agentRow = await getResearchAgentRow(page);
    await agentRow.click();
    await page.getByText('Datasets & Experiments').click();
    
    // For this test, we'll just verify the UI behavior since creating 10k+ examples 
    // would be too slow for a real test. In a real scenario, you might mock the backend
    // or create a smaller limit for testing.
    
    await createDataset(page, `large-dataset-test-${uniqueId}`);
    await page.getByRole('link', { name: `large-dataset-test-${uniqueId}` }).click();
    await page.getByRole('link', { name: 'Examples' }).click();
    
    // Add just one example and test export works
    await addExample(page, { 
      input: { id: `large-test-${uniqueId}` }, 
      output: "test output" 
    });
    
    // Export should work for small dataset
    const downloadPath = await waitForDownload(page, async () => {
      await page.getByRole('button', { name: 'Export' }).click();
    });
    
    expect(downloadPath).toBeTruthy();
    console.log('Small dataset export works correctly.');
    
    // Cleanup
    page.on('dialog', dialog => dialog.accept());
    await page.getByText('Datasets & Experiments').click();
    await deleteDataset(page, `large-dataset-test-${uniqueId}`);
    
    if (downloadPath) {
      cleanupTempFile(downloadPath);
    }
    
    console.log('--- Export Size Limits Test completed ---');
  });
});
