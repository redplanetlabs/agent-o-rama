(ns com.rpl.agent-o-rama.ui.queries
  (:require [uix.core :as uix :refer [defhook]]
            [uix.re-frame :refer [use-subscribe]]
            [re-frame.core :as rf]
            [re-frame.query :as rfq]
            [com.rpl.agent-o-rama.ui.common :as common]
            [com.rpl.specter :as s]))

(def has-more-pages? common/has-more-pages?)

(defhook use-rpc-query
  "HTTP RPC query via `re-frame.query` (same transport as `rfq/reg-query`).

  Options:
  - :rfq-key — Registered query keyword (e.g. `::rpc-datasets/get-all!!`)
  - :params — Argument map for `query-fn` / cache key
  - :enabled? — When false, skips fetch and uses `{:skip? true}` (default true)

  Returns:
  - :data — Response body from the RPC (what the server puts in the success payload)
  - :loading? — True during initial load (`:loading` or `:idle` status)
  - :fetching? — True while a request is in flight (including background refetch)
  - :error — Error value when status is `:error`
  - :refetch — Dispatches `:re-frame.query/refetch-query`"
  [{:keys [rfq-key params enabled?]
    :or {enabled? true}}]
  (let [params-sig (pr-str params)
        query-state (use-subscribe [:re-frame.query/query rfq-key params
                                    {:skip? (not enabled?)}])
        {:keys [status data error fetching?]} query-state
        loading? (#{:loading :idle} status)
        refetch (uix/use-callback
                 (fn []
                   (when enabled?
                     (rf/dispatch [:re-frame.query/refetch-query rfq-key params])))
                 [enabled? rfq-key params params-sig])]
    {:data data
     :loading? loading?
     :fetching? (boolean fetching?)
     :error (when (= status :error) error)
     :refetch refetch}))

(defhook use-infinite-rpc-query
  "Paginated lists backed by `re-frame.query` infinite queries (HTTP RPC).

  Uses the active `:re-frame.query/infinite-query` subscription (not the passive
  `-state` variant) so queries are marked active — required for
  `:re-frame.query/invalidate-tags` to refetch. When `:enabled?` is false, passes
  `{:skip? true}` so no fetch runs and the query is not marked active.

  Options:
  - :rfq-key — Registered query keyword (e.g. `::rpc-datasets/get-all-inf!!`)
  - :params — Base param map (module id, filters, etc.); `:limit` is merged from :page-size
  - :page-size — Passed to the server as `:limit` (default 20)
  - :enabled? — When false, skips subscription side effects (no fetch)
  - :flatten-items-fn — `(fn [pages] -> vector)` to merge page maps into row data

  Returns the same keys as `use-paginated-query` for drop-in replacement."
  [{:keys [rfq-key params page-size enabled? flatten-items-fn]
    :or {page-size 20
         enabled? true
         flatten-items-fn (fn [pages]
                            (vec (apply concat (map :items pages))))}}]
  (let [params' (assoc params :limit page-size)
        params-sig (pr-str params')
        query-state (use-subscribe [:re-frame.query/infinite-query rfq-key params'
                                    {:skip? (not enabled?)}])]
    (let [{:keys [status data error fetching-next?]} query-state
          pages (:pages data)
          items (if (seq pages)
                  (flatten-items-fn pages)
                  [])
          has-next? (boolean (:has-next? data))
          loading? (#{:loading :idle} status)
          load-more (uix/use-callback
                     (fn []
                       (when (and enabled? has-next?)
                         (rfq/fetch-next-page rfq-key params')))
                     [enabled? has-next? rfq-key params' params-sig])
          refetch (uix/use-callback
                   (fn []
                     (when enabled?
                       (rf/dispatch [:re-frame.query/refetch-infinite-query rfq-key params'])))
                   [enabled? rfq-key params' params-sig])]
      {:data items
       :isLoading (and loading? (empty? items))
       :isFetchingMore (boolean fetching-next?)
       :hasMore has-next?
       :error (when (= status :error) error)
       :loadMore load-more
       :refetch refetch})))
