(ns lrsql.build
  "Build utils for LRSQL artifacts"
  (:require [hf.depstar :as depstar]))

(def uber-params
  {:jar "target/bundle/lrsql.jar"
   :aot true
   :aliases [:db-h2 :db-sqlite]
   :compile-ns :all
   :no-pom true
   :exclude ["^lrsql.*clj$"
             "^.*sql$"
             "^.*yetanalytics.*clj$"
             ;; don't ship keystores
             "^.*jks$"]})

(defn uber
  "All backends, as an uberjar"
  [params]
  (-> uber-params
      (merge params)
      (depstar/uberjar)))

(defn uber-manual
  "All backends, as an uberjar, manually"
  [params]
  (-> (merge uber-params
             {:target-dir "target"})
      (merge params)
      (depstar/aot)
      (assoc :jar-type :uber)
      (depstar/build)))
