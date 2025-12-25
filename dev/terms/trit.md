# Trit

## Definition
A trit is an element of the Galois field GF(3), taking values {-1, 0, +1}. Trits are the fundamental charge units that classify computational roles and ensure conservation laws hold across derivation chains.

## Values

| Value | Name | Role | Operations |
|-------|------|------|------------|
| -1 | MINUS | Validator | verify, parse, analyze, read |
| 0 | ERGODIC | Coordinator | synthesize, simulate, coordinate |
| +1 | PLUS | Generator | create, execute, write |

## Arithmetic (GF(3))

```clojure
(defn gf3-add [a b]
  (let [sum (+ a b)]
    (cond (> sum 1)  (- sum 3)   ; wrap +2 → -1
          (< sum -1) (+ sum 3)   ; wrap -2 → +1
          :else      sum)))

(defn gf3-negate [a]
  (- a))

(defn gf3-conserved? [trits]
  (zero? (mod (reduce + trits) 3)))
```

## Addition Table

```
  + │ -1   0  +1
────┼────────────
 -1 │ +1  -1   0
  0 │ -1   0  +1
 +1 │  0  +1  -1
```

## Conservation Law

**Invariant**: For any complete operation, Σ trits ≡ 0 (mod 3).

This ensures every PLUS is balanced by a MINUS (directly or via ERGODIC mediation).

## Architecture Role

Trits classify:
- **Stalks** in the sheaf structure (local data collections)
- **Sub-agents** in trifurcation (MINUS/ERGODIC/PLUS)
- **MCP servers** by role (validator/coordinator/generator)
- **Skills** by function type

## Stalk Distribution (2-3-2 Pattern)

For balanced composition across 7 elements:

```
MINUS (-1):   2 elements  (validators)
ERGODIC (0): 3 elements  (coordinators)  
PLUS (+1):   2 elements  (generators)

Verification: 2×(-1) + 3×(0) + 2×(+1) = -2 + 0 + 2 = 0 ✓
```

## Möbius Function Connection

The Möbius function μ(n) for n=3:
```
μ(3) = -1  (3 is prime)
```

This creates perception/action duality:
- `alice (+1) × μ(3) = alice-bob (-1)`
- `bob (-1) × μ(3) = bob-alice (+1)` (double negation)

## Relationships
- **Used by**: [derivation], [stalk], [section], [agent]
- **Conserved across**: [trifurcation], [teleportation], [aggregation]

## Examples
```clojure
;; Trifurcate pattern
(def trits [-1 0 +1])
(gf3-conserved? trits)  ; => true

;; Skill classification
(skill->stalk "self-validation-loop")  ; => :minus
(skill->stalk "glass-bead-game")       ; => :ergodic
(skill->stalk "gflownet")              ; => :plus
```

## See Also
- [derivation] - Uses trits for seed evolution
- [stalk] - Collection over a single trit
- [sheaf-cohomology] skill - Gluing verification
