# Derivation

## Definition
A derivation is the fundamental unit of computation that replaces temporal succession with seed-based chaining. A derivation transforms a seed into a successor seed via a trit, producing local data (a section) that can glue with other derivations.

## Formula
```
derivation: (Seed × Trit) → (Seed × Section)

seed_{n+1} = f(seed_n, trit_n)
where f(s, t) = splitmix64((s ⊕ (t × γ)) mod 2⁶⁴)
```

## Architecture Role
Derivations are the pre-ontological substrate from which agents emerge. An agent is a fiber bundle of derivations over the trit poset {-1, 0, +1}. Derivations have no inherent temporal order—only derivational order determined by seed chaining.

## Components

| Component | Type | Description |
|-----------|------|-------------|
| Seed | uint64 | Deterministic state (e.g., 0x42D) |
| Trit | {-1, 0, +1} | GF(3) charge element |
| Section | local data | Output that can glue across stalks |
| Stalk | collection | All sections over a single trit |

## Invariants

1. **GF(3) Conservation**: Σ trits ≡ 0 (mod 3) across any complete derivation chain
2. **Determinism**: Same seed + trit always produces same successor
3. **Order Independence (SPI)**: Parallel derivations commute when seeds are independent
4. **Gluing**: Sections from adjacent stalks must satisfy cocycle condition

## Relationship to Agent

```
Derivation ─────────────────────────────────────────────────▶ Agent
    │                                                            │
    │  accumulate        bundle over          emergent           │
    │  sections          trit poset           pattern            │
    ▼                                                            ▼
 Section ──────glue──────▶ Global Section ──────result!──────▶ Output
```

An agent is the *observation* of a derivation bundle, not a primitive.

## Key Operations

| Operation | Derivation View | Agent View |
|-----------|-----------------|------------|
| Start | `(seed₀, trit₀)` | `agent-invoke` |
| Step | `f(seed, trit) → seed'` | `emit!` |
| Branch | Trifurcate into 3 stalks | `agg-start-node` |
| Terminate | Global section exists | `result!` |
| Teleport | XOR seed with γ | `fork` |

## Clojure Implementation

```clojure
(def GAMMA 0x9E3779B97F4A7C15)

(defn derive-seed [seed trit]
  (let [adjusted (bit-xor seed (* trit GAMMA))]
    (splitmix64-next adjusted)))

(defn derivation-chain [genesis-seed trits]
  (reductions derive-seed genesis-seed trits))
```

## Trifurcation (Mandatory Pattern)

Every derivation MUST split into three sub-derivations:

```
                    derivation(seed, intent)
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        derive(seed,-1)  derive(seed,0)  derive(seed,+1)
           MINUS          ERGODIC           PLUS
          validate       coordinate        generate
              │               │               │
              └───────────────┼───────────────┘
                              ▼
                    aggregate (verify Σ=0)
```

## Relationships
- **Replaces**: temporal succession, wall-clock time
- **Generates**: agent, node, emit, result (as emergent patterns)
- **Uses**: [trit], [seed], [section], [stalk]
- **Used by**: [agent] (as fiber bundle), [experiment] (as derivation comparison)

## Examples
- Genesis: `examples/clj/src/com/rpl/agent/CONTINUITY.md`
- Teleportation: `examples/clj/src/com/rpl/agent/teleport.clj`
- Trifurcation: `examples/clj/src/com/rpl/agent/topos_trifurcate.clj`

## See Also
- [unworld] skill - Derivational succession primitives
- [glass-bead-game] skill - World hopping via triangle inequality
- [bisimulation-game] skill - Observational equivalence of derivation chains
