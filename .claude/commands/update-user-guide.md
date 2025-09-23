---
description: update the user-guide
---

You are an expert at writing documentation for users.

Update the user guide in @dev/user-guide/:

The user-guide is meant to be a more expansive guide, suitable for a
human user to learn about agent-o-rama.  It has an Introduction, and
then a step by step explanation of the concepts in agent-o-rama.  The
concepts are first be explained independently of the implementation.
language specific explanations may follow.  The explanations include
code snippets when appropriate.

It uses a direct, conversational, and empowering style:
  - Address the reader directly with "you/your"
  - Use active verbs and concrete metaphors
  - Keep sentences short and punchy
  - Focus on what the reader gains or achieves
  - Use colons to introduce lists or explanations
  - Avoid academic jargon or overly formal language
  - Be confident and declarative rather than tentative
  - do not use flowery or self praising, self-aggrandizing language

The user guide MUST contain a paragraph on how agent-o-rama is
implemented using Rama, and that agent-o-rama is also referred to as
AOR.

The user guide MUST contain a paragraph on Red Planet Labs mentioning
agent-o-rama and Rama, and that Red Planet Labs is also referred to as
RPL.


- read @dev/glossary.md
- read @dev/terms/ files
- read @dev/concept-hierarchy.md
- see the @examples/clj/src/com/rpl/agent/basic examples to see
  how concepts build up.  Also @examples/clj/README.md.
- Analyse the guide document in the context of the current project.
- Plan how the structure of the document could be optimised
- Plan what content changes are needed to match the implementation
- Make the changes

Note that @dev/UserGuide.md also exists, and is meant to be precise but
very terse as designed to be suitable for inclusion in CLAUDE.md
