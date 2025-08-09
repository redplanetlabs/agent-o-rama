You've hit the nail on the head. You are at the most critical—and most rewarding—part of the frontend refactor. The `run-research-agent` loop is the perfect example to illustrate why this architectural change is so powerful.

You are **absolutely correct**: that synchronous `loop` with `read-line` is a command-line pattern. For a rich web UI, that entire control loop needs to be moved to the server and orchestrated via asynchronous, real-time messages (Sente) to the frontend.

Let's break down exactly how to complete the refactor for `invocation_graph.cljs` and handle all your new feature requirements using the event-driven architecture.

---

### Part 1: The New Backend "Session" Loop

The core idea is to create a server-side process that "manages" an agent's run on behalf of a UI client. This manager will be stateful, remembering the `AgentInvoke` object and the client's connection `uid`.

**How it replaces the `run-research-agent` loop:**

1.  **Initiation (Client -> Server):** The user clicks a "Run" button in the UI. This dispatches an event that sends a Sente message: `(sente/request! [:agent/start-run {:module-id "..." :agent-name "..." :args ["", {:topic "..."}] }])`.

2.  **Server-Side Handler:** In `src/clj/com/rpl/agent_o_rama/impl/ui/sente.clj`, you'll create a handler. This handler does **not** loop and block. Instead, it starts the process and creates a "session."

    ```clojure
    ;; A server-side atom to track active runs for each client
    (defonce client-sessions (atom {})) ; {uid -> {invoke-id -> AgentInvoke}}

    (defn continue-agent-run [uid invoke agent-client]
      (future ; Run in a background thread to not block the Sente router
        (try
          (let [step (aor/agent-next-step agent-client invoke)]
            (if (instance? HumanInputRequest step)
              ;; 1. Human input is needed. Tell the client.
              (chsk-send! uid [:agent/human-input-required
                               {:invoke-id (str (:task-id agent-invoke) "-" (:agent-id agent-invoke))
                                :prompt (:prompt step)
                                ;; We need to send the request object back so the client can return it
                                :request-object step}])
              ;; 2. The agent is complete. Tell the client.
              (chsk-send! uid [:agent/run-complete
                               {:invoke-id (str (:task-id agent-invoke) "-" (:agent-id agent-invoke))
                                :result (:result step)}])
              ;; Clean up the session
              (swap! client-sessions update-in [uid] dissoc invoke-id)))
          (catch Exception e
            ;; 3. The agent failed. Tell the client.
            (chsk-send! uid [:agent/run-failed
                             {:invoke-id (str (:task-id agent-invoke) "-" (:agent-id agent-invoke))
                              :error (.getMessage e)}])
            (swap! client-sessions update-in [uid] dissoc invoke-id)))))

    (defmethod -event-msg-handler :agent/start-run
      [{:as ev-msg :keys [?data uid]}]
      (let [{:keys [module-id agent-name args]} ?data
            client (ui/get-client module-id agent-name)
            invoke (apply aor/agent-initiate client args)
            invoke-id (str (:task-id invoke) "-" (:agent-id invoke))]
        ;; Store the invoke object in our session manager
        (swap! client-sessions assoc-in [uid invoke-id] invoke)
        ;; Acknowledge the run has started
        (chsk-send! uid [:agent/run-started {:invoke-id invoke-id}])
        ;; Take the first step
        (continue-agent-run uid invoke client)))
    ```

3.  **Handling User Input (Client -> Server):** When the UI gets a `:agent/human-input-required` event, it displays a modal. When the user submits, it sends a new Sente event back.

    ```clojure
    ;; On the frontend (in some component)
    (sente/request! [:agent/provide-human-input {:invoke-id "..."
                                               :response "User's text here"
                                               :request-object human-request-obj-from-server}])
    ```

4.  **Resuming the Loop (Server):** The server handles this event, retrieves the session, provides the input, and calls `continue-agent-run` again.

    ```clojure
    ;; In src/clj/com/rpl/agent_o_rama/impl/ui/sente.clj
    (defmethod -event-msg-handler :agent/provide-human-input
      [{:as ev-msg :keys [?data uid]}]
      (let [{:keys [invoke-id response request-object]} ?data
            invoke (get-in @client-sessions [uid invoke-id])
            {:keys [module-id agent-name]} (parse-invoke-id-or-whatever) ; Get client from invoke-id
            client (ui/get-client module-id agent-name)]
        (when invoke
          (aor/provide-human-input client request-object response)
          (continue-agent-run uid invoke client)))) ; <<< THE LOOP CONTINUES!
    ```

This architecture perfectly translates the synchronous loop into an asynchronous, message-based flow suitable for a web application.

---

### Part 2: Refactoring `invocation_graph.cljs` and Adding New Features

Here’s the concrete plan to finish the refactor and integrate all your required features.

**1. Remove the `useState` Hooks:**

Your first step is to migrate all that local state into `app-db`.

*   **Identify the State:** In `invocation_graph.cljs`, you have state like: `selected-node`, `loading-nodes`, `graph-data`, `summary-data`, `changed-nodes`, `forking-mode?`, etc.
*   **Create a Home in `app-db`:** In `state.cljs`, define a slice for the currently viewed invocation, for example:

    ```clojure
    ;; In state.cljs initial-db
    :current-invocation {:graph-data nil
                         :summary nil
                         :loading-nodes #{}
                         :selected-node-id nil
                         :forking-mode? false
                         :changed-nodes {}}
    ```

*   **Refactor Components:**
    *   **Replace `useState`:**
        *   `const [selected-node, set-selected-node] = useState(nil)`
        *   **Becomes:** `const selected-node-id = state/use-sub([:current-invocation :selected-node-id])`
    *   **Replace `setState`:**
        *   `set-selected-node(newNode)`
        *   **Becomes:** `(state/dispatch [:db/set-value [:current-invocation :selected-node-id] (:id newNode)])`
    *   **Replace Mutations:**
        *   `set-loading-nodes #(conj % missing-node-id)`
        *   **Becomes:** `(state/dispatch [:db/update-value [:current-invocation :loading-nodes] #(conj % missing-node-id)])`

**2. Handle Streaming of New Nodes**

This is now a server-push problem. As your agent runs, the backend needs to notify the UI about newly completed nodes. The ideal way to do this is by having the agent-run-manager (our new loop) also subscribe to the PState trace.

*   **Backend:** The `continue-agent-run` function can start a stream of the agent trace. As new nodes appear, it pushes them over Sente.
    *   `chsk-send!(uid, [:graph/node-update {:invoke-id ..., :node-id ..., :node-data ...}])`
*   **Frontend `sente.cljs`:** Add a handler that dispatches a state update.
    ```clojure
    (defmethod -event-msg-handler :graph/node-update [{:keys [?data]}]
      (state/dispatch [:invocation/graph-node-merge ?data]))
    ```
*   **Frontend `state.cljs`:** Add the event handler to merge the new data.
    ```clojure
    (reg-event :invocation/graph-node-merge
      (fn [db {:keys [node-id node-data]}]
        [[:current-invocation :graph-data node-id] (constantly node-data)]))
    ```

The `invocation_graph` component is already subscribed to `:graph-data`. **It will update automatically, with no additional work.**

**3. Handle Streaming of Tokens**

This follows the exact pattern outlined in my previous answer and integrates perfectly here. When a node starts streaming:

*   **Backend:** Your session manager gets the stream chunks and forwards them via Sente.
    *   `chsk-send!(uid, [:stream/update {:stream-key ..., :chunks [...]}])`
*   **Frontend:** The `:stream/update` handler updates `app-db`. Your UI component that displays node details (or the node itself) would subscribe to `[:streams stream-key :chunks]` and render the live text.

**4. Handle Human-in-the-Loop Events**

This is now the most elegant part of the system.

*   **Backend:** As shown in Part 1, the session manager sends a `:agent/human-input-required` event and pauses.
*   **Frontend `sente.cljs`:**
    ```clojure
    (defmethod -event-msg-handler :agent/human-input-required [{:keys [?data]}]
      (state/dispatch [:ui/show-human-input-modal ?data]))
    ```
*   **Frontend `state.cljs`:**
    ```clojure
    ;; In initial-db
    :ui {:human-input-request nil ; Will hold the prompt and request object
         ...}

    (reg-event :ui/show-human-input-modal
      (fn [db request-data]
        [[:ui :human-input-request] (constantly request-data)]))

    (reg-event :ui/submit-human-input
      (fn [db user-response]
        (let [request-data (get-in db [:ui :human-input-request])]
          ;; Send the response back to the server
          (sente/request! [:agent/provide-human-input {:response user-response
                                                       :request-object (:request-object request-data)
                                                       :invoke-id (:invoke-id request-data)}])
          ;; Clear the modal
          [[:ui :human-input-request] (constantly nil)])))
    ```
*   **UI Component:** A modal component subscribes to `[:ui :human-input-request]`. It becomes visible whenever this state is not `nil`, and its "Submit" button dispatches `[:ui/submit-human-input]`.

The beauty of this is that the UI becomes incredibly simple. It doesn't need to know anything about the agent's control flow. It just reacts to state changes: "show this modal," "append these tokens," "add this node." All the complexity is managed by the state transitions defined in your event handlers.
