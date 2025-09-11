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
