(ns dev.profile-handlers
  "Helper functions for profiling handler performance.
   
   Usage via clj-nrepl-eval:
   
   1. Find nREPL port:
      clj-nrepl-eval --discover-ports
   
   2. Start profiling:
      clj-nrepl-eval -p PORT '(require (quote dev.profile-handlers)) (dev.profile-handlers/start-profiling)'
   
   3. Make some requests to the invocations page
   
   4. Stop and view flamegraph:
      clj-nrepl-eval -p PORT '(dev.profile-handlers/stop-and-view-profile)'
   
   Or for simpler timing-based profiling:
      clj-nrepl-eval -p PORT '(dev.profile-handlers/profile-invocations-handler {:module-id \"your-module\" :agent-name \"your-agent\" :pagination []})'
  "
  (:require
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.ui.sente :as sente]))

;; ===== Timing-based profiling (always available) =====

(defmacro timing [label & body]
  `(let [start# (System/nanoTime)
         result# (do ~@body)
         end# (System/nanoTime)
         elapsed-ms# (/ (- end# start#) 1000000.0)]
     (println (format "%s: %.2f ms" ~label elapsed-ms#))
     result#))

(defn profile-serialization
  "Profile the ->ui-serializable function on sample data"
  [data]
  (timing "Total serialization"
    (common/->ui-serializable data)))

(defn profile-invocations-handler
  "Profile the invocations/get-page handler"
  [data]
  (let [preprocessed (timing "Preprocess" (common/preprocess-event-msg {:?data data :id :invocations/get-page}))
        handler-fn (get-method sente/-event-msg-handler :invocations/get-page)
        result (timing "Handler execution" (handler-fn (:?data preprocessed) nil))
        serialized (timing "Serialization" (common/->ui-serializable result))]
    (println "\nProfile complete. Check timings above.")
    {:result-keys (keys result)
     :serialized-keys (keys serialized)}))

;; ===== clj-async-profiler integration (requires dependency) =====

(defn start-profiling
  "Start CPU profiling. Requires clj-async-profiler on classpath.
   Add to deps.edn: com.clojure-goes-fast/clj-async-profiler {:mvn/version \"1.2.2\"}"
  []
  (try
    (require '[clj-async-profiler.core :as prof])
    (let [start-fn (resolve 'clj-async-profiler.core/start)]
      (start-fn {})
      (println "Profiling started. Make some requests, then call stop-and-view-profile"))
    (catch Exception e
      (println "clj-async-profiler not available. Add to deps.edn:")
      (println "  com.clojure-goes-fast/clj-async-profiler {:mvn/version \"1.2.2\"}")
      (println "\nError:" (.getMessage e)))))

(defn stop-and-view-profile
  "Stop profiling and generate flamegraph"
  []
  (try
    (require '[clj-async-profiler.core :as prof])
    (let [stop-fn (resolve 'clj-async-profiler.core/stop)
          serve-fn (resolve 'clj-async-profiler.core/serve-ui)]
      (stop-fn {})
      (serve-fn 8080)
      (println "Flamegraph server started at http://localhost:8080"))
    (catch Exception e
      (println "Error stopping profiler:" (.getMessage e)))))

(defn profile-specific-call
  "Profile a specific function call"
  [f & args]
  (try
    (require '[clj-async-profiler.core :as prof])
    (let [profile-fn (resolve 'clj-async-profiler.core/profile)]
      (profile-fn {} (apply f args)))
    (catch Exception e
      (println "clj-async-profiler not available, falling back to timing")
      (timing "Function call" (apply f args)))))

(comment
  ;; Example: Profile the serialization of a large map
  (profile-serialization {:nodes (into {} (map (fn [i] [i {:id i :name (str "node-" i) :data (range 100)}]) (range 100)))})
  
  ;; Example: Profile a full handler call
  (profile-invocations-handler
   {:module-id "my-module"
    :agent-name "my-agent"
    :pagination []})
  )

