(ns lrsql.spec.common
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [next.jdbc.protocols :as jp])
  (:import [java.time Instant]))

;; UUIDs

(s/def ::primary-key uuid?)
(s/def ::statement-id uuid?)

;; Transactions

(defn transaction?
  [tx]
  (satisfies? jp/Transactable tx))

;; Exceptions
;; Predicate and generator code taken from:
;; https://github.com/yetanalytics/lrs/blob/master/src/main/com/yetanalytics/lrs/protocol.cljc#L15

(defn exception?
  [ex]
  (instance? Exception ex))

(def exception-gen-fn
  (sgen/fmap #(apply ex-info %)
             (sgen/tuple (sgen/string-ascii)
                         (s/gen map?))))

(s/def ::error
  (s/with-gen exception? exception-gen-fn))

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
