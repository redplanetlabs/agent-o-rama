## Cursor Cloud specific instructions

### Project overview

Agent-o-rama is a Clojure/Java LLM agent platform built on Rama. See `README.md` and `dev/claude/CLAUDE.md` for full docs and command reference.

### Clojure REPL tools

`clojure-mcp-light` tools are installed globally:

- `clj-nrepl-eval -p <port> "<code>"` — evaluate Clojure via nREPL
- `clj-nrepl-eval --discover-ports` — find running nREPL servers
- `clj-paren-repair <files>` — auto-fix delimiter errors in Clojure files
- `clj-paren-repair-claude-hook` — pre/post edit hook for delimiter repair

`clojure-mcp` is installed as a Clojure tool (`clojure -Tmcp start ...`) and registered in `.cursor/mcp.json` with `:cli-assist` profile. It auto-starts a headless nREPL on port 7888.

**Always use `:reload` when requiring namespaces** to pick up source changes.

### Running services

- **REPL**: `lein with-profile +ui repl` starts nREPL + enables UI compilation. The REPL port is dynamic unless you add the `+nrepl-port` profile (port 7888).
- **UI**: Call `(aor/start-ui ipc)` inside the REPL after creating an IPC. Serves on `http://localhost:1974`.
- **IPC tests take several minutes** — this is normal (Rama in-process cluster startup is slow).
- No external services (database, Docker, etc.) are required for core dev/test.

### Key commands

| Task | Command |
|------|---------|
| Backend tests | `lein test` |
| CLJS tests | `lein with-profile +ui run -m shadow.cljs.devtools.cli compile :test` |
| Compile frontend | `lein with-profile +ui run -m shadow.cljs.devtools.cli --npm compile :frontend` |
| Lint | `lein run -m clj-kondo.main -- --lint src/clj` |
| REPL with UI | `lein with-profile +ui repl` |

### Gotchas

- JVM opts require **6 GB heap** (`-Xms6g -Xmx6g`). Ensure the VM has enough memory.
- `clj-kondo` reports many false-positive errors for Rama macros and Specter symbols — this is expected.
- The `:default` test selector excludes `com.rpl.agent-o-rama.ui.*` namespaces. Use `lein test :all` to include UI E2E tests (which need Docker/Chromium).
- Frontend assets must be copied before ClojureScript compilation: `cp -r resource/assets/* resource/public/`.
