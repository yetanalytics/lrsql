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
    ;; TODO: Query the statement by ID first; if IDs match, compare the payloads
    ;; to determine if the two statements are the same, in which case throw
    ;; an exception.
    ;; TODO: Void statements if appropriate.
    (do (f/insert-statement conn input)
        (:statement-id input)) ; Success! (Too bad H2 doesn't have INSERT...RETURNING)
    :agent
    (let [input' (select-keys input [:agent-ifi])
          exists (f/query-agent-exists conn input')]
      (when-not exists (f/insert-agent conn input)))
    :activity
    (let [input' (select-keys input [:activity-iri])
          exists  (f/query-activity-exists conn input')]
      (when-not exists (f/insert-activity conn input)))
    :attachment
    (let [input' (select-keys input [:attachment-sha])
          exists (f/query-attachment-exists conn input')]
      (when-not exists (f/insert-attachment conn input)))
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
