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

  <%(dofor[[name ret args]UI-OPTIONS-METHODS](str%>public <%=ret%> <%=name%>(<%=(args-declaration-str args)%>) {
    options.put(Keyword.intern("<%=(camel->kebab name)%>"), <%=(args-vars-str-or-true args)%>);
    return this;
  }
  <% )) %>

  public Map<Keyword, Object> getOptionsMap() {
    return options;
  }
}
