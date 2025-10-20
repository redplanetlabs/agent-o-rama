package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.agentorama.store.DocumentStore;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;
import java.util.Map;

/**
 * Java example demonstrating document store operations for structured multi-field data.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>declareDocumentStore: Create a document store with multiple fields
 *   <li>getStore: Access document stores from agent nodes
 *   <li>Store.getDocumentField: Retrieve specific field values
 *   <li>Store.putDocumentField: Store values in specific fields
 *   <li>Store.updateDocumentField: Update specific field values
 *   <li>Store.containsDocumentField: Check if fields exist
 *   <li>Structured document storage with multiple typed fields
 * </ul>
 *
 * <p>Uses HashMap for request and response data structures with keys:
 *
 * <ul>
 *   <li>Request: "userId" (String), "profileUpdates" (Map with "name", "age", "preferences")
 *   <li>Response: "userId" (String), "name" (String), "age" (Long), "preferences" (Map)
 * </ul>
 */
public class DocumentStoreAgent {

  /** Agent Module demonstrating document store usage. */
  public static class DocumentStoreModule extends AgentModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      // Declare document store for user profiles
      // Key: String (user-id), Fields: name (String), age (Long), preferences (Object)
      topology.declareDocumentStore(
          "$$user-profiles",
          String.class,
          "name",
          String.class,
          "age",
          Long.class,
          "preferences",
          Object.class);

      topology
          .newAgent("DocumentStoreAgent")
          .node("update-profile", "read-profile", new UpdateProfileFunction())
          .node("read-profile", null, new ReadProfileFunction());
    }
  }

  /** Node function that creates or updates user profile. */
  public static class UpdateProfileFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(AgentNode agentNode, Map<String, Object> request) {
      DocumentStore<String> profilesStore = agentNode.getStore("$$user-profiles");
      String userId = (String) request.get("userId");
      Map<String, Object> profileUpdates = (Map<String, Object>) request.get("profileUpdates");

      // Update individual profile fields
      if (profileUpdates.containsKey("name")) {
        profilesStore.putDocumentField(userId, "name", profileUpdates.get("name"));
      }

      if (profileUpdates.containsKey("age")) {
        profilesStore.putDocumentField(userId, "age", profileUpdates.get("age"));
      }

      if (profileUpdates.containsKey("preferences")) {
        // Demonstrate field update with function
        profilesStore.updateDocumentField(
            userId,
            "preferences",
            existing -> {
              Map<String, Object> existingPrefs =
                  existing != null ? (Map<String, Object>) existing : new HashMap<>();
              Map<String, Object> newPrefs =
                  (Map<String, Object>) profileUpdates.get("preferences");
              Map<String, Object> merged = new HashMap<>(existingPrefs);
              merged.putAll(newPrefs);
              return merged;
            });
      }

      agentNode.emit("read-profile", userId);
    }
  }

  /** Node function that reads profile and returns result. */
  public static class ReadProfileFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(AgentNode agentNode, String userId) {
      DocumentStore<String> profilesStore = agentNode.getStore("$$user-profiles");

      // Check which fields exist
      boolean hasName = profilesStore.containsDocumentField(userId, "name");
      boolean hasAge = profilesStore.containsDocumentField(userId, "age");
      boolean hasPrefs = profilesStore.containsDocumentField(userId, "preferences");

      // Retrieve all profile fields
      String name = hasName ? (String) profilesStore.getDocumentField(userId, "name") : null;
      Long age = hasAge ? (Long) profilesStore.getDocumentField(userId, "age") : null;
      Map<String, Object> preferences =
          hasPrefs
              ? (Map<String, Object>) profilesStore.getDocumentField(userId, "preferences")
              : null;

      Map<String, Object> result = new HashMap<>();
      result.put("userId", userId);
      result.put("name", name);
      result.put("age", age);
      result.put("preferences", preferences);

      agentNode.result(result);
    }
  }

  public static void main(String[] args) throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      DocumentStoreModule module = new DocumentStoreModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("DocumentStoreAgent");

      System.out.println("Document Store Agent Example:");

      // First invocation: Create user profile
      System.out.println("\n--- Creating user profile ---");
      Map<String, Object> request1 = new HashMap<>();
      request1.put("userId", "user123");
      Map<String, Object> updates1 = new HashMap<>();
      updates1.put("name", "Alice Smith");
      updates1.put("age", 28L);
      Map<String, Object> prefs1 = new HashMap<>();
      prefs1.put("theme", "dark");
      prefs1.put("newsletter", true);
      updates1.put("preferences", prefs1);
      request1.put("profileUpdates", updates1);

      @SuppressWarnings("unchecked")
      Map<String, Object> result1 = (Map<String, Object>) agent.invoke(request1);
      System.out.println("Profile created:");
      System.out.println("  Name: " + result1.get("name"));
      System.out.println("  Age: " + result1.get("age"));
      System.out.println("  Preferences: " + result1.get("preferences"));

      // Second invocation: Update specific fields
      System.out.println("\n--- Updating age and preferences ---");
      Map<String, Object> request2 = new HashMap<>();
      request2.put("userId", "user123");
      Map<String, Object> updates2 = new HashMap<>();
      updates2.put("age", 29L);
      Map<String, Object> prefs2 = new HashMap<>();
      prefs2.put("notifications", true);
      updates2.put("preferences", prefs2);
      request2.put("profileUpdates", updates2);

      @SuppressWarnings("unchecked")
      Map<String, Object> result2 = (Map<String, Object>) agent.invoke(request2);
      System.out.println("Profile updated:");
      System.out.println("  Name: " + result2.get("name"));
      System.out.println("  Age: " + result2.get("age"));
      System.out.println("  Preferences: " + result2.get("preferences"));

      // Third invocation: Different user
      System.out.println("\n--- Creating second user ---");
      Map<String, Object> request3 = new HashMap<>();
      request3.put("userId", "user456");
      Map<String, Object> updates3 = new HashMap<>();
      updates3.put("name", "Bob Jones");
      updates3.put("age", 35L);
      Map<String, Object> prefs3 = new HashMap<>();
      prefs3.put("theme", "light");
      updates3.put("preferences", prefs3);
      request3.put("profileUpdates", updates3);

      @SuppressWarnings("unchecked")
      Map<String, Object> result3 = (Map<String, Object>) agent.invoke(request3);
      System.out.println("Second profile created:");
      System.out.println("  Name: " + result3.get("name"));
      System.out.println("  Age: " + result3.get("age"));
      System.out.println("  Preferences: " + result3.get("preferences"));

      System.out.println("\nNotice how:");
      System.out.println("- Document fields can be updated independently");
      System.out.println("- Field updates persist across invocations");
      System.out.println("- Complex field merging is supported with updateDocumentField");
      System.out.println("- containsDocumentField checks field existence before retrieval");
      System.out.println("- Multiple users are stored in the same document store");
    }
  }
}
