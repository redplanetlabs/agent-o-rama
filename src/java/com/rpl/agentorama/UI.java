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

  public interface Options {
    static OptionsImpl create() {
      return new OptionsImpl();
    }

    static OptionsImpl port(int portNumber) {
      return create().port(portNumber);
    }

    static OptionsImpl noInputBeforeClose() {
      return create().noInputBeforeClose();
    }
  }

  public static class OptionsImpl implements Options {
    private Map<Keyword, Object> options = new HashMap<>();

    public OptionsImpl noInputBeforeClose() {
      options.put(Keyword.intern("no-input-before-close"), true);
      return this;
    }

    public OptionsImpl port(int portNumber) {
      options.put(Keyword.intern("port"), Long.valueOf(portNumber));
      return this;
    }

    Map<Keyword, Object> getOptionsMap() {
      return options;
    }
  }

  /**
   * Start the Agent-o-rama web UI with default settings.
   *
   * @param ipc the InProcessCluster to monitor
   * @return an AutoCloseable that can be used to stop the UI
   */
  public static AutoCloseable start(InProcessCluster ipc) {
    return (AutoCloseable) AORHelpers.START_UI.invoke(ipc);
  }

  /**
   * Start the Agent-o-rama web UI with custom options.
   *
   * @param ipc the InProcessCluster to monitor
   * @param options configuration options (e.g., {:port 8080})
   * @return an AutoCloseable that can be used to stop the UI
   */
  public static AutoCloseable start(InProcessCluster ipc, Options options) {
    return (AutoCloseable) AORHelpers.START_UI.invoke(ipc, ((OptionsImpl)options).getOptionsMap());
  }
}
