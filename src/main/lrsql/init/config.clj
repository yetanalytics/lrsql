(ns lrsql.init.config
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [aero.core :as aero]
            [camel-snake-kebab.core :as csk])
  (:import [java.io File]))

(defn- merge-user-config
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
           (json/parse-stream-strict rdr csk/->kebab-case-keyword))
         (catch Exception ex
           (throw
            (ex-info "Invalid JSON in Config File"
                     {:type ::invalid-user-config-json
                      :path user-config-json}
                     ex))))))))

;; The default aero `#include` resolver does not work with JARs, so we
;; need to resolve the root dirs manually.

(def config-path-prefix "lrsql/config/")

(defn- resolver
  [_ include]
  (io/resource (str config-path-prefix include)))

(defn read-config*
  "Read `config.edn` with the given value of `profile`. Valid
   profiles are `:test-[db-type]` and `:prod-[db-type]`.
  Based on the :config-file-json key found will attempt to merge in properties
  from the given path, if the file is present."
  [profile]
  (let [;; Read in and process aero config
        {:keys [database
                connection
                lrs
                webserver
                logger]} (-> (str config-path-prefix "config.edn")
                             io/resource
                             (aero/read-config
                              {:profile  profile
                               :resolver resolver})
                             merge-user-config)]
    ;; form the final config the app will use
    {:connection (assoc connection :database database)
     :lrs        (assoc lrs :stmt-url-prefix (:url-prefix webserver))
     :webserver  webserver
     :logger     logger}))

(def read-config
  "Memoized version of `read-config*`."
  (memoize read-config*))
