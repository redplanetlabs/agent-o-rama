# ClojureScript UI Architecture Summary

## Overview

The agent-o-rama UI is a ClojureScript single-page application (SPA)
built with UIx (React wrapper) and Reitit (routing). It provides a web
interface for monitoring, debugging, and managing agents built with the
agent-o-rama framework.

## Architecture Patterns

### State Management

**Centralized State with Specter**
- Single source of truth: `state/app-db` atom
- Event-driven state updates using a custom event system (`reg-event`,
  `dispatch`)
- Event handlers return Specter paths (navigators) for efficient nested
  state updates
- Subscriptions (`use-sub`) provide reactive component updates via
  Specter path selectors
- Eliminates prop drilling by allowing any component to subscribe to
  specific state paths

**State Structure**
- `:route` - Current route data from Reitit
- `:invocations-data` - Keyed by invoke-id, stores graph data, summary, and metadata
- `:queries` - React Query-style cache for Sente requests
- `:forms` - Form state keyed by form-id
- `:ui` - UI-specific state (selected nodes, modals, forking mode, etc.)
- `:sente` - WebSocket connection state

### Communication with Backend

**Sente (WebSocket + Ajax)**
- Bidirectional client-server communication over WebSocket
- Transit packer for efficient serialization
- Request-response pattern via `sente/request!`
- Automatic reconnection handling
- Connection state tracked in app-db

**Query Layer**
- Custom `use-sente-query` hook provides React Query-like functionality
- Features: loading states, error handling, caching, invalidation, polling
- Page visibility aware polling (pauses when tab hidden)
- Query invalidation by pattern matching on query keys

### UI Components

**UIx (React Wrapper)**
- Hiccup-style JSX via `$` macro
- Component definition with `defui` (for components) and `defhook` (for hooks)
- Full React hook support: `use-state`, `use-effect`, `use-callback`, etc.
- React Portal support for modals

**Component Organization**
- Feature-based namespace structure under `com.rpl.agent-o-rama.ui.*`
- Reusable components in `common.cljs`
- Feature-specific components in dedicated namespaces (agents, datasets, experiments, etc.)

### Routing

**Reitit Frontend Router**
- Nested route definitions with view stacks
- Route data includes `:views` vector specifying which components to render
- Path parameters automatically URL-decoded via event handler
- `ViewStack` component renders all views in the stack as siblings
- Breadcrumb generation based on route hierarchy

**Navigation Pattern**
- Declarative routing with `reitit.frontend.easy/href` for links
- Programmatic navigation via `reitit.frontend.easy/push-state`
- Sidebar navigation context-aware (shows module/agent-specific links)
- URL encoding/decoding utilities in `common.cljs`

### Form Management

**Declarative Form System**
- Form specifications registered via `reg-form`
- Field-level and form-level validation
- Support for wizard-style multi-step forms
- Automatic validation on field changes
- Integration with modal system
- Forms stored in app-db under `:forms` by form-id

**Form Features**
- Field validation with validator functions
- JSON schema validation for structured data
- Specter path-based field access for nested forms
- Loading and error states
- Declarative submit handlers with Sente integration

### Modal System

**Portal-Based Modals**
- React Portal rendering to body
- Global modal component tracks active modal in app-db
- Modal content can be either component or form-based
- Form modals integrate with form system for wizard support
- Escape key handling for dismissal
- Modal state: `{:active modal-type :data {...}}`

### Graph Visualization

**React Flow Integration**
- Two graph types: static agent graphs (ELK layout) and dynamic invocation graphs (Dagre layout)
- Custom node rendering with status indicators
- Real-time updates via WebSocket streaming
- Interactive features: node selection, detail panels, navigation

**Invocation Graph Features**
- Live streaming of node execution
- Human-in-the-loop (HITL) input forms
- Exception tracking and display
- Forking mode for modifying node inputs
- Downstream node detection for affected nodes
- Add to dataset functionality from graph nodes

**Agent Graph (Static)**
- ELK.js layered layout algorithm
- Custom edge rendering with B-spline curves
- Node type visualization (start nodes, aggregators, regular nodes)
- Minimap and zoom controls

### Specialized Features

**Dataset Management**
- Example CRUD operations
- Snapshot system for versioning (read-only historical views)
- Tagging system for examples
- Import/export in JSONL format
- Search and filtering
- Bulk operations (select multiple, delete, tag)

**Experiments**
- Regular experiments (single agent configuration)
- Comparative experiments (multiple configurations)
- Evaluator integration
- Results visualization and analysis

**Evaluators**
- Multiple evaluator types: LLM-as-judge, string match, JSON field match
- Summary evaluators for multiple examples
- Try evaluator on single example or dataset
- Evaluator configuration and management

**Trace Analytics**
- Add agent invocations or individual nodes to datasets
- Transform traced data into training examples
- Input/output extraction from execution graphs

## Conventions

### Naming
- Event IDs as keywords: `:namespace/action` (e.g., `:invocations/load-graph`)
- Component names in kebab-case, converted to PascalCase components
- Private helpers prefixed with `-` or in `let` blocks
- Specter paths as vectors of keywords

### Code Organization
- Core infrastructure in `state.cljs`, `events.cljs`, `sente.cljs`, `common.cljs`
- Feature namespaces under `ui.*` (agents, datasets, experiments, etc.)
- Forms defined in separate `-forms` namespaces
- Shared utilities in `common.cljs`

### State Updates
- Always use `dispatch` for state changes
- Event handlers return Specter paths, not direct state
- Use `use-sub` for reactive state access in components
- Generic events: `:db/set-value`, `:db/update-value`, `:db/set-values`

### Async Operations
- Use `sente/request!` for backend communication
- Loading states tracked in query cache or form state
- Error handling via query error states or form error fields
- Optimistic updates not used (rely on query refetching)

### Styling
- Tailwind CSS utility classes
- `common/cn` helper for conditional class names
- Color schemes: blue (primary), green (success), red (error), yellow (warning), orange (modified)
- Responsive design not emphasized (desktop-first)

### Testing
- No test files found in src/cljs (tests may be elsewhere or minimal)

## Key Technical Decisions

1. **Specter for State Management**: Efficient nested updates without mutation
2. **Sente over REST**: Real-time capabilities needed for live invocation tracking
3. **Custom Query Layer**: React Query features adapted for Sente/WebSocket
4. **View Stacks**: Multiple components render for nested routes (no outlets)
5. **Global Modal System**: Single modal instance prevents multiple overlays
6. **ELK vs Dagre**: ELK for static graphs (better layouts), Dagre for dynamic (simpler integration)
7. **Transit Packer**: Efficient serialization for ClojureScript data structures
8. **Portal-Based Modals**: Clean separation from app tree, handles stacking context
9. **Declarative Forms**: Reduces boilerplate, enforces validation patterns

## Dependencies

- **uix.core**: React wrapper with hiccup-style syntax
- **reitit**: Frontend routing
- **taoensso.sente**: WebSocket communication
- **com.rpl.specter**: Efficient nested data structure manipulation
- **@xyflow/react**: Graph visualization (React Flow)
- **@dagrejs/dagre**: Graph layout (invocation graphs)
- **elkjs**: Graph layout (agent graphs)
- **@heroicons/react**: Icon library
- **cognitect.transit**: Data serialization

## File Structure

```
src/cljs/com/rpl/agent_o_rama/
├── ui.cljs                           # Main app, routing, navigation
├── ui/
│   ├── state.cljs                    # State management, event system
│   ├── events.cljs                   # Orchestration events, graph processing
│   ├── sente.cljs                    # WebSocket setup, request helpers
│   ├── common.cljs                   # Shared utilities, reusable components
│   ├── queries.cljs                  # Query layer (use-sente-query hook)
│   ├── forms.cljs                    # Form system, validation, modal integration
│   ├── agents.cljs                   # Agent list, detail, invocations
│   ├── agent_graph.cljs              # Static agent graph visualization (ELK)
│   ├── invocation_page.cljs          # Invocation detail page wrapper
│   ├── invocation_graph_view.cljs    # Dynamic invocation graph (Dagre)
│   ├── datasets.cljs                 # Dataset CRUD, examples, snapshots
│   ├── datasets_forms.cljs           # Dataset-specific forms
│   ├── datasets/
│   │   ├── snapshot_selector.cljs    # Snapshot dropdown component
│   │   └── add_from_trace.cljs       # Add traced data to datasets
│   ├── evaluators.cljs               # Evaluator management and execution
│   ├── experiments/
│   │   ├── index.cljs                # Experiment list
│   │   ├── forms.cljs                # Experiment forms
│   │   ├── regular_detail.cljs       # Regular experiment detail
│   │   ├── comparative.cljs          # Comparative experiment list
│   │   ├── comparative_detail.cljs   # Comparative experiment detail
│   │   ├── evaluators.cljs           # Experiment evaluator config
│   │   └── events.cljs               # Experiment-specific events
│   ├── config_page.cljs              # Agent configuration UI
│   └── stats.cljs                    # Statistics and metrics display
```

## Performance Considerations

- Specter provides efficient nested state updates
- Subscriptions only trigger re-renders when specific state paths change
- Query caching reduces redundant backend requests
- Page visibility API pauses polling when tab hidden
- Virtual scrolling not implemented (may need for large datasets)
- Graph rendering handled by React Flow (canvas-based, performant)

## Future Improvement Areas

- Test coverage (no test files found)
- Error boundary components
- Optimistic updates for better UX
- Virtual scrolling for large lists
- Offline support and state persistence
- Accessibility (ARIA labels, keyboard navigation)
- Mobile responsiveness
- Code splitting for large features
