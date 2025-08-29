// this file is auto-generated
package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;


public interface EvaluatorBuilderOptions {
  interface Impl extends EvaluatorBuilderOptions {

    EvaluatorBuilderOptions.Impl param(String name, String description);

    EvaluatorBuilderOptions.Impl param(String name, String description, String defaultValue);

    EvaluatorBuilderOptions.Impl withoutInputPath();

    EvaluatorBuilderOptions.Impl withoutOutputPath();

    EvaluatorBuilderOptions.Impl withoutReferenceOutputPath();

  }

  /**
   * Creates an empty EvaluatorBuilderOptions. {@code EvaluatorBuilderOptions.withoutInputPath()} is the
   * same as {@code EvaluatorBuilderOptions.create().withoutInputPath()}
   */
  static Impl create() {
    return (Impl) AORHelpers.MAKE_EVALUATOR_BUILDER_OPTIONS.invoke();
  }

  static EvaluatorBuilderOptions.Impl param(String name, String description) {
    return create().param(name, description);
  }

  static EvaluatorBuilderOptions.Impl param(String name, String description, String defaultValue) {
    return create().param(name, description, defaultValue);
  }

  static EvaluatorBuilderOptions.Impl withoutInputPath() {
    return create().withoutInputPath();
  }

  static EvaluatorBuilderOptions.Impl withoutOutputPath() {
    return create().withoutOutputPath();
  }

  static EvaluatorBuilderOptions.Impl withoutReferenceOutputPath() {
    return create().withoutReferenceOutputPath();
  }

}
