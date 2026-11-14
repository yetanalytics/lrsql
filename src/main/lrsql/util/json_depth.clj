(ns lrsql.util.json-depth
  "A nesting-depth limit for request bodies, checked before any parser runs.

   Pedestal's body-params (on the xAPI statement, about, agents and activities
   routes and on every admin route) parses JSON with charred, EDN with
   clojure.edn and Transit with transit-java, and lrs's parse-multiparts
   parses the first part of a multipart/mixed statement body with cheshire.
   The document routes parse JSON documents with cheshire. Each of these
   recurses once per nesting level, and charred, clojure.edn and msgpack have
   no depth limit, so a body nested 2,000 levels or more overflowed the
   thread's stack and answered 500.

   `depth-limit-interceptor` sits ahead of those parsers: in the xAPI routes'
   wrap-interceptors (lrsql.system.webserver) and in the admin routes' common
   interceptors (lrsql.admin.routes). It decides by the same tests the parsers
   use, so the two cannot disagree about a body:

   - Content-Type matching ^application/json (body-params' own pattern, so
     `application/json; charset=utf-8` too): the whole body is read, scanned,
     and handed on unchanged. Deeper than `max-depth` -> 400.
   - Content-Type starting with multipart (parse-multiparts' own test) and a
     boundary that lrs's find-boundary accepts: only the first part, the one
     parse-multiparts parses as JSON, is read and scanned; the rest of the
     body is handed on unread. More than `max-first-part-bytes` without the
     end of the first part -> 413.
   - Content-Type matching ^application/edn, ^application/transit\\+json or
     ^application/transit\\+msgpack with a non-empty body, on any route but the
     document routes -> 415. Nothing in SQL LRS or its admin UI sends these.
     The document routes store such a body as bytes and never parse it, so it
     is left alone there.

   The scan counts [ and { outside JSON strings on the raw bytes (UTF-8
   multibyte sequences never contain those bytes, nor \" or \\), and stops at
   the first byte past the limit."
  (:require [clojure.string :as cstr]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.chain :as chain]
            [com.yetanalytics.lrs.pedestal.http.multipart-mixed :as multipart])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream InputStream
            PushbackInputStream SequenceInputStream]
           [java.nio.charset StandardCharsets]))

(def max-depth
  "The deepest nesting of a JSON request body that is accepted. A statement
   nests about 6 to 12 levels (a batch adds 1), leaving extensions over 50."
  64)

(def max-first-part-bytes
  "How much of a multipart/mixed body is read while looking for the end of
   its first part."
  (* 8 1024 1024))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Scan
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn too-deep?
  "True if the JSON text in `bs` from `start` (inclusive) to `end` (exclusive)
   nests deeper than `limit`. Not a validator: malformed JSON is the parser's
   to refuse."
  ([^bytes bs limit] (too-deep? bs 0 (alength bs) limit))
  ([^bytes bs start end limit]
   (let [start (long start) end (long end) limit (long limit)]
     (loop [i start depth 0 in-str false esc false]
       (if (>= i end)
         false
         (let [b (long (aget bs i))]
           (cond
             esc             (recur (inc i) depth true false)
             in-str          (case b
                               92 (recur (inc i) depth true true)    ; \
                               34 (recur (inc i) depth false false)  ; "
                               (recur (inc i) depth true false))
             :else           (case b
                               34 (recur (inc i) depth true false)
                               (91 123) (if (>= depth limit)          ; [ {
                                          true
                                          (recur (inc i) (inc depth) false false))
                               (93 125) (recur (inc i) (max 0 (dec depth)) false false)
                               (recur (inc i) depth false false)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bodies
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- read-all ^bytes [^InputStream in]
  (let [out (ByteArrayOutputStream.)
        buf (byte-array 65536)]
    (loop []
      (let [n (.read in buf)]
        (when (pos? n)
          (.write out buf 0 n)
          (recur))))
    (.toByteArray out)))

(defn- index-of
  "The index of `pat` in `bs` at or after `from` and before `end`, or -1."
  ^long [^bytes bs ^bytes pat from end]
  (let [m (alength pat) last-start (- (long end) m)]
    (loop [i (long from)]
      (cond
        (> i last-start) -1
        (loop [j 0]
          (cond (= j m) true
                (= (aget bs (+ i j)) (aget pat j)) (recur (inc j))
                :else false)) i
        :else (recur (inc i))))))

(defn- first-part
  "Read `in` until the end of the first part of a multipart body with
   `boundary`, at most `cap` bytes. Returns {:read <bytes read>, :start and
   :end of the first part's content, or :over-cap true, or :not-found true
   when the stream ended first}."
  [^InputStream in ^String boundary cap]
  (let [open  (.getBytes (str "--" boundary) StandardCharsets/ISO_8859_1)
        delim (.getBytes (str "\r\n--" boundary) StandardCharsets/ISO_8859_1)
        crlf2 (.getBytes "\r\n\r\n" StandardCharsets/ISO_8859_1)
        out   (ByteArrayOutputStream.)
        buf   (byte-array 65536)]
    ;; `from`: where the next search for the delimiter starts, so each byte is
    ;; searched about once however many reads the part takes.
    (loop [start -1 from 0]
      (let [n (.read in buf 0 (int (min (alength buf) (- (long cap) (.size out) -1))))]
        (when (pos? n) (.write out buf 0 n))
        (let [bs    (.toByteArray out)
              size  (alength bs)
              start (if (neg? start)
                      (let [o (index-of bs open 0 size)
                            h (if (neg? o) -1 (index-of bs crlf2 o size))]
                        (if (neg? h) -1 (+ h 4)))
                      start)
              end   (if (neg? start) -1 (index-of bs delim (max start from) size))]
          (cond
            (not (neg? end)) {:read bs :start start :end end}
            (> size (long cap)) {:read bs :over-cap true}
            (pos? n) (recur start (max 0 (- size (alength delim))))
            :else {:read bs :not-found true}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interceptor
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; The patterns io.pedestal.http.body-params/default-parser-map registers.
(def ^:private json-pattern #"^application/json")
(def ^:private refused-patterns
  [#"^application/edn" #"^application/transit\+json" #"^application/transit\+msgpack"])

;; The document routes' route-name namespaces (lrs's build-document-routes).
(def ^:private document-route-nses
  #{"com.yetanalytics.lrs.xapi.activities.state"
    "com.yetanalytics.lrs.xapi.activities.profile"
    "com.yetanalytics.lrs.xapi.agents.profile"})

(defn- document-route? [ctx]
  (let [rn (get-in ctx [:route :route-name])]
    (and (keyword? rn) (contains? document-route-nses (namespace rn)))))

(defn- non-empty
  "body-params' own emptiness test: a PushbackInputStream with its first byte
   unread, or nil if the body is absent or empty."
  [o]
  (when (instance? InputStream o)
    (let [pbis (PushbackInputStream. ^InputStream o)
          b    (.read pbis)]
      (when-not (= b -1)
        (.unread pbis b)
        pbis))))

(defn- refuse [ctx status message]
  (assoc (chain/terminate ctx)
         :response
         {:status  status
          :headers {"Content-Type" "application/json"}
          :body    (str "{\"error\":{\"message\":\"" message "\"}}")}))

(def ^:private too-deep-msg
  (str "JSON nesting exceeds " max-depth " levels"))

(defn check-body
  "The :enter function of `depth-limit-interceptor`."
  [ctx]
  (let [request (:request ctx)
        ctype   (:content-type request)
        ;; parse-multiparts' own lookup and test
        mp-type (or ctype
                    (get-in request [:headers "content-type"])
                    (get-in request [:headers "Content-Type"]))]
    (cond
      (and (string? ctype) (re-find json-pattern ctype))
      (if-some [in (non-empty (:body request))]
        (let [bs (read-all in)]
          (if (too-deep? bs max-depth)
            (refuse ctx 400 too-deep-msg)
            (assoc-in ctx [:request :body] (ByteArrayInputStream. bs))))
        ctx)

      (and (string? mp-type)
           (.startsWith ^String (cstr/trim mp-type) "multipart")
           (instance? InputStream (:body request)))
      (if-some [boundary (multipart/find-boundary mp-type)]
        (let [in (:body request)
              {:keys [^bytes read start end over-cap]}
              (first-part in boundary max-first-part-bytes)
              body (SequenceInputStream. (ByteArrayInputStream. read) in)]
          (cond
            over-cap
            (refuse ctx 413 "The first part of the multipart body is too large")
            (and end (too-deep? read start end max-depth))
            (refuse ctx 400 too-deep-msg)
            :else
            (assoc-in ctx [:request :body] body)))
        ctx)

      (and (string? ctype)
           (some #(re-find % ctype) refused-patterns)
           (not (document-route? ctx)))
      (if-some [in (non-empty (:body request))]
        (refuse (assoc-in ctx [:request :body] in) 415
                "Unsupported Content-Type: use application/json")
        ctx)

      :else
      ctx)))

(def depth-limit-interceptor
  "Refuses, before any parser runs, a JSON body nested deeper than
   `max-depth` (400), a multipart body whose first part does not end within
   `max-first-part-bytes` (413), and an EDN or Transit body outside the
   document routes (415). Every other request passes unchanged."
  (interceptor
   {:name  ::depth-limit
    :enter check-body}))
