# UI Data IDs

This document describes the `data-id` attributes used in the Agent-o-rama UI for testing and element identification.

## Agent Trace Display Panels

The agent trace display consists of three main panels, each with a unique `data-id`:

### Agent Graph Panel
- **data-id**: `agent-graph-panel`
- **Location**: Left side of the display (with right margin for info panel)
- **Description**: Contains the visual graph representation of agent nodes and their connections using ReactFlow
- **Contents**:
  - Interactive graph visualization
  - Node elements (custom and phantom types)
  - Edges showing node connections
  - MiniMap for navigation
  - Background grid
  - Controls for zoom/pan

### Agent Info Panel
- **data-id**: `agent-info-panel`
- **Location**: Fixed right side panel
- **Description**: Tabbed panel showing agent metadata and fork management
- **Contents**:
  - Tab navigation (Info and Fork tabs)
  - Tab-specific content (see below)

#### Info/Fork Tabs
The Agent Info Panel contains two tabs:

- **Info Tab**
  - **data-id**: `info-tab`
  - **Description**: Displays agent execution information and statistics
  - **Sub-components**:
    - `lineage-panel` - Parent/child fork relationships
    - `final-result-section` - Result value with success/failure badge and "Add to Dataset" button
    - `exceptions-panel` - List of exceptions with navigation to nodes
    - `overall-stats-section` - Execution metrics (time, retries, store ops, model calls, tokens)

- **Fork Tab**
  - **data-id**: `fork-tab`
  - **Description**: Shows and manages modified nodes for creating execution forks
  - **Sub-components** (when changes exist):
    - `fork-content` - Container for fork management UI
    - `changed-nodes-list` - List of modified nodes with previews
    - `fork-action-buttons` - Container for action buttons
      - `execute-fork-button` - Executes the fork with changes
      - `clear-fork-button` - Clears all changes
  - **Sub-components** (when no changes):
    - `fork-empty-state` - Message prompting user to select a node
  - **Note**: Only visible when not in live mode

### Node Invoke Details Panel
- **data-id**: `node-invoke-details-panel`
- **Location**: Bottom of the display, below the graph
- **Description**: Shows detailed information about a selected node's invocation
- **Contents**:
  - Node name and ID
  - Human input request interface (if applicable)
  - Result data
  - Exception details
  - Timing information (start, finish, duration)
  - Input arguments
  - Nested operations (store ops, model calls, agent calls)
  - Emits (child node invocations)
  - "Add node to Dataset" button

## Sub-components within Agent Info Panel

### Info Tab Sub-components

#### Lineage Panel
- **data-id**: `lineage-panel`
- **Description**: Shows fork ancestry and descendants
- **Contents**:
  - "Fork of" link to parent invocation (if this is a fork)
  - List of child fork invocations with expandable "show all" feature

#### Final Result Section
- **data-id**: `final-result-section`
- **Description**: Displays the agent's final result
- **Contents**:
  - Success/failure badge
  - Result value (expandable data viewer)
  - "Add to Dataset" button for the entire agent invocation

#### Exceptions Panel
- **data-id**: `exceptions-panel`
- **Description**: Lists all exceptions that occurred during execution
- **Contents**:
  - Exception count
  - For each exception:
    - Node name where it occurred
    - First line of exception message (clickable to expand)
    - "Go to Node" button (if node is loaded)

#### Overall Stats Section
- **data-id**: `overall-stats-section`
- **Description**: Displays execution metrics
- **Contents**:
  - Execution time (milliseconds)
  - Retry count (if any retries occurred)
  - Store operations (reads and writes)
  - Model calls count
  - Total tokens used

### Fork Tab Sub-components

#### Fork Content (when changes exist)
- **data-id**: `fork-content`
- **Description**: Container for all fork management UI
- **Child components**:
  - Changed nodes list
  - Action buttons

#### Changed Nodes List
- **data-id**: `changed-nodes-list`
- **Description**: Shows all nodes that have been modified for forking
- **Contents**: For each changed node:
  - Node name and ID
  - Warning indicator if node is overridden by upstream changes
  - Preview of new input (truncated if long)
  - "Remove" button to undo the change

#### Fork Action Buttons
- **data-id**: `fork-action-buttons`
- **Description**: Container for fork execution controls
- **Child buttons**:
  - `execute-fork-button` - Creates and executes a new fork with the changes
  - `clear-fork-button` - Removes all pending changes

#### Fork Empty State
- **data-id**: `fork-empty-state`
- **Description**: Shown when no nodes have been modified yet
- **Contents**: Instructional message

## Usage in Testing

These `data-id` attributes can be used in automated tests to reliably select elements:

```javascript
// Example: Select the agent graph panel
const graphPanel = document.querySelector('[data-id="agent-graph-panel"]');

// Example: Select the info tab
const infoTab = document.querySelector('[data-id="info-tab"]');

// Example: Check if node details panel is visible
const detailsPanel = document.querySelector('[data-id="node-invoke-details-panel"]');

// Example: Access sub-components
const lineagePanel = document.querySelector('[data-id="lineage-panel"]');
const exceptionsPanel = document.querySelector('[data-id="exceptions-panel"]');
const executeForkButton = document.querySelector('[data-id="execute-fork-button"]');
```

## Panel Visibility

- **Agent Graph Panel**: Always visible when graph data is available
- **Agent Info Panel**: Always visible (fixed position)
  - **Info Tab**: Always available
    - `lineage-panel`: Only visible when forks or parent exist
    - `final-result-section`: Only visible when result exists
    - `exceptions-panel`: Only visible when exceptions occurred
    - `overall-stats-section`: Always visible
  - **Fork Tab**: Only visible when not in live mode (`is-live` is false)
    - `fork-content` + children: Only visible when changes exist
    - `fork-empty-state`: Only visible when no changes exist
- **Node Invoke Details Panel**: Only visible when a node is selected
