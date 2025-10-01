(ns repl
  (:use
   [com.rpl.rama])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama.test :as rtest]
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server])
  (:import
   [dev.langchain4j.data.message
    SystemMessage
    UserMessage]))

(defn start-repl
  [ipc]
  (shadow.cljs.devtools.server/start!)
  (shadow/watch :frontend)
  (aor/start-ui ipc))

(defn stop-repl
  [ipc]
  (aor/stop-ui)
  (close! ipc))

(defn start-dev!
  [ipc]
  (shadow.cljs.devtools.server/start!)
  (shadow/watch :dev)
  (aor/start-ui ipc))

(comment
  (def ipc (open-cluster-manager-internal {"conductor.host" "localhost"}))
  (def ipc (rtest/create-ipc))

  (aor/stop-ui)

  (start-repl ipc)
  (stop-repl ipc)

  (start-dev! ipc)

  (shadow.cljs.devtools.server/stop!)
  (shadow.cljs.devtools.server/reload!)

  (require 'com.rpl.agent.basic.basic-agent)

  (rtest/launch-module!
   ipc
   com.rpl.agent.basic.basic-agent/BasicAgentModule
   {:tasks 1 :threads 1})

  (rtest/destroy-module!
   ipc
   (get-module-name com.rpl.agent.basic.basic-agent/BasicAgentModule))

  (require 'com.rpl.agent.basic.langchain4j-agent)
  (rtest/launch-module!
   ipc
   com.rpl.agent.basic.langchain4j-agent/LangChain4jAgentModule
   {:tasks 1 :threads 1})

  (rtest/destroy-module!
   ipc
   (get-module-name com.rpl.agent.basic.langchain4j-agent/LangChain4jAgentModule))

  (require 'com.rpl.agent.basic.tools-agent)
  (rtest/launch-module!
   ipc
   com.rpl.agent.basic.tools-agent/ToolsAgentModule
   {:tasks 1 :threads 1})
  (rtest/update-module!
   ipc
   com.rpl.agent.basic.tools-agent/ToolsAgentModule
   {:tasks 1 :threads 1})

  (rtest/destroy-module!
   ipc
   (get-module-name com.rpl.agent.basic.tools-agent/ToolsAgentModule))

  (require 'com.rpl.agent.react)
  (rtest/launch-module!
   ipc
   com.rpl.agent.react/ReActModule
   {:tasks 1 :threads 1})

  (rtest/destroy-module!
   ipc
   (get-module-name com.rpl.agent.react/ReActModule))

  (let [module-name   (get-module-name com.rpl.agent.react/ReActModule)
        agent-manager (aor/agent-manager ipc module-name)
        agent         (aor/agent-client agent-manager "ReActAgent")
        _ (print "Ask your question (agent has web search access): ")
        _ (flush)
        ^String user-input (read-line)
        result        (aor/agent-invoke
                       agent
                       [(SystemMessage/from
                         (format
                          "You are a helpful AI assistant. System time: %s"
                          (.toString (java.time.Instant/now))))
                        (UserMessage. user-input)])]
    (println result))


  (shadow/compile :frontend)
)
