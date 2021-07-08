(ns lrsql.spec.common
  (:require [clojure.spec.alpha :as s]
            [next.jdbc.protocols :as jp]))

;; UUIDs

(s/def ::primary-key uuid?)
(s/def ::statement-id uuid?)

;; Transactions

(defn transaction?
  [tx]
  (satisfies? jp/Transactable tx))
