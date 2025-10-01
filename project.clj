(defproject com.rpl/agent-o-rama "0.9.0-SNAPSHOT"
  :source-paths ["src/clj" "src/cljs" "resource"]
  :java-source-paths ["src/java" "test/cljs" "src/cljs"]
  :test-paths ["test/clj"]
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
  :dependencies [[com.rpl/rama-helpers "0.10.0"]
                 [com.github.f4b6a3/uuid-creator "6.1.1"]
                 [dev.langchain4j/langchain4j
                  "1.4.0"
                  :exclusions
                  [org.slf4j/slf4j-api]]
                 [com.networknt/json-schema-validator
                  "1.5.8"
                  :exclusions
                  [org.slf4j/slf4j-api
                   com.fasterxml.jackson.core/jackson-databind
                   com.fasterxml.jackson.dataformat/jackson-dataformat-yaml
                   com.ethlo.time/itu]]
                 [com.jayway.jsonpath/json-path
                  "2.9.0"
                  :exclusions
                  [org.slf4j/slf4j-api]]
                 [expound "0.9.0"]

                 ;; ui dependencies
                 [ring/ring-core "1.9.5"]
                 [http-kit "2.8.0"]
                 [ring/ring-codec "1.2.0"]
                 [com.taoensso/sente "1.20.0"]
                 [ring/ring-defaults "0.4.0"]
                 [com.cognitect/transit-clj "1.0.333"]
                 [com.cognitect/transit-cljs "0.8.280"]]
  :global-vars {*warn-on-reflection* true}
  :repositories
  [["releases"
    {:id  "maven-releases"
     :url "https://nexus.redplanetlabs.com/repository/maven-public-releases"}]]
  :profiles {:dev      {:resource-paths    ["test/resources/"]
                        :source-paths      ["src/clj"
                                            "src/cljs"
                                            "resource"
                                            "examples/clj/src"]
                        :test-paths        ["test/clj"
                                            "examples/clj/test"]
                        :java-source-paths ["src/java" "test/java"]
                        :dependencies
                        [[org.clojure/clojure "1.12.0"]
                         [meander/epsilon "0.0.650"]
                         [dev.langchain4j/langchain4j-open-ai "1.4.0"]
                         [dev.langchain4j/langchain4j-web-search-engine-tavily
                          "1.3.0-beta9"]]}
             :provided {:dependencies
                        ;; TODO: fix Rama version
                        [[com.rpl/rama "0.0.6-SNAPSHOT"]
                         [org.apache.logging.log4j/log4j-slf4j18-impl
                          "2.16.0"]]}
             :gen      {:prep-tasks   []
                        :source-paths ["scripts"]
                        :dependencies [[comb "0.1.1"]
                                       [org.clojure/clojure "1.12.0"]]}
             :ui       {:source-paths ["test/cljs"]
                        :dependencies [[com.rpl/specter "1.1.4"] ;; only cljs
                                       [com.pitch/uix.core "1.4.3"]
                                       [com.pitch/uix.dom "1.4.3"]
                                       [thheller/shadow-cljs "3.1.7"]
                                       [metosin/reitit-frontend "0.7.2"]
                                       [metosin/reitit-malli "0.7.2"]
                                       [net.java.dev.jna/jna "5.17.0"] ;; to fix
                                       ;; dynlink
                                       ;; error
                                       ;; on arm
                                       ;; macs
                                       [org.clojure/clojure "1.12.0"]]}}
  :codox {:source-paths ["src/clj"]
          :metadata     {:doc/format :markdown}
          :output-path  "target/doc"
          :namespaces   [com.rpl.agent-o-rama
                         com.rpl.agent-o-rama.langchain4j
                         com.rpl.agent-o-rama.store
                         com.rpl.agent-o-rama.tools]}
  :plugins [[lein-exec "0.3.7"]
            [lein-codox "0.10.8"]
            [lein-doo "0.1.11"]])
