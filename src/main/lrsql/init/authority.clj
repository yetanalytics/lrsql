(ns lrsql.init.authority
  "Utilities for generating xAPI authority Agents"
  (:require [cheshire.core :as json]
            [clojure.core.memoize :as mem]
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

(def sample-authority-fn-input
  {:authority-url "https://lrs.example.com"
   :cred-id       #uuid "00000000-0000-4000-0000-000000000001"
   :account-id    #uuid "00000000-0000-4000-0000-000000000002"})

(defn- valid-authority-fn?
  [authority-fn]
  (s/valid? ::xs/agent (authority-fn sample-authority-fn-input)))

(defn- assert-authority-fn
  [template-path authority-fn]
  (when-not (valid-authority-fn? authority-fn)
    (throw
     (ex-info "Authority template does not produce a valid xAPI Agent"
              {:type          ::invalid-json
               :template-path template-path}))))

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
  (-> "lrsql/config/authority.json.template"
      io/resource
      selm-parser/parse*
      make-authority-fn*))

(defn make-authority-fn
  "Returns a function that will render the template to data, using the
   template read at `template-path`. `make-authority-fn*` itself is not
   memoized, but it returns a memoized function, with `threshold` setting
   how many authorities will be cached before least recently used (LRU)
   clearing."
  [template-path & [threshold]]
  (let [^File f
        (io/file template-path)
        authority-fn
        (if (and f (.exists f))
          ;; Override template supplied - use that
          (let [template     (selm-parser/parse* f)
                authority-fn (make-authority-fn* template)]
            (assert-authority-fn template-path authority-fn)
            authority-fn)
          ;; Override template not supplied - fall back to default
          default-authority-fn)]
    (mem/lru authority-fn
             :lru/threshold (or threshold 512))))

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
