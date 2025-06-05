(ns com.rpl.agent-o-rama.ui.datasets
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   ["axios" :as axios]
   ["wouter" :as wouter :refer [useLocation]]
   [clojure.string :as str]

   [com.rpl.agent-o-rama.ui.common :as common]))

;; Utility functions
(defn format-date [date-str]
  (when date-str
    (-> (js/Date. date-str)
        (.toLocaleDateString))))

(defn format-duration [start end]
  (when (and start end)
    (let [duration (- (js/Date. end) (js/Date. start))]
      (str (Math/round (/ duration 1000)) "s"))))

;; Dataset list component
(defui dataset-card [{:keys [dataset on-select on-delete]}]
  (let [{:keys [id name description version entry-count tags created-at]} dataset]
    ($ :div.border.rounded-lg.p-4.hover:shadow-md.transition-shadow.cursor-pointer
       {:onClick #(on-select id)}
       ($ :div.flex.justify-between.items-start.mb-2
          ($ :h3.text-lg.font-semibold name)
          ($ :div.flex.gap-2
             ($ :span.text-sm.text-gray-500 (str "v" version))
             ($ :button.text-red-500.hover:text-red-700.text-sm
                {:onClick (fn [e] 
                           (.stopPropagation e)
                           (on-delete id))}
                "Delete")))
       ($ :p.text-gray-600.text-sm.mb-3 description)
       ($ :div.flex.justify-between.items-center
          ($ :div.flex.gap-2
             (for [tag tags]
               ($ :span.bg-blue-100.text-blue-800.text-xs.px-2.py-1.rounded
                  {:key tag} tag)))
          ($ :div.text-right.text-sm.text-gray-500
             ($ :div (str entry-count " entries"))
             ($ :div (format-date created-at)))))))

(defui datasets-list []
  (let [[location setLocation] (useLocation)
        [selectedDataset setSelectedDataset] (uix/use-state nil)
        [showCreateForm setShowCreateForm] (uix/use-state false)
        
        {:keys [data isLoading mutate]}
        (common/use-query {:query-keys ["datasets"]
                          :query-url "/api/datasets"})
        
        delete-dataset
        (fn [dataset-id]
          (when (js/confirm "Are you sure you want to delete this dataset?")
            (-> (axios/delete (str "/api/datasets/" dataset-id))
                (.then #(mutate)))))
        
        select-dataset
        (fn [dataset-id]
          (setLocation (str "/datasets/" dataset-id)))]
    
    ($ :div.p-6
       ($ :div.flex.justify-between.items-center.mb-6
          ($ :h1.text-2xl.font-bold "Datasets")
          ($ :button.bg-blue-500.text-white.px-4.py-2.rounded.hover:bg-blue-600
             {:onClick #(setShowCreateForm true)}
             "Create Dataset"))
       
       (if isLoading
         ($ :div.text-center "Loading datasets...")
         ($ :div.grid.grid-cols-1.md:grid-cols-2.lg:grid-cols-3.gap-4
            (for [dataset data]
              ($ dataset-card 
                 {:key (:id dataset)
                  :dataset dataset
                  :on-select select-dataset
                  :on-delete delete-dataset}))))
       
       (when showCreateForm
         ($ create-dataset-modal 
            {:on-close #(setShowCreateForm false)
             :on-success #(do (mutate) (setShowCreateForm false))})))))

;; Create dataset modal
(defui create-dataset-modal [{:keys [on-close on-success]}]
  (let [[formData setFormData] (uix/use-state {:name ""
                                               :description ""
                                               :version "1.0"
                                               :tags ""})
        
        handle-submit
        (fn [e]
          (.preventDefault e)
          (let [payload (-> formData
                           (update :tags #(when (seq %) 
                                           (map str/trim (str/split % #",")))))]
            (-> (axios/post "/api/datasets" payload)
                (.then on-success)
                (.catch #(js/console.error "Failed to create dataset:" %)))))]
    
    ($ :div {:className "fixed inset-0 bg-black/50 flex items-center justify-center z-50" :onClick on-close}
       ($ :div.bg-white.p-6.rounded-lg.w-96.max-w-full
          {:onClick #(.stopPropagation %)}
          ($ :h2.text-xl.font-bold.mb-4 "Create New Dataset")
          ($ :form {:onSubmit handle-submit}
             ($ :div.mb-4
                ($ :label.block.text-sm.font-medium.mb-1 "Name")
                ($ :input.w-full.border.rounded.px-3.py-2
                   {:type "text"
                    :value (:name formData)
                    :onChange #(setFormData assoc :name (.. % -target -value))
                    :required true}))
             ($ :div.mb-4
                ($ :label.block.text-sm.font-medium.mb-1 "Description")
                ($ :textarea.w-full.border.rounded.px-3.py-2.h-20
                   {:value (:description formData)
                    :onChange #(setFormData assoc :description (.. % -target -value))}))
             ($ :div.mb-4
                ($ :label.block.text-sm.font-medium.mb-1 "Version")
                ($ :input.w-full.border.rounded.px-3.py-2
                   {:type "text"
                    :value (:version formData)
                    :onChange #(setFormData assoc :version (.. % -target -value))}))
             ($ :div.mb-6
                ($ :label.block.text-sm.font-medium.mb-1 "Tags (comma-separated)")
                ($ :input.w-full.border.rounded.px-3.py-2
                   {:type "text"
                    :value (:tags formData)
                    :onChange #(setFormData assoc :tags (.. % -target -value))}))
             ($ :div.flex.gap-2.justify-end
                ($ :button.px-4.py-2.border.rounded.hover:bg-gray-50
                   {:type "button" :onClick on-close}
                   "Cancel")
                ($ :button.px-4.py-2.bg-blue-500.text-white.rounded.hover:bg-blue-600
                   {:type "submit"}
                   "Create")))))))

;; Dataset detail component
(defui dataset-detail [{:keys [dataset-id]}]
  (let [[activeTab setActiveTab] (uix/use-state "entries")
        
        {:keys [data isLoading]}
        (common/use-query {:query-keys ["dataset" dataset-id]
                          :query-url (str "/api/datasets/" dataset-id)})]
    
    (if isLoading
      ($ :div.p-6 "Loading dataset...")
      ($ :div.p-6
         ;; Header
         ($ :div.mb-6
            ($ :h1.text-2xl.font-bold (:name data))
            ($ :p.text-gray-600 (:description data))
            ($ :div.flex.gap-4.mt-2.text-sm.text-gray-500
               ($ :span (str "Version: " (:version data)))
               ($ :span (str (:entry-count data) " entries"))
               ($ :span (str "Created: " (format-date (:created-at data))))))
         
         ;; Tabs
         ($ :div.border-b.mb-6
            ($ :nav.-mb-px.flex.space-x-8
               ($ :button
                  {:className (str "py-2 px-1 border-b-2 font-medium text-sm "
                                  (if (= activeTab "entries")
                                    "border-blue-500 text-blue-600"
                                    "border-transparent text-gray-500 hover:text-gray-700"))
                   :onClick #(setActiveTab "entries")}
                  "Entries")
               ($ :button
                  {:className (str "py-2 px-1 border-b-2 font-medium text-sm "
                                  (if (= activeTab "evaluations")
                                    "border-blue-500 text-blue-600"
                                    "border-transparent text-gray-500 hover:text-gray-700"))
                   :onClick #(setActiveTab "evaluations")}
                  "Evaluations")))
         
         ;; Tab content
         (case activeTab
           "entries" ($ dataset-entries {:dataset-id dataset-id})
           "evaluations" ($ dataset-evaluations {:dataset-id dataset-id})
           nil)))))

;; Dataset entries component
(defui dataset-entries [{:keys [dataset-id]}]
  (let [[showAddForm setShowAddForm] (uix/use-state false)
        
        {:keys [data isLoading mutate]}
        (common/use-query {:query-keys ["dataset-entries" dataset-id]
                          :query-url (str "/api/datasets/" dataset-id "/entries")})]
           ($ :div
          ($ :div.flex.justify-between.items-center.mb-4
             ($ :h3.text-lg.font-semibold "Dataset Entries")
             ($ :button.bg-green-500.text-white.px-4.py-2.rounded.hover:bg-green-600
                {:onClick #(setShowAddForm true)}
                "Add Entry"))
          
          (if isLoading
            ($ :div.text-center "Loading entries...")
            (if (and data (:entries data))
              ($ :div.space-y-4
                 (for [entry (:entries data)]
                   ($ :div.border.rounded.p-4
                      {:key (:id entry)}
                      ($ :div.mb-2
                         ($ :h4.font-medium "Input:")
                         ($ :pre.bg-gray-100.p-2.rounded.text-sm.overflow-x-auto
                            (common/pp (:input entry))))
                      ($ :div.mb-2
                         ($ :h4.font-medium "Expected Output:")
                         ($ :div.text-sm.text-gray-700 (:expected-output entry)))
                      (when (:metadata entry)
                        ($ :div
                           ($ :h4.font-medium "Metadata:")
                           ($ :pre.bg-gray-50.p-2.rounded.text-xs
                              (common/pp (:metadata entry))))))))
              ($ :div.text-center.text-gray-500 "No entries yet")))
       
                (when showAddForm
           ($ add-entry-modal
              {:dataset-id dataset-id
               :on-close #(setShowAddForm false)
               :on-success #(do (mutate) (setShowAddForm false))})))))

;; Add entry modal
(defui add-entry-modal [{:keys [dataset-id on-close on-success]}]
  (let [[formData setFormData] (uix/use-state {:input ""
                                               :expected-output ""
                                               :metadata ""})
        
        handle-submit
        (fn [e]
          (.preventDefault e)
          (let [payload {:input (js/JSON.parse (:input formData))
                         :expected-output (:expected-output formData)
                         :metadata (when (seq (:metadata formData))
                                     (js/JSON.parse (:metadata formData)))}]
            (-> (axios/post (str "/api/datasets/" dataset-id "/entries") payload)
                (.then on-success)
                (.catch #(js/console.error "Failed to add entry:" %)))))]
    
    ($ :div {:className "fixed inset-0 bg-black/50 flex items-center justify-center z-50"
             :onClick on-close}
       ($ :div {:className "bg-white p-6 rounded-lg w-2/3 max-w-4xl max-h-5/6 overflow-y-auto"
                :onClick #(.stopPropagation %)}
          ($ :h2.text-xl.font-bold.mb-4 "Add Dataset Entry")
          ($ :form {:onSubmit handle-submit}
             ($ :div.mb-4
                ($ :label.block.text-sm.font-medium.mb-1 "Input (JSON)")
                ($ :textarea.w-full.border.rounded.px-3.py-2.h-32.font-mono.text-sm
                   {:value (:input formData)
                    :onChange #(setFormData assoc :input (.. % -target -value))
                    :placeholder "{\"query\": \"Your input here\", \"context\": {}}"
                    :required true}))
             ($ :div.mb-4
                ($ :label.block.text-sm.font-medium.mb-1 "Expected Output")
                ($ :textarea.w-full.border.rounded.px-3.py-2.h-32
                   {:value (:expected-output formData)
                    :onChange #(setFormData assoc :expected-output (.. % -target -value))
                    :required true}))
             ($ :div.mb-6
                ($ :label.block.text-sm.font-medium.mb-1 "Metadata (JSON, optional)")
                ($ :textarea.w-full.border.rounded.px-3.py-2.h-20.font-mono.text-sm
                   {:value (:metadata formData)
                    :onChange #(setFormData assoc :metadata (.. % -target -value))
                    :placeholder "{\"difficulty\": \"easy\", \"category\": \"support\"}"}))
             ($ :div.flex.gap-2.justify-end
                ($ :button.px-4.py-2.border.rounded.hover:bg-gray-50
                   {:type "button" :onClick on-close}
                   "Cancel")
                ($ :button.px-4.py-2.bg-green-500.text-white.rounded.hover:bg-green-600
                   {:type "submit"}
                   "Add Entry")))))))

;; Dataset evaluations component
(defui dataset-evaluations [{:keys [dataset-id]}]
  (let [[showRunForm setShowRunForm] (uix/use-state false)
        
        {:keys [data isLoading mutate]}
        (common/use-query {:query-keys ["evaluations" dataset-id]
                          :query-url (str "/api/evaluations?dataset-id=" dataset-id)})]
           ($ :div
          ($ :div.flex.justify-between.items-center.mb-4
             ($ :h3.text-lg.font-semibold "Evaluations")
             ($ :button.bg-purple-500.text-white.px-4.py-2.rounded.hover:bg-purple-600
                {:onClick #(setShowRunForm true)}
                "Run Evaluation"))
          
          (if isLoading
            ($ :div.text-center "Loading evaluations...")
            (if (seq data)
              ($ :div.space-y-4
                 (for [evaluation data]
              ($ :div.border.rounded.p-4
                 {:key (:id evaluation)}
                 ($ :div.flex.justify-between.items-start.mb-2
                    ($ :div
                       ($ :h4.font-medium (str "Evaluation " (:id evaluation)))
                       ($ :p.text-sm.text-gray-600 
                          (str "Agent: " (get-in evaluation [:agent-config :module-id]) 
                               "/" (get-in evaluation [:agent-config :agent-id]))))
                    ($ :span.px-2.py-1.rounded.text-sm
                       {:className (case (:status evaluation)
                                    "completed" "bg-green-100 text-green-800"
                                    "running" "bg-yellow-100 text-yellow-800"
                                    "failed" "bg-red-100 text-red-800"
                                    "bg-gray-100 text-gray-800")}
                       (:status evaluation)))
                 
                 (when (:results evaluation)
                   (let [results (:results evaluation)]
                     ($ :div.grid.grid-cols-2.md:grid-cols-4.gap-4.text-sm
                        ($ :div
                           ($ :div.text-gray-500 "Success Rate")
                           ($ :div.font-medium 
                              (str (Math/round (* 100 (/ (:successful results) (:total-entries results)))) "%")))
                        ($ :div
                           ($ :div.text-gray-500 "Avg Latency")
                           ($ :div.font-medium (str (:avg-latency-ms results) "ms")))
                        ($ :div
                           ($ :div.text-gray-500 "Avg Tokens")
                           ($ :div.font-medium (:avg-tokens results)))
                        ($ :div
                           ($ :div.text-gray-500 "Total Cost")
                           ($ :div.font-medium (str "$" (:total-cost-usd results)))))))
                 
                 ($ :div.text-xs.text-gray-500.mt-2
                                          (str "Started: " (format-date (:started-at evaluation)))
                      (when (:completed-at evaluation)
                        (str " • Duration: " (format-duration (:started-at evaluation) (:completed-at evaluation))))))))
              ($ :div.text-center.text-gray-500 "No evaluations yet")))
          
          (when showRunForm
            ($ run-evaluation-modal
               {:dataset-id dataset-id
                :on-close #(setShowRunForm false)
                :on-success #(do (mutate) (setShowRunForm false))})))))

;; Run evaluation modal
(defui run-evaluation-modal [{:keys [dataset-id on-close on-success]}]
  (let [[formData setFormData] (uix/use-state {:module-id ""
                                               :agent-id ""})
        
        handle-submit
        (fn [e]
          (.preventDefault e)
          (let [payload {:dataset-id dataset-id
                        :agent-config formData}]
            (-> (axios/post "/api/evaluations" payload)
                (.then on-success)
                (.catch #(js/console.error "Failed to start evaluation:" %)))))]
    
    ($ :div.fixed.inset-0.bg-black.bg-opacity-50.flex.items-center.justify-center.z-50
       {:onClick on-close}
       ($ :div.bg-white.p-6.rounded-lg.w-96.max-w-full
          {:onClick #(.stopPropagation %)}
          ($ :h2.text-xl.font-bold.mb-4 "Run Evaluation")
          ($ :form {:onSubmit handle-submit}
             ($ :div.mb-4
                ($ :label.block.text-sm.font-medium.mb-1 "Module ID")
                ($ :input.w-full.border.rounded.px-3.py-2
                   {:type "text"
                    :value (:module-id formData)
                    :onChange #(setFormData assoc :module-id (.. % -target -value))
                    :placeholder "e.g., ModuleA"
                    :required true}))
             ($ :div.mb-6
                ($ :label.block.text-sm.font-medium.mb-1 "Agent ID")
                ($ :input.w-full.border.rounded.px-3.py-2
                   {:type "text"
                    :value (:agent-id formData)
                    :onChange #(setFormData assoc :agent-id (.. % -target -value))
                    :placeholder "e.g., support-agent"
                    :required true}))
             ($ :div.flex.gap-2.justify-end
                ($ :button.px-4.py-2.border.rounded.hover:bg-gray-50
                   {:type "button" :onClick on-close}
                   "Cancel")
                ($ :button.px-4.py-2.bg-purple-500.text-white.rounded.hover:bg-purple-600
                   {:type "submit"}
                   "Start Evaluation")))))))

;; Main datasets component with routing
(defui datasets []
  (let [{:strs [dataset-id]} (js->clj (wouter/useParams))]
    (if dataset-id
      ($ dataset-detail {:dataset-id dataset-id})
      ($ datasets-list))))
