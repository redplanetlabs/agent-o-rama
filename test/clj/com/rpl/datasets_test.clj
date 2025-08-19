(ns com.rpl.datasets-test
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
   [com.rpl.test-common :as tc]))

(deftest dataset-operations-test
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
             (fn [agent-node]
               (aor/result! agent-node "done")
             )))
       ))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind depot (foreign-depot ipc module-name (po/datasets-depot-name)))
     (bind pstate
       (foreign-pstate ipc module-name (po/datasets-task-global-name)))

     (bind ds-id1 (h/random-uuid7))
     (bind ds-id2 (h/random-uuid7))

     (foreign-append!
      depot
      (aor-types/->valid-CreateDataset ds-id1 "Dataset 1" "this is a dataset"))
     (foreign-append!
      depot
      (aor-types/->valid-CreateDataset ds-id2 "Dataset 2" nil))


     ;; TODO: <<<<>>>>
     ;; - CreateDataset
     ;; - UpdateDatasetProperty
     ;; - DestroyDataset
     ;; - AddDatasetExample
     ;; - UpdateDatasetExample
     ;; - RemoveDatasetExample
     ;; - AddDatasetExampleTag
     ;; - RemoveDatasetExampleTag
     ;; - DatasetSnapshot
     ;; - RemoveDatasetSnapshot
     ;; - declare-search-datasets-topology
     ;; - declare-get-datasets-page-topology
    )))


; (drp/defrecord+ CreateDataset
;   [dataset-id :- UUID
;    name :- String
;    description :- (s/maybe String)])
;
; (drp/defrecord+ UpdateDatasetProperty
;   [dataset-id :- UUID
;    key :- clojure.lang.Keyword
;    value :- Object])
;
; (drp/defrecord+ DestroyDataset
;   [dataset-id :- UUID])
;
; (drp/defrecord+ AddDatasetExample
;   [dataset-id :- UUID
;    snapshot-name :- (s/maybe String)
;    example-id :- UUID
;    input :- [Object]
;    reference-output :- Object
;    tags :- (s/maybe #{String})
;   ])
;
; (drp/defrecord+ UpdateDatasetExample
;   [dataset-id :- UUID
;    snapshot-name :- (s/maybe String)
;    example-id :- UUID
;    key :- clojure.lang.Keyword
;    value :- Object])
;
; (drp/defrecord+ RemoveDatasetExample
;   [dataset-id :- UUID
;    snapshot-name :- (s/maybe String)
;    example-id :- UUID])
;
; (drp/defrecord+ AddDatasetExampleTag
;   [dataset-id :- UUID
;    snapshot-name :- (s/maybe String)
;    example-id :- UUID
;    tag :- String])
;
; (drp/defrecord+ RemoveDatasetExampleTag
;   [dataset-id :- UUID
;    snapshot-name :- (s/maybe String)
;    example-id :- UUID
;    tag :- String])
;
; (drp/defrecord+ DatasetSnapshot
;   [dataset-id :- UUID
;    from-snapshot-name :- (s/maybe String)
;    to-snapshot-name :- String])
;
; (drp/defrecord+ RemoveDatasetSnapshot
;   [dataset-id :- UUID
;    snapshot-name :- String])
