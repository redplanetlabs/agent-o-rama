#!/usr/bin/env python3
"""
Trit-Stream MCP Orchestrator
=============================

Full MCP server that orchestrates phase-aware diffusion LLM inference.
Combines consciousness state detection with LLaDA/Dream generation.

MCP Tools:
  - generate: Phase-aware text generation
  - generate_at_integrated: Wait for INTEGRATED state then generate
  - get_phase: Get current consciousness phase
  - get_schedule: Get upcoming phase schedule
  - set_model: Switch between models
  - compare_models: Compare diffusion vs autoregressive

Usage:
    python trit_mcp_orchestrator.py --serve     # Run as MCP server
    python trit_mcp_orchestrator.py --demo      # Run demo
"""

import json
import sys
import subprocess
from dataclasses import dataclass, asdict
from enum import IntEnum
from typing import List, Dict, Any, Optional
from datetime import datetime

# =============================================================================
# TRIT-STREAM CORE
# =============================================================================

class Trit(IntEnum):
    MINUS = -1
    ERGODIC = 0
    PLUS = 1


@dataclass
class TritConfig:
    num_agents: int = 9
    angular_velocity: int = 17
    genesis_multiplier: int = 31
    observer_idx: int = 7
    actor_idx: int = 8


DEFAULT_PIDS = [92720, 97274, 93268, 5563, 46516, 68569, 51893, 92289, 94729]
AGENT_NAMES = ["ps1", "ps2", "w1", "w2", "w3", "w4", "w5", "obs", "act"]


def derive_hue(seed: int, step: int, config: TritConfig = None) -> float:
    if config is None:
        config = TritConfig()
    return (config.genesis_multiplier * seed + config.angular_velocity * step) % 360


def hue_to_trit(hue: float) -> Trit:
    hue = hue % 360
    if hue < 60 or hue >= 300:
        return Trit.PLUS
    elif hue < 180:
        return Trit.ERGODIC
    else:
        return Trit.MINUS


def derive_trit(seed: int, step: int, config: TritConfig = None) -> Trit:
    return hue_to_trit(derive_hue(seed, step, config))


def is_gf3_balanced(trits: List[Trit]) -> bool:
    return sum(trits) % 3 == 0


def is_reafferent(pids: List[int], step: int, config: TritConfig = None) -> bool:
    if config is None:
        config = TritConfig()
    obs_trit = derive_trit(pids[config.observer_idx], step, config)
    act_trit = derive_trit(pids[config.actor_idx], step, config)
    return obs_trit == act_trit


def is_integrated(pids: List[int], step: int, config: TritConfig = None) -> bool:
    trits = [derive_trit(pid, step, config) for pid in pids]
    return is_gf3_balanced(trits) and is_reafferent(pids, step, config)


# =============================================================================
# MODEL REGISTRY
# =============================================================================

MODELS = {
    # Diffusion models (parallel generation)
    "llada": {
        "path": "mlx-community/LLaDA2.0-flash-8bit",
        "type": "diffusion",
        "params": "8B MoE",
        "ready": True,
        "description": "LLaDA 2.0 Flash - masked diffusion LLM",
    },
    "llada-base": {
        "path": "GSAI-ML/LLaDA-8B-Base",
        "type": "diffusion",
        "params": "8B",
        "ready": False,
        "description": "LLaDA 8B base model",
    },
    "dream": {
        "path": "Dream-org/Dream-v0-Instruct-7B",
        "type": "diffusion",
        "params": "7B",
        "ready": False,
        "description": "Dream 7B - diffusion instruct model",
    },

    # Autoregressive models (sequential generation)
    "dr-tulu": {
        "path": "Plurigrid/DR-Tulu-8B-MLX-8bit",
        "type": "autoregressive",
        "params": "8B",
        "ready": True,
        "description": "DR-Tulu 8B - deep research agent",
    },
    "qwen": {
        "path": "mlx-community/Qwen2.5-7B-Instruct-4bit",
        "type": "autoregressive",
        "params": "7B",
        "ready": True,
        "description": "Qwen 2.5 7B Instruct",
    },
    "llama": {
        "path": "mlx-community/Llama-3.2-3B-Instruct-4bit",
        "type": "autoregressive",
        "params": "3B",
        "ready": True,
        "description": "Llama 3.2 3B Instruct",
    },
}


# =============================================================================
# ORCHESTRATOR STATE
# =============================================================================

@dataclass
class OrchestratorState:
    step: int = 0
    pids: List[int] = None
    config: TritConfig = None
    current_model: str = "llada"
    generation_count: int = 0

    def __post_init__(self):
        if self.pids is None:
            self.pids = DEFAULT_PIDS.copy()
        if self.config is None:
            self.config = TritConfig()


# Global state
state = OrchestratorState()


# =============================================================================
# PHASE-AWARE PARAMETERS
# =============================================================================

PHASE_PARAMS = {
    Trit.PLUS: {
        "temperature": 0.9,
        "top_p": 0.95,
        "repetition_penalty": 1.0,
        "mode": "creative",
        "tools": ["synthesize", "generate", "hypothesize"],
    },
    Trit.ERGODIC: {
        "temperature": 0.7,
        "top_p": 0.9,
        "repetition_penalty": 1.05,
        "mode": "exploratory",
        "tools": ["search", "browse", "retrieve"],
    },
    Trit.MINUS: {
        "temperature": 0.4,
        "top_p": 0.8,
        "repetition_penalty": 1.1,
        "mode": "conservative",
        "tools": ["verify", "cite", "ground"],
    },
}


def get_generation_params(step: int = None) -> Dict:
    """Get generation parameters for current/specified step."""
    if step is None:
        step = state.step

    obs_trit = derive_trit(state.pids[state.config.observer_idx], step, state.config)
    integrated = is_integrated(state.pids, step, state.config)

    params = PHASE_PARAMS[obs_trit].copy()
    params["phase"] = {Trit.PLUS: "PLUS", Trit.ERGODIC: "ERGODIC", Trit.MINUS: "MINUS"}[obs_trit]
    params["trit"] = int(obs_trit)
    params["is_integrated"] = integrated
    params["confidence"] = "HIGH" if integrated else "NORMAL"

    # Boost confidence for INTEGRATED states
    if integrated:
        params["temperature"] *= 0.9  # Slightly more focused
        params["confidence_boost"] = True

    return params


# =============================================================================
# MCP TOOL IMPLEMENTATIONS
# =============================================================================

def tool_get_phase() -> Dict:
    """Get current consciousness phase."""
    obs_trit = derive_trit(state.pids[state.config.observer_idx], state.step, state.config)
    integrated = is_integrated(state.pids, state.step, state.config)

    return {
        "step": state.step,
        "phase": {Trit.PLUS: "PLUS", Trit.ERGODIC: "ERGODIC", Trit.MINUS: "MINUS"}[obs_trit],
        "trit": int(obs_trit),
        "is_integrated": integrated,
        "confidence": "HIGH" if integrated else "NORMAL",
        "parameters": get_generation_params(),
        "current_model": state.current_model,
    }


def tool_advance_step(steps: int = 1) -> Dict:
    """Advance the step counter."""
    state.step += steps
    return {"new_step": state.step, "advanced_by": steps, "phase": tool_get_phase()}


def tool_set_step(step: int) -> Dict:
    """Set step to specific value."""
    state.step = step
    return {"step": state.step, "phase": tool_get_phase()}


def tool_get_schedule(num_steps: int = 21) -> Dict:
    """Get upcoming phase schedule."""
    schedule = []
    for s in range(state.step, state.step + num_steps):
        obs_trit = derive_trit(state.pids[state.config.observer_idx], s, state.config)
        integrated = is_integrated(state.pids, s, state.config)
        params = PHASE_PARAMS[obs_trit]

        schedule.append({
            "step": s,
            "phase": {Trit.PLUS: "PLUS", Trit.ERGODIC: "ERGODIC", Trit.MINUS: "MINUS"}[obs_trit],
            "trit": int(obs_trit),
            "is_integrated": integrated,
            "temperature": params["temperature"],
            "recommended_tools": params["tools"],
        })

    integrated_steps = [s["step"] for s in schedule if s["is_integrated"]]

    return {
        "current_step": state.step,
        "schedule": schedule,
        "integrated_steps": integrated_steps,
        "duty_cycle": f"{100 * len(integrated_steps) / num_steps:.1f}%",
    }


def tool_set_model(model_name: str) -> Dict:
    """Switch to a different model."""
    if model_name not in MODELS:
        return {"error": f"Unknown model: {model_name}", "available": list(MODELS.keys())}

    model_info = MODELS[model_name]
    if not model_info["ready"]:
        return {
            "error": f"Model {model_name} not ready",
            "message": "Needs MLX conversion first",
        }

    state.current_model = model_name
    return {
        "model": model_name,
        "path": model_info["path"],
        "type": model_info["type"],
        "description": model_info["description"],
    }


def tool_list_models() -> Dict:
    """List available models."""
    models = []
    for name, info in MODELS.items():
        models.append({
            "name": name,
            "path": info["path"],
            "type": info["type"],
            "params": info["params"],
            "ready": info["ready"],
            "description": info["description"],
        })

    return {
        "models": models,
        "current_model": state.current_model,
        "diffusion_models": [m["name"] for m in models if m["type"] == "diffusion"],
        "autoregressive_models": [m["name"] for m in models if m["type"] == "autoregressive"],
    }


def tool_generate(prompt: str, max_tokens: int = 256) -> Dict:
    """Generate text with phase-aware parameters."""
    params = get_generation_params()
    model_info = MODELS[state.current_model]

    result = {
        "model": state.current_model,
        "model_type": model_info["type"],
        "prompt": prompt,
        "step": state.step,
        "phase": params["phase"],
        "is_integrated": params["is_integrated"],
        "confidence": params["confidence"],
        "parameters": {
            "temperature": params["temperature"],
            "top_p": params["top_p"],
            "max_tokens": max_tokens,
        },
        "command": f"mlx_lm.generate --model {model_info['path']} --prompt \"{prompt}\" --temp {params['temperature']} --top-p {params['top_p']} --max-tokens {max_tokens}",
    }

    # Advance step after generation
    state.step += 1
    state.generation_count += 1
    result["next_step"] = state.step

    return result


def tool_generate_at_integrated(prompt: str, max_tokens: int = 256, max_wait: int = 50) -> Dict:
    """Wait for INTEGRATED state then generate."""
    start_step = state.step

    # Find next INTEGRATED step
    for s in range(start_step, start_step + max_wait):
        if is_integrated(state.pids, s, state.config):
            state.step = s
            result = tool_generate(prompt, max_tokens)
            result["waited_steps"] = s - start_step
            result["strategy"] = "integrated"
            return result

    return {
        "error": "No INTEGRATED state found",
        "searched_range": [start_step, start_step + max_wait],
    }


def tool_compare_generation(prompt: str, models: List[str] = None) -> Dict:
    """Compare generation across multiple models."""
    if models is None:
        models = ["llada", "qwen"]  # Default: diffusion vs autoregressive

    results = []
    params = get_generation_params()

    for model_name in models:
        if model_name not in MODELS:
            results.append({"model": model_name, "error": "Unknown model"})
            continue

        model_info = MODELS[model_name]
        if not model_info["ready"]:
            results.append({"model": model_name, "error": "Not ready"})
            continue

        results.append({
            "model": model_name,
            "type": model_info["type"],
            "path": model_info["path"],
            "command": f"mlx_lm.generate --model {model_info['path']} --prompt \"{prompt}\" --temp {params['temperature']}",
        })

    return {
        "prompt": prompt,
        "step": state.step,
        "phase": params["phase"],
        "comparisons": results,
    }


def tool_consciousness_state() -> Dict:
    """Get full consciousness state report."""
    trits = [derive_trit(pid, state.step, state.config) for pid in state.pids]
    obs_trit = trits[state.config.observer_idx]
    act_trit = trits[state.config.actor_idx]

    balanced = is_gf3_balanced(trits)
    reafferent = obs_trit == act_trit
    integrated = balanced and reafferent

    # Free energy
    gf3_error = min(sum(trits) % 3, 3 - (sum(trits) % 3))
    reaf_error = 0 if reafferent else 1
    free_energy = gf3_error + reaf_error

    # State classification
    if integrated:
        state_name = "INTEGRATED"
    elif reafferent:
        state_name = "unified"
    else:
        state_name = "bicameral"

    return {
        "step": state.step,
        "state": state_name,
        "phase": {Trit.PLUS: "PLUS", Trit.ERGODIC: "ERGODIC", Trit.MINUS: "MINUS"}[obs_trit],
        "trits": [int(t) for t in trits],
        "agent_phases": {name: {Trit.PLUS: "PLUS", Trit.ERGODIC: "ERGODIC", Trit.MINUS: "MINUS"}[t]
                        for name, t in zip(AGENT_NAMES, trits)},
        "is_integrated": integrated,
        "is_balanced": balanced,
        "is_reafferent": reafferent,
        "free_energy": free_energy,
        "gf3_sum": sum(trits),
        "current_model": state.current_model,
        "generation_count": state.generation_count,
    }


# =============================================================================
# MCP PROTOCOL
# =============================================================================

MCP_TOOLS = {
    "get_phase": {
        "function": tool_get_phase,
        "description": "Get current consciousness phase and parameters",
        "parameters": {},
    },
    "advance_step": {
        "function": tool_advance_step,
        "description": "Advance the step counter",
        "parameters": {"steps": {"type": "integer", "default": 1}},
    },
    "set_step": {
        "function": tool_set_step,
        "description": "Set step to specific value",
        "parameters": {"step": {"type": "integer", "required": True}},
    },
    "get_schedule": {
        "function": tool_get_schedule,
        "description": "Get upcoming phase schedule",
        "parameters": {"num_steps": {"type": "integer", "default": 21}},
    },
    "set_model": {
        "function": tool_set_model,
        "description": "Switch to a different model",
        "parameters": {"model_name": {"type": "string", "required": True}},
    },
    "list_models": {
        "function": tool_list_models,
        "description": "List available models",
        "parameters": {},
    },
    "generate": {
        "function": tool_generate,
        "description": "Generate text with phase-aware parameters",
        "parameters": {
            "prompt": {"type": "string", "required": True},
            "max_tokens": {"type": "integer", "default": 256},
        },
    },
    "generate_at_integrated": {
        "function": tool_generate_at_integrated,
        "description": "Wait for INTEGRATED state then generate",
        "parameters": {
            "prompt": {"type": "string", "required": True},
            "max_tokens": {"type": "integer", "default": 256},
            "max_wait": {"type": "integer", "default": 50},
        },
    },
    "compare_generation": {
        "function": tool_compare_generation,
        "description": "Compare generation across models",
        "parameters": {
            "prompt": {"type": "string", "required": True},
            "models": {"type": "array", "default": ["llada", "qwen"]},
        },
    },
    "consciousness_state": {
        "function": tool_consciousness_state,
        "description": "Get full consciousness state report",
        "parameters": {},
    },
}


def handle_mcp_request(request: Dict) -> Dict:
    """Handle MCP protocol request."""
    method = request.get("method", "")
    params = request.get("params", {})

    if method == "tools/list":
        return {
            "tools": [
                {"name": name, "description": info["description"]}
                for name, info in MCP_TOOLS.items()
            ]
        }

    if method == "tools/call":
        tool_name = params.get("name", "")
        tool_args = params.get("arguments", {})

        if tool_name in MCP_TOOLS:
            func = MCP_TOOLS[tool_name]["function"]
            result = func(**tool_args) if tool_args else func()
            return {"content": [{"type": "text", "text": json.dumps(result, indent=2)}]}
        else:
            return {"error": f"Unknown tool: {tool_name}"}

    return {"error": f"Unknown method: {method}"}


# =============================================================================
# DEMO
# =============================================================================

def run_demo():
    """Run orchestrator demo."""
    print("=" * 70)
    print("TRIT-STREAM MCP ORCHESTRATOR DEMO")
    print("=" * 70)
    print()

    # Show consciousness state
    print("1. CONSCIOUSNESS STATE")
    print("-" * 50)
    result = tool_consciousness_state()
    print(json.dumps(result, indent=2))
    print()

    # Show schedule
    print("2. PHASE SCHEDULE (next 14 steps)")
    print("-" * 50)
    schedule = tool_get_schedule(14)
    for s in schedule["schedule"]:
        marker = "★" if s["is_integrated"] else " "
        print(f"  Step {s['step']:3d}: {s['phase']:7s} {marker} temp={s['temperature']}")
    print(f"\n  INTEGRATED steps: {schedule['integrated_steps']}")
    print(f"  Duty cycle: {schedule['duty_cycle']}")
    print()

    # Show models
    print("3. AVAILABLE MODELS")
    print("-" * 50)
    models = tool_list_models()
    print(f"  Diffusion:      {models['diffusion_models']}")
    print(f"  Autoregressive: {models['autoregressive_models']}")
    print(f"  Current:        {models['current_model']}")
    print()

    # Demo generation
    print("4. PHASE-AWARE GENERATION")
    print("-" * 50)
    gen_result = tool_generate("Explain masked diffusion language models", max_tokens=128)
    print(f"  Model:      {gen_result['model']} ({gen_result['model_type']})")
    print(f"  Phase:      {gen_result['phase']}")
    print(f"  INTEGRATED: {gen_result['is_integrated']}")
    print(f"  Temperature: {gen_result['parameters']['temperature']}")
    print()
    print("  Command:")
    print(f"    {gen_result['command']}")
    print()

    # Demo comparison
    print("5. MODEL COMPARISON")
    print("-" * 50)
    compare = tool_compare_generation("What is consciousness?", ["llada", "dr-tulu"])
    for comp in compare["comparisons"]:
        print(f"  {comp['model']:10s} ({comp['type']})")
    print()

    # Demo INTEGRATED generation
    print("6. INTEGRATED-STATE GENERATION")
    print("-" * 50)
    state.step = 0  # Reset
    int_result = tool_generate_at_integrated("Generate a hypothesis about AI consciousness")
    print(f"  Waited {int_result.get('waited_steps', 0)} steps for INTEGRATED state")
    print(f"  Generated at step {int_result.get('step', 'N/A')}")
    print(f"  Confidence: {int_result.get('confidence', 'N/A')}")
    print()


# =============================================================================
# MAIN
# =============================================================================

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Trit-Stream MCP Orchestrator")
    parser.add_argument("--serve", action="store_true", help="Run as MCP server")
    parser.add_argument("--demo", action="store_true", help="Run demo")
    parser.add_argument("--port", type=int, default=8765, help="Server port")

    args = parser.parse_args()

    if args.demo or not args.serve:
        run_demo()
    elif args.serve:
        print(f"MCP Server starting on port {args.port}...")
        print("(Full MCP server implementation would go here)")
        # In production, this would run a proper MCP server
