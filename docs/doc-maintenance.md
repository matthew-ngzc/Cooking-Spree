# Documentation maintenance

Purpose: keep this documentation accurate without creating a parallel, bloated copy of the codebase.

## Before handoff

1. Identify the changed contract: user-visible behavior, architecture, build/test command, persistence schema, map/asset convention, or workflow.
2. Update the one documentation page that owns that contract; keep facts in a single source here.
3. Add or update the smallest useful diagram when the contract involves components, runtime interactions, states, ownership, data shape, or another relationship that is easier to scan visually than as prose.
4. Update `docs/README.md` only if navigation or a document purpose changed.
5. Remove or revise conflicting statements instead of adding a dated workaround.
6. In the handoff, name the documentation file changed, the diagrams added or updated (or why none was useful), and the verification performed.

## Diagram policy

Use Mermaid in Markdown so diagrams remain reviewable, searchable, and versioned beside the text they explain. A diagram complements the prose; the owning page remains the single source of truth, and both must change together when their contract changes.

Choose the diagram that makes the relationship easiest to understand:

| Subject | Preferred diagram |
| --- | --- |
| Activities, modules, workers, external services, and ownership | Architecture/component flowchart |
| Calls, callbacks, lifecycle events, synchronization, and failure handling | Sequence diagram |
| Pause, session, game, pot, order, or UI lifecycle | State diagram |
| Relational tables and keys | Entity-relationship diagram |
| Firestore or another NoSQL store | Document-model diagram showing collection/document paths, nested maps or subcollections, ownership, references, and cardinality |
| A branching user or system process | Flowchart |

Add a diagram whenever one of these relationships is introduced or materially changed, and whenever it can replace a difficult paragraph or make an idea immediately scannable. Keep it close to the relevant explanation, label current behavior separately from proposed behavior, and show only details needed for the documented contract. Prefer several focused diagrams over one unreadable system map. Do not include secrets, tokens, emails, real UIDs, or copied Firebase configuration.

Verify that Mermaid renders on GitHub and that every node, transition, field group, and label agrees with the source and surrounding prose. A documentation or PR update is incomplete when it changes a depicted contract without updating its diagram. If no diagram would improve understanding, say why in the PR evidence instead of adding decoration.

## Ownership map

| Change | Update |
| --- | --- |
| Gameplay rule, recipes, scoring, interaction, tutorial claim | `gameplay.md` |
| Activity wiring, engine, map, assets, threads, structural seam | `architecture.md` |
| Build, SDK/toolchain, commands, test suite, manual test procedure | `development.md` |
| Saves, preferences, sign-in, Firebase, cloud schema | `persistence.md` |
| Product priority, accepted limitation, planned feature | `roadmap.md` |
| Agent workflow or repository guardrail | `AGENTS.md` |

Write links to source files rather than duplicating long APIs or configurations. If a claim cannot be kept current, delete it or replace it with a pointer to its source of truth.
