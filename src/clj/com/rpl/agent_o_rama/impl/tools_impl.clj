(ns com.rpl.agent-o-rama.impl.tools-impl
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [dev.langchain4j.agent.tool
    ToolExecutionRequest]))


;; TODO: <<<<>>>>
;;  - how to get this to work with annotation driven tools
;;  - handle hallucinated tool (error with error handler)
(defn mk-tool-fn
  [tools error-handler]
  ;; TODO: <<<<>>>> make map here from tool name -> tool
  (fn [agent-node ^ToolExecutionRequest request caller-data]
      ;; TODO: <<<<>>>>
      ;;  - this should produce ToolExecutionResultMessage
      ;;  - capture :tool-call nested op
      ;;    - or should that be captured by caller of the tools agent?
      ;;      - in which case, sub-agent would pass back more info...
      ;;        - could be special type handled by AOR that's merged into the
      ;;        info map for results
      ;;  - tool is given agent-node and context arguments followed by tool args
      ;;    - what if you just want to provide a simple function as a tool?
      ;;     - could have helper (simple-function-tool afn)
      ;;       - no, it should be the opposite. a declaration for a function
      ;;       that wants the context
      ;;    - or, could make agent-node and context thread locals
  ))
