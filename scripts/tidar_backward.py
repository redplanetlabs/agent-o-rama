#!/usr/bin/env python3
"""
TIDAR Backward Pass - Tree-structured Iterative Decomposition and Aggregation with Rollup.

Aggregates results from leaves to root using chain-rule style composition:
  21 leaves → 7 branches → 1 final result

GF(3) conservation verified at each aggregation level.
"""

from dataclasses import dataclass, field
from typing import List, Optional, Callable, Any, Dict

GOLDEN = 0x9E3779B97F4A7C15

def splitmix64(x: int) -> int:
    z = (x + GOLDEN) & 0xFFFFFFFFFFFFFFFF
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & 0xFFFFFFFFFFFFFFFF
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & 0xFFFFFFFFFFFFFFFF
    return (z ^ (z >> 31)) & 0xFFFFFFFFFFFFFFFF

def trit(seed: int, index: int) -> int:
    h = splitmix64(seed ^ index)
    return (h % 3) - 1

def gf3_check(trits: List[int]) -> bool:
    return sum(trits) % 3 == 0


@dataclass
class AgentNode:
    id: str
    task: str
    trit: int
    depth: int
    children: List['AgentNode'] = field(default_factory=list)
    result: Optional[str] = None

    def is_leaf(self) -> bool:
        return len(self.children) == 0


def build_agent_tree(
    root_task: str,
    n_roots: int = 7,
    n_children: int = 3,
    seed: int = 0x42D
) -> List[AgentNode]:
    roots = []
    for r in range(n_roots):
        root_seed = splitmix64(seed + r * 1000)
        root_node = AgentNode(
            id=f"root-{r}",
            task=f"{root_task} [Branch {r}]",
            trit=trit(root_seed, r),
            depth=0
        )
        for c in range(n_children):
            child_seed = splitmix64(root_seed + c)
            child_node = AgentNode(
                id=f"leaf-{r}-{c}",
                task=f"{root_node.task} [Sub-task {c}]",
                trit=trit(child_seed, c),
                depth=1
            )
            root_node.children.append(child_node)
        roots.append(root_node)
    return roots


def backward_pass(
    roots: List[AgentNode],
    compute_fn: Callable[[str], str],
    aggregate_fn: Optional[Callable[[List[str]], str]] = None
) -> Dict[str, Any]:
    """
    Backward pass: aggregate results from leaves → branches → final.
    
    Chain rule analog:
      ∂L/∂root = Σᵢ (∂L/∂childᵢ × ∂childᵢ/∂root)
    
    Args:
        roots: List of branch root nodes (from forward pass)
        compute_fn: Function to compute result at each leaf
        aggregate_fn: Optional custom aggregation (default: join with semicolons)
    
    Returns:
        Dict with branch_results, final_result, and gf3 verification
    """
    if aggregate_fn is None:
        aggregate_fn = lambda results: "; ".join(results)
    
    branch_results = []
    
    for root in roots:
        child_trits = []
        child_results = []
        
        for child in root.children:
            child.result = compute_fn(child.task)
            child_results.append(child.result)
            child_trits.append(child.trit)
        
        leaf_gf3_ok = gf3_check(child_trits)
        root.result = aggregate_fn(child_results)
        
        branch_results.append({
            "branch_id": root.id,
            "root_trit": root.trit,
            "child_trits": child_trits,
            "leaf_gf3_balanced": leaf_gf3_ok,
            "aggregated_result": root.result
        })
        
        print(f"[{root.id}] leaf trits={child_trits} GF(3)={leaf_gf3_ok}")
    
    branch_trits = [r["root_trit"] for r in branch_results]
    branch_gf3_ok = gf3_check(branch_trits)
    
    all_branch_outputs = [r["aggregated_result"] for r in branch_results]
    final_result = aggregate_fn(all_branch_outputs)
    
    print(f"[FINAL] branch trits={branch_trits} GF(3)={branch_gf3_ok}")
    
    return {
        "branch_results": branch_results,
        "final_result": final_result,
        "branch_gf3_balanced": branch_gf3_ok,
        "total_leaves": sum(len(r.children) for r in roots)
    }


def mock_compute(task: str) -> str:
    seed = hash(task) & 0xFFFFFFFF
    h = splitmix64(seed)
    return f"result_{h % 1000:03d}"


if __name__ == "__main__":
    print("=" * 60)
    print("TIDAR BACKWARD PASS")
    print("7 branches × 3 children = 21 leaves → 1 final result")
    print("=" * 60)
    
    roots = build_agent_tree("Analyze distributed agents")
    
    print("\n--- Forward tree built ---")
    all_trits = []
    for root in roots:
        all_trits.append(root.trit)
        for c in root.children:
            all_trits.append(c.trit)
    print(f"Total nodes: {len(all_trits)}")
    print(f"All trits: {all_trits}")
    print(f"Full tree GF(3): {gf3_check(all_trits)}")
    
    print("\n--- Backward pass ---")
    result = backward_pass(roots, mock_compute)
    
    print("\n--- Results ---")
    print(f"Leaves processed: {result['total_leaves']}")
    print(f"Branch GF(3) balanced: {result['branch_gf3_balanced']}")
    print(f"Final result: {result['final_result'][:80]}...")
