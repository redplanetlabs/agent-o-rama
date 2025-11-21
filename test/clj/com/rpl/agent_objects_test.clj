(ns com.rpl.agent-objects-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
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
   [com.rpl.rama.helpers
    TopologyUtils]
   [dev.langchain4j.data.document
    Document]
   [dev.langchain4j.data.segment
    TextSegment]
   [dev.langchain4j.data.embedding
    Embedding]
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
    EmbeddingMatch
    EmbeddingSearchRequest
    EmbeddingSearchResult
    EmbeddingStore]
   [dev.langchain4j.store.embedding.filter
    Filter]
   [dev.langchain4j.store.embedding.filter.comparison
    IsEqualTo]
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
         (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
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
      (TopologyUtils/advanceSimTime 150)
      (.onPartialResponse handler "You ")
      (TopologyUtils/advanceSimTime 100)
      (.onPartialResponse handler "said ")
      (TopologyUtils/advanceSimTime 100)
      (.onPartialResponse handler s)
      (TopologyUtils/advanceSimTime 100)
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

(deftype MockEmbeddingStore []
  EmbeddingStore
  (add [this embedding] "999")
  (^String add [this ^Embedding embedding ^Object embedded]
    "1001")
  (^void add [this ^String id ^Embedding embedding])
  (addAll [this embeddings]
    (.generateIds this (count embeddings)))
  (addAll [this embeddings embeddeds]
    (.generateIds this (count embeddings)))
  (addAll [this ids embeddings embeddeds])
  (generateIds [this n]
    (vec (for [i (range n)] (str i))))
  (remove [this id])
  (removeAll [this])
  (^void removeAll [this ^Filter filter])
  (^void removeAll [this ^java.util.Collection ids])
  (search [this request]
    (EmbeddingSearchResult.
     [(EmbeddingMatch. 0.5 "11" (tc/embedding 0.1 0.2) (tc/text-segment "foo" {"source" "doc1" "page" 1}))
      (EmbeddingMatch. 0.75 "12" (tc/embedding 1.5 0.3) (tc/text-segment "bar" {"source" "doc2" "page" 3}))
      (EmbeddingMatch. 0.6 "13" (tc/embedding 0.2 0.4) nil)
      (EmbeddingMatch. 0.8 "14" (tc/embedding 0.3 0.5) (tc/document "doc text" {"author" "Smith" "year" 2023}))
      (EmbeddingMatch. 0.9 "15" (tc/embedding 0.4 0.6) (TextSegment/from "baz"))])))

(deftest object-wrapping-test
  (with-open [ipc (rtest/create-ipc)
              _ (TopologyUtils/startSimTime)]
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
        (aor/declare-agent-object-builder
         topology
         "emb"
         (fn [setup] (MockEmbeddingStore.)))
        (->
          topology
          (aor/new-agent "foo")
          (aor/node
           "start"
           nil
           (fn [agent-node oname prompt]
             (let [obj (aor/get-agent-object agent-node oname)]
               (if-not (= oname "emb")
                 (aor/result! agent-node (lc4j/basic-chat obj prompt))
                 (let [^EmbeddingStore obj obj]
                   (.add obj (tc/embedding 1.0 2.0))
                   (.add obj (tc/embedding 1.1 2.1) "a1")
                   (.add obj "abcd" (tc/embedding 1.2 2.2))
                   (.addAll obj [(tc/embedding 1.3 2.3) (tc/embedding 1.4 2.4)])
                   (.addAll obj
                            [(tc/embedding 1.5 2.5) (tc/embedding 1.6 2.6)]
                            ["x" "y"])
                   (.addAll obj
                            ["7" "8"]
                            [(tc/embedding 1.7 2.7) (tc/embedding 1.8 2.8)]
                            ["x1" "y1"])
                   (.remove obj "id1")
                   (.removeAll obj)
                   (.removeAll obj (IsEqualTo. "a" 1))
                   (.removeAll obj ["id1" "id2"])
                   (.search
                    obj
                    (EmbeddingSearchRequest. (tc/embedding 0.1 0.3)
                                             (int 5)
                                             0.75
                                             (IsEqualTo. "b" 2)))
                   (aor/result! agent-node "eee")
                 ))))))))
     (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))

     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind root-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-root-task-global-name "foo")))
     (bind traces-query (:tracing-query (aor-types/underlying-objects foo)))

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
                           "outputTokenCount" 20
                           "totalTokenCount"  30}}]
         :input         ["chat1" "Hello"]}}
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)))
      ))
     (is (nil? (select-any [:invokes-map MAP-VALS :nested-ops ALL :info
                            (keypath "firstTokenTimeMillis")]
                           trace)))

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
                           "outputTokenCount" 40
                           "totalTokenCount"  55}}]
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
                          {"modelName"            "s-aor-model"
                           "inputTokenCount"      10
                           "finishReason"         "length"
                           "objectName"           "schat1"
                           "input"
                           [{"type"     "user"
                             "contents" [{"type" "text" "text" "Hi"}]}]
                           "response"             "You said Hi"
                           "outputTokenCount"     20
                           "totalTokenCount"      30
                           "firstTokenTimeMillis" 150}}]
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
                           "outputTokenCount" 20
                           "totalTokenCount"  30}}]
         :input         ["schat2" "Hi"]}}
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)))
      ))


     (bind inv (aor/agent-initiate foo "emb" ""))
     (bind [agent-task-id agent-id] (tc/extract-invoke inv))
     (is (= "eee" (aor/agent-result foo inv)))
     (bind root
       (foreign-select-one [(keypath agent-id) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id}))
     (bind trace
       (foreign-invoke-query traces-query
                             agent-task-id
                             [[agent-task-id root]]
                             10000))

;; Verify all nested operations exist with correct types
     (is
      (trace-matches?
       (walk/postwalk
        (fn [x]
          ;; meander seems unable to match floats/doubles/ints, so do this as a
          ;; workaround
          (cond
            (= (class (float-array 0)) (class x))
            ["*" (mapv str x)]

            (= Integer (class x))
            ["i" (str x)]

            (= Double (class x))
            ["d" (str x)]

            :else
            x))
        (:invokes-map trace))
       {!id1
        {:agent-id      ?agent-id
         :emits         []
         :agent-task-id ?agent-task-id
         :node          "start"
         :result        {:val "eee" :failure? false}
         :nested-ops    [{:type :db-write
                          :info
                          {"op"         "add"
                           "id"         "999"
                           "objectName" "emb"}}
                         {:type :db-write
                          :info
                          {"op"         "add"
                           "id"         "1001"
                           "objectName" "emb"}}
                         {:type :db-write
                          :info
                          {"op"         "add"
                           "id"         "abcd"
                           "objectName" "emb"}}
                         {:type :db-write
                          :info
                          {"op"         "addAll"
                           "ids"        ["0" "1"]
                           "objectName" "emb"}}
                         {:type :db-write
                          :info
                          {"op"         "addAll"
                           "ids"        ["0" "1"]
                           "objectName" "emb"}}
                         {:type :db-write
                          :info
                          {"op"         "addAll"
                           "ids"        ["7" "8"]
                           "objectName" "emb"}}
                         {:type :db-write
                          :info
                          {"op"         "remove"
                           "id"         "id1"
                           "objectName" "emb"}}
                         {:type :db-write
                          :info
                          {"op"         "removeAll"
                           "objectName" "emb"}}
                         {:type :db-write
                          :info
                          {"op"         "removeAll"
                           "filter"     "IsEqualTo(key=a, comparisonValue=1)"
                           "objectName" "emb"}}
                         {:type :db-write
                          :info
                          {"op"         "removeAll"
                           "ids"        ["id1" "id2"]
                           "objectName" "emb"}}
                         {:type :db-read
                          :info
                          {"op"         "search"
                           "objectName" "emb"
                           "request"
                           {"filter"     "IsEqualTo(key=b, comparisonValue=2)"
                            "maxResults" ["i" "5"]
                            "minScore"   ["d" "0.75"]}
                           "matches"
                           [{"id"       "11"
                             "score"    ["d" "0.5"]
                             "metadata" {"source" "doc1"
                                         "page"   ["i" "1"]}}
                            {"id"       "12"
                             "score"    ["d" "0.75"]
                             "metadata" {"source" "doc2"
                                         "page"   ["i" "3"]}}
                            {"id"    "13"
                             "score" ["d" "0.6"]}
                            {"id"    "14"
                             "score" ["d" "0.8"]}
                            {"id"       "15"
                             "score"    ["d" "0.9"]
                             "metadata" {}}]}}]
         :input         ["emb" ""]}}
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)))
      ))
    )))
