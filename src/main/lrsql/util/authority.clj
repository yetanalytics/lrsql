(ns lrsql.util.authority
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

(s/fdef make-authority-fn
  :args (s/cat :template-path (s/nilable string?)
               :threshold (s/? pos-int?))
  :ret ::ats/authority-fn)

(defn make-authority-fn**
  "Returns a function that will render the template to data, using `template`.
   Not memoized."
  [template]
  (fn [context-map]
    (binding [selm-u/*missing-value-formatter* throw-on-missing
              selm-u/*filter-missing-values*   (constantly false)]
      (-> template
          (selm-parser/render-template context-map)
          json/parse-string))))

(defn make-authority-fn*
  "Returns a function that will render the template to data, using the
   template read at `template-path`. `make-authority-fn*` itself is not
   memoized, but it returns a memoized function, with `threshold` setting
   how many authorities will be cached before least recently used (LRU)
   clearing."
  [template-path threshold]
  (let [^File f (io/file template-path)]
    (if (and f (.exists f))
      (let [template     (selm-parser/parse* f)
            authority-fn (make-authority-fn** template)]
        (if (valid-authority-fn? authority-fn)
          (mem/lru authority-fn
                   :lru/threshold (or threshold 512))
          (throw
           (ex-info "Authority template does not produce a valid xAPI Agent"
                    {:type          ::invalid-json
                     :template-path template-path}))))
      (throw
       (ex-info (format "No authority template specified at %s" template-path)
                {:type          ::no-authority-template
                 :template-path template-path})))))

(def make-authority-fn
  "Memoized version of `make-authority-fn*`. This should be called in order
   to avoid unnecessarily reading and parsing the file at `template-path`."
  (mem/memo
   (fn [template-path & [threshold]]
     (make-authority-fn* template-path threshold))))

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
   (dotimes [_ 1]
     (let [a-fn (make-authority-fn "config/authority.json.template")]
       (a-fn sample-authority-fn-input)
       (a-fn {:authority-url "https://lrs.example.com"
              :cred-id       #uuid "41ec697d-802e-4f3e-aad5-e5fc9fb55f35"
              :account-id    #uuid "3aa61cf9-a697-46f1-b60d-62a2c78ab33b"}))))
  )
