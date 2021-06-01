(ns lrsql.hugsql.util
  (:require [clj-uuid]
            [java-time]
            [clojure.spec.alpha :as s]
            [clojure.data.json  :as json]
            [clojure.java.io    :as io])
  (:import [java.util UUID]
           [java.time Instant]
           [java.io StringReader PushbackReader ByteArrayOutputStream]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Macros
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro wrap-parse-fn
  "Wrap `(parse-fn s)` in an exception such that on parse failure, the
   folloiwing error data is thrown:
     :kind      ::parse-failure
     :data      `data`
     :str-type  `str-type`"
  [parse-fn data-type data]
  `(try (~parse-fn ~data)
        (catch Exception e#
          (throw (ex-info (format "Cannot parse nil or invalid %s"
                                  ~data-type)
                          {:kind     ::parse-failure
                           :data     ~data
                           :daa-type ~data-type})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Timestamps
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn current-time
  "Return the current time as a java.util.Instant timestamp."
  []
  (java-time/instant))

(defn str->time
  "Parse a string into a java.util.Instant timestamp."
  [ts-str]
  (wrap-parse-fn java-time/instant "timestamp" ts-str))

(defn time->str
  "Convert a java.util.Instant timestamp into a string."
  [ts]
  (java-time/format ts))

(defn time->millis
  "Convert a java.util.Instant timestamp into a number representing the
   number of milliseconds since January 1, 1970."
  [^Instant ts]
  (.toEpochMilli ts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UUIDs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; The overall approach of generating a 48-bit timestamp and merging it into
;; a v4 UUID is taken from the Laravel PHP library's orderedUuid function:
;; https://itnext.io/laravel-the-mysterious-ordered-uuid-29e7500b4f8

;; The idea of incrementing the least significant bit on a timestamp collision
;; is taken from the ULID specification:
;; https://github.com/ulid/spec

(def ^{:private true :const true} bit-mask-12 0x0000000000000FFF)
(def ^{:private true :const true} bit-mask-16 0x000000000000FFFF)
(def ^{:private true :const true} bit-mask-36 0x0000FFFFFFFFFFFF)
(def ^{:private true :const true} bit-mask-61 0x1FFFFFFFFFFFFFFF)

(def max-time (java-time/instant bit-mask-36))

(def ^:private max-time-emsg
  (str "Cannot generate SQUUID past August 2, 10889."
       " The java.time.Instant timestamp would have exceeded 48 bits."))

(defn- assert-valid-time
  [ts]
  (when-not (java-time/before? ts max-time)
    (throw (ex-info max-time-emsg
                    {:kind ::exceeded-max-time
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
                      {:kind ::exceeded-max-uuid-node
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

(s/def ::timestamp inst?)
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

(defn str->uuid
  "Parse a string into an UUID."
  [uuid-str]
  (wrap-parse-fn UUID/fromString "UUID" uuid-str))

(defn uuid->str
  "Convert a UUID into a string."
  [uuid]
  (clj-uuid/to-string uuid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn read-str-strict
  "Reads one JSON value from input String. Throws if there are more.."
  [string & {:as options}]
  (let [rdr (PushbackReader. (StringReader. string) 64)
        obj (apply
             json/read rdr
             (mapcat identity options))]
    (if (apply json/read rdr
               (mapcat identity (assoc options
                                       :eof-error? false)))
      (throw (ex-info "More input after JSON object"
                      {:type ::extra-input
                       :json string}))
      obj)))

(defn- parse-json*
  [data]
  (cond
    (string? data)
    (read-str-strict data)
    (bytes? data) ; H2 returns JSON data as a byte array
    (read-str-strict (String.  data))))
;; ^"[B"

(defn parse-json
  "Parse `data` into JSON format. `data` may be a string or a byte array."
  [data]
  (wrap-parse-fn parse-json* "JSON" data))

(defn write-json
  "Write `jsn` to a string."
  [jsn]
  (json/write-str jsn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bytes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn data->bytes
  [data]
  (if (bytes? data)
    data
    (let [baos (ByteArrayOutputStream.)]
      (with-open [in (io/input-stream data)]
        (io/copy in baos)
        (.flush baos)
        (.toByteArray baos)))))
