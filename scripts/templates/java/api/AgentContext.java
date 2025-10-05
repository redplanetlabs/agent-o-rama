package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;


public interface AgentContext {
  interface Impl extends AgentContext {
    <% (dofor [[name ret args] AGENT-CONTEXT-METHODS] (str %>
    <%= ret %> <%= name %>(<%= (args-declaration-str args) %>);
    <% )) %>
  }

  /**
   * Creates an empty AgentContext
   */
  static Impl create() {
    return (Impl) AORHelpers.MAKE_AGENT_CONTEXT.invoke(args);
  }
  <% (dofor [[name ret args] AGENT-CONTEXT-METHODS] (str %>
  static <%= ret %> <%= name %>(<%= (args-declaration-str args) %>) {
    return create().<%= name %>(<%= (args-vars-str args) %>);
  }
  <% )) %>
}
