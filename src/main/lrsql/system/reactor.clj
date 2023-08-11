(ns lrsql.system.reactor
  (:require [com.stuartsierra.component    :as component]
            [next.jdbc                     :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.reaction.protocol       :as rp]
            [lrsql.input.reaction          :as react-input]
            [lrsql.ops.command.reaction    :as react-cmd]
            [lrsql.ops.query.reaction      :as react-q]))

(defrecord Reactor [backend
                    lrs]
  component/Lifecycle
  (start [this]
    this)
  (stop [this] this)
  rp/StatementReactor
  (-react-to-statement [_ statement-id]
    (let [conn (-> lrs
                   :connection
                   :conn-pool)
          statement-results
          (jdbc/with-transaction [tx conn]
            (reduce
             (fn [acc {:keys [reaction-id
                              error]
                       :as   result}]
               (if error
                 (let [input (react-input/error-reaction-input
                              reaction-id error)]
                   (react-cmd/error-reaction! backend tx input)
                   acc)
                 (conj acc (select-keys result [:statement :authority]))))
             []
             (:result
              (react-q/query-statement-reactions
               backend tx {:trigger-id statement-id}))))]
      ;; Submit statements one at a time with varying authority
      {:statement-ids
       (reduce
        (fn [acc {:keys [statement authority]}]
          (into acc
                (:statement-ids
                 (lrsp/-store-statements
                  lrs
                  {:agent  authority
                   :scopes #{:scope/statements.write}}
                  [statement]
                  []))))
        []
        statement-results)})))
