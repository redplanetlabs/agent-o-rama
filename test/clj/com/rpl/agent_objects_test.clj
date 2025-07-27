(ns com.rpl.agent-objects-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [meander.epsilon :as m])
  (:import
   [dev.langchain4j.data.message
    AiMessage
    UserMessage]
   [dev.langchain4j.model.chat.response
    ChatResponse$Builder]
   [dev.langchain4j.model.chat
    ChatModel
    StreamingChatModel]
   [dev.langchain4j.model.chat.request
    ChatRequest]
   [dev.langchain4j.model.chat.response
    ChatResponse
    StreamingChatResponseHandler]
   [dev.langchain4j.model.output
    FinishReason
    TokenUsage]
   [dev.langchain4j.store.embedding
    EmbeddingStore]
   [java.util
    IdentityHashMap]))

(def SEMS)
(def BUILDS-ATOM)
(def ACQUIRED-ATOM)
(def FAILS-ATOM)

(defn inc-build!
  [^String k]
  (transform [ATOM (keypath k) (nil->val 0)] inc BUILDS-ATOM)
  (String. k))

(defn every-identical?
  [objs]
  (every? #(identical? (first %) (second %)) (partition 2 1 objs)))

(defn unique-objects-count
  [objs]
  (let [m (IdentityHashMap.)]
    (doseq [o objs]
      (.put m o nil))
    (count m)))

(deftest object-lifecycle-test
  (let [plain-atom (atom {})]
    (with-redefs [SEMS          {"reg1" (h/mk-semaphore 0)
                                 "reg2" (h/mk-semaphore 0)
                                 "obj1" (h/mk-semaphore 0)
                                 "obj2" (h/mk-semaphore 0)
                                 "obj3" (h/mk-semaphore 0)
                                 "obj4" (h/mk-semaphore 0)}
                  BUILDS-ATOM   (atom {})
                  ACQUIRED-ATOM (atom {})
                  FAILS-ATOM    (atom [])
                  i/hook:building-plain-agent-object
                  (fn [name o]
                    (transform [ATOM (keypath name) (nil->val 0)]
                               inc
                               plain-atom))]
      (with-open [ipc (rtest/create-ipc)]
        (letlocals
         (bind module
           (aor/agentmodule
            [topology]
            (aor/declare-agent-object topology "reg1" 1)
            (aor/declare-agent-object topology "reg2" "abcde")
            (aor/declare-agent-object-builder
             topology
             "obj1"
             (fn [setup] (inc-build! "obj1"))
             {:worker-object-limit 3})
            (aor/declare-agent-object-builder
             topology
             "obj2"
             (fn [setup] (inc-build! "obj2"))
             {:worker-object-limit 4})
            (aor/declare-agent-object-builder
             topology
             "obj3"
             (fn [setup] (inc-build! "obj3"))
             {:thread-safe? true})
            (aor/declare-agent-object-builder
             topology
             "obj4"
             (fn [setup] (inc-build! "obj4"))
             {:thread-safe? true})
            (->
              topology
              (aor/new-agent "foo")
              (aor/node
               "start"
               nil
               (fn [agent-node n]
                 (try
                   (let [o (aor/get-agent-object agent-node n)]
                     (setval [ATOM (keypath n) (nil->val []) AFTER-ELEM]
                             o
                             ACQUIRED-ATOM)
                     (h/acquire-semaphore (get SEMS n) 1))
                   (catch Exception e
                     (swap! FAILS-ATOM conj e)))
                 (aor/result! agent-node "done")

               )))))
         (rtest/launch-module! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind config-depot
           (foreign-depot ipc module-name (po/agent-config-depot-name "foo")))

         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind foo (aor/agent-client agent-manager "foo"))

         (dotimes [_ 10]
           (aor/agent-initiate foo "reg1"))
         (is (condition-attained? (= 10 (count (get @ACQUIRED-ATOM "reg1")))))
         (is (every-identical? (get @ACQUIRED-ATOM "reg1")))
         (is (= 1 (select-any ["reg1" FIRST] @ACQUIRED-ATOM)))
         (is (= {"reg1" 1} @plain-atom))
         (is (= {} @BUILDS-ATOM))

         (dotimes [_ 9]
           (aor/agent-initiate foo "reg2"))
         (is (condition-attained? (= 9 (count (get @ACQUIRED-ATOM "reg2")))))
         (is (every-identical? (get @ACQUIRED-ATOM "reg2")))
         (is (= "abcde" (select-any ["reg2" FIRST] @ACQUIRED-ATOM)))
         (is (= {"reg1" 1 "reg2" 1} @plain-atom))
         (is (= {} @BUILDS-ATOM))

         (dotimes [_ 8]
           (aor/agent-initiate foo "obj3"))
         (is (condition-attained? (= 8 (count (get @ACQUIRED-ATOM "obj3")))))
         (is (every-identical? (get @ACQUIRED-ATOM "obj3")))
         (is (= "obj3" (select-any ["obj3" FIRST] @ACQUIRED-ATOM)))
         (is (= {"reg1" 1 "reg2" 1} @plain-atom))
         (is (= {"obj3" 1} @BUILDS-ATOM))

         (reset! BUILDS-ATOM {})
         (dotimes [_ 5]
           (aor/agent-initiate foo "obj4"))
         (is (condition-attained? (= 5 (count (get @ACQUIRED-ATOM "obj4")))))
         (is (every-identical? (get @ACQUIRED-ATOM "obj4")))
         (is (= "obj4" (select-any ["obj4" FIRST] @ACQUIRED-ATOM)))
         (is (= {"reg1" 1 "reg2" 1} @plain-atom))
         (is (= {"obj4" 1} @BUILDS-ATOM))

         (reset! BUILDS-ATOM {})
         (dotimes [_ 5]
           (aor/agent-initiate foo "obj1"))
         (is (condition-stable? (= 3 (count (get @ACQUIRED-ATOM "obj1")))))
         (is (= 3 (unique-objects-count (get @ACQUIRED-ATOM "obj1"))))
         (is (= {"obj1" 3} @BUILDS-ATOM))

         (h/release-semaphore (get SEMS "obj1"))
         (is (condition-stable? (= 4 (count (get @ACQUIRED-ATOM "obj1")))))
         (is (= 3 (unique-objects-count (get @ACQUIRED-ATOM "obj1"))))
         (is (= {"obj1" 3} @BUILDS-ATOM))

         (h/release-semaphore (get SEMS "obj1"))
         (is (condition-stable? (= 5 (count (get @ACQUIRED-ATOM "obj1")))))
         (is (= 3 (unique-objects-count (get @ACQUIRED-ATOM "obj1"))))
         (is (= {"obj1" 3} @BUILDS-ATOM))


         (reset! BUILDS-ATOM {})
         (dotimes [_ 6]
           (aor/agent-initiate foo "obj2"))
         (is (condition-stable? (= 4 (count (get @ACQUIRED-ATOM "obj2")))))
         (is (= 4 (unique-objects-count (get @ACQUIRED-ATOM "obj2"))))
         (is (= {"obj2" 4} @BUILDS-ATOM))

         (h/release-semaphore (get SEMS "obj2"))
         (is (condition-stable? (= 5 (count (get @ACQUIRED-ATOM "obj2")))))
         (is (= 4 (unique-objects-count (get @ACQUIRED-ATOM "obj2"))))
         (is (= {"obj2" 4} @BUILDS-ATOM))

         (h/release-semaphore (get SEMS "obj2"))
         (is (condition-stable? (= 6 (count (get @ACQUIRED-ATOM "obj2")))))
         (is (= 4 (unique-objects-count (get @ACQUIRED-ATOM "obj2"))))
         (is (= {"obj2" 4} @BUILDS-ATOM))

         (foreign-append! config-depot
                          (aor-types/change-acquire-object-timeout-millis 10))

         (aor/agent-initiate foo "obj2")
         (is (condition-stable? (= 1 (count @FAILS-ATOM))))
         (is (str/starts-with? (.getMessage ^Exception (first @FAILS-ATOM))
                               "Could not acquire object."))
        )))))

(defrecord MockChatModel1 []
  ChatModel
  (doChat [this request]
    (let [^UserMessage m (-> request
                             .messages
                             last)]
      (-> (ChatResponse$Builder.)
          (.aiMessage (AiMessage. (str (.singleText m) "!!!")))
          (.finishReason FinishReason/STOP)
          (.modelName "aor-model")
          (.tokenUsage (TokenUsage. (int 10) (int 20)))
          .build))))

(defrecord MockChatModel2 []
  ChatModel
  (^ChatResponse chat [this ^ChatRequest request]
    (let [^UserMessage m (-> request
                             .messages
                             last)]
      (-> (ChatResponse$Builder.)
          (.aiMessage (AiMessage. (str (.singleText m) "!?")))
          (.finishReason FinishReason/OTHER)
          (.modelName "aor-model2")
          (.tokenUsage (TokenUsage. (int 15) (int 40)))
          .build))))


(defrecord MockStreamingChatModel1 []
  StreamingChatModel
  (doChat [this request handler]
    (let [^UserMessage m (-> request
                             .messages
                             last)
          s        (.singleText m)
          response (-> (ChatResponse$Builder.)
                       (.aiMessage (AiMessage. (str "You said " s)))
                       (.finishReason FinishReason/LENGTH)
                       (.modelName "s-aor-model")
                       (.tokenUsage (TokenUsage. (int 10) (int 20)))
                       .build)]
      (.onPartialResponse handler "You ")
      (.onPartialResponse handler "said ")
      (.onPartialResponse handler s)
      (.onCompleteResponse handler response)
    )))

(defrecord MockStreamingChatModel2 []
  StreamingChatModel
  (^void chat [this ^ChatRequest request ^StreamingChatResponseHandler handler]
    (let [^UserMessage m (-> request
                             .messages
                             last)
          s        (.singleText m)
          response (-> (ChatResponse$Builder.)
                       (.aiMessage (AiMessage. (str "You said " s)))
                       (.finishReason FinishReason/CONTENT_FILTER)
                       (.modelName "s-aor-model2")
                       (.tokenUsage (TokenUsage. (int 10) (int 20)))
                       .build)]
      (.onPartialResponse handler "You ")
      (.onPartialResponse handler "sa")
      (.onPartialResponse handler "id ")
      (.onPartialResponse handler s)
      (.onCompleteResponse handler response)
    )))

(deftest object-wrapping-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (aor/declare-agent-object-builder
         topology
         "chat1"
         (fn [setup] (->MockChatModel1)))
        (aor/declare-agent-object-builder
         topology
         "chat2"
         (fn [setup] (->MockChatModel1))
         {:auto-tracing? false})
        (aor/declare-agent-object-builder
         topology
         "chat3"
         (fn [setup] (->MockChatModel2)))
        (aor/declare-agent-object-builder
         topology
         "schat1"
         (fn [setup] (->MockStreamingChatModel1)))
        (aor/declare-agent-object-builder
         topology
         "schat2"
         (fn [setup] (->MockStreamingChatModel2)))
        (->
          topology
          (aor/new-agent "foo")
          (aor/node
           "start"
           nil
           (fn [agent-node model prompt]
             (let [model (aor/get-agent-object agent-node model)]
               (aor/result! agent-node (lc4j/chat model prompt)))
           )))))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))

     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind root-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-root-task-global-name "foo")))
     (bind traces-query
       (foreign-query ipc
                      module-name
                      (queries/tracing-query-name "foo")))

     (bind inv (aor/agent-initiate foo "chat1" "Hello"))
     (bind [agent-task-id agent-id] (tc/extract-invoke inv))
     (is (= "Hello!!!" (aor/agent-result foo inv)))
     (bind root
       (foreign-select-one [(keypath agent-id) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id}))
     (bind trace
       (foreign-invoke-query traces-query
                             agent-task-id
                             [[agent-task-id root]]
                             10000))
     (is
      (trace-matches?
       (:invokes-map trace)
       {!id1
        {:agent-id      ?agent-id
         :emits         []
         :agent-task-id ?agent-task-id
         :node          "start"
         :result        {:val "Hello!!!" :failure? false}
         :nested-ops    [{:type :model-call
                          :info
                          {"modelName"        "aor-model"
                           "inputTokenCount"  10
                           "finishReason"     "stop"
                           "objectName"       "chat1"
                           "input"
                           [{"type"     "user"
                             "contents" [{"type" "text" "text" "Hello"}]}]
                           "response"         "Hello!!!"
                           "outputTokenCount" 20}}]
         :input         ["chat1" "Hello"]}}
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)))
      ))


     (bind inv (aor/agent-initiate foo "chat2" "Hello"))
     (bind [agent-task-id agent-id] (tc/extract-invoke inv))
     (is (= "Hello!!!" (aor/agent-result foo inv)))
     (bind root
       (foreign-select-one [(keypath agent-id) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id}))
     (bind trace
       (foreign-invoke-query traces-query
                             agent-task-id
                             [[agent-task-id root]]
                             10000))
     (is
      (trace-matches?
       (:invokes-map trace)
       {!id1
        {:agent-id      ?agent-id
         :emits         []
         :agent-task-id ?agent-task-id
         :node          "start"
         :result        {:val "Hello!!!" :failure? false}
         :nested-ops    []
         :input         ["chat2" "Hello"]}}
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)))
      ))

     (bind inv (aor/agent-initiate foo "chat3" "Hello"))
     (bind [agent-task-id agent-id] (tc/extract-invoke inv))
     (is (= "Hello!?" (aor/agent-result foo inv)))
     (bind root
       (foreign-select-one [(keypath agent-id) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id}))
     (bind trace
       (foreign-invoke-query traces-query
                             agent-task-id
                             [[agent-task-id root]]
                             10000))
     (is
      (trace-matches?
       (:invokes-map trace)
       {!id1
        {:agent-id      ?agent-id
         :emits         []
         :agent-task-id ?agent-task-id
         :node          "start"
         :result        {:val "Hello!?" :failure? false}
         :nested-ops    [{:type :model-call
                          :info
                          {"modelName"        "aor-model2"
                           "inputTokenCount"  15
                           "finishReason"     "other"
                           "objectName"       "chat3"
                           "input"
                           [{"type"     "user"
                             "contents" [{"type" "text" "text" "Hello"}]}]
                           "response"         "Hello!?"
                           "outputTokenCount" 40}}]
         :input         ["chat3" "Hello"]}}
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)))
      ))


     (bind inv (aor/agent-initiate foo "schat1" "Hi"))
     (bind [agent-task-id agent-id] (tc/extract-invoke inv))
     (is (= "You said Hi" (aor/agent-result foo inv)))
     (is (= ["You " "said " "Hi"] @(aor/agent-stream foo inv "start")))

     (bind root
       (foreign-select-one [(keypath agent-id) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id}))
     (bind trace
       (foreign-invoke-query traces-query
                             agent-task-id
                             [[agent-task-id root]]
                             10000))
     (is
      (trace-matches?
       (:invokes-map trace)
       {!id1
        {:agent-id      ?agent-id
         :emits         []
         :agent-task-id ?agent-task-id
         :node          "start"
         :result        {:val "You said Hi" :failure? false}
         :nested-ops    [{:type :model-call
                          :info
                          {"modelName"        "s-aor-model"
                           "inputTokenCount"  10
                           "finishReason"     "length"
                           "objectName"       "schat1"
                           "input"
                           [{"type"     "user"
                             "contents" [{"type" "text" "text" "Hi"}]}]
                           "response"         "You said Hi"
                           "outputTokenCount" 20}}]
         :input         ["schat1" "Hi"]}}
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)))
      ))


     (bind inv (aor/agent-initiate foo "schat2" "Hi"))
     (bind [agent-task-id agent-id] (tc/extract-invoke inv))
     (is (= "You said Hi" (aor/agent-result foo inv)))
     (is (= ["You " "sa" "id " "Hi"] @(aor/agent-stream foo inv "start")))

     (bind root
       (foreign-select-one [(keypath agent-id) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id}))
     (bind trace
       (foreign-invoke-query traces-query
                             agent-task-id
                             [[agent-task-id root]]
                             10000))
     (is
      (trace-matches?
       (:invokes-map trace)
       {!id1
        {:agent-id      ?agent-id
         :emits         []
         :agent-task-id ?agent-task-id
         :node          "start"
         :result        {:val "You said Hi" :failure? false}
         :nested-ops    [{:type :model-call
                          :info
                          {"modelName"        "s-aor-model2"
                           "inputTokenCount"  10
                           "finishReason"     "content_filter"
                           "objectName"       "schat2"
                           "input"
                           [{"type"     "user"
                             "contents" [{"type" "text" "text" "Hi"}]}]
                           "response"         "You said Hi"
                           "outputTokenCount" 20}}]
         :input         ["schat2" "Hi"]}}
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)))
      ))
     ;; TODO: <<<<>>>>>
     ;;  - test EmbeddingStore wrapping
    )))
