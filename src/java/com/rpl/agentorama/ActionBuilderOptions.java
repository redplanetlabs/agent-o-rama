// this file is auto-generated
package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;


public interface ActionBuilderOptions {
  interface Impl extends ActionBuilderOptions {
    
    ActionBuilderOptions.Impl param(String name, String description);
    
    ActionBuilderOptions.Impl param(String name, String description, String defaultValue);
    
    ActionBuilderOptions.Impl limitConcurrency();
    
  }

  /**
   * Creates an empty ActionBuilderOptions
   */
  static Impl create() {
    return (Impl) AORHelpers.MAKE_ACTION_BUILDER_OPTIONS.invoke();
  }
  
  static ActionBuilderOptions.Impl param(String name, String description) {
    return create().param(name, description);
  }
  
  static ActionBuilderOptions.Impl param(String name, String description, String defaultValue) {
    return create().param(name, description, defaultValue);
  }
  
  static ActionBuilderOptions.Impl limitConcurrency() {
    return create().limitConcurrency();
  }
  
}
