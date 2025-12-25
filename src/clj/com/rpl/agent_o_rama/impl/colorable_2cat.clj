(ns com.rpl.agent-o-rama.impl.colorable-2cat
  "2-categorical structure for agent graphs with deterministic coloring and derangements.
   
   Implements colorable-derangeable-2cat interface:
   - 0-cells: Agent graphs
   - 1-cells: Graph morphisms (functors between agent graphs)
   - 2-cells: Natural transformations between morphisms
   
   Features:
   - gay-mcp style GF(3) coloring with SplitMixTernary seeds
   - Derangement permutations for agent shuffling
   - Rezk completion: Iso ≃ Id (categorical isomorphisms = type identities)
   - Unworld seed chaining for derivational consistency"
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.types :as aor-types]))

;;; GF(3) Color Algebra ;;;

(def ^:const GF3-ZERO 0)
(def ^:const GF3-PLUS 1)
(def ^:const GF3-MINUS 2)

(defn gf3-add
  "Addition in GF(3): (a + b) mod 3"
  ^long [^long a ^long b]
  (mod (+ a b) 3))

(defn gf3-neg
  "Negation in GF(3): -a mod 3"
  ^long [^long a]
  (mod (- 3 a) 3))

(defn gf3-mul
  "Multiplication in GF(3): (a * b) mod 3"
  ^long [^long a ^long b]
  (mod (* a b) 3))

;;; SplitMixTernary PRNG for deterministic coloring ;;;

(defn splitmix64-next
  "SplitMix64 step for seed advancement"
  ^long [^long seed]
  (let [z (unchecked-add seed 0x9e3779b97f4a7c15)]
    (-> z
        (bit-xor (unsigned-bit-shift-right z 30))
        (unchecked-multiply 0xbf58476d1ce4e5b9)
        (#(bit-xor % (unsigned-bit-shift-right % 27)))
        (unchecked-multiply 0x94d049bb133111eb)
        (#(bit-xor % (unsigned-bit-shift-right % 31))))))

(defn splitmix-ternary
  "Extract GF(3) trit from seed"
  ^long [^long seed]
  (mod (splitmix64-next seed) 3))

(defn derive-seed
  "Unworld-style seed derivation: chain seeds deterministically"
  ^long [^long parent-seed ^long child-index]
  (splitmix64-next (unchecked-add parent-seed child-index)))

;;; Derangement Generator ;;;

(defn derangement?
  "Check if permutation is a derangement (no fixed points)"
  [perm]
  (not-any? (fn [[i v]] (= i v)) (map-indexed vector perm)))

(defn generate-derangement
  "Generate deterministic derangement of n elements using seed.
   Uses Sattolo's algorithm variant for fixed-point-free permutation."
  [n ^long seed]
  (if (< n 2)
    (throw (h/ex-info "Derangement requires n >= 2" {:n n}))
    (loop [arr (vec (range n))
           i (dec n)
           s seed]
      (if (zero? i)
        (if (derangement? arr) arr (recur (vec (range n)) (dec n) (splitmix64-next s)))
        (let [next-seed (splitmix64-next s)
              j (mod (Math/abs ^long next-seed) i)]
          (recur (assoc arr i (arr j) j (arr i))
                 (dec i)
                 next-seed))))))

;;; 2-Category Protocol ;;;

(defprotocol Colorable2Cat
  "2-categorical structure with GF(3) coloring"
  
  (color-of [this]
    "Return GF(3) color {0,1,2} of this cell")
  
  (seed-of [this]
    "Return deterministic seed for derivations")
  
  (compose-1 [this other]
    "Horizontal composition of 1-cells (functorial)")
  
  (compose-2 [this other]
    "Vertical composition of 2-cells (natural transformation)")
  
  (is-identity? [this]
    "Rezk completion: true if this represents Id")
  
  (is-isomorphism? [this]
    "Rezk completion: true if this is invertible (Iso ≃ Id)"))

;;; Cell Records ;;;

(defrecord ZeroCell [graph-id color seed]
  Colorable2Cat
  (color-of [_] color)
  (seed-of [_] seed)
  (compose-1 [_ _] (throw (h/ex-info "Cannot compose 0-cells horizontally" {})))
  (compose-2 [_ _] (throw (h/ex-info "Cannot compose 0-cells vertically" {})))
  (is-identity? [_] true)
  (is-isomorphism? [_] true))

(defrecord OneCell [source target morphism-fn color seed]
  Colorable2Cat
  (color-of [_] color)
  (seed-of [_] seed)
  (compose-1 [this other]
    (when-not (= target (:source other))
      (throw (h/ex-info "1-cells not composable" {:target target :source (:source other)})))
    (->OneCell source
               (:target other)
               (comp (:morphism-fn other) morphism-fn)
               (gf3-add color (:color other))
               (derive-seed seed (:seed other))))
  (compose-2 [_ _] (throw (h/ex-info "Use 2-cells for vertical composition" {})))
  (is-identity? [_] (= source target))
  (is-isomorphism? [this] (is-identity? this)))

(defrecord TwoCell [source-1cell target-1cell transformation-fn color seed]
  Colorable2Cat
  (color-of [_] color)
  (seed-of [_] seed)
  (compose-1 [this other]
    (->TwoCell (compose-1 source-1cell (:source-1cell other))
               (compose-1 target-1cell (:target-1cell other))
               (fn [x] ((:transformation-fn other) (transformation-fn x)))
               (gf3-add color (:color other))
               (derive-seed seed (:seed other))))
  (compose-2 [this other]
    (when-not (= target-1cell (:source-1cell other))
      (throw (h/ex-info "2-cells not vertically composable" {})))
    (->TwoCell source-1cell
               (:target-1cell other)
               (comp (:transformation-fn other) transformation-fn)
               (gf3-add color (:color other))
               (derive-seed seed (:seed other))))
  (is-identity? [_] (= source-1cell target-1cell))
  (is-isomorphism? [this] (is-identity? this)))

;;; Constructors ;;;

(defn mk-zero-cell
  "Create 0-cell (agent graph) with deterministic color from seed"
  [graph-id ^long seed]
  (->ZeroCell graph-id (splitmix-ternary seed) seed))

(defn mk-one-cell
  "Create 1-cell (morphism) between agent graphs"
  [source target morphism-fn ^long seed]
  (->OneCell source target morphism-fn (splitmix-ternary seed) seed))

(defn mk-two-cell
  "Create 2-cell (natural transformation) between morphisms"
  [source-1cell target-1cell transformation-fn ^long seed]
  (->TwoCell source-1cell target-1cell transformation-fn (splitmix-ternary seed) seed))

;;; Derangeable Agent Shuffling ;;;

(defn shuffle-agents
  "Apply derangement to agent collection using deterministic seed.
   Ensures no agent stays in original position (Rezk: permutation ≃ reindexing)."
  [agents ^long seed]
  (let [n (count agents)
        perm (generate-derangement n seed)]
    (mapv #(nth agents %) perm)))

;;; Color Conservation ;;;

(defn conserved?
  "Check GF(3) conservation: sum of colors = 0 (mod 3)"
  [cells]
  (zero? (reduce gf3-add 0 (map color-of cells))))

(defn triadic-partition
  "Partition cells into three color classes"
  [cells]
  (group-by color-of cells))

;;; Integration Hook ;;;

(defn wrap-agent-graph-with-2cat
  "Wrap resolved agent graph with 2-categorical structure"
  [resolved-graph ^long seed]
  (let [graph-id (:uuid resolved-graph)]
    {:zero-cell (mk-zero-cell graph-id seed)
     :graph resolved-graph
     :derivation-seed seed}))
