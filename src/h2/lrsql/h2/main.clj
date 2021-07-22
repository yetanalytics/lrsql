(ns lrsql.h2.main
  (:require [clojure.string :refer [lower-case]]
            [com.stuartsierra.component :as component]
            [lrsql.system :as system]
            [lrsql.h2.record :as ir])
  (:gen-class))

(def h2-interface (ir/map->H2Interface {}))

(defn- throw-bad-args [args]
  (throw (IllegalArgumentException.
          (format "Illegal main args: %s" (str args)))))

(defn- parse-persistent
  "Parse the values of the flags `-p` and `--persistent`. Throws an error
   if both are present and have different vals, or if the val is invalid."
  [?short-per ?long-per]
  (if (and (and ?short-per ?long-per)
           (not= (lower-case ?short-per) (lower-case ?long-per)))
    (throw-bad-args (format "-p %s, --persistent %s" ?short-per ?long-per))
    (let [per (or ?short-per ?long-per)]
      (case (lower-case per)
        "true"  true
        "false" false
        nil     false
        :else   (throw-bad-args per)))))

(defn -main
  "Main entrypoint for H2-backed LRSQL instances. Passing `-p true` or
   `--persistent true` will spin up a persistent H2 instance on disk;
   otherwise, an in-mem, ephemeral H2 instance will init instead."
  [& args]
  (let [{?short-per "-p" ?long-per "--persistent"} args
        persistent? (parse-persistent ?short-per ?long-per)
        profile     (if persistent? :prod-h2 :prod-h2-mem)]
    (-> (system/system h2-interface profile)
        component/start)))
