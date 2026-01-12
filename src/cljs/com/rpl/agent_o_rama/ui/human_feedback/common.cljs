(ns com.rpl.agent-o-rama.ui.human-feedback.common)

;; =============================================================================
;; LocalStorage utilities for reviewer name
;; =============================================================================

(def ^:private reviewer-name-storage-key "aor-reviewer-name")

(defn get-reviewer-name
  "Retrieves the saved reviewer name from localStorage, or empty string if not set."
  []
  (or (.getItem js/localStorage reviewer-name-storage-key) ""))

(defn save-reviewer-name!
  "Saves the reviewer name to localStorage for future use."
  [name]
  (.setItem js/localStorage reviewer-name-storage-key name))
