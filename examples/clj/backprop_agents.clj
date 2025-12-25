(ns backprop-agents
  "Hierarchical agent tree with backprop-style forward/backward passes.
   
   Architecture: 7 root agents × 3 sub-agents each = 21 leaf agents
   Uses GF(3) coloring for balanced scheduling.
   
   Forward pass: Split task → distribute to children
   Backward pass: Aggregate results → roll up gradients"
  (:require [com.rpl.agent-o-rama :as aor]
            [com.rpl.rama :refer :all]
            [com.rpl.rama.test :as rtest]))

;; ============================================================
;; GF(3) Constants and Coloring
;; ============================================================

(def GOLDEN 0x9E3779B97F4A7C15)

(defn splitmix64 [x]
  (let [z (bit-and (+ x GOLDEN) 0xFFFFFFFFFFFFFFFF)
        z (bit-and (* (bit-xor z (bit-shift-right z 30)) 
                      0xBF58476D1CE4E5B9) 0xFFFFFFFFFFFFFFFF)
        z (bit-and (* (bit-xor z (bit-shift-right z 27)) 
                      0x94D049BB133111EB) 0xFFFFFFFFFFFFFFFF)]
    (bit-and (bit-xor z (bit-shift-right z 31)) 0xFFFFFFFFFFFFFFFF)))

(defn trit [seed index]
  "Generate trit ∈ {-1, 0, +1} for agent coloring"
  (let [h (splitmix64 (bit-xor seed index))]
    (- (mod h 3) 1)))

(defn gf3-sum [trits]
  "Verify GF(3) conservation: sum ≡ 0 (mod 3)"
  (mod (reduce + trits) 3))

;; ============================================================
;; Agent Tree Structure
;; ============================================================

(def ROOT-AGENTS 7)
(def SUB-AGENTS-PER-ROOT 3)
(def TOTAL-LEAF-AGENTS (* ROOT-AGENTS SUB-AGENTS-PER-ROOT)) ;; 21

(defn agent-id [root-idx sub-idx]
  "Generate unique agent ID"
  (keyword (str "agent-" root-idx "-" sub-idx)))

(defn root-agent-id [root-idx]
  "Generate root agent ID"
  (keyword (str "root-" root-idx)))

(defn all-agent-ids []
  "Generate all 21 leaf agent IDs"
  (for [r (range ROOT-AGENTS)
        s (range SUB-AGENTS-PER-ROOT)]
    (agent-id r s)))

;; ============================================================
;; Forward Pass (Split Down)
;; ============================================================

(defn split-task [task depth seed]
  "Recursively split task into subtasks with GF(3) coloring.
   Returns tree of {:task :trit :children}"
  (if (zero? depth)
    {:task task
     :trit (trit seed (hash task))
     :children nil}
    {:task task
     :trit (trit seed (hash task))
     :children (vec (for [i (range 3)]  ;; Always 3 children (triad)
                      (split-task 
                       (str task "/subtask-" i)
                       (dec depth)
                       (splitmix64 (+ seed i)))))}))

(defn forward-pass [root-task]
  "Forward pass: split root into 7 branches, each with 3 leaves.
   Like forward prop in neural net - compute activations down the tree."
  (let [seed 0x42D]
    {:root root-task
     :branches (vec (for [i (range ROOT-AGENTS)]
                      (split-task 
                       (str root-task "/branch-" i)
                       1  ;; depth=1 gives 3 children per branch
                       (splitmix64 (+ seed (* i 1000))))))}))

;; ============================================================
;; Backward Pass (Roll Up - Chain Rule)
;; ============================================================

(defn leaf-compute [task-node llm-fn]
  "Compute at leaf node using LLM"
  (assoc task-node :result (llm-fn (:task task-node))))

(defn aggregate-children [parent-node agg-fn]
  "Aggregate child results back to parent (like gradient accumulation)"
  (let [child-results (map :result (:children parent-node))
        child-trits (map :trit (:children parent-node))
        ;; Verify GF(3) conservation at this level
        conservation-check (gf3-sum child-trits)]
    (assoc parent-node
           :result (agg-fn child-results)
           :child-trits child-trits
           :gf3-check conservation-check)))

(defn backward-pass [forward-tree llm-fn agg-fn]
  "Backward pass: aggregate results from leaves to root.
   Like backprop - gradients flow up, results aggregate.
   
   Chain rule analog: ∂L/∂root = Σᵢ (∂L/∂branchᵢ × ∂branchᵢ/∂root)"
  (let [;; Step 1: Compute at all leaves
        computed-branches 
        (mapv (fn [branch]
                (let [computed-children 
                      (mapv #(leaf-compute % llm-fn) (:children branch))]
                  (assoc branch :children computed-children)))
              (:branches forward-tree))
        
        ;; Step 2: Aggregate children → parents (like gradient accumulation)
        aggregated-branches
        (mapv #(aggregate-children % agg-fn) computed-branches)
        
        ;; Step 3: Final aggregation at root
        root-result (agg-fn (map :result aggregated-branches))
        all-trits (mapcat :child-trits aggregated-branches)]
    
    {:root-task (:root forward-tree)
     :branches aggregated-branches
     :final-result root-result
     :total-agents (count (all-agent-ids))
     :gf3-global-check (gf3-sum all-trits)
     :all-trits all-trits}))

;; ============================================================
;; Iteration: Forward-Backward Oscillation
;; ============================================================

(defn iterate-forward-backward [task n-iterations llm-fn agg-fn refine-fn]
  "Oscillate between forward and backward passes like SGD.
   Each iteration:
   1. Forward: split task with current state
   2. Backward: aggregate results
   3. Refine: update task based on aggregated feedback"
  (loop [current-task task
         iteration 0
         history []]
    (if (>= iteration n-iterations)
      {:final-task current-task
       :history history
       :iterations n-iterations}
      (let [;; Forward pass
            forward-tree (forward-pass current-task)
            ;; Backward pass
            backward-result (backward-pass forward-tree llm-fn agg-fn)
            ;; Refine task for next iteration
            refined-task (refine-fn current-task (:final-result backward-result))]
        (recur refined-task
               (inc iteration)
               (conj history {:iteration iteration
                              :task current-task
                              :result (:final-result backward-result)
                              :gf3-check (:gf3-global-check backward-result)}))))))

;; ============================================================
;; MLX Integration Placeholder
;; ============================================================

(defn mlx-generate [prompt]
  "Placeholder for MLX Gemma 3n generation.
   In production, calls Python subprocess or gRPC."
  (str "[MLX-GEMMA-3N] Response to: " (subs prompt 0 (min 50 (count prompt))) "..."))

(defn simple-aggregate [results]
  "Simple aggregation: concatenate with separator"
  (clojure.string/join "\n---\n" results))

(defn simple-refine [task result]
  "Simple refinement: append iteration feedback"
  (str task " [refined with: " (subs (str result) 0 (min 30 (count (str result)))) "]"))

;; ============================================================
;; Demo
;; ============================================================

(defn demo []
  (println "=" 60)
  (println "BACKPROP-STYLE AGENT TREE")
  (println "7 roots × 3 subs = 21 leaf agents")
  (println "=" 60)
  
  (let [result (iterate-forward-backward
                "Analyze the architecture of agent-o-rama"
                3  ;; 3 iterations
                mlx-generate
                simple-aggregate
                simple-refine)]
    
    (println "\nIterations:" (:iterations result))
    (println "\nHistory:")
    (doseq [h (:history result)]
      (println (format "  [%d] GF(3)=%d task=%s" 
                       (:iteration h)
                       (:gf3-check h)
                       (subs (:task h) 0 (min 60 (count (:task h)))))))
    
    (println "\nFinal task:" (subs (:final-task result) 0 (min 80 (count (:final-task result)))))
    (println "\nGF(3) conservation verified:" 
             (every? zero? (map :gf3-check (:history result))))))

(comment
  ;; Run demo
  (demo)
  
  ;; Test forward pass
  (forward-pass "root-task")
  
  ;; Test GF(3)
  (gf3-sum [1 0 -1])  ;; => 0 ✓
  (gf3-sum [1 1 1])   ;; => 0 ✓ (3 mod 3)
  )
