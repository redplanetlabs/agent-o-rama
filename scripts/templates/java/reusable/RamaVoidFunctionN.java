package com.rpl.agentorama.ops;


/**
 * Interface for custom function implementations of <%= (javadoc/args-str *operation-index*) %>
 */
public interface RamaVoidFunction<%= *operation-index* %><%= (mk-void-function-types *operation-index*) %> extends RamaVoidFunction {
  /**
   * Computes result of function from input arguments
   */
  void invoke(<%= (mk-type-args-decl *operation-index*) %>);
}
