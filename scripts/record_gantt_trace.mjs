/**
 * Short screen recording: invocation page → Timeline (Gantt) → Graph.
 * Output: dev/screenshots/gantt-trace-invocation.webm (Playwright video).
 */
import { chromium } from '@playwright/test';
import { randomUUID } from 'crypto';
import { mkdirSync, existsSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const outDir = process.env.GANTT_VIDEO_DIR || join(__dirname, '..', 'dev', 'screenshots');

async function main() {
  mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext({
    recordVideo: { dir: outDir, size: { width: 1400, height: 900 } },
  });
  const page = await context.newPage();
  const suffix = randomUUID().slice(0, 8);
  const uniqueInput = `gantt-video-${suffix}`;

  await page.goto('http://localhost:1974/', { waitUntil: 'domcontentloaded' });
  await page.getByRole('row', { name: /BasicAgentModule.*BasicAgent/ }).first().click();
  const form = page.locator('div').filter({ hasText: /^Manually Run Agent/ });
  await form.getByPlaceholder(/\[arg1, arg2, arg3, \.\.\.\]/).fill(JSON.stringify([uniqueInput]));
  await form.getByRole('button', { name: 'Submit' }).click();
  await page.waitForURL(/\/invocations\//, { timeout: 120000 });

  await page.locator('[data-id="agent-graph-panel"]').waitFor({ state: 'visible', timeout: 120000 });
  await page.locator('[data-id="agent-info-panel"]').waitFor({ state: 'visible', timeout: 120000 });
  await page.locator('[data-id="info-tab"]').click();
  await page.locator('[data-id="final-result-section"]').getByText('Final Result').waitFor({
    state: 'visible',
    timeout: 120000,
  });

  await page.locator('[data-id="agent-graph-panel"]').getByRole('button', { name: 'Timeline' }).click();
  await page.locator('[data-testid="gantt-trace-view"]').waitFor({ state: 'visible', timeout: 30000 });
  await page.waitForTimeout(1500);

  await page.locator('[data-id="agent-graph-panel"]').getByRole('button', { name: 'Graph' }).click();
  await page.locator('.react-flow').waitFor({ state: 'visible', timeout: 15000 });
  await page.waitForTimeout(1200);

  await context.close();
  await browser.close();
  console.log('Video saved under', outDir, '(Playwright default name .webm)');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
