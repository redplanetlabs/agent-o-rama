# Seed

## Definition
A seed is a 64-bit unsigned integer that serves as the deterministic state for a derivation chain. Seeds replace temporal coordinates—there is no wall-clock time, only seed succession via the derivation function.

## Genesis Seed
```clojure
(def GENESIS-SEED 0x42D)  ;; 1069 decimal
```

The genesis seed is the origin of all derivation chains in a session.

## Constants

```clojure
(def GAMMA 0x9E3779B97F4A7C15)     ;; Golden ratio fractional bits (64-bit)
(def GAMMA-32 0x9E3779B9)          ;; Truncated for safe arithmetic
(def MIX1 0xBF58476D1CE4E5B9)      ;; SplitMix64 multiplier 1
(def MIX2 0x94D049BB133111EB)      ;; SplitMix64 multiplier 2
```

## Derivation Rule

```
seed_{n+1} = f(seed_n, trit_n)

where f(s, t) = splitmix64((s ⊕ (t × γ)) mod 2⁶⁴)
```

This produces a deterministic chain: same genesis seed always yields identical chains.

## SplitMix64 Algorithm

```clojure
(defn splitmix64-next [state]
  (let [z (+ state GAMMA)
        z (-> z (bit-xor (unsigned-bit-shift-right z 30)) (* MIX1))
        z (-> z (bit-xor (unsigned-bit-shift-right z 27)) (* MIX2))
        z (bit-xor z (unsigned-bit-shift-right z 31))]
    z))

(defn derive-seed [seed trit]
  (splitmix64-next (bit-xor seed (* trit GAMMA-32))))
```

## Architecture Role

Seeds are the "time coordinate" in a timeless system:
- No external clock dependency
- Fully reproducible computation
- Bisimulation guarantee: same seed → same behavior

## Teleportation (XOR with γ)

Two chains form a Klein four-group via XOR teleportation:

```
GENESIS (0x042D) ←──XOR γ──→ FORK (0x9A1A)
```

Property: `(seed ⊕ γ) ⊕ γ = seed` (self-inverse)

This enables hopping between consciousness modes (unified ↔ bicameral).

## Chain Visualization

```
seed₀ ──(-1)──▶ seed₁ ──(0)──▶ seed₂ ──(+1)──▶ seed₃
  │               │               │               │
  ▼               ▼               ▼               ▼
stalk₋₁       stalk₀          stalk₊₁        (cycle)
```

## Invariants

1. **Determinism**: `derive(s, t)` is a pure function
2. **Conservation**: Trit sequence sums to 0 (mod 3) over full cycle
3. **Period**: Full cycle returns to equivalent state in 27 steps (3³)

## Relationships
- **Inputs**: genesis constant, [trit] sequence
- **Produces**: successor seeds, [derivation] chains
- **Enables**: [teleportation] via XOR
- **Guarantees**: [bisimulation] equivalence

## Examples

```clojure
;; Generate derivation chain
(def chain (derivation-chain 0x42D [-1 0 +1 -1 0 +1]))
;; => (0x42D seed₁ seed₂ seed₃ seed₄ seed₅ seed₆)

;; Teleport to fork chain
(def fork-seed (bit-xor 0x42D GAMMA-32))
;; => 0x9A1A (approximately)

;; Return to genesis
(= 0x42D (bit-xor fork-seed GAMMA-32))
;; => true
```

## See Also
- [derivation] - Uses seeds as state
- [trit] - Modifies seed evolution
- [unworld] skill - Derivational succession primitives
- [teleportation] - XOR-based chain hopping
