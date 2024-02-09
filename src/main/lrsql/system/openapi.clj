(ns lrsql.system.openapi
  (:require [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.string :refer [lower-case upper-case]]
            [xapi-schema.spec.resources :as xr]
            [spec-tools.openapi.core :as openapi]
            [clojure.spec.alpha :as s]))

(defmacro debug [form]
  `(let [res# ~form]
     (println ~(str form) ": " (str res#))
     res#))

(def path-prefix "/xapi")

(defn ref [kw-or-str]
  {"$ref" (str "#/components/schemas/" (name kw-or-str))})

(defn owrap [pairs]                     ;map of form: {key schema}
  {:type :object
   :properties pairs
   :required (mapv name (keys pairs))})

(defn awrap [schema]
  {:type :array
   :items schema})

(defn json-content [schema]
  {:content {"application/json" {:schema schema}}})

(defn response
  ([desc] {:description desc})
  ([desc schema]
   {:description desc
    :content {"application/json" {:schema schema}}}))

(defn annotate [route data]
  (vary-meta route assoc :openapi data))

(defn annotate-short [route data]
  (let [[path method] route]
    (annotate route {path {method data}})))

(def components
  {:securitySchemes
   {:bearerAuth {:type :http
                 :scheme :bearer
                 :bearerFormat :JWT}}
   :responses
   {:error-400 (response "Bad Request" (ref :Error))
    :error-401 (response "Unauthorized" (ref :Error))}
   :schemas
   {:Account (owrap {:homePage (ref :IRL)
                     :name {:type :string}})
    :Activity {:type :object
               :required [:id]
               :properties {:objectType {:type :string :pattern "String"}
                            :id (ref :IRI)
                            :definition {:type :object
                                         :properties {:name {}
                                                      :description {}
                                                      :type {}
                                                      :moreinfo {}
                                                      :extensions {}}}}}
    :Agent ;maybe important
    {:allOf [{:type :object
              :properties  {:name {:type :string}
                            :objectType {:type :string}}
              :required [:mbox]}
             (ref :IFI)]}
    :Group {:oneOf [{:properties {:objectType {:type :string :pattern "Group"}
                                  :name {:type :string}
                                  :member (awrap (ref :Agent))}
                     :required [:objectType :member]}
                    {:allOf [{:properties {:objectType {:type :string :pattern "Group"}
                                           :name {:type :string}
                                           :member (awrap (ref :Agent))}
                              :required [:objectType]}
                             (ref :IFI)]}]}
    :Actor {:oneOf [(ref :Group)
                    (ref :Agent)]}
    
    :Error (owrap {:error {:type :string}})

    :IFI {:oneOf [(owrap {:mbox (ref :MailToIRI)})
                  (owrap {:mbox_sha1sum {:type :string}})
                  (owrap {:openid (ref :URI)})
                  (owrap {:account (ref :Account)})]}
    
    :IRI {:type :string :format :iri}
    :IRL {:type :string}
    :MailToIRI {:type :string :format :email}
    :KeyPair (owrap {:api-key {:type "string"}
                     :secret-key {:type "string"}})

    :Person {:type :object
             :properties {:objectType {:type :string :pattern "Person"}
                          :name (awrap {:type :string})
                          :mbox (awrap (ref :MailToIRI))
                          :mbox_sha1sum (awrap {:type :string})
                          :openid* (awrap (ref :URI))
                          :account* (awrap (ref :Account))}
             :required [:objectType]}
    :Scopes (owrap {:scopes (awrap {:type "string"})})
    :ScopedKeyPair {:allOf [(ref :KeyPair)
                            (ref :Scopes)]}

    :statementId {:type :string}
    :Statement {:type :object :description "https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Data.md#20-statements"}

    :Timestamp {:type :string :format :date-time}

    :StatementResult {:type :object
                      :required [:statements]
                      :properties {:statements (awrap (ref :Statement))
                                   :more (ref :IRL)}}
    :URI {:type :string :format :uri}
    :UUID {:type :string :format :uuid}}})

(def error-400 {"$ref" (str "#/components/responses/error-400")})
(def error-401 {"$ref" (str "#/components/responses/error-401")})

(def lrs-additions
  {(format "%s/health" path-prefix) {:get {:operationId :health
                                            :responses {200 {:description "Empty body---a 200 indicates server is alive"}}
                                            :description "Simple heartbeat"}}
   (format "%s/about" path-prefix) {:get {:operationId :get-about
                                           :description "About info"
                                           :responses
                                           {200 (merge {:description "Object containing body text and optional etag"}
                                                       (json-content {:type :object
                                                                      :properties {:body {:type :string}
                                                                                   :etag {:type :string}}
                                                                      :required    [:body]}))}}}})

(defn sort-map [m]
  (apply sorted-map (apply concat (sort m))))

(defn- extract-annotation [route]
  (-> route meta :openapi))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;lrsql document routes

;:?#sdf --- sdf is optional
;:r#  ---- ref
;:t#  ---- {:type "after-t"}
;(a schema) ---- {:type :array :items schema)
; (o k s k s k s) --- {:type "object" :properties [ks] :required [
;

(defn cleaned [kw]
  (->> kw name rest rest (apply str) keyword))

(defn begins-with-?#? [kw]
  (= '(\? \#) (take 2 (name kw))))
(defn to-param
  ([kw]
   (let [required (not (begins-with-?#? kw))]
     {:name (if required kw (cleaned kw))
      :in :query
      :required required}))
  ([kw schema]
   (merge (to-param kw) {:schema schema})))

(defn ->params [items]
  (when items
    (let [split (reduce (fn [acc v]
                          (if (keyword? v)
                            (conj acc [v])
                            (update acc (dec (count acc))
                                    conj v)))
                        []
                        items)]
      (mapv #(apply to-param %) split))))

(defn a [schema] {:type :array :items schema})
(defn o [& ks]
  (let [pairs (partition 2 ks)]
    {:type :object
     :properties (mapcat (for [[k schema] pairs]
                           [(if (begins-with-?#? k)
                              (cleaned k)
                              k)
                            schema]))
     :required (->> pairs
                    (map first)
                    (filter begins-with-?#?)
                    cleaned)}))

(defn render-annote [token]
  (cond (keyword? token)
        (let [[pre rem] (split-at 2 (name token))]
          (cond
            (= pre '(\r \#)) (ref (->> rem (apply str) keyword))
            (= pre '(\t \#) )  {:type (->> rem (apply str) keyword)}
            (= pre '(\a \#) ) (a  (render-annote (->> rem (apply str \r \#) keyword)))
            :else token))
        :else
        token))

(defn k2k [m & ks]
  (let [pairs (partition 2 ks)]
    (reduce (fn [acc [old noo]]
              (cond-> acc
                (acc old) (assoc noo (acc old))
                true (dissoc old)))
            m
            pairs)))

(defn painless-print [item label] (println label ": "  item) item)

(def render-tree (partial clojure.walk/prewalk render-annote))

(defn process-route [m {:keys [desc params security opid rbod] :as data}]
  #_(println "params: " params)
  #_(println "->params: " (->params params))
  (cond-> data
    true  (k2k :opid  :operationId
               :desc :description
               :params :parameters
               :rbod :requestBody)
    true render-tree
    params (update :parameters ->params)
    (not security) (assoc :security [{:bearerAuth []}])
    rbod (update :requestBody json-content)))

(defn process-path [p m-map]
  (reduce (fn [acc [k v]]
            (assoc acc k (process-route k v)))
          {} m-map))

(defn transform-keys [m f]
  (reduce (fn [acc k]
            (-> acc
                (assoc (f k) (acc k))
                (dissoc k)))
          m
          (keys m)))

(defn process-paths [p-map]
  (let [add-path-prefix (fn [path-map]
                          (transform-keys path-map #(str path-prefix %)))
        process-all (fn [path-map]
                      (reduce (fn [acc [k v]]
                                (assoc acc k (process-path k v)))
                              {}
                              path-map))]
    (-> p-map
        add-path-prefix
        process-all)))

(defn ->print [item]
  (println item)
  item)

(defn p [ch]
  (sort-map
   (process-route "" ch)))

;paths
;state
;activities/state
;activities/profile
;agents/profile

;/statements
;/agents
;/activities
;

(def lrs-resources-shorthand
  {"/statements" {:put {:params [:statementId :t#string]
                        :rbod :r#Statement
                        :responses {204 (response "No content")}
                        :opid :put-statement
                        :desc ""}
                  :post {:params []
                         :rbod {:oneOf [:a#statementId :r#statementId]}
                         :responses {200 (response "Array of Statement id(s) (UUID) in the same order as the corresponding stored Statements."
                                                   :a#statementId)}
                         :opid :post-statement
                         :desc "Stores a Statement, or a set of Statements."}
                  :get {:params [:?#statementId :t#string
                                 :?#voidedStatementId :t#string
                                 :?#agent :r#Actor
                                 :?#verb :r#IRI
                                 :?#activity :r#IRI
                                 :?#registration :r#UUID
                                 :?#related_activities :t#boolean
                                 :?#related_agents :t#boolean
                                 :?#since :r#Timestamp
                                 :?#limit :t#integer
                                 :?#format :t#string
                                 :?#attachments :t#boolean
                                 :?#ascending :t#boolean]
                        
                        :responses {200 (response "Requested Statement or Results"
                                                  {:oneOf [:r#Statement
                                                           :r#StatementResult]})}
                        :opid :get-statement
                        :desc "https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#21-statement-resource"}}

   "/activities/state" {:put {:params [:activityId :r#IRI
                                       :agent :r#Agent
                                       :?#registration :r#UUID
                                       :stateId :t#string]
                              :rbod :t#object
                              :responses {204 (response "No content" )}
                              :opid :put-state
                              :desc "Stores or changes the document specified by the given stateId that exists in the context of the specified Activity, Agent, and registration (if specified)."}
                        :post {:params  [:activityId :r#IRI
                                         :agent :r#Agent
                                         :?#registration :r#UUID
                                         :stateId :t#string]
                               :rbod  :t#object
                               :responses {204 (response "No content" )}
                               :opid :post-state
                               :desc  "Stores or changes the document specified by the given  stateId that exists in the context of the specified Activity, Agent, and registration (if specified)."}
                        :get {:params   [:activityId :r#IRI
                                         :agent :r#Agent
                                         :?#registration :r#UUID
                                         :?#stateId :t#string
                                         :?#since :r#Timestamp]
                              :responses {200 (response "The requested state document, or an array of stateId(s)"
                                                        {:oneOf [:t#object
                                                                 (a :t#string)]})}
                              :opid :get-state
                              :desc   "Fetches the document specified by the given  stateId that exists in the context of the specified Activity, Agent, and registration (if specified), or an array of stateIds."}
                        :delete {:params    [:activityId :r#IRI
                                             :agent :r#Agent
                                             :?#registration :r#UUID
                                             :?#stateId :t#string]
                                 :responses  {204 (response "No content" )}
                                 :opid      :delete-state
                                 :desc     "Deletes all documents associated with the specified Activity, Agent, and registration (if specified), or just the document specified by stateId"}}
   "/agents" {:get   {:params [:agent :r#Agent]
                      :responses {200 (response "Return a special, Person Object for a specified Agent. The Person Object is very similar to an Agent Object, but instead of each attribute having a single value, each attribute has an array value, and it is legal to include multiple identifying properties." :r#Person)}
                      :opid :get-agent
                      :desc "Gets a specified agent"}}

   "/activities" {:get {:params [:activityId :r#IRI]
                        :responses {200 (response "The requested Activity object" :r#Activity)}
                        :opid :get-activity
                        :desc "Gets the Activity with the specified activityId"}}

   "/agents/profile" {:put     {:params [:agent :r#Agent :profileId :t#string]
                                :rbod :t#object
                                :responses {204 (response "No content")}
                                :opid :put-agents-profile
                                :desc "Stores or changes the specified Profile document in the context of the specified Agent."}

                      :post {:params [:agent :r#Agent :profileId :t#string]
                             :rbod :t#object
                             :responses  {204 (response "No content")}
                             :opid :post-agents-profile
                             :desc "Stores or changes the specified Profile document in the context of the specified Agent."}
                      :get    {:params  [:agent :r#Agent :?#profileId :t#string :?#since :r#Timestamp]
                               :responses {200 (response "If profileId is included in the request, the specified document.  Otherwise, an array of profileId for the specified Agent.")}
                               :opid :get-agents-profile
                               :desc  "Fetches the specified Profile document in the context of the specified Agent.  The semantics of the request are driven by the \"profileId\" parameter. If it is included, the GET method will act upon a single defined document identified by \"profileId\". Otherwise, GET will return the available ids."}
                      :delete  {:params [:agent :r#Agent :profileId :t#string]
                                :responses {204 (response "No content")}
                                :opid    :delete-agents-profile
                                :desc "Deletes the specified Profile document in the context of the specified Agent."}}

   "/activities/profile" {:put    {:params     [:activityId :r#IRI :profileId :t#string]
                                      :rbod       :t#object
                                      :responses  {204 (response "No content")}
                                      :opid       :put-activity-profile
                                      :desc "Stores or changes the specified Profile document in the context of the specified Activity."}

                          :post    {:params     [:activityId :r#IRI :profileId :t#string]
                                    :rbod       :t#object
                                    :responses  {204 (response "No content")}
                                    :opid       :post-activity-profile
                                    :desc        "Stores or changes the specified Profile document in the context of the specified Activity."}
                          :get      {:params      [:activityId :r#IRI
                                                   :?#profileId :t#string
                                                   :?since :r#Timestamp]
                                     :responses   {200 (response "The requested Profile document" :t#object)}
                                     :opid        :get-activity-profile
                                     :desc        "Fetches the specified Profile document in the context of the specified Activity.  The semantics of the request are driven by the \"profileId\" parameter. If it is included, the GET method will act upon a single defined document identified by \"profileId\". Otherwise, GET will return the available ids."}

                          :delete {:params     [:activityId :r#IRI :profileId :t#string]
                                   :responses  {204 (response "No content")}
                                   :opid       :delete-activity-profile
                                   :desc       "Deletes the specified Profile document in the context of the specified Activity."}}})



; for all document resources: "updated" timestamp in header of response


(def lrs-paths
  (merge 
   (process-paths lrs-resources-shorthand)
   lrs-additions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;end adhoc
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;begin stitching up

(def general-spec
  {:openapi "3.0.0"
   :info {:title "LRSQL"
          :version "0.7.2"}
   :externalDocs {:url "https://github.com/yetanalytics/lrsql/blob/main/doc/endpoints.md"}
   :components components})

(defn extract-route-spec [route-set]
  (reduce (fn [acc route]
            (merge-with merge acc (extract-annotation route)))
          {}
          route-set))

(defn final-spec [route-set config-specific] 
  (merge config-specific
         general-spec
         {:paths (->> (extract-route-spec route-set)
                      (merge lrs-paths)
                      (sort-map))}))

(defn compile-openapi-yaml [route-set config-specific]
  (yaml/generate-string (final-spec route-set config-specific)))
(defn compile-openapi-json [route-set config-specific]
  (json/generate-string (final-spec route-set config-specific)))
