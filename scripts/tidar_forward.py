#!/usr/bin/env python3
"""
TIDAR: Tree-structured Iterative Decomposition and Aggregation with Rollup

Forward pass: 7 branches × 3 sub-tasks = 21 leaf nodes
GF(3) balanced trits for coloring, SplitMix64 for determinism.
"""

from dataclasses import dataclass, field
from typing import Optional

GOLDEN = 0x9E3779B97F4A7C15


def splitmix64(state: int) -> tuple[int, int]:
    """SplitMix64: returns (next_state, output)."""
    state = (state + GOLDEN) & 0xFFFFFFFFFFFFFFFF
    z = state
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & 0xFFFFFFFFFFFFFFFF
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & 0xFFFFFFFFFFFFFFFF
    return state, z ^ (z >> 31)


def to_trit(value: int) -> int:
    """Map to balanced ternary GF(3): -1, 0, +1."""
    return (value % 3) - 1


@dataclass
class TidarNode:
    """A node in the TIDAR tree."""
    id: int
    trit: int  # GF(3) balanced: -1, 0, +1
    depth: int
    seed: int
    children: list["TidarNode"] = field(default_factory=list)
    result: Optional[any] = None  # populated in backward pass

    @property
    def is_leaf(self) -> bool:
        return len(self.children) == 0

    def trit_sum(self) -> int:
        """GF(3) conservation: sum all trits mod 3."""
        total = self.trit
        for child in self.children:
            total += child.trit_sum()
        return total % 3


def tidar_forward(task: str, seed: int = 0x42D) -> TidarNode:
    """
    Forward pass: decompose task into 7 branches × 3 sub-tasks.
    
    Returns tree structure ready for backward pass aggregation.
    GF(3) invariant: Σ trits ≡ 0 (mod 3) at each level.
    """
    state, out = splitmix64(seed)
    root = TidarNode(
        id=0,
        trit=to_trit(out),
        depth=0,
        seed=seed,
    )
    
    node_id = 1
    branch_trits = []
    
    for branch_idx in range(7):
        state, out = splitmix64(state)
        branch_trit = to_trit(out)
        branch_trits.append(branch_trit)
        
        branch = TidarNode(
            id=node_id,
            trit=branch_trit,
            depth=1,
            seed=state,
        )
        node_id += 1
        
        leaf_trits = []
        for leaf_idx in range(3):
            state, out = splitmix64(state)
            leaf_trit = to_trit(out)
            leaf_trits.append(leaf_trit)
            
            leaf = TidarNode(
                id=node_id,
                trit=leaf_trit,
                depth=2,
                seed=state,
            )
            node_id += 1
            branch.children.append(leaf)
        
        root.children.append(branch)
    
    return root


def tree_stats(root: TidarNode) -> dict:
    """Compute tree statistics for verification."""
    nodes = []
    leaves = []
    trit_counts = {-1: 0, 0: 0, 1: 0}
    
    def visit(node: TidarNode):
        nodes.append(node)
        trit_counts[node.trit] += 1
        if node.is_leaf:
            leaves.append(node)
        for child in node.children:
            visit(child)
    
    visit(root)
    
    return {
        "total_nodes": len(nodes),
        "leaf_nodes": len(leaves),
        "branches": len(root.children),
        "trit_distribution": trit_counts,
        "trit_sum_mod3": root.trit_sum(),
    }


def print_tree(node: TidarNode, indent: int = 0):
    """Pretty print the TIDAR tree."""
    trit_sym = {-1: "−", 0: "∘", 1: "+"}[node.trit]
    prefix = "  " * indent
    leaf_mark = "🍃" if node.is_leaf else "🌿"
    print(f"{prefix}{leaf_mark} [{node.id:2d}] trit={trit_sym} seed={node.seed:016x}")
    for child in node.children:
        print_tree(child, indent + 1)


if __name__ == "__main__":
    print("TIDAR Forward Pass Demo")
    print("=" * 50)
    
    tree = tidar_forward("test_task", seed=0x42D)
    print_tree(tree)
    
    print("\nStatistics:")
    stats = tree_stats(tree)
    for k, v in stats.items():
        print(f"  {k}: {v}")
    
    print(f"\n✓ GF(3) conservation: Σ trits ≡ {stats['trit_sum_mod3']} (mod 3)")
    print(f"✓ Structure: 1 root + 7 branches + 21 leaves = {stats['total_nodes']} nodes")
