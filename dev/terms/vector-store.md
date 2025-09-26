# Vector Store

## Definition
Vector databases or embedding stores that can be declared as agent objects for semantic search and retrieval-augmented generation (RAG) capabilities. Enables agents to perform similarity searches and semantic data retrieval.

## Architecture Role
Vector stores serve as specialized storage components for high-dimensional embeddings and semantic search operations. They bridge AI-powered agents with vector-based knowledge retrieval systems for advanced RAG workflows.

## Operations
Vector stores can be declared as agent objects, used for embedding storage and retrieval, similarity searches, and semantic queries. Operations include inserting embeddings, performing nearest neighbor searches, and retrieving semantically similar content.

## Invariants
Vector stores maintain high-dimensional embedding spaces with consistent dimensionality. Similarity operations preserve mathematical relationships between vectors. Store state is independent of individual agent execution contexts.

## Key Clojure API
- Primary functions: `declare-agent-object`, `declare-agent-object-builder`, `get-agent-object`
- Creation: Via `declare-agent-object-builder` with vector store configuration
- Access: `get-agent-object` within agent node functions for search operations

## Key Java API
- Primary functions: `AgentTopology.declareAgentObjectBuilder()`, `AgentNode.getAgentObject()`
- Creation: Builder pattern with vector store instances (e.g., Pinecone, Weaviate)
- Access: Through `AgentNode` interface for embedding and search operations

## Relationships
- Uses: [agent-objects], [agent-topology], [langchain4j-integration]
- Used by: [agent-node], [agent], [tool-calling]

## Dependency graph edges:
  agent-objects -> vector-store
  agent-topology -> vector-store
  langchain4j-integration -> vector-store
  vector-store -> agent-node
  vector-store -> agent
  vector-store -> tool-calling

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/rag_research.clj`
- Java: `examples/java/react/src/main/java/com/rpl/agent/react/ReActExample.java`