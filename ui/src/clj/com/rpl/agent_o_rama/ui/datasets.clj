(ns com.rpl.agent-o-rama.ui.datasets
  (:require [com.rpl.specter :as s]))

(defn index [{:keys [parameters]}]
  {:status
   200
   
   :body
   [{:module-id "ModuleA" :agent-id "research"}
    {:module-id "ModuleA" :agent-id "support"}
    {:module-id "ModuleB" :agent-id "research"}]})
