package com.rpl.agentorama;

import com.rpl.agentorama.ops.*;

public interface AgentGraph {<% (dofor [i (range 0 (dec MAX-ARITY))] (str %>
  <%= (mk-full-type-decl i) %> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction<%= (inc i) %><%= (mk-full-type-decl ["AgentNode"] i []) %> impl);<% )) %><% (dofor [i (range 1 (dec MAX-ARITY))] (str %>
  <%= (mk-full-type-decl i) %> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaVoidFunction<%= (inc i) %><%= (mk-full-type-decl ["AgentNode"] i []) %> impl);<% )) %>
  AgentGraph aggNode(String name, Object outputNodesSpec, AggNode aggNode);
}
