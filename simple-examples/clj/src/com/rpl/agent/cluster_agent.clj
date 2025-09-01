(ns com.rpl.agent.cluster-agent
  "Demonstrates cross-module agent communication and cluster integration.

  Features demonstrated:
  - Multiple agent modules for cross-module communication
  - Agent-to-agent communication patterns
  - Distributed agent coordination
  - Module deployment and interaction
  - Cross-agent data sharing"
  (:require
   [com.rpl.agent-o-rama :refer :all]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama :refer :all]
   [com.rpl.rama.test :as rtest]))

;;; Primary agent module
(defagentmodule ClusterAgentModule
  [topology]

  ;; Shared store for cross-module coordination
  (declare-key-value-store topology "$$coordination-store" String Object)

  (->
    topology
    (new-agent "CoordinatorAgent")

    ;; Node that initiates cross-module communication
    (node
     "coordinate"
     nil
     (fn [agent-node {:keys [task-id workers data]}]
       (let [coordination-store (get-store agent-node "$$coordination-store")]

         (println "CoordinatorAgent: Starting coordination for task" task-id)

         ;; Store coordination data
         (store/put! coordination-store
                     task-id
                     {:status     "coordinating"
                      :workers    workers
                      :data       data
                      :started-at (System/currentTimeMillis)})

         ;; Simulate coordination work
         (println "CoordinatorAgent: Coordinating with" workers "workers")
         (Thread/sleep 100)

         ;; Update coordination status
         (let [coordination-result {:processed-workers     workers
                                    :total-data-size       (count data)
                                    :coordination-complete true}]
           (store/put! coordination-store
                       task-id
                       (merge (store/get coordination-store task-id)
                              {:status       "completed"
                               :result       coordination-result
                               :completed-at (System/currentTimeMillis)}))

           (result! agent-node
                    {:action       "coordination-complete"
                     :task-id      task-id
                     :workers      workers
                     :coordination-result coordination-result
                     :processed-at (System/currentTimeMillis)})))))))

;;; Secondary agent module for worker functionality
(defagentmodule WorkerAgentModule
  [topology]

  ;; Shared store for worker state
  (declare-key-value-store topology "$$worker-store" String Object)

  (->
    topology
    (new-agent "WorkerAgent")

    ;; Node that performs worker tasks
    (node
     "work"
     nil
     (fn [agent-node {:keys [worker-id task items]}]
       (let [worker-store (get-store agent-node "$$worker-store")]

         (println (str "WorkerAgent-" worker-id
                       ": Starting work on task " task))

         ;; Store worker state
         (store/put! worker-store
                     worker-id
                     {:status      "working"
                      :task        task
                      :items-count (count items)
                      :started-at  (System/currentTimeMillis)})

         ;; Simulate processing work
         (doseq [item items]
           (println (str "WorkerAgent-" worker-id ": Processing " item))
           (Thread/sleep 50))

         ;; Update worker completion status
         (let [work-result {:items-processed (count items)
                            :processing-time (* (count items) 50)
                            :worker-id       worker-id}]
           (store/put! worker-store
                       worker-id
                       (merge (store/get worker-store worker-id)
                              {:status       "completed"
                               :result       work-result
                               :completed-at (System/currentTimeMillis)}))

           (result! agent-node
                    {:action       "work-complete"
                     :worker-id    worker-id
                     :task         task
                     :work-result  work-result
                     :processed-at (System/currentTimeMillis)})))))))

(defn -main
  "Run the cluster agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    ;; Deploy both modules to simulate cluster deployment
    (rtest/launch-module! ipc ClusterAgentModule {:tasks 2 :threads 2})
    (rtest/launch-module! ipc WorkerAgentModule {:tasks 2 :threads 2})

    (let [coord-manager     (agent-manager ipc
                                           (get-module-name ClusterAgentModule))
          worker-manager    (agent-manager ipc
                                           (get-module-name WorkerAgentModule))
          coordinator-agent (agent-client coord-manager "CoordinatorAgent")
          worker-agent      (agent-client worker-manager "WorkerAgent")]

      (println "Cluster Agent Example:")
      (println
       "Demonstrating cross-module agent communication and coordination")

      ;; Test data for distributed processing
      (let [task-data    ["item1" "item2" "item3" "item4" "item5" "item6"]
            worker-count 3
            task-id      "distributed-task-001"]

        (println "\nStarting coordinator agent...")
        (let [coord-result (agent-invoke coordinator-agent
                                         {:task-id task-id
                                          :workers worker-count
                                          :data    task-data})]

          (println "\nCoordinator Results:")
          (println "  Action:" (:action coord-result))
          (println "  Task ID:" (:task-id coord-result))
          (println "  Workers:" (:workers coord-result))
          (println "  Coordination result:" (:coordination-result coord-result))

          ;; Start multiple worker agents to simulate distributed work
          (println "\nStarting worker agents...")
          (let [items-per-worker (partition-all (Math/ceil (/ (count task-data)
                                                              worker-count))
                                                task-data)
                worker-results   (atom [])]

            (doseq [[idx items] (map-indexed vector items-per-worker)]
              (let [worker-id (str "worker-" (inc idx))
                    result    (agent-invoke worker-agent
                                            {:worker-id worker-id
                                             :task      task-id
                                             :items     items})]
                (swap! worker-results conj result)
                (println (str "\nWorker-" (inc idx) " Results:"))
                (println "    Action:" (:action result))
                (println "    Worker ID:" (:worker-id result))
                (println "    Work result:" (:work-result result))))

            (println "\nCluster Processing Summary:")
            (println "  Total items processed:" (count task-data))
            (println "  Workers used:" worker-count)
            (println "  Worker results:" (count @worker-results))

            (let [total-processing-time (reduce +
                                         (map #(get-in %
                                                       [:work-result
                                                        :processing-time])
                                              @worker-results))
                  total-items-processed (reduce +
                                         (map #(get-in %
                                                       [:work-result
                                                        :items-processed])
                                              @worker-results))]
              (println "  Total processing time:" total-processing-time "ms")
              (println "  Total items processed by workers:"
                       total-items-processed))))

        (println "\nNotice how:")
        (println "- Multiple agent modules can be deployed simultaneously")
        (println "- Agents from different modules can coordinate work")
        (println "- Each module maintains its own stores and state")
        (println
         "- Cross-module communication enables distributed processing")))))
