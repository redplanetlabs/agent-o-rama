"""
Cross-Skill Workflow Validator (MINUS -1: Validator Role)

End-to-end validation of workflows spanning Gmail, Drive, Calendar, Tasks.
Verifies GF(3) conservation, path equivalence, and MCP vs API consistency.

Key Invariants:
  1. GF(3) Conservation: Σ trits ≡ 0 (mod 3) across all operations
  2. Path Commutativity: Gmail→Task == Gmail→Event→Task (same end state)
  3. MCP↔API Equivalence: state_after_mcp(op) == state_after_api(op)
  4. Narya Bridge Types: before/after/delta tracking for all transitions
"""

from __future__ import annotations

import hashlib
import subprocess
import time
from dataclasses import dataclass, field
from enum import IntEnum
from typing import Any, Callable, Dict, List, Optional, Tuple

from workspace_acset import (
    WorkspaceACSet,
    verify_cross_skill_invariants,
    global_gf3_balance,
    suggest_balancing_operations,
    CrossSkillLink,
)
from mcp_api_equivalence import (
    MCPAPIBridge,
    EquivalenceResult,
    EquivalenceTest,
    StateSnapshot,
    state_diff,
    test_equivalence,
)
from path_invariance import (
    WorkflowPath,
    WorkflowStep,
    WorkflowResult,
    PathInvarianceResult,
    NaryaDelta,
    PREDEFINED_PATHS,
    WORKFLOW_STEPS,
    execute_path,
    check_path_commutativity,
    verify_workflow_completion,
    balance_path,
    Trit,
)


@dataclass
class ValidationResult:
    """Result of a workflow validation check."""
    
    workflow_name: str
    success: bool
    gf3_balanced: bool
    path_valid: bool
    mcp_api_equivalent: bool
    narya_delta: Optional[NaryaDelta] = None
    errors: List[str] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)
    metrics: Dict[str, Any] = field(default_factory=dict)
    
    @property
    def is_fully_valid(self) -> bool:
        return self.success and self.gf3_balanced and self.path_valid and self.mcp_api_equivalent


@dataclass
class InvariantReport:
    """Report of all invariant checks."""
    
    timestamp: float
    acset_invariants: Dict[str, Any]
    gf3_global: Tuple[int, bool]
    path_invariants: Dict[str, PathInvarianceResult]
    mcp_api_equivalence: Dict[str, EquivalenceResult]
    narya_log: List[NaryaDelta]
    all_valid: bool
    summary: str = ""


class WorkflowValidator:
    """
    Cross-skill workflow validator with GF(3) conservation.
    
    Trit Assignment: MINUS (-1) - Validator/Constrainer role
    """
    
    def __init__(self, acset: WorkspaceACSet, bridge: MCPAPIBridge):
        self.acset = acset
        self.bridge = bridge
        self.narya_log: List[NaryaDelta] = []
        self._validation_count = 0
        self._trit_sum = 0  # Running GF(3) sum for this validator
        
    def _record_delta(self, before: Dict, after: Dict) -> NaryaDelta:
        """Record a Narya delta and log it."""
        delta = NaryaDelta(before=before, after=after)
        self.narya_log.append(delta)
        return delta
    
    def _announce(self, message: str, voice: str = "Ava (Premium)") -> None:
        """Vocal announcement (validator uses Premium voice)."""
        try:
            subprocess.run(
                ["say", "-v", voice, message],
                capture_output=True,
                timeout=5,
            )
        except Exception:
            pass
    
    def validate_gmail_to_task(
        self,
        thread_id: str,
        task_title: str,
        tasklist_id: str = "default_list"
    ) -> ValidationResult:
        """
        Gmail message → Task creation with invariant checks.
        
        Path: gmail_read (-1) → task_create (+1) → task_complete (0)
        Expected GF(3): -1 + 1 + 0 = 0 ✓
        """
        workflow_name = "gmail_to_task"
        errors = []
        warnings = []
        
        before_state = {
            "thread_id": thread_id,
            "task_title": task_title,
            "tasklist_id": tasklist_id,
            "pending_message": f"Thread: {thread_id}",
        }
        
        path = PREDEFINED_PATHS.get("gmail_to_task")
        if not path:
            errors.append("Predefined path 'gmail_to_task' not found")
            return ValidationResult(
                workflow_name=workflow_name,
                success=False,
                gf3_balanced=False,
                path_valid=False,
                mcp_api_equivalent=False,
                errors=errors,
            )
        
        gf3_balanced = path.is_balanced
        if not gf3_balanced:
            warnings.append(f"Path GF(3) sum={path.gf3_sum}, not balanced")
        
        try:
            final_state, intermediates = execute_path(path, before_state)
            path_valid = True
        except Exception as e:
            errors.append(f"Path execution failed: {e}")
            path_valid = False
            final_state = {}
        
        mcp_result = self.bridge.execute_mcp("search_gmail_messages", {"query": thread_id})
        api_result = self.bridge.execute_api("search_gmail_messages", {"query": thread_id})
        mcp_api_equivalent = mcp_result.get("trit") == api_result.get("trit")
        
        self.acset.link_thread_to_task(thread_id, f"task_{task_title[:8]}", tasklist_id)
        
        self._trit_sum += int(Trit.MINUS)  # Validator contribution
        self._validation_count += 1
        
        narya_delta = self._record_delta(before_state, final_state)
        
        return ValidationResult(
            workflow_name=workflow_name,
            success=path_valid and not errors,
            gf3_balanced=gf3_balanced,
            path_valid=path_valid,
            mcp_api_equivalent=mcp_api_equivalent,
            narya_delta=narya_delta,
            errors=errors,
            warnings=warnings,
            metrics={
                "path_steps": len(path.steps),
                "trit_sum": path.gf3_sum,
                "intermediate_states": len(intermediates) if path_valid else 0,
            },
        )
    
    def validate_drive_to_calendar(
        self,
        file_id: str,
        event_title: str,
        event_time: str = "2025-01-20T10:00:00Z"
    ) -> ValidationResult:
        """
        Drive file → Calendar event with GF(3) conservation.
        
        Path: drive_upload (+1) → calendar_create (+1) → event_link (0)
        Expected GF(3): +1 + 1 + 0 = 2 ≡ 2 (mod 3) - needs balancing!
        """
        workflow_name = "drive_to_calendar"
        errors = []
        warnings = []
        
        before_state = {
            "file_id": file_id,
            "event_title": event_title,
            "event_time": event_time,
        }
        
        path = PREDEFINED_PATHS.get("drive_to_calendar")
        if not path:
            path = WorkflowPath(
                name="drive_to_calendar",
                steps=["drive_upload", "calendar_create", "event_link"],
                trit_sequence=[+1, +1, 0],
                description="Drive file → Calendar event",
            )
        
        gf3_balanced = path.is_balanced
        if not gf3_balanced:
            warnings.append(f"Path unbalanced (sum={path.gf3_sum}), auto-balancing")
            path = balance_path(path)
            gf3_balanced = path.is_balanced
        
        try:
            final_state, intermediates = execute_path(path, before_state)
            path_valid = True
        except Exception as e:
            errors.append(f"Path execution failed: {e}")
            path_valid = False
            final_state = {}
        
        mcp_result = self.bridge.execute_mcp("create_drive_file", {"file_name": file_id})
        api_result = self.bridge.execute_api("create_drive_file", {"file_name": file_id})
        
        mcp_data = mcp_result.get("data", {})
        api_data = api_result.get("data", {})
        diff = state_diff(mcp_data, api_data)
        mcp_api_equivalent = diff.get("equivalent", False)
        
        self.acset.link_file_to_event(file_id, f"event_{event_title[:8]}")
        
        self._trit_sum += int(Trit.MINUS)
        self._validation_count += 1
        
        narya_delta = self._record_delta(before_state, final_state)
        
        return ValidationResult(
            workflow_name=workflow_name,
            success=path_valid and gf3_balanced,
            gf3_balanced=gf3_balanced,
            path_valid=path_valid,
            mcp_api_equivalent=mcp_api_equivalent,
            narya_delta=narya_delta,
            errors=errors,
            warnings=warnings,
            metrics={
                "path_steps": len(path.steps),
                "trit_sum": path.gf3_sum,
                "auto_balanced": "drive_to_calendar" not in PREDEFINED_PATHS or not PREDEFINED_PATHS["drive_to_calendar"].is_balanced,
            },
        )
    
    def validate_full_workflow(
        self,
        email_params: Dict[str, Any],
        file_params: Dict[str, Any],
        event_params: Dict[str, Any],
        task_params: Dict[str, Any],
    ) -> ValidationResult:
        """
        Gmail → Drive → Calendar → Task complete workflow.
        
        Full path with all cross-skill morphisms validated.
        """
        workflow_name = "full_workflow"
        errors = []
        warnings = []
        all_metrics = {}
        
        before_state = {
            "email": email_params,
            "file": file_params,
            "event": event_params,
            "task": task_params,
            "timestamp": time.time(),
        }
        
        gmail_result = self.validate_gmail_to_task(
            thread_id=email_params.get("thread_id", "thread_001"),
            task_title=task_params.get("title", "Follow up"),
            tasklist_id=task_params.get("tasklist_id", "default"),
        )
        
        drive_result = self.validate_drive_to_calendar(
            file_id=file_params.get("file_id", "file_001"),
            event_title=event_params.get("title", "Review meeting"),
            event_time=event_params.get("time", "2025-01-20T10:00:00Z"),
        )
        
        errors.extend(gmail_result.errors)
        errors.extend(drive_result.errors)
        warnings.extend(gmail_result.warnings)
        warnings.extend(drive_result.warnings)
        
        all_gf3 = gmail_result.gf3_balanced and drive_result.gf3_balanced
        all_paths = gmail_result.path_valid and drive_result.path_valid
        all_equiv = gmail_result.mcp_api_equivalent and drive_result.mcp_api_equivalent
        
        full_path = PREDEFINED_PATHS.get("full_workflow")
        if full_path and not full_path.is_balanced:
            full_path = balance_path(full_path)
            warnings.append("Full workflow path was auto-balanced")
        
        acset_invariants = verify_cross_skill_invariants(self.acset)
        if not acset_invariants.get("all_valid"):
            warnings.append(f"ACSet invariant issues: orphaned={acset_invariants.get('orphaned_tasks')}")
        
        global_sum, global_balanced = global_gf3_balance(self.acset)
        all_metrics.update({
            "gmail_steps": gmail_result.metrics.get("path_steps", 0),
            "drive_steps": drive_result.metrics.get("path_steps", 0),
            "global_gf3_sum": global_sum,
            "global_gf3_balanced": global_balanced,
            "cross_links": len(self.acset._cross_links),
        })
        
        after_state = {
            "gmail_result": gmail_result.success,
            "drive_result": drive_result.success,
            "timestamp": time.time(),
        }
        
        narya_delta = self._record_delta(before_state, after_state)
        
        return ValidationResult(
            workflow_name=workflow_name,
            success=gmail_result.success and drive_result.success,
            gf3_balanced=all_gf3 and global_balanced,
            path_valid=all_paths,
            mcp_api_equivalent=all_equiv,
            narya_delta=narya_delta,
            errors=errors,
            warnings=warnings,
            metrics=all_metrics,
        )
    
    def compare_mcp_vs_api(self, workflow: WorkflowPath) -> EquivalenceResult:
        """
        Execute workflow via MCP and API, verify equivalence.
        
        Returns detailed comparison with state diff.
        """
        seed = hash(workflow.name) & 0xFFFFFFFF
        
        initial_params = {
            "workflow": workflow.name,
            "steps": workflow.steps,
        }
        
        mcp_states = []
        api_states = []
        
        for step_name in workflow.steps:
            step = WORKFLOW_STEPS.get(step_name)
            if not step:
                continue
            
            operation = self._step_to_operation(step_name)
            params = {"step": step_name, "trit": int(step.trit)}
            
            mcp_result = self.bridge.execute_mcp(operation, params)
            api_result = self.bridge.execute_api(operation, params)
            
            mcp_states.append(mcp_result)
            api_states.append(api_result)
        
        final_mcp = mcp_states[-1] if mcp_states else {}
        final_api = api_states[-1] if api_states else {}
        
        mcp_data = final_mcp.get("data", {})
        api_data = final_api.get("data", {})
        diff = state_diff(mcp_data, api_data)
        
        trit_mcp = sum(s.get("trit", 0) for s in mcp_states)
        trit_api = sum(s.get("trit", 0) for s in api_states)
        
        return EquivalenceResult(
            operation=workflow.name,
            params=initial_params,
            seed=seed,
            equivalent=diff.get("equivalent", False),
            mcp_result=final_mcp,
            api_result=final_api,
            state_diff=diff,
            gf3_conserved=(trit_mcp % 3) == (trit_api % 3),
            trit_mcp=trit_mcp,
            trit_api=trit_api,
        )
    
    def _step_to_operation(self, step_name: str) -> str:
        """Map workflow step to MCP operation name."""
        step_to_op = {
            "gmail_read": "search_gmail_messages",
            "gmail_reply": "send_gmail_message",
            "task_create": "create_task",
            "task_complete": "update_task",
            "drive_upload": "create_drive_file",
            "calendar_create": "create_event",
            "event_link": "modify_event",
        }
        return step_to_op.get(step_name, "unknown_operation")
    
    def check_all_invariants(self) -> InvariantReport:
        """Run all invariant checks and return comprehensive report."""
        timestamp = time.time()
        
        acset_invariants = verify_cross_skill_invariants(self.acset)
        
        gf3_global = global_gf3_balance(self.acset)
        
        path_invariants = {}
        path_pairs = [
            ("gmail_to_task", "reply_workflow_balanced"),
        ]
        for p1_name, p2_name in path_pairs:
            p1 = PREDEFINED_PATHS.get(p1_name)
            p2 = PREDEFINED_PATHS.get(p2_name)
            if p1 and p2:
                result = check_path_commutativity(p1, p2)
                path_invariants[f"{p1_name}_vs_{p2_name}"] = result
        
        mcp_api_equiv = {}
        for path_name, path in PREDEFINED_PATHS.items():
            result = self.compare_mcp_vs_api(path)
            mcp_api_equiv[path_name] = result
        
        all_valid = (
            acset_invariants.get("all_valid", False)
            and gf3_global[1]
            and all(r.paths_commute for r in path_invariants.values())
            and all(r.equivalent for r in mcp_api_equiv.values())
        )
        
        summary_lines = [
            f"Invariant Check @ {timestamp:.2f}",
            f"ACSet Valid: {'✓' if acset_invariants.get('all_valid') else '✗'}",
            f"GF(3) Global: sum={gf3_global[0]}, balanced={'✓' if gf3_global[1] else '✗'}",
            f"Path Invariants: {sum(1 for r in path_invariants.values() if r.paths_commute)}/{len(path_invariants)}",
            f"MCP↔API: {sum(1 for r in mcp_api_equiv.values() if r.equivalent)}/{len(mcp_api_equiv)}",
            f"Overall: {'✓ ALL VALID' if all_valid else '✗ ISSUES FOUND'}",
        ]
        
        return InvariantReport(
            timestamp=timestamp,
            acset_invariants=acset_invariants,
            gf3_global=gf3_global,
            path_invariants=path_invariants,
            mcp_api_equivalence=mcp_api_equiv,
            narya_log=self.narya_log.copy(),
            all_valid=all_valid,
            summary="\n".join(summary_lines),
        )


def create_test_harness() -> Tuple[WorkspaceACSet, MCPAPIBridge, WorkflowValidator]:
    """Create test harness with sample data."""
    acset = WorkspaceACSet()
    bridge = MCPAPIBridge(seed=1069)
    validator = WorkflowValidator(acset, bridge)
    
    alice = acset.add_person("alice@example.com", "Alice")
    bob = acset.add_person("bob@example.com", "Bob")
    
    thread1 = "thread_project_abc"
    acset.add_interaction(thread1, bob, "send", 100)
    acset.add_interaction(thread1, alice, "read", 101)
    
    file1 = "file_spec_doc"
    acset.add_drive_file(file1, "Project Spec.docx")
    acset.add_drive_interaction(file1, alice, "create", 102)
    
    event1 = "event_review_123"
    acset.add_calendar_event(event1, "Spec Review", "2025-01-20T10:00:00Z", "2025-01-20T11:00:00Z")
    acset.add_calendar_interaction(event1, alice, "create_event", 103)
    
    tasklist1 = "tasklist_project"
    task1 = "task_review_spec"
    acset.add_task_list(tasklist1, "Project Tasks")
    acset.add_task(task1, "Review spec doc", tasklist1)
    acset.add_task_interaction(task1, tasklist1, alice, "create_task", 104)
    
    acset.link_thread_to_file(thread1, file1)
    acset.link_thread_to_event(thread1, event1)
    acset.link_thread_to_task(thread1, task1, tasklist1)
    acset.link_file_to_event(file1, event1)
    acset.link_event_to_task(event1, task1, tasklist1)
    
    return acset, bridge, validator


def run_sample_workflows() -> None:
    """Run sample workflows and print results."""
    acset, bridge, validator = create_test_harness()
    
    print("=" * 70)
    print("WORKFLOW VALIDATOR TEST HARNESS (MINUS -1: Validator)")
    print("=" * 70)
    
    print("\n### Gmail → Task Validation ###\n")
    result1 = validator.validate_gmail_to_task(
        thread_id="thread_urgent_123",
        task_title="Follow up on urgent request",
        tasklist_id="tasklist_inbox",
    )
    print(f"Workflow: {result1.workflow_name}")
    print(f"  Success: {result1.success}")
    print(f"  GF(3) Balanced: {result1.gf3_balanced}")
    print(f"  Path Valid: {result1.path_valid}")
    print(f"  MCP↔API: {result1.mcp_api_equivalent}")
    if result1.errors:
        print(f"  Errors: {result1.errors}")
    if result1.warnings:
        print(f"  Warnings: {result1.warnings}")
    print(f"  Metrics: {result1.metrics}")
    
    print("\n### Drive → Calendar Validation ###\n")
    result2 = validator.validate_drive_to_calendar(
        file_id="file_quarterly_report",
        event_title="Quarterly Review",
        event_time="2025-01-25T14:00:00Z",
    )
    print(f"Workflow: {result2.workflow_name}")
    print(f"  Success: {result2.success}")
    print(f"  GF(3) Balanced: {result2.gf3_balanced}")
    print(f"  Path Valid: {result2.path_valid}")
    print(f"  MCP↔API: {result2.mcp_api_equivalent}")
    if result2.warnings:
        print(f"  Warnings: {result2.warnings}")
    print(f"  Metrics: {result2.metrics}")
    
    print("\n### Full Workflow Validation ###\n")
    result3 = validator.validate_full_workflow(
        email_params={"thread_id": "thread_kickoff"},
        file_params={"file_id": "file_project_plan"},
        event_params={"title": "Project Kickoff", "time": "2025-02-01T09:00:00Z"},
        task_params={"title": "Prepare kickoff materials", "tasklist_id": "tasklist_project"},
    )
    print(f"Workflow: {result3.workflow_name}")
    print(f"  Fully Valid: {result3.is_fully_valid}")
    print(f"  GF(3) Balanced: {result3.gf3_balanced}")
    print(f"  Metrics: {result3.metrics}")
    
    print("\n### MCP vs API Equivalence ###\n")
    path = PREDEFINED_PATHS.get("gmail_to_task")
    equiv_result = validator.compare_mcp_vs_api(path)
    print(f"Path: {equiv_result.operation}")
    print(f"  Equivalent: {equiv_result.equivalent}")
    print(f"  GF(3) Conserved: {equiv_result.gf3_conserved}")
    print(f"  Trit MCP: {equiv_result.trit_mcp}, API: {equiv_result.trit_api}")
    
    print("\n### All Invariants Check ###\n")
    report = validator.check_all_invariants()
    print(report.summary)
    
    print("\n### Narya Delta Log ###\n")
    for i, delta in enumerate(validator.narya_log[:3]):
        print(f"Delta {i+1}: {len(delta.delta)} changes")
    
    print(f"\nTotal validations: {validator._validation_count}")
    print(f"Validator trit sum: {validator._trit_sum}")
    print("=" * 70)


if __name__ == "__main__":
    run_sample_workflows()
