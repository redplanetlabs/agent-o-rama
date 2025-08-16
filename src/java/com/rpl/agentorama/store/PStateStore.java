package com.rpl.agentorama.store;

import com.rpl.rama.Path;
import java.util.List;

public interface PStateStore extends Store {
  <V> List<V> select(Path path);

  <V> List<V> select(Object partitioningKey, Path path);

  <V> V selectOne(Path path);

  <V> V selectOne(Object partitioningKey, Path path);

  void transform(Object partitioningKey, Path path);
}
