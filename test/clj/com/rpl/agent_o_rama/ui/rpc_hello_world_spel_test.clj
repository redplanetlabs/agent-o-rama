 (ns com.rpl.agent-o-rama.ui.rpc-hello-world-spel-test
   (:require
    [clojure.test :refer [deftest is testing]]
    [com.blockether.spel.core :as spel]
    [com.blockether.spel.locator :as locator]
    [com.blockether.spel.page :as page]
    [com.rpl.agent-o-rama.ui.etaoin-test-helpers :as eth]
    [com.rpl.agent-o-rama.ui.rpc-hello-world-test-agent
     :refer [RpcHelloWorldTestAgentModule]]))

 (def system (volatile! nil))

 (deftest rpc-hello-world-spel-test
   (eth/with-ui-system [system RpcHelloWorldTestAgentModule]
    (let [env @system
          hello-url (str "http://localhost:" (:port env)
                         "/agents/"
                         (java.net.URLEncoder/encode ^String (:module-name env) "UTF-8")
                         "/rpc-hello")]
     (spel/with-testing-page {:browser-type :chromium
                              :channel "chrome"
                              :headless true}
         [pg]
         (page/navigate pg hello-url)
         (page/wait-for-load-state pg :networkidle)
        (let [message-loc (page/get-by-test-id pg "rpc-hello-message")
              module-id-loc (page/get-by-test-id pg "rpc-hello-module")
              rpc-id-loc (page/get-by-test-id pg "rpc-hello-rpc-id")]
           (locator/wait-for message-loc {:state "visible" :timeout 10000})
           (testing "renders RPC hello world message"
             (is (= "Hello RPC world"
                    (locator/text-content message-loc))))
           (testing "renders module id from the RPC payload"
             (is (= (:module-name env)
                    (locator/text-content module-id-loc))))
           (testing "renders canonical RPC id"
             (is (= ":com.rpl.agent-o-rama.impl.ui.rpc.hello-world/index!!"
                    (locator/text-content rpc-id-loc)))))))))
