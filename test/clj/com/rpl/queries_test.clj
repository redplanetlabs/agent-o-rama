(ns com.rpl.queries-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
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
    {:agent-invokes     [{:task-id 1 :agent-id 2 :start-time-millis 19}
                         {:task-id 2 :agent-id 3 :start-time-millis 18}
                         {:task-id  2
                          :agent-id 2
                          :start-time-millis 14
                          :foo      1
                          :bar      2}
                         {:task-id 2 :agent-id 1 :start-time-millis 12}]
     :pagination-params {0 nil 1 1 2 0}}
    (queries/to-invokes-page-result
     {0 {}
      1 {0 {:start-time-millis 10}
         1 {:start-time-millis 11}
         2 {:start-time-millis 19}}
      2 {0 {:start-time-millis 9}
         1 {:start-time-millis 12}
         2 {:start-time-millis 14 :foo 1 :bar 2}
         3 {:start-time-millis 18}}}
     4)))
  (is
   (=
    {:agent-invokes     [{:task-id 1 :agent-id 2 :start-time-millis 19}
                         {:task-id 2 :agent-id 3 :start-time-millis 18}
                         {:task-id  2
                          :agent-id 2
                          :start-time-millis 14
                          :foo      1
                          :bar      2}
                         {:task-id 2 :agent-id 1 :start-time-millis 12}
                         {:task-id 1 :agent-id 1 :start-time-millis 11}
                         {:task-id 1 :agent-id 0 :start-time-millis 10}
                         {:task-id 2 :agent-id 0 :start-time-millis 9}]
     :pagination-params {0 nil 1 nil 2 nil}}
    (queries/to-invokes-page-result
     {0 {}
      1 {0 {:start-time-millis 10}
         1 {:start-time-millis 11}
         2 {:start-time-millis 19}}
      2 {0 {:start-time-millis 9}
         1 {:start-time-millis 12}
         2 {:start-time-millis 14 :foo 1 :bar 2}
         3 {:start-time-millis 18}}}
     5)))
  (is
   (=
    {:agent-invokes     [{:task-id 1 :agent-id 0 :start-time-millis 10}
                         {:task-id 2 :agent-id 0 :start-time-millis 9}]
     :pagination-params {0 nil 1 nil 2 nil}}
    (queries/to-invokes-page-result
     {0 {}
      1 {0 {:start-time-millis 10}}
      2 {0 {:start-time-millis 9}}}
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
    {:agent-invokes     [{:task-id 2 :agent-id 12 :start-time-millis 11}
                         {:task-id 2 :agent-id 11 :start-time-millis 10}
                         {:task-id 2 :agent-id 10 :start-time-millis 9}]
     :pagination-params {0 300 1 3 2 0}}
    (queries/to-invokes-page-result
     {0 {0   {:start-time-millis 0}
         100 {:start-time-millis 1}
         200 {:start-time-millis 2}
         300 {:start-time-millis 3}}
      1 {0 {:start-time-millis 4}
         1 {:start-time-millis 5}
         2 {:start-time-millis 6}
         3 {:start-time-millis 7}}
      2 {0  {:start-time-millis 8}
         10 {:start-time-millis 9}
         11 {:start-time-millis 10}
         12 {:start-time-millis 11}}}
     4)))
  (is
   (=
    {:agent-invokes     [{:task-id 1 :agent-id 4 :start-time-millis 40}
                         {:task-id 0 :agent-id 0 :start-time-millis 37}
                         {:task-id 2 :agent-id 3 :start-time-millis 35}
                         {:task-id 3 :agent-id 3 :start-time-millis 32}
                         {:task-id 3 :agent-id 2 :start-time-millis 31}
                         {:task-id 1 :agent-id 2 :start-time-millis 30}]
     :pagination-params {0 nil 1 1 2 2 3 1}}
    (queries/to-invokes-page-result
     {0 {0 {:start-time-millis 37}}
      1 {0 {:start-time-millis 10}
         1 {:start-time-millis 20}
         2 {:start-time-millis 30}
         4 {:start-time-millis 40}}
      2 {0 {:start-time-millis 5}
         1 {:start-time-millis 8}
         2 {:start-time-millis 25}
         3 {:start-time-millis 35}}
      3 {0 {:start-time-millis 1}
         1 {:start-time-millis 22}
         2 {:start-time-millis 31}
         3 {:start-time-millis 32}}}
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
     (launch-module-without-eval-agent! ipc module {:tasks 2 :threads 1})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind q (:invokes-page-query (aor-types/underlying-objects foo)))


     ;; this would be much faster if did agent-initiate-async and then resolved
     ;; the CompletableFuture's afterwards, but this makes it much more likely
     ;; for pages to be intermixed
     (bind invokes
       (vec
        (for [_ (range 50)]
          (let [{:keys [task-id agent-invoke-id]} (aor/agent-initiate foo)]
            [task-id agent-invoke-id]
          ))))
     (doseq [[task-id agent-id] invokes]
       (is
        (= "abc"
           (aor/agent-result foo
                             (aor-types/->AgentInvokeImpl task-id agent-id)))))


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
        (is (apply >= (mapv :start-time-millis all)))
        (bind all-invokes (mapv (fn [m] [(:task-id m) (:agent-id m)]) all))
        (is (= (set all-invokes) (set invokes)))
        (doseq [page res]
          (doseq [m page]
            (let [expected-keys #{:start-time-millis :finish-time-millis
                                  :invoke-args :result :task-id :agent-id
                                  :graph-version :human-request?}]
              (is (not (:human-request? m)))
              (is (= expected-keys
                     (set/intersection expected-keys
                                       (-> m
                                           keys
                                           set))))
            )))))
    )))
