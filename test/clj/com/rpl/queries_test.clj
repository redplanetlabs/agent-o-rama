(ns com.rpl.queries-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc])
  (:import
   [com.rpl.agentorama
    AgentInvoke]
   [java.util.concurrent
    CompletableFuture]))

(deftest to-invokes-page-result-test
  (is
   (=
    {:agent-invokes     [[1 2 19] [2 3 18] [2 2 14] [2 1 12]]
     :pagination-params {0 nil 1 1 2 0}}
    (queries/to-invokes-page-result
     {0 {}
      1 {0 10
         1 11
         2 19}
      2 {0 9
         1 12
         2 14
         3 18}}
     4)))
  (is
   (=
    {:agent-invokes     [[1 2 19] [2 3 18] [2 2 14] [2 1 12] [1 1 11] [1 0 10]
                         [2 0 9]]
     :pagination-params {0 nil 1 nil 2 nil}}
    (queries/to-invokes-page-result
     {0 {}
      1 {0 10
         1 11
         2 19}
      2 {0 9
         1 12
         2 14
         3 18}}
     5)))
  (is
   (=
    {:agent-invokes     [[1 0 10] [2 0 9]]
     :pagination-params {0 nil 1 nil 2 nil}}
    (queries/to-invokes-page-result
     {0 {}
      1 {0 10}
      2 {0 9}}
     1)))
  (is
   (=
    {:agent-invokes     []
     :pagination-params {0 nil 1 nil 2 nil 3 nil}}
    (queries/to-invokes-page-result
     {0 {}
      1 {}
      2 {}
      3 {}}
     10)))
  (is
   (=
    {:agent-invokes     [[2 12 11] [2 11 10] [2 10 9]]
     :pagination-params {0 300 1 3 2 0}}
    (queries/to-invokes-page-result
     {0 {0   0
         100 1
         200 2
         300 3}
      1 {0 4
         1 5
         2 6
         3 7}
      2 {0  8
         10 9
         11 10
         12 11}}
     4)))
  (is
   (=
    {:agent-invokes     [[1 4 40] [0 0 37] [2 3 35] [3 3 32] [3 2 31] [1 2 30]]
     :pagination-params {0 nil 1 1 2 2 3 1}}
    (queries/to-invokes-page-result
     {0 {0 37}
      1 {0 10
         1 20
         2 30
         4 40}
      2 {0 5
         1 8
         2 25
         3 35}
      3 {0 1
         1 22
         2 31
         3 32}}
     4)))
)

(deftest invokes-page-query-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (-> topology
            (aor/new-agent "foo")
            (aor/node "start"
                      nil
                      (fn [agent-node]
                        (aor/result! agent-node "abc"))))))
     (rtest/launch-module! ipc module {:tasks 2 :threads 1})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind q
       (foreign-query ipc
                      module-name
                      (queries/agent-get-invokes-page-query-name "foo")))


     ;; this would be much faster if did agent-initiate-async and then resolved
     ;; the CompletableFuture's afterwards, but this makes it much more likely
     ;; for pages to be intermixed
     (bind invokes
       (vec
        (for [_ (range 50)]
          (let [^AgentInvoke inv (aor/agent-initiate foo)]
            [(.getTaskId inv) (.getAgentInvokeId inv)]
          ))))
     (doseq [[task-id agent-id] invokes]
       (is (= "abc" (aor/agent-result foo (AgentInvoke. task-id agent-id)))))


     (doseq [i [1 6 9 10]]
       (letlocals
        (bind res
          (loop [ret    []
                 params nil]
            (let [{:keys [agent-invokes pagination-params]}
                  (foreign-invoke-query q i params)
                  ret (conj ret agent-invokes)]
              (if (every? nil? (vals pagination-params))
                ret
                (recur ret pagination-params)
              ))))

        ;; verify multiple pages
        (is (> (count res) 2))
        (is (every? #(>= (count %) i) (butlast res)))
        (bind all (apply concat res))
        (is (apply >= (mapv #(nth % 2) all)))
        (bind all-invokes (mapv #(vec (butlast %)) all))
        (is (= (set all-invokes) (set invokes)))))
    )))
