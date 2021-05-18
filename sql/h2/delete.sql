-- :name delete-state-document!
-- :command :execute
-- :result :affected
-- :doc Delete a state document.
DELETE FROM state_document
WHERE activity_iri = :activity-iri
AND agent_ifi = :agent-ifi
--~ (when (:state-id params) "AND state_id = :state-id")
--~ (when (:?registration params) "AND registration = :?registration")

-- :name delete-agent-profile-document!
-- :command :execute
-- :result :affected
-- :doc Delete an agent profile document.
DELETE FROM agent_profile_document
WHERE profile_id = :profile-id
AND agent_ifi = :agent-ifi 

-- :name delete-activity-profile-document!
-- :command :execute
-- :result :affected
-- :doc Delete an activity profile document.
DELETE FROM activity_profile_document
WHERE profile_id = :profile-id
AND activity_iri = :activity-iri
