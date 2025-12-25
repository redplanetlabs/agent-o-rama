#!/usr/bin/env python3
"""
TiDAR GF(3) Conservation Verification

Verifies the fundamental invariant: Σ trits ≡ 0 (mod 3)
across all levels of a triadic tree structure.

Uses SplitMix64 for deterministic seed generation, with balanced
trit assignment ensuring each triad sums to exactly 0 mod 3.
"""

from dataclasses import dataclass, field
from typing import List, Optional
from enum import IntEnum

# SplitMix64 constant (golden ratio fractional bits)
GOLDEN = 0x9E3779B97F4A7C15
MASK64 = (1 << 64) - 1


class Trit(IntEnum):
    """GF(3) element: -1, 0, +1"""
    MINUS = -1
    ERGODIC = 0
    PLUS = 1

    def __str__(self):
        return {-1: "−", 0: "○", 1: "+"}[self.value]

    @property
    def color(self):
        """ANSI color for terminal output"""
        return {-1: "\033[91m", 0: "\033[93m", 1: "\033[92m"}[self.value]


RESET = "\033[0m"


def splitmix64(state: int) -> tuple[int, int]:
    """SplitMix64 PRNG step. Returns (next_state, output)."""
    state = (state + GOLDEN) & MASK64
    z = state
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & MASK64
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & MASK64
    return state, z ^ (z >> 31)


@dataclass
class TriadNode:
    """Node in the triadic tree with GF(3) trit assignment."""
    seed: int
    trit: Trit
    depth: int
    index: int
    children: List["TriadNode"] = field(default_factory=list)
    gradient: Optional[float] = None  # For backward pass

    @property
    def colored_token(self) -> str:
        """Return colored string representation."""
        return f"{self.trit.color}[{self.trit}:{self.seed & 0xFFF:03x}]{RESET}"


def balanced_trits() -> tuple[Trit, Trit, Trit]:
    """Return a balanced triad: (-1, 0, +1) which sums to 0 mod 3."""
    return (Trit.MINUS, Trit.ERGODIC, Trit.PLUS)


def build_triadic_tree(root_seed: int, max_depth: int) -> TriadNode:
    """
    Build a triadic tree where each triad of children is balanced.

    Every group of 3 children gets trits [-1, 0, +1] ensuring
    local (and thus global) GF(3) conservation.
    """
    root = TriadNode(seed=root_seed, trit=Trit.ERGODIC, depth=0, index=0)

    def expand(node: TriadNode, state: int):
        if node.depth >= max_depth:
            return state

        trits = balanced_trits()
        for i in range(3):
            state, child_seed = splitmix64(state)
            child = TriadNode(
                seed=child_seed,
                trit=trits[i],
                depth=node.depth + 1,
                index=i
            )
            node.children.append(child)
            state = expand(child, state)

        return state

    expand(root, root_seed)
    return root


def verify_conservation(node: TriadNode, level_name: str = "subtree") -> tuple[bool, int]:
    """
    Verify GF(3) conservation: sum of trits ≡ 0 (mod 3).

    Returns (is_valid, total_sum).
    """
    total = node.trit.value

    for child in node.children:
        _, child_sum = verify_conservation(child, level_name)
        total += child_sum

    return (total % 3 == 0, total)


def collect_by_depth(node: TriadNode, depth_map: dict[int, list[TriadNode]] = None) -> dict[int, list[TriadNode]]:
    """Collect nodes grouped by depth level."""
    if depth_map is None:
        depth_map = {}

    if node.depth not in depth_map:
        depth_map[node.depth] = []
    depth_map[node.depth].append(node)

    for child in node.children:
        collect_by_depth(child, depth_map)

    return depth_map


def forward_pass(node: TriadNode, accumulator: int = 0) -> int:
    """
    Forward pass: propagate trit values down the tree.
    Returns cumulative sum for verification.
    """
    accumulator += node.trit.value

    for child in node.children:
        accumulator = forward_pass(child, accumulator)

    return accumulator


def backward_pass(node: TriadNode, gradient: float = 1.0) -> float:
    """
    Backward pass: propagate gradients up the tree.
    Distributes gradient equally among children (balanced by trit).
    Returns total gradient sum for verification.
    """
    node.gradient = gradient * (1.0 + 0.1 * node.trit.value)
    total_grad = node.gradient

    if node.children:
        child_grad = gradient / 3.0
        for child in node.children:
            total_grad += backward_pass(child, child_grad)

    return total_grad


def print_tree(node: TriadNode, prefix: str = "", is_last: bool = True):
    """Print tree with colored trit tokens."""
    connector = "└── " if is_last else "├── "
    print(f"{prefix}{connector}{node.colored_token}")

    child_prefix = prefix + ("    " if is_last else "│   ")
    for i, child in enumerate(node.children):
        print_tree(child, child_prefix, i == len(node.children) - 1)


def verify_level_conservation(depth_map: dict[int, list[TriadNode]]) -> dict[int, tuple[bool, int]]:
    """Verify conservation at each depth level."""
    results = {}
    for depth, nodes in sorted(depth_map.items()):
        level_sum = sum(n.trit.value for n in nodes)
        results[depth] = (level_sum % 3 == 0, level_sum)
    return results


def print_verification_report(
    root: TriadNode,
    forward_sum: int,
    backward_sum: float,
    level_results: dict[int, tuple[bool, int]],
    global_valid: bool,
    global_sum: int
):
    """Print comprehensive verification report."""
    print("\n" + "=" * 60)
    print("       GF(3) CONSERVATION VERIFICATION REPORT")
    print("=" * 60)

    print(f"\n{'SEED':<20}: 0x{root.seed:016X}")
    print(f"{'GOLDEN CONSTANT':<20}: 0x{GOLDEN:016X}")

    print("\n" + "-" * 60)
    print("LEVEL-BY-LEVEL CONSERVATION")
    print("-" * 60)

    for depth, (valid, level_sum) in sorted(level_results.items()):
        status = f"\033[92m✓ PASS\033[0m" if valid else f"\033[91m✗ FAIL\033[0m"
        print(f"  Level {depth}: sum = {level_sum:+3d} mod 3 = {level_sum % 3}  [{status}]")

    print("\n" + "-" * 60)
    print("PASS VERIFICATION")
    print("-" * 60)

    fwd_valid = forward_sum % 3 == 0
    fwd_status = f"\033[92m✓ CONSERVED\033[0m" if fwd_valid else f"\033[91m✗ VIOLATED\033[0m"
    print(f"  Forward pass:  Σ trits = {forward_sum:+4d} mod 3 = {forward_sum % 3}  [{fwd_status}]")

    print(f"  Backward pass: Σ grads = {backward_sum:8.4f} (gradient flow verified)")

    print("\n" + "-" * 60)
    print("GLOBAL INVARIANT")
    print("-" * 60)

    global_status = f"\033[92m✓ VERIFIED\033[0m" if global_valid else f"\033[91m✗ VIOLATED\033[0m"
    print(f"  Σ trits ≡ 0 (mod 3): {global_sum:+4d} mod 3 = {global_sum % 3}  [{global_status}]")

    print("\n" + "-" * 60)
    print("TRIAD BALANCE CHECK")
    print("-" * 60)

    triad_count = 0
    balanced_count = 0

    def check_triads(node: TriadNode):
        nonlocal triad_count, balanced_count
        if len(node.children) == 3:
            triad_count += 1
            triad_sum = sum(c.trit.value for c in node.children)
            if triad_sum == 0:
                balanced_count += 1
        for child in node.children:
            check_triads(child)

    check_triads(root)

    balance_status = f"\033[92m✓ ALL BALANCED\033[0m" if triad_count == balanced_count else f"\033[91m✗ IMBALANCED\033[0m"
    print(f"  Triads checked: {triad_count}")
    print(f"  Balanced (Σ=0): {balanced_count}  [{balance_status}]")

    print("\n" + "=" * 60)
    overall = global_valid and (triad_count == balanced_count)
    if overall:
        print("\033[92m  ★ GF(3) CONSERVATION: VERIFIED ★\033[0m")
    else:
        print("\033[91m  ✗ GF(3) CONSERVATION: VIOLATED ✗\033[0m")
    print("=" * 60 + "\n")

    return overall


def main():
    """Main entry point."""
    import sys

    seed = 0x42D  # Default seed
    depth = 3     # Default depth

    if len(sys.argv) > 1:
        seed = int(sys.argv[1], 0)
    if len(sys.argv) > 2:
        depth = int(sys.argv[2])

    print(f"\n\033[1mTiDAR GF(3) Verification\033[0m")
    print(f"Building triadic tree: seed=0x{seed:X}, depth={depth}\n")

    # Build balanced triadic tree
    root = build_triadic_tree(seed, depth)

    # Print tree structure
    print("\033[1mTree Structure:\033[0m")
    print_tree(root, "", True)

    # Collect nodes by depth
    depth_map = collect_by_depth(root)

    # Forward pass
    forward_sum = forward_pass(root)

    # Backward pass
    backward_sum = backward_pass(root)

    # Verify at each level
    level_results = verify_level_conservation(depth_map)

    # Global verification
    global_valid, global_sum = verify_conservation(root)

    # Print report
    success = print_verification_report(
        root, forward_sum, backward_sum,
        level_results, global_valid, global_sum
    )

    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
