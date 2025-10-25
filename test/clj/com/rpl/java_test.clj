(ns com.rpl.java-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.tools-impl :as tools-impl]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc])
  (:import
   [com.rpl.aortest
    TestModules
    TestSnippets]))

(deftest openai-tools-agent-test
  (let [options-vol (volatile! [])]
    (with-redefs [tools-impl/hook:new-tools-agent-options (fn [name options]
                                                            (vswap! options-vol
                                                                    conj
                                                                    [name options]))]
      (when (some? (System/getenv "OPENAI_API_KEY"))
        (is (= {"a" "8" "m" "54"} (TestModules/runBasicToolsOpenAIAgent)))
        (is (= 2 (count @options-vol)))
        (let [[[n1 o1] [n2 o2]] @options-vol]
          (is (= "tools" n1))
          (is (empty? o1))

          (is (= "tools2" n2))
          (is (= [:error-handler] (keys o2)))
          (is (= "edcba" ((:error-handler o2) (ex-info "fail" {}))))
        )))))


(deftest tools-agent-options-test
  (let [res (TestSnippets/toolsAgentOptionsCases)
        [o1 o2 o3 o4 o5 o6] (mapv deref res)]
    (is (= [:error-handler] (keys o1)))
    (is (= "Error: clojure.lang.ExceptionInfo: fail {}"
           (h/first-line ((:error-handler o1) (ex-info "fail" {})))))

    (is (= [:error-handler] (keys o2)))
    (is (thrown? clojure.lang.ExceptionInfo
                 ((:error-handler o2) (ex-info "fail" {}))))

    (is (= [:error-handler] (keys o3)))
    (is (= "ei" ((:error-handler o3) (ex-info "fail" {}))))
    (is (= "ae" ((:error-handler o3) (ArithmeticException.))))

    (is (= [:error-handler] (keys o4)))
    (is (= "blah" ((:error-handler o4) (ex-info "fail" {"a" "blah"}))))
    (is (= "java.lang.ClassCastException"
           ((:error-handler o4) (ClassCastException.))))

    (is (= {} o5))

    (is (= [:error-handler] (keys o6)))
    (is (= "abcde" ((:error-handler o6) (ex-info "fail" {}))))
  ))
