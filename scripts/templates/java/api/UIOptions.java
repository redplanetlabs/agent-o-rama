package com.rpl.agentorama;

public interface UIOptions {

  <%(dofor[[name ret args]UI-OPTIONS-METHODS](str%><%=ret%> <%=name%>(<%=(args-declaration-str args)%>);
  <% )) %>
}
