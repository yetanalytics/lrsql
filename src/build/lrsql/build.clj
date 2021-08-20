(ns lrsql.build
  "Build utils for LRSQL artifacts"
  (:require [hf.depstar :as depstar]
            [clojure.java.io :as io])
  (:import [net.sf.launch4j Log Builder]
           [net.sf.launch4j.config ConfigPersister]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Depstar
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def uber-params
  {:jar "target/bundle/lrsql.jar"
   :aot true
   :aliases [:db-h2 :db-sqlite :db-postgres]
   :compile-ns :all
   :no-pom true
   :exclude ["^lrsql.*clj$"
             "^.*sql$"
             "^.*yetanalytics.*clj$"
             ;; don't ship crypto
             "^.*jks$"
             "^.*key$"
             "^.*pem$"]})

(defn uber
  "All backends, as an uberjar"
  [params]
  (-> uber-params
      (merge params)
      (depstar/uberjar)))

(defn uber-manual
  "All backends, as an uberjar, manually
  Ensure that target dir is empty prior to use"
  [params]
  (-> (merge uber-params
             {:target-dir "target"})
      (merge params)
      (depstar/aot)
      (assoc :jar-type :uber)
      (depstar/build)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Launch4j
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def launch4j-base-dir
  (.getCanonicalFile (io/file "launch4j")))

(defn- set-launch4j-config!
  [launch4j-config]
  (doto (ConfigPersister/getInstance)
    (.load (.getCanonicalFile (io/file launch4j-config)))))

(defn- build-launch4j-exe!
  []
  (.build
   (Builder. (Log/getConsoleLog) launch4j-base-dir)))

(defn launch4j
  "Build the launch4j executable specified by the XML file at the
   `config` path."
  [{:keys [config]}]
  (set-launch4j-config! config)
  (build-launch4j-exe!))
