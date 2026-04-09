# AGENTS.md

## Cursor Cloud specific instructions

### Overview

Agent-o-rama is a Clojure/ClojureScript LLM agent platform built on Rama. No external infrastructure (databases, Docker, etc.) is needed — Rama's in-process cluster (IPC) handles everything in a single JVM.

### Prerequisites

- **JDK 21+** (pre-installed)
- **Leiningen** (`lein`) — Clojure build tool; install via `curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein | sudo tee /usr/local/bin/lein > /dev/null && sudo chmod +x /usr/local/bin/lein && lein version`
- **Node.js 20+** and **npm** (pre-installed)

### Common commands

See `dev/claude/CLAUDE.md` for the full list. Key commands:

| Task | Command |
|---|---|
| Install npm deps | `npm ci` |
| Fetch JVM deps | `lein with-profile +dev,+ui,+provided deps` |
| Compile ClojureScript frontend | `lein with-profile +ui run -m shadow.cljs.devtools.cli --npm compile :frontend` |
| Run Clojure tests (default, no UI tests) | `lein test` |
| Run ClojureScript tests | `node target/test/test.js` (after compiling with `lein with-profile +ui run -m shadow.cljs.devtools.cli compile :test`) |
| Start app (IPC + UI on port 1974) | `lein with-profile +dev,+ui run -m ci-playwright-setup --no-frontend` |
| Start REPL with UI support | `lein with-profile +ui,+nrepl-port repl` |

### Non-obvious caveats

- **JVM memory**: The project requires `-Xms6g -Xmx6g` (6 GB heap) and `-Xss6m` stack. These are set in `project.clj` `:jvm-opts`. Ensure the VM has at least 8 GB RAM.
- **IPC tests are slow**: Each Clojure test that creates an IPC spins up/down a full in-process Rama cluster. The full `lein test` suite takes ~15-20 minutes.
- **Frontend must be pre-compiled before starting the app**: Run the ClojureScript compile step before launching `ci-playwright-setup --no-frontend`. The compiled output goes to `resource/public/`.
- **UI test selectors**: `lein test` runs the `:default` selector which excludes `com.rpl.agent-o-rama.ui.*` namespaces. Use `lein test :ui` for Etaoin browser tests (needs ChromeDriver) or `lein test :all` for everything.
- **Rama dependencies come from a custom Maven repo**: `https://nexus.redplanetlabs.com/repository/maven-public-releases` — this is configured in `project.clj` `:repositories`.
- **Optional API keys**: `OPENAI_API_KEY` and `TAVILY_API_KEY` are only needed for LLM-based example agents and some integration tests. Core tests pass without them.
- **Port 1974**: The Agent-o-rama web UI listens on this port by default.
- **`resource/assets/`** contains static assets (logos) that should be copied to `resource/public/` before serving the frontend.
