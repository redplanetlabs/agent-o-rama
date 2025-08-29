package com.rpl.agentorama;

import com.rpl.rama.ops.*;
import com.rpl.agentorama.impl.AORHelpers;


public interface ToolsAgentOptions {
  public final class StaticStringHandler<T extends Throwable> {
    public final Class<T> type;
    public final String message;

    private StaticStringHandler(Class<T> type, String message) {
        this.type = type;
        this.message = message;
    }

    public static <T extends Throwable> StaticStringHandler<T> create(
            Class<T> type,
            String message) {
        return new StaticStringHandler<>(type, message);
    }
  }

  public final class FunctionHandler<T extends Throwable> {
    public final Class<T> type;
    public final RamaFunction1<? super T, String> function;

    private FunctionHandler(Class<T> type, RamaFunction1<? super T, String> function) {
        this.type = type;
        this.function = function;
    }

    public static <T extends Throwable> FunctionHandler<T> create(
            Class<T> type,
            RamaFunction1<? super T, String> function) {
        return new FunctionHandler<>(type, function);
    }
  }

  interface Impl extends ToolsAgentOptions {
    <% (dofor [[name ret args] TOOLS-AGENT-OPTIONS-METHODS] (str %>
    <%= ret %> <%= name %>(<%= (args-declaration-str args) %>);
    <% )) %>
  }

  /**
   * Creates an empty ToolsAgentOptions. {@code ToolsAgentOptions.errorHandlerRethrow()} is the
   * same as {@code ToolsAgentOptions.create().errorHandlerRethrow()}
   */
  static Impl create() {
    return (Impl) AORHelpers.MAKE_TOOLS_AGENT_OPTIONS.invoke();
  }
  <% (dofor [[name ret args] TOOLS-AGENT-OPTIONS-METHODS] (str %>
  static <%= ret %> <%= name %>(<%= (args-declaration-str args) %>) {
    return create().<%= name %>(<%= (args-vars-str args) %>);
  }
  <% )) %>
}
