(ns lrsql.bench
  (:require [clojure.core.async         :as a]
            [clojure.math.numeric-tower :as math]
            [clojure.pprint             :as pprint]
            [clojure.string             :as cstr]
            [clojure.tools.cli          :as cli]
            [clojure.tools.logging      :as log]
            [babashka.curl              :as curl]
            [java-time.api              :as jt]
            [com.yetanalytics.datasim   :as ds]
            [lrsql.util                 :as u])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Defs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def headers
  {"Content-Type"             "application/json"
   "X-Experience-API-Version" "1.0.3"})

(def cli-options
  [["-e" "--lrs-endpoint URI" "(SQL) LRS endpoint"
    :id      :lrs-endpoint
    :default "http://0.0.0.0:8080/xapi/statements"
    :desc    "The HTTP(S) endpoint of the (SQL) LRS webserver for Statement POSTs and GETs."]
   ["-i" "--insert-input URI" "DATASIM input source"
    :id   :insert-input
    :desc "The location of a JSON file containing a DATASIM input spec. If present, this input is used to insert statements into the DB."]
   ["-s" "--input-size LONG" "Size"
    :id       :insert-size
    :parse-fn #(Long/parseLong %)
    :default  1000
    :desc     "The total number of statements to insert. Ignored if `-i` is not present."]
   ["-b" "--batch-size LONG" "Statements per batch"
    :id       :batch-size
    :parse-fn #(Long/parseLong %)
    :default  10
    :desc     "The batch size to use for inserting statements. Ignored if `-i` is not present."]
   ["-a" "--async" "Run asynchronously?"
    :id      :async?
    :default false
    :desc    "If provided, insert statements asynchronously."]
   ["-c" "--concurrency LONG" "Number of threads"
    :id       :concurrency
    :parse-fn #(Long/parseLong %)
    :default  10
    :desc     "The number of parallel threads to run during statement insertion. Ignored if `-a` is not present."]
   ["-r" "--statement-refs STRING" "Statement Ref Insertion Type"
    :id       :statement-ref-type
    :parse-fn keyword
    :validate [#{:all :half :none} "Should be `all`, `half`, or `none`."]
    :default  :none
    :desc     "How Statement References should be generated and inserted. Valid options are none (no Statement References), half (half of the Statements have StatementRef objects), and all (all Statements have StatementRef objects)."]
   ["-q" "--query-input URI" "Query input source"
    :id   :query-input
    :desc "The location of a JSON file containing an array of statement query params. If not present, the benchmark does a single query with no params."]
   ["-n" "--query-number LONG" "Query execution number"
    :id       :query-number
    :parse-fn #(Long/parseLong %)
    :default  30
    :desc     "The number of times each query is performed."]
   ["-u" "--user STRING" "LRS User"
    :id   :user
    :desc "HTTP Basic Auth user."]
   ["-p" "--pass STRING" "LRS Password"
    :id   :pass
    :desc "HTTP Basic Auth password."]
   ["-h" "--help"
    :desc "Help menu."]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Input Reading
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn read-insert-input
  [input-path]
  (-> (ds/read-input input-path)
      (assoc-in [:parameters :seed] (rand-int 1000000000))))

(defn read-query-input
  [query-uri]
  (let [raw (slurp query-uri)]
    (u/parse-json raw :object? false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Async Zone
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- perform-async-op!
  "Enter the async zone and perform `curl-op` on `endpoint` and `requests`
   on `conc-size` concurrent threads."
  [curl-op endpoint requests conc-size]
  (let [post-fn (fn [req]
                  ;; Just throw any exceptions since if we silently fail
                  ;; statistics may become unreliable
                  (curl-op endpoint req))
        req-chan (a/to-chan! requests)
        res-chan (a/chan (count requests))]
    (a/pipeline-blocking conc-size
                         res-chan
                         (map post-fn)
                         req-chan)
    (a/<!! (a/into [] res-chan))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Stats
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
  (take size (ds/generate-seq inputs)))

(defmethod generate-statements :half
  [inputs size _]
  (let [stmt-seq    (take size (ds/generate-seq inputs))
        [tgts refs] (split-at (quot size 2) stmt-seq)
        refs'       (assoc-stmt-refs tgts refs)]
    (concat tgts refs')))

(defmethod generate-statements :all
  [inputs size _]
  (let [tgts  (take size (ds/generate-seq inputs))
        refs  (drop 1 tgts)
        refs' (assoc-stmt-refs tgts refs)]
    (cons (first tgts) refs')))

(defn perform-insert
  [endpoint curl-input]
  (let [start (jt/instant)
        _     (curl/post endpoint curl-input)
        end   (jt/instant)
        dur   (jt/duration start end)]
    (jt/as dur :millis)))

(defn store-statements-sync!
  [{endpoint   :lrs-endpoint
    input-uri  :insert-input
    size       :insert-size
    batch-size :batch-size
    user       :user
    pass       :pass
    sref-type  :statement-ref-type}]
  (let [inputs  (read-insert-input input-uri)
        stmts   (generate-statements inputs size sref-type)]
    (loop [batches (partition-all batch-size stmts)
           timings (transient [])]
      (if-some [batch (first batches)]
        (recur (rest batches)
               (conj! timings
                      (perform-insert
                       endpoint
                       {:headers    headers
                        :body       (u/write-json-str (vec batch))
                        :basic-auth [user pass]})))
        (let [timings-ret (persistent! timings)]
          (calc-statistics timings-ret (count timings-ret)))))))

(defn store-statements-async!
  [{endpoint    :lrs-endpoint
    input-uri   :insert-input
    size        :insert-size
    batch-size  :batch-size
    user        :user
    pass        :pass
    sref-type   :statement-ref-type
    concurrency :concurrency}]
  (let [inputs   (read-insert-input input-uri)
        stmts    (generate-statements inputs size sref-type)
        requests (mapv (fn [batch]
                         {:headers    headers
                          :body       (u/write-json-str (vec batch))
                          :basic-auth [user pass]})
                       (partition-all batch-size stmts))
        timings  (perform-async-op! perform-insert
                                    endpoint
                                    requests
                                    concurrency)]
    (calc-statistics timings (count timings))))

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

;; Sync

(defn- query-statements-sync*
  "Perform one query `query-times`."
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

(defn query-statements-sync
  [{endpoint    :lrs-endpoint
    query-uri   :query-input
    query-times :query-number
    user        :user
    pass        :pass}]
  (loop [queries (if query-uri (read-query-input query-uri) [{}])
         results (transient [])]
    (if-some [query (first queries)]
      (let [res (query-statements-sync* endpoint
                                        query
                                        query-times
                                        user
                                        pass)]
        (recur (rest queries)
               (conj! results res)))
      (persistent! results))))

;; Async

(defn query-statements-async
  [{endpoint    :lrs-endpoint
    query-uri   :query-input
    query-times :query-number
    user        :user
    pass        :pass
    concurrency :concurrency}]
  (let [queries  (if query-uri (read-query-input query-uri) [{}])
        requests (mapcat (fn [query]
                           (repeat query-times
                                   {:headers      headers
                                    :query-params (not-empty query)
                                    :basic-auth   [user pass]}))
                         queries)
        query-fn (fn [endpoint req]
                   (let [qm (if-some [q (:query-params req)] q {})]
                     {:ms    (perform-query endpoint req)
                      :query qm}))
        results  (perform-async-op! query-fn
                                    endpoint
                                    requests
                                    concurrency)
        stats    (reduce
                  (fn [m {:keys [ms query]}] (update m query conj ms))
                  {}
                  results)
        stats'   (reduce-kv
                  (fn [acc query x-vec]
                    (->> (calc-statistics x-vec query-times)
                         (merge {:query query})
                         (conj acc)))
                  []
                  stats)]
    stats'))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Putting it all together
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main
  [& args]
  (let [{:keys [summary errors]
         :as _parsed-opts
         {:keys [insert-input
                 async?
                 query-number
                 help
                 insert-size
                 batch-size
                 ;; Options that aren't used in `-main` but are later on
                 _lrs-endpoint
                 _query-input
                 _statement-ref-type
                 _concurrency
                 _user
                 _pass]
          :as   opts} :options}
        (cli/parse-opts args cli-options)]
    ;; Check for errors
    (when (not-empty errors)
      (log/errorf "CLI Parse Errors:\n%s" (cstr/join "\n" errors))
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
      (let [store-statements! (if async?
                                store-statements-async!
                                store-statements-sync!)
            results           (store-statements! opts)
            _                 (log/info "Statement insertion finished.")]
        (printf "\n%s Insert benchmark results for n = %d (in ms) %s\n"
                "**********"
                (quot insert-size batch-size)
                "**********")
        (pprint/print-table [(merge {:n-statements insert-size
                                     :batch-size   batch-size}
                                    results)])))
    ;; Query statements
    (log/info "Starting statement query benching...")
    (let [query-statements (if async?
                             query-statements-async
                             query-statements-sync)
          results          (query-statements opts)]
      (log/info "Statement query benching finished.")
      (printf "\n%s Query benchmark results for n = %d (in ms) %s\n"
              "**********"
              query-number
              "**********")
      (pprint/print-table results)
      (println ""))))

(comment
  ;; Perform benching from the repl
  (-main
   "-e" "http://localhost:8080/xapi/statements"
   "-i" "dev-resources/bench/insert_input.json"
   "-q" "dev-resources/bench/query_input.json"
   "-a" "true" "-c" "20"
   "-u" "username" "-p" "password"))
