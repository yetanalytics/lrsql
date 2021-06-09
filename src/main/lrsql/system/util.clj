(ns lrsql.system.util
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]))

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
