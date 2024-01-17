(ns lrsql.ops.util.reaction
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.reaction :as rs]
            [lrsql.util.reaction :as ru]
            [xapi-schema.spec :as-alias xs]))

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
  (bp/-snip-col bk {:col (format "%s.%s"
                                 (ru/encode-condition-name condition-name)
                                 col)}))

(s/fdef render-ref
  :args (s/cat :bk rs/reaction-backend?
               :condition-name ::rs/condition-name
               :path ::rs/path
               :kwargs (s/keys* :opt-un [::rs/datatype]))
  :ret ::rs/sqlvec)

(defn- render-ref
  "Render json references with optimizations for denorm fields"
  [bk condition-name path & {:keys [datatype]}]
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
     {:col  (format "%s.payload" (ru/encode-condition-name condition-name))
      :path path
      :datatype datatype})))

(def basic-stmt-types
  {"result" {"success"    :bool
             "completion" :bool
             "score" {"scaled" :dec
                      "raw"    :dec
                      "min"    :dec
                      "max"    :dec}}})

(def xapi-type-map
  (assoc basic-stmt-types "object" basic-stmt-types))

(defn- json-type
  [val]
  (cond
    (boolean? val) :bool
    (integer? val) :int
    (number? val)  :dec
    (nil? val)     :json
    :else          :string))

(defn- infer-type
  [path val]
  ;;covers result, context, substmt result, substmt context
  (if (some #(= % "extensions") [(get path 1) (get path 2)])
    ;; custom value type, or string if value null
    (json-type val)
    ;;xapi-spec type
    (or (get-in xapi-type-map path)
        :string)))



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
                         (render-ref bk ref-condition ref-path
                                     :datatype (infer-type ref-path nil))))]
      (case op
        ;; Clause special cases
        "contains"
        (bp/-snip-contains bk
                           {:col   (format
                                    "%s.payload"
                                    (ru/encode-condition-name condition-name))
                            :path  path
                            :right right-snip
                            :datatype  (infer-type path val)})
        (let [op-sql (get ops op)]
          (when (nil? op-sql)
            (throw (ex-info "Invalid Operation"
                            {:type      ::invalid-operation
                             :operation op})))
          (bp/-snip-clause bk
                           {:left  (render-ref bk condition-name path
                                               :datatype (infer-type path val))
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
                      {:left  (render-ref
                               bk condition-name path
                               :datatype (infer-type path ident-val))
                       :op    "="
                       :right (bp/-snip-val bk {:val ident-val})})))}))

(s/fdef render-ground
  :args (s/cat :bk rs/reaction-backend?
               :condition-names (s/every ::rs/condition-name)
               :trigger-id ::rs/trigger-id
               :trigger-stored ::rs/trigger-stored)
  :ret ::rs/sqlvec)

(defn- render-ground
  [bk condition-names trigger-id trigger-stored]
  (bp/-snip-and
   bk
   {:clauses
    (conj
     ;; ensure that all stmts are stored at or before trigger stmt stored time
     (mapv (fn [k]
             (bp/-snip-clause
              bk
              {:left  (render-col bk k "stored")
               :op    "<="
               :right (bp/-snip-val bk {:val trigger-stored})}))
           condition-names)
     ;; ensure that at least one statement is the trigger stmt
     (bp/-snip-or
      bk
      {:clauses (mapv
                 (fn [k]
                   (bp/-snip-clause
                    bk
                    {:left  (render-col bk k "statement_id")
                     :op    "="
                     :right (bp/-snip-val bk {:val trigger-id})}))
                 condition-names)}))}))

(s/fdef query-reaction-sqlvec
  :args (s/cat :bk rs/reaction-backend?
               :input rs/query-reaction-input-spec)
  :ret ::rs/sqlvec)

(defn- query-reaction-sqlvec
  [bk
   {{:keys [conditions]} :ruleset
    :keys                [trigger-id
                          trigger-stored
                          statement-identity]}]
  (let [condition-names (map name (keys conditions))]
    (bp/-snip-query-reaction
     bk
     {:select (mapv
               (fn [cn]
                 (let [ecn (ru/encode-condition-name cn)]
                   [(format "%s.payload" ecn) ecn]))
               condition-names)
      :from   (mapv
               (fn [cn]
                 ["xapi_statement" (ru/encode-condition-name cn)])
               condition-names)
      :where
      (bp/-snip-and
       bk
       {:clauses
        (into []
              (concat
               (when (seq statement-identity)
                 [(render-identity bk condition-names statement-identity)])
               [(render-ground bk condition-names trigger-id trigger-stored)]
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
     (reduce-kv
      (fn [m k v]
        (assoc m
               (-> k
                   name
                   ru/decode-condition-name
                   keyword)
               v))
      {}
      row))
   (bp/-query-reaction
    bk tx
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
      :ruleset (ru/stringify-template ruleset)})
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
                  :reaction_id
                  (bp/-query-reaction-history bk tx input)))})
