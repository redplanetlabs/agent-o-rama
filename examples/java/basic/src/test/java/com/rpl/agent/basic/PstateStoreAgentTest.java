package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Test class for PstateStoreAgent demonstrating complex path-based data structures.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>declarePStateStore: Creating PState storage with schema
 *   <li>getStore: Accessing PState stores from agent nodes
 *   <li>Store operations: pstateSelect, pstateSelectOne, pstateTransform
 *   <li>HashMap usage for request and response data structures
 * </ul>
 */
public class PstateStoreAgentTest {

  @Test
  public void testPstateStoreAgent() throws Exception {
    // Tests PState store operations with path-based queries using HashMap
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      PstateStoreAgent.PStateStoreModule module = new PstateStoreAgent.PStateStoreModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("PStateStoreAgent");

      // Test creating company with employee
      Map<String, Object> request = new HashMap<>();
      request.put("companyId", "test-corp");
      request.put("companyName", "Test Corp");
      request.put("deptId", "eng");
      request.put("deptName", "Engineering");
      Map<String, Object> employee = new HashMap<>();
      employee.put("id", "e001");
      employee.put("name", "Test Employee");
      employee.put("salary", 80000L);
      employee.put("metadata", new HashMap<>());
      request.put("employee", employee);

      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) agent.invoke(request);

      assertNotNull("Result should not be null", result);
      assertEquals("Company ID should match", "test-corp", result.get("companyId"));
      assertEquals("Company name should match", "Test Corp", result.get("companyName"));
      assertEquals("Department name should match", "Engineering", result.get("deptName"));
      assertEquals("Employee count should be 1", 1, result.get("employeeCount"));
      assertTrue(
          "Average salary should be 80000", ((Double) result.get("averageSalary")) == 80000.0);

      @SuppressWarnings("unchecked")
      List<String> allEmployees = (List<String>) result.get("allCompanyEmployeeNames");
      assertNotNull("All employees list should not be null", allEmployees);
      assertTrue("Should contain test employee", allEmployees.contains("Test Employee"));
    }
  }
}
