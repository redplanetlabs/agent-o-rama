import { expect } from '@playwright/test';

/**
 * Gets the agent row for the BasicAgentModule.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @returns {Promise<import('@playwright/test').Locator>} The agent row locator.
 */
export async function getBasicAgentRow(page) {
  // Target the specific row by its exact role name
  const agentRow = page.getByRole('row', { 
    name: 'com.rpl.agent.basic.basic-agent/BasicAgentModule BasicAgent' 
  });

  // Wait up to 30 seconds for agents to appear on first load.
  await expect(agentRow).toBeVisible({ timeout: 30000 });
  console.log('Found BasicAgent row');

  return agentRow;
}

/**
 * Gets the agent row for the E2ETestAgent module.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @returns {Promise<import('@playwright/test').Locator>} The agent row locator.
 */
export async function getE2ETestAgentRow(page) {
  const moduleNs = 'com.rpl.agent.e2e-test-agent';
  const moduleName = 'E2ETestAgentModule';
  const agentName = 'E2ETestAgent';

  // Use a more specific selector to avoid ambiguity
  const agentRow = page.getByRole('row', { name: `${moduleNs}/${moduleName} ${agentName}` });

  // Wait up to 30 seconds for agents to appear on first load.
  await expect(agentRow).toBeVisible({ timeout: 30000 });
  console.log(`Found agent: ${moduleNs}/${moduleName}:${agentName}`);

  return agentRow;
}

/**
 * Creates an evaluator via the UI.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {Object} options - The evaluator creation options.
 * @param {string} options.name - The unique name for the evaluator.
 * @param {string} options.builderName - The name of the builder to select.
 * @param {string} [options.description] - The description for the evaluator.
 * @param {Object} [options.params] - Additional parameters for the evaluator.
 */
export async function createEvaluator(page, { name, builderName, description, params = {}, inputJsonPath, outputJsonPath, referenceOutputJsonPath }) {
  console.log(`Creating evaluator: ${name}`);
  await page.getByRole('button', { name: 'Create Evaluator' }).first().click();

  const modal = page.locator('[role="dialog"]');
  await expect(modal).toBeVisible();
  await modal.getByText(builderName, { exact: true }).click();
  await expect(modal.getByLabel('Name')).toBeVisible();

  await modal.getByLabel('Name').fill(name);
  const descText = description || `E2E test evaluator for ${name}`;
  await modal.getByLabel('Description').fill(descText);

  for (const [paramKey, paramValue] of Object.entries(params)) {
    await modal.getByLabel(paramKey, { exact: true }).fill(paramValue);
  }

  // Optionally set JSONPath fields
  if (inputJsonPath || outputJsonPath || referenceOutputJsonPath) {
    if (inputJsonPath) {
      await modal.getByLabel('Input JSON Path', { exact: true }).fill(inputJsonPath);
    }
    if (outputJsonPath) {
      await modal.getByLabel('Output JSON Path', { exact: true }).fill(outputJsonPath);
    }
    if (referenceOutputJsonPath) {
      await modal.getByLabel('Reference Output JSON Path', { exact: true }).fill(referenceOutputJsonPath);
    }
  }

  await modal.getByRole('button', { name: 'Submit' }).click();

  await expect(modal).not.toBeVisible({ timeout: 15000 });
  
  // Verify evaluator was created by searching for it (in case it's not on the first page)
  const searchInput = page.getByPlaceholder('Search evaluators...');
  if (await searchInput.isVisible()) {
    await searchInput.fill(name);
    await page.waitForTimeout(500); // Wait for debounced search
    await expect(page.locator('table tbody tr').filter({ hasText: name })).toBeVisible();
    await searchInput.clear();
    await page.waitForTimeout(500); // Wait for search to clear
  } else {
    // If no search box, just verify it appears somewhere (might need to load more)
    await expect(page.locator('table tbody tr').filter({ hasText: name })).toBeVisible();
  }
  
  console.log(`Successfully created evaluator: ${name}`);
}

/**
 * Adds an example to the currently viewed dataset.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {Object} example - An object with `input`, optional `output`, and optional `tags` array.
 */
export async function addExample(page, { input, output, tags }) {
  console.log('Adding example with input:', JSON.stringify(input), 'tags:', tags);
  
  // Step 1: Count existing rows before adding
  const rowsBefore = await page.locator('table tbody tr').count();
  console.log(`Rows before adding example: ${rowsBefore}`);
  
  // Step 2: Create the example
  await page.locator('button').filter({ hasText: 'Add Example' }).filter({ hasNot: page.locator('[disabled]') }).first().click();

  const createModal = page.locator('[role="dialog"]');
  await expect(createModal).toBeVisible();
  await createModal.getByLabel('Input (JSON)').fill(JSON.stringify(input, null, 2));
  
  if (output !== undefined) {
    await createModal.getByLabel('Output (JSON, Optional)').fill(JSON.stringify(output, null, 2));
  }
  
  await createModal.getByRole('button', { name: 'Add Example' }).click();
  await expect(createModal).not.toBeVisible({ timeout: 15000 });

  // Step 3: Wait for the new row to appear
  await expect(async () => {
    const rowsAfter = await page.locator('table tbody tr').count();
    expect(rowsAfter).toBe(rowsBefore + 1);
  }).toPass({ timeout: 10000 });
  
  const rowsAfter = await page.locator('table tbody tr').count();
  console.log(`Rows after adding example: ${rowsAfter}`);

  // Step 4: Target the newly added row (last row)
  const newRow = page.locator('table tbody tr').nth(rowsAfter - 1);
  await expect(newRow).toBeVisible();

  // Step 5: If tags are provided, edit the example to add them
  if (tags && tags.length > 0) {
    // Click the newly added row
    await newRow.click();

    const editModal = page.locator('[role="dialog"]');
    await expect(editModal).toBeVisible();
    
    // Add tags one by one
    for (const tag of tags) {
      await editModal.getByPlaceholder('Add a tag and press Enter...').fill(tag);
      await editModal.getByPlaceholder('Add a tag and press Enter...').press('Enter');
    }

    const noTags = editModal.getByText('No tags', { exact: true })
    await expect(noTags).not.toBeVisible();

    for (const tag of tags) {
      const tagRow = editModal.getByText(tag, { exact: true })
      await expect(tagRow).toBeVisible();
    }

    const closeButton = editModal.getByRole('button', { name: '×' })
    closeButton.click();
    await expect(editModal).not.toBeVisible({ timeout: 15000 });
    console.log('Successfully added tags to example.');
  }
  
  console.log('Successfully added example.');
}

/**
 * Creates a dataset via the UI.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {string} name - The name of the dataset to create.
 * @returns {Promise<void>}
 */
export async function createDataset(page, name) {
  console.log(`Creating dataset: ${name}`);
  await page.getByRole('button', { name: 'Create Dataset' }).first().click();
  
  const modal = page.locator('[role="dialog"]');
  await expect(modal).toBeVisible();
  await modal.getByLabel('Name').fill(name);
  await modal.getByRole('button', { name: 'Create Dataset' }).click();
  
  await expect(modal).not.toBeVisible();
  
  // Verify dataset was created by searching for it (in case it's not on the first page)
  const searchInput = page.getByPlaceholder('Search datasets...');
  if (await searchInput.isVisible()) {
    await searchInput.fill(name);
    await page.waitForTimeout(500); // Wait for debounced search
    await expect(page.getByText(name)).toBeVisible();
    await searchInput.clear();
    await page.waitForTimeout(500); // Wait for search to clear
  } else {
    // If no search box, just verify it appears somewhere
    await expect(page.getByText(name)).toBeVisible();
  }
  
  console.log(`Successfully created dataset: ${name}`);
}

/**
 * Deletes a dataset via the UI.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {string} name - The name of the dataset to delete.
 * @returns {Promise<void>}
 */
export async function deleteDataset(page, name) {
  console.log(`Deleting dataset: ${name}`);
  
  // Set up dialog handler before clicking delete (only if not already handled)
  let dialogHandled = false;
  const dialogHandler = async (dialog) => {
    if (!dialogHandled) {
      dialogHandled = true;
      console.log(`Accepting confirmation dialog: ${dialog.message()}`);
      try {
        await dialog.accept();
      } catch (e) {
        // Dialog already handled by another handler (e.g., test-level handler)
        console.log(`Dialog already handled: ${e.message}`);
      }
    }
  };
  page.once('dialog', dialogHandler);
  
  const datasetRow = page.locator('table tbody tr').filter({ hasText: name });
  await datasetRow.getByRole('button', { name: 'Delete' }).click();
  
  // Wait a bit for dialog to appear and be handled
  await page.waitForTimeout(500);
  
  // Wait for the row to disappear after deletion
  await expect(datasetRow).not.toBeVisible({ timeout: 10000 });
  console.log(`Successfully deleted dataset: ${name}`);
}

/**
 * Deletes an evaluator via the UI.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {string} name - The name of the evaluator to delete.
 * @returns {Promise<void>}
 */
export async function deleteEvaluator(page, name) {
  console.log(`Deleting evaluator: ${name}`);
  
  // Search for the evaluator first to ensure it's visible
  const searchInput = page.getByPlaceholder('Search evaluators...');
  if (await searchInput.isVisible()) {
    await searchInput.fill(name);
    await page.waitForTimeout(500); // Wait for debounced search
  }
  
  // Set up dialog handler before clicking delete (only if not already handled)
  let dialogHandled = false;
  const dialogHandler = async (dialog) => {
    if (!dialogHandled) {
      dialogHandled = true;
      console.log(`Accepting confirmation dialog: ${dialog.message()}`);
      try {
        await dialog.accept();
      } catch (e) {
        // Dialog already handled by another handler (e.g., test-level handler)
        console.log(`Dialog already handled: ${e.message}`);
      }
    }
  };
  page.once('dialog', dialogHandler);
  
  const evalRow = page.locator('table tbody tr').filter({ hasText: name });
  await evalRow.getByRole('button', { name: 'Delete' }).click();
  
  // Wait a bit for dialog to appear and be handled
  await page.waitForTimeout(500);
  
  // Wait for the row to disappear after deletion
  await expect(evalRow).not.toBeVisible({ timeout: 10000 });
  
  // Clear search if it was used
  if (await searchInput.isVisible()) {
    await searchInput.clear();
    await page.waitForTimeout(300);
  }
  
  console.log(`Successfully deleted evaluator: ${name}`);
}

/**
 * Adds an evaluator to an experiment form using the new search-based selector.
 * This function works with the searchable evaluator selector introduced in the UI refactoring.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {Object} modal - The experiment modal locator.
 * @param {string} evaluatorName - The name of the evaluator to add.
 * @returns {Promise<void>}
 */
export async function addEvaluatorToExperiment(page, modal, evaluatorName) {
  console.log(`Adding evaluator to experiment: ${evaluatorName}`);
  
  // Find the search input within the modal (has placeholder "Search evaluators by name...")
  const searchInput = modal.getByPlaceholder(/Search evaluators/);
  await expect(searchInput).toBeVisible();
  
  // Click the search input to focus it and open the dropdown
  await searchInput.click();
  
  // Type the evaluator name to search for it
  await searchInput.fill(evaluatorName);
  
  // Wait for the dropdown results to appear and click the matching evaluator
  // The dropdown appears as a div with position absolute
  const dropdown = page.locator('.absolute.z-10').filter({ hasText: evaluatorName });
  await expect(dropdown).toBeVisible({ timeout: 5000 });
  
  // Click the evaluator in the dropdown
  await dropdown.getByText(evaluatorName, { exact: true }).click();
  
  // Verify the evaluator was added by checking for its badge
  await expect(modal.getByText(evaluatorName, { exact: true })).toBeVisible();
  
  console.log(`Successfully added evaluator: ${evaluatorName}`);
}
