package com.rpl.agentorama.impl;

import com.rpl.rama.impl.Util;

import clojure.lang.IFn;

public class AORHelpers {
  public static final IFn CREATE_AGENTS_TOPOLOGY = Util.getIFn("com.rpl.agent-o-rama", "agents-topology");
  public static final IFn CREATE_MULTI_AGG = Util.getIFn("com.rpl.agent-o-rama.impl.multi-agg", "mk-multi-agg");
  public static final IFn CREATE_AGENT_MANAGER = Util.getIFn("com.rpl.agent-o-rama", "agent-manager");
  public static IFn FREEZE = Util.getIFn("taoensso.nippy", "freeze");
  public static IFn THAW = Util.getIFn("taoensso.nippy", "thaw");

  public static byte[] freeze(Object v) {
    return (byte[]) FREEZE.invoke(v);
  }

  public static Object thaw(byte[] ser) {
    return THAW.invoke(ser);
  }
}
