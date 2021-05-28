(ns lrsql.conformance-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [config.core  :refer [env]]
            [next.jdbc    :as jdbc]
            [com.stuartsierra.component    :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.system :as system]
            [com.yetanalytics.lrs.test-runner :as conf]
            [lrsql.test-support :as support]
            [clojure.string :as cs]
            [clojure.tools.logging :as log]))

(def known-failures
  #{"XAPI-00125"
    ;; An LRS responds to a HEAD request in the same way as a GET
    ;; request, but without the message-body. This means run ALL GET
    ;; tests with HEAD
    "XAPI-00126"
    ;; An LRS accepts HEAD requests.
    "XAPI-00127"
    ;; An LRS rejects with error code 400 Bad Request, a PUT or POST
    ;; Request which does not have a "Content-Type" header
    ;; with value "application/json" or "multipart/mixed"
    "XAPI-00154" ;; TODO: broken by stmt-get-max/default
    ;; An LRS’s Statement API upon processing a successful GET
    ;; request with neither a “statementId” nor a
    ;; “voidedStatementId” parameter, returns code 200 OK and a
    ;; StatementResult Object.
    "XAPI-00162"
    ;; An LRS&'s Statement API processes a successful GET
    ;; request using a parameter (such as stored time) which
    ;; includes a voided statement and unvoided statements targeting
    ;; the voided statement. The API must return 200 Ok and the
    ;; statement result object, containing statements which target a
    ;; voided statement, but not the voided statement itself.
    "XAPI-00183"
    ;; A Document Merge only performs overwrites at one level deep,
    ;; although the entire object is replaced.
    "XAPI-00184"
    ;; A Document Merge overwrites any duplicate values from the
    ;; previous document with the new document.
    "XAPI-00188"
    ;; An LRS's State API upon processing a successful GET request
    ;; returns 200 Ok, State Document
    "XAPI-00192"
    ;; An LRS's State API upon processing a successful GET
    ;; request with a valid "stateId" as a parameter
    ;; returns the document satisfying the requirements of the GET
    ;; and code 200 OK NOTE: There is no requirement here that the
    ;; LRS reacts to the "since" parameter in the case of
    ;; a GET request with valid "stateId" - this is
    ;; intentional
    "XAPI-00217"
    ;; An LRS's State API can process a GET request with
    ;; "stateId" as a parameter
    "XAPI-00220"
    ;; An LRS's State API can process a GET request with
    ;; "registration" as a parameter
    "XAPI-00229"
    ;; An LRS's State API, rejects a POST request if the
    ;; document is found and either document is not a valid JSON
    ;; Object
    "XAPI-00232"
    ;; An LRS's State API, rejects a POST request if the
    ;; document is found and either document's type is not
    ;; "application/json" with error code 400 Bad Request
    "XAPI-00233"
    ;; An LRS's State API, upon receiving a POST request for a
    ;; document not currently in the LRS, treats it as a PUT request
    ;; and store a new document. Returning 204 No Content
    "XAPI-00234"
    ;; An LRS's State API performs a Document Merge if a
    ;; profileId is found and both it and the document in the POST
    ;; request have type "application/json". If the merge
    ;; is successful, the LRS MUST respond with HTTP status code 204
    ;; No Content.
    "XAPI-00254"
    ;; The Activity Object must contain all available information
    ;; about an activity from any statements who target the same
    ;; “activityId”. For example, LRS accepts two statements each
    ;; with a different language description of an activity using
    ;; the exact same “activityId”. The LRS must return both
    ;; language descriptions when a GET request is made to the
    ;; Activities endpoint for that “activityId”.
    "XAPI-00259"
    ;; The Agent Profile API MUST return 200 OK - Profile Content
    ;; when a GET request is received with a valid agent JSON
    ;; Object.
    "XAPI-00269"
    ;;  An LRS's Agent Profile API upon processing a successful
    ;; GET request with a valid Agent Object and valid
    ;; "profileId" as a parameter returns the document satisfying
    ;; the requirements of the GET and code 200 OK
    "XAPI-00274"
    ;; An LRS's Agent Profile API accepts valid GET requests
    ;; with code 200 OK, Profile document
    "XAPI-00278"
    ;; An LRS's Agent Profile API, rejects a POST request if
    ;; the document is found and either document's type is not
    ;; "application/json" with error code 400 Bad Request
    "XAPI-00279"
    ;; An LRS's Agent Profile API performs a Document Merge if
    ;; a profileId is found and both it and the document in the POST
    ;; request have type "application/json" If the merge
    ;; is successful, the LRS MUST respond with HTTP status code 204
    ;; No Content.
    "XAPI-00280"
    ;; An LRS's Agent Profile API, upon receiving a POST
    ;; request for a document not currently in the LRS, treats it as
    ;; a PUT request and store a new document.Returning 204 No
    ;; Content
    "XAPI-00281"
    ;; An LRS's Agent Profile API, rejects a POST request if
    ;; the document is found and either document is not a valid JSON
    ;; Object
    "XAPI-00282"
    ;; An LRS has an Agent Profile API with endpoint "base
    ;; IRI"+"/agents/profile"
    "XAPI-00288"
    ;; An LRS's Activity Profile API upon processing a
    ;; successful GET request with a valid "profileId"
    ;; as a parameter returns the document satisfying the
    ;; requirements of the GET and code 200 OK
    "XAPI-00290"
    ;; An LRS's Activity Profile API accepts GET requests
    "XAPI-00308"
    ;; An LRS's Activity Profile API performs a Document Merge
    ;; if a activityId is found and both it and the document in the
    ;; POST request have type "application/json" If the
    ;; merge is successful, the LRS MUST respond with HTTP status
    ;; code 204 No Content.
    "XAPI-00309"
    ;; An LRS's Activity Profile API, rejects a POST request if
    ;; the document is found and either document's type is not
    ;; "application/json" with error code 400 Bad Request
    "XAPI-00310"
    ;; An LRS's Activity Profile API, upon receiving a POST
    ;; request for a document not currently in the LRS, treats it as
    ;; a PUT request and store a new document. Returning 204 No
    ;; Content
    "XAPI-00313"
    ;; An LRS's Activity Profile API, rejects a POST request if
    ;; the document is found and either document is not a valid JSON
    ;; Object
    "XAPI-00314"
    ;; An LRS must reject, with 400 Bad Request, a POST request to
    ;; the Activity Profile API which contains name/value pairs with
    ;; invalid JSON and the Content-Type header is "application/json
    "XAPI-00322"
    ;; An LRS must support HTTP/1.1 entity tags (ETags) to implement
    ;; optimistic concurrency control when handling APIs where PUT
    ;; may overwrite existing data (State, Agent Profile, and
    ;; Activity Profile)
    })

(deftest conformance-test
  (support/assert-in-mem-db)
  (conf/with-test-suite
    (testing "known failures"
      (doseq [code known-failures]
        (testing (format "requirement: %s" code)
          ;; invoke the fixture manually so we get a fresh db for each assert
          (support/fresh-db-fixture
           (fn []
             (let [;; chomp the logs for the system itself
                   _ (log/log-capture!
                      'lrsql.conformance-test
                      :debug
                      :debug)
                   sys (system/system)
                   sys' (component/start sys)
                   ;; run test suite w/o bail
                   conformant?
                   (conf/conformant?
                    "-e" "http://localhost:8080/xapi" "-z"
                    "-g" code)
                   ;; stop capturing logs so we don't mess with test output
                   _ (log/log-uncapture!)]
               (is (not conformant?))
               (component/stop sys')))))))))
