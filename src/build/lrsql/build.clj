(ns lrsql.build
  "Build utils for LRSQL artifacts"
  (:require [hf.depstar :as depstar]
            [clojure.java.io :as io])
  (:import [java.io File]))

(defn uber
  "All backends, as an uberjar"
  [params]
  (-> {:jar "target/bundle/lrsql.jar"
       :aot true
       :aliases [:db-h2 :db-sqlite]
       :compile-ns :all
       :no-pom true
       :exclude ["^lrsql.*clj$"
                 "^.*yetanalytics.*clj$"
                 ;; don't ship keystores
                 "^.*jks$"]
       :main-class "clojure.main"}
      (merge params)
      (depstar/uberjar)))
