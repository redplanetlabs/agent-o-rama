(ns com.rpl.rama-objects-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [meander.epsilon :as m]))


(deftest rama-objects-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module1
       (module [setup topologies]
         (declare-depot setup *depot (hash-by identity))
         (let [s (stream-topology topologies "s")]
           (declare-pstate s $$p {clojure.lang.Keyword Long})
           (<<sources s
            (source> *depot :> *k)
             (+compound $$p {*k (aggs/+count)})))
         (<<query-topology topologies
           "q"
           [*v :> *res]
           (|origin)
           (str *v "!" :> *res)
         )))
     (bind module1-name (get-module-name module1))
     (bind module2
       (module [setup topologies]
         (declare-depot setup *depot (hash-by identity))
         (let [s (stream-topology topologies "s")]
           (declare-pstate s $$p {clojure.lang.Keyword Object})
           (<<sources s
            (source> *depot :> *k)
             (+compound $$p {*k (aggs/+last :abc)})))
         (<<query-topology topologies
           "q"
           [*v :> *res]
           (|origin)
           (str *v "!!" :> *res))
         (let [topology (aor/agent-topology setup topologies)]
           (aor/declare-key-value-store
            topology
            "$$kv"
            clojure.lang.Keyword
            Object)
           (aor/declare-document-store
            topology
            "$$doc"
            clojure.lang.Keyword
            :a Long)
           (aor/declare-pstate-store
            topology
            "$$pstore"
            {clojure.lang.Keyword (map-schema Long Long {:subindex? true})})
           (->
             topology
             (aor/new-agent "foo")
             (aor/node
              "kv"
              "doc"
              (fn [agent-node k]
                (let [kv (aor/get-store agent-node "$$kv")]
                  (store/update! kv k #(inc (or % 0)))
                  (aor/emit! agent-node "doc" k)
                )))
             (aor/node
              "doc"
              "pstate"
              (fn [agent-node k]
                (let [doc (aor/get-store agent-node "$$doc")]
                  (store/update-document-field! doc k :a #(inc (or % 0)))
                  (aor/emit! agent-node "pstate" k)
                )))
             (aor/node
              "pstate"
              nil
              (fn [agent-node k]
                (let [p (aor/get-store agent-node "$$pstore")]
                  (store/pstate-transform! [(keypath k 0) (nil->val 0) (term inc)]
                                           p
                                           :a)
                  (aor/result! agent-node :done)
                )))
           )
           (aor/define-agents! topology)
         )))
     (bind module2-name (get-module-name module2))

     (bind module3
       (module [setup topologies]
         (declare-depot setup *depot (hash-by identity))
         (let [s (stream-topology topologies "s")]
           (declare-pstate s $$p {clojure.lang.Keyword Object})
           (<<sources s
            (source> *depot :> *k)
             (+compound $$p {*k (aggs/+last :def)})))
         (<<query-topology topologies
           "q"
           [*v :> *res]
           (|origin)
           (str *v "!!!" :> *res))
         (let [topology (aor/agent-topology setup topologies)]
           (->
             topology
             (aor/new-agent "foo")
             (aor/node
              "start"
              nil
              (fn [agent-node k]
                (let [p3        (aor/get-store agent-node "$$p")
                      p1        (aor/get-mirror-store agent-node module1-name "$$p")
                      p2        (aor/get-mirror-store agent-node module2-name "$$p")
                      foo-m2    (aor/mirror-agent-client agent-node module2-name "foo")
                      m2-kv     (aor/get-mirror-store agent-node module2-name "$$kv")
                      m2-doc    (aor/get-mirror-store agent-node module2-name "$$doc")
                      m2-pstore (aor/get-mirror-store agent-node module2-name "$$pstore")
                      depot3    (aor/get-depot agent-node "*depot")
                      depot1    (aor/get-mirror-depot agent-node module1-name "*depot")
                      depot2    (aor/get-mirror-depot agent-node module2-name "*depot")
                      q3        (aor/get-query-topology-client agent-node "q")
                      q1        (aor/get-mirror-query-topology-client agent-node module1-name "q")
                      q2        (aor/get-mirror-query-topology-client agent-node module2-name "q")]
                  ;; TODO: <<<<>>>> use all depots/queries/PStates
                  ;; return results of all queries / PStates updates after depot appends
                  ;;  - shouldn't query topoologies and depot appends be traced? it would be easy to
                  ;;  do so
                  ;;    - 4 methods for depot and 1 for querytopologyclient (just the blocking
                  ;;    methods)
                ))))

           (aor/define-agents! topology)
         )))
     (bind module3-name (get-module-name module3))



     (rtest/launch-module! ipc module1 {:tasks 2 :threads 2})
     (launch-module-without-eval-agent! ipc module2 {:tasks 2 :threads 2})
     (launch-module-without-eval-agent! ipc module3 {:tasks 2 :threads 2})
     ;; TODO: <<<<>>>>
     ;;  - test using:
     ;;   - regular depots
     ;;   - mirror depots
     ;;   - mirror stores
     ;;     - including for regular PStates not declared as such
     ;;   - regular query topology
     ;;   - mirror query topology
     ;;   - other module having agents or not
     ;;   - direct test of store info query
    )))
