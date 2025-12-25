---
name: finder-color-walk
description: Deterministic GF(3)-balanced file coloring for macOS Finder and Google Drive. Routes files into triadic fibers with Strong Parallelism Invariance. Use for file organization, visual categorization, or synchronized local/cloud coloring.
---

# finder-color-walk

Deterministic triadic fanout over file-sets with GF(3) conservation and Strong Parallelism Invariance (SPI).

## SPI Guarantee (Order-Independent)

For a fixed file-set, policy, and seed:
- Routing is deterministic and order-independent
- Color assignment is deterministic and order-independent

Formally: any permutation of the same input file-set yields the same final mapping.

```python
# Same colors regardless of traversal order
colors_forward = compute_mapping(sorted(files), seed)
colors_reverse = compute_mapping(sorted(files, reverse=True), seed)
assert colors_forward == colors_reverse  # SPI holds
```

## GF(3) Conservation (gf3_balanced Policy)

Three fibers F0, F1, F2 with equal length N. For each index i:

```
t0(i) + t1(i) + t2(i) ≡ 0 (mod 3)
```

Implementation: choose t0, t1 deterministically; set t2 := (-t0 - t1) mod 3.

```python
def gf3_balanced_triplet(seed, p0, p1, p2):
    t0 = trit(f"{seed}::0::{p0}")
    t1 = trit(f"{seed}::1::{p1}")
    t2 = (-t0 - t1) % 3  # GF(3) conservation
    return t0, t1, t2
```

## Triadic Router (3 Fibers)

Route files into 3 balanced fibers using SHA-256 hashing:

```bash
bb triadic_router.bb --root . --out fibers.json
```

Output: `{ "fibers": [[...], [...], [...]], "gf3_checksum": 0 }`

## Finder Label Mapping (None/Green/Blue)

| Trit | Finder Label | Color | Role |
|------|-------------|-------|------|
| 0 | None | - | ERGODIC |
| 1 | Green | 🟢 | PLUS |
| 2 | Blue | 🔵 | MINUS |

Apply via `xattr` (macOS) or MCP commands (Drive).

## Drive Integration (drive_color_walk.py)

Bridge to Google Workspace MCP for cloud file coloring:

```python
from drive_color_walk import color_drive_files, generate_mcp_commands

# Get files via google-workspace MCP
files = list_drive_items(user_google_email, folder_id="root")

# Apply GF(3)-balanced coloring
colored = color_drive_files(files, seed="0x42D", policy="gf3_balanced")

# Generate MCP commands
commands = generate_mcp_commands(colored, user_google_email)
# Applies starred + description metadata for "coloring"
```

## Justfile Recipes

```bash
# Route files into 3 GF(3)-balanced fibers
just route ROOT="."

# Walk with raw policy (SPI holds, GF(3) not guaranteed)
just walk_raw

# Walk with GF(3)-balanced policy (SPI + GF(3) both hold)
just walk_gf3

# Verify SPI invariant (raw policy)
just spi_raw

# Verify SPI + GF(3) invariants (gf3_balanced policy)
just spi_gf3

# Full pipeline: route → walk → verify
just finder-full ROOT="."
```

## Files (in skills/finder-color-walk/)

| File | Description |
|------|-------------|
| `triadic_router.bb` | GF(3)-balanced routing into 3 fibers |
| `finder_color_walk.bb` | Single-stream walk (policy=raw, SPI only) |
| `parallel_walk.bb` | 3-stream walk + gf3_balanced coloring |
| `drive_color_walk.py` | Google Drive integration via MCP |
| `spi_test.py` | Verifies SPI + GF(3) invariants |
| `schema.jl` | Catlab ACSet schema: FileColorWalk |

## Policy Comparison

| Policy | SPI | GF(3) | Use Case |
|--------|-----|-------|----------|
| `raw` | ✓ | ✗ | Fast, per-file coloring |
| `gf3_balanced` | ✓ | ✓ | Triadic parallel processing |

## Related Skills

| Skill | Trit | Integration |
|-------|------|-------------|
| google-workspace | 0 | Drive file listing + update via MCP |
| gay-mcp | +1 | SplitMix64 algorithm, trit-to-hue mapping |
| triad-interleave | +1 | Schedule file walks in balanced triplets |
| bisimulation-game | -1 | Verify file state equivalence |
| spi-parallel-verify | -1 | Strong Parallelism Invariance verification |
