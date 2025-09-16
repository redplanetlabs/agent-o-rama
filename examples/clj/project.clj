(defproject com.rpl/agent-o-rama-examples "1.0.0-SNAPSHOT"
  ;; TODO: fix agent-o-rama version
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.rpl/agent-o-rama "0.9.0-SNAPSHOT"]
                 [com.rpl/rama "0.0.6-SNAPSHOT"]
                 [dev.langchain4j/langchain4j-open-ai "1.4.0"]
                 [dev.langchain4j/langchain4j-web-search-engine-tavily
                  "1.4.0-beta10"]
                 [org.apache.logging.log4j/log4j-slf4j18-impl "2.16.0"]]
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
  :profiles {:dev  {:resource-paths ["test/resources/"]
                    :src-paths      ["src" "test"]
                    :dependencies   [[meander/epsilon "0.0.650"]]}
             :test {:resource-paths ["test/resources/"]
                    :src-paths      ["src" "test"]
                    :dependencies   [[meander/epsilon "0.0.650"]]}}
)
