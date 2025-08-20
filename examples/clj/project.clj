(defproject com.rpl/agent-o-rama-examples "1.0.0-SNAPSHOT"
  :jvm-opts ["-Xss6m"]
  ;; TODO: fix agent-o-rama version
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.rpl/agent-o-rama "0.9.0-SNAPSHOT"]
                 [dev.langchain4j/langchain4j-open-ai "1.2.0"]
                 [dev.langchain4j/langchain4j-web-search-engine-tavily
                  "1.3.0-beta9"]
                 [http-kit "2.8.0"]]
  :test-paths ["test" "../../test/clj"]
  :global-vars {*warn-on-reflection* true}
  :repositories
  [["releases"
    {:id  "maven-releases"
     :url "https://nexus.redplanetlabs.com/repository/maven-public-releases"}]]
  :profiles {:dev      {:resource-paths ["test/resources/"]
                        :src-paths      ["src" "../../test/clj"]
                        :jvm-opts       ["-Xss6m"
                                         "-Xms6g"
                                         "-Xmx6g"
                                         "-XX:+UseG1GC"
                                         "-XX:MetaspaceSize=500000000"
                                         ;; this gives us stack traces directly
                                         ;; in output instead of an edn
                                         ;; file in tmp, which will be lost on
                                         ;; CI
                                         "-Dclojure.main.report=stderr"]
                        :dependencies
                        ;; TODO: fix Rama version
                        [[com.rpl/rama "0.0.6-SNAPSHOT"]
                         [org.apache.logging.log4j/log4j-slf4j18-impl
                          "2.16.0"]]}
             :provided {:dependencies
                        ;; TODO: fix Rama version
                        [[com.rpl/rama "0.0.6-SNAPSHOT"]
                         [org.apache.logging.log4j/log4j-slf4j18-impl
                          "2.16.0"]]}}
)
