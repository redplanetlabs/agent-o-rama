(ns com.rpl.fork-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.partitioner :as apart]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc])
  (:import
   [com.rpl.agentorama
    AgentInvoke]
   [com.rpl.rama.helpers
    TopologyUtils]
   [java.util.concurrent
    CompletableFuture]))


(deftest forking-test
  (tc/with-auto-builder
   ;; TODO: <<<<>>>>
   ;;  - make graph similar to retries graph with pluggable node that does
   ;;  the actual result
   ;;      - graph should have a loop in it
   ;;  - get trace, make function to extract invoke IDs for specific nodes
   ;;      - how to target which invocation of a node...
   ;;        - can be based on looking at the input of the node
   ;;  - fork regular node, start agg node within another agg, start agg
   ;;  node not within another agg, agg node within another agg, agg node
   ;;  not within another agg, and combination
  ))
