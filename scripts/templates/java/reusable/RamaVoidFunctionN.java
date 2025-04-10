package com.rpl.agentorama.ops;


/**
 * Interface for custom function implementations of <%= (javadoc/args-str *operation-index*) %>
 */
public interface RamaVoidFunction<%= *operation-index* %><<%= (str/join "," (conj (mk-type-strs *operation-index*))) %>> extends RamaVoidFunction {
  /**
   * Computes result of function from input arguments
   */
  void invoke(<%= (mk-type-args-decl *operation-index*) %>);
}
