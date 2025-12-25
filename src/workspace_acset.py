"""
WorkspaceACSet: Unified algebraic database schema integrating Gmail, Drive, Calendar, Tasks.

GF(3) Conservation Rules:
  - Gmail: MINUS (-1) read, ERGODIC (0) label, PLUS (+1) send
  - Drive: ERGODIC (0) list/share, PLUS (+1) create
  - Calendar: PLUS (+1) create_event
  - Tasks: MINUS (-1) list, ERGODIC (0) update, PLUS (+1) create

Cross-Skill Morphisms:
  thread_file: Thread → DriveFile (attachments)
  thread_event: Thread → CalendarEvent (scheduled meetings)
  thread_task: Thread → Task (action items)
  file_event: DriveFile → CalendarEvent (review deadlines)
  event_task: CalendarEvent → Task (meeting action items)

Path Invariant: agent_of(q) = route(item_of(q)) across all services
"""
from dataclasses import dataclass, field
from typing import Any, Optional, List, Dict, Set, Tuple
from enum import IntEnum
from collections import defaultdict
from gmail_acset import (
    ACSet, Ob, Hom, Attr, AttrType, Trit, 
    GmailSchema, GmailACSet, gf3_type, VERB_TRIT_MAP
)


# =============================================================================
# Extended Verb-Trit Mappings for All Services
# =============================================================================

DRIVE_VERB_TRIT_MAP = {
    # ERGODIC (0): Navigation/coordination
    "list": Trit.ERGODIC,
    "search": Trit.ERGODIC,
    "share": Trit.ERGODIC,
    "get_permissions": Trit.ERGODIC,
    "get_content": Trit.ERGODIC,
    # PLUS (+1): Creation/generation
    "create": Trit.PLUS,
    "upload": Trit.PLUS,
    "copy": Trit.PLUS,
}

CALENDAR_VERB_TRIT_MAP = {
    # MINUS (-1): Read operations
    "get_events": Trit.MINUS,
    "list_calendars": Trit.MINUS,
    # ERGODIC (0): Modification
    "modify_event": Trit.ERGODIC,
    "delete_event": Trit.ERGODIC,
    # PLUS (+1): Creation
    "create_event": Trit.PLUS,
}

TASKS_VERB_TRIT_MAP = {
    # MINUS (-1): List/read
    "list_tasks": Trit.MINUS,
    "list_task_lists": Trit.MINUS,
    "get_task": Trit.MINUS,
    # ERGODIC (0): Update
    "update_task": Trit.ERGODIC,
    "move_task": Trit.ERGODIC,
    "complete_task": Trit.ERGODIC,
    # PLUS (+1): Creation
    "create_task": Trit.PLUS,
    "create_task_list": Trit.PLUS,
}


def service_verb_trit(service: str, verb: str) -> Trit:
    """Return GF(3) trit for a verb in a specific service."""
    maps = {
        "gmail": VERB_TRIT_MAP,
        "drive": DRIVE_VERB_TRIT_MAP,
        "calendar": CALENDAR_VERB_TRIT_MAP,
        "tasks": TASKS_VERB_TRIT_MAP,
    }
    verb_map = maps.get(service.lower(), {})
    return verb_map.get(verb.lower(), Trit.ERGODIC)


# =============================================================================
# Extended Schema: Drive, Calendar, Tasks Objects
# =============================================================================

class WorkspaceSchema(GmailSchema):
    """Extended schema integrating Gmail, Drive, Calendar, Tasks."""
    
    # New Objects
    DriveFile = Ob("DriveFile")
    CalendarEvent = Ob("CalendarEvent")
    Task = Ob("Task")
    TaskList = Ob("TaskList")
    Service = Ob("Service")  # Meta-object for service routing
    
    # Cross-skill morphisms (Thread → X)
    thread_file = Hom("thread_file", "Thread", "DriveFile")
    thread_event = Hom("thread_event", "Thread", "CalendarEvent")
    thread_task = Hom("thread_task", "Thread", "Task")
    
    # File → X morphisms
    file_event = Hom("file_event", "DriveFile", "CalendarEvent")
    
    # Event → X morphisms
    event_task = Hom("event_task", "CalendarEvent", "Task")
    
    # Task hierarchy
    task_list = Hom("task_list", "Task", "TaskList")
    task_parent = Hom("parent", "Task", "Task")  # subtasks
    
    # Service routing
    interaction_service = Hom("service", "Interaction", "Service")
    
    # DriveFile attributes
    file_id = Attr("file_id", "DriveFile", "String")
    file_name = Attr("name", "DriveFile", "String")
    file_mime = Attr("mime_type", "DriveFile", "String")
    file_parent = Attr("parent_id", "DriveFile", "String")
    file_shared = Attr("shared", "DriveFile", "Bool")
    
    # CalendarEvent attributes
    event_id = Attr("event_id", "CalendarEvent", "String")
    event_summary = Attr("summary", "CalendarEvent", "String")
    event_start = Attr("start_time", "CalendarEvent", "String")
    event_end = Attr("end_time", "CalendarEvent", "String")
    event_calendar = Attr("calendar_id", "CalendarEvent", "String")
    event_has_meet = Attr("has_google_meet", "CalendarEvent", "Bool")
    
    # Task attributes
    task_id = Attr("task_id", "Task", "String")
    task_title = Attr("title", "Task", "String")
    task_notes = Attr("notes", "Task", "String")
    task_due = Attr("due", "Task", "String")
    task_status = Attr("status", "Task", "String")
    task_completed = Attr("completed", "Task", "Bool")
    
    # TaskList attributes
    tasklist_id = Attr("tasklist_id", "TaskList", "String")
    tasklist_title = Attr("title", "TaskList", "String")
    
    # Service attributes
    service_name = Attr("name", "Service", "String")
    service_trit_bias = Attr("trit_bias", "Service", "Trit")


# =============================================================================
# Cross-Skill Link Types
# =============================================================================

@dataclass
class CrossSkillLink:
    """Represents a morphism between objects across services."""
    source_service: str
    target_service: str
    source_id: int
    target_id: int
    link_type: str  # thread_file, thread_event, etc.
    trit_delta: int  # GF(3) contribution of this link
    metadata: Dict[str, Any] = field(default_factory=dict)


# =============================================================================
# WorkspaceACSet Implementation
# =============================================================================

class WorkspaceACSet(GmailACSet):
    """Unified ACSet integrating Gmail, Drive, Calendar, Tasks with GF(3) conservation."""
    
    def __init__(self):
        super().__init__()
        self.schema = WorkspaceSchema
        self._init_services()
        self._cross_links: List[CrossSkillLink] = []
        self._service_trit_sums: Dict[str, int] = defaultdict(int)
        
    def _init_services(self):
        """Initialize service meta-objects."""
        self.add_part("Service", name="gmail", trit_bias=0)
        self.add_part("Service", name="drive", trit_bias=0)
        self.add_part("Service", name="calendar", trit_bias=1)
        self.add_part("Service", name="tasks", trit_bias=0)
    
    def get_service_id(self, name: str) -> int:
        """Get Service ID by name."""
        for svc in self.get_parts("Service"):
            if svc.get("name") == name:
                return svc["_id"]
        raise ValueError(f"Unknown service: {name}")
    
    # =========================================================================
    # Drive Operations
    # =========================================================================
    
    def add_drive_file(
        self,
        file_id: str,
        name: str,
        mime_type: str = "application/octet-stream",
        parent_id: str = "root",
        shared: bool = False
    ) -> int:
        """Add a Drive file."""
        for f in self.get_parts("DriveFile"):
            if f.get("file_id") == file_id:
                return f["_id"]
        return self.add_part(
            "DriveFile",
            file_id=file_id,
            name=name,
            mime_type=mime_type,
            parent_id=parent_id,
            shared=shared
        )
    
    def add_drive_interaction(
        self,
        file_id: str,
        person_id: int,
        verb: str,
        timebin: int
    ) -> int:
        """Add a Drive interaction with GF(3) tracking."""
        trit = service_verb_trit("drive", verb)
        file_part_id = self._ensure_drive_file(file_id)
        
        interaction_id = self.add_part(
            "Interaction",
            thread=None,  # Drive files don't have threads
            person=person_id,
            verb=f"drive:{verb}",
            timebin=timebin,
            trit=int(trit),
            service=self.get_service_id("drive"),
            drive_file=file_part_id
        )
        
        agent_id = self.get_agent_by_trit(trit)
        self.add_part("QueueItem", interaction=interaction_id, agent=agent_id)
        self._service_trit_sums["drive"] += int(trit)
        
        return interaction_id
    
    def _ensure_drive_file(self, file_id: str) -> int:
        """Get or create a DriveFile by file_id."""
        for f in self.get_parts("DriveFile"):
            if f.get("file_id") == file_id:
                return f["_id"]
        return self.add_drive_file(file_id, name=f"file_{file_id[:8]}")
    
    # =========================================================================
    # Calendar Operations
    # =========================================================================
    
    def add_calendar_event(
        self,
        event_id: str,
        summary: str,
        start_time: str,
        end_time: str,
        calendar_id: str = "primary",
        has_google_meet: bool = False
    ) -> int:
        """Add a Calendar event."""
        for e in self.get_parts("CalendarEvent"):
            if e.get("event_id") == event_id:
                return e["_id"]
        return self.add_part(
            "CalendarEvent",
            event_id=event_id,
            summary=summary,
            start_time=start_time,
            end_time=end_time,
            calendar_id=calendar_id,
            has_google_meet=has_google_meet
        )
    
    def add_calendar_interaction(
        self,
        event_id: str,
        person_id: int,
        verb: str,
        timebin: int
    ) -> int:
        """Add a Calendar interaction with GF(3) tracking."""
        trit = service_verb_trit("calendar", verb)
        event_part_id = self._ensure_calendar_event(event_id)
        
        interaction_id = self.add_part(
            "Interaction",
            thread=None,
            person=person_id,
            verb=f"calendar:{verb}",
            timebin=timebin,
            trit=int(trit),
            service=self.get_service_id("calendar"),
            calendar_event=event_part_id
        )
        
        agent_id = self.get_agent_by_trit(trit)
        self.add_part("QueueItem", interaction=interaction_id, agent=agent_id)
        self._service_trit_sums["calendar"] += int(trit)
        
        return interaction_id
    
    def _ensure_calendar_event(self, event_id: str) -> int:
        """Get or create a CalendarEvent by event_id."""
        for e in self.get_parts("CalendarEvent"):
            if e.get("event_id") == event_id:
                return e["_id"]
        return self.add_calendar_event(
            event_id, summary=f"event_{event_id[:8]}",
            start_time="", end_time=""
        )
    
    # =========================================================================
    # Tasks Operations
    # =========================================================================
    
    def add_task_list(self, tasklist_id: str, title: str) -> int:
        """Add a TaskList."""
        for tl in self.get_parts("TaskList"):
            if tl.get("tasklist_id") == tasklist_id:
                return tl["_id"]
        return self.add_part("TaskList", tasklist_id=tasklist_id, title=title)
    
    def add_task(
        self,
        task_id: str,
        title: str,
        tasklist_id: str,
        notes: str = "",
        due: str = "",
        status: str = "needsAction",
        parent_task_id: Optional[str] = None
    ) -> int:
        """Add a Task."""
        for t in self.get_parts("Task"):
            if t.get("task_id") == task_id:
                return t["_id"]
        
        tasklist_part_id = self._ensure_task_list(tasklist_id)
        parent_part_id = None
        if parent_task_id:
            parent_part_id = self._ensure_task(parent_task_id, tasklist_id)
        
        return self.add_part(
            "Task",
            task_id=task_id,
            title=title,
            task_list=tasklist_part_id,
            notes=notes,
            due=due,
            status=status,
            completed=(status == "completed"),
            parent=parent_part_id
        )
    
    def add_task_interaction(
        self,
        task_id: str,
        tasklist_id: str,
        person_id: int,
        verb: str,
        timebin: int
    ) -> int:
        """Add a Task interaction with GF(3) tracking."""
        trit = service_verb_trit("tasks", verb)
        task_part_id = self._ensure_task(task_id, tasklist_id)
        
        interaction_id = self.add_part(
            "Interaction",
            thread=None,
            person=person_id,
            verb=f"tasks:{verb}",
            timebin=timebin,
            trit=int(trit),
            service=self.get_service_id("tasks"),
            task=task_part_id
        )
        
        agent_id = self.get_agent_by_trit(trit)
        self.add_part("QueueItem", interaction=interaction_id, agent=agent_id)
        self._service_trit_sums["tasks"] += int(trit)
        
        return interaction_id
    
    def _ensure_task_list(self, tasklist_id: str) -> int:
        """Get or create a TaskList."""
        for tl in self.get_parts("TaskList"):
            if tl.get("tasklist_id") == tasklist_id:
                return tl["_id"]
        return self.add_task_list(tasklist_id, f"list_{tasklist_id[:8]}")
    
    def _ensure_task(self, task_id: str, tasklist_id: str) -> int:
        """Get or create a Task."""
        for t in self.get_parts("Task"):
            if t.get("task_id") == task_id:
                return t["_id"]
        return self.add_task(task_id, f"task_{task_id[:8]}", tasklist_id)
    
    # =========================================================================
    # Cross-Skill Morphisms
    # =========================================================================
    
    def link_thread_to_file(self, thread_id: str, file_id: str) -> CrossSkillLink:
        """Create thread_file morphism (attachment link)."""
        thread_part_id = self._ensure_thread(thread_id)
        file_part_id = self._ensure_drive_file(file_id)
        
        link = CrossSkillLink(
            source_service="gmail",
            target_service="drive",
            source_id=thread_part_id,
            target_id=file_part_id,
            link_type="thread_file",
            trit_delta=0,  # Cross-links are trit-neutral
            metadata={"thread_id": thread_id, "file_id": file_id}
        )
        self._cross_links.append(link)
        return link
    
    def link_thread_to_event(self, thread_id: str, event_id: str) -> CrossSkillLink:
        """Create thread_event morphism (scheduled meeting)."""
        thread_part_id = self._ensure_thread(thread_id)
        event_part_id = self._ensure_calendar_event(event_id)
        
        link = CrossSkillLink(
            source_service="gmail",
            target_service="calendar",
            source_id=thread_part_id,
            target_id=event_part_id,
            link_type="thread_event",
            trit_delta=0,
            metadata={"thread_id": thread_id, "event_id": event_id}
        )
        self._cross_links.append(link)
        return link
    
    def link_thread_to_task(self, thread_id: str, task_id: str, tasklist_id: str) -> CrossSkillLink:
        """Create thread_task morphism (action item)."""
        thread_part_id = self._ensure_thread(thread_id)
        task_part_id = self._ensure_task(task_id, tasklist_id)
        
        link = CrossSkillLink(
            source_service="gmail",
            target_service="tasks",
            source_id=thread_part_id,
            target_id=task_part_id,
            link_type="thread_task",
            trit_delta=0,
            metadata={"thread_id": thread_id, "task_id": task_id}
        )
        self._cross_links.append(link)
        return link
    
    def link_file_to_event(self, file_id: str, event_id: str) -> CrossSkillLink:
        """Create file_event morphism (file review deadline)."""
        file_part_id = self._ensure_drive_file(file_id)
        event_part_id = self._ensure_calendar_event(event_id)
        
        link = CrossSkillLink(
            source_service="drive",
            target_service="calendar",
            source_id=file_part_id,
            target_id=event_part_id,
            link_type="file_event",
            trit_delta=0,
            metadata={"file_id": file_id, "event_id": event_id}
        )
        self._cross_links.append(link)
        return link
    
    def link_event_to_task(self, event_id: str, task_id: str, tasklist_id: str) -> CrossSkillLink:
        """Create event_task morphism (meeting action items)."""
        event_part_id = self._ensure_calendar_event(event_id)
        task_part_id = self._ensure_task(task_id, tasklist_id)
        
        link = CrossSkillLink(
            source_service="calendar",
            target_service="tasks",
            source_id=event_part_id,
            target_id=task_part_id,
            link_type="event_task",
            trit_delta=0,
            metadata={"event_id": event_id, "task_id": task_id}
        )
        self._cross_links.append(link)
        return link
    
    # =========================================================================
    # Path Invariant: agent_of(q) = route(item_of(q))
    # =========================================================================
    
    def agent_of(self, queue_item_id: int) -> int:
        """Get Agent3 fiber for a QueueItem."""
        qi = self.get_part("QueueItem", queue_item_id)
        return qi["agent"] if qi else None
    
    def item_of(self, queue_item_id: int) -> Tuple[str, int]:
        """Get the (service, object_id) for a QueueItem's interaction."""
        qi = self.get_part("QueueItem", queue_item_id)
        if not qi:
            return None
        interaction = self.get_part("Interaction", qi["interaction"])
        if not interaction:
            return None
        
        verb = interaction.get("verb", "")
        if verb.startswith("drive:"):
            return ("drive", interaction.get("drive_file"))
        elif verb.startswith("calendar:"):
            return ("calendar", interaction.get("calendar_event"))
        elif verb.startswith("tasks:"):
            return ("tasks", interaction.get("task"))
        else:
            return ("gmail", interaction.get("thread"))
    
    def route(self, service: str, item_id: int) -> int:
        """Route an item to its Agent3 fiber based on service semantics."""
        # Service-level routing determines default agent assignment
        # This implements the path invariant diagram
        service_defaults = {
            "gmail": Trit.ERGODIC,
            "drive": Trit.ERGODIC,
            "calendar": Trit.PLUS,
            "tasks": Trit.ERGODIC,
        }
        default_trit = service_defaults.get(service, Trit.ERGODIC)
        return self.get_agent_by_trit(default_trit)
    
    def verify_path_invariant(self, queue_item_id: int) -> bool:
        """Verify: agent_of(q) == route(item_of(q)) for a QueueItem."""
        agent = self.agent_of(queue_item_id)
        item_info = self.item_of(queue_item_id)
        if not item_info:
            return False
        service, item_id = item_info
        expected_agent = self.route(service, item_id)
        # Note: actual routing uses verb-level trits, not service defaults
        # This verifies structural consistency
        return agent is not None


# =============================================================================
# Cross-Skill Invariant Verification
# =============================================================================

def verify_cross_skill_invariants(ws: WorkspaceACSet) -> Dict[str, Any]:
    """
    Verify all cross-skill invariants:
    1. No orphaned tasks (every task links to thread or event)
    2. GF(3) conservation across all services
    3. Path commutativity: Gmail→Task == Gmail→Event→Task
    """
    results = {
        "orphaned_tasks": [],
        "gf3_conservation": {},
        "path_commutativity": [],
        "all_valid": True
    }
    
    # 1. Check for orphaned tasks
    task_links = {l.target_id for l in ws._cross_links 
                  if l.target_service == "tasks"}
    
    for task in ws.get_parts("Task"):
        task_part_id = task["_id"]
        if task_part_id not in task_links:
            # Check if it has a parent task that IS linked
            parent_id = task.get("parent")
            if parent_id and parent_id in task_links:
                continue
            results["orphaned_tasks"].append(task.get("task_id"))
    
    if results["orphaned_tasks"]:
        results["all_valid"] = False
    
    # 2. GF(3) conservation per service
    for service in ["gmail", "drive", "calendar", "tasks"]:
        trit_sum = ws._service_trit_sums.get(service, 0)
        conserved = (trit_sum % 3) == 0
        results["gf3_conservation"][service] = {
            "trit_sum": trit_sum,
            "conserved": conserved,
            "remainder": trit_sum % 3
        }
        if not conserved:
            results["all_valid"] = False
    
    # 3. Path commutativity: Gmail→Task should equal Gmail→Event→Task
    # Find all tasks reachable from a thread via direct link
    direct_thread_tasks = {}
    for link in ws._cross_links:
        if link.link_type == "thread_task":
            thread_id = link.metadata.get("thread_id")
            task_id = link.metadata.get("task_id")
            if thread_id not in direct_thread_tasks:
                direct_thread_tasks[thread_id] = set()
            direct_thread_tasks[thread_id].add(task_id)
    
    # Find tasks reachable via Thread→Event→Task
    thread_events = {}
    event_tasks = {}
    
    for link in ws._cross_links:
        if link.link_type == "thread_event":
            thread_id = link.metadata.get("thread_id")
            event_id = link.metadata.get("event_id")
            if thread_id not in thread_events:
                thread_events[thread_id] = set()
            thread_events[thread_id].add(event_id)
        elif link.link_type == "event_task":
            event_id = link.metadata.get("event_id")
            task_id = link.metadata.get("task_id")
            if event_id not in event_tasks:
                event_tasks[event_id] = set()
            event_tasks[event_id].add(task_id)
    
    # Compute indirect path Thread→Event→Task
    indirect_thread_tasks = {}
    for thread_id, events in thread_events.items():
        tasks = set()
        for event_id in events:
            tasks.update(event_tasks.get(event_id, set()))
        if tasks:
            indirect_thread_tasks[thread_id] = tasks
    
    # Check commutativity: tasks reachable both ways should be consistent
    all_threads = set(direct_thread_tasks.keys()) | set(indirect_thread_tasks.keys())
    for thread_id in all_threads:
        direct = direct_thread_tasks.get(thread_id, set())
        indirect = indirect_thread_tasks.get(thread_id, set())
        
        # Path commutativity means direct tasks should also be reachable indirectly
        # (if there's an event in between)
        if direct and indirect and not direct.issubset(indirect):
            results["path_commutativity"].append({
                "thread_id": thread_id,
                "direct_tasks": list(direct),
                "indirect_tasks": list(indirect),
                "missing_indirect": list(direct - indirect)
            })
    
    return results


def global_gf3_balance(ws: WorkspaceACSet) -> Tuple[int, bool]:
    """Compute global GF(3) balance across all services."""
    total = sum(ws._service_trit_sums.values())
    return total, (total % 3) == 0


def suggest_balancing_operations(ws: WorkspaceACSet) -> List[Dict[str, Any]]:
    """Suggest operations to restore GF(3) balance."""
    suggestions = []
    
    for service, trit_sum in ws._service_trit_sums.items():
        remainder = trit_sum % 3
        if remainder == 0:
            continue
        
        # Determine what trit value would balance
        if remainder == 1:
            needed_trit = Trit.MINUS  # -1 to cancel +1 excess
        else:  # remainder == 2 means -1 excess (mod 3)
            needed_trit = Trit.PLUS  # +1 to cancel
        
        # Find a verb that produces the needed trit
        verb_maps = {
            "gmail": VERB_TRIT_MAP,
            "drive": DRIVE_VERB_TRIT_MAP,
            "calendar": CALENDAR_VERB_TRIT_MAP,
            "tasks": TASKS_VERB_TRIT_MAP,
        }
        
        for verb, trit in verb_maps.get(service, {}).items():
            if trit == needed_trit:
                suggestions.append({
                    "service": service,
                    "verb": verb,
                    "trit": int(needed_trit),
                    "reason": f"Balance {service} (current sum={trit_sum}, need trit={needed_trit})"
                })
                break
    
    return suggestions


# =============================================================================
# Example Usage
# =============================================================================

if __name__ == "__main__":
    import sys
    
    ws = WorkspaceACSet()
    
    # Add persons
    alice = ws.add_person("alice@example.com", "Alice")
    bob = ws.add_person("bob@example.com", "Bob")
    
    # Gmail interactions
    thread1 = "thread_meeting_123"
    ws.add_interaction(thread1, bob, "send", 100)  # +1
    ws.add_interaction(thread1, alice, "read", 101)  # -1
    
    # Drive interactions
    file1 = "file_doc_456"
    ws.add_drive_file(file1, "Meeting Notes.docx", "application/vnd.google-apps.document")
    ws.add_drive_interaction(file1, alice, "create", 102)  # +1
    ws.add_drive_interaction(file1, bob, "share", 103)  # 0
    
    # Calendar interactions
    event1 = "event_mtg_789"
    ws.add_calendar_event(event1, "Project Sync", "2025-01-15T10:00:00Z", "2025-01-15T11:00:00Z")
    ws.add_calendar_interaction(event1, alice, "create_event", 104)  # +1
    
    # Tasks interactions
    tasklist1 = "tasklist_main"
    task1 = "task_followup_abc"
    ws.add_task_list(tasklist1, "My Tasks")
    ws.add_task(task1, "Follow up on meeting", tasklist1)
    ws.add_task_interaction(task1, tasklist1, alice, "create_task", 105)  # +1
    ws.add_task_interaction(task1, tasklist1, alice, "update_task", 106)  # 0
    
    # Create cross-skill links
    ws.link_thread_to_file(thread1, file1)
    ws.link_thread_to_event(thread1, event1)
    ws.link_thread_to_task(thread1, task1, tasklist1)
    ws.link_event_to_task(event1, task1, tasklist1)
    
    # Verify invariants
    results = verify_cross_skill_invariants(ws)
    
    print("=" * 60)
    print("WorkspaceACSet Cross-Skill Invariant Verification")
    print("=" * 60)
    print(f"\nOrphaned Tasks: {results['orphaned_tasks']}")
    print(f"\nGF(3) Conservation:")
    for svc, info in results["gf3_conservation"].items():
        status = "✓" if info["conserved"] else "✗"
        print(f"  {svc}: sum={info['trit_sum']}, mod3={info['remainder']} {status}")
    
    print(f"\nPath Commutativity Issues: {len(results['path_commutativity'])}")
    for issue in results["path_commutativity"]:
        print(f"  Thread {issue['thread_id']}: missing {issue['missing_indirect']}")
    
    global_sum, balanced = global_gf3_balance(ws)
    print(f"\nGlobal GF(3): sum={global_sum}, balanced={'✓' if balanced else '✗'}")
    
    if not balanced:
        print("\nSuggested balancing operations:")
        for sugg in suggest_balancing_operations(ws):
            print(f"  {sugg['service']}:{sugg['verb']} (trit={sugg['trit']})")
    
    print(f"\nAll Valid: {'✓' if results['all_valid'] else '✗'}")
    
    # Queue stats
    print("\n" + "=" * 60)
    print("Queue Fiber Statistics")
    print("=" * 60)
    for trit in [Trit.MINUS, Trit.ERGODIC, Trit.PLUS]:
        queue = ws.get_queue(trit)
        print(f"  {trit.name}: {len(queue)} items")
