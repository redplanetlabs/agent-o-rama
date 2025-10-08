(ns com.rpl.agent-o-rama.impl.java
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:import
   [com.rpl.agentorama
    ActionBuilderOptions$Impl
    AgentContext$Impl
    EvaluatorBuilderOptions$Impl
    ToolsAgentOptions$Impl
    ToolsAgentOptions$FunctionHandler
    ToolsAgentOptions$StaticStringHandler]))

(defn mk-tools-agent-options
  []
  (let [options (volatile! {})]
    (reify
     ToolsAgentOptions$Impl
     (errorHandlerDefault [this]
       (vswap! options assoc :error-handler (tools/error-handler-default))
       this)
     (errorHandlerStaticString [this message]
       (vswap! options
               assoc
               :error-handler
               (tools/error-handler-static-string message))
       this)
     (errorHandlerRethrow [this]
       (vswap! options assoc :error-handler (tools/error-handler-rethrow))
       this)
     (errorHandlerStaticStringByType [this handlers]
       (let [tuples (mapv (fn [^ToolsAgentOptions$StaticStringHandler h]
                            [(.type h) (.message h)])
                          handlers)]
         (vswap! options
                 assoc
                 :error-handler
                 (tools/error-handler-static-string-by-type tuples))
         this))
     (errorHandlerByType [this handlers]
       (let [tuples (mapv (fn [^ToolsAgentOptions$FunctionHandler h]
                            [(.type h) (h/convert-jfn (.function h))])
                          handlers)]
         (vswap! options
                 assoc
                 :error-handler
                 (tools/error-handler-by-type tuples))
         this))

     clojure.lang.IDeref
     (deref [this]
       @options
     ))))

(defn create-tool-info
  [tool-spec jfn]
  (tools/tool-info tool-spec (h/convert-jfn jfn)))

(defn create-tool-info-with-context
  [tool-spec jfn]
  (tools/tool-info tool-spec (h/convert-jfn jfn) {:include-context? true}))


(defn mk-evaluator-builder-options
  []
  (let [options (volatile! {})]
    (reify
     EvaluatorBuilderOptions$Impl
     (param [this name description]
       (.param this name description ""))
     (param [this name description defaultValue]
       (when (contains? (:params @options) name)
         (throw (h/ex-info "Param already declared" {:name name})))
       (setval [h/VOLATILE :params (keypath name)]
               {:description description :default defaultValue}
               options)
       this)
     (withoutInputPath [this]
       (vswap! options assoc :input-path? false)
       this)
     (withoutOutputPath [this]
       (vswap! options assoc :output-path? false)
       this)
     (withoutReferenceOutputPath [this]
       (vswap! options assoc :reference-output-path? false)
       this)
     clojure.lang.IDeref
     (deref [this]
       @options
     ))))

(defn mk-action-builder-options
  []
  (let [options (volatile! {})]
    (reify
     ActionBuilderOptions$Impl
     (param [this name description]
       (.param this name description ""))
     (param [this name description defaultValue]
       (when (contains? (:params @options) name)
         (throw (h/ex-info "Param already declared" {:name name})))
       (setval [h/VOLATILE :params (keypath name)]
               {:description description :default defaultValue}
               options)
       this)
     (limitConcurrency [this]
       (setval [h/VOLATILE :limit-concurrency?] true options)
       this)
     clojure.lang.IDeref
     (deref [this]
       @options
     ))))


(defn mk-agent-context
  []
  (let [metadata (volatile! {})]
    (reify
     AgentContext$Impl
     (^AgentContext$Impl metadata [this ^String name ^int val]
       (vswap! metadata assoc name (long val))
       this)
     (^AgentContext$Impl metadata [this ^String name ^long val]
       (vswap! metadata assoc name val)
       this)
     (^AgentContext$Impl metadata [this ^String name ^float val]
       (vswap! metadata assoc name (double val))
       this)
     (^AgentContext$Impl metadata [this ^String name ^double val]
       (vswap! metadata assoc name val)
       this)
     (^AgentContext$Impl metadata [this ^String name ^boolean val]
       (vswap! metadata assoc name val)
       this)
     (^AgentContext$Impl metadata [this ^String name ^String val]
       (vswap! metadata assoc name val)
       this)
     clojure.lang.IDeref
     (deref [this]
       {:metadata @metadata}
     ))))
