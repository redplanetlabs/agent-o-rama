package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;


public interface ActionBuilderOptions {
  interface Impl extends ActionBuilderOptions {
    <% (dofor [[name ret args] ACTION-BUILDER-OPTIONS-METHODS] (str %>
    <%= ret %> <%= name %>(<%= (args-declaration-str args) %>);
    <% )) %>
  }

  /**
   * Creates an empty ActionBuilderOptions
   */
  static Impl create() {
    return (Impl) AORHelpers.MAKE_ACTION_BUILDER_OPTIONS.invoke();
  }
  <% (dofor [[name ret args] ACTION-BUILDER-OPTIONS-METHODS] (str %>
  static <%= ret %> <%= name %>(<%= (args-declaration-str args) %>) {
    return create().<%= name %>(<%= (args-vars-str args) %>);
  }
  <% )) %>
}
