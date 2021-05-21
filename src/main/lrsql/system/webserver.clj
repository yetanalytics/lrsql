(ns lrsql.system.webserver
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [com.yetanalytics.lrs.pedestal.routes :refer [build]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [config.core :refer [env]]))

(defrecord Webserver [service server
                      lrs]
  component/Lifecycle
  (start [this]
    (if server
      (do (log/info "Webserver already started; do nothing")
          (log/tracef "Server map: %s" server)
          this)
      (if lrs
        (let [service
              (or service ;; accept passed in
                  {:env :prod
                   ::http/routes
                   (build {:lrs lrs})
                   ::http/allowed-origins
                   {:creds true :allowed-origins (constantly true)}
                   ::http/resource-path "/public"
                   ::http/type :jetty
                   ::http/host (:http-host env "0.0.0.0")
                   ::http/port (:http-port env 8080)
                   ::http/join? false
                   ::http/container-options
                   {:h2c? true
                    :h2? false
                    :ssl? false}})
              server (-> service
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
  (stop [this]
    (if server
      (do (log/info "Stopping webserver...")
          (http/stop server)
          (assoc this
                 :service nil
                 :server nil
                 :lrs nil))
      (do (log/info "Webserver already stopped; do nothing")
          this))))
