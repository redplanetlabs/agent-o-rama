package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;


public interface EvaluatorBuilderOptions {
  interface Impl extends EvaluatorBuilderOptions {
    <% (dofor [[name ret args] EVALUATOR-BUILDER-OPTIONS-METHODS] (str %>
    <%= ret %> <%= name %>(<%= (args-declaration-str args) %>);
    <% )) %>
  }

  /**
   * Creates an empty EvaluatorBuilderOptions. {@code EvaluatorBuilderOptions.withoutInputPath()} is the
   * same as {@code EvaluatorBuilderOptions.create().withoutInputPath()}
   */
  static Impl create() {
    return (Impl) AORHelpers.MAKE_EVALUATOR_BUILDER_OPTIONS.invoke();
  }
  <% (dofor [[name ret args] EVALUATOR-BUILDER-OPTIONS-METHODS] (str %>
  static <%= ret %> <%= name %>(<%= (args-declaration-str args) %>) {
    return create().<%= name %>(<%= (args-vars-str args) %>);
  }
  <% )) %>
}
