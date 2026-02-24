import { expect } from '@playwright/test';

/**
 * CSS selector for the inline dropdown menu rendered by common/Dropdown.
 * The menu is positioned absolute within the trigger's parent container.
 */
const DROPDOWN_MENU_SELECTOR = '.origin-top-right';

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
 * Manually invokes an agent using the UI form.
 * @param {import('@playwright/test').Page} page - The Playwright page object (must be on agent detail page).
 * @param {Array} args - The arguments to pass to the agent (will be JSON.stringify'd).
 * @returns {Promise<string>} The URL of the created invocation.
 */
export async function invokeAgentManually(page, args) {
  console.log('Invoking agent with args:', args);
  
  const manualRunForm = page.locator('div').filter({ hasText: /^Manually Run Agent/ });
  
  // Fill in the args
  await manualRunForm.getByPlaceholder(/\[arg1, arg2, arg3, ...\]/).fill(JSON.stringify(args));
  
  // Submit
  await manualRunForm.getByRole('button', { name: 'Submit' }).click();
  
  // Wait for navigation to invocation page
  await expect(page).toHaveURL(/\/invocations\//, { timeout: 30000 });
  console.log('Navigated to invocation trace page.');
  
  return page.url();
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
 * Gets the currently open common/Dropdown menu.
 * The menu is rendered inline (absolute-positioned) as a sibling of the trigger button.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @returns {import('@playwright/test').Locator} The menu locator.
 */
export function getCommonDropdownMenu(page) {
  return page.locator(DROPDOWN_MENU_SELECTOR).last();
}

/**
 * Opens a common/Dropdown trigger and waits for the menu to be visible.
 * Retries the click if the menu doesn't appear (the dropdown's document click
 * handler can race with polling re-renders and close the menu immediately).
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {import('@playwright/test').Locator} trigger - The dropdown trigger button locator.
 * @returns {Promise<import('@playwright/test').Locator>} The visible menu locator.
 */
export async function openCommonDropdown(page, trigger) {
  const menu = getCommonDropdownMenu(page);
  for (let attempt = 0; attempt < 5; attempt++) {
    await trigger.click();
    if (await menu.isVisible().catch(() => false)) return menu;
    await page.waitForTimeout(200);
    if (await menu.isVisible().catch(() => false)) return menu;
  }
  await expect(menu).toBeVisible({ timeout: 5000 });
  return menu;
}

/**
 * Selects an option from a common/Dropdown by clicking the trigger then the option.
 * Retries if the dropdown doesn't stay open (see openCommonDropdown).
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {import('@playwright/test').Locator} trigger - The dropdown trigger button locator.
 * @param {string|RegExp} optionText - Option text to click.
 * @param {{ exact?: boolean }} [options] - Matching options.
 * @returns {Promise<void>}
 */
export async function selectCommonDropdownOption(page, trigger, optionText, options = {}) {
  const { exact = true } = options;
  const menu = await openCommonDropdown(page, trigger);
  const option = typeof optionText === 'string'
    ? menu.getByText(optionText, { exact }).first()
    : menu.getByText(optionText).first();
  await expect(option).toBeVisible({ timeout: 15000 });
  await option.click();
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
 * @param {Object} example - An object with `input`, optional `output`, optional `tags`, and optional `searchText`.
 * @param {*} example.input - The input value (string or object).
 * @param {*} [example.output] - The reference output value.
 * @param {string[]} [example.tags] - Tags to add to the example.
 * @param {string} [example.searchText] - Text to search for in the Input cell to find the row. 
 *                                         If not provided, uses exact match on stringified input.
 */
export async function addExample(page, { input, output, tags, searchText }) {
  const inputStr = typeof input === 'string' ? input : JSON.stringify(input);
  console.log('Adding example with input:', inputStr.substring(0, 50), 'tags:', tags);
  
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
  
  // Step 2: Wait for table to show the new example
  const table = page.locator('table tbody');
  await expect(table).toBeVisible({ timeout: 5000 });
  
  // Find the row by targeting the Input cell (2nd column)
  // Use provided searchText for substring match, or exact match on simple inputs
  const matchPattern = searchText 
    ? searchText  // Substring match
    : new RegExp(`^${inputStr}$`);  // Exact match for simple inputs
  
  const newRow = page.locator('table tbody tr').filter({ 
    has: page.locator('td').nth(1).filter({ hasText: matchPattern })
  });
  await expect(newRow.first()).toBeVisible({ timeout: 10000 });
  console.log(`Verified example appears in table`);

  // Step 3: If tags are provided, edit the example to add them
  if (tags && tags.length > 0) {
    // Click the newly added row
    await newRow.first().click();

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

    const closeButton = editModal.getByRole('button', { name: '×' });
    await closeButton.click();
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
  if (shouldSkipCleanup()) {
    console.log(`⏭️  Skipping cleanup: Keeping dataset "${name}" (set SKIP_CLEANUP=false to enable cleanup)`);
    return;
  }
  
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
  
  // Search for the dataset to ensure it's visible
  const searchInput = page.getByPlaceholder('Search datasets...');
  if (await searchInput.isVisible()) {
    await searchInput.fill(name);
    await page.waitForTimeout(500);
  }
  
  const datasetRow = page.locator('table tbody tr').filter({ hasText: name });
  await datasetRow.getByRole('button', { name: 'Delete' }).click();
  
  // Wait a bit for dialog to appear and be handled
  await page.waitForTimeout(500);
  
  // Clear search to avoid false positives from similar dataset names
  if (await searchInput.isVisible()) {
    await searchInput.clear();
    await page.waitForTimeout(300);
  }
  
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
  if (shouldSkipCleanup()) {
    console.log(`⏭️  Skipping cleanup: Keeping evaluator "${name}" (set SKIP_CLEANUP=false to enable cleanup)`);
    return;
  }
  
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
 * Checks if cleanup should be skipped based on environment variable.
 * Set SKIP_CLEANUP=true or KEEP_TEST_DATA=true to skip cleanup.
 * @returns {boolean} True if cleanup should be skipped, false otherwise.
 */
export function shouldSkipCleanup() {
  return process.env.SKIP_CLEANUP === 'true' || process.env.KEEP_TEST_DATA === 'true';
}

/**
 * Deletes a human metric via the UI.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {string} name - The name of the metric to delete.
 * @returns {Promise<void>}
 */
export async function deleteHumanMetric(page, name) {
  if (shouldSkipCleanup()) {
    console.log(`⏭️  Skipping cleanup: Keeping metric "${name}" (set SKIP_CLEANUP=false to enable cleanup)`);
    return;
  }
  
  console.log(`Deleting metric: ${name}`);
  
  // Set up dialog handler before clicking delete
  let dialogHandled = false;
  const dialogHandler = async (dialog) => {
    if (!dialogHandled) {
      dialogHandled = true;
      console.log(`Accepting confirmation dialog: ${dialog.message()}`);
      try {
        await dialog.accept();
      } catch (e) {
        console.log(`Dialog already handled: ${e.message}`);
      }
    }
  };
  page.once('dialog', dialogHandler);
  
  // Search for the metric to ensure it's visible
  await page.getByRole('textbox', { name: /Search metrics/ }).fill(name);
  await page.waitForTimeout(500);
  
  const metricRow = page.locator('table tbody tr').filter({ hasText: name });
  await metricRow.getByRole('button', { name: 'Delete' }).click();
  
  // Wait a bit for dialog to appear and be handled
  await page.waitForTimeout(500);
  
  // Clear search to avoid false positives from similar metric names
  await page.getByRole('textbox', { name: /Search metrics/ }).clear();
  await page.waitForTimeout(300);
  
  // Wait for the row to disappear after deletion
  await expect(metricRow).not.toBeVisible({ timeout: 10000 });
  console.log(`Successfully deleted metric: ${name}`);
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
  
  // Prefer stable test id, fallback to placeholder for compatibility.
  let searchInput = modal.getByTestId('evaluator-selector-input');
  if (!(await searchInput.isVisible().catch(() => false))) {
    searchInput = modal.getByPlaceholder(/Search evaluators/i);
  }
  await expect(searchInput).toBeVisible();

  const dropdown = page.getByTestId('evaluator-selector-dropdown');
  const evaluatorOptionByTestId = page.getByTestId(`evaluator-selector-option-${evaluatorName}`);
  const evaluatorOptionByRole = page.getByRole('option').filter({ hasText: evaluatorName }).first();
  const optionIsVisible = async () =>
    (await evaluatorOptionByTestId.isVisible().catch(() => false))
    || (await evaluatorOptionByRole.isVisible().catch(() => false));

  // Retry search to handle async indexing / network jitter in CI.
  // Some backends match prefixes more reliably than full exact strings.
  const searchTerms = Array.from(new Set([
    evaluatorName,
    evaluatorName.substring(0, Math.min(12, evaluatorName.length)),
    evaluatorName.substring(0, Math.min(8, evaluatorName.length)),
  ])).filter(Boolean);

  for (const term of searchTerms) {
    for (let attempt = 0; attempt < 3; attempt++) {
      await searchInput.click();
      await searchInput.fill('');
      await searchInput.fill(term);
      // Dropdown visibility can race with scroll/portal updates, so treat it as optional.
      await dropdown.isVisible().catch(() => false);
      if (await optionIsVisible()) break;
      await page.waitForTimeout(700);
    }
    if (await optionIsVisible()) break;
  }
  const foundByTestId = await evaluatorOptionByTestId.isVisible().catch(() => false);
  const evaluatorOption = foundByTestId ? evaluatorOptionByTestId : evaluatorOptionByRole;
  await expect(evaluatorOption).toBeVisible({ timeout: 15000 });
  
  // Click the evaluator in the dropdown
  await evaluatorOption.click();
  
  console.log(`Successfully added evaluator: ${evaluatorName}`);
}

/**
 * Creates a human metric via the UI.
 * @param {import('@playwright/test').Page} page - The Playwright page object.
 * @param {Object} options - The metric creation options.
 * @param {string} options.name - The unique name for the metric.
 * @param {'numeric'|'categorical'} options.type - The type of metric.
 * @param {number} [options.min] - For numeric metrics, the minimum value.
 * @param {number} [options.max] - For numeric metrics, the maximum value.
 * @param {string[]} [options.categories] - For categorical metrics, array of category names.
 * @returns {Promise<void>}
 */
export async function createHumanMetric(page, { name, type, min, max, categories }) {
  console.log(`Creating human metric: ${name} (${type})`);
  
  await page.getByRole('button', { name: '+ Create Metric' }).click();
  const modal = page.locator('[role="dialog"]');
  await expect(modal).toBeVisible();
  
  await modal.getByLabel('Metric Name').fill(name);
  
  // Select metric type
  await modal.getByRole('combobox').selectOption(type);
  
  if (type === 'numeric') {
    if (min !== undefined) {
      await modal.getByLabel('Min').fill(String(min));
    }
    if (max !== undefined) {
      await modal.getByLabel('Max').fill(String(max));
    }
  } else if (type === 'categorical') {
    if (categories && categories.length > 0) {
      await modal.getByLabel('Options (comma separated)').fill(categories.join(', '));
    }
  }
  
  const createButton = modal.getByRole('button', { name: 'Create' });
  await createButton.click();
  await expect(modal).not.toBeVisible({ timeout: 10000 });
  
  // Search for the metric to ensure it's visible
  await page.getByRole('textbox', { name: /Search metrics/ }).fill(name);
  await page.waitForTimeout(500);
  
  // Verify metric was created
  await expect(page.locator('table tbody tr').filter({ hasText: name })).toBeVisible({ timeout: 5000 });
  
  console.log(`Successfully created metric: ${name}`);
}
