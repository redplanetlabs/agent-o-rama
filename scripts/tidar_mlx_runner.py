#!/tmp/mlx-env/bin/python
"""
TIDAR: Tree-structured Iterative Decomposition and Aggregation with Rollup

MLX runner with forward/backward passes and GF(3) conservation.
Uses batch_generate for massive speedup on leaf nodes.
"""

import time
from dataclasses import dataclass, field
from typing import List, Optional

from mlx_lm import load, generate, batch_generate

# GF(3) primitives
GOLDEN = 0x9E3779B97F4A7C15

def splitmix64(x: int) -> int:
    z = (x + GOLDEN) & 0xFFFFFFFFFFFFFFFF
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & 0xFFFFFFFFFFFFFFFF
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & 0xFFFFFFFFFFFFFFFF
    return (z ^ (z >> 31)) & 0xFFFFFFFFFFFFFFFF

def trit(seed: int, index: int) -> int:
    return (splitmix64(seed ^ index) % 3) - 1

def gf3_sum(trits: List[int]) -> int:
    return sum(trits) % 3


@dataclass
class TidarNode:
    id: str
    task: str
    trit: int
    depth: int
    children: List['TidarNode'] = field(default_factory=list)
    result: Optional[str] = None


def tidar_forward(
    root_task: str,
    n_roots: int = 3,
    n_children: int = 3,
    seed: int = 0x42D
) -> dict:
    """
    Forward pass: build agent tree, split task down.
    Returns tree structure with GF(3) coloring.
    """
    roots = []
    all_trits = []
    
    for r in range(n_roots):
        root_seed = splitmix64(seed + r * 1000)
        root_trit = trit(root_seed, r)
        all_trits.append(root_trit)
        
        root_node = TidarNode(
            id=f"root-{r}",
            task=f"{root_task} [aspect {r}]",
            trit=root_trit,
            depth=0
        )
        
        for c in range(n_children):
            child_seed = splitmix64(root_seed + c)
            child_trit = trit(child_seed, c)
            all_trits.append(child_trit)
            
            child_node = TidarNode(
                id=f"leaf-{r}-{c}",
                task=f"{root_node.task} [sub-{c}]",
                trit=child_trit,
                depth=1
            )
            root_node.children.append(child_node)
        
        roots.append(root_node)
    
    return {
        "roots": roots,
        "trits": all_trits,
        "gf3_check": gf3_sum(all_trits),
        "n_leaves": sum(len(r.children) for r in roots)
    }


def tidar_backward_batched(
    forward_result: dict,
    model,
    tokenizer,
    max_tokens: int = 20
) -> dict:
    """
    Backward pass with batched generation.
    Phase 1: Batch all leaf prompts → single batch_generate call
    Phase 2: Batch all root aggregation prompts
    Phase 3: Final synthesis
    """
    roots = forward_result["roots"]
    
    # Phase 1: Collect all leaf prompts
    leaf_prompts = []
    leaf_nodes = []
    for root in roots:
        for child in root.children:
            prompt = f"Briefly: {child.task}"
            leaf_prompts.append(prompt)
            leaf_nodes.append(child)
    
    print(f"  Batching {len(leaf_prompts)} leaf prompts...")
    
    # Format prompts for chat template
    formatted_leaf_prompts = [
        tokenizer.apply_chat_template(
            [{"role": "user", "content": p}],
            add_generation_prompt=True
        )
        for p in leaf_prompts
    ]
    
    # Single batch call for all leaves
    leaf_result = batch_generate(
        model, tokenizer,
        prompts=formatted_leaf_prompts,
        max_tokens=max_tokens,
        verbose=False
    )
    
    # Distribute results back to leaf nodes
    for node, text in zip(leaf_nodes, leaf_result.texts):
        node.result = text
    
    # Phase 2: Batch all root aggregation prompts
    agg_prompts = []
    for root in roots:
        child_results = [c.result[:30] for c in root.children]
        agg_prompt = f"Synthesize: {' | '.join(child_results)}"
        agg_prompts.append(agg_prompt)
    
    print(f"  Batching {len(agg_prompts)} aggregation prompts...")
    
    formatted_agg_prompts = [
        tokenizer.apply_chat_template(
            [{"role": "user", "content": p}],
            add_generation_prompt=True
        )
        for p in agg_prompts
    ]
    
    agg_result = batch_generate(
        model, tokenizer,
        prompts=formatted_agg_prompts,
        max_tokens=max_tokens,
        verbose=False
    )
    
    # Assign aggregation results to roots
    results = []
    for root, text in zip(roots, agg_result.texts):
        root.result = text
        results.append({
            "root_id": root.id,
            "trit": root.trit,
            "result": root.result
        })
    
    # Phase 3: Final synthesis (single call)
    final_prompt = f"Conclude: {' | '.join(r['result'][:25] for r in results)}"
    formatted_final = tokenizer.apply_chat_template(
        [{"role": "user", "content": final_prompt}],
        add_generation_prompt=True
    )
    final_result = generate(model, tokenizer, prompt=formatted_final, max_tokens=max_tokens)
    
    return {
        "branch_results": results,
        "final_result": final_result,
        "gf3_check": gf3_sum([r["trit"] for r in results])
    }


def main():
    print("=" * 60)
    print("TIDAR MLX Runner (Batched)")
    print("=" * 60)
    
    start_load = time.time()
    
    models_to_try = [
        "mlx-community/gemma-3-270m-it-8bit",
        "mlx-community/gemma-3-1b-it-qat-4bit",
    ]
    
    model = tokenizer = None
    model_name = None
    for m in models_to_try:
        try:
            print(f"Loading {m}...")
            model, tokenizer = load(m)
            model_name = m
            break
        except Exception as e:
            print(f"  Failed: {e}")
    
    if model is None:
        print("No model available")
        return
    
    load_time = time.time() - start_load
    print(f"Loaded {model_name} in {load_time:.2f}s")
    
    print("\n" + "=" * 60)
    print("FORWARD PASS")
    print("=" * 60)
    start_fwd = time.time()
    forward = tidar_forward("Explain agents", n_roots=7, n_children=3)
    fwd_time = time.time() - start_fwd
    print(f"Built {len(forward['roots'])} roots, {forward['n_leaves']} leaves")
    print(f"Trits: {forward['trits']}")
    print(f"GF(3) check: {forward['gf3_check']}")
    print(f"Forward time: {fwd_time:.3f}s")
    
    print("\n" + "=" * 60)
    print("BACKWARD PASS (Batched)")
    print("=" * 60)
    start_bwd = time.time()
    backward = tidar_backward_batched(forward, model, tokenizer, max_tokens=20)
    bwd_time = time.time() - start_bwd
    
    print(f"\nBackward time: {bwd_time:.2f}s")
    print(f"GF(3) check: {backward['gf3_check']}")
    
    print("\n" + "=" * 60)
    print("RESULTS")
    print("=" * 60)
    for br in backward["branch_results"]:
        print(f"  [{br['root_id']}] trit={br['trit']:+d} | {br['result'][:50]}...")
    
    print(f"\nFinal: {backward['final_result'][:80]}")
    
    total = load_time + fwd_time + bwd_time
    print("\n" + "=" * 60)
    print("TIMING")
    print("=" * 60)
    print(f"  Load:     {load_time:.2f}s")
    print(f"  Forward:  {fwd_time:.3f}s")
    print(f"  Backward: {bwd_time:.2f}s (batched)")
    print(f"  TOTAL:    {total:.2f}s")


if __name__ == "__main__":
    main()
