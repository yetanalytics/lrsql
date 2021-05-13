(ns lrsql.hugsql.command
  "DB commands that utilize HugSql functions."
  (:require [lrsql.hugsql.functions :as f]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Commands
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn insert-input!
  [conn {:keys [table] :as input}]
  (case table
    :statement
    (f/insert-statement conn input)
    :agent
    (let [input' (select-keys input [:agent-ifi])
          count  (f/query-agent-count conn input')]
      (when (zero? count) (f/insert-agent conn input)))
    :activity
    (let [input' (select-keys input [:activity-iri])
          count  (f/query-activity-count conn input')]
      (when (zero? count) (f/insert-agent conn input)))
    :attachment
    (let [input' (select-keys input [:attachment-sha])
          count  (f/query-attachment-count conn input')]
      (when (zero? count) (f/insert-attachment conn input)))
    :statement-to-agent
    (f/insert-statement-to-agent conn input)
    :statement-to-activity
    (f/insert-statement-to-activity conn input)
    :statement-to-attachment
    (f/insert-statement-to-attachment conn input)
    :state-document ; TODO
    nil
    :agent-profile-document ; TODO
    nil
    :activity-profile-document ; TODO
    nil))

(defn insert-inputs!
  [conn inputs]
  (doall (map (partial insert-input! conn) inputs)))

(defn query-statement-input
  [conn input]
  (f/query-statement conn input))
