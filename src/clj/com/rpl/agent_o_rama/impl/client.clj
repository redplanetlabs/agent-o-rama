(ns com.rpl.agent-o-rama.impl.client
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:import
   [com.rpl.agentorama
    AgentInvoke
    AgentStream
    AgentStreamByInvoke]
   [com.rpl.rama.diffs
    DestroyedDiff
    Diff]))

(defn- new-items
  [new-chunks old-chunks]
  (assert (vector? new-chunks))
  (assert (vector? old-chunks))
  (subvec new-chunks (count old-chunks)))

(def FINISHED ::finished)
(def FINISHED-INVOKE ::finished-invoke)

(defn- finished-stream?
  [chunks]
  (if-let [item (h/lastv chunks)]
    (= FINISHED (:chunk item))
    false
  ))

(defn- agent-stream-all-impl*
  ^AgentStreamByInvoke
  [streaming-pstate ^AgentInvoke agent-invoke node callback-fn]
  (let [agent-task-id  (.getTaskId agent-invoke)
        agent-id       (.getAgentInvokeId agent-invoke)
        results-vol    (volatile! {})
        old-chunks-vol (volatile! [])
        resets-vol     (volatile! {})
        ps-vol         (volatile! nil)
        pcallback-fn
        (fn [new-chunks ^Diff diff _]
          (when-not (instance? DestroyedDiff diff)
            (let [new-chunks              (or new-chunks [])
                  old-chunks              @old-chunks-vol
                  unknown?-vol            (volatile! false)
                  finished?               (finished-stream? new-chunks)
                  new-chunks              (if finished?
                                            (pop new-chunks)
                                            new-chunks)
                  delta-chunks            (new-items new-chunks old-chunks)

                  reset-now-vol           (volatile! #{})
                  delta-chunks-map-vol    (volatile! {})
                  finished-invoke-ids-vol (volatile! #{})
                  already-done?           (= ::finished @ps-vol)
                  new-results
                  (reduce
                   (fn [m {:keys [invoke-id index chunk]}]
                     (let [m (if (= index 0)

                               (if (contains? m invoke-id)
                                 (do
                                   ;; check original value at start and not m in
                                   ;; case the first invocation of this callback
                                   ;; also contained a reset, which shouldn't be
                                   ;; a reset for the user
                                   (when (contains? @results-vol invoke-id)
                                     (vswap! reset-now-vol conj invoke-id))
                                   (transform [h/VOLATILE (keypath invoke-id)
                                               (nil->val 0)]
                                              inc
                                              resets-vol)
                                   (setval [h/VOLATILE (keypath invoke-id)]
                                           []
                                           delta-chunks-map-vol)
                                   (assoc m invoke-id []))
                                 m)
                               m)]
                       (if (= FINISHED-INVOKE chunk)
                         (do
                           (vswap! finished-invoke-ids-vol conj invoke-id)
                           m)
                         (do
                           (setval [h/VOLATILE (keypath invoke-id) (nil->val [])
                                    AFTER-ELEM]
                                   chunk
                                   delta-chunks-map-vol)
                           (setval [(keypath invoke-id) (nil->val [])
                                    AFTER-ELEM]
                                   chunk
                                   m)))
                     ))
                   @results-vol
                   delta-chunks)]
              (when finished?
                (locking ps-vol
                  (if-let [ps @ps-vol]
                    (when-not (= ps ::finished)
                      (close! ps))
                    (vreset! ps-vol ::finished)
                  )))
              (vreset! results-vol new-results)
              (vreset! old-chunks-vol new-chunks)
              (when
                (and callback-fn
                     (not already-done?)
                     (or finished?
                         (-> @finished-invoke-ids-vol
                             empty?
                             not)
                         (-> @delta-chunks-map-vol
                             empty?
                             not)))
                (callback-fn
                 new-results
                 @delta-chunks-map-vol
                 @reset-now-vol
                 @finished-invoke-ids-vol
                 finished?)))))

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
     AgentStreamByInvoke
     (get [this] @results-vol)
     (numResetsByInvoke [this] @resets-vol)
     (close [this]
       (locking ps-vol
         (when-not (= ::finished @ps-vol)
           (vreset! ps-vol ::finished)
           (close! ps))))
     clojure.lang.IDeref
     (deref [this] (.get this)))))

(defn agent-stream-all-impl
  [streaming-pstate agent-invoke node callback-fn]
  (agent-stream-all-impl*
   streaming-pstate
   agent-invoke
   node
   (when callback-fn
     (fn [invoke-id->chunks invoke-id->new-chunks reset-invoke-ids
          finished-invoke-ids finished?]
       (callback-fn invoke-id->chunks
                    invoke-id->new-chunks
                    reset-invoke-ids
                    finished?)))))

(defn agent-stream-impl
  [streaming-pstate agent-invoke node callback-fn]
  (let [first-invoke-id-vol (volatile! nil)
        done-vol (volatile! false)
        delegate
        (agent-stream-all-impl*
         streaming-pstate
         agent-invoke
         node
         (fn [invoke-id->chunks invoke-id->new-chunks reset-invoke-ids
              finished-invoke-ids _]
           (when-not @done-vol
             (if (and (nil? @first-invoke-id-vol)
                      (-> invoke-id->chunks
                          empty?
                          not))
               (vreset! first-invoke-id-vol
                        (select-any MAP-KEYS invoke-id->chunks)))
             (let [finished? (contains? finished-invoke-ids
                                        @first-invoke-id-vol)]
               (when finished?
                 (vreset! done-vol true))
               (when (and callback-fn
                          (or finished?
                              (contains? invoke-id->new-chunks
                                         @first-invoke-id-vol)))
                 (callback-fn
                  (get invoke-id->chunks @first-invoke-id-vol)
                  (get invoke-id->new-chunks @first-invoke-id-vol)
                  (contains? reset-invoke-ids @first-invoke-id-vol)
                  finished?))
             ))))]
    (reify
     AgentStream
     (get [this]
       (get @delegate @first-invoke-id-vol []))
     (numResets [this]
       (get (.numResetsByInvoke delegate) @first-invoke-id-vol 0))
     (close [this]
       (.close delegate))
     clojure.lang.IDeref
     (deref [this] (.get this)))
  ))
