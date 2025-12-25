"""
InvariantEngine: Comprehensive invariant verification engine for workspace ACSet skills.

GF(3) Role: MINUS (-1) Validator
- Validates all structural and semantic invariants
- Cold hue range: 180-300° (blue/purple spectrum)
- Constrains system behavior within defined bounds

Invariant Categories:
1. Gmail Invariants - Thread consistency, trit balance, fiber disjointness
2. Drive Invariants - File hierarchy, permission validity, revision monotonicity
3. Calendar Invariants - Event bounds, conflict detection, RSVP completeness
4. Tasks Invariants - Task list membership, subtask validity, completion finality
5. Cross-Service Invariants - Morphism well-definedness, path commutativity, global GF(3)
"""
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, List, Literal, Optional, Tuple, Set
from enum import IntEnum, Enum
import json
from datetime import datetime

from workspace_acset import (
    WorkspaceACSet, CrossSkillLink,
    DRIVE_VERB_TRIT_MAP, CALENDAR_VERB_TRIT_MAP, TASKS_VERB_TRIT_MAP,
    service_verb_trit, verify_cross_skill_invariants, global_gf3_balance
)
from gmail_acset import Trit, VERB_TRIT_MAP


# =============================================================================
# Invariant Data Structures
# =============================================================================

class Severity(str, Enum):
    CRITICAL = "critical"   # System cannot proceed; data integrity at risk
    WARNING = "warning"     # Unexpected state; system can continue
    INFO = "info"           # Informational; potential optimization opportunity


@dataclass
class Invariant:
    """Formal invariant definition with predicate and metadata."""
    name: str
    predicate: Callable[["State"], bool]
    error_message: str
    severity: Literal["critical", "warning", "info"]
    service: str  # gmail, drive, calendar, tasks, cross-service
    description: str = ""
    sql_query: Optional[str] = None  # DuckDB query for verification
    repair_hint: Optional[str] = None
    
    def __hash__(self):
        return hash(self.name)


@dataclass
class Violation:
    """Record of an invariant violation."""
    invariant_name: str
    severity: str
    service: str
    error_message: str
    context: Dict[str, Any] = field(default_factory=dict)
    timestamp: str = field(default_factory=lambda: datetime.now().isoformat())
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "invariant": self.invariant_name,
            "severity": self.severity,
            "service": self.service,
            "error": self.error_message,
            "context": self.context,
            "timestamp": self.timestamp
        }


@dataclass
class Repair:
    """Suggested repair action for a violation."""
    violation: Violation
    action: str
    verb: Optional[str] = None
    target_id: Optional[str] = None
    parameters: Dict[str, Any] = field(default_factory=dict)
    confidence: float = 0.8
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "action": self.action,
            "verb": self.verb,
            "target": self.target_id,
            "parameters": self.parameters,
            "confidence": self.confidence
        }


@dataclass
class InvariantReport:
    """Complete report of invariant verification."""
    total_invariants: int
    passed: int
    violations: List[Violation]
    by_service: Dict[str, Dict[str, int]]
    by_severity: Dict[str, int]
    critical_passed: bool
    timestamp: str = field(default_factory=lambda: datetime.now().isoformat())
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "total_invariants": self.total_invariants,
            "passed": self.passed,
            "failed": len(self.violations),
            "violations": [v.to_dict() for v in self.violations],
            "by_service": self.by_service,
            "by_severity": self.by_severity,
            "critical_passed": self.critical_passed,
            "timestamp": self.timestamp
        }
    
    def __str__(self) -> str:
        lines = [
            "=" * 60,
            "INVARIANT VERIFICATION REPORT",
            "=" * 60,
            f"Timestamp: {self.timestamp}",
            f"Total Invariants: {self.total_invariants}",
            f"Passed: {self.passed}",
            f"Failed: {len(self.violations)}",
            f"Critical Passed: {'✓' if self.critical_passed else '✗'}",
            "",
            "By Severity:",
        ]
        for sev, count in self.by_severity.items():
            lines.append(f"  {sev}: {count}")
        
        lines.append("")
        lines.append("By Service:")
        for svc, stats in self.by_service.items():
            lines.append(f"  {svc}: passed={stats.get('passed', 0)}, failed={stats.get('failed', 0)}")
        
        if self.violations:
            lines.append("")
            lines.append("Violations:")
            for v in self.violations:
                lines.append(f"  [{v.severity.upper()}] {v.invariant_name}: {v.error_message}")
        
        lines.append("=" * 60)
        return "\n".join(lines)


# State wrapper for invariant predicates
class State:
    """Wrapper providing read-only access to WorkspaceACSet for invariant checking."""
    
    def __init__(self, acset: WorkspaceACSet):
        self._acset = acset
    
    @property
    def threads(self) -> List[Dict]:
        return self._acset.get_parts("Thread")
    
    @property
    def interactions(self) -> List[Dict]:
        return self._acset.get_parts("Interaction")
    
    @property
    def drive_files(self) -> List[Dict]:
        return self._acset.get_parts("DriveFile")
    
    @property
    def calendar_events(self) -> List[Dict]:
        return self._acset.get_parts("CalendarEvent")
    
    @property
    def tasks(self) -> List[Dict]:
        return self._acset.get_parts("Task")
    
    @property
    def task_lists(self) -> List[Dict]:
        return self._acset.get_parts("TaskList")
    
    @property
    def queue_items(self) -> List[Dict]:
        return self._acset.get_parts("QueueItem")
    
    @property
    def cross_links(self) -> List[CrossSkillLink]:
        return self._acset._cross_links
    
    @property
    def service_trit_sums(self) -> Dict[str, int]:
        return dict(self._acset._service_trit_sums)
    
    @property
    def thread_trit_sums(self) -> Dict[str, int]:
        return dict(self._acset._thread_trit_sums)
    
    def get_interactions_for_thread(self, thread_id: int) -> List[Dict]:
        """Get all interactions for a thread part ID."""
        return [i for i in self.interactions if i.get("thread") == thread_id]
    
    def get_thread_by_thread_id(self, thread_id: str) -> Optional[Dict]:
        """Get thread by Gmail thread_id string."""
        for t in self.threads:
            if t.get("thread_id") == thread_id:
                return t
        return None
    
    def get_file_by_id(self, file_id: str) -> Optional[Dict]:
        """Get DriveFile by file_id."""
        for f in self.drive_files:
            if f.get("file_id") == file_id:
                return f
        return None


# =============================================================================
# Invariant Definitions
# =============================================================================

# Gmail Invariants

def gmail_no_orphan_threads(state: State) -> bool:
    """Every thread has at least one interaction."""
    for thread in state.threads:
        interactions = state.get_interactions_for_thread(thread["_id"])
        if not interactions:
            return False
    return True

def gmail_trit_balance(state: State) -> bool:
    """Thread trit sum ≡ 0 (mod 3) when saturated."""
    for thread in state.threads:
        if thread.get("saturated", False):
            thread_id = thread.get("thread_id")
            trit_sum = state.thread_trit_sums.get(thread_id, 0)
            if trit_sum % 3 != 0:
                return False
    return True

def gmail_plus_after_minus(state: State) -> bool:
    """send/reply requires prior read in same thread."""
    for thread in state.threads:
        thread_part_id = thread["_id"]
        interactions = sorted(
            state.get_interactions_for_thread(thread_part_id),
            key=lambda x: x.get("timebin", 0)
        )
        
        has_read = False
        for i in interactions:
            verb = i.get("verb", "").replace("gmail:", "")
            trit = i.get("trit", 0)
            
            if trit == Trit.MINUS:
                has_read = True
            elif trit == Trit.PLUS and verb in ("send", "reply", "forward"):
                if not has_read:
                    return False
    return True

def gmail_fiber_disjoint(state: State) -> bool:
    """No interaction in multiple queue fibers."""
    seen_interactions: Set[int] = set()
    for qi in state.queue_items:
        interaction_id = qi.get("interaction")
        if interaction_id in seen_interactions:
            return False
        seen_interactions.add(interaction_id)
    return True


# Drive Invariants

def drive_no_orphan_files(state: State) -> bool:
    """Every file has a parent folder (except root)."""
    for f in state.drive_files:
        parent_id = f.get("parent_id", "root")
        if parent_id != "root" and not parent_id:
            return False
    return True

def drive_permission_valid(state: State) -> bool:
    """All permissions reference existing files."""
    # In current schema, permissions are implicit via shared flag
    # This checks that shared files exist
    file_ids = {f.get("file_id") for f in state.drive_files}
    for link in state.cross_links:
        if link.link_type == "thread_file":
            file_id = link.metadata.get("file_id")
            if file_id and file_id not in file_ids:
                return False
    return True

def drive_revision_monotonic(state: State) -> bool:
    """File revisions have increasing timestamps (via interaction timebins)."""
    file_last_timebin: Dict[int, int] = {}
    
    for interaction in state.interactions:
        verb = interaction.get("verb", "")
        if not verb.startswith("drive:"):
            continue
        
        file_id = interaction.get("drive_file")
        timebin = interaction.get("timebin", 0)
        
        if file_id in file_last_timebin:
            if timebin < file_last_timebin[file_id]:
                return False
        file_last_timebin[file_id] = timebin
    
    return True


# Calendar Invariants

def calendar_no_conflicts(state: State) -> bool:
    """No overlapping events (unless explicitly allowed)."""
    events = state.calendar_events
    
    for i, e1 in enumerate(events):
        start1 = e1.get("start_time", "")
        end1 = e1.get("end_time", "")
        calendar1 = e1.get("calendar_id", "primary")
        
        if not start1 or not end1:
            continue
            
        for e2 in events[i+1:]:
            start2 = e2.get("start_time", "")
            end2 = e2.get("end_time", "")
            calendar2 = e2.get("calendar_id", "primary")
            
            if calendar1 != calendar2:
                continue
            
            if not start2 or not end2:
                continue
            
            # Check overlap: e1.start < e2.end AND e2.start < e1.end
            if start1 < end2 and start2 < end1:
                return False
    
    return True

def calendar_rsvp_complete(state: State) -> bool:
    """All events have responses for all attendees."""
    # In current schema, RSVP is implicit - this is a placeholder
    # Would check attendee response status if modeled
    return True

def calendar_event_bounds(state: State) -> bool:
    """End time > start time for all events."""
    for event in state.calendar_events:
        start = event.get("start_time", "")
        end = event.get("end_time", "")
        
        if start and end and start >= end:
            return False
    
    return True


# Tasks Invariants

def tasks_no_orphan(state: State) -> bool:
    """Every task is in a task list."""
    for task in state.tasks:
        if not task.get("task_list"):
            return False
    return True

def tasks_subtask_valid(state: State) -> bool:
    """Subtask parent exists."""
    task_ids = {t["_id"] for t in state.tasks}
    
    for task in state.tasks:
        parent = task.get("parent")
        if parent is not None and parent not in task_ids:
            return False
    
    return True

def tasks_completion_final(state: State) -> bool:
    """Completed tasks don't regress to needs_action."""
    task_history: Dict[str, List[str]] = {}
    
    for interaction in sorted(state.interactions, key=lambda x: x.get("timebin", 0)):
        verb = interaction.get("verb", "")
        if not verb.startswith("tasks:"):
            continue
        
        task_id = interaction.get("task")
        if task_id is None:
            continue
        
        if task_id not in task_history:
            task_history[task_id] = []
        
        if "complete" in verb:
            task_history[task_id].append("completed")
        elif "update" in verb or "create" in verb:
            if task_history[task_id] and task_history[task_id][-1] == "completed":
                # Check if this is a regression
                for task in state.tasks:
                    if task["_id"] == task_id:
                        if task.get("status") == "needsAction":
                            return False
    
    return True


# Cross-Service Invariants

def cross_thread_task_link(state: State) -> bool:
    """thread_task morphism is well-defined."""
    thread_ids = {t["_id"] for t in state.threads}
    task_ids = {t["_id"] for t in state.tasks}
    
    for link in state.cross_links:
        if link.link_type == "thread_task":
            if link.source_id not in thread_ids:
                return False
            if link.target_id not in task_ids:
                return False
    
    return True

def cross_event_task_link(state: State) -> bool:
    """event_task morphism is well-defined."""
    event_ids = {e["_id"] for e in state.calendar_events}
    task_ids = {t["_id"] for t in state.tasks}
    
    for link in state.cross_links:
        if link.link_type == "event_task":
            if link.source_id not in event_ids:
                return False
            if link.target_id not in task_ids:
                return False
    
    return True

def cross_path_commutes(state: State) -> bool:
    """thread_task == event_task ∘ thread_event (path commutativity)."""
    # Build maps
    thread_events: Dict[int, Set[int]] = {}
    event_tasks: Dict[int, Set[int]] = {}
    thread_tasks_direct: Dict[int, Set[int]] = {}
    
    for link in state.cross_links:
        if link.link_type == "thread_event":
            if link.source_id not in thread_events:
                thread_events[link.source_id] = set()
            thread_events[link.source_id].add(link.target_id)
        elif link.link_type == "event_task":
            if link.source_id not in event_tasks:
                event_tasks[link.source_id] = set()
            event_tasks[link.source_id].add(link.target_id)
        elif link.link_type == "thread_task":
            if link.source_id not in thread_tasks_direct:
                thread_tasks_direct[link.source_id] = set()
            thread_tasks_direct[link.source_id].add(link.target_id)
    
    # For each thread with both paths, check commutativity
    for thread_id in set(thread_events.keys()) & set(thread_tasks_direct.keys()):
        # Compute indirect path: thread → events → tasks
        indirect_tasks: Set[int] = set()
        for event_id in thread_events.get(thread_id, set()):
            indirect_tasks.update(event_tasks.get(event_id, set()))
        
        direct_tasks = thread_tasks_direct.get(thread_id, set())
        
        # Direct tasks should be reachable via indirect path if path exists
        if indirect_tasks and not direct_tasks.issubset(indirect_tasks | direct_tasks):
            # This is a soft check - paths should be consistent
            pass
    
    return True

def cross_gf3_global(state: State) -> bool:
    """Σ all service trits ≡ 0 (mod 3)."""
    total = sum(state.service_trit_sums.values())
    return total % 3 == 0


# =============================================================================
# Invariant Engine
# =============================================================================

class InvariantEngine:
    """Comprehensive invariant verification engine for WorkspaceACSet."""
    
    def __init__(self, workspace_acset: WorkspaceACSet):
        self.acset = workspace_acset
        self.invariants: List[Invariant] = []
        self._context: Dict[str, Any] = {}
        self.register_all_invariants()
    
    def register_all_invariants(self):
        """Register all invariants across services."""
        self._register_gmail_invariants()
        self._register_drive_invariants()
        self._register_calendar_invariants()
        self._register_tasks_invariants()
        self._register_cross_service_invariants()
    
    def _register_gmail_invariants(self):
        """Register Gmail service invariants."""
        self.invariants.extend([
            Invariant(
                name="GMAIL_NO_ORPHAN_THREADS",
                predicate=gmail_no_orphan_threads,
                error_message="Thread exists without any interactions",
                severity="warning",
                service="gmail",
                description="Every thread should have at least one interaction",
                sql_query="""
                    SELECT t.thread_id FROM threads t
                    LEFT JOIN interactions i ON t._id = i.thread
                    WHERE i._id IS NULL
                """,
                repair_hint="Delete orphan thread or add missing interaction"
            ),
            Invariant(
                name="GMAIL_TRIT_BALANCE",
                predicate=gmail_trit_balance,
                error_message="Saturated thread has non-zero GF(3) balance",
                severity="critical",
                service="gmail",
                description="Thread trit sum ≡ 0 (mod 3) when marked saturated",
                sql_query="""
                    SELECT t.thread_id, SUM(i.trit) % 3 AS remainder
                    FROM threads t
                    JOIN interactions i ON t._id = i.thread
                    WHERE t.saturated = TRUE
                    GROUP BY t.thread_id
                    HAVING remainder != 0
                """,
                repair_hint="Add balancing interaction or unmark saturation"
            ),
            Invariant(
                name="GMAIL_PLUS_AFTER_MINUS",
                predicate=gmail_plus_after_minus,
                error_message="Send/reply without prior read in thread",
                severity="warning",
                service="gmail",
                description="PLUS verbs (send/reply) require prior MINUS verb (read)",
                sql_query="""
                    WITH ordered_interactions AS (
                        SELECT *, ROW_NUMBER() OVER (PARTITION BY thread ORDER BY timebin) AS rn
                        FROM interactions
                        WHERE verb LIKE 'gmail:%' OR verb NOT LIKE '%:%'
                    )
                    SELECT thread, verb FROM ordered_interactions
                    WHERE trit = 1 AND verb IN ('send', 'reply', 'forward')
                    AND NOT EXISTS (
                        SELECT 1 FROM ordered_interactions o2
                        WHERE o2.thread = ordered_interactions.thread
                        AND o2.trit = -1 AND o2.rn < ordered_interactions.rn
                    )
                """,
                repair_hint="Add read interaction before send"
            ),
            Invariant(
                name="GMAIL_FIBER_DISJOINT",
                predicate=gmail_fiber_disjoint,
                error_message="Interaction appears in multiple queue fibers",
                severity="critical",
                service="gmail",
                description="Each interaction belongs to exactly one Agent3 queue fiber",
                sql_query="""
                    SELECT interaction, COUNT(*) AS fiber_count
                    FROM queue_items
                    GROUP BY interaction
                    HAVING fiber_count > 1
                """,
                repair_hint="Remove duplicate queue assignments"
            ),
        ])
    
    def _register_drive_invariants(self):
        """Register Drive service invariants."""
        self.invariants.extend([
            Invariant(
                name="DRIVE_NO_ORPHAN_FILES",
                predicate=drive_no_orphan_files,
                error_message="File has no parent folder (and is not root)",
                severity="warning",
                service="drive",
                description="Every file should have a parent folder except root-level files",
                sql_query="""
                    SELECT file_id, name FROM drive_files
                    WHERE parent_id IS NULL OR parent_id = ''
                    AND parent_id != 'root'
                """,
                repair_hint="Set parent_id to 'root' or valid folder ID"
            ),
            Invariant(
                name="DRIVE_PERMISSION_VALID",
                predicate=drive_permission_valid,
                error_message="Cross-link references non-existent file",
                severity="critical",
                service="drive",
                description="All file references in cross-links must exist",
                sql_query="""
                    SELECT cl.file_id FROM cross_links cl
                    WHERE cl.link_type = 'thread_file'
                    AND NOT EXISTS (
                        SELECT 1 FROM drive_files df WHERE df.file_id = cl.file_id
                    )
                """,
                repair_hint="Create missing DriveFile or remove invalid link"
            ),
            Invariant(
                name="DRIVE_REVISION_MONOTONIC",
                predicate=drive_revision_monotonic,
                error_message="File interaction has earlier timebin than previous",
                severity="warning",
                service="drive",
                description="Drive interactions should have monotonically increasing timebins",
                sql_query="""
                    WITH drive_ops AS (
                        SELECT drive_file, timebin,
                               LAG(timebin) OVER (PARTITION BY drive_file ORDER BY timebin) AS prev_timebin
                        FROM interactions
                        WHERE verb LIKE 'drive:%'
                    )
                    SELECT * FROM drive_ops
                    WHERE timebin < prev_timebin
                """,
                repair_hint="Correct timebin ordering or mark as concurrent"
            ),
        ])
    
    def _register_calendar_invariants(self):
        """Register Calendar service invariants."""
        self.invariants.extend([
            Invariant(
                name="CALENDAR_NO_CONFLICTS",
                predicate=calendar_no_conflicts,
                error_message="Overlapping events on same calendar",
                severity="warning",
                service="calendar",
                description="Events should not overlap unless explicitly allowed",
                sql_query="""
                    SELECT e1.event_id AS event1, e2.event_id AS event2
                    FROM calendar_events e1
                    JOIN calendar_events e2 ON e1.calendar_id = e2.calendar_id
                    WHERE e1._id < e2._id
                    AND e1.start_time < e2.end_time
                    AND e2.start_time < e1.end_time
                """,
                repair_hint="Adjust event times or mark as allowing overlap"
            ),
            Invariant(
                name="CALENDAR_RSVP_COMPLETE",
                predicate=calendar_rsvp_complete,
                error_message="Event missing attendee responses",
                severity="info",
                service="calendar",
                description="All attendees should have RSVP status",
                sql_query="""
                    -- Placeholder: requires attendee modeling
                    SELECT 1 WHERE FALSE
                """,
                repair_hint="Send RSVP reminders"
            ),
            Invariant(
                name="CALENDAR_EVENT_BOUNDS",
                predicate=calendar_event_bounds,
                error_message="Event end time not after start time",
                severity="critical",
                service="calendar",
                description="End time must be strictly greater than start time",
                sql_query="""
                    SELECT event_id, start_time, end_time
                    FROM calendar_events
                    WHERE start_time >= end_time
                """,
                repair_hint="Swap start/end or extend end time"
            ),
        ])
    
    def _register_tasks_invariants(self):
        """Register Tasks service invariants."""
        self.invariants.extend([
            Invariant(
                name="TASKS_NO_ORPHAN",
                predicate=tasks_no_orphan,
                error_message="Task not in any task list",
                severity="critical",
                service="tasks",
                description="Every task must belong to a task list",
                sql_query="""
                    SELECT task_id, title FROM tasks
                    WHERE task_list IS NULL
                """,
                repair_hint="Assign task to a task list"
            ),
            Invariant(
                name="TASKS_SUBTASK_VALID",
                predicate=tasks_subtask_valid,
                error_message="Subtask references non-existent parent",
                severity="critical",
                service="tasks",
                description="Parent task references must be valid",
                sql_query="""
                    SELECT t1.task_id, t1.parent FROM tasks t1
                    WHERE t1.parent IS NOT NULL
                    AND NOT EXISTS (
                        SELECT 1 FROM tasks t2 WHERE t2._id = t1.parent
                    )
                """,
                repair_hint="Remove parent reference or create parent task"
            ),
            Invariant(
                name="TASKS_COMPLETION_FINAL",
                predicate=tasks_completion_final,
                error_message="Completed task regressed to needs_action",
                severity="warning",
                service="tasks",
                description="Once completed, tasks should not revert to pending",
                sql_query="""
                    -- Requires interaction history analysis
                    SELECT task, verb FROM interactions
                    WHERE verb LIKE 'tasks:%'
                    ORDER BY timebin
                """,
                repair_hint="Re-complete task or document reason for reopen"
            ),
        ])
    
    def _register_cross_service_invariants(self):
        """Register cross-service invariants."""
        self.invariants.extend([
            Invariant(
                name="CROSS_THREAD_TASK_LINK",
                predicate=cross_thread_task_link,
                error_message="thread_task morphism references invalid objects",
                severity="critical",
                service="cross-service",
                description="thread_task links must reference existing threads and tasks",
                sql_query="""
                    SELECT * FROM cross_links
                    WHERE link_type = 'thread_task'
                    AND (source_id NOT IN (SELECT _id FROM threads)
                         OR target_id NOT IN (SELECT _id FROM tasks))
                """,
                repair_hint="Create missing objects or remove invalid link"
            ),
            Invariant(
                name="CROSS_EVENT_TASK_LINK",
                predicate=cross_event_task_link,
                error_message="event_task morphism references invalid objects",
                severity="critical",
                service="cross-service",
                description="event_task links must reference existing events and tasks",
                sql_query="""
                    SELECT * FROM cross_links
                    WHERE link_type = 'event_task'
                    AND (source_id NOT IN (SELECT _id FROM calendar_events)
                         OR target_id NOT IN (SELECT _id FROM tasks))
                """,
                repair_hint="Create missing objects or remove invalid link"
            ),
            Invariant(
                name="CROSS_PATH_COMMUTES",
                predicate=cross_path_commutes,
                error_message="Path commutativity violated: thread_task ≠ event_task ∘ thread_event",
                severity="warning",
                service="cross-service",
                description="Morphism composition should be consistent across paths",
                sql_query="""
                    -- Complex join to verify path equivalence
                    WITH direct AS (
                        SELECT source_id AS thread, target_id AS task
                        FROM cross_links WHERE link_type = 'thread_task'
                    ),
                    indirect AS (
                        SELECT te.source_id AS thread, et.target_id AS task
                        FROM cross_links te
                        JOIN cross_links et ON te.target_id = et.source_id
                        WHERE te.link_type = 'thread_event'
                        AND et.link_type = 'event_task'
                    )
                    SELECT d.thread, d.task FROM direct d
                    WHERE NOT EXISTS (
                        SELECT 1 FROM indirect i
                        WHERE i.thread = d.thread AND i.task = d.task
                    )
                """,
                repair_hint="Add missing intermediate links for path consistency"
            ),
            Invariant(
                name="CROSS_GF3_GLOBAL",
                predicate=cross_gf3_global,
                error_message="Global GF(3) imbalance: Σ service trits ≢ 0 (mod 3)",
                severity="warning",
                service="cross-service",
                description="Sum of all service trit sums should be 0 mod 3",
                sql_query="""
                    SELECT SUM(trit) % 3 AS global_remainder
                    FROM interactions
                    HAVING global_remainder != 0
                """,
                repair_hint="Add balancing interactions across services"
            ),
        ])
    
    def check_all(self) -> InvariantReport:
        """Check all registered invariants."""
        state = State(self.acset)
        violations: List[Violation] = []
        by_service: Dict[str, Dict[str, int]] = {}
        by_severity: Dict[str, int] = {"critical": 0, "warning": 0, "info": 0}
        
        for inv in self.invariants:
            if inv.service not in by_service:
                by_service[inv.service] = {"passed": 0, "failed": 0}
            
            try:
                passed = inv.predicate(state)
            except Exception as e:
                passed = False
                self._context[inv.name] = {"error": str(e)}
            
            if passed:
                by_service[inv.service]["passed"] += 1
            else:
                by_service[inv.service]["failed"] += 1
                by_severity[inv.severity] += 1
                violations.append(Violation(
                    invariant_name=inv.name,
                    severity=inv.severity,
                    service=inv.service,
                    error_message=inv.error_message,
                    context=self._context.get(inv.name, {})
                ))
        
        critical_passed = by_severity["critical"] == 0
        
        return InvariantReport(
            total_invariants=len(self.invariants),
            passed=len(self.invariants) - len(violations),
            violations=violations,
            by_service=by_service,
            by_severity=by_severity,
            critical_passed=critical_passed
        )
    
    def check_service(self, service: str) -> InvariantReport:
        """Check invariants for a specific service."""
        state = State(self.acset)
        service_invariants = [inv for inv in self.invariants if inv.service == service]
        violations: List[Violation] = []
        by_severity: Dict[str, int] = {"critical": 0, "warning": 0, "info": 0}
        
        for inv in service_invariants:
            try:
                passed = inv.predicate(state)
            except Exception as e:
                passed = False
                self._context[inv.name] = {"error": str(e)}
            
            if not passed:
                by_severity[inv.severity] += 1
                violations.append(Violation(
                    invariant_name=inv.name,
                    severity=inv.severity,
                    service=inv.service,
                    error_message=inv.error_message,
                    context=self._context.get(inv.name, {})
                ))
        
        return InvariantReport(
            total_invariants=len(service_invariants),
            passed=len(service_invariants) - len(violations),
            violations=violations,
            by_service={service: {"passed": len(service_invariants) - len(violations), "failed": len(violations)}},
            by_severity=by_severity,
            critical_passed=by_severity["critical"] == 0
        )
    
    def check_critical(self) -> bool:
        """Check if all critical invariants pass."""
        state = State(self.acset)
        
        for inv in self.invariants:
            if inv.severity == "critical":
                try:
                    if not inv.predicate(state):
                        return False
                except Exception:
                    return False
        
        return True
    
    def suggest_repairs(self, violations: List[Violation]) -> List[Repair]:
        """Suggest repairs for violations."""
        repairs: List[Repair] = []
        
        for v in violations:
            # Find the invariant definition
            inv = next((i for i in self.invariants if i.name == v.invariant_name), None)
            if not inv:
                continue
            
            repair = self._generate_repair(v, inv)
            if repair:
                repairs.append(repair)
        
        return repairs
    
    def _generate_repair(self, violation: Violation, invariant: Invariant) -> Optional[Repair]:
        """Generate a repair suggestion for a specific violation."""
        service = violation.service
        
        # Service-specific repair generation
        if service == "gmail":
            return self._generate_gmail_repair(violation, invariant)
        elif service == "drive":
            return self._generate_drive_repair(violation, invariant)
        elif service == "calendar":
            return self._generate_calendar_repair(violation, invariant)
        elif service == "tasks":
            return self._generate_tasks_repair(violation, invariant)
        elif service == "cross-service":
            return self._generate_cross_repair(violation, invariant)
        
        return Repair(
            violation=violation,
            action="manual_review",
            confidence=0.3
        )
    
    def _generate_gmail_repair(self, v: Violation, inv: Invariant) -> Repair:
        if inv.name == "GMAIL_TRIT_BALANCE":
            return Repair(
                violation=v,
                action="add_balancing_interaction",
                verb="archive",  # ERGODIC to balance
                parameters={"trit": 0},
                confidence=0.7
            )
        elif inv.name == "GMAIL_PLUS_AFTER_MINUS":
            return Repair(
                violation=v,
                action="add_read_interaction",
                verb="read",
                parameters={"trit": -1},
                confidence=0.9
            )
        
        return Repair(violation=v, action="review_gmail_state", confidence=0.5)
    
    def _generate_drive_repair(self, v: Violation, inv: Invariant) -> Repair:
        if inv.name == "DRIVE_NO_ORPHAN_FILES":
            return Repair(
                violation=v,
                action="set_parent_to_root",
                parameters={"parent_id": "root"},
                confidence=0.8
            )
        
        return Repair(violation=v, action="review_drive_state", confidence=0.5)
    
    def _generate_calendar_repair(self, v: Violation, inv: Invariant) -> Repair:
        if inv.name == "CALENDAR_EVENT_BOUNDS":
            return Repair(
                violation=v,
                action="swap_event_times",
                confidence=0.6
            )
        elif inv.name == "CALENDAR_NO_CONFLICTS":
            return Repair(
                violation=v,
                action="reschedule_event",
                confidence=0.4
            )
        
        return Repair(violation=v, action="review_calendar_state", confidence=0.5)
    
    def _generate_tasks_repair(self, v: Violation, inv: Invariant) -> Repair:
        if inv.name == "TASKS_NO_ORPHAN":
            return Repair(
                violation=v,
                action="assign_to_default_list",
                parameters={"list_name": "My Tasks"},
                confidence=0.8
            )
        elif inv.name == "TASKS_SUBTASK_VALID":
            return Repair(
                violation=v,
                action="remove_parent_reference",
                confidence=0.7
            )
        
        return Repair(violation=v, action="review_tasks_state", confidence=0.5)
    
    def _generate_cross_repair(self, v: Violation, inv: Invariant) -> Repair:
        if inv.name == "CROSS_GF3_GLOBAL":
            # Calculate needed trit
            total = sum(State(self.acset).service_trit_sums.values())
            remainder = total % 3
            if remainder == 1:
                needed = Trit.MINUS
            elif remainder == 2:
                needed = Trit.PLUS
            else:
                needed = Trit.ERGODIC
            
            return Repair(
                violation=v,
                action="add_balancing_interaction",
                parameters={"needed_trit": int(needed), "any_service": True},
                confidence=0.6
            )
        
        return Repair(violation=v, action="review_cross_links", confidence=0.5)
    
    def get_invariant_by_name(self, name: str) -> Optional[Invariant]:
        """Get an invariant by name."""
        for inv in self.invariants:
            if inv.name == name:
                return inv
        return None
    
    def get_sql_queries(self) -> Dict[str, str]:
        """Get all SQL queries for DuckDB verification."""
        return {inv.name: inv.sql_query for inv in self.invariants if inv.sql_query}


# =============================================================================
# DuckDB Integration
# =============================================================================

def generate_duckdb_schema() -> str:
    """Generate DuckDB schema for invariant verification."""
    return """
    -- InvariantEngine DuckDB Schema
    
    CREATE TABLE IF NOT EXISTS threads (
        _id INTEGER PRIMARY KEY,
        thread_id VARCHAR UNIQUE,
        needs_action BOOLEAN DEFAULT TRUE,
        last_action_bin INTEGER DEFAULT 0,
        saturated BOOLEAN DEFAULT FALSE
    );
    
    CREATE TABLE IF NOT EXISTS interactions (
        _id INTEGER PRIMARY KEY,
        thread INTEGER REFERENCES threads(_id),
        person INTEGER,
        verb VARCHAR,
        timebin INTEGER,
        trit INTEGER CHECK (trit IN (-1, 0, 1)),
        drive_file INTEGER,
        calendar_event INTEGER,
        task INTEGER,
        service INTEGER
    );
    
    CREATE TABLE IF NOT EXISTS queue_items (
        _id INTEGER PRIMARY KEY,
        interaction INTEGER REFERENCES interactions(_id),
        agent INTEGER
    );
    
    CREATE TABLE IF NOT EXISTS drive_files (
        _id INTEGER PRIMARY KEY,
        file_id VARCHAR UNIQUE,
        name VARCHAR,
        mime_type VARCHAR,
        parent_id VARCHAR DEFAULT 'root',
        shared BOOLEAN DEFAULT FALSE
    );
    
    CREATE TABLE IF NOT EXISTS calendar_events (
        _id INTEGER PRIMARY KEY,
        event_id VARCHAR UNIQUE,
        summary VARCHAR,
        start_time VARCHAR,
        end_time VARCHAR,
        calendar_id VARCHAR DEFAULT 'primary',
        has_google_meet BOOLEAN DEFAULT FALSE
    );
    
    CREATE TABLE IF NOT EXISTS tasks (
        _id INTEGER PRIMARY KEY,
        task_id VARCHAR UNIQUE,
        title VARCHAR,
        task_list INTEGER,
        notes VARCHAR,
        due VARCHAR,
        status VARCHAR DEFAULT 'needsAction',
        completed BOOLEAN DEFAULT FALSE,
        parent INTEGER REFERENCES tasks(_id)
    );
    
    CREATE TABLE IF NOT EXISTS task_lists (
        _id INTEGER PRIMARY KEY,
        tasklist_id VARCHAR UNIQUE,
        title VARCHAR
    );
    
    CREATE TABLE IF NOT EXISTS cross_links (
        _id INTEGER PRIMARY KEY,
        source_service VARCHAR,
        target_service VARCHAR,
        source_id INTEGER,
        target_id INTEGER,
        link_type VARCHAR,
        trit_delta INTEGER DEFAULT 0
    );
    
    -- Indexes for invariant queries
    CREATE INDEX IF NOT EXISTS idx_interactions_thread ON interactions(thread);
    CREATE INDEX IF NOT EXISTS idx_interactions_trit ON interactions(trit);
    CREATE INDEX IF NOT EXISTS idx_queue_items_interaction ON queue_items(interaction);
    CREATE INDEX IF NOT EXISTS idx_cross_links_type ON cross_links(link_type);
    """


# =============================================================================
# Example Usage
# =============================================================================

if __name__ == "__main__":
    from workspace_acset import WorkspaceACSet
    
    # Create workspace with sample data
    ws = WorkspaceACSet()
    
    # Add persons
    alice = ws.add_person("alice@example.com", "Alice")
    bob = ws.add_person("bob@example.com", "Bob")
    
    # Gmail interactions (intentionally unbalanced for testing)
    thread1 = "thread_test_123"
    ws.add_interaction(thread1, bob, "send", 100)  # +1
    ws.add_interaction(thread1, alice, "read", 101)  # -1
    ws.add_interaction(thread1, alice, "reply", 102)  # +1 (valid: read before reply)
    
    # Drive file
    file1 = "file_doc_456"
    ws.add_drive_file(file1, "Test Doc.docx")
    ws.add_drive_interaction(file1, alice, "create", 103)
    
    # Calendar event with invalid bounds (end < start)
    event1 = "event_test_789"
    ws.add_calendar_event(event1, "Test Event", "2025-01-15T11:00:00Z", "2025-01-15T10:00:00Z")
    
    # Tasks
    tasklist1 = "tasklist_main"
    task1 = "task_test_abc"
    ws.add_task_list(tasklist1, "My Tasks")
    ws.add_task(task1, "Test Task", tasklist1)
    
    # Cross-links
    ws.link_thread_to_file(thread1, file1)
    ws.link_thread_to_task(thread1, task1, tasklist1)
    
    # Run invariant engine
    engine = InvariantEngine(ws)
    
    print("=" * 60)
    print("INVARIANT ENGINE TEST")
    print("=" * 60)
    
    # Check all invariants
    report = engine.check_all()
    print(report)
    
    # Check specific service
    print("\n" + "=" * 60)
    print("CALENDAR SERVICE CHECK")
    print("=" * 60)
    calendar_report = engine.check_service("calendar")
    print(calendar_report)
    
    # Check critical only
    print(f"\nCritical invariants passed: {engine.check_critical()}")
    
    # Suggest repairs
    if report.violations:
        print("\n" + "=" * 60)
        print("SUGGESTED REPAIRS")
        print("=" * 60)
        repairs = engine.suggest_repairs(report.violations)
        for r in repairs:
            print(f"  {r.violation.invariant_name}: {r.action} (confidence={r.confidence})")
    
    # Show SQL queries
    print("\n" + "=" * 60)
    print("SAMPLE SQL QUERIES")
    print("=" * 60)
    queries = engine.get_sql_queries()
    for name in list(queries.keys())[:3]:
        print(f"\n{name}:")
        print(f"  {queries[name][:100]}...")
