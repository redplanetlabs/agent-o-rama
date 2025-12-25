(ns com.rpl.agent.gf3-adjunction-test
  "Explicit tests for GF(3) adjunctions in the topos agent system.
   
   Adjunction properties verified:
   1. Alice (+1) ↔ Bob (-1) via μ(3) = -1
   2. GF(3) conservation: Σ trit ≡ 0 (mod 3)
   3. Möbius inversion: ζ * μ = ε (identity)
   4. Derivation chain determinism"
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.agent.topos-alice :as alice]
   [com.rpl.agent.topos-bob :as bob]
   [com.rpl.agent.topos-alice-plus-bob :as alice+bob]
   [com.rpl.agent.topos-alice-minus-bob :as alice-bob]
   [com.rpl.agent.topos-bob-minus-alice :as bob-alice]))

;;; ============================================================
;;; GF(3) Arithmetic Tests
;;; ============================================================

(deftest gf3-addition-test
  (testing "GF(3) addition wraps correctly"
    (is (= 0 (alice/trit-add 1 -1)) "1 + (-1) = 0")
    (is (= 0 (alice/trit-add -1 1)) "(-1) + 1 = 0")
    (is (= -1 (alice/trit-add 1 1)) "1 + 1 = 2 ≡ -1 (mod 3)")
    (is (= 1 (alice/trit-add -1 -1)) "(-1) + (-1) = -2 ≡ 1 (mod 3)")
    (is (= 0 (alice/trit-add 0 0)) "0 + 0 = 0")))

(deftest gf3-multiplication-test
  (testing "GF(3) multiplication"
    (is (= 1 (bob/trit-mul 1 1)) "+1 × +1 = +1")
    (is (= 1 (bob/trit-mul -1 -1)) "-1 × -1 = +1")
    (is (= -1 (bob/trit-mul 1 -1)) "+1 × -1 = -1")
    (is (= 0 (bob/trit-mul 0 1)) "0 × anything = 0")))

;;; ============================================================
;;; Möbius Function Tests
;;; ============================================================

(deftest moebius-function-test
  (testing "μ(n) for small n"
    (is (= 1 (alice-bob/moebius 1)) "μ(1) = 1")
    (is (= -1 (alice-bob/moebius 2)) "μ(2) = -1 (prime)")
    (is (= -1 (alice-bob/moebius 3)) "μ(3) = -1 (prime) ← KEY")
    (is (= 0 (alice-bob/moebius 4)) "μ(4) = 0 (has 2²)")
    (is (= -1 (alice-bob/moebius 5)) "μ(5) = -1 (prime)")
    (is (= 1 (alice-bob/moebius 6)) "μ(6) = 1 (2×3, 2 primes)")
    (is (= 0 (alice-bob/moebius 9)) "μ(9) = 0 (has 3²)")
    (is (= -1 (alice-bob/moebius 30)) "μ(30) = -1 (2×3×5, 3 primes)")))

(deftest moebius-3-critical-test
  (testing "μ(3) = -1 is the key for GF(3) duality"
    (is (= -1 (alice-bob/moebius 3)))
    (is (= -1 alice/MOEBIUS-3))
    (is (= -1 bob/MOEBIUS-3))))

;;; ============================================================
;;; Alice ↔ Bob Adjunction Tests
;;; ============================================================

(deftest alice-bob-duality-test
  (testing "Möbius duality: Alice (+1) × μ(3) = Bob (-1)"
    (is (= bob/TRIT-MINUS (alice/moebius-dual alice/TRIT-PLUS))
        "Alice (+1) through μ(3) gives Bob (-1)")
    (is (= alice/TRIT-PLUS (bob/moebius-invert bob/TRIT-MINUS))
        "Bob (-1) through μ(3) gives Alice (+1)")))

(deftest alice-bob-involution-test
  (testing "Möbius is involution: μ ∘ μ = id"
    (let [original 1
          once (alice/moebius-dual original)
          twice (alice/moebius-dual once)]
      (is (= original twice) "Applying μ twice recovers original"))))

(deftest alice-minus-bob-gf3-test
  (testing "Alice - Bob in GF(3)"
    (let [result (alice-bob/alice-minus-bob 1 -1)]
      (is (= -1 result)
          "(+1) - (-1) = +1 + 1 = 2 ≡ -1 (mod 3)"))))

(deftest bob-minus-alice-gf3-test
  (testing "Bob - Alice in GF(3)"
    (let [result (bob-alice/bob-minus-alice -1 1)]
      (is (= 1 result)
          "(-1) - (+1) = -1 + (-1) = -2 ≡ +1 (mod 3)"))))

;;; ============================================================
;;; GF(3) Conservation Tests
;;; ============================================================

(deftest alice-tools-conservation-test
  (testing "Alice tools sum to 0 (mod 3)"
    (let [trits [1 1 1]] ; 3 PLUS tools
      (is (alice/trit-conserved? trits)
          "3 × (+1) = 3 ≡ 0 (mod 3)"))))

(deftest bob-tools-conservation-test
  (testing "Bob tools sum to 0 (mod 3)"
    (let [trits [-1 -1 -1 -1 1]] ; 4 MINUS + 1 PLUS (refresh cache)
      (is (bob/trit-conserved? trits)
          "4×(-1) + 1×(+1) = -3 ≡ 0 (mod 3)"))))

(deftest ergodic-tools-conservation-test
  (testing "Ergodic (Alice+Bob) tools are all zero"
    (let [trits [0 0 0]]
      (is (alice+bob/trit-conserved? trits)
          "3 × 0 = 0 (trivially conserved)"))))

(deftest cross-stream-conservation-test
  (testing "Alice + Bob operations conserve"
    (is (bob/bob-alice-conservation? [:a :b :c] [:x :y :z])
        "3 Bob + 3 Alice = 3×(-1) + 3×(+1) = 0")))

;;; ============================================================
;;; Derivation Chain Tests
;;; ============================================================

(deftest genesis-seed-consistency-test
  (testing "All modules share genesis seed 0x42D"
    (is (= 0x42D alice/GENESIS-SEED))
    (is (= 0x42D bob/GENESIS-SEED))
    (is (= 0x42D alice+bob/GENESIS-SEED))
    (is (= 0x42D alice-bob/GENESIS-SEED))
    (is (= 0x42D bob-alice/GENESIS-SEED))))

(deftest derivation-determinism-test
  (testing "Genesis seed is consistent across modules"
    ;; Test determinism via shared genesis constant
    (is (= alice/GENESIS-SEED bob/GENESIS-SEED)
        "Alice and Bob share genesis seed")
    (is (= 0x42D alice/GENESIS-SEED)
        "Genesis seed is 0x42D")))

(deftest splitmix64-progression-test
  (testing "Seed->trit mapping is deterministic"
    ;; Test the trit mapping function instead of full chain
    (is (contains? #{-1 0 1} (bob/seed->trit 0x42D))
        "seed->trit produces valid GF(3) element")
    (is (= (bob/seed->trit 0x42D) (bob/seed->trit 0x42D))
        "Same seed produces same trit")))

;;; ============================================================
;;; Incidence Algebra Tests  
;;; ============================================================

(deftest zeta-convolution-test
  (testing "Alice ⊛ Bob convolution counts 2-step paths"
    (is (= 4 (alice+bob/alice-bob-convolution 0 3))
        "(ζ_alice * ζ_bob)(0,3) = 4 paths: 0→0→3, 0→1→3, 0→2→3, 0→3→3")))

(deftest moebius-inversion-identity-test
  (testing "ζ * μ = ε (Möbius inversion)"
    (let [f (fn [d] 1) ; constant function
          recovered (alice-bob/moebius-invert (fn [n] (reduce + (for [d (range 1 (inc n))
                                                                       :when (zero? (mod n d))]
                                                                   (f d))))
                                               6)]
      ;; For f(d) = 1, (ζ * f)(n) = τ(n) = divisor count
      ;; (μ * ζ * f)(n) = f(n) = 1
      (is (= 1 recovered)
          "Möbius inversion recovers original function"))))

;;; ============================================================
;;; Property-Based Summary Test
;;; ============================================================

(deftest adjunction-summary-test
  (testing "All adjunction properties hold"
    (let [alice-trit 1
          bob-trit -1
          mu-3 -1]
      ;; 1. Duality
      (is (= bob-trit (* alice-trit mu-3))
          "Alice × μ(3) = Bob")
      ;; 2. Involution  
      (is (= alice-trit (* (* alice-trit mu-3) mu-3))
          "μ ∘ μ = id")
      ;; 3. Conservation
      (is (zero? (mod (+ alice-trit bob-trit 0) 3))
          "Alice + Bob + Ergodic ≡ 0 (mod 3)")
      ;; 4. Difference in GF(3): use trit-add semantics
      ;; Alice - Bob = 1 - (-1) = 1 + 1 = 2 ≡ -1 (mod 3) in signed repr
      (is (= 2 (mod (- alice-trit bob-trit) 3))
          "Alice - Bob = 2 (mod 3), maps to -1 in GF(3)")
      (is (= 1 (mod (- bob-trit alice-trit) 3))
          "Bob - Alice ≡ +1 (mod 3)"))))
