(ns lrsql.spec.common
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [next.jdbc.protocols :as jp]
            [lrsql.interface.protocol :as ip])
  (:import [java.time Instant]))

;; UUIDs

(s/def ::primary-key uuid?)
(s/def ::statement-id uuid?)

;; DB Interface

(defn insert-interface?
  [int]
  (satisfies? ip/InsertInterface int))

(defn update-interface?
  [int]
  (satisfies? ip/UpdateInterface int))

(defn delete-interface?
  [int]
  (satisfies? ip/DeleteInterface int))

(defn query-interface?
  [int]
  (satisfies? ip/QueryInterface int))

;; Transactions

(defn transaction?
  [tx]
  (satisfies? jp/Transactable tx))

;; Instants

;; Need to use this since `inst?` also returns true for java.util.Date
;; instances, not just java.time.Instant ones.

(def instant-spec
  (s/with-gen
    (partial instance? Instant)
    (fn []
      (sgen/fmap
       #(Instant/ofEpochSecond %)
       (sgen/large-integer* {:min 0})))))
