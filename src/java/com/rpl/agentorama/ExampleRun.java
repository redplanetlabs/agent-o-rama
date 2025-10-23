package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;

/**
 * Represents a single example run for summary evaluators..
 */
public interface ExampleRun {
  /**
   * Creates a new example run with the specified input, reference output, and actual output.
   * 
   * @param input the input data for the example
   * @param referenceOutput the expected/reference output
   * @param output the actual output from the agent
   * @return the example run instance
   */
  static ExampleRun create(Object input, Object referenceOutput, Object output) {
    return (ExampleRun) AORHelpers.CREATE_EXAMPLE_RUN.invoke(input, referenceOutput, output);
  }

  /**
   * Gets the input data for this example run.
   * 
   * @param <T> the type of the input data
   * @return the input data
   */
  <T> T getInput();
  
  /**
   * Gets the reference output for this example run.
   * 
   * @param <T> the type of the reference output
   * @return the reference output
   */
  <T> T getReferenceOutput();
  
  /**
   * Gets the actual output for this example run.
   * 
   * @param <T> the type of the actual output
   * @return the actual output
   */
  <T> T getOutput();
}
