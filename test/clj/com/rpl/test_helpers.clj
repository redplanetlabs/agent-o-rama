(ns com.rpl.test-helpers
  (:use [clojure.test]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.ramaspecter :refer [walker]]))

(defmacro letlocals
  [& body]
  (let [[tobind [last-binding-or-expr]]
        (split-at (dec (count body)) body)

        last-expr
        (if (and (list? last-binding-or-expr)
                 (= 'bind (first last-binding-or-expr)))
          (last last-binding-or-expr)
          last-binding-or-expr)

        binded
        (vec (mapcat (fn [e]
                       (if (and (list? e) (= 'bind (first e)))
                         [(second e) (last e)]
                         ['_ e]))

              tobind))]
    `(let ~binded
          ~last-expr)))

(defmacro ex-info-thrown?
  [re data & body]
  `(try
     ~@body
     (is false "Did not throw exception")
     (catch clojure.lang.ExceptionInfo e#
       (is (re-matches ~re (ex-message e#)))
       (is (= ~data (ex-data e#)))
     )))

(defn wait-agent-finished!
  [root-pstate agent-task-id agent-id]
  (let [prom  (promise)
        proxy (foreign-proxy [(keypath agent-id) :ack-val]
                             root-pstate
                             {:pkey        agent-task-id
                              :callback-fn (fn [new-val _ _]
                                             (when (= new-val 0)
                                               (deliver prom nil))
                                           )})]
    (when (= ::failed (deref prom 30000 ::failed))
      (throw (ex-info "Agent did not complete" {})))
    (close! proxy)
  ))

(defn invoke-agent-and-wait!
  [depot root-pstate args]
  (let [res (foreign-append! depot (aor-types/->AgentInitiate args 0))
        [agent-task-id agent-id] (-> res
                                     vals
                                     first)]
    (wait-agent-finished! root-pstate agent-task-id agent-id)
    [agent-task-id agent-id]
  ))

(defn invoke-agent-and-return!
  [depot invokes-pstate args]
  (let [[agent-task-id agent-id] (invoke-agent-and-wait! depot
                                                         invokes-pstate
                                                         args)]
    (foreign-select-one
     [(keypath agent-id) :result]
     invokes-pstate
     {:pkey agent-task-id})
  ))

(defn- parse-var-prefix
  [sym]
  (let [s (name sym)]
    (if-let [[_ prefix] (re-matches #"^(.*?)[0-9]+$" s)]
      prefix
      s)))

(defmacro trace-matches?
  [data & bindings]
  (let [unique-syms   (set (select [(walker symbol?)
                                    #(= \!
                                        (-> %
                                            str
                                            first))]
                                   bindings))
        unique-syms   (setval [ALL NAME FIRST] \? unique-syms)
        unique-groups (vals (group-by parse-var-prefix unique-syms))
        unique-guards (for [group unique-groups]
                        `(m/guard (= ~(count group)
                                     (count (set ~(vec group))))))
        bindings      (setval [(walker symbol?)
                               NAME
                               FIRST
                               #(= \! %)]
                              \?
                              bindings)]
    `(m/find
      ~data
      (m/and
       ~@bindings
       ~@unique-guards)
      true)))

(defn trace-time-deltas
  [trace]
  (transform [MAP-VALS
              (multi-path
               STAY
               [(must :nested-ops) ALL (view #(into {} %))])
              (submap [:start-time-millis :finish-time-millis])
              #(= 2 (count %))]
             (fn [{:keys [start-time-millis finish-time-millis]}]
               {:delta-millis (- finish-time-millis start-time-millis)})
             trace))

(defn trace-no-times
  [trace]
  (setval [MAP-VALS
           (multi-path
            STAY
            [(must :nested-ops) ALL (view #(into {} %))])
           (submap [:start-time-millis :finish-time-millis])]
          {}
          trace))

(defn condition-attained?*
  ([f] (condition-attained?* f {}))
  ([f
    {:keys [max-wait max-delay initial-delay backoff-factor retry-on-exception
            context]
     :or   {max-wait           10000
            max-delay          100
            initial-delay      10
            backoff-factor     2
            retry-on-exception false
           }}]
   (let [start-time-millis (System/currentTimeMillis)]
     (loop [delay    (long initial-delay)
            wait     0
            attempts 1]
       (let [[res e] (if retry-on-exception
                       (try
                         [(f) nil]
                         (catch Exception e
                           [false e]))
                       [(f) nil])]
         (if (or e (not res))
           (if (< (- (System/currentTimeMillis) start-time-millis) max-wait)
             (do
               (Thread/sleep delay)
               (recur (long (min (* delay backoff-factor) max-delay))
                      (long (+ wait delay))
                      (inc attempts)))
             false)
           true))))))

(defmacro condition-attained?
  [& body]
  `(condition-attained?* (fn [] ~@body)))

(defn condition-stable?*
  [exec-fn]
  (let [start-time-millis (h/current-time-millis)
        attempts (atom 0)
        matches  (atom 0)
        res
        (loop []
          (swap! attempts inc)
          (let [cond-exec-result (exec-fn)]
            (cond
              cond-exec-result
              (do
                (swap! matches inc)
                (if (= @matches 10)
                  :success
                  (do
                    ;; give a chance for condition to break
                    (Thread/sleep 2)
                    (recur)
                  )))

              (> (- (h/current-time-millis) start-time-millis)
                 30000)
              :timeout

              :else
              (if (> @matches 0)
                :not-stable
                (do
                  (Thread/sleep 1)
                  (recur))))))]
    (= res :success)))

(defmacro condition-stable?
  [& body]
  `(condition-stable?* (fn [] ~@body)))

(let [prev aor-types/get-config]
  (defn max-retries-override
    [max-retries]
    (fn [m config]
      (if (= (:name config) (:name aor-types/MAX-RETRIES-CONFIG))
        max-retries
        (prev m config)))))
