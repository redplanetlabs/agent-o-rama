import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getE2ETestAgentRow, invokeAgentManually } from './helpers.js';

const uniqueId = randomUUID().substring(0, 8);
const runPrefix = `e2e-inv-pagination-${uniqueId}`;
const invocationCount = 32;

async function visibleRowCount(page) {
  return page.locator('table tbody tr').count();
}

async function visibleTraceHrefs(page) {
  return page.locator('table tbody tr td a[href*="/invocations/"]').evaluateAll((links) =>
    links.map((a) => a.getAttribute('href')).filter(Boolean)
  );
}

function taskIdsFromTraceHrefs(hrefs) {
  const taskIds = new Set();
  for (const href of hrefs) {
    const match = href.match(/\/invocations\/(\d+)-/);
    if (match) taskIds.add(match[1]);
  }
  return taskIds;
}

test.describe('Invocations pagination', () => {
  test.setTimeout(6 * 60 * 1000);

  test('loads all filtered invocations with no duplicates across pages', async ({ page }) => {
    await page.goto('/');
    const agentRow = await getE2ETestAgentRow(page);
    await agentRow.click();
    const agentBaseUrl = page.url();

    const runIds = [];
    for (let i = 0; i < invocationCount; i++) {
      const runId = `${runPrefix}-${String(i).padStart(3, '0')}`;
      runIds.push(runId);
      await invokeAgentManually(page, [{ 'run-id': runId, 'output-value': runId }]);
      await page.goto(agentBaseUrl);
    }

    await page.goto(`${agentBaseUrl}/invocations`);
    await page.getByTestId('invocations-filter-args-query').fill(runPrefix);
    await expect(page.locator('tbody tr').filter({ hasNotText: runPrefix })).toHaveCount(0, { timeout: 30000 });
    await expect(page.locator('table tbody')).toBeVisible({ timeout: 30000 });
    await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 30000 });

    const loadMoreRow = page.locator('tfoot tr').filter({ hasText: 'Load More' });
    let loadMoreClicks = 0;
    let noProgressClicks = 0;
    while (await loadMoreRow.isVisible()) {
      loadMoreClicks++;
      const beforeHrefs = await visibleTraceHrefs(page);
      await loadMoreRow.click();

      await expect(page.locator('tfoot').filter({ hasText: 'Loading...' })).not.toBeVisible({ timeout: 15000 });
      const afterHrefs = await visibleTraceHrefs(page);
      if (afterHrefs.length > beforeHrefs.length) {
        noProgressClicks = 0;
      } else {
        noProgressClicks++;
      }

      if (loadMoreClicks > 10) {
        throw new Error('Too many Load More clicks while paginating invocations');
      }
      if (noProgressClicks >= 2) {
        break;
      }
    }

    const allHrefs = await visibleTraceHrefs(page);
    expect(allHrefs.length).toBe(invocationCount);
    expect(new Set(allHrefs).size).toBe(allHrefs.length);

    const taskIds = taskIdsFromTraceHrefs(allHrefs);
    expect(taskIds.size).toBeGreaterThan(0);

    for (const runId of runIds) {
      await expect(page.locator('tbody tr').filter({ hasText: runId }).first()).toBeVisible();
    }
  });
});
