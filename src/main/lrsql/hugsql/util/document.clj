(ns lrsql.hugsql.util.document)

(defn document-dispatch
  "Return either `:state-document`, `:agent-profile-document`, or
   `:activity-profile-document` depending on the fields in `params`. Works for
   both ID params and query params."
  [{state-id    :stateId
    profile-id  :profileId
    activity-id :activityId
    agent       :agent
    :as         params}]
  (cond
    ;; ID params
    state-id
    :state-document
    (and profile-id agent)
    :agent-profile-document
    (and profile-id activity-id)
    :activity-profile-document
    ;; Query params
    (and activity-id agent)
    :state-document
    activity-id
    :activity-profile-document
    agent
    :agent-profile-document
    ;; Error
    :else
    (throw (ex-info "Invalid document ID or query parameters!"
                    {:kind   ::invalid-document-resource-params
                     :params params}))))
