(ns lrsql.system.util
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [ring.util.codec :refer [form-encode form-decode]]))

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

(defn parse-db-props
  "Given `prop-str` of the form \"key=value&key=value...\", return a
   keyword-key map of property names to values."
  [prop-str]
  (reduce-kv (fn [m k v] (assoc m (keyword k) (form-encode v)))
             {}
             (form-decode prop-str)))
