(ns com.rpl.agent-o-rama.tools
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.clojure :as c]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.tools-impl :as tools-impl]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs])
  (:import
   [dev.langchain4j.agent.tool
    ToolSpecification]))

(defn tool-specification
  ([name parameters-json-schema]
   (tool-specification name parameters-json-schema nil))
  ([name parameters-json-schema description]
   (-> (ToolSpecification/builder)
       (.name name)
       (.parameters parameters-json-schema)
       (.description description)
       .build)))

(defn tool-info
  ([tool-specification tool-fn]
   (tool-info tool-specification tool-fn nil))
  ([tool-specification tool-fn options]
   (let [options (merge {:include-context? false} options)]
     (h/validate-options! tool-specification
                          options
                          {:include-context? h/boolean-spec})
     (when-not (ifn? tool-fn)
       (throw (h/ex-info "Invalid tool function" {:type (class tool-fn)})))
     (when-not (instance? ToolSpecification tool-specification)
       (throw (h/ex-info "Invalid tool specification"
                         {:type (class tool-specification)})))
     (aor-types/->ToolInfoImpl tool-specification
                               tool-fn
                               (:include-context? options))
   )))

(defn error-handler-static-string [s]
  (constantly s))

(defn error-handler-rethrow []
  (fn [e] (throw e)))

(defn error-handler-default
  []
  (fn [e]
    (tools-impl/tool-error-string (h/throwable->str e))))

(defn error-handler-by-type
  [tuples]
  (fn [e]
    (if-let [ret (reduce
                  (fn [_ [ex-type afn]]
                    (when (instance? ex-type e)
                      (reduced (afn e))))
                  nil
                  tuples)]
      ret
      (throw e)
    )))

(defn error-handler-static-string-by-type
  [tuples]
  (let [tuples (transform [(view vec) ALL LAST] (fn [s] (constantly s)) tuples)]
    (error-handler-by-type tuples)))

(defn hook:new-tools-agent-options [name options])

(defn new-tools-agent
  ([topology name tools]
   (new-tools-agent topology name tools nil))
  ([topology name tools options]
   (hook:new-tools-agent-options name options)
   (let [options (merge {:error-handler (error-handler-default)}
                        options)]
     (h/validate-options! name
                          options
                          {:error-handler h/fn-spec})
     (-> topology
         (c/new-agent name)
         (c/agg-start-node
          "begin"
          "tool"
          (fn begin
            ([agent-node requests]
             (begin agent-node requests nil))
            ([agent-node requests caller-data]
             (doseq [r requests]
               (c/emit! agent-node "tool" r caller-data)))))
         (c/node
          "tool"
          "agg-results"
          (tools-impl/mk-tool-fn tools (:error-handler options)))
         (c/agg-node
          "agg-results"
          nil
          aggs/+vec-agg
          (fn [agent-node agg-state _]
            (c/result! agent-node agg-state)))
     ))))
