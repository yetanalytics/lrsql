(ns lrsql.system.util
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [ring.util.codec :refer [form-encode]]
            [lrsql.spec.config :refer [db-prop-regex]]))

(defmacro assert-config
  [spec component-name config]
  `(when-some [err# (s/explain-data ~spec ~config)]
     (do
       (log/errorf "Configuration errors:\n%s"
                   (with-out-str (s/explain-out err#)))
       (throw (ex-info (format "Invalid %s configuration!"
                               ~component-name)
                       {:type ::invalid-config
                        :error-data err#})))))

(defn- remove-quotes
  [s]
  (cond-> s
    (re-matches #"(?:\".*\")|(?:'.*')" s)
    (subs 1 (-> s count dec))))

(defn parse-db-props
  "Given `prop-str` of the form \"key=value&key=value...\", return a
   keyword-key map of property names to values."
  [prop-str]
  (let [grps (->> prop-str
                  (re-matches db-prop-regex)
                  rest
                  (filter some?))]
    (loop [g grps
           m (transient {})]
      (if-not (empty? g)
        (let [[[k v] g'] (split-at 2 g)]
          (recur g' (assoc! m (keyword k) (form-encode (remove-quotes v)))))
        (persistent! m)))))
