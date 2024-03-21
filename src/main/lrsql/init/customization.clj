(ns lrsql.init.customization
  "Utilities for white labeling/customization overrides"
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]))

(def lang-path
  "lrsql/customization/language.json")

(def custom-language-map
  "The language map function to render customized admin frontend language maps"
  (-> lang-path
      io/resource
      slurp
      json/parse-string))

