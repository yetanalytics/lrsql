(ns lrsql.init.git-data
  (:require [clojure.edn :as edn]
            [clojure.string]
            [clojure.java.shell :refer [sh]]))

(defn read-version []
  (try (edn/read-string (slurp "lrsql/config/git-details.edn"))
       (catch Exception _
         (try (clojure.string/trim (str "DEV-" (:out (sh "git" "describe" "--tags"))))
              (catch Exception _ "DEV")))))
