"""
Equivalence Class Convergence Detector (PLUS +1: Generator)

Detects when workspace systems reach ANIMA via equivalence class stabilization.
Tracks convergence across Gmail, Drive, Calendar, Tasks, Docs with:
- Enumerated entropy (distinct equivalence classes)
- Maximum entropy bounds
- Stability windows
- Convergence rate estimation
- Cross-service global ANIMA detection

GF(3) Trit: +1 (PLUS - Generator role in triadic system)
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple, Set, Any, Callable
from collections import deque
from enum import IntEnum
import hashlib
import time
import math


class Trit(IntEnum):
    MINUS = -1
    ERGODIC = 0
    PLUS = 1


class SplitMixTernary:
    """Deterministic RNG with GF(3) trit generation."""
    
    GOLDEN = 0x9E3779B97F4A7C15
    MIX1 = 0xBF58476D1CE4E5B9
    MIX2 = 0x94D049BB133111EB
    MASK64 = 0xFFFFFFFFFFFFFFFF
    
    def __init__(self, seed: int):
        self.state = seed & self.MASK64
        self.initial_seed = seed
        
    def next_u64(self) -> int:
        self.state = (self.state + self.GOLDEN) & self.MASK64
        z = self.state
        z = ((z ^ (z >> 30)) * self.MIX1) & self.MASK64
        z = ((z ^ (z >> 27)) * self.MIX2) & self.MASK64
        return (z ^ (z >> 31)) & self.MASK64
    
    def next_trit(self) -> Trit:
        """Generate GF(3) trit based on convergence state."""
        val = self.next_u64() % 3
        return Trit(val - 1)
    
    def next_float(self) -> float:
        return self.next_u64() / self.MASK64
    
    def split(self, n: int = 3) -> List['SplitMixTernary']:
        """Fork into n independent child generators."""
        return [SplitMixTernary((self.state + i * self.GOLDEN) & self.MASK64) 
                for i in range(n)]


@dataclass
class State:
    """Generic state representation for convergence tracking."""
    state_id: str
    data: Dict[str, Any] = field(default_factory=dict)
    timestamp: float = field(default_factory=time.time)
    trit: Trit = Trit.ERGODIC
    
    def state_tuple(self) -> Tuple:
        """Immutable representation for hashing."""
        sorted_data = tuple(sorted(self.data.items()))
        return (self.state_id, sorted_data, int(self.trit))


@dataclass
class AnimaResult:
    """Result of ANIMA detection."""
    at_anima: bool
    enum_entropy: int
    max_entropy: int
    stability_window: int
    convergence_rate: float
    reason: str
    condensed_fingerprint: Optional[str] = None
    gf3_balanced: bool = True


@dataclass
class ConvergenceMetrics:
    """Metrics for convergence detection."""
    enum_entropy: int      # Number of distinct equivalence classes
    max_entropy: int       # Maximum possible classes
    stability_window: int  # Consecutive stable iterations
    convergence_rate: float  # Rate of class reduction
    at_anima: bool         # enum_entropy == max_entropy and stable
    
    def progress(self) -> float:
        """Convergence progress as percentage."""
        if self.max_entropy == 0:
            return 100.0
        return 100.0 * self.enum_entropy / self.max_entropy
    
    def estimated_steps_to_anima(self) -> Optional[int]:
        """Estimate steps until ANIMA if rate is positive."""
        if self.at_anima:
            return 0
        if self.convergence_rate <= 0:
            return None  # Not converging
        remaining = self.max_entropy - self.enum_entropy
        return int(math.ceil(remaining / self.convergence_rate)) if self.convergence_rate > 0 else None


@dataclass
class PathStep:
    """Recommended action to accelerate convergence."""
    service: str
    action: str
    target_id: str
    expected_trit: Trit
    rationale: str
    priority: int = 0  # Higher = more important


class ConvergenceDetector:
    """
    Detects equivalence class convergence toward ANIMA fixed point.
    
    ANIMA is reached when:
    - All states collapse to stable equivalence classes
    - No new classes form for stability_threshold iterations
    - GF(3) conservation holds across the system
    """
    
    def __init__(
        self,
        window_size: int = 10,
        stability_threshold: int = 5,
        seed: int = 0x42D
    ):
        self.window_size = window_size
        self.stability_threshold = stability_threshold
        self.rng = SplitMixTernary(seed)
        
        self.class_history: List[Set[int]] = []
        self.state_history: List[State] = []
        self.class_counts: Dict[int, int] = {}
        self.trit_sum: int = 0
        self.iteration: int = 0
        self._stable_since: int = 0
        
    def update(self, state: State) -> ConvergenceMetrics:
        """Process new state, return current metrics."""
        self.iteration += 1
        self.state_history.append(state)
        
        equiv_class = self.compute_equivalence_class(state)
        
        current_classes = self.class_history[-1].copy() if self.class_history else set()
        was_new = equiv_class not in current_classes
        current_classes.add(equiv_class)
        self.class_history.append(current_classes)
        
        self.class_counts[equiv_class] = self.class_counts.get(equiv_class, 0) + 1
        self.trit_sum += int(state.trit)
        
        if was_new:
            self._stable_since = self.iteration
        
        if len(self.class_history) > self.window_size:
            self.class_history = self.class_history[-self.window_size:]
        if len(self.state_history) > self.window_size * 2:
            self.state_history = self.state_history[-self.window_size:]
        
        return self._compute_metrics()
    
    def compute_equivalence_class(self, state: State) -> int:
        """Hash state to equivalence class ID."""
        state_bytes = str(state.state_tuple()).encode('utf-8')
        hash_val = hashlib.sha256(state_bytes).hexdigest()[:8]
        return int(hash_val, 16)
    
    def detect_anima(self) -> AnimaResult:
        """Check if system has reached ANIMA fixed point."""
        metrics = self._compute_metrics()
        
        if metrics.at_anima:
            fingerprint = self._compute_condensed_fingerprint()
            return AnimaResult(
                at_anima=True,
                enum_entropy=metrics.enum_entropy,
                max_entropy=metrics.max_entropy,
                stability_window=metrics.stability_window,
                convergence_rate=metrics.convergence_rate,
                reason="stable_equilibrium",
                condensed_fingerprint=fingerprint,
                gf3_balanced=(self.trit_sum % 3 == 0)
            )
        
        reason = "converging"
        if not self.is_stable():
            reason = "classes_still_forming"
        elif metrics.enum_entropy < metrics.max_entropy:
            reason = "below_max_entropy"
        elif self.trit_sum % 3 != 0:
            reason = "gf3_imbalance"
            
        return AnimaResult(
            at_anima=False,
            enum_entropy=metrics.enum_entropy,
            max_entropy=metrics.max_entropy,
            stability_window=metrics.stability_window,
            convergence_rate=metrics.convergence_rate,
            reason=reason,
            gf3_balanced=(self.trit_sum % 3 == 0)
        )
    
    def enum_entropy(self) -> int:
        """Count distinct equivalence classes."""
        if not self.class_history:
            return 0
        return len(self.class_history[-1])
    
    def is_stable(self) -> bool:
        """True if no new classes in stability_threshold iterations."""
        return (self.iteration - self._stable_since) >= self.stability_threshold
    
    def condensation_candidates(self) -> List[Tuple[int, int]]:
        """Return pairs of classes that could be merged based on similarity."""
        if len(self.class_history) < 2:
            return []
        
        candidates = []
        current = list(self.class_history[-1])
        
        for i in range(len(current)):
            for j in range(i + 1, len(current)):
                count_i = self.class_counts.get(current[i], 0)
                count_j = self.class_counts.get(current[j], 0)
                if abs(count_i - count_j) <= 1:
                    candidates.append((current[i], current[j]))
        
        return candidates[:5]
    
    def project_convergence(self, steps: int) -> float:
        """Estimate probability of ANIMA within steps iterations."""
        if self.is_stable() and len(self.class_history) >= 2:
            metrics = self._compute_metrics()
            if metrics.at_anima:
                return 1.0
            
            growth_rate = self._estimate_class_growth_rate()
            
            if growth_rate <= 0:
                stable_steps = self.iteration - self._stable_since
                prob = min(1.0, stable_steps / self.stability_threshold)
                return prob
            
            decay = math.exp(-growth_rate * steps / 10)
            return 1.0 - decay
        
        if len(self.class_history) < 3:
            return 0.1
        
        recent_growth = []
        for i in range(-min(5, len(self.class_history)-1), 0):
            old_size = len(self.class_history[i-1])
            new_size = len(self.class_history[i])
            recent_growth.append(new_size - old_size)
        
        avg_growth = sum(recent_growth) / len(recent_growth) if recent_growth else 0
        
        if avg_growth <= 0:
            return min(0.8, 0.3 + 0.1 * steps)
        else:
            return max(0.05, 0.5 - 0.05 * avg_growth * steps)
    
    def _compute_metrics(self) -> ConvergenceMetrics:
        """Compute current convergence metrics."""
        current_entropy = self.enum_entropy()
        max_entropy = max(current_entropy, len(self.state_history))
        stability = self.iteration - self._stable_since
        rate = self._estimate_convergence_rate()
        
        at_anima = (
            self.is_stable() and
            current_entropy > 0 and
            (self.trit_sum % 3 == 0)
        )
        
        return ConvergenceMetrics(
            enum_entropy=current_entropy,
            max_entropy=max_entropy,
            stability_window=stability,
            convergence_rate=rate,
            at_anima=at_anima
        )
    
    def _estimate_convergence_rate(self) -> float:
        """Estimate rate at which classes are stabilizing."""
        if len(self.class_history) < 3:
            return 0.0
        
        window = min(5, len(self.class_history) - 1)
        changes = []
        
        for i in range(-window, 0):
            old_size = len(self.class_history[i-1])
            new_size = len(self.class_history[i])
            changes.append(abs(new_size - old_size))
        
        avg_change = sum(changes) / len(changes) if changes else 0
        return max(0, 1.0 - avg_change)
    
    def _estimate_class_growth_rate(self) -> float:
        """Estimate how fast new classes are appearing."""
        if len(self.class_history) < 3:
            return 0.5
        
        window = min(5, len(self.class_history) - 1)
        growths = []
        
        for i in range(-window, 0):
            old_size = len(self.class_history[i-1])
            new_size = len(self.class_history[i])
            growths.append(new_size - old_size)
        
        return sum(growths) / len(growths) if growths else 0
    
    def _compute_condensed_fingerprint(self) -> str:
        """Compute deterministic fingerprint of current classes."""
        sorted_classes = sorted(self.class_history[-1]) if self.class_history else []
        fingerprint_data = str(sorted_classes).encode('utf-8')
        return hashlib.sha256(fingerprint_data).hexdigest()[:16]
    
    def ascii_status(self) -> str:
        """Single-line ASCII status."""
        metrics = self._compute_metrics()
        stable = "✓" if self.is_stable() else "✗"
        anima = "✓" if metrics.at_anima else "✗"
        return f"E:{metrics.enum_entropy}/{metrics.max_entropy} S:{stable} A:{anima}"


class WorkspaceConvergence:
    """
    Cross-service convergence detection for Google Workspace.
    
    Tracks convergence independently per service and detects global ANIMA
    when all services reach stable equilibrium.
    """
    
    SERVICES = ['gmail', 'drive', 'calendar', 'tasks', 'docs']
    
    def __init__(self, acset: Any = None, seed: int = 0x42D):
        self.acset = acset
        self.rng = SplitMixTernary(seed)
        
        children = self.rng.split(len(self.SERVICES))
        self.detectors: Dict[str, ConvergenceDetector] = {
            service: ConvergenceDetector(seed=children[i].state)
            for i, service in enumerate(self.SERVICES)
        }
        
        self._last_metrics: Dict[str, ConvergenceMetrics] = {}
        self._global_trit_sum: int = 0
    
    def update(self, service: str, state: State) -> ConvergenceMetrics:
        """Update a single service's detector."""
        if service not in self.detectors:
            raise ValueError(f"Unknown service: {service}")
        
        self._global_trit_sum += int(state.trit)
        metrics = self.detectors[service].update(state)
        self._last_metrics[service] = metrics
        return metrics
    
    def update_all(self, workspace_state: Dict[str, List[State]]) -> Dict[str, ConvergenceMetrics]:
        """Update all service detectors with batch state."""
        results = {}
        for service, states in workspace_state.items():
            if service in self.detectors:
                for state in states:
                    results[service] = self.update(service, state)
        return results
    
    def global_anima(self) -> bool:
        """True if ALL services at ANIMA and GF(3) balanced."""
        if not self._last_metrics:
            return False
        
        all_at_anima = all(
            self.detectors[svc].detect_anima().at_anima
            for svc in self.SERVICES
        )
        
        gf3_balanced = (self._global_trit_sum % 3) == 0
        
        return all_at_anima and gf3_balanced
    
    def weakest_service(self) -> str:
        """Service furthest from ANIMA."""
        if not self._last_metrics:
            return self.SERVICES[0]
        
        worst_service = None
        worst_score = float('inf')
        
        for service in self.SERVICES:
            detector = self.detectors[service]
            metrics = detector._compute_metrics()
            
            stability = metrics.stability_window
            rate = metrics.convergence_rate
            score = stability * rate
            
            if not detector.is_stable():
                score = -1  # Unstable is worst
            
            if score < worst_score:
                worst_score = score
                worst_service = service
        
        return worst_service or self.SERVICES[0]
    
    def recommend_actions(self) -> List[PathStep]:
        """Suggest operations to accelerate convergence."""
        recommendations = []
        
        weakest = self.weakest_service()
        detector = self.detectors[weakest]
        
        current_trit_mod = self._global_trit_sum % 3
        if current_trit_mod == 1:
            needed_trit = Trit.MINUS
        elif current_trit_mod == 2:
            needed_trit = Trit.PLUS
        else:
            needed_trit = Trit.ERGODIC
        
        service_actions = {
            'gmail': [('read', Trit.MINUS), ('label', Trit.ERGODIC), ('send', Trit.PLUS)],
            'drive': [('list', Trit.ERGODIC), ('create', Trit.PLUS)],
            'calendar': [('get_events', Trit.MINUS), ('modify_event', Trit.ERGODIC), ('create_event', Trit.PLUS)],
            'tasks': [('list_tasks', Trit.MINUS), ('update_task', Trit.ERGODIC), ('create_task', Trit.PLUS)],
            'docs': [('read', Trit.MINUS), ('edit', Trit.ERGODIC), ('create', Trit.PLUS)],
        }
        
        for action, trit in service_actions.get(weakest, []):
            if trit == needed_trit or needed_trit == Trit.ERGODIC:
                recommendations.append(PathStep(
                    service=weakest,
                    action=action,
                    target_id="*",
                    expected_trit=trit,
                    rationale=f"Balance {weakest} (need trit={needed_trit.name})",
                    priority=10 if trit == needed_trit else 5
                ))
        
        for service, detector in self.detectors.items():
            if service == weakest:
                continue
            if not detector.is_stable():
                candidates = detector.condensation_candidates()
                if candidates:
                    recommendations.append(PathStep(
                        service=service,
                        action="condense",
                        target_id=str(candidates[0]),
                        expected_trit=Trit.ERGODIC,
                        rationale=f"Merge similar classes in {service}",
                        priority=3
                    ))
        
        return sorted(recommendations, key=lambda x: -x.priority)[:5]
    
    def service_metrics(self) -> Dict[str, ConvergenceMetrics]:
        """Get metrics for all services."""
        return {
            service: detector._compute_metrics()
            for service, detector in self.detectors.items()
        }
    
    def global_metrics(self) -> ConvergenceMetrics:
        """Aggregate metrics across all services."""
        all_metrics = self.service_metrics()
        
        total_enum = sum(m.enum_entropy for m in all_metrics.values())
        total_max = sum(m.max_entropy for m in all_metrics.values())
        min_stability = min(m.stability_window for m in all_metrics.values()) if all_metrics else 0
        avg_rate = (
            sum(m.convergence_rate for m in all_metrics.values()) / len(all_metrics)
            if all_metrics else 0.0
        )
        
        return ConvergenceMetrics(
            enum_entropy=total_enum,
            max_entropy=total_max,
            stability_window=min_stability,
            convergence_rate=avg_rate,
            at_anima=self.global_anima()
        )
    
    def ascii_table(self) -> str:
        """ASCII visualization of convergence progress."""
        lines = []
        lines.append("Service    | EnumEnt | MaxEnt | Stable | ANIMA")
        lines.append("-----------+---------+--------+--------+------")
        
        for service in self.SERVICES:
            detector = self.detectors[service]
            metrics = detector._compute_metrics()
            anima = detector.detect_anima()
            
            stable = "✓" if detector.is_stable() else "✗"
            at_anima = "✓" if anima.at_anima else "✗"
            
            svc_name = service.capitalize().ljust(10)
            enum_str = str(metrics.enum_entropy).center(7)
            max_str = str(metrics.max_entropy).center(6)
            stable_str = stable.center(6)
            anima_str = at_anima.center(5)
            
            lines.append(f"{svc_name} |{enum_str}|{max_str}|{stable_str}|{anima_str}")
        
        lines.append("-----------+---------+--------+--------+------")
        
        global_m = self.global_metrics()
        global_stable = "✓" if global_m.stability_window >= 5 else "✗"
        global_anima = "✓" if global_m.at_anima else "✗"
        
        enum_str = str(global_m.enum_entropy).center(7)
        max_str = str(global_m.max_entropy).center(6)
        stable_str = global_stable.center(6)
        anima_str = global_anima.center(5)
        
        lines.append(f"Global     |{enum_str}|{max_str}|{stable_str}|{anima_str}")
        
        return "\n".join(lines)


def convergence_affects_trit(detector: ConvergenceDetector) -> Trit:
    """
    GF(3) integration: convergence state affects trit generation.
    
    - Converging (rate > 0.5): Favor ERGODIC (stabilizing)
    - Stable: Favor PLUS (generating new states safe)
    - Diverging: Favor MINUS (need validation)
    """
    metrics = detector._compute_metrics()
    
    if metrics.at_anima:
        return detector.rng.next_trit()
    
    if metrics.convergence_rate > 0.7:
        weights = [0.2, 0.6, 0.2]
    elif metrics.convergence_rate > 0.3:
        weights = [0.25, 0.5, 0.25]
    else:
        weights = [0.5, 0.3, 0.2]
    
    r = detector.rng.next_float()
    if r < weights[0]:
        return Trit.MINUS
    elif r < weights[0] + weights[1]:
        return Trit.ERGODIC
    else:
        return Trit.PLUS


if __name__ == "__main__":
    print("=" * 60)
    print("Convergence Detector Demo")
    print("=" * 60)
    
    ws_conv = WorkspaceConvergence(seed=0x42D)
    
    for i in range(20):
        for service in WorkspaceConvergence.SERVICES:
            state = State(
                state_id=f"{service}_state_{i % 3}",
                data={"iteration": i, "value": i % 5},
                trit=ws_conv.rng.next_trit()
            )
            ws_conv.update(service, state)
    
    for i in range(10):
        for service in WorkspaceConvergence.SERVICES:
            state = State(
                state_id=f"{service}_stable",
                data={"stable": True},
                trit=Trit.ERGODIC
            )
            ws_conv.update(service, state)
    
    print("\n" + ws_conv.ascii_table())
    
    print(f"\nGlobal ANIMA: {ws_conv.global_anima()}")
    print(f"Weakest Service: {ws_conv.weakest_service()}")
    
    print("\nRecommended Actions:")
    for action in ws_conv.recommend_actions():
        print(f"  [{action.priority}] {action.service}:{action.action} - {action.rationale}")
    
    detector = ws_conv.detectors['gmail']
    print(f"\nGmail convergence projection (10 steps): {detector.project_convergence(10):.2%}")
    print(f"Condensation candidates: {detector.condensation_candidates()}")
