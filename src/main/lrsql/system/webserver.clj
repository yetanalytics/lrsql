(ns lrsql.system.webserver
  (:require [clojure.tools.logging :as log]
            [lrsql.util.logging   :refer [logo]]
            [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [com.yetanalytics.lrs.pedestal.routes :refer [build]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [clojure.core :refer [format]]
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
                enable-admin-status
                enable-stmt-html
                sec-head-hsts
                sec-head-frame
                sec-head-content-type
                sec-head-xss
                sec-head-download
                sec-head-cross-domain
                sec-head-content
                allow-all-origins
                allowed-origins
                jwt-no-val
                jwt-no-val-uname
                jwt-no-val-issuer
                jwt-no-val-role-key
                jwt-no-val-role]
         jwt-exp           :jwt-exp-time
         jwt-lwy           :jwt-exp-leeway}
        config
        ;; Keystore and private key
        ;; The private key is used as the JWT symmetric secret
        {:keys [keystore
                private-key]} (cu/init-keystore config)
        ;; OIDC Interceptors & derived settings
        {{oidc-resource-interceptors :resource-interceptors
          oidc-admin-interceptors    :admin-interceptors
          oidc-admin-ui-interceptors :admin-ui-interceptors} :interceptors
         :keys                                               [enable-local-admin]}
        (oidc/init
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
                                         oidc-resource-interceptors)})
             (add-admin-routes
              {:lrs                   lrs
               :exp                   jwt-exp
               :leeway                jwt-lwy
               :no-val?               jwt-no-val
               :no-val-issuer         jwt-no-val-issuer
               :no-val-uname          jwt-no-val-uname
               :no-val-role-key       jwt-no-val-role-key
               :no-val-role           jwt-no-val-role
               :secret                private-key
               :enable-admin-ui       enable-admin-ui
               :enable-admin-status   enable-admin-status
               :enable-account-routes enable-local-admin
               :oidc-interceptors     oidc-admin-interceptors
               :oidc-ui-interceptors  oidc-admin-ui-interceptors
               :head-opts
               {:sec-head-hsts         sec-head-hsts
                :sec-head-frame        sec-head-frame
                :sec-head-content-type sec-head-content-type
                :sec-head-xss          sec-head-xss
                :sec-head-download     sec-head-download
                :sec-head-cross-domain sec-head-cross-domain
                :sec-head-content      sec-head-content}}))
        ;; Build allowed-origins list. Add without ports as well for
        ;; default ports
        allowed-list
        (or allowed-origins
            (cond-> [(format "http://localhost:%s" http-port)
                     (format "https://localhost:%s" ssl-port)
                     (format "http://%s:%s" http-host http-port)
                     (format "https://%s:%s" http-host ssl-port)]
              (= http-port 80) (conj (format "http://%s" http-host))
              (= ssl-port 443) (conj (format "https://%s" http-host))))]
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
      :allowed-origins (fn [origin]
                         (or allow-all-origins
                             (some #(= origin %) allowed-list)))}
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
