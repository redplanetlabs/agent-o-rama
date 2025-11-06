set -x 
# destroy local cluster
pushd ../rama/
./scripts/local-cluster/shutdown-local-cluster.sh
rm -rf ./local-cluster
./scripts/local-cluster/setup-local-cluster.sh
popd
# compile new aor
./scripts/build.sh
# uberjar in examples directory
pushd ./examples/clj
lein uberjar
popd
# ./rama --deploy
pushd ../rama/local-cluster/client
export JAR=/Users/tommy/programming/agent-o-rama/examples/clj/target/agent-o-rama-examples-1.0.0-SNAPSHOT-standalone.jar
./rama deploy --action launch --jar $JAR --module com.rpl.agent.research-agent/ResearchAgentModule --workers 1 --threads 1 --tasks 1
./rama deploy --action launch --jar $JAR --module com.rpl.agent.basic.basic-agent/BasicAgentModule --workers 1 --threads 1 --tasks 1
