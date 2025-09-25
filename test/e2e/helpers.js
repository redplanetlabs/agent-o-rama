import { expect } from '@playwright/test';

/**
 * Gets the agent row for the research agent module.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @returns {Promise<import('@playwright/test').Locator>} The agent row locator.
 */
export async function getResearchAgentRow(page) {
  const moduleNs = 'com.rpl.agent.research-agent';
  const moduleName = 'ResearchAgentModule';
  const agentName = 'researcher';

  const agentRow = page.locator('table tbody tr').filter({ hasText: moduleNs }).filter({ hasText: moduleName }).filter({ hasText: agentName });
  
  // Wait up to 30 seconds for the agent to appear. The first load can be slow.
  await expect(agentRow).toBeVisible({ timeout: 30000 });
  console.log(`Found agent: ${moduleNs}/${moduleName}:${agentName}`);
  
  return agentRow;
}

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
  await modal.getByText(builderName).click();
  await expect(modal.getByLabel('Name')).toBeVisible();

  await modal.getByLabel('Name').fill(name);
  const descText = description || `E2E test evaluator for ${name}`;
  await modal.getByLabel('Description').fill(descText);

  for (const [paramKey, paramValue] of Object.entries(params)) {
    await modal.getByLabel(paramKey, { exact: true }).fill(paramValue);
  }

  // Optionally set JSONPath fields by expanding Advanced Options
  if (inputJsonPath || outputJsonPath || referenceOutputJsonPath) {
    await modal.getByRole('button', { name: 'Advanced Options' }).click();
    if (inputJsonPath) {
      await modal.getByLabel('Input JSON Path').fill(inputJsonPath);
    }
    if (outputJsonPath) {
      await modal.getByLabel('Output JSON Path').fill(outputJsonPath);
    }
    if (referenceOutputJsonPath) {
      await modal.getByLabel('Reference Output JSON Path').fill(referenceOutputJsonPath);
    }
  }

  await modal.getByRole('button', { name: 'Submit' }).click();

  await expect(modal).not.toBeVisible({ timeout: 15000 });
  await expect(page.locator('table tbody tr').filter({ hasText: name })).toBeVisible();
  console.log(`Successfully created evaluator: ${name}`);
}

/**
 * Adds an example to the currently viewed dataset.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {Object} example - An object with `input`, optional `output`, and optional `tags` array.
 */
export async function addExample(page, { input, output, tags }) {
  console.log('Adding example with input:', JSON.stringify(input), 'tags:', tags);
  
  // Step 1: Create the example
  await page.locator('button').filter({ hasText: 'Add Example' }).filter({ hasNot: page.locator('[disabled]') }).first().click();

  const createModal = page.locator('[role="dialog"]');
  await expect(createModal).toBeVisible();
  await createModal.getByLabel('Input (JSON)').fill(JSON.stringify(input, null, 2));
  
  if (output !== undefined) {
    await createModal.getByLabel('Output (JSON, Optional)').fill(JSON.stringify(output, null, 2));
  }
  
  await createModal.getByRole('button', { name: 'Add Example' }).click();
  await expect(createModal).not.toBeVisible({ timeout: 15000 });

  // Step 2: If tags are provided, edit the example to add them
  if (tags && tags.length > 0) {
    // Find the newly created example row - target the input column (second td)
    const inputValue = typeof input === 'string' ? input : (input.id || JSON.stringify(input));
    const rowWithInput = page.locator('table tbody tr').filter({ 
      has: page.locator('td').nth(1).getByText(inputValue, { exact: true })
    });
    await expect(rowWithInput).toBeVisible({ timeout: 10000 });
    
    // Click edit button on the row
    await rowWithInput.click();
    

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
  } else {
    // Just verify the example was created - target the input column (second td)
    const inputValue = typeof input === 'string' ? input : (input.id || JSON.stringify(input));
    const rowWithInput = page.locator('table tbody tr').filter({ 
      has: page.locator('td').nth(1).getByText(inputValue, { exact: true })
    });
    await expect(rowWithInput).toBeVisible({ timeout: 10000 });
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
  await expect(page.getByText(name)).toBeVisible();
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
  const datasetRow = page.locator('table tbody tr').filter({ hasText: name });
  await datasetRow.getByRole('button', { name: 'Delete' }).click();
  await expect(datasetRow).not.toBeVisible();
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
  const evalRow = page.locator('table tbody tr').filter({ hasText: name });
  await evalRow.getByRole('button', { name: 'Delete' }).click();
  await expect(evalRow).not.toBeVisible();
  console.log(`Successfully deleted evaluator: ${name}`);
}
