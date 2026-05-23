/**
 * Records a short demo of Gantt timeline live updates + row selection
 * for GanttStressAgent (30-way fan-out / agg pattern).
 *
 * Usage (server on :1974 with GanttStressModule):
 *   node scripts/record_gantt_demo.mjs
 */
import { chromium } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const outDir = path.join('dev', 'screenshots');
fs.mkdirSync(outDir, { recursive: true });

const moduleNs = 'com.rpl.agent.gantt-stress.gantt-stress-agent';
const moduleName = 'GanttStressModule';
const agentName = 'GanttStressAgent';

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    recordVideo: { dir: outDir, size: { width: 1440, height: 900 } },
  });
  const page = await context.newPage();

  await page.goto('http://localhost:1974/');
  const agentRow = page.getByRole('row', {
    name: `${moduleNs}/${moduleName} ${agentName}`,
  });
  await agentRow.waitFor({ timeout: 120000 });
  await agentRow.click();

  const seed = Math.floor(Math.random() * 1_000_000_000);
  const manualRunForm = page.locator('div').filter({ hasText: /^Manually Run Agent/ });
  await manualRunForm.getByPlaceholder(/\[arg1, arg2, arg3, ...\]/).fill(JSON.stringify([seed]));
  await manualRunForm.getByRole('button', { name: 'Submit' }).click();

  await page.waitForURL(/\/invocations\//, { timeout: 120000 });
  await page.getByTestId('trace-view-gantt').click();
  const gantt = page.getByTestId('gantt-trace-view');
  await gantt.waitFor({ timeout: 30000 });

  await gantt.getByText('stress-fanout').first().waitFor({ timeout: 120000 });
  await page.waitForTimeout(2500);

  const workerRow = gantt.locator('[data-node-id]').filter({ hasText: 'stress-worker' }).first();
  await workerRow.click({ timeout: 60000 });
  await page.locator('[data-id="node-invoke-details-panel"]').waitFor({ timeout: 30000 });
  await page.waitForTimeout(2000);

  await gantt.getByText('agg', { exact: false }).first().scrollIntoViewIfNeeded().catch(() => {});
  await page.waitForTimeout(1500);

  await page.getByText('Final Result', { exact: true }).waitFor({ timeout: 180000 });
  await page.waitForTimeout(2000);

  const video = page.video();
  const webmPath = path.join(outDir, 'gantt-fanout-agg-demo.webm');
  await page.close();
  if (video) {
    await video.saveAs(webmPath);
    console.log('Saved demo video:', webmPath);
  }
  await context.close();
  await browser.close();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
