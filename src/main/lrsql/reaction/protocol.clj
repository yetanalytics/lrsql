(ns lrsql.reaction.protocol)

(defprotocol StatementReactor
  (-start-executor [this]
    "Start the reaction executor IFF there is an LRS reaction channel.")
  (-get-reactions [this tx ttl]
    "Get the list of reactions, subject to the cache with the provided time-to-live in msecs.")
  (-react-to-statement [this statement-id]
    "Given a trigger statement ID, check active reactions and possibly issue reaction statements to the LRS or add reaction errors."))
