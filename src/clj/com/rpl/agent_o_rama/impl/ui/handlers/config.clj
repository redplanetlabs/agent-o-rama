(ns com.rpl.agent-o-rama.impl.ui.handlers.config
  (:require
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common])
  (:use [com.rpl.rama]
        [com.rpl.rama.path]))

(defn- schema-fn->input-type [schema-fn]
  (cond
    (or (= schema-fn aor-types/natural-long?)
        (= schema-fn aor-types/positive-long?)) :number
    (= schema-fn h/boolean-spec) :boolean
    :else :text))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :config/get-all
  [{:keys [client]} uid]
  (let [client-objects (aor-types/underlying-objects client)
        config-pstate (:config-pstate client-objects)
        current-config-map (or (foreign-select-one STAY config-pstate {:pkey 0}) {})]
    (for [[key config-def] aor-types/ALL-CONFIGS]
      (let [current-value (get current-config-map key (:default config-def))]
        {:key key
         :doc (:doc config-def)
         :current-value (str current-value)
         :default-value (str (:default config-def))
         :input-type (schema-fn->input-type (:schema-fn config-def))}))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :config/set
  [{:keys [client key value]} uid]
  (let [client-objects (aor-types/underlying-objects client)
        agent-config-depot (:agent-config-depot client-objects)
        config-def (get aor-types/ALL-CONFIGS key)]
    (when-not config-def
      (throw (ex-info "Unknown configuration key" {:key key})))
    (try
      (let [parsed-value (case (schema-fn->input-type (:schema-fn config-def))
                           :number (Long/parseLong value)
                           value)
            change-fn (:change-fn config-def)
            change-record (change-fn parsed-value)]
        (foreign-append! agent-config-depot change-record)
        {:success true})
      (catch Exception e
        (throw (ex-info (str "Failed to set config: " (.getMessage e)) {:key key :value value}))))))

;; Handler to get all global configs
(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :config/get-all-global
  [{:keys [manager]} uid]
  (let [manager-objects (aor-types/underlying-objects manager)
        ;; Use the global config PState
        config-pstate (:global-config-pstate manager-objects)
        current-config-map (or (foreign-select-one STAY config-pstate {:pkey 0}) {})]
    ;; Read from ALL-GLOBAL-CONFIGS instead of ALL-CONFIGS
    (for [[key config-def] aor-types/ALL-GLOBAL-CONFIGS]
      (let [current-value (get current-config-map key (:default config-def))]
        {:key key
         :doc (:doc config-def)
         :current-value (str current-value)
         :default-value (str (:default config-def))
         :input-type (schema-fn->input-type (:schema-fn config-def))}))))

;; Handler to set a global config
(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :config/set-global
  [{:keys [manager key value]} uid]
  (let [manager-objects (aor-types/underlying-objects manager)
        ;; Use the global actions depot to send the config change
        global-actions-depot (:global-actions-depot manager-objects)
        config-def (get aor-types/ALL-GLOBAL-CONFIGS key)]
    (when-not config-def
      (throw (ex-info "Unknown global configuration key" {:key key})))
    (try
      (let [parsed-value (case (schema-fn->input-type (:schema-fn config-def))
                           :number (Long/parseLong value)
                           value)
            change-fn (:change-fn config-def)
            change-record (change-fn parsed-value)]
        (foreign-append! global-actions-depot change-record)
        {:success true})
      (catch Exception e
        (throw (ex-info (str "Failed to set global config: " (.getMessage e)) {:key key :value value}))))))
