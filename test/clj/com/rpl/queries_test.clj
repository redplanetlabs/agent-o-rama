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
     :pagination-params {0 nil
                         1 {:end-id 1 :inclusive? true}
                         2 {:end-id 0 :inclusive? true}}}
    (queries/to-invokes-page-result
     {0 {:task-page {} :scan-end-id nil}
      1 {:task-page {0 {:start-time-millis 10}
                     1 {:start-time-millis 11}
                     2 {:start-time-millis 19}}
         :scan-end-id nil}
      2 {:task-page {0 {:start-time-millis 9}
                     1 {:start-time-millis 12}
                     2 {:start-time-millis 14 :foo 1 :bar 2}
                     3 {:start-time-millis 18}}
         :scan-end-id nil}}
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
     {0 {:task-page {} :scan-end-id nil}
      1 {:task-page {0 {:start-time-millis 10}
                     1 {:start-time-millis 11}
                     2 {:start-time-millis 19}}
         :scan-end-id nil}
      2 {:task-page {0 {:start-time-millis 9}
                     1 {:start-time-millis 12}
                     2 {:start-time-millis 14 :foo 1 :bar 2}
                     3 {:start-time-millis 18}}
         :scan-end-id nil}}
     5)))
  (is
   (=
    {:agent-invokes     [{:task-id 0 :agent-id 11 :start-time-millis 11}
                         {:task-id 0 :agent-id 10 :start-time-millis 10}]
     :pagination-params {0 {:end-id 10 :inclusive? false}}}
    (queries/to-invokes-page-result
     {0 {:task-page {10 {:start-time-millis 10}
                     11 {:start-time-millis 11}}
         :scan-end-id 10}}
     2)))
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
                 (foreign-invoke-query q i params nil)
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

     ;; This is the desired API for the new filter-capable query:
     ;; [page-size pagination-params filters]
     ;; It should accept filter params and return only matching invokes.
     ;; This assertion intentionally fails until query topology is upgraded.
     (bind slow-res
       (try
         {:data (foreign-invoke-query q
                                      10
                                      nil
                                      {:node-name "start"
                                       :latency-ms {:min 80}
                                       :has-error? false})}
         (catch Throwable t {:error t})))
     (is (nil? (:error slow-res))
         "invokes-page query should accept filter params without arity/runtime errors")
     (when-let [rows (-> slow-res :data :agent-invokes)]
       (is (seq rows))
       (is (every? #(= :success (:status %)) rows))
       (is (every? (fn [m]
                     (let [lat (- (:finish-time-millis m) (:start-time-millis m))]
                       (>= lat 80)))
                   rows)))

     (bind err-res
       (try
         {:data (foreign-invoke-query q
                                      10
                                      nil
                                      {:has-error? true})}
         (catch Throwable t {:error t})))
     (is (nil? (:error err-res))
         "invokes-page query should support has-error filter")
     (when-let [rows (-> err-res :data :agent-invokes)]
       (is (seq rows))
       (is (every? #(= :failure (:status %)) rows)))

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
       (try
         {:data (foreign-invoke-query q
                                      10
                                      nil
                                      {:feedback-metric {:metric-name "quality"
                                                         :comparator :<=
                                                         :value 3
                                                         :source :human}})}
         (catch Throwable t {:error t})))
     (is (nil? (:error feedback-res))
         "invokes-page query should support feedback metric comparator filter")
     (when-let [rows (-> feedback-res :data :agent-invokes)]
      (is (= 1 (count rows)))
      (is (every? #(contains? % :feedback-metric-value) rows))
      (is (every? #(number? (:feedback-metric-value %)) rows)))

     (bind source-res
       (try
         {:data (foreign-invoke-query q
                                      10
                                      nil
                                      {:source "EXPERIMENT"})}
         (catch Throwable t {:error t})))
     (is (nil? (:error source-res))
         "invokes-page query should support source string filter")
     (when-let [rows (-> source-res :data :agent-invokes)]
       (is (= 1 (count rows))))

     (bind source-not-res
       (try
         {:data (foreign-invoke-query q
                                      20
                                      nil
                                      {:source "EXPERIMENT"
                                       :source-not? true})}
         (catch Throwable t {:error t})))
     (is (nil? (:error source-not-res))
         "invokes-page query should support source negation filter")
     (when-let [rows (-> source-not-res :data :agent-invokes)]
       (is (= 5 (count rows))))

     (bind source-manual-res
       (try
         {:data (foreign-invoke-query q
                                      20
                                      nil
                                      {:source "MANUAL"})}
         (catch Throwable t {:error t})))
     (is (nil? (:error source-manual-res))
         "invokes-page query should support manual source class")
     (when-let [rows (-> source-manual-res :data :agent-invokes)]
       (is (= 5 (count rows))))

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
