(ns lrsql.ops.util.reaction
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.reaction :as rs]
            [lrsql.util :as u]
            [lrsql.util.reaction :as ru]
            [cheshire.core :as json]
            [xapi-schema.spec :as-alias xs]
            [clojure.java.io :as io]))

(def ops
  {"gt"    ">"
   "lt"    "<"
   "gte"   ">="
   "lte"   "<="
   "eq"    "="
   "noteq" "!="
   "like"  "LIKE"})

(s/fdef render-col
  :args (s/cat :bk rs/reaction-backend?
               :condition-name ::rs/condition-name
               :col string?)
  :ret ::rs/sqlvec)

(defn- render-col
  [bk condition-name col]
  (bp/-snip-col bk {:col (format "%s.%s" condition-name col)}))

(s/fdef render-ref
  :args (s/cat :bk rs/reaction-backend?
               :condition-name ::rs/condition-name
               :path ::rs/path)
  :ret ::rs/sqlvec)

(defn- render-ref
  "Render json references with optimizations for denorm fields"
  [bk condition-name path]
  (case path
    ["timestamp"]
    (render-col bk condition-name "timestamp")
    ["stored"]
    (render-col bk condition-name "stored")
    ["verb" "id"]
    (render-col bk condition-name "verb_iri")
    ["context" "registration"]
    (render-col bk condition-name "registration")
    (bp/-snip-json-extract
     bk
     {:col  (format "%s.payload" condition-name)
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
        "contains"
        (bp/-snip-contains bk
                           {:col   (format "%s.payload" condition-name)
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
               :condition-names (s/every ::rs/condition-name)
               :trigger-id ::rs/trigger-id)
  :ret ::rs/sqlvec)

(defn- render-ground
  [bk condition-names trigger-id]
  (bp/-snip-or
   bk
   {:clauses (mapv
              (fn [k]
                (bp/-snip-clause
                 bk
                 {:left  (render-col bk k "statement_id")
                  :op    "="
                  :right (bp/-snip-val bk {:val trigger-id})}))
              condition-names)}))

(s/fdef query-reaction-sqlvec
  :args (s/cat :bk rs/reaction-backend?
               :input rs/query-reaction-input-spec)
  :ret ::rs/sqlvec)

(defn- query-reaction-sqlvec
  [bk
   {{:keys [conditions]} :ruleset
    :keys                [trigger-id
                          statement-identity]}]
  (let [condition-names (map name (keys conditions))]
    (bp/-snip-query-reaction
     bk
     {:select (mapv
               (fn [cn]
                 [(format "%s.payload" cn) cn])
               condition-names)
      :from   (mapv
               (fn [cn]
                 ["xapi_statement" cn])
               condition-names)
      :where
      (bp/-snip-and
       bk
       {:clauses
        (into []
              (concat
               (when (seq statement-identity)
                 [(render-identity bk condition-names statement-identity)])
               [(render-ground bk condition-names trigger-id)]
               (map
                (fn [[condition-key condition]]
                  (render-condition
                   bk
                   (name condition-key)
                   condition))
                conditions)))})})))

(s/fdef query-reaction
  :args (s/cat :bk rs/reaction-backend?
               :tx transaction?
               :input rs/query-reaction-input-spec)
  :ret rs/query-reaction-ret-spec)

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

(s/fdef query-active-reactions
  :args (s/cat :bx rs/reaction-backend?
               :tx transaction?)
  :ret rs/query-active-reactions-ret-spec)

(defn query-active-reactions
  "Return all currently active reactions."
  [bk tx]
  (mapv
   (fn [{:keys [id ruleset]}]
     {:id      id
      :ruleset (ru/deserialize-ruleset ruleset)})
   (bp/-query-active-reactions bk tx)))

(s/fdef query-reaction-history
  :args (s/cat :bx rs/reaction-backend?
               :tx transaction?
               :input rs/query-reaction-history-input-spec)
  :ret rs/query-reaction-history-ret-spec)

(defn query-reaction-history
  "Given a statement ID, return any reactions leading up to the issuance of that
  statement."
  [bk tx input]
  {:result (into #{}
                 (map
                  (comp u/str->uuid :reaction_id)
                  (bp/-query-reaction-history bk tx input)))})
