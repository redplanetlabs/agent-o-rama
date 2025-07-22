(defproject examples "1.0.0-SNAPSHOT"
  :dependencies [[com.rpl/rama-helpers "0.10.0"]
                 [org.clojure/clojure "1.12.0"]
                 [com.rpl/agent-o-rama "1.0.0-SNAPSHOT"]]
  :repositories
  [["releases" {:id "maven-releases"
                :url "https://nexus.redplanetlabs.com/repository/maven-public-releases"}]]

  :profiles {:dev {:resource-paths ["test/resources/"]}
             :provided {:dependencies [[com.rpl/rama "0.0.6-SNAPSHOT"]]}}
  )
