(ns com.rpl.agent-o-rama.ui.feedback-test-agent-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.ui.feedback-test-agent :as fta]
   [com.rpl.rama :refer :all]
   [com.rpl.rama.path :as path]
   [com.rpl.rama.test :as rtest]))

(deftest feedback-test-agent-basic-test
  (testing "FeedbackTestAgent runs successfully with different modes"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module!
       ipc
       fta/FeedbackTestAgentModule
       {:tasks 1 :threads 1})
      (let [module-name   (get-module-name fta/FeedbackTestAgentModule)
            agent-manager (aor/agent-manager ipc module-name)
            agent-client  (aor/agent-client agent-manager "FeedbackTestAgent")]

        (is (= "test-hello"
               (aor/agent-invoke agent-client "hello")))

        (is (= "ok"
               (aor/agent-invoke agent-client {"mode" "short"})))

        (is (= "test-medium-result"
               (aor/agent-invoke agent-client {"mode" "medium"})))

        (is (= "test-this-is-a-very-long-result-string-for-testing"
               (aor/agent-invoke agent-client {"mode" "long"})))

        (is (= "test-custom"
               (aor/agent-invoke
                agent-client
                {"mode" "prefixed" "text" "custom"})))))))

(deftest feedback-generation-test
  (testing "FeedbackTestAgent generates feedback with varying scores"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module!
       ipc
       fta/FeedbackTestAgentModule
       {:tasks 1 :threads 1})
      (let [module-name       (get-module-name fta/FeedbackTestAgentModule)
            agent-manager     (aor/agent-manager ipc module-name)
            agent-client      (aor/agent-client
                               agent-manager
                               "FeedbackTestAgent")
            root-pstate       (:root-pstate
                               (aor-types/underlying-objects
                                agent-client))

            get-feedback      (fn [agent-invoke]
                                (foreign-select-one
                                 [(path/keypath
                                   (:agent-invoke-id agent-invoke))
                                  :feedback
                                  :results]
                                 root-pstate
                                 {:pkey (:task-id agent-invoke)}))
            wait-for-mb-count #(rtest/wait-for-microbatch-processed-count
                                ipc
                                module-name
                                aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME
                                %)]

        (let [global-actions-depot (:global-actions-depot
                                    (aor-types/underlying-objects
                                     agent-manager))
              setup-result         (fta/setup-feedback-testing!
                                    agent-manager
                                    global-actions-depot)]
          (is (= 4 (count (:evaluators setup-result))))
          (is (= 3 (count (:agent-rules setup-result))))
          (is (= 2 (count (:node-rules setup-result)))))

        (let [invoke (aor/agent-initiate agent-client {"mode" "short"})]
          (is (= "ok" (aor/agent-result agent-client invoke)))
          (wait-for-mb-count 5) ; NOTE there are 5 rules
          (let [feedback (get-feedback invoke)]
            (is (seq feedback))
            (is (>= (count feedback) 2))
            (is (= #{"single-eval" "dual-eval"}
                   (set (mapv (comp :eval-name :source) feedback))))))

        (let [invoke (aor/agent-initiate agent-client {"mode" "medium"})]
          (is (= "test-medium-result"
                 (aor/agent-result agent-client invoke)))
          (wait-for-mb-count 10)
          (let [feedback (get-feedback invoke)]
            (is (seq feedback))
            (is (>= (count feedback) 2))
            (is (= #{"single-eval" "dual-eval"}
                   (set (mapv (comp :eval-name :source) feedback))))))

        (let [invoke (aor/agent-initiate agent-client {"mode" "long"})]
          (is (= "test-this-is-a-very-long-result-string-for-testing"
                 (aor/agent-result agent-client invoke)))
          (wait-for-mb-count 15)
          (let [feedback (get-feedback invoke)]
            (is (seq feedback))
            (is (>= (count feedback) 2))
            (is (= #{"single-eval" "dual-eval"}
                   (set (mapv (comp :eval-name :source) feedback))))))))))
