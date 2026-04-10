(ns com.rpl.agent-o-rama.impl.ui.rpc.config
  (:require
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common])
  (:use [com.rpl.rama]
        [com.rpl.rama.path]))

(defn- get-client [system module-id agent-name]
  (get-in system [:aor-cache module-id :clients (common/url-decode agent-name)]))

(defn- get-manager [system module-id]
  (get-in system [:aor-cache module-id :manager]))

(defn- schema-fn->input-type [schema-fn]
  (cond
    (or (= schema-fn aor-types/natural-long?)
        (= schema-fn aor-types/positive-long?)) :number
    (= schema-fn h/boolean-spec) :boolean
    :else :text))

(defn get-all!!
  [system {:keys [module-id agent-name]}]
  (let [client (get-client system module-id agent-name)
        client-objects (aor-types/underlying-objects client)
        config-pstate (:config-pstate client-objects)
        current-config-map (or (foreign-select-one STAY config-pstate {:pkey 0}) {})]
    (for [[key config-def] aor-types/ALL-CONFIGS]
      (let [current-value (get current-config-map key (:default config-def))]
        {:key key
         :doc (:doc config-def)
         :current-value (str current-value)
         :default-value (str (:default config-def))
         :input-type (schema-fn->input-type (:schema-fn config-def))}))))

(defn get-all-global!!
  [system {:keys [module-id]}]
  (let [manager (get-manager system module-id)
        manager-objects (aor-types/underlying-objects manager)
        config-pstate (:global-config-pstate manager-objects)
        current-config-map (or (foreign-select-one STAY config-pstate {:pkey 0}) {})]
    (for [[key config-def] aor-types/ALL-GLOBAL-CONFIGS]
      (let [current-value (get current-config-map key (:default config-def))]
        {:key key
         :doc (:doc config-def)
         :current-value (str current-value)
         :default-value (str (:default config-def))
         :input-type (schema-fn->input-type (:schema-fn config-def))}))))

;; =============================================================================
;; MUTATIONS
;; =============================================================================

(defn set!!
  [system {:keys [module-id agent-name key value]}]
  (let [client (get-client system module-id agent-name)
        client-objects (aor-types/underlying-objects client)
        agent-config-depot (:agent-config-depot client-objects)
        config-def (get aor-types/ALL-CONFIGS key)]
    (when-not config-def
      (throw (ex-info "Unknown configuration key" {:key key})))
    (let [parsed-value (case (schema-fn->input-type (:schema-fn config-def))
                         :number (Long/parseLong value)
                         value)
          change-fn (:change-fn config-def)
          change-record (change-fn parsed-value)]
      (foreign-append! agent-config-depot change-record)
      {:success true})))

(defn set-global!!
  [system {:keys [module-id key value]}]
  (let [manager (get-manager system module-id)
        manager-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot manager-objects)
        config-def (get aor-types/ALL-GLOBAL-CONFIGS key)]
    (when-not config-def
      (throw (ex-info "Unknown global configuration key" {:key key})))
    (let [parsed-value (case (schema-fn->input-type (:schema-fn config-def))
                         :number (Long/parseLong value)
                         value)
          change-fn (:change-fn config-def)
          change-record (change-fn parsed-value)]
      (foreign-append! global-actions-depot change-record)
      {:success true})))
