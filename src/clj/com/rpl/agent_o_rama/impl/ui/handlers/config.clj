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
