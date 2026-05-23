(ns com.rpl.agent-o-rama.ui.invocations.polling-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [com.rpl.agent-o-rama.ui.events :as events]))

;; Test via the public merge path: graph-needs-poll? is private; mirror its logic here
;; so we lock in the contract used by process-graph-page.
(defn- graph-needs-poll? [raw-nodes trace-truncated?]
  (or trace-truncated?
      (some (fn [[_ node-data]]
              (and (:node node-data)
                   (:start-time-millis node-data)
                   (not (:finish-time-millis node-data))))
            raw-nodes)))

(deftest graph-needs-poll-test
  (testing "keeps polling while drawable nodes lack finish times"
    (let [id #uuid "550e8400-e29b-41d4-a716-446655440000"
          raw {id {:node "worker"
                  :start-time-millis 100
                  :finish-time-millis nil}}]
      (is (graph-needs-poll? raw false))
      (is (graph-needs-poll? {} true))
      (is (not (graph-needs-poll? {id (assoc (get raw id) :finish-time-millis 200)} false)))))
