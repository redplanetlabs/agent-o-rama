(ns com.rpl.agent-o-rama.throttled-logging-test
  "Tests for throttled logging functionality.

   Verifies that logging is throttled after WORKER-LOG-THROTTLE-RATE messages
   and that accumulated messages are flushed after the time window expires."
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [clojure.tools.logging :as cljlogging]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.throttled-logging :as tl]
   [com.rpl.rama :refer :all]
   [com.rpl.rama.path :refer :all]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-helpers :refer [invoke-agent-and-wait!]]
   [rpl.rama.distributed.config :as conf]
   [rpl.rama.java-api.ipc :as jipc]))

(def ^:private captured-logs (atom []))

(defn- capture-log
  [_logger level throwable message]
  (swap!
    captured-logs
    conj
    {:level level :message message :throwable throwable}))

(defn- message-starts-with
  [prefix]
  (fn [r] (str/includes? (:message r) (str " " prefix))))

(aor/defagentmodule ThrottledLoggingTestModule
  [topology]

  ;; Create a simple agent that generates log messages
  (-> topology
      (aor/new-agent "LoggingAgent")
      (aor/node
       "log-messages"
       nil
       (fn [agent-node num-messages message-base]
         (dotimes [i num-messages]
           (tl/error (keyword (str "test-callsite-" message-base))
                     (str message-base " message " i)))
         (aor/result! agent-node :completed)))))

(defn- messages
  [prefix]
  (filterv (message-starts-with prefix) @captured-logs))

(deftest throttled-logging-basic-test
  (reset! captured-logs [])
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module!
     ipc
     ThrottledLoggingTestModule
     {:tasks 1 :threads 1})
    (let [module-name (get-module-name ThrottledLoggingTestModule)
          depot       (foreign-depot
                       ipc
                       module-name
                       (po/agent-depot-name "LoggingAgent"))
          root-pstate (foreign-pstate
                       ipc
                       module-name
                       (po/agent-root-task-global-name "LoggingAgent"))]

      (with-redefs [cljlogging/log* capture-log]

        ;; Generate a small number of log messages
        (invoke-agent-and-wait! depot root-pstate [5 "test"])

        ;; Should have captured some log messages
        (is (pos? (count (messages "test")))
            "Should have captured at least one log message")))))

(deftest throttled-logging-rate-limit-test
  (reset! captured-logs [])
  (let [rate-limit 4]
    (with-redefs [jipc/ipc-cluster-options
                  (fn []
                    {:config-overrides
                     {conf/WORKER-LOG-THROTTLE-RATE rate-limit}})]
      (with-open [ipc (rtest/create-ipc)]
        (rtest/launch-module!
         ipc
         ThrottledLoggingTestModule
         {:tasks 1 :threads 1})
        (let [module-name (get-module-name ThrottledLoggingTestModule)
              depot       (foreign-depot
                           ipc
                           module-name
                           (po/agent-depot-name "LoggingAgent"))
              root-pstate (foreign-pstate
                           ipc
                           module-name
                           (po/agent-root-task-global-name "LoggingAgent"))
              msg-prefix  "rapid"]

          (with-redefs [cljlogging/log* capture-log]
            ;; Generate many log messages rapidly to trigger throttling
            (invoke-agent-and-wait! depot root-pstate [100 msg-prefix])

            (let [message-count (count (messages msg-prefix))]
              ;; Should have fewer messages than we generated due to throttling
              (is (< message-count 100)
                  "Should have fewer messages due to throttling.")
              ;; But should have at least some messages
              (is
               (= (dec rate-limit) message-count)
               "Should have correct number of un-throttled log messages"))))))))

(deftest throttled-logging-different-callsites-test
  (reset! captured-logs [])
  (let [rate-limit 4]
    (with-redefs [jipc/ipc-cluster-options
                  (fn []
                    {:config-overrides
                     {conf/WORKER-LOG-THROTTLE-RATE rate-limit}})]
      (with-open [ipc (rtest/create-ipc)]
        (rtest/launch-module!
         ipc
         ThrottledLoggingTestModule
         {:tasks 1 :threads 1})
        (let [module-name (get-module-name ThrottledLoggingTestModule)
              depot       (foreign-depot
                           ipc
                           module-name
                           (po/agent-depot-name "LoggingAgent"))
              root-pstate (foreign-pstate
                           ipc
                           module-name
                           (po/agent-root-task-global-name "LoggingAgent"))]
          (testing "with logging from two callsites"
            (with-redefs [cljlogging/log* capture-log]
              ;; Generate log messages from different callsites (different
              ;; callsite-ids)
              (invoke-agent-and-wait! depot root-pstate [50 "callsite1"])
              (invoke-agent-and-wait! depot root-pstate [50 "callsite2"])

              (testing "has captured messages from both callsites"
                (is (= (dec rate-limit) (count (messages "callsite1")))
                    "have messages from callsite1")
                (is (= (dec rate-limit) (count (messages "callsite2")))
                    "have messages from callsite2")))))))))

(aor/defagentmodule TimeWindowTestModule
  [topology]

  ;; Agent that can log with controlled timing
  (-> topology
      (aor/new-agent "TimedLoggingAgent")
      (aor/node "log-with-delay"
                nil
                (fn [agent-node burst-size delay-ms final-burst]
                  ;; First burst of messages
                  (dotimes [i burst-size]
                    (tl/error :time-test-callsite
                              (str "burst1 message " i)))

                  ;; Wait for the specified delay
                  (Thread/sleep (long delay-ms))

                  ;; Second burst of messages
                  (dotimes [i final-burst]
                    (tl/error :time-test-callsite
                              (str "burst2 message " i)))

                  (aor/result! agent-node :completed)))))

(deftest throttled-logging-time-window-test
  ;; Test that throttled messages are flushed after the time window expires.
  (reset! captured-logs [])
  (let [time-window-secs   3
        time-window-millis (* time-window-secs 1000)
        rate-limit         3]
    (with-redefs [jipc/ipc-cluster-options
                  (fn []
                    {:config-overrides
                     {conf/WORKER-LOG-THROTTLE-RATE
                      rate-limit
                      conf/WORKER-LOG-THROTTLE-MEASUREMENT-TIME-WINDOW-SECONDS
                      time-window-secs}})]
      (with-open [ipc (rtest/create-ipc)]
        (rtest/launch-module! ipc TimeWindowTestModule {:tasks 1 :threads 1})
        (let [module-name (get-module-name TimeWindowTestModule)
              depot       (foreign-depot
                           ipc
                           module-name
                           (po/agent-depot-name "TimedLoggingAgent"))
              root-pstate (foreign-pstate
                           ipc
                           module-name
                           (po/agent-root-task-global-name
                            "TimedLoggingAgent"))]

          (testing "with logs spanning throttling window,"
            (with-redefs [cljlogging/log* capture-log]
              ;; Generate initial burst, wait for time window, then final burst
              ;; Using a delay longer than typical throttle time window (default
              ;; 60 seconds)
              (invoke-agent-and-wait!
               depot
               root-pstate
               [50 (+ 100 time-window-millis) 10])

              ;; Give time for final processing
              (Thread/sleep (+ 100 time-window-millis))

              (testing "we see the throttled log messages"
                (let [messages @captured-logs]
                  (is (= (* 2 rate-limit) (count messages))
                      "Should have messages from both time periods")

                  (testing "from both burst1 and burst2"
                    (is (= rate-limit
                           (count
                            (filterv #(str/includes? % "burst1") messages)))
                        "contains messages from first burst")
                    (is (= rate-limit
                           (count
                            (filterv #(str/includes? % "burst2") messages)))
                        "contains messages from second burst")))))))))))

(deftest throttled-logging-callsite-isolation-test
  (reset! captured-logs [])
  (let [rate-limit 4]
    (with-redefs [jipc/ipc-cluster-options
                  (fn []
                    {:config-overrides
                     {conf/WORKER-LOG-THROTTLE-RATE rate-limit}})]
      (with-open [ipc (rtest/create-ipc)]
        (rtest/launch-module!
         ipc
         ThrottledLoggingTestModule
         {:tasks 1 :threads 1})
        (let [module-name (get-module-name ThrottledLoggingTestModule)
              depot       (foreign-depot
                           ipc
                           module-name
                           (po/agent-depot-name "LoggingAgent"))
              root-pstate (foreign-pstate
                           ipc
                           module-name
                           (po/agent-root-task-global-name "LoggingAgent"))]
          (testing "with logging from multiple unique callsites"
            (with-redefs [cljlogging/log* capture-log]
              ;; Generate messages from different callsites (each with different
              ;; callsite-id)
              (invoke-agent-and-wait! depot root-pstate [5 "isolation1"])
              (invoke-agent-and-wait! depot root-pstate [5 "isolation2"])
              (invoke-agent-and-wait! depot root-pstate [5 "isolation3"])
              (invoke-agent-and-wait! depot root-pstate [5 "isolation4"])

              (testing "each callsite has its own throttling counter"
                (is (= (dec rate-limit) (count (messages "isolation1")))
                    "isolation1 should have its own throttle limit")
                (is (= (dec rate-limit) (count (messages "isolation2")))
                    "isolation2 should have its own throttle limit")
                (is (= (dec rate-limit) (count (messages "isolation3")))
                    "isolation3 should have its own throttle limit")
                (is (= (dec rate-limit) (count (messages "isolation4")))
                    "isolation4 should have its own throttle limit")))))))))
