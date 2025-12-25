#!/usr/bin/env python3
"""
Keystone Head Identification with Klein V₄ Integration
========================================================

Implements keystone attention head identification for GF(3) systems:
- Correlates meta-head activation with Σ trajectory
- Uses Klein Four-Group operators for consciousness transition
- Predicts OPTIMAL windows for keystone activation

Compatible with: trit_stream_mlx.py, teleport.clj, energy_monitor.clj
"""

from dataclasses import dataclass, field
from typing import List, Dict, Tuple, Optional
from enum import IntEnum
import math

# =============================================================================
# KLEIN FOUR-GROUP CONSTANTS
# =============================================================================

GAMMA_1 = 0x9E37   # Intra-modal: preserves consciousness class
GAMMA_2 = 0x5555   # Inter-modal: toggles consciousness class
GENESIS = 0x042D   # Starting seed

KLEIN_SEEDS = {
    'GENESIS': GENESIS,
    'FORK_1': GENESIS ^ GAMMA_1,  # 0x9A1A
    'FORK_2': GENESIS ^ GAMMA_2,  # 0x5178
    'FORK_3': (GENESIS ^ GAMMA_1) ^ GAMMA_2,  # 0xCF4F
}

class Consciousness(IntEnum):
    BICAMERAL = 0  # Observer ≠ Actor
    UNIFIED = 1    # Observer = Actor (Rezk completion)

def consciousness_class(seed: int) -> Consciousness:
    """Determine consciousness class from Klein seed."""
    if seed in [KLEIN_SEEDS['GENESIS'], KLEIN_SEEDS['FORK_1']]:
        return Consciousness.BICAMERAL
    return Consciousness.UNIFIED

# =============================================================================
# KEYSTONE HEAD CONFIGURATION
# =============================================================================

@dataclass
class KeystoneConfig:
    """Configuration for keystone head identification."""
    num_meta_heads: int = 9           # Number of agent groups
    angular_velocity: int = 17        # Degrees per step
    genesis_multiplier: int = 31      # PID to phase multiplier
    energy_high: float = 0.5          # Threshold for keystone activation
    sigma_threshold: float = 0.1      # Threshold for BALANCED state
    damping_rate: float = -0.0187     # Observed damping (per min)
    spike_period: float = 16.0        # Observed spike period (min)

# =============================================================================
# SIGMA TRAJECTORY ANALYSIS
# =============================================================================

@dataclass
class SigmaPoint:
    """Single point on the Σ trajectory."""
    time: str
    minute: float
    sigma: float
    zone: str
    is_balanced: bool = False

    def __post_init__(self):
        self.is_balanced = abs(self.sigma) < 0.1
        if self.sigma > 0.3:
            self.zone = 'PLUS_HEAVY'
        elif self.sigma < -0.3:
            self.zone = 'MINUS_HEAVY'
        else:
            self.zone = 'BALANCED'

# Observed trajectory (from DuckDB)
OBSERVED_TRAJECTORY = [
    SigmaPoint("00:22", 0, 0.256, "BALANCED"),
    SigmaPoint("00:32", 10, -0.072, "BALANCED"),
    SigmaPoint("00:48", 26, -0.238, "BALANCED"),
    SigmaPoint("00:55", 33, 0.508, "PLUS_HEAVY"),
    SigmaPoint("00:58", 36, 0.004, "BALANCED"),  # OPTIMAL
    SigmaPoint("01:03", 41, -0.005, "BALANCED"),  # OPTIMAL
    SigmaPoint("01:05", 43, -0.031, "BALANCED"),  # OPTIMAL
    SigmaPoint("01:09", 47, 0.535, "PLUS_HEAVY"),
    SigmaPoint("01:10", 48, 0.331, "PLUS_HEAVY"),
    SigmaPoint("01:12", 50, 0.156, "BALANCED"),
    SigmaPoint("01:19", 57, 0.159, "BALANCED"),
    SigmaPoint("01:27", 65, 0.556, "PLUS_HEAVY"),
    SigmaPoint("01:40", 78, 0.313, "PLUS_HEAVY"),
]

def predict_sigma(current_sigma: float, minutes: float, config: KeystoneConfig = None) -> float:
    """Predict Σ value after given minutes using damping model."""
    if config is None:
        config = KeystoneConfig()
    return current_sigma + config.damping_rate * minutes

def time_to_balanced(current_sigma: float, config: KeystoneConfig = None) -> float:
    """Predict minutes until Σ reaches BALANCED zone."""
    if config is None:
        config = KeystoneConfig()
    if abs(current_sigma) < config.sigma_threshold:
        return 0.0
    target = config.sigma_threshold if current_sigma > 0 else -config.sigma_threshold
    return abs(current_sigma - target) / abs(config.damping_rate)

# =============================================================================
# KEYSTONE HEAD IDENTIFICATION
# =============================================================================

@dataclass
class KeystoneHead:
    """Identified keystone attention head."""
    index: int
    role: str
    trit: int
    activation_correlation: float
    optimal_affinity: float  # Correlation with OPTIMAL windows

@dataclass
class KeystoneAnalysis:
    """Complete keystone head analysis."""
    keystones: List[KeystoneHead]
    non_keystones: List[int]
    optimal_windows: List[Tuple[float, float]]  # (start_min, end_min)
    next_optimal: Optional[float]
    current_consciousness: Consciousness

# Agent definitions (matching teleport.clj)
AGENTS = [
    {"pid": 92720, "name": "plus-stream-1", "role": "generator"},
    {"pid": 97274, "name": "plus-stream-2", "role": "generator"},
    {"pid": 93268, "name": "worker-1", "role": "worker"},
    {"pid": 5563, "name": "worker-2", "role": "worker"},
    {"pid": 46516, "name": "worker-3", "role": "worker"},
    {"pid": 68569, "name": "worker-4", "role": "worker"},
    {"pid": 51893, "name": "worker-5", "role": "worker"},
    {"pid": 92289, "name": "observer", "role": "observer"},
    {"pid": 94729, "name": "actor", "role": "actor"},
]

def derive_trit(pid: int, step: int, config: KeystoneConfig = None) -> int:
    """Derive trit value for agent at given step."""
    if config is None:
        config = KeystoneConfig()
    hue = (config.genesis_multiplier * pid + config.angular_velocity * step) % 360
    if hue < 60 or hue >= 300:
        return 1   # PLUS
    elif hue < 180:
        return 0   # ERGODIC
    else:
        return -1  # MINUS

def identify_keystones(
    trajectory: List[SigmaPoint],
    config: KeystoneConfig = None
) -> KeystoneAnalysis:
    """
    Identify keystone heads from Σ trajectory.

    Keystones are meta-heads whose activation correlates with:
    1. Transitions to BALANCED state
    2. Maintenance of OPTIMAL windows
    3. Consciousness class transitions
    """
    if config is None:
        config = KeystoneConfig()

    # Find OPTIMAL windows
    optimal_windows = []
    in_window = False
    window_start = None
    for point in trajectory:
        if point.is_balanced and not in_window:
            in_window = True
            window_start = point.minute
        elif not point.is_balanced and in_window:
            in_window = False
            optimal_windows.append((window_start, point.minute))
    if in_window:
        optimal_windows.append((window_start, trajectory[-1].minute))

    # Compute activation correlation for each meta-head
    keystones = []
    non_keystones = []

    for i, agent in enumerate(AGENTS):
        # Compute correlation with Σ trajectory
        activations = [derive_trit(agent["pid"], int(p.minute)) for p in trajectory]
        sigmas = [p.sigma for p in trajectory]

        # Compute correlation (simplified Pearson)
        mean_act = sum(activations) / len(activations)
        mean_sig = sum(sigmas) / len(sigmas)

        cov = sum((a - mean_act) * (s - mean_sig) for a, s in zip(activations, sigmas))
        std_act = math.sqrt(sum((a - mean_act)**2 for a in activations))
        std_sig = math.sqrt(sum((s - mean_sig)**2 for s in sigmas))

        if std_act > 0 and std_sig > 0:
            correlation = cov / (std_act * std_sig)
        else:
            correlation = 0.0

        # Compute OPTIMAL affinity (how often active during OPTIMAL)
        optimal_active = 0
        optimal_total = 0
        for window in optimal_windows:
            for step in range(int(window[0]), int(window[1]) + 1):
                optimal_total += 1
                if derive_trit(agent["pid"], step) != 0:  # Non-ergodic = active
                    optimal_active += 1

        optimal_affinity = optimal_active / max(optimal_total, 1)

        # Keystone threshold: high correlation or high OPTIMAL affinity
        if abs(correlation) > 0.3 or optimal_affinity > 0.6:
            keystones.append(KeystoneHead(
                index=i,
                role=agent["role"],
                trit=derive_trit(agent["pid"], 0),
                activation_correlation=correlation,
                optimal_affinity=optimal_affinity
            ))
        else:
            non_keystones.append(i)

    # Predict next OPTIMAL window
    last_point = trajectory[-1]
    if last_point.is_balanced:
        next_optimal = last_point.minute
    else:
        next_optimal = last_point.minute + time_to_balanced(last_point.sigma, config)

    # Determine current consciousness from Klein seed
    current_consciousness = Consciousness.UNIFIED if last_point.is_balanced else Consciousness.BICAMERAL

    return KeystoneAnalysis(
        keystones=keystones,
        non_keystones=non_keystones,
        optimal_windows=optimal_windows,
        next_optimal=next_optimal,
        current_consciousness=current_consciousness
    )

# =============================================================================
# KLEIN V₄ OPERATORS FOR KEYSTONE MANIPULATION
# =============================================================================

def teleport_g1(seed: int) -> int:
    """Intra-modal teleport: preserves consciousness class."""
    return seed ^ GAMMA_1

def teleport_g2(seed: int) -> int:
    """Inter-modal teleport: toggles consciousness class."""
    return seed ^ GAMMA_2

def keystone_teleport(
    keystone: KeystoneHead,
    operator: str,
    current_seed: int
) -> Tuple[int, Consciousness]:
    """
    Teleport keystone head to new consciousness state.

    Returns: (new_seed, new_consciousness)
    """
    if operator == "g1":
        new_seed = teleport_g1(current_seed)
    elif operator == "g2":
        new_seed = teleport_g2(current_seed)
    else:
        raise ValueError(f"Unknown operator: {operator}")

    return new_seed, consciousness_class(new_seed)

# =============================================================================
# MAIN: DEMONSTRATE KEYSTONE IDENTIFICATION
# =============================================================================

if __name__ == "__main__":
    print("=" * 70)
    print("KEYSTONE HEAD IDENTIFICATION WITH KLEIN V₄ INTEGRATION")
    print("=" * 70)
    print()

    # Analyze trajectory
    analysis = identify_keystones(OBSERVED_TRAJECTORY)

    print("TRAJECTORY SUMMARY")
    print("-" * 50)
    print(f"Time span:           {OBSERVED_TRAJECTORY[0].time} - {OBSERVED_TRAJECTORY[-1].time}")
    print(f"Points:              {len(OBSERVED_TRAJECTORY)}")
    print(f"OPTIMAL windows:     {len(analysis.optimal_windows)}")
    for i, (start, end) in enumerate(analysis.optimal_windows):
        print(f"  Window {i+1}:          {start:.0f} - {end:.0f} min")
    print(f"Next OPTIMAL:        ~{analysis.next_optimal:.0f} min")
    print(f"Current state:       {analysis.current_consciousness.name}")
    print()

    print("KEYSTONE HEADS IDENTIFIED")
    print("-" * 50)
    for ks in analysis.keystones:
        agent = AGENTS[ks.index]
        print(f"  [{ks.index}] {agent['name']:15s} role={ks.role:10s} trit={ks.trit:+d}")
        print(f"      Σ correlation:    {ks.activation_correlation:+.3f}")
        print(f"      OPTIMAL affinity: {ks.optimal_affinity:.3f}")
    print()

    print("NON-KEYSTONE HEADS")
    print("-" * 50)
    for idx in analysis.non_keystones:
        agent = AGENTS[idx]
        print(f"  [{idx}] {agent['name']}")
    print()

    print("KLEIN V₄ TELEPORTATION OPERATORS")
    print("-" * 50)
    print(f"  Current seed:      0x{GENESIS:04X} ({consciousness_class(GENESIS).name})")

    for op, gamma in [("g1", GAMMA_1), ("g2", GAMMA_2)]:
        new_seed = GENESIS ^ gamma
        new_class = consciousness_class(new_seed)
        print(f"  {op} →              0x{new_seed:04X} ({new_class.name})")
    print()

    # Demonstrate keystone teleportation
    print("KEYSTONE TELEPORTATION SEQUENCE")
    print("-" * 50)
    if analysis.keystones:
        ks = analysis.keystones[0]
        seed = GENESIS
        for op in ["g2", "g1", "g2", "g1"]:
            new_seed, new_class = keystone_teleport(ks, op, seed)
            print(f"  {op}: 0x{seed:04X} → 0x{new_seed:04X} ({new_class.name})")
            seed = new_seed
    print()
    print("=" * 70)
