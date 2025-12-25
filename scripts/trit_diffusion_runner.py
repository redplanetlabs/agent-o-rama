#!/usr/bin/env python3
"""
Trit-Stream Diffusion Runner
=============================

Phase-aware diffusion LLM inference on MLX.
Integrates trit-stream consciousness with LLaDA/Dream generation.

PLUS phase:   Creative unmasking (higher temperature)
ERGODIC phase: Exploratory unmasking (balanced)
MINUS phase:  Conservative unmasking (lower temperature)

Usage:
    python trit_diffusion_runner.py --model llada --prompt "Your prompt"
    python trit_diffusion_runner.py --phase-aware --steps 100
"""

import argparse
import subprocess
import sys
from dataclasses import dataclass
from enum import IntEnum
from typing import List, Dict, Any, Optional

# =============================================================================
# TRIT-STREAM CORE
# =============================================================================

class Trit(IntEnum):
    MINUS = -1
    ERGODIC = 0
    PLUS = 1


@dataclass
class TritConfig:
    angular_velocity: int = 17
    genesis_multiplier: int = 31
    observer_idx: int = 7
    actor_idx: int = 8


DEFAULT_PIDS = [92720, 97274, 93268, 5563, 46516, 68569, 51893, 92289, 94729]


def derive_trit(seed: int, step: int, config: TritConfig = None) -> Trit:
    """Derive trit from seed and step."""
    if config is None:
        config = TritConfig()
    hue = (config.genesis_multiplier * seed + config.angular_velocity * step) % 360
    if hue < 60 or hue >= 300:
        return Trit.PLUS
    elif hue < 180:
        return Trit.ERGODIC
    else:
        return Trit.MINUS


def is_integrated(pids: List[int], step: int, config: TritConfig = None) -> bool:
    """Check if current step is INTEGRATED (high confidence)."""
    if config is None:
        config = TritConfig()
    trits = [derive_trit(pid, step, config) for pid in pids]
    balanced = sum(trits) % 3 == 0
    reafferent = trits[config.observer_idx] == trits[config.actor_idx]
    return balanced and reafferent


def get_phase(step: int, pids: List[int] = None, config: TritConfig = None) -> Dict:
    """Get current phase information."""
    if pids is None:
        pids = DEFAULT_PIDS
    if config is None:
        config = TritConfig()

    obs_trit = derive_trit(pids[config.observer_idx], step, config)
    integrated = is_integrated(pids, step, config)

    return {
        "step": step,
        "phase": {Trit.PLUS: "PLUS", Trit.ERGODIC: "ERGODIC", Trit.MINUS: "MINUS"}[obs_trit],
        "trit": int(obs_trit),
        "is_integrated": integrated,
        "confidence": "HIGH" if integrated else "NORMAL",
    }


# =============================================================================
# MLX MODEL CONFIGURATIONS
# =============================================================================

MODELS = {
    # Diffusion models
    "llada": {
        "path": "mlx-community/LLaDA2.0-flash-8bit",
        "type": "diffusion",
        "ready": True,
    },
    "llada-fp16": {
        "path": "GSAI-ML/LLaDA-8B-Instruct",
        "type": "diffusion",
        "ready": False,
        "convert_cmd": "python -m mlx_lm.convert --hf-path GSAI-ML/LLaDA-8B-Instruct --mlx-path ./llada-8b-mlx -q",
    },
    "dream": {
        "path": "Dream-org/Dream-v0-Instruct-7B",
        "type": "diffusion",
        "ready": False,
        "convert_cmd": "python -m mlx_lm.convert --hf-path Dream-org/Dream-v0-Instruct-7B --mlx-path ./dream-7b-mlx -q",
    },

    # Autoregressive comparison
    "qwen": {
        "path": "mlx-community/Qwen2.5-7B-Instruct-4bit",
        "type": "autoregressive",
        "ready": True,
    },
    "llama": {
        "path": "mlx-community/Llama-3.2-3B-Instruct-4bit",
        "type": "autoregressive",
        "ready": True,
    },
    "dr-tulu": {
        "path": "Plurigrid/DR-Tulu-8B-MLX-8bit",
        "type": "autoregressive",
        "ready": True,
    },
}


# =============================================================================
# PHASE-AWARE GENERATION PARAMETERS
# =============================================================================

def get_phase_params(trit: int) -> Dict[str, float]:
    """Get generation parameters based on trit phase."""
    params = {
        Trit.PLUS: {
            "temperature": 0.9,
            "top_p": 0.95,
            "description": "Creative/generative mode",
        },
        Trit.ERGODIC: {
            "temperature": 0.7,
            "top_p": 0.9,
            "description": "Balanced/exploratory mode",
        },
        Trit.MINUS: {
            "temperature": 0.4,
            "top_p": 0.8,
            "description": "Conservative/verification mode",
        },
    }
    return params.get(trit, params[Trit.ERGODIC])


# =============================================================================
# RUNNER
# =============================================================================

def run_generation(
    model_name: str,
    prompt: str,
    step: int = 0,
    phase_aware: bool = True,
    max_tokens: int = 256,
    pids: List[int] = None,
) -> Dict:
    """Run generation with phase-aware parameters."""

    if pids is None:
        pids = DEFAULT_PIDS

    # Get phase information
    phase_info = get_phase(step, pids)

    # Get model configuration
    if model_name not in MODELS:
        available = ", ".join(MODELS.keys())
        raise ValueError(f"Unknown model: {model_name}. Available: {available}")

    model_config = MODELS[model_name]

    # Check if model is ready
    if not model_config["ready"]:
        print(f"Model {model_name} needs conversion first:")
        print(f"  {model_config.get('convert_cmd', 'See documentation')}")
        return {"error": "Model not ready"}

    # Get phase-aware parameters
    if phase_aware:
        params = get_phase_params(phase_info["trit"])
        temp = params["temperature"]
        top_p = params["top_p"]
    else:
        temp = 0.7
        top_p = 0.9

    # Build command
    cmd = [
        sys.executable, "-m", "mlx_lm.generate",
        "--model", model_config["path"],
        "--prompt", prompt,
        "--max-tokens", str(max_tokens),
        "--temp", str(temp),
        "--top-p", str(top_p),
    ]

    result = {
        "model": model_name,
        "model_path": model_config["path"],
        "model_type": model_config["type"],
        "prompt": prompt,
        "phase": phase_info,
        "parameters": {"temperature": temp, "top_p": top_p},
        "command": " ".join(cmd),
    }

    print("=" * 70)
    print("TRIT-STREAM DIFFUSION RUNNER")
    print("=" * 70)
    print()
    print(f"Model: {model_name} ({model_config['type']})")
    print(f"Path:  {model_config['path']}")
    print()
    print(f"Step:  {step}")
    print(f"Phase: {phase_info['phase']} (trit={phase_info['trit']})")
    print(f"INTEGRATED: {phase_info['is_integrated']}")
    print(f"Confidence: {phase_info['confidence']}")
    print()
    print(f"Temperature: {temp}")
    print(f"Top-p:       {top_p}")
    print()
    print("Command:")
    print(f"  {' '.join(cmd)}")
    print()

    # Execute (optional - comment out for dry run)
    # try:
    #     output = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    #     result["output"] = output.stdout
    #     result["error"] = output.stderr if output.returncode != 0 else None
    # except Exception as e:
    #     result["error"] = str(e)

    return result


def find_next_integrated(start_step: int, max_search: int = 50, pids: List[int] = None) -> Optional[int]:
    """Find next INTEGRATED step."""
    if pids is None:
        pids = DEFAULT_PIDS
    for step in range(start_step, start_step + max_search):
        if is_integrated(pids, step):
            return step
    return None


def run_integrated_generation(
    model_name: str,
    prompt: str,
    start_step: int = 0,
    max_tokens: int = 256,
) -> Dict:
    """Wait for INTEGRATED state then generate."""
    next_int = find_next_integrated(start_step)

    if next_int is None:
        print(f"No INTEGRATED state found in next 50 steps from {start_step}")
        return {"error": "No INTEGRATED state found"}

    print(f"Found INTEGRATED state at step {next_int}")
    print(f"(Skipped {next_int - start_step} steps)")
    print()

    return run_generation(model_name, prompt, step=next_int, phase_aware=True, max_tokens=max_tokens)


# =============================================================================
# SCHEDULE DISPLAY
# =============================================================================

def display_schedule(start_step: int = 0, num_steps: int = 21, pids: List[int] = None):
    """Display upcoming phase schedule."""
    if pids is None:
        pids = DEFAULT_PIDS

    print("=" * 70)
    print("TRIT-STREAM PHASE SCHEDULE")
    print("=" * 70)
    print()
    print("Step | Phase   | Int? | Temp | Recommended Action")
    print("-----|---------|------|------|--------------------")

    for step in range(start_step, start_step + num_steps):
        phase = get_phase(step, pids)
        params = get_phase_params(phase["trit"])
        status = "★" if phase["is_integrated"] else " "

        action = {
            "PLUS": "Generate/synthesize",
            "ERGODIC": "Explore/search",
            "MINUS": "Verify/ground",
        }.get(phase["phase"], "")

        if phase["is_integrated"]:
            action = "HIGH CONFIDENCE: " + action

        print(f" {step:3d} | {phase['phase']:7s} | {status:4s} | {params['temperature']:.1f}  | {action}")

    print()
    integrated_steps = [s for s in range(start_step, start_step + num_steps) if is_integrated(pids, s)]
    print(f"INTEGRATED steps: {integrated_steps}")
    print(f"Duty cycle: {len(integrated_steps)}/{num_steps} = {100*len(integrated_steps)/num_steps:.1f}%")
    print()


# =============================================================================
# MAIN
# =============================================================================

def main():
    parser = argparse.ArgumentParser(description="Trit-Stream Diffusion Runner")
    parser.add_argument("--model", "-m", default="llada", choices=list(MODELS.keys()),
                        help="Model to use")
    parser.add_argument("--prompt", "-p", default="Explain diffusion language models",
                        help="Prompt for generation")
    parser.add_argument("--step", "-s", type=int, default=0,
                        help="Current step in trit-stream")
    parser.add_argument("--phase-aware", "-a", action="store_true",
                        help="Use phase-aware parameters")
    parser.add_argument("--wait-integrated", "-w", action="store_true",
                        help="Wait for INTEGRATED state before generating")
    parser.add_argument("--schedule", action="store_true",
                        help="Display phase schedule")
    parser.add_argument("--max-tokens", "-t", type=int, default=256,
                        help="Maximum tokens to generate")
    parser.add_argument("--list-models", "-l", action="store_true",
                        help="List available models")

    args = parser.parse_args()

    if args.list_models:
        print("Available models:")
        print("-" * 50)
        for name, config in MODELS.items():
            status = "✓ Ready" if config["ready"] else "⚠ Needs conversion"
            print(f"  {name:15s} {config['type']:15s} {status}")
        return

    if args.schedule:
        display_schedule(args.step)
        return

    if args.wait_integrated:
        run_integrated_generation(args.model, args.prompt, args.step, args.max_tokens)
    else:
        run_generation(args.model, args.prompt, args.step, args.phase_aware, args.max_tokens)


if __name__ == "__main__":
    main()
