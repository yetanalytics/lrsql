(ns lrsql.ops.command.reaction
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.reaction :as rs]
            [lrsql.spec.common :refer [transaction?]]))

(s/fdef insert-reaction!
  :args (s/cat :bk rs/reaction-backend?
               :tx transaction?
               :input rs/insert-reaction-input-spec)
  :ret rs/insert-reaction-ret-spec)

(defn insert-reaction!
  "Insert a new reaction."
  [bk tx {:keys [primary-key] :as input}]
  (bp/-insert-reaction! bk tx input)
  {:result primary-key})

(s/fdef update-reaction!
  :args (s/cat :bk rs/reaction-backend?
               :tx transaction?
               :input rs/update-reaction-input-spec)
  :ret rs/update-reaction-ret-spec)

(defn update-reaction!
  "Update an existing reaction."
  [bk tx {:keys [reaction-id] :as input}]
  (let [result (bp/-update-reaction! bk tx input)]
    {:result (if (= 1 result)
               reaction-id
               :lrsql.reaction/reaction-not-found-error)}))

(s/fdef delete-reaction!
  :args (s/cat :bk rs/reaction-backend?
               :tx transaction?
               :input rs/delete-reaction-input-spec)
  :ret rs/delete-reaction-ret-spec)

(defn delete-reaction!
  "Delete a reaction."
  [bk tx {:keys [reaction-id] :as input}]
  (let [result (bp/-delete-reaction! bk tx input)]
    {:result (if (= 1 result)
               reaction-id
               :lrsql.reaction/reaction-not-found-error)}))

(s/fdef error-reaction!
  :args (s/cat :bk rs/reaction-backend?
               :tx transaction?
               :input rs/error-reaction-input-spec)
  :ret rs/error-reaction-ret-spec)

(defn error-reaction!
  "Set error on and deactivate a reaction."
  [bk tx {:keys [reaction-id] :as input}]
  (let [result (bp/-error-reaction! bk tx input)]
    {:result (if (= 1 result)
               reaction-id
               :lrsql.reaction/reaction-not-found-error)}))
