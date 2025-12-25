#!/tmp/mlx-env/bin/python
"""
TIDAR Cached: MLX prompt caching for multi-iteration speedup.

Key insight: System context is identical across iterations - cache it.
Uses mlx_lm.models.cache for KV cache persistence.
"""

import os
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional, Callable

from mlx_lm import load, generate
from mlx_lm.models.cache import make_prompt_cache, save_prompt_cache, load_prompt_cache

GOLDEN = 0x9E3779B97F4A7C15
CACHE_DIR = Path("/tmp/tidar_cache")


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


class CachedTidarRunner:
    """
    TIDAR runner with MLX prompt caching.
    
    Structure:
    1. First iteration: Create cache with common system context
    2. Subsequent iterations: Reuse cache, only compute new prompt parts
    """
    
    def __init__(self, model_name: str = None, cache_file: str = "tidar_context.safetensors"):
        self.model = None
        self.tokenizer = None
        self.model_name = model_name
        self.prompt_cache = None
        self.cache_path = CACHE_DIR / cache_file
        self.load_time = 0.0
        self.cache_hits = 0
        self.cache_misses = 0
        
        CACHE_DIR.mkdir(parents=True, exist_ok=True)
    
    def load_model(self):
        """Load model and initialize or restore cache."""
        start = time.time()
        
        models_to_try = [
            self.model_name,
            "mlx-community/gemma-3-270m-it-8bit",
            "mlx-community/gemma-3-1b-it-qat-4bit",
        ]
        
        for m in filter(None, models_to_try):
            try:
                print(f"Loading {m}...")
                self.model, self.tokenizer = load(m)
                self.model_name = m
                break
            except Exception as e:
                print(f"  Failed: {e}")
        
        if self.model is None:
            raise RuntimeError("No model available")
        
        self.load_time = time.time() - start
        print(f"Loaded {self.model_name} in {self.load_time:.2f}s")
        
        self._init_cache()
    
    def _init_cache(self):
        """Initialize or load existing prompt cache."""
        if self.cache_path.exists():
            try:
                print(f"Loading cached context from {self.cache_path}")
                self.prompt_cache = load_prompt_cache(str(self.cache_path))
                self.cache_hits += 1
                print("  Cache restored - skipping context prefill")
                return
            except Exception as e:
                print(f"  Cache load failed: {e}, creating fresh")
        
        self.cache_misses += 1
        print("Creating fresh prompt cache...")
        self.prompt_cache = make_prompt_cache(self.model)
        
        system_context = self._build_system_context()
        messages = [{"role": "system", "content": system_context}]
        formatted = self.tokenizer.apply_chat_template(
            messages, add_generation_prompt=False
        )
        
        _ = generate(
            self.model,
            self.tokenizer,
            prompt=formatted,
            max_tokens=1,
            prompt_cache=self.prompt_cache,
        )
        
        save_prompt_cache(str(self.cache_path), self.prompt_cache)
        print(f"  Cache saved to {self.cache_path}")
    
    def _build_system_context(self) -> str:
        """Build the common system context for all iterations."""
        return """You are a TIDAR agent (Tree-structured Iterative Decomposition and Aggregation with Rollup).
Your role is to process decomposed sub-tasks and provide brief, focused responses.

Rules:
- Respond concisely (1-2 sentences max)
- Focus on the specific sub-task assigned
- Output should be aggregatable with sibling results

GF(3) Conservation: All operations preserve balanced ternary invariants."""
    
    def generate_cached(self, prompt: str, max_tokens: int = 20) -> str:
        """Generate with cached context - much faster for iterations 2+."""
        messages = [{"role": "user", "content": prompt}]
        formatted = self.tokenizer.apply_chat_template(
            messages, add_generation_prompt=True
        )
        
        return generate(
            self.model,
            self.tokenizer,
            prompt=formatted,
            max_tokens=max_tokens,
            prompt_cache=self.prompt_cache,
        )
    
    def forward(self, root_task: str, n_roots: int = 3, n_children: int = 3, 
                seed: int = 0x42D) -> dict:
        """Forward pass: build agent tree with GF(3) coloring."""
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
    
    def backward(self, forward_result: dict, max_tokens: int = 20) -> dict:
        """Backward pass: aggregate results using cached generation."""
        roots = forward_result["roots"]
        results = []
        
        for root in roots:
            child_results = []
            for child in root.children:
                prompt = f"Briefly: {child.task}"
                child.result = self.generate_cached(prompt, max_tokens)
                child_results.append(child.result)
            
            agg_prompt = f"Synthesize: {' | '.join(r[:30] for r in child_results)}"
            root.result = self.generate_cached(agg_prompt, max_tokens)
            
            results.append({
                "root_id": root.id,
                "trit": root.trit,
                "result": root.result
            })
        
        final_prompt = f"Conclude: {' | '.join(r['result'][:25] for r in results)}"
        final_result = self.generate_cached(final_prompt, max_tokens)
        
        return {
            "branch_results": results,
            "final_result": final_result,
            "gf3_check": gf3_sum([r["trit"] for r in results])
        }
    
    def run_iteration(self, task: str, iteration: int, n_roots: int = 7, 
                      n_children: int = 3) -> dict:
        """Run a single TIDAR iteration with timing."""
        print(f"\n{'=' * 60}")
        print(f"ITERATION {iteration}")
        print(f"{'=' * 60}")
        
        start_fwd = time.time()
        forward = self.forward(task, n_roots=n_roots, n_children=n_children,
                               seed=0x42D + iteration)
        fwd_time = time.time() - start_fwd
        print(f"Forward: {len(forward['roots'])} roots, {forward['n_leaves']} leaves")
        print(f"GF(3): {forward['gf3_check']} | Time: {fwd_time:.3f}s")
        
        start_bwd = time.time()
        backward = self.backward(forward)
        bwd_time = time.time() - start_bwd
        print(f"Backward: {bwd_time:.2f}s | Final GF(3): {backward['gf3_check']}")
        
        return {
            "iteration": iteration,
            "forward_time": fwd_time,
            "backward_time": bwd_time,
            "total_time": fwd_time + bwd_time,
            "final_result": backward["final_result"][:80],
        }
    
    def clear_cache(self):
        """Remove cached context."""
        if self.cache_path.exists():
            self.cache_path.unlink()
            print(f"Cleared cache: {self.cache_path}")


def main():
    print("=" * 60)
    print("TIDAR Cached Runner - MLX Prompt Caching Demo")
    print("=" * 60)
    
    runner = CachedTidarRunner()
    runner.load_model()
    
    task = "Explain agent architectures"
    n_iterations = 3
    
    iteration_times = []
    for i in range(n_iterations):
        result = runner.run_iteration(task, iteration=i, n_roots=3, n_children=3)
        iteration_times.append(result["total_time"])
        print(f"Result: {result['final_result']}...")
    
    print("\n" + "=" * 60)
    print("TIMING SUMMARY")
    print("=" * 60)
    print(f"Model load:      {runner.load_time:.2f}s")
    print(f"Cache hits:      {runner.cache_hits}")
    print(f"Cache misses:    {runner.cache_misses}")
    print()
    
    for i, t in enumerate(iteration_times):
        speedup = iteration_times[0] / t if i > 0 else 1.0
        marker = "" if i == 0 else f" ({speedup:.1f}x vs iter 0)"
        print(f"Iteration {i}:     {t:.2f}s{marker}")
    
    if len(iteration_times) > 1:
        avg_cached = sum(iteration_times[1:]) / len(iteration_times[1:])
        print(f"\nAvg cached iter: {avg_cached:.2f}s")
        print(f"Expected speedup: Context prefill skipped on iterations 1+")


if __name__ == "__main__":
    main()
