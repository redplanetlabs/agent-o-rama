(ns com.rpl.agent-o-rama.ui.experiments.download
  "Browser downloads for experiment JSON exports (ui.handlers.http)."
  (:require
   [uix.core :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [ArrowDownTrayIcon]]))

(defn export-path
  "Path only (no origin). When experiment-id is set, exports that run only."
  [module-id dataset-id experiment-id include-trace?]
  (let [mid (js/encodeURIComponent module-id)
        did (js/encodeURIComponent (str dataset-id))
        epath (if experiment-id
                (str "/experiments/" (js/encodeURIComponent (str experiment-id)))
                "/experiments")
        q (if include-trace? "?trace=true" "?trace=false")]
    (str "/api/datasets/" mid "/" did epath "/export" q)))

(defn trigger-export-download!
  "Triggers a file download via GET; session cookie is sent for same-origin requests."
  [{:keys [module-id dataset-id experiment-id include-trace?]}]
  (when (and module-id dataset-id (some? include-trace?))
    (let [path (export-path module-id dataset-id experiment-id include-trace?)
          url (str (.-origin js/location) path)
          a (.createElement js/document "a")]
      (set! (.-href a) url)
      (set! (.-download a) "experiments-export.json")
      (.appendChild (.-body js/document) a)
      (.click a)
      (.remove a))))

(def ^:private btn-class
  "inline-flex items-center px-3 py-2 text-sm font-medium rounded-md border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 cursor-pointer shadow-sm")

(defui ExportToolbar [{:keys [module-id dataset-id experiment-id]}]
  (when (and module-id dataset-id)
    ($ :div.flex.flex-wrap.items-center.gap-2
       ($ :button
          {:type "button"
           :className btn-class
           :title "Download experiment results as JSON (no evaluator invoke traces)."
           :onClick #(trigger-export-download!
                      {:module-id module-id
                       :dataset-id dataset-id
                       :experiment-id experiment-id
                       :include-trace? false})}
          ($ ArrowDownTrayIcon {:className "h-4 w-4 mr-1 shrink-0"})
          "Results JSON")
       ($ :button
          {:type "button"
           :className btn-class
           :title "Include per-run evaluator traces from the same query topology as the invocation graph."
           :onClick #(trigger-export-download!
                      {:module-id module-id
                       :dataset-id dataset-id
                       :experiment-id experiment-id
                       :include-trace? true})}
          ($ ArrowDownTrayIcon {:className "h-4 w-4 mr-1 shrink-0"})
          "Traces JSON"))))
