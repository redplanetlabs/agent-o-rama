package com.rpl.agentorama.store;

import java.util.List;
import com.rpl.rama.Path;

/**
 * Direct access to Rama's built-in PState storage.
 *
 * PStates are stores defined as any combination of data structures of any size.
 * They are distributed, durable, and replicated, and read and written to with a flexible "path" API.
 *
 * @see <a href="https://redplanetlabs.com/docs/~/pstates.html">PStates Documentation</a>
 */
public interface PStateStore extends Store {
  /**
   * Selects data using a path expression.
   *
   * @param path the path expression for data selection, e.g {@code Path.key("a").mapVals()}
   * @param <V> the type of data being selected
   * @return list of selected values
   */
  <V> List<V> select(Path path);

  /**
   * Selects data using a path expression with a partitioning key.
   *
   * @param partitioningKey the partitioning key for the operation
   * @param path the path expression for data selection, e.g {@code Path.key("a").mapVals()}
   * @param <V> the type of data being selected
   * @return list of selected values
   */
  <V> List<V> select(Object partitioningKey, Path path);

  /**
   * Selects a single value using a path expression.
   *
   * @param path the path expression for data selection, e.g {@code Path.key("a", "b")}
   * @param <V> the type of data being selected
   * @return the selected value, or null if not found
   */
  <V> V selectOne(Path path);

  /**
   * Selects a single value using a path expression with a partitioning key.
   *
   * @param partitioningKey the partitioning key for the operation
   * @param path the path expression for data selection, e.g {@code Path.key("a", "b")}
   * @param <V> the type of data being selected
   * @return the selected value, or null if not found
   */
  <V> V selectOne(Object partitioningKey, Path path);

  /**
   * Transforms data using a path expression with a partitioning key.
   *
   * @param partitioningKey the partitioning key for the operation
   * @param path the path expression for data transformation, e.g {@code Path.key("a", "b").termVal(10)}
   */
  void transform(Object partitioningKey, Path path);
}
