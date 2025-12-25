#!/usr/bin/env python3
"""
Hierarchical Agent Tree with Backprop-style Forward/Backward Passes.

Architecture:
- 7 root agents
- 3 sub-agents per root (GF(3) triad)
- 21 total leaf agents
- Forward: split task down
- Backward: aggregate results up (chain rule style)

Uses Gemma 3n via MLX for local inference.
"""

import asyncio
import json
import time
from dataclasses import dataclass, field
from typing import List, Optional, Callable, Any
from concurrent.futures import ThreadPoolExecutor

# MLX imports
from mlx_lm import load, generate, stream_generate

# ============================================================
# GF(3) Coloring (matching Gay.jl protocol)
# ============================================================

GOLDEN = 0x9E3779B97F4A7C15

def splitmix64(x: int) -> int:
    """SplitMix64 deterministic hash"""
    z = (x + GOLDEN) & 0xFFFFFFFFFFFFFFFF
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & 0xFFFFFFFFFFFFFFFF
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & 0xFFFFFFFFFFFFFFFF
    return (z ^ (z >> 31)) & 0xFFFFFFFFFFFFFFFF

def trit(seed: int, index: int) -> int:
    """Generate trit ∈ {-1, 0, +1}"""
    h = splitmix64(seed ^ index)
    return (h % 3) - 1

def gf3_sum(trits: List[int]) -> int:
    """Verify GF(3) conservation: sum ≡ 0 (mod 3)"""
    return sum(trits) % 3

# ============================================================
# Agent Node
# ============================================================

@dataclass
class AgentNode:
    """Node in the agent tree"""
    id: str
    task: str
    trit: int  # GF(3) color
    depth: int
    children: List['AgentNode'] = field(default_factory=list)
    result: Optional[str] = None
    
    def is_leaf(self) -> bool:
        return len(self.children) == 0

# ============================================================
# Gemma 3n MLX Model
# ============================================================

class GemmaMLX:
    """Gemma 3n model via MLX"""
    
    def __init__(self, model_name: str = "mlx-community/gemma-3n-E4B-it-4bit"):
        print(f"Loading {model_name}...")
        self.model, self.tokenizer = load(model_name)
        print("Model loaded.")
    
    def generate(self, prompt: str, max_tokens: int = 50) -> str:
        """Generate response"""
        messages = [{"role": "user", "content": prompt}]
        formatted = self.tokenizer.apply_chat_template(
            messages, add_generation_prompt=True
        )
        return generate(
            self.model, self.tokenizer, 
            prompt=formatted, 
            max_tokens=max_tokens
        )
    
    def stream(self, prompt: str, max_tokens: int = 50):
        """Streaming generation"""
        messages = [{"role": "user", "content": prompt}]
        formatted = self.tokenizer.apply_chat_template(
            messages, add_generation_prompt=True
        )
        for r in stream_generate(
            self.model, self.tokenizer,
            prompt=formatted,
            max_tokens=max_tokens
        ):
            yield r.text

# ============================================================
# Forward Pass (Split Down)
# ============================================================

def build_agent_tree(
    root_task: str,
    n_roots: int = 7,
    n_children: int = 3,
    seed: int = 0x42D
) -> List[AgentNode]:
    """
    Build hierarchical agent tree.
    
    Forward pass: split root task into branches, each with sub-tasks.
    """
    roots = []
    
    for r in range(n_roots):
        root_seed = splitmix64(seed + r * 1000)
        root_node = AgentNode(
            id=f"root-{r}",
            task=f"{root_task} [Branch {r}: focus on aspect {r}]",
            trit=trit(root_seed, r),
            depth=0
        )
        
        # Add children (triadic structure for GF(3))
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

def forward_pass(root_task: str) -> dict:
    """
    Forward pass: build tree and assign tasks.
    Like forward prop - activations flow down.
    """
    print("\n" + "="*60)
    print("FORWARD PASS (Split Down)")
    print("="*60)
    
    roots = build_agent_tree(root_task)
    
    # Collect all trits for verification
    all_trits = []
    for root in roots:
        all_trits.append(root.trit)
        for child in root.children:
            all_trits.append(child.trit)
    
    print(f"Built {len(roots)} root agents")
    print(f"Total leaf agents: {sum(len(r.children) for r in roots)}")
    print(f"All trits: {all_trits}")
    print(f"GF(3) check: {gf3_sum(all_trits)}")
    
    return {"roots": roots, "trits": all_trits}

# ============================================================
# Backward Pass (Roll Up - Chain Rule)
# ============================================================

def backward_pass(
    roots: List[AgentNode],
    llm: GemmaMLX,
    max_tokens: int = 30
) -> dict:
    """
    Backward pass: aggregate results from leaves to roots.
    Like backprop - gradients flow up via chain rule.
    
    Chain rule analog:
    ∂L/∂root = Σᵢ (∂L/∂childᵢ × ∂childᵢ/∂root)
    """
    print("\n" + "="*60)
    print("BACKWARD PASS (Roll Up)")
    print("="*60)
    
    results = []
    
    for root in roots:
        print(f"\n[{root.id}] Processing {len(root.children)} children...")
        
        # Step 1: Compute at leaves (parallel would be ideal)
        child_results = []
        for child in root.children:
            prompt = f"In 1 sentence, analyze: {child.task}"
            print(f"  [{child.id}] trit={child.trit:+d} generating...")
            
            start = time.time()
            child.result = llm.generate(prompt, max_tokens=max_tokens)
            elapsed = time.time() - start
            
            print(f"    Done in {elapsed:.2f}s: {child.result[:50]}...")
            child_results.append(child.result)
        
        # Step 2: Aggregate children → parent (chain rule accumulation)
        aggregation_prompt = f"""Synthesize these {len(child_results)} analyses into one insight:
{chr(10).join(f'- {r[:100]}' for r in child_results)}

One sentence synthesis:"""
        
        print(f"  [{root.id}] Aggregating children...")
        root.result = llm.generate(aggregation_prompt, max_tokens=50)
        print(f"    Aggregated: {root.result[:60]}...")
        
        results.append({
            "root_id": root.id,
            "root_trit": root.trit,
            "child_trits": [c.trit for c in root.children],
            "result": root.result
        })
    
    # Step 3: Final aggregation at root
    all_root_results = [r["result"] for r in results]
    final_prompt = f"""Synthesize these {len(all_root_results)} branch analyses:
{chr(10).join(f'- {r[:80]}' for r in all_root_results)}

Final one sentence conclusion:"""
    
    print("\n[FINAL] Aggregating all branches...")
    final_result = llm.generate(final_prompt, max_tokens=60)
    
    return {
        "branch_results": results,
        "final_result": final_result,
        "gf3_check": gf3_sum([r["root_trit"] for r in results])
    }

# ============================================================
# Forward-Backward Oscillation (SGD-style)
# ============================================================

def iterate_forward_backward(
    task: str,
    n_iterations: int,
    llm: GemmaMLX
) -> dict:
    """
    Oscillate between forward and backward passes.
    Like SGD iterations - each pass refines the solution.
    """
    print("\n" + "="*60)
    print(f"ITERATING {n_iterations} TIMES")
    print("="*60)
    
    current_task = task
    history = []
    
    for i in range(n_iterations):
        print(f"\n{'='*60}")
        print(f"ITERATION {i+1}/{n_iterations}")
        print(f"{'='*60}")
        
        # Forward pass
        forward = forward_pass(current_task)
        
        # Backward pass
        backward = backward_pass(forward["roots"], llm, max_tokens=25)
        
        # Refine task for next iteration
        current_task = f"{task} [Iteration {i+1}: {backward['final_result'][:50]}]"
        
        history.append({
            "iteration": i,
            "gf3_check": backward["gf3_check"],
            "result_preview": backward["final_result"][:100]
        })
    
    return {
        "final_task": current_task,
        "final_result": backward["final_result"],
        "history": history,
        "iterations": n_iterations
    }

# ============================================================
# Main
# ============================================================

def main():
    print("="*60)
    print("BACKPROP-STYLE HIERARCHICAL AGENT TREE")
    print("7 roots × 3 children = 21 leaf agents")
    print("Using Gemma 3n via MLX")
    print("="*60)
    
    # Load model
    llm = GemmaMLX("mlx-community/gemma-3n-E4B-it-4bit")
    
    # Run iterations
    result = iterate_forward_backward(
        "Explain how agent-o-rama orchestrates distributed AI agents",
        n_iterations=2,
        llm=llm
    )
    
    print("\n" + "="*60)
    print("FINAL RESULTS")
    print("="*60)
    print(f"\nIterations: {result['iterations']}")
    print(f"\nFinal result:\n{result['final_result']}")
    print(f"\nHistory:")
    for h in result["history"]:
        print(f"  [{h['iteration']}] GF(3)={h['gf3_check']} | {h['result_preview'][:60]}...")

if __name__ == "__main__":
    main()
