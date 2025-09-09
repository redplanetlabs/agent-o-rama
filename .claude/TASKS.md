# Task States

[ ] - Planned/TODO
[x] - Completed
[!] - Blocked

Include REFINE to refine the spec

# Small

- [x] Add project specific claude code commands to work with the project
      task list.
      - There needs to be a "task:next-section" command to process each
        section of the task list, e.g "task:next-medium
	  - e.g. for "test:next-medium". the instruction might be:
        Find the next incomplete "Medium" item in the project task list


# Medium

- [X] add a glossary in @doc/glossary.md with terms that have project
      specific meanings that are either different, or more constrained,
      than their general ones.
	  - Use markdown, with each term being a second level heading,
        followed by its definition.
	  Include at least the following:
	  - Agent
	  - Agent Graph
	  - Agent Node
	  - Agent Invoke
	  - Agent Module
	  - Streaming Subscription
	  - Streaming Chunk
	  - Node Emit
	  - Agent result
	  - Router Node
	  - Scatter Gather Pattern
	  - Sub Agents
	  - Tools Sub Agent
	  - Aggregation

	  Analyse the project for their definitions, and for other terms to
      include.

	  Order alphabetically.

- [x] Update @doc/UserGuide.md. The guide was written a while back,
      before the glossary existed.

      - read @doc/glossary.md
      - Analyse the guide document in the context of the current project.
        Take a look at the git og for new features.

      - Plan how the structure of the document could be optimised
	  - Plan what content changes are needed to match the implementation
	  - Make the changes

- [x] for each feature in agent-o-rama, ensure that we have a simple
      example of that feature, in isolation from any other concept if
      possible.

	  - use the @doc/glossary.md @doc/UserGuide.md and the project source
        and think hard to create a list of features.

	  - Analyse the dependencies between features, and design what the
        set of examples should look like, listing the features to be
        used in each example.  Think hard about minimising the number of
        features in each example.

	  - stop, show this list to the user, and ask for feedback.

	  - One identified example at a time:
      	  - think about a good example that could use the just the
            features identified for that test.
		  - design the test
		  - implement test

      Put each example in simple-examples/clj/src/com/rpl/agent as a
      single namespace.  The namespace name must include the features that
	  are used.

- [x] add a langchain streaming example to the simple-examples suite.  It
      should go in the "Advanced Patterns" section of
      @simple-examples/clj/README.md.
	  - look at the other langchain simple examples for context
	  - it should just demonstrate how when using a streaming model, you
        just use aor/agent-stream to subscribe to the stream.
	  - keep the example minimal.
	  - remember to add a test (use a real streaming chat model)

- [x] move the `simple-examples` into the `examples` directory.  The
      code and tests should move to the com.rpl.agent.basic parent
      namespace, in the src, test trees respectively.

	  Update the clj examples readme to integrate @simple-examples/clj/README.md

	  check the deps.edn files too

- [x] Create a new user guide documentation in markdown format in
      @dev/user-guide/ (do not touch the existing @doc/userguide.md,
      which is meant for AI context) .  You can use multiple files under
      @dev/user-guide/.  This guide is intended for human users of
      agent-o-rama.

      There should be an Introduction, covering what agent-o-rama is.

	  Then a step by step explanation of the concepts in agent-o-rama.
      The concepts should first be explained independently of the
      implementation.  language specific explanations may follow.  The
      explanations should include code snippets if a appropriate.

		- Use the glossary for concepts, and analyse the codebase to find out how the
          concepts build on each other.

		- see the @examples/clj/src/com/rpl/agent/basic examples to see
		  how concepts build up.  Also @examples/clj/README.md.

	Use a direct, conversational, and empowering style:
  - Address the reader directly with "you/your"
  - Use active verbs and concrete metaphors
  - Keep sentences short and punchy
  - Focus on what the reader gains or achieves
  - Use colons to introduce lists or explanations
  - Avoid academic jargon or overly formal language
  - Be confident and declarative rather than tentative


- [ ] update the aggregation-agent example to show the return value of
      agg-start node being passed as last arg to agg-node

# Large
