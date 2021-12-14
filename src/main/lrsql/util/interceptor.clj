(ns lrsql.util.interceptor
  "Common interceptors shared by LRS and admin routes."
  (:require [clojure.string :as cstr]
            [io.pedestal.interceptor.error :refer [error-dispatch]]
            [io.pedestal.interceptor.chain :as chain]))

(defn- get-message
  [ex]
  (.getMessage ^Throwable ex))

;; clj-kondo/VSCode does not recognize complex `error-dispatch` macro
#_{:clj-kondo/ignore [:unresolved-symbol]}
(defn handle-json-parse-exn
  "Return an interceptor to handle Jackson JsonParseExceptions (which
   are thrown by the `body-params` Pedestal interceptor). `redact?`,
   if true, will attempt to redact any personal identifying info."
  ([]
   (handle-json-parse-exn false))
  ([redact?]
   (error-dispatch
    [ctx ex]
    ;; JSON Parse failure
    [{:exception-type :com.fasterxml.jackson.core.JsonParseException
      :exception      exception}]
    (let [msg (cond-> exception
                true    get-message
                redact? (cstr/replace #"Unrecognized token '.*'"
                                      "Unrecognized token '[REDACTED]'"))]
      (assoc ctx
             :response
             {:status 400
              :body   {:error msg}}))
    ;; JSON EOF failure (subclass of the above)
    [{:exception-type :com.fasterxml.jackson.core.io.JsonEOFException
      :exception      exception}]
    (let [msg (get-message exception)]
      (assoc ctx
             :response
             {:status 400
              :body   {:error msg}}))
    ;; Other error (incl. non-parsing-related Jackson errors);
    ;; continue as need be.
    :else
    (assoc ctx ::chain/error ex))))
