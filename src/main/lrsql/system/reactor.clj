(ns lrsql.system.reactor
  (:require [com.stuartsierra.component    :as component]
            [next.jdbc                     :as jdbc]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [clojure.tools.logging         :as log]
            [lrsql.reaction.protocol       :as rp]
            [lrsql.init.reaction           :as react-init]
            [lrsql.input.reaction          :as react-input]
            [lrsql.ops.command.reaction    :as react-cmd]
            [lrsql.ops.query.reaction      :as react-q]))

(defrecord Reactor [backend
                    lrs
                    reaction-executor
                    reaction-cache]
  component/Lifecycle
  (start [this]
    (-> this
        (assoc :reaction-cache (react-init/new-reaction-cache))
        rp/-start-executor))
  (stop [this]
    (react-init/shutdown-reactions!
     (:reaction-channel lrs)
     reaction-executor)
    (assoc this
           :backend nil
           :lrs nil
           :reaction-executor nil))
  rp/StatementReactor
  (-start-executor [this]
    (if-let [reaction-channel (:reaction-channel lrs)]
      (assoc this
             :reaction-executor
             (react-init/reaction-executor reaction-channel this))
      this))
  (-get-reactions [_ tx ttl]
    (or
     ;; get from cache
     (when (not (zero? ttl))
       (react-init/validate-cache! reaction-cache ttl))
     ;; get from db and set in cache
     (do (log/debug "querying for reactions...")
         (:reactions
          (reset! reaction-cache
                  (react-init/cache-reactions
                   (react-q/query-active-reactions
                    backend tx)))))))
  (-react-to-statement [this statement-id]
    (let [conn (-> lrs
                   :connection
                   :conn-pool)
          statement-results
          (jdbc/with-transaction [tx conn]
            (let [reactions (rp/-get-reactions this tx 10000)] ;; TODO: Make this config
              (if (empty? reactions)
                []
                (reduce
                 (fn [acc {:keys [reaction-id
                                  error]
                           :as   result}]
                   (if error
                     (let [input (react-input/error-reaction-input
                                  reaction-id error)]
                       (react-cmd/error-reaction! backend tx input)
                       (reset! reaction-cache nil)
                       acc)
                     (conj acc (select-keys result [:statement :authority]))))
                 []
                 (:result
                  (react-q/query-statement-reactions
                   backend tx {:reactions  reactions
                               :trigger-id statement-id}))))))]
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
