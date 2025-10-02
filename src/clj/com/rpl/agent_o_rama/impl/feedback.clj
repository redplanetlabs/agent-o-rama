(ns com.rpl.agent-o-rama.impl.feedback
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops]))


(defn add-feedback-path
  ^:direct-nav [scores source]
  (let [t (h/current-time-millis)
        v (aor-types/->valid-FeedbackImpl scores source t t)]
    (path :feedback :results NIL->VECTOR AFTER-ELEM (termval v))))

(defn action-state-path
  ^:direct-nav [rule-name]
  (path :feedback :actions (keypath rule-name)))

(defn set-action-state-path
  ^:direct-nav [rule-name val]
  (path (action-state-path rule-name) (termval val)))
