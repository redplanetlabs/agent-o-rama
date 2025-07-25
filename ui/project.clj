(defproject com.rpl.agent-o-rama/ui "1.0.0-SNAPSHOT"
  :source-paths ["src/clj" "src/cljs" "resource"]

  :dependencies
  [[org.clojure/clojure "1.12.0"]
   [com.rpl/rama-helpers "0.10.0"]
   [org.apache.logging.log4j/log4j-slf4j18-impl "2.16.0"]
   
   [thheller/shadow-cljs "3.1.7"]
   [net.java.dev.jna/jna "5.17.0"]
   
   [ring/ring-core "1.14.1"]
   [ring/ring-jetty-adapter "1.14.1"]
   [com.rpl/specter "1.1.4"] ;; only cljs
   [com.pitch/uix.core "1.4.3"]
   [com.pitch/uix.dom "1.4.3"]
   [metosin/reitit "0.8.0"]
   [com.rpl/rama "0.0.6-SNAPSHOT"
    :exclusions
    [org.eclipse.jetty/jetty-http
     org.eclipse.jetty/jetty-io
     org.eclipse.jetty/jetty-util
     hawk]]
   [com.rpl/agent-o-rama "1.0.0-SNAPSHOT"]]

  :global-vars {*warn-on-reflection* true}
  :repositories
  [["releases"
    {:id  "maven-releases"
     :url "https://nexus.redplanetlabs.com/repository/maven-public-releases"}]])
