package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;

public interface ExampleRun {
  static ExampleRun create(Object input, Object referenceOutput, Object output) {
    return (ExampleRun) AORHelpers.CREATE_EXAMPLE_RUN.invoke(input, referenceOutput, output);
  }

  <T> T getInput();
  <T> T getReferenceOutput();
  <T> T getOutput();
}
