// this file is auto-generated
package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;


public interface AgentContext {
  interface Impl extends AgentContext {
    
    AgentContext.Impl metadata(String name, long val);
    
    AgentContext.Impl metadata(String name, int val);
    
    AgentContext.Impl metadata(String name, String val);
    
    AgentContext.Impl metadata(String name, float val);
    
    AgentContext.Impl metadata(String name, double val);
    
    AgentContext.Impl metadata(String name, boolean val);
    
  }

  /**
   * Creates an empty AgentContext
   */
  static Impl create() {
    return (Impl) AORHelpers.MAKE_AGENT_CONTEXT.invoke();
  }
  
  static AgentContext.Impl metadata(String name, long val) {
    return create().metadata(name, val);
  }
  
  static AgentContext.Impl metadata(String name, int val) {
    return create().metadata(name, val);
  }
  
  static AgentContext.Impl metadata(String name, String val) {
    return create().metadata(name, val);
  }
  
  static AgentContext.Impl metadata(String name, float val) {
    return create().metadata(name, val);
  }
  
  static AgentContext.Impl metadata(String name, double val) {
    return create().metadata(name, val);
  }
  
  static AgentContext.Impl metadata(String name, boolean val) {
    return create().metadata(name, val);
  }
  
}
