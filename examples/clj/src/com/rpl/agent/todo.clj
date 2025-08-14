(ns com.rpl.agent.todo
  "An agent to manage TODO items.
  Uses long term memory for accumulating a profile and the TODO items."
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.path :as path]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j])
  (:import
   [com.rpl.agentorama
    AgentComplete]
   [dev.langchain4j.data.message SystemMessage
    UserMessage]
   [dev.langchain4j.model.openai OpenAiChatModel
    OpenAiStreamingChatModel]))

(defn under->dash [s]
  (str/replace s \_  \-))

(defn dash->under [s]
  (str/replace s \-  \_))

(def MAPPER (j/object-mapper
             {:decode-key-fn (comp keyword under->dash)
              :encode-key-fn (comp dash->under str)}))

;; Chatbot instruction for choosing what to update and what tools to call
(def MODEL-SYSTEM-MESSAGE
  "You are a helpful chatbot.

You are designed to be a companion to a user, helping them keep track of their
ToDo list.

You have a long term memory which keeps track of three things:
1. The user's profile (general information about them)
2. The user's ToDo list
3. General instructions for updating the ToDo list

Here is the current User Profile (may be empty if no information has been
collected yet):
<user_profile>
%s
</user_profile>

Here is the current ToDo List (may be empty if no tasks have been added yet):
<todos>
%s
</todos>

Here are the current user-specified preferences for updating the ToDo list (may
be empty if no preferences have been specified yet):
<instructions>
%s
</instructions>

Here are your instructions for reasoning about the user's messages:

1. Reason carefully about the user's messages as presented below.

2. Decide whether any of the your long-term memory should be updated:
- If personal information was provided about the user, update the user's profile
  by calling UpdateMemory tool with type `profile`
- If tasks are mentioned, update the ToDo list by calling UpdateMemory tool with
  type `todo`
- If the user has specified preferences for how to update the ToDo list, update
  the instructions by calling UpdateMemory tool with type `instructions`

3. Tell the user that you have updated your memory, if appropriate:
- Do not tell the user you have updated the user's profile
- Tell the user them when you update the todo list
- Do not tell the user that you have updated instructions

4. Err on the side of updating the todo list. No need to ask for explicit
permission.

5. Respond naturally to user user after a tool call was made to save memories,
or if no tool call was made.")

(def UPDATE-PROFILE
  "Reflect on the following interaction.

Extract a profile of the user.

Create the expected response format based solely on the information available in
the chat. If you don't have information to put in specific fields, or you want
to leave them with their current values, then leave them out of the returned
object.

<current_profile>
%s
</current_profile>")

(def UPDATE-TODOS
  "Reflect on the following interaction.

Extract todos for the user.

Create the expected response format based solely on the information available in
the chat. If you don't have information to put in specific fields, leave them
blank.

Do not remove todo items unless explicitly requested to do so. Combine existing
todo items with any new todo items.

<current_todos>
%s
</current_todos>")

(def CREATE-INSTRUCTIONS
  "Reflect on the following interaction.

Based on this interaction, update your instructions for how to update ToDo
list items. Use any feedback from the user to update how they like to have
items added, etc.

Your current instructions are:

<current_instructions>
%s
</current_instructions>")

(def ^:private Profile
  (lj/object
   {:description "The profile of a user."}
   {"name"        (lj/string "The user's name")
    "job"         (lj/string "The user's job")
    "connections" (lj/array
                   "Personal connection of the user, such as family members, friends, or coworkers"
                   (lj/string "A personal connection"))
    "interests"   (lj/array
                   "Interests that the user has"
                   (lj/string "An interest that the user has"))}))

(def ^:private ToDoFields
  {"task"      (lj/string "The task to be completed.")
   "deadline"  (lj/string
                "When the task needs to be completed by (if applicable)")
   "solutions" (lj/array
                "List of specific, actionable solutions (e.g., specific ideas, service providers, or concrete options relevant to completing the task)",
                (lj/string "A specific, actionable solution"))
   "status"    (lj/enum
                "Current status of the task"
                ["not started" "in progress" "done" "archived"])})

(def ^:private ToDo
  (lj/object
   {:description "A ToDo item"
    :required    ["task"]}
   ToDoFields))

(def ^:pricate Instruction
  (lj/object
   {:description "Instruction"}
   {"instructions" (lj/string "instructions")}))

(defn create-todo-tool
  [agent-node {:keys [user-id]} todo]
  (let [store (aor/get-store agent-node "$$todos")
        uuid  (str (random-uuid))]
    (store/pstate-transform!
     [(path/keypath user-id)
      (path/keypath uuid)
      (path/termval todo)]
     store
     user-id)
    "created"))

(defn update-todo-tool
  [agent-node {:keys [user-id]} arguments]
  (let [store (aor/get-store agent-node "$$todos")
        uuid  (arguments "uuid")
        todo  (arguments "todo")]
    (store/pstate-transform!
     [(path/keypath user-id)
      (path/keypath uuid)
      (path/term #(merge % todo))]
     store
     user-id)
    "updated"))

(defn delete-todo-tool
  [agent-node {:keys [user-id]} arguments]
  (let [store (aor/get-store agent-node "$$todos")
        uuid  (arguments "uuid")]
    (store/pstate-transform!
     [(path/keypath user-id)
      (path/keypath uuid)
      path/NONE]
     store
     user-id)
    "deleted"))

(def TODO-TOOLS
  [(tools/tool-info
    (tools/tool-specification
     "CreateToDo"
     ToDo
     "Creates a todo using info from chat messages")
    create-todo-tool
    {:include-context? true})
   (tools/tool-info
    (tools/tool-specification
     "UpdateToDo"
     (lj/object
      {:description "Instruction to update an existing ToDo item"}
      {"uuid" (lj/string "The uuid identifying the ToDo item to update")
       "todo" ToDo})
     "Updates an existing todo using from chat messages")
    update-todo-tool
    {:include-context? true})
   (tools/tool-info
    (tools/tool-specification
     "DeleteToDo"
     (lj/object
      {"uuid" (lj/string "The uuid identifying the ToDo item to delete")})
     "Updates profile, todo or instruction memory with info from chat messages")
    delete-todo-tool
    {:include-context? true})])

(defn update-profile
  [agent-node messages {:keys [user-id]}]
  (let [chat-model    (aor/get-agent-object agent-node "openai-non-streaming")
        store         (aor/get-store agent-node "$$profiles")
        profile       (store/get store user-id)
        system-msg    (format UPDATE-PROFILE profile)
        chat-messages (into
                       [(SystemMessage. system-msg)]
                       messages)
        chat-options  {:response-format
                       (lc4j/json-response-format "Profile" Profile)}
        response      (lc4j/chat
                       chat-model
                       (lc4j/chat-request
                        chat-messages
                        chat-options))
        new-profile   (j/read-value (.text (.aiMessage response)))]
    (store/update! store user-id #(merge % new-profile)))
  "updated")

(defn update-todo
  [agent-node messages {:keys [user-id] :as config}]
  (let [chat-model    (aor/get-agent-object agent-node "openai-non-streaming")
        todo-tools    (aor/agent-client agent-node "todo-tools")
        store         (aor/get-store agent-node "$$todos")
        todos         (into
                       {}
                       (store/pstate-select
                        [(path/keypath user-id) path/ALL]
                        store
                        user-id))
        system-msg    (format
                       UPDATE-TODOS
                       (j/write-value-as-string todos MAPPER))
        chat-messages (->
                       [(SystemMessage. system-msg)]
                       (into messages)
                       #_(conj
                          (UserMessage.
                           "Please update the ToDos based on the conversation")))
        chat-options  {:tools TODO-TOOLS}
        response      (lc4j/chat
                       chat-model
                       (lc4j/chat-request chat-messages chat-options))
        ai-message    (.aiMessage response)
        tool-calls    (not-empty (vec (.toolExecutionRequests ai-message)))]
    (when tool-calls
      (aor/agent-invoke todo-tools tool-calls config))
    "updated"))


(defn update-instruction
  [agent-node messages {:keys [user-id]}]
  (let [chat-model      (aor/get-agent-object agent-node "openai-non-streaming")
        store           (aor/get-store agent-node "$$instructions")
        instruction     (store/get store user-id)
        system-msg      (format
                         CREATE-INSTRUCTIONS
                         instruction)
        chat-messages   (->
                         [(SystemMessage. system-msg)]
                         (into messages)
                         (conj
                          (UserMessage.
                           "Please update the instructions based on the conversation")))
        chat-options    {:response-format
                         (lc4j/json-response-format "Instruction" Instruction)}
        response        (lc4j/chat
                         chat-model
                         (lc4j/chat-request
                          chat-messages
                          chat-options))
        new-instruction (j/read-value (.text (.aiMessage response)))]

    (store/put! store user-id new-instruction)
    "updated"))

(defn update-tool
  [agent-node config arguments]
  (let [update-type (get arguments "update_type")
        messages    (:messages config)]
    (case update-type
      "profile"      (update-profile agent-node messages config)
      "todo"         (update-todo agent-node messages config)
      "instructions" (update-instruction agent-node messages config))))

(def TOOLS
  [(tools/tool-info
    (tools/tool-specification
     "UpdateMemory"
     (lj/object
      {:description "Updates persistent memory for info from chat messages"
       :required    ["update_type"]}
      {"update_type" (lj/enum ["profile" "todo" "instructions"])})
     "Updates profile, todo or instruction memory with info from chat messages")
    update-tool
    {:include-context? true})])

(aor/defagentmodule TodoModule
  [topology]

  (aor/declare-agent-object
   topology
   "openai-api-key"
   (System/getenv "OPENAI_API_KEY"))

  (aor/declare-agent-object-builder
   topology
   "openai"
   (fn [setup]
     (-> (OpenAiStreamingChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         (.temperature 0.0)
         (.logRequests true)
         (.logResponses true)
         .build)))

  (aor/declare-agent-object-builder
   topology
   "openai-non-streaming"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         (.temperature 0.0)
         (.logRequests true)
         (.logResponses true)
         .build)))

  (aor/declare-document-store
   topology
   "$$profiles"
   Long
   "name" String
   "job" String
   "connections" java.util.List
   "interests" java.util.List)

  (aor/declare-pstate-store
   topology
   "$$todos"
   {Long (rama/map-schema String java.util.Map {:subindex? true})})

  (aor/declare-key-value-store topology "$$instructions" Long Object)

  (->
   topology
   (aor/new-agent "ToDoAgent")

   (aor/node
    "maestro"
    "maestro"
    (fn maestro-node [agent-node messages {:keys [user-id] :as config}]
      (let [chat-model         (aor/get-agent-object
                                agent-node
                                "openai-non-streaming")
            tools              (aor/agent-client agent-node "tools")
            profiles-store     (aor/get-store agent-node "$$profiles")
            todos-store        (aor/get-store agent-node "$$todos")
            instructions-store (aor/get-store agent-node "$$instructions")
            profile            (store/get profiles-store user-id)
            todos              (into
                                {}
                                (store/pstate-select
                                 [(path/keypath user-id) path/ALL]
                                 todos-store
                                 user-id))
            instructions       (store/get instructions-store user-id)
            system-msg         (format
                                MODEL-SYSTEM-MESSAGE
                                profile
                                (j/write-value-as-string todos MAPPER)
                                instructions)
            chat-messages      (into
                                [(SystemMessage. system-msg)]
                                messages)
            chat-options       {:tools TOOLS}
            response           (lc4j/chat
                                chat-model
                                (lc4j/chat-request
                                 chat-messages
                                 chat-options))
            ai-message         (.aiMessage response)
            tool-calls         (not-empty
                                (vec
                                 (.toolExecutionRequests ai-message)))
            next-messages      (conj messages ai-message)]
        (if tool-calls
          (let [tool-results  (aor/agent-invoke
                               tools
                               tool-calls
                               (assoc config :messages messages))
                next-messages (into next-messages tool-results)]
            (aor/emit! agent-node
                       "maestro"
                       next-messages
                       config))
          (aor/result! agent-node {:messages next-messages}))))))

  (tools/new-tools-agent topology "tools" TOOLS)
  (tools/new-tools-agent topology "todo-tools" TODO-TOOLS))

(def inputs
  ["My name is Lance. I live in SF with my wife. I have a 1 year old daughter."
   "My wife asked me to book swim lessons for the baby."
   "When creating or updating ToDo items, include specific local businesses / vendors."
   "I need to fix the jammed electric Yale lock on the door."
   "For the swim lessons, I need to get that done by end of November."
   "Need to call back City Toyota to schedule car service."
   "I have 30 minutes, what tasks can I get done?"
   "Yes, give me some options to call for swim lessons."
   ])

(defn run-agent
  []
  (with-open [ipc (rtest/create-ipc)
              _   (aor/start-ui ipc)]
    (rtest/launch-module! ipc TodoModule {:tasks 4 :threads 2})
    (let [module-name   (rama/get-module-name TodoModule)
          agent-manager (aor/agent-manager ipc module-name)
          user-id       0]
      (with-open [agent (aor/agent-client agent-manager "ToDoAgent")]
        (try
          (loop [inputs inputs]
            (when inputs
              (let [agent-invoke (aor/agent-initiate
                                  agent
                                  [(UserMessage. (first inputs))]
                                  {:user-id user-id})
                    step         (aor/agent-next-step agent agent-invoke)
                    result       (:result step)]
                (assert (instance? AgentComplete step))
                (doseq [msg (:messages result)]
                  (println msg))
                (recur (next inputs)))))
          (catch Exception e
            (prn :exeception e))))
      (let [profile-pstate (rama/foreign-pstate ipc module-name "$$profiles")]
        (println
         :profile
         (rama/foreign-select-one (path/keypath user-id) profile-pstate))
        (assert
         (rama/foreign-select-one (path/keypath user-id) profile-pstate)
         "Has a profile")))))
