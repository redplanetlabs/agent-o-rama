// this file is auto-generated
package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;


public interface ActionBuilderOptions {
  interface Impl extends ActionBuilderOptions {
    
    EvaluatorBuilderOptions.Impl param(String name, String description);
    
    EvaluatorBuilderOptions.Impl param(String name, String description, String defaultValue);
    
  }

  /**
   * Creates an empty ActionBuilderOptions
   */
  static Impl create() {
    return (Impl) AORHelpers.MAKE_ACTION_BUILDER_OPTIONS.invoke();
  }
  
  static EvaluatorBuilderOptions.Impl param(String name, String description) {
    return create().param(name, description);
  }
  
  static EvaluatorBuilderOptions.Impl param(String name, String description, String defaultValue) {
    return create().param(name, description, defaultValue);
  }
  
}
