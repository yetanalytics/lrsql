(ns lrsql.util.path
  "Utilities for property paths to query data from Statements.
   Property path specs are defined in the lrs-reactions library."
  (:require [clojure.spec.alpha :as s]
            [clojure.string     :as cstr]
            [com.yetanalytics.lrs-reactions.spec :as rs]))

(s/fdef path->jsonpath-string
  :args (s/cat :path ::rs/path
               :qstring (s/? string?))
  :ret string?)

(defn path->jsonpath-string
  "Given a vector of keys and/or indices, return a JSONPath string suitable for
  SQL JSON access."
  ([path]
   (path->jsonpath-string path "$"))
  ([[seg & rpath] s]
   (if seg
     (recur rpath
            (cond
              (string? seg)
              (format "%s.\"%s\"" s seg)

              (nat-int? seg)
              (format "%s[%d]" s seg)

              :else
              (throw (ex-info "Invalid path segement"
                              {:type ::invalid-path-segment
                               :segment seg}))))
     s)))

(s/fdef path->csv-header
  :args (s/cat :path ::rs/path)
  :ret string?)

(defn path->csv-header
  "Given a vector of keys and/or indices, return a string of the keys
   separated by underscores, suitable for use as CSV headers."
  [path]
  (cstr/join "_" path))
