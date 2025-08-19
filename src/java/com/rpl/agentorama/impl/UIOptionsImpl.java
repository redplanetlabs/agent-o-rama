package com.rpl.agentorama.impl;

import clojure.lang.Keyword;
import java.util.HashMap;
import java.util.Map;

public class UIOptionsImpl implements UIOptionsIface {
  private Map<Keyword, Object> options = new HashMap<>();

  public static UIOptionsIface create() {
    return new UIOptionsImpl();
  }

  public UIOptionsIface noInputBeforeClose() {
    options.put(Keyword.intern("no-input-before-close"), true);
    return this;
  }

  public UIOptionsIface port(int portNumber) {
    options.put(Keyword.intern("port"), Long.valueOf(portNumber));
    return this;
  }

  public Map<Keyword, Object> getOptionsMap() {
    return options;
  }
}
