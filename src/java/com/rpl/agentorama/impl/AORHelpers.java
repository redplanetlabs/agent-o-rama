package com.rpl.agentorama.impl;

import com.rpl.rama.impl.Util;

import clojure.lang.IFn;

public class AORHelpers {
  public static final IFn CREATE_AGENTS_TOPOLOGY = Util.getIFn("com.rpl.agent-o-rama", "agents-topology");
}
