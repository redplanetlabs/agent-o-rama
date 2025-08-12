// this file is auto-generated
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
    
    ToolsAgentOptions.Impl errorHandlerDefault();
    
    ToolsAgentOptions.Impl errorHandlerStaticString(String message);
    
    ToolsAgentOptions.Impl errorHandlerRethrow();
    
    ToolsAgentOptions.Impl errorHandlerStaticStringByType(StaticStringHandler... handlers);
    
    ToolsAgentOptions.Impl errorHandlerByType(FunctionHandler... handlers);
    
  }

  /**
   * Creates an empty ToolsAgentOptions. {@code ToolsAgentOptions.errorHandlerRethrow()} is the
   * same as {@code ToolsAgentOptions.create().errorHandlerRethrow()}
   */
  static Impl create() {
    return (Impl) AORHelpers.MAKE_OPTIONS.invoke();
  }
  
  static ToolsAgentOptions.Impl errorHandlerDefault() {
    return create().errorHandlerDefault();
  }
  
  static ToolsAgentOptions.Impl errorHandlerStaticString(String message) {
    return create().errorHandlerStaticString(message);
  }
  
  static ToolsAgentOptions.Impl errorHandlerRethrow() {
    return create().errorHandlerRethrow();
  }
  
  static ToolsAgentOptions.Impl errorHandlerStaticStringByType(StaticStringHandler... handlers) {
    return create().errorHandlerStaticStringByType(handlers);
  }
  
  static ToolsAgentOptions.Impl errorHandlerByType(FunctionHandler... handlers) {
    return create().errorHandlerByType(handlers);
  }
  
}
