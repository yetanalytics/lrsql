(ns lrsql.util
  (:require [clj-uuid]
            [java-time]
            [java-time.properties :as jt-props]
            [aero.core :as aero]
            [hugsql.core :as hug]
            [clojure.spec.alpha :as s]
            [clojure.java.io    :as io]
            [cheshire.core      :as cjson]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.xapi.document :refer [json-bytes-gen-fn]]
            [com.yetanalytics.lrs.xapi.statements.timestamp :refer [normalize]]
            [lrsql.spec.common :refer [instant-spec]])
  (:import [java.util UUID]
           [java.time Instant]
           [java.io StringReader PushbackReader ByteArrayOutputStream]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Macros
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro def-hugsql-db-fns
  "A custom version of `hugsql.core/def-db-fns` that reads .sql files
   during compilation (instead of during runtime as in the original macro)."
  [file]
  (let [parsed-defs# (hug/parsed-defs-from-file file)]
    `(doseq [~'pdef ~parsed-defs#]
       (hug/validate-parsed-def! ~'pdef)
       (let [~'exp-pdef (hug/expand-compile-frags ~'pdef)]
         (hug/compile-exprs ~'exp-pdef)
         (hug/dispatch-on-pdef ~'exp-pdef
                               {} ; options - empty map is the hugsql default
                               ~hug/intern-db-fn
                               ~hug/intern-sqlvec-fn)))))

(defmacro wrap-parse-fn
  "Wrap `(parse-fn s)` in an exception such that on parse failure, the
   folloiwing error data is thrown:
     :type       ::parse-failure
     :data       `data`
     :data-type  `data-type`"
  [parse-fn data-type data]
  `(try (~parse-fn ~data)
        (catch Exception e#
          (throw (ex-info (format "Cannot parse nil or invalid %s"
                                  ~data-type)
                          {:type      ::parse-failure
                           :data      ~data
                           :data-type ~data-type})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Config
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn read-config*
  "Read `config/config.edn` with the given value of `profile`. Valid
   profiles are `:test-[db-type]` and `:prod-[db-type]`."
  [profile]
  (aero/read-config (io/resource "config.edn") {:profile profile}))

(def read-config
  "Memoized version of `read-config*`."
  (memoize read-config*))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Timestamps
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef current-time
  :args (s/cat)
  :ret instant-spec)

(defn current-time
  "Return the current time as a java.util.Instant timestamp."
  []
  (java-time/instant))

(def valid-units
  (set (map jt-props/unit-key jt-props/predefined-units)))

(s/fdef offset-time
  :args (s/cat :ts instant-spec
               :offset-amt nat-int?
               :offset-unit valid-units)
  :ret instant-spec)

(defn offset-time
  "Given a java.util.Instant timestamp `ts` and the offset given by the
   `offset-amount` int and the `offset-unit` keyword, return another timestamp
   that was offset by the given amount. Valid units are given by
   `java-time.repl/show-units`."
  [^Instant ts offset-amount offset-unit]
  (.plus ts offset-amount (java-time/unit offset-unit)))

(s/fdef str->time
  :args (s/cat :ts-str ::xs/timestamp)
  :ret instant-spec)

(defn str->time
  "Parse a string into a java.util.Instant timestamp."
  [ts-str]
  (wrap-parse-fn java-time/instant "timestamp" ts-str))

(s/fdef time->str
  :args (s/cat :ts instant-spec)
  :ret ::xs/timestamp)

(defn time->str
  "Convert a java.util.Instant timestamp into a string. Said string is
   normalized according to the requirements of the lrs library."
  [ts]
  (normalize (java-time/format ts)))

(s/fdef time->millis
  :args (s/cat :ts instant-spec)
  :ret nat-int?)

(defn time->millis
  "Convert a java.util.Instant timestamp into a number representing the
   number of milliseconds since January 1, 1970."
  [^Instant ts]
  (.toEpochMilli ts))

(comment
  (def t #inst "2021")
  (normalize (java-time/format t)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UUIDs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; The overall approach of generating a 48-bit timestamp and merging it into
;; a v4 UUID is taken from the Laravel PHP library's orderedUuid function:
;; https://itnext.io/laravel-the-mysterious-ordered-uuid-29e7500b4f8

;; The idea of incrementing the least significant bit on a timestamp collision
;; is taken from the ULID specification:
;; https://github.com/ulid/spec

(def ^{:private true :const true} bit-mask-12
  (unchecked-long 0x0000000000000FFF))
(def ^{:private true :const true} bit-mask-16
  (unchecked-long 0x000000000000FFFF))
(def ^{:private true :const true} bit-mask-36
  (unchecked-long 0x0000FFFFFFFFFFFF))
(def ^{:private true :const true} bit-mask-61
  (unchecked-long 0x1FFFFFFFFFFFFFFF))
(def ^{:private true :const true} bit-mask-64
  (unchecked-long 0xFFFFFFFFFFFFFFFF))

(def max-time (java-time/instant bit-mask-36))

(def ^:private max-time-emsg
  (str "Cannot generate SQUUID past August 2, 10889."
       " The java.time.Instant timestamp would have exceeded 48 bits."))

(defn- assert-valid-time
  [ts]
  (when-not (java-time/before? ts max-time)
    (throw (ex-info max-time-emsg
                    {:type ::exceeded-max-time
                     :time ts}))))

(defn- inc-uuid
  [uuid]
  (let [uuid-msb (clj-uuid/get-word-high uuid)
        uuid-lsb (clj-uuid/get-word-low uuid)]
    (cond
      ;; least significant bits not maxed out
      (not (zero? (bit-and-not bit-mask-61 uuid-lsb)))
      (UUID. uuid-msb (inc uuid-lsb))
      ;; most significant bits not maxed out
      (not (zero? (bit-and-not bit-mask-12 uuid-msb)))
      (UUID. (inc uuid-msb) uuid-lsb)
      ;; oh no
      :else
      (throw (ex-info (format "Cannot increment UUID %s any further."
                              uuid)
                      {:type ::exceeded-max-uuid-node
                       :uuid uuid})))))

(defn- make-squuid
  [ts]
  (let [;; Base UUID
        uuid      (clj-uuid/v4)
        uuid-msb  (clj-uuid/get-word-high uuid)
        uuid-lsb  (clj-uuid/get-word-low uuid)
        ;; Timestamp
        ts-long   (time->millis ts)
        ;; Putting it all together
        uuid-msb' (bit-or (bit-shift-left ts-long 16)
                          (bit-and bit-mask-16 uuid-msb))
        squuid    (UUID. uuid-msb' uuid-lsb)]
    {:base-uuid uuid
     :squuid    squuid}))

;; The atom is private so that only generate-squuid(*) can mutate it.
;; Note that merging Instant/EPOCH with v0 UUID returns the v0 UUID again.
(def ^:private current-time-atom
  (atom {:timestamp (Instant/EPOCH)
         :base-uuid (clj-uuid/v0)
         :squuid    (clj-uuid/v0)}))

(s/def ::timestamp instant-spec)
(s/def ::base-uuid uuid?)
(s/def ::squuid uuid?)

(s/fdef generate-squuid*
  :args (s/cat)
  :ret (s/keys :req-un [::timestamp ::base-uuid ::squuid]))

(defn generate-squuid*
  "Return a map containing the following:
   :squuid     The sequential UUID made up of a base UUID and timestamp.
   :base-uuid  The base v4 UUID that provides the lower 80 bits.
   :timestamp  The timestamp that provides the higher 48 bits.
   
   The sequential UUIDs have 7 reserved bits from the RFC 4122 standard;
   4 for the UUID version and 3 for the UUID variant. This leaves 73 random
   bits, allowing for about 9.4 sextillion random segments.
   
   The timestamp is coerced to millisecond resolution. Due to the 48 bit
   maximum on the timestamp, the latest time supported is February 11, 10332.
   
   In case that this function is called multiple times in the same millisecond,
   subsequent SQUUIDs are created by incrementing the base UUID and thus the
   random segment of the SQUUID. An exception is thrown in the unlikely case
   where all 73 random bits are 1s and incrementing can no longer occur."
  []
  (let [ts (java-time/instant (System/currentTimeMillis))
        _  (assert-valid-time ts)
        {:keys [timestamp]} @current-time-atom]
    (if-not (java-time/after? ts timestamp)
      ;; Timestamp clash - increment UUIDs
      (swap! current-time-atom #(-> %
                                    (update :base-uuid inc-uuid)
                                    (update :squuid inc-uuid)))
      ;; No timestamp clash - make new UUIDs
      (swap! current-time-atom #(-> %
                                    (assoc :timestamp ts)
                                    (merge (make-squuid ts)))))))

(s/fdef generate-squuid
  :args (s/cat)
  :ret ::squuid)

(defn generate-squuid
  "Return a new sequential UUID, or SQUUID. The most significant 48 bits
   are created from a timestamp representing the current time, which always
   increments, enforcing sequentialness. See `generate-squuid*` for more
   details."
  []
  (:squuid (generate-squuid*)))

(s/fdef str->uuid
  :args (s/cat :uuid-str ::xs/uuid)
  :ret uuid?)

(defn str->uuid
  "Parse a string into an UUID."
  [uuid-str]
  (wrap-parse-fn UUID/fromString "UUID" uuid-str))

(s/fdef time->uuid
  :args (s/cat :ts instant-spec)
  :ret uuid?)

(defn time->uuid
  "Convert a java.util.Instant timestamp to a UUID. The upper 48 bits represent
   the timestamp, while the lower 80 bits are set to be all 1s."
  [^Instant ts]
  (let [ts-long  (time->millis ts)
        uuid-msb (bit-or (bit-shift-left ts-long 16)
                         bit-mask-16)
        uuid-lsb bit-mask-64]
    (UUID. uuid-msb uuid-lsb)))

(s/fdef uuid->str
  :args (s/cat :uuid uuid?)
  :ret ::xs/uuid)

(defn uuid->str
  "Convert a UUID into a string."
  [uuid]
  (clj-uuid/to-string uuid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Overall approach is taken from lrs:
;; https://github.com/yetanalytics/lrs/blob/master/src/main/com/yetanalytics/lrs/xapi/document.cljc
;; Reimplemented here as functions instead of dynamic vars in order to avoid
;; issues with AOT compliation.

(defn- parse-json*
  "Read a JSON string or byte array `data`."
  [data]
  (let [string (if (bytes? data) (String. ^"[B" data) data)]
    (with-open [rdr (PushbackReader. (StringReader. string) 64)]
      (doall (cjson/parsed-seq rdr)))))

(s/def ::object? boolean?)

(s/fdef parse-json
  :args (s/cat :data (s/with-gen
                       (s/or :string string? :bytes bytes?)
                       json-bytes-gen-fn)
               :kwargs (s/keys* :opt-un [::object?]))
  :ret ::xs/any-json)

(defn parse-json
  "Read a JSON string or byte array `data`. `data` must only consist of one
   JSON object, array, or scalar; in addition, `data` must be an object by
   default. To parse JSON arrays or scalars, set `:object?` to false."
  [data & {:keys [object?] :or {object? true}}]
  (let [[result & ?more] (wrap-parse-fn parse-json* "JSON" data)]
    (cond
      ?more
      (throw (ex-info "More input after first JSON data!"
                      {:type  ::extra-json-input
                       :first result
                       :rest  ?more}))
      (and object? (not (map? result)))
      (throw (ex-info "Parsed JSON result is not an object!"
                      {:type   ::not-json-object
                       :result result}))
      :else
      result)))

(defn- write-json*
  "Write `jsn` to an output stream."
  [out-stream jsn]
  (with-open [wtr (io/writer out-stream)]
    (cjson/generate-stream jsn wtr)
    out-stream))

(s/fdef write-json
  :args (s/cat :jsn ::xs/any-json)
  :ret bytes?)

(defn write-json
  "Write `jsn` to a byte array."
  [jsn]
  (let [out-stream (ByteArrayOutputStream. 4096)]
    (.toByteArray ^ByteArrayOutputStream (write-json* out-stream jsn))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bytes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; IOFactory predicate taken from lrs.xapi.statements.attachment/content
(s/fdef data->bytes ; TODO: Add more options if need be
  :args (s/cat :data (s/or :string string?
                           :bytes bytes?
                           :io-factory #(satisfies? clojure.java.io/IOFactory %)))
  :ret bytes?)

(defn data->bytes
  "Convert `data` into a byte array."
  [data]
  (if (bytes? data)
    data
    (let [baos (ByteArrayOutputStream.)]
      (with-open [in (io/input-stream data)]
        (io/copy in baos)
        (.flush baos)
        (.toByteArray baos)))))
