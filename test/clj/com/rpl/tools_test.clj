(ns com.rpl.tools-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [meander.epsilon :as m]))

(def TOOLS
  [(tools/tool-info ...spec (fn [args] (+ (get args "a") (get args "b"))))
   (tools/tool-info ...spec (fn [args]) {:include-context? true})]
)

(deftest tools-test
  (with-redefs [aor-types/get-config (max-retries-override 0)]
    (with-open [ipc (rtest/create-ipc)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (-> topology
              (aor/new-agent "foo")
              (aor/node
               "start"
               nil
               (fn [agent-node v]
                 (let [tools (aor/get-agent topology "tools1")]
                   ;; TODO: <<<<>>>>
                 ))))
          (tools/new-tools-agent topology "tools1" [])
          (tools/new-tools-agent topology
                                 "tools2"
                                 []
                                 {:error-handler
                                  (tools/error-handler-static-string "blah")})
          (tools/new-tools-agent topology
                                 "tools3"
                                 []
                                 {:error-handler
                                  (tools/error-handler-rethrow)})
          (tools/new-tools-agent topology
                                 "tools4"
                                 []
                                 {:error-handler
                                  (tools/error-handler-static-string-by-type
                                   [[RuntimeException "re"]
                                    [clojure.lang.ExceptionInfo "ei"]])})
         ))
       (bind module-name (get-module-name module))
       (rtest/launch-module! ipc module {:tasks 24 :threads 2})
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))
       ;; TODO: <<<<>>>>
       ;; - test all the nested ops cases
       ;;   - success
       ;;   - invalid tool call
       ;;   - exception rethrow
       ;;   - a new exception during error handling
       ;; - test all error handlers
      ))))
