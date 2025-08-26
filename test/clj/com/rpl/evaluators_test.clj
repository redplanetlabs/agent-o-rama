(ns com.rpl.evaluators-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.evaluators :as evals]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]))

(deftest evaluator-operations-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (aor/declare-evaluator-builder
         topology
         "concise-10"
         "Concise 10 limit"
         (fn [params]
           (fn [{:strs [input output referenceOutput]}]
             (let [len (+ (count input) (count output) (count referenceOutput))]
               {"concise?" (< len 10)}
             ))))
        (aor/declare-evaluator-builder
         topology
         "concise-x"
         "Concise X limit"
         (fn [params]
           (let [target (parse-long (get params "len"))]
             (fn [{:strs [input output referenceOutput]}]
               (let [len (+ (count input)
                            (count output)
                            (count referenceOutput))]
                 {"concise?"     (< len target)
                  "not-concise?" (>= len target)}
               ))))
         {:params       {"len" "the target length"}
          :input-path?  true
          :output-path? false
          :reference-output-path? false})
        (-> topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             nil
             (fn [agent-node]
               (aor/result! agent-node "done")
             )))
       ))
     (rtest/launch-module! ipc module {:tasks 2 :threads 2})
     (bind module-name (get-module-name module))
     (bind manager (aor/agent-manager ipc module-name))

     ;; TODO: <<<<>>>>>
     ;; - complete clojure API for declare and client methods for create,
     ;; delete, search
     ;;   - need method to fetch all the builders, including built-in ones
     ;; (defn create-evaluator!
     ;;   ([^AgentManager manager name builder-name params description]
     ;;    (create-evaluator! manager name builder-name params description nil))
     ;;   ([^AgentManager manager name builder-name params description options]
     ; (defn remove-evaluator [^AgentManager manager name]
     ; (defn search-evaluators [^AgentManager manager search-string]
     ;; - and java API declareEvaluatorBuilder
     ;; - need to verify building/caching one each task is working
     ;;   - does it need a lock? how is it actually accessed for experiments?

    )))
