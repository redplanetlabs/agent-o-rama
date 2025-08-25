Of course. Based on your detailed notes, here is a UI sketch for the Datasets feature. This sketch outlines the key components, their layout, state management, and interactions, following the "command -> refetch" pattern you suggested and ignoring pagination and optimistic updates for now.

We'll design this as a two-level interface:
1.  **Datasets Index Page**: Lists all available datasets.
2.  **Dataset Detail Page**: A deep dive into a single dataset, its snapshots, and its examples.

---

### Page 1: Datasets Index

**Purpose:** Provide an overview of all created datasets, allow for creation of new ones, and facilitate quick searching and management.

#### UI Wireframe / Layout

```
/------------------------------------------------------------------\
|  Datasets                               + Create New Dataset   |
|  --------------------------------------------------------------  |
|                                                                  |
|  [ Search datasets... (input box) ]                              |
|                                                                  |
|  /--------------------------------------------------------------\ |
|  | Dataset Name 1          [ Edit ] [ Delete ]                  | |
|  | <Description of dataset 1>                                   | |
|  \--------------------------------------------------------------/ |
|                                                                  |
|  /--------------------------------------------------------------\ |
|  | My Awesome Dataset      [ Edit ] [ Delete ]                  | |
|  | The description is here...                                   | |
|  \--------------------------------------------------------------/ |
|                                                                  |
|  ... (more datasets)                                             |
|                                                                  |
\------------------------------------------------------------------/
```

---

### Component Breakdown: Datasets Index

#### 1. `DatasetsIndexPage` (Main Component)

*   **State:**
    *   `datasetsQuery`: Managed by the `use-sente-query` hook.
        *   `data`: The list of all datasets.
        *   `loading?`: True while fetching.
        *   `error`: Stores any fetch error.
        *   `refetch`: Function to re-trigger the query.
    *   `isCreateModalOpen`: `useState` boolean to control the "Create Dataset" modal.
    *   `searchText`: `useState` string for the search filter.

*   **UI / Layout:**
    *   A main header with the title "Datasets".
    *   A "Create New Dataset" button on the top right.
    *   A search input field below the header.
    *   Displays a loading spinner if `loading?` is true.
    *   Displays an error message if `error` is present.
    *   Renders a `DatasetList` component with the filtered list of datasets.

*   **Interactions/Events:**
    *   **On Mount:** The `use-sente-query` hook makes an initial call to `:api/get-datasets`.
    *   **Search Input Change:** Updates `searchText` state. The list rendered is filtered client-side based on this text.
    *   **Click "Create New Dataset"**: Sets `isCreateModalOpen` to true, showing the `CreateDatasetModal`.
    *   **On Dataset Create Success:** The modal will call the `refetch` function from the query hook to update the list.

#### 2. `CreateDatasetModal` Component

*   **State:**
    *   `name`, `description`, `inputSchema`, `outputSchema`: `useState` for each form field.
    *   `isSubmitting`: `useState` boolean, true during the Sente request.
    *   `formError`: `useState` string to display server-side validation errors.

*   **UI / Layout:**
    *   A modal overlay.
    *   A form with:
        *   Text input for "Name" (required).
        *   Textarea for "Description".
        *   Large textarea for "Input JSON Schema".
        *   Large textarea for "Output JSON Schema".
    *   "Create" and "Cancel" buttons. The "Create" button is disabled if `isSubmitting` is true or if `name` is empty.

*   **Interactions/Events:**
    *   **Click "Create"**:
        1.  Sets `isSubmitting` to `true` and clears `formError`.
        2.  Sends a `:api/create-dataset` event via Sente with the form data.
        3.  **On Reply**:
            *   **Success**: Close the modal, call the `onSuccess` callback (which will be the `refetch` function).
            *   **Failure**: Sets `isSubmitting` to `false`, and sets `formError` to the error message from the server (e.g., "Invalid JSON Schema: Unexpected token..."). The modal remains open for the user to correct their input.

---

### Page 2: Dataset Detail Page

**Purpose:** The main workspace for a single dataset. Manage its properties, snapshots, and examples.

#### UI Wireframe / Layout

```
/------------------------------------------------------------------\
|  <Breadcrumbs: Datasets / My Awesome Dataset>                    |
|  --------------------------------------------------------------  |
|                                                                  |
|  /-- Dataset Details -----------------------------------------\ |
|  |  Name: [ My Awesome Dataset (input) ]  [Save] [Cancel]      | |
|  |  Desc: [ The description... (textarea) ]                    | |
|  |  Input Schema: [ View Full Schema (button) ]                 | |
|  |  Output Schema: [ View Full Schema (button) ]                | |
|  \--------------------------------------------------------------/ |
|                                                                  |
|  /-- Snapshots ------------------------------------------------\ |
|  |  [ Snapshot: Latest (dropdown) v ]   + Create Snapshot      | |
|  \--------------------------------------------------------------/ |
|                                                                  |
|  /-- Examples -------------------------------------------------\ |
|  |                          + Add Example   + Upload JSONL      | |
|  |                                                              | |
|  | /----------------------------------------------------------\ | |
|  | | Input           | Output          | Tags      | Actions  | | |
|  | |-----------------|-----------------|-----------|----------| | |
|  | | {"query":...}   | "Some answ..."  | [tag1]    | [Edit][Del] | |
|  | \----------------------------------------------------------/ | |
|  \--------------------------------------------------------------/ |
\------------------------------------------------------------------/
```

---

### Component Breakdown: Dataset Detail Page

#### 1. `DatasetDetailPage` (Main Component)

*   **State:**
    *   `datasetPropsQuery`: `use-sente-query` to fetch the dataset's name, description, and schemas.
    *   `snapshotListQuery`: `use-sente-query` to fetch the list of available snapshots.
    *   `selectedSnapshot`: `useState` string, initialized to `"latest"`. This controls which set of examples is shown.
    *   `examplesQuery`: `use-sente-query` that fetches examples for the `selectedSnapshot`. Its `query-key` will include `selectedSnapshot` so it automatically refetches when the snapshot changes.

*   **UI / Layout:**
    *   Renders the `DatasetHeader` component with data from `datasetPropsQuery`.
    *   Renders the `SnapshotManager` component.
    *   Renders the `ExamplesPanel` component with data from `examplesQuery`.

#### 2. `DatasetHeader` Component

*   **State:**
    *   Receives dataset properties as props.
    *   `isEditing`: `useState` boolean.
    *   `editName`, `editDescription`, etc.: `useState` for edited values when `isEditing` is true.

*   **UI / Layout:**
    *   **View Mode**: Displays name and description. Shows "View Schema" buttons for schemas which open a read-only modal. An "Edit" button is visible.
    *   **Edit Mode**: Name becomes a text input, description a textarea. "Save" and "Cancel" buttons are visible.

*   **Interactions/Events:**
    *   **Click "Edit"**: Sets `isEditing` to true, populates edit state with current values.
    *   **Click "Save"**: Sends `:api/update-dataset-props` event, disables form. On success, sets `isEditing` to false and calls the `refetch` function passed in props. On error, displays it.
    *   **Click "Cancel"**: Sets `isEditing` to false, discarding changes.

#### 3. `SnapshotManager` Component

*   **State:**
    *   Receives `selectedSnapshot` and `setSelectedSnapshot` as props.
    *   Receives snapshot list from `snapshotListQuery`.
    *   Modal states for creating/deleting snapshots.

*   **UI / Layout:**
    *   A dropdown (`<select>`) listing `"Latest"` plus all named snapshots.
    *   A "Create Snapshot" button.
    *   A "Delete Snapshot" button, visible only when a named snapshot is selected.

*   **Interactions/Events:**
    *   **Change Dropdown**: Calls `setSelectedSnapshot`. This triggers the `examplesQuery` on the parent to refetch.
    *   **Click "Create Snapshot"**: Opens a modal asking for a name for the new snapshot. On submit, sends `:api/create-snapshot` event. On success, closes modal and refetches the snapshot list.
    *   **Click "Delete Snapshot"**: Opens a confirmation modal. On confirm, sends `:api/delete-snapshot`, refetches the list, and sets `selectedSnapshot` back to `"latest"`.

#### 4. `ExamplesPanel` and `ExampleListItem`

*   **UI / Layout:**
    *   A table to display the list of examples.
    *   **`Input`/`Output` Columns**: These are the most complex. They will display a truncated, single-line version of the data (e.g., `"{...}"` or a snippet of text). A "View" button or making the text clickable will open a modal showing the full, pretty-printed data.
    *   **`Tags` Column**: Display tags as small, rounded pills. Each pill has a small "x" button to remove it. A small "+" button in the cell to add a new tag.
    *   **`Actions` Column**: "Edit" and "Delete" buttons.

*   **Interactions/Events:**
    *   **Click Row/View Button**: Opens a read-only modal with the full input/output.
    *   **Click "Edit"**: Opens an `EditExampleModal`.
    *   **`EditExampleModal`**:
        *   Contains two large textareas for `input` and `output`.
        *   User edits the JSON text directly.
        *   On "Save", the modal sends an `:api/update-example` event with the *raw text*. The server is responsible for parsing it as JSON and running schema validation.
        *   If the server returns a validation error, it's displayed within the modal. On success, the modal closes, and the examples list is refetched.
    *   **Click "Add Example"**: Opens a similar modal to "Edit", but for creating a new example.
    *   **Click "Upload JSONL"**: Opens a modal with a file input. On file selection, it will send the file content to the server via an `:api/upload-jsonl` event. The modal will show a loading state and then display a summary of the upload (e.g., "Successfully uploaded 48 examples, 2 failed"). Refetches the example list on completion.

This UI structure directly addresses your notes, relies on the robust "command-refetch" pattern, and centralizes complex logic like schema validation on the server, keeping the UI focused on presentation and user interaction.
