(ns examples.core
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.rama.aggs :as aggs]
            [com.rpl.rama.ops :as ops]
            [com.rpl.agent-o-rama :as aor]))

(aor/defagentmodule FlowModule [topology]
  (-> topology
      (aor/new-agent "foo")
      (aor/node "start"
                nil
                (fn [agent-node]
                  (aor/result! agent-node "done")))))

