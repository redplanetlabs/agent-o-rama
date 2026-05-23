(ns com.rpl.agent-o-rama.ui.invocations.graph-node-test
  (:require
   [cljs.test :refer [deftest is]]
   [com.rpl.agent-o-rama.ui.invocations.graph-node :as gn]))

(deftest node-ids-equal-test
  (let [u #uuid "550e8400-e29b-41d4-a716-446655440000"]
    (is (gn/node-ids-equal? u u))
    (is (gn/node-ids-equal? u (str u)))
    (is (gn/node-ids-equal? (str u) u))
    (is (not (gn/node-ids-equal? u nil)))
    (is (not (gn/node-ids-equal? "a" "b")))))

(deftest canonical-node-id-test
  (let [u #uuid "550e8400-e29b-41d4-a716-446655440000"
        graph {u {:node "root"}}]
    (is (= u (gn/canonical-node-id graph u)))
    (is (= u (gn/canonical-node-id graph (str u))))))
