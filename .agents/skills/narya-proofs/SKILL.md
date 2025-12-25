---
name: narya-proofs
description: Mechanically verified proofs from Narya event logs. Verifies queue consistency, replay determinism, non-leakage, and GF(3) conservation. Use for proving system invariants, audit trails, or formal verification of event-sourced systems.
---

# Narya Proofs Skill

Unified verification for event-sourced systems using JSONL interaction logs. Generates cryptographic proof certificates with GF(3) conservation guarantees.

## Four Verifiers with GF(3) Assignments

| Verifier | Trit | Role | Color Range |
|----------|------|------|-------------|
| `queue_consistency` | -1 | MINUS validator | Cold (180-300°) |
| `non_leakage` | -1 | MINUS validator | Cold (180-300°) |
| `replay_determinism` | 0 | ERGODIC coordinator | Neutral (60-180°) |
| `gf3_conservation` | +1 | PLUS generator | Warm (0-60°, 300-360°) |

**GF(3) Meta-Balance**: Sum = -1 + -1 + 0 + 1 = -1 ≡ 2 (mod 3). Runner adds meta-trit +1 → 0 ≡ 0 (mod 3) ✓

## Denotation

> **This skill generates cryptographic proof certificates for event-sourced systems, verifying that all invariants hold and ensuring consistency across distributed systems via mechanically checked proofs.**

```
ProofBundle = ∏_{verifier} (Events → VerifierResult)
Certificate = sha256(Merkle(ProofBundle))
Verdict: VERIFIED ⟺ ∀ verifier: passed = true
```

## Invariant Set

| Invariant | ID | Definition | Verifier |
|-----------|-----|------------|----------|
| `QueueConsistency` | INV-001 | No duplicate event IDs, monotonic timestamps | `queue_consistency` |
| `ReplayDeterminism` | INV-002 | Same seed → same content hash | `replay_determinism` |
| `NonLeakage` | INV-003 | No PII/secrets in event content | `non_leakage` |
| `GF3Conservation` | INV-004 | Context trit sum ≡ 0 (mod 3) | `gf3_conservation` |
| `ProofIntegrity` | INV-005 | Certificate hash covers all verifier outputs | Hash verification |

## GF(3) Typed Effects

| Verifier | Trit | Effect Type | Description |
|----------|------|-------------|-------------|
| `queue_consistency` | -1 | VALIDATOR | No state mutation, validates structure |
| `non_leakage` | -1 | VALIDATOR | No state mutation, validates schema |
| `replay_determinism` | 0 | COORDINATOR | Ensures deterministic replay coordination |
| `gf3_conservation` | +1 | GENERATOR | Generates proof of conservation |

## Narya Compatibility

| Field | Definition |
|-------|------------|
| `before` | Initial event log (JSONL) |
| `after` | Proof bundle with all verifier results |
| `delta` | Proof of state transition (certificate) |
| `birth` | Empty event log |
| `impact` | 1 if any verifier fails (state change from VERIFIED to FAILED) |

## Condensation Policy

**Trigger**: When all 4 verifiers pass for 3 consecutive verification cycles.

**Action**: Archive event log segment, emit condensed proof certificate.

## Proof Objects and Certificates

```python
from dataclasses import dataclass

@dataclass
class VerifierResult:
    name: str           # Verifier name
    trit: int           # GF(3) assignment {-1, 0, +1}
    passed: bool        # Verification passed
    details: dict       # Violation details

@dataclass
class ProofBundle:
    log_path: str           # Source JSONL file
    events_total: int       # Total events processed
    verifiers: dict         # Results per verifier
    overall: str            # "VERIFIED" or "FAILED"
    proof_hash: str         # sha256:... certificate
    gf3_meta: dict          # Trit conservation metadata
```

## Narya JSONL Log Format

Each line is a JSON object representing an event:

```jsonl
{"event_id": "e1", "timestamp": 1735084800.0, "thread_id": "t1", "trit": -1, "context": "workflow-A", "delta": {"type": "queue_item", "queue_id": "q1", "agent_of": 1, "item_of": "i1", "route": 1}, "content": {"action": "enqueue"}}
{"event_id": "e2", "timestamp": 1735084801.0, "thread_id": "t1", "trit": 0, "context": "workflow-A", "delta": {"type": "route_update", "interaction_id": "i1", "agent_id": 1}, "seed": 42}
{"event_id": "e3", "timestamp": 1735084802.0, "thread_id": "t1", "trit": 1, "context": "workflow-A", "delta": {"type": "agent_assignment", "queue_id": "q1", "agent_id": 1}}
```

**Required fields**: `event_id`
**Optional fields**: `timestamp`, `thread_id`, `trit`, `context`, `delta`, `content`, `seed`, `before_hash`, `after_hash`

## 1. Queue Consistency (Diagram Commutativity)

**Claim**: `agent_of(q) = route(item_of(q))` always holds.

```
        agent_of
Queue q ────────────► Agent a
    │                    ▲
    │ item_of            │ route
    ▼                    │
Interaction i ───────────┘
```

Verifies that the diagram commutes for every event touching `agent_of`, `item_of`, or `route`.

```python
from src.narya_proofs.queue_consistency import (
    QueueConsistencyVerifier,
    generate_proof_certificate,
    NaryaEvent
)

verifier = QueueConsistencyVerifier(seed=1069)
log = [
    NaryaEvent(event_id="e1", before_hash="000", after_hash="abc",
               delta={"type": "queue_item", "queue_id": "q1", 
                      "agent_of": 1, "item_of": "i1", "route": 1})
]
summary = verifier.verify_log(log)
cert = generate_proof_certificate(log, seed=1069)
# cert["verdict"] == "VERIFIED"
```

## 2. Replay Determinism (Hash Replay, Time-Travel)

**Claim**: Events with the same seed produce identical content hashes.

Verifies that replay is deterministic—running the same seed produces identical outputs regardless of execution order or timing.

```python
from src.narya_proofs.runner import replay_determinism

events = [
    {"event_id": "e1", "seed": 42, "content": {"value": "hello"}},
    {"event_id": "e2", "seed": 42, "content": {"value": "hello"}},  # Same seed → same hash ✓
    {"event_id": "e3", "seed": 99, "content": {"value": "world"}},
]

result = replay_determinism(events)
# result.passed == True
# result.details["hash_matches"] == 2
```

## 3. Non-Leakage (Schema Conformance, PII Detection)

**Claim**: No secrets or PII appear in event content.

Detects:
- Email addresses
- SSNs (`\d{3}-\d{2}-\d{4}`)
- Credit card numbers (16 digits)
- Redaction markers `[REDACTED:...]`
- Credentials (`password=`, `api_key=`, etc.)

```python
from src.narya_proofs.runner import non_leakage

events = [
    {"event_id": "e1", "content": {"user": "alice"}},  # Clean ✓
    {"event_id": "e2", "content": {"email": "alice@example.com"}},  # Leak! ✗
]

result = non_leakage(events)
# result.passed == False
# result.details["leak_details"][0]["types"] == ["email"]
```

## 4. GF(3) Conservation (Workflow Law)

**Claim**: In any closed workflow context, sum of trits ≡ 0 (mod 3).

```python
from src.narya_proofs.gf3_conservation import (
    GF3ConservationVerifier,
    Event,
    create_triadic_cycle
)

# Create verifier
verifier = GF3ConservationVerifier(auto_close=True)

# Valid triadic cycle (sum = -1 + 0 + 1 = 0)
events = [
    Event("e1", "ctx-alpha", trit=-1),
    Event("e2", "ctx-alpha", trit=0),
    Event("e3", "ctx-alpha", trit=1),
]

for e in events:
    verifier.add_event(e)

proof = verifier.verify_context_closure("ctx-alpha")
# proof.conserved == True
# proof.qed == True
```

### ASCII Visualization

```
─── Trit Flow: ctx-alpha ───

  Event   │ Trit │ Running Sum │ Visualization
──────────┼──────┼─────────────┼────────────────────────────────
  e1      │ [-1] │    -1 (2)   │ ◀── █████████████████████│
  e2      │ [ 0] │    -1 (2)   │ ─●─ █████████████████████│
  e3      │ [+1] │     0 (0) ◆ │ ──▶                     │
──────────┴──────┴─────────────┴────────────────────────────────
  Final: Σ =   0, ✓ CONSERVED (mod3=0)
```

## Unified Runner with CLI

```bash
# Run all verifiers on a JSONL log
python -m src.narya_proofs.runner path/to/events.jsonl

# Output to file
python -m src.narya_proofs.runner events.jsonl -o proof.json

# With custom seed
python -m src.narya_proofs.runner events.jsonl --seed 42

# Quiet mode (JSON only)
python -m src.narya_proofs.runner events.jsonl -q
```

### Programmatic Usage

```python
from src.narya_proofs import NaryaProofRunner

runner = NaryaProofRunner(seed=1069)
runner.load_log("events.jsonl")
runner.run_all_verifiers()

bundle = runner.generate_proof_bundle()
print(bundle.overall)      # "VERIFIED" or "FAILED"
print(bundle.proof_hash)   # "sha256:abc123..."
print(runner.to_json())    # Full JSON certificate
```

## Example Verification Output

```json
{
  "log_path": "/path/to/events.jsonl",
  "events_total": 150,
  "verifiers": {
    "queue_consistency": {
      "passed": true,
      "events_checked": 150,
      "violations": 0,
      "violation_details": []
    },
    "non_leakage": {
      "passed": true,
      "clean": 150,
      "leaked": 0,
      "leak_details": []
    },
    "replay_determinism": {
      "passed": true,
      "hash_matches": 45,
      "total_seeds": 45,
      "mismatches": []
    },
    "gf3_conservation": {
      "passed": true,
      "contexts": 5,
      "conserved": 5,
      "violated": 0,
      "total_trit_sum": 0,
      "total_mod3": 0
    }
  },
  "overall": "VERIFIED",
  "proof_hash": "sha256:8a4f2e1b3c5d7e9f...",
  "gf3_meta": {
    "verifier_trits": {
      "queue_consistency": -1,
      "non_leakage": -1,
      "replay_determinism": 0,
      "gf3_conservation": 1
    },
    "verifier_trit_sum": -1,
    "meta_trit": 1,
    "total_sum": 0,
    "conserved": true
  }
}
```

## Reference Files

- [runner.py](file:///Users/alice/agent-o-rama/agent-o-rama/src/narya_proofs/runner.py) — Unified runner, CLI, all 4 verifiers
- [queue_consistency.py](file:///Users/alice/agent-o-rama/agent-o-rama/src/narya_proofs/queue_consistency.py) — Diagram commutativity, Čech cohomology integration
- [gf3_conservation.py](file:///Users/alice/agent-o-rama/agent-o-rama/src/narya_proofs/gf3_conservation.py) — Context tracking, triadic cycles
- [non_leakage.py](file:///Users/alice/agent-o-rama/agent-o-rama/src/narya_proofs/non_leakage.py) — PII detection patterns
- [replay_determinism.py](file:///Users/alice/agent-o-rama/agent-o-rama/src/narya_proofs/replay_determinism.py) — Seed→hash verification

## GF(3) Triadic Integration

Forms valid triads with complementary skills:

```
narya-proofs (-1) ⊗ ordered-locale (0) ⊗ gay-mcp (+1) = 0 ✓
narya-proofs (-1) ⊗ bisimulation-game (-1) ⊗ gf3_conservation (+1) = -1 ≡ 2 (mod 3)
sheaf-cohomology (-1) ⊗ narya-proofs (-1) ⊗ topos-generate (+1) + meta(+1) = 0 ✓
```

## Commands

```bash
# Run verification demo
just narya-verify events.jsonl

# Generate proof certificate
just narya-cert events.jsonl -o cert.json

# Queue consistency only
just narya-queue-check events.jsonl

# GF(3) conservation report (ASCII visualization)
just narya-gf3-report events.jsonl
```

---

**Skill Name**: narya-proofs  
**Type**: Formal Verification / Proof Generation / Event Sourcing  
**Trit**: -1 (MINUS - overall validator role)  
**GF(3)**: Conserved via meta-trit balancing  
**Proof Hash**: SHA-256 Merkle root over all proof objects
