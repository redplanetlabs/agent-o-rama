package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;
import com.rpl.agentorama.impl.UIOptionsImpl;
import com.rpl.rama.test.InProcessCluster;

/**
 * Java API for starting the Agent-o-rama web UI.
 *
 * <p>The UI provides real-time monitoring of agent execution, state visualization, and debugging
 * tools for agent development.
 */
public class UI {

  public interface Options {
    <%(dofor[[name ret args]UI-OPTIONS-METHODS](str%>static <%=ret%> <%=name%>(<%=(args-declaration-str args)%>) {
      return UIOptionsImpl.create().<%=name%>(<%=(args-vars-str args)%>);
    }
    <% )) %>
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
   * @param options configuration options (Ui.Options)
   * @return an AutoCloseable that can be used to stop the UI
   */
  public static AutoCloseable start(InProcessCluster ipc, UIOptions options) {
    return (AutoCloseable)
        AORHelpers.START_UI.invoke(ipc, ((UIOptionsImpl) options).getOptionsMap());
  }
}
