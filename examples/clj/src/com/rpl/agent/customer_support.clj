(ns com.rpl.agent.customer-support
  "AI-powered customer support agent for airline booking assistance.

   Provides comprehensive travel support including flight search and booking,
   hotel reservations, car rentals, and policy information. Maintains customer
   context and supports complex multi-step interactions."
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.path :as path]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j])
  (:import
   [java.util UUID]))

(def CUSTOMER-SUPPORT-SYSTEM-MESSAGE
  "You are a helpful customer support assistant for Swiss Airlines.
   You help customers with flight bookings, changes, cancellations, and general
   travel assistance.

   You have access to tools to:
   - Search and view flight information
   - Update or cancel bookings
   - Search for hotels and make reservations
   - Find car rental options
   - Look up company policies

   Always be polite, helpful, and professional. If you cannot help with
   something, explain clearly and suggest alternatives when possible.")

;; Mock databases - in a real system these would be external databases
(def MOCK-FLIGHTS
  [{:flight-id         "LX101"
    :departure-airport "ZUR"
    :arrival-airport   "JFK"
    :departure-time    "2024-03-15T08:00"
    :arrival-time      "2024-03-15T11:30"
    :price             850
    :available-seats   45}
   {:flight-id         "LX102"
    :departure-airport "JFK"
    :arrival-airport   "ZUR"
    :departure-time    "2024-03-15T14:30"
    :arrival-time      "2024-03-16T06:45"
    :price             920
    :available-seats   32}
   {:flight-id         "LX201"
    :departure-airport "ZUR"
    :arrival-airport   "LAX"
    :departure-time    "2024-03-16T10:15"
    :arrival-time      "2024-03-16T13:45"
    :price             1200
    :available-seats   28}])

(def MOCK-HOTELS
  [{:hotel-id        "H001"
    :name            "Grand Hotel"
    :location        "New York"
    :price-tier      "luxury"
    :price-per-night 350}
   {:hotel-id        "H002"
    :name            "City Inn"
    :location        "New York"
    :price-tier      "budget"
    :price-per-night 120}
   {:hotel-id        "H003"
    :name            "Business Lodge"
    :location        "Los Angeles"
    :price-tier      "business"
    :price-per-night 180}])

(def MOCK-CAR-RENTALS
  [{:rental-id     "R001" :location  "New York" :car-type "Economy"
    :price-per-day 45     :available true}
   {:rental-id     "R002" :location  "New York" :car-type "Luxury"
    :price-per-day 120    :available true}
   {:rental-id     "R003" :location  "Los Angeles" :car-type "SUV"
    :price-per-day 85     :available true}])

(def POLICIES
  {"baggage"
   "Carry-on: 1 bag up to 8kg. Checked baggage: 1 bag up to 23kg included in economy."
   "cancellation"
   "Free cancellation up to 24 hours before departure. Cancellation fee applies after."
   "change-fee"
   "Flight changes: 50 EUR fee for economy, free for business class."
   "refund"
   "Refunds processed within 7-14 business days to original payment method."})

;; Tool functions
(defn fetch-user-flight-information
  "Retrieve flight information for a specific passenger."
  [agent-node {:keys [passenger-id]} arguments]
  (let [bookings-store (aor/get-store agent-node "$$bookings")
        passenger-id   (get arguments "passenger-id" passenger-id)]
    (if-let [booking (store/get bookings-store passenger-id)]
      (j/write-value-as-string
       {:status  "success"
        :booking booking
        :message
        (format
         "Found booking for passenger %s: Flight %s from %s to %s on %s"
         passenger-id
         (:flight-id booking)
         (:departure-airport booking)
         (:arrival-airport booking)
         (:departure-date booking))})
      (j/write-value-as-string
       {:status  "not-found"
        :message (format
                  "No booking found for passenger ID: %s"
                  passenger-id)}))))

(defn search-flights
  "Search for available flights between airports."
  [agent-node config arguments]
  (let [departure-airport (get arguments "departure-airport")
        arrival-airport   (get arguments "arrival-airport")
        start-date        (get arguments "start-date")
        end-date          (get arguments "end-date")
        matching-flights  (filterv
                           (fn [flight]
                             (and
                              (= (:departure-airport flight) departure-airport)
                              (= (:arrival-airport flight) arrival-airport)
                              (>= (:available-seats flight) 1)))
                           MOCK-FLIGHTS)]
    (j/write-value-as-string
     {:status  "success"
      :flights matching-flights
      :message (format
                "Found %d flights from %s to %s"
                (count matching-flights)
                departure-airport arrival-airport)})))

(defn update-ticket-to-new-flight
  "Update a passenger's ticket to a new flight."
  [agent-node config arguments]
  (let [ticket-no      (get arguments "ticket-no")
        new-flight-id  (get arguments "new-flight-id")
        bookings-store (aor/get-store agent-node "$$bookings")
        flight         (first
                        (filter
                         #(= (:flight-id %) new-flight-id)
                         MOCK-FLIGHTS))]
    (if flight
      (do
        (store/put! bookings-store ticket-no
                    (merge flight {:ticket-no    ticket-no
                                   :booking-date (str (LocalDateTime/now))}))
        (j/write-value-as-string
         {:status  "success"
          :message (format
                    "Successfully updated ticket %s to flight %s"
                    ticket-no new-flight-id)}))
      (j/write-value-as-string
       {:status "error"
        :message
        (format "Flight %s not found or not available" new-flight-id)}))))

(defn cancel-ticket
  "Cancel a specific ticket."
  [agent-node config arguments]
  (let [ticket-no      (get arguments "ticket-no")
        bookings-store (aor/get-store agent-node "$$bookings")]
    (if (store/get bookings-store ticket-no)
      (do
        (store/pstate-transform!
         [(path/keypath ticket-no) path/NONE] bookings-store ticket-no)
        (j/write-value-as-string
         {:status  "success"
          :message (format
                    "Ticket %s has been cancelled successfully"
                    ticket-no)}))
      (j/write-value-as-string
       {:status  "not-found"
        :message (format "Ticket %s not found" ticket-no)}))))

(defn search-hotels
  "Search for hotels in a location."
  [agent-node config arguments]
  (let [location        (get arguments "location")
        name            (get arguments "name")
        price-tier      (get arguments "price-tier")
        checkin-date    (get arguments "checkin-date")
        checkout-date   (get arguments "checkout-date")
        matching-hotels (filter (fn [hotel]
                                  (and (= (:location hotel) location)
                                       (or (nil? price-tier)
                                           (= (:price-tier hotel) price-tier))
                                       (or (nil? name)
                                           (str/includes?
                                            (str/lower-case (:name hotel))
                                            (str/lower-case name)))))
                                MOCK-HOTELS)]
    (j/write-value-as-string
     {:status  "success"
      :hotels  matching-hotels
      :message (format
                "Found %d hotels in %s"
                (count matching-hotels)
                location)})))

(defn book-hotel
  "Book a hotel reservation."
  [agent-node {:keys [passenger-id]} arguments]
  (let [hotel-id             (get arguments "hotel-id")
        checkin-date         (get arguments "checkin-date")
        checkout-date        (get arguments "checkout-date")
        hotel-bookings-store (aor/get-store agent-node "$$hotel-bookings")
        hotel                (first
                              (filter #(= (:hotel-id %) hotel-id) MOCK-HOTELS))
        booking-id           (str (UUID/randomUUID))]
    (if hotel
      (do
        (store/put! hotel-bookings-store booking-id
                    (merge hotel {:booking-id    booking-id
                                  :passenger-id  passenger-id
                                  :checkin-date  checkin-date
                                  :checkout-date checkout-date
                                  :booking-date  (str (LocalDateTime/now))}))
        (j/write-value-as-string
         {:status     "success"
          :booking-id booking-id
          :message    (format "Successfully booked %s for %s to %s"
                              (:name hotel) checkin-date checkout-date)}))
      (j/write-value-as-string
       {:status  "error"
        :message (format "Hotel %s not found" hotel-id)}))))

(defn search-car-rentals
  "Search for car rental options."
  [agent-node config arguments]
  (let [location         (get arguments "location")
        start-date       (get arguments "start-date")
        end-date         (get arguments "end-date")
        car-type         (get arguments "car-type")
        matching-rentals (filter (fn [rental]
                                   (and (= (:location rental) location)
                                        (:available rental)
                                        (or (nil? car-type)
                                            (= (:car-type rental) car-type))))
                                 MOCK-CAR-RENTALS)]
    (j/write-value-as-string
     {:status  "success"
      :rentals matching-rentals
      :message (format
                "Found %d car rentals in %s"
                (count matching-rentals)
                location)})))

(defn book-car-rental
  "Book a car rental."
  [agent-node {:keys [passenger-id]} arguments]
  (let [rental-id          (get arguments "rental-id")
        start-date         (get arguments "start-date")
        end-date           (get arguments "end-date")
        car-bookings-store (aor/get-store agent-node "$$car-bookings")
        rental             (first
                            (filter
                             #(= (:rental-id %) rental-id)
                             MOCK-CAR-RENTALS))
        booking-id         (str (UUID/randomUUID))]
    (if (and rental (:available rental))
      (do
        (store/put! car-bookings-store booking-id
                    (merge rental {:booking-id   booking-id
                                   :passenger-id passenger-id
                                   :start-date   start-date
                                   :end-date     end-date
                                   :booking-date (str (LocalDateTime/now))}))
        (j/write-value-as-string
         {:status     "success"
          :booking-id booking-id
          :message    (format "Successfully booked %s car for %s to %s"
                              (:car-type rental) start-date end-date)}))
      (j/write-value-as-string
       {:status  "error"
        :message (format "Car rental %s not available" rental-id)}))))

(defn lookup-policy
  "Look up company policy information."
  [agent-node config arguments]
  (let [query             (get arguments "query")
        query-lower       (str/lower-case query)
        matching-policies (filter (fn [[key _]]
                                    (str/includes? query-lower key))
                                  POLICIES)]
    (if (seq matching-policies)
      (j/write-value-as-string
       {:status   "success"
        :policies (into {} matching-policies)
        :message  (format "Found %d policy matches for '%s'"
                          (count matching-policies) query)})
      (j/write-value-as-string
       {:status  "not-found"
        :message (format "No policies found matching '%s'" query)}))))

;; Tool definitions using agent-o-rama tools framework
(def CUSTOMER-SUPPORT-TOOLS
  [(tools/tool-info
    (tools/tool-specification
     "fetch_user_flight_information"
     (lj/object
      {:description
       "Retrieve current flight booking information for a specific passenger"
       :required ["passenger-id"]}
      {"passenger-id" (lj/string "The passenger ID to look up")})
     "Retrieve current flight booking information for a specific passenger")
    fetch-user-flight-information
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "search_flights"
     (lj/object
      {:description "Search for available flights between airports"
       :required    ["departure-airport" "arrival-airport"]}
      {"departure-airport" (lj/string "3-letter departure airport code")
       "arrival-airport"   (lj/string "3-letter arrival airport code")
       "start-date"        (lj/string "Earliest departure date (YYYY-MM-DD)")
       "end-date"          (lj/string "Latest departure date (YYYY-MM-DD)")})
     "Search for available flights between airports")
    search-flights
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "update_ticket_to_new_flight"
     (lj/object
      {:description "Update an existing ticket to a new flight"
       :required    ["ticket-no" "new-flight-id"]}
      {"ticket-no"     (lj/string "Ticket number to update")
       "new-flight-id" (lj/string "New flight ID to change to")})
     "Update an existing ticket to a new flight")
    update-ticket-to-new-flight
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "cancel_ticket"
     (lj/object
      {:description "Cancel a flight ticket"
       :required    ["ticket-no"]}
      {"ticket-no" (lj/string "Ticket number to cancel")})
     "Cancel a flight ticket")
    cancel-ticket
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "search_hotels"
     (lj/object
      {:description "Search for hotels in a specific location"
       :required    ["location"]}
      {"location"      (lj/string "City or location to search")
       "name"          (lj/string "Hotel name to search for")
       "price-tier"    (lj/enum
                        "Price category preference"
                        ["budget" "business" "luxury"])
       "checkin-date"  (lj/string "Check-in date (YYYY-MM-DD)")
       "checkout-date" (lj/string "Check-out date (YYYY-MM-DD)")})
     "Search for hotels in a specific location")
    search-hotels
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "book_hotel"
     (lj/object
      {:description "Book a hotel reservation"
       :required    ["hotel-id" "checkin-date" "checkout-date"]}
      {"hotel-id"      (lj/string "Hotel ID to book")
       "checkin-date"  (lj/string "Check-in date (YYYY-MM-DD)")
       "checkout-date" (lj/string "Check-out date (YYYY-MM-DD)")})
     "Book a hotel reservation")
    book-hotel
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "search_car_rentals"
     (lj/object
      {:description "Search for car rental options"
       :required    ["location"]}
      {"location"   (lj/string "City or location for car rental")
       "start-date" (lj/string "Rental start date (YYYY-MM-DD)")
       "end-date"   (lj/string "Rental end date (YYYY-MM-DD)")
       "car-type"   (lj/string "Preferred car type")})
     "Search for car rental options")
    search-car-rentals
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "book_car_rental"
     (lj/object
      {:description "Book a car rental"
       :required    ["rental-id" "start-date" "end-date"]}
      {"rental-id"  (lj/string "Car rental ID to book")
       "start-date" (lj/string "Rental start date (YYYY-MM-DD)")
       "end-date"   (lj/string "Rental end date (YYYY-MM-DD)")})
     "Book a car rental")
    book-car-rental
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "lookup_policy"
     (lj/object
      {:description "Look up company policies and procedures"
       :required    ["query"]}
      {"query"
       (lj/string
        "Policy topic to search for (e.g., baggage, cancellation, refund)")})
     "Look up company policies and procedures")
    lookup-policy
    {:include-context? true})])

(aor/defagentmodule CustomerSupportModule
  [topology]

  ;; Declare OpenAI model
  (aor/declare-agent-object topology
                            "openai-api-key"
                            (System/getenv "OPENAI_API_KEY"))

  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         .build)))

  ;; Declare stores for persistent data
  (aor/declare-key-value-store topology "$$bookings" String Object)
  (aor/declare-key-value-store topology "$$hotel-bookings" String Object)
  (aor/declare-key-value-store topology "$$car-bookings" String Object)
  (aor/declare-key-value-store topology "$$conversations" String Object)

  ;; Define the agent workflow
  (->
   topology
   (aor/new-agent "customer-support")

   ;; Main assistant node - handles conversation and tool decisions
   (aor/node
    "chat"
    "chat"
    (fn [agent-node messages config]
      (let [openai                 (aor/get-agent-object
                                    agent-node
                                    "openai-model")
            conversation-store     (aor/get-store agent-node "$$conversations")
            {:keys [passenger-id]} config
            tools                  (aor/agent-client agent-node "tools")

            ;; Build conversation history
            system-msg   (SystemMessage. CUSTOMER-SUPPORT-SYSTEM-MESSAGE)
            all-messages (concat [system-msg] messages)

            ;; Make API call with tools
            response   (lc4j/chat openai
                                  (lc4j/chat-request
                                   all-messages
                                   {:tools CUSTOMER-SUPPORT-TOOLS}))
            ai-message (.aiMessage response)
            tool-calls (not-empty (vec (.toolExecutionRequests ai-message)))]

        ;; Store conversation state
        (when passenger-id
          (store/put! conversation-store passenger-id
                      {:messages     (conj messages ai-message)
                       :last-updated (str (LocalDateTime/now))}))

        ;; Check if assistant wants to use tools
        (if tool-calls
          (let [tool-results  (aor/agent-invoke tools tool-calls config)
                next-messages (into (conj messages ai-message) tool-results)]
            (aor/emit! agent-node "chat" next-messages config))
          (aor/result! agent-node (.text ai-message)))))))

  (tools/new-tools-agent topology "tools" CUSTOMER-SUPPORT-TOOLS))

;;; Example invocation

(defn run-agent
  "Start the customer support agent with sample interactions."
  []
  (println "Starting Customer Support Agent...")
  (with-open [ipc (rtest/create-ipc)
              _   (aor/start-ui ipc)]
    ;; Launch the topology
    (rtest/launch-module! ipc CustomerSupportModule {:tasks 4 :threads 2})

    (let [module-name   (rama/get-module-name CustomerSupportModule)
          agent-manager (aor/agent-manager ipc module-name)
          agent         (aor/agent-client agent-manager "customer-support")]

      ;; Sample interactions
      (println "\n=== Sample Customer Support Interactions ===\n")

      ;; Test 1: Flight search
      (println "🔍 Testing flight search...")
      (let [result (aor/agent-invoke
                    agent
                    [(UserMessage.
                      "I need to find flights from ZUR to JFK for March 15th")]
                    {:passenger-id "P123"})]
        (println "Customer:"
                 "I need to find flights from ZUR to JFK for March 15th")
        (println "Agent:" result)
        (println))

      ;; Test 2: Policy lookup
      (println "📋 Testing policy lookup...")
      (let [result (aor/agent-invoke
                    agent
                    [(UserMessage. "What is your baggage policy?")]
                    {:passenger-id "P124"})]
        (println "Customer:" "What is your baggage policy?")
        (println "Agent:" result)
        (println))

      ;; Test 3: Hotel search
      (println "🏨 Testing hotel search...")
      (let [result
            (aor/agent-invoke
             agent
             [(UserMessage.
               "I need a hotel in New York for March 15-17, preferably budget-friendly")]
             {:passenger-id "P125"})]
        (println
         "Customer:"
         "I need a hotel in New York for March 15-17, preferably budget-friendly")
        (println "Agent:" result)
        (println))

      (println "Customer Support Agent completed sample interactions!")
      agent)))
