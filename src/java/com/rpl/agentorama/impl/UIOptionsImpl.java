// this file is auto-generated
package com.rpl.agentorama.impl;

import clojure.lang.Keyword;
import java.util.HashMap;
import java.util.Map;

import com.rpl.agentorama.UIOptions;

public class UIOptionsImpl implements UIOptions {
  private Map<Keyword, Object> options = new HashMap<>();

  public static UIOptions create() {
    return new UIOptionsImpl();
  }

  public UIOptions port(int portNumber) {
    options.put(Keyword.intern("port"), portNumber);
    return this;
  }
  public UIOptions noInputBeforeClose() {
    options.put(Keyword.intern("no-input-before-close"), true);
    return this;
  }
  

  public Map<Keyword, Object> getOptionsMap() {
    return options;
  }
}
