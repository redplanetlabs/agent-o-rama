import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import {
  getE2ETestAgentRow,
  invokeAgentManually,
  createHumanMetric,
  deleteHumanMetric,
  createEvaluator,
  deleteEvaluator,
  createDataset,
  deleteDataset,
  addExample,
  addEvaluatorToExperiment,
  selectCommonDropdownOption,
  shouldSkipCleanup,
} from './helpers.js';

const uniqueId = randomUUID().substring(0, 8);
const metricName = `e2e-inv-filter-metric-${uniqueId}`;
const datasetName = `e2e-inv-filter-dataset-${uniqueId}`;
const evaluatorName = `e2e-inv-filter-evaluator-${uniqueId}`;

const runIds = {
  nodeLong: `e2e-node-long-${uniqueId}`,
  nodeShort: `e2e-node-short-${uniqueId}`,
  latencyFast: `e2e-lat-fast-${uniqueId}`,
  latencySlow: `e2e-lat-slow-${uniqueId}`,
  failure: `e2e-fail-${uniqueId}`,
  feedbackThree: `e2e-fb-3-${uniqueId}`,
  feedbackFive: `e2e-fb-5-${uniqueId}`,
  experiment: `e2e-exp-${uniqueId}`,
};

const longNodeName =
  'a_very_long_node_name_that_is_designed_specifically_to_test_ui_overflow_rendering_and_text_wrapping_behavior';

const comparatorExpectations = {
  '<': { include: [], exclude: [runIds.feedbackThree, runIds.feedbackFive] },
  '<=': { include: [runIds.feedbackThree], exclude: [runIds.feedbackFive] },
  '=': { include: [runIds.feedbackThree], exclude: [runIds.feedbackFive] },
  'not=': { include: [runIds.feedbackFive], exclude: [runIds.feedbackThree] },
  '>': { include: [runIds.feedbackFive], exclude: [runIds.feedbackThree] },
  '>=': { include: [runIds.feedbackThree, runIds.feedbackFive], exclude: [] },
};

async function openFilterPanel(page, label) {
  await selectCommonDropdownOption(page, page.getByTestId('add-invocations-filter'), label);
}

async function applyCurrentFilter(page) {
  await page.getByTestId('invocations-filter-apply').click();
  await page.waitForTimeout(400);
}

async function ensureFeedbackFilterOpen(page) {
  const comparatorSelect = page.getByTestId('invocations-filter-feedback-comparator');
  if (!(await comparatorSelect.isVisible().catch(() => false))) {
    await page.getByRole('button', { name: /^Feedback\b/ }).click();
    await expect(comparatorSelect).toBeVisible();
  }
  return comparatorSelect;
}

async function addNumericFeedback(page, invokeUrl, reviewerName, metric, value) {
  await page.goto(invokeUrl);
  await page.locator('[data-id="feedback-tab"]').click();

  const panel = page.locator('[data-id="agent-feedback-container"]');
  await panel.getByTestId('add-feedback-button').click();

  const modal = page.locator('[role="dialog"]');
  await expect(modal).toBeVisible();
  await modal.getByTestId('reviewer-name-input').fill(reviewerName);
  await modal.getByTestId('add-metric-button').click();

  const metricInput = modal.getByTestId('metric-selector-0-input');
  await metricInput.click();
  await metricInput.fill(metric);
  await page.locator('[role="option"]').filter({ hasText: metric }).first().click();
  await modal.getByTestId('metric-value-0').fill(String(value));
  await modal.getByRole('button', { name: /Submit|Save/i }).click();
  await expect(modal).not.toBeVisible({ timeout: 10000 });
}

test.describe('Invocations filters', () => {
  test.setTimeout(10 * 60 * 1000);

  test('covers node, latency, error, source=experiment, and feedback comparators', async ({ page }) => {
    let agentBaseUrl = '';
    let feedbackThreeUrl = '';
    let feedbackFiveUrl = '';
    let evaluatorCreated = false;

    try {
      await page.goto('/');
      const agentRow = await getE2ETestAgentRow(page);
      await agentRow.click();
      agentBaseUrl = page.url();

      // Seed invocations for path, latency, and error filters.
      await invokeAgentManually(page, [{
        'run-id': runIds.nodeLong,
        'long-node-names?': true,
        'output-value': runIds.nodeLong,
      }]);
      await page.goto(agentBaseUrl);

      await invokeAgentManually(page, [{
        'run-id': runIds.nodeShort,
        'long-node-names?': false,
        'output-value': runIds.nodeShort,
      }]);
      await page.goto(agentBaseUrl);

      await invokeAgentManually(page, [{
        'run-id': runIds.latencyFast,
        'timeout-ms': 10,
        'output-value': runIds.latencyFast,
      }]);
      await page.goto(agentBaseUrl);

      await invokeAgentManually(page, [{
        'run-id': runIds.latencySlow,
        'timeout-ms': 10000,
        'output-value': runIds.latencySlow,
      }]);
      await page.goto(agentBaseUrl);

      await invokeAgentManually(page, [{
        'run-id': runIds.failure,
        'fail-at-node': 'start',
        'retries-before-success': 999,
      }]);
      await page.goto(agentBaseUrl);

      feedbackThreeUrl = await invokeAgentManually(page, [{
        'run-id': runIds.feedbackThree,
        'output-value': runIds.feedbackThree,
      }]);
      await page.goto(agentBaseUrl);

      feedbackFiveUrl = await invokeAgentManually(page, [{
        'run-id': runIds.feedbackFive,
        'output-value': runIds.feedbackFive,
      }]);
      await page.goto(agentBaseUrl);

      // Create metric and apply feedback labels.
      await page.getByRole('link', { name: 'Human Metrics' }).click();
      await createHumanMetric(page, { name: metricName, type: 'numeric', min: 1, max: 10 });
      await addNumericFeedback(page, feedbackThreeUrl, 'Filter Reviewer 3', metricName, 3);
      await addNumericFeedback(page, feedbackFiveUrl, 'Filter Reviewer 5', metricName, 5);
      await page.getByRole('link', { name: 'Evaluators' }).click();
      await createEvaluator(page, { name: evaluatorName, builderName: 'random-float' });
      evaluatorCreated = true;

      // Create one experiment run for source=experiment filter.
      await page.goto(agentBaseUrl);
      await page.getByText('Datasets & Experiments').click();
      await createDataset(page, datasetName);
      await page.getByRole('link', { name: datasetName }).click();
      await page.getByRole('link', { name: 'Examples' }).click();
      await addExample(page, {
        input: { 'run-id': runIds.experiment, 'output-value': runIds.experiment },
        output: runIds.experiment,
        searchText: runIds.experiment,
      });

      await page.getByRole('link', { name: 'Experiments', exact: true }).click();
      await page.getByRole('button', { name: 'Run New Experiment' }).click();
      const expModal = page.locator('[role="dialog"]');
      await expModal.getByLabel('Experiment Name').fill(`e2e-inv-filter-exp-${uniqueId}`);
      await selectCommonDropdownOption(page, expModal.getByTestId('agent-name-dropdown'), 'E2ETestAgent');
      await expModal.locator('div').filter({ hasText: /^Input Arguments/ }).getByRole('textbox').fill('$');
      await addEvaluatorToExperiment(page, expModal, evaluatorName);
      await expModal.getByRole('button', { name: 'Run Experiment' }).click();
      await expect(page).toHaveURL(/experiments\//, { timeout: 30000 });
      await expect(page.getByText('Completed').first()).toBeVisible({ timeout: 120000 });

      // Start filter assertions on full invocations page.
      await page.goto(`${agentBaseUrl}/invocations`);

      // Node filter: long node.
      await openFilterPanel(page, 'Node');
      await page.getByTestId('invocations-filter-node-select').selectOption(longNodeName);
      await applyCurrentFilter(page);
      await expect(page.locator('tbody tr').filter({ hasText: runIds.nodeLong }).first()).toBeVisible();
      await expect(page.locator('tbody tr').filter({ hasText: runIds.nodeShort })).toHaveCount(0);

      // Node filter: short node.
      await page.locator('button').filter({ hasText: 'Node' }).first().click();
      await page.getByTestId('invocations-filter-node-select').selectOption('short_path_node');
      await applyCurrentFilter(page);
      await expect(page.locator('tbody tr').filter({ hasText: runIds.nodeShort }).first()).toBeVisible();
      await expect(page.locator('tbody tr').filter({ hasText: runIds.nodeLong })).toHaveCount(0);

      // Reset node filter to Any node.
      await page.locator('button').filter({ hasText: 'Node' }).first().click();
      await page.getByTestId('invocations-filter-node-select').selectOption('');
      await applyCurrentFilter(page);

      // Latency filter.
      await openFilterPanel(page, 'Latency');
      await page.getByTestId('invocations-filter-latency-min').fill('9000');
      await applyCurrentFilter(page);
      await expect(page.locator('tbody tr').filter({ hasText: runIds.latencySlow }).first()).toBeVisible();
      await expect(page.locator('tbody tr').filter({ hasText: runIds.latencyFast })).toHaveCount(0);

      // Reset latency.
      await page.locator('button').filter({ hasText: 'Latency' }).first().click();
      await page.getByTestId('invocations-filter-latency-min').fill('');
      await page.getByTestId('invocations-filter-latency-max').fill('');
      await applyCurrentFilter(page);

      // Error filter.
      await openFilterPanel(page, 'Error');
      await page.getByTestId('invocations-filter-error-select').selectOption('errors-only');
      await applyCurrentFilter(page);
      await expect(page.locator('tbody tr').filter({ hasText: runIds.failure }).first()).toBeVisible();
      await expect(page.locator('tbody tr').filter({ hasText: runIds.nodeLong })).toHaveCount(0);

      // Reset error.
      await page.locator('button').filter({ hasText: 'Error' }).first().click();
      await page.getByTestId('invocations-filter-error-select').selectOption('all');
      await applyCurrentFilter(page);

      // Source filter.
      await openFilterPanel(page, 'Source');
      await page.getByTestId('invocations-filter-source-select').selectOption('EXPERIMENT');
      await applyCurrentFilter(page);
      await expect(page.locator('tbody tr').filter({ hasText: runIds.experiment }).first()).toBeVisible();
      await expect(page.locator('tbody tr').filter({ hasText: runIds.nodeShort })).toHaveCount(0);

      // Source filter with NOT toggle.
      await page.locator('button').filter({ hasText: 'Source' }).first().click();
      await page.getByTestId('invocations-filter-source-not').check();
      await applyCurrentFilter(page);
      await expect(page.locator('tbody tr').filter({ hasText: runIds.nodeShort }).first()).toBeVisible();
      await expect(page.locator('tbody tr').filter({ hasText: runIds.experiment })).toHaveCount(0);

      // Reset source.
      await page.locator('button').filter({ hasText: 'Source' }).first().click();
      await page.getByTestId('invocations-filter-source-not').uncheck();
      await page.getByTestId('invocations-filter-source-select').selectOption('all');
      await applyCurrentFilter(page);

      // Feedback filter comparators.
      await openFilterPanel(page, 'Feedback');
      await page.getByTestId('invocations-filter-feedback-metric').selectOption(metricName);
      await page.getByTestId('invocations-filter-feedback-value').fill('3');

      for (const comparator of ['<', '<=', '=', 'not=', '>', '>=']) {
        const comparatorSelect = await ensureFeedbackFilterOpen(page);
        await comparatorSelect.selectOption(comparator);
        await applyCurrentFilter(page);

        const expected = comparatorExpectations[comparator];
        if (expected.include.length === 0) {
          await expect(page.getByText('No invocations found')).toBeVisible();
        } else {
          await expect(page.locator('tbody')).toBeVisible();
        }

        for (const runId of expected.include) {
          await expect(page.locator('tbody tr').filter({ hasText: runId }).first()).toBeVisible();
        }
        for (const runId of expected.exclude) {
          await expect(page.locator('tbody tr').filter({ hasText: runId })).toHaveCount(0);
        }
      }
    } finally {
      if (!shouldSkipCleanup()) {
        await page.goto(agentBaseUrl || '/');
        if (!agentBaseUrl) {
          const agentRow = await getE2ETestAgentRow(page);
          await agentRow.click();
          agentBaseUrl = page.url();
        }
        await page.getByText('Datasets & Experiments').click();
        await deleteDataset(page, datasetName);
        if (evaluatorCreated) {
          await page.getByRole('link', { name: 'Evaluators' }).click();
          await deleteEvaluator(page, evaluatorName);
        }
        await page.getByRole('link', { name: 'Human Metrics' }).click();
        await deleteHumanMetric(page, metricName);
      }
    }
  });
});
