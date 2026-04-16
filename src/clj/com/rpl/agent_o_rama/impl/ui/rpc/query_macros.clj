(ns com.rpl.agent-o-rama.impl.ui.rpc.query-macros)

(defmacro defrpc-query
  "Expand to a top-level `(rfq/reg-query ...)` (no `def`).

  Default `:query-fn` is `(fn [params] {:rpc/id query-kw :payload params})`.
  Pass `:query-fn` in `opts` to override. Other `rfq/reg-query` keys merge on top.

  `opts` must be a literal map (so the macro can merge at expand time)."
  [query-kw opts]
  `(~'rfq/reg-query ~query-kw
    ~(merge {:query-fn `(fn [params#]
                          {:rpc/id ~query-kw
                           :payload params#})}
            opts)))
