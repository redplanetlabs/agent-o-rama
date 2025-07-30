(defproject com.rpl/agent-o-rama "1.0.0-SNAPSHOT"
  :source-paths ["src/clj"]
  :java-source-paths ["src/java" "test/java"]
  :test-paths ["test/clj"]
  :jvm-opts ["-Xss6m"]
  :dependencies [[com.rpl/rama-helpers "0.10.0"]]
  :global-vars {*warn-on-reflection* true}
  :repositories
  [["releases"
    {:id  "maven-releases"
     :url "https://nexus.redplanetlabs.com/repository/maven-public-releases"}]]
  :profiles {:dev      {:resource-paths ["test/resources/"]
                        :dependencies   [[meander/epsilon "0.0.650"]]}
             :provided {:dependencies
                        [[com.rpl/rama "0.0.6-SNAPSHOT"]
                         [org.apache.logging.log4j/log4j-slf4j18-impl
                          "2.16.0"]]}
             :gen      {:prep-tasks   []
                        :source-paths ["scripts"]
                        :dependencies [[comb "0.1.1"]
                                       [org.clojure/clojure "1.12.0"]]}
             :ui       {:dependencies
                        [[thheller/shadow-cljs "3.1.7"]
                         [ring/ring-core "1.14.1"]
                         [ring/ring-jetty-adapter "1.14.1"]
                         [com.rpl/specter "1.1.4"] ;; only cljs
                         [com.pitch/uix.core "1.4.3"]
                         [com.pitch/uix.dom "1.4.3"]
                         [metosin/reitit "0.8.0"]]}}
  :plugins [[lein-exec "0.3.7"]]
)
