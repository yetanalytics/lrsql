(ns lrsql.ops.query.reaction
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.reaction :as rs]
            [cheshire.core :as json]
            [xapi-schema.spec :as xs]
            [clojure.java.io :as io]))

(def ops
  {:gt    ">"
   :lt    "<"
   :gte   ">="
   :lte   "<="
   :eq    "="
   :noteq "!="
   #_#_:contains
   :contains-str})

(s/fdef render-ref
  :args (s/cat :bk rs/reaction-backend?
               :col ::rs/condition-name
               :path ::rs/path)
  :ret ::rs/sqlvec)

(defn- render-ref
  "Render json references with optimizations for denorm fields"
  [bk col path]
  (case path
    [:timestamp]
    (bp/-snip-col bk {:col (format "%s.timestamp" (name col))})
    (bp/-snip-json-extract
     bk
     {:col  (format "%s.payload" (name col))
      :path path})))

(s/fdef render-condition
  :args (s/cat :bk rs/reaction-backend?
               :condition-name ::rs/condition-name
               :condition ::rs/condition)
  :ret ::rs/sqlvec)

(defn- render-condition
  [bk
   condition-name
   {and-conds :and
    or-conds  :or
    not-cond  :not
    :keys     [op path val ref]
    :as       condition}]
  (cond
    and-conds
    (bp/-snip-and bk {:clauses (mapv (partial render-condition bk condition-name)
                                     and-conds)})
    or-conds
    (bp/-snip-or bk {:clauses (mapv (partial render-condition bk condition-name)
                                    or-conds)})
    not-cond
    (bp/-snip-not bk {:clause (render-condition bk condition-name not-cond)})
    (and op path (or val ref))
    (let [op-sql (get ops op)]
      (when (nil? op-sql)
        (throw (ex-info "Invalid Operation"
                        {:type      ::invalid-operation
                         :operation op})))
      (bp/-snip-clause bk
                       {:left  (render-ref bk condition-name path)
                        :op    op-sql
                        :right (if val
                                 (bp/-snip-val bk {:val val})
                                 (let [{ref-condition :condition
                                        ref-path      :path} ref]
                                   (render-ref bk ref-condition ref-path)))}))
    :else (throw (ex-info "Invalid Condition"
                          {:type      ::invalid-condition
                           :condition condition}))))

(s/fdef query-reaction-sqlvec
  :args (s/cat :bk rs/reaction-backend?
               :input ::rs/input)
  :ret ::rs/sqlvec)

(defn- query-reaction-sqlvec
  [bk {:keys [conditions
              trigger-id]}]
  (let [condition-keys (keys conditions)]
    (bp/-snip-query-reaction
     bk
     {:select (mapv
               (fn [k]
                 [(format "%s.payload" (name k)) (name k)])
               condition-keys)
      :from   (mapv
               (fn [k]
                 ["xapi_statement" (name k)])
               condition-keys)
      :where
      (bp/-snip-and
       bk
       {:clauses
        (into [(bp/-snip-or
                bk
                {:clauses (mapv
                           (fn [k]
                             (bp/-snip-clause
                              bk
                              {:left  (bp/-snip-col
                                       bk {:col (format "%s.statement_id" (name k))})
                               :op    "="
                               :right (bp/-snip-val bk {:val trigger-id})}))
                           condition-keys)})]
              (map
               (fn [[condition-name condition]]
                 (render-condition
                  bk
                  condition-name
                  condition))
               conditions))})})))

(s/fdef query-reaction
  :args (s/cat :bk rs/reaction-backend?
               :tx transaction?
               :input ::rs/input)
  :ret (s/every (s/map-of ::rs/condition-name ::xs/statement)))

(defn query-reaction
  "For the given reaction input, return matching statements named for conditions."
  [bk tx input]
  (mapv
   (fn [row]
     (into {}
           (for [[condition-name statement-bs] row]
             [condition-name
              (with-open [r (io/reader statement-bs)]
                (json/parse-stream r))])))
   (bp/-query-reaction bk tx
                       {:sql (query-reaction-sqlvec bk input)})))
