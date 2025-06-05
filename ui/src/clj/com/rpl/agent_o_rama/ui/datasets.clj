(ns com.rpl.agent-o-rama.ui.datasets
  (:require [com.rpl.specter :as s]
            [ring.util.response :as resp]))

;; Dummy data store
(def datasets-db 
  (atom {"customer-support-v1" {:id "customer-support-v1"
                                :name "Customer Support Scenarios"
                                :description "Common customer support interactions for testing"
                                :version "1.0"
                                :created-at "2024-01-15T10:30:00Z"
                                :updated-at "2024-01-20T14:22:00Z"
                                :entry-count 150
                                :tags ["support" "production" "validated"]
                                :metadata {:difficulty-levels ["easy" "medium" "hard"]
                                           :categories ["returns" "billing" "technical"]}}
         "product-research-v2" {:id "product-research-v2"
                                :name "Product Research Dataset"
                                :description "Research queries and expected analysis outputs"
                                :version "2.1"
                                :created-at "2024-01-10T09:15:00Z"
                                :updated-at "2024-01-25T16:45:00Z"
                                :entry-count 75
                                :tags ["research" "analysis" "beta"]
                                :metadata {:domains ["technology" "healthcare" "finance"]
                                           :complexity ["basic" "advanced"]}}}))

(def dataset-entries-db
  (atom {"customer-support-v1" 
         [{:id "entry-1"
           :input {:query "How do I return an item I bought last week?"
                   :context {:user-type "premium"
                             :order-id "ORD-12345"
                             :item-category "electronics"}}
           :expected-output "I'd be happy to help you return your item. Since you're a premium member, you have 30 days for returns..."
           :metadata {:difficulty "easy"
                      :category "returns"
                      :estimated-tokens 150}}
          {:id "entry-2"
           :input {:query "My billing shows a charge I don't recognize"
                   :context {:user-type "standard"
                             :account-balance "$45.67"
                             :last-purchase "2024-01-18"}}
           :expected-output "Let me help you investigate this charge. I can see your recent transactions..."
           :metadata {:difficulty "medium"
                      :category "billing"
                      :estimated-tokens 200}}]
         "product-research-v2"
         [{:id "entry-3"
           :input {:research-query "Analyze the competitive landscape for AI code assistants"
                   :context {:market-focus "enterprise"
                             :time-horizon "2024-2025"}}
           :expected-output "The AI code assistant market is highly competitive with several key players..."
           :metadata {:complexity "advanced"
                      :domain "technology"
                      :estimated-tokens 500}}]}))

(def evaluations-db
  (atom {"eval-1" {:id "eval-1"
                   :dataset-id "customer-support-v1"
                   :agent-config {:module-id "ModuleA" :agent-id "support-agent"}
                   :status "completed"
                   :started-at "2024-01-25T10:00:00Z"
                   :completed-at "2024-01-25T10:15:00Z"
                   :results {:total-entries 150
                             :successful 142
                             :failed 8
                             :avg-latency-ms 1250
                             :avg-tokens 175
                             :total-cost-usd 2.45}
                   :sample-results [{:entry-id "entry-1"
                                     :success true
                                     :latency-ms 1100
                                     :output "I'd be happy to help you return your item..."
                                     :score 0.92}
                                    {:entry-id "entry-2"
                                     :success false
                                     :latency-ms 2300
                                     :error "Timeout after 30 seconds"
                                     :score 0.0}]}}))

;; Dataset CRUD handlers
(defn list-datasets [_request]
  {:status 200
   :body (vals @datasets-db)})

(defn get-dataset [{:keys [path-params]}]
  (let [dataset-id (:id path-params)]
    (def dataset-id dataset-id)
    (if-let [dataset (get @datasets-db dataset-id)]
      {:status 200 :body dataset}
      {:status 404 :body {:error "Dataset not found"}})))

(defn create-dataset [{:keys [body-params]}]
  (let [dataset-id (str "dataset-" (System/currentTimeMillis))
        dataset (assoc body-params 
                       :id dataset-id
                       :created-at (java.time.Instant/now)
                       :entry-count 0)]
    (swap! datasets-db assoc dataset-id dataset)
    {:status 201 :body dataset}))

(defn update-dataset [{:keys [path-params body-params]}]
  (let [dataset-id (:id path-params)]
    (if (get @datasets-db dataset-id)
      (do
        (swap! datasets-db update dataset-id merge body-params 
               {:updated-at (java.time.Instant/now)})
        {:status 200 :body (get @datasets-db dataset-id)})
      {:status 404 :body {:error "Dataset not found"}})))

(defn delete-dataset [{:keys [path-params]}]
  (let [dataset-id (:id path-params)]
    (if (get @datasets-db dataset-id)
      (do
        (swap! datasets-db dissoc dataset-id)
        (swap! dataset-entries-db dissoc dataset-id)
        {:status 204})
      {:status 404 :body {:error "Dataset not found"}})))

;; Dataset entries handlers
(defn get-dataset-entries [{:keys [path-params query-params]}]
  (let [dataset-id (:id path-params)
        limit (parse-long (get query-params "limit" "50"))
        offset (parse-long (get query-params "offset" "0"))
        entries (get @dataset-entries-db dataset-id [])]
    (if (get @datasets-db dataset-id)
      {:status 200 
       :body {:entries (->> entries (drop offset) (take limit))
              :total (count entries)
              :offset offset
              :limit limit}}
      {:status 404 :body {:error "Dataset not found"}})))

(defn add-dataset-entry [{:keys [path-params body-params]}]
  (let [dataset-id (:id path-params)
        entry-id (str "entry-" (System/currentTimeMillis))
        entry (assoc body-params :id entry-id)]
    (if (get @datasets-db dataset-id)
      (do
        (swap! dataset-entries-db update dataset-id (fnil conj []) entry)
        (swap! datasets-db update-in [dataset-id :entry-count] inc)
        {:status 201 :body entry})
      {:status 404 :body {:error "Dataset not found"}})))

;; Evaluation handlers
(defn start-evaluation [{:keys [body-params]}]
  (let [eval-id (str "eval-" (System/currentTimeMillis))
        evaluation {:id eval-id
                    :dataset-id (:dataset-id body-params)
                    :agent-config (:agent-config body-params)
                    :status "running"
                    :started-at (java.time.Instant/now)
                    :progress {:completed 0 :total 100}}]
    (swap! evaluations-db assoc eval-id evaluation)
    {:status 202 :body evaluation}))

(defn get-evaluation [{:keys [path-params]}]
  (let [eval-id (:id path-params)]
    (if-let [evaluation (get @evaluations-db eval-id)]
      {:status 200 :body evaluation}
      {:status 404 :body {:error "Evaluation not found"}})))

(defn list-evaluations [{:keys [query-params]}]
  (let [dataset-id (get query-params "dataset-id")
        evaluations (vals @evaluations-db)
        filtered (if dataset-id
                   (filter #(= (:dataset-id %) dataset-id) evaluations)
                   evaluations)]
    {:status 200 :body filtered}))

;; Legacy handler for agent-specific datasets
(defn index [{:keys [parameters]}]
  {:status 200
   :body [{:dataset-name "ModuleA" :agent-id "research"}]})



