/**
 * Records demo: Gantt timeline live updates, row selection, stable total after completion.
 */
import { chromium } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

const outDir = path.join('dev', 'screenshots');
fs.mkdirSync(outDir, { recursive: true });

const moduleNs = 'com.rpl.agent.gantt-stress.gantt-stress-agent';
const moduleName = 'GanttStressModule';
const agentName = 'GanttStressAgent';

async function readGanttTotalMs(page) {
  const gantt = page.getByTestId('gantt-trace-view');
  const headerText = await gantt.locator('.border-b.border-gray-200.bg-gray-50').last().innerText();
  const m = headerText.match(/total\s+([\d.]+)(ms|s|m)/i);
  if (!m) return null;
  const n = parseFloat(m[1]);
  if (m[2] === 'ms') return n;
  if (m[2] === 's') return n * 1000;
  return n * 60000;
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    recordVideo: { dir: outDir, size: { width: 1440, height: 900 } },
  });
  const page = await context.newPage();

  await page.goto('http://localhost:1974/');
  await page.waitForFunction(() => /Agent-o-rama/i.test(document.title), null, { timeout: 120000 });

  const agentRow = page.getByRole('row', { name: `${moduleNs}/${moduleName} ${agentName}` });
  await agentRow.waitFor({ state: 'visible', timeout: 120000 });
  await agentRow.click();

  await page.waitForURL(/GanttStressAgent/, { timeout: 120000 });

  const seed = Math.floor(Math.random() * 1_000_000_000);
  console.log('Seed:', seed);
  const argsInput = page.getByPlaceholder(/\[arg1, arg2, arg3, ...\]/);
  await argsInput.waitFor({ state: 'visible', timeout: 120000 });
  await page.waitForTimeout(1000);
  await argsInput.fill(JSON.stringify([seed]), { timeout: 120000 });
  await page.getByRole('button', { name: 'Submit' }).click({ timeout: 120000 });

  await page.waitForURL(/\/invocations\//, { timeout: 120000 });
  await page.getByTestId('trace-view-gantt').click();
  const gantt = page.getByTestId('gantt-trace-view');
  await gantt.waitFor({ timeout: 30000 });

  await gantt.getByText('stress-fanout').first().waitFor({ timeout: 120000 });
  console.log('Live fan-out visible on timeline');
  await page.waitForTimeout(2000);

  await page.getByText('Final Result', { exact: true }).waitFor({ timeout: 600000 });
  console.log('Final result visible');
  await page.waitForTimeout(2000);

  const workerRow = gantt.locator('[data-node-id]').filter({ hasText: 'stress-worker' }).first();
  await workerRow.click({ timeout: 60000 });
  await page.locator('[data-id="node-invoke-details-panel"]').waitFor({ timeout: 30000 });
  console.log('Row click opened node panel');
  await page.waitForTimeout(1500);

  const t1 = await readGanttTotalMs(page);
  await page.waitForTimeout(4000);
  const t2 = await readGanttTotalMs(page);
  await page.waitForTimeout(4000);
  const t3 = await readGanttTotalMs(page);

  if (t1 != null && t2 != null && t3 != null && (t2 > t1 + 500 || t3 > t2 + 500)) {
    throw new Error(`Gantt total still growing after completion: ${t1} -> ${t2} -> ${t3} ms`);
  }
  console.log(`Gantt total stable after completion: ${t1}ms, ${t2}ms, ${t3}ms`);

  await gantt.getByText('stress-collect').first().scrollIntoViewIfNeeded().catch(() => {});
  await page.waitForTimeout(3000);

  const webmPath = path.join(outDir, 'gantt-polling-fix-proof.webm');
  const video = page.video();
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
