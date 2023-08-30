(ns lrsql.reaction.protocol)

(defprotocol StatementReactor
  (-start-executor [this]
    "Start the reaction executor IFF there is an LRS reaction channel.")
  (-react-to-statement [this statement-id]
    "Given a trigger statement ID, check active reactions and possibly issue reaction statements to the LRS or add reaction errors."))
