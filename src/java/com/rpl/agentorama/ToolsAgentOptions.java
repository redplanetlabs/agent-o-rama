// this file is auto-generated
package com.rpl.agentorama;

import com.rpl.rama.ops.*;
import com.rpl.agentorama.impl.AORHelpers;


public interface ToolsAgentOptions {
  public final class StaticStringHandler {
    public final Class type;
    public final String message;

    private StaticStringHandler(Class type, String message) {
        this.type = type;
        this.message = message;
    }

    public static <T extends Throwable> StaticStringHandler create(
            Class<T> type,
            String message) {
        return new StaticStringHandler(type, message);
    }
  }

  public final class FunctionHandler {
    public final Class type;
    public final RamaFunction1 function;

    private FunctionHandler(Class type, RamaFunction1 function) {
        this.type = type;
        this.function = function;
    }

    public static <T extends T2, T2 extends Throwable> FunctionHandler create(
            Class<T> type,
            RamaFunction1<T2, String> function) {
        return new FunctionHandler(type, function);
    }
  }

  interface Impl extends ToolsAgentOptions {

    ToolsAgentOptions.Impl errorHandlerDefault();

    ToolsAgentOptions.Impl errorHandlerRetry();

    ToolsAgentOptions.Impl errorHandlerStaticStringByType(StaticStringHandler... handlers);

    ToolsAgentOptions.Impl errorHandlerByType(FunctionHandler... handlers);

  }

  /**
   * Creates an empty ToolsAgentOptions. {@code ToolsAgentOptions.errorHandlerRetry()} is the
   * same as {@code ToolsAgentOptions.create().errorHandlerRetry()}
   */
  static Impl create() {
    return (Impl) AORHelpers.MAKE_OPTIONS.invoke();
  }

  static ToolsAgentOptions.Impl errorHandlerDefault() {
    return create().errorHandlerDefault();
  }

  static ToolsAgentOptions.Impl errorHandlerRetry() {
    return create().errorHandlerRetry();
  }

  static ToolsAgentOptions.Impl errorHandlerStaticStringByType(StaticStringHandler... handlers) {
    return create().errorHandlerStaticStringByType(handlers);
  }

  static ToolsAgentOptions.Impl errorHandlerByType(FunctionHandler... handlers) {
    return create().errorHandlerByType(handlers);
  }

}
