(ns lrsql.init.git-data
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string]))

(defn read-version []
  (try (edn/read-string (slurp (io/resource "lrsql/config/git-details.edn")))
       (catch Exception _
         (try
           (let [hash (:out (sh "git" "rev-parse" "HEAD"))]
             (->> hash
                  (take 7)
                  (apply str)
                  (str "DEV-")
                  clojure.string/trim))
              (catch Exception _ "DEV")))))
