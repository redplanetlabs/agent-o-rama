/**
 * One-off: open BasicAgent, run once, switch to Timeline, screenshot.
 * Requires: server on http://localhost:1974, `npx playwright install chromium`.
 */
import { chromium } from '@playwright/test';
import { randomUUID } from 'crypto';
import { mkdirSync, existsSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const outDir = process.env.GANTT_SCREENSHOT_DIR || join(__dirname, '..', 'dev', 'screenshots');
const outPath = join(outDir, 'gantt-trace-invocation.png');

async function main() {
  mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  const suffix = randomUUID().slice(0, 8);
  const uniqueInput = `gantt-demo-${suffix}`;

  await page.goto('http://localhost:1974/', { waitUntil: 'domcontentloaded' });
  const agentRow = page.getByRole('row', {
    name: /com\.rpl\.agent\.basic\.basic-agent.*BasicAgentModule.*BasicAgent/,
  });
  await agentRow.waitFor({ state: 'visible', timeout: 120000 });
  await agentRow.click();

  const manualRunForm = page.locator('div').filter({ hasText: /^Manually Run Agent/ });
  await manualRunForm.getByPlaceholder(/\[arg1, arg2, arg3, \.\.\.\]/).fill(JSON.stringify([uniqueInput]));
  await manualRunForm.getByRole('button', { name: 'Submit' }).click();

  await page.waitForURL(/\/invocations\//, { timeout: 120000 });
  await page.waitForFunction(
    () => {
      const r = document.getElementById('root');
      return r && r.children.length > 0;
    },
    null,
    { timeout: 120000 }
  );

  await page.locator('[data-id="agent-graph-panel"]').waitFor({ state: 'visible', timeout: 120000 });
  await page.getByText('Trace view', { exact: false }).waitFor({ state: 'visible', timeout: 120000 });
  await page.locator('[data-id="agent-info-panel"]').waitFor({ state: 'visible', timeout: 120000 });
  await page.locator('[data-id="info-tab"]').click();
  await page.locator('[data-id="final-result-section"]').getByText('Final Result').waitFor({
    state: 'visible',
    timeout: 120000,
  });

  await page.locator('[data-id="agent-graph-panel"]').getByRole('button', { name: 'Timeline' }).click();
  await page.locator('[data-testid="gantt-trace-view"]').waitFor({ state: 'visible', timeout: 30000 });

  await page.screenshot({ path: outPath, fullPage: true });
  console.log('Wrote', outPath);
  await browser.close();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
