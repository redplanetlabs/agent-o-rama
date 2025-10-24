# Quick Start Guide

This guide will get you up and running with Agent-o-rama in just a few minutes. You'll learn how to run examples locally and then deploy to a Rama cluster.

## Table of Contents

1. [Running Examples Locally](#running-examples-locally)
2. [Running on a Local Rama Cluster](#running-on-a-local-rama-cluster)

## Running Examples Locally

The fastest way to try Agent-o-rama is to run the examples locally using InProcessCluster (IPC). This runs a complete Rama cluster in a single JVM process - perfect for development and testing.

The `examples/` directory in the Agent-o-rama repo contains `java/` and `clj/` subfolders with lots of examples of agents. One of those examples is a simple ReAct agent that can search the web to answer questions. Below are instructions for running the Java or Clojure versions of that agent.

For both, the example will run an IPC cluster, launch the UI at `http://localhost:1974`, prompt you for a question, invoke the agent, and then print the result. The UI will remain open for viewing traces, performing more invokes, and other exploration until you press enter in the terminal.

### Java Example

#### Prerequisites

- Maven installed
- OpenAI API key
- [Tavily](https://www.tavily.com/) API key (for web search, the free tier is sufficient)

#### Running the Example

1. **Set up environment variables:**
   ```bash
   export OPENAI_API_KEY=your_openai_key_here
   export TAVILY_API_KEY=your_tavily_key_here
   ```

2. **Build and run the example:**
   ```bash
   cd examples/java
   mvn clean compile exec:java -Dexec.mainClass="com.rpl.agent.react.ReActExample"
   ```


### Clojure Example

#### Prerequisites

- [Leiningen](https://leiningen.org/) installed
- OpenAI API key
- [Tavily](https://www.tavily.com/) API key (for web search, the free tier is sufficient)

#### Running the Example

1. **Set up environment variables:**
   ```bash
   export OPENAI_API_KEY=your_openai_key_here
   export TAVILY_API_KEY=your_tavily_key_here
   ```

2. **Start a REPL:**
   ```bash
   cd examples/clj
   lein repl
   ```

3. **Load the example and run it:**
   ```clojure
   (require '[com.rpl.agent.react :as react])
   (react/run-agent)
   ```

## Running on a Local Rama Cluster

For a more production-like experience, you can run Agent-o-rama on a local Rama cluster. This gives you the full distributed capabilities of Rama while running on your local machine.

### Step 1: Download Rama

Download the latest Rama release from [https://redplanetlabs.com/download](https://redplanetlabs.com/download) and unpack it somewhere.

### Step 2: Set Up a Local Rama Cluster

Run a single node cluster by running these commands:

```bash
./rama devZookeeper &
./rama conductor &
./rama supervisor &
```

This isn't a production-worthy setup – see the docs on [setting up Rama clusters](https://redplanetlabs.com/docs/~/operating-rama.html#_setting_up_a_rama_cluster) for more details. There are also one-click deploys available [for AWS](https://github.com/redplanetlabs/rama-aws-deploy) and [for Azure](https://github.com/redplanetlabs/rama-azure-deploy).

### Step 3: Download Agent-o-rama Release

Download the latest Agent-o-rama release from [https://github.com/redplanetlabs/agent-o-rama/releases](https://github.com/redplanetlabs/agent-o-rama/releases) and unpack it.


### Step 4: Start the Agent-o-rama UI

Launch the Agent-o-rama frontend:

```bash
./aor --rama /path/to/rama-1.2.0
```

The UI will be available at [http://localhost:1974](http://localhost:1974).

### Step 5: Build and Deploy Your Module

Build an uberjar witn your module code. To do so for the examples:

```bash
# For Java projects  
cd examples/java
mvn clean package

# For Clojure projects
cd examples/clj
lein uberjar
```

Deploy the module to your local Rama cluster using the Rama CLI:

```bash
# Navigate to your Rama installation
cd /path/to/rama-1.2.0

# Deploy the module (Java uberjar)
./rama deploy \
  --action launch \
  --jar /path/to/your-module.jar \
  --module com.rpl.agent.react.ReActModule \
  --tasks 4 \
  --threads 2

# Deploy the module (Clojure uberjar)
./rama deploy \
  --action launch \
  --jar /path/to/your-module.jar \
  --module com.rpl.agent.react/ReActModule \
  --tasks 4 \
  --threads 2
```

## Next Steps

Now that you have a module deployed you can play with it in the UI by invoking the module from the web interface, exploring traces, building datasets, running experiments, and viewing analytics.

## Getting Help

- **Documentation**: Check out the comprehensive guides on the Agent-o-rama [wiki](https://github.com/redplanetlabs/agent-o-rama/wiki)
- **Examples**: Explore the `examples/` directory for more patterns
- **Community**: Join discussions on the [Rama mailing list](https://groups.google.com/u/1/g/rama-user) or #rama channel on [Clojurians](https://clojurians.slack.com/)

Happy building with Agent-o-rama!
