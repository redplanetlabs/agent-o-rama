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

(rf/reg-sub :invocation/ui
  (fn [db [_ invoke-id]]
    (merge (default-invocation-ui)
           (get-in db [:ui :invocations invoke-id]))))

(rf/reg-sub :invocation/selected-node-id
  :<- [:invocation/ui]
  (fn [ui [_ _invoke-id]]
    (:selected-node-id ui)))

(rf/reg-sub :invocation/forking-mode?
  :<- [:invocation/ui]
  (fn [ui [_ _invoke-id]]
    (:forking-mode? ui)))

(rf/reg-sub :invocation/changed-nodes
  :<- [:invocation/ui]
  (fn [ui [_ _invoke-id]]
    (:changed-nodes ui)))

(rf/reg-sub :invocation/trace-view-mode
  :<- [:invocation/ui]
  (fn [ui [_ _invoke-id]]
    (:trace-view-mode ui)))

(rf/reg-sub :invocation/sidebar-width
  :<- [:invocation/ui]
  (fn [ui [_ _invoke-id]]
    (:sidebar-width ui)))

(rf/reg-sub :invocation/state
  (fn [db [_ invoke-id]]
    (get-in db [:invocations-data invoke-id])))

(rf/reg-sub :invocation/graph-data
  :<- [:invocation/state]
  (fn [state [_ _invoke-id]]
    (get-in state [:graph :nodes])))

(rf/reg-sub :invocation/real-edges
  :<- [:invocation/state]
  (fn [state [_ _invoke-id]]
    (or (get-in state [:graph :edges]) [])))

(rf/reg-sub :invocation/implicit-edges
  :<- [:invocation/state]
  (fn [state [_ _invoke-id]]
    (or (:implicit-edges state) [])))

(rf/reg-sub :invocation/is-complete
  :<- [:invocation/state]
  (fn [state [_ _invoke-id]]
    (boolean (:is-complete state))))

(rf/reg-sub :invocation/root-invoke-id
  :<- [:invocation/state]
  (fn [state [_ _invoke-id]]
    (:root-invoke-id state)))

(rf/reg-sub :invocation/summary
  :<- [:invocation/state]
  (fn [state [_ _invoke-id]]
    (:summary state)))

(rf/reg-sub :invocation/flow-nodes-and-edges
  :<- [:invocation/graph-data]
  :<- [:invocation/real-edges]
  :<- [:invocation/implicit-edges]
  (fn [[graph-data real-edges implicit-edges] [_ _invoke-id]]
    (when (seq graph-data)
      (graph-layout/process-graph-data graph-data real-edges implicit-edges))))

(rf/reg-sub :invocation/affected-nodes
  :<- [:invocation/graph-data]
  :<- [:invocation/changed-nodes]
  :<- [:invocation/forking-mode?]
  (fn [[graph-data changed-nodes forking-mode?] [_ _invoke-id]]
    (when forking-mode?
      (graph-layout/find-downstream-nodes graph-data (set (keys changed-nodes))))))

(rf/reg-sub :invocation/selected-node-data
  :<- [:invocation/graph-data]
  :<- [:invocation/selected-node-id]
  (fn [[graph-data selected-node-id] [_ _invoke-id]]
    (when-let [node-data (and graph-data selected-node-id (get graph-data selected-node-id))]
      (assoc node-data :node-id selected-node-id))))
