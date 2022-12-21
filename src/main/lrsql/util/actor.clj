(ns lrsql.util.actor
  (:require [com.yetanalytics.lrs.xapi.agents :as agnt]))

;; NOTE: Actors and Groups are encoded without any intention of being parsed
;; back, hence this sort of simple encoding is sufficient.
(defn actor->ifi
  "Returns string of the format `<ifi-type>::<ifi-value>`.
   Returns `nil` if `actor` doesn't have an IFI (e.g. Anonymous Group)."
  [actor]
  (let [{?mbox    "mbox"
         ?sha     "mbox_sha1sum"
         ?openid  "openid"
         ?account "account"}
        actor]
    (cond
      ?mbox    (str "mbox::" ?mbox)
      ?sha     (str "mbox_sha1sum::" ?sha)
      ?openid  (str "openid::" ?openid)
      ?account (let [{acc-name "name"
                      acc-page "homePage"}
                     ?account]
                 (str "account::" acc-name "@" acc-page))
      :else    nil)))

(defn actor->ifi-coll
  "Similar to `actor->ifi`, except that it returns a vector of IFIs. If
   `actor` has a `member` field (i.e. it's a group), return the vector
   of those IFIs (even if it's an identified group)."
  [actor]
  (if-some [member (get actor "member")]
    (mapv actor->ifi member)
    [(actor->ifi actor)]))

(defn actor->person
  "Given the Agent or Group `actor`, return an equivalent Person object with
   the IFI and name wrapped in vectors."
  [actor]
  ;; agnt/person takes zero or more args
  (if actor
    (agnt/person actor)
    (agnt/person)))
