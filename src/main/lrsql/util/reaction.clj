(ns lrsql.util.reaction
  "Utilities to support reactions."
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [lrsql.spec.common :as cs]
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
               :trigger-id uuid?)
  :ret ::xs/statement)

(defn add-reaction-metadata
  [statement reaction-id trigger-id]
  (vary-meta statement merge {::ss/reaction-id reaction-id
                              ::ss/trigger-id  trigger-id}))

(s/fdef generate-statement
  :args (s/cat :cond-map (s/map-of simple-keyword?
                                   ::xs/statement)
               :template ::xs/any-json)
  :ret ::xs/statement)

(defn generate-statement [cond->statement template]
  (walk/postwalk #(if (and (map? %)
                           (= (key (first %)) "$templatePath"))
                    (let [input-path (val (first %))
                          path (update input-path 0 keyword)
                          result (get-in cond->statement path :not-found)]
                      (case result
                        :not-found (throw (ex-info (str "No value found at " input-path)
                                                   {:path input-path
                                                    :type ::invalid-path}))
                        result))
                    %)
                 template))

(s/fdef stringify-template
  :args (s/cat :raw-ruleset ::cs/any-json)
  :ret ::rs/ruleset)

(defn stringify-template
  "On read, the reaction template has keyword keys. Stringify them!"
  [raw-ruleset]
  (update raw-ruleset :template walk/stringify-keys))
