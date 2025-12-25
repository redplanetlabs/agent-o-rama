"""
GmailACSet: Algebraic database schema for Gmail interactions with GF(3) queue fibers.

Schema:
- Interaction: thread_id, person_id, verb, timebin, trit
- Agent3: 3 queue fibers (MINUS=-1, ERGODIC=0, PLUS=+1)
- QueueItem: links Interaction to Agent3
- Thread: needs_action, last_action_bin, saturated
- Partner: person relationships (graph)
"""
from dataclasses import dataclass, field
from typing import Any, Callable, Optional
from enum import IntEnum
from collections import defaultdict


class Trit(IntEnum):
    MINUS = -1    # Read/search operations
    ERGODIC = 0   # Label/archive/snooze operations
    PLUS = 1      # Send/forward/schedule operations


VERB_TRIT_MAP = {
    # MINUS (-1): Consumption/validation
    "read": Trit.MINUS,
    "search": Trit.MINUS,
    "view": Trit.MINUS,
    "fetch": Trit.MINUS,
    "list": Trit.MINUS,
    # ERGODIC (0): Coordination/metadata
    "label": Trit.ERGODIC,
    "archive": Trit.ERGODIC,
    "snooze": Trit.ERGODIC,
    "star": Trit.ERGODIC,
    "mark_read": Trit.ERGODIC,
    "mark_unread": Trit.ERGODIC,
    "move": Trit.ERGODIC,
    # PLUS (+1): Generation/execution
    "send": Trit.PLUS,
    "forward": Trit.PLUS,
    "reply": Trit.PLUS,
    "schedule": Trit.PLUS,
    "draft": Trit.PLUS,
    "compose": Trit.PLUS,
}


def gf3_type(verb: str) -> Trit:
    """Return GF(3) trit for a Gmail verb."""
    return VERB_TRIT_MAP.get(verb.lower(), Trit.ERGODIC)


# =============================================================================
# ACSet Schema Components (py-acsets compatible)
# =============================================================================

@dataclass
class Ob:
    """Object (table) in the ACSet schema."""
    name: str


@dataclass 
class Hom:
    """Morphism (foreign key) between objects."""
    name: str
    dom: str
    cod: str


@dataclass
class AttrType:
    """Attribute type."""
    name: str
    python_type: type


@dataclass
class Attr:
    """Attribute on an object."""
    name: str
    dom: str
    attr_type: str


# Schema definition
class GmailSchema:
    """Schema for Gmail ACSet."""
    
    # Objects (tables)
    Interaction = Ob("Interaction")
    Agent3 = Ob("Agent3")
    QueueItem = Ob("QueueItem")
    Thread = Ob("Thread")
    Person = Ob("Person")
    Partner = Ob("Partner")
    
    # Morphisms (foreign keys)
    interaction_thread = Hom("thread", "Interaction", "Thread")
    interaction_person = Hom("person", "Interaction", "Person")
    queue_interaction = Hom("interaction", "QueueItem", "Interaction")
    queue_agent = Hom("agent", "QueueItem", "Agent3")
    partner_src = Hom("src", "Partner", "Person")
    partner_tgt = Hom("tgt", "Partner", "Person")
    
    # Attribute types
    StringType = AttrType("String", str)
    IntType = AttrType("Int", int)
    BoolType = AttrType("Bool", bool)
    TritType = AttrType("Trit", int)  # -1, 0, +1
    
    # Attributes
    interaction_verb = Attr("verb", "Interaction", "String")
    interaction_timebin = Attr("timebin", "Interaction", "Int")
    interaction_trit = Attr("trit", "Interaction", "Trit")
    
    agent3_fiber = Attr("fiber", "Agent3", "Trit")  # -1, 0, +1
    agent3_name = Attr("name", "Agent3", "String")
    
    thread_id = Attr("thread_id", "Thread", "String")
    thread_needs_action = Attr("needs_action", "Thread", "Bool")
    thread_last_action_bin = Attr("last_action_bin", "Thread", "Int")
    thread_saturated = Attr("saturated", "Thread", "Bool")
    
    person_email = Attr("email", "Person", "String")
    person_name = Attr("name", "Person", "String")
    
    partner_weight = Attr("weight", "Partner", "Int")


# =============================================================================
# ACSet Instance
# =============================================================================

class ACSet:
    """Generic ACSet instance conforming to a schema."""
    
    def __init__(self, schema):
        self.schema = schema
        self._tables: dict[str, list[dict]] = defaultdict(list)
        self._next_id: dict[str, int] = defaultdict(lambda: 1)
    
    def add_part(self, ob_name: str, **attrs) -> int:
        """Add a part (row) to an object (table)."""
        part_id = self._next_id[ob_name]
        self._next_id[ob_name] += 1
        row = {"_id": part_id, **attrs}
        self._tables[ob_name].append(row)
        return part_id
    
    def get_parts(self, ob_name: str) -> list[dict]:
        """Get all parts of an object."""
        return self._tables[ob_name]
    
    def get_part(self, ob_name: str, part_id: int) -> Optional[dict]:
        """Get a specific part by ID."""
        for row in self._tables[ob_name]:
            if row["_id"] == part_id:
                return row
        return None
    
    def set_attr(self, ob_name: str, part_id: int, attr: str, value: Any):
        """Set an attribute value."""
        part = self.get_part(ob_name, part_id)
        if part:
            part[attr] = value
    
    def get_attr(self, ob_name: str, part_id: int, attr: str) -> Any:
        """Get an attribute value."""
        part = self.get_part(ob_name, part_id)
        return part.get(attr) if part else None


# =============================================================================
# GmailACSet Implementation
# =============================================================================

@dataclass
class GmailACSet(ACSet):
    """Gmail ACSet with GF(3) queue fibers."""
    
    def __init__(self):
        super().__init__(GmailSchema)
        self._init_agent3_fibers()
        self._thread_trit_sums: dict[str, int] = defaultdict(int)
    
    def _init_agent3_fibers(self):
        """Initialize the 3 queue fibers."""
        self.add_part("Agent3", fiber=Trit.MINUS, name="MINUS_queue")
        self.add_part("Agent3", fiber=Trit.ERGODIC, name="ERGODIC_queue")
        self.add_part("Agent3", fiber=Trit.PLUS, name="PLUS_queue")
    
    def get_agent_by_trit(self, trit: Trit) -> int:
        """Get Agent3 ID by trit value."""
        for agent in self.get_parts("Agent3"):
            if agent["fiber"] == trit:
                return agent["_id"]
        raise ValueError(f"No agent for trit {trit}")
    
    def add_interaction(
        self,
        thread_id: str,
        person_id: int,
        verb: str,
        timebin: int
    ) -> int:
        """Add an interaction and queue it to the appropriate fiber."""
        trit = gf3_type(verb)
        
        interaction_id = self.add_part(
            "Interaction",
            thread=self._ensure_thread(thread_id),
            person=person_id,
            verb=verb,
            timebin=timebin,
            trit=int(trit)
        )
        
        agent_id = self.get_agent_by_trit(trit)
        self.add_part(
            "QueueItem",
            interaction=interaction_id,
            agent=agent_id
        )
        
        self._thread_trit_sums[thread_id] += int(trit)
        self._update_thread_saturation(thread_id)
        
        return interaction_id
    
    def _ensure_thread(self, thread_id: str) -> int:
        """Get or create a thread by Gmail thread_id."""
        for thread in self.get_parts("Thread"):
            if thread.get("thread_id") == thread_id:
                return thread["_id"]
        return self.add_part(
            "Thread",
            thread_id=thread_id,
            needs_action=True,
            last_action_bin=0,
            saturated=False
        )
    
    def _update_thread_saturation(self, thread_id: str):
        """Update thread saturation based on GF(3) conservation."""
        trit_sum = self._thread_trit_sums[thread_id]
        saturated = (trit_sum % 3) == 0
        
        for thread in self.get_parts("Thread"):
            if thread.get("thread_id") == thread_id:
                thread["saturated"] = saturated
                break
    
    def add_person(self, email: str, name: str = "") -> int:
        """Add a person."""
        for person in self.get_parts("Person"):
            if person.get("email") == email:
                return person["_id"]
        return self.add_part("Person", email=email, name=name or email)
    
    def add_partner(self, src_id: int, tgt_id: int, weight: int = 1) -> int:
        """Add a partner relationship edge."""
        return self.add_part("Partner", src=src_id, tgt=tgt_id, weight=weight)
    
    def get_queue(self, trit: Trit) -> list[dict]:
        """Get all interactions in a specific queue fiber."""
        agent_id = self.get_agent_by_trit(trit)
        queue_items = [
            q for q in self.get_parts("QueueItem")
            if q.get("agent") == agent_id
        ]
        return [
            self.get_part("Interaction", q["interaction"])
            for q in queue_items
        ]
    
    def gf3_ok(self, thread_id: str, proposed_trit: Trit) -> bool:
        """Check if adding this trit maintains GF(3) balance toward saturation."""
        current_sum = self._thread_trit_sums.get(thread_id, 0)
        new_sum = current_sum + int(proposed_trit)
        return abs(new_sum % 3) <= abs(current_sum % 3)
    
    def thread_balance(self, thread_id: str) -> int:
        """Return current GF(3) balance for a thread (0 = saturated)."""
        return self._thread_trit_sums.get(thread_id, 0) % 3


# =============================================================================
# Event Converter
# =============================================================================

@dataclass
class GmailEvent:
    """Raw Gmail event from MCP or API."""
    thread_id: str
    person_email: str
    verb: str
    timestamp: int


def to_interaction(acset: GmailACSet, event: GmailEvent) -> int:
    """Convert a Gmail event to an Interaction in the ACSet."""
    person_id = acset.add_person(event.person_email)
    timebin = event.timestamp // 3600
    
    return acset.add_interaction(
        thread_id=event.thread_id,
        person_id=person_id,
        verb=event.verb,
        timebin=timebin
    )


# =============================================================================
# Sheaf Cohomology Integration
# =============================================================================

def verify_local_consistency(acset: GmailACSet) -> bool:
    """Verify Čech cohomology: local thread patches glue globally."""
    for thread in acset.get_parts("Thread"):
        thread_id = thread.get("thread_id")
        if not thread.get("saturated"):
            balance = acset.thread_balance(thread_id)
            if balance != 0:
                return False
    return True


def h1_obstruction(acset: GmailACSet) -> list[str]:
    """Return thread_ids with non-zero GF(3) obstruction."""
    obstructions = []
    for thread in acset.get_parts("Thread"):
        thread_id = thread.get("thread_id")
        if acset.thread_balance(thread_id) != 0:
            obstructions.append(thread_id)
    return obstructions


# =============================================================================
# Example Usage
# =============================================================================

if __name__ == "__main__":
    gmail = GmailACSet()
    
    alice = gmail.add_person("person_a@example.test", "Person A")
    bob = gmail.add_person("person_b@example.test", "Person B")
    gmail.add_partner(alice, bob, weight=10)
    
    thread1 = "thread_abc123"
    
    gmail.add_interaction(thread1, bob, "send", timebin=100)
    print(f"After send: balance={gmail.thread_balance(thread1)}, saturated={gmail.get_part('Thread', 1)['saturated']}")
    
    gmail.add_interaction(thread1, alice, "read", timebin=101)
    print(f"After read: balance={gmail.thread_balance(thread1)}, saturated={gmail.get_part('Thread', 1)['saturated']}")
    
    gmail.add_interaction(thread1, alice, "archive", timebin=102)
    print(f"After archive: balance={gmail.thread_balance(thread1)}, saturated={gmail.get_part('Thread', 1)['saturated']}")
    
    print(f"\nMINUS queue: {len(gmail.get_queue(Trit.MINUS))} items")
    print(f"ERGODIC queue: {len(gmail.get_queue(Trit.ERGODIC))} items")
    print(f"PLUS queue: {len(gmail.get_queue(Trit.PLUS))} items")
    
    print(f"\ngf3_ok for read: {gmail.gf3_ok(thread1, Trit.MINUS)}")
    print(f"H¹ obstructions: {h1_obstruction(gmail)}")
