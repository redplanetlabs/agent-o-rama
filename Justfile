# Agent-O-Rama Justfile
# Run with: just <recipe>

default:
    @just --list

# WORM: Worm through agent thread history, take DuckLake snapshot
worm *ARGS:
    bb scripts/worm.bb {{ARGS}}

# WORM with screenshot
worm-screenshot:
    bb scripts/worm.bb --screenshot

# Run teleport verification
teleport-verify:
    bb -e "(load-file \"examples/clj/src/com/rpl/agent/teleport_verify.clj\") (com.rpl.agent.teleport-verify/-main)"

# Run ACSet handoff verification
acset-verify:
    bb -e "(load-file \"examples/clj/src/com/rpl/agent/handoff_acset.clj\") (com.rpl.agent.handoff-acset/-main)"

# Run many-to-more lattice verification
lattice-verify:
    bb -e "(load-file \"examples/clj/src/com/rpl/agent/many_to_more.clj\") (com.rpl.agent.many-to-more/-main)"

# Run all verifications
verify-all: teleport-verify acset-verify lattice-verify

# Run topos EVE
eve:
    bb -e "(load-file \"examples/clj/src/com/rpl/agent/topos_eve.clj\") (com.rpl.agent.topos-eve/-main)"

# Run cobordism screen model
cobordism:
    bb -e "(load-file \"examples/clj/src/com/rpl/agent/cobordism_screen.clj\") (com.rpl.agent.cobordism-screen/-main)"

# === HISTORY COMPOSITION ===

# Approach 2: DuckDB SQL union (requires duckdb CLI)
history-sql:
    duckdb < scripts/history_union.sql

# Approach 3: Babashka JSONL parsing with full counts
history-compose:
    bb scripts/history_compose.bb

# === DUCKLAKE RECONSTRUCTION ===

# Trifurcated reconstruction from ~/.topos/ducklake.duckdb
ducklake-reconstruct:
    bb scripts/ducklake_reconstruct.bb

# Query ducklake directly
ducklake-query SQL:
    duckdb ~/.topos/ducklake.duckdb "{{SQL}}"

# Show ducklake tables
ducklake-tables:
    duckdb ~/.topos/ducklake.duckdb "SHOW TABLES;"

# Export ducklake snapshot
ducklake-export:
    duckdb ~/.topos/ducklake.duckdb "EXPORT DATABASE 'worm_data/ducklake_export' (FORMAT PARQUET);"

# === RANDOM WALK RECONSTRUCTION ===

# Run random walk reconstruction (default 9 steps)
walk-reconstruct STEPS="9":
    bb scripts/random_walk_reconstruct.bb {{STEPS}}

# Verify ~/.topos structure
topos-verify:
    ls -la ~/.topos/ | head -30
    @echo "---"
    @echo "Files: $(ls ~/.topos/*.py ~/.topos/*.jl 2>/dev/null | wc -l | tr -d ' ')"
    @echo "Dirs: $(ls -d ~/.topos/*/ 2>/dev/null | wc -l | tr -d ' ')"

# Hydrate sparse ~/.topos directories (dry run)
topos-hydrate-dry:
    bb scripts/topos_hydrate.bb --dry-run

# Hydrate sparse ~/.topos directories (create files)
topos-hydrate:
    bb scripts/topos_hydrate.bb

# === MLX LOCAL LLM (December 2025 Models) ===

# Start MLX server with default model (Qwen3-8B - best Dec 2025)
mlx-server MODEL="mlx-community/Qwen3-8B-4bit":
    @echo "Starting MLX server with {{MODEL}}"
    @echo "Endpoint: http://localhost:8080/v1"
    uvx --from mlx-lm mlx_lm.server --model "{{MODEL}}" --host 0.0.0.0 --port 8080 --use-default-chat-template

# Start MLX with tiny model for quick testing (~300MB)
mlx-tiny:
    just mlx-server "mlx-community/Qwen3-0.6B-4bit"

# Start MLX with small model (1.7B - good balance)
mlx-small:
    just mlx-server "mlx-community/Qwen3-1.7B-4bit"

# Start MLX with medium model (4B - reasoning capable)
mlx-medium:
    just mlx-server "mlx-community/Qwen3-4B-4bit"

# Start MLX with MoE model (30B total, only 3B active - efficient!)
mlx-moe:
    just mlx-server "mlx-community/Qwen3-30B-A3B-4bit"

# Start MLX with Coder model (BEST for Clojure/SCI - 358 languages)
mlx-coder:
    just mlx-server "lmstudio-community/Qwen3-Coder-30B-A3B-Instruct-MLX-4bit"

# Test MLX server connectivity
mlx-test:
    python3 scripts/test_mlx.py

# === MULTI-MODEL AGENTS ===

# Show multi-model agent architecture
agents-arch:
    bb scripts/multi_model_agents.bb arch

# Check which models are running
agents-check:
    bb scripts/multi_model_agents.bb check

# Run task across all available models (parallel, with subagents)
agents-run TASK="Write a Clojure function for Babashka that parses JSON":
    bb scripts/multi_model_agents.bb run "{{TASK}}"

# === GAY TIDAR (GF(3) Triadic Orchestration) ===

# Show gay tidar architecture
tidar-arch:
    bb scripts/gay_tidar_agent.bb arch

# Check triadic stream availability
tidar-check:
    bb scripts/gay_tidar_agent.bb check

# Run triadic orchestration (MINUS/ERGODIC/PLUS)
tidar-run TASK="Analyze the codebase" SEED="42D":
    bb scripts/gay_tidar_agent.bb run "{{TASK}}" {{SEED}}

# Show color and trits for a seed
tidar-color SEED="42D":
    bb scripts/gay_tidar_agent.bb color {{SEED}}

# Start MINUS stream (validator) on :8080
tidar-minus:
    @echo "Starting MINUS stream (validator) on :8080..."
    uvx --from mlx-lm mlx_lm.server --model "lmstudio-community/Qwen3-Coder-30B-A3B-Instruct-MLX-4bit" --host 0.0.0.0 --port 8080 --use-default-chat-template

# Start ERGODIC stream (coordinator) on :8081
tidar-ergodic:
    @echo "Starting ERGODIC stream (coordinator) on :8081..."
    uvx --from mlx-lm mlx_lm.server --model "mlx-community/Qwen3-8B-4bit" --host 0.0.0.0 --port 8081 --use-default-chat-template

# Start PLUS stream (generator) on :8082
tidar-plus:
    @echo "Starting PLUS stream (generator) on :8082..."
    uvx --from mlx-lm mlx_lm.server --model "mlx-community/Qwen3-4B-4bit" --host 0.0.0.0 --port 8082 --use-default-chat-template

# Start coder model on port 8080 (primary)
agents-start-coder:
    @echo "Starting CODER agent on :8080..."
    uvx --from mlx-lm mlx_lm.server --model "lmstudio-community/Qwen3-Coder-30B-A3B-Instruct-MLX-4bit" --host 0.0.0.0 --port 8080 --use-default-chat-template

# Start medium model on port 8081
agents-start-medium:
    @echo "Starting MEDIUM agent on :8081..."
    uvx --from mlx-lm mlx_lm.server --model "mlx-community/Qwen3-8B-4bit" --host 0.0.0.0 --port 8081 --use-default-chat-template

# Start small model on port 8082
agents-start-small:
    @echo "Starting SMALL agent on :8082..."
    uvx --from mlx-lm mlx_lm.server --model "mlx-community/Qwen3-4B-4bit" --host 0.0.0.0 --port 8082 --use-default-chat-template

# Run ASI agent with local MLX (server must be running)
asi-mlx TOPIC="agentic coordination":
    OPENAI_BASE_URL=http://localhost:8080/v1 cd examples/clj && lein run -m com.rpl.agent.asi-agent "{{TOPIC}}"

# === TOPOS SHEAF ===

# Run sheaf verification (7 MCP servers, 2-3-2 distribution)
sheaf-verify:
    cd examples/clj && lein run -m com.rpl.agent.topos-sheaf

# Run trifurcate pattern
trifurcate:
    cd examples/clj && lein run -m com.rpl.agent.topos-trifurcate

# Run full topos MCP orchestrator
topos-mcp:
    cd examples/clj && lein run -m com.rpl.agent.topos-mcp

# Run topos forward (SPI verification)
topos-forward:
    cd examples/clj && lein run -m com.rpl.agent.topos-forward

# Run world hopping (unified: sheaf × trifurcate × triangle inequality)
world-hop-lein:
    cd examples/clj && lein run -m com.rpl.agent.topos-world-hop

# Verify all invariants (GF3 + triangle + sheaf)
invariants-verify:
    cd examples/clj && lein run -m com.rpl.agent.topos-world-hop

# === ASI AGENT ===

# Run ASI agent via Babashka (no Rama, uses Anthropic API)
asi TOPIC="distributed coordination" N="4":
    bb scripts/asi_bb.clj "{{TOPIC}}" {{N}}

# Dry run (no self-hosting)
asi-dry TOPIC="test topic":
    bb scripts/asi_bb.clj "{{TOPIC}}" 3 --dry-run

# List top skills by complexity
asi-skills N="10":
    clj -M -e "(load-file \"dev/asi_dev.clj\") (in-ns 'asi-dev) (doseq [s (top {{N}})] (println (format \"%2d. %-30s %3d files, %5d LOC\" (:rank s) (:name s) (:files s) (:loc s))))"

# Search skills by keyword
asi-search QUERY:
    clj -M -e "(load-file \"dev/asi_dev.clj\") (in-ns 'asi-dev) (println (search \"{{QUERY}}\"))"

# Show skill info
asi-info SKILL:
    clj -M -e "(load-file \"dev/asi_dev.clj\") (in-ns 'asi-dev) (println (:preview (skill-info \"{{SKILL}}\")))"

# === CENTRAL PATTERN ANALYSIS ===

# Analyze central interactions and patterns
central-patterns:
    bb scripts/central_patterns.bb

# Quick pattern summary from DuckLake
pattern-summary:
    duckdb ~/.topos/ducklake.duckdb "SELECT pattern_id, pattern_type, occurrences, trit FROM interaction_patterns ORDER BY occurrences DESC;"

# XOR signature analysis
xor-analysis:
    duckdb ~/.topos/ducklake.duckdb "SELECT xor_fingerprint, freq, ROUND(avg_omega,3) as omega FROM xor_signatures ORDER BY freq DESC LIMIT 20;"

# === WORLD HOPPING ===

# List available worlds (amp, claude, codex, codex-mlx)
world-list:
    bb scripts/world_hop.bb list

# Show world distance matrix
world-distances:
    bb scripts/world_hop.bb distances

# Hop between worlds (preserves invariants)
world-hop FROM TO:
    bb scripts/world_hop.bb hop {{FROM}} {{TO}}

# Configure Codex for local MLX
codex-mlx:
    bb scripts/world_hop.bb mlx

# Run mlxies (local MLX codex)
mlxies *ARGS:
    ./scripts/mlxies {{ARGS}}

# Run newies (remote OpenAI codex)  
newies *ARGS:
    ./scripts/newies {{ARGS}}

# Show newies/mlxies interaction patterns
handoff-show:
    bb scripts/newies_mlxies_handoff.bb show

# Create handoff from one to other
handoff FROM TO CONTEXT:
    bb scripts/newies_mlxies_handoff.bb handoff {{FROM}} {{TO}} "{{CONTEXT}}"

# Show preserved invariants
world-invariants:
    bb scripts/world_hop.bb invariants

# === ASI SHEAF VERIFICATION ===

# Test sheaf cohomology on skill overlaps (default 4 skills)
asi-sheaf N="4":
    bb scripts/asi_sheaf.bb {{N}}

# Test sheaf cohomology on skill groups (category, agent, clojure)
asi-sheaf-group GROUP="agent":
    bb scripts/asi_sheaf_group.bb {{GROUP}}

# === ACHIEVEMENTS ===

# Show all achievements
achievements:
    @cat ACHIEVEMENTS.md | head -100

# Query achievements from DuckLake
achievements-db:
    duckdb ~/.topos/ducklake.duckdb "SELECT pattern_id, description, trit FROM interaction_patterns WHERE pattern_type = 'achievement' ORDER BY trit;"

# Verify achievements GF(3) conservation
achievements-gf3:
    duckdb ~/.topos/ducklake.duckdb "SELECT SUM(trit) as sum, CASE WHEN SUM(trit) % 3 = 0 THEN 'CONSERVED ✓' ELSE 'IMBALANCED' END as gf3 FROM interaction_patterns WHERE pattern_type = 'achievement';"
