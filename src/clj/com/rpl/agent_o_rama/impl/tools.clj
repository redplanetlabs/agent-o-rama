(ns com.rpl.agent-o-rama.impl.tools
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
;;  - what about specifying tools dynamically based on the request?
;;      - that's just on the request – the tool agent doesn't change
(defn mk-tool-fn
  [tools error-handler]
  ;; TODO: <<<<>>>> how to specify tools?
  ;;  - make map here from tool name -> tool
  ;;    - need own type for this
  ;;    - could be [(tool-info aspec afn {:include-context? true}) (tool-info
  ;;    aspec afn])
  ;;      - and could add to lc4j ChatRequest builder an option to take in tools
  ;;      of this form
  ;;        - or even better, just make it only accept the tool infos!
  ;;          - just call it :tools
  ;;      - what should option be? there's two things, and context is name of
  ;;      what gets passed by parent
  ;;        - call it :include-context?, and call the argument "caller-data"
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
