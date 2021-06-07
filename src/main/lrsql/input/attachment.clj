(ns lrsql.input.attachment
  (:require [clojure.spec.alpha :as s]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.spec.common :as c]
            [lrsql.spec.attachment :as as]
            [lrsql.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Attachment Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef attachment-insert-input
  :args (s/cat :statement-id ::c/statement-id
               :attachment ::ss/attachment)
  :ret ::as/attachment-input)

(defn attachment-insert-input
  "Given `statement-id` and `attachment`, construct the input for
   `functions/insert-attachment!`. `statement-id` will be associated with
   `attachment` as a foreign key reference."
  [statement-id attachment]
  (let [{contents     :content
         content-type :contentType
         length       :length
         sha2         :sha2}
        attachment]
    {:table          :attachment
     :primary-key    (u/generate-squuid)
     :statement-id   statement-id
     :attachment-sha sha2
     :content-type   content-type
     :content-length length
     :contents       (u/data->bytes contents)}))
