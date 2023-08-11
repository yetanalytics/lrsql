(ns lrsql.init.reaction
  "Reaction initialization functions."
  (:require [clojure.core.async    :as a]
            [clojure.spec.alpha    :as s]
            [clojure.tools.logging :as log]
            [lrsql.util            :as u]
            [lrsql.spec.config     :as config-spec]
            [lrsql.spec.common     :as common-spec]
            [xapi-schema.spec      :as xs]))

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
