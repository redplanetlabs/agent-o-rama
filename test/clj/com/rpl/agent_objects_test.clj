(ns com.rpl.agent-objects-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc])
  (:import
   [java.util
    IdentityHashMap]))

(def SEMS)
(def BUILDS-ATOM)
(def ACQUIRED-ATOM)

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
                 (let [o (aor/get-agent-object agent-node n)]
                   (setval [ATOM (keypath n) (nil->val []) AFTER-ELEM]
                           o
                           ACQUIRED-ATOM)
                   (h/acquire-semaphore (get SEMS n) 1)
                   (aor/result! agent-node "done")
                 ))))))
         (rtest/launch-module! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))

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

         ;; TODO: <<<<>>>>
         ;;  - verify timeout on acquire causes exception on the acquire
         ;;  callsite
         ;;    - configure config to lower timeout
        )))))

;; TODO: <<<<>>>>
;; - test auto-wrap
;;   - can make custom impl of ChatModel that makes a fixed ChatResponse for
;;   doChat
;; - test StreamingChatModel streaming
;;  - custom impl that calls the callbacks directly and synchronously
