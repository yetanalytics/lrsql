(ns lrsql.spec.common
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [next.jdbc.protocols :as jp]
            [clojure.core.async.impl.protocols :as ap])
  (:import [java.time Instant]))

;; UUIDs

(s/def ::primary-key uuid?)
(s/def ::statement-id uuid?)

;; Transactions

(defn transaction?
  [tx]
  (satisfies? jp/Transactable tx))

;; Exceptions

(defn exception?
  [ex]
  (instance? Exception ex))

(s/def ::error exception?)

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

;; Core.async channels
(s/def ::channel #(satisfies? ap/Channel %))

;; JSON

;; Like :xapi-schema.spec/any-json BUT allows simple keyword keys.
(s/def ::any-json
  (s/nilable
   (s/or :scalar
         (s/or :string
               string?
               :number
               (s/or :double
                     (s/double-in :infinite? false :NaN? false)
                     :int
                     int?)
               :boolean
               boolean?)
         :coll
         (s/or :map
               (s/map-of
                (s/or :string string?
                      :keyword simple-keyword?)
                ::any-json
                :gen-max 4)
               :vector
               (s/coll-of
                ::any-json
                :kind vector?
                :into []
                :gen-max 4)))))
