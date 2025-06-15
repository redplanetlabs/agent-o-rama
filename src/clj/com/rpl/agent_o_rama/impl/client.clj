(ns com.rpl.agent-o-rama.impl.client
  (:use [com.rpl.rama]
        [com.rpl.rama path]))

(defn new-items [new-chunks old-chunks]
  (assert (vector? new-chunks))
  (assert (vector? old-chunks))
  (subvec new-chunks (count old-chunks)))
