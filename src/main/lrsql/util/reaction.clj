(ns lrsql.util.reaction
  "Utilities to support reactions."
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.reaction :as rs]
            [lrsql.spec.statement :as ss]
            [xapi-schema.spec :as xs]))

(s/fdef path->string
  :args (s/cat :path ::rs/path
               :qstring (s/? string?))
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
              (string? seg)
              (format "%s.\"%s\"" s seg)

              (nat-int? seg)
              (format "%s[%d]" s seg)

              :else
              (throw (ex-info "Invalid path segement"
                              {:type ::invalid-path-segment
                               :segment seg}))))
     s)))

(s/fdef statement-identity
  :args (s/cat :identity-paths ::rs/identity-paths
               :statement ::xs/statement)
  :ret (s/nilable
        ::rs/statement-identity))

(defn statement-identity
  "Given a vector of identity paths and a statement, return a map of paths to
  values. Return nil if any are missing or a collection is found."
  [identity-paths
   statement]
  (reduce
   (fn [m path]
     (if-some [found-val (get-in statement
                                 path)]
       (if (coll? found-val)
         (reduced nil)
         (assoc m path found-val))
       (reduced nil)))
   {}
   identity-paths))

(s/fdef add-reaction-metadata
  :args (s/cat :statement ::xs/statement
               :reaction-id uuid?
               :statement-id :statement/id)
  :ret ::xs/statement)

(defn add-reaction-metadata
  [statement reaction-id trigger-id]
  (vary-meta statement merge {::ss/reaction-id reaction-id
                              ::ss/trigger-id  trigger-id}))
