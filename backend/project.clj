(defproject backend "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.stuartsierra/component "1.1.0"]
                 [http-kit "2.8.0"]
                 [org.clojure/tools.namespace "1.5.0"] ;; TODO move to dev alias?
                 [metosin/reitit "0.8.0"]]
  :repl-options {:init-ns backend.core})
