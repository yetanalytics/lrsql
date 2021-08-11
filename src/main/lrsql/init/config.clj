(ns lrsql.init.config
  (:require [clojure.java.io :as io]
            [aero.core :as aero]
            [lrsql.util.config :as config-u]))

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
                webserver]} (-> (str config-path-prefix "config.edn")
                                io/resource
                                (aero/read-config
                                 {:profile  profile
                                  :resolver resolver})
                                config-u/merge-user-config)]
    ;; form the final config the app will use
    {:connection (assoc connection :database database)
     :lrs        (assoc lrs :stmt-url-prefix (:url-prefix webserver))
     :webserver  webserver}))

(def read-config
  "Memoized version of `read-config*`."
  (memoize read-config*))
