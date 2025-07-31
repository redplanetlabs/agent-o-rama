(ns com.rpl.agent-o-rama.impl.langchain4j-trace
  (:use [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:import
   [dev.langchain4j.agent.tool
    ToolExecutionRequest]
   [dev.langchain4j.data.message
    AiMessage
    AudioContent
    ImageContent
    CustomMessage
    PdfFileContent
    SystemMessage
    TextContent
    ToolExecutionResultMessage
    UserMessage
    VideoContent]
   [dev.langchain4j.model.output
    FinishReason]))

(defprotocol MessageTrace
  (message->trace* [message]))

(defprotocol ContentTrace
  (content->trace* [content]))

(defn message->trace
  [message]
  (h/remove-empty-vals (message->trace* message)))

(defn content->trace
  [message]
  (h/remove-empty-vals (content->trace* message)))

(defn tool-request->trace
  [^ToolExecutionRequest tool-request]
  {"id"       (.id tool-request)
   "toolName" (.name tool-request)
   "args"     (.arguments tool-request)})

(defn messages->trace
  [messages]
  (mapv message->trace messages))

(extend-protocol MessageTrace
  AiMessage
  (message->trace* [message]
    {"type"         "ai"
     "text"         (.text message)
     "toolRequests" (mapv tool-request->trace
                          (.toolExecutionRequests message))})
  CustomMessage
  (message->trace* [message]
    {"type"  "custom"
     "attrs" (transform MAP-VALS str (into {} (.attributes message)))})
  SystemMessage
  (message->trace* [message]
    {"type" "system"
     "text" (.text message)})
  ToolExecutionResultMessage
  (message->trace* [message]
    {"type"     "toolResult"
     "id"       (.id message)
     "toolName" (.toolName message)
     "text"     (.text message)})
  UserMessage
  (message->trace* [message]
    {"type"     "user"
     "name"     (.name message)
     "contents" (mapv content->trace (.contents message))
    })
  Object
  (message->trace* [message]
    {"type" "unknown"
     "str"  (str message)}))

(extend-protocol ContentTrace
  TextContent
  (content->trace* [content]
    {"type" "text"
     "text" (.text content)})
  ImageContent
  (content->trace* [content]
    {"type"          "image"
     "mimeType"      (h/safe-> content .image .mimeType)
     "revisedPrompt" (h/safe-> content .image .revisedPrompt)
     "url"           (h/safe-> content .image .url)
     "dataLength"    (h/safe-> content .image .base64Data count)})
  AudioContent
  (content->trace* [content]
    {"type"       "audio"
     "mimeType"   (h/safe-> content .audio .mimeType)
     "url"        (h/safe-> content .audio .url)
     "dataLength" (h/safe-> content .audio .base64Data count)})
  VideoContent
  (content->trace* [content]
    {"type"       "video"
     "mimeType"   (h/safe-> content .video .mimeType)
     "url"        (h/safe-> content .video .url)
     "dataLength" (h/safe-> content .video .base64Data count)})
  PdfFileContent
  (content->trace* [content]
    {"type"       "pdf"
     "mimeType"   (h/safe-> content .pdfFile .mimeType)
     "url"        (h/safe-> content .pdfFile .url)
     "dataLength" (h/safe-> content .pdfFile .base64Data count)})
  Object
  (content->trace* [content]
    {"type" "unknown"}))

(defn finish-reason->trace
  [^FinishReason finish-reason]
  (condp = finish-reason
    FinishReason/CONTENT_FILTER "content_filter"
    FinishReason/LENGTH "length"
    FinishReason/OTHER "other"
    FinishReason/STOP "stop"
    FinishReason/TOOL_EXECUTION "tool_execution"
    "unknown"))
