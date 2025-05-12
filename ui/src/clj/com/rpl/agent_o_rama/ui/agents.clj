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
   [ ;; Newest Run First (assuming descending sort by time)
    {:run_id "789012345" ;; Corresponds to graph-id
     :start_time_utc "2023-10-27T12:05:10Z"
     :end_time_utc "2023-10-27T12:05:12Z" ;; Requires calculation
     :duration_ms 1850 ;; Requires calculation
     :status "Success" ;; Determined from result/errors
     :input_preview ["Order #5512", {:item_id "XYZ-100", :quantity 2}] ;; From invoke_args
     :output_preview {:confirmation_id "CONF-5512-ABC", :status "Success"} ;; From result.val
     :error_message nil
     :agent_version 12 ;; From invoke entry
     ;; Optional aggregated stats:
     :llm_calls 1
     :total_tokens 450
     }
    {:run_id "789012340"
     :start_time_utc "2023-10-27T11:58:05Z"
     :end_time_utc "2023-10-27T11:58:06Z"
     :duration_ms 980
     :status "Success"
     :input_preview ["Order #5511", {:item_id "ABC-200", :quantity 1}]
     :output_preview {:confirmation_id "CONF-5511-DEF", :status "Success"}
     :error_message nil
     :agent_version 12
     :llm_calls 1
     :total_tokens 380
     }
    {:run_id "789012335"
     :start_time_utc "2023-10-27T11:55:20Z"
     :end_time_utc "2023-10-27T11:55:21Z"
     :duration_ms 750
     :status "Failed"
     :input_preview ["Order #5510", {:item_id "INVALID-ID", :quantity 1}]
     :output_preview nil ;; Failed, no result
     :error_message "Validation Error: Item ID not found." ;; Needs error schema/storage
     :agent_version 12
     :llm_calls 0
     :total_tokens 0
     }
    ;; Could add an example of a "Running" status if needed
    ;; {:run_id "789012350"
    ;;  :start_time_utc "2023-10-27T12:10:00Z"
    ;;  :end_time_utc nil
    ;;  :duration_ms nil
    ;;  :status "Running"
    ;;  :input_preview ["Order #5513", {:item_id "XYZ-100", :quantity 5}]
    ;;  :output_preview nil
    ;;  :tags ["Production"]
    ;;  :error_message nil
    ;;  :agent_version 12
    ;;  }
    ]})
