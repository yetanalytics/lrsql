(ns lrsql.system.webserver
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [com.yetanalytics.lrs.pedestal.routes :refer [build]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.routes :refer [add-admin-routes]]
            [lrsql.spec.config :as cs]
            [lrsql.system.util :refer [assert-config]]))

(defn- read-jwt-secret
  "Read the symmetric secret key for JWT signing and unsigning at `path`."
  [path]
  (try (slurp path)
       (catch Exception _
         (ex-info (format "Cannot read JWT secret at the path '%s'" path)
                  {:type ::invalid-jwt-secret-path
                   :path path}))))

(defn- service-map
  "Create a new service map for the webserver."
  [lrs config]
  (let [;; Destructure webserver config
        {jwt-exp   :jwt-expiration-time
         jwt-lwy   :jwt-expiration-leeway
         jwt-path  :jwt-secret
         http2?    :http2?
         http-host :http-host
         http-port :http-port
         ssl-port  :ssl-port
         keystore  :keystore
         keypass   :key-password}
        config
        ;; Make routes
        routes
        (->> (build {:lrs lrs})
             (add-admin-routes {:lrs    lrs
                                :exp    jwt-exp
                                :leeway jwt-lwy
                                :secret (read-jwt-secret jwt-path)}))]
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
