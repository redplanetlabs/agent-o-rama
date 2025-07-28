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
                  (aor/result! agent-node "done"))))
  (-> topology
      (aor/new-agent "agg")
      (aor/node "start"
                "abc"
                (fn [agent-node arg]
                  (aor/emit! agent-node "abc" (str arg "!"))
                  ))
      (aor/agg-start-node "abc"
                          "agg"
                          (fn [agent-node arg]
                            (dotimes [_ 3]
                              (aor/emit! agent-node "agg" 1))
                            (str arg "?")))
      (aor/agg-node "agg"
                    nil
                    aggs/+sum
                    (fn [agent-node agg node-start-res]
                      (aor/result! agent-node [agg node-start-res])))))

(aor/defagentmodule AggModule 
 {}
 [topology]
 (-> topology
     (aor/new-agent "agg-2")
     (aor/node "start"
               "abc"
               (fn [agent-node arg]
                 (aor/emit! agent-node "abc" (str arg "!"))
                 ))
     (aor/agg-start-node "abc"
                         "agg"
                         (fn [agent-node arg]
                           (dotimes [_ 3]
                             (aor/emit! agent-node "agg" 1))
                           (str arg "?")))
     (aor/agg-node "agg"
                   nil
                   aggs/+sum
                   (fn [agent-node agg node-start-res]
                     (aor/result! agent-node [agg node-start-res]))))
  
  (-> topology
      (aor/new-agent "loop")
      (aor/node "start"
                "node1"
                (fn [agent-node arg]
                  (aor/emit! agent-node "node1" 1000)))
      (aor/node "node1"
                "node2"
                (fn [agent-node arg]
                  (if (>= 0 arg)
                    (aor/result! agent-node "done! node 1")
                    (aor/emit! agent-node "node2" (dec arg)))))
      (aor/node "node2"
                "node1"
                (fn [agent-node arg]
                  (if (>= 0 arg)
                    (aor/result! agent-node "done! node 2")
                    (aor/emit! agent-node "node1" (dec arg)))))))

