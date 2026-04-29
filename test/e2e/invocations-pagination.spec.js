import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';
import { getE2ETestAgentRow, invokeAgentManually } from './helpers.js';

const uniqueId = randomUUID().substring(0, 8);
const runPrefix = `e2e-inv-pagination-${uniqueId}`;
const invocationCount = 32;

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

    // Listing can lag behind trace navigation in CI; give the store a moment to index.
    await page.waitForTimeout(2000);

    await page.goto(`${agentBaseUrl}/invocations`);
    await page.getByTestId('invocations-filter-args-query').fill(runPrefix);

    // Args filter is debounced (~300ms). Do not assert `hasNotText` while tbody is empty — that
    // passed during loading and let the test run pagination against an unfiltered / empty table.
    await expect(page.locator('table tbody')).toBeVisible({ timeout: 60000 });
    await expect(page.locator('tbody tr').filter({ hasText: runPrefix })).not.toHaveCount(0, {
      timeout: 120000,
    });

    const loadMore = page.getByTestId('invocations-load-more');

    // Click "Load More" while it exists and we still need rows. Do not require the button up front:
    // if the backend only indexed 31 rows, hasMore is false and we should time out here, not
    // fail a spurious "Load More visible" assertion.
    await expect
      .poll(
        async () => {
          const n = (await visibleTraceHrefs(page)).length;
          if (n >= invocationCount) return n;
          if (await loadMore.isVisible()) {
            await loadMore.click();
            await expect(page.locator('tfoot').filter({ hasText: 'Loading...' })).not.toBeVisible({
              timeout: 45000,
            });
          }
          return (await visibleTraceHrefs(page)).length;
        },
        { timeout: 180000 }
      )
      .toBe(invocationCount);

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
