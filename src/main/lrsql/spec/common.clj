(ns lrsql.spec.common
  (:require [clojure.spec.alpha :as s]))

(s/def ::primary-key uuid?)
(s/def ::statement-id uuid?)
