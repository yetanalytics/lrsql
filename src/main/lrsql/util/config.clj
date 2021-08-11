(ns lrsql.util.config
  "Functions for working with system and user configuration"
  (:require [cheshire.core :as cjson]
            [clojure.java.io :as io]
            [camel-snake-kebab.core :as csk])
  (:import [java.io File]))

(defn merge-user-config
  "Given a static config map from aero and a path where a user config json file
  might reside, deep merge in valid json."
  [{:keys [user-config-json]
    :as   static-config}]
  (let [;; place handle on the config file at path
        ^File config-file (io/file user-config-json)]
    (merge-with
     merge
     static-config
     ;; merge with user configuration if one is provided
     (when (.exists config-file)
       (try
         (with-open [rdr (io/reader config-file)]
           (cjson/parse-stream-strict rdr csk/->kebab-case-keyword))
         (catch Exception ex
           (throw
            (ex-info "Invalid JSON in Config File"
                     {:type ::invalid-user-config-json
                      :path user-config-json}
             ex))))))))
