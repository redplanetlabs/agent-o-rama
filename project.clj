(defproject com.rpl/agent-o-rama "1.0.0-SNAPSHOT"
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :test-paths ["test/clj"]
  :repositories [["releases" {:id "maven-releases"
                              :url "https://nexus.redplanetlabs.com/repository/maven-public-releases"}]]
  :profiles {:dev {:resource-paths ["test/resources/"]}
             :provided {:dependencies [[com.rpl/rama "1.0.0"]
                                       [org.apache.logging.log4j/log4j-slf4j18-impl "2.16.0"]]}
              :gen {:prep-tasks   []
                    :source-paths ["scripts"]
                    :dependencies [[comb "0.1.1"]
                                   [org.clojure/clojure "1.12.0"]]}}
  :plugins [[lein-exec "0.3.7"]]
  )
