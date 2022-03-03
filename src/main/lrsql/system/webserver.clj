(ns lrsql.system.webserver
  (:require [clojure.tools.logging :as log]
            [lrsql.util.logging   :refer [logo]]
            [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [com.yetanalytics.lrs.pedestal.routes :refer [build]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.routes :refer [add-admin-routes]]
            [lrsql.init.oidc :as oidc]
            [lrsql.spec.config :as cs]
            [lrsql.system.util :refer [assert-config redact-config-vars]]
            [lrsql.util.cert :as cu]
            [lrsql.util.interceptor :refer [handle-json-parse-exn]]))

(defn- service-map
  "Create a new service map for the webserver."
  [lrs config]
  (let [;; Destructure webserver config
        {:keys [enable-http
                enable-http2
                http-host
                http-port
                ssl-port
                url-prefix
                key-password
                enable-admin-ui
                enable-stmt-html]
         jwt-exp :jwt-exp-time
         jwt-lwy :jwt-exp-leeway}
        config
        ;; Keystore and private key
        ;; The private key is used as the JWT symmetric secret
        {:keys [keystore
                private-key]} (cu/init-keystore config)
        ;; OIDC Resource Interceptors
        oidc-interceptors (oidc/resource-interceptors config)
        ;; OIDC Admin Interceptors
        oidc-admin-interceptors
        (into oidc-interceptors
              (oidc/admin-interceptors config))
        ;; OIDC Admin UI
        oidc-admin-ui-interceptors (oidc/admin-ui-interceptors
                                    config
                                    (:config lrs))

        ;; Make routes - the lrs error interceptor is appended to the
        ;; start to all lrs routes
        routes
        (->> (build {:lrs               lrs
                     :path-prefix       url-prefix
                     :wrap-interceptors (into
                                         [i/error-interceptor
                                          (handle-json-parse-exn)]
                                         oidc-interceptors)})
             (add-admin-routes {:lrs                  lrs
                                :exp                  jwt-exp
                                :leeway               jwt-lwy
                                :secret               private-key
                                :enable-admin-ui      enable-admin-ui
                                :oidc-interceptors    oidc-admin-interceptors
                                :oidc-ui-interceptors oidc-admin-ui-interceptors}))]
    {:env                      :prod
     ::http/routes             routes
     ;; only serve assets if the admin ui is enabled
     ::http/resource-path      (when enable-admin-ui "/public")
     ::http/type               :jetty
     ::http/host               http-host
     ::http/port               (when enable-http http-port) ; nil = no HTTP
     ::http/join?              false
     ::i/path-prefix           url-prefix
     ::i/enable-statement-html enable-stmt-html
     ::http/allowed-origins
     {:creds           true
      :allowed-origins (constantly true)}
     ::http/container-options
     {:h2c?         (and enable-http enable-http2)
      :h2?          enable-http2
      :ssl?         true
      :ssl-port     ssl-port
      :keystore     keystore
      :key-password key-password}}))

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
         (log/debugf "Server map: %s" (redact-config-vars server))
         this)
     (if lrs
       (let [service (or service ;; accept passed in
                         (service-map lrs config))
             server  (-> service
                         i/xapi-default-interceptors
                         http/create-server
                         http/start)]
         ;; Logging
         (let [{{ssl-port :ssl-port} ::http/container-options
                http-port ::http/port
                host ::http/host} service]
           (if http-port
             (log/infof "Starting new webserver at host %s, HTTP port %s, and SSL port %s"
                        host
                        http-port
                        ssl-port)
             (log/infof "Starting new webserver at host %s and SSL port %s"
                        host
                        ssl-port)))
         (log/info logo)
         (log/debugf "Server map: %s" (redact-config-vars server))
         ;; Return new webserver
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
