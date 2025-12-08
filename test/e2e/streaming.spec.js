// test/e2e/streaming.spec.js
import { test, expect } from '@playwright/test';

/**
 * Helper to get the StreamingTestAgent row.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<import('@playwright/test').Locator>}
 */
async function getStreamingTestAgentRow(page) {
  const moduleNs = 'com.rpl.agent.streaming-test-agent';
  const moduleName = 'StreamingTestAgentModule';
  const agentName = 'StreamingTestAgent';

  const agentRow = page.getByRole('row', { name: `${moduleNs}/${moduleName} ${agentName}` });
  
  // Wait up to 30 seconds for agents to appear on first load.
  await expect(agentRow).toBeVisible({ timeout: 30000 });
  console.log(`Found agent: ${moduleNs}/${moduleName}:${agentName}`);
  
  return agentRow;
}

test.describe('Streaming UI', () => {
  
  // Increase the timeout for streaming tests
  test.setTimeout(120 * 1000); // 2 minutes

  test('should display streaming output panel when chunks are received', async ({ page }) => {
    console.log('--- Starting Streaming UI Test ---');

    // --- 1. SETUP: Navigate to the agent detail page ---
    await page.goto('/');
    await expect(page).toHaveTitle(/Agent-o-rama/);

    const agentRow = await getStreamingTestAgentRow(page);
    await agentRow.click();
    
    const agentDetailUrlRegex = /agents\/.*com\.rpl\.agent\.streaming-test-agent.*StreamingTestAgentModule\/agent\/StreamingTestAgent/;
    await expect(page).toHaveURL(agentDetailUrlRegex);
    console.log('Successfully navigated to StreamingTestAgent detail page.');
    
    // --- 2. ACTION: Manually run the agent with slow streaming ---
    const testChunks = ["Hello ", "world", "! ", "This ", "is ", "a ", "streaming ", "test."];
    const input = { chunks: testChunks, "delay-ms": 200 };

    console.log('Running agent with streaming input');
    const manualRunForm = page.locator('div').filter({ hasText: /^Manually Run Agent/ });
    
    await manualRunForm.getByPlaceholder(/\[arg1, arg2, arg3, ...\]/).fill(JSON.stringify([input]));
    await manualRunForm.getByRole('button', { name: 'Submit' }).click();

    // --- 3. WAIT: Navigate to invocation page ---
    await expect(page).toHaveURL(/\/invocations\//, { timeout: 30000 });
    console.log('Navigated to invocation trace page.');

    // --- 4. CLICK: Select the stream-node to see streaming output ---
    // Wait for the node to appear in the graph
    const streamNode = page.locator('[data-id]').filter({ hasText: 'stream-node' }).first();
    await expect(streamNode).toBeVisible({ timeout: 30000 });
    console.log('Found stream-node in graph.');
    
    // Click the node to select it
    await streamNode.click();
    console.log('Clicked stream-node.');

    // --- 5. ASSERTION: Verify streaming panel appears with content ---
    // The streaming panel should appear once chunks start arriving
    const streamingPanel = page.locator('div').filter({ hasText: 'Streaming Output' }).first();
    await expect(streamingPanel).toBeVisible({ timeout: 30000 });
    console.log('Streaming Output panel is visible.');

    // Verify the "Live" indicator appears while streaming
    const liveIndicator = streamingPanel.getByText('Live');
    // Note: This may or may not be visible depending on timing - streaming might complete quickly
    
    // --- 6. ASSERTION: Verify the accumulated text contains our chunks ---
    // Wait for streaming to complete and verify final text
    const expectedText = testChunks.join('');
    // Use .first() to target the content <pre>, not the status <pre>
    const preElement = streamingPanel.locator('pre.text-gray-800').first();
    
    // Wait for the full text to appear (with some tolerance for timing)
    await expect(preElement).toContainText(expectedText, { timeout: 30000 });
    console.log('Streaming content contains expected text.');

    // --- 7. ASSERTION: Verify chunk count is displayed ---
    const chunkCountText = streamingPanel.getByText(/\d+ chunks? received/);
    await expect(chunkCountText).toBeVisible({ timeout: 10000 });
    console.log('Chunk count is displayed.');

    // --- 8. ASSERTION: Verify the final result also appears ---
    const finalResultHeader = page.getByText('Final Result', { exact: true });
    await expect(finalResultHeader).toBeVisible({ timeout: 30000 });
    console.log('Final Result panel is visible.');

    console.log('--- Streaming UI Test completed successfully ---');
  });

  test('should not show streaming panel when no chunks are received', async ({ page }) => {
    console.log('--- Starting No-Chunks Streaming Test ---');

    // Navigate to the agent
    await page.goto('/');
    const agentRow = await getStreamingTestAgentRow(page);
    await agentRow.click();
    
    await expect(page).toHaveURL(/StreamingTestAgent/);

    // Run with empty chunks array
    const input = { chunks: [], "delay-ms": 0 };
    const manualRunForm = page.locator('div').filter({ hasText: /^Manually Run Agent/ });
    await manualRunForm.getByPlaceholder(/\[arg1, arg2, arg3, ...\]/).fill(JSON.stringify([input]));
    await manualRunForm.getByRole('button', { name: 'Submit' }).click();

    // Wait for invocation page
    await expect(page).toHaveURL(/\/invocations\//, { timeout: 30000 });

    // Click the stream-node
    const streamNode = page.locator('[data-id]').filter({ hasText: 'stream-node' }).first();
    await expect(streamNode).toBeVisible({ timeout: 30000 });
    await streamNode.click();

    // Wait for the node details panel to appear (verify by checking Result section)
    const resultSection = page.getByText('Result').first();
    await expect(resultSection).toBeVisible({ timeout: 10000 });
    console.log('Node details panel is visible.');

    // Verify streaming panel does NOT appear (no chunks = no panel)
    const streamingPanel = page.getByText('Streaming Output');
    await expect(streamingPanel).not.toBeVisible({ timeout: 5000 });
    console.log('Streaming panel correctly hidden when no chunks.');

    console.log('--- No-Chunks Streaming Test completed successfully ---');
  });

  test('should auto-scroll as new chunks arrive', async ({ page }) => {
    console.log('--- Starting Auto-Scroll Streaming Test ---');

    // Navigate to the agent
    await page.goto('/');
    const agentRow = await getStreamingTestAgentRow(page);
    await agentRow.click();
    
    await expect(page).toHaveURL(/StreamingTestAgent/);

    // Generate many chunks to trigger scrolling
    const manyChunks = Array.from({ length: 50 }, (_, i) => `Line ${i + 1}\n`);
    const input = { chunks: manyChunks, "delay-ms": 50 };
    
    const manualRunForm = page.locator('div').filter({ hasText: /^Manually Run Agent/ });
    await manualRunForm.getByPlaceholder(/\[arg1, arg2, arg3, ...\]/).fill(JSON.stringify([input]));
    await manualRunForm.getByRole('button', { name: 'Submit' }).click();

    // Wait for invocation page
    await expect(page).toHaveURL(/\/invocations\//, { timeout: 30000 });

    // Click the stream-node
    const streamNode = page.locator('[data-id]').filter({ hasText: 'stream-node' }).first();
    await expect(streamNode).toBeVisible({ timeout: 30000 });
    await streamNode.click();

    // Wait for streaming panel
    const streamingPanel = page.locator('div').filter({ hasText: 'Streaming Output' }).first();
    await expect(streamingPanel).toBeVisible({ timeout: 30000 });

    // Get the scrollable container (use specific class to avoid matching sidebar/other panels)
    const scrollContainer = streamingPanel.locator('.bg-white.rounded.border-blue-100');
    
    // Wait for some content to arrive
    await page.waitForTimeout(1000);

    // Check that the container is scrolled (scrollTop > 0 when content overflows)
    // We'll verify by checking that later lines are visible
    // Use .first() to target the content <pre>, not the status <pre>
    const preElement = streamingPanel.locator('pre.text-gray-800').first();
    
    // Wait for the last line to appear
    await expect(preElement).toContainText('Line 50', { timeout: 60000 });
    console.log('All chunks received and visible.');

    // Verify auto-scroll worked by checking scroll position
    const scrollInfo = await scrollContainer.evaluate((el) => ({
      scrollTop: el.scrollTop,
      scrollHeight: el.scrollHeight,
      clientHeight: el.clientHeight
    }));
    
    console.log('Scroll info:', scrollInfo);
    
    // If content overflows and auto-scroll works, scrollTop should be near the bottom
    if (scrollInfo.scrollHeight > scrollInfo.clientHeight) {
      const scrolledToBottom = scrollInfo.scrollTop + scrollInfo.clientHeight >= scrollInfo.scrollHeight - 10;
      expect(scrolledToBottom).toBe(true);
      console.log('Auto-scroll verified: container is scrolled to bottom.');
    } else {
      console.log('Content fits without scrolling (no overflow).');
    }

    console.log('--- Auto-Scroll Streaming Test completed successfully ---');
  });
});

