# Ontology Index

The pre-agent ontology for Agent-o-rama, organized by abstraction layer.

## Layer Hierarchy

```
Layer 4: EMERGENT      agent, skill, experiment
            ↑
Layer 3: OPERATIONAL   node, emit, aggregation, result
            ↑
Layer 2: SHEAF         stalk, section, cohomology
            ↑
Layer 1: DERIVATIONAL  derivation, chain
            ↑
Layer 0: PRE-ONTOLOGICAL  seed, trit, γ (gamma)
```

## Stability Invariants

These invariants MUST hold across all layers:

| ID | Invariant | Formula | Verified By |
|----|-----------|---------|-------------|
| I1 | GF(3) Conservation | `Σ trits ≡ 0 (mod 3)` | aggregation, trifurcate |
| I2 | Determinism | `derive(s,t) = derive(s,t)` | seed chaining |
| I3 | Order Independence (SPI) | `parallel(f) = sequential(f)` | spi-parallel-verify |
| I4 | Gluing (Cocycle) | `g_ij ∘ g_jk = g_ik` | cohomology check |
| I5 | Bisimulation | `A ~ B ⟺ ∀obs. obs(A) = obs(B)` | bisimulation-game |

## Layer 0: Pre-Ontological

The absolute primitives. No further decomposition.

| Term | Type | Definition |
|------|------|------------|
| [seed](seed.md) | uint64 | Deterministic state replacing time |
| [trit](trit.md) | {-1, 0, +1} | GF(3) charge element |
| γ (gamma) | constant | 0x9E3779B97F4A7C15 (golden ratio bits) |

### Axioms
```
A0: Seeds are 64-bit unsigned integers
A1: Trits form the field GF(3) under modular arithmetic
A2: γ is the fractional part of the golden ratio × 2⁶⁴
```

## Layer 1: Derivational

Computation as seed transformation.

| Term | Type | Definition |
|------|------|------------|
| [derivation](derivation.md) | (Seed × Trit) → (Seed × Section) | Fundamental computation unit |
| chain | [Seed] | Sequence of derived seeds |

### Rules
```
D1: seed_{n+1} = splitmix64(seed_n ⊕ (trit_n × γ))
D2: Derivation replaces temporal succession
D3: Same genesis seed → identical chains (determinism)
```

## Layer 2: Sheaf-Theoretic

Local-to-global data organization.

| Term | Type | Definition |
|------|------|------------|
| [stalk](stalk.md) | Set(Section) | Collection of sections over one trit |
| [section](section.md) | local data | Output of derivation, can glue |
| cohomology | (H⁰, H¹) | Global sections and obstructions |

### Sheaf Condition
```
S1: Sections in overlapping stalks must agree (cocycle)
S2: H⁰ = global sections (glued outputs)
S3: H¹ = obstructions (composition failures)
```

### Stalk Distribution (2-3-2)
```
MINUS:   2 elements, trit=-1, role=validator
ERGODIC: 3 elements, trit=0,  role=coordinator
PLUS:    2 elements, trit=+1, role=generator

Verification: 2(-1) + 3(0) + 2(+1) = 0 ✓
```

## Layer 3: Operational

Agent-o-rama execution primitives.

| Term | Type | Definition |
|------|------|------------|
| [agent-node](agent-node.md) | function | Computation vertex in graph |
| [agent-emit](agent-emit.md) | operation | Dataflow between nodes |
| [aggregation](aggregation.md) | pattern | Gluing via scatter-gather |
| [agent-result](agent-result.md) | operation | Terminal global section |

### Correspondence
```
O1: node ≅ section-producer (derivation wrapped)
O2: emit ≅ stalk transition (trit change)
O3: aggregation ≅ gluing (cocycle check)
O4: result ≅ global section (H⁰ element)
```

## Layer 4: Emergent

Observable patterns, not primitives.

| Term | Type | Definition |
|------|------|------------|
| [agent](agent.md) | fiber bundle | Observation of derivation bundle over trit poset |
| skill | executable section | Self-contained knowledge unit |
| [experiment](experiment.md) | comparison | Derivation chain evaluation |

### Emergence Rules
```
E1: Agent = bundle of stalks over {-1, 0, +1}
E2: Skill = section that can be loaded and executed
E3: Observation collapses derivation to result
```

## Trifurcation (Mandatory Pattern)

Every operation MUST split into three sub-derivations:

```
         intent
            │
   ┌────────┼────────┐
   ▼        ▼        ▼
MINUS    ERGODIC   PLUS
(-1)      (0)      (+1)
   │        │        │
   └────────┼────────┘
            ▼
       aggregate
     (verify Σ=0)
```

## Cross-References

### By Relationship
- **Contains**: agent → node → emit/result
- **Produces**: derivation → section → global section
- **Indexes**: trit → stalk → section
- **Glues**: section × section → cohomology → result

### By Skill
| Skill | Layer | Role |
|-------|-------|------|
| unworld | 1 | Derivational succession |
| sheaf-cohomology | 2 | Gluing verification |
| bisimulation-game | 2 | Observational equivalence |
| spi-parallel-verify | 3 | Order independence |
| gflownet | 4 | Reward-proportional sampling |

## Verification Checklist

Before any operation completes:

- [ ] GF(3) sum ≡ 0 (mod 3)
- [ ] All three trits represented (trifurcation)
- [ ] Cocycle condition satisfied (sections glue)
- [ ] Deterministic (same seed → same result)
- [ ] Order-independent (SPI holds)

## File Listing

```
dev/terms/
├── ONTOLOGY.md          # This index
├── seed.md              # Layer 0
├── trit.md              # Layer 0
├── derivation.md        # Layer 1
├── stalk.md             # Layer 2
├── section.md           # Layer 2
├── agent-node.md        # Layer 3
├── agent-emit.md        # Layer 3
├── aggregation.md       # Layer 3
├── agent-result.md      # Layer 3
├── agent.md             # Layer 4
└── ... (58 total terms)
```
