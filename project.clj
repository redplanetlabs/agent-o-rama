(defproject com.rpl/agent-o-rama "0.9.0-SNAPSHOT"
  :source-paths ["src/clj" "src/cljs" "resource"]
  :java-source-paths ["src/java"]
  :test-paths ["test/clj"]
  :jvm-opts ["-Xss6m"]
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.rpl/rama-helpers "0.10.0"]
                 [dev.langchain4j/langchain4j
                  "1.2.0"
                  :exclusions
                  [org.slf4j/slf4j-api]]

                 ;; ui dependencies
                 [ring/ring-core "1.9.5"]
                 [ring/ring-jetty-adapter "1.8.2"]
                 [ring/ring-codec "1.2.0"]
                 [metosin/reitit "0.7.2"]]
  :global-vars {*warn-on-reflection* true}
  :repositories
  [["releases"
    {:id  "maven-releases"
     :url "https://nexus.redplanetlabs.com/repository/maven-public-releases"}]]
  :profiles {:dev      {:resource-paths    ["test/resources/"]
                        :java-source-paths ["src/java" "test/java"]
                        :dependencies
                        [[meander/epsilon "0.0.650"]
                         [dev.langchain4j/langchain4j-open-ai "1.2.0"]]}
             :provided {:dependencies
                        ;; TODO: fix Rama version
                        [[com.rpl/rama "0.0.6-SNAPSHOT"]
                         [org.apache.logging.log4j/log4j-slf4j18-impl
                          "2.16.0"]]}
             :gen      {:prep-tasks   []
                        :source-paths ["scripts"]
                        :dependencies [[comb "0.1.1"]
                                       [org.clojure/clojure "1.12.0"]]}
             :ui       {:dependencies [
                                       [com.rpl/specter "1.1.4"] ;; only cljs
                                       [com.pitch/uix.core "1.4.3"]
                                       [com.pitch/uix.dom "1.4.3"]
                                       [thheller/shadow-cljs "3.1.7"]
                                       [net.java.dev.jna/jna "5.17.0"] ;; to fix
                                                                       ;; dynlink
                                                                       ;; error
                                                                       ;; on arm
                                                                       ;; macs
                                       [org.clojure/clojure "1.12.0"]
                                      ]}}
  :plugins [[lein-exec "0.3.7"]])
