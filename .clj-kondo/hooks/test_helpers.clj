(ns hooks.test-helpers
  (:require
   [clj-kondo.hooks-api :as api]))

(defn letlocals
  [{:keys [node]}]
  (let [children (rest (:children node))
        ;; Split into bindings and last expression
        [bindings-and-exprs last-item] (split-at (dec (count children)) children)
        
        ;; Check if last item is a (bind ...) form
        last-binding? (and (api/list-node? (first last-item))
                          (= 'bind (api/sexpr (first (:children (first last-item))))))
        
        last-expr (if last-binding?
                   (last (:children (first last-item)))
                   (first last-item))
        
        ;; Process bindings: (bind sym expr) -> [sym expr], expr -> [_ expr]
        let-bindings (vec (mapcat (fn [item]
                                   (if (and (api/list-node? item)
                                           (= 'bind (api/sexpr (first (:children item)))))
                                     ;; (bind sym expr) -> [sym expr]
                                     (let [bind-children (rest (:children item))]
                                       [(first bind-children) (second bind-children)])
                                     ;; expr -> [_ expr]
                                     [(api/token-node '_) item]))
                                 bindings-and-exprs))]
    {:node (api/list-node
            (list
             (api/token-node 'let)
             (api/vector-node let-bindings)
             last-expr))}))
