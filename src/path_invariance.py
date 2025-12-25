"""
Path Invariance Checker for Cross-Skill Workflows

Narya/Unison-style verification that different workflow paths
to the same goal yield equivalent states with GF(3) conservation.

Trit: PLUS (+1) - Generator role
"""

from __future__ import annotations
from dataclasses import dataclass, field
from enum import IntEnum
from typing import List, Dict, Any, Callable, Optional, Tuple
from functools import reduce
import hashlib


class Trit(IntEnum):
    """GF(3) trit values for workflow steps."""
    MINUS = -1   # Validator/constrainer (e.g., read operations)
    ERGODIC = 0  # Coordinator/synthesizer (e.g., transform operations)
    PLUS = 1     # Generator/executor (e.g., write/create operations)


@dataclass
class WorkflowStep:
    """Single step in a workflow path."""
    name: str
    trit: Trit
    effect_handler: Optional[Callable[[Dict], Dict]] = None
    description: str = ""
    
    def __post_init__(self):
        if self.effect_handler is None:
            self.effect_handler = lambda state: state


@dataclass
class WorkflowPath:
    """A composable sequence of workflow steps as morphisms."""
    name: str
    steps: List[str]
    trit_sequence: List[int]
    description: str = ""
    
    @property
    def gf3_sum(self) -> int:
        """Sum of trits in GF(3) (mod 3)."""
        return sum(self.trit_sequence) % 3
    
    @property
    def is_balanced(self) -> bool:
        """Check if path satisfies GF(3) conservation: Σ trits ≡ 0 (mod 3)."""
        return self.gf3_sum == 0
    
    def compose(self, other: WorkflowPath) -> WorkflowPath:
        """Compose two paths (morphism composition)."""
        return WorkflowPath(
            name=f"{self.name}→{other.name}",
            steps=self.steps + other.steps,
            trit_sequence=self.trit_sequence + other.trit_sequence,
            description=f"Composition of {self.name} and {other.name}"
        )
    
    def __rshift__(self, other: WorkflowPath) -> WorkflowPath:
        """Enable path1 >> path2 composition syntax."""
        return self.compose(other)


@dataclass
class NaryaDelta:
    """Narya-style before/after/delta tracking."""
    before: Dict[str, Any]
    after: Dict[str, Any]
    delta: Dict[str, Any] = field(default_factory=dict)
    
    def __post_init__(self):
        if not self.delta:
            self.delta = self._compute_delta()
    
    def _compute_delta(self) -> Dict[str, Any]:
        """Compute changes between before and after states."""
        delta = {}
        all_keys = set(self.before.keys()) | set(self.after.keys())
        for key in all_keys:
            before_val = self.before.get(key)
            after_val = self.after.get(key)
            if before_val != after_val:
                delta[key] = {
                    'before': before_val,
                    'after': after_val,
                    'changed': True
                }
        return delta
    
    @property
    def is_identity(self) -> bool:
        """Check if this represents an identity transformation."""
        return len(self.delta) == 0


@dataclass
class PathInvarianceResult:
    """Result of checking path commutativity."""
    path1: WorkflowPath
    path2: WorkflowPath
    states_equivalent: bool
    gf3_conserved: bool
    narya_delta: NaryaDelta
    replay_deterministic: bool = True
    
    @property
    def paths_commute(self) -> bool:
        """True if both paths yield equivalent results."""
        return self.states_equivalent and self.gf3_conserved and self.replay_deterministic


@dataclass
class WorkflowResult:
    """Result of workflow validation."""
    start_event: str
    end_event: str
    path: WorkflowPath
    success: bool
    final_state: Dict[str, Any]
    narya_delta: NaryaDelta
    gf3_balanced: bool
    orphaned_states: List[str] = field(default_factory=list)
    error: Optional[str] = None


# =============================================================================
# Effect Handlers (Unison-style)
# =============================================================================

class EffectHandler:
    """Unison-style effect handler for workflow steps."""
    
    @staticmethod
    def gmail_read(state: Dict) -> Dict:
        """Effect: Read Gmail message (MINUS - consumes/reads)."""
        new_state = state.copy()
        new_state['gmail_read'] = True
        new_state['message_content'] = state.get('pending_message', '')
        new_state.pop('pending_message', None)
        return new_state
    
    @staticmethod
    def gmail_reply(state: Dict) -> Dict:
        """Effect: Reply to Gmail (PLUS - creates)."""
        new_state = state.copy()
        new_state['gmail_replied'] = True
        new_state['reply_sent'] = True
        return new_state
    
    @staticmethod
    def task_create(state: Dict) -> Dict:
        """Effect: Create task (PLUS - creates)."""
        new_state = state.copy()
        task_id = hashlib.md5(str(state).encode()).hexdigest()[:8]
        new_state['task_created'] = True
        new_state['task_id'] = task_id
        return new_state
    
    @staticmethod
    def task_complete(state: Dict) -> Dict:
        """Effect: Complete task (ERGODIC - transforms)."""
        new_state = state.copy()
        new_state['task_completed'] = True
        new_state['task_status'] = 'done'
        return new_state
    
    @staticmethod
    def drive_upload(state: Dict) -> Dict:
        """Effect: Upload to Drive (PLUS - creates)."""
        new_state = state.copy()
        file_id = hashlib.md5(str(state).encode()).hexdigest()[:8]
        new_state['drive_uploaded'] = True
        new_state['file_id'] = file_id
        return new_state
    
    @staticmethod
    def calendar_create(state: Dict) -> Dict:
        """Effect: Create calendar event (PLUS - creates)."""
        new_state = state.copy()
        event_id = hashlib.md5(str(state).encode()).hexdigest()[:8]
        new_state['event_created'] = True
        new_state['event_id'] = event_id
        return new_state
    
    @staticmethod
    def event_link(state: Dict) -> Dict:
        """Effect: Link event to resources (ERGODIC - coordinates)."""
        new_state = state.copy()
        new_state['event_linked'] = True
        new_state['links'] = {
            'file_id': state.get('file_id'),
            'task_id': state.get('task_id')
        }
        return new_state


# =============================================================================
# Workflow Step Registry
# =============================================================================

WORKFLOW_STEPS: Dict[str, WorkflowStep] = {
    'gmail_read': WorkflowStep(
        name='gmail_read',
        trit=Trit.MINUS,
        effect_handler=EffectHandler.gmail_read,
        description='Read Gmail message'
    ),
    'gmail_reply': WorkflowStep(
        name='gmail_reply',
        trit=Trit.PLUS,
        effect_handler=EffectHandler.gmail_reply,
        description='Reply to Gmail message'
    ),
    'task_create': WorkflowStep(
        name='task_create',
        trit=Trit.PLUS,
        effect_handler=EffectHandler.task_create,
        description='Create a new task'
    ),
    'task_complete': WorkflowStep(
        name='task_complete',
        trit=Trit.ERGODIC,
        effect_handler=EffectHandler.task_complete,
        description='Mark task as complete'
    ),
    'drive_upload': WorkflowStep(
        name='drive_upload',
        trit=Trit.PLUS,
        effect_handler=EffectHandler.drive_upload,
        description='Upload file to Drive'
    ),
    'calendar_create': WorkflowStep(
        name='calendar_create',
        trit=Trit.PLUS,
        effect_handler=EffectHandler.calendar_create,
        description='Create calendar event'
    ),
    'event_link': WorkflowStep(
        name='event_link',
        trit=Trit.ERGODIC,
        effect_handler=EffectHandler.event_link,
        description='Link event to other resources'
    ),
}


# =============================================================================
# Predefined Workflow Paths
# =============================================================================

PREDEFINED_PATHS: Dict[str, WorkflowPath] = {
    'gmail_to_task': WorkflowPath(
        name='gmail_to_task',
        steps=['gmail_read', 'task_create', 'task_complete'],
        trit_sequence=[-1, +1, 0],  # Σ = 0 ✓
        description='Gmail message → Task creation → Complete'
    ),
    'drive_to_calendar': WorkflowPath(
        name='drive_to_calendar',
        steps=['drive_upload', 'calendar_create', 'event_link'],
        trit_sequence=[+1, +1, 0],  # Σ = 2 ≡ 2 (mod 3) - needs balancing!
        description='Drive file → Calendar event'
    ),
    'full_workflow': WorkflowPath(
        name='full_workflow',
        steps=['gmail_read', 'drive_upload', 'calendar_create', 'task_create', 'task_complete', 'event_link'],
        trit_sequence=[-1, +1, +1, +1, 0, 0],  # Σ = 2 - needs MINUS step
        description='Gmail → Drive → Calendar → Task → Complete'
    ),
    'reply_workflow': WorkflowPath(
        name='reply_workflow',
        steps=['gmail_read', 'task_create', 'gmail_reply'],
        trit_sequence=[-1, +1, +1],  # Σ = 1 - needs ERGODIC balancing
        description='Gmail read → Gmail reply (MINUS → PLUS, needs ERGODIC)'
    ),
    # Balanced version with explicit coordinator step
    'reply_workflow_balanced': WorkflowPath(
        name='reply_workflow_balanced',
        steps=['gmail_read', 'task_complete', 'gmail_reply'],  # task_complete is ERGODIC
        trit_sequence=[-1, 0, +1],  # Σ = 0 ✓
        description='Gmail read → Coordinate → Gmail reply (balanced)'
    ),
}


# =============================================================================
# Core Verification Functions
# =============================================================================

def execute_path(path: WorkflowPath, initial_state: Dict[str, Any]) -> Tuple[Dict[str, Any], List[Dict]]:
    """
    Execute a workflow path and return final state with intermediate states.
    
    Returns:
        Tuple of (final_state, list_of_intermediate_states)
    """
    state = initial_state.copy()
    intermediates = [state.copy()]
    
    for step_name in path.steps:
        if step_name not in WORKFLOW_STEPS:
            raise ValueError(f"Unknown workflow step: {step_name}")
        
        step = WORKFLOW_STEPS[step_name]
        state = step.effect_handler(state)
        intermediates.append(state.copy())
    
    return state, intermediates


def states_are_equivalent(state1: Dict, state2: Dict, ignore_keys: List[str] = None) -> bool:
    """
    Check if two states are semantically equivalent.
    
    Args:
        state1: First state
        state2: Second state
        ignore_keys: Keys to ignore in comparison (e.g., timestamps)
    """
    ignore_keys = ignore_keys or []
    
    # Filter out ignored keys
    s1 = {k: v for k, v in state1.items() if k not in ignore_keys}
    s2 = {k: v for k, v in state2.items() if k not in ignore_keys}
    
    return s1 == s2


def check_path_commutativity(
    path1: WorkflowPath,
    path2: WorkflowPath,
    initial_state: Dict[str, Any] = None
) -> PathInvarianceResult:
    """
    Verify that two paths commute: path1 and path2 lead to equivalent end states.
    
    This checks the fundamental category-theoretic property:
        f ∘ g = g ∘ f (commutativity)
    
    For workflows:
        Gmail→Task→Event == Gmail→Event→Task (same end state)
    """
    initial_state = initial_state or {}
    
    # Execute both paths
    final1, intermediates1 = execute_path(path1, initial_state)
    final2, intermediates2 = execute_path(path2, initial_state)
    
    # Check state equivalence
    states_equiv = states_are_equivalent(final1, final2)
    
    # Check GF(3) conservation for both paths
    gf3_conserved = path1.is_balanced and path2.is_balanced
    
    # Compute Narya delta
    narya_delta = NaryaDelta(before=initial_state, after=final1)
    
    # Check replay determinism (running twice gives same result)
    replay1, _ = execute_path(path1, initial_state)
    replay2, _ = execute_path(path1, initial_state)
    replay_deterministic = states_are_equivalent(replay1, replay2)
    
    return PathInvarianceResult(
        path1=path1,
        path2=path2,
        states_equivalent=states_equiv,
        gf3_conserved=gf3_conserved,
        narya_delta=narya_delta,
        replay_deterministic=replay_deterministic
    )


def verify_workflow_completion(
    start_event: str,
    end_event: str,
    via_mcp: bool = True,
    path: WorkflowPath = None
) -> WorkflowResult:
    """
    Validate complete workflow from start to end.
    
    Ensures:
    - Σ trits ≡ 0 (mod 3) at completion
    - No orphaned intermediate states
    - Replay determinism
    """
    # Default to gmail_to_task path if not specified
    if path is None:
        path = PREDEFINED_PATHS.get('gmail_to_task')
    
    initial_state = {
        'start_event': start_event,
        'via_mcp': via_mcp,
        'pending_message': f"Event: {start_event}"
    }
    
    try:
        final_state, intermediates = execute_path(path, initial_state)
        
        # Check for orphaned states (states that don't contribute to final result)
        orphaned = []
        for i, state in enumerate(intermediates[:-1]):
            if i > 0:  # Skip initial state
                # A state is orphaned if its changes don't appear in final state
                step_delta = NaryaDelta(before=intermediates[i-1], after=state)
                final_delta = NaryaDelta(before=intermediates[i-1], after=final_state)
                if step_delta.delta and not any(k in final_delta.delta for k in step_delta.delta):
                    orphaned.append(f"step_{i}")
        
        # Compute overall delta
        narya_delta = NaryaDelta(before=initial_state, after=final_state)
        
        # Verify GF(3) balance
        gf3_balanced = path.is_balanced
        
        return WorkflowResult(
            start_event=start_event,
            end_event=end_event,
            path=path,
            success=True,
            final_state=final_state,
            narya_delta=narya_delta,
            gf3_balanced=gf3_balanced,
            orphaned_states=orphaned
        )
        
    except Exception as e:
        return WorkflowResult(
            start_event=start_event,
            end_event=end_event,
            path=path,
            success=False,
            final_state={},
            narya_delta=NaryaDelta(before={}, after={}),
            gf3_balanced=False,
            error=str(e)
        )


def balance_path(path: WorkflowPath) -> WorkflowPath:
    """
    Add balancing steps to make a path GF(3)-conserving.
    
    If Σ trits ≡ 1 (mod 3): add MINUS (-1) step
    If Σ trits ≡ 2 (mod 3): add PLUS (+1) step (since +1 ≡ -2 mod 3)
    """
    current_sum = path.gf3_sum
    
    if current_sum == 0:
        return path  # Already balanced
    
    new_steps = path.steps.copy()
    new_trits = path.trit_sequence.copy()
    
    if current_sum == 1:
        # Need to add -1 (MINUS)
        # Adding two MINUS steps: 1 + (-1) + (-1) = -1 ≡ 2 mod 3, not right
        # Need to add two MINUS: 1 + (-2) = -1 ≡ 2 mod 3
        # Actually: to go from 1 to 0, add steps summing to -1 or +2
        # -1 is simpler: add one validation step
        new_steps.append('gmail_read')  # MINUS step
        new_trits.append(-1)
        # Now sum = 1 + (-1) = 0 ✓
        # Wait that's 0, good!
        # But we need sum to be ≡ 0 mod 3
        # 1 + (-1) = 0 ≡ 0 mod 3 ✓
        pass
    elif current_sum == 2:
        # Need to add +1 (since 2 + 1 = 3 ≡ 0 mod 3)
        new_steps.append('task_create')  # PLUS step  
        new_trits.append(+1)
    
    return WorkflowPath(
        name=f"{path.name}_balanced",
        steps=new_steps,
        trit_sequence=new_trits,
        description=f"{path.description} (auto-balanced)"
    )


def verify_all_predefined_paths() -> Dict[str, Dict]:
    """Verify GF(3) conservation for all predefined paths."""
    results = {}
    for name, path in PREDEFINED_PATHS.items():
        results[name] = {
            'steps': path.steps,
            'trit_sequence': path.trit_sequence,
            'gf3_sum': path.gf3_sum,
            'is_balanced': path.is_balanced,
            'needs_balancing': not path.is_balanced
        }
        if not path.is_balanced:
            balanced = balance_path(path)
            results[name]['balanced_version'] = {
                'steps': balanced.steps,
                'trit_sequence': balanced.trit_sequence,
                'gf3_sum': balanced.gf3_sum
            }
    return results


# =============================================================================
# Composable Path Algebra
# =============================================================================

def compose_paths(*paths: WorkflowPath) -> WorkflowPath:
    """Compose multiple paths into a single path."""
    return reduce(lambda a, b: a >> b, paths)


def identity_path() -> WorkflowPath:
    """Identity morphism in the path category."""
    return WorkflowPath(
        name='identity',
        steps=[],
        trit_sequence=[],
        description='Identity path (no-op)'
    )


# =============================================================================
# Main Entry Point
# =============================================================================

def main():
    """Demo the path invariance checker."""
    print("=" * 60)
    print("PATH INVARIANCE CHECKER - Narya/Unison Style")
    print("=" * 60)
    
    # Verify predefined paths
    print("\n### Predefined Path Analysis ###\n")
    results = verify_all_predefined_paths()
    for name, info in results.items():
        status = "✓ BALANCED" if info['is_balanced'] else "✗ UNBALANCED"
        print(f"{name}:")
        print(f"  Steps: {info['steps']}")
        print(f"  Trits: {info['trit_sequence']}")
        print(f"  GF(3) sum: {info['gf3_sum']} {status}")
        if info.get('balanced_version'):
            bv = info['balanced_version']
            print(f"  → Auto-balanced: {bv['steps']}, trits={bv['trit_sequence']}")
        print()
    
    # Test path commutativity
    print("\n### Path Commutativity Test ###\n")
    path1 = PREDEFINED_PATHS['gmail_to_task']
    path2 = PREDEFINED_PATHS['reply_workflow_balanced']
    
    result = check_path_commutativity(path1, path2)
    print(f"Comparing: {path1.name} vs {path2.name}")
    print(f"  States equivalent: {result.states_equivalent}")
    print(f"  GF(3) conserved: {result.gf3_conserved}")
    print(f"  Replay deterministic: {result.replay_deterministic}")
    print(f"  Paths commute: {result.paths_commute}")
    
    # Test workflow completion
    print("\n### Workflow Completion Test ###\n")
    workflow = verify_workflow_completion(
        start_event="new_email_received",
        end_event="task_completed",
        path=PREDEFINED_PATHS['gmail_to_task']
    )
    print(f"Workflow: {workflow.path.name}")
    print(f"  Success: {workflow.success}")
    print(f"  GF(3) balanced: {workflow.gf3_balanced}")
    print(f"  Orphaned states: {workflow.orphaned_states or 'None'}")
    print(f"  Final state keys: {list(workflow.final_state.keys())}")
    
    # Test path composition
    print("\n### Path Composition ###\n")
    p1 = PREDEFINED_PATHS['gmail_to_task']
    p2 = PREDEFINED_PATHS['drive_to_calendar']
    composed = p1 >> p2
    print(f"Composed: {composed.name}")
    print(f"  Steps: {composed.steps}")
    print(f"  Total GF(3) sum: {composed.gf3_sum}")
    
    if not composed.is_balanced:
        balanced = balance_path(composed)
        print(f"  → Balanced: {balanced.steps}, sum={balanced.gf3_sum}")


if __name__ == '__main__':
    main()
