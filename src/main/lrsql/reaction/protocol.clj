(ns lrsql.reaction.protocol)

(defprotocol StatementReactor
  (-react-to-statement [this statement-id]
    "Given a trigger statement ID, check active reactions and possibly issue reaction statements to the LRS or add reaction errors."))
