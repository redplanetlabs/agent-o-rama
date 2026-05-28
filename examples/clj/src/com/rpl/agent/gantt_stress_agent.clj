(ns com.rpl.agent.gantt-stress-agent
  "Stress agent for Timeline (Gantt) visualization: 30-way outer agg fan-out; each
   slot runs a nested 10-way agg with pseudo-random sleeps so bar lengths differ."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]))

(defn- sleep-ms! [^long ms]
  (Thread/sleep ms))

(defn- pseudo-random-sleep-ms
  "Deterministic per (seed, slot, branch-idx) in ~5–85 ms so nested invokes finish
  quickly under IPC while bars still differ visibly on the Gantt chart."
  [^long seed ^long slot ^long branch-idx]
  (let [x (mod (+ (* seed 1315423911)
                  (* slot 2246822519)
                  (* branch-idx 3266489917))
               80000)]
    (+ 5 (mod x 80))))

(aor/defagentmodule GanttStressModule
  [topology]

  (-> topology
      (aor/new-agent "GanttStressAgent")
      (aor/agg-start-node
       "stress-fanout"
       "stress-worker"
       (fn [agent-node seed]
         (doseq [i (range 30)]
           (aor/emit! agent-node "stress-worker" {:slot i :seed (long seed)}))))

      (aor/node
       "stress-worker"
       "branch-fanout"
       (fn [agent-node {:keys [slot seed]}]
         (aor/emit! agent-node "branch-fanout" {:outer-slot slot :seed (long seed)})))

      (aor/agg-start-node
       "branch-fanout"
       "branch-leaf-worker"
       (fn [agent-node {:keys [outer-slot seed]}]
         (doseq [b (range 10)]
           (aor/emit! agent-node
                      "branch-leaf-worker"
                      {:outer-slot outer-slot
                       :branch-idx b
                       :seed (long seed)}))))

      (aor/node
       "branch-leaf-worker"
       "branch-collect"
       (fn [agent-node {:keys [outer-slot branch-idx seed]}]
         (let [ms (pseudo-random-sleep-ms (long seed) (long outer-slot) (long branch-idx))]
           (sleep-ms! ms)
           (aor/emit! agent-node "branch-collect"
                      {:outer-slot outer-slot
                       :branch-idx branch-idx
                       :seed (long seed)
                       :slept-ms ms
                       :leaf-key [outer-slot branch-idx]}))))

      (aor/agg-node
       "branch-collect"
       "stress-collect"
       aggs/+vec-agg
       (fn [agent-node rows _agg-start-res]
         (let [sorted (vec (sort-by :branch-idx rows))]
           (aor/emit! agent-node "stress-collect"
                      {:slot (:outer-slot (first sorted))
                       :seed (:seed (first sorted))
                       :branches (count sorted)
                       :rows sorted}))))

      (aor/agg-node
       "stress-collect"
       nil
       aggs/+vec-agg
       (fn [agent-node parts _agg-start-res]
         (let [sorted (vec (sort-by :slot parts))
               n (count sorted)]
           (aor/result! agent-node
                        {:gantt-stress-done true
                         :seed (long (:seed (first sorted)))
                         :outer-branches n
                         :slots (mapv (fn [p] {:slot (:slot p)
                                               :branches (:branches p)})
                                      sorted)}))))))

(defn -main
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc GanttStressModule {:tasks 1 :threads 1})
    (let [manager (aor/agent-manager ipc (rama/get-module-name GanttStressModule))
          agent (aor/agent-client manager "GanttStressAgent")]
      (println (aor/agent-invoke agent 42)))))
