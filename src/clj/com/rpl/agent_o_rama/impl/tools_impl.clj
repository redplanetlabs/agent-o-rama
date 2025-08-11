(ns com.rpl.agent-o-rama.impl.tools-impl
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops]
   [jsonista.core :as j])
  (:import
   [com.rpl.agentorama
    InvalidToolNameException]
   [dev.langchain4j.agent.tool
    ToolExecutionRequest
    ToolSpecification]
   [dev.langchain4j.data.message
    ToolExecutionResultMessage]))

;; TODO: <<<<>>>> use error templates
; INVALID_TOOL_NAME_ERROR_TEMPLATE = (
;     "Error: {requested_tool} is not a valid tool, try one of [{available_tools}]."
; )
; TOOL_CALL_ERROR_TEMPLATE = "Error: {error}\n Please fix your mistakes."

(def MAPPER (j/object-mapper {:decode-key-fn str}))

(defn- mk-tools-by-name
  [tools]
  (let [tools-by-name (group-by #(.name ^ToolSpecification
                                        (:tool-specification %))
                                tools)
        invalid       (select [ALL
                               (selected? LAST (view count) (pred> 1))
                               FIRST]
                              tools-by-name)]
    (when-not (empty? invalid)
      (throw (h/ex-info "Cannot have multiple tools with the same name"
                        {:conflicting-names invalid})))
    (transform MAP-VALS first tools-by-name)))

(defn mk-tool-fn
  [tools error-handler]
  (let [tools-by-name (mk-tools-by-name tools)]
    (fn [agent-node ^ToolExecutionRequest request caller-data]
      (let [start-time-millis (h/current-time-millis)
            tool-name (.name request)
            args      (-> request
                          .arguments
                          (j/read-value MAPPER))
            base-info {"id"   (.id request)
                       "name" tool-name
                       "args" args}]
        (try
          (if-let [{:keys [tool-fn include-context?]}
                   (get tools-by-name tool-name)]
            (let [ret (if include-context?
                        (tool-fn agent-node caller-data args)
                        (tool-fn args))]
              (anode/record-nested-op!-impl agent-node
                                            :tool-call
                                            start-time-millis
                                            (h/current-time-millis)
                                            (merge base-info
                                                   {"type"   "success"
                                                    "result" ret}))
              (i/emit! agent-node
                       "agg-results"
                       (ToolExecutionResultMessage/from request (str ret))))

            (do
              (anode/record-nested-op!-impl agent-node
                                            :tool-call
                                            start-time-millis
                                            (h/current-time-millis)
                                            (assoc base-info "type" "invalid"))
              (throw (InvalidToolNameException. (str "Invalid tool name: "
                                                     request)))))
          (catch Throwable t
            (try
              (let [error-ret (error-handler t)]
                (i/emit! agent-node
                         "agg-results"
                         (ToolExecutionResultMessage/from request error-ret))
                (anode/record-nested-op!-impl
                 agent-node
                 :tool-call
                 start-time-millis
                 (h/current-time-millis)
                 (merge base-info
                        {"type"      "failure"
                         "exception" (h/throwable->str t)
                         "result"    error-ret})))
              (catch Throwable t2
                (anode/record-nested-op!-impl
                 agent-node
                 :tool-call
                 start-time-millis
                 (h/current-time-millis)
                 (merge base-info
                        (if (identical? t t2)
                          {"type"      "throw"
                           "exception" (h/throwable->str t)}
                          {"type"       "throw"
                           "exception1" (h/throwable->str t)
                           "exception2" (h/throwable->str t2)})))
                (throw t2)
              ))
          ))
      ))))
