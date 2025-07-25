(ns com.rpl.agent-o-rama.langchain4j
  (:use [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:import
   [dev.langchain4j.model.chat
    ChatModel]
   [dev.langchain4j.model.chat.request
    ChatRequest]))

(defn chat
  [^ChatModel model request]
  (cond
    (string? request)
    (.chat model ^String request)

    (list? request)
    (.chat model ^java.util.List request)

    (instance? ChatRequest request)
    (.chat model ^ChatRequest request)

    :else
    (throw (h/ex-info "Unknown request type" {:type (class request)}))))
