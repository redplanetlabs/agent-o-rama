package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;
import com.rpl.rama.test.InProcessCluster;

import clojure.lang.Keyword;

import java.util.HashMap;
import java.util.Map;

/**
 * Java API for starting the Agent-o-rama web UI.
 *
 * <p>The UI provides real-time monitoring of agent execution, state visualization, and debugging
 * tools for agent development.
 */
public class UI {

  public static class WithOptions {
    private Map<Keyword, Object> options = new HashMap<>();

    public WithOptions noInputBeforeClose() {
      options.put(Keyword.intern("no-input-before-close"), true);
      return this;
    }

    public WithOptions port(int portNumber) {
      options.put(Keyword.intern("port"), Long.valueOf(portNumber));
      return this;
    }

    public AutoCloseable start(InProcessCluster ipc) {
      return UI.start(ipc, options);
    }
  }

  public static WithOptions withOptions() {
    return new WithOptions();
  }

  /**
   * Start the Agent-o-rama web UI with default settings.
   *
   * @param ipc the InProcessCluster to monitor
   * @return a Closeable that can be used to stop the UI
   */
  public static AutoCloseable start(InProcessCluster ipc) {
    return (AutoCloseable) AORHelpers.START_UI.invoke(ipc);
  }

  /**
   * Start the Agent-o-rama web UI with custom options.
   *
   * @param ipc the InProcessCluster to monitor
   * @param options configuration options (e.g., {:port 8080})
   * @return a Closeable that can be used to stop the UI
   */
  public static AutoCloseable start(InProcessCluster ipc, Map<Keyword, Object> options) {
    return (AutoCloseable) AORHelpers.START_UI.invoke(ipc, options);
  }
}
