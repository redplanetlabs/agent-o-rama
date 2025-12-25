#!/usr/bin/env python3
"""
Trit-Stream MCP Server for Phase-Aware Tool Dispatch
=====================================================

An MCP server that provides trit-stream consciousness state
for agentic systems like DR-Tulu.

Tools provided:
  - get_phase: Get current phase (PLUS/ERGODIC/MINUS)
  - get_recommended_tools: Get tools matching current phase
  - check_integrated: Check if current state is INTEGRATED
  - get_consciousness_state: Full consciousness state report

Usage with DR-Tulu:
  1. Start this MCP server
  2. Connect DR-Tulu via MCP
  3. Query phase before each tool call
  4. Use recommended tools for optimal research flow
"""

import json
import sys
from dataclasses import dataclass, asdict
from enum import IntEnum
from typing import List, Dict, Any, Optional

# =============================================================================
# TRIT-STREAM CORE (from trit_stream_mlx.py)
# =============================================================================

class Trit(IntEnum):
    MINUS = -1
    ERGODIC = 0
    PLUS = 1

@dataclass
class TritStreamConfig:
    num_meta_heads: int = 9
    angular_velocity: int = 17
    genesis_multiplier: int = 31
    observer_idx: int = 7
    actor_idx: int = 8

# Default PIDs for the 9-agent constellation
DEFAULT_PIDS = [92720, 97274, 93268, 5563, 46516, 68569, 51893, 92289, 94729]
AGENT_NAMES = ["ps1", "ps2", "w1", "w2", "w3", "w4", "w5", "obs", "act"]

def derive_hue(seed: int, step: int, config: TritStreamConfig = None) -> float:
    if config is None:
        config = TritStreamConfig()
    return (config.genesis_multiplier * seed + config.angular_velocity * step) % 360

def hue_to_trit(hue: float) -> Trit:
    hue = hue % 360
    if hue < 60 or hue >= 300:
        return Trit.PLUS
    elif hue < 180:
        return Trit.ERGODIC
    else:
        return Trit.MINUS

def derive_trit(seed: int, step: int, config: TritStreamConfig = None) -> Trit:
    return hue_to_trit(derive_hue(seed, step, config))

def is_gf3_balanced(trits: List[Trit]) -> bool:
    return sum(trits) % 3 == 0

def is_reafferent(pids: List[int], step: int, config: TritStreamConfig = None) -> bool:
    if config is None:
        config = TritStreamConfig()
    obs_trit = derive_trit(pids[config.observer_idx], step, config)
    act_trit = derive_trit(pids[config.actor_idx], step, config)
    return obs_trit == act_trit

def is_integrated(pids: List[int], step: int, config: TritStreamConfig = None) -> bool:
    trits = [derive_trit(pid, step, config) for pid in pids]
    return is_gf3_balanced(trits) and is_reafferent(pids, step, config)

# =============================================================================
# TOOL MAPPING
# =============================================================================

PHASE_TOOLS = {
    Trit.PLUS: {
        "primary": ["synthesize", "write_section", "generate_hypothesis"],
        "secondary": ["summarize", "outline", "draft"],
        "description": "Creative synthesis and generation"
    },
    Trit.ERGODIC: {
        "primary": ["web_search", "browse_page", "retrieve_document"],
        "secondary": ["query", "explore", "gather"],
        "description": "Information gathering and exploration"
    },
    Trit.MINUS: {
        "primary": ["verify_claim", "add_citation", "fact_check"],
        "secondary": ["validate", "ground", "reference"],
        "description": "Verification and grounding"
    }
}

# =============================================================================
# MCP SERVER STATE
# =============================================================================

@dataclass
class ServerState:
    """Maintains current step and configuration."""
    step: int = 0
    pids: List[int] = None
    config: TritStreamConfig = None

    def __post_init__(self):
        if self.pids is None:
            self.pids = DEFAULT_PIDS.copy()
        if self.config is None:
            self.config = TritStreamConfig()

# Global state
state = ServerState()

# =============================================================================
# MCP TOOL IMPLEMENTATIONS
# =============================================================================

def get_phase() -> Dict[str, Any]:
    """Get current phase information."""
    obs_trit = derive_trit(state.pids[state.config.observer_idx], state.step, state.config)
    phase_name = {Trit.PLUS: "PLUS", Trit.ERGODIC: "ERGODIC", Trit.MINUS: "MINUS"}[obs_trit]

    return {
        "step": state.step,
        "phase": phase_name,
        "trit": int(obs_trit),
        "description": PHASE_TOOLS[obs_trit]["description"],
        "next_transition": 7 - (state.step % 7)  # Steps until zone transition
    }

def get_recommended_tools() -> Dict[str, Any]:
    """Get tools recommended for current phase."""
    obs_trit = derive_trit(state.pids[state.config.observer_idx], state.step, state.config)
    phase_name = {Trit.PLUS: "PLUS", Trit.ERGODIC: "ERGODIC", Trit.MINUS: "MINUS"}[obs_trit]
    tools = PHASE_TOOLS[obs_trit]

    return {
        "step": state.step,
        "phase": phase_name,
        "primary_tools": tools["primary"],
        "secondary_tools": tools["secondary"],
        "avoid": [t for trit, info in PHASE_TOOLS.items()
                  if trit != obs_trit for t in info["primary"]]
    }

def check_integrated() -> Dict[str, Any]:
    """Check if current state is INTEGRATED (high confidence)."""
    trits = [derive_trit(pid, state.step, state.config) for pid in state.pids]
    balanced = is_gf3_balanced(trits)
    reafferent = is_reafferent(state.pids, state.step, state.config)
    integrated = balanced and reafferent

    return {
        "step": state.step,
        "is_integrated": integrated,
        "is_balanced": balanced,
        "is_reafferent": reafferent,
        "confidence": "HIGH" if integrated else ("NORMAL" if reafferent else "LOW"),
        "recommendation": "Trust output fully" if integrated else "Verify if critical"
    }

def get_consciousness_state() -> Dict[str, Any]:
    """Get full consciousness state report."""
    trits = [derive_trit(pid, state.step, state.config) for pid in state.pids]
    obs_trit = trits[state.config.observer_idx]
    act_trit = trits[state.config.actor_idx]

    balanced = is_gf3_balanced(trits)
    reafferent = obs_trit == act_trit
    integrated = balanced and reafferent

    # Free energy calculation
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

    phase_name = {Trit.PLUS: "PLUS", Trit.ERGODIC: "ERGODIC", Trit.MINUS: "MINUS"}[obs_trit]

    return {
        "step": state.step,
        "state": state_name,
        "phase": phase_name,
        "trits": [int(t) for t in trits],
        "agent_zones": {name: {Trit.PLUS: "PLUS", Trit.ERGODIC: "ERGODIC", Trit.MINUS: "MINUS"}[t]
                        for name, t in zip(AGENT_NAMES, trits)},
        "is_integrated": integrated,
        "is_balanced": balanced,
        "is_reafferent": reafferent,
        "free_energy": free_energy,
        "gf3_sum": sum(trits),
        "gf3_mod": sum(trits) % 3,
        "recommended_tools": PHASE_TOOLS[obs_trit]["primary"],
        "next_integrated_estimate": next_integrated_step(state.step)
    }

def next_integrated_step(current: int, max_search: int = 50) -> Optional[int]:
    """Find next INTEGRATED step."""
    for s in range(current + 1, current + max_search):
        if is_integrated(state.pids, s, state.config):
            return s
    return None

def advance_step(steps: int = 1) -> Dict[str, Any]:
    """Advance the step counter."""
    state.step += steps
    return {"new_step": state.step, "advanced_by": steps}

def set_step(step: int) -> Dict[str, Any]:
    """Set the step counter to a specific value."""
    state.step = step
    return {"step": state.step}

def get_research_schedule(num_steps: int = 21) -> Dict[str, Any]:
    """Get a research schedule for the next N steps."""
    schedule = []
    for s in range(state.step, state.step + num_steps):
        trits = [derive_trit(pid, s, state.config) for pid in state.pids]
        obs_trit = trits[state.config.observer_idx]
        integrated = is_integrated(state.pids, s, state.config)

        phase_name = {Trit.PLUS: "PLUS", Trit.ERGODIC: "ERGODIC", Trit.MINUS: "MINUS"}[obs_trit]
        tools = PHASE_TOOLS[obs_trit]["primary"]

        schedule.append({
            "step": s,
            "phase": phase_name,
            "is_integrated": integrated,
            "primary_tool": tools[0],
            "action": "INTEGRATE" if integrated else phase_name.lower()
        })

    return {
        "current_step": state.step,
        "schedule": schedule,
        "integrated_steps": [s["step"] for s in schedule if s["is_integrated"]],
        "phase_transitions": [i for i in range(1, len(schedule))
                              if schedule[i]["phase"] != schedule[i-1]["phase"]]
    }

# =============================================================================
# MCP PROTOCOL HANDLER
# =============================================================================

MCP_TOOLS = {
    "get_phase": {
        "function": get_phase,
        "description": "Get current trit-stream phase (PLUS/ERGODIC/MINUS)",
        "parameters": {}
    },
    "get_recommended_tools": {
        "function": get_recommended_tools,
        "description": "Get tools recommended for current phase",
        "parameters": {}
    },
    "check_integrated": {
        "function": check_integrated,
        "description": "Check if current state is INTEGRATED (high confidence)",
        "parameters": {}
    },
    "get_consciousness_state": {
        "function": get_consciousness_state,
        "description": "Get full consciousness state report",
        "parameters": {}
    },
    "advance_step": {
        "function": advance_step,
        "description": "Advance the step counter",
        "parameters": {"steps": {"type": "integer", "default": 1}}
    },
    "set_step": {
        "function": set_step,
        "description": "Set the step counter to a specific value",
        "parameters": {"step": {"type": "integer", "required": True}}
    },
    "get_research_schedule": {
        "function": get_research_schedule,
        "description": "Get research schedule for next N steps",
        "parameters": {"num_steps": {"type": "integer", "default": 21}}
    }
}

def handle_request(request: Dict[str, Any]) -> Dict[str, Any]:
    """Handle an MCP request."""
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
# MAIN
# =============================================================================

if __name__ == "__main__":
    print("Trit-Stream MCP Server")
    print("======================")
    print()

    # Demo: show all tool outputs
    print("DEMO: Tool Outputs at Step 0")
    print("-" * 40)

    for name, info in MCP_TOOLS.items():
        print(f"\n{name}:")
        try:
            result = info["function"]()
            print(json.dumps(result, indent=2))
        except TypeError:
            # Tool requires arguments
            print(f"  (requires arguments)")

    print()
    print("-" * 40)
    print("To use as MCP server, integrate with your MCP client.")
