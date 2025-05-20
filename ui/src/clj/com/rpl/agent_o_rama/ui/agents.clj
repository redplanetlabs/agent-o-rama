(ns com.rpl.agent-o-rama.ui.agents)

(defn index [{:keys [parameters]}]
  {:status
   200
   
   :body
   [{:module-id "ModuleA" :agent-id "research"}
    {:module-id "ModuleA" :agent-id "support"}
    {:module-id "ModuleB" :agent-id "research"}]})

(defn get [{{:keys [module-id agent-id]} :path-params}]
  {:status
   200
   
   :body
   {:invokes ;;agent-invoke-pstate-<agent-id> 
    [
     ;; probably want to join/lookup more data about each root invoke from the
     ;; $$_agent_node-<agent-id>[root-invoke-id] pstate
     {:root-invoke-id 121
      :invoke-args ["CUSTOMER-123"]
      :graph-version 0
      :result {:success true} }
     {:root-invoke-id 122
      :invoke-args ["CUSTOMER-66"]
      :graph-version 0
      :result {:success true} }
     {:root-invoke-id 123
      :invoke-args ["CUSTOMER-456"]
      :graph-version 0
      :result {:success true} }
     {:root-invoke-id 124
      :invoke-args ["CUSTOMER-222"]
      :graph-version 0
      :result {:success true} }]}})

(def all-data
  {-8054378284095755674
   {:agg-invoke-id 630510010793576188,
    :emits
    [{:invoke-id 2007203707096883205,
      :target-task-id 3,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}
     {:invoke-id 6339085489094251374,
      :target-task-id 0,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}
     {:invoke-id 482236679803680669,
      :target-task-id 2,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}],
    :started-agg-invoke-id 630510010793576188,
    :finish-time-millis 1747325315357,
    :node "node3",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315238,
    :input ["xyz-1-00-000"],
    :graph-task-id 3},
   -8655404759264112200
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id -6698531969404300007,
      :target-task-id 3,
      :node-name "node3",
      :args [{:val "xyz-2-01-000", :async-op-index nil}]}],
    :finish-time-millis 1747325315173,
    :node "node2",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315173,
    :input ["xyz-2-01"],
    :graph-task-id 3},
   -4729418866968894801
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id -8054378284095755674,
      :target-task-id 3,
      :node-name "node3",
      :args [{:val "xyz-1-00-000", :async-op-index nil}]}],
    :finish-time-millis 1747325315181,
    :node "node2",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315181,
    :input ["xyz-1-00"],
    :graph-task-id 3},
   5646292865141442886
   {:agg-invoke-id 5722314036235352800,
    :emits
    [{:invoke-id 1630884286879202167,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315275,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315275,
    :input [1],
    :graph-task-id 3},
   272018652252578009
   {:agg-invoke-id 5722314036235352800,
    :emits
    [{:invoke-id -4051595530914790539,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315255,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315255,
    :input [1],
    :graph-task-id 3},
   4843875360279436393
   {:agg-invoke-id nil,
    :agg-input-count 3,
    :agg-start-res "xyz-1-01-000-0000",
    :emits [],
    :node "agg",
    :agg-inputs-first-10
    [{:invoke-id -1211499101036016895, :args ["1-a"]}
     {:invoke-id -6980148090289722847, :args ["1-a"]}
     {:invoke-id -1629512404312516279, :args ["1-a"]}],
    :async-ops [],
    :agg-ack-val 0,
    :result {:val [["1-a" "1-a" "1-a"] "xyz-1-01-000-0000"]},
    :agg-finished? true,
    :graph-id 0,
    :start-time-millis 1747325315334,
    :agg-state ["1-a" "1-a" "1-a"],
    :input [["1-a" "1-a" "1-a"] "xyz-1-01-000-0000"],
    :agg-start-invoke-id -1226649785603838054,
    :graph-task-id 3},
   -6698531969404300007
   {:agg-invoke-id -8302302075013504311,
    :emits
    [{:invoke-id 8388285912279931320,
      :target-task-id 3,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}
     {:invoke-id -5052231843912662095,
      :target-task-id 2,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}
     {:invoke-id -4972584630469802481,
      :target-task-id 0,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}],
    :started-agg-invoke-id -8302302075013504311,
    :finish-time-millis 1747325315335,
    :node "node3",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315217,
    :input ["xyz-2-01-000"],
    :graph-task-id 3},
   -229621813011379216 {:invoked-agg-invoke-id 630510010793576188},
   630510010793576188
   {:agg-invoke-id nil,
    :agg-input-count 3,
    :agg-start-res "xyz-1-00-000-0000",
    :emits [],
    :node "agg",
    :agg-inputs-first-10
    [{:invoke-id -229621813011379216, :args ["1-a"]}
     {:invoke-id -2626339997418845289, :args ["1-a"]}
     {:invoke-id -4446611212630640670, :args ["1-a"]}],
    :async-ops [],
    :agg-ack-val 0,
    :result {:val [["1-a" "1-a" "1-a"] "xyz-1-00-000-0000"]},
    :agg-finished? true,
    :graph-id 0,
    :start-time-millis 1747325315334,
    :agg-state ["1-a" "1-a" "1-a"],
    :input [["1-a" "1-a" "1-a"] "xyz-1-00-000-0000"],
    :agg-start-invoke-id -8054378284095755674,
    :graph-task-id 3},
   -5052231843912662095
   {:agg-invoke-id -8302302075013504311,
    :emits
    [{:invoke-id 8008274892258923736,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315244,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315244,
    :input [1],
    :graph-task-id 3},
   -7359672106698672064
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id 4392204058265531881,
      :target-task-id 3,
      :node-name "node3",
      :args [{:val "xyz-2-00-000", :async-op-index nil}]}],
    :finish-time-millis 1747325315173,
    :node "node2",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315172,
    :input ["xyz-2-00"],
    :graph-task-id 3},
   3277133613075306271 {:invoked-agg-invoke-id -3191147737470829596},
   -4446611212630640670 {:invoked-agg-invoke-id 630510010793576188},
   4392204058265531881
   {:agg-invoke-id 7842576444508565020,
    :emits
    [{:invoke-id -111912734637948053,
      :target-task-id 3,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}
     {:invoke-id -1770684469587148431,
      :target-task-id 0,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}
     {:invoke-id -1779280137036749794,
      :target-task-id 2,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}],
    :started-agg-invoke-id 7842576444508565020,
    :finish-time-millis 1747325315335,
    :node "node3",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315196,
    :input ["xyz-2-00-000"],
    :graph-task-id 3},
   3702329396130505942
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id -7986827514455586545,
      :target-task-id 3,
      :node-name "node3",
      :args [{:val "xyz-0-00-000", :async-op-index nil}]}],
    :finish-time-millis 1747325315180,
    :node "node2",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315180,
    :input ["xyz-0-00"],
    :graph-task-id 3},
   7842576444508565020
   {:agg-invoke-id nil,
    :agg-input-count 3,
    :agg-start-res "xyz-2-00-000-0000",
    :emits [],
    :node "agg",
    :agg-inputs-first-10
    [{:invoke-id 4941848864028593224, :args ["1-a"]}
     {:invoke-id 1785545293343513853, :args ["1-a"]}
     {:invoke-id -384298525775658678, :args ["1-a"]}],
    :async-ops [],
    :agg-ack-val 0,
    :result {:val [["1-a" "1-a" "1-a"] "xyz-2-00-000-0000"]},
    :agg-finished? true,
    :graph-id 0,
    :start-time-millis 1747325315309,
    :agg-state ["1-a" "1-a" "1-a"],
    :input [["1-a" "1-a" "1-a"] "xyz-2-00-000-0000"],
    :agg-start-invoke-id 4392204058265531881,
    :graph-task-id 3},
   4941848864028593224 {:invoked-agg-invoke-id 7842576444508565020},
   -1770684469587148431
   {:agg-invoke-id 7842576444508565020,
    :emits
    [{:invoke-id 4941848864028593224,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315236,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315236,
    :input [1],
    :graph-task-id 3},
   6233800438881828545
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id -7359672106698672064,
      :target-task-id 0,
      :node-name "node2",
      :args [{:val "xyz-2-00", :async-op-index nil}]}
     {:invoke-id -8655404759264112200,
      :target-task-id 0,
      :node-name "node2",
      :args [{:val "xyz-2-01", :async-op-index nil}]}],
    :finish-time-millis 1747325315161,
    :node "node1",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315161,
    :input ["xyz-2"],
    :graph-task-id 3},
   4657277326222655577 {:invoked-agg-invoke-id 5722314036235352800},
   -7986827514455586545
   {:agg-invoke-id 5722314036235352800,
    :emits
    [{:invoke-id 5646292865141442886,
      :target-task-id 3,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}
     {:invoke-id 272018652252578009,
      :target-task-id 0,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}
     {:invoke-id 2959421471393637824,
      :target-task-id 2,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}],
    :started-agg-invoke-id 5722314036235352800,
    :finish-time-millis 1747325315357,
    :node "node3",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315236,
    :input ["xyz-0-00-000"],
    :graph-task-id 3},
   5722314036235352800
   {:agg-invoke-id nil,
    :agg-input-count 3,
    :agg-start-res "xyz-0-00-000-0000",
    :emits [],
    :node "agg",
    :agg-inputs-first-10
    [{:invoke-id -4051595530914790539, :args ["1-a"]}
     {:invoke-id 1630884286879202167, :args ["1-a"]}
     {:invoke-id 4657277326222655577, :args ["1-a"]}],
    :async-ops [],
    :agg-ack-val 0,
    :result {:val [["1-a" "1-a" "1-a"] "xyz-0-00-000-0000"]},
    :agg-finished? true,
    :graph-id 0,
    :start-time-millis 1747325315331,
    :agg-state ["1-a" "1-a" "1-a"],
    :input [["1-a" "1-a" "1-a"] "xyz-0-00-000-0000"],
    :agg-start-invoke-id -7986827514455586545,
    :graph-task-id 3},
   1785545293343513853 {:invoked-agg-invoke-id 7842576444508565020},
   -111912734637948053
   {:agg-invoke-id 7842576444508565020,
    :emits
    [{:invoke-id 1785545293343513853,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315239,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315239,
    :input [1],
    :graph-task-id 3},
   -7342351599422710957
   {:agg-invoke-id 4843875360279436393,
    :emits
    [{:invoke-id -1211499101036016895,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315278,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315278,
    :input [1],
    :graph-task-id 3},
   -2626339997418845289 {:invoked-agg-invoke-id 630510010793576188},
   -1629512404312516279 {:invoked-agg-invoke-id 4843875360279436393},
   6339085489094251374
   {:agg-invoke-id 630510010793576188,
    :emits
    [{:invoke-id -229621813011379216,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315278,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315278,
    :input [1],
    :graph-task-id 3},
   -1505678660778100009 {:invoked-agg-invoke-id -3191147737470829596},
   2959421471393637824
   {:agg-invoke-id 5722314036235352800,
    :emits
    [{:invoke-id 4657277326222655577,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315277,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315277,
    :input [1],
    :graph-task-id 3},
   -4972584630469802481
   {:agg-invoke-id -8302302075013504311,
    :emits
    [{:invoke-id -5217600154874891798,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315237,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315237,
    :input [1],
    :graph-task-id 3},
   1755062496601524621 {:invoked-agg-invoke-id -8302302075013504311},
   -1211499101036016895 {:invoked-agg-invoke-id 4843875360279436393},
   -1214798873521487204
   {:agg-invoke-id -3191147737470829596,
    :emits
    [{:invoke-id 7087469347612233478,
      :target-task-id 3,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}
     {:invoke-id 4209889553097268777,
      :target-task-id 2,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}
     {:invoke-id 4437889718413585070,
      :target-task-id 0,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}],
    :started-agg-invoke-id -3191147737470829596,
    :finish-time-millis 1747325315357,
    :node "node3",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315237,
    :input ["xyz-0-01-000"],
    :graph-task-id 3},
   -3191147737470829596
   {:agg-invoke-id nil,
    :agg-input-count 3,
    :agg-start-res "xyz-0-01-000-0000",
    :emits [],
    :node "agg",
    :agg-inputs-first-10
    [{:invoke-id -1505678660778100009, :args ["1-a"]}
     {:invoke-id 3277133613075306271, :args ["1-a"]}
     {:invoke-id -8868567550841986134, :args ["1-a"]}],
    :async-ops [],
    :agg-ack-val 0,
    :result {:val [["1-a" "1-a" "1-a"] "xyz-0-01-000-0000"]},
    :agg-finished? true,
    :graph-id 0,
    :start-time-millis 1747325315332,
    :agg-state ["1-a" "1-a" "1-a"],
    :input [["1-a" "1-a" "1-a"] "xyz-0-01-000-0000"],
    :agg-start-invoke-id -1214798873521487204,
    :graph-task-id 3},
   482236679803680669
   {:agg-invoke-id 630510010793576188,
    :emits
    [{:invoke-id -2626339997418845289,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315277,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315277,
    :input [1],
    :graph-task-id 3},
   3788540444613744310
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id 3702329396130505942,
      :target-task-id 3,
      :node-name "node2",
      :args [{:val "xyz-0-00", :async-op-index nil}]}
     {:invoke-id -2005356971586705830,
      :target-task-id 2,
      :node-name "node2",
      :args [{:val "xyz-0-01", :async-op-index nil}]}],
    :finish-time-millis 1747325315162,
    :node "node1",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315161,
    :input ["xyz-0"],
    :graph-task-id 3},
   4209889553097268777
   {:agg-invoke-id -3191147737470829596,
    :emits
    [{:invoke-id -8868567550841986134,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315277,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315277,
    :input [1],
    :graph-task-id 3},
   8008274892258923736 {:invoked-agg-invoke-id -8302302075013504311},
   -6980148090289722847 {:invoked-agg-invoke-id 4843875360279436393},
   -2224644144398831147
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id -1226649785603838054,
      :target-task-id 3,
      :node-name "node3",
      :args [{:val "xyz-1-01-000", :async-op-index nil}]}],
    :finish-time-millis 1747325315182,
    :node "node2",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315182,
    :input ["xyz-1-01"],
    :graph-task-id 3},
   -8868567550841986134 {:invoked-agg-invoke-id -3191147737470829596},
   1630884286879202167 {:invoked-agg-invoke-id 5722314036235352800},
   -2005356971586705830
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id -1214798873521487204,
      :target-task-id 3,
      :node-name "node3",
      :args [{:val "xyz-0-01-000", :async-op-index nil}]}],
    :finish-time-millis 1747325315181,
    :node "node2",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315181,
    :input ["xyz-0-01"],
    :graph-task-id 3},
   -1779280137036749794
   {:agg-invoke-id 7842576444508565020,
    :emits
    [{:invoke-id -384298525775658678,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315244,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315244,
    :input [1],
    :graph-task-id 3},
   8388285912279931320
   {:agg-invoke-id -8302302075013504311,
    :emits
    [{:invoke-id 1755062496601524621,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315243,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315243,
    :input [1],
    :graph-task-id 3},
   4437889718413585070
   {:agg-invoke-id -3191147737470829596,
    :emits
    [{:invoke-id -1505678660778100009,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315277,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315277,
    :input [1],
    :graph-task-id 3},
   7280974400808539463
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id -4729418866968894801,
      :target-task-id 2,
      :node-name "node2",
      :args [{:val "xyz-1-00", :async-op-index nil}]}
     {:invoke-id -2224644144398831147,
      :target-task-id 2,
      :node-name "node2",
      :args [{:val "xyz-1-01", :async-op-index nil}]}],
    :finish-time-millis 1747325315162,
    :node "node1",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315162,
    :input ["xyz-1"],
    :graph-task-id 3},
   9162688563907339900
   {:agg-invoke-id 4843875360279436393,
    :emits
    [{:invoke-id -6980148090289722847,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315278,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315278,
    :input [1],
    :graph-task-id 3},
   -384298525775658678 {:invoked-agg-invoke-id 7842576444508565020},
   -1226649785603838054
   {:agg-invoke-id 4843875360279436393,
    :emits
    [{:invoke-id 5917111155659496302,
      :target-task-id 3,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}
     {:invoke-id 9162688563907339900,
      :target-task-id 2,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}
     {:invoke-id -7342351599422710957,
      :target-task-id 0,
      :node-name "node4",
      :args [{:val 1, :async-op-index nil}]}],
    :started-agg-invoke-id 4843875360279436393,
    :finish-time-millis 1747325315357,
    :node "node3",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315238,
    :input ["xyz-1-01-000"],
    :graph-task-id 3},
   -5217600154874891798 {:invoked-agg-invoke-id -8302302075013504311},
   5917111155659496302
   {:agg-invoke-id 4843875360279436393,
    :emits
    [{:invoke-id -1629512404312516279,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315306,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315306,
    :input [1],
    :graph-task-id 3},
   -4051595530914790539 {:invoked-agg-invoke-id 5722314036235352800},
   2007203707096883205
   {:agg-invoke-id 630510010793576188,
    :emits
    [{:invoke-id -4446611212630640670,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315306,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315306,
    :input [1],
    :graph-task-id 3},
   -5260405110785520210
   {:agg-invoke-id nil,
    :emits
    [{:invoke-id 3788540444613744310,
      :target-task-id 3,
      :node-name "node1",
      :args [{:val "xyz-0", :async-op-index nil}]}
     {:invoke-id 7280974400808539463,
      :target-task-id 2,
      :node-name "node1",
      :args [{:val "xyz-1", :async-op-index nil}]}
     {:invoke-id 6233800438881828545,
      :target-task-id 0,
      :node-name "node1",
      :args [{:val "xyz-2", :async-op-index nil}]}],
    :finish-time-millis 1747325315113,
    :node "start",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315105,
    :input ["xyz"],
    :graph-task-id 3},
   7087469347612233478
   {:agg-invoke-id -3191147737470829596,
    :emits
    [{:invoke-id 3277133613075306271,
      :target-task-id 3,
      :node-name "agg",
      :args [{:val "1-a", :async-op-index nil}]}],
    :finish-time-millis 1747325315276,
    :node "node4",
    :async-ops [],
    :result nil,
    :graph-id 0,
    :start-time-millis 1747325315276,
    :input [1],
    :graph-task-id 3},
   -8302302075013504311
   {:agg-invoke-id nil,
    :agg-input-count 3,
    :agg-start-res "xyz-2-01-000-0000",
    :emits [],
    :node "agg",
    :agg-inputs-first-10
    [{:invoke-id -5217600154874891798, :args ["1-a"]}
     {:invoke-id 1755062496601524621, :args ["1-a"]}
     {:invoke-id 8008274892258923736, :args ["1-a"]}],
    :async-ops [],
    :agg-ack-val 0,
    :result {:val [["1-a" "1-a" "1-a"] "xyz-2-01-000-0000"]},
    :agg-finished? true,
    :graph-id 0,
    :start-time-millis 1747325315311,
    :agg-state ["1-a" "1-a" "1-a"],
    :input [["1-a" "1-a" "1-a"] "xyz-2-01-000-0000"],
    :agg-start-invoke-id -6698531969404300007,
    :graph-task-id 3}})

(defn invoke [{{:keys [module-id agent-id invoke-id]} :path-params}]
  {:status
   200
   
   :body
   {:next-task-invoke-pairs [] ;; [task id, invoke id]
    :invokes-map all-data}})
