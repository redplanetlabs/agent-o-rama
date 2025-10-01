(ns com.rpl.agent-o-rama.impl.multi-agg-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama.impl.multi-agg :as ma])
  (:import
   [com.rpl.agentorama MultiAgg$Impl]
   [com.rpl.rama.ops RamaFunction0 RamaFunction2]))

(deftest mk-multi-agg-test
  ;; Test that mk-multi-agg creates a functional MultiAgg instance
  ;; that supports both Java interop (.init, .on methods) and
  ;; internal protocol methods (internal-add-init!, internal-add-handler!)
  (testing "mk-multi-agg"
    (testing "creates instance with working internal protocol methods"
      (let [agg (ma/mk-multi-agg)]
        (ma/internal-add-init! agg (fn [] {:count 0}))
        (ma/internal-add-handler! agg "increment" (fn [state] (update state :count inc)))

        (let [state (ma/multi-agg-state agg)]
          (is (fn? (:init-fn state)))
          (is (= {:count 0} ((:init-fn state))))
          (is (contains? (:on-handlers state) "increment"))
          (is (= {:count 1} ((get (:on-handlers state) "increment") {:count 0}))))))

    (testing "supports Java interop .init method"
      (let [agg ^MultiAgg$Impl (ma/mk-multi-agg)
            init-fn (reify RamaFunction0
                      (invoke [_] {:total 0 :items []}))]
        (.init agg init-fn)

        (let [state (ma/multi-agg-state agg)
              result ((:init-fn state))]
          (is (= {:total 0 :items []} result)))))

    (testing ".init method returns the MultiAgg instance for chaining"
      (let [agg ^MultiAgg$Impl (ma/mk-multi-agg)
            init-fn (reify RamaFunction0
                      (invoke [_] {:sum 0}))
            handler-fn (reify RamaFunction2
                         (invoke [_ state val]
                           (update state :sum + val)))
            result (.on ^MultiAgg$Impl (.init agg init-fn) "add" handler-fn)]

        (is (identical? agg result))

        (let [state (ma/multi-agg-state agg)]
          (is (= {:sum 0} ((:init-fn state))))
          (is (contains? (:on-handlers state) "add"))
          (is (= {:sum 5} ((get (:on-handlers state) "add") {:sum 0} 5))))))

    (testing "prevents duplicate init registration"
      (let [agg ^MultiAgg$Impl (ma/mk-multi-agg)
            init-fn (reify RamaFunction0
                      (invoke [_] {}))]
        (.init agg init-fn)
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"MultiAgg already has init function specified"
             (.init agg init-fn)))))

    (testing "prevents duplicate handler registration"
      (let [agg ^MultiAgg$Impl (ma/mk-multi-agg)
            handler-fn (reify RamaFunction2
                         (invoke [_ state val] state))]
        (.on agg "test" handler-fn)
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"MultiAgg already has handler for given name"
             (.on agg "test" handler-fn)))))))
