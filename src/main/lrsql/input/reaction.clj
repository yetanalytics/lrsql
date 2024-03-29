(ns lrsql.input.reaction
  (:require [clojure.spec.alpha :as s]
            [lrsql.spec.reaction :as rs]
            [lrsql.util :as u]))

(s/fdef insert-reaction-input
  :args (s/cat :title ::rs/title :ruleset ::rs/ruleset :active ::rs/active)
  :ret rs/insert-reaction-input-spec)

(defn insert-reaction-input
  "Given `ruleset` and `active`, construct the input map for `insert-reaction!`."
  [title ruleset active]
  (let [{squuid    :squuid
         squuid-ts :timestamp} (u/generate-squuid*)]
    {:primary-key squuid
     :title       title
     :ruleset     ruleset
     :active      active
     :created     squuid-ts
     :modified    squuid-ts}))

(s/fdef update-reaction-input
  :args (s/cat :reaction-id ::rs/reaction-id
               :title (s/nilable ::rs/title)
               :ruleset (s/nilable ::rs/ruleset)
               :active (s/nilable ::rs/active))
  :ret rs/update-reaction-input-spec)

(defn update-reaction-input
  "Given `reaction-id`, `ruleset` and `active`, construct the input map for
  `update-reaction!`."
  [reaction-id title ruleset active]
  (merge
   {:reaction-id reaction-id
    :modified    (u/current-time)}
   (when title
     {:title title})
   (when ruleset
     {:ruleset ruleset})
   (when (some? active)
     {:active active})))

(s/fdef delete-reaction-input
  :args (s/cat :reaction-id ::rs/reaction-id)
  :ret rs/delete-reaction-input-spec)

(defn delete-reaction-input
  "Given `reaction-id`, construct the input map for `delete-reaction!`."
  [reaction-id]
  {:reaction-id reaction-id
   :modified    (u/current-time)})

(s/fdef error-reaction-input
  :args (s/cat :reaction-id ::rs/reaction-id
               :error       ::rs/error)
  :ret rs/error-reaction-input-spec)

(defn error-reaction-input
  "Given `reaction-id` and `error`, construct the input map for `error-reaction!`"
  [reaction-id error]
  {:reaction-id reaction-id
   :modified    (u/current-time)
   :error       error})
