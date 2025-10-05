package com.rpl.agentorama.impl;

import clojure.lang.IFn;
import clojure.lang.Var;
import com.rpl.rama.impl.Util;
import java.util.*;

public class AORHelpers {
  public static final IFn CREATE_AGENTS_TOPOLOGY =
      Util.getIFn("com.rpl.agent-o-rama", "agent-topology");
  public static final IFn CREATE_MULTI_AGG =
      Util.getIFn("com.rpl.agent-o-rama.impl.multi-agg", "mk-multi-agg");
  public static final IFn CREATE_AGENT_MANAGER =
      Util.getIFn("com.rpl.agent-o-rama", "agent-manager");
  public static final IFn WRAP_AGENT_OBJECT =
      Util.getIFn("com.rpl.agent-o-rama.impl.agent-node", "wrap-agent-object");
  public static IFn FREEZE = Util.getIFn("taoensso.nippy", "freeze");
  public static IFn THAW = Util.getIFn("taoensso.nippy", "thaw");
  public static IFn MAKE_TOOLS_AGENT_OPTIONS =
      Util.getIFn("com.rpl.agent-o-rama.impl.java", "mk-tools-agent-options");
  public static IFn MAKE_EVALUATOR_BUILDER_OPTIONS =
      Util.getIFn("com.rpl.agent-o-rama.impl.java", "mk-evaluator-builder-options");
  public static IFn MAKE_ACTION_BUILDER_OPTIONS =
      Util.getIFn("com.rpl.agent-o-rama.impl.java", "mk-action-builder-options");
  public static IFn MAKE_AGENT_CONTEXT =
      Util.getIFn("com.rpl.agent-o-rama.impl.java", "mk-agent-context");
  public static IFn CREATE_TOOL_INFO =
      Util.getIFn("com.rpl.agent-o-rama.impl.java", "create-tool-info");
  public static IFn CREATE_TOOL_INFO_WITH_CONTEXT =
      Util.getIFn("com.rpl.agent-o-rama.impl.java", "create-tool-info-with-context");
  public static final IFn START_UI = Util.getIFn("com.rpl.agent-o-rama", "start-ui");
  public static final Var BUILT_IN_EVAL_BUILDERS = Util.getVar("com.rpl.agent-o-rama.impl.evaluators", "BUILT-IN");
  public static final IFn CREATE_EXAMPLE_RUN = Util.getIFn("com.rpl.agent-o-rama", "mk-example-run");

  public static byte[] freeze(Object v) {
    return (byte[]) FREEZE.invoke(v);
  }

  public static Object thaw(byte[] ser) {
    return THAW.invoke(ser);
  }
}
