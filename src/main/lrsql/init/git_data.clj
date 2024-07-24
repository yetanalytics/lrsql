(ns lrsql.init.git-data
  (:require [cheshire.core :as json]))

(defn read-git []
  (let [read-text (or (try (slurp "target/bundle/git-details.json")
                           (catch Exception e
                             nil))
                      (try (slurp "dev-resources/git-details.json")
                           (catch Exception e
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
      (first (clojure.string/split (get git-data "last_tag" "") #"\-" ))))

(defn read-last-version []
  (to-last-version (read-git)))
