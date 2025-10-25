package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Test class for DocumentStoreAgent demonstrating structured multi-field data storage.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>declareDocumentStore: Creating document storage with multiple fields
 *   <li>getStore: Accessing document stores from agent nodes
 *   <li>Store operations: getDocumentField, putDocumentField, updateDocumentField,
 *       containsDocumentField
 *   <li>HashMap usage for request and response data structures
 * </ul>
 */
public class DocumentStoreAgentTest {

  @Test
  public void testDocumentStoreAgent() throws Exception {
    // Tests document store operations with structured multi-field data using HashMap
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      DocumentStoreAgent.DocumentStoreModule module = new DocumentStoreAgent.DocumentStoreModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("DocumentStoreAgent");

      // Test creating a user profile
      Map<String, Object> request = new HashMap<>();
      request.put("userId", "test-user");
      Map<String, Object> updates = new HashMap<>();
      updates.put("name", "Test User");
      updates.put("age", 30L);
      Map<String, Object> prefs = new HashMap<>();
      prefs.put("theme", "dark");
      prefs.put("newsletter", true);
      updates.put("preferences", prefs);
      request.put("profileUpdates", updates);

      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) agent.invoke(request);

      assertNotNull("Result should not be null", result);
      assertEquals("User ID should match", "test-user", result.get("userId"));
      assertEquals("Name should match", "Test User", result.get("name"));
      assertEquals("Age should match", 30L, result.get("age"));

      @SuppressWarnings("unchecked")
      Map<String, Object> resultPrefs = (Map<String, Object>) result.get("preferences");
      assertNotNull("Preferences should not be null", resultPrefs);
      assertEquals("Theme should match", "dark", resultPrefs.get("theme"));
      assertEquals("Newsletter should match", true, resultPrefs.get("newsletter"));
    }
  }
}
