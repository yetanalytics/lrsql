(ns lrsql.binary-attachment-test
  "HTTP-level regression coverage for lossless xAPI binary attachments."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [cheshire.core :as json]
            [com.stuartsierra.component :as component]
            [lrsql.test-support :as support])
  (:import [java.io ByteArrayOutputStream]
           [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]
           [java.security MessageDigest]
           [java.util Base64 UUID]))

(use-fixtures :each support/fresh-db-fixture)

(defn- bytes-contain?
  [^bytes haystack ^bytes needle]
  (boolean
   (some (fn [i]
           (every? (fn [j]
                     (= (aget haystack (+ i j)) (aget needle j)))
                   (range (alength needle))))
         (range (inc (- (alength haystack) (alength needle)))))))

(defn- hex-sha256
  [^bytes payload]
  (apply str (map #(format "%02x" (bit-and 0xff %))
                  (.digest (doto (MessageDigest/getInstance "SHA-256")
                             (.update payload))))))

(defn- request
  [client method url auth content-type body]
  (let [builder (doto (HttpRequest/newBuilder (URI/create url))
                  (.header "Authorization" auth)
                  (.header "X-Experience-API-Version" "2.0.0"))
        builder (if content-type (.header builder "Content-Type" content-type) builder)
        builder (.method builder method
                         (if body
                           (HttpRequest$BodyPublishers/ofByteArray body)
                           (HttpRequest$BodyPublishers/noBody)))]
    (.send client (.build builder) (HttpResponse$BodyHandlers/ofByteArray))))

(deftest binary-attachment-http-roundtrip-test
  (let [system (component/start (support/test-system))]
    (try
      (let [{:keys [api-key-default api-secret-default]} (get-in system [:lrs :config])
            basic (.encodeToString (Base64/getEncoder)
                                   (.getBytes (str api-key-default ":" api-secret-default)
                                              "UTF-8"))
            auth (str "Basic " basic)
            client (HttpClient/newHttpClient)
            url "http://localhost:8080/xapi/statements"
            boundary "binaryattachmentboundary"
            stmt-id (str (UUID/randomUUID))
            payload (byte-array (map unchecked-byte [255 254 0 128 137 80 78 71]))
            sha (hex-sha256 payload)
            statement {"id" stmt-id
                       "actor" {"mbox" "mailto:binary-attachment@example.org"}
                       "verb" {"id" "http://adlnet.gov/expapi/verbs/attempted"}
                       "object" {"id" "http://example.org/binary-attachment"}
                       "attachments" [{"usageType" "http://example.org/attachment"
                                       "display" {"en-US" "Binary attachment"}
                                       "contentType" "application/octet-stream"
                                       "length" (alength payload)
                                       "sha2" sha}]}
            body (ByteArrayOutputStream.)]
        (.write body (.getBytes
                      (str "--" boundary "\r\nContent-Type: application/json\r\n\r\n"
                           (json/generate-string statement)
                           "\r\n--" boundary "\r\n"
                           "Content-Type: application/octet-stream\r\n"
                           "Content-Transfer-Encoding: binary\r\n"
                           "X-Experience-API-Hash: " sha "\r\n\r\n")
                      "UTF-8"))
        (.write body payload)
        (.write body (.getBytes (str "\r\n--" boundary "--\r\n") "US-ASCII"))
        (testing "binary multipart POST succeeds without transcoding"
          (let [resp (request client "POST" url auth
                              (str "multipart/mixed; boundary=" boundary)
                              (.toByteArray body))]
            (is (= 200 (.statusCode resp))
                (str "Unexpected POST status: " (.statusCode resp)
                     " " (String. ^bytes (.body resp) "UTF-8")))))
        (testing "GET attachments=true contains the exact original bytes"
          (let [resp (request client "GET"
                              (str url "?statementId=" stmt-id "&attachments=true")
                              auth nil nil)
                bytes ^bytes (.body resp)
                response-text (String. bytes "ISO-8859-1")]
            (is (= 200 (.statusCode resp))
                (str "Unexpected GET status: " (.statusCode resp)))
            (is (bytes-contain? bytes payload)
                "GET multipart attachment must preserve all eight original bytes")
            (is (.contains response-text sha)
                "GET multipart response must retain the declared SHA-256"))))
      (finally
        (component/stop system)))))
