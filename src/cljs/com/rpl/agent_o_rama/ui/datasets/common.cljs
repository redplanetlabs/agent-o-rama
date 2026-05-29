(ns com.rpl.agent-o-rama.ui.datasets.common)

(defn pretty-print-json [json-data]
  (try
    (js/JSON.stringify (clj->js json-data) nil 2)
    (catch js/Error _
      (str json-data))))
