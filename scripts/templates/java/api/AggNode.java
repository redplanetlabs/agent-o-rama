package com.rpl.agentorama;

import com.rpl.rama.ops.*;
import com.rpl.agentorama.ops.*;
import java.util.*;

public interface AggNode {<% (dofor [i (range 1 (dec MAX-ARITY))] (str %>
  <%= (mk-full-type-decl i) %> AggNode on(String name, RamaVoidFunction<%= (inc i) %><%= (mk-full-type-decl ["AgentNode"] i []) %> impl);<% )) %>
  <T> AggNode onAny(RamaFunction3<AgentNode, T, List, T> impl);
  <T> void onComplete(RamaVoidFunction2<AgentNode, T> impl);
}
