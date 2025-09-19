(ns com.rpl.agent.basic.pstate-store-agent
  "Demonstrates PState store operations for complex path-based data structures.

  Features demonstrated:
  - declare-pstate-store: Create a PState store with schema
  - get-store: Access PState stores from agent nodes
  - store/pstate-select: Query data using path expressions
  - store/pstate-select-one: Query data using path expressions
  - store/pstate-transform!: Update data using path-based transformations
  - Complex nested data structures and path-based operations
  - Schema-based storage with Rama's native persistent state"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama :refer :all :as rama]
   [com.rpl.rama.path :refer :all :as path]
   [com.rpl.rama.test :as rtest]))

;;; Agent module demonstrating PState store usage
(aor/defagentmodule PStateStoreModule
  [topology]

  ;; Declare PState store for hierarchical organization data

  ;; Schema:
  ;; {company-id ->
  ;;   {:name String,
  ;;    :departments {dept-id ->
  ;;      {:name String, :employees {emp-id -> {:id String, :name String,
  ;;      :salary Long}}}}}}}
  (aor/declare-pstate-store
   topology
   "$$organizations"
   {String (fixed-keys-schema
            {:name        String
             :departments {String (fixed-keys-schema
                                   {:name      String
                                    :employees {String (fixed-keys-schema
                                                        {:id       String
                                                         :name     String
                                                         :salary   Long
                                                         :metadata
                                                         Object})}})}})})

  (->
    topology
    (aor/new-agent "PStateStoreAgent")

    ;; Node to initialize or update organization data
    (aor/node
     "update-org"
     "query-data"
     (fn [agent-node
          {:keys [company-id company-name dept-id dept-name employee]}]
       (let [org-store (aor/get-store agent-node "$$organizations")]

         ;; Initialize company if it doesn't exist
         (when company-name
           (store/pstate-transform!
            [(path/keypath company-id :name)
             (path/term (fn [existing] (or existing company-name)))]
            org-store
            company-id))

         ;; Initialize department if it doesn't exist
         (when dept-name
           (store/pstate-transform!
            [(path/keypath company-id :departments dept-id :name)
             (path/term (fn [existing] (or existing dept-name)))]
            org-store
            company-id))

         ;; Add or update employee
         (when employee
           (let [emp-id (:id employee)]
             ;; Now employees is a map, so we can directly set by employee ID
             (store/pstate-transform!
              [(path/keypath company-id :departments dept-id :employees emp-id)
               (path/termval employee)]
              org-store
              company-id)))

         (aor/emit! agent-node
                    "query-data"
                    {:company-id  company-id
                     :dept-id     dept-id
                     :employee-id (when employee (:id employee))}))))

    ;; Node to query and analyze data
    (aor/node
     "query-data"
     "calculate-metrics"
     (fn [agent-node {:keys [company-id dept-id employee-id]}]
       (let [org-store         (aor/get-store agent-node "$$organizations")
             company-name      (store/pstate-select-one
                                [(path/keypath company-id :name)]
                                org-store
                                company-id)
             dept-name         (store/pstate-select-one
                                [(path/keypath company-id
                                               :departments
                                               dept-id
                                               :name)]
                                org-store
                                company-id)
             all-employees     (store/pstate-select-one
                                [(path/keypath company-id
                                               :departments
                                               dept-id
                                               :employees)]
                                org-store
                                company-id)
             specific-employee (when employee-id
                                 (store/pstate-select-one
                                  [(path/keypath company-id
                                                 :departments dept-id
                                                 :employees
                                                 employee-id)]
                                  org-store
                                  company-id))
             all-departments   (store/pstate-select
                                [(path/keypath company-id :departments)]
                                org-store
                                company-id)]
         ;; Query various data paths
         (aor/emit! agent-node
                    "calculate-metrics"
                    {:company-id        company-id
                     :company-name      company-name
                     :dept-id           dept-id
                     :dept-name         dept-name
                     :all-employees     all-employees
                     :specific-employee specific-employee
                     :all-departments   all-departments}))))

    ;; Final node to calculate metrics and return result
    (aor/node
     "calculate-metrics"
     nil
     (fn [agent-node
          {:keys [company-id company-name dept-id dept-name
                  all-employees specific-employee all-departments]}]
       (let [org-store (aor/get-store agent-node "$$organizations")]

         ;; Calculate department metrics
         (let [employee-list   (if (map? all-employees)
                                 (vals all-employees)
                                 [])
               total-employees (count employee-list)
               avg-salary      (if (and (seq employee-list)
                                        (every? map? employee-list))
                                 (/ (reduce + (map :salary employee-list))
                                    total-employees)
                                 0)
               dept-count      (count (keys all-departments))

               ;; Demonstrate complex path querying - get all
               ;; employee names across all departments
               all-company-employees
               (store/pstate-select
                [(path/keypath company-id :departments) MAP-VALS :employees
                 MAP-VALS :name]
                org-store
                company-id)
               result          {:action           "pstate-query"
                                :company-id       company-id
                                :company-name     company-name
                                :dept-id          dept-id
                                :dept-name        dept-name
                                :employee-count   total-employees
                                :average-salary   avg-salary
                                :department-count dept-count
                                :all-company-employee-names
                                all-company-employees
                                :queried-employee specific-employee
                                :processed-at     (System/currentTimeMillis)}]

           (aor/result! agent-node result)))))))

(defn -main
  "Run the PState store agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)
              _ui (aor/start-ui ipc {:port 1975})]
    (rtest/launch-module! ipc PStateStoreModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager
                   ipc
                   (rama/get-module-name PStateStoreModule))
          agent   (aor/agent-client manager "PStateStoreAgent")]

      (println "PState Store Agent Example:")

      ;; First invocation: Create company and first employee
      (println "\n--- Creating company and first employee ---")
      (let [result1 (aor/agent-invoke
                     agent
                     {:company-id   "tech-corp"
                      :company-name "TechCorp Inc"
                      :dept-id      "engineering"
                      :dept-name    "Engineering"
                      :employee     {:id       "emp001"
                                     :name     "Alice Johnson"
                                     :salary   95000
                                     :metadata {:level  "senior"
                                                :skills ["clojure" "java"]}}})]
        (println "Result 1:")
        (println "  Company:" (:company-name result1))
        (println "  Department:" (:dept-name result1))
        (println "  Employee count:" (:employee-count result1))
        (println "  Average salary:" (:average-salary result1)))

      ;; Second invocation: Add another employee to same department
      (println "\n--- Adding second employee ---")
      (let [result2 (aor/agent-invoke
                     agent
                     {:company-id "tech-corp"
                      :dept-id    "engineering"
                      :employee   {:id       "emp002"
                                   :name     "Bob Smith"
                                   :salary   85000
                                   :metadata {:level  "mid"
                                              :skills ["python" "sql"]}}})]
        (println "Result 2:")
        (println "  Employee count:" (:employee-count result2))
        (println "  Average salary:" (:average-salary result2))
        (println "  All company employees:"
                 (:all-company-employee-names result2)))

      ;; Third invocation: Add different department
      (println "\n--- Adding marketing department ---")
      (let [result3 (aor/agent-invoke
                     agent
                     {:company-id "tech-corp"
                      :dept-id    "marketing"
                      :dept-name  "Marketing"
                      :employee   {:id       "emp003"
                                   :name     "Carol Davis"
                                   :salary   75000
                                   :metadata {:level  "manager"
                                              :skills ["strategy"
                                                       "analytics"]}}})]
        (println "Result 3:")
        (println "  Department count:" (:department-count result3))
        (println "  Current dept employee count:" (:employee-count result3))
        (println "  All company employees:"
                 (:all-company-employee-names result3)))

      ;; Fourth invocation: Update existing employee
      (println "\n--- Updating employee salary ---")
      (let [result4 (aor/agent-invoke
                     agent
                     {:company-id "tech-corp"
                      :dept-id    "engineering"
                      :employee   {:id       "emp001" ; Same ID - will update
                                   :name     "Alice Johnson"
                                   :salary   105000 ; Salary increase
                                   :metadata {:level  "principal"
                                              :skills ["clojure" "java"
                                                       "architecture"]}}})]
        (println "Result 4:")
        (println "  Employee count:" (:employee-count result4))
        (println "  Average salary:" (:average-salary result4))
        (println "  Updated employee:" (:queried-employee result4)))

      (println "\nNotice how:")
      (println "- Complex nested data structures are supported")
      (println "- Path-based querying allows precise data access")
      (println "- Updates can target specific nested elements")
      (println
       "- Cross-department queries are possible with path expressions"))))

(comment
  (-main))
