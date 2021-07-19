(ns lrsql.protocol
  "Protocols that serve as a low-level interface for DB functions.")

(defprotocol LrsqlDDL
  (-create-all! [this tx]
    "Create all tables and indexes.")
  (-drop-all! [this tx]
    "Drop all tables and delete all indexes."))

(defprotocol LrsqlInsert
  ;; Statements + Statement Objects
  (-insert-statement! [this tx input])
  (-insert-actor! [this tx input])
  (-insert-activity! [this tx input])
  (-insert-attachment! [this tx input])
  (-insert-statement-to-actor! [this tx input])
  (-insert-statement-to-activity! [this tx input])
  (-insert-statement-to-statement! [this tx input])
  ;; Documents
  (-insert-state-document! [this tx input])
  (-insert-agent-profile-document! [this tx input])
  (-insert-activity-profile-document! [this tx input])
  ;; Credentials + Admin Accounts
  (-insert-admin-account! [this tx input])
  (-insert-credential! [this tx input])
  (-insert-credential-scope! [this tx input]))

(defprotocol LrsqlUpdate
  ;; Actor + Activities
  (-update-actor! [this tx input])
  (-update-activity! [this tx input])
  ;; Verbs
  (-void-statement! [this tx input])
  ;; Documents
  (-update-state-document! [this tx input])
  (-update-agent-profile-document! [this tx input])
  (-update-activity-profile-document! [this tx input]))

(defprotocol LrsqlDelete
  ;; Documents
  (-delete-state-document! [this tx input])
  (-delete-state-documents! [this tx input])
  (-delete-agent-profile-document! [this tx input])
  (-delete-activity-profile-document! [this tx input])
  ;; Credentials + Admin Accounts
  (-delete-admin-account! [this tx input])
  (-delete-credential! [this tx input])
  (-delete-credential-scope! [this tx input]))

(defprotocol LrsqlQuery
  ;; Statements
  (-query-statement [this tx input])
  (-query-statements [this tx input])
  (-query-statement-exists [this tx input])
  ;; Statement Objects
  (-query-actor [this tx input])
  (-query-activity [this tx input])
  (-query-attachments [this tx input])
  ;; Statement References
  (-query-statement-descendants [this tx input])
  ;; Documents
  (-query-state-document [this tx input])
  (-query-agent-profile-document [this tx input])
  (-query-activity-profile-document [this tx input]))
