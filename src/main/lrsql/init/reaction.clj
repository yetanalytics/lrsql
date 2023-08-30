(ns lrsql.init.reaction
  "Reaction initialization functions."
  (:require [clojure.core.async            :as a]
            [clojure.spec.alpha            :as s]
            [clojure.tools.logging         :as log]
            [lrsql.reaction.protocol       :as rp]
            [lrsql.util                    :as u]
            [lrsql.spec.config             :as config-spec]
            [lrsql.spec.common             :as common-spec]
            [lrsql.spec.reaction           :as rs]
            [xapi-schema.spec              :as xs]
            [clojure.string                :as cs]))

(s/fdef reaction-channel
  :args (s/cat :config ::config-spec/lrs)
  :ret (s/nilable ::common-spec/channel))

(defn reaction-channel
  "Based on config, return a channel to receive reactions or nil if reactions
  are disabled"
  [{enable-reactions     :enable-reactions
    reaction-buffer-size :reaction-buffer-size}]
  (when enable-reactions
    (a/chan reaction-buffer-size)))

(s/fdef offer-trigger!
  :args (s/cat :?reaction-channel (s/nilable ::common-spec/channel)
               :trigger-id ::xs/uuid)
  :ret nil?)

(defn offer-trigger!
  "Given a (possibly nil) reaction channel and a string statement ID, submit the
  ID to the channel as a UUID if it exists, or do nothing if it is nil.
  Log if the channel exists but the ID cannot be submitted."
  [?reaction-channel trigger-id]
  (when ?reaction-channel
    (when-not (a/offer! ?reaction-channel (u/str->uuid trigger-id))
      (log/warnf "Reaction channel full, dropping statement ID: %s"
                 trigger-id))))

(s/fdef reaction-executor
  :args (s/cat :reaction-channel (s/nilable ::common-spec/channel)
               :reactor rs/reactor?)
  :ret (s/nilable ::common-spec/channel))

(defn reaction-executor
  "Given a (possibly nil) reaction channel and a reactor implementation, process
  reactions in a thread pool. If the channel is nil, returns nil."
  [reaction-channel reactor]
  (log/info "Starting reaction processor...")
  (let [reaction-executor
        (a/go-loop []
          (log/debug "Listening for reaction trigger...")
          (if-let [trigger-id (a/<! reaction-channel)]
            (let [_ (log/debugf "Reacting to statement ID: %s"
                                trigger-id)
                  {:keys [statement-ids]}
                  (a/<!
                   (a/thread
                     (rp/-react-to-statement reactor trigger-id)))]
              (when (not-empty statement-ids)
                (log/debugf "Created reaction to %s, statement IDs: %s"
                            trigger-id
                            (cs/join ", " statement-ids)))
              (recur))
            (do
              (log/debugf "Reaction channel shutdown")
              ::shutdown)))]
    (log/info "Reaction processor started.")
    reaction-executor))

(s/fdef shutdown-reactions!
  :args (s/cat :?reaction-channel (s/nilable ::common-spec/channel)
               :?reaction-executor (s/nilable ::common-spec/channel))
  :ret nil?)

(defn shutdown-reactions!
  "Given a (possibly nil) reaction channel and reaction executor channel,
  gracefully shut them down."
  [?reaction-channel
   ?reaction-executor]
  (when (and ?reaction-channel ?reaction-executor)
    (log/info "Stopping reaction processor...")
    (log/debug "Closing reaction channel...")
    (a/close! ?reaction-channel)
    (log/debug "Draining reaction buffer...")
    ;; Block until all reactions are processed
    (a/<!! ?reaction-executor)
    (log/debug "Reaction executor shut down.")
    (log/info "Reaction processor stopped.")))

(s/fdef new-reaction-cache
  :args (s/cat)
  :ret #(instance? clojure.lang.Atom %))

(defn new-reaction-cache
  "Create a new empty reaction cache."
  []
  (atom nil))

(s/fdef cache-reactions
  :args (s/cat :reactions ::rs/reactions)
  :ret rs/reaction-cache-state-spec)

(defn cache-reactions
  "Provide the reaction cache state for a given list of reactions."
  [reactions]
  {:query-at  (System/currentTimeMillis)
   :reactions reactions})

(s/fdef validate-cache!
  :args (s/cat :reaction-cache #(instance? clojure.lang.Atom %)
               :ttl nat-int?)
  :ret (s/nilable ::rs/reactions))

(defn validate-cache!
  "Check the reaction cache and if it is valid return reactions. Otherwise clear
  it."
  [reaction-cache ttl]
  (:reactions
   (swap! reaction-cache
          (fn [state]
            (when-let [{:keys [query-at]} state]
              (let [age (- (System/currentTimeMillis)
                           query-at)]

                (when (> ttl age)
                  (log/debugf "reaction cache hit ttl: %s age: %s" ttl age)
                  state)))))))
