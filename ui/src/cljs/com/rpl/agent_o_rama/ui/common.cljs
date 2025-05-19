(ns com.rpl.agent-o-rama.ui.common
  (:require
   [com.rpl.agent-o-rama.ui.query :as query]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]))

(defn pp [x] (with-out-str (cljs.pprint/pprint x)))
