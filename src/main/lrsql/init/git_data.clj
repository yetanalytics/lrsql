(ns lrsql.init.git-data
  (:require [cheshire.core :as json]
            [clojure.string]))

(defn read-git []
  (let [read-text (or (try (slurp "target/bundle/git-details.json")
                           (catch Exception _
                             nil))
                      (try (slurp "dev-resources/git-details.json")
                           (catch Exception _
                             nil)))]
    (json/parse-string read-text)))

(defn- to-version [git-data]
  (let [{:strs [tag]} git-data]
        (if (not= tag "")
      tag
      nil)))

(defn read-version []
  (to-version (read-git)))

(defn- to-last-version [git-data]
  (or (to-version git-data)
      (-> git-data (get "last_tag" "")
          (clojure.string/split #"\-")
          first)))

(defn read-last-version []
  (to-last-version (read-git)))
