(defproject com.rpl/agent-o-rama-examples "1.0.0-SNAPSHOT"
  :dependencies [[com.rpl/agent-o-rama "0.6.0"]
                 [dev.langchain4j/langchain4j-open-ai "1.4.0"]
                 [dev.langchain4j/langchain4j-web-search-engine-tavily
                  "1.4.0-beta10"]]
  :jvm-opts ["-Xss6m"
             "-Xms6g"
             "-Xmx6g"
             "-XX:+UseG1GC"
             "-XX:MetaspaceSize=500000000"
             ;; Ensure stack traces are not elided
             "-XX:-OmitStackTraceInFastThrow"
             ;; this gives us stack traces directly in output instead of an edn
             ;; file in tmp, which will be lost on CI
             "-Dclojure.main.report=stderr"
             ;; allow termination of threads
             "-Djdk.attach.allowAttachSelf"]
  :src-paths ["src"]
  :test-paths ["test"]
  :global-vars {*warn-on-reflection* true}
  :repositories
  [["releases"
    {:id  "maven-releases"
     :url "https://nexus.redplanetlabs.com/repository/maven-public-releases"}]]
  :profiles {:dev      {:resource-paths ["test/resources/"]
                        :src-paths      ["src" "test"]
                        :dependencies   [[meander/epsilon "0.0.650"]]
                        :jvm-opts       ["-Xss6m"]}
             :provided {:dependencies
                        [[com.rpl/rama "1.2.0"]
                         [org.clojure/clojure "1.12.2"]
                         [org.apache.logging.log4j/log4j-slf4j2-impl
                          "2.25.1"]]}
             :test     {:resource-paths ["test/resources/"]
                        :src-paths      ["src" "test"]
                        :dependencies   [[meander/epsilon "0.0.650"]]}}
)
