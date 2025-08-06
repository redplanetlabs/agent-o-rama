(ns com.rpl.agent.loop
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j]
   [org.httpkit.client :as http]))

(aor/defagentmodule LoopAgent
  [topology]
  (->
   topology
   (aor/new-agent "looper")
   (aor/node
    "loop1"
    "loop2"
    (fn [agent-node n]
      (if (<= n 0)
        (aor/result! agent-node n)
        (aor/emit! agent-node "loop2" n))))
   (aor/node
    "loop2"
    "loop1"
    (fn [agent-node n]
      (if (<= n 0)
        (aor/result! agent-node n)
        (aor/emit! agent-node "loop1" n))))))

(defn run-loop-agent
  []
  (with-open [ipc (rtest/create-ipc)
              ui (aor/start-ui ipc)]
    (rtest/launch-module! ipc LoopAgent {:tasks 4 :threads 2})
    (let [module-name   (get-module-name LoopAgent)
          agent-manager (aor/agent-manager ipc module-name)
          researcher    (aor/agent-client agent-manager "looper")
          inv (aor/agent-initiate 10)]
      (println (aor/agent-result researcher inv)))))

(comment
  (run-loop-agent))
