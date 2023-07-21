(ns lrsql.ops.query.reaction
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.reaction :as rs]
            [lrsql.util.reaction :as ru]
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
   :like  "LIKE"})

(s/fdef render-col
  :args (s/cat :bk rs/reaction-backend?
               :condition-name ::rs/condition-name
               :col string?)
  :ret ::rs/sqlvec)

(defn- render-col
  [bk condition-name col]
  (bp/-snip-col bk {:col (format "%s.%s" (name condition-name) col)}))

(s/fdef render-ref
  :args (s/cat :bk rs/reaction-backend?
               :condition-name ::rs/condition-name
               :path ::rs/path)
  :ret ::rs/sqlvec)

(defn- render-ref
  "Render json references with optimizations for denorm fields"
  [bk condition-name path]
  (case path
    [:timestamp]
    (render-col bk condition-name "timestamp")
    [:stored]
    (render-col bk condition-name "stored")
    [:verb :id]
    (render-col bk condition-name "verb_iri")
    [:context :registration]
    (render-col bk condition-name "registration")
    (bp/-snip-json-extract
     bk
     {:col  (format "%s.payload" (name condition-name))
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
    (let [right-snip (if val
                       (bp/-snip-val bk {:val val})
                       (let [{ref-condition :condition
                              ref-path      :path} ref]
                         (render-ref bk ref-condition ref-path)))]
      (case op
        ;; Clause special cases
        :contains
        (bp/-snip-contains bk
                           {:col   (format "%s.payload" (name condition-name))
                            :path  path
                            :right right-snip})
        (let [op-sql (get ops op)]
          (when (nil? op-sql)
            (throw (ex-info "Invalid Operation"
                            {:type      ::invalid-operation
                             :operation op})))
          (bp/-snip-clause bk
                           {:left  (render-ref bk condition-name path)
                            :op    op-sql
                            :right right-snip}))))
    :else (throw (ex-info "Invalid Condition"
                          {:type      ::invalid-condition
                           :condition condition}))))

(s/fdef render-identity
  :args (s/cat :bk rs/reaction-backend?
               :condition-keys (s/every ::rs/condition-name)
               :statement-identity (s/map-of ::rs/path string?))
  :ret ::rs/sqlvec)

(defn- render-identity
  [bk condition-keys statement-identity]
  (bp/-snip-and
   bk
   {:clauses (into []
                   (for [condition-name   condition-keys
                         [path ident-val] statement-identity]
                     (bp/-snip-clause
                      bk
                      {:left  (render-ref bk condition-name path)
                       :op    "="
                       :right (bp/-snip-val bk {:val ident-val})})))}))

(s/fdef render-ground
  :args (s/cat :bk rs/reaction-backend?
               :condition-keys (s/every ::rs/condition-name)
               :trigger-id :statement/id)
  :ret ::rs/sqlvec)

(defn- render-ground
  [bk condition-keys trigger-id]
  (bp/-snip-or
   bk
   {:clauses (mapv
              (fn [k]
                (bp/-snip-clause
                 bk
                 {:left  (render-col bk (name k) "statement_id")
                  :op    "="
                  :right (bp/-snip-val bk {:val trigger-id})}))
              condition-keys)}))

(s/fdef query-reaction-sqlvec
  :args (s/cat :bk rs/reaction-backend?
               :input ::rs/input
               :trigger-id :statement/id
               :statement-identity ::rs/statement-identity)
  :ret ::rs/sqlvec)

(defn- query-reaction-sqlvec
  [bk
   {{:keys [conditions]} :ruleset
    :keys                [trigger-id
                          statement-identity]}]
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
        (into []
              (concat
               (when (seq statement-identity)
                 [(render-identity bk condition-keys statement-identity)])
               [(render-ground bk condition-keys trigger-id)]
               (map
                (fn [[condition-name condition]]
                  (render-condition
                   bk
                   condition-name
                   condition))
                conditions)))})})))

(s/fdef query-reaction
  :args (s/cat :bk rs/reaction-backend?
               :tx transaction?
               :input ::query-reaction-input)
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
                       {:sql (query-reaction-sqlvec
                              bk input)})))
