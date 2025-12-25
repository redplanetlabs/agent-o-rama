"""
Cross-Service Path Verifier with Auto-Balancing

ERGODIC (0): Coordinator role - verifies path equivalences across services
with GF(3) conservation and automatic balancing.

Implements:
- Composable PathStep and ServicePath with >> operator
- PathVerifier for commutativity and equivalence tracking
- EquivalenceTracker for convergence detection
- Predefined cross-service workflows

Trit: ERGODIC (0) - Coordinator/synthesizer
"""

from __future__ import annotations
from dataclasses import dataclass, field
from typing import List, Dict, Set, Optional, Tuple, Any, Callable
from enum import IntEnum
import hashlib
from collections import defaultdict

try:
    from workspace_acset import (
        WorkspaceACSet, service_verb_trit,
        DRIVE_VERB_TRIT_MAP, CALENDAR_VERB_TRIT_MAP, TASKS_VERB_TRIT_MAP
    )
    from gmail_acset import Trit, VERB_TRIT_MAP
except ImportError:
    class Trit(IntEnum):
        MINUS = -1
        ERGODIC = 0
        PLUS = 1
    
    VERB_TRIT_MAP = {
        "read": Trit.MINUS, "search": Trit.MINUS,
        "label": Trit.ERGODIC, "archive": Trit.ERGODIC, "star": Trit.ERGODIC,
        "send": Trit.PLUS, "reply": Trit.PLUS,
    }
    DRIVE_VERB_TRIT_MAP = {
        "list": Trit.ERGODIC, "search": Trit.ERGODIC, "share": Trit.ERGODIC,
        "get": Trit.MINUS,
        "create": Trit.PLUS, "upload": Trit.PLUS,
    }
    CALENDAR_VERB_TRIT_MAP = {
        "get": Trit.MINUS, "list": Trit.MINUS,
        "modify": Trit.ERGODIC,
        "create": Trit.PLUS,
    }
    TASKS_VERB_TRIT_MAP = {
        "list": Trit.MINUS, "get": Trit.MINUS,
        "update": Trit.ERGODIC, "complete": Trit.ERGODIC,
        "create": Trit.PLUS,
    }


# =============================================================================
# Service Trit Maps
# =============================================================================

SERVICE_TRIT_MAPS = {
    "gmail": {
        "read": Trit.MINUS, "search": Trit.MINUS, "get": Trit.MINUS,
        "label": Trit.ERGODIC, "archive": Trit.ERGODIC, "star": Trit.ERGODIC,
        "send": Trit.PLUS, "reply": Trit.PLUS, "create": Trit.PLUS,
    },
    "drive": {
        "list": Trit.ERGODIC, "share": Trit.ERGODIC,
        "get": Trit.MINUS, "search": Trit.MINUS,
        "create": Trit.PLUS, "upload": Trit.PLUS,
    },
    "calendar": {
        "get": Trit.MINUS, "list": Trit.MINUS,
        "modify": Trit.ERGODIC,
        "create": Trit.PLUS,
    },
    "tasks": {
        "list": Trit.MINUS, "get": Trit.MINUS,
        "update": Trit.ERGODIC, "complete": Trit.ERGODIC,
        "create": Trit.PLUS,
    },
    "docs": {
        "get": Trit.MINUS, "read": Trit.MINUS,
        "modify": Trit.ERGODIC, "update": Trit.ERGODIC,
        "create": Trit.PLUS,
    },
}

def get_verb_trit(service: str, verb: str) -> Trit:
    """Get GF(3) trit for a service:verb combination."""
    verb_map = SERVICE_TRIT_MAPS.get(service.lower(), {})
    return verb_map.get(verb.lower(), Trit.ERGODIC)


# =============================================================================
# PathStep: Atomic Cross-Service Operation
# =============================================================================

@dataclass
class PathStep:
    """Single atomic operation in a cross-service workflow."""
    service: str  # gmail, drive, calendar, tasks, docs
    operation: str  # verb
    trit: int  # -1, 0, +1
    metadata: Dict[str, Any] = field(default_factory=dict)
    
    @classmethod
    def from_verb(cls, service: str, verb: str, **metadata) -> PathStep:
        """Create PathStep with auto-computed trit."""
        trit = int(get_verb_trit(service, verb))
        return cls(service=service, operation=verb, trit=trit, metadata=metadata)
    
    def __rshift__(self, other: PathStep) -> ServicePath:
        """Compose steps: step1 >> step2 → ServicePath."""
        return ServicePath(steps=[self, other])
    
    def __repr__(self) -> str:
        trit_sym = {-1: "−", 0: "○", 1: "+"}[self.trit]
        return f"{self.service}.{self.operation}[{trit_sym}]"
    
    @property
    def qualified_name(self) -> str:
        return f"{self.service}:{self.operation}"


# =============================================================================
# ServicePath: Composable Path of Operations
# =============================================================================

@dataclass
class ServicePath:
    """Composable sequence of cross-service operations."""
    steps: List[PathStep] = field(default_factory=list)
    name: str = ""
    description: str = ""
    
    def __post_init__(self):
        if not self.name and self.steps:
            self.name = " >> ".join(str(s) for s in self.steps)
    
    @property
    def trit_sum(self) -> int:
        """Raw sum of all trits in the path."""
        return sum(step.trit for step in self.steps)
    
    @property
    def gf3_sum(self) -> int:
        """GF(3) sum: trit_sum mod 3."""
        return self.trit_sum % 3
    
    @property
    def is_balanced(self) -> bool:
        """Check if path satisfies GF(3) conservation: Σ trits ≡ 0 (mod 3)."""
        return self.gf3_sum == 0
    
    def __rshift__(self, other: ServicePath | PathStep) -> ServicePath:
        """Compose paths: path1 >> path2 or path >> step."""
        if isinstance(other, PathStep):
            return ServicePath(
                steps=self.steps + [other],
                name=f"{self.name} >> {other}",
                description=self.description
            )
        return ServicePath(
            steps=self.steps + other.steps,
            name=f"{self.name} >> {other.name}",
            description=f"{self.description}; {other.description}"
        )
    
    def balance(self) -> ServicePath:
        """Auto-add steps to achieve GF(3) = 0 (trit_sum % 3 == 0)."""
        if self.is_balanced:
            return self
        
        raw_sum = self.trit_sum
        remainder = raw_sum % 3
        balancing_steps = []
        
        # GF(3) arithmetic: need total to be ≡ 0 (mod 3)
        # remainder=1: add steps summing to 2 (or -1 ≡ 2 mod 3)
        #   Two PLUS (+1+1=2) or one step that sums to 2
        # remainder=2: add step summing to 1 (one PLUS)
        
        if remainder == 1:
            # Need +2 more, use two MINUS steps (-1 * 2 = -2 ≡ 1 mod 3)
            # Actually: 1 + 2 = 3 ≡ 0, so add two PLUS steps
            balancing_steps.append(PathStep.from_verb("tasks", "create"))  # +1
            balancing_steps.append(PathStep.from_verb("gmail", "send"))    # +1
        elif remainder == 2:
            # Need +1 more: 2 + 1 = 3 ≡ 0 mod 3
            balancing_steps.append(PathStep.from_verb("tasks", "create"))  # +1
        
        return ServicePath(
            steps=self.steps + balancing_steps,
            name=f"{self.name}_balanced",
            description=f"{self.description} (auto-balanced)"
        )
    
    @property
    def services_traversed(self) -> List[str]:
        """List of unique services in traversal order."""
        seen = []
        for step in self.steps:
            if step.service not in seen:
                seen.append(step.service)
        return seen
    
    def visualize_trit_flow(self) -> str:
        """ASCII visualization of trit flow through path."""
        lines = []
        lines.append("┌" + "─" * 60 + "┐")
        lines.append(f"│ Path: {self.name[:54]:54} │")
        lines.append("├" + "─" * 60 + "┤")
        
        # Trit flow visualization
        flow_line = "│ "
        cumulative = 0
        
        for i, step in enumerate(self.steps):
            trit_sym = {-1: "━", 0: "○", 1: "╋"}[step.trit]
            cumulative += step.trit
            
            if i > 0:
                flow_line += " → "
            
            flow_line += f"{step.service[:4]}{trit_sym}"
        
        flow_line = f"{flow_line:60}│"
        lines.append(flow_line)
        
        # Balance bar
        lines.append("│" + " " * 60 + "│")
        balance_sym = "✓" if self.is_balanced else "✗"
        bal_line = f"│ Σ={self.trit_sum:+d}  GF(3)={self.gf3_sum}  Balanced={balance_sym}"
        lines.append(f"{bal_line:61}│")
        
        lines.append("└" + "─" * 60 + "┘")
        
        return "\n".join(lines)


# =============================================================================
# State Representation for Equivalence Tracking
# =============================================================================

@dataclass
class State:
    """Represents workflow state for equivalence tracking."""
    data: Dict[str, Any] = field(default_factory=dict)
    trit_history: List[int] = field(default_factory=list)
    services_touched: Set[str] = field(default_factory=set)
    timestamp: int = 0
    
    @property
    def hash(self) -> str:
        """Deterministic hash of state for equivalence comparison."""
        content = str(sorted(self.data.items())) + str(sorted(self.services_touched))
        return hashlib.sha256(content.encode()).hexdigest()[:16]
    
    def apply_step(self, step: PathStep) -> State:
        """Apply a path step and return new state."""
        new_data = self.data.copy()
        new_data[f"{step.service}:{step.operation}"] = True
        new_data[f"_last_{step.service}"] = step.operation
        
        return State(
            data=new_data,
            trit_history=self.trit_history + [step.trit],
            services_touched=self.services_touched | {step.service},
            timestamp=self.timestamp + 1
        )


# =============================================================================
# EquivalenceTracker: Track State Equivalence Classes
# =============================================================================

class EquivalenceTracker:
    """Track state equivalence classes for convergence detection."""
    
    def __init__(self):
        self.classes: Dict[int, Set[str]] = {}  # class_id → state_hashes
        self.hash_to_class: Dict[str, int] = {}  # state_hash → class_id
        self.history: List[Tuple[int, str]] = []  # (class_id, state_hash)
        self._next_class_id = 0
    
    def classify(self, state: State) -> int:
        """Assign state to equivalence class, creating new class if needed."""
        state_hash = state.hash
        
        if state_hash in self.hash_to_class:
            class_id = self.hash_to_class[state_hash]
        else:
            # Create new equivalence class
            class_id = self._next_class_id
            self._next_class_id += 1
            self.classes[class_id] = {state_hash}
            self.hash_to_class[state_hash] = class_id
        
        self.history.append((class_id, state_hash))
        return class_id
    
    def has_converged(self, window: int = 5) -> bool:
        """Check if equivalence classes are stable over window iterations."""
        if len(self.history) < window:
            return False
        
        recent = self.history[-window:]
        class_ids = [h[0] for h in recent]
        
        # Converged if all recent classifications are the same
        return len(set(class_ids)) == 1
    
    def merge_classes(self, class1: int, class2: int) -> int:
        """Merge two equivalent classes (condensation)."""
        if class1 not in self.classes or class2 not in self.classes:
            return class1
        
        if class1 == class2:
            return class1
        
        # Merge class2 into class1
        self.classes[class1] |= self.classes[class2]
        
        # Update hash mappings
        for state_hash in self.classes[class2]:
            self.hash_to_class[state_hash] = class1
        
        del self.classes[class2]
        return class1
    
    @property
    def num_classes(self) -> int:
        return len(self.classes)
    
    def class_sizes(self) -> Dict[int, int]:
        return {cid: len(hashes) for cid, hashes in self.classes.items()}


# =============================================================================
# PathVerifier: Main Verification Engine
# =============================================================================

class PathVerifier:
    """Cross-service path verification with GF(3) balancing."""
    
    # ERGODIC balancing operations by service
    ERGODIC_OPS = {
        "gmail": PathStep.from_verb("gmail", "label"),
        "drive": PathStep.from_verb("drive", "share"),
        "calendar": PathStep.from_verb("calendar", "modify"),
        "tasks": PathStep.from_verb("tasks", "update"),
        "docs": PathStep.from_verb("docs", "modify"),
    }
    
    def __init__(self, acset: Optional[Any] = None):
        self.acset = acset
        self.equivalence_tracker = EquivalenceTracker()
        self._verification_history: List[Dict] = []
    
    def execute_path(self, path: ServicePath, initial_state: State = None) -> State:
        """Execute a path and return final state."""
        state = initial_state or State()
        
        for step in path.steps:
            state = state.apply_step(step)
        
        return state
    
    def verify_commutativity(
        self,
        path1: ServicePath,
        path2: ServicePath,
        initial_state: State = None
    ) -> bool:
        """Check if path1 and path2 produce equivalent end states."""
        initial = initial_state or State()
        
        final1 = self.execute_path(path1, initial)
        final2 = self.execute_path(path2, initial)
        
        # States are equivalent if they have the same hash
        equivalent = final1.hash == final2.hash
        
        # Track in equivalence tracker
        class1 = self.equivalence_tracker.classify(final1)
        class2 = self.equivalence_tracker.classify(final2)
        
        if equivalent and class1 != class2:
            # Merge equivalent classes
            self.equivalence_tracker.merge_classes(class1, class2)
        
        self._verification_history.append({
            "path1": path1.name,
            "path2": path2.name,
            "equivalent": equivalent,
            "class1": class1,
            "class2": class2,
            "gf3_1": path1.gf3_sum,
            "gf3_2": path2.gf3_sum,
        })
        
        return equivalent
    
    def auto_balance(self, path: ServicePath) -> ServicePath:
        """Insert minimal ERGODIC operations to achieve GF(3) = 0."""
        if path.is_balanced:
            return path
        
        balanced = path.balance()
        
        # If still unbalanced after simple balancing, add ERGODIC steps
        if not balanced.is_balanced:
            # Add ERGODIC step (trit=0) which doesn't change sum
            # but we need actual balancing
            pass
        
        return balanced
    
    def find_balancing_operations(self, current_sum: int) -> List[PathStep]:
        """Suggest operations that balance the trit sum to achieve GF(3)=0."""
        remainder = current_sum % 3
        
        if remainder == 0:
            return []  # Already balanced
        
        suggestions = []
        
        if remainder == 1:
            # Need to add -1 (MINUS operations)
            suggestions.extend([
                PathStep.from_verb("gmail", "read"),
                PathStep.from_verb("gmail", "search"),
                PathStep.from_verb("drive", "get"),
                PathStep.from_verb("calendar", "get"),
                PathStep.from_verb("tasks", "list"),
            ])
        elif remainder == 2:
            # Need to add +1 (PLUS operations)
            suggestions.extend([
                PathStep.from_verb("gmail", "send"),
                PathStep.from_verb("drive", "create"),
                PathStep.from_verb("calendar", "create"),
                PathStep.from_verb("tasks", "create"),
            ])
        
        return suggestions
    
    def track_equivalence_class(self, state: State) -> int:
        """Return equivalence class ID for state."""
        return self.equivalence_tracker.classify(state)
    
    def detect_convergence(self, history: List[State], window: int = 5) -> bool:
        """True if equivalence classes stable for window iterations."""
        if len(history) < window:
            return False
        
        # Classify all states in window
        for state in history[-window:]:
            self.equivalence_tracker.classify(state)
        
        return self.equivalence_tracker.has_converged(window)
    
    def verify_path(self, path: ServicePath) -> Dict[str, Any]:
        """Comprehensive verification of a single path."""
        initial = State()
        final = self.execute_path(path, initial)
        
        return {
            "path": path.name,
            "steps": len(path.steps),
            "services": path.services_traversed,
            "trit_sum": path.trit_sum,
            "gf3_sum": path.gf3_sum,
            "is_balanced": path.is_balanced,
            "final_state_hash": final.hash,
            "equivalence_class": self.track_equivalence_class(final),
        }
    
    def verify_all_paths(self, paths: List[ServicePath]) -> Dict[str, Any]:
        """Verify multiple paths and detect commutativity relations."""
        results = {
            "paths": [],
            "commutativity_matrix": [],
            "equivalence_classes": {},
            "all_balanced": True,
        }
        
        # Verify each path
        for path in paths:
            result = self.verify_path(path)
            results["paths"].append(result)
            if not result["is_balanced"]:
                results["all_balanced"] = False
        
        # Build commutativity matrix
        n = len(paths)
        matrix = [[None for _ in range(n)] for _ in range(n)]
        
        for i in range(n):
            for j in range(i, n):
                equiv = self.verify_commutativity(paths[i], paths[j])
                matrix[i][j] = equiv
                matrix[j][i] = equiv
        
        results["commutativity_matrix"] = matrix
        results["equivalence_classes"] = self.equivalence_tracker.class_sizes()
        
        return results


# =============================================================================
# Predefined Cross-Service Paths
# =============================================================================

def build_predefined_paths() -> Dict[str, ServicePath]:
    """Build predefined cross-service workflow paths."""
    
    paths = {}
    
    # gmail_to_task: gmail.search >> tasks.create
    paths["gmail_to_task"] = ServicePath(
        steps=[
            PathStep.from_verb("gmail", "search"),   # -1
            PathStep.from_verb("tasks", "create"),   # +1
        ],
        name="gmail_to_task",
        description="Search Gmail → Create Task (Σ=0 ✓)"
    )
    
    # gmail_to_event: gmail.search >> calendar.create
    paths["gmail_to_event"] = ServicePath(
        steps=[
            PathStep.from_verb("gmail", "search"),   # -1
            PathStep.from_verb("calendar", "create"),# +1
        ],
        name="gmail_to_event",
        description="Search Gmail → Create Calendar Event (Σ=0 ✓)"
    )
    
    # full_gtd: gmail.search >> tasks.create >> calendar.create >> tasks.complete
    paths["full_gtd"] = ServicePath(
        steps=[
            PathStep.from_verb("gmail", "search"),   # -1
            PathStep.from_verb("tasks", "create"),   # +1
            PathStep.from_verb("calendar", "create"),# +1
            PathStep.from_verb("tasks", "complete"), # 0
        ],
        name="full_gtd",
        description="Full GTD: Gmail → Task → Event → Complete (Σ=1, needs balancing)"
    )
    
    # file_review: drive.get >> docs.create >> gmail.send
    paths["file_review"] = ServicePath(
        steps=[
            PathStep.from_verb("drive", "get"),      # -1
            PathStep.from_verb("docs", "create"),    # +1
            PathStep.from_verb("gmail", "send"),     # +1
        ],
        name="file_review",
        description="File Review: Drive → Docs → Gmail (Σ=1, needs balancing)"
    )
    
    # meeting_prep: calendar.get >> docs.create >> drive.share >> gmail.send
    paths["meeting_prep"] = ServicePath(
        steps=[
            PathStep.from_verb("calendar", "get"),   # -1
            PathStep.from_verb("docs", "create"),    # +1
            PathStep.from_verb("drive", "share"),    # 0
            PathStep.from_verb("gmail", "send"),     # +1
        ],
        name="meeting_prep",
        description="Meeting Prep: Calendar → Docs → Drive → Gmail (Σ=1)"
    )
    
    # Balanced versions
    paths["full_gtd_balanced"] = paths["full_gtd"].balance()
    paths["file_review_balanced"] = paths["file_review"].balance()
    paths["meeting_prep_balanced"] = paths["meeting_prep"].balance()
    
    return paths


PREDEFINED_PATHS = build_predefined_paths()


# =============================================================================
# Visualization
# =============================================================================

def visualize_path_matrix(verifier: PathVerifier, paths: List[ServicePath]) -> str:
    """ASCII visualization of path equivalence matrix."""
    n = len(paths)
    lines = []
    
    # Header
    lines.append("┌" + "─" * (8 + 4 * n) + "┐")
    lines.append("│ PATH EQUIVALENCE MATRIX" + " " * (8 + 4 * n - 24) + "│")
    lines.append("├" + "─" * 8 + "┬" + ("───┬" * (n - 1)) + "───┤")
    
    # Column headers
    header = "│        │"
    for i, path in enumerate(paths):
        header += f" {i} │"
    lines.append(header)
    lines.append("├" + "─" * 8 + "┼" + ("───┼" * (n - 1)) + "───┤")
    
    # Verify all pairs
    results = verifier.verify_all_paths(paths)
    matrix = results["commutativity_matrix"]
    
    for i, path in enumerate(paths):
        row = f"│ {i}:{path.name[:5]:5} │"
        for j in range(n):
            if matrix[i][j]:
                row += " ✓ │"
            else:
                row += " ✗ │"
        lines.append(row)
    
    lines.append("└" + "─" * 8 + "┴" + ("───┴" * (n - 1)) + "───┘")
    
    return "\n".join(lines)


def visualize_all_paths() -> str:
    """Visualize all predefined paths with trit flow."""
    lines = []
    lines.append("=" * 62)
    lines.append(" CROSS-SERVICE PATH VERIFIER - GF(3) Balance Report")
    lines.append("=" * 62)
    
    for name, path in sorted(PREDEFINED_PATHS.items()):
        if "balanced" in name:
            continue  # Skip auto-balanced duplicates
        
        lines.append("")
        lines.append(path.visualize_trit_flow())
        
        if not path.is_balanced:
            balanced = path.balance()
            lines.append(f"  → Balanced version: {balanced.name}")
    
    return "\n".join(lines)


# =============================================================================
# Main Entry Point
# =============================================================================

def main():
    """Demo the cross-service path verifier."""
    print(visualize_all_paths())
    
    print("\n" + "=" * 62)
    print(" PATH COMMUTATIVITY VERIFICATION")
    print("=" * 62)
    
    verifier = PathVerifier()
    
    # Test specific paths
    paths_to_test = [
        PREDEFINED_PATHS["gmail_to_task"],
        PREDEFINED_PATHS["gmail_to_event"],
        PREDEFINED_PATHS["full_gtd_balanced"],
    ]
    
    print(visualize_path_matrix(verifier, paths_to_test))
    
    # Show equivalence classes
    print("\nEquivalence Classes:")
    for class_id, size in verifier.equivalence_tracker.class_sizes().items():
        print(f"  Class {class_id}: {size} states")
    
    # Demonstrate balancing
    print("\n" + "=" * 62)
    print(" AUTO-BALANCING DEMONSTRATION")
    print("=" * 62)
    
    unbalanced = PREDEFINED_PATHS["file_review"]
    print(f"\nUnbalanced path: {unbalanced.name}")
    print(f"  Trit sum: {unbalanced.trit_sum}, GF(3): {unbalanced.gf3_sum}")
    
    suggestions = verifier.find_balancing_operations(unbalanced.trit_sum)
    print(f"\nSuggested balancing operations:")
    for step in suggestions[:3]:
        print(f"  • {step}")
    
    balanced = verifier.auto_balance(unbalanced)
    print(f"\nBalanced path: {balanced.name}")
    print(f"  Trit sum: {balanced.trit_sum}, GF(3): {balanced.gf3_sum}")
    print(f"  Is balanced: {'✓' if balanced.is_balanced else '✗'}")


if __name__ == "__main__":
    main()
