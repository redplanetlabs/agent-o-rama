(ns com.rpl.agent-o-rama.impl.datasets
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agent_o_rama.impl.types
    AddDatasetExample
    AddDatasetExampleTag
    CreateDataset
    DatasetSnapshot
    DestroyDataset
    RemoveDatasetExample
    RemoveDatasetExampleTag
    RemoveDatasetSnapshot
    UpdateDatasetExample
    UpdateDatasetProperty]))


(deframaop handle-datasets-op
  [{:keys [*dataset-id] :as *data}]
  (<<with-substitutions
   [$$datasets (po/datasets-task-global)]
   (<<subsource *data
    (case> CreateDataset :> {:keys [*name *description]})
     (local-transform> [(keypath *dataset-id) :props
                        (termval {:name *name :description *description})]
                       $$datasets)

    (case> UpdateDatasetProperty :> {:keys [*key *value]})
     (local-transform> [(keypath *dataset-id :props *key) (termval *value)]
                       $$datasets)

    (case> DestroyDataset)
     (local-transform> [(keypath *dataset-id :snapshots MAP-VALS) NONE>]
                       $$datasets)
     (|direct (ops/current-task-id))
     (local-transform> [(keypath *dataset-id) NONE>]
                       $$datasets)

    (case> AddDatasetExample
           :> {:keys [*snapshot-name *example-id *input *reference-output
                       *tags]})
     (local-transform>
      [(keypath *dataset-id :snapshots *snapshot-name *example-id)
       (termval
        {:input *input :reference-output *reference-output :tags *tags})]
      $$datasets)

    (case> UpdateDatasetExample
           :> {:keys [*snapshot-name *example-id *key *value]})
     (local-transform>
      [(keypath *dataset-id :snapshots *snapshot-name *example-id *key)
       (termval *value)]
      $$datasets)

    (case> RemoveDatasetExample :> {:keys [*snapshot-name *example-id]})
     (local-transform>
      [(keypath *dataset-id :snapshots *snapshot-name *example-id) NONE>]
      $$datasets)

    (case> AddDatasetExampleTag :> {:keys [*snapshot-name *example-id *tag]})
     (local-transform>
      [(keypath *dataset-id :snapshots *snapshot-name *example-id :tags)
       NONE-ELEM
       (termval *tag)]
      $$datasets)

    (case> RemoveDatasetExampleTag
           :> {:keys [*snapshot-name *example-id *tag]})
     (local-transform>
      [(keypath *dataset-id :snapshots *snapshot-name *example-id :tags)
       (set-elem *tag)
       NONE>]
      $$datasets)

    (case> DatasetSnapshot
           :> {:keys [*from-snapshot-name *to-snapshot-name]})
     (local-select> [(keypath *dataset-id :snapshots *from-snapshot-name)
                     ALL]
                    $$datasets
                    {:allow-yield? true}
                    :> [*example-id *example])
     (local-transform>
      [(keypath *dataset-id :snapshots *to-snapshot-name *example-id)
       (termval *example)]
      $$datasets)

    (case> RemoveDatasetSnapshot :> {:keys [*snapshot-name]})
     (local-transform>
      [(keypath *dataset-id :snapshots *snapshot-name) NONE>]
      $$datasets)
   )))
