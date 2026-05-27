(ns com.rpl.agent-o-rama.ui.invocations.subs
  (:require
   [re-frame.core :as rf]
   [com.rpl.agent-o-rama.ui.invocations.graph-layout :as graph-layout]))

(defn invocation-ui-path
  [invoke-id & path]
  (into [:ui :invocations invoke-id] path))

(defn- default-invocation-ui []
  {:trace-view-mode "graph"
   :sidebar-width 320
   :selected-node-id nil
   :forking-mode? false
   :changed-nodes {}})

(defn- invoke-id [query-vec]
  (second query-vec))

(defn- sub-invocation-ui [query-vec _db]
  (rf/subscribe [:invocation/ui (invoke-id query-vec)]))

(defn- sub-invocation-state [query-vec _db]
  (rf/subscribe [:invocation/state (invoke-id query-vec)]))

(rf/reg-sub :invocation/ui
  (fn [db [_ invoke-id]]
    (merge (default-invocation-ui)
           (get-in db [:ui :invocations invoke-id]))))

(rf/reg-sub :invocation/selected-node-id
  sub-invocation-ui
  (fn [ui _]
    (:selected-node-id ui)))

(rf/reg-sub :invocation/forking-mode?
  sub-invocation-ui
  (fn [ui _]
    (:forking-mode? ui)))

(rf/reg-sub :invocation/changed-nodes
  sub-invocation-ui
  (fn [ui _]
    (:changed-nodes ui)))

(rf/reg-sub :invocation/trace-view-mode
  sub-invocation-ui
  (fn [ui _]
    (:trace-view-mode ui)))

(rf/reg-sub :invocation/sidebar-width
  sub-invocation-ui
  (fn [ui _]
    (:sidebar-width ui)))

(rf/reg-sub :invocation/state
  (fn [db [_ invoke-id]]
    (get-in db [:invocations-data (str invoke-id)])))

(rf/reg-sub :invocation/graph-data
  sub-invocation-state
  (fn [state _]
    (get-in state [:graph :nodes])))

(rf/reg-sub :invocation/real-edges
  sub-invocation-state
  (fn [state _]
    (or (get-in state [:graph :edges]) [])))

(rf/reg-sub :invocation/implicit-edges
  sub-invocation-state
  (fn [state _]
    (or (:implicit-edges state) [])))

(rf/reg-sub :invocation/is-complete
  sub-invocation-state
  (fn [state _]
    (boolean (:is-complete state))))

(rf/reg-sub :invocation/root-invoke-id
  sub-invocation-state
  (fn [state _]
    (:root-invoke-id state)))

(rf/reg-sub :invocation/summary
  sub-invocation-state
  (fn [state _]
    (:summary state)))

(rf/reg-sub :invocation/flow-nodes-and-edges
  (fn [query-vec _db]
    (let [id (invoke-id query-vec)]
      [(rf/subscribe [:invocation/graph-data id])
       (rf/subscribe [:invocation/real-edges id])
       (rf/subscribe [:invocation/implicit-edges id])]))
  (fn [[graph-data real-edges implicit-edges] _]
    (when (seq graph-data)
      (graph-layout/process-graph-data graph-data real-edges implicit-edges))))

(rf/reg-sub :invocation/affected-nodes
  (fn [query-vec _db]
    (let [id (invoke-id query-vec)]
      [(rf/subscribe [:invocation/graph-data id])
       (rf/subscribe [:invocation/changed-nodes id])
       (rf/subscribe [:invocation/forking-mode? id])]))
  (fn [[graph-data changed-nodes forking-mode?] _]
    (when forking-mode?
      (graph-layout/find-downstream-nodes graph-data (set (keys changed-nodes))))))

(rf/reg-sub :invocation/selected-node-data
  (fn [query-vec _db]
    (let [id (invoke-id query-vec)]
      [(rf/subscribe [:invocation/graph-data id])
       (rf/subscribe [:invocation/selected-node-id id])]))
  (fn [[graph-data selected-node-id] _]
    (when-let [node-data (and graph-data selected-node-id (get graph-data selected-node-id))]
      (assoc node-data :node-id selected-node-id))))
