(ns com.rpl.agent-o-rama.ui.components.conversation
  "Components and utilities for displaying LangChain4j conversation data"
  (:require
   [clojure.string :as str]
   [uix.core :refer [defui $]]
   [com.rpl.agent-o-rama.ui.state :as state]))

(defn get-flexible
  "Get a value from a map using either a string or keyword key"
  [m k]
  (or (get m k)
      (get m (keyword k))))

(defn chat-message?
  "Check if a map represents a LangChain4j chat message.
  Handles both string and keyword keys."
  [m]
  (.log js/console "chat-meesage?" (pr-str m))
  (boolean
   (and (map? m)
        (let [aor-type (get-flexible m "_aor-type")]
          (and aor-type
               (string? aor-type)
               (or (str/includes? aor-type "SystemMessage")
                   (str/includes? aor-type "UserMessage")
                   (str/includes? aor-type "AiMessage")))))))

(defn conversation?
  "Check if data is a conversation (vector of chat messages)"
  [data]
  (.log js/console "conversation?" (pr-str data))
  (and (sequential? data)
       (seq data)
       (every? chat-message? data)))

(defn extract-message-role-and-text
  "Extract role and text from a chat message.
  Returns a map with :role and :text keys.

  Optional separator parameter controls how contents arrays are
  joined (default: newline).  Handles both string and keyword keys."
  ([msg] (extract-message-role-and-text msg "\n"))
  ([msg separator]
   (let [msg-type (get-flexible msg "_aor-type")
         role (when msg-type (last (str/split msg-type #"\.")))
         text (or (get-flexible msg "text")
                  (when-let [contents (get-flexible msg "contents")]
                    (if (sequential? contents)
                      (->> contents
                           (map #(get-flexible % "text"))
                           (filter some?)
                           (str/join separator))
                      (get-flexible contents "text"))))]
     {:role role
      :text text})))

(defn conversation-preview-text
  "Generate preview text for a conversation.
  Returns a vector of preview lines (not truncated)."
  [messages]
  (let [preview-lines
        (->> messages
             (take 3)
             (mapv (fn [msg]
                     (let [{:keys [role text]} (extract-message-role-and-text
                                                msg " ")
                           label (case role
                                   "SystemMessage" "SYSTEM"
                                   "UserMessage" "USER"
                                   "AiMessage" "AI"
                                   (or role "MSG"))
                           display-text (if (str/blank? text)
                                          "(empty)"
                                          text)]
                       (str label ": " display-text)))))]
    (if (> (count messages) 3)
      (conj
       preview-lines
       (str "... (" (- (count messages) 3) " more messages)"))
      preview-lines)))

(defui ConversationModal [{:keys [messages]}]
  ($ :div {:className "p-6 space-y-4 max-h-[600px] overflow-y-auto"}
     (for [[idx msg] (map-indexed vector messages)]
       (let [{:keys [role text]} (extract-message-role-and-text msg)
             [bg-class text-class label]
             (case role
               "SystemMessage" ["bg-gray-100" "text-gray-700" "SYSTEM"]
               "UserMessage" ["bg-blue-50" "text-blue-900" "USER"]
               "AiMessage" ["bg-green-50" "text-green-900" "AI"]
               ["bg-gray-50" "text-gray-800" (or role "MESSAGE")])]
         ($ :div
            {:key idx
             :className (str bg-class " p-3 rounded-lg border border-gray-200")}
            ($ :div {:className "flex items-center gap-2 mb-2"}
               ($ :span
                  {:className (str "text-xs font-bold " text-class " uppercase tracking-wide")}
                  label))
            ($ :pre
               {:className (str "text-sm " text-class " whitespace-pre-wrap break-words font-sans")
                :style {:overflow-wrap "break-word"
                        :word-break "break-word"}}
               (or text "(no text)")))))))

(defui conversation-display
  "Display a compact preview of a conversation with click to expand.
  preview-text should be a vector of lines to display."
  [{:keys [messages color preview-text]
    :or {color "blue"}}]
  (let [num-messages (count messages)
        display-modal
        (fn [e]
          (.stopPropagation e)
          (state/dispatch
           [:modal/show :conversation
            {:title (str "Conversation (" num-messages " messages)")
             :component
             ($ ConversationModal
                {:title (str "Conversation (" num-messages " messages)")
                 :messages messages})}]))
        display-json-modal
        (fn [e]
          (.stopPropagation e)
          (let [json-str (js/JSON.stringify (clj->js messages) nil 2)]
            (state/dispatch
             [:modal/show :conversation-json
              {:title "Conversation (JSON)"
               :component
               ($ :div.p-6
                  ($ :pre.text-xs.bg-gray-50.p-3.rounded.border.overflow-y-auto.max-h-96.font-mono.whitespace-pre-wrap.break-words
                     json-str))}])))]
    ($ :div
       {:className
        (str "text-" color "-600 bg-" color "-50 border border-" color "-200 rounded p-2 min-w-0 max-w-full overflow-hidden")}
       ($ :div {:className "flex items-center justify-between text-xs font-semibold mb-1 min-w-0 gap-2"
                :style {:color "#6b7280"}}
          ($ :span {:className "truncate"} (str "💬 Conversation (" num-messages " messages)"))
          ($ :a {:className "text-blue-600 hover:text-blue-800 underline cursor-pointer flex-shrink-0"
                 :onClick display-json-modal
                 :title "View as JSON"}
             "as json"))
       ($ :div
          {:className
           (str "cursor-pointer hover:bg-" color "-100 px-1 py-0.5 rounded transition-colors min-w-0 overflow-hidden")
           :onClick display-modal
           :title "Click to view full conversation"}
          ($ :div {:className "text-xs font-sans space-y-0.5 min-w-0 max-w-full"}
             (for [[idx line] (map-indexed vector preview-text)]
               ($ :div {:key idx
                        :className "truncate min-w-0"}
                  line)))))))
