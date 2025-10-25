# Agent-o-rama Examples

This directory contains example implementations of AI agents using Agent-o-rama.

Agents can be run from the REPL like so:

```
lein repl
user=> (require '[com.rpl.agent.react :as react])
user=> (react/run-agent)
```

This will prompt for an input to the agent and then print the result. It also opens the UI at http://localhost:1974, which will remain open for viewing traces, performing more invokes, and other exploration until you press enter in the terminal.
