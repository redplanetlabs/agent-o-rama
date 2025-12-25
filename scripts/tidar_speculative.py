#!/tmp/mlx-env/bin/python
"""
TIDAR Speculative Decoding: Comparative timing with MLX draft models.

Uses:
- Main model: mlx-community/gemma-3-1b-it-qat-4bit
- Draft model: mlx-community/gemma-3-270m-it-8bit

Demonstrates speculative decoding speedup for aggregation steps.

NOTE: Speculative decoding benefits when draft << main (e.g., 1B draft for 32B main).
With similar-sized models (270m vs 1b), overhead may exceed benefit.
For production, pair e.g., gemma-3-27b with gemma-3-270m for ~2x speedup.
"""

import time
from dataclasses import dataclass, field
from typing import List, Optional

from mlx_lm import load, stream_generate

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


def stream_generate_text(
    model, 
    tokenizer, 
    prompt: str, 
    max_tokens: int = 100,
    draft_model=None,
    num_draft_tokens: int = 4
) -> tuple[str, int]:
    messages = [{"role": "user", "content": prompt}]
    formatted = tokenizer.apply_chat_template(messages, add_generation_prompt=True)
    
    text = ""
    token_count = 0
    
    kwargs = {
        "prompt": formatted,
        "max_tokens": max_tokens,
    }
    if draft_model is not None:
        kwargs["draft_model"] = draft_model
        kwargs["num_draft_tokens"] = num_draft_tokens
    
    for response in stream_generate(model, tokenizer, **kwargs):
        text = response.text
        token_count = response.token
    
    return text, token_count


def tidar_backward_speculative(
    forward_result: dict,
    model,
    tokenizer,
    draft_model,
    max_tokens: int = 80,
    use_speculative: bool = True
) -> tuple[dict, float]:
    roots = forward_result["roots"]
    results = []
    
    start = time.time()
    
    for root in roots:
        child_results = []
        for child in root.children:
            prompt = f"Briefly: {child.task}"
            result, _ = stream_generate_text(
                model, tokenizer, prompt, max_tokens=30,
                draft_model=draft_model if use_speculative else None
            )
            child.result = result
            child_results.append(result)
        
        agg_prompt = f"Synthesize briefly: {' | '.join(r[:40] for r in child_results)}"
        result, _ = stream_generate_text(
            model, tokenizer, agg_prompt, max_tokens=40,
            draft_model=draft_model if use_speculative else None
        )
        root.result = result
        
        results.append({
            "root_id": root.id,
            "trit": root.trit,
            "result": root.result
        })
    
    final_prompt = f"Conclude briefly: {' | '.join(r['result'][:30] for r in results)}"
    final_result, final_tokens = stream_generate_text(
        model, tokenizer, final_prompt, max_tokens=max_tokens,
        draft_model=draft_model if use_speculative else None
    )
    
    elapsed = time.time() - start
    
    return {
        "branch_results": results,
        "final_result": final_result,
        "final_tokens": final_tokens,
        "gf3_check": gf3_sum([r["trit"] for r in results])
    }, elapsed


def main():
    print("=" * 70)
    print("TIDAR SPECULATIVE DECODING COMPARISON")
    print("=" * 70)
    
    main_model_id = "mlx-community/gemma-3-1b-it-qat-4bit"
    draft_model_id = "mlx-community/gemma-3-270m-it-8bit"
    
    print(f"\nMain model:  {main_model_id}")
    print(f"Draft model: {draft_model_id}")
    print()
    
    print("Loading main model...")
    t0 = time.time()
    main_model, tokenizer = load(main_model_id)
    main_load = time.time() - t0
    print(f"  Loaded in {main_load:.2f}s")
    
    print("Loading draft model...")
    t0 = time.time()
    draft_model, _ = load(draft_model_id)
    draft_load = time.time() - t0
    print(f"  Loaded in {draft_load:.2f}s")
    
    print("\n" + "=" * 70)
    print("FORWARD PASS: Building agent tree")
    print("=" * 70)
    
    forward = tidar_forward("Explain LLM agents", n_roots=3, n_children=2)
    print(f"Built {len(forward['roots'])} roots, {forward['n_leaves']} leaves")
    print(f"Trits: {forward['trits']}")
    print(f"GF(3) check: {forward['gf3_check']}")
    
    print("\n" + "=" * 70)
    print("RUN 1: WITHOUT Speculative Decoding")
    print("=" * 70)
    
    forward1 = tidar_forward("Explain LLM agents", n_roots=3, n_children=2, seed=0x42D)
    result_no_spec, time_no_spec = tidar_backward_speculative(
        forward1, main_model, tokenizer, draft_model,
        max_tokens=80, use_speculative=False
    )
    
    print(f"Time: {time_no_spec:.2f}s")
    print(f"Final tokens: {result_no_spec['final_tokens']}")
    print(f"GF(3) check: {result_no_spec['gf3_check']}")
    print(f"Final: {result_no_spec['final_result'][:100]}...")
    
    print("\n" + "=" * 70)
    print("RUN 2: WITH Speculative Decoding (draft_model)")
    print("=" * 70)
    
    forward2 = tidar_forward("Explain LLM agents", n_roots=3, n_children=2, seed=0x42D)
    result_spec, time_spec = tidar_backward_speculative(
        forward2, main_model, tokenizer, draft_model,
        max_tokens=80, use_speculative=True
    )
    
    print(f"Time: {time_spec:.2f}s")
    print(f"Final tokens: {result_spec['final_tokens']}")
    print(f"GF(3) check: {result_spec['gf3_check']}")
    print(f"Final: {result_spec['final_result'][:100]}...")
    
    print("\n" + "=" * 70)
    print("TIMING COMPARISON")
    print("=" * 70)
    
    speedup = time_no_spec / time_spec if time_spec > 0 else 0
    
    print(f"{'Method':<30} {'Time':>10} {'Speedup':>10}")
    print("-" * 52)
    print(f"{'Without speculative':<30} {time_no_spec:>9.2f}s {'1.00x':>10}")
    print(f"{'With speculative (draft)':<30} {time_spec:>9.2f}s {speedup:>9.2f}x")
    print("-" * 52)
    
    if speedup > 1.0:
        print(f"\n✓ Speculative decoding is {speedup:.2f}x FASTER")
    elif speedup < 1.0:
        print(f"\n⚠ Speculative decoding is {1/speedup:.2f}x slower (overhead > benefit)")
    else:
        print("\n≈ No significant difference")
    
    print("\n" + "=" * 70)
    print("SUMMARY")
    print("=" * 70)
    print(f"Model load times:   main={main_load:.2f}s, draft={draft_load:.2f}s")
    print(f"Without speculative: {time_no_spec:.2f}s")
    print(f"With speculative:    {time_spec:.2f}s")
    print(f"Speedup factor:      {speedup:.2f}x")


if __name__ == "__main__":
    main()
