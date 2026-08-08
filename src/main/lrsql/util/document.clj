(ns lrsql.util.document
  (:require [com.yetanalytics.lrs.xapi.document :as doc])
  (:import [java.security MessageDigest]))

(defn compute-etag
  "Compute an ETag (SHA-1 hex string) from document contents (byte array).
   Matches the etag computation in the upstream LRS interceptor layer
   (com.yetanalytics.lrs.util.hash/sha-1)."
  ^String [^bytes contents]
  (apply str
         (map #(.substring
                (Integer/toString
                 (+ (bit-and % 0xff) 0x100) 16) 1)
              (.digest (MessageDigest/getInstance "SHA-1")
                       contents))))

(defn parse-etag-header
  "Parse an If-Match or If-None-Match header value into a set of etag strings.
   Strips surrounding quotes. Returns nil if header is nil."
  [header-value]
  (when header-value
    (into #{} (re-seq #"\w+" header-value))))

(defn check-etag-precondition
  "Validate If-Match / If-None-Match preconditions against the current document.
   Returns nil if preconditions pass, or an error map if they fail.

   - `if-match`: If present, the request etag must match the current document's etag.
     '*' means any document must exist.
   - `if-none-match`: If present, the request etag must NOT match.
     '*' means no document must exist.
   - `current-doc`: The current document map from query-document (may have :contents),
     or nil if no document exists."
  [if-match if-none-match current-doc]
  (let [current-etag (when-let [contents (:contents current-doc)]
                       (compute-etag (if (bytes? contents)
                                       contents
                                       (.getBytes (str contents) "UTF-8"))))
        doc-exists?  (some? current-doc)]
    (cond
      ;; If-Match validation
      (and if-match (= if-match "*") (not doc-exists?))
      {:error (ex-info "Precondition Failed: If-Match * but no document exists"
                       {:type ::doc/precondition-failed})}

      (and if-match (not= if-match "*"))
      (let [match-etags (parse-etag-header if-match)]
        (when-not (and current-etag (contains? match-etags current-etag))
          {:error (ex-info "Precondition Failed: If-Match etag mismatch"
                           {:type ::doc/precondition-failed
                            :expected match-etags
                            :actual current-etag})}))

      ;; If-None-Match validation
      (and if-none-match (= if-none-match "*") doc-exists?)
      {:error (ex-info "Precondition Failed: If-None-Match * but document exists"
                       {:type ::doc/precondition-failed})}

      (and if-none-match (not= if-none-match "*"))
      (let [none-match-etags (parse-etag-header if-none-match)]
        (when (and current-etag (contains? none-match-etags current-etag))
          {:error (ex-info "Precondition Failed: If-None-Match etag matches"
                           {:type ::doc/precondition-failed
                            :rejected none-match-etags
                            :actual current-etag})}))

      ;; No precondition headers or all checks passed
      :else nil)))

(defn document-dispatch
  "Return either `:state-document`, `:agent-profile-document`, or
   `:activity-profile-document` depending on the fields in `params`. Works for
   both ID params and query params."
  [{?state-id    :stateId
    ?profile-id  :profileId
    ?activity-id :activityId
    ?agent       :agent
    :as          params}]
  (cond
    ;; ID params
    ?state-id
    :state-document
    (and ?profile-id ?agent)
    :agent-profile-document
    (and ?profile-id ?activity-id)
    :activity-profile-document
    ;; Query params
    (and ?activity-id ?agent)
    :state-document
    ?activity-id
    :activity-profile-document
    ?agent
    :agent-profile-document
    ;; Error
    :else
    (throw (ex-info "Invalid document ID or query parameters!"
                    {:type   ::invalid-document-resource-params
                     :params params}))))
