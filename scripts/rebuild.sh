#!/usr/bin/env bash
set -ex

# Rama source checkout, override if it is not a sibling of this repo
RAMA_SRC="${RAMA_SRC:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/rama}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# destroy local cluster
pushd "$RAMA_SRC"
./scripts/local-cluster/shutdown-local-cluster.sh
rm -rf ./local-cluster
./scripts/local-cluster/setup-local-cluster.sh
popd
# compile new aor
pushd "$REPO_ROOT"
./scripts/build-ui.sh
lein install
popd
# uberjar in examples directory
pushd "$REPO_ROOT/examples/clj"
lein uberjar
popd
# ./rama --deploy
JAR="$(ls -t "$REPO_ROOT"/examples/clj/target/agent-o-rama-examples-*-standalone.jar | head -n 1)"
pushd "$RAMA_SRC/local-cluster/client"
./rama deploy --action launch --jar "$JAR" --module com.rpl.agent.research-agent/ResearchAgentModule --workers 1 --threads 1 --tasks 1
./rama deploy --action launch --jar "$JAR" --module com.rpl.agent.basic.basic-agent/BasicAgentModule --workers 1 --threads 1 --tasks 1
popd
