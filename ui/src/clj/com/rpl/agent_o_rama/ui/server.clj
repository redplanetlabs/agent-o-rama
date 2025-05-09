(ns com.rpl.agent-o-rama.ui.server)

(defn handler [req]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body "Hello World!"})
