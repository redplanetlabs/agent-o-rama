(ns com.rpl.agent-o-rama.ui.agents
  (:require [com.rpl.specter :as s]))

(defn index [{:keys [parameters]}]
  {:status
   200
   
   :body
   [{:module-id "ModuleA" :agent-id "research"}
    {:module-id "ModuleA" :agent-id "support"}
    {:module-id "ModuleB" :agent-id "research"}]})

(defn get-invokes [{{:keys [module-id agent-id]} :path-params}]
  {:status
   200
   
   :body
   {:invokes ;;agent-invoke-pstate-<agent-id> 
    [
     ;; probably want to join/lookup more data about each root invoke from the
     ;; $$_agent_node-<agent-id>[root-invoke-id] pstate
     {:root-invoke-id 121
      :invoke-args ["CUSTOMER-123"]
      :graph-version 0
      :result {:success true} }
     {:root-invoke-id 122
      :invoke-args ["CUSTOMER-66"]
      :graph-version 0
      :result {:success true} }
     {:root-invoke-id 123
      :invoke-args ["CUSTOMER-456"]
      :graph-version 0
      :result {:success true} }
     {:root-invoke-id 124
      :invoke-args ["CUSTOMER-222"]
      :graph-version 0
      :result {:success true} }]}})

(def all-data
  {-1183571186781372609 {:invoked-agg-invoke-id 6016686666795829822},
   6016686666795829822
   {:agg-invoke-id nil,
    :agg-input-count 3,
    :agg-start-res "xyz-0-01-000-0000",
    :emits [],
    :finish-time-millis 1748898749501,
    :node "agg",
    :agg-inputs-first-10
    [{:invoke-id -1183571186781372609, :args ["1-a"]}
     {:invoke-id -2844967203242097507, :args ["1-a"]}
     {:invoke-id 2565790692897823018, :args ["1-a"]}],
    :agg-ack-val 0,
    :result
    {:val [["1-a" "1-a" "1-a"] "xyz-0-01-000-0000"], :failure? false},
    :agg-finished? true,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749489,
    :agg-state ["1-a" "1-a" "1-a"],
    :input [["1-a" "1-a" "1-a"] "xyz-0-01-000-0000"],
    :agg-start-invoke-id 6772023139057767335,
    :graph-task-id 3},
   6772023139057767335
   {:started-agg? true,
    :agg-invoke-id 6016686666795829822,
    :emits
    [{:invoke-id -4117109912539327325,
      :target-task-id 3,
      :node-name "node4",
      :args [1]}
     {:invoke-id 3599212109813205718,
      :target-task-id 0,
      :node-name "node4",
      :args [1]}
     {:invoke-id -7519488666983447018,
      :target-task-id 2,
      :node-name "node4",
      :args [1]}],
    :finish-time-millis 1748898749296,
    :node "node3",
    :result nil,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749256,
    :input ["xyz-0-01-000"],
    :graph-task-id 3},
   -7519488666983447018
   {:agg-invoke-id 6016686666795829822,
    :emits
    [{:invoke-id -2844967203242097507,
      :target-task-id 3,
      :node-name "agg",
      :args ["1-a"]}],
    :finish-time-millis 1748898749397,
    :node "node4",
    :result nil,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749345,
    :input [1],
    :graph-task-id 3},
   -4217398990033465259 {:invoked-agg-invoke-id -8676892891881354486},
   -3656460452452032586
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id 6772023139057767335,
      :target-task-id 3,
      :node-name "node3",
      :args ["xyz-0-01-000"]}],
    :finish-time-millis 1748898749193,
    :node "node2",
    :result nil,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749171,
    :input ["xyz-0-01"],
    :graph-task-id 3},
   -4117109912539327325
   {:agg-invoke-id 6016686666795829822,
    :emits
    [{:invoke-id 2565790692897823018,
      :target-task-id 3,
      :node-name "agg",
      :args ["1-a"]}],
    :finish-time-millis 1748898749421,
    :node "node4",
    :result nil,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749369,
    :input [1],
    :graph-task-id 3},
   -6292301921292308170
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id -5229794342068332562,
      :target-task-id 3,
      :node-name "node2",
      :args ["xyz-0-00"]}
     {:invoke-id -3656460452452032586,
      :target-task-id 2,
      :node-name "node2",
      :args ["xyz-0-01"]}],
    :finish-time-millis 1748898749130,
    :node "node1",
    :result nil,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749114,
    :input ["xyz-0"],
    :graph-task-id 3},
   4379592056016283542
   {:agg-invoke-id -8676892891881354486,
    :emits
    [{:invoke-id -5626458476645414388,
      :target-task-id 3,
      :node-name "agg",
      :args ["1-a"]}],
    :finish-time-millis 1748898749379,
    :node "node4",
    :result nil,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749315,
    :input [1],
    :graph-task-id 3},
   2565790692897823018 {:invoked-agg-invoke-id 6016686666795829822},
   -2844967203242097507 {:invoked-agg-invoke-id 6016686666795829822},
   -8676892891881354486
   {:agg-invoke-id nil,
    :agg-input-count 3,
    :agg-start-res "xyz-0-00-000-0000",
    :emits [],
    :finish-time-millis 1748898749501,
    :node "agg",
    :agg-inputs-first-10
    [{:invoke-id -4217398990033465259, :args ["1-a"]}
     {:invoke-id -5626458476645414388, :args ["1-a"]}
     {:invoke-id 2260430608803227174, :args ["1-a"]}],
    :agg-ack-val 0,
    :result
    {:val [["1-a" "1-a" "1-a"] "xyz-0-00-000-0000"], :failure? false},
    :agg-finished? true,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749488,
    :agg-state ["1-a" "1-a" "1-a"],
    :input [["1-a" "1-a" "1-a"] "xyz-0-00-000-0000"],
    :agg-start-invoke-id 3182019010553149164,
    :graph-task-id 3},
   3599212109813205718
   {:agg-invoke-id 6016686666795829822,
    :emits
    [{:invoke-id -1183571186781372609,
      :target-task-id 3,
      :node-name "agg",
      :args ["1-a"]}],
    :finish-time-millis 1748898749357,
    :node "node4",
    :result nil,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749339,
    :input [1],
    :graph-task-id 3},
   6679449464743596269
   {:agg-invoke-id -8676892891881354486,
    :emits
    [{:invoke-id -4217398990033465259,
      :target-task-id 3,
      :node-name "agg",
      :args ["1-a"]}],
    :finish-time-millis 1748898749356,
    :node "node4",
    :result nil,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749315,
    :input [1],
    :graph-task-id 3},
   2260430608803227174 {:invoked-agg-invoke-id -8676892891881354486},
   5596136011188865643
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id -6292301921292308170,
      :target-task-id 3,
      :node-name "node1",
      :args ["xyz-0"]}],
    :finish-time-millis 1748898749088,
    :node "start",
    :result nil,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749066,
    :input ["xyz"],
    :graph-task-id 3},
   130054052758164426
   {:agg-invoke-id -8676892891881354486,
    :emits
    [{:invoke-id 2260430608803227174,
      :target-task-id 3,
      :node-name "agg",
      :args ["1-a"]}],
    :finish-time-millis 1748898749397,
    :node "node4",
    :result nil,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749339,
    :input [1],
    :graph-task-id 3},
   -5229794342068332562
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id 3182019010553149164,
      :target-task-id 3,
      :node-name "node3",
      :args ["xyz-0-00-000"]}],
    :finish-time-millis 1748898749172,
    :node "node2",
    :result nil,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749154,
    :input ["xyz-0-00"],
    :graph-task-id 3},
   -5626458476645414388 {:invoked-agg-invoke-id -8676892891881354486},
   3182019010553149164
   {:started-agg? true,
    :agg-invoke-id -8676892891881354486,
    :emits
    [{:invoke-id 130054052758164426,
      :target-task-id 3,
      :node-name "node4",
      :args [1]}
     {:invoke-id 4379592056016283542,
      :target-task-id 2,
      :node-name "node4",
      :args [1]}
     {:invoke-id 6679449464743596269,
      :target-task-id 0,
      :node-name "node4",
      :args [1]}],
    :finish-time-millis 1748898749256,
    :node "node3",
    :result nil,
    :nested-ops [],
    :graph-id 0,
    :start-time-millis 1748898749230,
    :input ["xyz-0-00-000"],
    :graph-task-id 3}})

(defn remove-implicit-nodes
  "Preprocesses the invokes-map to remove implicit nodes and rewire edges to real nodes.
   Returns a new map without implicit nodes where all references are updated."
  [invokes-map]
  (let [implicit->real
        (into {}
              (s/select [s/ALL
                         (s/selected? s/LAST (s/must :invoked-agg-invoke-id))
                         (s/view (fn [[id node]]
                                   [id (:invoked-agg-invoke-id node)]))]
                        invokes-map))]
    (->> invokes-map
         (s/setval [s/ALL 
                    (s/selected? s/LAST (s/must :invoked-agg-invoke-id))]
                   s/NONE)
         (s/transform [s/ALL 
                       s/LAST 
                       (s/must :emits) 
                       s/ALL 
                       :invoke-id]
                      #(get implicit->real % %)))))

(def use-large? false)

(defn generate-synthetic-graph
  "Generate a large synthetic invokes-map containing `n` nodes arranged as a binary tree.
  Each node (except leaves) emits to its left and right children. The root node is
  labelled `start` to integrate smoothly with existing front-end logic."
  [n]
  (into {}
        (for [id (range 1 (inc n))]
          (let [left  (* 2 id)
                right (inc left)
                emits (->> [[left 0] [right 1]]
                           (filter (fn [[child _]] (<= child n)))
                           (map (fn [[child task-id]]
                                  {:invoke-id child
                                   :target-task-id task-id
                                   :node-name (str "node" child)
                                   :args []})))]
            [id {:node (if (= id 1) "start" (str "node" id))
                 :emits emits
                 :start-time-millis 0
                 :finish-time-millis 1
                 :input []
                 :result nil
                 :async-ops []}]))))

(def ^:private synthetic-20k-graph (generate-synthetic-graph 20000))

(defn invoke [{{:keys [module-id agent-id invoke-id]} :path-params
              query-params                            :query-params}]
  {:status 200
   :body   {:next-task-invoke-pairs [] ;; [task id, invoke id]
            :invokes-map (remove-implicit-nodes (if use-large? synthetic-20k-graph all-data))}})

(defn get-paginated-graph
  "Traverses the graph starting from a node and returns a subset of nodes
   within the specified depth limit. Returns nodes and their immediate children,
   marking which children have further descendants for pagination."
  [invokes-map start-node-id max-depth]
  (let [;; Preprocess to remove implicit nodes
        clean-graph (remove-implicit-nodes invokes-map)
        
        ;; Simple helper to get children - no mapping needed!
        get-children (fn [node-id]
                       (when-let [node (get clean-graph node-id)]
                         (map :invoke-id (:emits node))))
        
        ;; Clean BFS traversal
        traverse (fn [start-id depth-limit]
                   (loop [queue [[start-id 0]]
                          visited #{}
                          result {}]
                     (if (empty? queue)
                       result
                       (let [[current-id depth] (first queue)
                             remaining (rest queue)]
                         (if (or (visited current-id) 
                                 (> depth depth-limit)
                                 (nil? (get clean-graph current-id)))
                           (recur remaining visited result)
                           (let [node (get clean-graph current-id)
                                 children (get-children current-id)
                                 
                                 ;; Check if there are children that would be at the next depth level
                                 ;; but aren't included due to depth limit
                                 has-unloaded-children? (and (= depth depth-limit)
                                                             (seq children))
                                 
                                 ;; Add pagination info
                                 node-with-pagination (assoc node :has-paginated-children 
                                                             (if has-unloaded-children? 
                                                               (set children)
                                                               #{}))
                                 
                                 ;; Add children to queue only if within depth
                                 new-queue (if (< depth depth-limit)
                                             (concat remaining (map #(vector % (inc depth)) children))
                                             remaining)]
                             (recur new-queue
                                    (conj visited current-id)
                                    (assoc result current-id node-with-pagination))))))))
        
        ;; Find the start node ID
        start-id (or start-node-id
                     ;; Find node with :node "start" in the clean graph
                     (first (keep (fn [[id node]]
                                    (when (= (:node node) "start") id))
                                  clean-graph)))]
    (traverse start-id max-depth)))

(defn invoke-paginated 
  [{{:keys [module-id agent-id invoke-id]} :path-params
    {:strs [start-node-id depth] :or {depth "3"}} :query-params
    :as req}]
  (let [depth-int (Integer/parseInt depth)
        start-id (when start-node-id (Long/parseLong start-node-id))
        paginated-data (get-paginated-graph (if use-large? synthetic-20k-graph all-data) start-id depth-int)]
    {:status 200
     :body {:invokes-map paginated-data
            :pagination {:depth depth-int
                         :start-node-id (or start-id "root")}}}))


