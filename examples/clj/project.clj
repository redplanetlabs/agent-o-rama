(defproject com.rpl/agent-o-rama-examples "1.0.0-SNAPSHOT"
  :jvm-opts ["-Xss6m"]
  ;; TODO: fix agent-o-rama version
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.rpl/agent-o-rama "0.9.0-SNAPSHOT"]
                 [dev.langchain4j/langchain4j-open-ai "1.2.0"]
                 [dev.langchain4j/langchain4j-web-search-engine-tavily
                  "1.2.0-beta8"]
                 [http-kit "2.8.0"]]
  :global-vars {*warn-on-reflection* true}
  :repositories
  [["releases"
    {:id  "maven-releases"
     :url "https://nexus.redplanetlabs.com/repository/maven-public-releases"}]]
  :profiles {:dev      {:resource-paths ["test/resources/"]
                        :jvm-opts       ["-Xss6m"]}
             :provided {:dependencies
                        ;; TODO: fix Rama version
                        [[com.rpl/rama "0.0.6-SNAPSHOT"]
                         [org.apache.logging.log4j/log4j-slf4j18-impl
                          "2.16.0"]]}}
)
