(ns com.rpl.agent-o-rama.ui.agents)

(defn index [{:keys [parameters]}]
  {:status
   200
   
   :body
   [{:module-id "ModuleA" :agent-id "research"}
    {:module-id "ModuleA" :agent-id "support"}
    {:module-id "ModuleB" :agent-id "research"}]})

(defn get [{{:keys [module-id agent-id]} :path-params}]
  {:status
   200
   
   :body
   [{:information "about " :agent agent-id :module module-id}]})
