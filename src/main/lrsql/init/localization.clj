(ns lrsql.init.localization
  "Utilities for white labeling/localization overrides"
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]))

(def lang-path
  "lrsql/localization/language.json")

(defn custom-language-map*
  "The language map function to render customized admin frontend language maps"
  []
  (-> lang-path
      io/resource
      slurp
      json/parse-string))

(def custom-language-map
  (memoize custom-language-map*))
