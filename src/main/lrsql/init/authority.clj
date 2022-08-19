(ns lrsql.init.authority
  "Utilities for generating xAPI authority Agents"
  (:require [cheshire.core :as json]
            [clojure.core.memoize :as mem]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [selmer.parser :as selm-parser]
            [selmer.util :as selm-u]
            [xapi-schema.spec :as xs]
            [lrsql.spec.authority :as ats])
  (:import [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper vars and functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn throw-on-missing
  "When a user enters a variable and it is not in our context map, throw!
   Used by selmer when context map validation fails."
  [tag context-map]
  (throw
   (ex-info (format "\"%s\" is not a valid variable for the authority template."
                    (:tag-value tag))
            {:type        ::unknown-variable
             :tag         tag
             :context-map context-map})))

(def default-authority-path
  "lrsql/config/authority.json.template")

(def sample-authority-fn-input
  {:authority-url "https://lrs.example.com"
   :cred-id       #uuid "00000000-0000-4000-0000-000000000001"
   :account-id    #uuid "00000000-0000-4000-0000-000000000002"})

(defn validate-authority-fn*
  "Returns `authority-fn` if it's valid, throws an exception if
   `(authority-fn sample-auth-fn-input)` does not satisfy `:statement/authority`,
   and logs `warn-msg` if it does not satisfy `warn-spec`."
  [authority-fn template-path warn-spec sample-auth-fn-input warn-msg]
  (let [sample-authority (authority-fn sample-auth-fn-input)]
    (when-not (s/valid? :statement/authority sample-authority)
      (throw (ex-info "Authority template does not produce a valid xAPI Authority."
                      {:type          ::invalid-json
                       :template-path template-path})))
    ;; TODO: Remove warning on inappropriate Authority type on xAPI 2.0
    (when-not (s/valid? warn-spec sample-authority)
      (log/warn warn-msg))
    authority-fn))

(defn- validate-authority-fn
  "Returns `authority-fn` if it's valid, throws an exception if invalid,
   and logs a warning if it is not an xAPI agent.
   `template-path` defaults to the `default-authority-path`."
  ([authority-fn]
   (validate-authority-fn authority-fn default-authority-path))
  ([authority-fn template-path]
   (validate-authority-fn* authority-fn
                           template-path
                           ::xs/agent
                           sample-authority-fn-input
                           "Authority template for Basic Auth does not produce a valid xAPI Agent.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Make authority function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef make-authority-fn
  :args (s/cat :template-path (s/nilable string?)
               :threshold (s/? pos-int?))
  :ret ::ats/authority-fn)

(defn make-authority-fn*
  "Returns a function that will render the template to data, using `template`.
   Not memoized."
  [template]
  (fn [context-map]
    (binding [selm-u/*missing-value-formatter* throw-on-missing
              selm-u/*filter-missing-values*   (constantly false)]
      (-> template
          (selm-parser/render-template context-map)
          json/parse-string))))

(def default-authority-fn
  "The default precompiled function to render authority"
  (-> default-authority-path
      io/resource
      selm-parser/parse*
      make-authority-fn*
      ;; Validation should always pass but we should still sanity check
      ;; (e.g. during dev changes).
      validate-authority-fn))

(defn make-authority-fn
  "Returns a function that will render the template to data, using the
   template read at `template-path`. `make-authority-fn*` itself is not
   memoized, but it returns a memoized function, with `threshold` setting
   how many authorities will be cached before least recently used (LRU)
   clearing (default 512)."
  ([template-path]
   (make-authority-fn template-path 512))
  ([template-path threshold]
   (let [^File f
         (io/file template-path)
         authority-fn
         (if (and f (.exists f))
           ;; Override template supplied - use that
           (-> f
               selm-parser/parse*
               make-authority-fn*
               (validate-authority-fn template-path))
           ;; Override template not supplied - fall back to default
           default-authority-fn)]
     (mem/lru authority-fn
              :lru/threshold threshold))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  ;; use the default by resource path
  (let [a-fn (make-authority-fn nil)]
    (a-fn {:authority-url "https://lrs.example.com"
           :cred-id       #uuid "41ec697d-802e-4f3e-aad5-e5fc9fb55f35"
           :account-id    #uuid "3aa61cf9-a697-46f1-b60d-62a2c78ab33b"}))

  ;; use arbitrary by fs path

  (let [a-fn (make-authority-fn "config/authority.json.template")]
    (a-fn {:authority-url "https://lrs.example.com"
           :cred-id       #uuid "41ec697d-802e-4f3e-aad5-e5fc9fb55f35"
           :account-id    #uuid "3aa61cf9-a697-46f1-b60d-62a2c78ab33b"}))
  ;; only remember one authority ;; don't ever do this but just for example
  (let [a-fn (make-authority-fn "config/authority.json.template" 1)]
    (a-fn {:authority-url "https://lrs.example.com"
           :cred-id       #uuid "41ec697d-802e-4f3e-aad5-e5fc9fb55f36"
           :account-id    #uuid "3aa61cf9-a697-46f1-b60d-62a2c78ab33b"}))

  ;; Run `make-authority-fn` during startup and then pass the resulting function
  ;; to a suitable component. It will only ever render the agent once for a
  ;; given context map (input), until old entries are evicted when `threshold`
  ;; is reached
  (time
   (dotimes [_ 1000]
     (let [a-fn (make-authority-fn "config/authority.json.template" 1)]
       (a-fn sample-authority-fn-input)
       (a-fn {:authority-url "https://lrs.example.com"
              :cred-id       #uuid "41ec697d-802e-4f3e-aad5-e5fc9fb55f35"
              :account-id    #uuid "3aa61cf9-a697-46f1-b60d-62a2c78ab33b"}))))
  )
