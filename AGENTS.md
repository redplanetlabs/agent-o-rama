# AGENTS.md

Agent-o-rama: LLM agent platform on Rama with Java/Clojure APIs, tracing, experiments, and deployment.

## Ontology
See `dev/terms/ONTOLOGY.md` for the 5-layer pre-agent ontology:
- **Layer 0**: seed, trit, γ (pre-ontological primitives)
- **Layer 1**: derivation, chain (replaces temporal succession)
- **Layer 2**: stalk, section, cohomology (sheaf structure)
- **Layer 3**: node, emit, aggregation, result (operational)
- **Layer 4**: agent, skill (emergent patterns, not primitives)

**Key invariant**: GF(3) conservation — `Σ trits ≡ 0 (mod 3)` across all operations.

## Resilience Pattern
When risk of getting stuck, always trifurcate into 3 parallel subagents:
- **MINUS (-1)**: Validate/verify the current state
- **ERGODIC (0)**: Coordinate/synthesize alternatives  
- **PLUS (+1)**: Generate/execute the solution

This ensures GF(3) conservation and prevents single-point failures.

## Commands
```bash
lein test                                    # Run all tests (excludes UI tests)
lein test :only com.rpl.agent-o-rama-test    # Run single test namespace
lein test :only com.rpl.agent-o-rama-test/graph-test  # Run single test
lein test :ui                                # UI tests only
lein test-all                                # All tests including examples
lein with-profile +dev run -m user           # Start dev REPL with IPC
lein uberjar                                 # Build for deployment
```

## Architecture
- `src/clj/com/rpl/agent_o_rama.clj` - Main Clojure API (`aor/` namespace)
- `src/java/` - Java API with `AgentModule`, `AgentNode`, `AgentClient`
- `src/cljs/` - ClojureScript UI (UIx/React, shadow-cljs)
- `examples/clj/` - Example agents and ASI self-evolving agent
- Rama macros: `<<sources`, `source>`, `batch<-`, `loop<-`, PState declarations

## Code Style
- Format with zprint (see `.zprintrc`); 100 char width, no map commas
- Enable `*warn-on-reflection*` globally; avoid reflection warnings
- Rama operators: `defagentmodule`, `aor/new-agent`, `aor/node`, `aor/result!`
- Test with `deftest`/`testing`/`is`; use IPC via `rtest/create-ipc`
- Java interop: LangChain4j for LLM calls, builder pattern for models
