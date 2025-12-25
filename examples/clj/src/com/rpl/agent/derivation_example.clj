(ns com.rpl.agent.derivation-example
  "Minimal derivation-based agent demonstrating pre-agent ontology.
   Layer 0: seed, trit | Layer 1: derivation | Layer 2: stalk, section"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]))

;;; Layer 0: Pre-Ontological Primitives
(def GENESIS-SEED 0x42D)
(def GAMMA-32 0x9E3779B9)
(def TRITS {:minus -1 :ergodic 0 :plus +1})

;;; Layer 1: Derivation
(defn derive-seed [seed trit]
  (let [mixed (bit-xor seed (* trit GAMMA-32))]
    (bit-and (+ mixed GAMMA-32) 0xFFFFFFFF)))

(defn gf3-add [a b]
  (let [s (+ a b)]
    (cond (> s 1) (- s 3) (< s -1) (+ s 3) :else s)))

(defn gf3-conserved? [trits]
  (zero? (mod (reduce + trits) 3)))

;;; Layer 2: Stalk/Section
(defn section [seed trit role result]
  {:seed seed :trit trit :stalk (name role) :data result})

;;; Layer 4: Agent (emergent from derivations)
(aor/defagentmodule DerivationModule [topology]
  (-> topology
      (aor/new-agent "derivation-agent")
      
      ;; Trifurcate: emit to all three stalks
      (aor/agg-start-node "trifurcate" "derive"
        (fn [node {:keys [seed intent] :or {seed GENESIS-SEED}}]
          (doseq [[role trit] TRITS]
            (aor/emit! node "derive" seed trit role intent))
          {:genesis seed}))
      
      ;; Derive: each stalk produces a section
      (aor/node "derive" "glue"
        (fn [node seed trit role intent]
          (let [new-seed (derive-seed seed trit)
                result (str role ": processed '" intent "' → " (format "0x%X" new-seed))]
            (aor/emit! node "glue" (section new-seed trit role result)))))
      
      ;; Glue: aggregate sections, verify GF(3)
      (aor/agg-node "glue" nil aggs/+vec-agg
        (fn [node sections {:keys [genesis]}]
          (let [trits (mapv :trit sections)
                conserved? (gf3-conserved? trits)]
            (aor/result! node
              {:genesis genesis
               :sections sections
               :trits trits
               :sum (reduce + trits)
               :conserved? conserved?}))))))

(defn -main [& args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc DerivationModule {:tasks 4 :threads 2})
    (let [mgr (aor/agent-manager ipc (rama/get-module-name DerivationModule))
          agent (aor/agent-client mgr "derivation-agent")
          result (aor/agent-invoke agent {:intent (or (first args) "test")})]
      (println "=== Derivation Result ===")
      (println "Genesis:" (format "0x%X" (:genesis result)))
      (println "Trits:" (:trits result) "Sum:" (:sum result))
      (println "GF(3) Conserved?" (:conserved? result))
      (doseq [s (:sections result)]
        (println " " (:stalk s) "→" (:data s)))
      result)))

(comment (-main "hello"))
