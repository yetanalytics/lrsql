(ns lrsql.system.webserver
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :refer [input-stream]]
            [jdk.security.KeyStore :as ks]
            [jdk.security.Key :as k]
            [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [com.yetanalytics.lrs.pedestal.routes :refer [build]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.routes :refer [add-admin-routes]]
            [lrsql.spec.config :as cs]
            [lrsql.system.util :refer [assert-config]]))

(defn- read-private-key
  "Read the private key at the keystore stored at `ks-path`, defined by
   `key-alias` and protected by `ks-pass`. Returns a string that can be
   then used as a JWT secret."
  [ks-path key-alias ks-pass]
  (let [istream  (input-stream ks-path)
        pass     (char-array ks-pass)
        kstore   (doto (ks/*get-instance (ks/*get-default-type))
                   (ks/load istream pass))
        priv-key (ks/get-key kstore key-alias pass)]
    ;; `slurp` turns byte array into string
    (slurp (k/get-encoded priv-key))))

(defn- service-map
  "Create a new service map for the webserver."
  [lrs config]
  (let [;; Destructure webserver config
        {jwt-exp   :jwt-expiration-time
         jwt-lwy   :jwt-expiration-leeway
         http2?    :http2?
         http-host :http-host
         http-port :http-port
         ssl-port  :ssl-port
         keystore  :keystore
         keyalias  :key-alias
         keypass   :key-password}
        config
        ;; Make JWT secret from the TSL private key
        jwt-secret
        (read-private-key keystore keyalias keypass)
        ;; Make routes
        routes
        (->> (build {:lrs lrs})
             (add-admin-routes {:lrs    lrs
                                :exp    jwt-exp
                                :leeway jwt-lwy
                                :secret jwt-secret}))]
    {:env                 :prod
     ::http/routes        routes
     ::http/resource-path "/public"
     ::http/type          :jetty
     ::http/host          http-host
     ::http/port          http-port
     ::http/join?         false
     ::http/allowed-origins
     {:creds           true
      :allowed-origins (constantly true)}
     ::http/container-options
     {:h2c?         true
      :h2?          http2?
      :ssl?         true
      :ssl-port     ssl-port
      :keystore     keystore
      :key-password keypass}}))

(defrecord Webserver [service
                      server
                      lrs
                      config]
  component/Lifecycle
  (start
   [this]
   (assert-config ::cs/webserver "webserver" config)
   (if server
     (do (log/info "Webserver already started; do nothing.")
         (log/tracef "Server map: %s" server)
         this)
     (if lrs
       (let [service (or service ;; accept passed in
                         (service-map lrs config))
             server  (-> service
                         i/xapi-default-interceptors
                         http/create-server
                         http/start)]
         (log/infof "Starting new webserver at host %s and port %s"
                    (::http/host service)
                    (::http/port service))
         (log/tracef "Server map: %s" server)
         (assoc this
                :service service
                :server server))
       (throw (ex-info "LRS Required to build service!"
                       {:type ::start-no-lrs
                        :webserver this})))))
  (stop
   [this]
   (if server
     (do (log/info "Stopping webserver...")
         (http/stop server)
         (assoc this
                :service nil
                :server nil
                :lrs nil))
     (do (log/info "Webserver already stopped; do nothing.")
         this))))
