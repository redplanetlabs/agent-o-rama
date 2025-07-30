(defproject com.rpl/agent-o-rama "0.9.0-SNAPSHOT"
  :source-paths ["src/clj"]
  :java-source-paths ["src/java" "test/java"]
  :test-paths ["test/clj"]
  :jvm-opts ["-Xss6m"]
  :dependencies [[com.rpl/rama-helpers "0.10.0"]
                 [dev.langchain4j/langchain4j
                  "1.2.0"
                  :exclusions
                  [org.slf4j/slf4j-api]]]
  :global-vars {*warn-on-reflection* true}
  :repositories
  [["releases"
    {:id  "maven-releases"
     :url "https://nexus.redplanetlabs.com/repository/maven-public-releases"}]]
  :profiles {:dev      {:resource-paths ["test/resources/"]
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
                                       [org.clojure/clojure "1.12.0"]]}}
  :plugins [[lein-exec "0.3.7"]]
)
