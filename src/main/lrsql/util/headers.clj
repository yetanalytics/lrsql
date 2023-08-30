(ns lrsql.util.headers
  (:require [io.pedestal.http.secure-headers :as hsh]
            [io.pedestal.interceptor :refer [interceptor]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; General
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn headers-interceptor
  "Takes a map of header names to values and creates an interceptor to inject
   them in response."
  [headers]
  (interceptor
   {:leave (fn [{response :response :as context}]
             (assoc-in context [:response :headers]
                       (merge headers (:headers response))))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Security Headers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-value "[default]")

(def sec-head-defaults
  {:sec-head-hsts         (hsh/hsts-header)
   :sec-head-frame        (hsh/frame-options-header)
   :sec-head-content-type (hsh/content-type-header)
   :sec-head-xss          (hsh/xss-protection-header)
   :sec-head-download     (hsh/download-options-header)
   :sec-head-cross-domain (hsh/cross-domain-policies-header)
   :sec-head-content      (hsh/content-security-policy-header)})

(def sec-head-names
  {:sec-head-hsts         "Strict-Transport-Security"
   :sec-head-frame        "X-Frame-Options"
   :sec-head-content-type "X-Content-Type-Options"
   :sec-head-xss          "X-XSS-Protection"
   :sec-head-download     "X-Download-Options"
   :sec-head-cross-domain "X-Permitted-Cross-Domain-Policies"
   :sec-head-content      "Content-Security-Policy"})

(defn build-sec-headers
  [sec-header-opts]
  (reduce-kv
   (fn [agg h-key h-val]
     (if (string? h-val)
       (assoc agg (get sec-head-names h-key)
              (if (= default-value h-val)
                (get sec-head-defaults h-key)
                h-val))
       agg)) {} sec-header-opts))

(defn secure-headers
  "Iterate header-opts, generating values for each header and returning an
   interceptor"
  [sec-header-opts]
  (let [sec-headers (build-sec-headers sec-header-opts)]
    (headers-interceptor sec-headers)))
