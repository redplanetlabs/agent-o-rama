(ns com.rpl.agent-o-rama.impl.tools
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops]))


;; TODO: <<<<>>>>
;;  - how to get this to work with annotation driven tools
;;  - handle hallucinated tool (error with error handler)
;;  - what about specifying tools dynamically based on the request?
;;      - that's just on the request – the tool agent doesn't change
(defn mk-tool-fn
  [tools error-handler]
  ;; TODO: <<<<>>>> how to specify tools?
  ;;  - make map here from tool name -> tool
  ;;    - include ToolSpecification...
  ;;    - need own type for this
  (fn [agent-node request context]
      ;; TODO: <<<<>>>>
      ;;  - request is ToolExecutionRequest
      ;;  - this should produce ToolExecutionResultMessage
      ;;  - capture :tool-call nested op
      ;;    - or should that be captured by caller of the tools agent?
      ;;      - in which case, sub-agent would pass back more info...
      ;;  - tool is given agent-node and context arguments followed by tool args
      ;;    - what if you just want to provide a simple function as a tool?
      ;;     -  could have helper (simple-function-tool afn)
  ))
