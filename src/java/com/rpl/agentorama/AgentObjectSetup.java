package com.rpl.agentorama;

import com.rpl.rama.ops.*;

public interface AgentObjectSetup {
  <T> T getAgentObject(String name);
  <T> T getSharedScopedInstance(String key, RamaFunction0<T> builder);
  String getObjectName();
}
