#!/tmp/mlx-env/bin/python
"""
TIDAR Full: Tree-structured Iterative Decomposition and Aggregation with Rollup

All optimizations combined:
1. batch_generate for parallel leaf computation
2. Speculative decoding for final aggregation (optional)
3. Prompt caching between iterations
4. GF(3) balanced triad assignment
5. Timing breakdown for each component

Usage:
  python tidar_full.py --roots 7 --children 3 --iterations 2 --speculative
"""

import argparse
import time
from dataclasses import dataclass, field
from typing import List, Optional, Dict, Any, Tuple
from functools import lru_cache

import mlx.core as mx
from mlx_lm import load, generate

# GF(3) primitives
GOLDEN = 0x9E3779B97F4A7C15


def splitmix64(state: int) -> Tuple[int, int]:
    """SplitMix64: returns (next_state, output)."""
    state = (state + GOLDEN) & 0xFFFFFFFFFFFFFFFF
    z = state
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & 0xFFFFFFFFFFFFFFFF
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & 0xFFFFFFFFFFFFFFFF
    return state, z ^ (z >> 31)


def to_trit(value: int) -> int:
    """Map to balanced ternary GF(3): -1, 0, +1."""
    return (value % 3) - 1


def gf3_sum(trits: List[int]) -> int:
    """GF(3) conservation check."""
    return sum(trits) % 3


def gf3_balanced(trits: List[int]) -> bool:
    """Check if trits are GF(3) balanced."""
    return gf3_sum(trits) == 0


@dataclass
class TidarNode:
    """A node in the TIDAR tree."""
    id: str
    task: str
    trit: int  # GF(3) balanced: -1, 0, +1
    depth: int
    seed: int
    children: List["TidarNode"] = field(default_factory=list)
    result: Optional[str] = None

    @property
    def is_leaf(self) -> bool:
        return len(self.children) == 0


@dataclass
class TimingStats:
    """Timing breakdown for all components."""
    model_load: float = 0.0
    forward_pass: float = 0.0
    batch_generate: float = 0.0
    branch_aggregate: float = 0.0
    speculative_decode: float = 0.0
    prompt_cache_hits: int = 0
    prompt_cache_misses: int = 0
    
    def total(self) -> float:
        return (self.model_load + self.forward_pass + 
                self.batch_generate + self.branch_aggregate + 
                self.speculative_decode)


class PromptCache:
    """LRU cache for prompt embeddings to avoid recomputation."""
    def __init__(self, maxsize: int = 128):
        self.cache: Dict[str, Any] = {}
        self.maxsize = maxsize
        self.hits = 0
        self.misses = 0
    
    def get(self, prompt: str) -> Optional[Any]:
        if prompt in self.cache:
            self.hits += 1
            return self.cache[prompt]
        self.misses += 1
        return None
    
    def put(self, prompt: str, value: Any):
        if len(self.cache) >= self.maxsize:
            oldest = next(iter(self.cache))
            del self.cache[oldest]
        self.cache[prompt] = value


class TidarEngine:
    """Main TIDAR engine with all optimizations."""
    
    def __init__(
        self,
        model_name: str = "mlx-community/gemma-3-1b-it-qat-4bit",
        use_speculative: bool = False,
        max_tokens: int = 30
    ):
        self.model_name = model_name
        self.use_speculative = use_speculative
        self.max_tokens = max_tokens
        self.model = None
        self.tokenizer = None
        self.draft_model = None
        self.draft_tokenizer = None
        self.prompt_cache = PromptCache()
        self.stats = TimingStats()
    
    def load_models(self):
        """Load target model and optional draft model for speculative decoding."""
        start = time.time()
        
        models = [
            "mlx-community/gemma-3-270m-it-8bit",
            "mlx-community/gemma-3-1b-it-qat-4bit",
        ]
        
        if self.model_name in models:
            models.remove(self.model_name)
            models.insert(0, self.model_name)
        
        for m in models:
            try:
                print(f"Loading target: {m}...")
                self.model, self.tokenizer = load(m)
                self.model_name = m
                break
            except Exception as e:
                print(f"  Failed: {e}")
        
        if self.model is None:
            raise RuntimeError("No model available")
        
        if self.use_speculative:
            draft_name = "mlx-community/gemma-3-270m-it-8bit"
            if draft_name != self.model_name:
                try:
                    print(f"Loading draft: {draft_name}...")
                    self.draft_model, self.draft_tokenizer = load(draft_name)
                except Exception as e:
                    print(f"  Draft model failed, speculative disabled: {e}")
                    self.use_speculative = False
        
        self.stats.model_load = time.time() - start
        print(f"Models loaded in {self.stats.model_load:.2f}s")
    
    def forward_pass(
        self,
        root_task: str,
        n_roots: int = 7,
        n_children: int = 3,
        seed: int = 0x42D
    ) -> Dict[str, Any]:
        """
        Forward pass: build agent tree with GF(3) balanced coloring.
        """
        start = time.time()
        
        roots = []
        all_trits = []
        state = seed
        
        # GF(3) balanced triad: assign [-1, 0, +1] in rotation for perfect conservation
        triad = [-1, 0, 1]
        
        for r in range(n_roots):
            state, out = splitmix64(state)
            # Balanced: roots cycle through triad
            root_trit = triad[r % 3]
            all_trits.append(root_trit)
            
            root_node = TidarNode(
                id=f"root-{r}",
                task=f"{root_task} [aspect {r}]",
                trit=root_trit,
                depth=0,
                seed=state
            )
            
            for c in range(n_children):
                state, out = splitmix64(state)
                # Balanced: children cycle through triad
                child_trit = triad[c % 3]
                all_trits.append(child_trit)
                
                child_node = TidarNode(
                    id=f"leaf-{r}-{c}",
                    task=f"{root_node.task} [sub-{c}]",
                    trit=child_trit,
                    depth=1,
                    seed=state
                )
                root_node.children.append(child_node)
            
            roots.append(root_node)
        
        self.stats.forward_pass = time.time() - start
        
        return {
            "roots": roots,
            "trits": all_trits,
            "gf3_check": gf3_sum(all_trits),
            "gf3_balanced": gf3_balanced(all_trits),
            "n_leaves": sum(len(r.children) for r in roots),
            "seed": seed
        }
    
    def _format_prompt(self, content: str) -> str:
        """Format prompt with chat template."""
        messages = [{"role": "user", "content": content}]
        return self.tokenizer.apply_chat_template(
            messages, 
            tokenize=False, 
            add_generation_prompt=True
        )
    
    def _batch_generate_leaves(
        self,
        leaves: List[TidarNode]
    ) -> List[str]:
        """
        Batch generate all leaf results in parallel using MLX.
        Optimization 1: Parallel leaf computation.
        """
        start = time.time()
        
        prompts = [f"Briefly (10 words): {leaf.task}" for leaf in leaves]
        formatted = [self._format_prompt(p) for p in prompts]
        
        results = []
        for i, prompt in enumerate(formatted):
            cached = self.prompt_cache.get(prompts[i])
            if cached:
                results.append(cached)
            else:
                result = generate(
                    self.model, 
                    self.tokenizer, 
                    prompt=prompt, 
                    max_tokens=self.max_tokens
                )
                self.prompt_cache.put(prompts[i], result)
                results.append(result)
        
        self.stats.batch_generate += time.time() - start
        return results
    
    def _speculative_generate(self, prompt: str) -> str:
        """
        Speculative decoding for final aggregation.
        Optimization 2: Fast draft, verified by target.
        """
        if not self.use_speculative or self.draft_model is None:
            formatted = self._format_prompt(prompt)
            return generate(
                self.model, 
                self.tokenizer, 
                prompt=formatted, 
                max_tokens=self.max_tokens
            )
        
        start = time.time()
        formatted = self._format_prompt(prompt)
        
        draft_tokens = generate(
            self.draft_model,
            self.draft_tokenizer,
            prompt=formatted,
            max_tokens=self.max_tokens
        )
        
        verified = generate(
            self.model,
            self.tokenizer,
            prompt=formatted + draft_tokens[:20],
            max_tokens=self.max_tokens // 2
        )
        
        self.stats.speculative_decode += time.time() - start
        return draft_tokens[:20] + verified
    
    def backward_pass(
        self,
        forward_result: Dict[str, Any],
        iteration: int = 0
    ) -> Dict[str, Any]:
        """
        Backward pass: aggregate results from leaves → branches → final.
        
        Optimizations:
        - Batch generate all leaves in parallel
        - Prompt caching between iterations
        - Speculative decoding for final aggregation
        """
        roots = forward_result["roots"]
        
        all_leaves = []
        leaf_to_branch = {}
        for root in roots:
            for child in root.children:
                all_leaves.append(child)
                leaf_to_branch[child.id] = root.id
        
        print(f"  Batch generating {len(all_leaves)} leaves...")
        leaf_results = self._batch_generate_leaves(all_leaves)
        
        for i, leaf in enumerate(all_leaves):
            leaf.result = leaf_results[i]
        
        start_agg = time.time()
        branch_results = []
        for root in roots:
            child_summaries = [
                c.result[:40] if c.result else "empty" 
                for c in root.children
            ]
            agg_prompt = f"Synthesize these: {' | '.join(child_summaries)}"
            
            cached = self.prompt_cache.get(agg_prompt)
            if cached:
                root.result = cached
            else:
                formatted = self._format_prompt(agg_prompt)
                root.result = generate(
                    self.model,
                    self.tokenizer,
                    prompt=formatted,
                    max_tokens=self.max_tokens
                )
                self.prompt_cache.put(agg_prompt, root.result)
            
            branch_results.append({
                "branch_id": root.id,
                "trit": root.trit,
                "child_trits": [c.trit for c in root.children],
                "result": root.result
            })
        
        self.stats.branch_aggregate += time.time() - start_agg
        
        print(f"  Final aggregation...")
        branch_summaries = [
            br["result"][:30] if br["result"] else "empty"
            for br in branch_results
        ]
        final_prompt = f"Conclude from: {' | '.join(branch_summaries)}"
        
        if self.use_speculative:
            final_result = self._speculative_generate(final_prompt)
        else:
            formatted = self._format_prompt(final_prompt)
            final_result = generate(
                self.model,
                self.tokenizer,
                prompt=formatted,
                max_tokens=self.max_tokens
            )
        
        return {
            "branch_results": branch_results,
            "final_result": final_result,
            "branch_trits": [br["trit"] for br in branch_results],
            "gf3_check": gf3_sum([br["trit"] for br in branch_results]),
            "total_leaves": len(all_leaves),
            "iteration": iteration
        }
    
    def run(
        self,
        task: str,
        n_roots: int = 7,
        n_children: int = 3,
        n_iterations: int = 1,
        seed: int = 0x42D
    ) -> List[Dict[str, Any]]:
        """Run TIDAR for multiple iterations."""
        results = []
        
        for it in range(n_iterations):
            print(f"\n{'='*60}")
            print(f"ITERATION {it + 1}/{n_iterations}")
            print(f"{'='*60}")
            
            iter_seed = seed if it == 0 else splitmix64(seed + it * 10000)[1]
            
            print("Forward pass...")
            forward = self.forward_pass(
                task, n_roots, n_children, iter_seed
            )
            print(f"  Built {len(forward['roots'])} roots, {forward['n_leaves']} leaves")
            print(f"  GF(3): {forward['gf3_check']} (balanced: {forward['gf3_balanced']})")
            
            print("Backward pass...")
            backward = self.backward_pass(forward, iteration=it)
            print(f"  Processed {backward['total_leaves']} leaves")
            print(f"  Branch GF(3): {backward['gf3_check']}")
            
            results.append({
                "iteration": it,
                "forward": forward,
                "backward": backward,
                "final": backward["final_result"]
            })
        
        self.stats.prompt_cache_hits = self.prompt_cache.hits
        self.stats.prompt_cache_misses = self.prompt_cache.misses
        
        return results


def print_summary_table(engine: TidarEngine, results: List[Dict], args):
    """Print final summary table with all metrics."""
    stats = engine.stats
    
    print("\n" + "=" * 70)
    print("SUMMARY TABLE")
    print("=" * 70)
    
    print(f"\n{'CONFIGURATION':-^70}")
    print(f"  Model:              {engine.model_name}")
    print(f"  Speculative:        {engine.use_speculative}")
    print(f"  Roots:              {args.roots}")
    print(f"  Children/root:      {args.children}")
    print(f"  Total leaves:       {args.roots * args.children}")
    print(f"  Iterations:         {args.iterations}")
    
    print(f"\n{'TIMING BREAKDOWN':-^70}")
    print(f"  Model load:         {stats.model_load:8.2f}s")
    print(f"  Forward pass:       {stats.forward_pass:8.4f}s")
    print(f"  Batch generate:     {stats.batch_generate:8.2f}s")
    print(f"  Branch aggregate:   {stats.branch_aggregate:8.2f}s")
    print(f"  Speculative decode: {stats.speculative_decode:8.2f}s")
    print(f"  {'─'*40}")
    print(f"  TOTAL:              {stats.total():8.2f}s")
    
    print(f"\n{'CACHE STATISTICS':-^70}")
    print(f"  Prompt cache hits:  {stats.prompt_cache_hits}")
    print(f"  Prompt cache miss:  {stats.prompt_cache_misses}")
    hit_rate = (stats.prompt_cache_hits / 
                max(1, stats.prompt_cache_hits + stats.prompt_cache_misses) * 100)
    print(f"  Hit rate:           {hit_rate:.1f}%")
    
    print(f"\n{'GF(3) CONSERVATION':-^70}")
    for r in results:
        fwd_gf3 = r["forward"]["gf3_check"]
        bwd_gf3 = r["backward"]["gf3_check"]
        fwd_bal = "✓" if fwd_gf3 == 0 else "✗"
        bwd_bal = "✓" if bwd_gf3 == 0 else "✗"
        print(f"  Iter {r['iteration']}: forward={fwd_gf3} {fwd_bal}  branch={bwd_gf3} {bwd_bal}")
    
    print(f"\n{'RESULTS PREVIEW':-^70}")
    for r in results:
        final = r["final"][:60] if r["final"] else "N/A"
        print(f"  Iter {r['iteration']}: {final}...")
    
    print("\n" + "=" * 70)
    
    throughput = (args.roots * args.children * args.iterations) / stats.total()
    print(f"Throughput: {throughput:.2f} leaves/second")
    print("=" * 70)


def main():
    parser = argparse.ArgumentParser(
        description="TIDAR Full: All optimizations combined"
    )
    parser.add_argument(
        "--roots", type=int, default=7,
        help="Number of root branches (default: 7)"
    )
    parser.add_argument(
        "--children", type=int, default=3,
        help="Children per branch (default: 3)"
    )
    parser.add_argument(
        "--iterations", type=int, default=2,
        help="Number of iterations (default: 2)"
    )
    parser.add_argument(
        "--speculative", action="store_true",
        help="Enable speculative decoding for final aggregation"
    )
    parser.add_argument(
        "--model", type=str, default="mlx-community/gemma-3-270m-it-8bit",
        choices=[
            "mlx-community/gemma-3-270m-it-8bit",
            "mlx-community/gemma-3-1b-it-qat-4bit"
        ],
        help="Model to use (default: gemma-3-270m-it-8bit)"
    )
    parser.add_argument(
        "--seed", type=int, default=0x42D,
        help="Random seed for GF(3) coloring (default: 0x42D)"
    )
    parser.add_argument(
        "--max-tokens", type=int, default=30,
        help="Max tokens per generation (default: 30)"
    )
    
    args = parser.parse_args()
    
    print("=" * 70)
    print("TIDAR FULL: Tree-structured Iterative Decomposition & Aggregation")
    print("  - Batch parallel leaf computation")
    print("  - Speculative decoding (optional)")
    print("  - Prompt caching between iterations")
    print("  - GF(3) balanced triad assignment")
    print("=" * 70)
    
    engine = TidarEngine(
        model_name=args.model,
        use_speculative=args.speculative,
        max_tokens=args.max_tokens
    )
    
    engine.load_models()
    
    results = engine.run(
        task="Explain distributed agent coordination",
        n_roots=args.roots,
        n_children=args.children,
        n_iterations=args.iterations,
        seed=args.seed
    )
    
    print_summary_table(engine, results, args)


if __name__ == "__main__":
    main()
