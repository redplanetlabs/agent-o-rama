(ns com.rpl.queries-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.evaluators :as evals]
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
                 ;; Keep scan-page-size tight so this test exercises true
                 ;; multi-request pagination instead of draining each task.
                 (foreign-invoke-query q i 10 params nil)
                  ret (conj ret agent-invokes)]
              (if (every? nil? (vals pagination-params))
                ret
                (recur ret pagination-params)
              ))))

        ;; page size is approximate at query level, but should still paginate.
        (is (> (count res) 1))
        (is (every? #(>= (count %) i) (butlast res)))
        (bind all (apply concat res))
        (is (apply >= (mapv :start-time-millis all)))
        (bind all-invokes (mapv (fn [m] [(:task-id m) (:agent-id m)]) all))
        (is (= (set all-invokes) (set invokes)))
        (doseq [page res]
          (doseq [m page]
            (let [expected-keys #{:start-time-millis :finish-time-millis
                                  :invoke-args :status :task-id :agent-id
                                  :graph-version :human-request?}]
              (is (not (:human-request? m)))
              (is (= expected-keys
                     (set/intersection expected-keys
                                       (-> m
                                           keys
                                           set))))
            )))))
    )))

(deftest invokes-page-query-filters-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (-> topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             nil
             (fn [agent-node {:keys [route sleep-ms]}]
               (when sleep-ms
                 (Thread/sleep ^long sleep-ms))
               (cond
                 (= route :fail)
                 (throw (ex-info "boom" {:route route}))

                 (= route :slow)
                 (aor/result! agent-node {:ok true :route route})

                 :else
                 (aor/result! agent-node {:ok true :route :fast})))))))
     (launch-module-without-eval-agent! ipc module {:tasks 2 :threads 1})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind global-actions-depot
       (:global-actions-depot (aor-types/underlying-objects agent-manager)))
     (bind q (:invokes-page-query (aor-types/underlying-objects foo)))

     ;; Build a mixed population across success/failure and latency buckets.
     (bind experiment-source
       (aor-types/->valid-ExperimentSourceImpl
        (java.util.UUID/randomUUID)
        (java.util.UUID/randomUUID)))
     (bind runs
       [{:name :fast-1 :args {:route :fast :sleep-ms 1}}
        {:name :fast-2 :args {:route :fast :sleep-ms 1}}
        {:name :slow-exp :args {:route :slow :sleep-ms 90} :source experiment-source}
        {:name :slow-2 :args {:route :slow :sleep-ms 100}}
        {:name :fail-1 :args {:route :fail :sleep-ms 20}}
        {:name :fail-2 :args {:route :fail :sleep-ms 30}}])

     (bind created-runs
       (vec
        (for [{:keys [name args source]} runs]
          (let [inv (if source
                      (binding [aor-types/OPERATION-SOURCE source]
                        (aor/agent-initiate foo args))
                      (aor/agent-initiate foo args))]
            {:name name :invoke inv}))))

     (doseq [{:keys [invoke]} created-runs]
       (let [inv invoke]
         (try
           (aor/agent-result foo inv)
           (catch Throwable _))))

     (bind slow-res
       (foreign-invoke-query q
                             10
                             100
                             nil
                             {:node-name "start"
                              :latency-ms {:min 80}
                              :has-error? false}))
     (bind slow-rows (:agent-invokes slow-res))
     (is (seq slow-rows))
     (is (every? #(= :success (:status %)) slow-rows))
     (is (every? (fn [m]
                   (let [lat (- (:finish-time-millis m) (:start-time-millis m))]
                     (>= lat 80)))
                 slow-rows))

     (bind err-res
       (foreign-invoke-query q
                             10
                             100
                             nil
                             {:has-error? true}))
     (bind err-rows (:agent-invokes err-res))
     (is (seq err-rows))
     (is (every? #(= :failure (:status %)) err-rows))

     ;; Add human feedback scores for metric-filter testing.
     (bind fast-target
       (aor-types/->valid-FeedbackTarget
        "foo"
        (:invoke (first (filter #(= :fast-1 (:name %)) created-runs)))
        nil))
     (bind slow-exp-target
       (aor-types/->valid-FeedbackTarget
        "foo"
        (:invoke (first (filter #(= :slow-exp (:name %)) created-runs)))
        nil))
     (evals/add-human-feedback! global-actions-depot fast-target "reviewer-1" {"quality" 2} "bad")
     (evals/add-human-feedback! global-actions-depot slow-exp-target "reviewer-2" {"quality" 8} "good")

     (bind feedback-res
       (foreign-invoke-query q
                             10
                             100
                             nil
                             {:feedback-metric {:metric-name "quality"
                                                :comparator :<=
                                                :value 3
                                                :source :human}}))
     (bind feedback-rows (:agent-invokes feedback-res))
     (is (= 1 (count feedback-rows)))
     (is (every? #(contains? % :feedback-metric-value) feedback-rows))
     (is (every? #(number? (:feedback-metric-value %)) feedback-rows))

     (bind source-res
       (foreign-invoke-query q
                             10
                             100
                             nil
                             {:source "EXPERIMENT"}))
     (bind source-rows (:agent-invokes source-res))
     (is (= 1 (count source-rows)))

     (bind source-not-res
       (foreign-invoke-query q
                             20
                             100
                             nil
                             {:source "EXPERIMENT"
                              :source-not? true}))
     (bind source-not-rows (:agent-invokes source-not-res))
     (is (= 5 (count source-not-rows)))

     (bind source-manual-res
       (foreign-invoke-query q
                             20
                             100
                             nil
                             {:source "MANUAL"}))
     (bind source-manual-rows (:agent-invokes source-manual-res))
     (is (= 5 (count source-manual-rows)))

     (bind empty-res
       (foreign-invoke-query q
                             10
                             100
                             nil
                             {:has-error? true
                              :source "EXPERIMENT"}))
     (is (empty? (:agent-invokes empty-res)))

     )))

(deftest invokes-page-query-filter-pagination-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (-> topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             nil
             (fn [agent-node {:keys [route sleep-ms]}]
               (when sleep-ms
                 (Thread/sleep ^long sleep-ms))
               (if (= route :fail)
                 (throw (ex-info "boom" {:route route}))
                 (aor/result! agent-node {:ok true :route :fast})))))))
     (launch-module-without-eval-agent! ipc module {:tasks 2 :threads 1})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind q (:invokes-page-query (aor-types/underlying-objects foo)))

     ;; Sparse matches force scan-window growth; pagination should continue
     ;; from each task's scan cursor without restarts or duplicates.
     (bind runs
       (vec
        (for [i (range 40)]
          (let [fail? (zero? (mod i 4))]
            {:fail? fail?
             :args {:route (if fail? :fail :fast)
                    :sleep-ms 1}}))))

     (bind created-runs
       (vec
        (for [{:keys [fail? args]} runs]
          {:fail? fail?
           :invoke (aor/agent-initiate foo args)})))

     (doseq [{:keys [invoke]} created-runs]
       (try
         (aor/agent-result foo invoke)
         (catch Throwable _)))

     (bind expected-failed-invokes
       (set
        (for [{:keys [fail? invoke]} created-runs
              :when fail?]
          [(:task-id invoke) (:agent-invoke-id invoke)])))

     (bind pages
       (loop [ret []
              params nil
              i 0]
         (when (> i 200)
           (throw (ex-info "filtered pagination did not terminate"
                           {:iterations i})))
         (let [{:keys [agent-invokes pagination-params]}
               (foreign-invoke-query q
                                    2
                                    100
                                    params
                                    {:has-error? true})
               ret (conj ret agent-invokes)]
           (if (every? nil? (vals pagination-params))
             ret
             (recur ret pagination-params (inc i))))))

     (bind all (apply concat pages))
     (bind all-ids (mapv (fn [m] [(:task-id m) (:agent-id m)]) all))

     (is (> (count pages) 1))
     (is (= (count all-ids) (count (set all-ids))))
     (is (= expected-failed-invokes (set all-ids)))
     (is (every? #(= :failure (:status %)) all))
     )))

(deftest invokes-page-query-scan-page-size-matrix-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (-> topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             nil
             (fn [agent-node {:keys [route sleep-ms]}]
               (when sleep-ms
                 (Thread/sleep ^long sleep-ms))
               (if (= route :fail)
                 (throw (ex-info "boom" {:route route}))
                 (aor/result! agent-node {:ok true :route :fast})))))))
     (launch-module-without-eval-agent! ipc module {:tasks 2 :threads 1})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind q (:invokes-page-query (aor-types/underlying-objects foo)))

     ;; Sparse source matches force each request to read through multiple scan windows.
     (bind experiment-source
       (aor-types/->valid-ExperimentSourceImpl
        (java.util.UUID/randomUUID)
        (java.util.UUID/randomUUID)))
     (bind runs
       (vec
        (for [i (range 120)]
          (let [experiment? (zero? (mod i 11))]
            {:experiment? experiment?
             :args {:route (if experiment? :experiment :manual)
                    :sleep-ms 1}}))))

     (bind created-runs
       (vec
        (for [{:keys [experiment? args]} runs]
          {:experiment? experiment?
           :invoke (if experiment?
                     (binding [aor-types/OPERATION-SOURCE experiment-source]
                       (aor/agent-initiate foo args))
                     (aor/agent-initiate foo args))})))

     (doseq [{:keys [invoke]} created-runs]
       (aor/agent-result foo invoke))

     (bind expected-experiment-invokes
       (set
        (for [{:keys [experiment? invoke]} created-runs
              :when experiment?]
          [(:task-id invoke) (:agent-invoke-id invoke)])))

     (doseq [scan-page-size [2 3 5 8]]
       (let [pages
             (loop [ret []
                    params nil
                    i 0]
               (when (> i 250)
                 (throw (ex-info "scan-size matrix pagination did not terminate"
                                 {:scan-page-size scan-page-size
                                  :iterations i})))
               (let [{:keys [agent-invokes pagination-params]}
                     (foreign-invoke-query q
                                          4
                                          scan-page-size
                                          params
                                          {:source "EXPERIMENT"})
                     ret (conj ret agent-invokes)]
                 (if (every? nil? (vals pagination-params))
                   ret
                   (recur ret pagination-params (inc i)))))
             all (apply concat pages)
             all-ids (mapv (fn [m] [(:task-id m) (:agent-id m)]) all)]
         (is (> (count pages) 2))
         (is (= (count all-ids) (count (set all-ids))))
         (is (= expected-experiment-invokes (set all-ids)))
         (is (every? #(= :success (:status %)) all))))
     )))
