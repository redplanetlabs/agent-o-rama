package com.rpl.agent.basic;

import static com.rpl.rama.helpers.TopologyUtils.*;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.agentorama.store.PStateStore;
import com.rpl.rama.Path;
import com.rpl.rama.PState;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Java example demonstrating PState store operations for complex path-based data structures.
 *
 * <p>Features demonstrated:bcommit for now
 *
 * <ul>
 *   <li>declarePStateStore: Create a PState store with schema
 *   <li>getStore: Access PState stores from agent nodes
 *   <li>PStateStore.select: Query data using path expressions
 *   <li>PStateStore.selectOne: Query single values using path expressions
 *   <li>PStateStore.transform: Update data using path-based transformations
 *   <li>Complex nested data structures and path-based operations
 *   <li>Schema-based storage with Rama's native persistent state
 * </ul>
 *
 * <p>Uses HashMap for request and response data structures with keys:
 *
 * <ul>
 *   <li>Request: "companyId", "companyName", "deptId", "deptName", "employee" (Map)
 *   <li>Response: "action", "companyId", "companyName", "deptId", "deptName", "employeeCount",
 *       "averageSalary", "departmentCount", "allCompanyEmployeeNames", "queriedEmployee"
 * </ul>
 */
public class PstateStoreAgent {

  /** Agent Module demonstrating PState store usage. */
  public static class PStateStoreModule extends AgentsModule {

    @Override
     protected void defineAgents(AgentTopology topology) {
      // Declare PState store for hierarchical organization data
      // Schema: {company-id -> {:name String, :departments {dept-id -> {:name String, :employees
      // {emp-id -> {:id String, :name String, :salary Long, :metadata Object}}}}}}
      topology.declarePStateStore(
          "$$organizations",
          PState.mapSchema(
              String.class,
              PState.fixedKeysSchema(
                  "name",
                  String.class,
                  "departments",
                  PState.mapSchema(
                      String.class,
                      PState.fixedKeysSchema(
                          "name",
                          String.class,
                          "employees",
                          PState.mapSchema(
                              String.class,
                              PState.fixedKeysSchema(
                                  "id",
                                  String.class,
                                  "name",
                                  String.class,
                                  "salary",
                                  Long.class,
                                  "metadata",
                                  Object.class)))))));

      topology
          .newAgent("PStateStoreAgent")
          .node("update-org", "query-data", new UpdateOrgFunction())
          .node("query-data", "calculate-metrics", new QueryDataFunction())
          .node("calculate-metrics", null, new CalculateMetricsFunction());
    }
  }

  /** Node function that initializes or updates organization data. */
  public static class UpdateOrgFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(AgentNode agentNode, Map<String, Object> request) {
      PStateStore orgStore = agentNode.getStore("$$organizations");
      String companyId = (String) request.get("companyId");
      String companyName = (String) request.get("companyName");
      String deptId = (String) request.get("deptId");
      String deptName = (String) request.get("deptName");
      Map<String, Object> employee = (Map<String, Object>) request.get("employee");

      // Initialize company if it doesn't exist
      if (companyName != null) {
        orgStore.transform(
          companyId,
          Path
          .key(companyId, "name")
          .term(existing -> existing != null ? existing : companyName));
      }

      // Initialize department if it doesn't exist
      if (deptName != null) {
        orgStore.transform(
          companyId,
          Path.key(companyId, "departments", deptId, "name")
          .term(existing -> existing != null ? existing : deptName));
      }

      // Add or update employee
      if (employee != null) {
        String empId = (String) employee.get("id");
        orgStore.transform(
          companyId,
          Path.key(companyId, "departments", deptId, "employees", empId)
          .termVal(employee));
      }

      Map<String, Object> emitData = new HashMap<>();
      emitData.put("companyId", companyId);
      emitData.put("deptId", deptId);
      emitData.put("employeeId", employee != null ? employee.get("id") : null);

      agentNode.emit("query-data", emitData);
    }
  }

  /** Node function that queries and analyzes data. */
  public static class QueryDataFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(AgentNode agentNode, Map<String, Object> request) {
      PStateStore orgStore = agentNode.getStore("$$organizations");
      String companyId = (String) request.get("companyId");
      String deptId = (String) request.get("deptId");
      String employeeId = (String) request.get("employeeId");

      // Query various data paths
      String companyName =
          (String) orgStore.selectOne(companyId, Path.key(companyId, "name"));

      String deptName =
          (String)
          orgStore.selectOne(
            companyId,
            Path.key(companyId, "departments", deptId, "name"));

      Map<String, Object> allEmployees =
          (Map<String, Object>) orgStore.selectOne(
            companyId,
            Path.key(companyId, "departments", deptId, "employees"));

      Map<String, Object> specificEmployee = null;
      if (employeeId != null) {
        specificEmployee =
            (Map<String, Object>)
                orgStore.selectOne(
                companyId,
                Path.key(companyId, "departments", deptId, "employees", employeeId));
      }

      Map<String, Object> allDepartments =
          (Map<String, Object>)
          orgStore.selectOne(
            companyId,
            Path.key(companyId, "departments"));

      Map<String, Object> emitData = new HashMap<>();
      emitData.put("companyId", companyId);
      emitData.put("companyName", companyName);
      emitData.put("deptId", deptId);
      emitData.put("deptName", deptName);
      emitData.put("allEmployees", allEmployees);
      emitData.put("specificEmployee", specificEmployee);
      emitData.put("allDepartments", allDepartments);

      agentNode.emit("calculate-metrics", emitData);
    }
  }

  /** Final node function that calculates metrics and returns result. */
  public static class CalculateMetricsFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(AgentNode agentNode, Map<String, Object> request) {
      PStateStore orgStore = agentNode.getStore("$$organizations");
      String companyId = (String) request.get("companyId");
      String companyName = (String) request.get("companyName");
      String deptId = (String) request.get("deptId");
      String deptName = (String) request.get("deptName");
      Map<String, Object> allEmployees = (Map<String, Object>) request.get("allEmployees");
      Map<String, Object> specificEmployee = (Map<String, Object>) request.get("specificEmployee");
      Map<String, Object> allDepartments = (Map<String, Object>) request.get("allDepartments");

      // Calculate department metrics
      List<Map<String, Object>> employeeList = new ArrayList<>();
      if (allEmployees != null) {
        for (Object emp : allEmployees.values()) {
          if (emp instanceof Map) {
            employeeList.add((Map<String, Object>) emp);
          }
        }
      }

      int totalEmployees = employeeList.size();
      double avgSalary = 0;
      if (!employeeList.isEmpty()) {
        long sum = 0;
        for (Map<String, Object> emp : employeeList) {
          Long salary = (Long) emp.get("salary");
          if (salary != null) {
            sum += salary;
          }
        }
        avgSalary = (double) sum / totalEmployees;
      }

      int deptCount = allDepartments != null ? allDepartments.size() : 0;

      // Demonstrate complex path querying - get all employee names across all departments
      List<String> allCompanyEmployeeNames =
          orgStore.select(
            companyId,
            Path.key(companyId, "departments")
            .mapVals()
            .key("employees")
            .mapVals()
            .key("name")).stream()
        .map(obj -> (String) obj)
        .collect(Collectors.toList());

      Map<String, Object> result = new HashMap<>();
      result.put("action", "pstate-query");
      result.put("companyId", companyId);
      result.put("companyName", companyName);
      result.put("deptId", deptId);
      result.put("deptName", deptName);
      result.put("employeeCount", totalEmployees);
      result.put("averageSalary", avgSalary);
      result.put("departmentCount", deptCount);
      result.put("allCompanyEmployeeNames", allCompanyEmployeeNames);
      result.put("queriedEmployee", specificEmployee);
      result.put("processedAt", System.currentTimeMillis());

      agentNode.result(result);
    }
  }

  public static void main(String[] args) throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      PStateStoreModule module = new PStateStoreModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("PStateStoreAgent");

      System.out.println("PState Store Agent Example:");

      // First invocation: Create company and first employee
      System.out.println("\n--- Creating company and first employee ---");
      Map<String, Object> request1 = new HashMap<>();
      request1.put("companyId", "tech-corp");
      request1.put("companyName", "TechCorp Inc");
      request1.put("deptId", "engineering");
      request1.put("deptName", "Engineering");
      Map<String, Object> emp1 = new HashMap<>();
      emp1.put("id", "emp001");
      emp1.put("name", "Alice Johnson");
      emp1.put("salary", 95000L);
      Map<String, Object> meta1 = new HashMap<>();
      meta1.put("level", "senior");
      meta1.put("skills", List.of("clojure", "java"));
      emp1.put("metadata", meta1);
      request1.put("employee", emp1);

      @SuppressWarnings("unchecked")
      Map<String, Object> result1 = (Map<String, Object>) agent.invoke(request1);
      System.out.println("Result 1:");
      System.out.println("  Company: " + result1.get("companyName"));
      System.out.println("  Department: " + result1.get("deptName"));
      System.out.println("  Employee count: " + result1.get("employeeCount"));
      System.out.println("  Average salary: " + result1.get("averageSalary"));

      // Second invocation: Add another employee to same department
      System.out.println("\n--- Adding second employee ---");
      Map<String, Object> request2 = new HashMap<>();
      request2.put("companyId", "tech-corp");
      request2.put("deptId", "engineering");
      Map<String, Object> emp2 = new HashMap<>();
      emp2.put("id", "emp002");
      emp2.put("name", "Bob Smith");
      emp2.put("salary", 85000L);
      Map<String, Object> meta2 = new HashMap<>();
      meta2.put("level", "mid");
      meta2.put("skills", List.of("python", "sql"));
      emp2.put("metadata", meta2);
      request2.put("employee", emp2);

      @SuppressWarnings("unchecked")
      Map<String, Object> result2 = (Map<String, Object>) agent.invoke(request2);
      System.out.println("Result 2:");
      System.out.println("  Employee count: " + result2.get("employeeCount"));
      System.out.println("  Average salary: " + result2.get("averageSalary"));
      System.out.println("  All company employees: " + result2.get("allCompanyEmployeeNames"));

      // Third invocation: Add different department
      System.out.println("\n--- Adding marketing department ---");
      Map<String, Object> request3 = new HashMap<>();
      request3.put("companyId", "tech-corp");
      request3.put("deptId", "marketing");
      request3.put("deptName", "Marketing");
      Map<String, Object> emp3 = new HashMap<>();
      emp3.put("id", "emp003");
      emp3.put("name", "Carol Davis");
      emp3.put("salary", 75000L);
      Map<String, Object> meta3 = new HashMap<>();
      meta3.put("level", "manager");
      meta3.put("skills", List.of("strategy", "analytics"));
      emp3.put("metadata", meta3);
      request3.put("employee", emp3);

      @SuppressWarnings("unchecked")
      Map<String, Object> result3 = (Map<String, Object>) agent.invoke(request3);
      System.out.println("Result 3:");
      System.out.println("  Department count: " + result3.get("departmentCount"));
      System.out.println("  Current dept employee count: " + result3.get("employeeCount"));
      System.out.println("  All company employees: " + result3.get("allCompanyEmployeeNames"));

      System.out.println("\nNotice how:");
      System.out.println("- Complex nested data structures are supported");
      System.out.println("- Path-based querying allows precise data access");
      System.out.println("- Updates can target specific nested elements");
      System.out.println("- Cross-department queries are possible with path expressions");
    }
  }
}
