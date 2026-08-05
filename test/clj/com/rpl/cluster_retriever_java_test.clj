(ns com.rpl.cluster-retriever-java-test
  (:require
   [clojure.test :refer [deftest is testing]])
  (:import
   [com.rpl.aortest
    ClusterRetrieverTest]))

(deftest cluster-retriever-java-test
  (testing "AgentNode.getClusterRetriever from Java"
    (is (ClusterRetrieverTest/runAllTests))))
