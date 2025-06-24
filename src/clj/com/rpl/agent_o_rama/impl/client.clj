(ns com.rpl.agent-o-rama.impl.client
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:import
   [com.rpl.agentorama
    AgentInvoke
    AgentStream
    StreamingChunk]
   [com.rpl.rama.diffs
    DestroyedDiff
    Diff
    Diff$Processor
    SequenceInsertDiff$Processor]))

(defn- new-items
  [new-chunks old-chunks]
  (assert (vector? new-chunks))
  (assert (vector? old-chunks))
  (subvec new-chunks (count old-chunks)))

(def FINISHED ::finished)

(defn- finished-stream?
  [chunks]
  (if-let [item (h/lastv chunks)]
    (= FINISHED (.getChunk ^StreamingChunk item))
    false
  ))

(defn agent-stream-impl
  [streaming-pstate ^AgentInvoke agent-invoke node callback-fn]
  (let [agent-task-id (.getTaskId agent-invoke)
        agent-id      (.getAgentInvokeId agent-invoke)
        results-vol   (volatile! [])
        resets-vol    (volatile! 0)
        ps-vol        (volatile! nil)
        pcallback-fn
        (fn [new-chunks ^Diff diff _]
          (when-not (instance? DestroyedDiff diff)
            (let [new-chunks   (or new-chunks [])
                  old-chunks   @results-vol
                  unknown?-vol (volatile! false)
                  finished?    (finished-stream? new-chunks)
                  new-chunks   (if finished?
                                 (pop new-chunks)
                                 new-chunks)
                  _ (.process
                     diff
                     (reify
                      Diff$Processor
                      (unhandled [this]
                        (vreset! unknown?-vol true))

                      SequenceInsertDiff$Processor
                      (processSequenceInsertDiff [this diff])
                     ))

                  reset?
                  (or (< (count new-chunks)
                         (count old-chunks))
                      (not= old-chunks
                            (subvec new-chunks
                                    0
                                    (count old-chunks))))]
              (when reset?
                (vswap! resets-vol inc))
              (when finished?
                (locking ps-vol
                  (if-let [ps @ps-vol]
                    (when-not (= ps ::finished)
                      (close! ps))
                    (vreset! ps-vol ::finished)
                  )))
              (vreset! results-vol new-chunks)
              (when callback-fn
                (if reset?
                  (callback-fn
                   new-chunks
                   new-chunks
                   true
                   finished?)
                  (callback-fn
                   new-chunks
                   (new-items new-chunks
                              old-chunks)
                   false
                   finished?))))))

        ps
        (foreign-proxy
         [(keypath agent-id node :all)
          (srange-dynamic h/start-index
                          h/srange-dynamic-end-index)]
         streaming-pstate
         {:pkey        agent-task-id
          :callback-fn pcallback-fn})]
    (locking ps-vol
      (if (= ::finished @ps-vol)
        (close! ps)
        (vreset! ps-vol ps)))
    (reify
     AgentStream
     (get [this] @results-vol)
     (numResets [this] @resets-vol)
     (close [this]
       (locking ps-vol
         (when-not (= ::finished @ps-vol)
           (vreset! ps-vol ::finished)
           (close! ps))))
     clojure.lang.IDeref
     (deref [this] (.get this)))))
