package com.rpl.aortest;

import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.UI;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Tests for the UI Java API. */
public class UITest {

  public static void testUIStartWithDefaultPort() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {

      // Launch a simple module to have something for the UI to monitor
      AgentsModule module = new TestModules.BasicToolsOpenAIAgent();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Start UI with default settings
      try (AutoCloseable uiCloseable = UI.start(ipc, UI.Options.noInputBeforeClose())) {
        if (uiCloseable == null) {
          throw new AssertionError("UI should return a non-null AutoCloseable");
        }

        // Wait a moment for UI to start
        Thread.sleep(2000);

        // Test HTTP connectivity to default port 1974
        if (!isUIReachable("http://localhost:1974")) {
          throw new AssertionError("UI should be reachable on default port 1974");
        }

        System.out.println("✓ UI started successfully on default port 1974");
      }
    }
  }

  public static void testUIStartWithCustomPort() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {

      // Launch a simple module to have something for the UI to monitor
      AgentsModule module = new TestModules.BasicToolsOpenAIAgent();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Start UI with custom port
      try (AutoCloseable uiCloseable = UI.start(ipc, UI.Options.noInputBeforeClose().port(9876))) {
        if (uiCloseable == null) {
          throw new AssertionError("UI should return a non-null AutoCloseable");
        }

        // Wait a moment for UI to start
        Thread.sleep(2000);

        // Test HTTP connectivity to custom port
        if (!isUIReachable("http://localhost:9876")) {
          throw new AssertionError("UI should be reachable on custom port 9876");
        }

        System.out.println("✓ UI started successfully on custom port 9876");
      }
    }
  }

  public static void testUICloseability() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {

      // Launch a simple module to have something for the UI to monitor
      AgentsModule module = new TestModules.BasicToolsOpenAIAgent();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Start UI
      try (AutoCloseable uiCloseable = UI.start(ipc, UI.Options.noInputBeforeClose())) {

        // Wait a moment for UI to start
        Thread.sleep(2000);

        if (!isUIReachable("http://localhost:1974")) {
          throw new AssertionError("UI should be reachable after start");
        }
      }
      // Verify UI is no longer reachable (may take a moment to shut down)
      Thread.sleep(1000); // Give it time to shut down
      if (isUIReachable("http://localhost:1974")) {
        throw new AssertionError("UI should not be reachable after close");
      }

      System.out.println("✓ UI closed successfully");
    }
  }

  public static boolean runAllTests() throws Exception {
    System.out.println("Running UI tests...");
    testUIStartWithDefaultPort();
    testUIStartWithCustomPort();
    testUICloseability();
    System.out.println("All UI tests passed!");
    return true;
  }

  /** Check if the UI is reachable at the given URL. */
  private static boolean isUIReachable(String url) {
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(5))
              .GET()
              .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      // UI should return a successful HTTP response (200 range)
      return response.statusCode() >= 200 && response.statusCode() < 300;

    } catch (IOException | InterruptedException | RuntimeException e) {
      // Any exception means UI is not reachable
      return false;
    }
  }
}
