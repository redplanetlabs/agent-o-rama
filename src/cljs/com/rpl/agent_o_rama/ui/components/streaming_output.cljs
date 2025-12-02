(ns com.rpl.agent-o-rama.ui.components.streaming-output
  "Example component demonstrating real-time streaming output from agent nodes."
  (:require
   [uix.core :as uix :refer [$ defui]]
   [com.rpl.agent-o-rama.ui.streaming :as streaming]
   [com.rpl.agent-o-rama.ui.common :as common]))

(defui StreamingNodeOutput
  "Displays real-time streaming output from a specific node.
  
  Props:
  - invoke-id: Agent invocation ID (format: 'task-id-agent-id')
  - node-name: Name of the node to stream from
  - class-name: Optional CSS classes
  - show-cursor?: Optional, show animated cursor while streaming (default true)"
  [{:keys [invoke-id node-name class-name show-cursor?]
    :or {show-cursor? true}}]
  
  (let [{:keys [text streaming? reset-count]}
        (streaming/use-node-stream invoke-id node-name)]
    
    ($ :div
       {:className (common/cn
                    "relative font-mono text-sm whitespace-pre-wrap"
                    class-name)}
       
       ;; Show reset indicator if stream was reset due to retry
       (when (pos? reset-count)
         ($ :div.absolute.top-0.right-0.bg-yellow-100.text-yellow-800.text-xs.px-2.py-1.rounded.shadow
            {:title "Stream was reset due to node retry"}
            (str "↻ Retried " reset-count " time" (when (> reset-count 1) "s"))))
       
       ;; Main text output
       ($ :div.min-h-8
          text
          
          ;; Animated cursor while streaming
          (when (and streaming? show-cursor?)
            ($ :span.animate-pulse.ml-1
               {:className "inline-block w-2 h-4 bg-blue-500"}))))))

(defui StreamingCard
  "Card wrapper for streaming output with header and status.
  
  Props:
  - invoke-id: Agent invocation ID
  - node-name: Name of the node
  - title: Optional card title (defaults to node name)"
  [{:keys [invoke-id node-name title]}]
  
  (let [{:keys [streaming? chunks reset-count complete?]}
        (streaming/use-node-stream invoke-id node-name)
        
        status-color (cond
                       streaming? "text-blue-600"
                       complete? "text-green-600"
                       :else "text-gray-400")]
    
    ($ :div.bg-white.rounded-lg.shadow.border.border-gray-200
       
       ;; Header
       ($ :div.px-4.py-3.border-b.border-gray-200.flex.items-center.justify-between
          ($ :h3.text-sm.font-semibold.text-gray-900
             (or title node-name))
          
          ($ :div.flex.items-center.gap-2
             ;; Status indicator
             ($ :div.flex.items-center.gap-1
                ($ :div.w-2.h-2.rounded-full
                   {:className (if streaming?
                                 "bg-blue-500 animate-pulse"
                                 (if complete?
                                   "bg-green-500"
                                   "bg-gray-300"))})
                ($ :span {:className (str "text-xs " status-color)}
                   (cond
                     streaming? "Streaming..."
                     complete? "Complete"
                     :else "Waiting")))
             
             ;; Chunk count
             (when (seq chunks)
               ($ :span.text-xs.text-gray-500
                  (str (count chunks) " chunk" (when (not= (count chunks) 1) "s"))))))
       
       ;; Content
       ($ :div.p-4
          ($ StreamingNodeOutput {:invoke-id invoke-id
                                  :node-name node-name
                                  :show-cursor? streaming?})))))

(defui MultiNodeStreaming
  "Display streaming output from multiple nodes side-by-side.
  
  Props:
  - invoke-id: Agent invocation ID
  - node-names: Vector of node names to stream from"
  [{:keys [invoke-id node-names]}]
  
  ($ :div.grid.grid-cols-1.lg:grid-cols-2.gap-4
     (for [node-name node-names]
       ($ StreamingCard
          {:key node-name
           :invoke-id invoke-id
           :node-name node-name}))))

;; =============================================================================
;; EXAMPLE: Compact inline streaming
;; =============================================================================

(defui InlineStreamingText
  "Compact inline streaming text with optional label.
  Perfect for showing LLM responses inline in a UI."
  [{:keys [invoke-id node-name label]}]
  
  (let [{:keys [text streaming?]}
        (streaming/use-node-stream invoke-id node-name)]
    
    ($ :div.flex.items-start.gap-2
       (when label
         ($ :span.text-sm.font-medium.text-gray-700
            (str label ":")))
       
       ($ :span.text-sm.text-gray-900
          (if (and (empty? text) streaming?)
            ($ :span.text-gray-400.italic "Generating...")
            text)
          
          (when streaming?
            ($ :span.ml-1.inline-block.w-1.h-3.bg-blue-500.animate-pulse))))))

