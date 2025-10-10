(ns com.rpl.agent-o-rama.ui.components.conversation-test
  (:require
   [cljs.test :refer [deftest is testing]]
   [clojure.string :as str]
   [com.rpl.agent-o-rama.ui.components.conversation :as conversation]))

(deftest chat-message-test
  (testing "chat-message? detects LangChain4j messages"
    (testing "recognizes SystemMessage with string keys"
      (is (conversation/chat-message?
           {"_aor-type" "dev.langchain4j.data.message.SystemMessage"
            "text"      "You are a helpful assistant"})))

    (testing "recognizes UserMessage with string keys"
      (is (conversation/chat-message?
           {"_aor-type" "dev.langchain4j.data.message.UserMessage"
            "text"      "Hello"})))

    (testing "recognizes AiMessage with string keys"
      (is (conversation/chat-message?
           {"_aor-type" "dev.langchain4j.data.message.AiMessage"
            "text"      "Hi there!"})))
    
    (testing "recognizes SystemMessage with keyword keys"
      (is (conversation/chat-message?
           {:_aor-type "dev.langchain4j.data.message.SystemMessage"
            :text      "You are a helpful assistant"})))

    (testing "recognizes UserMessage with keyword keys"
      (is (conversation/chat-message?
           {:_aor-type "dev.langchain4j.data.message.UserMessage"
            :text      "Hello"})))

    (testing "recognizes AiMessage with keyword keys"
      (is (conversation/chat-message?
           {:_aor-type "dev.langchain4j.data.message.AiMessage"
            :text      "Hi there!"})))

    (testing "rejects non-message maps"
      (is (false? (conversation/chat-message? {"_aor-type" "some.other.Type"
                                               "text"      "not a message"}))))

    (testing "rejects maps without _aor-type"
      (is (false? (conversation/chat-message? {"text" "no type field"}))))

    (testing "rejects non-maps"
      (is (false? (conversation/chat-message? "not a map")))
      (is (false? (conversation/chat-message? nil))))))

(deftest extract-message-role-and-text-test
  (testing "extract-message-role-and-text extracts role and text correctly"
    (testing "extracts role from SystemMessage"
      (let [msg    {"_aor-type" "dev.langchain4j.data.message.SystemMessage"
                    "text"      "You are a helpful assistant"}
            result (conversation/extract-message-role-and-text msg)]
        (is (= "SystemMessage" (:role result)))
        (is (= "You are a helpful assistant" (:text result)))))

    (testing "extracts role from UserMessage"
      (let [msg    {"_aor-type" "dev.langchain4j.data.message.UserMessage"
                    "text"      "Hello"}
            result (conversation/extract-message-role-and-text msg)]
        (is (= "UserMessage" (:role result)))
        (is (= "Hello" (:text result)))))

    (testing "extracts role from AiMessage"
      (let [msg    {"_aor-type" "dev.langchain4j.data.message.AiMessage"
                    "text"      "Hi there"}
            result (conversation/extract-message-role-and-text msg)]
        (is (= "AiMessage" (:role result)))
        (is (= "Hi there" (:text result)))))

    (testing "extracts text from contents array with default separator (newline)"
      (let [msg    {"_aor-type" "dev.langchain4j.data.message.UserMessage"
                    "contents"  [{"text" "Part 1"} {"text" "Part 2"}]}
            result (conversation/extract-message-role-and-text msg)]
        (is (= "Part 1\nPart 2" (:text result)))))

    (testing "extracts text from contents array with custom separator (space)"
      (let [msg    {"_aor-type" "dev.langchain4j.data.message.UserMessage"
                    "contents"  [{"text" "Part 1"} {"text" "Part 2"}]}
            result (conversation/extract-message-role-and-text msg " ")]
        (is (= "Part 1 Part 2" (:text result)))))

    (testing "extracts text from contents single object"
      (let [msg    {"_aor-type" "dev.langchain4j.data.message.UserMessage"
                    "contents"  {"text" "Content text"}}
            result (conversation/extract-message-role-and-text msg)]
        (is (= "Content text" (:text result)))))

    (testing "filters out nil text from contents array"
      (let [msg    {"_aor-type" "dev.langchain4j.data.message.UserMessage"
                    "contents"  [{"text" "Part 1"} {} {"text" "Part 2"}]}
            result (conversation/extract-message-role-and-text msg " ")]
        (is (= "Part 1 Part 2" (:text result)))))

    (testing "handles missing text field"
      (let [msg    {"_aor-type" "dev.langchain4j.data.message.UserMessage"}
            result (conversation/extract-message-role-and-text msg)]
        (is (= "UserMessage" (:role result)))
        (is (nil? (:text result)))))

    (testing "handles missing _aor-type"
      (let [msg    {"text" "Some text"}
            result (conversation/extract-message-role-and-text msg)]
        (is (nil? (:role result)))
        (is (= "Some text" (:text result)))))))

(deftest extract-message-role-and-text-keyword-keys-test
  (testing "extract-message-role-and-text works with keyword keys"
    (testing "extracts role from SystemMessage with keyword keys"
      (let [msg    {:_aor-type "dev.langchain4j.data.message.SystemMessage"
                    :text      "You are a helpful assistant"}
            result (conversation/extract-message-role-and-text msg)]
        (is (= "SystemMessage" (:role result)))
        (is (= "You are a helpful assistant" (:text result)))))

    (testing "extracts role from UserMessage with keyword keys"
      (let [msg    {:_aor-type "dev.langchain4j.data.message.UserMessage"
                    :text      "Hello"}
            result (conversation/extract-message-role-and-text msg)]
        (is (= "UserMessage" (:role result)))
        (is (= "Hello" (:text result)))))

    (testing "extracts role from AiMessage with keyword keys"
      (let [msg    {:_aor-type "dev.langchain4j.data.message.AiMessage"
                    :text      "Hi there"}
            result (conversation/extract-message-role-and-text msg)]
        (is (= "AiMessage" (:role result)))
        (is (= "Hi there" (:text result)))))

    (testing "extracts text from contents array with keyword keys and default separator"
      (let [msg    {:_aor-type "dev.langchain4j.data.message.UserMessage"
                    :contents  [{:text "Part 1"} {:text "Part 2"}]}
            result (conversation/extract-message-role-and-text msg)]
        (is (= "Part 1\nPart 2" (:text result)))))

    (testing "extracts text from contents array with keyword keys and custom separator"
      (let [msg    {:_aor-type "dev.langchain4j.data.message.UserMessage"
                    :contents  [{:text "Part 1"} {:text "Part 2"}]}
            result (conversation/extract-message-role-and-text msg " ")]
        (is (= "Part 1 Part 2" (:text result)))))

    (testing "extracts text from contents single object with keyword keys"
      (let [msg    {:_aor-type "dev.langchain4j.data.message.UserMessage"
                    :contents  {:text "Content text"}}
            result (conversation/extract-message-role-and-text msg)]
        (is (= "Content text" (:text result)))))

    (testing "handles missing text field with keyword keys"
      (let [msg    {:_aor-type "dev.langchain4j.data.message.UserMessage"}
            result (conversation/extract-message-role-and-text msg)]
        (is (= "UserMessage" (:role result)))
        (is (nil? (:text result)))))))

(deftest conversation-test
  (testing "conversation? detects conversation vectors"
    (testing "recognizes valid conversation"
      (is (conversation/conversation?
           [{"_aor-type" "dev.langchain4j.data.message.SystemMessage"
             "text"      "You are helpful"}
            {"_aor-type" "dev.langchain4j.data.message.UserMessage"
             "text"      "Hi"}
            {"_aor-type" "dev.langchain4j.data.message.AiMessage"
             "text"      "Hello!"}])))

    (testing "recognizes single message as conversation"
      (is (conversation/conversation?
           [{"_aor-type" "dev.langchain4j.data.message.UserMessage"
             "text"      "Hi"}])))

    (testing "rejects empty vector"
      (is (not (conversation/conversation? []))))

    (testing "rejects vector with non-messages"
      (is (not (conversation/conversation?
                [{"_aor-type" "dev.langchain4j.data.message.UserMessage"
                  "text"      "Hi"}
                 {"some" "other data"}]))))

    (testing "rejects non-sequential data"
      (is (not (conversation/conversation?
                {"_aor-type" "dev.langchain4j.data.message.UserMessage"
                 "text"      "Hi"})))
      (is (not (conversation/conversation? "not a vector")))
      (is (not (conversation/conversation? nil))))))

(deftest conversation-integration-test
  (testing "conversation data in nested structures"
    (testing "should detect conversation in map value"
      (let [data {:messages [{"_aor-type" "dev.langchain4j.data.message.UserMessage"
                              "text"      "Hello"}
                             {"_aor-type" "dev.langchain4j.data.message.AiMessage"
                              "text"      "Hi there!"}]}]
        (is (true? (conversation/conversation? (:messages data))))))

    (testing "should not treat list of non-messages as conversation"
      (let [data {:items [{"name" "item1"} {"name" "item2"}]}]
        (is (false? (conversation/conversation? (:items data))))))))

(deftest conversation-preview-text-test
  (testing "conversation-preview-text generates correct preview"
    (testing "returns a vector of preview lines without truncation"
      (let [messages [{"_aor-type" "dev.langchain4j.data.message.SystemMessage"
                       "text"      "You are a helpful assistant"}
                      {"_aor-type" "dev.langchain4j.data.message.UserMessage"
                       "text" "Hello, can you help me with something today? I have a question."}
                      {"_aor-type" "dev.langchain4j.data.message.AiMessage"
                       "text"      "Of course! I'd be happy to help you."}]
            preview  (conversation/conversation-preview-text messages)]
        (is (vector? preview))
        (is (= 3 (count preview)))
        (is (= "SYSTEM: You are a helpful assistant" (nth preview 0)))
        (is (= "USER: Hello, can you help me with something today? I have a question." (nth preview 1)))
        (is (= "AI: Of course! I'd be happy to help you." (nth preview 2)))))

    (testing "indicates when there are more messages"
      (let [messages [{"_aor-type" "dev.langchain4j.data.message.SystemMessage"
                       "text"      "System"}
                      {"_aor-type" "dev.langchain4j.data.message.UserMessage"
                       "text"      "User 1"}
                      {"_aor-type" "dev.langchain4j.data.message.AiMessage"
                       "text"      "AI 1"}
                      {"_aor-type" "dev.langchain4j.data.message.UserMessage"
                       "text"      "User 2"}
                      {"_aor-type" "dev.langchain4j.data.message.AiMessage"
                       "text"      "AI 2"}]
            preview  (conversation/conversation-preview-text messages)]
        (is (= 4 (count preview)))
        (is (= "... (2 more messages)" (last preview)))))

    (testing "handles messages with contents array"
      (let [messages [{"_aor-type" "dev.langchain4j.data.message.UserMessage"
                       "contents"  [{"text" "Part 1"} {"text" "Part 2"}]}]
            preview  (conversation/conversation-preview-text messages)]
        (is (= ["USER: Part 1 Part 2"] preview))))

    (testing "handles empty text"
      (let [messages [{"_aor-type" "dev.langchain4j.data.message.UserMessage"
                       "text"      ""}]
            preview  (conversation/conversation-preview-text messages)]
        (is (= ["USER: (empty)"] preview))))

    (testing "shows exactly 3 messages without more indicator"
      (let [messages [{"_aor-type" "dev.langchain4j.data.message.UserMessage"
                       "text"      "1"}
                      {"_aor-type" "dev.langchain4j.data.message.AiMessage"
                       "text"      "2"}
                      {"_aor-type" "dev.langchain4j.data.message.UserMessage"
                       "text"      "3"}]
            preview  (conversation/conversation-preview-text messages)]
        (is (= 3 (count preview)))
        (is (not (str/includes? (str preview) "more messages")))))))

(deftest conversation-modal-scrollable-test
  ;; Test that ConversationModal renders with scrollable container
  (testing "ConversationModal has scrollable container"
    (let [messages [{"_aor-type" "dev.langchain4j.data.message.UserMessage"
                     "text"      "Message 1"}
                    {"_aor-type" "dev.langchain4j.data.message.AiMessage"
                     "text"      "Message 2"}]
          modal-element (conversation/ConversationModal {:messages messages})
          ;; Extract the className from the modal's root div
          modal-props (.-props modal-element)
          class-name (.-className modal-props)]
      (testing "has max-height constraint"
        (is (str/includes? class-name "max-h-[600px]")
            "Modal should have max-height to enable scrolling"))
      (testing "has vertical overflow scroll"
        (is (str/includes? class-name "overflow-y-auto")
            "Modal should have overflow-y-auto for vertical scrolling")))))
