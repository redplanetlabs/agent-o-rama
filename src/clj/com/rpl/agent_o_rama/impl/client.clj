(ns com.rpl.agent-o-rama.impl.client
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:import
   [com.rpl.agentorama
    StreamingChunk]))

(defn new-items [new-chunks old-chunks]
  (assert (vector? new-chunks))
  (assert (vector? old-chunks))
  (subvec new-chunks (count old-chunks)))

(def FINISHED ::finished)

(defn finished-stream?
  [chunks]
  (if-let [item (h/lastv chunks)]
    (= FINISHED (.getChunk ^StreamingChunk item))
    false
  ))
