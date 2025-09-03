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

# Large
