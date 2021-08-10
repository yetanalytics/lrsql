(ns lrsql.bench
  (:require [clojure.string :refer [join]]
            [clojure.math.numeric-tower :as math]
            [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [clojure.pprint :as pprint]
            [java-time :as jt]
            [babashka.curl :as curl]
            [com.yetanalytics.datasim.sim :as sim]
            [com.yetanalytics.datasim.input :as sim-input]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Defs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def headers
  {"Content-Type"             "application/json"
   "X-Experience-API-Version" "1.0.3"})

(def cli-options
  [["-i" "--insert-input URI" "DATASIM input source"
    :id :insert-input
    :desc "The location of a JSON file containing a DATASIM input spec. If given, this input is used to insert statements into the DB."]
   ["-s" "--input-size LONG" "Size"
    :id :insert-size
    :parse-fn #(Long/parseLong %)
    :default 1000
    :desc "The total number of statements to insert. Ignored if `-i` is not given."]
   ["-b" "--batch-size LONG" "Statements per batch"
    :id :batch-size
    :parse-fn #(Long/parseLong %)
    :default 10
    :desc "The batch size to use for inserting statements. Ignored if `-i` is not given."]
   ["-r" "--statement-refs STRING" "Statement Ref Insertion Type"
    :id :statement-ref-type
    :parse-fn keyword
    :validate [#{:all :half :none} "Should be `all`, `half`, or `none`."]
    :default :none
    :desc "How Statement Refs should be inserted. Valid options are `none` (default), `half`, and `all`."]
   ["-q" "--query-input URI" "Query input source"
    :id :query-input
    :desc "The location of a JSON file containing an array of statement query params. If not given, the benchmark does a single query with no params."]
   ["-n" "--query-number LONG" "Query execution number"
    :id :query-number
    :parse-fn #(Long/parseLong %)
    :default 30
    :desc "The number of times each query is performed."]
   ["-u" "--user STRING" "LRS User"
    :id :user
    :desc "HTTP Basic Auth user."]
   ["-p" "--pass STRING" "LRS Password"
    :id :pass
    :desc "HTTP Basic Auth password."]
   ["-h" "--help"]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Input Reading
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn read-insert-input
  [input-path]
  (-> (sim-input/from-location :input :json input-path)
      (assoc-in [:parameters :seed] (rand-int 1000000000))))

(defn read-query-input
  [query-uri]
  (let [raw (slurp query-uri)]
    (u/parse-json raw :object? false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- assoc-stmt-refs
  [targets refs]
  (map (fn [tgt ref]
         (let [tid  (get tgt "id")
               robj {"objectType" "StatementRef" "id" tid}]
           (assoc ref "object" robj)))
       targets
       refs))

(defmulti generate-statements
  {:arglist '([inputs size sref-type])}
  (fn [_ _ sref-type] sref-type)
  :default :none)

(defmethod generate-statements :none
  [inputs size _]
  (take size (sim/sim-seq inputs)))

(defmethod generate-statements :half
  [inputs size _]
  (let [stmt-seq    (take size (sim/sim-seq inputs))
        [tgts refs] (split-at (quot size 2) stmt-seq)
        refs'       (assoc-stmt-refs tgts refs)]
    (concat tgts refs')))

(defmethod generate-statements :all
  [inputs size _]
  (let [tgts  (take size (sim/sim-seq inputs))
        refs  (drop 1 tgts)
        refs' (assoc-stmt-refs tgts refs)]
    (cons (first tgts) refs')))

(defn store-statements
  [endpoint input-uri size batch-size user pass sref-type]
  (let [inputs  (read-insert-input input-uri)
        stmts   (generate-statements inputs size sref-type)]
    (loop [batches (partition-all batch-size stmts)]
      (if-some [batch (first batches)]
        (do
          (curl/post endpoint
                     {:headers    headers
                      :body       (String. (u/write-json (vec batch)))
                      :basic-auth [user pass]})
          (recur (rest batches)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statement Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn perform-query
  [endpoint curl-input]
  (let [start (jt/instant)
        _     (curl/get endpoint curl-input)
        end   (jt/instant)
        dur   (jt/duration start end)]
    (jt/as dur :millis)))

(defn- calc-statistics
  [x-vec n]
  (binding [*unchecked-math* true] ; Micro-optimize
    (let [x-max  (apply max x-vec)
          x-min  (apply min x-vec)
          x-sum  (reduce + x-vec)
          x-mean (quot x-sum n)
          ;; Calculate sample (not population) standard deviation
          x-sdel (map #(math/expt (- % x-mean) 2) x-vec)
          x-sd   (math/round (math/sqrt (quot (reduce + x-sdel) n)))]
      {:mean  x-mean
       :sd    x-sd
       :max   x-max
       :min   x-min
       :total x-sum})))

(defn perform-queries
  [endpoint query query-times user pass]
  ;; `nil` = `{}` since babashka/curl does not like the latter
  (let [curl-input {:headers      headers
                    :query-params (not-empty query)
                    :basic-auth   [user pass]}]
    (loop [n   query-times
           res (transient [])]
      (if (< 0 n)
        ;; Perform query
        (let [r (perform-query endpoint curl-input)]
          (recur (dec n)
                 (conj! res r)))
        ;; Calculate statistics and return
        (let [results (persistent! res)]
          (merge
           {:query (pr-str query)}
           (calc-statistics results query-times)))))))

(defn query-statements
  [endpoint query-uri query-times user pass]
  (loop [queries (if query-uri (read-query-input query-uri) [{}])
         results (transient [])]
    (if-some [query (first queries)]
      (let [res (perform-queries endpoint
                                 query
                                 query-times
                                 user
                                 pass)]
        (recur (rest queries)
               (conj! results res)))
      (persistent! results))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Putting it all together
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main
  [lrs-endpoint & args]
  (let [{:keys [summary errors]
         :as _parsed-opts
         {:keys [insert-input
                 insert-size
                 statement-ref-type
                 batch-size
                 query-input
                 query-number
                 user
                 pass
                 help]} :options}
        (cli/parse-opts args cli-options)]
    ;; Check for errors
    (when (not-empty errors)
      (log/errorf "CLI Parse Errors:\n%s" (join "\n" errors))
      (throw (ex-info "CLI Parse Errors!"
                      {:type   ::cli-parse-error
                       :errors errors})))
    ;; Print help and exit
    (when help
      (println summary)
      (System/exit 0))
    ;; Store statements
    (when insert-input
      (log/info "Starting statement insertion...")
      (store-statements lrs-endpoint
                        insert-input
                        insert-size
                        batch-size
                        user
                        pass
                        statement-ref-type)
      (log/info "Statement insertion finished."))
    ;; Query statements
    (log/info "Starting statement query benching...")
    (let [results (query-statements lrs-endpoint
                                    query-input
                                    query-number
                                    user
                                    pass)]
      (log/info "Statement query benching finished.")
      (printf "\n%s Query benchmark results for n = %d (in ms) %s\n"
              "**********"
              query-number
              "**********")
      (pprint/print-table results)
      (println ""))))
