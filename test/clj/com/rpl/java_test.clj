(ns com.rpl.java-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
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
  (when (some? (System/getenv "OPENAI_API_KEY"))
    (is (= {"a" "8" "m" "54"} (TestModules/runBasicToolsOpenAIAgent)))
  ))

;; TODO: <<<<>>>>
;; (TestSnippets/toolsAgentOptionsCases)
