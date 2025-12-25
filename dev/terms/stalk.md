# Stalk

## Definition
A stalk is a collection of local data (sections) associated with a single trit value in the sheaf structure. Stalks partition computational resources by role: validators (MINUS), coordinators (ERGODIC), and generators (PLUS).

## Structure

```
Sheaf over Trit Poset
         │
    ┌────┴────┬────────────┐
    ▼         ▼            ▼
Stalk₋₁   Stalk₀      Stalk₊₁
 MINUS    ERGODIC       PLUS
   │         │            │
   ▼         ▼            ▼
[sections] [sections] [sections]
```

## Stalk Contents

| Stalk | Trit | Roles | Example MCP Servers | Example Skills |
|-------|------|-------|---------------------|----------------|
| MINUS | -1 | validator, parser, analyzer | babashka, tree-sitter | spi-parallel-verify, bisimulation-game |
| ERGODIC | 0 | coordinator, synthesizer, simulator | deepwiki, playwright, signal | glass-bead-game, triad-interleave |
| PLUS | +1 | generator, executor, creator | aptos, exa | gflownet, self-evolving-agent |

## Architecture Role

Stalks organize the pre-sheaf structure that underlies agent computation. When sections from different stalks can glue together (satisfy the cocycle condition), we obtain a global section—the observable output.

## Gluing Condition

Sections from adjacent stalks must be compatible on overlaps:

```
g_ij ∘ g_jk = g_ik  (cocycle condition)
```

In practice: concept overlap between skill analyses must exceed threshold for consistent composition.

## Distribution Patterns

### 2-3-2 Pattern (7 elements)
```clojure
{:minus 2, :ergodic 3, :plus 2}
;; Sum: 2×(-1) + 3×(0) + 2×(+1) = 0 ✓
```

### Balanced Triad (3 elements)
```clojure
{:minus 1, :ergodic 1, :plus 1}
;; Sum: (-1) + (0) + (+1) = 0 ✓
```

## Clojure Implementation

```clojure
(def SKILL-STALKS
  {:minus   {:trit -1 :roles [:validator :parser :analyzer]}
   :ergodic {:trit  0 :roles [:coordinator :synthesizer :simulator]}
   :plus    {:trit +1 :roles [:generator :executor :creator]}})

(defn skill->stalk [skill-name]
  (let [n (str/lower-case skill-name)]
    (cond
      (or (str/includes? n "verify") (str/includes? n "valid")
          (str/includes? n "check") (str/includes? n "parse"))
      :minus
      
      (or (str/includes? n "evolv") (str/includes? n "gen")
          (str/includes? n "creat") (str/includes? n "flow"))
      :plus
      
      :else :ergodic)))
```

## Relationships
- **Contains**: [section] (local data)
- **Indexed by**: [trit] (charge value)
- **Glues via**: [cohomology] (obstruction detection)
- **Bundles into**: [agent] (fiber bundle over trit poset)

## See Also
- [trit] - The index set for stalks
- [section] - Elements within a stalk
- [derivation] - Produces sections into stalks
- [sheaf-cohomology] skill - Verifies gluing
