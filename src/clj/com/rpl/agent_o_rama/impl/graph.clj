(ns com.rpl.agent-o-rama.impl.graph
  (:use [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.multi-agg :as ma]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops]
   [loom.attr :as lattr]
   [loom.graph :as lgraph])
  (:import
   [com.rpl.agentorama
    AgentGraph
    MultiAgg$Impl
    UpdateMode]
   [com.rpl.agentorama.impl
    BuiltInAgg
    NippyMap]
   [com.rpl.agentorama.ops
    RamaVoidFunction3]
   [com.rpl.agent_o_rama.impl.types
    Node
    NodeAgg
    NodeAggStart]
   [com.rpl.rama.ops
    RamaAccumulatorAgg
    RamaCombinerAgg]
   [java.util
    UUID]))

(defn- nodes->graph
  [nodes]
  (reduce-kv
   (fn [graph name {:keys [node-obj output-nodes]}]
     (reduce
      (fn [graph output]
        (lgraph/add-edges graph [name output]))
      (-> graph
          (lgraph/add-nodes name)
          (lattr/add-attr name :node-obj node-obj))
      output-nodes))
   (lgraph/digraph)
   nodes))

(defn- annotate-aggs-add-queue
  [queue nodes path curr-node agg-stack]
  (let [new-path (conj path curr-node)]
    (reduce
     (fn [queue node]
       (conj queue [node agg-stack new-path]))
     queue
     nodes)))

(defn- annotate-aggs
  [graph start-node]
  (loop [queue     (conj clojure.lang.PersistentQueue/EMPTY [start-node [] []])
         graph     graph
         traversed #{}]
    (if (empty? queue)
      graph
      (let [[node agg-stack path] (peek queue)
            next-queue     (pop queue)
            curr-agg       (peek agg-stack)
            node-obj       (lattr/attr graph node :node-obj)
            next-traversed (conj traversed node)]
        (cond
          (contains? traversed node)
          (do
            (when-not (= (lattr/attr graph node :agg) curr-agg)
              (throw (h/ex-info "Invalid loop to different agg context"
                                {:agg1 curr-agg
                                 :agg2 (lattr/attr graph node :agg)
                                 :node node
                                 :path path}))
              graph)
            (recur next-queue graph traversed))

          (instance? Node node-obj)
          (recur
           (annotate-aggs-add-queue next-queue
                                    (lgraph/successors graph node)
                                    path
                                    node
                                    agg-stack)
           (lattr/add-attr graph node :agg curr-agg)
           next-traversed)

          (instance? NodeAggStart node-obj)
          (let [new-agg-stack (conj agg-stack node)]
            (recur
             (annotate-aggs-add-queue next-queue
                                      (lgraph/successors graph node)
                                      path
                                      node
                                      new-agg-stack)
             (lattr/add-attr graph node :agg curr-agg)
             next-traversed))

          (instance? NodeAgg node-obj)
          (do
            (when (nil? curr-agg)
              (throw (h/ex-info "Reached AggNode outside of agg context"
                                {:name node :path path})))
            (let [new-agg-stack  (pop agg-stack)
                  start-node-obj (lattr/attr graph curr-agg :node-obj)]
              (if (some? (:agg-node-name start-node-obj))
                (throw
                 (h/ex-info
                  "Only one AggNode can be reached per aggregation context"
                  {:curr-agg curr-agg :other-agg node :path path})))

              (recur
               (annotate-aggs-add-queue next-queue
                                        (lgraph/successors graph node)
                                        path
                                        node
                                        new-agg-stack)
               (-> graph
                   (lattr/add-attr node :agg curr-agg)
                   (lattr/add-attr curr-agg
                                   :node-obj
                                   (assoc start-node-obj :agg-node-name node)))
               next-traversed)))

          :else
          (throw (h/ex-info "Undefined node" {:node node :path path})))
      ))))

(defprotocol AgentGraphInternal
  (internal-add-node! [this name output-nodes-spec node])
  (agent-graph-state [this]))

(defn resolve-agent-graph
  [agent-graph]
  (let [{:keys [nodes start-node update-mode]} (agent-graph-state agent-graph)
        graph     (nodes->graph nodes)
        agg-graph (annotate-aggs graph start-node)]
    (aor-types/->valid-AgentGraph
     (NippyMap.
      (reduce
       (fn [m node]
         (let [output-nodes (lgraph/successors agg-graph node)
               node-obj     (lattr/attr agg-graph node :node-obj)]
           (when (and (instance? NodeAggStart node-obj)
                      (nil? (:agg-node-name node-obj)))
             (throw (h/ex-info "No corresponding agg node"
                               {:start-agg-node node})))
           (assoc m
            node
            (aor-types/->valid-AgentNode
             node-obj
             (set output-nodes)
             (lattr/attr agg-graph node :agg)))
         ))
       {}
       (lgraph/nodes agg-graph)))
     start-node
     update-mode
     (h/random-uuid-str))))


(defmacro reify-AgentGraph
  [& body]
  `(reify
    ~'AgentGraph
    ~@(for [i (range 1 h/MAX-ARITY)]
        (let [name-sym (h/type-hinted String 'name#)
              osym     (h/type-hinted Object 'outputNodesSpec#)
              jfn-sym  (h/type-hinted (h/rama-void-function-class i) 'jfn#)
              node-sym (h/type-hinted AgentGraph 'node)]
          `(~node-sym
            [this# ~name-sym ~osym ~jfn-sym]
            (internal-add-node!
             this#
             ~name-sym
             ~osym
             (aor-types/->Node (h/convert-void-jfn ~jfn-sym)))
           )))
    ~@(for [i (range 1 (inc h/MAX-ARITY))]
        (let [name-sym (h/type-hinted String 'name#)
              osym     (h/type-hinted Object 'outputNodesSpec#)
              jfn-sym  (h/type-hinted (h/rama-function-class i) 'jfn#)
              agg-start-node-sym (h/type-hinted AgentGraph 'aggStartNode)]
          `(~agg-start-node-sym
            [this# ~name-sym ~osym ~jfn-sym]
            (internal-add-node!
             this#
             ~name-sym
             ~osym
             (aor-types/->NodeAggStart (h/convert-jfn ~jfn-sym) nil))
           )))
    ~@body
   ))

(defn- normalize-output-nodes
  [spec]
  (cond (string? spec) [spec]
        (coll? spec) (set spec)
        (instance? java.util.List spec) (set spec)
        (nil? spec) #{}
        :else (throw (h/ex-info "Invalid output nodes spec"
                                {:spec spec :class (class spec)}))))

(defn internal-add-agg-node!
  [this name outputNodesSpec agg afn]
  (if (instance? MultiAgg$Impl agg)
    (let [{:keys [init-fn on-handlers]} (ma/multi-agg-state agg)
          update-fn (fn [state dispatch-name & args]
                      (when-not (contains? on-handlers dispatch-name)
                        (throw (h/ex-info "Invalid dispatch name for MultiAgg"
                                          {:valid-names (keys on-handlers)
                                           :name        dispatch-name})))
                      (apply (get on-handlers dispatch-name) state args))]
      (internal-add-node!
       this
       name
       outputNodesSpec
       (aor-types/->NodeAgg init-fn update-fn afn)))
    (let [agg       (if (instance? BuiltInAgg agg)
                      (.agg ^BuiltInAgg agg)
                      agg)
          init-fn   (ops/agg->init-fn agg)
          update-fn (ops/agg->update-fn agg)]
      (internal-add-node!
       this
       name
       outputNodesSpec
       (aor-types/->NodeAgg init-fn update-fn afn)))))

(defn internal-add-agg-node-java!
  [this name outputNodesSpec agg jfn]
  (internal-add-agg-node!
   this
   name
   outputNodesSpec
   agg
   (h/convert-void-jfn jfn)))

(defn convert-update-mode->clj
  [mode]
  (condp = mode
    UpdateMode/CONTINUE :continue
    UpdateMode/RESTART :restart
    UpdateMode/DROP :drop
    (throw (h/ex-info "Invalid mode" {:mode mode}))))

(defn convert-update-mode->java
  [mode]
  (condp = mode
    :continue UpdateMode/CONTINUE
    :restart UpdateMode/RESTART
    :drop UpdateMode/DROP
    (throw (h/ex-info "Invalid mode" {:mode mode}))))

(defn mk-agent-graph
  []
  (let [nodes-vol      (volatile! {})
        start-node-vol (volatile! nil)
        mode-vol       (volatile! nil)]
    (reify-AgentGraph
      (setUpdateMode
       [this mode]
       (if (nil? @mode-vol)
         (vreset! mode-vol (convert-update-mode->clj mode))
         (throw (h/ex-info "Update mode already set" {:mode @mode-vol})))
       this)
      (^AgentGraph aggNode
       [this ^String name ^Object outputNodesSpec ^RamaAccumulatorAgg agg
        ^RamaVoidFunction3 impl]
       (internal-add-agg-node-java!
        this
        name
        outputNodesSpec
        agg
        impl))
      (^AgentGraph aggNode [this ^String name ^Object outputNodesSpec
                            ^RamaCombinerAgg agg ^RamaVoidFunction3 impl]
        (internal-add-agg-node-java!
         this
         name
         outputNodesSpec
         agg
         impl))
      (^AgentGraph aggNode [this ^String name ^Object outputNodesSpec
                            ^MultiAgg$Impl agg ^RamaVoidFunction3 impl]
        (internal-add-agg-node-java!
         this
         name
         outputNodesSpec
         agg
         impl))
      (^AgentGraph aggNode [this ^String name ^Object outputNodesSpec
                            ^BuiltInAgg agg ^RamaVoidFunction3 impl]
        (internal-add-agg-node-java!
         this
         name
         outputNodesSpec
         agg
         impl))
      AgentGraphInternal
      (internal-add-node!
        [this name output-nodes-spec node-obj]
        (when (or (nil? name) (= "" name))
          (throw (h/ex-info "Node name cannot be nil or empty string"
                            {:name name})))
        (when (contains? @nodes-vol name)
          (throw (h/ex-info "Node already exists" {:name name})))
        (when (nil? @start-node-vol)
          (vreset! start-node-vol name))
        (vswap! nodes-vol
                assoc
                name
                {:node-obj     node-obj
                 :output-nodes (normalize-output-nodes output-nodes-spec)})
        this)
      (agent-graph-state [this]
        {:nodes       @nodes-vol
         :start-node  @start-node-vol
         :update-mode (or @mode-vol :continue)})
    )))

(defn graph->historical-graph-info
  [graph]
  (aor-types/->valid-HistoricalAgentGraphInfo
   (transform
    MAP-VALS
    (fn [{:keys [node output-nodes agg-context]}]
      (aor-types/->valid-HistoricalAgentNodeInfo
       (aor-types/node->type-kw node)
       output-nodes
       agg-context
      ))
    (:node-map graph))
   (:start-node graph)
   (:uuid graph)))
