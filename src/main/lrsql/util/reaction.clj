(ns lrsql.util.reaction
  "Utilities to support reactions."
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.reaction :as rs]))

(s/fdef path->string
  :args (s/cat :path ::rs/path)
  :ret string?)

(defn path->string
  "Given a vector of keys and/or indices, return a JSONPath string suitable for
  SQL JSON access."
  ([path]
   (path->string path "$"))
  ([[seg & rpath] s]
   (if seg
     (recur rpath
            (cond
              ;; TODO: might need some escapes
              (or (keyword? seg)
                  (string? seg))
              (format "%s.%s" s (name seg))

              (nat-int? seg)
              (format "%s[%d]" s seg)

              :else
              (throw (ex-info "Invalid path segement"
                              {:type ::invalid-path-segment
                               :segment seg}))))
     s)))
