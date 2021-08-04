(ns lrsql.bench
  (:require [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [clojure.pprint :as pprint]
            [java-time :as jt]
            [babashka.curl :as curl]
            [com.yetanalytics.datasim.sim :as sim]
            [com.yetanalytics.datasim.input :as sim-input]
            [lrsql.util :as u]))

(def headers
  {"Content-Type"             "application/json"
   "X-Experience-API-Version" "1.0.3"})

(defn read-input
  [input-path]
  (-> (sim-input/from-location :input :json input-path)
      (assoc-in [:parameters :seed] (rand-int 1000000000))))

(defn read-query-input
  [query-uri]
  (let [raw (slurp query-uri)]
    (u/parse-json raw :object? false)))

(def cli-options
  [["-i" "--insert-input URI" "DATASIM input source"
    :id :insert-input
    :default nil
    :desc "The location of a JSON file containing a DATASIM input spec. Used to populate the DB; if not given, then the initial insertion is ignored."]
   ["-s" "--input-size LONG" "Size"
    :id :insert-size
    :parse-fn #(Long/parseLong %)
    :default 1000
    :desc "The total number of statements to insert. Ignored if `-i` is not given."]
   ["-b" "--batch-size LONG" "Statements per batch"
    :id :batch-size
    :parse-fn #(Long/parseLong %)
    :default 10
    :desc "The batch size to use for posting statements. Ignored if `-i` is not given."]
   ["-q" "--query-input URI" "Query input source"
    :id :query-input
    :desc "The location of a JSON file containing an array of statement query params. If not specified, the benchmark defaults to a single query with no params."]
   ["-n" "--query-number LONG" "Query execution number"
    :id :query-number
    :parse-fn #(Long/parseLong %)
    :default 1000
    :desc "The number of times each query given by `-q` is performed."]
   ["-u" "--user STRING" "LRS User"
    :id :user
    :desc "HTTP Basic Auth user"]
   ["-p" "--pass STRING" "LRS Password"
    :id :pass
    :desc "HTTP Basic Auth password"]
   ["-h" "--help"]])

(defn store-statements
  [endpoint input-uri size batch-size user pass]
  (let [inputs  (read-input input-uri)
        stmts   (take size (sim/sim-seq inputs))]
    (loop [batches (partition-all batch-size stmts)]
      (if-some [batch (first batches)]
        (do
          (curl/post endpoint {:headers    headers
                               :body       (String. (u/write-json (vec batch)))
                               :basic-auth [user pass]})
          (recur (rest batches)))))))

(defn perform-query
  [endpoint query query-times user pass]
  (let [start-time (jt/instant)
        curl-input {:headers      headers
                    :query-params query
                    :basic-auth   [user pass]}]
    (dotimes [_ query-times]
      (curl/get endpoint curl-input))
    (let [end-time (jt/instant)
          duration (jt/duration start-time end-time)
          millis   (jt/as duration :millis)]
      ;; TODO: min, max, sd
      {:param-str   (pr-str query)
       :time-millis millis
       :avg-millis  (quot millis query-times)})))

(defn query-statements
  [endpoint query-uri query-times user pass]
  (loop [queries (if query-uri (read-query-input query-uri) [{}])
         results (transient [])]
    (if-some [query (first queries)]
      (let [res (perform-query endpoint query query-times user pass)]
        (recur (rest queries)
               (conj! results res)))
      (persistent! results))))

(defn -main
  [lrs-endpoint & args]
  (let [{:keys [arguments
                summary
                errors]
         :as parsed-opts
         {:keys [insert-input
                 insert-size
                 batch-size
                 query-input
                 query-number
                 user
                 pass]} :options}
        (cli/parse-opts args cli-options)]
    ;; Check for errors
    (when (not-empty errors)
      (throw (ex-info "CLI Parse Error!"
                      {:type   ::cli-parse-error
                       :errors errors})))
    (when-not query-input
      (throw (ex-info "Missing Query Input!"
                      {:type ::missing-query-input})))
    ;; Store statements
    (when insert-input
      (log/info "Starting statement insertion...")
      (store-statements lrs-endpoint
                        insert-input
                        insert-size
                        batch-size
                        user
                        pass)
      (log/info "Statement insertion finished."))
    ;; Query statements
    (log/info "Starting statement query benching...")
    (let [results (query-statements lrs-endpoint
                                    query-input
                                    query-number
                                    user
                                    pass)]
      (log/info "Statement query benching finished.")
      (pprint/print-table results))))
